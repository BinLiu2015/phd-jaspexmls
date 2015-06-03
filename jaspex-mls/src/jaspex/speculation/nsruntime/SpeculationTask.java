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
import jaspex.speculation.runtime.CodegenHelper;
import jaspex.speculation.runtime.Callable;
import jaspex.stm.*;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import contlib.Continuation;

/** Container que associa um result à SpeculationTask que o deve receber **/
final class ExtendedResult {
	final SpeculationTask _task;
	final ExecutionResult _result;

	ExtendedResult(SpeculationTask task, ExecutionResult result) {
		_result = result;
		_task = task;
	}
}

/** Classes utilizadas na implementação do suporte para freeze/restore de tasks **/
abstract class FrozenTask {

	@SuppressWarnings("unused")
	private static final Logger Log = LoggerFactory.getLogger(FrozenTask.class);

	static final FrozenTask EMPTY_TASK = new FrozenTask(null, null) {
		@Override public void abortFrozen() { throw new AssertionError("EMPTY_TASK should never be aborted"); }
		@Override public void thawImpl() { throw new AssertionError("EMPTY_TASK should never be thawed"); }
		@Override public String toString() { return "FROZEN_EMPTY_TASK"; }
	};

	protected final SpeculationTask _specTask;
	protected final Transaction _transaction;

	protected FrozenTask(SpeculationTask specTask, Transaction transaction) {
		_specTask = specTask;
		_transaction = transaction;
	}

	/** May never return **/
	public final void thaw() {
		restore(_transaction);
		thawImpl();
	}

	/**
	  * Tratamos o caso de child tasks frozen de uma forma especial, já que a sua computação vai
	  * ser deitada fora (e logo não queremos perder ciclos a fazer coisas como resume da sua continuação),
	  * e com o detalhe importante que depois do abortFrozen() a task actual (aquando da entrada no
	  * abortFrozen) tem que ser reposta, já que pode ter que ser re-executada.
	  */
	public void abortFrozen() {
		//Log.debug(Thread.currentThread() + " abortFrozen " + _specTask  + " (" + this + ")");

		// Nada a fazer se não existir childTask, ou se mesmo existindo, seja inherited
		// (ver detalhes desta segunda parte no waitCurrentTransactionCommit)
		if (_specTask._childTask == null || _specTask._childInherited) {
			_specTask._frozenTask = EMPTY_TASK;
			return;
		}

		// Guardar a task actual para repor no final do método
		SpeculationTask savedCurrent = SpeculationTask.current();
		restore(null);
		SpeculationTask.abortChildTask();
		((SpeculationTaskWorkerThread) Thread.currentThread()).setCurrentSpeculationTask(savedCurrent);
	}

	/** May never return **/
	public abstract void thawImpl();

	private void restore(Transaction transaction) {
		SpeculationTaskWorkerThread t = (SpeculationTaskWorkerThread) Thread.currentThread();

		//Log.debug(t + " Thawed task " + _specTask + " (" + this + ")");

		t.setCurrentSpeculationTask(_specTask);
		t.setCurrentTransaction(transaction);
		_specTask._frozenTask = EMPTY_TASK; // Cuidado com race no acesso feito pelo SpeculationTask.run

		if (Options.PROFILE) SpeculationTask.profilingResume();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
	}
}

/** Implementação de freeze que funciona em qualquer situação **/
final class ContinuationFrozenTask extends FrozenTask {
	private final Continuation _taskContinuation;

	ContinuationFrozenTask(SpeculationTask specTask, Transaction transaction, Continuation taskContinuation) {
		super(specTask, transaction);
		_taskContinuation = taskContinuation;
	}

	@Override
	public void thawImpl() {
		ContSpeculationControl.resumeContinuation(_taskContinuation);
	}
}

/** Implementação de freeze no final da task estar completa, só faltando o commit e o setResult **/
final class FinishedFrozenTask extends FrozenTask {
	private final ExtendedResult _taskResult;

	FinishedFrozenTask(SpeculationTask specTask, Transaction transaction, ExtendedResult taskResult) {
		super(specTask, transaction);
		_taskResult = taskResult;
	}

	@Override
	public void thawImpl() {
		SpeculationTask.waitCurrentTransactionCommit();
		_taskResult._task.setResult(_taskResult._result);
	}
}

