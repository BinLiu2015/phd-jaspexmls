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

public class InfoClassAdapter extends ClassVisitor {

	protected InfoClass _infoClass;

	public InfoClassAdapter(InfoClass infoClass) {
		this(new EmptyClassVisitor(), infoClass);
	}

	public InfoClassAdapter(ClassVisitor cv, InfoClass infoClass) {
		super(Opcodes.ASM4, cv);
		_infoClass = infoClass;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		for (String s : interfaces) {
			_infoClass.addInterfaceType(Type.fromAsm(s));
		}
		_infoClass.setAccess(access);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		_infoClass.addAnnotation(Type.fromBytecode(desc));
		return cv.visitAnnotation(desc, visible);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		InfoMethod method = new InfoMethod(access, name, desc, signature, exceptions, _infoClass);
		_infoClass.addMethod(method);
		return new InfoMethodVisitor(method);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		InfoField field = new InfoField(access, name, Type.fromBytecode(desc),
			(signature != null ? Type.fromBytecode(signature) : null), value, _infoClass);
		_infoClass.addField(field);
		return null;
	}

	public InfoClass infoClass() {
		return _infoClass;
	}

}
