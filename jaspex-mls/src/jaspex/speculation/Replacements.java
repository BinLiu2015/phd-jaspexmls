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

package jaspex.speculation;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import jaspex.speculation.nsruntime.ContSpeculationControl;
import jaspex.speculation.runtime.SpeculationControl;
import jaspex.stm.Transaction;

/** Classe que contém versões JaSPEx-friendly de métodos da JDK, que são substituídas no programa pelo
  * MethodReplacerMethodVisitor.
  *
  * Nota IMPORTANTE: Esta classe está listada como transaccional no ClassFilter. Ou seja, se a operação a
  * executar não for transaccional, DEVE SER CHAMADO O nonTransactionalActionAttempted() MANUALMENTE!
  **/
public final class Replacements {

	public static void java_lang_System_exit(int status) {
		SpeculationControl.nonTransactionalActionAttempted("(Replacement) Invoked System.exit()");
		ContSpeculationControl.terminate(null, true);
		throw new AssertionError("Should never happen");
	}

	public static Class<?> java_lang_Class_forName(String className, boolean initialize, ClassLoader loader)
		throws ClassNotFoundException {
		SpeculationControl.nonTransactionalActionAttempted("(Replacement) Invoked Class.forName()");
		if (loader instanceof SpeculativeClassLoader) {
			return Class.forName(className, initialize, loader);
		} else {
			throw new Error();
		}
	}

	private static final class DummyReplacementThread extends Thread {
		private static final Thread INSTANCE = new DummyReplacementThread();
		private DummyReplacementThread() {
			setContextClassLoader(SpeculativeClassLoader.INSTANCE);
		}
	};

	public static Thread java_lang_Thread_currentThread() {
		return DummyReplacementThread.INSTANCE;
	}

	public static ClassLoader java_lang_ClassLoader_getSystemClassLoader() {
		return SpeculativeClassLoader.INSTANCE;
	}

	/*public static sun.misc.Unsafe sun_misc_Unsafe_getUnsafe() {
		return jaspex.util.Unsafe.UNSAFE;
	}*/

	public static String java_lang_String_valueOf(Object o) {
		if (o instanceof String) return o.toString();
		SpeculationControl.nonTransactionalActionAttempted("(Replacement) Invoked String.valueOf()");
		return String.valueOf(o);
	}

	/** Usado em colaboração com o ProcessedReplacements.getGarbageCollectorMXBeans. Este
	  * método faz todo o trabalho, sendo que o outro apenas tem a assinatura correcta
	  * (uma List carregada pelo SpeculativeClassLoader).
	  **/
	public static Object convertGarbageCollectorMXBeans() {
		java.util.List<java.lang.management.GarbageCollectorMXBean> l =
			java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();
		try {
			Object arrayList =
				SpeculativeClassLoader.INSTANCE.loadClass("java.util.ArrayList").newInstance();
			java.lang.reflect.Method add = arrayList.getClass().getMethod("add", Object.class);
			for (Object o : l) add.invoke(arrayList, o);

			return arrayList;
		} catch (ClassNotFoundException e)    { throw new Error(e); }
		  catch (InstantiationException e)    { throw new Error(e); }
		  catch (IllegalAccessException e)    { throw new Error(e); }
		  catch (SecurityException e)         { throw new Error(e); }
		  catch (NoSuchMethodException e)     { throw new Error(e); }
		  catch (IllegalArgumentException e)  { throw new Error(e); }
		  catch (InvocationTargetException e) { throw new Error(e); }
	}

	public static <T> Collection<T> java_util_Collections_synchronizedCollection(Collection<T> o) {
		return o;
	}

	public static <T> List<T> java_util_Collections_synchronizedList(List<T> o) {
		return o;
	}

	public static <K,V> Map<K,V> java_util_Collections_synchronizedMap(Map<K,V> o) {
		return o;
	}

	public static <T> Set<T> java_util_Collections_synchronizedSet(Set<T> o) {
		return o;
	}

	public static <K,V> SortedMap<K,V> java_util_Collections_synchronizedSortedMap(SortedMap<K,V> o) {
		return o;
	}

	public static <T> SortedSet<T> java_util_Collections_synchronizedSortedSet(SortedSet<T> o) {
		return o;
	}

