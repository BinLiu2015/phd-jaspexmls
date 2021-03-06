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

import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

/** MethodVisitor que remove os NOPs de um método (ver também NopAddBeforeLabelMethodVisitor) **/
public class NopRemoveMethodVisitor extends MethodVisitor {

	public NopRemoveMethodVisitor(int access, String name, String desc,
			String signature, String[] exceptions, ClassVisitor cv) {
		super(Opcodes.ASM4, cv.visitMethod(access, name, desc, signature, exceptions));
	}

	@Override
	public void visitInsn(int opcode) {
		if (opcode != NOP) mv.visitInsn(opcode);
	}

}
