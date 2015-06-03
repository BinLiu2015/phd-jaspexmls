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

import static jaspex.speculation.runtime.AbortSpeculationException.ABORT_SPECULATION_EXCEPTION;

/** Classe quem contém resultados de especulações **/
public final class ExecutionResult {

	public static final ExecutionResult ABORT_SPECULATION = newThrowableResult(ABORT_SPECULATION_EXCEPTION);

	private static final ExecutionResult NULL_RESULT = new ExecutionResult(null, null);

	final Object _object;
	final Throwable _throwable;

	private ExecutionResult(Object resultObject, Throwable resultThrowable) {
		_object = resultObject;
		_throwable = resultThrowable;
	}

	static ExecutionResult newObjectResult(Object result) {
		if (result == null) return NULL_RESULT;
		return new ExecutionResult(result, null);
	}

	static ExecutionResult newThrowableResult(Throwable throwable) {
		return new ExecutionResult(null, throwable);
	}

	public boolean isThrowable() {
		return (_throwable != null);
	}

	public boolean isObject() {
		return (_throwable == null);
	}

	@Override
	public String toString() {
		return "ExecutionResult{" + ((this == ABORT_SPECULATION) ? "ABORT_SPECULATION" :
				(isObject() ?
					("Object: " + (_object == null ? "null" : _object.getClass().getName())) :
					("Throwable: " + _throwable.getClass().getName())))
			+ "@" + Integer.toHexString(System.identityHashCode(isObject() ? _object : _throwable)) + "}";
	}

}
