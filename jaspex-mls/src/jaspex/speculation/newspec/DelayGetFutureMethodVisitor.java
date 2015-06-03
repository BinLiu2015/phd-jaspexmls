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

import jaspex.speculation.CommonTypes;
import jaspex.speculation.InvokedMethod;

import java.util.Arrays;
import java.util.List;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.*;

import asmlib.*;
import asmlib.Type;

import static jaspex.speculation.CommonTypes.FUTURE;
import static jaspex.speculation.CommonTypes.TRANSACTION;
import static jaspex.speculation.newspec.DelayGetFutureMethodVisitor.extractReturnType;
import static jaspex.speculation.newspec.DelayGetFutureMethodVisitor.isFuture;
import static org.objectweb.asm.Opcodes.*;

/** Classe que simula uma stack usando variáveis locais para guardar os elementos.
  *
  * Esta classe permite que o fulfillFuturesStack concretize futuros que estão em posições da
  * stack do Java normalmente inacessíveis com os truques normais de swap. A ideia é guardar
  * valores que estão no topo da stack verdadeira na FakeStack, sendo retirados do topo da
  * stack verdadeira.
  *
  * Como "optimização", depois dos valores serem removidos dos locals, é escrito null nas posições
  * ocupadas, e quando a stack é criada, tenta reutilizar posições no final da lista dos locals
  * que já estejam a null.
  **/
class FakeLocalsStack {
	private int _nextPosition;
	private final UtilList<Object> _elements = new UtilArrayList<Object>();
	protected final UtilList<Object> _stack;
	private final MethodVisitor mv;

	public FakeLocalsStack(UtilList<Object> locals, UtilList<Object> stack, MethodVisitor mv) {
		_stack = stack;

		int pos = locals.size();
		for (; pos > 0; pos--) {
			if (!locals.get(pos-1).equals(Opcodes.NULL)) break;
		}

		_nextPosition = pos;
		this.mv = mv;
	}

	/** Transferir valor do topo da stack verdadeira para a FakeStack **/
	// Guarda valor na _nextPosition, actualiza _nextPosition em getNumSlots() e
	// faz push no _elements, devolve getNumSlots()
	public int push() {
		// Determinar tipo no topo da Stack
		Object realStackTop = _stack.removeLast();
		if (realStackTop.equals(TOP)) realStackTop = _stack.removeLast();

		int slotsChanged = 0;

		// Transferir valor da stack para os locals
		if (realStackTop.equals(NULL)) {
			// Não guardamos null nos locals
			mv.visitInsn(POP);
			slotsChanged = 1;
		} else {
			Type t = localToType(realStackTop);
			mv.visitVarInsn(t.getStoreInsn(), _nextPosition);
			_nextPosition += t.getNumberSlots();
			slotsChanged = t.getNumberSlots();
		}

		// Fazer tracking do estado da FakeStack
		_elements.push(realStackTop);

		return slotsChanged;
	}

	/** Transferir valor do topo da FakeStack para a stack verdadeira **/
	// Faz pop no _elements, passa valor para a stack, reduz _nextPosition em
	// getNumSlots(), e faz set de getNumSlots() a null
	public int pop() {
		Object fakeStackTop = _elements.pop();

		_stack.add(fakeStackTop); // Repor tipo na representação da stack original

		if (fakeStackTop.equals(NULL)) {
			mv.visitInsn(ACONST_NULL);
			return 1;
		} else {
			Type t = localToType(fakeStackTop);
			_nextPosition -= t.getNumberSlots();
			mv.visitVarInsn(t.getLoadInsn(), _nextPosition);

			// Limpar FakeStack (locals)
			for (int i = 0; i < t.getNumberSlots(); i++) {
				mv.visitInsn(ACONST_NULL);
				mv.visitVarInsn(ASTORE, _nextPosition + i);
			}

			if (t.getNumberSlots() > 1) _stack.add(TOP);
			return t.getNumberSlots();
		}
	}

	public int size() {
		return _elements.size();
	}

