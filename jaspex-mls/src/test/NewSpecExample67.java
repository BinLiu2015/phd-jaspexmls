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

import java.io.FileNotFoundException;

public class NewSpecExample67 {

	private NewSpecExample67() { }

	private static class NS67_Inner extends java.io.RandomAccessFile {
		private NS67_Inner() throws FileNotFoundException {
			super("", "");
		}
	}

	@SuppressWarnings("null")
	public static void main(String[] args) {
		NS67_Inner dummy = null;
		try {
			dummy.length();
		} catch (Exception e) { }
		System.out.println("Success!");
	}

}
