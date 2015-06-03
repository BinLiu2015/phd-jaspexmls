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

import java.util.*;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.*;
import asmlib.*;

import jaspex.speculation.CommonTypes;
import jaspex.speculation.InvokedMethod;
import jaspex.speculation.runtime.CodegenHelper;

import static jaspex.speculation.newspec.DelayGetFutureMethodVisitor.returnOpcodes;

/** Classe tenta identificar e resolver casos de "overspeculation", em que é inserida uma especulação mas
  * rapidamente é encontrada uma instrucção não-transaccional ou que precisa do valor de retorno do método.
  *
  * Nota: Esta classe permite que cross-overspeculation venha a acontecer. Isto porque num caso como
  * int m() {
  *   spawnspeculation m2();
  *   return 1;
  * }
  * Fica um futuro pendente à saida de m(). O problema é que composto com
  * int m2() {
  *   spawnspeculation m3();
  *   return 0;
  * }
  * A execução especulativa de m2() foi inútil: consiste apenas em criar uma nova especulação para m3,
  * executar o return, e terminar a SpeculationTask.
  * Como resolver isto? Talvez adicionar um argumento extra os métodos que indique se deve ou não ser
  * permitido um futuro ficar pendente (como indicar a profundidade da callstack?)?
  *
  * (Um caso especial são os métodos que retornam void, que iriam sempre causar isto, e nesses não são
  * permitidos futuros pendentes).
  **/
public class RemoveOverspeculation {

	private static final Logger Log = LoggerFactory.getLogger(RemoveOverspeculation.class);

	public static boolean scanOverspeculation(InfoClass currentClass, ClassNode cNode,
		Map<InfoMethod, UtilList<Integer>> rejectedSpecIdsMap) {
		boolean changedMap = false;

		if (!jaspex.Options.NOREMOVEOVERSPEC) for (MethodNode m : cNode.methods) {
			if (!m.name.endsWith("$speculative") &&
				(!m.name.equals("<init>") ||
				 !m.desc.contains(CommonTypes.SPECULATIVECTORMARKER.bytecodeName()))) {
				continue;
			}

			//Log.debug("Checking {}", m);
			RemoveOverspeculation ro = new RemoveOverspeculation(m);
			UtilList<Integer> rejectedSpecIds = ro._rejectedSynchronization;

			if (ro._foundSpawn) {
				Log.trace("RemoveOverspeculation " + currentClass.type().commonName() + "." +
					m.name + ": NeedValue " + ro._rejectedNeedValue +
					", Sync " + ro._rejectedSynchronization);
			}

			// No modo agressivervp permitimos os "rejectedNeedValue" (ver scanMethod)
			if (!jaspex.Options.AGRESSIVERVP) rejectedSpecIds.addAll(ro._rejectedNeedValue);

			if (!rejectedSpecIds.isEmpty()) {
				InfoMethod currentMethod = currentClass.getMethod(m.name, m.desc);
				UtilList<Integer> existingRejected = rejectedSpecIdsMap.get(currentMethod);

				if (existingRejected == null) {
					rejectedSpecIdsMap.put(currentMethod, rejectedSpecIds);
					changedMap = true;
				} else {
					for (Integer i : rejectedSpecIds) {
						if (!existingRejected.contains(i)) {
							existingRejected.add(i);
							changedMap = true;
						}
					}
				}
			}
		}

		//Log.debug("RemoveOverspeculation result: {}", rejectedSpecIdsMap);

		return changedMap;
	}

	private final MethodNode _mNode;
	// Rejeitados devido a ser necessário o valor do futuro (get)
	private final UtilList<Integer> _rejectedNeedValue = new UtilArrayList<Integer>();
	// Rejeitados devido a ser necessária sincronização (nonTransactionalActionAttempted)
	private final UtilList<Integer> _rejectedSynchronization = new UtilArrayList<Integer>();
	// É feita especulação neste método?
	private boolean _foundSpawn;

	private RemoveOverspeculation(MethodNode mNode) {
		_mNode = mNode;

		scanMethod();
	}

