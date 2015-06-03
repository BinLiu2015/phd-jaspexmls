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

import jaspex.speculation.newspec.FutureVerifier.FutureValue;
import jaspex.speculation.newspec.FutureVerifier.MergedUninitializedValue;

import java.util.*;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.*;
import static org.objectweb.asm.Opcodes.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.*;

import static jaspex.speculation.newspec.DelayGetFutureMethodVisitor.returnOpcodes;
import static jaspex.speculation.newspec.DelayGetFutureMethodVisitor.localLoadOpcodes;
import static jaspex.speculation.newspec.DelayGetFutureMethodVisitor.localStoreOpcodes;
import static jaspex.speculation.newspec.FixFutureMultipleControlFlows.isFuture;
import static jaspex.speculation.newspec.FixFutureMultipleControlFlows.insnForFrame;
import static jaspex.speculation.newspec.FixFutureMultipleControlFlows.Log;
import static jaspex.speculation.newspec.FixFutureMultipleControlFlows.listGenericCast;

import static jaspex.util.ShellColor.color;

/** Versão extendida da Frame que faz track dos seus possíveis predecessores **/
// Inspirado no exemplo na página 117 do manual do ASM
class FlowFrame extends Frame<BasicValue> {
	private final SortedMap<Integer, FlowFrame> _predecessors = new TreeMap<Integer, FlowFrame>();

	public FlowFrame(int nLocals, int nStack) {
		super(nLocals, nStack);
	}

	public FlowFrame(Frame<? extends BasicValue> src) {
		super(src);
	}

	@Override
	public String toString() {
		return "F@" + String.format("%08x", hashCode()) + " [" + super.toString() + "]";
	}

	// Superclasse não permite fazer setStack, implementação com reflection
	private static final java.lang.reflect.Field _values;

	static {
		try {
	                _values = Frame.class.getDeclaredField("values");
	                _values.setAccessible(true);
                } catch (SecurityException e) { throw new Error(e); }
                  catch (NoSuchFieldException e) { throw new Error(e); }
	}

	public void setStack(int pos, BasicValue value) {
		try {
			Object[] values = (Object[]) _values.get(this);
			values[pos + getLocals()] = value;
		} catch (IllegalArgumentException e) { throw new Error(e); }
		  catch (IllegalAccessException e) { throw new Error(e); }
	}

	public Collection<FlowFrame> predecessors() {
		return _predecessors.values();
	}

	public void addPredecessor(int index, FlowFrame predecessor) {
		_predecessors.put(index, predecessor);
	}
}

/** Analyzer que cria FlowFrames e popula os seus sets de predecessores e sucessores **/
class FlowAnalyzer extends Analyzer<BasicValue> {

	protected final FutureVerifier _interpreter;
	protected InsnList _il;
	private MethodNode _mn;

	public FlowAnalyzer(FutureVerifier interpreter) {
		super(interpreter);
		_interpreter = interpreter;
	}

	@Override
	protected FlowFrame newFrame(int nLocals, int nStack) {
		return new FlowFrame(nLocals, nStack);
	}

	@Override
	public FlowFrame newFrame(Frame<? extends BasicValue> src) {
		return new FlowFrame(src);
	}

	@Override
	protected void newControlFlowEdge(int src, int dst) {
		FlowFrame srcNode = (FlowFrame) getFrames()[src];
		FlowFrame dstNode = (FlowFrame) getFrames()[dst];

		dstNode.addPredecessor(src, srcNode);
	}

	@Override
	protected boolean newControlFlowExceptionEdge(int src, TryCatchBlockNode tcb) {
		// Ignore labels
		if (_il.get(src) instanceof LabelNode) return false;

		// Não aceitar control flow edges que têm prioridade inferior aos anteriores
		// Os handlers estão por ordem de prioridade descrescente na tabela das excepções
		for (TryCatchBlockNode handler : getHandlers(src)) {
			if (handler.equals(tcb)) return true;
			if (isSubTypeOf(tcb.type, handler.type)) return false;
		}

		throw new AssertionError();
	}

	@Override
	public Frame<BasicValue>[] analyze(String owner, MethodNode mn) throws AnalyzerException {
		_mn = mn;
		_il = _mn.instructions;

		try {
			return super.analyze(owner, mn);
		} catch (AnalyzerException e) {
			Throwable t = e;
			while ((t = t.getCause()) != null) {
				if (!(t instanceof AnalyzerException)) {
					StackTraceElement[] stackTrace = t.getStackTrace();
					if (stackTrace.length == 0 || stackTrace[0].getClassName()
						.startsWith("org.objectweb.asm.tree")) {
						break;
					}
					throw new Error(t);
				}
			}
			throw e;
		}
	}

	protected boolean isSubTypeOf(String subType, String type) {
		return _interpreter.isAssignableFrom(
			Type.getObjectType(type == null ? "java/lang/Throwable" : type),
			Type.getObjectType(subType == null ? "java/lang/Throwable" : subType));
	}

	/** Popula frame com os seus predecessores causados por excepções.
	  * Caso alguns dos seus predecessores não tenha frame (normalmente porque o analyzer falhou
	  * antes de a computar), esta é computada.
	  **/
	void populateExceptionPredecessors(FlowFrame frame) {
		List<FlowFrame> frames = listGenericCast(Arrays.asList(getFrames()));
		LabelNode frameLabel = (LabelNode) insnForFrame(_il, frames, frame);

		for (int i = 0; i < frames.size(); i++) {
			List<TryCatchBlockNode> handlers = getHandlers(i);
			if (handlers == null) continue;

			for (TryCatchBlockNode tcb : handlers) {
				if (!(newControlFlowExceptionEdge(i, tcb) &&
					tcb.handler.equals(frameLabel))) continue;

				boolean deadCode = false;

				if (frames.get(i) == null) {
					deadCode = fixNullFrame(frames, i);
				}

				if (!deadCode) {
					frame.addPredecessor(i, frames.get(i));
				}
			}
		}
	}

