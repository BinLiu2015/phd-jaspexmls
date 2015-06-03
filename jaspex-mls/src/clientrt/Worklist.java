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

package clientrt;

import jaspex.speculation.nsruntime.SpeculationTask;
import jaspex.speculation.runtime.SpeculationControl;
import jaspex.stm.Transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Worklist experimental, usado para aplicações que estão a ser modificadas para melhor colaborar com o JaSPEx.
  *
  * Esta worklist permite que um conjunto de tarefas sejam processadas por threads, sem que hajam conflitos
  * devido à adição e remoção de elementos à lista, e sem que seja dado um sinal de "queue vazia" sem que
  * todas as tarefas estejam terminadas.
  *
  * Conceptualmente, uma tarefa tem alguns estados diferentes:
  * - Antes adição: Não são aceites tarefas especulativamente. Antes disso, a adição de tarefas apenas adiciona
  *   uma commit action que irá adicionar a nova tarefa durante o commit da transacção.
  * - Sem owner e não-processada. (_owner == null, _task != null). Neste estado uma tarefa está pronta para ser
  *   processada. Quando é escolhida para ser processada, a thread escolhida coloca no seu write-set a operação
  *   _task = null (para o caso de fazer commit), e coloca-se directamente como _owner.
  * - Com owner e não-processada. (_owner != null, _task != null). Neste estado a tarefa está possívelmente a ser
  *   processada pelo owner, ou então o owner abortou. Um caso especial é que se _owner == actual então a tarefa
  *   na realidade está no estado owner e processada, só que ainda não foi feito o commit.
  * - Processada. (_task == null, _owner não interessa). Neste estado a tarefa já foi processada e já foi feito
  *   o commit dessa operação.
  *
  * A ideia de todo o algoritmo da Worklist é que operações na worklist são feitas directamente nesta, e com um
  * lock activo, mas depois uma parte final acede à lista usando a STM e fora do lock.
  *
  **/
public class Worklist<V> {

	private static class WorkItem {
		Object _task;
		Object _owner;
		WorkItem _next;

		private static final int TASK_OFFSET = Transaction.getFieldOffset(WorkItem.class, "_task");
		private static final int PARENT_LEN = Worklist.class.getName().length() + 1;

		WorkItem(Object task) {
			_task = task;
		}

		boolean hasOwner() {
			return _owner != null;
		}

		boolean isProcessed() {
			return _task == null;
		}

		Object takeOwnership() {
			Object task = Transaction.loadObject(this, _task, TASK_OFFSET); // task = _task
			Transaction.addAbortAction(new jaspex.stm.TransactionAction() {
				@Override public void run() {
					_owner = null;
				}
			});
			Transaction.storeObject(this, null, TASK_OFFSET); // _task = null
			_owner = Transaction.getOwnerTag();
			return task;
		}

		@Override
		public String toString() {
			return super.toString().substring(PARENT_LEN) + "(" + _task + "," + _owner + ")";
		}

		@SuppressWarnings("unused")
		public String printList() {
			java.util.List<WorkItem> l = new java.util.ArrayList<WorkItem>();

			for (WorkItem workItem = this; workItem != null; workItem = workItem._next) {
				l.add(workItem);
			}

			return l.toString();
		}
	}

	private static final Logger Log = LoggerFactory.getLogger(Worklist.class);

	private WorkItem _first;
	private WorkItem _last;

	public Worklist() { }

	private synchronized void addInternal(Object task) {
		WorkItem workItem = new WorkItem(task);

		if (_last != null) _last._next = workItem;
		_last = workItem;
		if (_first == null) _first = workItem;
	}

	public void add(final V task) {
		Transaction.addCommitAction(new jaspex.stm.TransactionAction() {
			@Override public void run() {
				addInternal(task);
			}
		});
	}

	/** Faz GC da lista, deixando em _first um elemento unprocessed, ou null, e devolvendo esse elemento **/
	private synchronized WorkItem garbageCollect() {
		WorkItem workItem = _first;

		// Procurar o primeiro unprocessed
		while (workItem != null && workItem.isProcessed()) {
			workItem = workItem._next;
		}

		// Anteriores podem ser garbage collected (mesmo que o actual seja null)
		_first = workItem;

		// Nenhuma tarefa existente
		if (workItem == null) _last = null;

		return workItem;
	}

	/** Tentar obter uma tarefa a ser processada em duas fases:
	  * - Na primeira fase a lista é percorrida até ser encontrada uma tarefa que esteja unprocessed e não
	  *   tenha owner. No caso de uma tarefa com estas características ser encontrada, é devolvida;
	  * - Na segunda fase, a lista é percorrida até ser encontrada uma tarefa que esteja unprocessed, mas que
	  *   pode já ter owner. Neste caso vamos roubar a tarefa a este owner.
	  *
	  * Cuidado com o que está a ser feito dentro da lock --- especialmente cuidado de não chamar nada na
	  * Transaction que possa fazer trigger de commit).
	  **/
	private synchronized WorkItem selectTask() {
		WorkItem workItem = garbageCollect();

		// Nenhuma tarefa existente
		if (workItem == null) return null;

		// Procurar o primeiro que seja unowned (porque o WorkItem devolvido pelo GC pode ter dono)
		do {
			if (!workItem.isProcessed() && !workItem.hasOwner()) return workItem;
			workItem = workItem._next;
		} while (workItem != null);

		// Não encontramos workItems sem dono. Vamos roubar o mais antigo.
		// Como entretanto podem ter acabado mais, repetimos o GC de novo.
		workItem = garbageCollect();

		Object ownerTag = Transaction.getOwnerTag();
		boolean txActive = ownerTag != null;

		// Procurar um workitem que não tenha sido processado, incluindo por nós
		// (O teste do ownertag é necessário porque é possível que a _task esteja a
		// null no write-set da transacção actual, e estamos a fazer acesso directo)
		while (workItem != null) {
			if (!workItem.isProcessed() || (txActive && workItem._owner != ownerTag)) {
				Log.debug(Thread.currentThread() + " stolen task " + workItem);
				return workItem;
			}
			workItem = workItem._next;
		}

		return null;
	}

	public V getAndRemove() {
		WorkItem workItem = selectTask();
		if (workItem == null) {
			// Forçar commit
			if (Transaction.isInTransaction()) {
				SpeculationControl.nonTransactionalActionAttempted("Forced commit on empty worklist");
				// O commit pode ter adicionado novas tarefas, vamos ter que repetir o processo
				// antes de podermos decidir que a lista está vazia
				return getAndRemove();
			}
			Log.debug(Thread.currentThread() + " worklist empty");
			return null;
		}

		//Log.debug(Thread.currentThread() + " got task " + workItem);

		SpeculationTask.current()._freezeInhibit = true;

		// Acesso usando agora a STM
		@SuppressWarnings("unchecked")
		V task = (V) workItem.takeOwnership();

		//Log.debug(Thread.currentThread() + " took ownership of " + workItem + " / task " + task);

		if (task == null && (workItem._task != null)) {
			// Encontramos task que nos foi roubada. Vamos forçar o commit.
			SpeculationControl.nonTransactionalActionAttempted("Forced commit on stolen task");
		}

		return task != null ? task : getAndRemove();
	}

}