	private void scanMethod() {
		for (AbstractInsnNode node = _mNode.instructions.getFirst(); node != null; node = node.getNext()) {
			if (node instanceof MethodInsnNode) {
				MethodInsnNode mInsn = (MethodInsnNode) node;

				if (mInsn.owner.equals(CommonTypes.CONTSPECULATIONCONTROL.asmName()) &&
					mInsn.name.equals("spawnSpeculation")) {
					_foundSpawn = true;
					Log.trace("  Tracking {} ({})", getFutureId(mInsn), getFutureType(mInsn));
					TrackingResult res = scanFuture(mInsn, mInsn.getNext());
					if (res != TrackingResult.OK) {
						addOverspeculation(mInsn, res);
					}
				}
			}

		}
	}

	/** Possíveis retornos do scanFuture **/
	private enum TrackingResult {
		/** Especulação deve ser inserida **/
		OK,
		/** Overspeculation, precisamos do valor da especulação muito cedo **/
		NEEDVALUE,
		/** Overspeculation, especulação vai terminar muito cedo **/
		FORCEDSYNC;

		/** Junta dois TrackingResults, escolhendo o superset de ambos.
		  * Quando existem vários TrackingResults para um futuro (porque podem acontecer diferentes
		  * coisas em diferentes caminhos pelo método), temos que escolher um "superset" que determina
		  * o valor a ser retornado pelo scanFuture.
		  *
		  * Neste momento a estratégia implementada pelo combine é a de "arriscar" e permitir
		  * overspeculation, desde que um dos caminhos possíveis não o seja. Talvez no futuro possa
		  * ser adicionado outro modo mais conservador (sendo que basta alterar o combine).
		  **/
		public static TrackingResult combine(TrackingResult tr1, TrackingResult tr2) {
			return tr1.compareTo(tr2) <= 0 ? tr1 : tr2;
		}
	}