/** Uma SpeculationTask representa uma tarefa a executar na newSpec.
  * Contém:
  * - Continuação da task
  * - Fields para parent comunicar o resultado
  * - Mecanismo de sincronização entre tarefa pai e filha
  *
  * Para mais info ver também "Notas newspec" no wiki
  **/
public final class SpeculationTask implements RunnableFuture<Object> {

	private static final Logger Log = LoggerFactory.getLogger(SpeculationTask.class);

	// Continuação a executar
	protected Continuation _taskRunnable;

	// Resultados
	// Nota: Deveria esta variável ser volatile?
	// Parece-me que no caso em que o parent faz set do result, e o filho só depois o vai ver (e portanto
	// encontra-o logo, sem fazer sincronização), não existe nenhuma acção de sincronização explicita.
	// O result em si tem fields final, portanto os objectos lá contidos estão seguros, mas não sei se as
	// barreiras usadas para a implementação dos finals também garantem que outras alterações feitas pelo
	// parent de forma racy (por exemplo escritas na STM) serão vistas pelo filho que observe o result.
	private ExecutionResult _result;

	// Child SpeculationTask, se existir
	// De notar que isto pode mudar conforme novas especulações são spawned
	protected SpeculationTask _childTask;
	// Para propósitos de abort, por vezes precisamos de distinguir entre uma child que foi gerada por esta
	// task (ou seja, a qual está referida como childTask no Runnable que está na "base" da method stack),
	// de uma child que foi herdada do parent (ou seja, distinguir entre alterações feitas ao _childTask pelo
	// setChildTask ou pelo inheritChildTask)
	protected boolean _childInherited;

	// Usado para guardar estado frozen da task *actual*
	// Este field mais tarde será verificado pelo parent quando faz o setResult
	protected FrozenTask _frozenTask;
	// Suporte para impedir freeze por-spectask (usado pela Worklist)
	public boolean _freezeInhibit;

	// Suporte para RVP
	protected PredictableCallable _parentCallable;
	private Object _predictedResult;
	// Offset do campo _predictedResult, para ser acedido usando a STM
	private static final int PREDICTED_RESULT_OFFSET =
		Transaction.getFieldOffset(SpeculationTask.class, "_predictedResult");

	// Suporte para dummy transactions
	private final boolean _useDummyTx;

	// Suporte para estatísticas extra
	// Método que o parent foi executar (e que foi substituido pelo spawnSpeculation)
	private final String _parentInfo;
	// Método onde iniciamos a execução especulativa (onde o spawnSpeculation foi inserido)
	private final String _taskSource;

	// Estatísticas
	public static long _committedSpeculations = 0;
	public static long _abortedSpeculations = 0;
	public static long _failedSpeculations = 0;
	public static long _correctPredictions = 0;
	public static long _wrongPredictions = 0;

	// Suporte para profiling
	// Apenas a thread com esta lock está a executar
	private static final ReentrantLock PROFILING_EXECUTION_LOCK = new ReentrantLock(true);
	// Semáforo utilizador para forçar troca entre threads. Quando uma nova SpeculationTask começa,
	// incrementa o semáforo, sendo que outra task que faça profilingYield(true) vai esperar até que
	// essa thread tenha tido oportunidade de executar. Neste casos utilizamos uma Condition, em que
	// a thread actual fica à espera que o sinal da nova thread seja feito.
	private static final Condition PROFILING_FORCESWITCH_CONDITION = PROFILING_EXECUTION_LOCK.newCondition();
	private static final BufferedWriter PROFILING_OUTPUT = createProfilingOutput();
	private static final int PROFILING_OUTPUT_VERSION = 2;
	private ProfilingInfo _profiling = null;

	SpeculationTask(String parentInfo, String taskSource, boolean useDummyTx) {
		_parentInfo = parentInfo;
		_taskSource = taskSource;
		_useDummyTx = useDummyTx;
	}

	SpeculationTask(Callable parent, String taskSource) {
		this(Options.TXSTATS || Options.TXABORTSTATS || Options.PROFILE ? parent.getClass().getName() : null,
			taskSource,
			Options.ALLOWDUMMYTX ? parent.useDummyTransaction() : false);

		if (Options.RVP) _parentCallable = (PredictableCallable) parent;
	}

