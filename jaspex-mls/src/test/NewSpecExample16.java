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

public class NewSpecExample16 {
	private NewSpecExample16() { }

	static int doA() { return 42; }

	static void print(int i, long j, Integer k, double l, int m) {
		System.out.println(i * j * k * l * m);
	}

	public static void main(String[] args) {
		int i = 0;
		if (i == 0) {
			i = doA();
		}

		print(i, 6l, 9, 13.0, 3);
	}

}
