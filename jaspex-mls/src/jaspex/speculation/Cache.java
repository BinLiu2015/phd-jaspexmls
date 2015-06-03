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

import jaspex.Jaspex;
import jaspex.speculation.runtime.CodegenHelper;
import jaspex.util.HashUtils;
import jaspex.util.IOUtils;

import java.io.*;
import java.util.*;

import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asmlib.Type;

/** Esta classe gere uma cache de classes processadas para utilização pelo JaSPEx.
  *
  * Para além disso, ainda tem que persistir o estado do CodegenHelper, já que é necessario para gerar as
  * classes Codegen correctamente (podem ser necessárias novas numa nova execução da aplicação, por seguir
  * codepaths diferentes, ou as antigas podem não ter sido todas geradas).
  *
  * Para tentar fazer alguma validação, é guardado no ficheiro CACHE_STATE_FILE o hash da code tree do JaSPEx
  * e dos argumentos com que foi iniciado, de forma que se o JaSPEx for alterado ou os argumentos mudarem
  * (algumas das opções do jaspex, como -staticworkaround alteram o bytecode gerado), a cache é invalidada.
  *
  * Para guardar contra alterações do programa-alvo, é introduzida em cada classe o hash da classe que lhe deu
  * origem, e esse hash é validado quando uma classe é lida da cache. UMA NOTA MUITO IMPORTANTE DISTO É QUE
  * MESMO ASSIM É POSSÍVEL QUE O PROGRAMA-ALVO MUDE E O JASPEX NÃO DETECTE: Várias decisões ao longo do
  * processo de preparação de classes são tomadas com base em informação lida de outras classes, e não só da
  * classe que está a ser preparada. Seria unusual, mas não impossível que fosse tomada uma decisão com base
  * numa outra classe externa, que fosse depois alterada, e portanto ao carregar a classe pedida essa
  * alteração não fosse detectada, já que apenas é validado o hash da classe pedida.
  * Por isso é que o JaSPEx pára com erro se for detectado um hash diferente na cache, em vez de simplemente
  * re-preparar a classe: Um hash diferente é sinal que uma das classes mudou, e portanto não podemos saber
  * se outras classes também deveriam ter mudado para reflectir essa mudança.
  **/
public class Cache {

	private static final Logger Log = LoggerFactory.getLogger(Cache.class);

	public static final String CACHE_DIR = "cache" + File.separatorChar;
	private static final String CACHE_STATE_FILE = CACHE_DIR + "cachestate.bin";

	private static final String JASPEX_HASH = hashJaspex();
	private static final String ARGUMENTS_HASH = HashUtils.hashString(Jaspex._arguments);

	private static final String CLASS_HASH_FIELD = "$cache_originalhash";

	static {
		assert (jaspex.Options.CLASSCACHE);
	}

	public static String hashJaspex() {
		File f = new File(Jaspex.class.getResource("Jaspex.class")
				.getPath()).getParentFile().getParentFile();
		return HashUtils.hashBytesToString(HashUtils.hashSubtree(f));
	}

	public static String hashClass(Type className) {
		InputStream is = ClassLoader.getSystemResourceAsStream(className.asmName() + ".class");
		try {
			String res = HashUtils.hashBytesToString(HashUtils.md5().digest(IOUtils.readStream(is)));
			is.close();
			return res;
		} catch (IOException e) { throw new Error(e); }
	}

	private static class CacheState implements Serializable {
		private static final long serialVersionUID = 1L;

		// md5sum da directoria de build / jar do jaspex
		public final String _jaspexHash;
		// md5sum dos argumentos passados ao jaspex
		public final String _argumentsHash;
		// estado do codegen
		public final InvokedMethodInfo[] _invokedMethods;

		// Necessário para reconstuir os InvokeMethods
		private static class InvokedMethodInfo implements Serializable {
			private static final long serialVersionUID = 1L;

			private final int _id;
			private final int _opcode;
			private final String _owner;
			private final String _name;
			private final String _desc;

			private InvokedMethodInfo(int id, InvokedMethod m) {
				_id = id;
				_opcode = m.opcode();
				_owner = m.owner().bytecodeName();
				_name = m.name();
				_desc = m.desc();
			}

			public InvokedMethod toInvokedMethod() {
				return new InvokedMethod(_opcode, Type.fromBytecode(_owner), _name, _desc);
			}
		}

