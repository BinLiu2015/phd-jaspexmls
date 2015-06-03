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

import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

/** Classe que remove MONITORENTER/MONITOREXIT, substituindo-os por um POP da referência do objecto a fazer lock/unlock **/
public class RemoveMonitorsClassVisitor extends MethodVisitor {

	public RemoveMonitorsClassVisitor(int access, String name, String desc, String signature, String[] exceptions, ClassVisitor cv) {
		super(Opcodes.ASM4, cv.visitMethod(access, name, desc, signature, exceptions));
	}

	@Override
	public void visitInsn(int opcode) {
		if (opcode == MONITORENTER || opcode == MONITOREXIT) opcode = POP;
		mv.visitInsn(opcode);
	}

}
