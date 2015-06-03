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
import asmlib.Type;
import asmlib.extra.InvokedMethod;

import java.io.*;
import java.util.*;

import org.objectweb.asm.*;

import util.StringList;

public class NativeGraph implements ExamineSetControl {

	public static void main(String[] argsArr) throws IOException, InstrumentationException {
		StringList args = new StringList(argsArr);

		Set<Type> inputExamineSet = new HashSet<Type>();
		while (!args.isEmpty()) {
			String str = args.first();
			if (str.startsWith("report:")) break;
			inputExamineSet.add(Type.fromFilePath(str));
			args.removeFirst();
		}

		if (args.isEmpty() || inputExamineSet.isEmpty()) {
			System.err.println("Usage: java NativeGraph <class1> <class2> ... <classn> report:<reportname> <arguments>");
			System.exit(1);
		}

		String reportType = StringList.split(args.pollFirst(), ":").last();
		Report rpt = Report.get(reportType);

		if (rpt == null) {
			System.err.println("Unknown report type '" + reportType + "'");
			System.exit(1);
		}

		NativeGraph p = new NativeGraph(inputExamineSet);
		rpt.execute(args, new TreeSet<Type>(inputExamineSet), p.doneMap());

		/*Log.debug("Outputting dot graph");
		p.outputDotGraph(inputExamineSet.iterator().next());

		p.printStats(inputExamineSet.iterator().next());*/
	}

	private ClassReader cr;
	private InfoClass currentClass;
	private NavigableSet<Type> examineSet;
	private Map<Type, InfoClass> doneMap;
	private ExamineSetControl esc = this;

	//public NavigableSet<Type> examineSet() { return new TreeSet<Type>(examineSet); }
		// Não existe Collections.unmodifiableNavigableSet(...)
	public Map<Type, InfoClass> doneMap() { return Collections.unmodifiableMap(doneMap); }

	public NativeGraph(Set<Type> inputExamineSet) throws IOException {
		examineSet = new TreeSet<Type>(inputExamineSet);
		doneMap = new HashMap<Type, InfoClass>();

		harvestInformation();
		connectGraph();
	}

	private void harvestInformation() throws IOException {
		// Pass 1
		// Obter informação a partir das classes, individualmente sem as ligar, incluindo métodos
		System.out.println("Pass 1...");
		while (!examineSet.isEmpty()) {
			Type current = examineSet.pollFirst();
			//Log.debug("(Pass 1) Examining " + current);

			try {
				cr = new ClassReader(current.commonName());
			} catch (IOException e) {
				throw new InstrumentationException("Problem opening class " + current);
			}

			currentClass = new InfoClass(cr.getClassName(), cr.getSuperName());

			doneMap.put(current, currentClass);

			// Colocar superclasse no examineSet, se necessário
			esc.add(currentClass.superclassType());

			// Processar classe
			cr.accept(new NGInfoClassAdapter(currentClass, this), 0);

			// Colocar interfaces implementadas no examineSet
			for (Type iface : currentClass.interfaceTypes()) esc.add(iface);
		}
	}

