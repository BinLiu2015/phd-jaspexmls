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

import jaspex.Options;
import asmlib.Type;

import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Classe que verifica se métodos estão marcados como ACC_SYNCHRONIZED ou usam MONITORENTER/MONITOREXIT **/
public class CheckMonitorUsage extends ClassVisitor {

	private static final Logger Log = LoggerFactory.getLogger(CheckMonitorUsage.class);

	private Type _type;

	private boolean _foundMonitors;
	private boolean _foundSynchronized;

	public CheckMonitorUsage(ClassVisitor cv) {
		super(Opcodes.ASM4, cv);
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		_type = Type.fromAsm(name);

		cv.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		if (!Options.REMOVESYNC && ((access & ACC_SYNCHRONIZED) != 0)) _foundSynchronized = true;

		return new MethodVisitor(Opcodes.ASM4, cv.visitMethod(access, name, desc, signature, exceptions)) {
			@Override
			public void visitInsn(int opcode) {
				if (!Options.REMOVEMONITORS && (opcode == MONITORENTER || opcode == MONITOREXIT)) {
					_foundMonitors = true;
				}
				mv.visitInsn(opcode);
			}
		};
	}

	@Override
	public void visitEnd() {
		if (_foundMonitors) Log.warn("Class {} uses monitors", _type.commonName());
		if (_foundSynchronized) Log.warn("Class {} uses synchronizeds", _type.commonName());
		cv.visitEnd();
	}

}
