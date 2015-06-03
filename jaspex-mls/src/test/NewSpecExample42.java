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

import java.util.*;

/** Exemplo/teste para o instanceof Transactional **/
public class NewSpecExample42 extends ArrayList<Integer> {

	private static final long serialVersionUID = 1L;

	private NewSpecExample42() { super(); }

	@Override
	public boolean addAll(int index, Collection<? extends Integer> c) { return false; }

	public static void main(String[] args) {
		NewSpecExample42 list = new NewSpecExample42();
		System.out.println("addAll with correct type: " + list.addAll(0, null));
		List<?> noTypeList = list;
		System.out.println("addAll with List type (trans): " + noTypeList.addAll(0, null));
		noTypeList = new ArrayList<Integer>();
		System.out.println("addAll with List type (non-trans): " + noTypeList.addAll(0, null));
	}

}
