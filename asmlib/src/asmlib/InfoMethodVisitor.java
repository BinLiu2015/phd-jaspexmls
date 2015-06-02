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

public class InfoMethodVisitor extends MethodVisitor {

	protected final InfoMethod _method;

	public InfoMethodVisitor(InfoMethod method) {
		super(Opcodes.ASM4);
		_method = method;
	}

	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		Type ownerClass = Type.fromAsm(owner);
		if (!ownerClass.equals(_method.infoClass().type())) {
			// Método acede a fields de outras classes!
			_method.setAccessesOutsideFields(true);
		}
	}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		InfoAnnotation ia = new InfoAnnotation(Type.fromBytecode(desc), visible);
		_method.addAnnotation(ia);
		return new InfoAnnotationVisitor(ia);
	}

	@Override
	public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
		InfoAnnotation ia = new InfoAnnotation(parameter, Type.fromBytecode(desc), visible);
		_method.addAnnotation(ia);
		return new InfoAnnotationVisitor(ia);
	}

	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		_method.setMaxLocals(maxLocals);
	}

}
