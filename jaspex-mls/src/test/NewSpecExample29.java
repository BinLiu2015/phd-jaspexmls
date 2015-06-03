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

// Inspirado por problema no jscheme

public class NewSpecExample29 {

	private NewSpecExample29() { }

	private static boolean a() { return false; }

	private static Object m() {
		int i = 0;
		boolean x;
		do {
			x = a();
			if (i == 0) x = true;
			if (i == 0) x = a() && (i != 0);
		} while (x);
		if (i == 0 && a()) return null;
		try {
			a();
		} catch (Exception e) { }
		return null;
	}

	public static void main(String[] args) {
		m();
		System.out.println("Success!");
	}

}