	private static Type localToType(Object o) {
		if (o.equals(INTEGER)) return Type.PRIM_INT;
		if (o.equals(FLOAT)) return Type.PRIM_FLOAT;
		if (o.equals(LONG)) return Type.PRIM_LONG;
		if (o.equals(DOUBLE)) return Type.PRIM_DOUBLE;
		if (o instanceof String) return Type.fromAsm((String) o);
		throw new AssertionError();
	}

	public UtilList<Object> stackState() {
		return new UtilArrayList<Object>(_stack);
	}
}

/** Versão de FakeLocalsStack que conta Futures com doubles e longs como ocupando dois slots.
  * Poderia ter feito a alteração directamente na FakeLocalsStack, mas assim consigo mantê-la
  * independente do que o JaSPEx está a fazer.
  **/
class FakeFutureLocalsStack extends FakeLocalsStack {
	FakeFutureLocalsStack(UtilList<Object> locals, UtilList<Object> stack, MethodVisitor mv) {
		super(locals, stack, mv);
	}

	@Override
	public int push() {
		Object top = _stack.last();
		int places = super.push();
		if (isFuture(top)) return extractReturnType(top).getNumberSlots();
		return places;
	}

	@Override
	public int pop() {
		int places = super.pop();
		Object top = _stack.last();
		if (isFuture(top)) return extractReturnType(top).getNumberSlots();
		return places;
	}
}

/** MethodVisitor que concretiza os Futures inseridos pelo InsertContinuationSpeculationMethodVisitor
  * imediatamente antes de eles serem necessários (e não logo que são colocados na stack vindos do
  * spawnSpeculation).
  **/

// FIXME: Ideia DelayGetFutureMethodVisitor2: Atrasar ainda mais coisas como IINC ou I2L (etc),
//	recolhendo-os para serem executados o mais tarde possível, apenas quando o valor tiver mesmo
//	que ser consumido (para executar um método ou guardar num slot), em vez de fazer concretização
//	demasiado cedo porque algum bytecode simples foi colocado no sitio "errado".
public class DelayGetFutureMethodVisitor extends MethodVisitor {

	@SuppressWarnings("unused")
	private static final Logger Log = LoggerFactory.getLogger(DelayGetFutureMethodVisitor.class);

	private AnalyzerAdapter _analyzerAdapter;
	private final boolean _active;

	public DelayGetFutureMethodVisitor(int access, String name, String desc,
		String signature, String[] exceptions, ClassVisitor cv, InfoClass currentClass) {
		super(Opcodes.ASM4, new AnalyzerAdapter(currentClass.type().asmName(),
				access, name, desc, cv.visitMethod(access, name, desc, signature, exceptions)));

		// FIXME: Especulação em constructores disabled por agora (first draft)
		if (name.endsWith("$speculative") /*||
			(name.equals("<init>") &&
			CommonTypes.SPECULATIVECTORMARKER.equals(m.argumentTypes().peekLast()))*/) {
			_active = true;
		} else {
			_active = false;
		}

		_analyzerAdapter = (AnalyzerAdapter) mv;
	}

	private UtilList<Object> currentLocals() {
		return _analyzerAdapter.locals == null ? null : new UtilArrayList<Object>(_analyzerAdapter.locals);
	}

	private UtilList<Object> currentStack() {
		return _analyzerAdapter.stack == null ? null : new UtilArrayList<Object>(_analyzerAdapter.stack);
	}

