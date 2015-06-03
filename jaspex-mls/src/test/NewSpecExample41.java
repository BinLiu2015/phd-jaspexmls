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

/** Exemplo/teste para o instanceof Transactional
  *
  * Sempre que se acede a uma instância de NewSpecExample41, a versão $speculative dos métodos deve ser
  * executada, não a versão normal (neste caso, size$speculative() vs size()), qualquer que seja o método
  * de acesso (ou seja, sabendo o tipo ou apenas usando List).
  **/
public class NewSpecExample41 extends ArrayList<Integer> {

	private static final long serialVersionUID = 1L;

	private NewSpecExample41() { super(); }

	@Override
	public int size() {
		new Throwable().printStackTrace();
		return 0;
	}

	public static void main(String[] args) {
		NewSpecExample41 list = new NewSpecExample41();
		System.out.println("Size with correct type: " + list.size());
		List<?> noTypeList = list;
		System.out.println("Size with List type (trans): " + noTypeList.size());
		noTypeList = new ArrayList<Integer>();
		System.out.println("Size with List type (non-trans): " + noTypeList.size());
	}

}