		private CacheState() {
			_jaspexHash = JASPEX_HASH;
			_argumentsHash = ARGUMENTS_HASH;
			Map<Integer, InvokedMethod> codegenState = CodegenHelper.saveCodegen();
			_invokedMethods = new InvokedMethodInfo[codegenState.size()];
			int i = 0;
			for (Map.Entry<Integer, InvokedMethod> entry : codegenState.entrySet()) {
				_invokedMethods[i++] = new InvokedMethodInfo(entry.getKey(), entry.getValue());
			}
		}

		private Map<Integer, InvokedMethod> getCodegenState() {
			Map<Integer, InvokedMethod> map = new HashMap<Integer, InvokedMethod>(_invokedMethods.length);
			for (InvokedMethodInfo imi : _invokedMethods) {
				map.put(imi._id, imi.toInvokedMethod());
			}
			return map;
		}
	}

	private static void clearCache() {
		File cacheDir = new File(CACHE_DIR);
		if (!cacheDir.exists()) return;

		for (File f : cacheDir.listFiles()) {
			f.delete();
		}
		if (!cacheDir.delete()) throw new Error("Cannot delete cache directory");
	}

	public static void flush() {
		new File(CACHE_DIR).mkdir();
		try {
			ObjectOutputStream oos = new ObjectOutputStream(
							new FileOutputStream(CACHE_STATE_FILE));
			oos.writeObject(new CacheState());
			oos.close();
		} catch (java.io.FileNotFoundException e) { throw new Error(e);	}
		  catch (java.io.IOException e) { throw new Error(e); }
	}

	public static void tryRestore() {
		Log.debug("Attempting to restore cached state...");
		try {
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(CACHE_STATE_FILE);
			} catch (FileNotFoundException e) {
				Log.debug("Existing cache not found");
				clearCache();
				return;
			}
			ObjectInputStream ois = new ObjectInputStream(fis);
			CacheState cs = null;
			try {
				cs = (CacheState) ois.readObject();
			} catch (IOException e) { /* cs == null */ }
			ois.close();
			if (cs == null || !cs._jaspexHash.equals(JASPEX_HASH) || !cs._argumentsHash.equals(ARGUMENTS_HASH)) {
				Log.debug("Cache is stale");
				clearCache();
				return;
			}
			Log.debug("Cache is valid");
			CodegenHelper.restoreCodegen(cs.getCodegenState());
		} catch (IOException e) { throw new Error(e); }
		  catch (ClassNotFoundException e) { throw new Error(e); }
	}

	public static void saveClass(final Type className, byte[] classBytes) {
		new File(CACHE_DIR).mkdir();
		try {
			ClassReader cr = new ClassReader(classBytes);
			ClassWriter cw = new ClassWriter(cr, 0);

			cr.accept(new ClassVisitor(Opcodes.ASM4, cw) {
				@Override
				public void visitEnd() {
					visitField(Opcodes.ACC_FINAL | Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, CLASS_HASH_FIELD,
						asmlib.Type.STRING.bytecodeName(), null, hashClass(className));
					cv.visitEnd();
				}
			}, 0);

			FileOutputStream fos =
				new FileOutputStream(CACHE_DIR + className.commonName() + ".class");
			fos.write(cw.toByteArray());
			fos.close();
		} catch (IOException e) { throw new Error(e); }
	}

	public static byte[] lookupClass(Type className) {
		try {
			byte[] classBytes = IOUtils.readFile(new File(CACHE_DIR + className.commonName() + ".class"));

			ClassReader cr = new ClassReader(classBytes);

			final String[] hash = new String[1];

			cr.accept(new ClassVisitor(Opcodes.ASM4) {
				@Override
				public FieldVisitor visitField(int access, String name, String desc,
					String signature, Object value) {
					if (name.equals(CLASS_HASH_FIELD)) hash[0] = (String) value;
					return null;
				}
			}, 0);

			if (hash[0] == null) throw new Error("Unexpected class file found in cache");

			if (!hash[0].equals(hashClass(className))) {
				clearCache();
				// Ver nota no inicio do ficheiro sobre porque é que isto tem de dar erro
				throw new Error("Invalid class in cache, clear cache and re-run");
			}

			return classBytes;
		} catch (FileNotFoundException e) {
			return null;
		} catch (IOException e) {
			throw new Error(e);
		}
	}

}
