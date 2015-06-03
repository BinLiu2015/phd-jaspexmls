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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Versão modificada de speculation.runtime.Executor **/
public final class Executor {

	@SuppressWarnings("unused")
	private static final Logger Log = LoggerFactory.getLogger(Executor.class);

	/** Tamanho de buffer para tasks **/
	public static final int BUFFER_SIZE = 64;
	// Offset de campo "count" da ArrayBlockingQueue, para acessos usando Unsafe
	private static final long COUNT_FIELD_OFFSET =
		jaspex.stm.Transaction.getFieldOffset(ArrayBlockingQueue.class, "count");

	/** ThreadFactory que instância Threads pertencendo ao NewspecWorkerThreadGroup. **/
	final static class ThreadFactory implements java.util.concurrent.ThreadFactory {
		public SpeculationTaskWorkerThread newThread(final Runnable r) {
			return new SpeculationTaskWorkerThread(r);
		}
	}

	/** RejectedExecutionHandler que usa uma cached exception. **/
	final static class RejectedExecutionHandler implements java.util.concurrent.RejectedExecutionHandler {
		private static final RejectedExecutionException REJECTED_EXECUTION_EXCEPTION
			= new RejectedExecutionException();

		public void rejectedExecution(Runnable r, java.util.concurrent.ThreadPoolExecutor executor) {
			throw REJECTED_EXECUTION_EXCEPTION;
		}
	}

	/** Classe usada pelas worker threads.
	  * Tem um field _current para manter a SpeculationTask actual, evitando a utilização de um
	  * ThreadLocal para fazer o mesmo.
	  **/
	public final static class SpeculationTaskWorkerThread extends Thread {
		static final ThreadGroup _workerThreadGroup = new ThreadGroup("NewspecWorkerGroup");
		private static final AtomicInteger _nextThreadId = new AtomicInteger();

		SpeculationTaskWorkerThread(Runnable target) {
			super(_workerThreadGroup, target, "WorkT" + _nextThreadId.getAndIncrement());
			setContextClassLoader(jaspex.speculation.SpeculativeClassLoader.INSTANCE);
			// *sigh* como o java não nos deixa guardar o valor do _nextThreadId antes de
			// o passar à superclasse, fazemos batota voltando a converter o nome
			_threadId = Integer.parseInt(getName().substring(getName().indexOf('T') + 1));
		}

		@Override
		public String toString() {
			return "Thread[" + getName() + "]";
		}

		private SpeculationTask _current;
		public SpeculationTask currentSpeculationTask() { return _current; }
		public void setCurrentSpeculationTask(SpeculationTask speculationTask) { _current = speculationTask; }

		private jaspex.stm.Transaction _currentTransaction;
		public jaspex.stm.Transaction currentTransaction() { return _currentTransaction; }
		public void setCurrentTransaction(jaspex.stm.Transaction transaction) { _currentTransaction = transaction; }

		private Runnable _nextRunnable;
		public Runnable getAndCleanRunnable() { Runnable r = _nextRunnable; _nextRunnable = null; return r; }
		public void setNextRunnable(Runnable runnable) { _nextRunnable = runnable; }

		private final int _threadId;
		public int threadId() { return _threadId; }
	}

	/** ThreadPoolExecutor parametrizado e modificado para colaborar com a SpeculationTaskWorkerThread
	  * para fazer set da SpeculationTask actual antes de executar.
	  **/
	final static class ThreadPoolExecutor extends java.util.concurrent.ThreadPoolExecutor {
		private ThreadPoolExecutor() {
			super(  /* CorePoolSize */    threadPoolSize(),
				/* maximumPoolSize */ threadPoolSize(),
				/* keepAliveTime */   60, TimeUnit.SECONDS,
				/* workQueue */       createWorkQueue(),
				/* threadFactory */   new ThreadFactory(),
				/* rejectedHandler */ new RejectedExecutionHandler());
		}

		// Utilizado para manter o _hasFreeThreads
		private final int _maximumPoolSize = getMaximumPoolSize();
		private final AtomicInteger _workingThreads = new AtomicInteger();

		@Override
		protected void beforeExecute(Thread t, Runnable r) {
			if ((_workingThreads.getAndIncrement()+1) == _maximumPoolSize) _hasFreeThreads = false;
			((SpeculationTaskWorkerThread) t).setCurrentSpeculationTask((SpeculationTask) r);
		}