	// Usado apenas pelo bootstrapMain para criar a primeira SpeculationTask
	static SpeculationTask bootstrapTask(Continuation bootstrapMethod) {
		SpeculationTask speculationTask = new SpeculationTask(
			String.format("%" + CodegenHelper.CODEGEN_CLASS_PREFIX.length() +
					"sBOOTSTRAP.BOOTSTRAP$speculative", ""), "BOOTSTRAP", false);
		speculationTask._taskRunnable = bootstrapMethod;
		speculationTask._result = ExecutionResult.newObjectResult(null);
		return speculationTask;
	}

	// Runnable
	@Override
	public void run() {
		if (Options.PROFILE) profilingBegin();

		// Isto *devia* ser um boolean. Mas um boolean aqui faz a VM crashar na maior parte das
		// benchmarks. *sigh*
		long speculative = 1;

		//Log.debug("picked up {}", this);
		if (_result == null) {
			new Transaction(this, _useDummyTx);
		} else if (_result == ExecutionResult.ABORT_SPECULATION) {
			// Task já foi aborted, mesmo antes de ser iniciada

			// Parece-me que não faz sentido existir uma childTask neste momento, já que
			// o inherit só é feito aquando do resume da continuação, mas deixar aqui
			// o assert por algum tempo para o verificar
			assert (_childTask == null);

			cleanup();
			if (Options.PROFILE) profilingEnd();
			return;
		} else {
			speculative = 0;
		}

		// Nota: Cuidado com excepções que se propagam daqui, a VM não gosta mesmo nada disso.
		//	 _TODAS_ as excepções devem ser apanhadas pelo código da _taskRunnable.
		Continuation.runWithContinuationSupport(speculative == 1 ? _taskRunnable : getAndCleanTaskRunnable());

		SpeculationTaskWorkerThread currentThread = (SpeculationTaskWorkerThread) Thread.currentThread();
		Runnable nextRunnable = currentThread.getAndCleanRunnable();
		while (nextRunnable != null) {
			if (Options.PROFILE) profilingYield(true);

			Continuation.runWithContinuationSupport(nextRunnable);
			nextRunnable = currentThread.getAndCleanRunnable();
		}

		// Fazer cleanup/profilingEnd apenas quando não existe uma task frozen
		// Cuidado com races entre o teste pela _frozenTask e a escrita no FrozenTask.restore()
		if (_frozenTask == null) {
			cleanup();
			if (Options.PROFILE) profilingEnd();
		}
	}

	public static void waitCurrentTransactionCommit() {
		waitCurrentTransactionCommit(false);
	}

	// If doomed == true, we already assume that validation will fail, and abort right away
	// Note that validation might still pass, as it can be incomplete at this point (for efficiency reasons)
	public static void waitCurrentTransactionCommit(boolean doomed) {
		if (!Transaction.isInTransaction()) return;

		SpeculationTask current = current();

		// Esperar que o nosso parent seja committed e que passemos a estar em program order
		current.waitForResult(true, null);

		// Para o caso de precisarmos de re-executar
		Continuation taskRunnable = current.getAndCleanTaskRunnable();

		// Se o resultado for uma excepção, temos sempre que abortar
		if (!doomed && current._result.isObject()) {
			if (Transaction.commit()) {
				if (Options.PROFILE) profilingFinishedTx(true);
				_committedSpeculations++;
				// Transacção foi commited, ou seja o que foi feito até agora validou
				// correctamente, podemos prosseguir a execução
				return;
			}
		}

		transactionAbort(doomed || current._result.isObject());

		// Transacção abortou, ou seja, toda a execução deve ser repetida
		// Desta vez a execução já não vai ser dentro de uma transacção, pois
		// já estamos em program order

		// Cuidado que alterações aqui provavelmente têm que ser mirrored no
		// abortFrozen() e no final do get()

		abortChildTask();

		if (current._result == ExecutionResult.ABORT_SPECULATION) {
			// Caso especial, parent sinalizou para fazermos abort
			// Devolver thread à pool
			ContSpeculationControl.returnWorkerToPool();
		} else {
			// Re-executar código
			ContSpeculationControl.resumeContinuation(taskRunnable);
		}
		throw new AssertionError("Should never happen");
	}

