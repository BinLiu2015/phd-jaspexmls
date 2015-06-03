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

/** Exemplo que testa a substituição de Thread.currentThread() **/
public class NewSpecExample60 {

	private NewSpecExample60() { }

	private Thread theThread;

	public static void main(String[] args) {
		NewSpecExample60 test = new NewSpecExample60();
		test.theThread = Thread.currentThread();
		test.doStuff();
	}

	private void checkThread() {
		if (theThread != Thread.currentThread()) {
			throw new RuntimeException("Someting wrong happened");
		}
	}

	private void doStuff() {
		checkThread();
		doStuff2();
		checkThread();
		doStuff2();
		checkThread();
		doStuff2();
		checkThread();
		doStuff2();
		checkThread();
		doStuff2();
		checkThread();
		doStuff2();
		checkThread();
	}

	private void doStuff2() {
		checkThread();
		doStuff3();
		checkThread();
		doStuff3();
		checkThread();
		doStuff3();
		checkThread();
		doStuff3();
		checkThread();
	}

	private void doStuff3() {
		checkThread();
	}

}
