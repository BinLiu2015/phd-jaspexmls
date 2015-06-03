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

import java.io.*;
import java.util.*;

import util.StringList;

import static jaspex.util.ShellColor.color;

/** Sistema simples para executar estes em test.* **/
public class SimpleTester {
	private SimpleTester() { }

	private static final List<String> disabledTests = Arrays.asList(
		/* Nunca terminam */
		"test.NewSpecExample23", "test.NewSpecExample24", "test.NewSpecExample25", "test.NewSpecExample26",
		/* Output varia propositadamente (backtrace excepção) */
		"test.NewSpecExample41", "test.NewSpecExample42",
		/* Output varia por hashcode */
		"test.NewSpecExample79", "test.NewSpecExample84",
		/* Teste de opção -removemonitors */
		"test.NewSpecExample53",
		/* Teste de opção -removesync */
		"test.NewSpecExample76"
	);

	private static final List<String> knownToFail = Arrays.asList(
		/* Falha com o -XX:-FailOverToOldVerifier */
		"test.NewSpecExample04", "test.NewSpecExample63", "test.NewSpecExample64",
		/* Outros problemas */
		"test.NewSpecExample35", "test.NewSpecExample62"
	);

	private static final String NORMAL_COMMAND = "java";
	private static final String JASPEX_COMMAND =
		"java jaspex.Jaspex -silent -txstats -noremoveoverspec";

	public static void main(String[] args) throws IOException, InterruptedException {
		StringList tests = new StringList();

		// Testes default
		tests.addAll(ls("src/test/", "NewSpecExample", "test."));

		int i = 0;
		for (String t : tests) {
			String color;
			if (disabledTests.contains(t)) {
				color = "1";
			} else {
				color = "1;37;" +
					(runTest(t) ? "42" : knownToFail.contains(t) ? "43" : "41");
			}
			System.out.print(color(t, color) + " ");
			if ((++i % 4) == 0) System.out.println();
			else System.out.flush();
		}
		System.out.println();
	}

	private static StringList ls(String path, String matchFilter, String classPackage) {
		StringList files = new StringList();
		for (File file : new File(path).listFiles()) {
			if (file.getName().contains(matchFilter)) {
				files.add(classPackage + file.getName().replace(".java", ""));
			}
		}
		Collections.sort(files);
		return files;
	}

	private static boolean runTest(String commandLine) throws IOException, InterruptedException {
		Runtime r = Runtime.getRuntime();
		Process p1 = r.exec(NORMAL_COMMAND + " " + commandLine);
		Process p2 = r.exec(JASPEX_COMMAND + " " + commandLine);

		// stdout == inputstream do Process (!?!?)
		return equalOutput(p1.getInputStream(), p2.getInputStream()) &&
			equalOutput(p1.getErrorStream(), p2.getErrorStream()) &&
			p1.waitFor() == p2.waitFor();
	}

	private static boolean equalOutput(InputStream is1, InputStream is2) throws IOException {
		BufferedReader b1 = new BufferedReader(new InputStreamReader(is1));
		BufferedReader b2 = new BufferedReader(new InputStreamReader(is2));
		String s1 = b1.readLine();
		String s2 = b2.readLine();
		while (s1 != null && s1.equals(s2)) {
			s1 = b1.readLine();
			s2 = b2.readLine();
		}
		return s1 == null || s1.equals(s2);
	}
}