	@Override
	public Object get() {
		if (Options.RVP && _result == null) {
			 // Usar Return Value Prediction

			if (_predictedResult != null) {
				// Reutilizar previsão já existente
				return Transaction.loadObject(this, _predictedResult, PREDICTED_RESULT_OFFSET);
			}

			boolean usePrediction = true;
			synchronized (this) {
				if (_result == null) {
					// Escrever previsão no field _predictedResult, que vai ser lido usando a
					// STM, para que fique registado no readset da tx que chama este método
					// Cuidado com races entre isto e a escrita final!
					_predictedResult = _parentCallable.predict();
				} else {
					usePrediction = false;
				}
			}

			if (usePrediction) {
				/*Log.debug("Used RVP to predict value of {} (prediction {})",
						_parentCallable, _predictedResult);*/

				return Transaction.loadObject(this, _predictedResult, PREDICTED_RESULT_OFFSET);
			}
		}

		boolean getFromParent = (this == current());

		waitForResult(getFromParent, null);

		// Se estamos a fazer get do parent, significa que ele já fez commit, e que portanto
		// já temos o commit token, logo não vale a pena adiar mais a validação+commit
		// da execução actual.
		// De notar que é possível estarmos a fazer get a um futuro que não seja nosso parent,
		// mas que será mais antigo e parent do nosso parent. Nesse caso, podemos obter o valor,
		// mas ainda não podemos tentar fazer commit, porque o nosso parent ainda não foi validado.
		if (getFromParent) waitCurrentTransactionCommit();

		if (_result.isObject()) return _result._object;

		// Este caso é algo estranho:
		// - No caso de getFromParent == true, se o resultado for uma excepção, esta já foi
		// tratada dentro do waitCurrentTransactionCommit (e este código nunca se executará,
		// porque a continuação foi re-resumed)
		// - Caso contrário, estamos a tentar obter um resultado de um grandparent, e como este
		// é uma excepção, a especulação actual é inválida e deve ser terminada.
		transactionAbort(false);

		// Notificar filho, se existir, para fazer abort
		abortChildTask();
		// Devolver thread à pool
		ContSpeculationControl.returnWorkerToPool();
		throw new AssertionError("Should never happen");
	}

	/** Passagem do commit token é implicita através da chamada a este método **/
	public void setResult(ExecutionResult result) {
		if (Transaction.isInTransaction()) {
			// Chamar waitForResult directamente, passando-lhe o resultado e a tarefa para
			// poder ser usado o freeze simples com a FinishedFrozenTask
			current().waitForResult(true, new ExtendedResult(this, result));
			waitCurrentTransactionCommit();
		}

		assert (current()._childTask == this) :
			(current() + " childTask: " + current()._childTask + " this: " + this);

		Object predicted = null;

		synchronized (this) {
			//Log.debug("Setting result " + current() + " --> " + this + " (result: " + result + ")");
			assert (_result == null) :
				(current() + " this: " + this + " oldresult: " + _result + " newresult: " + result);
			if (Options.RVP) {
				// Guardar valor anterior para as estatísticas
				predicted = _predictedResult;

				// No caso de ser uma excepção, isto está errado, mas a child vai fazer
				// abort de qualquer forma
				_predictedResult = result._object;
			}
			_result = result;
			notifyAll();
		}

		if (Options.RVP) {
			// Estatísticas RVP
			// Não completamente precisas no caso da previsão ser null
			if (predicted != null) {
				if (result.isObject() && predicted == result._object) _correctPredictions++;
				else _wrongPredictions++;
			}

			if (result.isObject()) _parentCallable.updatePrediction(result._object);
		}

		if (_frozenTask != null) {
			if (result == ExecutionResult.ABORT_SPECULATION) {
				if (Options.PROFILE) profilingPause();
				// Ao contrário do caso abaixo, neste caso temos mesmo que retornar, já que a
				// task actual pode ter que ser re-executada depois de sinalizar o abort da child
				_frozenTask.abortFrozen();
				if (Options.PROFILE) profilingResume();
			} else {
				if (Options.PROFILE) profilingEnd();
				// Assume-se que a task actual já não vai fazer mais nada. De notar que em alguns
				// casos thaw() pode retornar -- cuidado no futuro se quisermos adicionar código
				// depois do thaw().
				// (Em caso de problemas, usar um returnWorkerToPool() aqui).
				_frozenTask.thaw();
			}
		}
	}

