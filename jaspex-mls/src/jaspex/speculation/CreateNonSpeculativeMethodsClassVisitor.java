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

import org.objectweb.asm.*;

/** ClassVisitor utilizado para copiar todos os métodos $speculative para uma versão $non_speculative.
  *
  * A versão $non_speculative dos métodos executa-se com protecção da STM mas nunca tenta fazer
  * especulação (e portanto não sofre o overhead de o tentar fazer).
  *
  * Actualmente, métodos $non_speculative são usados em dois casos:
  * - oldspec decidiu temporariamente deixar de fazer especulações
  * - <clinit> está a ser executado
  *
  * Não confundir os métodos $non_speculative com os originais (que não são modificados); os originais são
  * usados apenas em casos muito especiais:
  * - Implementação de interfaces e de métodos abstractos de superclasses não-transactificáveis
  * - Métodos nativos
  * Portanto é necessária a existência tanto das versões originais como das $non_speculative.
  *
  * (De notar que a versão original de um método só deve ser chamada numa task onde já tenha ocorrido um
  * nonTransactionalActionAttempted(), e portanto todas as suas operações já são consideradas seguras.)
  *
  * Ver também no wiki em "Transformações Especulação" algumas notas sobre isto.
  **/
public class CreateNonSpeculativeMethodsClassVisitor extends ClassVisitor {

	public static String convertName(String name) {
		return name.replace("$speculative", "$non_speculative");
	}

	public static String convertDesc(String desc) {
		return desc.replace(CommonTypes.SPECULATIVECTORMARKER.bytecodeName(),
					CommonTypes.NONSPECULATIVECTORMARKER.bytecodeName());
	}

	static class CreateNonSpeculativeMethodVisitor extends MethodVisitor {
		public CreateNonSpeculativeMethodVisitor(int access, String name, String desc, String signature,
			String[] exceptions, ClassVisitor cv) {
			super(Opcodes.ASM4, cv.visitMethod(access, convertName(name), convertDesc(desc),
								signature, exceptions));
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String desc) {
			mv.visitMethodInsn(opcode, owner, convertName(name), convertDesc(desc));
		}
	}

	// Excepção ao code style normal para simular o ClassAdapter
	private final ClassVisitor cv;

	public CreateNonSpeculativeMethodsClassVisitor(ClassVisitor cv) {
		super(Opcodes.ASM4);
		this.cv = cv;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature,
		String[] exceptions) {
		if (name.equals("<init>")) {
			if (!desc.contains("/SpeculativeCtorMarker")) return null;
		} else {
			if (!name.endsWith("$speculative")) return null;
		}
		return new CreateNonSpeculativeMethodVisitor(access, name, desc, signature, exceptions, cv);
	}

	@Override
	public void visitEnd() {
		// Necessário porque não estamos a funcionar como ClassAdapter, mas como ClassVisitor
		cv.visitEnd();
	}

}
