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

public class NewSpecExample40 {

	private NewSpecExample40() { }

	NewSpecExample40 o;
	NewSpecExample40 m1() { return null; }
	NewSpecExample40 m2() { return null; }
	double m3() { return 0.0; }

	@SuppressWarnings("unused")
	private void doIt() {
		while (o != null) {
			NewSpecExample40 test = m1();
			o = o.m2();
		}
		o.m3();
	}

	public static void main(String[] args) {
		try {
			new NewSpecExample40().doIt();
			System.out.println("Something went wrong");
		} catch (NullPointerException e) {
			System.out.println("Success!");
		}
	}
}
