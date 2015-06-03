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

package jaspex.speculation.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jaspex.Options;
import jaspex.speculation.CommonTypes;
import jaspex.speculation.FixPrivateMethodAccessMethodVisitor;
import jaspex.speculation.InvokedMethod;
import jaspex.speculation.newspec.SpeculationSkiplist;
import util.UtilList;

import static jaspex.util.ShellColor.color;

import asmlib.Type;

/** Classe que gera classes wrapper para chamadas especulativas a métodos.
  *
  * Em vez de se coleccionar os argumentos num array, e depois usar reflection para invocar um método dentro do
  * ExecutionTask, geramos uma classe que guarda os argumentos em fields com o tipo correcto (evita boxing/unboxing)
  * e que tem um método call, que executa o método alvo, com os argumentos com que a instância da classe wrapper
  * foi criada.
  **/
public final class CodegenHelper {

	private static final Logger Log = LoggerFactory.getLogger(CodegenHelper.class);

	/** Prefixo usado para classes codegen.
	  * Derivado do nome do CodegenHelper, para permitir refactorizações automáticas.
	  **/
	public static final String CODEGEN_CLASS_PREFIX =
		CodegenHelper.class.getPackage().getName() + ".codegen.Codegen$";

	/** Mappings entre métodos a serem invocados usado especulação e ids. **/
	private static final Map<Integer, InvokedMethod> _idToMethodMap = new HashMap<Integer, InvokedMethod>();
	private static final Map<InvokedMethod, Integer> _methodToIdMap = new HashMap<InvokedMethod, Integer>();

	public static Map<Integer, InvokedMethod> saveCodegen() {
		return java.util.Collections.unmodifiableMap(_idToMethodMap);
	}

	public static void restoreCodegen(Map<Integer, InvokedMethod> idToMethodMap) {
		assert (_idToMethodMap.isEmpty());
		assert (_methodToIdMap.isEmpty());
		for (Map.Entry<Integer, InvokedMethod> entry : idToMethodMap.entrySet()) {
			Integer id = entry.getKey();
			InvokedMethod method = entry.getValue();
			_idToMethodMap.put(id, method);
			_methodToIdMap.put(method, id);
		}
	}

	/** Mantém um mapping entre InvokedMethods e inteiros.
	  * Cada InvokedMethod único tem um Id, e esse Id é incluido no nome da classe a ser gerada.
	  **/
	public static Type methodToCodegenType(InvokedMethod method) {
	        Integer id = _methodToIdMap.get(method);
	        if (id == null) {
			id = _methodToIdMap.size();
			_methodToIdMap.put(method, id);
			_idToMethodMap.put(id, method);
	        }
	        return Type.fromCommon(CODEGEN_CLASS_PREFIX + id + "$"
			+ method.owner().commonName().replace('.', '_') + "."
			+ FixPrivateMethodAccessMethodVisitor.stripPrivate(method.name()));
	}

	/** Devolve id interno do codegen. Usar apenas para debugging **/
	public static int codegenId(InvokedMethod method) {
		Integer id = _methodToIdMap.get(method);
		if (id == null) return -1;
		return id;
	}

	public static boolean isCodegenClass(Type type) {
		return type.commonName().startsWith(CODEGEN_CLASS_PREFIX);
	}

	/** Usado para debugging, transforma nome de classe codegen de volta para o nome original **/
	public static String codegenToOriginal(String codegenName) {
		int endClassIndex = codegenName.lastIndexOf('.');
		return codegenName.substring(CODEGEN_CLASS_PREFIX.length(), endClassIndex).replace('_', '.')
			+ codegenName.subSequence(endClassIndex, codegenName.length()).toString().replace("$speculative", "");
	}

