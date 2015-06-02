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

package asmlib.extra;

import asmlib.*;

public class InvokedMethod implements Comparable<InvokedMethod> {

	private Type _owner;
	private String _name;
	private String _desc;
	private InfoMethod _method;

	public InvokedMethod(Type owner, String name, String desc) {
		_owner = owner;
		_name = name;
		_desc = desc;
	}

	public InvokedMethod(Type owner, String name, String desc, InfoMethod method) {
		this(owner, name, desc);
		_method = method;
	}

	public Type owner() {
		if (_method == null) return _owner;
		else return _method.infoClass().type();
	}

	public String name() {
		if (_method == null) return _name;
		else return _method.name();
	}

	public String desc() {
		if (_method == null) return _desc;
		else return _method.desc();
	}

	public InfoMethod method() { return _method; }

	public void setMethod(InfoMethod method) { _method = method; }

	@Override
	public int hashCode() {
		return (owner() + name() + desc()).hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof InvokedMethod) {
			InvokedMethod other = (InvokedMethod)o;
			return compareTo(other) == 0;
		}
		return false;
	}

	public int compareTo(InvokedMethod other) {
		return (owner() + name() + desc()).compareTo(other.owner() + other.name() + other.desc());
	}

	@Override
	public String toString() {
		return "asmlib.extra.InvokedMethod (Owner: " + _owner + " | Name: " + _name + " | Desc:" + _desc + ")";
	}

}
