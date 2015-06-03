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
import asmlib.extra.InvokedMethod;

import java.io.*;
import java.util.*;

//import org.objectweb.asm.*;
//import org.objectweb.asm.commons.*;

import util.StringList;

public class GenerateDotReport extends Report {

	@Override
	public String getName() { return "generateDot"; }

	@Override
	public void execute(StringList args, NavigableSet<Type> examineSet, Map<Type, InfoClass> doneMap)
		throws IOException {
		System.out.println("Outputting dot graph");

		if (args.size() == 1) args.addFirst(examineSet.first().toString());
		if (args.size() != 2) throw new InstrumentationException("syntax: report:" + getName() + " [<starting class>] <output file>");

		NavigableSet<InfoMethod> processingSet = new TreeSet<InfoMethod>();
		Set<InfoMethod> doneSet = new HashSet<InfoMethod>();
		processingSet.add(doneMap.get(Type.fromCommon(args.pollFirst()))
			.getAllMethod("main","([Ljava/lang/String;)V"));

		if (processingSet.first() == null) throw new InstrumentationException("The starting class must have a main() method");

		FileWriter out = new FileWriter(args.pollFirst());
		out.write("digraph NativeGraph {\n\tcompound=true;\n\n");

		Map<Type, InfoClass> reachableMap = NativeGraph.reachableClassesFromMethod(processingSet.first());

		while (!processingSet.isEmpty()) {
			String fontAttributes = ""; //"fontname=\"DejaVu Sans Mono\",fontsize=12,";

			InfoMethod currentMethod = processingSet.pollFirst();
			InfoClass current = currentMethod.infoClass();
			System.out.println("(Pass 3) Examining " + current.type() + "." + currentMethod.name());

			doneSet.add(currentMethod);

			if (!reachableMap.containsKey(current.type())) throw new AssertionError("Class " + current.type() + " not found in the reachableMap");

			out.write("\tsubgraph cluster_" + cleanDot(current.type().toString()) + " {\n");
			out.write("\t\t" + fontAttributes + "label=\"" + current.type() + "\"; style=dashed;\n");
			if (current.isInterface()) out.write("\t\tcolor=blue;\n");

			String fullNameClean = cleanDot(current.type() + "." + currentMethod.name() + cleanDesc(currentMethod.desc()));

			String attributes = fontAttributes;
			if (currentMethod.isNative()) {
				attributes += "style=filled,fontcolor=white,color=darkred,";
			} else if (currentMethod.canInvokeNative(reachableMap)) {
				attributes += "style=filled,color=\".7 .3 1.0\",";
			}

			out.write("\t\t" + fullNameClean + " [label=\"" + currentMethod.name() + "\""
				+ "," + attributes + "]\n");

			List<InvokedMethod> invokedMethods = currentMethod.invokedMethodsSet();
			List<InvokedMethod> intraList = new ArrayList<InvokedMethod>();
			List<InvokedMethod> interList = new ArrayList<InvokedMethod>();

			List<InfoMethod> interInheritList = currentMethod.subclassOverrides(reachableMap);

			// Dividir entre métodos intra-classe e inter-classe
			for (InvokedMethod invoked : invokedMethods) {
				if (invoked.owner().equals(current.type())) intraList.add(invoked);
				else interList.add(invoked);
			}

			printMethodLinks(out, NativeGraph.invokedMethodToInfoMethod(intraList), fullNameClean, processingSet, doneSet, "\t\t", "");
			out.write("\t}\n\n");
			printMethodLinks(out, NativeGraph.invokedMethodToInfoMethod(interList), fullNameClean, processingSet, doneSet, "\t", "\n");
			printMethodLinks(out, interInheritList, fullNameClean, processingSet, doneSet, "\t", "[color=red]\n");
		}

		out.write("}\n");
		out.close();
		System.out.println("All done! Examinados " + doneSet.size() + " métodos.");
	}

	private void printMethodLinks(FileWriter out, List<InfoMethod> list, String fullNameClean, Set<InfoMethod> processingSet, Set<InfoMethod> doneSet, String prepend, String pospend) throws IOException {
		if (!list.isEmpty()) {
			out.write(prepend + fullNameClean + " -> {\n");
			for (InfoMethod m : list) {
				out.write(prepend + "\t" + cleanDot(m.infoClass().type() + "." + m.name() + cleanDesc(m.desc())) + ";\n");
				if (!doneSet.contains(m)) processingSet.add(m);
			}
			out.write(prepend + "} " + pospend + "\n");
		}
	}

	private String cleanDesc(String desc) {
		return desc.replace("/", ".").replace(";L", ",").replace(";)", ")").replace(";", ",").replace("(Ljava", "(java").replace("(Lsun", "(sun").replace(")L", ")");
	}

	private String cleanDot(String dotName) {
		return dotName.replace("$", "＄").replace("<", "≺").replace(">", "≻").replace(".", "⋅").replace("(", "〈").replace(")", "〉").replace(",", "′").replace("[", "⌈").replace("]", "⌉");
	}
}