	/** Tenta computar a frame correspondente à instrucção na posição i com base numa frame existente
	  * anterior.
	  **/
	boolean fixNullFrame(List<FlowFrame> frames, int i) {
		Log.trace("Fixing null frame: " + i);
		AbstractInsnNode previousInsn = null;
		boolean computeFromFrameNode = false;
		boolean deadCode = false;

		int j = 1;
		for (;; j++) {
			previousInsn = _il.get(i-j);
			int opcode = previousInsn.getOpcode();
			FlowFrame previousFrame = frames.get(i-j);
			if (opcode < 0) {
				if (previousInsn instanceof FrameNode && previousFrame != null) {
					break;
				}
				if (previousInsn instanceof LabelNode) {
					// FIXME: Aqui devia-se analisar os
					// predecessores da frame, para tentar
					// obter uma frame correcta vinda deles.
				}
				continue;
			}
			if (opcode == NOP && previousFrame == null) {
				// Continuar busca se encontramos um NOP sem
				// frame computada
				continue;
			}
			// Encontrámos o fim do bloco anterior
			if (opcode == GOTO || opcode == ATHROW || returnOpcodes.contains(opcode)) {
				if (_il.get(i) instanceof FrameNode) {
					// No inicio de cada bloco deve estar um FrameNode
					// Se aqui chegamos, então quer dizer não encontramos nenhuma
					// frame que pudessemos usar para computar a actual, e vamos
					// em vez disso usar a informação do FrameNode para criar uma
					// (mesmo que possivelmente incorrecta)
					computeFromFrameNode = true;
				} else {
					// Encontrámos dead code.
					// Em casos como o do NewSpecExample20, a seguinte estrutura
					// pode ocorrer:
					// GOTO L8
					// NOP
					// L9
					// FRAME FULL [I I J] []
					// ou seja, o NOP que foi adicionado antes da label é dead code
					// e poderemos estar a tentar analizar uma range que contém
					// este padrão.
					deadCode = true;
				}
				break;
			}

			if (previousFrame == null) {
				// Vamos ter que calcular a frame da instrucção actual antes de
				// podermos prosseguir
				Log.trace("Recursing fixNullFrame");
				fixNullFrame(frames, i-j);
			}

			break;
		}

		try {
			if (deadCode) return true;

			if (computeFromFrameNode) {
				frames.set(i, frameFromFrameNode((FrameNode) _il.get(i)));
			} else {
				frames.set(i, computeNextFrame(frames.get(i-j), previousInsn));
			}

			//FixFutureMultipleControlFlows.printCode(_mn, frames, frame.predecessors(), frame);

			return false;
		} catch (AnalyzerException e) {
			throw new Error(e);
		}
	}

	FlowFrame computeNextFrame(FlowFrame previousFrame, AbstractInsnNode insn) throws AnalyzerException {
		if (previousFrame == null) throw new NullPointerException("Null previousFrame");

		FlowFrame newFrame = newFrame(previousFrame);

		// Verificar que instrucção produz uma frame
		if (insn.getOpcode() == ATHROW || returnOpcodes.contains(insn.getOpcode())) {
			throw new AssertionError();
		}

		if (!(insn instanceof FrameNode || insn instanceof LabelNode || insn instanceof LineNumberNode)) {
			try {
				newFrame.execute(insn, _interpreter);
			} catch (IndexOutOfBoundsException e) {
				throw new AnalyzerException(insn, "Error inside computeNextFrame", e);
			} catch (NullPointerException e) {
				throw new AnalyzerException(insn, "Error inside computeNextFrame", e);
			}
		}

		return newFrame;
	}

	/** Computa FlowFrame a partir de um FrameNode **/
	FlowFrame frameFromFrameNode(FrameNode frameNode) {
		if (frameNode.type != Opcodes.F_FULL && frameNode.type != Opcodes.F_NEW) {
			throw new AssertionError();
		}

		FlowFrame f = new FlowFrame(_mn.maxLocals, _mn.maxStack);

		List<BasicValue> locals = convertFromFrame(frameNode.local, true);
		List<BasicValue> stack = convertFromFrame(frameNode.stack, false);

		for (int i = 0; i < locals.size(); i++) f.setLocal(i, locals.get(i));

		// Inicializar os locals restantes
		for (int i = locals.size(); i < _mn.maxLocals; i++) {
			f.setLocal(i, BasicValue.UNINITIALIZED_VALUE);
		}

		for (int i = 0; i < stack.size(); i++) f.push(stack.get(i));

		return f;
	}

	/** Converte lista de tipos no formato do visitFrame em BasicValues **/
	private UtilList<BasicValue> convertFromFrame(List<Object> values, boolean locals) {
		UtilList<BasicValue> outList = new UtilArrayList<BasicValue>();

		for (Object o : values) {
			if (o instanceof Integer) {
				Integer i = (Integer) o;
				     if (i.equals(Opcodes.TOP))     { outList.add(BasicValue.UNINITIALIZED_VALUE); }
				else if (i.equals(Opcodes.INTEGER)) { outList.add(BasicValue.INT_VALUE); }
				else if (i.equals(Opcodes.FLOAT))   { outList.add(BasicValue.FLOAT_VALUE); }
				else if (i.equals(Opcodes.LONG))    { outList.add(BasicValue.LONG_VALUE);
								      if (locals) outList.add(BasicValue.UNINITIALIZED_VALUE); }
				else if (i.equals(Opcodes.DOUBLE))  { outList.add(BasicValue.DOUBLE_VALUE);
								      if (locals) outList.add(BasicValue.UNINITIALIZED_VALUE); }
				else if (i.equals(Opcodes.NULL))    { outList.add(BasicValue.REFERENCE_VALUE); }
				else if (i.equals(Opcodes.UNINITIALIZED_THIS)) { throw new AssertionError("FIXME"); }
				else { throw new AssertionError(); }
			} else if (o instanceof String) {
				String s = (String) o;
				outList.add(_interpreter.newValue(Type.getObjectType(s)));
			} else if (o instanceof Label) {
				throw new AssertionError("FIXME");
			} else {
				throw new AssertionError("FIXME");
			}
		}

		return outList;
	}
}

/** Classe que permite injectar as frames computadas pelo Analyzer no MethodNode **/
class FrameInjector {
	private static Object[] stack(Frame<BasicValue> currentFrame) {
		UtilList<BasicValue> stackValues = new UtilArrayList<BasicValue>();
		for (int i = 0; i < currentFrame.getStackSize(); i++) {
			stackValues.add(currentFrame.getStack(i));
		}
		return convertToFrame(stackValues, false);
	}

