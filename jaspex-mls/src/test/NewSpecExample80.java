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

import java.util.Arrays;

public class NewSpecExample80 {
	private static final Object[] myArray = new Object[1000];
	private static final Object[] dummyArray = new Object[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };

	public static void doCopy(int startPos) {
		System.arraycopy(dummyArray, 0, myArray, startPos, dummyArray.length);
	}

	public static void main(String[] args) {
		for (int i = 0; i < myArray.length; i += dummyArray.length) {
			doCopy(i);
		}
		System.out.println(Arrays.asList(myArray));
	}
}
