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

package jaspex;

import java.util.*;

import asmlib.*;

/** "Filtro" que é utilizado para determinar que classes não devem ser transactificadas, sendo usadas as
  * versões normais da JVM.
  *
  * Também mantém uma lista de classes e métodos que podem ser usados sem precisarem de ser
  * considerados acções não-transaccionais.
  **/
public final class ClassFilter {

	// Packages nas quais classes não devem ser transactificadas
	private static final String[] prefixes = { "java.", "sun.", "jaspex.", "clientrt.",
		"org.eclipse.tptp.", "com.yourkit.runtime.", "org.netbeans.lib.profiler.",
		/* Pode ser transactificado, mas vem no rt.jar */
		"javax.",
		/* Referido pelo javax.xml.parsers.SAXParser */
		"org.xml.sax." };

	// No caso de Options.TRANSACTIFYJDK estar activa, que classes é que aceitamos modificar (excepções
	// à lista acima)
	private static final List<String> allowedJDKTransactification = initList(
		"java.util.", Iterable.class, StringBuilder.class, ThreadLocal.class, jaspex.speculation.ProcessedReplacements.class );

	// Excepções à lista acima (um pouco confuso, mas necessário)
	// A lista prefixes faz um ban global a packages. Dentro dessas, com TRANSACTIFYJDK queremos permitir
	// algumas, essas estão no allowedJDKTransactification. Mas para o allowedJDKTransactification não
	// ter que ser muito compreensivo, este de novo pode listar packages inteiras. Mas, mesmo assim, dentro
	// dessas packages podem existir classes NUNCA queremos transactificar, mesmo com o TRANSACTIFYJDK
	// activo, e esses estão na allowedJDKTransactificationExceptions.
	// Ou seja, a ordem de importância dos overrides é:
	// allowedJDKTransactificationExceptions > allowedJDKTransactification > prefixes
	// A decisão de uma classe é baseada em qual destas 3 listas a classe é encontrada primeiro,
	// independentemente de estar nas outras.
	private static final List<String> allowedJDKTransactificationExceptions = initList(
		"java.util.concurrent.", "java.util.zip.", java.util.Scanner.class, java.util.Locale.class );

	public static boolean isTransactifiable(Type type) {
		if (Options.TRANSACTIFYJDK) {
			// Excepções
			if (type.commonName().startsWith("java.util.concurrent.ConcurrentHashMap")) {
				// Nota: Tem que ser startsWith para contar com classes internas
				return true;
			}

			for (String s : allowedJDKTransactificationExceptions) if (type.commonName().startsWith(s)) return false;
			for (String s : allowedJDKTransactification) if (type.commonName().startsWith(s)) return true;
		}
		for (String s : prefixes) if (type.commonName().startsWith(s)) return false;
		return true;
	}

	// NOTA: Para uma classe ou método ser whitelisted duas coisas devem acontecer:
	// - Não deve ter estado mutável
	// - Não deve fazer computação sobre coisas recebidas em argumentos que possam ser mutáveis
	// e não-completamente transactificados. Isto inclui interfaces da JDK que possam ter implementações
	// não-transaccionais (como uma List), e, _muito importante_ ARRAYS!
	// - Deve ser final / Não deve ter uma subclasse na JDK que possa fazer os dois items acima
	// Para perceber porquê, basta considerar que a thread em program-order está a mutar uma ArrayList
	// ou um array: é a program order thread, pode fazê-lo à vontade.
	// Mas se concurrentemente fizermos Arrays.copyOf(array) ou new ArrayList(lista) dentro de uma especulação,
	// as leituras feitas por estes métodos não são validadas e portanto podem existir commits inválidos.
	// Logo, esses dois métodos não podem ser permitidos. Por outro lado Arrays.asList(array) ou
	// Collections.unmodifiableList(lista) não põem qualquer problema.
	//
	// Para simplificar as pequenas excepções que existem em classes comuns como String, a friendly whitelist
	// de classes não serve para todos os métodos da classe: Se algum método receber um array ou algo para qual
	// isTransactifiable() retorne false, então não é permitido na mesma. Para o permitir, estes devem ser
	// adicionados explicitamente à unconditionalWhitelist (de notar que esta serve tanto para classes, como
	// para prefixos de métodos, métodos sem argumentos, ou o método com uma assinatura exacta).
	private static final List<String> friendlyClassWhitelist = initList(
		Boolean.class, Byte.class, Character.class, Short.class,
		Integer.class, Long.class, Float.class, Double.class,
		String.class, Math.class, StrictMath.class, java.math.BigInteger.class,
		Class.class, StackTraceElement.class,
		java.util.regex.Pattern.class,
		java.net.URI.class,
		"org.eclipse.tptp.martini.CGProxy",
		"com.yourkit.runtime.Callback" );

