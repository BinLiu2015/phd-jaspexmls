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

package jaspex.speculation.nsruntime;

import jaspex.Options;
import jaspex.speculation.nsruntime.Executor.SpeculationTaskWorkerThread;
import jaspex.speculation.runtime.SpeculationException;
import jaspex.speculation.runtime.Callable;
import jaspex.stm.Transaction;

import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import contlib.Continuation;

/** Equivalente do SpeculationControl para especulações baseadas em continuações **/
public final class ContSpeculationControl {

	private static final Logger Log = LoggerFactory.getLogger(ContSpeculationControl.class);

	// Stats throttling submissões à threadpool
	private static long _earlyRejected = 0;
	private static long _lateRejected = 0;

	protected static final int STACK_MAXIMUM = 50;

	/** Método chamado pelo bytecode instrumentado para sinalizar um ponto de inicio de especulação **/
	public static Future<?> spawnSpeculation(final Callable continueExecution, String source) throws Exception {
		//Log.debug("spawnSpeculation: {}", continueExecution.getClass().getName());

		if (Options.COUNTTASKS) TaskCountProfiler.addTask(continueExecution);
		if (Options.PROFILE) SpeculationTask.profilingYield(false);

		// Antes de começar a usar a maquinaria pesada, vamos tentar fazer uma verificação
		// rápida para tentar determinar se existem worker threads livres
		if (Options.NOSPECULATION || !Executor.hasFreeThreads()) {
			// Nem sequer tentar fazer especulação
			_earlyRejected++;
			return new NoSpeculationFuture(continueExecution.call());
		}

		// Decidimos tentar fazer especulação

		SpeculationTask specTask = new SpeculationTask(continueExecution, source);

		// Guardar childTask actual, que vai passar a ser a childTask da especulação (e logo "grandchild"
		// da actual)
		SpeculationTask grandchildTask = SpeculationTask.current()._childTask;

		Continuation continuation = captureContinuation();
		if (!continuation.isResumed()) {
			// Se já existirem demasiadas frames na stack, não fazer especulação
			/*if (exactDepth(speculation) >= STACK_MAXIMUM) {
				_lateRejected++;
				Log.warn("Rejecting speculation, stack too deep");
				return new NoSpeculationFuture(continueExecution.call_nonspeculative());
			}*/

			specTask._taskRunnable = continuation;

			if (!Executor.tryExecute(specTask)) { // Todas as threads estão ocupadas
				_lateRejected++;
				// Não fazer especulação, executamos imediatamente o método no sitio
				// correcto, sem transacções ou delays
				continuation = null;
				specTask = null;
				return new NoSpeculationFuture(continueExecution.call());
			}

			// Especulação foi aceite

			// Registar a nova childtask como sendo child da actual
			SpeculationTask.setChildTask(specTask);
			grandchildTask = null;

			final SpeculationTask childTask = specTask;
			cleanStackAndContinueWith(new Runnable() {
				public void run() {
					ExecutionResult result = null;
					try {
						result = ExecutionResult.newObjectResult(continueExecution.call());
					} catch (SpeculationException e) { terminate(e, false); }
					  catch (VirtualMachineError e)  { terminate(e, false); }
					  catch (AssertionError e)       { terminate(e, false); }
					  catch (Throwable t) {
						result = ExecutionResult.newThrowableResult(t);
					}
					try {
						childTask.setResult(result);
					} catch (Throwable t) { terminate(t, false); }
				}
			});
			throw new AssertionError("Should never happen");
		} else {
			// Quando resumida, continuação começa aqui
			SpeculationTask.inheritChildTask(grandchildTask); // Guardar a childtask herdada
			SpeculationTask.checkThrowable();
			return specTask;
		}
	}

	/*public static int exactDepth(Continuation c) {
		Object[] stack = (Object[]) c.getStack()[0];
		int i = 0;
		while (stack[i] != sun.misc.Continuation.class) i++;
		return i + 3; // Pequena correcção baseada em comparação com o Thread.getStackTrace()
	}*/

