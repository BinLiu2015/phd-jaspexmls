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

import java.io.IOException;

import asmlib.InfoClass;
import asmlib.Type;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.AnalyzerAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

/** MethodVisitor que substitui chamadas a Object.toString() e Object.hashCode() pela classe mais
  * específica, se a conseguir determinar.
  *
  * Quando uma classe não redefine o hashCode() e o toString(), por alguma razão tanto o javac como
  * o ecj emitem código para chamar Object.toString()/hashCode(), em vez de ser
  * ClasseActual.toString()/hashCode(). Isto faz com que mesmo ao adicionarmos um toString/hashCode
  * transaccional à classe, o código antigo continue a incluir um nonTransactionalActionAttempted,
  * em vez de uma chamada à versão transaccional dos métodos.
  *
  * O objectivo deste MethodVisitor é simplesmente usar o AnalyzerAdapter para determinar o tipo
  * actualmente no topo da stack quando se corre uma destas operações, e substituir Object por esse
  * tipo.
  **/
public class ChangeObjectMethodsMethodVisitor extends MethodVisitor {

	@SuppressWarnings("unused")
	private static final Logger Log = LoggerFactory.getLogger(ChangeObjectMethodsMethodVisitor.class);

	private final AnalyzerAdapter _analyzerAdapter;
	private final InfoClass _currentClass;

	public ChangeObjectMethodsMethodVisitor(int access, String name, String desc, String signature,
		String[] exceptions, ClassVisitor cv, InfoClass currentClass) {
		super(Opcodes.ASM4, new AnalyzerAdapter(currentClass.type().asmName(), access, name, desc,
			cv.visitMethod(access, name, desc, signature, exceptions)));
		_analyzerAdapter = (AnalyzerAdapter) mv;
		_currentClass = currentClass;
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
		String fullName = owner + '.' + name + desc;
		if ((opcode == INVOKEVIRTUAL) &&
			(fullName.equals("java/lang/Object.toString()Ljava/lang/String;") ||
			 fullName.equals("java/lang/Object.hashCode()I"))) {
			Type realOwner = Type.fromAsm(
				(String) _analyzerAdapter.stack.get(_analyzerAdapter.stack.size()-1));
			try {
				// Só fazer alteração se o alvo for uma classe, e não uma interface!
				if (realOwner.equals(_currentClass.type()) ||
					!InfoClass.fromType(realOwner).isInterface()) {
					owner = realOwner.asmName();
				}
			} catch (IOException e) { throw new Error(e); }
		}
		mv.visitMethodInsn(opcode, owner, name, desc);
	}

}