	/** Array com o número de slots da stack que é consumido durante a execução do opcode.
	  * (Apenas para opcodes em que estamos interessados em fazer a concretização)
	  **/
	private static final int[][] opcodeSlots = {
		/* 0 */	{ },
		/* 1 */	{ INEG, FNEG, I2F, I2B, I2C, I2S, I2L, I2D, F2I, F2L, F2D, IRETURN, FRETURN,
			ARETURN, ARRAYLENGTH, ATHROW, MONITORENTER, MONITOREXIT, IFEQ, IFNE, IFLT,
			IFGE, IFGT, IFLE, IFNULL, IFNONNULL },
		/* 2 */	{ IALOAD, FALOAD, AALOAD, BALOAD, CALOAD, SALOAD, LALOAD, DALOAD,
			IADD, FADD, ISUB, FSUB, IMUL, FMUL, IDIV, FDIV,
			IREM, FREM, ISHL, ISHR, IUSHR, IAND, IOR, IXOR, FCMPL, FCMPG, LNEG, DNEG,
			L2I, L2F, L2D, D2I, D2L, D2F, LRETURN, DRETURN, IF_ICMPEQ, IF_ICMPNE,
			IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE },
		/* 3 */ { IASTORE, FASTORE, AASTORE, BASTORE, CASTORE, SASTORE, LSHL, LSHR, LUSHR },
		/* 4 */ { LADD, DADD, LSUB, DSUB, LMUL, DMUL, LDIV, DDIV, LREM, DREM, LAND, LOR, LXOR,
			LCMP, DCMPL, DCMPG,  LASTORE, DASTORE }
	};

	private void checkOpcode(int opcode) {
		for (int slots = 1; slots < opcodeSlots.length; slots++) {
			for (int j = 0; j < opcodeSlots[slots].length; j++) {
				if (opcodeSlots[slots][j] == opcode) {
					fulfillFuturesStack(slots);
					return;
				}
			}
		}
	}

	@Override
	public void visitInsn(int opcode) {
		if (!_active) { mv.visitInsn(opcode); return; }

		// Bytecodes que não operam sobre um futuro que esteja na stack, ou que operando
		// não precisam que este seja concretizado (ex: POP):
		// NOP, ACONST_NULL, ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4,
		// ICONST_5, LCONST_0, LCONST_1, FCONST_0, FCONST_1, FCONST_2, DCONST_0, DCONST_1,
		// POP, POP2*, DUP, DUP_X1, DUP_X2, DUP2*, DUP2_X1*, DUP2_X2*, SWAP, RETURN
		// * - Futuro não tem que ser concretizado, mas tem que se ter muito cuidado
		// porque um future que representa um long/double só ocupa 1 slot na stack

		// Bytecodes que precisam que um futuro seja concretizado:
		// IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD, IASTORE, LASTORE,
		// FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE,
		// IADD, LADD, FADD, DADD, ISUB, LSUB, FSUB, DSUB, IMUL, LMUL, FMUL, DMUL, IDIV,
		// LDIV, FDIV, DDIV, IREM, LREM, FREM, DREM, INEG, LNEG, FNEG, DNEG, ISHL, LSHL,
		// ISHR, LSHR, IUSHR, LUSHR, IAND, LAND, IOR, LOR, IXOR, LXOR, I2L, I2F, I2D, L2I,
		// L2F, L2D, F2I, F2L, F2D, D2I, D2L, D2F, I2B, I2C, I2S, LCMP, FCMPL, FCMPG,
		// DCMPL, DCMPG, IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, ARRAYLENGTH, ATHROW,
		// MONITORENTER, MONITOREXIT

		// O array opcodeSlots contém informação do número de slots que precisamos de verificar,
		// para opcodes que estão na lista de opcodes que necessitam concretização
		checkOpcode(opcode);

		// A semântica destes opcodes muda se existir um Future a substituir um long/double,
		// e temos que corrigir a instrucção nesse caso
		if ((opcode == DUP2 || opcode == DUP2_X1 || opcode == DUP2_X2 || opcode == POP2)
			&& (currentStack() != null)) {
			Object stackTop = currentStack().last();
			if (isFuture(stackTop) && (extractReturnType(stackTop).getNumberSlots() == 2)) {
				// Temos de fazer substituição de bytecodes
				switch (opcode) {
					case DUP2:
						opcode = DUP;
						break;
					case DUP2_X1:
						opcode = DUP_X1;
						break;
					case DUP2_X2:
						opcode = DUP_X2;
						break;
					case POP2:
						opcode = POP;
						break;
				}
			}
		}
		// Mesmo problema do caso acima, mas o Futuro não está no topo da stack
		if ((opcode == DUP_X2) && (currentStack() != null)) {
			Object belowTop = currentStack().get(currentStack().size()-2);
			if (isFuture(belowTop) && (extractReturnType(belowTop).getNumberSlots() == 2)) {
				opcode = DUP_X1;
			}
		}

		mv.visitInsn(opcode);
	}

