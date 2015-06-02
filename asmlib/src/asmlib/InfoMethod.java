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

import java.util.*;
import org.objectweb.asm.Opcodes;

import util.StringList;
import util.UtilList;
import asmlib.extra.InvokedMethod;

public class InfoMethod implements Comparable<InfoMethod> {

	/** Comparador que ignora a classe a qual o método pertence **/
	public final static class InfoMethodNameDescOnlyComparator implements Comparator<InfoMethod> {
		@Override
		public int compare(InfoMethod m1, InfoMethod m2) {
			return (m1.name() + m1.desc()).compareTo(m2.name() + m2.desc());
		}
	}

	private final int _access;
	private final String _name;
	private final String _desc;
	private final String _signature;
	private final String[] _exceptions;
	private final List<InfoAnnotation> _annotations = new ArrayList<InfoAnnotation>();
	private final InfoClass _definedInfoClass;	// Classe onde o método está realmente definido
	private InfoClass _infoClass;		// Classe a partir de onde o método está a ser visto
	private boolean _accessesOutsideFields = false;

	private int _maxLocals;

	public InfoMethod(int access, String name, String desc, String signature, String[] exceptions, InfoClass infoClass) {
		_access = access;
		_name = name;
		_desc = desc;
		_signature = signature;
		_exceptions = exceptions;
		_definedInfoClass = infoClass;
		_infoClass = infoClass;
	}

	public InfoMethod(InfoMethod other, InfoClass infoClass) {
		this(other.access(), other.name(), other.desc(), other.signature(), other.exceptions(), other.infoClass());
		_infoClass = infoClass;
		addInvokedMethod(new InvokedMethod(other.infoClass().type(), name(), desc(), other));
	}

	public int access()		{ return _access; }
	public String name()		{ return _name; }
	public String fullName()	{ return infoClass().type() + "." + name() + desc(); }
	public String desc()		{ return _desc; }
	public String signature()	{ return _signature; }
	public String[] exceptions()	{ return _exceptions; }
	public InfoClass definedInfoClass() { return _definedInfoClass; }
	public InfoClass infoClass()	{ return _infoClass; }

	public String fullJavaName()	{
		Type t = returnType();
		return (t.isPrimitive() ? t.primitiveTypeName() : t.commonName()) +
			" " + infoClass().type() + "." + name() + javaDesc();
	}

	public int maxLocals()		{ return _maxLocals; }
	public void setMaxLocals(int maxLocals)	{ _maxLocals = maxLocals; }

	public void addAnnotation(InfoAnnotation annot) {
		_annotations.add(annot);
	}

	public List<InfoAnnotation> annotations() { return Collections.unmodifiableList(_annotations); }

	public boolean hasAnnotation(Type annotClass) {
		return _annotations.contains(new InfoAnnotation(annotClass));
	}

	public InfoAnnotation getAnnotation(Type annotClass) {
		InfoAnnotation target = new InfoAnnotation(annotClass);
		for (InfoAnnotation annot : _annotations) {
			if (annot.equals(target)) return annot;
		}
		return null;
	}

	@Override
	public String toString() {
		return "InfoMethod (name=" + name() + " desc=" + desc()
			/*+ " signature=" + signature()*/ /*+ " annotations="
			+ annotations()*/ + " class=" + (infoClass() != null ? infoClass().type() : null)
			+ " native=" + isNative() + ")";
	}

	public void setAccessesOutsideFields(boolean b) {
		_accessesOutsideFields = b;
	}

	public boolean accessesOutsideFields() { return _accessesOutsideFields; }

	public boolean isNative() {
		return (access() & Opcodes.ACC_NATIVE) != 0;
	}

	public boolean isAbstract() {
		return (access() & Opcodes.ACC_ABSTRACT) != 0;
	}

	public boolean isFinal() {
		// Método é final se for declarado final, ou se a classe onde está declarado for final
		return ((access() & Opcodes.ACC_FINAL) != 0) || infoClass().isFinal();
	}

	public boolean isPrivate() {
		return (access() & Opcodes.ACC_PRIVATE) != 0;
	}

	public boolean isSynchronized() {
		return (access() & Opcodes.ACC_SYNCHRONIZED) != 0;
	}

