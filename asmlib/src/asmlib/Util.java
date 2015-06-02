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

import java.io.IOException;
import java.io.PrintWriter;

import org.objectweb.asm.*;
import org.objectweb.asm.util.TraceClassVisitor;

public class Util {

	public static void printClass(byte[] classBytes) {
		ClassReader cr = new ClassReader(classBytes);
		cr.accept(new TraceClassVisitor(new PrintWriter(System.out)), 0);
	}

	public static void populateSuperclasses(InfoClass infoClass) throws IOException {
		if ((infoClass.superclass() != null) || infoClass.type().equals(Type.OBJECT)) return;

		ClassReader cr = new ClassReader(infoClass.superclassType().commonName());

		InfoClass superclass = new InfoClass(cr.getClassName(), cr.getSuperName());
		cr.accept(new InfoClassAdapter(superclass), 0);

		infoClass.setSuperclass(superclass);

		populateSuperclasses(superclass);
	}

	public static void populateSuperinterfaces(InfoClass infoClass) throws IOException {
		for (Type superInterfaceName : infoClass.interfaceTypes()) {
			ClassReader cr = new ClassReader(superInterfaceName.commonName());
			InfoClass superiface = new InfoClass(cr.getClassName(), cr.getSuperName());
			cr.accept(new InfoClassAdapter(superiface), 0);
			infoClass.addInterface(superiface);
			populateSuperinterfaces(superiface);
		}
	}

	/** Generates code needed to swap the two types received, when they are at the top of the stack,
	  * even if one or both of them occupy two slots (are longs or doubles).
	  **/
	public static void swap(MethodVisitor mv, Type stackTop, Type belowTop) {
		if (stackTop.getNumberSlots() == 1) {
			if (belowTop.getNumberSlots() == 1) {
				// Top = 1, below = 1
				mv.visitInsn(Opcodes.SWAP);
			} else {
				// Top = 1, below = 2
				mv.visitInsn(Opcodes.DUP_X2);
				mv.visitInsn(Opcodes.POP);
			}
		} else {
			if (belowTop.getNumberSlots() == 1) {
				// Top = 2, below = 1
				mv.visitInsn(Opcodes.DUP2_X1);
			} else {
				// Top = 2, below = 2
				mv.visitInsn(Opcodes.DUP2_X2);
			}
			mv.visitInsn(Opcodes.POP2);
		}
	}

	/** Generates code needed to swap the two types received, when they are at the top of the stack,
	  * even if one or both of them occupy two slots (are longs or doubles).
	  **/
	public static void swap(MethodVisitor mv, int stackTop, int belowTop) {
		swap(mv, stackTop == 2 ? Type.PRIM_LONG : Type.PRIM_INT,
			 belowTop == 2 ? Type.PRIM_LONG : Type.PRIM_INT);
	}

}
