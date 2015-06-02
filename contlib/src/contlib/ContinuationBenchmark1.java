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

public class ContinuationBenchmark1 implements Runnable {

	private ContinuationBenchmark1() { }

	private int val = 0;
	private static final int RUNS = 10;
	private static final int MAXCOUNT = 1000000;

	public void run() {
		Continuation continuation = Continuation.capture();
		if (val < MAXCOUNT) {
			val++;
			//if (val % 100000 == 0) System.out.println(val);
			Continuation.resume(continuation);
		}
	}

	public static void main(String[] args) throws InterruptedException {
		for (int i = 0; i < RUNS; i++) {
			long clockBefore = System.currentTimeMillis();
			Continuation.runWithContinuationSupport(new ContinuationBenchmark1());
			long clockAfter = System.currentTimeMillis();
			System.out.println("Run #" + i + " took " + (clockAfter-clockBefore) +
				"ms, avg " + ((float)(clockAfter-clockBefore))/MAXCOUNT + "ms per iteration");
		}
		//while (true) { Thread.sleep(1000); }
	}
}
