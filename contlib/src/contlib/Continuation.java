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

/** This class provides a wrapper API based on the experimental continuation support in the JVM. **/

/* Implementation notes:
 *   For this to work, sun.misc.Continuation.resume() has been modified to allow resuming a continuation
 *   multiple times.
 *
 *   Great care must be taken when modifying the runWithContinuationSupport code, as memory leaks can
 *   very easily be created by having captured continuations refer to previously resumed continuations.
 *
 *   The old JVM with the original continuation.patch is referred to as vm2011. The new, enhanced one
 *   is referred to as vm2013.
 */
public final class Continuation {

	/** Small class that pairs with runWithContinuationSupport so that continuations can be
	  * caught without creating memory leaks from leaving references to other Continuation objects
	  * on the stack.
	  *
	  * Note that while runWithContinuationSupport() is alive on the stack an instance of
	  * ContinuationRunner attached to it is also live, but the stack frame of the run() method
	  * below is destroyed upon resuming the continuation.
	  *
	  * Due to internal JVM continuation implementation details, sometimes a Continuation also
	  * saves a reference to a ContinuationRunner (so be careful what you pass to Continuation.enter(),
	  * and what is referenced by it).
	  **/
	private final static class ContinuationRunner implements Runnable {
		private Continuation _continuation;

		public ContinuationRunner() { }

		public ContinuationRunner(Continuation c) {
			c._resumed = true;
			_continuation = c;
		}

		public void run() {
			Continuation continuation = _continuation;

			// Clean object so it won't be kept alive by references to the Runner on the stack
			_continuation = null;

			// Current stack frame is destroyed and replaced with the one from the continuation
			continuation._jvmContinuation.resume(continuation);
		}

		public void setNativeContinuation(sun.misc.Continuation c) {
			// c is always null on vm2013
			if (c != null) {
				_continuation = new Continuation(c);
			}
		}
	}

	/** Starts a scope inside of which continuations can be captured and resumed.
	  * Note to clients: I have seen JVM aborts when exceptions get propagated from inside
	  * runWithContinuationSupport() to the outside.
	  * If you see any such issues, try wrapping your runnable with another runnable that catches the
	  * exceptions before they reach runWithContinuationSupport().
	  *
	  * Note that any variables referred to inside this method or s.m.C.enter() will stay live and be
	  * saved by any caught continuations, so we have to be very careful not to leave anything behind
	  * that might cause such a memory leak (see also ContinuationRunner above).
	  **/
	public static void runWithContinuationSupport(Runnable runnable) {
		ContinuationRunner contRunner = new ContinuationRunner();
		contRunner.setNativeContinuation(
			(sun.misc.Continuation) sun.misc.Continuation.enter(runnable, null));

		// Note: Loop never runs on vm2013
		while (contRunner._continuation != null) {
			contRunner.setNativeContinuation(
				(sun.misc.Continuation) sun.misc.Continuation.enter(contRunner, null));
		}
	}

	/** Starts continuation scope and resumes continuation.
	  * See also runWithContinuationSupport(Runnable).
	  **/
	public static void runWithContinuationSupport(Continuation continuation) {
		ContinuationRunner contRunner = new ContinuationRunner(continuation);
		//runWithContinuationSupport(contRunner);

		// Note: Inlined version of runWithContinuationSupport(Runnable)
		// Note: Loop only runs once on vm2013
		while (contRunner._continuation != null) {
			contRunner.setNativeContinuation(
				(sun.misc.Continuation) sun.misc.Continuation.enter(contRunner, null));
		}
	}

	// MemoryTest4 crashes if this version is used, instead of the one above
	/*public static void runWithContinuationSupport(Continuation continuation) {
		runWithContinuationSupport(new ContinuationRunner(continuation));
	}*/

	/** Captures current status of the thread, and returns it in a Continuation object. **/
	public static Continuation capture() {
		sun.misc.Continuation cont = new sun.misc.Continuation();
	        Continuation c = (Continuation) cont.save();
	        // Support for vm2013
	        if (c == null) {
			//hack.resume(new Continuation(hack)); // mimic vm2011 behavior
			c = new Continuation(cont);
	        }
	        return c;
	}

	/** Captures current status of the thread, and returns it in a Continuation object.
	  * This version allows extra arguments to be stored inside the continuation.
	  **/
	public static <E> Continuation capture(E ... extraArgs) {
		sun.misc.Continuation cont = new sun.misc.Continuation();
		if (extraArgs.length > 0) cont.set_data2(extraArgs);
		Continuation c = (Continuation) cont.save();
	        // Support for vm2013
		if (c == null) {
			//cont.resume(new Continuation(cont)); // mimic vm2011 behavior
			c = new Continuation(cont);
		}
	        return c;
	}

	/** Restores continuation. **/
	public static void resume(Continuation continuation) {
	        continuation._resumed = true;
	        continuation._jvmContinuation.resume(continuation);
	}

	// Instance fields for continuation
        private boolean _resumed;
        private final sun.misc.Continuation _jvmContinuation;

        private Continuation(sun.misc.Continuation jvmContinuation) { _jvmContinuation = jvmContinuation; }

        public boolean isResumed() { return _resumed; }

        @SuppressWarnings("unchecked")
        public <E> E[] extraArguments() { return (E[]) _jvmContinuation.data2(); }

	/** USE WITH CARE: Access the stack info saved inside the Continuation **/
	public Object[] getStack() {
		return (Object[]) _jvmContinuation.stack;
	}

	/** Used for debugging. Should never contain a Continuation, otherwise, we got a memory leak.
	  * Note that sometimes it can contain an internal JVM object that is != null but has no toString,
	  * and getClass().getName() returns <unkown>. This happens to the first continuation created
	  * inside a runWithContinuationSupport started with a runnable.
	  **/
	public Object debugBottomRef() {
		Object[] refs = (Object[]) getStack()[0];
		return refs[refs.length-1];
	}

	@Override
	public String toString() {
		return super.toString() + ":" + _jvmContinuation;
	}

}
