/*
 * jaspex-mls: a Java Software Speculative Parallelization Framework
 * Copyright (C) 2015 Ivo Anjo <ivo.anjo@ist.utl.pt>
 *
 * This file is part of jaspex-mls.
 *
 * jaspex-mls is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * jaspex-mls is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with jaspex-mls.  If not, see <http://www.gnu.org/licenses/>.
 */

package jaspex.speculation.newspec;

import jaspex.speculation.CommonTypes;
import jaspex.speculation.runtime.CodegenHelper;

import java.io.IOException;
import java.util.*;

import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asmlib.Type;

/** Classe que mantém uma representação da hierarquia de classes do programa **/
public class ClassHierarchy {

	@SuppressWarnings("unused")
	private static final Logger Log = LoggerFactory.getLogger(ClassHierarchy.class);

	private static HashMap<Type, TypeInfo> _typeInfoMap = new HashMap<Type, TypeInfo>();

	private static class TypeInfo {
		final Type _type;
		final Type _superclass;
		final Type[] _interfaces;
		final int _access;

		TypeInfo(Type type) {
			_type = type;

			ClassReader cr = null;
			try {
				cr = new ClassReader(_type.commonName());
			} catch (IOException e) {
				throw new RuntimeException("Error loading " + type.commonName(), e);
			}

			_superclass = cr.getSuperName() == null ? null : Type.fromAsm(cr.getSuperName());

			String[] interfaces = cr.getInterfaces();
			if (interfaces != null) {
				_interfaces = new Type[interfaces.length];
				for (int i = 0; i < interfaces.length; i++) {
					_interfaces[i] = Type.fromAsm(interfaces[i]);
				}
			} else {
				_interfaces = null;
			}

			_access = cr.getAccess();

			_typeInfoMap.put(type, this);
		}
	}

	private static TypeInfo getTypeInfo(Type type) {
		TypeInfo info = _typeInfoMap.get(type);
		return info != null ? info : new TypeInfo(type);
	}

	public static boolean isAssignableFrom(org.objectweb.asm.Type t, org.objectweb.asm.Type u) {
		return isAssignableFrom(Type.fromType(t), Type.fromType(u));
	}

	/** Semelhante ao Class.isAssignableFrom(), direcção tipo <- subtipo
	  * isAssignableFrom(Object, Integer) == true
	  * isAssignableFrom(Integer, Object) == false
	  **/
	public static boolean isAssignableFrom(Type type, Type subType) {
		// Caso mais simples
		if (type.equals(subType)) return true;

		// Handling tipos primitivos
		if (type.isPrimitive()) {
			// Não tenho de toda a certeza disto; Como o ClassHierarchy está a correr em
			// dueto com o isAssignableFrom do ASM, isto parece ser assim.
			return false;
		} else if (subType.isPrimitive()) return false;

		// Arrays
		if (subType.isArray()) {
			// Qualquer array é um objecto
			if (type.equals(Type.OBJECT)) return true;
			if (type.isArray()) {
				// Caso especial: arrays de tipos nativos só podem ser atribuidos a Object[] se
				// tiverem mais uma dimensão que o Object[].
				// Ou seja:
				// Object[] x = new int[0]; // Não permitido
				// Object[][] x = new int[0]; // Não permitido
				// Object[] x = new int[0][0]; // OK
				// Object[][] x = new int[0][0][0]; // OK
				if (type.stripArray().equals(Type.OBJECT) && subType.stripArray().isPrimitive()
					&& (type.arrayDimensions() >= subType.arrayDimensions())) {
					return false;
				}
				// Se o tipo original for object, o subtipo é sempre aceite, desde
				// que tenha número de dimensões >= que o original
				if (type.stripArray().equals(Type.OBJECT) &&
					(subType.arrayDimensions() >= type.arrayDimensions())) {
					return true;
				}
				// Se têm o mesmo número de dimensões
				if (type.arrayDimensions() == subType.arrayDimensions()) {
					return isAssignableFrom(type.stripArray(), subType.stripArray());
				}
			}
			return false;
		}

		// Codegen
		if (CodegenHelper.isCodegenClass(subType)) {
			return type.equals(Type.OBJECT) || type.equals(CommonTypes.CALLABLE);
		}

		type = simplifyIfFuture(type);
		subType = simplifyIfFuture(subType);

		TypeInfo info = getTypeInfo(subType);

		return checkHierarchy(type, info);
	}

