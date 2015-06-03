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

package jaspex.speculation.newspec;

import jaspex.ClassFilter;
import jaspex.speculation.FixPrivateMethodAccessMethodVisitor;

import java.util.*;
import java.io.*;
import java.net.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.StringList;

/** Lista simples de métodos a não escolher para especulação **/
public class SpeculationSkiplist {

	private static final Logger Log = LoggerFactory.getLogger(SpeculationSkiplist.class);

	private static final String BUILTIN_LIST =
		SpeculationSkiplist.class.getResource("skiplist-builtin.txt").getFile();

	private static final String TAG_USEDUMMY = "usedummy: ";
	private static final String TAG_ALLOW = "allow: ";
	private static final String TAG_SKIPFIELDTX = "skipfieldtx: ";

	// Tal como no ClassFilter, esta lista suporta packages, classes, métodos parciais, etc.
	private static final List<String> speculationSkiplist = new ArrayList<String>();
	private static final List<String> useDummyTransactionList = new ArrayList<String>();
	private static final List<String> allowList = new ArrayList<String>(); // Whitelist
	private static final List<String> skipFieldTxList = new ArrayList<String>();

	static {
		Exception error = null;
		try {
			List<String> inputFiles = new ArrayList<String>();

			if (BUILTIN_LIST == null) {
				Log.error("Could not load built-in skiplist");
			} else {
				inputFiles.add(BUILTIN_LIST);
			}

			if (jaspex.Options.SKIPSPECULATION != null) {
				inputFiles.addAll(StringList.split(jaspex.Options.SKIPSPECULATION, ","));
			}

			for (String fileName : inputFiles) {
				BufferedReader br = new BufferedReader(new FileReader(new File(fileName)));
				String line;
				while ((line = br.readLine()) != null) {
					line = line.trim();
					String lineLower = line.toLowerCase();
					if (line.isEmpty() || line.startsWith("#")) continue;
					if (lineLower.startsWith(TAG_USEDUMMY)) {
						useDummyTransactionList.add(line.substring(TAG_USEDUMMY.length()));
					} else if (lineLower.startsWith(TAG_ALLOW)) {
						allowList.add(line.substring(TAG_ALLOW.length()));
					} else if (lineLower.startsWith(TAG_SKIPFIELDTX)) {
						skipFieldTxList.add(line.substring(TAG_SKIPFIELDTX.length()));
					} else {
						speculationSkiplist.add(line);
					}
				}
				br.close();
			}
		} catch (FileNotFoundException e) { error = e; }
		  catch (MalformedURLException e) { error = e; }
		  catch (IOException e) 	  { error = e; }
		if (error != null) throw new Error("Error loading speculation skip list", error);
	}

	private static boolean checkSkip(List<String> methodList, jaspex.speculation.InvokedMethod m) {
		String methodName = m.name().replace("$speculative", "");
		methodName = FixPrivateMethodAccessMethodVisitor.stripPrivate(methodName);
		return ClassFilter.listContainsMethod(methodList, m.owner(), methodName, m.desc());
	}

	public static boolean skipMethod(jaspex.speculation.InvokedMethod m) {
		return !checkSkip(allowList, m) && checkSkip(speculationSkiplist, m);
	}

	public static boolean useDummyTransaction(jaspex.speculation.InvokedMethod m) {
		return checkSkip(useDummyTransactionList, m);
	}

	public static boolean skipFieldTx(asmlib.Type ownerClass, String fieldName, asmlib.Type fieldType) {
		return ClassFilter.listContainsMethod(skipFieldTxList, ownerClass, fieldName, " " + fieldType.bytecodeName());
	}

}
