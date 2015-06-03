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

package test;

/** Teste desenhado para testar a opção -detectlocal
  * Correr o teste com -txstats deve detectar 1 transacção vazia.
  **/
public class NewSpecExample81 {
	private int i;

	private NewSpecExample81() { }

	private static int runTest(int sleep) {
		jaspex.Debug.sleep(sleep);
		NewSpecExample81 o = new NewSpecExample81();
		o.i = 10;
		return o.i;
	}

	public static void main(String[] args) {
		runTest(100); // Executa não-especulativamente
		runTest(0);   // Executa especulativamente
	}
}
