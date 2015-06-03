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

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jaspex.Options;
import jaspex.speculation.nsruntime.SpeculationTask;
import jaspex.speculation.nsruntime.Executor.SpeculationTaskWorkerThread;
import jaspex.stm.FieldAccess.Type;

public final class Transaction {

	private static final Logger Log = LoggerFactory.getLogger(Transaction.class);

	/** Map used to reverse map offsets to the field name. Used only for debugging **/
	private static final Map<Class<?>, Map<Long, String>> reverseOffsetLookupMap =
		new HashMap<Class<?>, Map<Long, String>>();
	/** Map used to reverse map field base objects back to their corresponding classes. Used only for debugging **/
	private static final Map<StaticFieldBase, Class<?>> reverseSFBLookupMap =
		new HashMap<StaticFieldBase, Class<?>>();

	/** Used to initialize field offsets **/
	public static int getFieldOffset(Class<?> cls, String fieldName) throws SecurityException {
		try {
			java.lang.reflect.Field f = cls.getDeclaredField(fieldName);
			long offset;
			if ((f.getModifiers() & org.objectweb.asm.Opcodes.ACC_STATIC) != 0) {
				offset = jaspex.util.Unsafe.UNSAFE.staticFieldOffset(f);
			} else {
				offset = jaspex.util.Unsafe.UNSAFE.objectFieldOffset(f);
			}
			assert (offset != 0);

			// Add reverse match to the reverseOffsetLookupMap
			synchronized (Transaction.class) {
				Map<Long, String> offsetToField = reverseOffsetLookupMap.get(cls);
				if (offsetToField == null) {
					offsetToField = new HashMap<Long, String>();
					reverseOffsetLookupMap.put(cls, offsetToField);
				}
				offsetToField.put(offset, fieldName);
			}

			assert (((int) offset) == offset);

			return (int) offset;
		} catch (NoSuchFieldException e) { throw new Error(e); }
	}

	/** Used to initialize the field base object needed when accessing static fields (normal version) **/
	public static Object staticFieldBase(Class<?> cls) {
		return wrappedStaticFieldBase(cls)._staticFieldBase;
	}

	/** Used to initialize the field base object needed when accessing static fields (-staticworkaround version) **/
	@SuppressWarnings("deprecation")
	public static StaticFieldBase wrappedStaticFieldBase(Class<?> cls) {
		Object fieldBase = jaspex.util.Unsafe.UNSAFE.staticFieldBase(cls);
		assert (fieldBase != null);
		StaticFieldBase sfb = new StaticFieldBase(fieldBase);

		synchronized (Transaction.class) {
			reverseSFBLookupMap.put(sfb, cls);
		}

		return sfb;
	}

	// Transaction control API
	public static Transaction current() {
		return ((SpeculationTaskWorkerThread) Thread.currentThread()).currentTransaction();
	}

	private static void setCurrent(Transaction tx) {
		((SpeculationTaskWorkerThread) Thread.currentThread()).setCurrentTransaction(tx);
	}

	public static boolean isInTransaction() {
		return current() != null;
	}

	// Tests if a transaction is active, and if it has done any work or not
	public static boolean isEmpty() {
		Transaction t = current();
		return (t == null) || (t.readSetEmpty() && (t.writeSet == null));
	}

	public static void abort() {
		/*Transaction tx = current();
		if (tx != null) {
			if (tx.abortAction != null) tx.abortAction.runActions();
		}*/
		if (!Options.FASTMODE) assert (current() != null) : Thread.currentThread() + " " + current();
		setCurrent(null);
	}

	public static boolean commit() {
		return current().commitTx();
	}

	// Transactional Reads/Writes API
	// NORMAL LOADS
	public static Object loadObject(Object instance, Object value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((ObjectFieldAccess) tx.tmRead(new ObjectFieldAccess(instance, offset, value)))._value;
	}

	public static boolean loadBoolean(Object instance, boolean value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((BooleanFieldAccess) tx.tmRead(new BooleanFieldAccess(instance, offset, value)))._value;
	}

	public static byte loadByte(Object instance, byte value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((ByteFieldAccess) tx.tmRead(new ByteFieldAccess(instance, offset, value)))._value;
	}

