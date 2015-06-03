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

package java.lang;

import java.util.ArrayList;

// Substituto transaccional para o java.lang.StringBuilder

/** HACKED LOCAL VERSION OF STRINGBUILDER **/

public final class StringBuilder /*extends AbstractStringBuilder*/ implements java.io.Serializable, CharSequence, Appendable {

	public final static boolean REPLACEMENT_CLASS_MARKER = true;

	private final ArrayList<String> BUFFER = new ArrayList<String>();

	/** use serialVersionUID for interoperability */
	static final long serialVersionUID = 4383685877147921099L;

	// Implemented methods

	public StringBuilder() { }
	public StringBuilder(int capacity) { }

	public StringBuilder(String init) {
		append(init);
	}

	public StringBuilder(CharSequence seq) {
		append(seq);
	}

	public StringBuilder append(String s) {
		BUFFER.add(s);
		return this;
	}

	public StringBuilder append(boolean b) {
		return append(String.valueOf(b));
	}

	public StringBuilder append(char c) {
		return append(String.valueOf(c));
	}

	public StringBuilder append(int i) {
		return append(String.valueOf(i));
	}

	public StringBuilder append(long l) {
		return append(String.valueOf(l));
	}

	public StringBuilder append(float f) {
		return append(String.valueOf(f));
	}

	public StringBuilder append(double d) {
		return append(String.valueOf(d));
	}

	public StringBuilder append(Object o) {
		return append(o.toString());
	}

	@Override
	public String toString() {
		// Construir string de forma transaccional
		// Porque é que esta operação é segura?
		// - Não é feito spawn de especulações directa ou indirectamente a partir deste método
		// - O realstringbuffer não é transaccional, mas é local apenas a este método, e as leituras que
		// faz são todas de strings retiradas do BUFFER. Como o BUFFER é transaccional, caso exista uma
		// modificação esta é detectada com sucesso.
		Object realStringBuilder = jaspex.Builtin.StringBuilder_new();
		for (String s : BUFFER) {
			jaspex.Builtin.StringBuilder_append(realStringBuilder, s);
		}
		return jaspex.Builtin.StringBuilder_toString(realStringBuilder);
	}

	// Stubs: FIXME -- Implement later

	public StringBuilder append(StringBuffer sb) {
		throw new Error("FIXME");
	}

	public StringBuilder append(CharSequence s) {
		// Implementação incompleta...
		if (s instanceof String) return append((String) s);
		throw new Error("FIXME");
	}

	public StringBuilder append(CharSequence s, int start, int end) {
		throw new Error("FIXME");
	}

	public StringBuilder append(char[] str) {
		throw new Error("FIXME");
	}

	public StringBuilder append(char[] str, int offset, int len) {
		throw new Error("FIXME");
	}

	public StringBuilder appendCodePoint(int codePoint) {
		throw new Error("FIXME");
	}

	public StringBuilder delete(int start, int end) {
		throw new Error("FIXME");
	}

	public StringBuilder deleteCharAt(int index) {
		throw new Error("FIXME");
	}

	public StringBuilder replace(int start, int end, String str) {
		throw new Error("FIXME");
	}

	public StringBuilder insert(int index, char[] str, int offset, int len) {
		throw new Error("FIXME");
	}

	public StringBuilder insert(int offset, Object obj) {
		throw new Error("FIXME");
	}

	public StringBuilder insert(int offset, String str) {
		throw new Error("FIXME");
	}

	public StringBuilder insert(int offset, char[] str) {
		throw new Error("FIXME");
	}

	public StringBuilder insert(int dstOffset, CharSequence s) {
		throw new Error("FIXME");
	}

	public StringBuilder insert(int dstOffset, CharSequence s, int start, int end) {
		throw new Error("FIXME");
	}

	public StringBuilder insert(int offset, boolean b) {
		throw new Error("FIXME");
	}

	public StringBuilder insert(int offset, char c) {
		throw new Error("FIXME");
	}

	public StringBuilder insert(int offset, int i) {
		throw new Error("FIXME");
	}

	public StringBuilder insert(int offset, long l) {
		throw new Error("FIXME");
	}

	public StringBuilder insert(int offset, float f) {
		throw new Error("FIXME");
	}

	public StringBuilder insert(int offset, double d) {
		throw new Error("FIXME");
	}

	public int indexOf(String str) {
		throw new Error("FIXME");
	}

	public int indexOf(String str, int fromIndex) {
		throw new Error("FIXME");
	}

	public int lastIndexOf(String str) {
		throw new Error("FIXME");
	}

	public int lastIndexOf(String str, int fromIndex) {
		throw new Error("FIXME");
	}

	public StringBuilder reverse() {
		throw new Error("FIXME");
	}

	private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
		throw new Error("FIXME");
	}

	private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
		throw new Error("FIXME");
	}

	// Membros do AbstractStringBuilder

	// Implementados

	public void trimToSize() {
		BUFFER.trimToSize();
	}

	public int length() {
		int len = 0;
		for (String s : BUFFER) len += s.length();
		return len;
	}

	// Stubs: FIXME -- Implement later
	public int capacity() {
		throw new Error("FIXME");
	}

	public void ensureCapacity(int minimumCapacity) {
		throw new Error("FIXME");
	}

	public void setLength(int newLength) {
		throw new Error("FIXME");
	}

	public char charAt(int index) {
		throw new Error("FIXME");
	}

	public int codePointAt(int index) {
		throw new Error("FIXME");
	}

	public int codePointBefore(int index) {
		throw new Error("FIXME");
	}

	public int codePointCount(int beginIndex, int endIndex) {
		throw new Error("FIXME");
	}

	public int offsetByCodePoints(int index, int codePointOffset) {
		throw new Error("FIXME");
	}

	public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
		throw new Error("FIXME");
	}

	public void setCharAt(int index, char ch) {
		throw new Error("FIXME");
	}

	public String substring(int start) {
		throw new Error("FIXME");
	}

	public CharSequence subSequence(int start, int end) {
		throw new Error("FIXME");
	}

	public String substring(int start, int end) {
		throw new Error("FIXME");
	}

}
