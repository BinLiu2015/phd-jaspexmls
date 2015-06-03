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

package jaspex.stm;

/** It seems that most (all?) builds of Hotspot for Java 6 have a bug in the C2 compiler when attempting to
  * compile a class that references the original staticFieldBase that Unsafe returns.
  *
  * To hopefully temporarily work around this issue, we wrap that object in an instance of StaticFieldBase,
  * that seems to cause no issue.
  *
  * Another solution to this issue (employed by deuce) is to not mark the field where the staticFieldBase
  * is stored as final.
  *
  * Simple testcase for this bug:
  * public class UnsafeBugTest {
  * 	public static void doSomething(Object o) { }
  * 	private static final Object _staticFieldBase =
  * 		jaspex.stm.Transaction.staticFieldBase(UnsafeBugTest.class);
  * 	public static void main(String[] args) {
  * 		int i = 0;
  * 		while ( i <= Integer.MAX_VALUE ) {
  * 			doSomething(_staticFieldBase);
  * 			i++;
  * 		}
  * 	}
  * }
  **/
public final class StaticFieldBase {
	final Object _staticFieldBase;

	StaticFieldBase(Object staticFieldBase) {
		_staticFieldBase = staticFieldBase;
	}

	@Override
	public int hashCode() {
		return System.identityHashCode(_staticFieldBase);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof StaticFieldBase && ((StaticFieldBase) o)._staticFieldBase == _staticFieldBase;
	}
}
