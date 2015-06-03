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

import java.io.IOException;

import jaspex.ClassFilter;

import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

import asmlib.*;
import asmlib.Type;

/** ClassVisitor que adiciona toString() e hashCode() a todas as classes "top-level"
  * da hierarquia (que sejam subclasses de Object ou de classes não-transaccionais),
  * para que estes métodos possam ser chamados transaccionalmente.
  **/
public class AddObjectMethodsClassVisitor extends ClassVisitor {

	private boolean _injectToString = false;
	private boolean _injectHashCode = false;

	public AddObjectMethodsClassVisitor(ClassVisitor cv, InfoClass currentClass) {
		super(Opcodes.ASM4, cv);

		if (currentClass.isInterface()) return;

		if (currentClass.superclassType().equals(Type.OBJECT)) {
			_injectHashCode = true;
			_injectToString = true;
			return;
		}

		// Verificar se superclasse não-transactificável tem toString/hashCode
		// Se não tiver, esta classe deverá inserir copias dele, c.c. serão mais
		// tarde adicionados overrides pelo InjectOverloadsClassVisitor
		if (!ClassFilter.isTransactifiable(currentClass.superclassType())) {
			// Popular superclasses, se necessário
			if (currentClass.superclass() == null) {
				try {
					asmlib.Util.populateSuperclasses(currentClass);
				} catch (IOException e) {
					throw new Error(e);
				}
			}

			InfoClass superclass = currentClass.superclass();
			boolean foundToString = false;
			boolean foundHashCode = false;
			while (!superclass.type().equals(Type.OBJECT)) {
				if (!foundToString) {
					foundToString =
						superclass.getMethod("toString", "()Ljava/lang/String;") != null;
				}
				if (!foundHashCode) {
					foundHashCode =
						superclass.getMethod("hashCode", "()I") != null;
				}
				superclass = superclass.superclass();
			}

			_injectToString = !foundToString;
			_injectHashCode = !foundHashCode;
		}
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		String fullName = name + desc;
		if (fullName.equals("toString()Ljava/lang/String;")) _injectToString = false;
		if (fullName.equals("hashCode()I")) _injectHashCode = false;
		return cv.visitMethod(access, name, desc, signature, exceptions);
	}

	@Override
	public void visitEnd() {
		if (_injectToString) {
			// toString completamente transaccional usando o jaspex.Builtin.StringBuilder_*
			// Funciona porque apenas o hashCode() pode causar especulações, e portanto não
			// pode haver concorrência no acesso ao StringBuilder, já que será sempre a mesma
			// thread/task a executar o código que se segue à chamada do hashCode
			/*
			@Override
			public String toString() {
				String hashCode = Integer.toHexString(hashCode());
				Object stringBuilder = jaspex.Builtin.StringBuilder_new();
				jaspex.Builtin.StringBuilder_append(stringBuilder, getClass().getName());
				jaspex.Builtin.StringBuilder_append(stringBuilder, '@');
				jaspex.Builtin.StringBuilder_append(stringBuilder, hashCode);
				return jaspex.Builtin.StringBuilder_toString(stringBuilder);
			}
			 */
			MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null);
			mv.visitCode();
			mv.visitVarInsn(ALOAD, 0);
			// Chamada depois é corrigida pelo ChangeObjectMethodsMethodVisitor
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "hashCode", "()I");
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "toHexString", "(I)Ljava/lang/String;");
			mv.visitVarInsn(ASTORE, 1);
			mv.visitMethodInsn(INVOKESTATIC, "jaspex/Builtin", "StringBuilder_new", "()Ljava/lang/Object;");
			mv.visitVarInsn(ASTORE, 2);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;");
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;");
			mv.visitMethodInsn(INVOKESTATIC, "jaspex/Builtin", "StringBuilder_append", "(Ljava/lang/Object;Ljava/lang/String;)V");
			mv.visitVarInsn(ALOAD, 2);
			mv.visitIntInsn(BIPUSH, '@');
			mv.visitMethodInsn(INVOKESTATIC, "jaspex/Builtin", "StringBuilder_append", "(Ljava/lang/Object;C)V");
			mv.visitVarInsn(ALOAD, 2);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitMethodInsn(INVOKESTATIC, "jaspex/Builtin", "StringBuilder_append", "(Ljava/lang/Object;Ljava/lang/String;)V");
			mv.visitVarInsn(ALOAD, 2);
			mv.visitMethodInsn(INVOKESTATIC, "jaspex/Builtin", "StringBuilder_toString", "(Ljava/lang/Object;)Ljava/lang/String;");
			mv.visitInsn(ARETURN);
			mv.visitMaxs(2, 3);
			mv.visitEnd();
		}

		if (_injectHashCode) {
			MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "hashCode", "()I", null, null);
			mv.visitCode();
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "identityHashCode", "(Ljava/lang/Object;)I");
			mv.visitInsn(IRETURN);
			mv.visitMaxs(1, 1);
			mv.visitEnd();
		}

		cv.visitEnd();
	}

}