	private void connectGraph() {
		// Pass 2
		System.out.println("Pass 2...");

		// Re-examinar de novo todas as classes, ligando-as às suas superclasses e aos métodos
		// ATENÇÃO: Primeiro têm que se ligar _todas_ as superclasses, e só depois fazer o resto, senão o getAllMethod usado para ligar os métodos não funciona.
		Deque<InfoClass> processingSet;
		processingSet = new ArrayDeque<InfoClass>(doneMap.values());
		while (!processingSet.isEmpty()) {
			InfoClass current = processingSet.pollFirst();

			// Ligar classe à sua superclasse
			Type superclass = current.superclassType();
			if (superclass != null) current.setSuperclass(doneMap.get(superclass));

			// Ligar classe às suas superinterfaces
			for (Type iface : current.interfaceTypes()) {
				current.addInterface(doneMap.get(iface));
			}
		}

		processingSet = new ArrayDeque<InfoClass>(doneMap.values());
		while (!processingSet.isEmpty()) {
			InfoClass current = processingSet.pollFirst();
			//Log.debug("(Pass 2) Examining " + current.name());

			// Ligar métodos
			for (InfoMethod m : current.allMethods()) {
				//Log.debug("\tchecking " + m.name() + m.desc());

				for (InvokedMethod invoked : m.invokedMethodsSet()) {
					Type owner = invoked.owner();
					if (owner.isArray()) owner = Type.fromCommon("java.lang.Object");

					//Log.debug("\t\tconnecting to " + owner + "::" + invoked.name() + invoked.desc());

					InfoClass targetClass = doneMap.get(owner);
					assert (targetClass != null);
					invoked.setMethod(targetClass.getAllMethod(invoked.name(), invoked.desc()));
				}
			}
		}

		System.out.println("All done! Examinadas " + doneMap.size() + " classes.");
	}

	/** Este método retorna um mapa com as classes que são acessíveis a partir de um determinado método.
	  * Ou seja classes que o método refira, ou classes que sejam referidas por métodos chamados a partir
	  * desse método, and so on.
	  * O objectivo é usar esta lista para permitir filtrar o "doneMap" para saber o subset de classes
	  * que é realmente utilizado a partir de um certo ponto.
	  **/
	public static Map<Type, InfoClass> reachableClassesFromMethod(InfoMethod orig) {
		Map<Type, InfoClass> reachableMap = new HashMap<Type, InfoClass>();
		NavigableSet<InfoMethod> processingSet = new TreeSet<InfoMethod>();
		Set<InfoMethod> doneSet = new HashSet<InfoMethod>();
		int resultSize;
		@SuppressWarnings("unused")
		int passes = 0;

		do {
			passes++;
			resultSize = doneSet.size();
			doneSet.clear();

			processingSet.add(orig);
			while (!processingSet.isEmpty()) {
				InfoMethod currentMethod = processingSet.pollFirst();
				InfoClass current = currentMethod.infoClass();
				doneSet.add(currentMethod);
				reachableMap.put(current.type(), current);

				for (InvokedMethod invoked : currentMethod.invokedMethodsSet()) {
					if (!doneSet.contains(invoked.method())) processingSet.add(invoked.method());
				}

				// Infelizmente isto tem que ser repetido até convergir, e por isso é que
				// todo este código é executado até o doneSet estar estável.
				if (!currentMethod.isFinal()) {
					for (InfoMethod m : currentMethod.subclassOverrides(reachableMap)) {
						if (!doneSet.contains(m)) processingSet.add(m);
					}
				}
			}
		} while (resultSize < doneSet.size());

		//Log.debug("Completed reachableClassesFromMethod in " + passes + " passes.");

		return reachableMap;
	}

	public static List<InfoMethod> invokedMethodToInfoMethod(List<InvokedMethod> invokedList) {
		List<InfoMethod> infoList = new ArrayList<InfoMethod>();
		for (InvokedMethod invoked : invokedList) infoList.add(invoked.method());
		return infoList;
	}

	public void add(Type type) {
		if (type == null) return;
		if (type.isArray()) {
			//className = className.stripArray();
			return;
			// Todos os arrays têm os mesmos métodos que Object, basicamente, portanto
			// parece-me que devem ser apenas considerados Object. Nesse caso, de qualquer
			// forma Object já vai ser processado, portanto não fazemos nada
		}
		if (type.isPrimitive()) {
			// Nota: Ordem não pode ser trocada com o if anterior pois um array de primitivos
			// não é considerado primitvo
			return;
			// Creio que isto já nunca acontece, agora que não fazemos stripArray
		}

		if (!doneMap.containsKey(type)) {
			//Log.debug("\t\tadding " + className);
			examineSet.add(type);
		}
	}

}
