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

package nativegraph;

import asmlib.*;

import java.io.*;
import java.util.*;

import util.StringList;

public class NativeMethodsTableReport extends Report {

	private static class NMTMethodInfo {
		InfoMethod method;
		int numReferences;
		int type = -1; // -1 unknown : 0 normal : 1 native : 2 canCallNative
	}

	@Override
	public String getName() { return "nativeMethodsTable"; }

	@Override
	public void execute(StringList args, NavigableSet<Type> examineSet, Map<Type, InfoClass> doneMap)
		throws IOException {
		System.out.println("NativeMethodsTable report running...");

		if (args.size() == 0) args.addFirst(examineSet.first().toString());
		if (args.size() != 1) throw new InstrumentationException("syntax: report:" + getName() + " [<starting class>]");

		NavigableSet<InfoMethod> processingSet = new TreeSet<InfoMethod>();
		Set<InfoMethod> doneSet = new HashSet<InfoMethod>();
		processingSet.add(doneMap.get(Type.fromCommon(args.pollFirst()))
			.getAllMethod("main","([Ljava/lang/String;)V"));

		Map<Type, InfoClass> reachableMap = NativeGraph.reachableClassesFromMethod(processingSet.first());

		NavigableMap<String, NMTMethodInfo> methodMap = new TreeMap<String, NMTMethodInfo>();

		NMTMethodInfo mInfo;

		while (!processingSet.isEmpty()) {
			InfoMethod currentMethod = processingSet.pollFirst();
			InfoClass current = currentMethod.infoClass();
			System.out.println("(Pass 3) Examining " + current.type() + "." + currentMethod.name());

			doneSet.add(currentMethod);

			if (!reachableMap.containsKey(current.type())) throw new AssertionError("Class " + current.type() + " not found in the reachableMap");

			if (!methodMap.containsKey(currentMethod.fullName())) {
				mInfo = new NMTMethodInfo();
				mInfo.method = currentMethod;
				methodMap.put(currentMethod.fullName(), mInfo);
			}

			mInfo = methodMap.get(currentMethod.fullName());
			if (currentMethod.isNative()) {
				mInfo.type = 1;
			} else if (currentMethod.canInvokeNative(reachableMap)) {
				mInfo.type = 2;
			} else {
				mInfo.type = 0;
			}

			List<InfoMethod> analyseList = NativeGraph.invokedMethodToInfoMethod(currentMethod.invokedMethodsSet());
			analyseList.addAll(currentMethod.subclassOverrides(reachableMap));

			for (InfoMethod m : analyseList) {
				if (!doneSet.contains(m)) processingSet.add(m);

				if (!methodMap.containsKey(m.fullName())) {
					mInfo = new NMTMethodInfo();
					mInfo.method = m;
					methodMap.put(m.fullName(), mInfo);
				}
				methodMap.get(m.fullName()).numReferences++;
			}
		}

		NavigableSet<String> resultSet = new TreeSet<String>(methodMap.navigableKeySet());
		List<NMTMethodInfo> normalList = new ArrayList<NMTMethodInfo>();
		List<NMTMethodInfo> nativeList = new ArrayList<NMTMethodInfo>();
		List<NMTMethodInfo> canCallNativeList = new ArrayList<NMTMethodInfo>();
		while (!resultSet.isEmpty()) {
			String methodName = resultSet.pollFirst();
			mInfo = methodMap.get(methodName);
			if (mInfo.type == 0) normalList.add(mInfo);
			else if (mInfo.type == 1) nativeList.add(mInfo);
			else if (mInfo.type == 2) canCallNativeList.add(mInfo);
			else throw new InstrumentationException("Unexpected mInfo.type value (" + mInfo.type + ") for method " + mInfo.method.fullName());
		}

		printList(nativeList, "Native");
		printList(canCallNativeList, "Can Call Native");
		printList(normalList, "Normal");
	}

	private void printList(List<NMTMethodInfo> lst, String title) {
		System.out.println("");
		System.out.println(title + " Methods");
		System.out.println("\tRefs.\tMethod");
		for (NMTMethodInfo mInfo : lst) {
			System.out.println("\t" + mInfo.numReferences + "\t" + mInfo.method.fullJavaName());
		}
	}

}
