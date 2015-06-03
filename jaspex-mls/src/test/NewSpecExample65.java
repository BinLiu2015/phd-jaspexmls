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

import java.util.Iterator;
import java.util.List;

public class NewSpecExample65 {

	private NewSpecExample65() { }

	private static List<?> dummy0() { return null; }
	private static Object dummy1() { return null; }

	static Object doIt(boolean cond) {
		Object v0 = null;

		if (cond) {
			v0 = dummy1();
			Iterator<?> i = dummy0().iterator();
			while (i.hasNext()) {
				Object v1 = cond ? null : dummy1();

				if (cond) v1.toString();
			}
		}

		return v0;
	}

	public static void main(String[] args) {
		System.out.println("Success!");
	}
}
