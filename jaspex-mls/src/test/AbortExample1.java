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

public class AbortExample1 {

	public static void main(String[] args) {
		a();
	}

	@SuppressWarnings("unused")
	public static int a() {
		int v1 = b();
		int v2 = c();
		for (int i = 0; i < 10; i++); // dummy computation
		return v1;
	}

	public static int b() {
		c();
		for (int i = 0; i < 10; i++); // dummy computation
		return 0;
	}

	public static int c() {
		d();
		for (int i = 0; i < 10; i++); // dummy computation
		return 0;
	}

	public static int d() {
		for (int i = 0; i < 10; i++); // dummy computation
		throw new RuntimeException("Teste");
	}

}