	public static byte[] generateClass(Type codegenType) {
		// Obter id a partir do tipo
		String name = codegenType.commonName();
		name = name.replaceFirst(Matcher.quoteReplacement(CODEGEN_CLASS_PREFIX), "");
		int id = Integer.parseInt(name.substring(0, name.indexOf("$")));

		// Obter InvokedMethod
		InvokedMethod method = _idToMethodMap.get(id);

		Log.trace(color("[ CodeGen ]", "31") + " Generating wrapper for {}.{}{}",
			method.owner(), method.name(), method.desc());

		UtilList<Type> arguments = method.argumentTypes();
		if (!method.isStatic()) arguments.addFirst(method.owner());
		String argumentsDesc = "";
		for (Type type : arguments) argumentsDesc += type.bytecodeName();

		// Optimização: Se método for ()V e static, criamos um singleton apenas com o call,
		//		e tornamos o constructor private para que a classe não seja instanciada
		//		noutros locais
		boolean singletonMode = arguments.isEmpty();

		// Criar classe
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(V1_6, ACC_PUBLIC | ACC_FINAL, codegenType.asmName(), null,
				CommonTypes.CALLABLE.asmName(),
				(Options.RVP ?
					new String[] { CommonTypes.PREDICTABLECALLABLE.asmName() } :
					null));
		cw.visitSource(color("JaSPEx Generated Wrapper Class", "31"), null);

		// Criar fields para conter argumentos
		{
		int fieldPos = 0;
		for (Type t : arguments) {
			cw.visitField(ACC_PRIVATE + ACC_FINAL, "arg" + (fieldPos++), t.bytecodeName(), null, null);
		}
		}

		// Criar constructor
		{
		MethodVisitor mv = cw.visitMethod((singletonMode ? ACC_PRIVATE : ACC_PUBLIC),
				"<init>", "(" + argumentsDesc + ")V", null, null);
		mv.visitCode();
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKESPECIAL, Type.OBJECT.asmName(), "<init>", "()V");
		int localsPos = 0;
		int fieldPos = 0;
		for (Type t : arguments) {
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(t.getLoadInsn(), localsPos+1);
			mv.visitFieldInsn(PUTFIELD, codegenType.asmName(), "arg" + fieldPos++, t.bytecodeName());
			localsPos += t.getNumberSlots();
		}
		mv.visitInsn(RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		}

		// Criar método estático como "bridge" para o constructor
		// A ideia deste método é resolver o problema de já termos os argumentos para a invocação na stack
		// quando descobrimos que queremos criar uma instância desta classe. Assim, em vez de andar a
		// fazer swaps até colocarmos a instância desta classe por baixo dos argumentos para o seu
		// construtor, chamamos este método estático para fazer esse trabalho por nós.
		// O que este método é basicamente:
		// public static codegenType newInstance(args) { return new codegenType(args); }
		if (!singletonMode) {
		MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "newInstance", "(" + argumentsDesc + ")" +
			codegenType.bytecodeName(), null, null);
		mv.visitCode();
		mv.visitTypeInsn(NEW, codegenType.asmName());
		mv.visitInsn(DUP);
		int localsPos = 0;
		for (Type t : arguments) {
			mv.visitVarInsn(t.getLoadInsn(), localsPos);
			localsPos += t.getNumberSlots();
		}
		mv.visitMethodInsn(INVOKESPECIAL, codegenType.asmName(), "<init>", "(" + argumentsDesc + ")V");
		mv.visitInsn(ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		}

		// Criar métodos call e call_nonspeculative
		for (String[] target : new String[][] {
			{"call", method.name()},
			{"call_nonspeculative", method.name().replace("$speculative",  "$non_speculative")}}) {
		MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, target[0], "()" + Type.OBJECT.bytecodeName(), null, null);
		mv.visitCode();
		int fieldPos = 0;
		for (Type t : arguments) {
			mv.visitVarInsn(ALOAD, 0);
			mv.visitFieldInsn(GETFIELD, codegenType.asmName(), "arg" + fieldPos++, t.bytecodeName());
		}
		mv.visitMethodInsn(method.opcode() == INVOKESPECIAL ? INVOKEVIRTUAL : method.opcode(),
			method.owner().asmName(), target[1], method.desc());
		if (method.returnType().equals(Type.PRIM_VOID)) {
			if (!jaspex.Options.FASTMODE) mv.visitLdcInsn("JASPEX VOID RETURN VALUE");
			else mv.visitInsn(ACONST_NULL);
		} else if (method.returnType().isPrimitive()) boxWrap(method.returnType(), mv);
		mv.visitInsn(ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		}

		if (singletonMode) {
		// Criar field com singleton
		cw.visitField(ACC_STATIC | ACC_FINAL | ACC_PUBLIC, "INSTANCE", codegenType.bytecodeName(), null, null);
		}

		if (Options.RVP) {
		// Field com previsão
		cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "PREDICTOR", CommonTypes.PREDICTOR.bytecodeName(), null, null);

		{ // Método de acesso à previsão
			MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "predict", "()" + Type.OBJECT.bytecodeName(), null, null);
			mv.visitCode();
			if (method.returnType().equals(Type.PRIM_VOID)) {
				mv.visitTypeInsn(NEW, "java/lang/AssertionError");
				mv.visitInsn(DUP);
				mv.visitLdcInsn("Asked for prediction for Void method");
				mv.visitMethodInsn(INVOKESPECIAL, "java/lang/AssertionError", "<init>", "(Ljava/lang/Object;)V");
				mv.visitInsn(ATHROW);
			} else {
				mv.visitFieldInsn(GETSTATIC, codegenType.asmName(), "PREDICTOR", CommonTypes.PREDICTOR.bytecodeName());
				mv.visitMethodInsn(INVOKEVIRTUAL, CommonTypes.PREDICTOR.asmName(), "predict", "()" + Type.OBJECT.bytecodeName());
				mv.visitInsn(ARETURN);
			}
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		{ // Método de actualização da previsão
			MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "updatePrediction", "(" + Type.OBJECT.bytecodeName() + ")V", null, null);
			mv.visitCode();
			if (!method.returnType().equals(Type.PRIM_VOID)) {
				mv.visitFieldInsn(GETSTATIC, codegenType.asmName(), "PREDICTOR", CommonTypes.PREDICTOR.bytecodeName());
				mv.visitVarInsn(ALOAD, 1);
				mv.visitMethodInsn(INVOKEVIRTUAL, CommonTypes.PREDICTOR.asmName(), "updatePrediction", "(" + Type.OBJECT.bytecodeName() + ")V");
			}
			mv.visitInsn(RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}

		}