	private static Object[] locals(Frame<BasicValue> currentFrame) {
		UtilList<BasicValue> localsValues = new UtilArrayList<BasicValue>();
		for (int i = 0; i < currentFrame.getLocals(); i++) {
			localsValues.add(currentFrame.getLocal(i));
		}
		return convertToFrame(localsValues, true);
	}

	/** Converte lista de BasicValues no formato usado no visitFrame **/
	private static Object[] convertToFrame(UtilList<BasicValue> values, boolean locals) {
		UtilList<Object> outList = new UtilArrayList<Object>();
		int top = 0;

		for (int i = 0; i < values.size(); i++) {
			BasicValue v = values.get(i);

			if (v.equals(BasicValue.UNINITIALIZED_VALUE) && locals) {
				// Os locals são criados logo com o tamanho do MAXLOCALS, mas com
				// UNINITIALIZED_VALUE até serem usados
				// Mesmo assim, podem existir outros locals usados, e temos
				// que introduzir algo na lista para as posições não mudarem

				// Por vezes um UninitializedValue está no lugar de um top
				if (i > 0 &&
					(values.get(i-1).equals(BasicValue.LONG_VALUE) ||
					 values.get(i-1).equals(BasicValue.DOUBLE_VALUE))) {
					top++;
					continue;
				}
				outList.add("jaspex/HACK/UninitializedValue");
				continue;
			}
			if (v instanceof MergedUninitializedValue) {
				// Normalmente não devia ser deixado um MergedUninitializedValue
				// na stack/locals, mas tal pode acontecer quando não faz diferença
				// nenhuma no bloco (por exemplo porque vai fazer return)
				outList.add("jaspex/HACK/MergedUninitializedValue");
				continue;
			}
			if (v.getType() == null || v.equals(BasicValue.RETURNADDRESS_VALUE)) {
				throw new AssertionError("FIXME");
			}
			Type type = v.getType();
			Object convertedType;
			switch (type.getSort()) {
				case Type.BOOLEAN:
				case Type.BYTE:
				case Type.CHAR:
				case Type.SHORT:
				case Type.INT:
					convertedType = Opcodes.INTEGER;
					break;
				case Type.FLOAT:
					convertedType = Opcodes.FLOAT;
					break;
				case Type.LONG:
					convertedType = Opcodes.LONG;
					break;
				case Type.DOUBLE:
					convertedType = Opcodes.DOUBLE;
					break;
				case Type.ARRAY:
				case Type.OBJECT:
					convertedType = type.getInternalName();
					break;
				default:
					throw new AssertionError();
			}
			outList.add(convertedType);
		}

		assert ((outList.size() + top) == values.size());
		return outList.toArray();
	}

	/** Adiciona as frames geradas pelo Analyzer ao MethodNode, para serem usadas
	  * pelo DelayGetFutureMethodVisitor.
	  **/
	public static void injectFrames(MethodNode mn, Frame<BasicValue>[] frames) {
		InsnList insnList = mn.instructions;

		AbstractInsnNode node = insnList.getFirst();
		int pos = 0;
		while (node != null) {
			Frame<BasicValue> currentFrame = frames[pos++];
			if (currentFrame != null && !(node instanceof FrameNode)) {
				Object[] locals = locals(currentFrame);
				Object[] stack = stack(currentFrame);
				insnList.insertBefore(node,
					new FrameNode(F_NEW, locals.length, locals, stack.length, stack));
			}
			node = node.getNext();
		}
	}
}

/** TryCatchBlockNode que verifica que start != end **/
class SafeTryCatchBlockNode extends TryCatchBlockNode {
	public SafeTryCatchBlockNode(LabelNode start, LabelNode end, LabelNode handler, String type) {
		super(start, end, handler, type);
		if (start.equals(end)) throw new AssertionError();
	}
}

/** SimpleVerifier modificado para usar MergedUninitializedValue em vez de BasicValue.UNINITIALIZED_VALUE,
  * e para ignorar quaisquer Futuros que veja durante a interpretação, como se o código de concretização
  * já tivesse sido inserido.
  **/
// Nota: javac recusa-se a compilar isto sem a full path para a class...
class FutureVerifier extends org.objectweb.asm.tree.analysis.SimpleVerifier {

	private static final Comparator<BasicValue> COMPARATOR = new Comparator<BasicValue>() {
		public int compare(BasicValue v1, BasicValue v2) {
			// Importante: Tanto o MergedUninitializedValue como o FutureValue poderiam
			// retornar != 0 para algo que é considerado equals; este teste adicional corrige
			// o problema
			if (v1.equals(v2)) return 0;

			Type t1 = v1.getType();
			Type t2 = v2.getType();
			return t1 == null ? (t2 == null ? 0 : -1) : (t2 == null ? 1 :
			       t1.getDescriptor().compareTo(t2.getDescriptor()));
		}
	};

	/** Substituto do BasicValue.UNINITIALIZED_VALUE, no caso em que este era emitido por
	  * não ser possível a combinação de dois tipos, e que guarda os tipos que não foram
	  * combinados.
	  **/
	static class MergedUninitializedValue extends BasicValue {
		private static final BasicValue[] BASICVALUEARRAY = new BasicValue[0];
		private final BasicValue[] _values;

		public MergedUninitializedValue(BasicValue v1, BasicValue v2) {
			super(null);

			Set<BasicValue> valueSet = new TreeSet<BasicValue>(COMPARATOR);

			if (v1 instanceof MergedUninitializedValue) {
				valueSet.addAll(Arrays.asList(((MergedUninitializedValue) v1)._values));
			} else {
				valueSet.add(v1);
			}
			if (v2 instanceof MergedUninitializedValue) {
				valueSet.addAll(Arrays.asList(((MergedUninitializedValue) v2)._values));
			} else {
				valueSet.add(v2);
			}

			_values = valueSet.toArray(BASICVALUEARRAY);
		}

		@Override
		public String toString() {
			return "{" + Arrays.asList(_values) + "}";
		}

		public BasicValue getFuture() {
			for (BasicValue v : _values) if (isFuture(v)) return v;
			return null;
		}

		@Override
		public boolean isReference() {
			return true;
		}

		@Override
		public boolean equals(Object other) {
			if (other == this) return true;
			return other instanceof MergedUninitializedValue &&
				Arrays.equals(this._values, ((MergedUninitializedValue) other)._values);
		}
	}

	/** Substituto de um BasicValue quando este representa um Future
	  * Utilizado para permitir que o equals ignore a informação extra do FutureMetadata.
	  **/
	static class FutureValue extends BasicValue {
		private final Type _returnType;

