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

package jaspex.stm;

import static jaspex.util.Unsafe.UNSAFE;

import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public abstract class FieldAccess implements Iterable<FieldAccess> {

	/** Static shared stuff **/
	public static final int  OBJECT_ARRAY_BASE = UNSAFE.arrayBaseOffset(Object[].class);
	public static final int BOOLEAN_ARRAY_BASE = UNSAFE.arrayBaseOffset(boolean[].class);
	public static final int    BYTE_ARRAY_BASE = UNSAFE.arrayBaseOffset(byte[].class);
	public static final int    CHAR_ARRAY_BASE = UNSAFE.arrayBaseOffset(char[].class);
	public static final int  DOUBLE_ARRAY_BASE = UNSAFE.arrayBaseOffset(double[].class);
	public static final int   FLOAT_ARRAY_BASE = UNSAFE.arrayBaseOffset(float[].class);
	public static final int     INT_ARRAY_BASE = UNSAFE.arrayBaseOffset(int[].class);
	public static final int    LONG_ARRAY_BASE = UNSAFE.arrayBaseOffset(long[].class);
	public static final int   SHORT_ARRAY_BASE = UNSAFE.arrayBaseOffset(short[].class);

	public static final int  OBJECT_ARRAY_SHIFT = computeShift(Object[].class);
	public static final int BOOLEAN_ARRAY_SHIFT = computeShift(boolean[].class);
	public static final int    BYTE_ARRAY_SHIFT = computeShift(byte[].class);
	public static final int    CHAR_ARRAY_SHIFT = computeShift(char[].class);
	public static final int  DOUBLE_ARRAY_SHIFT = computeShift(double[].class);
	public static final int   FLOAT_ARRAY_SHIFT = computeShift(float[].class);
	public static final int     INT_ARRAY_SHIFT = computeShift(int[].class);
	public static final int    LONG_ARRAY_SHIFT = computeShift(long[].class);
	public static final int   SHORT_ARRAY_SHIFT = computeShift(short[].class);

	private static final int computeShift(Class<?> c) {
		int scale = UNSAFE.arrayIndexScale(c);
		if ((scale & (scale - 1)) != 0) throw new Error("Data type scale not a power of two");
		return 31 - Integer.numberOfLeadingZeros(scale);
	}

	protected static final long positionOffset(int base, int shift, int i) {
		return ((long) i << shift) + base;
	}

	// Used only by FutureFieldAccess, but needs to be made available to Transaction
	protected static enum Type { OBJECT, BOOLEAN, BYTE, CHAR, DOUBLE, FLOAT, INT, LONG, SHORT }

	/** Instance fields **/
	final Object _instance;
	final long _offset;
	FieldAccess _next;

	FieldAccess(Object instance, long offset) {
		_instance = instance;
		_offset = offset;
	}

	// Used when the FieldAccess is a read-set entry
	public abstract boolean validate();
	// Used when the FieldAccess is a write-set entry
	public abstract void writeback();

	@Override
	public final int hashCode() {
		return System.identityHashCode(_instance) + (int) _offset;
	}

	@Override
	public final boolean equals(Object o) {
		if (o instanceof FieldAccess) {
			FieldAccess other = (FieldAccess) o;
			return (_instance == other._instance) && (_offset == other._offset);
		}
		return false;
	}

	// To be overridden by FutureFieldAccess
	public FieldAccess nativeFieldAccess() {
		return this;
	}

	// Iterator/Iterable implementation
	@Override
	public Iterator<FieldAccess> iterator() {
		return new Iterator<FieldAccess>() {
			private FieldAccess _next = FieldAccess.this;

			@Override public boolean hasNext() {
				return _next != null;
			}

			@Override public FieldAccess next() {
				FieldAccess fa = _next;
				_next = fa._next;
				return fa;
			}

			@Override public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	// May be used to compare the values contained in two FieldAccesses
	public abstract boolean valueEquals(FieldAccess o);
}

final class ObjectFieldAccess extends FieldAccess {
	final Object _value;

	ObjectFieldAccess(Object instance, long offset, Object value) {
		super(instance, offset);
		_value = value;
	}

	ObjectFieldAccess(Object[] array, int pos, Object value) {
		this(array, positionOffset(OBJECT_ARRAY_BASE, OBJECT_ARRAY_SHIFT, pos), value);
	}

	@Override
	public boolean validate() {
		return _value == UNSAFE.getObject(_instance, _offset);
	}

	@Override
	public void writeback() {
		UNSAFE.putObject(_instance, _offset, _value);
	}

	@Override
	public boolean valueEquals(FieldAccess o) {
		return o instanceof ObjectFieldAccess && (_value == ((ObjectFieldAccess)o)._value);
	}
}

final class BooleanFieldAccess extends FieldAccess {
	final boolean _value;

	BooleanFieldAccess(Object instance, long offset, boolean value) {
		super(instance, offset);
		_value = value;
	}

	BooleanFieldAccess(boolean[] array, int pos, boolean value) {
		this(array, positionOffset(BOOLEAN_ARRAY_BASE, BOOLEAN_ARRAY_SHIFT, pos), value);
	}

	@Override
	public boolean validate() {
		return _value == UNSAFE.getBoolean(_instance, _offset);
	}

	@Override
	public void writeback() {
		UNSAFE.putBoolean(_instance, _offset, _value);
	}

	@Override
	public boolean valueEquals(FieldAccess o) {
		return o instanceof BooleanFieldAccess && (_value == ((BooleanFieldAccess)o)._value);
	}
}

final class ByteFieldAccess extends FieldAccess {
	final byte _value;

	ByteFieldAccess(Object instance, long offset, byte value) {
		super(instance, offset);
		_value = value;
	}

	ByteFieldAccess(byte[] array, int pos, byte value) {
		this(array, positionOffset(BYTE_ARRAY_BASE, BYTE_ARRAY_SHIFT, pos), value);
	}

	@Override
	public boolean validate() {
		return _value == UNSAFE.getByte(_instance, _offset);
	}

	@Override
	public void writeback() {
		UNSAFE.putByte(_instance, _offset, _value);
	}

	@Override
	public boolean valueEquals(FieldAccess o) {
		return o instanceof ByteFieldAccess && (_value == ((ByteFieldAccess)o)._value);
	}
}

final class CharFieldAccess extends FieldAccess {
	final char _value;

	CharFieldAccess(Object instance, long offset, char value) {
		super(instance, offset);
		_value = value;
	}

	CharFieldAccess(char[] array, int pos, char value) {
		this(array, positionOffset(CHAR_ARRAY_BASE, CHAR_ARRAY_SHIFT, pos), value);
	}

	@Override
	public boolean validate() {
		return _value == UNSAFE.getChar(_instance, _offset);
	}

	@Override
	public void writeback() {
		UNSAFE.putChar(_instance, _offset, _value);
	}

	@Override
	public boolean valueEquals(FieldAccess o) {
		return o instanceof CharFieldAccess && (_value == ((CharFieldAccess)o)._value);
	}
}

final class DoubleFieldAccess extends FieldAccess {
	final double _value;

	DoubleFieldAccess(Object instance, long offset, double value) {
		super(instance, offset);
		_value = value;
	}

	DoubleFieldAccess(double[] array, int pos, double value) {
		this(array, positionOffset(DOUBLE_ARRAY_BASE, DOUBLE_ARRAY_SHIFT, pos), value);
	}

	@Override
	public boolean validate() {
		return _value == UNSAFE.getDouble(_instance, _offset);
	}

	@Override
	public void writeback() {
		UNSAFE.putDouble(_instance, _offset, _value);
	}

	@Override
	public boolean valueEquals(FieldAccess o) {
		return o instanceof DoubleFieldAccess && (_value == ((DoubleFieldAccess)o)._value);
	}
}

final class FloatFieldAccess extends FieldAccess {
	final float _value;

	FloatFieldAccess(Object instance, long offset, float value) {
		super(instance, offset);
		_value = value;
	}

	FloatFieldAccess(float[] array, int pos, float value) {
		this(array, positionOffset(FLOAT_ARRAY_BASE, FLOAT_ARRAY_SHIFT, pos), value);
	}

	@Override
	public boolean validate() {
		return _value == UNSAFE.getFloat(_instance, _offset);
	}

	@Override
	public void writeback() {
		UNSAFE.putFloat(_instance, _offset, _value);
	}

	@Override
	public boolean valueEquals(FieldAccess o) {
		return o instanceof FloatFieldAccess && (_value == ((FloatFieldAccess)o)._value);
	}
}

final class IntFieldAccess extends FieldAccess {
	final int _value;

	IntFieldAccess(Object instance, long offset, int value) {
		super(instance, offset);
		_value = value;
	}

	IntFieldAccess(int[] array, int pos, int value) {
		this(array, positionOffset(INT_ARRAY_BASE, INT_ARRAY_SHIFT, pos), value);
	}

	@Override
	public boolean validate() {
		return _value == UNSAFE.getInt(_instance, _offset);
	}

	@Override
	public void writeback() {
		UNSAFE.putInt(_instance, _offset, _value);
	}

	@Override
	public boolean valueEquals(FieldAccess o) {
		return o instanceof IntFieldAccess && (_value == ((IntFieldAccess)o)._value);
	}
}

final class LongFieldAccess extends FieldAccess {
	final long _value;

	LongFieldAccess(Object instance, long offset, long value) {
		super(instance, offset);
		_value = value;
	}

	LongFieldAccess(long[] array, int pos, long value) {
		this(array, positionOffset(LONG_ARRAY_BASE, LONG_ARRAY_SHIFT, pos), value);
	}

	@Override
	public boolean validate() {
		return _value == UNSAFE.getLong(_instance, _offset);
	}

	@Override
	public void writeback() {
		UNSAFE.putLong(_instance, _offset, _value);
	}

	@Override
	public boolean valueEquals(FieldAccess o) {
		return o instanceof LongFieldAccess && (_value == ((LongFieldAccess)o)._value);
	}
}

final class ShortFieldAccess extends FieldAccess {
	final short _value;

	ShortFieldAccess(Object instance, long offset, short value) {
		super(instance, offset);
		_value = value;
	}

	ShortFieldAccess(short[] array, int pos, short value) {
		this(array, positionOffset(SHORT_ARRAY_BASE, SHORT_ARRAY_SHIFT, pos), value);
	}

	@Override
	public boolean validate() {
		return _value == UNSAFE.getShort(_instance, _offset);
	}

	@Override
	public void writeback() {
		UNSAFE.putShort(_instance, _offset, _value);
	}

	@Override
	public boolean valueEquals(FieldAccess o) {
		return o instanceof ShortFieldAccess && (_value == ((ShortFieldAccess)o)._value);
	}
}

/** Represents a write of a value that will be yielded by a Future **/
final class FutureFieldAccess extends FieldAccess {
	final Future<?> _future;
	final Type _type;

	FieldAccess _nativeFieldAccess;

	FutureFieldAccess(Object instance, long offset, Future<?> future, Type type) {
		super(instance, offset);
		_future = future;
		_type = type;
	}

	// Support for arrays
	FutureFieldAccess( Object[] array, int pos, Future<?> future) { this(array, positionOffset( OBJECT_ARRAY_BASE,  OBJECT_ARRAY_SHIFT, pos), future, Type.OBJECT); }
	FutureFieldAccess(boolean[] array, int pos, Future<?> future) { this(array, positionOffset(BOOLEAN_ARRAY_BASE, BOOLEAN_ARRAY_SHIFT, pos), future, Type.BOOLEAN); }
	FutureFieldAccess(   byte[] array, int pos, Future<?> future) { this(array, positionOffset(   BYTE_ARRAY_BASE,    BYTE_ARRAY_SHIFT, pos), future, Type.BYTE); }
	FutureFieldAccess(   char[] array, int pos, Future<?> future) { this(array, positionOffset(   CHAR_ARRAY_BASE,    CHAR_ARRAY_SHIFT, pos), future, Type.CHAR); }
	FutureFieldAccess( double[] array, int pos, Future<?> future) { this(array, positionOffset( DOUBLE_ARRAY_BASE,  DOUBLE_ARRAY_SHIFT, pos), future, Type.DOUBLE); }
	FutureFieldAccess(  float[] array, int pos, Future<?> future) { this(array, positionOffset(  FLOAT_ARRAY_BASE,   FLOAT_ARRAY_SHIFT, pos), future, Type.FLOAT); }
	FutureFieldAccess(    int[] array, int pos, Future<?> future) { this(array, positionOffset(    INT_ARRAY_BASE,     INT_ARRAY_SHIFT, pos), future, Type.INT); }
	FutureFieldAccess(   long[] array, int pos, Future<?> future) { this(array, positionOffset(   LONG_ARRAY_BASE,    LONG_ARRAY_SHIFT, pos), future, Type.LONG); }
	FutureFieldAccess(  short[] array, int pos, Future<?> future) { this(array, positionOffset(  SHORT_ARRAY_BASE,   SHORT_ARRAY_SHIFT, pos), future, Type.SHORT); }

	@Override
	public boolean validate() {
		throw new Error("FutureFieldAccess instance should not be on a read-set");
	}

	@Override
	public void writeback() {
		nativeFieldAccess().writeback();
	}

	@Override
	@SuppressWarnings("unchecked")
	public FieldAccess nativeFieldAccess() {
		FieldAccess fa = _nativeFieldAccess;
		if (fa != null) return fa;

		try { switch (_type) {
			case  OBJECT: fa = new  ObjectFieldAccess(_instance, _offset, _future.get()); break;
			case BOOLEAN: fa = new BooleanFieldAccess(_instance, _offset, ((Future<Boolean>) _future).get()); break;
			case    BYTE: fa = new    ByteFieldAccess(_instance, _offset, ((Future<Byte>) _future).get()); break;
			case    CHAR: fa = new    CharFieldAccess(_instance, _offset, ((Future<Character>) _future).get()); break;
			case  DOUBLE: fa = new  DoubleFieldAccess(_instance, _offset, ((Future<Double>) _future).get()); break;
			case   FLOAT: fa = new   FloatFieldAccess(_instance, _offset, ((Future<Float>) _future).get()); break;
			case     INT: fa = new     IntFieldAccess(_instance, _offset, ((Future<Integer>) _future).get()); break;
			case    LONG: fa = new    LongFieldAccess(_instance, _offset, ((Future<Long>) _future).get()); break;
			case   SHORT: fa = new   ShortFieldAccess(_instance, _offset, ((Future<Short>) _future).get()); break;
		}} catch (ExecutionException e)   { throw new Error(e); }
		   catch (InterruptedException e) { throw new Error(e); }

		_nativeFieldAccess = fa;
		return fa;
	}

	@Override
	public boolean valueEquals(FieldAccess o) {
		throw new Error("FutureFieldAccess does not implement valueEquals()");
	}
}
