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

package jaspex.transactifier;

import asmlib.*;
import asmlib.Type;

import java.io.*;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.util.CheckClassAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jaspex.Options;

public class Transactifier {

	private static final Logger Log = LoggerFactory.getLogger(Transactifier.class);

	private final ClassReader cr;
	private final InfoClass currentClass;
	private final boolean _JDKClass;

	/** Constructor utilizado pelo JDKTransactifier **/
	public Transactifier(byte[] classBytes) throws IOException {
		this(new ClassReader(classBytes), true);
	}

	/** Constructor normal **/
	public Transactifier(Type type) throws IOException {
		this(new ClassReader(type.commonName()), false);
	}

	private Transactifier(ClassReader classReader, boolean JDKClass) throws IOException {
		// Forçar a geração de FRAMES para todas as classes, logo no inicio de toda a cadeia,
		// para evitar possíveis problemas em tudo o que se segue e que vai processar esta classe
		ClassWriter cw = new jaspex.util.ClassWriter(ClassWriter.COMPUTE_FRAMES);
		try {
			classReader.accept(cw, ClassReader.EXPAND_FRAMES);
		} catch (RuntimeException e) {
			if (!e.getMessage().equals("JSR/RET are not supported with computeFrames option")) throw e;

			Log.debug("Class " + classReader.getClassName() + " uses JSR/RET. Inlining...");
			// Repetir processo, fazendo inline de JSR/RET primeiro
			cw = new jaspex.util.ClassWriter(ClassWriter.COMPUTE_FRAMES);

			classReader.accept(new ClassVisitor(Opcodes.ASM4, cw) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String desc,
						String signature, String[] exceptions) {
					return new JSRInlinerAdapter(
						cv.visitMethod(access, name, desc, signature, exceptions),
						access, name, desc, signature, exceptions);
				}
			}, ClassReader.EXPAND_FRAMES);
		}

		cr = new ClassReader(cw.toByteArray());
		currentClass = InfoClass.fromType(Type.fromAsm(cr.getClassName()));
		_JDKClass = JDKClass;
	}

	public byte[] transform() throws IOException {
		byte[] output = transactify();

		// Passar verificador do ASM pelo output
		//checkBytecode(output);

		return output;
	}

	public static void checkBytecode(byte[] output) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		ClassReader cr = new ClassReader(output);

		try {
			if (!Options.FASTMODE) CheckClassAdapter.verify(cr,
						jaspex.speculation.SpeculativeClassLoader.INSTANCE, false, pw);
		} catch (ClassCircularityError e) { }

		try {
			DuplicateMethodChecker.verify(cr, pw);
			UninitializedCallChecker.verify(cr, pw);
		} catch (RuntimeException e) {
			// Ignorar erro
			if (!e.toString().contains("jaspex.MARKER.Transactional")) throw e;
		} catch (AssertionError e) {
			if (!e.toString().contains("Unexpected type found on stack")) throw e;
			e.printStackTrace();
		} catch (LinkageError e) {
			// Causado pelo ClassWriter.getCommonSuperClass tentar carregar classes na VM
		}

		String verifierOutput = sw.toString();
		int length = verifierOutput.length();
		if (length > 0) {
			if (verifierOutput.charAt(length-1) == '\n') verifierOutput = verifierOutput.substring(0, length-1);
			Log.warn("Error(s) were detected on the output bytecode for class {}", cr.getClassName().replace('/', '.'));
			Log.debug(verifierOutput);
		}
	}

	private byte[] transactify() throws IOException {
		final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

		// Copiar métodos originais inalterados
		cr.accept(new ClassVisitor(Opcodes.ASM4) {
			@Override
			public void visit(int version, int access, String name, String signature,
				String superName, String[] interfaces) {
				cw.visit(version, access, name, signature, superName, interfaces);
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature,
				String[] exceptions) {
				if (name.equals("<clinit>")) return new EmptyMethodVisitor();
				return cw.visitMethod(access, name, desc, signature, exceptions);
			}

			@Override public void visitEnd() { cw.visitEnd(); }
		}, 0);

		// Variável que contém o último ClassVisitor da "chain" de classvisitors que servem de filtros ao
		// ficheiro original
		ClassVisitor cv = cw;

		// Métodos alterados são marcados com $transactional no nome
		// Este marcador é temporário, e irá ser substituido por $speculative mais à frente
		// Nota: Apenas os métodos são renomeados, os seus INVOKE* ainda não apontam para os
		// $transactional; essa alteração é feita no SpeculativeTransformer
		cv = new ClassVisitor(Opcodes.ASM4, cv) {
			@Override public MethodVisitor visitMethod(int access, String name, String desc,
				String signature, String[] exceptions) {
				if (name.equals("<init>")) {
					desc = desc.replace(")", "Ljaspex/MARKER/Transactional;)");
				} else if (!name.equals("<clinit>")) {
					name += "$transactional";
				}
				return cv.visitMethod(access, name, desc, signature, exceptions);
			}
		};

		// Remover ACC_SYNCHRONIZED dos métodos
		if (Options.REMOVESYNC || _JDKClass) cv = new RemoveSyncClassVisitor(cv);

		// Remover MONITORENTER/MONITOREXIT dos métodos
		if (Options.REMOVEMONITORS) cv = new GenericMethodVisitorAdapter(cv, RemoveMonitorsClassVisitor.class);

		// Verificar se existem métodos com ACC_SYNCHRONIZED ou opcodes MONITORENTER/MONITOREXIT
		if (!_JDKClass) cv = new CheckMonitorUsage(cv);

		// Corrigir chamadas a alguns métodos de java.lang.Object
		cv = new GenericMethodVisitorAdapter(cv, ChangeObjectMethodsMethodVisitor.class, currentClass);

		// Adicionar overrides a alguns métodos de java.lang.Object
		cv = new AddObjectMethodsClassVisitor(cv, currentClass);

		// Suporte para Arrays
		cv = new GenericMethodVisitorAdapter(cv, ChangeArrayAccessMethodVisitor.class, currentClass, _JDKClass);

		// Adicionar inicialização de offsets usados pela unsafetrans ao clinit da classe
		// Nota: Visitor tem que estar *depois* do FieldTransactifierClassVisitor
		if (!_JDKClass) cv = new GenericMethodVisitorAdapter(cv, ChangeClinitMethodVisitor.class, currentClass);

		// Visitor que cria os fields offset e o staticfieldbase
		if (!_JDKClass) cv = new FieldTransactifierClassVisitor(cv);

		// Alterar acessos a fields para passarem pela STM
		cv = new GenericMethodVisitorAdapter(cv, ChangeFieldAccessMethodVisitor.class, currentClass, _JDKClass);

		// Modificar string com filename da classe que aparece em excepções
		if (!_JDKClass) cv = new MarkAsTransactifiedClassVisitor(cv);

		// Verificar e fazer upgrade da versão da classe, se necessário
		if (!_JDKClass) cv = new ClassVisitor(Opcodes.ASM4, cv) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				// Transactificação precisa de version >= 49, porque usa o MethodVisitor.visitLdcInsn(Type)
				// MethodVisitor.visitLdcInsn(Type), que só funciona a partir dessa versão dos classfiles.
				// http://asm.ow2.org/asm40/javadoc/user/org/objectweb/asm/MethodVisitor.html#visitLdcInsn(java.lang.Object)
				// http://stackoverflow.com/questions/2784791/
				// Caso especial: V1_1 é 196653, por alguma razão...
				if (version < Opcodes.V1_5 || version == Opcodes.V1_1) {
					//Log.debug("Class " + name + " has version " + version + ", upgrading it to Java 5");
					version = Opcodes.V1_5;
				}

				if (version > Opcodes.V1_6 && !name.startsWith("java/")) {
					Log.warn("Class " + name + " is compiled for Java 7 or newer");
				}

				super.visit(version, access, name, signature, superName, interfaces);
			}
		};

		cr.accept(cv, ClassReader.EXPAND_FRAMES);

		return cw.toByteArray();
	}

}
