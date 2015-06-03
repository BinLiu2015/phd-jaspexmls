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

/** Teste que falha pois o FixFutureMultipleControlFlows tenta descobrir os problemas por ordem de bytecode,
  * e neste caso vai começar a analisar o try/catch a pensar que será o problemNode, enquanto que devido
  * aos loops serem compilados com o teste no final, o verdadeiro problemNode vem depois do código do catch
  * no bytecode original.
  *
  * Como resolver? Parece-me que a solução passaria pelo FixFutureMultipleControlFlows só começar a analisar
  * problemNodes sem predecessores depois de resolver os problemas dos outros, mas a dificuldade está em
  * definir quais são os "outros" correctos.
  *
  * Outra ideia: "reachable from behind/forward" -- ao considerar a problemFrame, determinar se ela é
  * reachable do inicio do método, ou de código depois dela (construindo um grafo or something). Se for código
  * depois, então ver se existe outra problemFrame que esteja nessa condição, e so on. Se encontrada, usar essa
  * em vez da original. ==> Ou seja, processar as problemFrames pela ordem real do fluxo no método, e não
  * pela ordem no bytecode.
  **/
public class NewSpecExample20 {
	private NewSpecExample20() { }

	static int doA() { return 0; }

	@SuppressWarnings("unused")
	static void doSomething() {
		int k = 0;
		for (int i = doA(); k < 10; i++, k++) {
			try {
				for (long j = 0; j > 0; j++) { }
			} catch (Throwable t) {	}
		}
	}

	public static void main(String[] args) {
		doSomething();
	}

}
