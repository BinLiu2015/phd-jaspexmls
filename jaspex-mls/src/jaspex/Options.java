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

import static jaspex.Jaspex.getOption;
import static jaspex.Jaspex.getStringOption;

public final class Options {

	/** Opções **/
	// FastMode: Salta sobre certas operações úteis para debug/controlo (logging, etc)
	public static final boolean FASTMODE = getOption("fast",
		"(PERFORMANCE) disable extra verifications/assertions and debug output");
	// NoSpeculation: Fazer todas as alterações, mas correr apenas com uma thread.
	public static final boolean NOSPECULATION = getOption("nospeculation",
		"(DEBUG) apply all bytecode modifications, but never actually accept any speculation");
	// NoInsertSpeculation: Não inserir chamadas ao spawnSpeculation de todo, permitindo medir
	// 			o overhead de todo o processo, fora a transformação do spawnSpeculation()
	public static final boolean NOINSERTSPECULATION = getOption("noinsertspeculation",
		"(DEBUG) apply bytecode modifications, except the transformation to call spawnSpeculation()");
	// PrintClass: Imprimir classes geradas/modificadas
	public static final boolean PRINTCLASS = getOption("printclass",
		"(DEBUG) print modified classes before loading them");
	// WriteClass: Escrever para o disco classes geradas/modificadas
	public static final boolean WRITECLASS = getOption("writeclass",
		"(DEBUG) write modified classes to output/ directory before loading them");
	// Silent: Desliga debug output
	public static final boolean SILENT = getOption("silent",
		"disables (almost) all debug output");
	// NoLineNumber: Remove instrucções LINENUMBER do bytecode Java
	public static final boolean NOLINENUMBER = getOption("nolinenumber",
		"(DEBUG) remove LINENUMBER bytecodes");
	// RemoveSync: Remove ACC_SYNCHRONIZED de todos os métodos do programa transactificado (usar com cuidado)
	public static final boolean REMOVESYNC = getOption("removesync",
		"(DEBUG) remove ACC_SYNCHRONIZED from transactified classes (use with care)");
	// RemoveSync: Remove MONITORENTER/MONITOREXIT de todos os métodos do programa transactificado (usar com cuidado)
	public static final boolean REMOVEMONITORS = getOption("removemonitors",
		"(DEBUG) remove MONITORENTER/MONITOREXIT from transactified classes (use with EXTREME care)");
	// Debug: Remove tweaking de nome de classes (e talvez no futuro cores no output)
	public static final boolean DEBUG = getOption("debug",
		"(DEBUG) disable pretty printing in debug outputs and class names, mainly for use when running " +
		"inside a debugger");
	// StaticWorkaround: Usa outra forma de transactificação de fields static como workaround para crash na VM
		// Passou de opção para default por ser necessário com o -detectlocal e causa menos eventuais
		// reaparecimentos de crashes da VM que adoptar a solução da Deuce
	public static final boolean STATICWORKAROUND = true; /*getOption("staticworkaround",
		"employs an alternative implementation for transactifying static fields, as a workaround for JVM " +
		"crashes when trying to JIT compile code");*/
	// Jar: Permite chamar JaSPEx de forma semelhante a java -jar <jarfile.jar>
	public static final boolean JAR = getOption("jar",
		"similar to java -jar <jarfile.jar>");
	// SkipSpeculation: Lista de ficheiros contendo métodos e classes que não devem fazer trigger de
	//		    especulações (normalmente gerada por um profiler)
	public static final String SKIPSPECULATION = getStringOption("skipspeculation",
		"list of files containing methods and classes which are not accepted as targets for speculation");
	// SignalEarlyCommit: Modo experimental onde a stm verifica em cada acesso ao read/write-set se o parent
	//		      da SpeculationTask já terminou, podendo ser antecipado o commit, em vez de ser a
	//		      SpeculationTask a fazer trigger do commit quando terminar/precisar do valor do parent
	public static final boolean SIGNALEARLYCOMMIT = true; /*getOption("signalearlycommit",
		"(PERFORMANCE,EXPERIMENTAL) mode where the STM checks at every read/write access if the current " +
		"transaction can be committed, instead of a speculation only signalling the commit when it really " +
		"needs to be done");*/
	// ContFreeze: Modo experimental em que SpeculationTasks são frozen e associadas ao seu parent em vez de
	//		ficarem à espera pelo resultado do parent, permitindo que a thread que lhes faz host seja
	//		devolvida à thread pool. O parent encarregar-se-à depois de fazer thaw e de acabar o
	//		trabalho delas.
	public static final boolean CONTFREEZE = getOption("contfreeze",
		"(PERFORMANCE) allow the current SpeculationTask to be frozen inside a continuation " +
		"instead of waiting for its parent to finish working, allowing threads to return to " +
		"the thread pool earlier");
	// WsSizeHack: Força commit quanto o writeset chega a um determinado número de items.
	public static final boolean WSSIZEHACK = getOption("wssizehack",
		"(PERFORMANCE,HACK) forces speculation to commit if writeset grows above a certain size");
	// RVP: Modo experimental que faz return value prediction
	public static final boolean RVP = getOption("rvp",
		"(PERFORMANCE,EXPERIMENTAL) employ return value prediction");
	// Agressive RVP: Modo experimental que usa RVP de forma ainda mais agressiva que na versão acima
	public static final boolean AGRESSIVERVP = getOption("agressivervp",
		"(PERFORMANCE,EXPERIMENTAL) employ more agressive return value prediction (requires -rvp)")
		&& requires(RVP);
	// TxStats: Imprime estatísticas detalhadas durante o commit de uma transacção
	public static final boolean TXSTATS = getOption("txstats",
		"(DEBUG) prints detailed statistics for every committed transaction");
	// AQTweaks: Fazer tweak de comportamento do tamanho da threadpool para a hybridqueue (com arrayqueue)
	public static final boolean AQTWEAKS = getOption("aqtweaks",
		"(PERFORMANCE,EXPERIMENTAL) tweaks thread pool behavior for task buffering");
	// ClassCache: Fazer cache de bytecode preparado de classes
	public static final boolean CLASSCACHE = getOption("classcache",
		"(PERFORMANCE,EXPERIMENTAL) cache and reuse classes modified for speculation");
	// NoRemoveOverspeculation: Desactivar RemoveOverspeculation
	public static final boolean NOREMOVEOVERSPEC = getOption("noremoveoverspec",
		"(DEBUG) disable RemoveOverspeculation for testing purposes");
	// Profiling: Executar single-threaded com estatísticas de tempo
	public static final boolean PROFILE = getOption("profile",
		"(DEBUG,EXPERIMENTAL) execute in single-threaded mode with more statistics");
	// NtTracker: Fazer tracking de acessos a estruturas não-transaccionais, e permitir algumas operações
	//	      transaccionais sobre estas.
	public static final boolean NTTRACKER = getOption("nttracker",
		"(PERFORMANCE,EXPERIMENTAL,HACK) allow limited speculative access to non-transactional objects (note " +
		"that this assumes that, for instance in the case of collections, operations like equals and hashcode " +
		"on the objects contained therein are always safe to use; this assumption is not verified and " +
		"if broken will lead to wrong results)");
	// TransactifyJDK: Fazer transactificação de classes da JDK
	public static final boolean TRANSACTIFYJDK = !getOption("nojdkchanges",
		"(DEBUG) disable transactification of some JDK classes (JDK changes are enabled by default if the" +
		"'JDK_HACK_PACKAGE' environment variable is defined)") && (System.getenv("JDK_HACK_PACKAGE") != null);
	// ReadMap: Usar HashMap para manter read-set
	public static final boolean READMAP = getOption("readmap",
		"(PERFORMANCE,EXPERIMENTAL) use hashmap instead of linked-list to represent transaction read-sets");
	// HybridQueue: Queue experimental que troca entre AlternativeQueue e a SynchronousQueue.
	public static final boolean HYBRIDQUEUE = !getOption("notaskbuffering",
		"forces thread pool task buffering to off (replaces -alternativequeue/-hybridqueue)");
	// CountTasks: Profiling simples que mantém uma contagem dos spawnSpeculation chamados
	public static final boolean COUNTTASKS = getOption("counttasks",
		"(DEBUG) counts the number of times each method is called via spawnSpeculation for profiling");
	// AllowDummyTx: Permite executar uma tarefa especulativa sem manter read e write-set; usado em combinação
	// com um ficheiro de SkipSpeculation e o comando useDummy
	public static final boolean ALLOWDUMMYTX = getOption("allowdummytx",
		"(HACK) allows bypassing transactification in a per-case basis; use with EXTREME care");
	// TxAbortStats: Imprime estatísticas detalhadas sobre transacções aborted
	public static final boolean TXABORTSTATS = getOption("txabortstats",
		"(DEBUG) prints detailed statistics for every aborted transaction");
	// DetectLocal: Acesso directo a objectos criados durante a transacção actual
	public static final boolean DETECTLOCAL = getOption("detectlocal",
		"(PERFORMANCE,EXPERIMENTAL) directly access objects created by current transaction");
	// NoFreeze: Desligar freeze de especulações, para debugging
	public static final boolean NOFREEZE = getOption("nofreeze",
		"(DEBUG) disable speculation freeze");

	/** Evil init method, usado para obrigar a classe a ser carregada na VM e as opções
	  * acima serem inicializadas.
	  **/
	static void init() { }

	private static boolean requires(Boolean ... values) {
		for (boolean b : values) {
			if (!b) throw new RuntimeException("Invalid option for current configuration");
		}
		return true;
	}

}
