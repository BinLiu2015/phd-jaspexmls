/*
 * jaspex-mls: a Java Software Speculative Parallelization Framework
 * Copyright (C) 2015 Ivo Anjo <ivo.anjo@ist.utl.pt>
 *
 * This file is part of jaspex-mls.
 *
 * jaspex-mls is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * jaspex-mls is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with jaspex-mls.  If not, see <http://www.gnu.org/licenses/>.
 */

package jaspex.speculation;

import jaspex.ClassFilter;
import jaspex.Options;
import jaspex.speculation.nsruntime.ContSpeculationControl;
import jaspex.speculation.runtime.*;
import jaspex.stm.ExternalAccessHelper;
import jaspex.transactifier.Transactifier;
import jaspex.util.Unsafe;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asmlib.Type;

import util.StringList;

import static jaspex.util.ShellColor.color;

/** ClassLoader que passa classes pelo Transactifier e pelo SpeculativeTransformer antes de as carregar na VM **/
public class SpeculativeClassLoader extends ClassLoader {

	private static final Logger Log = LoggerFactory.getLogger(SpeculativeClassLoader.class);

	public static final SpeculativeClassLoader INSTANCE = new SpeculativeClassLoader();

	private SpeculativeClassLoader() {
		if (jaspex.Options.CLASSCACHE) {
			Cache.tryRestore();
		}
	}

	@Override
	protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		String mode = color("[ NormalLoad ]", "32");
		Type classType = Type.fromCommon(name);

		try {
			Class<?> c = findLoadedClass(name);
			if (c != null) return c;

			// Não se podem transactificar coisas java.* + coisas nossas
			if (!ClassFilter.isTransactifiable(classType) &&
				!CodegenHelper.isCodegenClass(classType) &&
				!ExternalAccessHelper.isExternalAccessClass(classType)) {
				mode = color("[ UpstreamLoader ]", "35");

				if (name.startsWith("java.util.concurrent.Future$")) {
					// Bug newspec
					throw new AssertionError("Tried to load Future with extra type info");
				}

				return getParent().loadClass(name);
			}

			c = findClass(name);
			if (resolve) resolveClass(c);
			return c;
		} finally {
			if ((!name.startsWith("java.") || Options.TRANSACTIFYJDK) && !name.startsWith("jaspex.")) {
				Log.trace("{} Loading {}", mode, name);
			}
			if (CodegenHelper.isCodegenClass(classType)) {
				Log.trace("Loaded codegen class {}",
						name.replace("jaspex.speculation.runtime.codegen.", ""));
			}
		}
	}

	@Override
	protected Class<?> findClass(String className) throws ClassNotFoundException {
		byte[] classFile;
		try {
			long startTime = System.currentTimeMillis();
			classFile = getClassBytes(Type.fromCommon(className));
			long prepareTime = System.currentTimeMillis() - startTime;
			if (prepareTime > 1000) {
				Log.warn("Needed " + ((double) prepareTime)/1000 + "s to prepare " + className);
			}
		} catch (IOException e) {
			throw new ClassNotFoundException("Could not load class " + className, e);
		} catch (RuntimeException e) {
			Log.error("Error while preparing class " + className, e);
			throw e;
		} catch (Error e) {
			Log.error("Error while preparing class " + className, e);
			throw e;
		}

		// Verificar que classe já não foi carregada entretanto (CheckClassAdapter pode causar isso)
		Class<?> c = findLoadedClass(className);
		if (c != null) {
			Log.warn("Class {} was already loaded during findClass", className);
			return c;
		}

		try {
			if (Options.TRANSACTIFYJDK) {
				// Usar UNSAFE para carregar classe, fazendo bypass às verificações que impedem
				// o carregamento de classes da JDK
				return Unsafe.UNSAFE.defineClass(className, classFile, 0, classFile.length,
									this, getDefaultProtectionDomain());
			} else {
				return defineClass(className, classFile, 0, classFile.length);
			}
		} catch (SecurityException e) {
			throw new Error(e);
		}
	}

	// Baseado em http://stackoverflow.com/questions/271506/
	// Adiciona jarFile ao classpath actual, como se tivesse sido passado por -cp ou $CLASSPATH
	private static void addToClasspath(File jarFile) throws Exception {
		java.net.URL url = jarFile.toURI().toURL();
		java.lang.reflect.Method m =
			java.net.URLClassLoader.class.getDeclaredMethod("addURL", java.net.URL.class);
		m.setAccessible(true);
		m.invoke(ClassLoader.getSystemClassLoader(), url);
		// É raro, mas já encontrei um programa que gosta de ir consultar isto, e depois não
		// se encontra
		System.setProperty("java.class.path",
			jarFile.getAbsolutePath() + File.pathSeparatorChar +
			System.getProperty("java.class.path"));
	}

	protected byte[] getClassBytes(Type className) throws IOException {
		byte[] classBytes;

		if (jaspex.Options.CLASSCACHE) {
			classBytes = Cache.lookupClass(className);
			if (classBytes != null) return classBytes;
		}

		if (CodegenHelper.isCodegenClass(className)) {
			return CodegenHelper.generateClass(className);
		} else if (ExternalAccessHelper.isExternalAccessClass(className)) {
			return ExternalAccessHelper.generateClass(className);
		} else {
			classBytes = new Transactifier(className).transform();
			classBytes = new SpeculativeTransformer(false).transform(classBytes);
			if (jaspex.Options.CLASSCACHE) {
				Cache.saveClass(className, classBytes);
			}
			return classBytes;
		}
	}

	private java.security.ProtectionDomain getDefaultProtectionDomain() {
		try {
			Field f = ClassLoader.class.getDeclaredField("defaultDomain");
			f.setAccessible(true);
			return (java.security.ProtectionDomain) f.get(this);
		} catch (Exception e) { throw new Error(e); }
	}

	public void execute(StringList args) throws Throwable {
		Log.info("JaSPEx SpeculativeClassLoader (Start time: {}, Args: '{}')",
			new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()),
			args.join(" "));

		if (Options.JAR) {
			File jar = new File(args.pollFirst());
			addToClasspath(jar);
			JarFile jarFile = new JarFile(jar);
			String mainClass = jarFile.getManifest().getMainAttributes()
						.getValue(Attributes.Name.MAIN_CLASS);
			jarFile.close();
			args.addFirst(mainClass);
		}

			// Infelizmente não podemos usar reflection para invocar métodos dentro do
			// runWithContinuationSupport, portanto abusamos do codegen para obter um
			// objecto callable que chame o main
			Type codegenType = CodegenHelper.methodToCodegenType(
				new InvokedMethod(Opcodes.INVOKESTATIC, Type.fromCommon(args.pollFirst()),
					"main$speculative",
					"(" + Type.STRING.toArray().bytecodeName() + ")V"));

			final Callable c = (Callable) loadClass(codegenType.commonName(), true)
				.getConstructor(String[].class).newInstance((Object) args.toArray());

			// Passamos o objecto que quando invocado chama o main para o ContSpeculationControl
			ContSpeculationControl.bootstrapMain(c);
	}

}
