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

public class NewSpecExample38 {

	private NewSpecExample38() { }

	public static void main(String[] args) {
		Integer[] arr = new Integer[] { 1, 2, 3 };
		for (int i = 0; i < arr.length; i++) {
			int val = arr[i];
			System.out.println(val);
		}

		Integer[][] otherArr = new Integer[][] { arr, arr, arr };
		Integer[][][] biggerArr = new Integer[][][] { otherArr, otherArr, otherArr };
		int val = biggerArr[0][0][0];
		System.out.println(val);
	}

}
