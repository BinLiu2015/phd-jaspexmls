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

class NS57_TestA {
	NS57_TestA() { }

	private void m() {
		System.out.println("A.m()");
	}

	public void runM() {
		m();
	}

	// Testar modificação ao <clinit>
	static {
		new NS57_TestA().m();
	}
}

class NS57_TestB extends NS57_TestA {
	NS57_TestB() { }

	private void m() {
		System.out.println("B.m()");
	}

	public void runM_B() {
		m();
	}
}

class NS57_TestC extends NS57_TestB {
	NS57_TestC() { }

	public void m() {
		System.out.println("C.m()");
	}
}

public class NewSpecExample57 {

	private NewSpecExample57() { }

	public static void main(String[] args) {
		new NS57_TestC().m();
		new NS57_TestC().runM_B();
		new NS57_TestC().runM();
	}

}