	private static final List<String> unconditionalWhitelist = initList(
		"java.lang.Object.<init>", "java.lang.Object.getClass",
		"java.lang.String.equals",
		"java.lang.StringBuilder.<init>()V", "java.lang.StringBuilder.<init>(I)V", "java.lang.StringBuilder.<init>(Ljava/lang/String;)V",
		"java.lang.StringBuffer.<init>()V", "java.lang.StringBuffer.<init>(I)V", "java.lang.StringBuffer.<init>(Ljava/lang/String;)V",
		"java.lang.Throwable.<init>",
		"java.lang.Exception.<init>",
		"java.lang.RuntimeException.<init>",
		"java.lang.Error.<init>",
		"java.lang.System.identityHashCode",
		"java.lang.System.gc()",
		"java.lang.Runtime.gc()",
		"java.lang.Thread.yield()",
		"java.lang.Thread.getContextClassLoader()",
		"java.lang.reflect.Array.newInstance(Ljava/lang/Class;I)",
		"java.lang.ThreadLocal.<init>",
		java.lang.Enum.class,

		"java.util.AbstractMap.<init>", "java.util.AbstractQueue.<init>",
		"java.util.AbstractSet.<init>", "java.util.AbstractList.<init>",
		"java.util.ArrayList.<init>()V", "java.util.ArrayList.<init>(I)V",
		"java.util.ArrayDeque.<init>()V", "java.util.ArrayDeque.<init>(I)V",
		"java.util.HashSet.<init>()V", "java.util.HashSet.<init>(I)V", "java.util.HashSet.<init>(IF)V",
		"java.util.HashMap.<init>()V", "java.util.HashMap.<init>(I)V", "java.util.HashMap.<init>(IF)V",
		"java.util.Hashtable.<init>()V", "java.util.Hashtable.<init>(I)V", "java.util.Hashtable.<init>(IF)V",
		"java.util.PriorityQueue.<init>()V", "java.util.PriorityQueue.<init>(I)V", "java.util.PriorityQueue.<init>(ILjava/util/PriorityQueue;)V",
		"java.util.LinkedList.<init>()V",
		"java.util.TreeSet.<init>()V",
		"java.util.BitSet<init>",
		"java.util.Vector.<init>()V", "java.util.Vector.<init>(I)V",  "java.util.Vector.<init>(II)V",
		"java.util.Stack.<init>",
		"java.util.Arrays.asList",
		"java.util.Collections.unmodifiable", "java.util.Collections.checked",
		"java.util.Random.<init>",
		"java.util.Properties.<init>()V",

		"java.io.StringWriter.<init>",

		jaspex.stm.Transaction.class,
		jaspex.speculation.runtime.SpeculationControl.class,
		jaspex.Debug.class,
		jaspex.Builtin.class,
		jaspex.speculation.Replacements.class,
		jaspex.speculation.ProcessedReplacements.class,
		"clientrt.",
		"jaspex.MARKER.beforeInlinedStore" );

	public static boolean isMethodWhitelisted(Type owner, String methodName, String methodDesc) {
		if (friendlyClassWhitelist.contains(owner.commonName())) {
			// Validar argumetos; ver nota acima sobre isto
			if (argumentsValid(methodDesc)) return true;
		}
		return listContainsMethod(unconditionalWhitelist, owner, methodName, methodDesc);
	}

