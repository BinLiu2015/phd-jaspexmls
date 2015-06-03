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

import jaspex.ClassFilter;
import asmlib.Type;
import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

/** MethodVisitor que substitui o jaspex/MARKER/Transactional pelo SPECULATIVECTORMARKER na assinatura dos
  * constructores, e altera o código de métodos transactificados para usar estas novas versões dos constructores.
  **/
public class SpeculativeCtorMethodVisitor extends MethodVisitor {

	private final boolean _isClinit;
	private final boolean _active;

	private static String convertDesc(String desc) {
		return desc.replace("Ljaspex/MARKER/Transactional;)", CommonTypes.SPECULATIVECTORMARKER.bytecodeName() + ")");
	}

	public SpeculativeCtorMethodVisitor(int access, String name, String desc, String signature, String[] exceptions, ClassVisitor cv) {
		super(Opcodes.ASM4, cv.visitMethod(access, name,
			(name.equals("<init>") ? convertDesc(desc) : desc), signature, exceptions));
		_isClinit = name.equals("<clinit>");
		_active = _isClinit || name.endsWith("$transactional") ||
				(name.equals("<init>") && desc.contains("jaspex/MARKER/Transactional"));
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
		if (_active && name.equals("<init>") && opcode == INVOKESPECIAL && ClassFilter.isTransactifiable(Type.fromAsm(owner))) {
			mv.visitInsn(ACONST_NULL);
			Type marker = _isClinit ?
				CommonTypes.NONSPECULATIVECTORMARKER /* <clinit> é sempre "$non_speculative" */
				: CommonTypes.SPECULATIVECTORMARKER;
			desc = desc.replace(")", marker.bytecodeName() + ")");
		}

		mv.visitMethodInsn(opcode, owner, name, desc);
	}

}
