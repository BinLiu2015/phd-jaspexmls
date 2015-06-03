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

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Implementação de Future simples para ser usada no lugar de uma SpeculationTask, quando uma especulação
  * não é aceite.
  **/
final class NoSpeculationFuture implements Future<Object> {

	private final Object _returnValue;

	NoSpeculationFuture(Object returnValue) {
		_returnValue = returnValue;
	}

	@Override public Object get() {
		return _returnValue;
	}

	@Override public boolean isDone() { throw new Error("Not Implemented"); }
	@Override public boolean cancel(boolean mayInterruptIfRunning) { throw new Error("Not Implemented"); }
	@Override public Object get(long timeout, TimeUnit unit) { throw new Error("Not Implemented"); }
	@Override public boolean isCancelled() { throw new Error("Not Implemented"); }

}