	// Excepções à blacklist. Para aqueles casos em que queremos fazer blacklist de toda uma classe/package,
	// excepto um ou dois métodos.
	private static final List<String> blacklistExceptions = initList(
		java.lang.reflect.Constructor.class,
		java.lang.reflect.InvocationTargetException.class,
		java.lang.reflect.Array.class,
		"java.util.concurrent.locks.ReentrantLock.<init>",
		"java.lang.ThreadLocal.<init>",
		Options.TRANSACTIFYJDK ? ThreadLocal.class : null /* Permitir ThreadLocal de substituição */
		);

	// Blacklist (não completa) de classes perigosas e que consideramos um erro o programa invocar
	private static final List<String> blacklist = initList(
		Thread.class, ThreadLocal.class, InheritableThreadLocal.class,
		java.util.concurrent.ExecutorService.class,
		"java.util.concurrent.",
		"sun.misc.Unsafe", // Classes da JDK gostam de sobreusar o Unsafe
		"java.lang.ClassLoader.<init>",
		"java.security.SecureClassLoader.<init>",
		"java.net.URLClassLoader.<init>",
		"java.lang.reflect.",
		"java.lang.System.setIn", "java.lang.System.setOut", "java.lang.System.setErr",
		"java.lang.Object.wait",
		// Os seguintes métodos de Class podem fazer leak de detalhes internos das classes que foram
		// alterados pelo JaSPEx
		// Esta lista é ligeiramente permissiva, do ponto de vista que alguns dos outros métodos também
		// poderiam ser usados para descobrir a presença do JaSPEx, mas o programa teria que estar a
		// fazer operações mesmo muito estranhas (como estar a pedir fields por nome não-existentes que
		// existem no JaSPEx). O objectivo aqui é permitir os métodos que normalmente não vão mudar a
		// semântica, e banir os que poderiam dar azo a problemas mais facilmente.
		"java.lang.Class.forName(Ljava/lang/String;ZLjava/lang/ClassLoader;)",
		"java.lang.Class.getClasses()", // Não tenho a certeza quanto a este
		"java.lang.Class.getConstructors()",
		"java.lang.Class.getDeclaredClasses()", // Não tenho a certeza quanto a este
		"java.lang.Class.getDeclaredConstructors()",
		"java.lang.Class.getDeclaredFields()",
		"java.lang.Class.getDeclaredMethods()",
		"java.lang.Class.getFields()",
		"java.lang.Class.getGenericInterfaces()",
		"java.lang.Class.getInterfaces()",
		"java.lang.Class.getMethods()",
		"java.lang.Class.getModifiers()",
		"java.lang.Class.isLocalClass()", // Não tenho a certeza quanto a este
		"java.lang.Class.isMemberClass()" // Não tenho a certeza quanto a este
		);

	public static boolean isMethodBlacklisted(Type owner, String methodName, String methodDesc) {
		if (listContainsMethod(unconditionalWhitelist, owner, methodName, methodDesc)) return false;
		if (listContainsMethod(blacklistExceptions, owner, methodName, methodDesc)) return false;

		return listContainsMethod(blacklist, owner, methodName, methodDesc);
	}

	private static List<String> initList(Object ... entries) {
		ArrayList<String> newList = new ArrayList<String>(entries.length);

		for (int i = 0; i < entries.length; i++) {
			     if (entries[i] instanceof Class)  newList.add(((Class<?>) entries[i]).getName());
			else if (entries[i] instanceof String) newList.add((String) entries[i]);
			else if (entries[i] == null) { /* do nothing */ }
			else throw new Error();
		}

		newList.trimToSize();
		return newList;
	}

	private static boolean argumentsValid(String methodDesc) {
		for (Type t : Type.getArgumentTypes(methodDesc)) {
			if (t.isArray() ||
				(!isTransactifiable(t) && !friendlyClassWhitelist.contains(t.commonName()))) {
				return false;
			}
		}
		return true;
	}

	public static boolean listContainsMethod(List<String> list, Type owner, String methodName, String methodDesc) {
		String fullName = owner.commonName() + "." + methodName + methodDesc;
		for (String s : list) if (fullName.startsWith(s)) return true;
		return false;
	}

}
