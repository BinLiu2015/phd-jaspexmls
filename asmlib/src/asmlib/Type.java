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

import util.*;

import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.Opcodes;

/**
 * Classe que representa um tipo em Java/na JVM.
 * A ideia desta classe é isolar os diferentes "formatos" em que o nome de uma classe pode estar:
 * filePath: java/lang/String.class
 * simple: String
 * package: java.lang
 * bytecode: Ljava/lang/String;
 * asm: java/lang/String
 * common: java.lang.String
 * opcode: Tipos primitivos, em que o opcode é o utilizado nos classfiles do java para os representar
 * class: Instância de java/lang/Class
 **/

/* Nota: Numa classe como
   public class GenTest<Xpto extends String> {
	Xpto field;

	GenTest(Xpto arg) { field = arg; }
   }
   field vai ter o description Ljava/lang/Object; e vai ter a signature TXpto; .
   Pelos vistos os nomes dos placeholders são guardados nos generics, prefixados com T e terminados com ; .
   (Daí o T...; que esta classe suporta)
 */

public class Type implements Comparable<Type> {

	// Alguns tipos já pré-gerados, para mais fácil acesso
	public static final Type OBJECT = Type.fromCommon("java.lang.Object");
	public static final Type STRING = Type.fromCommon("java.lang.String");

	public static final Type PRIM_BOOLEAN = Type.fromBytecode('Z');
	public static final Type PRIM_BYTE = Type.fromBytecode('B');
	public static final Type PRIM_CHAR = Type.fromBytecode('C');
	public static final Type PRIM_SHORT = Type.fromBytecode('S');
	public static final Type PRIM_INT = Type.fromBytecode('I');
	public static final Type PRIM_LONG = Type.fromBytecode('J');
	public static final Type PRIM_FLOAT = Type.fromBytecode('F');
	public static final Type PRIM_DOUBLE = Type.fromBytecode('D');
	public static final Type PRIM_VOID = Type.fromBytecode('V');

	public static final Type OBJECT_BOOLEAN = Type.fromCommon("java.lang.Boolean");
	public static final Type OBJECT_BYTE = Type.fromCommon("java.lang.Byte");
	public static final Type OBJECT_CHARACTER = Type.fromCommon("java.lang.Character");
	public static final Type OBJECT_SHORT = Type.fromCommon("java.lang.Short");
	public static final Type OBJECT_INTEGER = Type.fromCommon("java.lang.Integer");
	public static final Type OBJECT_LONG = Type.fromCommon("java.lang.Long");
	public static final Type OBJECT_FLOAT = Type.fromCommon("java.lang.Float");
	public static final Type OBJECT_DOUBLE = Type.fromCommon("java.lang.Double");
	public static final Type OBJECT_VOID = Type.fromCommon("java.lang.Void");

	/** Nome da classe no formato bytecode **/
	private final String _bytecodeName;

	private Type(String bytecodeName) {
		_bytecodeName = bytecodeName;

		if (bytecodeName.isEmpty()) throw new AssertionError("Empty bytecodeName");
		if (bytecodeName.length() == 1 && !isPrimitive()) throw new Error("Invalid bytecodeName");
	}

	public static Type fromCommon(String common) {
		String bytecode = "L" + common.replace(".", "/").replace("[]", "") + ";";

		if (common.endsWith("[]")) {
			String prefix = common.substring(common.indexOf('['), common.lastIndexOf(']') + 1)
						.replace(']', '[');
			bytecode = prefix.substring(0, prefix.length()/2) + bytecode;
		}

		return Type.fromBytecode(bytecode);
	}

	public static Type fromFilePath(String path) {
		return Type.fromCommon(path.replace("/", ".").replace(".class", ""));
	}

	public static Type fromAsm(String asm) {
		if (asm.startsWith("[")) {
			// Caso especial, parece que o "formato asm" passa a "formato bytecode" quando é um array
			// Por exemplo o formato asm para array de object é "[Ljava/lang/Object;"
			return Type.fromBytecode(asm);
		}
		return Type.fromBytecode("L" + asm + ";");
	}

	public static Type fromBytecode(char c) {
		return Type.fromBytecode(String.valueOf(c));
	}

	public static Type fromBytecode(String bytecode) {
		assert (bytecode.indexOf('.') == -1 || bytecode.indexOf('<') >= 0);
		return new Type(bytecode);
	}

