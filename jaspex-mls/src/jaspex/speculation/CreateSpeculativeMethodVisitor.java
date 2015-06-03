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
import static org.objectweb.asm.Opcodes.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jaspex.ClassFilter;
import jaspex.Options;
import jaspex.stm.NonTransactionalStateTracker;
import jaspex.stm.NonTransactionalStateTracker.SafeOperation;

import asmlib.*;
import asmlib.Type;

/** Cria versões especulativas dos métodos, que quando executam uma operação "forbidden" (que não pode ser
  * executada especulativamente), chamam um método do runtime.SpeculationControl.
  **/
public class CreateSpeculativeMethodVisitor extends MethodVisitor {

	private static final Logger Log = LoggerFactory.getLogger(CreateSpeculativeMethodVisitor.class);

	private final String _methodName;
	private final InfoClass _currentClass;
	private final boolean _active;

	private static MethodVisitor createNextMethodVisitor(int access, String name, String desc, String signature,
		String[] exceptions, ClassVisitor cv) {

		if (name.endsWith("$transactional")) {
			name = name.replace("$transactional", "$speculative");

			// Método original podia ser nativo, mas o $speculative não é
			access &= ~Opcodes.ACC_NATIVE;

			// Todos os métodos $speculative devem ser public (senão o codegen falha com java.lang.IllegalAccessError)
			access = access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC;
		}

		return cv.visitMethod(access, name, desc, signature, exceptions);
	}

