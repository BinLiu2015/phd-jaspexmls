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

public class NewSpecExample62 {

	private NewSpecExample62() { }

	private NewSpecExample62(Object o0, boolean b0, boolean b1) { }
	private Object m() { return null; }

	@SuppressWarnings("unused")
	private void doIt(boolean branch) {
		Object dummy0 = null;

		if (branch) {
			dummy0 = m();
		}

		if (branch) {
			int dummy1 = 0;
		}

		new NewSpecExample62(dummy0, false, branch != true);
	}

	public static void main(String[] args) {
		System.out.println("Success!");
	}

}