	public static Type fromOpcode(int opcode) {
		char type;

		switch (opcode) {
			case Opcodes.T_BOOLEAN:
				type = 'Z';
				break;
			case Opcodes.T_BYTE:
				type = 'B';
				break;
			case Opcodes.T_CHAR:
				type = 'C';
				break;
			case Opcodes.T_SHORT:
				type = 'S';
				break;
			case Opcodes.T_INT:
				type = 'I';
				break;
			case Opcodes.T_LONG:
				type = 'J';
				break;
			case Opcodes.T_FLOAT:
				type = 'F';
				break;
			case Opcodes.T_DOUBLE:
				type = 'D';
				break;
			default:
				throw new InstrumentationException("Unknown or invalid bytecode type");
		}

		return Type.fromBytecode(type);
	}

	public static Type fromLoadOpcode(int opcode) {
		char type;

		switch (opcode) {
			case Opcodes.ILOAD:
				type = 'I';
				break;
			case Opcodes.LLOAD:
				type = 'J';
				break;
			case Opcodes.FLOAD:
				type = 'F';
				break;
			case Opcodes.DLOAD:
				type = 'D';
				break;
			case Opcodes.ALOAD:
				return Type.OBJECT;
			default:
				throw new InstrumentationException("Unknown or invalid bytecode type");
		}

		return Type.fromBytecode(type);
	}

	public static Type fromClass(Class<?> c) {
		if (c.isPrimitive()) {
			String name = c.getName().toLowerCase();
			for (String[] typeMapping : primitiveTypeNames) {
				if (typeMapping[0].equals(name)) return Type.fromBytecode(typeMapping[1]);
			}
			throw new AssertionError("This should never happen");
		}

		String s = c.getName();

		if (s.charAt(0) == '[') {
			// Java troca para um misto entre bytecodeName e commonName para arrays
			return Type.fromBytecode(s.replace('.', '/'));
		}

		return Type.fromCommon(s);
	}

	public static Type fromType(org.objectweb.asm.Type t) {
		return Type.fromBytecode(t.getDescriptor());
	}

	public static UtilList<Type> getArgumentTypes(String methodDescriptor) {
		String input = methodDescriptor;
		UtilList<Type> outList = new UtilArrayList<Type>();
		char c;
		int array = 0;
		Type t;

		// Exemplo descrição com "constraints" de generics
		// <T:Ljava/lang/Object;>([TT;)[TT;
		if ((input.charAt(0) != '(') && (input.charAt(0) == '<')) {
			input = input.substring(input.indexOf('('));
		}

		while (input.length() > 0) {
			c = input.charAt(0);
			input = input.substring(1);
			if (c == '[') {
				array++;
			} else if ((c == 'L') || (c == 'T')) {
				int pos = input.indexOf(';') + 1;
				String s = input.substring(0, pos);
				// Handling generics
				int genCount = StringUtils.countMatches(s, "<");
				if (genCount > 0) {
					while (genCount > 0) {
						pos = input.indexOf('>', pos);
						genCount--;
					}
					pos = input.indexOf(';', pos) + 1;
					s = input.substring(0, pos);
				}
				input = input.substring(pos);

				t = Type.fromBytecode(c + s);
				if (array > 0) {
					t = t.toArray(array);
					array = 0;
				}

				outList.add(t);
			} else if (c == '(') {
				// do nothing
			} else if (c == 'V') {
				throw new AssertionError("Invalid type in methodDescriptor");
			} else if (c == ')') {
				return outList;
			} else {
				t = Type.fromBytecode(Character.valueOf(c).toString());
				if (!t.isPrimitive()) throw new AssertionError("Invalid type in methodDescriptor");

				if (array > 0) {
					t = t.toArray(array);
					array = 0;
				}

				outList.add(t);
			}
		}

		throw new Error("Malformed methodDescriptor");
	}