		// Gerar clinit caso existam fields para inicializar
		if (singletonMode || Options.RVP) {
			// Adicionar static initializer
			MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
			mv.visitCode();
			if (singletonMode) {
				mv.visitTypeInsn(NEW, codegenType.asmName());
				mv.visitInsn(DUP);
				mv.visitMethodInsn(INVOKESPECIAL, codegenType.asmName(), "<init>", "()V");
				mv.visitFieldInsn(PUTSTATIC, codegenType.asmName(), "INSTANCE", codegenType.bytecodeName());
			}
			if (Options.RVP) {
				mv.visitLdcInsn(method.returnType().bytecodeName());
				mv.visitMethodInsn(INVOKESTATIC, CommonTypes.PREDICTORFACTORY.asmName(), "newPredictor",
					"(" + Type.STRING.bytecodeName() + ")" + CommonTypes.PREDICTOR.bytecodeName());
				mv.visitFieldInsn(PUTSTATIC, codegenType.asmName(), "PREDICTOR", CommonTypes.PREDICTOR.bytecodeName());
			}
			mv.visitInsn(RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}

		// Criar toString
		{
		Type sb = Type.fromClass(StringBuilder.class);
		MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "toString", "()" + Type.STRING.bytecodeName(), null, null);
		mv.visitCode();
		mv.visitTypeInsn(NEW, sb.asmName());
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKESPECIAL, Type.OBJECT.asmName(), "toString", "()" + Type.STRING.bytecodeName());
		mv.visitMethodInsn(INVOKESPECIAL, sb.asmName(), "<init>", "(" + Type.STRING.bytecodeName() + ")V");
		mv.visitVarInsn(ASTORE, 1);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitLdcInsn("{");
		mv.visitMethodInsn(INVOKEVIRTUAL, sb.asmName(), "append", "(" + Type.STRING.bytecodeName() + ")" + sb.bytecodeName());
		int fieldPos = 0;
		boolean first = true;
		for (Type t : arguments) {
			mv.visitVarInsn(ALOAD, 1);
			mv.visitLdcInsn((first ? "" : ", ") + "arg" + fieldPos + ": ");
			first = false;
			mv.visitMethodInsn(INVOKEVIRTUAL, sb.asmName(), "append", "(" + Type.STRING.bytecodeName() + ")" + sb.bytecodeName());
			mv.visitVarInsn(ALOAD, 1);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitFieldInsn(GETFIELD, codegenType.asmName(), "arg" + fieldPos++, t.bytecodeName());
			if (t.isPrimitive()) boxWrap(t, mv);
			mv.visitMethodInsn(INVOKESTATIC, Type.STRING.asmName(), "valueOf", "(" + Type.OBJECT.bytecodeName() + ")" + Type.STRING.bytecodeName());
			mv.visitMethodInsn(INVOKEVIRTUAL, sb.asmName(), "append", "(" + Type.STRING.bytecodeName() + ")" + sb.bytecodeName());
		}
		mv.visitVarInsn(ALOAD, 1);
		mv.visitLdcInsn("} ");
		mv.visitMethodInsn(INVOKEVIRTUAL, sb.asmName(), "append", "(" + Type.STRING.bytecodeName() + ")" + sb.bytecodeName());
		mv.visitVarInsn(ALOAD, 1);
		mv.visitMethodInsn(INVOKEVIRTUAL, Type.OBJECT.asmName(), "toString", "()" + Type.STRING.bytecodeName());
		mv.visitInsn(ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		}

		if (Options.ALLOWDUMMYTX && SpeculationSkiplist.useDummyTransaction(method)) {
			Log.info("Setting isDummy transaction flag for " + method.owner().commonName() + "." + method.name());

			MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "useDummyTransaction", "()Z", null, null);
			mv.visitCode();
			mv.visitInsn(ICONST_1);
			mv.visitInsn(IRETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}

		cw.visitEnd();

		byte[] newClass = cw.toByteArray();

		//if (speculation.SpeculativeClassLoader.PRINTCLASS) asmlib.Util.printClass(newClass);

		if (Options.WRITECLASS) {
			try {
				new java.io.File("output" + java.io.File.separatorChar).mkdir();
				java.io.FileOutputStream fos = new java.io.FileOutputStream("output" +
					java.io.File.separatorChar + codegenType.commonName() + ".class");
				fos.write(newClass);
				fos.close();
			} catch (java.io.FileNotFoundException e) { throw new Error(e);	}
			  catch (java.io.IOException e) { throw new Error(e); }
		}


		return newClass;
	}

	public static void boxWrap(Type argumentType, MethodVisitor mv) {
		mv.visitMethodInsn(INVOKESTATIC, argumentType.toObject().asmName(), "valueOf",
				"(" + argumentType.bytecodeName() + ")" + argumentType.toObject().bytecodeName());
	}

}
