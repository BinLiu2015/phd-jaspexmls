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

package asmlib.extra;

import java.util.ArrayList;

import org.objectweb.asm.*;

/** MethodVisitor that removes unused TryCatchBlocks.
  * Currently it only considers as unused a TryCatchBlock with the same start and end as an earlier
  * (and thus higher priority) block, and having the same type.
  *
  * Correction issues due to incompleteness are not an issue here, because either the JVM will
  * accurately remove other TryCatchBlocks when the class is loaded, or it will signal an error.
  *
  * (To obtain a ClassVisitor, combine it with a GenericMethodVisitorAdapter)
  **/
public class RemoveUnusedTryCatchBlockMethodVisitor extends MethodVisitor {

	private static class TryCatchBlock {
		final Label _start;
		final Label _end;
		final String _type;

		TryCatchBlock(Label start, Label end, String type) {
			_start = start;
			_end = end;
			_type = (type != null) ? type : "java/lang/Throwable";
		}

		@Override
		public boolean equals(Object other) {
			if (!(other instanceof TryCatchBlock)) throw new IllegalArgumentException();
			TryCatchBlock o = (TryCatchBlock) other;
			return (_start == o._start) && (_end == o._end) && (_type.equals(o._type));
		}
	}

	private final ArrayList<TryCatchBlock> _tryCatchBlockList = new ArrayList<TryCatchBlock>();

	public RemoveUnusedTryCatchBlockMethodVisitor(int access, String name, String desc, String signature,
		String[] exceptions, ClassVisitor cv) {
		super(Opcodes.ASM4, cv.visitMethod(access, name, desc, signature, exceptions));
	}

	@Override
	public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
		TryCatchBlock currentBlock = new TryCatchBlock(start, end, type);

		for (TryCatchBlock tcb : _tryCatchBlockList) if (tcb.equals(currentBlock)) return;

		_tryCatchBlockList.add(currentBlock);
		super.visitTryCatchBlock(start, end, handler, type);
	}

}
