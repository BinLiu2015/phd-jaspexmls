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

import java.lang.reflect.Field;
import java.util.*;

import org.objectweb.asm.Opcodes;

public class InfoField {

	private final int _access;
	private final String _name;
	private final Type _desc;
	private final Type _signature;
	private final Object _value;
	private final List<Type> _annotations = new ArrayList<Type>();
	private final InfoClass _infoClass;

	public InfoField(int access, String name, Type desc, Type signature, Object value, InfoClass infoClass) {
		_access = access;
		_name = name;
		_desc = desc;
		_signature = signature;
		_value = value;
		_infoClass = infoClass;
	}

	public int access()		{ return _access; }
	public String name()		{ return _name; }
	public Type desc()		{ return _desc; }
	public Type signature()		{ return _signature; }
	public Object value()		{ return _value; }
	public InfoClass infoClass()	{ return _infoClass; }

	public void addAnnotation(Type annot) {
		_annotations.add(annot);
	}

	public List<Type> annotations() { return Collections.unmodifiableList(_annotations); }

	@Override
	public String toString() {
		return "InfoField [name=" + name() + ";desc=" + desc()
			+ ";signature=" + signature() + ";value=" + value() + ";annotations="
			+ annotations() + ";class=" + (infoClass() == null ? "null" : infoClass().type()) + "]";
	}

	public boolean hasAnnotation(Type annotClass) {
		return _annotations.contains(annotClass);
	}

	public boolean isFinal() {
		return (access() & Opcodes.ACC_FINAL) != 0;
	}

	public boolean isStatic() {
		return (access() & Opcodes.ACC_STATIC) != 0;
	}

	/** Creates an InfoField based on a java.lang.reflect.Field **/
	public static InfoField fromField(Field refF) {
		int access = refF.getModifiers();
		String name = refF.getName();
		Type desc = Type.fromClass(refF.getType());
		Type signature = null;
		Object value = null;
		InfoClass infoClass = null;
		return new InfoField(access, name, desc, signature, value, infoClass);
	}

}
