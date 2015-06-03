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

/** Exemplo criado para testar o -signalearlycommit (NOTA: Usa skiplist!) **/
public class NewSpecExample51 {

	private static final int DELAY_TARGET = 16;

	private NewSpecExample51() { }

	// Este método precisa de estar na skiplist-builtin, ou numa passada manualmente
	private static void delay(int target) {
		// target 18 -> 5s | 19 -> 15s
		int incr = 1;
		double d = 1, oldD = 0;
		while ( StrictMath.log(d) <= target ) {
			oldD = d;
			d += incr;
			if (oldD == d) incr++;
			//if (d % 10000000 == 0) System.out.println(StrictMath.log(d));
		}
	}

	static void xpto() {
		delay(DELAY_TARGET-1);
	}

	static int x;

	static void doIt() {
		delay(DELAY_TARGET);
		x = 42;
		delay(DELAY_TARGET+2);
	}

	public static void main(String[] args) {
		xpto();
		doIt();
	}

}
