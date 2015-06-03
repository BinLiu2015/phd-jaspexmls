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

/** Testecase para execução de <clinit> especulativamente.
 *
  * A ideia é a program-order thread executar m1() que modifica i (o que o resto do ciclo faz não é
  * importante), ao mesmo tempo que uma thread especulativa executa m2().
  *
  * Como m2() se executará competamente antes de m1() [a não ser que exista alguma anomalia no scheduling
  * das threads], a leitura+escrita de m2() em i está condenada, e como em m2() está a primeira referência
  * à classe Helper, é dentro do seu contexto que o <clinit> de Helper vai ser executado.
  *
  * Quando m2() tentar fazer commit, irá abortar, e re-executar, mas ao abortar o resultado do <clinit>
  * é apagado.
  */
class Helper {
	static int val;

	static {
		val = getval();
	}

	static int getval() { return 42; }
}

public class NewSpecExample35 {

	private NewSpecExample35() { }

	private static int i;

	public static void m1() throws Exception {
		jaspex.Debug.sleep(500);
		m1b();
	}

	public static void m1b() {
		i = 58;
	}

	public static void m2() {
		i += Helper.val;
	}

	public static void m3() {
		System.out.println("Final values i:" + i + " val:" + Helper.val +
				(i == 100 ? " SUCCESS" : " FAILED!!"));
	}

	public static void main(String[] args) throws Exception {
		m1();
		m2();
		jaspex.Debug.sleep(1000);
		m3();
	}

}
