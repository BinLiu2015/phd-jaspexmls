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

public class AbortExample2 {

	private AbortExample2() { }

	public static void m0() {
		jaspex.Debug.sleep(1000);
		throw new Error("Done");
	}

	public static void m1() {
		jaspex.Debug.sleep(50);
		//System.out.print(".");
	}

	public static void m2() {
		jaspex.Debug.sleep(100);
		//System.out.print(".");
	}

	public static void m3() {
		jaspex.Debug.sleep(150);
		//System.out.print(".");
	}

	public static void m4() {
		jaspex.Debug.sleep(200);
		//System.out.print(".");
	}

	public static void m5() {
		jaspex.Debug.sleep(250);
		//System.out.print(".");
	}

	public static void main(String[] args) {
		m0();
		m1();
		m2();
		m3();
		m4();
		m5();
	}

}