	@Override
	public void visitIntInsn(int opcode, int operand) {
		if (!_active) { mv.visitIntInsn(opcode, operand); return; }

		if (opcode == NEWARRAY) {
			fulfillFuturesStack(1);
		}
		mv.visitIntInsn(opcode, operand);
	}

	@Override
	public void visitTypeInsn(int opcode, String type) {
		if (!_active) { mv.visitTypeInsn(opcode, type); return; }

		if (opcode == ANEWARRAY || opcode == CHECKCAST || opcode == INSTANCEOF) {
			fulfillFuturesStack(1);
		}
		mv.visitTypeInsn(opcode, type);
	}

	// Usado pelo visitMethodInsn para manter estado entre chamadas, quando encontra um
	// CommonTypes.MARKER_BEFOREINLINEDSTORE
	private boolean sawInlinedStoreFuture;

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
		if (!_active || currentStack() == null) {
			mv.visitMethodInsn(opcode, owner, name, desc);
			return;
		}

		InvokedMethod m = new InvokedMethod(opcode, Type.fromAsm(owner), name, desc);

		// Detectar se isto é uma escrita num field (versão inlined)
		// (Passo 1:)
		if (owner.equals(CommonTypes.MARKER_BEFOREINLINEDSTORE)) {
			// Detectar se queremos escrever um Futuro no field
			if (isFuture(currentStack().last())) {
				// Queremos escrever um future num objecto, mas a referência
				// para esse objecto pode também ser um future, e portanto
				// podemos ter que o concretizar, mas sem concretizar o Future
				// que vai ser escrito.
				// Para isso, passamos uma representação falsa da stack ao
				// fullfillFuturesStack, para que faça a concretização apenas do
				// Future que queremos.
				if (name.equals("normalStoreDummy")) { // Apenas para normal stores, não static
					UtilList<Object> stack = currentStack();
					stack.set(stack.size()-1, NULL);
					fulfillFuturesStack(2, stack);
				}
				assert (!sawInlinedStoreFuture);
				sawInlinedStoreFuture = true;
				return;
			}
		}
		// (Passo 2:)
		if (owner.equals(TRANSACTION.asmName()) && name.startsWith("store") && sawInlinedStoreFuture) {
			sawInlinedStoreFuture = false;
			mv.visitMethodInsn(opcode, owner, name.replace("store", "storeFuture"),
				"(" + (desc.contains(CommonTypes.STATICFIELDBASE.bytecodeName()) ?
					FUTURE.bytecodeName() + CommonTypes.STATICFIELDBASE.bytecodeName() :
					Type.OBJECT.bytecodeName() + FUTURE.bytecodeName())
				+ "J)V");
			return;
		}

		// Detectar se isto é uma escrita num array (já transactificado)
		if (owner.equals(TRANSACTION.asmName()) && name.startsWith("arrayStore")) {
			// Detectar se queremos escrever um Futuro no array
			if (isFuture(currentStack().last())) {
				// Semelhante ao caso anterior, temos três coisas na stack que podem ser
				// Futuros: o array alvo, a posição, e o valor a ser escrito
				// Apenas o valor a ser escrito pode ser um futuro; os outros dois têm
				// que ser concretizados obrigatoriamente (mas será raro o caso onde
				// serão futuros)
				// Para isso, passamos uma representação falsa da stack ao
				// fullfillFuturesStack, para que faça a concretização apenas dos
				// Futures que queremos.
				UtilList<Object> stack = currentStack();
				stack.set(stack.size()-1, NULL);
				fulfillFuturesStack(3, stack);

				// Substituir por chamada a storeFutureWhateverArray
				mv.visitMethodInsn(opcode, owner, name.replace("Store", "StoreFuture"),
					"(" + m.argumentTypes().first().bytecodeName() + "I" +
						FUTURE.bytecodeName() + ")V");
				return;
			}
		}

