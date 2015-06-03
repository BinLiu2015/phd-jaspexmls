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

package jaspex.speculation.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SpeculationControl {

	private static final Logger Log = LoggerFactory.getLogger(SpeculationControl.class);

	/** Este método é invocado por métodos $speculative, antes da execução de uma operação
	  * não-transaccional, para que decida o que fazer (abortar especulação, etc).
	  **/
	public static void nonTransactionalActionAttempted() {
		if (!jaspex.stm.Transaction.isInTransaction()) return;

		jaspex.speculation.nsruntime.SpeculationTask.waitCurrentTransactionCommit();
	}

	/** Versão do método que inclui output de debug a dizer qual a nonTransactionalAction **/
	public static void nonTransactionalActionAttempted(String description) {
		if (!jaspex.stm.Transaction.isInTransaction()) {
			Log.trace("nonTransactionalActionAttempted (no tx active) {}", description);
			return;
		}

		nonTransactionalActionAttempted();

		Log.debug("nonTransactionalActionAttempted {} (Source: {})", description,
			jaspex.speculation.nsruntime.SpeculationTask.current());
	}

	/** Versão especial do método que é usado com -nttracker para fazer tracking limitado de
	  * leituras/mutações a objectos não-transaccionais. Para mais detalhes ver documentação do
	  * NonTransactionalStateTracker.
	  **/
	public static void nonTransactionalActionAttempted(Object target, boolean mutation) {
		if (jaspex.stm.Transaction.isInTransaction()) {
			if (mutation) {
				jaspex.speculation.nsruntime.SpeculationTask.waitCurrentTransactionCommit();
			} else {
				// Thread especulativa vai ler objecto
				jaspex.stm.NonTransactionalStateTracker.stateAccessed(target);
			}
		} else if (mutation) {
			// Thread em program order vai mutar objecto
			jaspex.stm.NonTransactionalStateTracker.stateMutated(target);
		}
	}

	/** Versão debug do método acima, similar ao nonTransactionalActionAttempted(description) **/
	public static void nonTransactionalActionAttempted(Object target, boolean mutation, String description) {
		boolean txActive = jaspex.stm.Transaction.isInTransaction();
		if (!txActive) {
			Log.trace("nonTransactionalActionAttempted[nttracker] (no tx active) {}", description);
		}

		nonTransactionalActionAttempted(target, mutation);

		if (txActive) {
			Log.debug("nonTransactionalActionAttempted[nttracker,mutation:{}] {} (Source: {})", mutation,
				description, jaspex.speculation.nsruntime.SpeculationTask.current());
		}
	}

	/** Método injectado no inicio de todos os <clinit> de classes transactificadas **/
	// O <clinit> ser executado especulativamente pode causar vários problemas: escritas feitas
	// durante o <clinit> podem ser anuladas se uma transacção for aborted, e o <clinit> não pode
	// ser re-executado
	// FIXME: Neste momento apenas se faz log do erro, mas no futuro isto precisa de ser resolvido
	public static void inClinit() {
		if (jaspex.stm.Transaction.current() != null) {
			boolean canCommit =
				jaspex.speculation.nsruntime.SpeculationTask.current().canCommit() &&
				jaspex.stm.Transaction.current().validate();
			if (!canCommit) {
				Log.error("FIXME: " + Thread.currentThread().getStackTrace()[2].getClassName() +
					".<clinit> is running speculatively (canCommit: false)");
			} else {
				// Forçar já commit, já que (canCommit() && validate()) significa que vai ter sucesso
				nonTransactionalActionAttempted("inside <clinit>");
			}
		}
	}

	/** Método invocado quando se tenta chamar algo de uma classe blacklisted (como java.lang.ThreadLocal) **/
	public static void blacklistedActionAttempted(String description) {
		if (jaspex.Options.NOINSERTSPECULATION || jaspex.Options.NOSPECULATION) {
			Log.info("blacklistedActionAttempted {}, " +
				"allowed due to NOINSERTSPECULATION/NOSPECULATION", description);
			return;
		}
		Log.error("blacklistedActionAttempted {}", description);
		jaspex.speculation.nsruntime.ContSpeculationControl.terminate(
			new Error("Code tried to execute blacklisted action"), false);
	}

}