	// Determina se type é algum supertipo ou interface da classe descrita em info
	private static boolean checkHierarchy(Type type, TypeInfo info) {
		if (type.equals(info._type)) return true;
		if (info._type.equals(Type.OBJECT)) return false;

		// Testar interfaces
		for (Type iface : info._interfaces) {
			// Directas
			if (type.equals(iface)) return true;
			// Recursivamente verificar também superinterfaces
			if (checkHierarchy(type, getTypeInfo(iface))) return true;
		}

		return checkHierarchy(type, getTypeInfo(info._superclass));
	}

	public static boolean isInterface(org.objectweb.asm.Type type) {
		return isInterface(Type.fromType(type));
	}

	public static boolean isInterface(Type type) {
		if (type.isArray()) return false;
		return (getTypeInfo(type)._access & Opcodes.ACC_INTERFACE) != 0;
	}

	public static org.objectweb.asm.Type getSuperclass(org.objectweb.asm.Type type) {
		return getSuperclass(Type.fromType(type)).toType();
	}

	public static Type getSuperclass(Type type) {
		if (type.isArray()) return Type.OBJECT;
		return getTypeInfo(type)._superclass;
	}

	private static Set<Type> getInterfaces(Type type) {
		if (type.equals(Type.OBJECT)) return Collections.emptySet();

		TypeInfo info = getTypeInfo(type);
		Set<Type> interfaces = null;

		if (info._interfaces != null) {
			interfaces = new HashSet<Type>(Arrays.asList(info._interfaces));

			for (Type iface : info._interfaces) {
				interfaces.addAll(getInterfaces(iface));
			}

			interfaces.addAll(getInterfaces(info._superclass));
		} else {
			interfaces = getInterfaces(info._superclass);
		}

		return interfaces;
	}

	public static List<Type> getCommonInterfaces(Type t1, Type t2) {
		if (t1.isArray() || t2.isArray()) return Collections.emptyList();
		t1 = simplifyIfFuture(t1);
		t2 = simplifyIfFuture(t2);

		Set<Type> interfacesT1 = getInterfaces(t1);
		Set<Type> interfacesT2 = getInterfaces(t2);

		if (interfacesT1.size() > 0) {
			interfacesT1.retainAll(interfacesT2);

			if (interfacesT1.size() > 0) {
				/*Log.debug("getCommonInterfaces(" + t1.commonName() + ", " + t2.commonName()
						+ ") -> " + interfacesT1);*/
			}
		}

		List<Type> res = new ArrayList<Type>(interfacesT1);
		// Fazer sort antes de retornar, já que muitos dos clientes desta interface vão usar apenas
		// uma das interfaces no caso de existirem várias, e assim a decisão é determinística
		Collections.sort(res);

		return res;
	}

	public static Type getBestCommonInterface(Type t1, Type t2) {
		if (t1.isArray() || t2.isArray()) return null;
		t1 = simplifyIfFuture(t1);
		t2 = simplifyIfFuture(t2);

		Set<Type> interfacesT1 = getInterfaces(t1);
		Set<Type> interfacesT2 = getInterfaces(t2);

		if (interfacesT2.contains(t1)) return t1;
		if (interfacesT1.contains(t2)) return t2;

		if (interfacesT1.size() > 0) interfacesT1.retainAll(interfacesT2);

		if (interfacesT1.isEmpty()) return null;
		if (interfacesT1.size() == 1) return interfacesT1.iterator().next();

		// FIXME: Como decidir quando temos várias? Especialmente quando os tipos não se intersectam
		// de nenhuma forma? Parece-me que esta API não é a correcta para fazer isto, mas o ASM não
		// parece estar equipado para lidar correctamente com isto
		Type[] res = interfacesT1.toArray(new Type[0]);
		Arrays.sort(res);

		return res[0];
	}

	public static org.objectweb.asm.Type getBestCommonInterface(org.objectweb.asm.Type t1,
		org.objectweb.asm.Type t2) {
		Type iface = getBestCommonInterface(Type.fromType(t1), Type.fromType(t2));
		return iface != null ? iface.toType() : null;
	}

	public static Type getCommonType(Type t1, Type t2) {
		t1 = simplifyIfFuture(t1);
		t2 = simplifyIfFuture(t2);

		TypeInfo superType = getTypeInfo(t1);

		while (!superType._type.equals(Type.OBJECT)) {
			if (isAssignableFrom(superType._type, t2)) return superType._type;
			superType = getTypeInfo(superType._superclass);
		}

		Type commonInterface = getBestCommonInterface(t1, t2);
		if (commonInterface != null) return commonInterface;

		return Type.OBJECT;
	}

	private static Type simplifyIfFuture(Type t) {
		return t.bytecodeName().startsWith("Ljava/util/concurrent/Future$") ? CommonTypes.FUTURE : t;
	}

}
