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

package test;

public class NewSpecExample04 {
	private NewSpecExample04() { }

	// Copiado do EarlyAbortTest
	private static boolean delay(int target) {
		// target 18 -> 5s | 19 -> 15s
		int incr = 1;
		double d = 1, oldD = 0;
		while ( StrictMath.log(d) <= target ) {
			oldD = d;
			d += incr;
			if (oldD == d) incr++;
			//if (d % 10000000 == 0) System.out.println(StrictMath.log(d));
		}
		return true;
	}

	static int doA() {
		@SuppressWarnings("unused")
		boolean b = !delay(17);
		throw new Error("Ooops");
	}

	static int doB() {
		return 42;
	}

	public static void main(String[] args) {
		int i = 0;
		try {
			i = doA();
		} catch (Throwable t) { System.out.println("Exception caught correctly!"); }
		int j = doB() + doB();

		System.out.println(i+j);
	}

}
