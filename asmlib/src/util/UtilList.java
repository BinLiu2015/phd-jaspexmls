/*
 * asmlib: a toolkit based on ASM for working with java bytecode
 * Copyright (C) 2015 Ivo Anjo <ivo.anjo@ist.utl.pt>
 *
 * This file is part of asmlib.
 *
 * asmlib is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * asmlib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with asmlib.  If not, see <http://www.gnu.org/licenses/>.
 */

package util;

import java.util.*;

/**
  * Base interface for List class combining List and Deque operations
  **/
public interface UtilList<E> extends List<E>, Deque<E> {

	class UtilListIteratorReverser<E> implements Iterator<E>, Iterable<E> {
		final ListIterator<E> listIterator;

		UtilListIteratorReverser(ListIterator<E> listIterator) {
			this.listIterator = listIterator;
		}

		@Override
                public boolean hasNext() {
			return listIterator.hasPrevious();
                }

		@Override
                public E next() {
	                return listIterator.previous();
                }

		@Override
                public void remove() {
	                listIterator.remove();
                }

		@Override
		public Iterator<E> iterator() {
			return this;
		}

	}

	public E first();
	public E last();

	public Iterable<E> reverseIteration();
	public UtilList<E> reversed();

}