	/** Este método cria um SpeculationTask que quando submetido à threadpool corre o main
	  * do programa.
	  **/
	public static void bootstrapMain(final Callable main) {
		final class BootstrapMain implements Runnable {
			private Continuation _mainContinuation;

			public Continuation getContinuation() {
				// Uma SpeculationTask só sabe correr uma Continuation, por isso para
				// gerar a primeira continuação, começamos a executar este runner, capturamos
				// uma continuação, que guardamos em _mainContinuation e retornamos aqui
				// para ser transferido para uma SpeculationTask.
				Continuation.runWithContinuationSupport(this);
				return _mainContinuation;
			}

			@Override public void run() {
				Throwable exception = null;
				try {
					_mainContinuation = captureContinuation();
					if (!_mainContinuation.isResumed()) return;
					// Not needed anymore
					_mainContinuation = null;
					main.call();
					SpeculationTask.waitCurrentTransactionCommit();
				} catch (SpeculationException e) { terminate(e, false); }
				  catch (VirtualMachineError e)  { terminate(e, false); }
				  catch (AssertionError e)       { terminate(e, false); }
				  catch (Throwable t) {
					SpeculationTask.waitCurrentTransactionCommit();
					exception = t;
				}
				terminate(exception, true);
			}
		};

		Executor.tryExecute(SpeculationTask.bootstrapTask(new BootstrapMain().getContinuation()));
	}

