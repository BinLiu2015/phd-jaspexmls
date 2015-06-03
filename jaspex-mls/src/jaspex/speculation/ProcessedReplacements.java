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

package jaspex.speculation;

/** Semelhante ao Replacements, mas permitimos que esta classe seja carregada pelo SpeculativeClassLoader **/
public class ProcessedReplacements {

	/** Método implementado em colaboração com o Replacements porque a assinatura pedida tem
	  * que incluir o List carregado pelo SpeculativeClassLoader, o que significa que esta
	  * classe tem que ser carregada pelo SpeculativeClassLoader. O trabalho em si é feito
	  * pelo método no Replacements, que usa reflection (normalmente proíbido durante
	  * especulação) para obter o verdadeiro resultado, e o converter entre uma Lista
	  * carregada pelo ClassLoader do sistema e uma carregada pelo SpeculativeClassLoader.
	  **/
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static java.util.List<java.lang.management.GarbageCollectorMXBean>
		java_lang_management_ManagementFactory_getGarbageCollectorMXBeans() {
		return (java.util.List) Replacements.convertGarbageCollectorMXBeans();
	}

}
