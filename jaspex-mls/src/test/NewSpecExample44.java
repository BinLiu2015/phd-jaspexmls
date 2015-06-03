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

/** Testcase bug FixFutureMultipleControlFlows, não ocorre com -nolinenumber **/
public class NewSpecExample44 {

	private NewSpecExample44() { }

	static Object m(Object o) { return null; }

	private static void doIt() {
		Object o = null;
		try {
			o = m(null);
		} catch (Exception e ) { }
		try {
			o = m( /* ESTE NEWLINE É QUE FAZ TRIGGER DO BUG */
					null);
		} catch (Exception e ) { }
		if (o != null) return;
		return;
	}

	public static void main(String[] args) {
		doIt();
	}

}