		public FutureValue(Type t) {
			super(t);
			_returnType = Type.getType(
					FutureMetadata.fromBytecode(t.getDescriptor()).returnType().bytecodeName());
		}

		@Override
		public boolean equals(Object other) {
			if (other == this) return true;
			return other instanceof FutureValue &&
				_returnType.equals(((FutureValue) other)._returnType);
		}

		@Override
		public int getSize() {
			return (_returnType.getSize() > 1) ? 2 : 1;
		}

		@Override
		public int hashCode() {
			return _returnType.hashCode();
		}

		public Type returnType() {
			return _returnType;
		}
	}

	public FutureVerifier(String currentClass, String currentSuperClass) {
		super(Type.getObjectType(currentClass), Type.getObjectType(currentSuperClass), false);
		setClassLoader(jaspex.speculation.SpeculativeClassLoader.INSTANCE);
	}

	@Override
	/** Implementação do merge que mantém informação dos dois tipos que originaram o merge
	  * num MergedUninitializedValue.
	  **/
	public BasicValue merge(BasicValue v, BasicValue w) {
		BasicValue value = super.merge(v, w);
		if (!v.equals(w)) {
			boolean isFutureV = isFuture(v);
			boolean isFutureW = isFuture(w);
			if ((value.equals(BasicValue.UNINITIALIZED_VALUE) &&
					(isFutureV || isFutureW ||
						v instanceof MergedUninitializedValue ||
						w instanceof MergedUninitializedValue)) ||
			// Nota: REFERENCE_VALUE parece ocorrer quando ASM está a tomar atalhos
			// e não lhe "apetece" ver quais os verdadeiros tipos
				(value.equals(BasicValue.REFERENCE_VALUE) && (isFutureV || isFutureW)) ||
			// ASM considera merge(null, T) = T, mas quando T é um Futuro, não podemos
			// assumir isso, pois por exemplo podem existir dois caminhos: um que coloca
			// null na pilha, e outro um futuro, como exemplificado no NewSpecExample28
				(isFutureV && "null".equals(w.getType().getInternalName())) ||
				(isFutureW && "null".equals(v.getType().getInternalName()))) {
					BasicValue muv = new MergedUninitializedValue(v, w);
					return muv.equals(v) ? v :
						muv.equals(w) ? w :
								muv;
			}
		}
		if (value.equals(BasicValue.REFERENCE_VALUE) && !value.equals(v) && !value.equals(w)) {
			// Ou os dois tipos só tem Object em comum, ou só têm uma interface em comum e
			// o ASM não faz o trabalho de a descobrir
			Type tv = v.getType();
			Type tw = w.getType();
			if (tv != null && tw != null) {
				Type commonInterface = ClassHierarchy.getBestCommonInterface(tv, tw);
				if (commonInterface != null) return newValue(commonInterface);
			}
		}
		return value;
	}

	// Suporte para verificação como se código de concretização de Futuros já estivesse presente

	@Override
	public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
		return super.unaryOperation(insn, checkAndPrepare(insn, value));
	}

	@Override
	public BasicValue binaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2)
		throws AnalyzerException {
		return super.binaryOperation(insn, checkAndPrepare(insn, value1), checkAndPrepare(insn, value2));
	}

	@Override
	public BasicValue ternaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2,
		BasicValue value3) throws AnalyzerException {
		return super.ternaryOperation(insn, checkAndPrepare(insn, value1), checkAndPrepare(insn, value2), checkAndPrepare(insn, value3));
	}

	@Override
	public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
		ArrayList<BasicValue> newValues = new ArrayList<BasicValue>(values.size());
		for (BasicValue v : values) newValues.add(checkAndPrepare(insn, v));
		return super.naryOperation(insn, newValues);
	}

	@Override
	public BasicValue copyOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
		// DelayGetFutureMethodVisitor.visitVarInsn, versão interpetador
		int opcode = insn.getOpcode();

		if (opcode == DUP2 || opcode == DUP2_X1 || opcode == DUP2_X2 || opcode == POP2) {
			if (isFuture(value)) {
				Type t = stripFuture(value).getType();
				if (t.equals(Type.LONG_TYPE) || t.equals(Type.DOUBLE_TYPE)) {
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
					insn = new InsnNode(opcode);
				}
			}
		} else if (localLoadOpcodes.contains(opcode) && isFuture(value)) {
			insn = new VarInsnNode(ALOAD, ((VarInsnNode) insn).var);
		} else if (localStoreOpcodes.contains(opcode) && isFuture(value)) {
			insn = new VarInsnNode(ASTORE, ((VarInsnNode) insn).var);
		}
		return super.copyOperation(insn, value);
	}

	@Override
	public void returnOperation(AbstractInsnNode insn, BasicValue value, BasicValue expected)
			throws AnalyzerException {
		super.returnOperation(insn, checkAndPrepare(insn, value), expected);
	}

	/** CUIDADO: Na maior parte dos casos deve ser usado o checkAndPrepare e não o stripValue directamente! **/
	private BasicValue stripFuture(BasicValue v) {
		return v instanceof FutureValue ? newValue(((FutureValue) v).returnType()) : v;
	}

	/** Testa que BasicValue não é um MergedUninitializedValue e faz strip se for um Future **/
	private BasicValue checkAndPrepare(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
		if (value instanceof MergedUninitializedValue) {
			throw new AnalyzerException(insn, "MergedUninitializedValue during checkAndPrepare", "an object reference", value);
		}
		return stripFuture(value);
	}

	@Override
	public boolean isAssignableFrom(Type t, Type u) {
		return ClassHierarchy.isAssignableFrom(t, u);
	}

	@Override
	protected boolean isInterface(Type t) {
		if (t.getDescriptor().startsWith(FutureMetadata.FUTURE_PREFIX)) return true;
		return ClassHierarchy.isInterface(t);
	}

	@Override
	protected Type getSuperClass(Type t) {
		return ClassHierarchy.getSuperclass(t);
	}

	@Override
	protected boolean isSubTypeOf(BasicValue value, BasicValue expected) {
		if (value instanceof MergedUninitializedValue) {
			// Evitar NPE no SimpleVerifier.isSubTypeOf, isto é equivalente
			return false;
		}
		return super.isSubTypeOf(value, expected);
	}

	@Override
	public BasicValue newValue(Type t) {
		if (t != null && t.getDescriptor().startsWith(FutureMetadata.FUTURE_PREFIX)) {
			return new FutureValue(t);
		}
		return super.newValue(t);
	}
}