	@Override
	public String toString() {
		return commonName();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) return true;
		if (o instanceof Type) {
			Type other = (Type) o;
			return compareTo(other) == 0;
		}
		return false;
	}

	public int compareTo(Type other) {
		return bytecodeName().compareTo(other.bytecodeName());
	}

	@Override
	public int hashCode() {
		return bytecodeName().hashCode();
	}

	public String simpleName() {
		return StringList.split(commonName(), ".").last();
	}

	public String commonName() {
		if (isPrimitive()) return primitiveTypeName();

		String bytecode = bytecodeName();
		String suffix = "";
		// Transformar arrays
		if (isArray()) {
			int dimensions = arrayDimensions();
			for (int i = 0; i < dimensions; i++) suffix += "[]";
			bytecode = bytecode.substring(dimensions, bytecode.length());
		}

		if (bytecode.charAt(0) == 'L') {
			bytecode = bytecode.substring(1, bytecode.length()-1);
		} else if (bytecode.charAt(0) == 'T') {
			// FIXME: Não tenho bem a certeza quanto a este
			bytecode = bytecode.substring(1, bytecode.length()-1);
		} else {
			// array de tipo primitivo
		}

		bytecode = bytecode.replace('/', '.');

		if (!suffix.isEmpty()) bytecode += suffix;

		return bytecode;
	}

	public String bytecodeName() {
		return _bytecodeName;
	}

	public String asmName() {
		String type = bytecodeName();

		// O asmName de um array é aparentemente o seu bytecodeName sem alterações
		if (isArray()) return type;
		if (isPrimitive()) throw new Error("Cannot represent primitive types using asmName");

		return type.substring(1, type.length()-1);
	}

	public org.objectweb.asm.Type toType() {
		return org.objectweb.asm.Type.getObjectType(asmName());
	}

	/** Converte arrays na sua classe original: Object[][][] passa a Object **/
	public Type stripArray() {
		String strip = bytecodeName();
		return Type.fromBytecode(strip.replace("[", ""));
	}

	public int arrayDimensions() {
		String bytecodeName = bytecodeName();
		int pos = 0;
		while (pos < bytecodeName.length() && (bytecodeName.charAt(pos) == '[')) pos++;
		return pos;
	}

	public boolean isPrimitive() {
		String name = bytecodeName();
		if (name.length() != 1) return false;
		char c = name.charAt(0);
		return ((c == 'Z') || (c == 'B') || (c == 'C') || (c == 'S')
			|| (c == 'I') || (c == 'J') || (c == 'F') || (c == 'D'))
			|| (c == 'V');
	}

	public boolean isArray() {
		return bytecodeName().charAt(0) == '[';
	}

	public Type toArray() {
		return toArray(1);
	}

	public Type toArray(int dimensions) {
		if (dimensions <= 0) throw new AssertionError("Dimensions cannot be <= 0");
		String dims = "";
		for (int i = 0; i < dimensions; i++) dims += "[";
		return Type.fromBytecode(dims + bytecodeName());
	}

	public boolean isGeneric() {
		return bytecodeName().contains("<");
	}

	private static final Type[][] primitiveWrappers = new Type[][] {
		{OBJECT_BOOLEAN, PRIM_BOOLEAN}, {OBJECT_BYTE, PRIM_BYTE}, {OBJECT_CHARACTER, PRIM_CHAR},
		{OBJECT_SHORT, PRIM_SHORT}, {OBJECT_INTEGER, PRIM_INT}, {OBJECT_LONG, PRIM_LONG},
		{OBJECT_FLOAT, PRIM_FLOAT}, {OBJECT_DOUBLE, PRIM_DOUBLE}, {OBJECT_VOID, PRIM_VOID}
	};

	public Type toObject() {
		for (Type[] map : primitiveWrappers) {
			if (this.equals(map[1])) return map[0];
		}
		return null;
	}

	public Type toPrimitive() {
		for (Type[] map : primitiveWrappers) {
			if (this.equals(map[0])) return map[1];
		}
		return null;
	}

	/** Método que retorna o opcode certo para o tipo de variável que se quer fazer load.
	  * Opcodes disponíveis:
	  * - ILOAD para boolean, byte, char, short, int
	  * - LLOAD para long
	  * - FLOAD para float
	  * - DLOAD para double
	  * - ALOAD para referência (objecto ou array)
	  **/
	public int getLoadInsn() {
		char c = bytecodeName().charAt(0);
		switch (c) {
			case 'Z': // boolean
			case 'B': // byte
			case 'C': // char
			case 'S': // short
			case 'I': // int
				return Opcodes.ILOAD;
			case 'J': // long
				return Opcodes.LLOAD;
			case 'F': // float
				return Opcodes.FLOAD;
			case 'D': // double
				return Opcodes.DLOAD;
			case '[': // Algum tipo de array
			case 'L': // objecto
			case 'T': // objecto (generics)
				return Opcodes.ALOAD;
		}
		throw new InstrumentationException("Unknown fieldType in getLoadInsn");
	}

	/** Método que retorna o opcode certo para o tipo de variável que se quer fazer store.
	  * Opcodes disponíveis:
	  * - ISTORE para boolean, byte, char, short, int
	  * - LSTORE para long
	  * - FSTORE para float
	  * - DSTORE para double
	  * - ASTORE para referência (objecto ou array)
	  **/
	public int getStoreInsn() {
		char c = bytecodeName().charAt(0);
		switch (c) {
			case 'Z': // boolean
			case 'B': // byte
			case 'C': // char
			case 'S': // short
			case 'I': // int
				return Opcodes.ISTORE;
			case 'J': // long
				return Opcodes.LSTORE;
			case 'F': // float
				return Opcodes.FSTORE;
			case 'D': // double
				return Opcodes.DSTORE;
			case '[': // Algum tipo de array
			case 'L': // objecto
			case 'T': // objecto (generics)
				return Opcodes.ASTORE;
		}
		throw new InstrumentationException("Unknown fieldType in getStoreInsn");
	}

	/** Método que retorna o opcode certo para o tipo de variável que se quer fazer return.
	  * Opcodes disponíveis:
	  * - IRETURN para boolean, byte, char, short, int
	  * - LRETURN para long
	  * - FRETURN para float
	  * - DRETURN para double
	  * - ARETURN para referência (objecto ou array)
	  * -  RETURN para métodos void
	  **/
	public int getReturnInsn() {
		char c = bytecodeName().charAt(0);
		switch (c) {
			case 'Z': // boolean
			case 'B': // byte
			case 'C': // char
			case 'S': // short
			case 'I': // int
				return Opcodes.IRETURN;
			case 'J': // long
				return Opcodes.LRETURN;
			case 'F': // float
				return Opcodes.FRETURN;
			case 'D': // double
				return Opcodes.DRETURN;
			case '[': // Algum tipo de array
			case 'L': // objecto
			case 'T': // objecto (generics)
				return Opcodes.ARETURN;
			case 'V': // void
				return Opcodes.RETURN;
		}
		throw new InstrumentationException("Unknown fieldType in getReturnInsn");
	}

	/** Método que retorna o opcode certo para o tipo de variável que se quer fazer pop na stack.
	  * Opcodes disponíveis:
	  * - POP2 para long, double
	  * - POP para todos os restantes
	  **/
	public int getPopInsn() {
		if (getNumberSlots() == 1) {
			return Opcodes.POP;
		} else {
			return Opcodes.POP2;
		}
	}

	/** Método que retorna o opcode certo para o tipo de variável que se quer fazer dup na stack.
	  * Opcodes disponíveis:
	  * - DUP2 para long, double
	  * - DUP para todos os restantes
	  **/
	public int getDupInsn() {
		if (getNumberSlots() == 1) {
			return Opcodes.DUP;
		} else {
			return Opcodes.DUP2;
		}
	}

	private static final String[][] primitiveTypeNames = new String[][] {
		{"boolean", "Z"}, {"byte", "B"}, {"char", "C"},
		{"short", "S"}, {"int", "I"}, {"long", "J"},
		{"float", "F"}, {"double", "D"}, {"void", "V"}
	};

	/** Devolve o nome do tipo básico (int, long, ...).
	  * Útil para por exemplo chamar intValue() sobre Integer, longValue() sobre Long, ...
	  **/
	public String primitiveTypeName() {
		if (!isPrimitive()) throw new InstrumentationException("Type doesn't represent a Primitive Type");

		String c = Character.valueOf(bytecodeName().charAt(0)).toString();
		for (String[] typeMapping : primitiveTypeNames) {
			if (typeMapping[1].equals(c)) return typeMapping[0];
		}

		throw new AssertionError("This should never happen");
	}

	/** Método que retorna o número de slots necessários por um certo tipo na stack.
	  * Basicamente, tudo ocupa 1 slot, excepto Long e Double, que ocupam 2.
	  **/
	public int getNumberSlots() {
		char c = bytecodeName().charAt(0);
		switch (c) {
			case 'J': // long
			case 'D': // double
				return 2;
			default:
				return 1;
		}
	}
}