	public static void java_lang_System_arraycopy(
		Object s /*src*/, int sP /*srcPos*/, Object d /*dest*/, int dP /*destPos*/, int l /*length*/) {

		if (s == null || d == null) throw new NullPointerException();
		if (sP < 0 || dP < 0 || l < 0) throw new IndexOutOfBoundsException();
		if (l == 0) return;

		if (s == d && sP < dP && sP + l >= dP) {
			/* If the src and dest arguments refer to the same array object, then the copying
			 * is performed as if the components at positions srcPos through srcPos+length-1 were
			 * first copied to a temporary array with length components and then the contents of
			 * the temporary array were copied into positions destPos through destPos+length-1 of
			 * the destination array.
			 */
			// Quando sP >= dP não existe problema, já que nunca sobrepomos elementos que vamos precisar
			// Quando sP < dP e sP + l < dP, também não vamos escrever em cima de elementos repetidos
			// Apenas no caso restante é que precisamos de fazer uma cópia de parte/todo o array antes
			throw new Error("FIXME: Not implemented yet");
		}

		       if (s instanceof boolean[] && d instanceof boolean[]) { arraycopy((boolean[]) s, sP, (boolean[]) d, dP, l);
		} else if (s instanceof    byte[] && d instanceof    byte[]) { arraycopy(   (byte[]) s, sP,    (byte[]) d, dP, l);
		} else if (s instanceof    char[] && d instanceof    char[]) { arraycopy(   (char[]) s, sP,    (char[]) d, dP, l);
		} else if (s instanceof   short[] && d instanceof   short[]) { arraycopy(  (short[]) s, sP,   (short[]) d, dP, l);
		} else if (s instanceof     int[] && d instanceof     int[]) { arraycopy(    (int[]) s, sP,     (int[]) d, dP, l);
		} else if (s instanceof    long[] && d instanceof    long[]) { arraycopy(   (long[]) s, sP,    (long[]) d, dP, l);
		} else if (s instanceof   float[] && d instanceof   float[]) { arraycopy(  (float[]) s, sP,   (float[]) d, dP, l);
		} else if (s instanceof  double[] && d instanceof  double[]) { arraycopy( (double[]) s, sP,  (double[]) d, dP, l);
		} else if (s instanceof  Object[] && d instanceof  Object[]) { arraycopy( (Object[]) s, sP,  (Object[]) d, dP, l);
		} else throw new ArrayStoreException();
	}

	private static void arraycopy(boolean[] src, int srcPos, boolean[] dest, int destPos, int length) {
		if (srcPos+length > src.length || destPos+length > dest.length) throw new IndexOutOfBoundsException();

		for (int i = 0; i < length; i++) {
			Transaction.arrayStoreBoolean(dest, destPos + i, Transaction.arrayLoadBoolean(src, srcPos + i));
		}
	}

	private static void arraycopy(byte[] src, int srcPos, byte[] dest, int destPos, int length) {
		if (srcPos+length > src.length || destPos+length > dest.length) throw new IndexOutOfBoundsException();

		for (int i = 0; i < length; i++) {
			Transaction.arrayStoreByte(dest, destPos + i, Transaction.arrayLoadByte(src, srcPos + i));
		}
	}

	private static void arraycopy(char[] src, int srcPos, char[] dest, int destPos, int length) {
		if (srcPos+length > src.length || destPos+length > dest.length) throw new IndexOutOfBoundsException();

		for (int i = 0; i < length; i++) {
			Transaction.arrayStoreChar(dest, destPos + i, Transaction.arrayLoadChar(src, srcPos + i));
		}
	}

	private static void arraycopy(short[] src, int srcPos, short[] dest, int destPos, int length) {
		if (srcPos+length > src.length || destPos+length > dest.length) throw new IndexOutOfBoundsException();

		for (int i = 0; i < length; i++) {
			Transaction.arrayStoreShort(dest, destPos + i, Transaction.arrayLoadShort(src, srcPos + i));
		}
	}

	private static void arraycopy(int[] src, int srcPos, int[] dest, int destPos, int length) {
		if (srcPos+length > src.length || destPos+length > dest.length) throw new IndexOutOfBoundsException();

		for (int i = 0; i < length; i++) {
			Transaction.arrayStoreInt(dest, destPos + i, Transaction.arrayLoadInt(src, srcPos + i));
		}
	}

	private static void arraycopy(long[] src, int srcPos, long[] dest, int destPos, int length) {
		if (srcPos+length > src.length || destPos+length > dest.length) throw new IndexOutOfBoundsException();

		for (int i = 0; i < length; i++) {
			Transaction.arrayStoreLong(dest, destPos + i, Transaction.arrayLoadLong(src, srcPos + i));
		}
	}

	private static void arraycopy(float[] src, int srcPos, float[] dest, int destPos, int length) {
		if (srcPos+length > src.length || destPos+length > dest.length) throw new IndexOutOfBoundsException();

		for (int i = 0; i < length; i++) {
			Transaction.arrayStoreFloat(dest, destPos + i, Transaction.arrayLoadFloat(src, srcPos + i));
		}
	}

	private static void arraycopy(double[] src, int srcPos, double[] dest, int destPos, int length) {
		if (srcPos+length > src.length || destPos+length > dest.length) throw new IndexOutOfBoundsException();

		for (int i = 0; i < length; i++) {
			Transaction.arrayStoreDouble(dest, destPos + i, Transaction.arrayLoadDouble(src, srcPos + i));
		}
	}

	private static void arraycopy(Object[] src, int srcPos, Object[] dest, int destPos, int length) {
		if (srcPos+length > src.length || destPos+length > dest.length) throw new IndexOutOfBoundsException();

		// No caso especial de objectos temos que manter o invariante do tipo do array, ou seja por exemplo
		// se src for Integer[] e dest for Float[] só podemos atribuir se for null. Em vez de repetir aqui
		// todas essas regras, escrevemos normalmente primeiro os valores no typecheckArray, que tem o mesmo
		// tipo do array de destino, e deixamos para o Java o trabalho de fazer enforce das regras ou lançar
		// ArrayStoreException como especificado na documentação do System.arraycopy.
		Object[] typecheckArray = (Object[]) Array.newInstance(dest.getClass().getComponentType(), 1);

		for (int i = 0; i < length; i++) {
			typecheckArray[0] = Transaction.arrayLoadObject(src, srcPos + i);
			Transaction.arrayStoreObject(dest, destPos + i, typecheckArray[0]);
		}
	}

}
