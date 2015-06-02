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

import org.objectweb.asm.*;

/** MethodVisitor that fixes methods that cause a ClassFormatError by having the same
  * Label as start and end of a try catch block.
  *
  * (To obtain a ClassVisitor, combine it with a GenericMethodVisitorAdapter)
  **/
public class FixDeadTryCatchBlockMethodVisitor extends MethodVisitor {

	public FixDeadTryCatchBlockMethodVisitor(int access, String name, String desc, String signature,
		String[] exceptions, ClassVisitor cv) {
		super(Opcodes.ASM4, cv.visitMethod(access, name, desc, signature, exceptions));
	}

	@Override
	public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
		if (!start.equals(end)) super.visitTryCatchBlock(start, end, handler, type);
	}

}
