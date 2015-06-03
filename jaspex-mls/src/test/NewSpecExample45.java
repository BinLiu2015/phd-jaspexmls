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

/** Testcase para adição de overrides de métodos de superclasses NT.
  *
  * Estão a ser adicionados a toda a hierarquia de classes, e não apenas à primeira que estende a classe NT.
  * Quando alguns dos métodos herdados são final, quer dizer que o override de NewSpecExample45 será final
  * (correcto), mas NewSpecExample45helper também vai tentar adicionar o mesmo override, que também será final,
  * causando um erro.
  **/
class NewSpecExample45helper extends NewSpecExample45 {
	private static final long serialVersionUID = 1L;
	protected NewSpecExample45helper() { super(); }
}

public class NewSpecExample45 extends java.util.Calendar {
	private static final long serialVersionUID = 1L;

	protected NewSpecExample45() { }

	@Override public void add(int arg0, int arg1) { }
	@Override protected void computeFields() { }
	@Override protected void computeTime() { }
	@Override public int getGreatestMinimum(int arg0) { return 0; }
	@Override public int getLeastMaximum(int arg0) { return 0; }
	@Override public int getMaximum(int arg0) { return 0; }
	@Override public int getMinimum(int arg0) { return 0; }
	@Override public void roll(int arg0, boolean arg1) { }
	@Override public long getTimeInMillis() { return 0; }

	public static void main(String[] args) {
		new NewSpecExample45helper();
	}
}