/** Implementação de Mapa de Labels especial para ser usado com clones.
  * Quando se faz clone de um Jump/LookupSwitch/TableSwitch, o asm assume que o mapa passado tem
  * mapeamentos para todas as Labels. Esta implementação de um LabelMap permite que apenas as
  * substituições sejam adicionadas ao mapa; labels que não estão no mapa assume-se que são para
  * ficar iguais e portanto o get(key) devolve key se key ∉ LabelMap.
  **/
class LabelMap extends HashMap<LabelNode, LabelNode> {
	private static final long serialVersionUID = 1L;

	@Override
	public LabelNode get(Object key) {
		LabelNode ret = super.get(key);
		if (ret == null) ret = (LabelNode) key;
		return ret;
	}

	public LabelNode getMapping(LabelNode key) {
		LabelNode ret = super.get(key);
		if (ret == null) throw new AssertionError();
		return ret;
	}
}

/** Classe que tenta detectar e resolver o problema de num método, diferentes fluxos de controlo
  * poderem dar origem a que numa variável local esteja ou um Futuro ou um tipo normal, e portanto o código
  * apenas é válido para um dos branches.
  * Ver também wiki, "Notas newspec > Resolução de problema de múltiplos fluxos de controlo"
  **/
public class FixFutureMultipleControlFlows {

	static final Logger Log = LoggerFactory.getLogger(FixFutureMultipleControlFlows.class);

	/** Limite de iterações que tentamos fazer antes de desistir **/
	private static final int MAX_ITERS = 70;
	/** Factor de crescimento máximo do número de instrucções de um método **/
	private static final int MAX_GROWTH = 10;

	public static void computeControlFlowGraph(ClassNode cNode, ClassNode origMethods) {
		List<MethodNode> methods = new UtilArrayList<MethodNode>(cNode.methods);

		for (MethodNode m : methods) {
			if (m.name.endsWith("$speculative")) {
				LabelMap labelMap = new LabelMap();
				int i = 0;
				Throwable failed = null;
				int maxInstructions = m.instructions.size()*MAX_GROWTH;
				boolean success = false;

				try {
					while (computeControlFlowGraph(cNode.name, cNode.superName, m, labelMap, i > 0)
						&& (i++ < MAX_ITERS) && (m.instructions.size() <= maxInstructions));
					success = true;
				} catch (AssertionError e)        { failed = e; }
				  catch (NullPointerException e)  { failed = e; }
				  catch (ClassCircularityError e) { failed = e; }

				if (failed != null) {
					Log.warn("BUG in FixFutureMultipleControlFlows while processing " +
							cNode.name + "." + m.name /*, failed*/);
					if (!jaspex.Options.FASTMODE && failed.getMessage() != null
						&& !failed.getMessage().startsWith("KNOWN BUG")) {
						Log.warn("Exception was", failed);
					} else {
						Log.trace("Exception was", failed);
					}
				}

				if (i >= MAX_ITERS) {
					success = false;
					Log.debug("Unable to stabilize control flows for {}.{}{}",
							cNode.name, m.name, m.desc);
				}

				if (m.instructions.size() > maxInstructions) {
					success = false;
					Log.debug("Reverting method, code grew too much {}.{}{}",
							cNode.name, m.name, m.desc);
				}

				if (!success) {
					// Repor versão do método sem especulação
					MethodNode origMethod = null;
					for (MethodNode om : origMethods.methods) {
						if (om.name.equals(m.name) && om.desc.equals(m.desc)) {
							origMethod = om;
							break;
						}
					}
					if (origMethod == null) throw new AssertionError();

					cNode.methods.remove(m);
					cNode.methods.add(origMethod);
				} else if (i >= 20) {
					Log.debug("Needed {} iters to stabilize {}.{}{}",
						i, cNode.name, m.name, m.desc);
				}
			}
		}
	}

