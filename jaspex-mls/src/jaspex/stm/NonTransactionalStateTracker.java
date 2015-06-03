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

package jaspex.stm;

import jaspex.speculation.newspec.*;

import java.util.*;

import org.slf4j.*;

import asmlib.*;

/** Classe usada para fazer tracking limitado de mudanças de estado de classes não-transaccionais, permitindo
  * que sejam usadas de forma limitada durante especulação, desde que não estejam a ser mutadas.
  *
  * A ideia principal é permitir acesso a mapas, listas e sets dentro de transacções, garantindo que estas não
  * são alteradas em paralelo pela thread em program-order (e causando o abort das especulações caso estas
  * sejam alteradas).
  *
  * (De notar que por exemplo no caso de Collection.contains, o equals de objectos dentro da collection pode
  * ser chamado e como isso não pode ser controlado assume-se que a operação é segura.)
  *
  * Para a thread program-order é mantido um pseudo-writeset que regista os objectos não-transaccionais
  * modificados. Para as threads especulativas é mantido um pseudo-readset que é validado contra o write-set
  * em commit-time.
  *
  * Para isto funcionar, a thread em program-order, sempre que faz uma mutação a uma estrutura de dados, cria
  * uma entrada na lista de StateMutation; por outro lado, threads especulativas, ao fazer o primeiro acesso
  * a algo não-transaccional, guardam o estado actual (a última StateMutation), e mais tarde vão validar as
  * suas leituras contra este estado.
  * Ou seja, o _currentState é um pseudo-writeset da thread em program-order, e o _accesses é o read-set de
  * threads especulativas.
  *
  * Operações não-transaccionais passam então a ser classificadas em três tipos:
  * - Não consideradas: Comportamento antigo, é sempre chamado o nonTransactionalActionAttempted()
  * - "Safe": As listadas na whitelist abaixo (SAFE_OPERATIONS), que são consideradas seguras por apenas
  *   lerem a estrutura de dados, e não mutarem o seu estado. Estas são as operações que são permitidas às
  *   threads especulativas.
  * - "Unsafe": Operação não listada nos safeMethods de uma entrada na whitelist, ou seja, é considerada
  *   uma escrita na estrutura de dados. Caso seja executada dentro de uma especulação, é chamado o
  *   nonTransactionalActionAttempted(); caso seja executada fora de uma especulação, a instância mutada
  *   é adicionada à write-list global (stateMutated).
  **/
public final class NonTransactionalStateTracker {

	private static final Logger Log = LoggerFactory.getLogger(NonTransactionalStateTracker.class);

	// Gestão de lista de métodos Safe

	/** Lista de métodos mutáveis seguros de serem chamados **/
	private static final List<SafeOperation> SAFE_OPERATIONS = Arrays.asList(
		new SafeOperation(Collection.class,
			"contains", "equals", "hashCode", "size", "isEmpty"
			/* TODO: toArray() é seguro, mas toArray(T[]) não é */ ),
		new SafeOperation(Map.class,
			"containsKey", "containsValue", "get"),
		new SafeOperation(List.class,
			"get", "indexOf", "lastIndexOf"),
		new SafeOperation(Vector.class,
			"elementAt", "capacity", "firstElement", "lastElement")
		);

	public static class SafeOperation {
		public final Type _class;
		public final List<String> _safeMethods;

		private SafeOperation(Class<?> klass, String ... safeMethods) {
			_class = Type.fromClass(klass);
			_safeMethods = Arrays.asList(safeMethods);
		}
	}

	/** Tenta encontrar uma SafeOperation que contenha o método pedido. Caso encontre, devolve essa
	  * (significando que o método é safe); caso não encontra, devolve uma qualquer SafeOperation que se
	  * aplique à classe recebida (significando que deve ser feito o tracking, mas o método não é safe).
	  * Finalmente, devolve null se a classe não faz match com nada da lista de SAFE_OPERATIONS.
	  **/
	public static SafeOperation getSafeOperation(Type targetClass, String methodName) {
		SafeOperation found = null;
		for (SafeOperation so : SAFE_OPERATIONS) {
			if (ClassHierarchy.isAssignableFrom(so._class, targetClass)) {
				found = so;
				if (so._safeMethods.contains(methodName)) break;
			}
		}
		return found;
	}

	// Gestão de estado da thread em program order

	/** Entrada na lista ligada de mutações a estado não-transaccional **/
	private static final class StateMutation {
		protected StateMutation _next;
		protected final Object _instance;

		protected StateMutation(Object instance) {
			_instance = instance;
		}

		@Override
		public String toString() {
			return "StateMutation@" + Integer.toHexString(hashCode());
		}
	}

	/** Estado actual da thread que está em program-order.
	  * Esta lista funciona de forma especial devido a GC: a última posição é mantida em _currentState,
	  * sendo que posições anteriores só são potencialmente mantidas por threads especulativas como o seu
	  * _startingState. Assim, O GC do java automaticamente limpa entradas antigas, sendo que apenas as
	  * necessárias são mantidas vivas.
	  **/
	private static volatile StateMutation _currentState = new StateMutation(null);

	/** Invocado pela thread em program-order **/
	public static void stateMutated(Object mutated) {
		StateMutation sm = new StateMutation(mutated);
		Log.debug("stateMutated {} -> {}", _currentState, sm);
		_currentState._next = sm;
		_currentState = sm;
	}

	// Tracking de estado de threads especulativas

	/** Invocado por threads especulativas **/
	public static void stateAccessed(Object accessed) {
		Transaction t = Transaction.current();
		t.nonTransStateTracker = new NonTransactionalStateTracker();
		t.nonTransStateTracker.registerAccess(accessed);
	}

	/** Estado do programa quando o Tracker foi criado (imediatamente antes do primeiro acesso a estado
	  * de um objecto não-transaccional.
	  **/
	private final StateMutation _startingState;
	/** Objectos não-transaccionais acedidos (basicamente read-set de objectos não-transaccionais) **/
	private final IdentityHashMap<Object, Boolean> _accesses = new IdentityHashMap<Object, Boolean>();

	public NonTransactionalStateTracker() {
		_startingState = _currentState;
	}

	/** Invocado por threads especulativas **/
	public void registerAccess(Object accessed) {
		_accesses.put(accessed, Boolean.TRUE);
	}

	public boolean validate() {
		boolean result = doValidate();
		System.out.println("Validating for " + Transaction.current() + " => " + result);
		return result;
	}

	/** Valida leituras não-transaccionais **/
	public boolean doValidate() {
		StateMutation sm = _startingState._next;
		while (sm != null) {
			if (_accesses.containsKey(sm._instance)) return false;
		}
		return true;
	}

	/** Usado para debugging **/
	public void printTxStats(StringBuilder output) {
		output.append("\n\tNonTransactionalStateTracker size: " + _accesses.size());
		output.append("\n\t\tStarting state: " + _startingState);
		output.append("\n\t\tFinal state: " + _currentState);
		output.append("\n\t\tAccesses:");
		for (Map.Entry<Object, Boolean> e : _accesses.entrySet())  {
			output.append("\n\t\t\t" + e.getKey().getClass().getName() + '@' +
				Integer.toHexString(System.identityHashCode(e.getKey())));
		}
	}
}
