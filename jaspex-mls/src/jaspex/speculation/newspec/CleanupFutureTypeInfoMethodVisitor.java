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

package jaspex.speculation.newspec;

import jaspex.speculation.CommonTypes;

import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;

import static jaspex.speculation.CommonTypes.FUTURE;

/** MethodVisitor que remove a informação a mais passada dentro do tipo dos Futuros inseridos no código.
  * Ver também comentários no InsertContinuationSpeculationMethodVisitor.visitMethodInsn().
  **/
public class CleanupFutureTypeInfoMethodVisitor extends MethodVisitor {

	public CleanupFutureTypeInfoMethodVisitor(int access, String name, String desc,
			String signature, String[] exceptions, ClassVisitor cv) {
		super(Opcodes.ASM4, cv.visitMethod(access, name, desc, signature, exceptions));
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
		if (opcode == INVOKESTATIC &&
			owner.equals(CommonTypes.CONTSPECULATIONCONTROL.asmName()) &&
			name.equals("spawnSpeculation")) {
			desc = desc.substring(0, desc.indexOf(')') + 1) + FUTURE.bytecodeName();
		} else if (opcode == INVOKEINTERFACE && owner.startsWith(FUTURE.asmName())) {
			owner = FUTURE.asmName();
		}
		mv.visitMethodInsn(opcode, owner, name, desc);
	}

}
