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
public class MemoryTest4 implements Runnable {

	private static Continuation _testContinuation;
	private static int _contCounter;
	private static final int LOOP_ITERS = 50;

	private MemoryTest4() {
		System.out.println(toString());
	};

	public void run() {
		_testContinuation = myCapture();
		if (!_testContinuation.isResumed()) return;

		do {
			Continuation c = myCapture();
			if (!c.isResumed()) Continuation.resume(c);
			_contCounter++;
		} while (_contCounter < LOOP_ITERS);
	}

	public static void main(String[] args) {
		Continuation.runWithContinuationSupport(new MemoryTest4());
		Continuation.runWithContinuationSupport(_testContinuation);

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