	/** Método que implementa a detecção e correcção do problema dos múltiplos fluxos de controlo.
	  * Retorna true se alterou o método, e false cc.
	  **/
	private static boolean computeControlFlowGraph(String owner, String superOwner, MethodNode mn,
		LabelMap labelMap, boolean ranBefore) {
		//Log.debug("Visiting: {}.{}", owner, mn.name);

		FlowAnalyzer analyzer = new FlowAnalyzer(new FutureVerifier(owner, superOwner));

		try {
			analyzer.analyze(owner, mn);
			//if (ranBefore) printCode(mn, Arrays.asList(analyzer.getFrames()), null, null);
			FrameInjector.injectFrames(mn, analyzer.getFrames());
			return false;
		} catch (AnalyzerException e) { /*e.printStackTrace();*/ }

		// Se chegámos aqui, o Analyzer detectou problemas, e vamos ter que fazer correcções no método

		// Detectar o primeiro LabelNode onde aparece um MergedUninitializedValue
		LabelNode problemLabelNode = null;
		FlowFrame problemFrame = null;
		int problemPosition = -1;
		boolean problemInStack = false;

		List<FlowFrame> frames = listGenericCast(Arrays.asList(analyzer.getFrames()));

		outer: for (int i = 0; i < frames.size(); i++) {
			FlowFrame f = frames.get(i);
			if (f == null) continue;

			int localsStartPos = 0;
			int stackStartPos = 0;
			// Identificar se alguma entrada nos locals/stack da frame deve ser escolhida como
			// problemática
			// Isto é um loop porque algumas das entradas encontradas podem ser backwards edges,
			// e não estamos interessadas nessas
			while (true) {
				for (int j = localsStartPos; j < f.getLocals(); j++) {
					if (f.getLocal(j) instanceof MergedUninitializedValue) {
						problemPosition = j;
						problemInStack = false;
						localsStartPos = j+1;
					}
				}
				for (int j = stackStartPos; j < f.getStackSize() && (problemPosition < 0); j++) {
					if (f.getStack(j) instanceof MergedUninitializedValue) {
						problemPosition = j;
						problemInStack = true;
						stackStartPos = j+1;
					}
				}

				// Já processamos completamente a frame e não encontramos nada, ou só
				// encontramos backwards edges, passar à próxima
				if (problemPosition < 0) continue outer;

				if ((f.predecessors().size() > 0) &&
					(getPredecessorWithFuture(f, problemInStack, problemPosition) == null)) {
					// Isto pode acontecer quando instrucção que encontrámos é o target de
					// um "backwards edge". Nesse caso, não é esta a instrucção que queremos
					// processar, mas queremos uma que tenha como predecessor um Future,
					// não um MergedUninitializedValue
					/*Log.debug("Found result of backwards edge at " +
						(problemInStack ? "stack" : "locals") + " position " + i +
						", continuing");*/
					problemPosition = -1;
					continue;
				}

				if (problemInStack && !(mn.instructions.get(i) instanceof LabelNode) &&
					(mn.instructions.get(i-1) instanceof VarInsnNode)) {
					// Caso especial: Uma instrucção de load colocou um
					// MergedUninitializedValue na stack, e como não estava lá na instrucção
					// anterior o getPredecessorWithFuture não detecta este caso, mas não
					// estamos interessados nesta frame, estamos interessadas na que originou
					// o MUV que estava nos locals
					problemPosition = -1;
					continue;
				}

				// Frame e posição escolhidas são consideradas problemáticas
				break;
			}

			AbstractInsnNode insn = mn.instructions.get(i);
			// First node with problematic frame should be a LabelNode
			if (!(insn instanceof LabelNode)) throw new AssertionError();
			problemLabelNode = (LabelNode) insn;
			problemFrame = f;

			printCode(mn, frames, problemFrame.predecessors(), f);
			Log.trace("First problematic frame is " + f + "\n\t\tPredecessors: " + f.predecessors());
			break;
		}

		if (problemLabelNode == null) {
			Log.warn("Errors found during analysis, bytecode possibly invalid, bailing out");
			throw new AssertionError(); // Causar revert de todas as alterações no método
			//return false;
		}

		// Duplicar código problemático, para depois o alterar com o DelayGetFutureMethodVisitor
		InsnList il = new InsnList();

		// Label que marca o inicio do código a copiar
		LabelNode copiedBlockStartLabel = new LabelNode();

		// Criar mapa para passar ao AbstractInsnNode (ver javadoc ASM)
		//LabelMap labelMap = new LabelMap();
		labelMap.put(problemLabelNode, copiedBlockStartLabel);

		// Adiciona copiedBlockStartLabel à nova il
		il.add(problemLabelNode.clone(labelMap));

		// Usado para manter a última (inclusivé) instrucção do bloco copiado
		AbstractInsnNode lastInsn = null;

		// Simular execução das frames durante a cópia
		// O objectivo disto é resolver problemas como o NewSpecExample17, onde código copiado deve
		// fazer branch para:
		// -- Código copiado numa iteração anterior ("código novo") se a frame continuar a conter um
		// 	futuro, porque o código novo é o que é suposto lidar com a existência do futuro
		// -- Código existente do método ("código antigo") se a frame já não contém um futuro, o que
		// 	significa que a concretização se faz durante o bloco actual
		FlowFrame currentFrame = analyzer.newFrame(problemFrame);
		if (problemInStack) {
			currentFrame.setStack(problemPosition,
				((MergedUninitializedValue) currentFrame.getStack(problemPosition)).getFuture());
		} else {
			currentFrame.setLocal(problemPosition,
				((MergedUninitializedValue) currentFrame.getLocal(problemPosition)).getFuture());
		}

		for (AbstractInsnNode n = problemLabelNode.getNext(); n != null; n = n.getNext()) {
			if (n instanceof LabelNode) {
				LabelNode labelNode = (LabelNode) n;
				if (getNextIgnoreLabelLineNop(labelNode) instanceof FrameNode) {
					// Se label se refere a uma frame, o bloco terminou
					// FIXME: Tem que se saltar sempre para labels novas, se existirem?
					il.add(new JumpInsnNode(GOTO, labelMap.get(labelNode)));
					break;
				} else {
					// Caso contrário, substituimos por uma nova label, para permitir
					// que os LineNumberNodes continuem a existir no novo código.
					labelMap.put(labelNode, new LabelNode());
				}
			}

			// Detectar, no caso de um salto, qual a label que se deve utilizar (ver comentários acima)
			// FIXME: Será que no caso do switch/case algumas das labels têm que apontar para o código
			//	  novo, e outras para o antigo?
			if (n instanceof JumpInsnNode ||
				n instanceof LookupSwitchInsnNode || n instanceof TableSwitchInsnNode) {
				// Se ProblemPosition ainda tem um Futuro, saltar para código novo
				if ((problemInStack && isFuture(currentFrame.getStack(problemPosition))) ||
					(!problemInStack && isFuture(currentFrame.getLocal(problemPosition)))) {
					il.add(n.clone(labelMap));
				} else { // Deixou de ter um Futuro, saltar para código antigo
					il.add(n.clone(new LabelMap()));
				}
			} else {
				il.add(n.clone(labelMap));
			}

			lastInsn = n;

			// Se chegamos ao fim do bloco (GOTO, ATHROW ou *RETURN) também paramos a cópia
			if (n.getOpcode() == GOTO || n.getOpcode() == ATHROW ||
				returnOpcodes.contains(n.getOpcode())) break;

			// Actualizar currentFrame -- simular execução da instrucção actual
			try {
				currentFrame = analyzer.computeNextFrame(currentFrame, n);
			} catch (AnalyzerException e) {
				// Ocorreu um erro, continuamos com a última frame válida
				//Log.debug("WARNING: AnalyzerException during computeNextFrame");
			}
			//Log.debug("CurrentFrame: " + currentFrame + " (isFuture? " +
			//	(isFuture(currentFrame.getLocal(problemPosition)) ? "yes" : "no") + ")");
		}

		LabelNode copiedBlockEndLabel = new LabelNode();
		il.add(copiedBlockEndLabel);

		mn.instructions.add(il);
		il = mn.instructions;

		// Detectar qual dos seus predecessores originou o Futuro que ficou no MergedUninitializedValue

		if (problemFrame.predecessors().isEmpty()) { // ProblemFrame é o inicio de um exception handler
			// Popular predecessors da problemFrame com control flows de exceptions
			analyzer.populateExceptionPredecessors(problemFrame);

			//printCode(mn, frames, problemFrame.predecessors());

			// Adicionar um novo tryCatchBlock com:
			// - Range [Primeira instrucção com Future,
			//		Última instrucção que tem future *E* faz parte da lista de predecessors]
			//	Razão: Lista de predecessors --> handler ainda está activo
			//	       Tem future --> future pode ser substituido mais tarde
			//		       (por exemplo { i = doA(); j = doB(); i = 0 })
			// - Target: Novo bloco copiado -- copiedBlockStartLabel

			AbstractInsnNode startBlockInsn = null;
			AbstractInsnNode endBlockInsn = null;

			for (FlowFrame f : problemFrame.predecessors()) {
				BasicValue v = problemInStack ? f.getStack(problemPosition) : f.getLocal(problemPosition);
				if (isFuture(v)) {
					AbstractInsnNode insn = insnForFrame(il, frames, f);

					if (startBlockInsn == null) {
						startBlockInsn = insn;
					}

					if (endBlockInsn != null) {
						// Detectar se o bloco actual terminou
						if (getTryCatchBlock(mn, startBlockInsn, insn) == null) break;
					}

					endBlockInsn = insn;
				} else if (startBlockInsn != null) {
					break;
				}
			}

			// Provavelmente o problema do NewSpecExample20, ver comentários no ficheiro
			if (startBlockInsn == null || endBlockInsn == null) {
				throw new AssertionError("KNOWN BUG: Probably picked the wrong code to copy");
			}

			//Log.debug("PredecessorInsn [Exception]: First " + startBlockInsn + " Last " + endBlockInsn);

			LabelNode startBlockLabel = labelBefore(il, startBlockInsn);
			LabelNode endBlockLabel = labelAfter(il, endBlockInsn);

			TryCatchBlockNode originalBlock = getTryCatchBlock(mn, startBlockInsn, endBlockInsn);
			assert (originalBlock != null);

			mn.tryCatchBlocks.add(0, new SafeTryCatchBlockNode(
					startBlockLabel, endBlockLabel, copiedBlockStartLabel, originalBlock.type));

			if (originalBlock.start.equals(startBlockLabel) && originalBlock.end.equals(endBlockLabel)) {
				// Novo bloco substitui completamente o antigo
				mn.tryCatchBlocks.remove(originalBlock);
			} else {
				// Como o novo try/catch substitui o antigo, para que o verificador da JVM e do ASM
				// lidem melhor com a coisa (embora segundo os specs não seria necessário), vamos
				// alterar o inicio e/ou o fim do bloco original para deixar de conter as instrucções
				// que estão cobertas pelo novo bloco
				if (originalBlock.start.equals(startBlockLabel)) {
					originalBlock.start = endBlockLabel;
				} else if (originalBlock.end.equals(endBlockLabel)) {
					originalBlock.end = startBlockLabel;
				} else {
					Log.debug("FIXME: Original (old) try catch block should be adjusted");
				}
			}
		} else { // Existem predecessores, problemFrame é um bloco de código normal
			FlowFrame predecessorWithFuture =
				getPredecessorWithFuture(problemFrame, problemInStack, problemPosition);

			if (predecessorWithFuture == null) throw new AssertionError();

			AbstractInsnNode predecessorInsn = insnForFrame(il, frames, predecessorWithFuture);
			//Log.debug("PredecessorInsn: " + predecessorInsn);

			// Detectar como vai ser feito o salto para a nova secção do código

			// Casos possíveis:
			// - Predecessor é instrucção imediatamente antes da labelnode (e não é um salto)
			//	-> Não alteramos, adicionamos goto
			//	-> Se for um salto, podemos ou não ter que alterar, dependendo de ser ou não
			//	   um salto para a labelNode que marca o início da secção problemática
			// - Predecessor é jump / table|lookup switch
			//	-> Temos que alterar
			// Fazendo clone com o labelmap obtemos um nó que tem o salto para a nova label trocado
			// pelo salto para a antiga.
			if (directlyPrecedes(predecessorInsn, problemLabelNode) &&
				!hasLabelAsTarget(predecessorInsn, problemLabelNode)) {
				// Não temos que alterar nó, saltamos directamente para novo bloco
				il.insert(predecessorInsn, new JumpInsnNode(GOTO, copiedBlockStartLabel));
			} else {
				if (!((predecessorInsn instanceof LookupSwitchInsnNode) ||
					(predecessorInsn instanceof TableSwitchInsnNode) ||
					(predecessorInsn instanceof JumpInsnNode))) {
					throw new AssertionError(); // Instrucção tem que ser salto
				}
				// Nó tem que ser alterado
				AbstractInsnNode replacementNode = predecessorInsn.clone(labelMap);
				il.set(predecessorInsn, replacementNode);
				if (lastInsn == predecessorInsn) lastInsn = replacementNode;
			}
		}

		// Corrigir exception handlers do método

		// Como blocos de código são copiados, temos também que copiar os exception handlers,
		// para que os try/catch continuem a funcionar correctamente
		List<AbstractInsnNode> copiedCodeRange = getRange(problemLabelNode, lastInsn);
		List<SafeTryCatchBlockNode> newTryCatchBlocks = new ArrayList<SafeTryCatchBlockNode>();
		for (TryCatchBlockNode tryCatchBlock : mn.tryCatchBlocks) {
			List<AbstractInsnNode> blockRange = getRange(tryCatchBlock.start, tryCatchBlock.end);
			blockRange.retainAll(copiedCodeRange);
			if (blockRange.isEmpty()) continue;
			// Corner case: Supostamente um try/catch block cobre [start, end[, enquanto
			// que o getRange devolve [start, end]
			if (blockRange.size() == 1 && problemLabelNode == tryCatchBlock.end) continue;

			//Log.debug("Exception handler table needs fixup");

			// Determinar a nova label de inicio
			LabelNode newStart;
			if (copiedCodeRange.contains(tryCatchBlock.start)) {
				// loco excepção começa já dentro do copiedCodeRange
				newStart = labelMap.getMapping(tryCatchBlock.start);
			} else {
				// Bloco excepção começa fora do copiedCodeRange
				newStart = copiedBlockStartLabel;
			}

			// Determinar a nova label de fim
			LabelNode newEnd;
			if (copiedCodeRange.contains(tryCatchBlock.end)) {
				// Bloco excepção começa dentro do copiedCodeRange
				newEnd = labelMap.getMapping(tryCatchBlock.end);
			} else {
				// Bloco excepção acaba fora do copiedCodeRange
				newEnd = copiedBlockEndLabel;
			}

			newTryCatchBlocks.add(new SafeTryCatchBlockNode(
				newStart, newEnd, tryCatchBlock.handler, tryCatchBlock.type));
		}
		mn.tryCatchBlocks.addAll(newTryCatchBlocks);

		return true;
	}

