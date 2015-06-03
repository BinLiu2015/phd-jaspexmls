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

/** Exemplo criado para testar o suporte para freeze/thaw de especulações **/
public class NewSpecExample52 {

	private static final int DELAY_TARGET = 17;

	private NewSpecExample52() { }

	private static void delay(int target) {
		//jaspex.Debug.println("delay", ((Integer) target).toString());

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

	public static void main(String[] args) {
		delay(DELAY_TARGET);

		for (int i = 0; i < 100; i++) {
			delay(DELAY_TARGET - 4);
		}

		delay(DELAY_TARGET);

		for (int i = 0; i < 100; i++) {
			delay(DELAY_TARGET - 4);
		}

		delay(DELAY_TARGET);
	}

}