	public static char loadChar(Object instance, char value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((CharFieldAccess) tx.tmRead(new CharFieldAccess(instance, offset, value)))._value;
	}

	public static double loadDouble(Object instance, double value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((DoubleFieldAccess) tx.tmRead(new DoubleFieldAccess(instance, offset, value)))._value;
	}

	public static float loadFloat(Object instance, float value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((FloatFieldAccess) tx.tmRead(new FloatFieldAccess(instance, offset, value)))._value;
	}

	public static int loadInt(Object instance, int value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((IntFieldAccess) tx.tmRead(new IntFieldAccess(instance, offset, value)))._value;
	}

	public static long loadLong(Object instance, long value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((LongFieldAccess) tx.tmRead(new LongFieldAccess(instance, offset, value)))._value;
	}

	public static short loadShort(Object instance, short value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((ShortFieldAccess) tx.tmRead(new ShortFieldAccess(instance, offset, value)))._value;
	}

	// NORMAL STORES
	public static void storeObject(Object instance, Object value, long offset) {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putObject(instance, offset, value); }
		else { tx.tmWrite(new ObjectFieldAccess(instance, offset, value)); }
	}

	public static void storeBoolean(Object instance, boolean value, long offset) {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putBoolean(instance, offset, value); }
		else { tx.tmWrite(new BooleanFieldAccess(instance, offset, value)); }
	}

	public static void storeByte(Object instance, byte value, long offset) {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putByte(instance, offset, value); }
		else { tx.tmWrite(new ByteFieldAccess(instance, offset, value)); }
	}

	public static void storeChar(Object instance, char value, long offset) {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putChar(instance, offset, value); }
		else { tx.tmWrite(new CharFieldAccess(instance, offset, value)); }
	}

	public static void storeDouble(Object instance, double value, long offset) {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putDouble(instance, offset, value); }
		else { tx.tmWrite(new DoubleFieldAccess(instance, offset, value)); }
	}

	public static void storeFloat(Object instance, float value, long offset) {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putFloat(instance, offset, value); }
		else { tx.tmWrite(new FloatFieldAccess(instance, offset, value)); }
	}

	public static void storeInt(Object instance, int value, long offset) {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putInt(instance, offset, value); }
		else { tx.tmWrite(new IntFieldAccess(instance, offset, value)); }
	}

	public static void storeLong(Object instance, long value, long offset) {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putLong(instance, offset, value); }
		else { tx.tmWrite(new LongFieldAccess(instance, offset, value)); }
	}

	public static void storeShort(Object instance, short value, long offset) {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null || (Options.DETECTLOCAL && isLocal(tx, instance)) || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putShort(instance, offset, value); }
		else { tx.tmWrite(new ShortFieldAccess(instance, offset, value)); }
	}

	// FUTURE STORES
	public static void storeFutureObject(Object instance, Future<Object> future, long offset) throws InterruptedException, ExecutionException {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putObject(instance, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(instance, offset, future, Type.OBJECT)); }
	}

	public static void storeFutureBoolean(Object instance, Future<Boolean> future, long offset) throws InterruptedException, ExecutionException {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putBoolean(instance, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(instance, offset, future, Type.BOOLEAN)); }
	}

	public static void storeFutureByte(Object instance, Future<Byte> future, long offset) throws InterruptedException, ExecutionException {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putByte(instance, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(instance, offset, future, Type.BYTE)); }
	}

	public static void storeFutureChar(Object instance, Future<Character> future, long offset) throws InterruptedException, ExecutionException {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putChar(instance, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(instance, offset, future, Type.CHAR)); }
	}

	public static void storeFutureDouble(Object instance, Future<Double> future, long offset) throws InterruptedException, ExecutionException {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putDouble(instance, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(instance, offset, future, Type.DOUBLE)); }
	}

	public static void storeFutureFloat(Object instance, Future<Float> future, long offset) throws InterruptedException, ExecutionException {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putFloat(instance, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(instance, offset, future, Type.FLOAT)); }
	}

	public static void storeFutureInt(Object instance, Future<Integer> future, long offset) throws InterruptedException, ExecutionException {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putInt(instance, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(instance, offset, future, Type.INT)); }
	}

	public static void storeFutureLong(Object instance, Future<Long> future, long offset) throws InterruptedException, ExecutionException {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putLong(instance, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(instance, offset, future, Type.LONG)); }
	}

	public static void storeFutureShort(Object instance, Future<Short> future, long offset) throws InterruptedException, ExecutionException {
		if (instance == null) throw new NullPointerException();
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putShort(instance, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(instance, offset, future, Type.SHORT)); }
	}

	// Alternate versions of transactional Reads/Writes API for -staticworkaround
	// NORMAL LOADS (-staticworkaround)
	public static Object loadObject(StaticFieldBase sfb, Object value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((ObjectFieldAccess) tx.tmRead(new ObjectFieldAccess(sfb._staticFieldBase, offset, value)))._value;
	}

	public static boolean loadBoolean(StaticFieldBase sfb, boolean value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((BooleanFieldAccess) tx.tmRead(new BooleanFieldAccess(sfb._staticFieldBase, offset, value)))._value;
	}

	public static byte loadByte(StaticFieldBase sfb, byte value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((ByteFieldAccess) tx.tmRead(new ByteFieldAccess(sfb._staticFieldBase, offset, value)))._value;
	}

	public static char loadChar(StaticFieldBase sfb, char value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((CharFieldAccess) tx.tmRead(new CharFieldAccess(sfb._staticFieldBase, offset, value)))._value;
	}

	public static double loadDouble(StaticFieldBase sfb, double value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((DoubleFieldAccess) tx.tmRead(new DoubleFieldAccess(sfb._staticFieldBase, offset, value)))._value;
	}

	public static float loadFloat(StaticFieldBase sfb, float value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((FloatFieldAccess) tx.tmRead(new FloatFieldAccess(sfb._staticFieldBase, offset, value)))._value;
	}

	public static int loadInt(StaticFieldBase sfb, int value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((IntFieldAccess) tx.tmRead(new IntFieldAccess(sfb._staticFieldBase, offset, value)))._value;
	}

	public static long loadLong(StaticFieldBase sfb, long value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((LongFieldAccess) tx.tmRead(new LongFieldAccess(sfb._staticFieldBase, offset, value)))._value;
	}

	public static short loadShort(StaticFieldBase sfb, short value, int offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return value;
		return ((ShortFieldAccess) tx.tmRead(new ShortFieldAccess(sfb._staticFieldBase, offset, value)))._value;
	}

	// NORMAL STORES (-staticworkaround)
	public static void storeObject(Object value, StaticFieldBase sfb, long offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putObject(sfb._staticFieldBase, offset, value); }
		else { tx.tmWrite(new ObjectFieldAccess(sfb._staticFieldBase, offset, value)); }
	}

	public static void storeBoolean(boolean value, StaticFieldBase sfb, long offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putBoolean(sfb._staticFieldBase, offset, value); }
		else { tx.tmWrite(new BooleanFieldAccess(sfb._staticFieldBase, offset, value)); }
	}

	public static void storeByte(byte value, StaticFieldBase sfb, long offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putByte(sfb._staticFieldBase, offset, value); }
		else { tx.tmWrite(new ByteFieldAccess(sfb._staticFieldBase, offset, value)); }
	}

	public static void storeChar(char value, StaticFieldBase sfb, long offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putChar(sfb._staticFieldBase, offset, value); }
		else { tx.tmWrite(new CharFieldAccess(sfb._staticFieldBase, offset, value)); }
	}

	public static void storeDouble(double value, StaticFieldBase sfb, long offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putDouble(sfb._staticFieldBase, offset, value); }
		else { tx.tmWrite(new DoubleFieldAccess(sfb._staticFieldBase, offset, value)); }
	}

	public static void storeFloat(float value, StaticFieldBase sfb, long offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putFloat(sfb._staticFieldBase, offset, value); }
		else { tx.tmWrite(new FloatFieldAccess(sfb._staticFieldBase, offset, value)); }
	}

	public static void storeInt(int value, StaticFieldBase sfb, long offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putInt(sfb._staticFieldBase, offset, value); }
		else { tx.tmWrite(new IntFieldAccess(sfb._staticFieldBase, offset, value)); }
	}

	public static void storeLong(long value, StaticFieldBase sfb, long offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putLong(sfb._staticFieldBase, offset, value); }
		else { tx.tmWrite(new LongFieldAccess(sfb._staticFieldBase, offset, value)); }
	}

	public static void storeShort(short value, StaticFieldBase sfb, long offset) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { UNSAFE.putShort(sfb._staticFieldBase, offset, value); }
		else { tx.tmWrite(new ShortFieldAccess(sfb._staticFieldBase, offset, value)); }
	}

	// FUTURE STORES (-staticworkaround)
	public static void storeFutureObject(Future<Object> future, StaticFieldBase sfb, long offset) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putObject(sfb._staticFieldBase, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(sfb._staticFieldBase, offset, future, Type.OBJECT)); }
	}

	public static void storeFutureBoolean(Future<Boolean> future, StaticFieldBase sfb, long offset) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putBoolean(sfb._staticFieldBase, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(sfb._staticFieldBase, offset, future, Type.BOOLEAN)); }
	}

	public static void storeFutureByte(Future<Byte> future, StaticFieldBase sfb, long offset) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putByte(sfb._staticFieldBase, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(sfb._staticFieldBase, offset, future, Type.BYTE)); }
	}

	public static void storeFutureChar(Future<Character> future, StaticFieldBase sfb, long offset) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putChar(sfb._staticFieldBase, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(sfb._staticFieldBase, offset, future, Type.CHAR)); }
	}

	public static void storeFutureDouble(Future<Double> future, StaticFieldBase sfb, long offset) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putDouble(sfb._staticFieldBase, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(sfb._staticFieldBase, offset, future, Type.DOUBLE)); }
	}

	public static void storeFutureFloat(Future<Float> future, StaticFieldBase sfb, long offset) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putFloat(sfb._staticFieldBase, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(sfb._staticFieldBase, offset, future, Type.FLOAT)); }
	}

	public static void storeFutureInt(Future<Integer> future, StaticFieldBase sfb, long offset) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putInt(sfb._staticFieldBase, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(sfb._staticFieldBase, offset, future, Type.INT)); }
	}

	public static void storeFutureLong(Future<Long> future, StaticFieldBase sfb, long offset) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putLong(sfb._staticFieldBase, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(sfb._staticFieldBase, offset, future, Type.LONG)); }
	}

	public static void storeFutureShort(Future<Short> future, StaticFieldBase sfb, long offset) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { UNSAFE.putShort(sfb._staticFieldBase, offset, future.get()); }
		else { tx.tmWrite(new FutureFieldAccess(sfb._staticFieldBase, offset, future, Type.SHORT)); }
	}

	// ARRAY LOADS

	// Nota: Nada impede os arrays de serem tratados pelos métodos normais, sendo que o código de transactificação
	// 	 teria de fazer o inline do cálculo da deslocação no array usando o base e o shift e dar esse valor
	//	 já calculado à nossa API.
	//	 Para as leituras não vejo nenhum problema, para as escritas é que imagino que seria necessária alguma
	//	 manipulação de stack.
	//	 Por outro lado, também o outro espectro da implementação seria possível: criar FieldAccess específicos
	//	 para arrays, em que nunca sequer seria necessária a utilização de unsafe para o seu acesso.
	//	 Não experimentei as alternativas, mas imagino que os overheads sejam similares. TODO?

	public static Object arrayLoadObject(Object[] array, int pos) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return array[pos];
		return ((ObjectFieldAccess) tx.tmRead(new ObjectFieldAccess(array, pos, array[pos])))._value;
	}

	public static boolean arrayLoadBoolean(boolean[] array, int pos) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return array[pos];
		return ((BooleanFieldAccess) tx.tmRead(new BooleanFieldAccess(array, pos, array[pos])))._value;
	}

	public static byte arrayLoadByte(byte[] array, int pos) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return array[pos];
		return ((ByteFieldAccess) tx.tmRead(new ByteFieldAccess(array, pos, array[pos])))._value;
	}

	public static char arrayLoadChar(char[] array, int pos) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return array[pos];
		return ((CharFieldAccess) tx.tmRead(new CharFieldAccess(array, pos, array[pos])))._value;
	}

	public static double arrayLoadDouble(double[] array, int pos) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return array[pos];
		return ((DoubleFieldAccess) tx.tmRead(new DoubleFieldAccess(array, pos, array[pos])))._value;
	}

	public static float arrayLoadFloat(float[] array, int pos) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return array[pos];
		return ((FloatFieldAccess) tx.tmRead(new FloatFieldAccess(array, pos, array[pos])))._value;
	}

	public static int arrayLoadInt(int[] array, int pos) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return array[pos];
		return ((IntFieldAccess) tx.tmRead(new IntFieldAccess(array, pos, array[pos])))._value;
	}

	public static long arrayLoadLong(long[] array, int pos) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return array[pos];
		return ((LongFieldAccess) tx.tmRead(new LongFieldAccess(array, pos, array[pos])))._value;
	}

	public static short arrayLoadShort(short[] array, int pos) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) return array[pos];
		return ((ShortFieldAccess) tx.tmRead(new ShortFieldAccess(array, pos, array[pos])))._value;
	}

	// ARRAY STORES
	public static void arrayStoreObject(Object[] array, int pos, Object value) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { array[pos] = value; return; }
		@SuppressWarnings("unused")
		Object dummy = array[pos];
		tx.tmWrite(new ObjectFieldAccess(array, pos, value));
	}

	public static void arrayStoreBoolean(boolean[] array, int pos, boolean value) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { array[pos] = value; return; }
		@SuppressWarnings("unused")
		boolean dummy = array[pos];
		tx.tmWrite(new BooleanFieldAccess(array, pos, value));
	}

	public static void arrayStoreByte(byte[] array, int pos, byte value) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { array[pos] = value; return; }
		@SuppressWarnings("unused")
		byte dummy = array[pos];
		tx.tmWrite(new ByteFieldAccess(array, pos, value));
	}

	public static void arrayStoreChar(char[] array, int pos, char value) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { array[pos] = value; return; }
		@SuppressWarnings("unused")
		char dummy = array[pos];
		tx.tmWrite(new CharFieldAccess(array, pos, value));
	}

	public static void arrayStoreDouble(double[] array, int pos, double value) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { array[pos] = value; return; }
		@SuppressWarnings("unused")
		double dummy = array[pos];
		tx.tmWrite(new DoubleFieldAccess(array, pos, value));
	}

	public static void arrayStoreFloat(float[] array, int pos, float value) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { array[pos] = value; return; }
		@SuppressWarnings("unused")
		float dummy = array[pos];
		tx.tmWrite(new FloatFieldAccess(array, pos, value));
	}

	public static void arrayStoreInt(int[] array, int pos, int value) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { array[pos] = value; return; }
		@SuppressWarnings("unused")
		int dummy = array[pos];
		tx.tmWrite(new IntFieldAccess(array, pos, value));
	}

	public static void arrayStoreLong(long[] array, int pos, long value) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { array[pos] = value; return; }
		@SuppressWarnings("unused")
		long dummy = array[pos];
		tx.tmWrite(new LongFieldAccess(array, pos, value));
	}

	public static void arrayStoreShort(short[] array, int pos, short value) {
		Transaction tx = Transaction.current();
		if (tx == null || (Options.ALLOWDUMMYTX && tx.isDummy)) { array[pos] = value; return; }
		@SuppressWarnings("unused")
		short dummy = array[pos];
		tx.tmWrite(new ShortFieldAccess(array, pos, value));
	}

	// FUTURE STORES TO ARRAYS
	public static void arrayStoreFutureObject(Object[] array, int pos, Future<Object> future) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { array[pos] = future.get(); return; }
		@SuppressWarnings("unused")
		Object dummy = array[pos];
		tx.tmWrite(new FutureFieldAccess(array, pos, future));
	}

	public static void arrayStoreFutureBoolean(boolean[] array, int pos, Future<Boolean> future) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { array[pos] = future.get(); return; }
		@SuppressWarnings("unused")
		boolean dummy = array[pos];
		tx.tmWrite(new FutureFieldAccess(array, pos, future));
	}

	public static void arrayStoreFutureByte(byte[] array, int pos, Future<Byte> future) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { array[pos] = future.get(); return; }
		@SuppressWarnings("unused")
		byte dummy = array[pos];
		tx.tmWrite(new FutureFieldAccess(array, pos, future));
	}

	public static void arrayStoreFutureChar(char[] array, int pos, Future<Character> future) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { array[pos] = future.get(); return; }
		@SuppressWarnings("unused")
		char dummy = array[pos];
		tx.tmWrite(new FutureFieldAccess(array, pos, future));
	}

	public static void arrayStoreFutureDouble(double[] array, int pos, Future<Double> future) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { array[pos] = future.get(); return; }
		@SuppressWarnings("unused")
		double dummy = array[pos];
		tx.tmWrite(new FutureFieldAccess(array, pos, future));
	}

	public static void arrayStoreFutureFloat(float[] array, int pos, Future<Float> future) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { array[pos] = future.get(); return; }
		@SuppressWarnings("unused")
		float dummy = array[pos];
		tx.tmWrite(new FutureFieldAccess(array, pos, future));
	}

	public static void arrayStoreFutureInt(int[] array, int pos, Future<Integer> future) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { array[pos] = future.get(); return; }
		@SuppressWarnings("unused")
		Object dummy = array[pos];
		tx.tmWrite(new FutureFieldAccess(array, pos, future));
	}

	public static void arrayStoreFutureLong(long[] array, int pos, Future<Long> future) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { array[pos] = future.get(); return; }
		@SuppressWarnings("unused")
		long dummy = array[pos];
		tx.tmWrite(new FutureFieldAccess(array, pos, future));
	}

	public static void arrayStoreFutureShort(short[] array, int pos, Future<Short> future) throws InterruptedException, ExecutionException {
		Transaction tx = Transaction.current();
		if (tx == null) { array[pos] = future.get(); return; }
		@SuppressWarnings("unused")
		short dummy = array[pos];
		tx.tmWrite(new FutureFieldAccess(array, pos, future));
	}

	// Support for -detectlocal
	//public static long localAccessesCount;

	private static boolean isLocal(Transaction tx, Object instance) {
		boolean res = (instance instanceof jaspex.speculation.runtime.Transactional) &&
			((jaspex.speculation.runtime.Transactional) instance).$getOwner() == tx.OWNER_TAG;
		//if (res) localAccessesCount++;
		return res;
	}

	// Used during object initialization to obtain the current transaction's (if any is active) OWNER_TAG
	public static Object getOwnerTag() {
		Transaction tx = current();
		return tx != null ? tx.OWNER_TAG : null;
	}

	// Object used to mark -detectlocal object ownership. Because currently the references to the owner objects
	// are never cleaned up, it's best to use a very small object than using the Transaction or the
	// SpeculationTask as owner tags.
	private final Object OWNER_TAG = new Object();

	// Transaction instance stuff
	// Creates and activates a new transaction
	public Transaction(SpeculationTask speculationTask, boolean isDummy) {
		this.speculationTask = speculationTask;
		this.isDummy = Options.ALLOWDUMMYTX && isDummy;
		setCurrent(this);
	}

	private final SpeculationTask speculationTask;

	// Used for supporting -nttracker
	public NonTransactionalStateTracker nonTransStateTracker;

	private FieldAccess readSet = null;

	// Used only with -readmap
	private HashMap<FieldAccess,FieldAccess> readSetMap;

	private final Iterable<FieldAccess> readSetIterator() {
		return Options.READMAP ? readSetMap.values() : readSet;
	}

	private final boolean readSetEmpty() {
		return Options.READMAP ? (readSetMap == null) : (readSet == null);
	}

	private final boolean isDummy;

	// Used by clientrt / worklist
	// Allows adding code that runs on commit (after validation, before writeback)
	/*
	private TransactionAction commitAction = null;
	private TransactionAction abortAction = null;
	*/

	public static void addCommitAction(TransactionAction ca) {
		throw new Error("Disabled");
		/*Transaction tx = Transaction.current();
		if (tx != null) {
			ca._next = tx.commitAction;
			tx.commitAction = ca;
		} else {
			ca.run();
		}*/
	}

	public static void addAbortAction(TransactionAction ca) {
		throw new Error("Disabled");
		/*Transaction tx = Transaction.current();
		if (tx != null) {
			ca._next = tx.abortAction;
			tx.abortAction = ca;
		}*/
	}

	// Important note:
	// FieldAccess's equals just compares (instance, offset), so it can be used both as a key and as an
	// entry -- NOTE THAT JAVA'S HASHMAP IMPLEMENTATION DOES NOT REPLACE KEYS THAT ARE EQUAL: The key is
	// always the first FieldAccess that was inserted. Only the mapped value changes with further writes
	// to the same field. To access the CORRECT writeSet, always iterate over writeSet.values(),
	// iterating over keySet() YIELDS WRONG VALUES.
	private HashMap<FieldAccess,FieldAccess> writeSet;

	public boolean validate() {
		boolean result = validateTx();
		if (!result && Options.TXABORTSTATS) printStats(true);
		return result;
	}

	private boolean validateTx() {
		// Validate transaction
		if (!readSetEmpty()) {
			int count = 0;
			for (FieldAccess entry : readSetIterator()) {
				if (!entry.validate()) return false;
				count++;
			}
			if (!Options.FASTMODE && count > 1000) {
				Log.debug("Committing Tx with huge readset ({} entries)", count);
			}
		}
		// Validate non-transactional accesses
		if (Options.NTTRACKER &&
			(nonTransStateTracker != null) && !nonTransStateTracker.validate()) return false;

		return true;
	}

	private boolean commitTx() {
		if (!validate()) return false;

		// Futures present in the write-set can trigger a recursive commit, so we clean
		// Transaction.current() before doing any writebacks
		setCurrent(null);

		//if (commitAction != null) commitAction.runActions();

		// Perform writeback
		if (writeSet != null) {
			if (!Options.FASTMODE && writeSet.size() > 1000) {
				Log.debug("Committing Tx with huge writeset ({} entries)", writeSet.size());
			}
			for (FieldAccess entry : writeSet.values()) {
				entry.writeback();
			}
		}

		if (Options.TXSTATS) printStats(false);

		return true;
	}

	protected FieldAccess tmRead(FieldAccess fieldRead) {
		FieldAccess value = null;

		// Check writeSet for field
		if (writeSet != null) {
			value = writeSet.get(fieldRead);
			// Resolve Future, if any
			if (value != null) value = value.nativeFieldAccess();
		}

		if (value == null) {
			value = fieldRead;

			if (!Options.READMAP) {	// Read-set as linked-list
				fieldRead._next = readSet;
				readSet = fieldRead;
			} else {			// Read-set as hashmap
				if (readSetMap == null) readSetMap = new HashMap<FieldAccess,FieldAccess>();

				FieldAccess oldAccess = readSetMap.put(fieldRead, fieldRead);
				// Validate that oldAccess is still the same as fieldRead, otherwise one of them
				// was invalid and the transaction is doomed to fail
				if (oldAccess != null && !oldAccess.valueEquals(fieldRead)) {
					if (Options.TXABORTSTATS) printStats(true);
					SpeculationTask.waitCurrentTransactionCommit(true);
				}

				// Don't let the read-set get carried away
				if (readSetMap.size() > 100000) {
					SpeculationTask.waitCurrentTransactionCommit();
				}
			}
		}

		if (Options.SIGNALEARLYCOMMIT && speculationTask.canCommit()) {
			SpeculationTask.waitCurrentTransactionCommit();
		}

		return value;
	}

	protected void tmWrite(FieldAccess fieldWrite) {
		if (writeSet == null) {
			writeSet = new HashMap<FieldAccess,FieldAccess>();
		}
		writeSet.put(fieldWrite, fieldWrite);

		// TODO: HACK, mas a ideia base é interessante... Como obter algo semelhante que não seja tão hackish?
		if (Options.WSSIZEHACK && writeSet.size() > 1000) {
			SpeculationTask.waitCurrentTransactionCommit();
		}

		if (Options.SIGNALEARLYCOMMIT && speculationTask.canCommit()) {
			SpeculationTask.waitCurrentTransactionCommit();
		}
	}

	private static String offsetToFieldName(Class<?> cls, Long offset) {
		if (cls.isArray() && offset < 0) return "[]";

		Map<Long, String> offsetToFieldMap = reverseOffsetLookupMap.get(cls);
		if (offsetToFieldMap == null) {
			Class<?> superclass = cls.getSuperclass();
			return offsetToFieldName(superclass, offset);
		}

		String fieldName = offsetToFieldMap.get(offset);
		if (fieldName == null) return offsetToFieldName(cls.getSuperclass(), offset);

		return fieldName;
	}

	private static void buildStats(Iterable<FieldAccess> entrySet, String name, StringBuilder output, boolean validate) {
		class FieldStats {
			int accesses;
			int failedValidation;
			boolean isStatic;

			FieldStats(boolean isStatic) {
				this.isStatic = isStatic;
			}
		};

		if (entrySet != null) {
			int size = 0;
			Map<Class<?>, Map<Long, FieldStats>> freqMap =
				new java.util.TreeMap<Class<?>, Map<Long, FieldStats>>(
					new java.util.Comparator<Class<?>>() {
						@Override public int compare(Class<?> c1, Class<?> c2) {
							return c1.getName().compareTo(c2.getName());
						}
					});

			for (FieldAccess entry : entrySet) {
				size++;
				Class<?> targetClass = entry._instance.getClass();
				boolean isStatic = false;
				if (targetClass.getSuperclass() == null && !targetClass.equals(Object.class)) {
					// Special case: entry._instance is a static field base, not a class
					// instance (the class for a static field base behaves strangely)
					isStatic = true;
					targetClass = reverseSFBLookupMap.get(new StaticFieldBase(entry._instance));
				}
				Map<Long, FieldStats> fieldMap = freqMap.get(targetClass);
				if (fieldMap == null) {
					fieldMap = new java.util.TreeMap<Long, FieldStats>();
					freqMap.put(targetClass, fieldMap);
				}
				Long offset = targetClass.isArray() ? -1 : entry._offset;
				FieldStats stats = fieldMap.get(offset);
				if (stats == null) {
					stats = new FieldStats(isStatic);
					fieldMap.put(offset, stats);
				}
				stats.accesses++;
				if (validate && !entry.validate()) {
					stats.failedValidation++;
					GLOBAL_FAILED_ACCESSES.add(entry);
				}
			}

			output.append("\n\t" + name + "Set size: " + size);

			for (Map.Entry<Class<?>, Map<Long, FieldStats>> fEntry : freqMap.entrySet()) {
				Class<?> cls = fEntry.getKey();

				output.append("\n\t\t" + asmlib.Type.fromClass(cls));

				for (Map.Entry<Long, FieldStats> fieldFEntry : fEntry.getValue().entrySet()) {
					long offset = fieldFEntry.getKey();
					FieldStats stats = fieldFEntry.getValue();

					output.append("\n\t\t\t" + offsetToFieldName(cls, offset) +
						(stats.isStatic ? " (static)" : "") +
						" " + stats.accesses +
						(stats.failedValidation > 0 ?
							" (" + stats.failedValidation + " failed validation)" :
							""));
				}
			}
		} else {
			output.append("\n\t(" + name + "Set empty)");
		}
	}

	public void printStats(boolean aborted) {
		StringBuilder output = new StringBuilder();

		output.append((aborted ? "ABORTED" : "COMMITTED") +
			" Transaction Statistics for " + this + " (host " + Thread.currentThread() + ")");
		if (speculationTask != null) output.append("\n\tSource: " + speculationTask);

		if (!readSetEmpty()) buildStats(readSetIterator(), "Read", output, aborted);
		if (writeSet != null) buildStats(writeSet.values(), "Write", output, false);
		if (Options.NTTRACKER && nonTransStateTracker != null) nonTransStateTracker.printTxStats(output);

		Log.info(output.toString());
	}

	/** Global list of all failed FieldAccesses, for debugging **/
	private static final ArrayList<FieldAccess> GLOBAL_FAILED_ACCESSES = new ArrayList<FieldAccess>();

	public static void printGlobalStats() {
		StringBuilder output = new StringBuilder();
		buildStats(GLOBAL_FAILED_ACCESSES, "Global abort stats -- ", output, false);
		Log.info(output.toString());
	}

}