		@Override
		protected void afterExecute(Runnable r, Throwable t) {
			_workingThreads.getAndDecrement();
			_hasFreeThreads = true;
		}

		private static BlockingQueue<Runnable> createWorkQueue() {
			return Options.HYBRIDQUEUE ? HybridQueue.newQueue() : new SynchronousQueue<Runnable>();
		}
	}

	/** Queue híbrida que começa a delegar para uma ArrayBlockingQueue, e pode ser trocada para uma
	  * SynchronousQueue em caso de problemas.
	  *
	  * A ArrayBlockingQueue (e outros métodos de buffering de tasks) podem falhar devido a ser possível
	  * que todas as tasks que estão a ser processadas em threads estejam bloqueadas à espera da conclusão
	  * de uma task que ainda está buffered à espera de uma thread livre.
	  **/
	// Nota: Implementamos BlockingQueue<Object> em vez de BlockingQueue<Runnable> para evitar os
	// bridge methods inúteis que eram criados
	final static class HybridQueue implements BlockingQueue<Object> {

		BlockingQueue<Runnable> _currentQueue = new ArrayBlockingQueue<Runnable>(BUFFER_SIZE);
		boolean _fallback = false;

		@SuppressWarnings({ "unchecked", "rawtypes" })
		static BlockingQueue<Runnable> newQueue() {
			return ((BlockingQueue) new HybridQueue());
		}

		BlockingQueue<Runnable> switchToSynchronous() {
			BlockingQueue<Runnable> oldQueue = _currentQueue;
			_currentQueue = new SynchronousQueue<Runnable>();
			_fallback = true;
			return oldQueue;
		}

		@Override public boolean isEmpty() { return _currentQueue.isEmpty(); }
		@Override public Object take() throws InterruptedException { return _currentQueue.take(); }
		@Override public boolean offer(Object e) { return _currentQueue.offer((Runnable) e); }
		@Override public int size() { return _currentQueue.size(); }
		@Override public Object peek() { return _currentQueue.peek(); }
		@Override public Object poll() { return _currentQueue.poll(); }

		@Override public Object element() { throw new Error("Not Implemented"); }
		@Override public Object remove() { throw new Error("Not Implemented"); }
		@Override public boolean addAll(Collection<?> c) { throw new Error("Not Implemented"); }
		@Override public void clear() { throw new Error("Not Implemented"); }
		@Override public boolean containsAll(Collection<?> c) { throw new Error("Not Implemented"); }
		@Override public Iterator<Object> iterator() { throw new Error("Not Implemented"); }
		@Override public boolean removeAll(Collection<?> c) { throw new Error("Not Implemented"); }
		@Override public boolean retainAll(Collection<?> c) { throw new Error("Not Implemented"); }
		@Override public Object[] toArray() { throw new Error("Not Implemented"); }
		@Override public <T> T[] toArray(T[] a) { throw new Error("Not Implemented"); }
		@Override public boolean add(Object e) { throw new Error("Not Implemented"); }
		@Override public boolean contains(Object o) { throw new Error("Not Implemented"); }
		@Override public int drainTo(Collection<Object> c) { throw new Error("Not Implemented"); }
		@Override public int drainTo(Collection<Object> c, int s) { throw new Error("Not Implemented"); }
		@Override public boolean offer(Object e, long t, TimeUnit u) { throw new Error("Not Implemented"); }
		@Override public Object poll(long t, TimeUnit u) { throw new Error("Not Implemented"); }
		@Override public void put(Object e) { throw new Error("Not Implemented"); }
		@Override public int remainingCapacity() { throw new Error("Not Implemented"); }
		@Override public boolean remove(Object o) { throw new Error("Not Implemented"); }

	}

	/** Singleton executor **/
	private static final ThreadPoolExecutor _executor = new ThreadPoolExecutor();

	// FIXME: Acabar com isto e consultar directamente o _workingThreads?
	private static volatile boolean _hasFreeThreads = true;

	// Acesso às queues, para implementar o hasFreeThreads
	private static final HybridQueue _hybridQueue
		= Options.HYBRIDQUEUE ? (HybridQueue) (BlockingQueue<?>) _executor.getQueue() : null;
	private static final ArrayBlockingQueue<Runnable> _arrayQueue
		= Options.HYBRIDQUEUE ? (ArrayBlockingQueue<Runnable>) _hybridQueue._currentQueue : null;

