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

public class IOUtils {

	public static byte[] readFile(File f) throws IOException {
		FileInputStream input = new FileInputStream(f);
		byte[] fileBytes = readStream(input, true);
		return fileBytes;
	}

	public static void copy(InputStream in, OutputStream out, boolean closeInputStream) throws IOException {
		byte[] buffer = new byte[4096];
		int len = 0;
		while ((len = in.read(buffer)) >= 0) {
			out.write(buffer, 0, len);
		}
		out.flush();
	}

	public static void copy(InputStream in, OutputStream out) throws IOException {
		copy(in, out, false);
	}

	public static byte[] readStream(InputStream in, boolean closeStream) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(in.available());
		copy(in, out, closeStream);
		return out.toByteArray();
	}

	public static byte[] readStream(InputStream in) throws IOException {
		return readStream(in, false);
	}

}
