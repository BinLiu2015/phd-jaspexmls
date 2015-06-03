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

// Versão original deste teste feita pelo Hugo, obrigada!

public class NewSpecExample21 {

	private NewSpecExample21() { }

	public static void main(String[] args) {
		NewSpecExample21 t = new NewSpecExample21();
		try {
			System.err.println(t.doTest());
		} catch (TestException e) {
			System.err.println("This should never happen");
		}
	}

	public int doTest() throws TestException {
		boolean first = true;
		while (true) {
			try {
				try {
					return inner(first);
				} finally {
					if (first) {
						first = false;
						throw new CommitEx();
					}
				}
			} catch (CommitEx e) {
				// Intentionally empty
			}
		}
	}

	public int inner(boolean first) throws TestException{
		if (first) throw new TestException();
		return 42;
	}

	@SuppressWarnings("serial")
	public class TestException extends Exception { }
	@SuppressWarnings("serial")
	public class CommitEx extends Exception { }

}
