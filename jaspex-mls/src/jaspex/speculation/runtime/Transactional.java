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

/** Todas as classes transactificadas pelo JaSPEx são também modificadas para implementarem esta interface.
  * Isto permite que em runtime se possa simplesmente fazer "o instanceof Transactional" para testar
  * se um qualquer objecto o é uma instância de uma classe transactificada.
  **/
public interface Transactional {
	// Usado para o -detectlocal. De notar que os métodos não são inseridos se a opção não estiver
	// ligada, mas a JVM parece não se importar muito com isso.
	Object $getOwner();
	void $setOwner(Object o);
}
