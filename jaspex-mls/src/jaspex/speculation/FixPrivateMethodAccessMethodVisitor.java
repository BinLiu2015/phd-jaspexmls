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

import asmlib.*;
import asmlib.Type;

import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;

/** MethodVisitor com solução parcial ao problema do acesso aos métodos privados.
  * Devido à utilização dos callables gerados para aceder a métodos, todos os métodos precisam de passar
  * a ser públicos.
  * Infelizmente, no caso de métodos privados isso pode causar erros na semântica do programa:
  * (versão mais simples do NewSpecExample56)
  *
  * class A {
  * 	private void m() {
  * 		System.out.println("A.m()");
  * 	}
  *
  * 	public void runM() {
  * 		m(); // isto TEM DE SER um invokespecial
  * 	}
  * }
  *
  * class B extends A {
  * 	public void m() {
  * 		System.out.println("B.m()");
  * 	}
  *
  *   	public static void main(String[] args) {
  * 		new B().m();
  * 		new B().runM();
  * 	}
  * }
  *
  * Neste caso, se A.m() passar a público, a semântica do programa é alterada.
  * (De notar que infelizmente não é possível usar um invokespecial no callable para executar a versão
  * correcta de m()).
  *
  * Para resolver isto, esta classe começa com duas tranformações:
  * * Transformar métodos privados para ter $private no nome
  * * Alterar chamadas com invokespecial a estes métodos para usarem o novo nome
  *
  * O método é também convertido para final, já que é equivalente semanticamente, e em caso de bug e existir
  * um duplicado na hierarquia, é causado um erro no verificador da VM ao carregar as classes.
  **/
public class FixPrivateMethodAccessMethodVisitor extends MethodVisitor {
	private final InfoClass _currentClass;
	private final boolean _active;

	private static String privateName(InfoClass infoClass) {
		return "$private$" + infoClass.type().commonName().replace('.', '_');
	}

	private static boolean test(int access, int flag) {
		return (access & flag) != 0;
	}

	private static boolean testAccess(int access) {
		// Testar se é privado e não é static
		return test(access, ACC_PRIVATE) && !test(access, ACC_STATIC);
	}

	private static int convertAccess(int access, String name) {
		if (name.endsWith("$transactional")) {
			if (testAccess(access)) {
				// Vamos renomear o método, e já agora marcamos como final
				return access | ACC_FINAL;
			} else if (test(access, ACC_PRIVATE) && test(access, ACC_STATIC)) {
				// Caso ainda mais estranho (NewSpecExample58): Método private static final
				// Neste caso não precisamos de renomear os métodos, já que o INVOKESTATIC
				// funciona como o INVOKESPECIAL para métodos não-de-instância.
				// MAS, se o método for final isto causa problemas, logo basta retirar o final
				return access & ~ACC_FINAL;
			}
		}
		return access;
	}

	private static String convertName(int access, String name, InfoClass infoClass) {
		if (name.endsWith("$transactional") && testAccess(access)) {
			name = name.replace("$transactional", privateName(infoClass) + "$transactional");
		}
		return name;
	}

	public FixPrivateMethodAccessMethodVisitor(int access, String name, String desc, String signature,
		String[] exceptions, ClassVisitor cv, InfoClass currentClass, Boolean JDKClass) {
		super(Opcodes.ASM4, cv.visitMethod(
			convertAccess(access, name), convertName(access, name, currentClass), desc, signature, exceptions));
		_currentClass = currentClass;
		_active = name.endsWith("$transactional")
			|| (name.equals("<clinit>") && !JDKClass) // Não modificar <clinit> quando usado com o JDKTransactifier
			|| (name.equals("<init>") && desc.contains("jaspex/MARKER/Transactional"));
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
		if (_active && opcode == INVOKESPECIAL && !name.equals("<init>")
			&& Type.fromAsm(owner).equals(_currentClass.type())) {
			assert (testAccess(_currentClass.getMethod(name, desc).access()));
			// Nesta altura do processamento os INVOKE* ainda não foram modificados, logo
			// os nomes dos métodos nos INVOKE* não acabam com $transactional
			name += privateName(_currentClass);
		}
		mv.visitMethodInsn(opcode, owner, name, desc);
	}

	/** Retira parte $private$ do nome do método **/
	public static String stripPrivate(String methodName) {
		int pos = methodName.indexOf("$private$");
		if (pos < 0) return methodName;
		int end = methodName.indexOf('$', pos + 1 + "$private$".length());
		return methodName.substring(0, pos) + (end < 0 ? "" : methodName.substring(end));
	}
}
