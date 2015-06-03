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

/** Quando é feito o inline do código de acesso transaccional a um field pelo ChangeFieldAccessMethodVisitor,
  * é adicionado uma referência a um marcador (jaspex.MARKER.beforeInlinedStore) que é usado pelo
  * DelayGetFutureMethodVisitor.
  *
  * Como o DelayGetFutureMethodVisitor só opera sobre métodos $speculative, temos que remover o marcador
  * das outras versões (ou de todas, quando estamos a usar newspec).
  **/
public class RemoveInlineMarkerMethodVisitor extends MethodVisitor {

	public RemoveInlineMarkerMethodVisitor(int access, String name, String desc,
			String signature, String[] exceptions, ClassVisitor cv) {
		super(Opcodes.ASM4, cv.visitMethod(access, name, desc, signature, exceptions));
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
		if (owner.equals(CommonTypes.MARKER_BEFOREINLINEDSTORE)) return;
		mv.visitMethodInsn(opcode, owner, name, desc);
	}

}