	public CreateSpeculativeMethodVisitor(int access, String name, String desc, String signature,
		String[] exceptions, ClassVisitor cv, InfoClass currentClass, Boolean JDKClass) {
		super(Opcodes.ASM4, createNextMethodVisitor(access, name, desc, signature, exceptions, cv));

		_active = name.endsWith("$transactional")
			|| (name.equals("<clinit>") && !JDKClass) // Não modificar <clinit> quando usado com o JDKTransactifier
			|| (name.equals("<init>") && desc.contains(CommonTypes.SPECULATIVECTORMARKER.bytecodeName()));
		_currentClass = currentClass;
		name = name.replace("$transactional", "");
		_methodName = name;

		InfoMethod im = new InfoMethod(access, name, desc, signature, exceptions, null);

		if (_active && im.isSynchronized()) {
			insertBlacklistedActionAttemptedCall("INSIDE SYNCHRONIZED METHOD, USE -REMOVESYNC");
		}

		// Tratamento especial para métodos nativos
		// Ver também no wiki em "Transformações Especulação"
		if (_active && im.isNative()) {
			if (im.isPrivate()) {
				// Se este é o overload de um método nativo privado, temos que corrigir aqui o
				// nome para obter o método original
				name = FixPrivateMethodAccessMethodVisitor.stripPrivate(name);
			}

			mv.visitCode();
			// Contador de posições de argumentos
			int pos = 0;
			if (!im.isStatic()) mv.visitVarInsn(ALOAD, pos++);
			for (Type argType : im.argumentTypes()) {
				mv.visitVarInsn(argType.getLoadInsn(), pos);
				pos += argType.getNumberSlots();
			}
			insertSpeculationControlCall("INVOKE NATIVE METHOD (" + name + desc + ")", "");
			mv.visitMethodInsn(im.isStatic() ? INVOKESTATIC : INVOKESPECIAL,
				_currentClass.type().asmName(), name, desc);
			mv.visitInsn(im.returnType().getReturnInsn());
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
		Type ownerType = Type.fromAsm(owner);
		String methodFullname = ownerType.commonName() + "." + name /*+ desc*/;

		if (_active && ClassFilter.isMethodBlacklisted(ownerType, name, desc)) {
			Log.debug("Code references blacklisted method " + methodFullname);
			insertBlacklistedActionAttemptedCall("INVOKE BLACKLISTED METHOD (" + methodFullname + ")");
		}

		/* Suporte para -nttracker, baseado no código do instanceof Transactional
		 * Altera código para chamar uma versão especial do nonTransactionalActionAttempted se
		 * a classe ou o método estiverem listados no NonTransactionalStateTracker.SAFE_OPERATIONS
		 *
		 * Como o nonTransactionalActionAttempted passa a ter que receber a instância, temos que manipular
		 * a stack para colocar a instância no topo (tal como o instanceof Transactional precisava)
		 */
		if (_active && Options.NTTRACKER && !ClassFilter.isTransactifiable(ownerType) &&
			!ClassFilter.isMethodWhitelisted(ownerType, name, desc)) {
			SafeOperation so = NonTransactionalStateTracker.getSafeOperation(ownerType, name);
			if (so != null) {
				boolean safeMethod = so._safeMethods.contains(name);

				// Por agora suporte apenas para 3 slots de stack usados pelos argumentos;
				// talvez no futuro se possa expandir para qualquer número usando a FakeLocalsStack
				InvokedMethod m = new InvokedMethod(opcode, ownerType, name, desc);
				util.UtilList<Type> args = m.argumentTypes();
				int neededSlots = m.argumentSlotsCount();
				if ((opcode == INVOKEVIRTUAL || opcode == INVOKEINTERFACE) && (neededSlots < 4)) {
					switch (neededSlots) {
						case 0:
							mv.visitInsn(DUP);
							break;
						case 1:
							mv.visitInsn(SWAP);
							mv.visitInsn(DUP_X1);
							break;
						case 2:
							Util.swap(mv, 2, 1);
							mv.visitInsn(DUP_X2);
							break;
						case 3:
							if (args.first().getNumberSlots() == 2) {
								// caso especial, precisa de alguma manipulação extra
								Util.swap(mv, 1, 2);
							}
							Util.swap(mv, 2, 2);
							mv.visitInsn(DUP2_X2);
							mv.visitInsn(POP);
							break;
						default:
							throw new AssertionError();
					}
					// Instância está no topo

					// mutation?
					mv.visitInsn(safeMethod ? ICONST_0 : ICONST_1);

					insertSpeculationControlCall("INVOKE " + methodFullname,
						Type.OBJECT.bytecodeName() + Type.PRIM_BOOLEAN.bytecodeName());

					if (neededSlots == 3 && args.first().getNumberSlots() == 2) {
						// o que faltava do caso especial acima
						Util.swap(mv, 2, 1);
					}
					mv.visitMethodInsn(opcode, owner, name, desc);
					return;
				}
			}
		}

		if (_active && !ClassFilter.isTransactifiable(ownerType) && !ClassFilter.isMethodWhitelisted(ownerType, name, desc)) {
			/* Suporte para instanceof Transactional desactivado, por agora, devido a problema:
			 * Quando se detecta que uma instância é Transactional, e não se faz a chamada ao
			 * nonTransactionalActionAttempted, é executada na mesma a versão "normal" de um método,
			 * não a versão $speculative ou $non_speculative.
			 *
			 * A versão normal de um método é apenas para ser executada (como documentado no wiki,
			 * em "Transformações Especulação") "...numa stack onde já tenha ocorrido um
			 * nonTransactionalActionAttempted, e portanto todas as operações não protegidas neste
			 * método são seguras."
			 * Isto não acontece neste momento.
			 * (De notar que não é só alterar a chamada abaixo, já que o tipo que vemos actualmente
			 * não foi transactificado, e portanto não podemos simplesmente chamar o m$speculative em
			 * vez de m, já que o tipo actual não tem métodos $speculative.
			 *
			 * Existem 3 possíveis soluções, a meu ver:
			 * - Acabar com métodos $non_speculative, e fazer os normais passarem a ser $non_speculative
			 * - Expandir o conceito de instanceof Transactional para instanceof Transactional$ownerType,
			 *   em que para cada tipo non-transactional passa a existir uma interface que contém
			 *   métodos $speculative ou $non_speculative; depois "é só" fazer cast para este tipo
			 *   e chamar o método correcto
			 * - Com uma JDK transactificada, a existência do instanceof Transactional já não faz
			 *   sentido, portanto isto não é uma solução, mas uma nota de que se a JDK passar a ser
			 *   transaccional, já não necessitamos disto.

			// Suporte simples para instanceof Transactional
			// Classes tagged com esta interface foram transactificadas, e portanto podemos
			// invocar o método na mesma (por exemplo, uma classe transactificada que
			// implementa java.util.List é segura)
			// Por agora suporte apenas para 3 slots de stack usados pelos argumentos;
			// talvez no futuro se possa expandir para qualquer número usando a FakeLocalsStack
			InvokedMethod m = new InvokedMethod(opcode, ownerType, name, desc);
			util.UtilList<Type> args = m.argumentTypes();
			int neededSlots = m.argumentSlotsCount();
			boolean isFinal = false;
			try {
				// FIXME: No futuro, poder-se-ia também testar se o método em si é final,
				// já que nesse caso também de certeza que não poderia ter sido substituido
				// com uma versão transaccional
				isFinal = ((Class.forName(ownerType.commonName()).getModifiers() & ACC_FINAL) != 0);
			} catch (ClassNotFoundException e) { throw new Error(e); }
			if (!isFinal && (opcode == INVOKEVIRTUAL || opcode == INVOKEINTERFACE) && (neededSlots < 4)) {
				switch (neededSlots) {
					case 0:
						mv.visitInsn(DUP);
						break;
					case 1:
						mv.visitInsn(SWAP);
						mv.visitInsn(DUP_X1);
						break;
					case 2:
						Util.swap(mv, 2, 1);
						mv.visitInsn(DUP_X2);
						break;
					case 3:
						if (args.first().getNumberSlots() == 2) {
							// caso especial, precisa de alguma manipulação extra
							Util.swap(mv, 1, 2);
						}
						Util.swap(mv, 2, 2);
						mv.visitInsn(DUP2_X2);
						mv.visitInsn(POP);
						break;
					default:
						throw new AssertionError();
				}
				mv.visitTypeInsn(INSTANCEOF, CommonTypes.TRANSACTIONAL.asmName());
				Label l = new Label();
				mv.visitJumpInsn(IFNE, l);
				insertSpeculationControlCall("INVOKE METHOD (" + methodFullname + ")");
				mv.visitLabel(l);
				if (neededSlots == 3 && args.first().getNumberSlots() == 2) {
					// o que faltava do caso especial acima
					Util.swap(mv, 2, 1);
				}
				mv.visitMethodInsn(opcode, owner, name, desc);
				return;
			}*/

			insertSpeculationControlCall("INVOKE " + methodFullname, "");
			mv.visitMethodInsn(opcode, owner, name, desc);
			return;
		}

		if (_active && !name.equals("<init>") &&
			!ClassFilter.isMethodWhitelisted(ownerType, name, desc) && !ownerType.isArray()) {
			// Caso especial para o <clinit>: todos os <clinit> devem comportar-se como $non_speculative,
			// e portanto fazemos já aqui a alteração
			// Nota quanto ao teste do isArray: O array ainda tem os métodos normais herdados
			// de um Object, e são todos inofensivos (portanto não precisam de ser protegidos
			// por uma chamada ao nonTransactionalActionAttempted), mas não é possível fazer
			// o rename deles para $speculative/$non_speculative, portanto também não lhes
			// alteramos o nome
			if (_methodName.equals("<clinit>")) {
				name += "$non_speculative";
			} else {
				name += "$speculative";
			}
		}

		mv.visitMethodInsn(opcode, owner, name, desc);
	}

	@Override
	public void visitInsn(int opcode) {
		if (_active && (opcode == MONITORENTER || opcode == MONITOREXIT)) {
			insertBlacklistedActionAttemptedCall("INVOKE BLACKLISTED OPERATION (MONITORENTER/MONITOREXIT)");
		}

		mv.visitInsn(opcode);
	}

	private void insertSpeculationControlCall(String reason, String extraArgs) {
		if (!Options.FASTMODE) {
			mv.visitLdcInsn("(" + _currentClass.type().commonName() + "." + _methodName +
				"): " + reason);
			mv.visitMethodInsn(INVOKESTATIC, CommonTypes.SPECULATIONCONTROL.asmName(),
				"nonTransactionalActionAttempted", "(" + extraArgs + Type.STRING.bytecodeName() + ")V");
		} else {
			mv.visitMethodInsn(INVOKESTATIC, CommonTypes.SPECULATIONCONTROL.asmName(),
				"nonTransactionalActionAttempted", "(" + extraArgs + ")V");
		}
	}

	private void insertBlacklistedActionAttemptedCall(String reason) {
		mv.visitLdcInsn("(" + _currentClass.type().commonName() + "." + _methodName +
			"$speculative): " + reason);
		mv.visitMethodInsn(INVOKESTATIC, CommonTypes.SPECULATIONCONTROL.asmName(),
			"blacklistedActionAttempted", "(" + Type.STRING.bytecodeName() + ")V");
	}

}
