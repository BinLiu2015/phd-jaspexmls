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

/** Teste recursão com grande profundidade da stack **/
public class NewSpecExample77 {

	private NewSpecExample77() { }

	private final int[] testArr = new int[3000];

	public Object doIt(int i) {
		if (i >= testArr.length) return null;
		doIt(i+1);
		testArr[i] = 1;
		return null;
	}

	public static void main(String[] args) {
		new NewSpecExample77().doIt(0);
	}

}
