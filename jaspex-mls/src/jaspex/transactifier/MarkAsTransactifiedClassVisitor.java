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

package jaspex.transactifier;

import org.objectweb.asm.*;

import jaspex.speculation.CommonTypes;

import java.util.Arrays;

import static jaspex.util.ShellColor.color;

/** ClassVisitor que marca uma classe como transactificada, adicionando-lhe a interface
  * jaspex.speculation.runtime.Transactional.
  *
  * Opcionalmente altera também a string do nome da classe que aparece em backtraces de excepções
  * para conter informação de que classe foi transactificada.
  **/
public class MarkAsTransactifiedClassVisitor extends ClassVisitor {

	private boolean _visitSource = false;

	public MarkAsTransactifiedClassVisitor(ClassVisitor cv) {
		super(Opcodes.ASM4, cv);
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		String[] newInterfaces = Arrays.copyOf(interfaces, interfaces.length+1);
		newInterfaces[interfaces.length] = CommonTypes.TRANSACTIONAL.asmName();
		cv.visit(version, access, name, signature, superName, newInterfaces);
	}

	@Override
	public void visitSource(String source, String debug) {
		_visitSource = true;
		if (source == null) source = "";
		cv.visitSource(jaspex.Options.DEBUG ? source : color(source + " (Trans)", "32"), debug);
	}

	@Override
	public void visitEnd() {
		if (!_visitSource) visitSource(null, null);
		cv.visitEnd();
	}

}
