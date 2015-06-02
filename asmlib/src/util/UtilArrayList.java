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

public class UtilArrayList<E> extends ArrayList<E> implements UtilList<E> {

	private static final long serialVersionUID = 1L;

	public UtilArrayList() {
		super();
	}

	public UtilArrayList(Collection<? extends E> c) {
		super(c);
	}

	public UtilArrayList(int initialCapacity) {
		super(initialCapacity);
	}

	/** Creates a new list, containing the objects provided by the Iterator,
	  * in the same order as they are supplied.
	  **/
	public UtilArrayList(Iterator<? extends E> it) {
		super();
		while (it.hasNext()) add(it.next());
	}

	@Override
	public void addFirst(E e) {
		add(0, e);
	}

	@Override
	public void addLast(E e) {
		add(e);
	}

	@Override
	public Iterator<E> descendingIterator() {
		return new UtilListIteratorReverser<E>(listIterator(size()));
	}

	/** Returns an Iterable object, suitable for using with foreach to iterate
	  * over the current list in reverse order.
	  **/
	public Iterable<E> reverseIteration() {
		return new UtilListIteratorReverser<E>(listIterator(size()));
	}

	/** Returns a new list, with the same elements as the current list, but with
	  * a reversed order.
	  **/
	public UtilArrayList<E> reversed() {
		return new UtilArrayList<E>(descendingIterator());
	}

	@Override
	public E element() {
		return getFirst();
	}

	@Override
	public E getFirst() {
		if (isEmpty()) throw new NoSuchElementException();
		return peekFirst();
	}

	@Override
	public E getLast() {
		if (isEmpty()) throw new NoSuchElementException();
		return peekLast();
	}

	@Override
	public boolean offer(E e) {
		return offerLast(e);
	}

	@Override
	public boolean offerFirst(E e) {
		addFirst(e);
		return true;
	}

	@Override
	public boolean offerLast(E e) {
		addLast(e);
		return true;
	}

	@Override
	public E peek() {
		return peekFirst();
	}

	@Override
	public E peekFirst() {
		try {
			return get(0);
		} catch (IndexOutOfBoundsException e) {
			return null;
		}
	}

	@Override
	public E peekLast() {
		try {
			return get(size()-1);
		} catch (IndexOutOfBoundsException e) {
			return null;
		}
	}

	@Override
	public E poll() {
		return pollFirst();
	}

	@Override
	public E pollFirst() {
		try {
			return remove(0);
		} catch (IndexOutOfBoundsException e) {
			return null;
		}
	}

	@Override
	public E pollLast() {
		try {
			return remove(size()-1);
		} catch (IndexOutOfBoundsException e) {
			return null;
		}
	}

	@Override
	public E pop() {
		return removeFirst();
	}

	@Override
	public void push(E e) {
		addFirst(e);
	}

	@Override
	public E remove() {
		return removeFirst();
	}

	@Override
	public E removeFirst() {
		try {
			return remove(0);
		} catch (IndexOutOfBoundsException e) {
			throw new NoSuchElementException();
		}
	}

	@Override
	public boolean removeFirstOccurrence(Object o) {
		return remove(o);
	}

	@Override
	public E removeLast() {
		try {
			return remove(size()-1);
		} catch (IndexOutOfBoundsException e) {
			throw new NoSuchElementException();
		}
	}

	@Override
	public boolean removeLastOccurrence(Object o) {
		try {
			remove(lastIndexOf(o));
			return true;
		} catch (IndexOutOfBoundsException e) {
			return false;
		}
	}

	@Override
        public E first() {
		return getFirst();
        }

	@Override
        public E last() {
	        return getLast();
        }

	/** Used for quick testing **/
	public static void main(String[] args) {
		UtilArrayList<Integer> list = new UtilArrayList<Integer>();
		list.addAll(Arrays.asList(1, 2, 3, 4, 5));
		// Print list
		System.out.println("List: " + list);
		// Test reversion
		System.out.println("Reverse:" + list.reversed());
		// Test reverse iteration
		for (Integer i : list.reverseIteration()) System.out.println(i.toString());
	}

}
