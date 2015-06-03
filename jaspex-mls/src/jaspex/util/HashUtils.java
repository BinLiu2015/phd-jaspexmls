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

package jaspex.util;

import java.io.*;
import java.security.*;
import java.math.BigInteger;

public class HashUtils {

	public static String hashFile(File f) {
		try {
			return hashBytesToString(md5().digest(IOUtils.readFile(f)));
		} catch (IOException e) { throw new Error(e); }
	}

	private static void hashSubtree(File dir, MessageDigest md) throws IOException {
		if (!dir.isDirectory()) throw new RuntimeException("Argument is not a directory");

		for (File f : dir.listFiles()) {
			if (f.isFile()) md.update(IOUtils.readFile(f));
			else if (f.isDirectory()) hashSubtree(f, md);
		}
	}

	public static byte[] hashSubtree(File directory) {
		try {
			MessageDigest md = md5();
			hashSubtree(directory, md);
			return md.digest();
		} catch (IOException e) { throw new Error(e); }
	}

	public static String hashString(String s) {
		return hashBytesToString(md5().digest(s.getBytes()));
	}

	public static String hashBytesToString(byte[] hashBytes) {
		return String.format("%032x", new BigInteger(1, hashBytes));
	}

	public static MessageDigest md5() {
		try {
			return MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) { throw new Error(e); }
	}

}