	/** Método que espera que este SpeculationTask tenha sido populado com um resultado pelo Pai **/
	private void waitForResult(boolean ownTask, ExtendedResult taskResult) {
		// Isto talvez seja uma race? Ver nota junto à declaração do field.
		if (_result != null) return;

		if (!Options.NOFREEZE && (Options.CONTFREEZE || taskResult != null) && ownTask && !_freezeInhibit) {
			freeze(taskResult);
			if (_result != null) return;
		}

		synchronized(this) {
			while (_result == null) try {
				//Log.debug(Thread.currentThread() + " Waiting for result on " + this);
				if (Options.PROFILE) profilingAwait();
				else wait();
			} catch (InterruptedException e) { throw new Error(e); }
		}
	}

	/** Verifica se pai atirou excepção, e faz rethrow dela no filho **/
	static void checkThrowable() {
		ExecutionResult result = current()._result;
		if (result != null && result.isThrowable()) {
			if (result == ExecutionResult.ABORT_SPECULATION) {
				// Causar abort da transacção, e retorno à thread-pool
				waitCurrentTransactionCommit();
			} else {
				// Pode acontecer que estejamos neste método com uma transacção activa:
				// Poderíamos chamar waitCurrentTransactionCommit(), o que causaria um
				// abort seguido de um restore já sem transacção activa, mas sabemos
				// que se aqui estamos então ainda não se executou nada (ainda estamos
				// dentro do spawnSpeculation), e portanto podemos ser mais eficientes
				// com a operação.
				assert (Transaction.isEmpty());
				if (Transaction.isInTransaction()) transactionAbort(false);

				// Atirar excepção recebida do pai
				jaspex.util.Unsafe.UNSAFE.throwException(result._throwable);
			}
			throw new AssertionError("Should never happen");
		}
	}

	/** Acesso à task actual: Esta é mantida num campo da thread actual (mantido pelo Executor) **/
	public static SpeculationTask current() {
		return ((Executor.SpeculationTaskWorkerThread) Thread.currentThread()).currentSpeculationTask();
	}

	private static void transactionAbort(boolean failedValidation) {
		Transaction.abort();
		if (Options.PROFILE) profilingFinishedTx(false);
		if (failedValidation) _failedSpeculations++;
		else _abortedSpeculations++;
	}

	static void inheritChildTask(SpeculationTask speculationTask) {
		SpeculationTask current = current();
		current._childTask = speculationTask;
		current._childInherited = true;
	}

	static void setChildTask(SpeculationTask speculationTask) {
		SpeculationTask current = current();
		current._childTask = speculationTask;
		current._childInherited = false;
	}

	/** Este método não é privado apenas para poder ser chamado pela FrozenTask **/
	static void abortChildTask() {
		SpeculationTask current = current();

		// Apenas fazer abort à childTask se não for inherited, ou seja, se for uma task que foi
		// spawned durante a execução da continuação actual, e portanto que não é reachable por
		// mais nenhuma parte do resto do programa, se formos fazer retry.
		// De notar que se for herdada, não faz sentido fazer abort dela porque quem fez spawn
		// dela foi um parente nosso, e ela vai continuar a ser reachable independentemente do
		// retry da task actual.
		if (current._childInherited) return;

		if (current._childTask != null) {
			current._childTask.setResult(ExecutionResult.ABORT_SPECULATION);
			current._childTask = null;
		}
	}

	private void cleanup() {
		_childTask = null;
	}

	private Continuation getAndCleanTaskRunnable() {
		Continuation c = _taskRunnable;
		_taskRunnable = null;
		return c;
	}

	// Usado para suportar o -signalearlycommit
	public final boolean canCommit() {
		return _result != null;
	}

