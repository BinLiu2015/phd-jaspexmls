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

import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.*;

public class InfoClass implements Comparable<InfoClass> {

	private final Type _type;
	private final Type _superclassType;
	private final List<Type> _annotations = new ArrayList<Type>();
	private final List<InfoField> _fields = new ArrayList<InfoField>();
	private final List<InfoMethod> _methods = new ArrayList<InfoMethod>();
	private List<InfoMethod> _superMethods = null;
	private final List<Type> _interfaceTypes = new ArrayList<Type>();
	private final List<InfoClass> _interfaces = new ArrayList<InfoClass>();
	private final List<InfoClass> _subclasses = new ArrayList<InfoClass>();
	private InfoClass _superclass;
	private int _access = -1;

	public InfoClass(Type type, Type superclassType) {
		_type = type;
		_superclassType = superclassType;
	}

	// Auxiliary constructor to avoid clients having to test that superclass != null
	public InfoClass(String classStr, String superclassStr) {
		this(Type.fromAsm(classStr),
			((superclassStr != null) ? Type.fromAsm(superclassStr) : null));
	}

	public Type type()		{ return _type; }
	public Type superclassType()	{ return _superclassType; }
	public InfoClass superclass()	{ return _superclass; }

	public void setSuperclass(InfoClass superclass)	{
		_superclass = superclass;
		_superclass.addSubclass(this);
	}

	public void addAnnotation(Type annot) {
		_annotations.add(annot);
	}

	public List<Type> annotations() { return new ArrayList<Type>(_annotations); }

	public void addInterfaceType(Type iface) {
		_interfaceTypes.add(iface);
	}

	public void addInterface(InfoClass iface) {
		if (!_interfaceTypes.contains(iface.type())) {
			throw new InstrumentationException("Attempt to add an interface which is not on the interfaceTypes() list");
		}

		_interfaces.add(iface);
		iface.addSubclass(this);
	}

	public void setAccess(int access) {
		if (_access < 0) _access = access;
		else throw new InstrumentationException("Cannot change access after setting it the first time");
	}

	public int access() {
		if (_access < 0) throw new InstrumentationException("Access value not set yet");
		return _access;
	}

	public boolean isFinal() {
		return (access() & Opcodes.ACC_FINAL) != 0;
	}

	public boolean isInterface() {
		return (access() & Opcodes.ACC_INTERFACE) != 0;
	}

	public void addField(InfoField field) {
		_fields.add(field);
	}

	public List<InfoField> fields() { return new ArrayList<InfoField>(_fields); }

	public boolean existsField(String fieldName) {
		for (InfoField f : fields()) {
			if (f.name().equals(fieldName)) return true;
		}
		return false;
	}

	public void addMethod(InfoMethod method) {
		_methods.add(method);
	}

	public List<InfoMethod> methods() { return new ArrayList<InfoMethod>(_methods); }
	public List<Type> interfaceTypes() { return new ArrayList<Type>(_interfaceTypes); }
	public List<InfoClass> interfaces() { return new ArrayList<InfoClass>(_interfaces); }

	public List<InfoMethod> allMethods() {
		List<InfoMethod> methods = methods();
		if (_superMethods == null) {
			_superMethods = new ArrayList<InfoMethod>();
			if (_superclass != null) {
				for (InfoMethod m : _superclass.allMethods()) _superMethods.add(new InfoMethod(m, this));
			}
			for (InfoClass iface : _interfaces) {
				for (InfoMethod m : iface.allMethods()) _superMethods.add(new InfoMethod(m, this));
			}
		}
		methods.addAll(_superMethods);
		return methods;
	}

	public InfoMethod getMethod(String methodName, String desc) {
		for (InfoMethod m : methods()) {
			if (m.name().equals(methodName) && m.desc().equals(desc)) return m;
		}
		return null;
	}

	public InfoMethod getAllMethod(String methodName, String desc) {
		for (InfoMethod m : allMethods()) {
			if (m.name().equals(methodName) && m.desc().equals(desc)) return m;
		}
		return null;
	}

	public InfoField getField(String fieldName, Type desc) {
		for (InfoField f : fields()) {
			if (f.name().equals(fieldName) && f.desc().equals(desc)) return f;
		}
		return null;
	}

	public InfoField getAllField(String fieldName, Type desc) {
	        InfoField f = getField(fieldName, desc);
	        if (f != null) return f;

	        // The order used in the VM to find fields is current class -> interfaces -> superclasses;
	        // This is important whenever fields are being overridden
	        for (InfoClass iface : _interfaces) {
			f = iface.getAllField(fieldName, desc);
			if (f != null) return f;
	        }

	        if (_superclass != null) return _superclass.getAllField(fieldName, desc);
	        return null;
	}

	@Override
	public String toString() {
		return "InfoClass [name=" + type() + /*";annotations=" + annotations() +*/ "]";
	}

	/** Tests if class has an annotation of the given annotation type **/
	public boolean hasAnnotation(Type annot) {
		return _annotations.contains(annot);
	}

	/** Tests if class implements the given inferface type **/
	public boolean hasInterfaceType(Type iface) {
		return _interfaceTypes.contains(iface);
	}

	public void addSubclass(InfoClass subclass) {
		_subclasses.add(subclass);
	}

	public List<InfoClass> subclasses() { return new ArrayList<InfoClass>(_subclasses); }

	@Override
	public int hashCode() {
		return type().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof InfoClass) {
			InfoClass other = (InfoClass)o;
			return compareTo(other) == 0;
		}
		return false;
	}

	public int compareTo(InfoClass other) {
		return type().compareTo(other.type());
	}

	public static InfoClass fromType(Type t) throws IOException {
		org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(t.commonName());
		InfoClass infoClass = new InfoClass(cr.getClassName(), cr.getSuperName());
		cr.accept(new InfoClassAdapter(infoClass), 0);
		return infoClass;
	}

}
