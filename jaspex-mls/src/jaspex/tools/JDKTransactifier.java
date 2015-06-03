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

package jaspex.tools;

import jaspex.speculation.SpeculativeTransformer;
import jaspex.transactifier.Transactifier;
import jaspex.util.IOUtils;

import java.io.*;
import java.util.*;
import java.util.jar.*;

import asmlib.Type;

import util.StringList;

/** Programa com o objectivo de transactificar o rt.jar da JVM **/
public class JDKTransactifier {

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.err.println("JDKTransactifier");
			System.err.println("\tUsage: java jaspex.tools.JDKTransactifier original.jar transformed.jar");
			System.exit(1);
		}

		// Preparar JaSPEx
		jaspex.Jaspex.initialize(new StringList("-fast"));

		JarFile inputJar = new JarFile(args[0]);
		JarOutputStream outputJar = new JarOutputStream(new FileOutputStream(args[1]));

		for (Enumeration<JarEntry> jarEnum = inputJar.entries(); jarEnum.hasMoreElements();) {
			JarEntry entry = jarEnum.nextElement();
			if (entry.isDirectory() || !entry.getName().endsWith(".class")
				|| !shouldModify(Type.fromFilePath(entry.getName()))) {
				// Não modificar outros ficheiros ou directorias

				outputJar.putNextEntry(entry);
				IOUtils.copy(inputJar.getInputStream(entry), outputJar);
			} else {
				// Modificar classe

				// Criar uma nova JarEntry que não inclui size, crc, etc, para que estes
				// sejam recomputados
				System.out.println("Processing " + entry);
				outputJar.putNextEntry(new JarEntry(entry.getName()));
				outputJar.write(transformClass(inputJar.getInputStream(entry), entry));
			}
		}

		inputJar.close();
		outputJar.close();
	}

	private static boolean shouldModify(Type t) {
		//return (t.commonName().startsWith("java.util."));
		return true;
	}

	private static byte[] transformClass(InputStream in, JarEntry entry) throws IOException {
		byte[] classBytes = IOUtils.readStream(in);
		try {
			return new SpeculativeTransformer(true).transform(new Transactifier(classBytes).transform());
		} catch (RuntimeException e) {
			if (e.toString().contains("JSR/RET are not supported with computeFrames option")) {
				System.out.println("Skipped " + entry);
				return classBytes;
			}
			throw e;
		}
	}

}