	@Override
	public int hashCode() {
		return (infoClass().type() + name() + desc()).hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof InfoMethod) {
			InfoMethod other = (InfoMethod)o;
			return compareTo(other) == 0;
		}
		return false;
	}

	public int compareTo(InfoMethod other) {
		return (infoClass().type() + "." + name() + desc()).compareTo(
			other.infoClass().type() + "." + other.name() + other.desc());
	}

	private Set<InvokedMethod> _invokedMethods = new HashSet<InvokedMethod>();
	private List<InvokedMethod> _invokedMethodsList = new ArrayList<InvokedMethod>();

	public void addInvokedMethod(InvokedMethod m) {
		_invokedMethods.add(m);
		_invokedMethodsList.add(m);
	}

	public List<InvokedMethod> invokedMethodsSet() { return new ArrayList<InvokedMethod>(_invokedMethods); }
	public List<InvokedMethod> invokedMethodsList() {
		return new ArrayList<InvokedMethod>(_invokedMethodsList);
	}

	/** Se uma UnwindException está a ser leaked para o exterior, significa que há bug no método chamado
	  * pois estas nunca devem sair desta classe, portanto esta excepção serve apenas para detectar bugs
	  * no InfoMethod, não algum erro da parte de quem o chama.
	  **/
	static class UnwindException extends Error {
		private static final long serialVersionUID = 1L;
	}

	private boolean _invokesNativeWorking = false;

	/** ATENÇÃO: TOTALMENTE NÃO THREAD-SAFE. **/
	// Nota: Este método não tem em conta subclasses (overrides), logo invokesNative == false não
	// implica que uma chamada pelo método não seja native, porque pode ir parar a uma subclasse.
	// Ver canInvokeNative()
	public boolean invokesNative() {
		if (isNative()) return true;

		// Ter cuidado com isto, porque pode causar ciclos
		if (_invokesNativeWorking) throw new UnwindException();
		_invokesNativeWorking = true;

		for (InvokedMethod im : _invokedMethods) {
			try {
			if (im.method().invokesNative()) { _invokesNativeWorking = false; return true; }
			} catch (UnwindException e) { }
		}
		_invokesNativeWorking = false;
		return false;
	}

	/** ATENÇÃO: TOTALMENTE NÃO THREAD-SAFE. **/
	// Considera também subclasses que estejam no reachableMap, ou todas as subclasses caso
	// reachableMap esteja a null, na sua resposta.
	public boolean canInvokeNative(Map<Type, InfoClass> reachableMap) {
		if (isNative()) return true;

		// Ter cuidado com isto, porque pode causar ciclos
		if (_invokesNativeWorking) throw new UnwindException();
		_invokesNativeWorking = true;

		for (InvokedMethod im : _invokedMethods) try {
			if (im.method().canInvokeNative(reachableMap)) { _invokesNativeWorking = false; return true; }
		} catch (UnwindException e) { }

		// Constructores não podem ser overridden
		if (name().equals("<init>")) { _invokesNativeWorking = false; return false; }

		for (InfoMethod m : subclassOverrides(reachableMap)) try {
			if (m.canInvokeNative(reachableMap)) { _invokesNativeWorking = false; return true; }
		} catch (UnwindException e) { }

		_invokesNativeWorking = false;
		return false;
	}

	public boolean isInherited() { return !infoClass().equals(definedInfoClass()); }

	public InvokedMethod getParentMethod() {
		if (isInherited()) {
			return _invokedMethods.iterator().next();
		}
		throw new InstrumentationException("getParentMethod() should not be called on a method where isInherited() is false");
	}

	public boolean isLeaf() {
		// Método é leaf se não invocar métodos ou se só se invocar a si mesmo recursivamente
		if (_invokedMethods.size() == 0) return true;
		if (_invokedMethods.size() == 1) {
			InvokedMethod m = _invokedMethods.iterator().next();
			return (m.owner().equals(infoClass().type())
				&& m.name().equals(name())
				&& m.desc().equals(desc()));
		}
		return false;
	}

	public List<InfoMethod> subclassOverrides(Map<Type, InfoClass> reachableMap) {
		// Constructores não podem ser overridden
		if (name().equals("<init>")) return new ArrayList<InfoMethod>();
		// Métodos finais não podem ser overridden
		if (isFinal()) return new ArrayList<InfoMethod>();

		NavigableSet<InfoClass> processingSet = new TreeSet<InfoClass>(infoClass().subclasses());
		List<InfoMethod> lst = new ArrayList<InfoMethod>();
		while (!processingSet.isEmpty()) {
			InfoClass ic = processingSet.pollFirst();
			if ((reachableMap == null) || reachableMap.containsKey(ic.type())) {
				InfoMethod m = ic.getMethod(name(), desc());
				if (m != null) lst.add(m);
				processingSet.addAll(ic.subclasses());
			}
		}
		return lst;
	}

	public Type returnType() {
		return Type.fromBytecode(desc().substring(desc().indexOf(")") + 1));
	}

	public Type genericReturnType() {
		if (signature() == null) return null;
		return Type.fromBytecode(signature().substring(signature().indexOf(")") + 1));
	}

	public UtilList<Type> argumentTypes() {
		return Type.getArgumentTypes(desc());
	}

	public UtilList<Type> genericArgumentTypes() {
		if (signature() == null) return null;
		return Type.getArgumentTypes(signature());
	}

	public String genericConstraints() {
		if (signature() == null) return null;
		return signature().substring(0, signature().indexOf("("));
	}

	public String javaDesc() {
		String output = " (";
		StringList sl = new StringList();
		for (Type t : argumentTypes()) {
			sl.add(t.isPrimitive() ? t.primitiveTypeName() : t.commonName());
		}
		output += sl.join(", ") + ")";
		return output;
	}

	public boolean isStatic() {
		return (access() & Opcodes.ACC_STATIC) != 0;
	}

	public boolean isCtor() {
		return (name().equals("<init>"));
	}

	public boolean isStaticCtor() {
		return (name().equals("<clinit>"));
	}

}
