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

package jaspex.util;

import asmlib.Type;
import jaspex.speculation.newspec.ClassHierarchy;

import org.objectweb.asm.ClassReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ClassWriter que implementa o getCommonSuperClass sem precisar de carregar classes na JVM **/
public class ClassWriter extends org.objectweb.asm.ClassWriter {

	private static final Logger Log = LoggerFactory.getLogger(ClassWriter.class);

	public ClassWriter(ClassReader classReader, int flags) {
		super(classReader, flags);
	}

	public ClassWriter(int flags) {
		super(flags);
	}

	@Override
	protected String getCommonSuperClass(String type1, String type2) {
		Type res = ClassHierarchy.getCommonType(Type.fromAsm(type1), Type.fromAsm(type2));

		if (!jaspex.Options.FASTMODE) {
			try {
				String originalRes = super.getCommonSuperClass(type1, type2);
				if (!originalRes.equals(Type.OBJECT.asmName()) && !res.asmName().equals(originalRes)) {
					Log.warn("getCommonSuperClass divergence (" + type1 + ", " + type2 +
							") -> {" + res.asmName() + ", " + originalRes + "}");
				}
			} catch (LinkageError e) {
				// Isto pode falhar porque asm tenta carregar classe que está a ser processada
			} catch (RuntimeException e) {
				// Pode acontecer em alguns casos, e temos que fazer este hack porque o ASM
				// mantém a mensagem e deita fora a excepção original...
				if (!e.getMessage().startsWith("java.lang.ClassNotFoundException")) throw e;
			}
		}

		return res.asmName();
	}

}
