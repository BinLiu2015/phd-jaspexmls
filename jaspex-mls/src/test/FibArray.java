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

import java.math.BigInteger;

public class FibArray {

	private static Integer fib(int n) {
		BigInteger v1 = BigInteger.ZERO;
		BigInteger v2 = BigInteger.ONE;
		BigInteger res = (n == 1) ? BigInteger.ONE : BigInteger.ZERO;
		for (int i = 2; i <= n; i++) {
			res = v2.add(v1);
			v1 = v2;
			v2 = res;
		}
		return res.bitLength();
	}

	public static void main(String[] args) {
		int first = Integer.parseInt(args[0]);
		int last = Integer.parseInt(args[1]);

		Integer[] array = new Integer[last-first + 1];

		// Compute fib values
		for (int i = first; i <= last; i++) {
			array[i-first] = fib(i);
		}

		// Print them
		for (int i = first; i <= last; i++) {
			System.out.println("fib(" + i + ") = " + array[i-first]);
		}
	}

}