	/** Termina a aplicação, imprimindo estatísticas e um stack trace, em caso de terminação
	  * com throwable.
	  **/
	public static synchronized void terminate(Throwable t, boolean normal) {
		if (t != null) {
			java.io.StringWriter stacktrace = new java.io.StringWriter();
			try {
				t.printStackTrace(new java.io.PrintWriter(stacktrace));
			} catch (NullPointerException e) {
				// A JDK da openjdk-continuation tem um bug no Throwable, em
				// que o campo suppressedExceptions (feature nova no Java 7) em
				// certos casos (como stack overflows), não é correctamente
				// inicializado.
				// Acho que pode safely ser ignorado este problema.
			}
			if (normal) {
				System.err.print("Exception in thread \"main\" " + stacktrace.toString());
			} else {
				System.err.print(
					jaspex.util.ShellColor.color(
						"JaSPEx terminated due to an unexpected exception:", "48;5;160")
					+ " " + stacktrace.toString());
			}
		} else if (!normal) {
			System.err.println(jaspex.util.ShellColor.color("Forced VM termination", "48;5;160"));
		}

		long uptime = java.lang.management.
				ManagementFactory.getRuntimeMXBean().getUptime();
		String prettyUptime = String.format("%dm%d.%03ds", (uptime/1000)/60,
				(uptime/1000)%60, uptime%1000);

		double cpuTime = ((com.sun.management.OperatingSystemMXBean)
			java.lang.management.ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime();
		double cpuPercent = cpuTime / (uptime*10000); // convert to ns, but scale to 100%

		Log.info(jaspex.util.ShellColor.color("Stats: " + prettyUptime + ", " + (SpeculationTask._committedSpeculations +
			SpeculationTask._abortedSpeculations + SpeculationTask._failedSpeculations) + " speculations (" +
			SpeculationTask._committedSpeculations + " committed, " +
			SpeculationTask._abortedSpeculations + " aborted / " +
			SpeculationTask._failedSpeculations + " failed validation, " +
			_earlyRejected + " early rejected, " + _lateRejected +
			" late rejected, " + Executor.getCompletedTaskCount() +
			" tasks completed by thread pool)" +
			(Options.RVP ? (", RVP enabled (" +
					SpeculationTask._correctPredictions + " correct, " +
					SpeculationTask._wrongPredictions + " wrong)") : "") +
			", CPU Usage " + String.format("%3.0f", cpuPercent) + "%" +
			" / " + String.format("%.0f", (cpuPercent/Executor.systemThreads())) + "%",
			"48;5;70;38;5;15"));

		if (Options.COUNTTASKS) TaskCountProfiler.printResults();
		if (Options.TXABORTSTATS) Transaction.printGlobalStats();

		if (t == null && !normal) return;

		// Impedir outras threads de continuarem a fazer output enquanto a VM não termina
		System.out.close();
		System.err.close();

		_cleanExit = true;
		System.exit(t == null ? 0 : 1);
	}

	private static boolean _cleanExit = false;

	static {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override public void run() {
				if (jaspex.Options.CLASSCACHE) jaspex.speculation.Cache.flush();
				if (jaspex.Options.PROFILE) SpeculationTask.closeProfilingOutput();
				if (!_cleanExit) terminate(null, false);
			}
		});
	}

	/** Obter uma continuação vazia **/
	private static final Continuation _emptyContinuation;

	static {
		final Continuation[] emptyContinuation = new Continuation[1];
		Continuation.runWithContinuationSupport(new Runnable() {
			@Override public void run() {
				Continuation c = captureContinuation();
				if (!c.isResumed()) emptyContinuation[0] = c;
			}
		});
		_emptyContinuation = emptyContinuation[0];
	}

	/** Usado para "retornar" uma Worker thread para a pool directamente.
	  *
	  * A ideia é em vez de usar uma excepção e esperar que ela não seja intersectada
	  * (como se fazia antigamente, e se faz na JVSTM), em vez disso é feito o resume
	  * de uma continuação vazia, o que faz com que a stack anterior seja descartada.
	  **/
	static void returnWorkerToPool() {
		resumeContinuation(_emptyContinuation);
	}

	/** Usado para limpar a stack actual, e continuar a executar a partir do Runnable recebido.
	  *
	  * Isto funciona em colaboração com o SpeculationTask, que verifica o campo nextRunnable cada
	  * vez que o runWithContinuationSupport retorna.
	  **/
	static void cleanStackAndContinueWith(Runnable r) {
		((SpeculationTaskWorkerThread) Thread.currentThread()).setNextRunnable(r);
		resumeContinuation(_emptyContinuation);
	}

	/** Captura "segura" de continuação -- quando falha, saimos logo **/
	static Continuation captureContinuation() {
		try {
			return Continuation.capture();
		} catch (IllegalThreadStateException e) {
			if (Options.CONTFREEZE) {
				// Caso especial para freezereuse, detectar se estamos a fazer freeze "durante"
				// um <clinit> e tentar recuperar a situação.
				StackTraceElement[] stackState = Thread.currentThread().getStackTrace();
				// 0 -- getStackTrace()
				// 1 -- captureContinuation()
				// 2 -- freeze()
				if (stackState[2].getClassName().equals(SpeculationTask.class.getName()) &&
					stackState[2].getMethodName().equals("freeze")) {
					// Estamos a fazer freeze, procurar <clinit>
					for (int i = 3; i < stackState.length; i++) {
						if (stackState[i].getMethodName().equals("<clinit>")) {
							Log.warn("Workaround for freeze in <clinit>");
							throw FreezeWorkaroundException.FREEZE_WORKAROUND_EXCEPTION;
						} else if (stackState[i].getClass().equals(Continuation.class)) {
							break;
						}
					}
				}
			}
			terminate(e, false);
		}
		throw new AssertionError("Should never happen");
	}

	/** Resume "seguro" de continuação -- quando falha, saimos logo **/
	static void resumeContinuation(Continuation c) {
		try {
			Continuation.resume(c);
		} catch (IllegalArgumentException e) {
			Log.debug("Terminating execution due to continuation error. Current task: "
				+ SpeculationTask.current());
			terminate(e, false);
		}
		throw new AssertionError("Should never happen");
	}

}
