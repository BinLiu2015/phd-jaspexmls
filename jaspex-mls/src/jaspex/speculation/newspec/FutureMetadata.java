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

package jaspex.speculation.newspec;

import asmlib.Type;

import util.StringList;

/** Para passar informação extra entre o InsertContinuationSpeculationMethodVisitor, o
  * FixFutureMultipleControlFlows e o DelayGetFutureMethodVisitor, os Futuros inseridos
  * pelo primeiro contêm metadados extra (como o tipo original que está a ser substituido
  * por um Futuro) dentro do próprio tipo.
  *
  * O formato utilizado neste momento é
  * java/util/concurrent/Future$$encodeType(bytecodename do tipo original)$$id spawn
  * mas nenhuma das restantes classes deve saber nem manipular o tipo directamente;
  * a única coisa que pode ser assumida é que o formato começará sempre com
  * java/util/concurrent/Future$
  *
  * Esta classe serve para centralizar o acesso e criação destes Futuros estendidos, que
  * originalmente estava espalhado pelas três classes.
  **/
public class FutureMetadata {

	private static final String DIVIDER = "$$";
	private static final String ASM_FUTURE_PREFIX = jaspex.speculation.CommonTypes.FUTURE.asmName() + DIVIDER;
	public static final String FUTURE_PREFIX = "L" + ASM_FUTURE_PREFIX;

	private final Type _returnType;
	private final int _id;

	public Type returnType() {
		return _returnType;
	}

	public int id() {
		return _id;
	}

	public FutureMetadata(Type returnType, int id) {
		_returnType = returnType;
		_id = id;
	}

	public String bytecodeName() {
		return FUTURE_PREFIX + encodeType(_returnType.bytecodeName()) + DIVIDER + _id + ";";
	}

	public static FutureMetadata fromBytecode(String bytecodeName) {
		return fromAsm(bytecodeName.substring(1, bytecodeName.length() - 1));
	}

	public static FutureMetadata fromAsm(String asmName) {
		if (!asmName.startsWith(ASM_FUTURE_PREFIX)) throw new AssertionError("Unexpected " + asmName);

		StringList metadata = util.StringList.split(asmName, DIVIDER);

		return new FutureMetadata(
			Type.fromBytecode(decodeType(metadata.get(1))), Integer.parseInt(metadata.get(2)));
	}

	/** Substituir alguns caracteres especiais dos tipos antes de fazer embedding no futuro.
	  * Até agora só o ';' parece dar problemas. (Esta estratégia substitui o antigo tryHackFixTerminator)
	  **/
	private static String encodeType(String bytecodeName) { return bytecodeName.replace(';', ':'); }
	private static String decodeType(String encodedType)  { return  encodedType.replace(':', ';'); }

}
