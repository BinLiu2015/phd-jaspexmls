/*
 * contlib: JVM continuations on top of OpenJDK hotspot with custom patches
 * Copyright (C) 2015 Ivo Anjo <ivo.anjo@ist.utl.pt>
 *
 * This file is part of contlib.
 *
 * contlib is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * contlib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with contlib.  If not, see <http://www.gnu.org/licenses/>.
 */

package contlib.test;

import contlib.Continuation;

public class CreationTest implements Runnable {

	private CreationTest() { };

	private boolean expected = false;

	public void run() {
		Continuation c = Continuation.capture();
		if (c.isResumed() != expected) throw new Error();
		if (!expected) {
			expected = true;
			Continuation.resume(c);
		}
		System.out.println("Test succeeded!");
	}

	public static void main(String[] args) {
		Continuation.runWithContinuationSupport(new CreationTest());
	}

}
