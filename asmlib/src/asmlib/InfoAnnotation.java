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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class InfoAnnotation implements Comparable<InfoAnnotation> {

	private final Type _type;
	private boolean _visibleAtRuntime;
	private int _paramIndex;
	private final Map<String, Object> _values = new HashMap<String, Object>();

	public InfoAnnotation(Type type, boolean visible) {
		_type = type;
		_visibleAtRuntime = visible;
	}

	public InfoAnnotation(int parameter, Type type, boolean visible) {
		_paramIndex = parameter;
		_type = type;
		_visibleAtRuntime = visible;
	}

	public InfoAnnotation(Type annotClass) {
		_type = annotClass;
	}

	public void addValue(String name, Object value) {
		_values.put(name, value);
	}

	public Boolean     booleanValue(String name) { return (Boolean)   _values.get(name); }
	public Byte           byteValue(String name) { return    (Byte)   _values.get(name); }
	public Character characterValue(String name) { return (Character) _values.get(name); }
	public Short         shortValue(String name) { return (Short)     _values.get(name); }
	public Integer     integerValue(String name) { return (Integer)   _values.get(name); }
	public Long           longValue(String name) { return (Long)      _values.get(name); }
	public Float         floatValue(String name) { return (Float)     _values.get(name); }
	public Double       doubleValue(String name) { return (Double)    _values.get(name); }
	public String       stringValue(String name) { return (String)    _values.get(name); }
	public Object             value(String name) { return             _values.get(name); }

	public Type type() { return _type; }

	@Override
	public String toString() {
		String res = "";
		if (_visibleAtRuntime) {
			res += "@Retention(RetentionPolicy.RUNTIME)\n";
		}
		if (_paramIndex >= 0) {
			res += "@Target(ElementType.PARAMETER = " + _paramIndex + ")\n";
		}
		res = "@" + _type;
		if (!_values.isEmpty()) {
			res += "(\n";
			for (Entry<String, Object> entry : _values.entrySet()) {
				res += "\t" + entry.getKey() + " = " + entry.getValue() + ",\n";
			}
			res = res.substring(0, res.length() - 1) + ")";
		}
		return res;
	}

	@Override
	public int hashCode() {
		return _type.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof InfoAnnotation) {
			InfoAnnotation other = (InfoAnnotation)o;
			return compareTo(other) == 0;
		}
		return false;
	}

	public int compareTo(InfoAnnotation other) {
		return _type.compareTo(other._type);
	}

	public boolean hasValue(String name) {
		return _values.containsKey(name);
	}

}
