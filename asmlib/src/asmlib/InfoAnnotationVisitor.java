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

/** FIXME: Triggering the loading of classes by the VM as a side-effect of their analysis with asmlib
  * 	   is a very bad idea. Nevertheless, this code works and the API has users, so it has been
  *	   merged but is disabled by default, pending a proper rewrite to avoid using reflection.
  **/
public class InfoAnnotationVisitor extends AnnotationVisitor {

	private final InfoAnnotation _annotation;
	public static boolean _enableInfoAnnotationVisitor = false;

	public InfoAnnotationVisitor(InfoAnnotation ia) {
		super(Opcodes.ASM4);
		_annotation = ia;
	}

	@Override
	public void visit(String name, Object value) {
		_annotation.addValue(name, value);
	}

	@Override
	public void visitEnum(String name, String desc, String value) {
		if (!_enableInfoAnnotationVisitor) throw new Error("visitEnum not implemented");

		try {
			Class<?> enumClass = Class.forName(Type.fromBytecode(desc).commonName());

			for (Enum<?> enumElem : (Enum[]) enumClass.getEnumConstants()) {
				if (enumElem.name().equals(value)) {
					_annotation.addValue(name, enumElem);
					return;
				}
			}

			throw new AssertionError();
		} catch (Exception e) {
			throw new Error(e);
		}
	}

	@Override
	public void visitEnd() {
		if (!_enableInfoAnnotationVisitor) return;

		Exception inner = null;

		try {
			for (java.lang.reflect.Method m :
				Class.forName(_annotation.type().commonName()).getDeclaredMethods()) {
				if (!_annotation.hasValue(m.getName())) {
					_annotation.addValue(m.getName(), m.getDefaultValue());
				}
			}
			return;
		} catch (SecurityException e)      { inner = e; }
		  catch (ClassNotFoundException e) { inner = e; }
		throw new Error("Error while using reflection to extract default value of annotations", inner);
	}

}
