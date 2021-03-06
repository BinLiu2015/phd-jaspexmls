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

import org.objectweb.asm.*;

/** Replacement of ASM3's EmptyVisitor for ClassVisitors **/
public class EmptyClassVisitor extends ClassVisitor {
	public EmptyClassVisitor() {
		super(Opcodes.ASM4);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		class EmptyAnnotationVisitor extends AnnotationVisitor {
			EmptyAnnotationVisitor() { super(Opcodes.ASM4); }
			@Override public AnnotationVisitor visitAnnotation(String name, String desc) { return this; }
			@Override public AnnotationVisitor visitArray(String name) { return this; }
		}

		return new EmptyAnnotationVisitor();
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		return new EmptyFieldVisitor();
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		return new EmptyMethodVisitor();
	}

}
