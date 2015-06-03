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

package jaspex;

/** Classe que contém alguns métodos especiais que não devem ser transactificados e são usados em colaboração
  * com classes da JDK substítuidas.
  *
  * Muito cuidado com a sua utilização, já que facilmente o resultado pode não ser transaccional. É responsabilidade
  * do cliente desta classe a sua utilização de forma segura.
  **/
public class Builtin {

	public static Object StringBuilder_new() {
		return new StringBuilder();
	}

	public static void StringBuilder_append(Object stringBuilder, String s) {
		((StringBuilder) stringBuilder).append(s);
	}

	public static void StringBuilder_append(Object stringBuilder, char c) {
		((StringBuilder) stringBuilder).append(c);
	}

	public static String StringBuilder_toString(Object stringBuilder) {
		return ((StringBuilder) stringBuilder).toString();
	}

}