	/** Devolver a thread actual para a pool, em vez de ficar à espera do resultado do parent.
	  *
	  * Dois modos de freeze:
	  * - Simples:  Quando a task terminou todo o seu trabalho, só faltando o commit da transacção,
	  * 		e fazer set do valor no filho, usamos este modo, que não usa Continuações, e apenas
	  * 		passa a tarefa de fazer set e commit para outra thread.
	  * 		Em _frozenTask fica uma FinishedFrozenTask.
	  * - Completo: Task está a meio, e portanto usamos uma continuação para que depois possa ser
	  * 		terminado o trabalho.
	  * 		Em _frozenTask fica uma ContinuationFrozenTask.
	  *
	  * Por default apenas usamos o freeze simples, a utilização do freeze completo pode ser ligado/desligado.
	  *
	  * Nota: O *nome* deste método está hardcoded no ContSpeculationControl.captureContinuation, por
	  * causa do FreezeWorkaround. Cuidado com refactorings automáticos.
	  **/
	private static void freeze(ExtendedResult taskResult) {
		Continuation c = null;

		try {
			if (taskResult == null) c = ContSpeculationControl.captureContinuation();
		} catch (FreezeWorkaroundException e) { return; }

		if ((c == null) || !c.isResumed()) { // Executado pela thread que vai fazer freeze
			SpeculationTask specTask = current();
			Transaction transaction = Transaction.current();

			boolean alreadyFinished = true;

			if (specTask._result == null) {
				synchronized (specTask) {
					if (specTask._result == null) {
						specTask._frozenTask = (c == null) ?
							new FinishedFrozenTask(specTask, transaction, taskResult) :
							new ContinuationFrozenTask(specTask, transaction, c);
						alreadyFinished = false;
						if (Options.PROFILE) profilingPause();
					}
				}
			}

			// Resultado foi escrito pelo parent entretanto
			if (alreadyFinished) return;

			//Log.debug(Thread.currentThread() + " Frozen task " + specTask);

			Transaction.abort(); // Isto é só para limpar o ThreadLocal, não é um abort realmente
			ContSpeculationControl.returnWorkerToPool();
			throw new AssertionError("Should never happen");
		}

		// Thread que faz thaw recomeça execução aqui, no caso da ContinuationFrozenTask
	}

	@Override
	public String toString() {
		String baseName = String.format("SpeculationTask@%08x", hashCode());
		if (!Options.TXSTATS && !Options.TXABORTSTATS && !Options.PROFILE) return baseName;
		return baseName + " (Parent: " + CodegenHelper.codegenToOriginal(_parentInfo) +
			", called from " + _taskSource +
			(_frozenTask != null && _frozenTask != FrozenTask.EMPTY_TASK ?
				", Frozen in " + _frozenTask : "") + ")";
	}

	/** Implementação de execução single-threaded para profiling.
	  * Ver também notas sobre Profiling no wiki.
	  **/
	static class ProfilingInfo {
		// Timestamp (em ns) do ínicio do periodo actual de tracking
		long _intervalStartTS = -1;
		// Tempo (em ns) a executar especulativamente
		long _accumulatedTimeInTx;
		// Tempo (em ns) a executar não-especulativamente
		long _accumulatedTimeOutsideTx;
		// Commit teve sucesso?
		boolean _commitSuccess;

		void startInterval() {
			assert(_intervalStartTS <= 0);

			_intervalStartTS = System.nanoTime();
		}

		void endInterval(boolean inTx) {
			assert(_intervalStartTS > 0);

			long duration = System.nanoTime() - _intervalStartTS;

			if (inTx) _accumulatedTimeInTx += duration;
			else _accumulatedTimeOutsideTx += duration;

			_intervalStartTS = -1;
		}

		void endInterval() {
			endInterval(Transaction.isInTransaction());
		}
	}

	private static void profilingBegin() {
		current()._profiling = new ProfilingInfo();
		profilingResume();
	}

	static void profilingResume() {
		profilingSafeLock();
		PROFILING_FORCESWITCH_CONDITION.signalAll();
		current()._profiling.startInterval();
	}

	private static void profilingPause() {
		current()._profiling.endInterval();
		profilingSafeUnlock();
	}

	// Substituto do wait normal da ST, com suporte para profiling
	private synchronized void profilingAwait() throws InterruptedException {
		profilingPause();
		wait();
		profilingResume();
	}

