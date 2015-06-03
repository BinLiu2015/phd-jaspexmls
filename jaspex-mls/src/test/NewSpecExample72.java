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

/** Classe de teste para o RemoveOverspeculation **/
public class NewSpecExample72 {

	private NewSpecExample72() { }

	private static void m() { }
	private static int m2() { return 0; }
	private static boolean m3() { return false; }

	/** Não deve haver especulação aqui **/
	@SuppressWarnings("unused")
	private static void test1(boolean val) {
		int i = 0;
		if (val) {
			i = m2();
		}
		System.out.println(i);
	}

	/** Não deve haver especulação neste método, com ou sem RVP **/
	@SuppressWarnings("unused")
	private static void test2() {
		int i = 0;
		boolean val = m3();
		i++;
		System.out.println(i);
	}

	/** Não deve haver especulação aqui **/
	@SuppressWarnings("unused")
	private static void test3(int val) {
		m();
		int i = val;
		switch (i) {
			case 0:
				i++;
				break;
			case 1:
				i--;
			case 2:
				i++;
				break;
			default:
				i = 42;
		}
		System.out.println(i);
	}

	/** Ok fazer especulação de m(), não de m2() **/
	@SuppressWarnings("unused")
	private static void test4(int val) {
		m();
		int i = val;
		switch (i) {
			case 0:
				i++;
				break;
			case 1:
				m2();
				i--;
			case 2:
				i++;
				break;
			default:
				i = 42;
		}
		System.out.println(i);
	}

	/** Ok fazer especulação de m(), não de m2() **/
	@SuppressWarnings("unused")
	private static void test5(int val) {
		m();
		int i = val;
		switch (i) {
			case 0:
				i++;
				break;
			case 1:
				i--;
			case 2:
				i++;
				break;
			default:
				m2();
				i = 42;
		}
		System.out.println(i);
	}

	/** Ok fazer especulação de m() **/
	@SuppressWarnings("unused")
	private static void test9(int val) {
		m();
		for (int i = 0; i < 10; i++) {
			val += i;
		}
		System.out.println(val);
	}

	/** Ok fazer especulação de m() **/
	@SuppressWarnings("unused")
	private static int test10(int val) {
		m();
		if (val == 0) {
			System.out.println(val);
		}
		return 0;
	}

	/** Ok fazer especulação de m() **/
	@SuppressWarnings("unused")
	private static void test11(int val) {
		m();
		int i = val;
		switch (i) {
			case -100:
				System.out.println();
				i++;
				break;
			case 0:
				i--;
			case 100:
				i++;
				break;
			default:
				m2();
				i = 42;
		}
		System.out.println(i);
	}

	/** Não deve ser feita especulação **/
	@SuppressWarnings("unused")
	private static void test12(int val) {
		if (val == 0) {
			m();
		} else {
			m();
		}
		System.out.println();
	}

	/** Ok fazer especulação de m2() **/
	@SuppressWarnings("unused")
	private static int test13(int val) {
		int i = m2();
		switch (val) {
			case 0:
				return 0;
			case 2:
				System.out.println();
				break;
			default:
				System.out.println();
				break;
		}
		return 0;
	}

	/** Não deve ser feita especulação
	  * Este teste usa todos os bytecodes para branches.
	  **/
	@SuppressWarnings("unused")
	private static void test14(int val1, int val2, Object val3, Object val4) {
		m2();
		if (val1 <  val2) { val1++; } else { val1--; }
		if (val1 <= val2) { val1++; } else { val1--; }
		if (val1 == val2) { val1++; } else { val1--; }
		if (val1 != val2) { val1++; } else { val1--; }
		if (val1 >= val2) { val1++; } else { val1--; }
		if (val1 >  val2) { val1++; } else { val1--; }
		if (val1 <  0)    { val1++; } else { val1--; }
		if (val1 <= 0)    { val1++; } else { val1--; }
		if (val1 == 0)    { val1++; } else { val1--; }
		if (val1 != 0)    { val1++; } else { val1--; }
		if (val1 >= 0)    { val1++; } else { val1--; }
		if (val1 >  0)    { val1++; } else { val1--; }
		if (val3 == null) { val1++; } else { val1--; }
		if (val3 != null) { val1++; } else { val1--; }
		if (val3 == val4) { val1++; } else { val1--; }
		if (val3 != val4) { val1++; } else { val1--; }
		System.out.println();
	}

	/** Ok fazer especulação de m() **/
	@SuppressWarnings("unused")
	private static int test15(int val) {
		m();
		if (val == 0) {
			return 0;
		}
		System.out.println();
		return 0;
	}

	/** Não deve ser feita especulação **/
	@SuppressWarnings("unused")
	private static int test16(int val) {
		int i = m2();
		switch (val) {
			case 0:
				return i++;
			case 2:
				System.out.println();
				break;
			default:
				System.out.println();
				break;
		}
		return 0;
	}

	/** Não deve ser feita especulação **/
	@SuppressWarnings("unused")
	private static void test17(int val) {
		int i = 0;
		if (val == 0) {
			i = m2();
		} else if (val == 1) {
			i = m2();
		} else {
			i = m2();
		}
		i++;
		jaspex.Debug.println("" + i);
	}

	public static void main(String[] args) {

	}

}
