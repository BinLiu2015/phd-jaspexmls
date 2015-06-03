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

/** Exemplo usado para testar o suporte para RVP **/
public class NewSpecExample54 {

	private NewSpecExample54() { }

	private static int retValue = 10;

	private static int delay(int target) {
		// target 18 -> 5s | 19 -> 15s
		int incr = 1;
		double d = 1, oldD = 0;
		while ( StrictMath.log(d) <= target ) {
			oldD = d;
			d += incr;
			if (oldD == d) incr++;
		}

		/*int v = jaspex.Debug.randomInt(10000);
		jaspex.Debug.println("returning: ", Integer.toString(v));
		return v;*/
		return retValue;
	}

	public static void main(String[] args) {

		// Hack para inicializar RVP com valor da 1ª run
		delay(1);

		//jaspex.Debug.sleep(1000);

		int val = delay(18) + delay(18);

		// Nota: Para isto funcionar correctamente, precisa de -agressivervp, senão
		// não será feita especulação da chamada que escreve em v2

		jaspex.Debug.println(Integer.toString(val));
		System.out.println("done -- " + (val));
	}

}