	// Tenta fazer yield para outra task. Se waitForOther == true, esperamos até que outra tenha corrido
	// de certeza usando a Condition.
	static void profilingYield(boolean waitForOther) {
		if (!waitForOther && !PROFILING_EXECUTION_LOCK.hasQueuedThreads()) return;
		ProfilingInfo pInfo = current()._profiling;

		// Pausar tracking
		pInfo.endInterval();

		if (waitForOther) {
			try {
				PROFILING_FORCESWITCH_CONDITION.await();
			} catch (InterruptedException e) { throw new Error(e); }
		} else {
			// Forçar yield, já que a lock é fair
			profilingSafeUnlock();
			profilingSafeLock();
		}

		// Reiniciar tracking
		pInfo.startInterval();
	}

	// Faz reset ao intervalo actual, passando a contar o tempo como fora da transacção
	private static void profilingFinishedTx(boolean commit) {
		ProfilingInfo pInfo = current()._profiling;
		pInfo.endInterval(true);
		pInfo._commitSuccess = commit;
		pInfo.startInterval();
	}

	// Nota: Não colocar profilingEnd antes de returnWorkerToPool(), já que a execução salta para o run(),
	// que depois chama o profilingEnd
	private static void profilingEnd() {
		SpeculationTask current = current();
		ProfilingInfo pInfo = current._profiling;
		current._profiling = null;
		pInfo.endInterval(false);

		try {
			PROFILING_OUTPUT.write(
				(pInfo._accumulatedTimeInTx / 1000) + "\t" +
				(pInfo._accumulatedTimeOutsideTx / 1000) + "\t" +
				pInfo._commitSuccess + "\t" +
				current._taskSource + " / " +
				CodegenHelper.codegenToOriginal(current._parentInfo) +
				'\n');
			// Fazer flush periodicamente
			if (Math.random() > 0.99) PROFILING_OUTPUT.flush();
		} catch (IOException e) { throw new Error(e); }

		profilingSafeUnlock();
	}

	// Usado para nos certificarmos que não ficam locks a mais devido a bugs na colocação
	// das instrucções de profiling
	private static void profilingSafeLock() {
		PROFILING_EXECUTION_LOCK.lock();
		assert PROFILING_EXECUTION_LOCK.getHoldCount() == 1 :
			PROFILING_EXECUTION_LOCK.getHoldCount();
		// HACK, workaround para bugs no profiling
		/*while (PROFILING_EXECUTION_LOCK.getHoldCount() > 1) {
			PROFILING_EXECUTION_LOCK.unlock();
		}*/
	}

	private static void profilingSafeUnlock() {
		assert PROFILING_EXECUTION_LOCK.getHoldCount() == 1 :
			PROFILING_EXECUTION_LOCK.getHoldCount();
		PROFILING_EXECUTION_LOCK.unlock();
	}

	/** Gestão ficheiro output **/
	private static BufferedWriter createProfilingOutput() {
		if (!Options.PROFILE) return null;

		String traceFile =
			"trace-" + java.lang.management.ManagementFactory.getRuntimeMXBean().getName() + ".txt";
		Log.info("Profiling ON. Tracefile is " + traceFile);
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(traceFile), 4194304);

			Process p = Runtime.getRuntime().exec("/bin/ln -sf " + traceFile + " trace-latest.txt");
			p.waitFor();
			if (p.exitValue() != 0) Log.error("Error creating symlink to latest trace file");

			bw.write("META trace-version " + PROFILING_OUTPUT_VERSION + "\n");

			return bw;
		} catch (IOException e) { throw new Error(e); }
		  catch (InterruptedException e) { throw new Error(e); }
	}

	static void closeProfilingOutput() {
		try {
			if (Options.PROFILE) {
				PROFILING_OUTPUT.write("META trace-end\n");
				PROFILING_OUTPUT.close();
			}
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	@Override public boolean isDone() { throw new Error("Not Implemented"); }
	@Override public boolean cancel(boolean mayInterruptIfRunning) { throw new Error("Not Implemented"); }
	@Override public Object get(long timeout, TimeUnit unit) { throw new Error("Not Implemented"); }
	@Override public boolean isCancelled() { throw new Error("Not Implemented"); }
}
