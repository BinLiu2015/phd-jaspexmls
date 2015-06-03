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

import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

/** MethodVisitor que adiciona um NOP antes de cada Label. O objectivo desta operação é que no
  * FixFutureMultipleControlFlows, a frame relativa ao NOP possa ser analisada, para obter o
  * estado da stack/locals ANTES de vários caminhos no método serem unidos.
  *
  * Para ser exacto, o FixFutureMultipleControlFlows precisa de saber o estado da stack antes de vários
  * ramos de um método se juntarem, para poder detectar de onde (em que slot dos locals/stack) veio o
  * Futuro que gerou o MergedUninitializedValue.
  * O problema que estava a ocorrer era que se um método fosse algo como:
  * 	ifne l2
  * 	astore de future nos locals
  * 	l2:
  * 	...
  * nunca haveria uma frame onde veriamos o future nos locals, porque a frame do astore mostra-lo ia
  * na stack (antes da execução do astore), e a frame do l2 já mostrava o merge dos dois ramos.
  *
  * Para dar a volta a isto são adicionados NOPs, antes de quaisquer label (e posteriormente removidos),
  * de forma que o exemplo anterior fica
  * 	ifne l2
  * 	astore de future nos locals
  * 	nop
  * 	l2:
  * 	...
  * e logo no nop temos uma frame onde vemos o future nos locals.
  **/
public class NopAddBeforeLabelMethodVisitor extends MethodVisitor {

	public NopAddBeforeLabelMethodVisitor(int access, String name, String desc,
			String signature, String[] exceptions, ClassVisitor cv) {
		super(Opcodes.ASM4, cv.visitMethod(access, name, desc, signature, exceptions));
	}

	@Override
	public void visitLabel(Label label) {
		mv.visitInsn(NOP);
		mv.visitLabel(label);
	}

}
