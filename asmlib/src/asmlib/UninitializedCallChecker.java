/*
 * asmlib: a toolkit based on ASM for working with java bytecode
 * Copyright (C) 2015 Ivo Anjo <ivo.anjo@ist.utl.pt>
 *
 * This file is part of asmlib.
 *
 * asmlib is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * asmlib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with asmlib.  If not, see <http://www.gnu.org/licenses/>.
 */

package asmlib;

import java.util.*;
import java.io.*;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.AnalyzerAdapter;

/** Verificador que procura invocações de métodos que são feitas sobre instâncias ainda incompletas
  * de objectos (instâncias ainda não completamente contruídas).
  **/

public class UninitializedCallChecker extends MethodVisitor implements Opcodes {

	private AnalyzerAdapter _analyzerAdapter;
	private PrintWriter _pw;
	private String _name;
	private String _desc;

	public UninitializedCallChecker(int access, String name, String desc, String signature,
		String[] exceptions, ClassVisitor cv, String className, PrintWriter pw) {
		super(Opcodes.ASM4, new AnalyzerAdapter(className, access, name, desc,
			cv.visitMethod(access, name, desc, signature, exceptions)));
		_analyzerAdapter = (AnalyzerAdapter) mv;
		_pw = pw;
		_name = name;
		_desc = desc;
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
		if (opcode == Opcodes.INVOKESTATIC) {
			mv.visitMethodInsn(opcode, owner, name, desc);
			return;
		}

		// Contar numero slots na stack utilizados
		// Não basta tirar o size() do args porque alguns argumentos (doubles e longs)
		// utilizam dois slots na stack
		List<Type> args = new InfoMethod(0, null, desc, null, null, null).argumentTypes();
		int numArgs = 0;
		for (Type t : args) numArgs += t.getNumberSlots();

		Object typeInStack = _analyzerAdapter.stack.get(_analyzerAdapter.stack.size() - numArgs - 1);

		if (typeInStack instanceof Integer) {
			if (typeInStack.equals(NULL)) {
				_pw.println("WARNING (" + _name + _desc + "): Method call using null " +
						"reference -- this is always going to result in a NPE");
			} else if (!typeInStack.equals(UNINITIALIZED_THIS)) {
				throw new AssertionError("Unexpected type found on stack (" + _name + _desc + ")");
			} else if (!name.equals("<init>")) {
				_pw.println("CALL USING UNINITIALIZED OBJECT REFERENCE DETECTED: " + _name + _desc);
			}
		}

		mv.visitMethodInsn(opcode, owner, name, desc);
	}

	public static void verify(ClassReader cr, PrintWriter pw) {
		// O AnalyzerAdapter
		// precisa que as frames estejam delimitadas no código para funcionar correctamente.
		// Frames parecem ser obrigatórias a partir do Java 6, mas muitas classes não as têm
		// por isso fazemos mais um pass com um ClassWriter, para obtermos uma classe com
		// as frames calculadas.
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		cr.accept(cw, 0);
		cr = new ClassReader(cw.toByteArray());

		try {
			cr.accept(new GenericMethodVisitorAdapter(new EmptyClassVisitor(),
				UninitializedCallChecker.class, cr.getClassName(), pw), ClassReader.EXPAND_FRAMES);
		} catch (ArrayIndexOutOfBoundsException e) { }
	}

}
