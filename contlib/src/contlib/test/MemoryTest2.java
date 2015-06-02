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

// Tests for memory leaks if no continuations are used
// After the test finishes, the VM should have minimal memory footprint
public class MemoryTest2 implements Runnable {

	private static Continuation _testContinuation;
	private static int _contCounter;
	private static final int LOOP_ITERS = 50;

	private MemoryTest2() { };

	public void run() {
		do {
			_contCounter++;
			_testContinuation = myCapture();
		} while (_testContinuation.isResumed());
	}

	public static void main(String[] args) {
		Continuation.runWithContinuationSupport(new MemoryTest2());

		for (int i = 0; i < LOOP_ITERS; i++) {
			Continuation.runWithContinuationSupport(_testContinuation);
		}

		if (_contCounter != (LOOP_ITERS+1)) {
			System.out.println("ERROR: Got " + _contCounter + " loop iters, expected " + (LOOP_ITERS+1));
		} else {
			System.out.println("TEST OK");
		}

		System.out.println("Finished test");
		while (true) {
			System.runFinalization();
			System.gc();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) { throw new Error(e); }
			System.out.print(".");
		}
	}

	public static Continuation myCapture() {
		Continuation c = Continuation.capture();
		if (!c.isResumed()) {
			Object last = c.debugBottomRef();
			String objectStr = last.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(last));
			System.out.println(c + " bottom is " + objectStr);
		}
		return c;
	}

}