	/** Retorna um dos predecessores da FlowFrame que tenha um Futuro na posição indicada **/
	private static FlowFrame getPredecessorWithFuture(FlowFrame problemFrame, boolean problemInStack, int problemPos) {
		for (FlowFrame f : problemFrame.predecessors()) {
			BasicValue v = problemInStack ? f.getStack(problemPos) : f.getLocal(problemPos);
			if (v != null && isFuture(v)) return f;
		}
		return null;
	}

	/** Devolve o 1º TryCatchBlockNode que contém a range de instrucções entre [start, end] **/
	private static TryCatchBlockNode getTryCatchBlock(MethodNode mn, AbstractInsnNode start, AbstractInsnNode end) {
		for (TryCatchBlockNode tryCatchBlock : mn.tryCatchBlocks) {
			List<AbstractInsnNode> blockRange = getRange(tryCatchBlock.start, tryCatchBlock.end);
			if (blockRange.contains(start) && blockRange.contains(end)) return tryCatchBlock;
		}
		return null;
	}

	/** Devolve uma lista de todas as instrucções que estão entre [start, end] **/
	static List<AbstractInsnNode> getRange(AbstractInsnNode start, AbstractInsnNode end) {
		if (start == end) throw new AssertionError();

		List<AbstractInsnNode> insnList = new ArrayList<AbstractInsnNode>();
		for (AbstractInsnNode n = start; n != end; n = n.getNext()) {
			insnList.add(n);
		}
		insnList.add(end);

		return insnList;
	}