	/** Verificação barata do estado da threadpool. **/
	public static boolean hasFreeThreads() {
		if (Options.HYBRIDQUEUE && !_hybridQueue._fallback) {
			return jaspex.util.Unsafe.UNSAFE.getInt(_arrayQueue, COUNT_FIELD_OFFSET) < BUFFER_SIZE;
		}
		// Usado para SynchronousQueue
		return _hasFreeThreads;
	}

	public static int threadPoolSize() {
		return Options.AQTWEAKS ? systemThreads() : systemThreads()*4;
	}

	/** Número de Cores/Processadores do sistema (inc. hyperthreading). **/
	public static int systemThreads() { return Runtime.getRuntime().availableProcessors(); }

	/** Número aproximado de tarefas completadas pela ThreadPool. **/
	public static long getCompletedTaskCount() {
		return _executor.getCompletedTaskCount();
	}

	/** Execute que retorna valor boleano com sucesso em vez de excepção. **/
	public static boolean tryExecute(SpeculationTask task) {
		try {
			_executor.execute(task);
			return true;
		} catch (RejectedExecutionException e) {
			return false;
		}
	}

	static {
		// Iniciar detector deadlocks
		if (Options.HYBRIDQUEUE) new DeadlockDetectorThread(_executor).start();
	}

	/** Thread usada para detectar deadlocks na pool quando não se usa uma SynchronousQueue **/
	private static class DeadlockDetectorThread extends Thread {

		private static final Logger Log = LoggerFactory.getLogger(DeadlockDetectorThread.class);

		/** Tempo entre cada verificação **/
		private final int SLEEP_DELAY = 500;
		private final int RETEST_DELAY = SLEEP_DELAY * 10;

		private final ThreadPoolExecutor _executor;
		private final BlockingQueue<Runnable> _queue;

		public DeadlockDetectorThread(ThreadPoolExecutor executor) {
			super("Deadlock Detector");
			setDaemon(true);
			_executor = executor;
			_queue = executor.getQueue();
		}

		@Override
		public void run() {
			/** A ideia geral do detector é periodicamente verificar a WorkQueue: Se a próxima task
			  * a ser executada não mudar durante um certo período de tempo, e observarmos todas as
			  * threads no estado WAITING, então provavelmente aconteceu um deadlock.
			  **/
			while (true) try {
				Runnable originalHead;

				// Obter próxima tarefa a ser processada
				while ((originalHead = _queue.peek()) == null) Thread.sleep(SLEEP_DELAY);

				// Verificar se durante algum tempo a tarefa não muda
				// Nota: Isto assume que a mesma tarefa não pode ser colocada várias vezes na
				// queue. O que é verdade neste momento, mas poderá mudar no futuro.
				Thread.sleep(RETEST_DELAY);
				if (originalHead != _queue.peek()) continue;

				// Tarefa não mudou, vamos verificar estado da Thread Pool
				Thread[] workerThreads = new Thread[_executor.getMaximumPoolSize()];
				SpeculationTaskWorkerThread._workerThreadGroup.enumerate(workerThreads);

				boolean waiting = true;
				for (Thread t : workerThreads) {
					waiting &= (t.getState() == Thread.State.WAITING);
				}

				if (!waiting || (originalHead != _queue.peek())) continue;

				BlockingQueue<Runnable> queue = _queue;

				if (Options.HYBRIDQUEUE) {
					Log.error("Threadpool deadlock detected, disabling task buffering");

					HybridQueue hq = (HybridQueue) (BlockingQueue<?>) _queue;
					queue = hq.switchToSynchronous();
				} else {
					// Provável deadlock detectado
					Log.error("Threadpool deadlock detected, no new speculations will be "
						+ "accepted for the remainder of this execution");

					// Desactivar thread pool
					_executor.shutdown();
				}

				// Acabar trabalho restante
				ThreadFactory factory = (ThreadFactory) _executor.getThreadFactory();
				Runnable r;
				while ((r = queue.poll()) != null) {
					SpeculationTaskWorkerThread t = factory.newThread(r);
					t.setCurrentSpeculationTask((SpeculationTask) r);
					t.start();
				}

				break;
			} catch (InterruptedException e) { throw new Error(e); }
		}
	}

}
