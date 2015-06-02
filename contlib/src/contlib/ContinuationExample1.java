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

package contlib;

public class ContinuationExample1 implements Runnable {

	private int val = 5;

	public void run() {
		long clock = System.currentTimeMillis();
		Continuation continuation = Continuation.capture();
		val--;
		System.out.println("Continuation clock=" + clock + " val=" + val);
		if (val > 0) {
			Continuation.resume(continuation);
			throw new AssertionError("Dead code!");
		}
	}

	public static void main(String[] args) {
		Continuation.runWithContinuationSupport(new ContinuationExample1());
	}
}