	/** Obtém próximo nó que não seja uma Label ou um LineNumber **/
	private static AbstractInsnNode getNextIgnoreLabelLineNop(AbstractInsnNode n) {
		while ((n != null) &&
				(n instanceof LabelNode || n instanceof LineNumberNode ||
					(n instanceof InsnNode && (((InsnNode) n).getOpcode() == NOP)))) {
			n = n.getNext();
		}
		return n;
	}

	/** Método que detecta se o node1 está imediatamente antes do node2, ignorando LabelNodes
	  * e LineNumberNodes que possam aparecer entre eles.
	  * Nota: Diferente do nextIgnoreLabelLine pois node2 pode ser uma Label.
	  **/
	private static boolean directlyPrecedes(AbstractInsnNode node1, AbstractInsnNode node2) {
		AbstractInsnNode n = node1;
		while ((n = n.getNext()) != null) {
			if (n == node2) return true;
			if (!((n instanceof LabelNode) || (n instanceof LineNumberNode))) return false;
		}
		return false;
	}

	/** Método que devolve true se n for um Jump, LookupSwitch ou TableSwitch Node, e entre as
	  * suas labels estiver a especificada.
	  **/
	private static boolean hasLabelAsTarget(AbstractInsnNode n, LabelNode label) {
		if (n instanceof JumpInsnNode) {
			JumpInsnNode jumpNode = (JumpInsnNode) n;
			return jumpNode.label.equals(label);
		} else if (n instanceof LookupSwitchInsnNode) {
			LookupSwitchInsnNode lsNode = (LookupSwitchInsnNode) n;
			return lsNode.labels.contains(label);
		} else if (n instanceof TableSwitchInsnNode) {
			TableSwitchInsnNode lsNode = (TableSwitchInsnNode) n;
			return lsNode.labels.contains(label);
		}
		return false;
	}

	static boolean isFuture(BasicValue v) {
		return v instanceof FutureValue;
	}

	static void printCode(MethodNode mn, List<? extends Frame<?>> frames, Collection<FlowFrame> highlight,
		FlowFrame specialHighlight) {
		if (!Log.isTraceEnabled()) return;

		Textifier textifier = new Textifier();
		TraceMethodVisitor tmv = new TraceMethodVisitor(textifier);
		mn.accept(tmv);

		highlight = highlight != null ? highlight : new ArrayList<FlowFrame>();

		List<String> instructions = listGenericCast(textifier.getText());
		InsnList il = mn.instructions;

		int offset = mn.tryCatchBlocks.size();
		for (int i = 0; i < instructions.size(); i++) {
			int pos = i - offset;
			Frame<?> f = pos >= 0 && pos < frames.size() ? frames.get(pos) : null;
			String insn = pos < il.size() && pos >= 0 ?
					il.get(pos).toString().replace("org.objectweb.asm.tree.", "")
					: null;
			String highlightColor =
				specialHighlight != null && specialHighlight.equals(f) ?
					"45"
					: highlight.contains(f) ? "41" : "32";
			Log.trace(pos + instructions.get(i).replace("\n", "") + " " +
					(f != null ? color(f.toString(), highlightColor) : "") + " (" + insn + ")");
		}
	}

	/** Obtém label antes da instrucção. Se já existir uma, é reutilizada, cc uma nova é criada. **/
	public static LabelNode labelBefore(InsnList il, AbstractInsnNode insn) {
		if (!(insn.getPrevious() instanceof LabelNode)) {
			il.insertBefore(insn, new LabelNode());
		}

		return (LabelNode) insn.getPrevious();
	}

	/** Obtém label depois da instrucção. Se já existir uma, é reutilizada, cc uma nova é criada. **/
	public static LabelNode labelAfter(InsnList il, AbstractInsnNode insn) {
		if (!(insn.getNext() instanceof LabelNode)) {
			il.insert(insn, new LabelNode());
		}

		return (LabelNode) insn.getNext();
	}

	/** Obtém AbstractInsnNode correspondente a uma Frame **/
	public static AbstractInsnNode insnForFrame(InsnList il, List<? extends Frame<?>> frameList, Frame<?> f) {
		if (f != null) return il.get(frameList.indexOf(f));
		throw new AssertionError("Tried to lookup position of null frame");
	}

	/** Converts raw generic types (such as List) or parameterized generic types (List<X>) into other
	  * parametrized generic types (List<Y>), avoiding warnings elsewhere and centralizing the use
	  * of @SupressWarnings on this method.
	  **/
	@SuppressWarnings("unchecked")
	static <E> List<E> listGenericCast(List<?> lst) {
		return (List<E>) lst;
	}

}