	/** Scan simples para tentar eliminar overspeculation.
	  *
	  * A ideia deste método é determinar se entre uma operação de spawnSpeculation e o get do seu Future /
	  * nonTransactionalActionAttempted existem instrucções suficientes para valer o trabalho de fazer
	  * especulação.
	  *
	  * Quando observamos um spawnSpeculation, começamos a fazer tracking dele. Se virmos alguma
	  * operação complexa (alguns saltos, outros métodos, etc), paramos o tracking. Caso contrário, se
	  * ainda estivermos a fazer tracking quando encontramos o get / nonTransactionalActionAttempted, então
	  * colocamos o método na lista de especulações a rejeitar.
	  **/
	private TrackingResult scanFuture(MethodInsnNode tracking, AbstractInsnNode start) {
		for (AbstractInsnNode node = start; node != null; node = node.getNext()) {
			if (node instanceof MethodInsnNode) {
				MethodInsnNode mInsn = (MethodInsnNode) node;

				if (mInsn.owner.equals(CommonTypes.CONTSPECULATIONCONTROL.asmName()) &&
					mInsn.name.equals("spawnSpeculation")) {
					// do nothing
				} else if (mInsn.owner.startsWith(CommonTypes.FUTURE.asmName()) && mInsn.name.equals("get")) {
					int futureId = Integer.parseInt(mInsn.owner.substring(mInsn.owner.lastIndexOf('$')+1));

					if (getFutureId(tracking) == futureId) {
						return TrackingResult.NEEDVALUE;
					}
				} else if (mInsn.owner.equals(CommonTypes.SPECULATIONCONTROL.asmName())
						&& (mInsn.name.equals("nonTransactionalActionAttempted") ||
							mInsn.name.equals("blacklistedActionAttempted"))) {
					return TrackingResult.FORCEDSYNC;
				} else if (mInsn.owner.equals(CommonTypes.TRANSACTION.asmName())
						|| mInsn.owner.equals(CommonTypes.MARKER_BEFOREINLINEDSTORE)
						|| mInsn.owner.equals(CommonTypes.DEBUGCLASS.asmName())) {
					continue;
				} else if (mInsn.owner.equals(CommonTypes.REPLACEMENTS.asmName())) {
					// do nothing
				} else if (mInsn.owner.startsWith("jaspex")
						&& !CodegenHelper.isCodegenClass(Type.fromAsm(mInsn.owner))) {
					throw new AssertionError("Resetting tracking due to call to " +
									mInsn.owner + "." + mInsn.name);
				}

				return TrackingResult.OK;
			} else if (node instanceof JumpInsnNode) {
				JumpInsnNode jInsn = (JumpInsnNode) node;

				if (_mNode.instructions.indexOf(jInsn) > _mNode.instructions.indexOf(jInsn.label)) {
					// Salto para trás, não continuar tracking
					return TrackingResult.OK;
				}

				if (jInsn.getOpcode() == Opcodes.GOTO) return scanFuture(tracking, jInsn.label);
				if (jInsn.getOpcode() == Opcodes.JSR) throw new AssertionError();

				// Opcode é um de
				// IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT,
				// IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE, IFNULL, IFNONNULL
				// Portanto vamos ter que analisar ambos os branches

				return TrackingResult.combine(scanFuture(tracking, jInsn.getNext()),
								scanFuture(tracking, jInsn.label));
			} else if (node instanceof LookupSwitchInsnNode
				|| node instanceof TableSwitchInsnNode) {
				List<LabelNode> targets = new UtilArrayList<LabelNode>();

				// Infelizmente o ASM não tem uma classe comum entre os dois tipos de switch
				if (node instanceof LookupSwitchInsnNode) {
					LookupSwitchInsnNode sInsn = (LookupSwitchInsnNode) node;
					if (sInsn.dflt != null) targets.add(sInsn.dflt);
					targets.addAll(sInsn.labels);
				} else {
					TableSwitchInsnNode sInsn = (TableSwitchInsnNode) node;
					if (sInsn.dflt != null) targets.add(sInsn.dflt);
					targets.addAll(sInsn.labels);
				}

				// Vamos analisar todos os targets do switch e fazer merge do resultado
				TrackingResult mergedResult = null;
				for (LabelNode l : targets) {
					TrackingResult res = scanFuture(tracking, l);

					if (mergedResult == null) {
						mergedResult = res;
					} else {
						mergedResult = TrackingResult.combine(mergedResult, res);
					}
				}

				return mergedResult;
			} else if (node instanceof InsnNode) {
				InsnNode insn = (InsnNode) node;

				// Encontrámos fim do método
				if (insn.getOpcode() == Opcodes.ATHROW
					|| returnOpcodes.contains(insn.getOpcode())) {
					if (new InvokedMethod(_mNode).returnType().equals(Type.PRIM_VOID)) {
						// Caso especial: Todos os métodos que retornam void são
						// escolhidos para especulação -- Uma consequência directa
						// disso é que ter um futuro activo aquando do retorno do
						// método não serve para nada, já que a única coisa que vai
						// acontecer é a especulação acabada de criar vai tentar fazer
						// commit imediatamente
						return TrackingResult.FORCEDSYNC;
					}

					return TrackingResult.OK;
				}
			}
		}

		// FIXME: Alguma vez aqui chegamos?
		//return TrackingResult.OK;
		throw new AssertionError("FIXME");
	}

	private void addOverspeculation(MethodInsnNode tracking, TrackingResult tr) {
		int specId = getFutureId(tracking);
		Type futureType = getFutureType(tracking);
		Log.trace("  Overspeculation detected for FutureId {} ({})", specId, futureType);
		if (tr == TrackingResult.NEEDVALUE && jaspex.Options.RVP &&
			(futureType.equals(Type.PRIM_BOOLEAN) || futureType.equals(Type.OBJECT_BOOLEAN))) {
			// Permitir overspeculation em booleans quando estamos a usar RVP
		} else {
			if (tr == TrackingResult.NEEDVALUE) {
				_rejectedNeedValue.add(specId);
			} else {
				_rejectedSynchronization.add(specId);
			}
		}
	}

	private static int getFutureId(MethodInsnNode mInsn) {
		return FutureMetadata.fromBytecode(mInsn.desc.substring(mInsn.desc.indexOf(')') + 1)).id();
	}

	private static Type getFutureType(MethodInsnNode mInsn) {
		return FutureMetadata.fromBytecode(mInsn.desc.substring(mInsn.desc.indexOf(')') + 1)).returnType();
	}

}
