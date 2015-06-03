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

import asmlib.Type;

import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;

/** ClassVisitor que adiciona o campo $owner e os métodos $setOwner / $getOwner, usado pelo suporte
  * para -detectlocal, assim como adiciona $owner = Transaction.current() ao constructor dos objectos.
  *
  * A ideia é que o campo $owner mantém a transacção que criou um objecto, e essa transacção
  * pode aceder directamente ao objecto.
  *
  * Esta alteração apenas é feita ao Objecto top-level de uma hierarquia, ou seja ao objecto cuja
  * superclasse é não-transaccional (Object, ou outra coisa).
  *
  * FIXME: De notar que a implementação actual causa o leak dos $owners, já que nunca são limpos.
  **/
public class InjectDetectLocalClassVisitor extends ClassVisitor {

	private boolean _active;
	private String _name;

	public InjectDetectLocalClassVisitor(ClassVisitor cv) {
		super(Opcodes.ASM4, cv);
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		cv.visit(version, access, name, signature, superName, interfaces);
		_active = jaspex.Options.DETECTLOCAL && ((access & ACC_INTERFACE) == 0)
				&& !ClassFilter.isTransactifiable(Type.fromAsm(superName));
		_name = name;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		if (_active && name.equals("<init>") && desc.contains("/SpeculativeCtorMarker")) {
			return new MethodVisitor(Opcodes.ASM4, cv.visitMethod(access, name, desc, signature, exceptions)) {
				@Override public void visitCode() {
					mv.visitCode();
					mv.visitVarInsn(ALOAD, 0);
					mv.visitMethodInsn(INVOKESTATIC, CommonTypes.TRANSACTION.asmName(),
								"getOwnerTag", "()" + Type.OBJECT.bytecodeName());
					mv.visitFieldInsn(PUTFIELD, _name, "$owner",Type.OBJECT.bytecodeName());
				}
			};
		} else {
			return cv.visitMethod(access, name, desc, signature, exceptions);
		}
	}

	@Override
	public void visitEnd() {
		if (_active) {
			cv.visitField(ACC_PUBLIC, "$owner", Type.OBJECT.bytecodeName(), null, null);

			{
				MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "$setOwner", "(Ljava/lang/Object;)V", null, null);
				mv.visitCode();
				mv.visitVarInsn(ALOAD, 0);
				mv.visitVarInsn(ALOAD, 1);
				mv.visitFieldInsn(PUTFIELD, _name, "$owner", Type.OBJECT.bytecodeName());
				mv.visitInsn(RETURN);
				mv.visitMaxs(0, 0);
				mv.visitEnd();
			}
			{
				MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "$getOwner", "()Ljava/lang/Object;", null, null);
				mv.visitCode();
				mv.visitVarInsn(ALOAD, 0);
				mv.visitFieldInsn(GETFIELD, _name, "$owner", Type.OBJECT.bytecodeName());
				mv.visitInsn(ARETURN);
				mv.visitMaxs(1, 1);
				mv.visitEnd();
			}
		}

		cv.visitEnd();
	}

}
