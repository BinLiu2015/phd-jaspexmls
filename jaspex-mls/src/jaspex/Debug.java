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

package jaspex;

import java.util.Random;

/** Classe usada para fazer debugging de aplicações.
  *
  * Métodos nesta classe não passam pela STM ou são alterados pelo jaspex (mas objectos que eles referem podem
  * fazê-lo).
  **/
public class Debug {

	private static final Random RANDOM = new Random();

	public static void println(int i) {
		print(Integer.toString(i));
	}

	public static void println(String msg0) {
		print(msg0);
	}

	public static void println(String msg0, String msg1) {
		print(msg0 + " " + msg1);
	}

	private static void print(String s) {
		System.out.println(s);
	}

	public static void sleep(int ms) {
		try { Thread.sleep(ms); } catch (InterruptedException e) { throw new Error(e); }
	}

	public static int randomInt() {
		return RANDOM.nextInt();
	}

	public static int randomInt(int n) {
		return RANDOM.nextInt(n);
	}

	public static String currentThread() {
		return Thread.currentThread().toString();
	}

	// Verifica se a classe recebida tem o field REPLACEMENT_CLASS_MARKER, ou seja, se é uma classe da JDK que
	// foi substituida por uma implementação do JaSPEx
	public static boolean checkReplacementMarker(Class<?> c) {
		try {
			c.getField("REPLACEMENT_CLASS_MARKER");
			return true;
		} catch (SecurityException e) {
			throw new Error(e);
		} catch (NoSuchFieldException e) {
			return false;
		}
	}

}