		// Obter número de stack slots que vão ser consumidos por esta operação
		int stackSlots = m.isStatic() ? 0 : 1;
		for (Type t : m.argumentTypes()) stackSlots += t.getNumberSlots();

		complexFullfillFuturesStack(stackSlots);

		mv.visitMethodInsn(opcode, owner, name, desc);
	}

	@Override
	public void visitJumpInsn(int opcode, Label label) {
		if (!_active) { mv.visitJumpInsn(opcode, label); return; }

		checkOpcode(opcode);
		mv.visitJumpInsn(opcode, label);
	}

	@Override
	public void visitIincInsn(int var, int increment) {
		if (!_active) { mv.visitIincInsn(var, increment); return; }

		// Caso especial: o IINC incrementa directamente uma var local sem ter que fazer
		// todo o processo de a ler para a stack, incrementar, e fazer store de novo.
		// Quando a variável é um future em vez do valor que se estava à espera, injectamos
		// código equivalente ao IINC que concretize também o futuro.
		if ((currentLocals() != null) && isFuture(currentLocals().get(var))) {
			mv.visitLdcInsn(increment);
			mv.visitVarInsn(ALOAD, var);

			// Concretizar future, versão reduzida do fullfillFuturesStack
			String slotType = (String) currentLocals().get(var);
			// Isto é um tipo nativo de certeza, pode é não ser um int (pode ser qualquer coisa
			// que nos locals se possa guardar como um int: boolean, char, byte ou short)
			Type returnType = extractReturnType(slotType);
			mv.visitMethodInsn(INVOKEINTERFACE, slotType /* Future, mas é preciso manter o extra info */,
				"get", "()" + Type.OBJECT.bytecodeName());
			mv.visitTypeInsn(CHECKCAST, returnType.toObject().asmName());
			mv.visitMethodInsn(INVOKEVIRTUAL,
				returnType.toObject().asmName(), returnType.primitiveTypeName() + "Value",
				"()" + returnType.bytecodeName());

			mv.visitInsn(IADD);
			mv.visitVarInsn(ISTORE, var);
		} else {
			mv.visitIincInsn(var, increment);
		}
	}

	@Override
	public void visitTableSwitchInsn(int min, int max, Label dflt, Label ... labels) {
		if (!_active) { mv.visitTableSwitchInsn(min, max, dflt, labels); return; }

		fulfillFuturesStack(1);
		mv.visitTableSwitchInsn(min, max, dflt, labels);
	}

	@Override
	public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
		if (!_active) { mv.visitLookupSwitchInsn(dflt, keys, labels); return; }

		fulfillFuturesStack(1);
		mv.visitLookupSwitchInsn(dflt, keys, labels);
	}

	@Override
	public void visitMultiANewArrayInsn(String desc, int dims) {
		if (!_active) { mv.visitMultiANewArrayInsn(desc, dims); return; }

		// Segundo a documentação da JVM, mesmo que o desc indique um array maior, apenas
		// são consumidos "dims" posições na stack

		complexFullfillFuturesStack(dims);
		mv.visitMultiANewArrayInsn(desc, dims);
	}

	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		if (!_active) { mv.visitFieldInsn(opcode, owner, name, desc); return; }

		if (opcode == GETFIELD) {
			// Acesso transaccional inlined a field
			fulfillFuturesStack(1);
		}
		mv.visitFieldInsn(opcode, owner, name, desc);
	}

	/** Opcodes usados para loads em variáveis locais **/
	static final List<Integer> localLoadOpcodes =
		Arrays.asList( ILOAD, LLOAD, FLOAD, DLOAD, ALOAD );

	/** Opcodes usados para stores em variáveis locais **/
	static final List<Integer> localStoreOpcodes =
		Arrays.asList( ISTORE, LSTORE, FSTORE, DSTORE, ASTORE );

	/** Opcodes usados para return **/
	static final List<Integer> returnOpcodes =
		Arrays.asList( IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN );

	@Override
	public void visitVarInsn(int opcode, int var) {
		if (!_active) { mv.visitVarInsn(opcode, var); return; }

		if (opcode == RET) throw new Error("FIXME");

		// Para podemos guardar Futures em variáveis locais, em vez de sermos obrigados a
		// concretizá-los antes de os guardar, trocamos *STORES/*LOADS de tipos que foram
		// "substituidos" por Futures por ASTORE/ALOAD.

		if (localLoadOpcodes.contains(opcode) && (currentLocals() != null) &&
			isFuture(currentLocals().get(var))) {
			//Log.debug("Replaced with ALOAD");
			mv.visitVarInsn(ALOAD, var);
		} else if (localStoreOpcodes.contains(opcode) && (currentStack() != null) &&
			isFuture(currentStack().last())) {
			//Log.debug("Replaced with ASTORE " + var);
			mv.visitVarInsn(ASTORE, var);
		} else {
			//Log.debug("Not changed");
			mv.visitVarInsn(opcode, var);
		}
	}

	@Override
	// As alterações para especulação podem alterar o maxStack (conversão de IINC, por exemplo)
	public void visitMaxs(int maxStack, int maxLocals) {
		mv.visitMaxs(maxStack + 2, maxLocals);
	}

	/** Método que verifica se o número de slots especificado da stack (máx. 4) contém um ou mais
	  * Futures: se sim, emite código para os concretizar.
	  *
	  * Nota: O número de slots é o final, depois de concretização. Ou seja, se estiverem dois futures
	  * 	  com tipo Double na stack, na realidade estão a ocupar 2 slots, mas slots = 4, ou seja,
	  *	  o seu tamanho-alvo, depois de concretização.
	  **/
	private void fulfillFuturesStack(int slots) {
		UtilList<Object> stack = currentStack();
		if (stack == null) return;
		fulfillFuturesStack(slots, stack);
	}

	private void fulfillFuturesStack(int slots, UtilList<Object> frameStack) {
		if (slots > 4) throw new AssertionError();

		UtilList<Object> stack = frameStack.reversed();
		//Log.debug("Checkstack: " + stackToString(stack.reversed()));

		for (int pos = 0; pos < slots; pos++) {
			Object slot = stack.get(pos);
			if (isFuture(slot)) {
				Type returnType = extractReturnType(slot);

				// Colocar future a concretizar no topo da stack
				switch (pos) {
					case 0:
						break; // Nada a fazer
					case 1:
						mv.visitInsn(SWAP);
						break;
					case 2:
						Util.swap(mv, 2, FUTURE.getNumberSlots());
						break;
					case 3:
						if (stack.get(1).equals(TOP)) {
							// Caso muito especial. Estado da stack é
							// [Futuro, Double/Long, Algo size 1>
							// logo fazer o swap normal iria partir o double/long,
							// o que não funciona. Neste caso fazemos mais um
							// swap para transformar no EQUIVALENTE ao caso
							// [Futuro, Also size 1, Algo size 1, Algo size 1>
							// e depois temos que desfazer esta transformação
							// no final
							Util.swap(mv, 1, 2);
						}
						Util.swap(mv, 2, 2);
						mv.visitInsn(SWAP);
						break;
					default:
						throw new AssertionError();
				}

				// Concretizar future
				mv.visitMethodInsn(INVOKEINTERFACE, (String) slot /* Future, mas é preciso manter o extra info */,
					"get", "()" + Type.OBJECT.bytecodeName());

				if (returnType.isPrimitive()) {
					mv.visitTypeInsn(CHECKCAST, returnType.toObject().asmName());
					mv.visitMethodInsn(INVOKEVIRTUAL,
						returnType.toObject().asmName(),
						returnType.primitiveTypeName() + "Value",
						"()" + returnType.bytecodeName());
				} else {
					mv.visitTypeInsn(CHECKCAST, returnType.asmName());
				}

				// Repor stack como deve ser
				switch (pos) {
					case 0:
						break; // Nada a fazer
					case 1:
						// Topo da stack pode agora conter objecto com 1 ou 2 slots
						// Portanto temos que fazer swap a contar com isso
						Util.swap(mv, returnType.getNumberSlots(), 1);
						break;
					case 2:
						Util.swap(mv, returnType.getNumberSlots(), 2);
						break;
					case 3:
						mv.visitInsn(SWAP);
						Util.swap(mv, 2, 2);
						if (stack.get(1).equals(TOP)) {
							// Voltar a desfazer a transformação do caso especial
							// Ver comentário no case 3 do switch anterior
							Util.swap(mv, 2, 1);
						}
						break;
					default:
						throw new AssertionError();
				}

				if (returnType.getNumberSlots() == 2) {
					// Actualizar estado/tamanho da stack
					stack.set(pos, TOP);
					stack.add(pos+1, LONG);
				}
			}
		}
	}

	/** Método que complementa o fullfillFuturesStack, mas para um número arbitrário de slots,
	  * usando a FakeLocalsStack.
	  **/
	private void complexFullfillFuturesStack(int slots) {
		int pos = 0;
		int deepestFuture = 0;
		// Encontrar o futuro mais fundo na stack (calculando a sua posição na stack original,
		// como se não existissem futuros)
		for (Object slot : currentStack().reversed()) {
			assert (pos <= slots);
			if (pos == slots) break;
			pos++;
			if (isFuture(slot)) {
				// Compensar caso seja um futuro com um double/long
				pos += extractReturnType(slot).getNumberSlots()-1;
				deepestFuture = pos;
			}
		}

		FakeLocalsStack fakeStack = new FakeFutureLocalsStack(currentLocals(), currentStack(), mv);
		// Transferir valores para a FakeLocalsStack
		while (deepestFuture > 4) {
			deepestFuture -= fakeStack.push();
		}

		// Futuro já pode ser concretizado com o fulfill normal
		fulfillFuturesStack(deepestFuture, fakeStack.stackState());

		// Retirar valores da FakeLocalsStack, e concretizar os restantes
		while (fakeStack.size() > 0) {
			fulfillFuturesStack(fakeStack.pop(), fakeStack.stackState());
		}
	}

	@SuppressWarnings("unused")
	private static String stackToString(List<Object> stack) {
		if (stack == null) return "null";
		StringBuffer b = new StringBuffer();

		b.append("[");
		for (Object elem : stack) {
			String desc = "";
			if (elem == TOP) { desc = "TOP"; }
			else if (elem == INTEGER) { desc = "int"; }
			else if (elem == FLOAT) { desc = "float"; }
			else if (elem == LONG) { desc = "long"; }
			else if (elem == DOUBLE) { desc = "double"; }
			else if (elem == NULL) { desc = "null"; }
			else if (elem == UNINITIALIZED_THIS) { desc = "UNINIT_THIS"; }
			else if (elem instanceof String) { desc = Type.fromAsm((String)elem).commonName(); }
			else { desc = elem.toString(); }
			b.append(desc + ", ");
		}
		if (b.length() > 1) {
			b.replace(b.length() - 2, b.length(), "");
		}
		b.append(">");

		return b.toString();
	}

	public static boolean isFuture(Object type) {
		if (type.toString().startsWith("jaspex/HACK/")) {
			// Quando as frames são criadas pelo FixFutureMultipleControlFlows, em alguns
			// casos são incluidos os marcadores jaspex/HACK/, mas estes *só* ocorrem quando
			// o valor não é suposto ser usado (por exemplo um MUV que fica nos locals quando
			// chegamos a um RETURN). Se estamos a tentar fazer isFuture de algum destes hacks,
			// provavelmente algo está errado.
			throw new AssertionError("Unexpected type in frame");
		}
		return type.toString().startsWith(FUTURE.asmName() + "$");
	}

	/** Obtem o tipo de retorno a partir de um future em AsmName **/
	public static Type extractReturnType(Object future) {
		return FutureMetadata.fromAsm(future.toString()).returnType();
	}

}
