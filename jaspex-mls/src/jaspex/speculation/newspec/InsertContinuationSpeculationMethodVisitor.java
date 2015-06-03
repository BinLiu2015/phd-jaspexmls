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

import jaspex.ClassFilter;
import jaspex.speculation.*;
import jaspex.speculation.runtime.*;

import java.util.*;

import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asmlib.*;
import asmlib.Type;

import util.*;

public class InsertContinuationSpeculationMethodVisitor extends MethodVisitor {

	private static final Logger Log = LoggerFactory.getLogger(InsertContinuationSpeculationMethodVisitor.class);

	private final boolean _active;

	private int _spawnId; // É atribuido a cada spawn feito um id local, que é usado para
			      // evitar overspeculation (ver também RemoveOverspeculation)
	private final UtilList<Integer> _rejectedSpecIds; // Ids a ignorar
	private boolean _firstPass;

	private final InfoMethod _currentMethod;

	private UtilList<InvokedMethod> _insertedSpeculations;

	public InsertContinuationSpeculationMethodVisitor(int access, String name, String desc,
		String signature, String[] exceptions, ClassVisitor cv, InfoClass currentClass) {
		this(access, name, desc, signature, exceptions, cv, currentClass, new HashMap<InfoMethod, UtilList<Integer>>());
		_firstPass = true;
	}

	public InsertContinuationSpeculationMethodVisitor(int access, String name, String desc,
		String signature, String[] exceptions, ClassVisitor cv, InfoClass currentClass,
		HashMap<InfoMethod, UtilList<Integer>> rejectedSpecIdsMap) {
		super(Opcodes.ASM4, cv.visitMethod(access, name, desc, signature, exceptions));

		// FIXME: Especulação em constructores disabled por agora (first draft)
		if (name.endsWith("$speculative") /*||
			(name.equals("<init>") &&
			CommonTypes.SPECULATIVECTORMARKER.equals(m.argumentTypes().peekLast()))*/) {
			_active = true;
		} else {
			_active = false;
		}

		_currentMethod = currentClass.getMethod(name, desc);
		UtilList<Integer> rejectedSpecIds = rejectedSpecIdsMap.get(_currentMethod);
		_rejectedSpecIds = (rejectedSpecIds != null) ? rejectedSpecIds : new UtilArrayList<Integer>();
		_firstPass = false;
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
		InvokedMethod m = new InvokedMethod(opcode, Type.fromAsm(owner), name, desc);

		if (jaspex.Options.NOINSERTSPECULATION
			|| !_active || !ClassFilter.isTransactifiable(m.owner()) ||
			SpeculationSkiplist.skipMethod(m) || name.equals("<init>") ||
			// Ter cuidado com especulações com INVOKESPECIAL: podem ser chamadas ao super(),
			// o que pode causar recursão infinita quando o Callable tenta fazer a mesma
			// chamada, ou chamadas a métodos privados da própria classe que pode dar alguns
			// problemas, como exemplificado no NewSpecExample55 e 56.
			// Uma alternativa seria adicionar trampolins ou outra forma de referir o método
			// da superclasse directamente (o Luís aparentemente resolve dessa maneira).
			(opcode == INVOKESPECIAL && !m.owner().equals(_currentMethod.infoClass().type())) ||
			!name.contains("$speculative")) {
			mv.visitMethodInsn(opcode, owner, name, desc);
			return;
		}

		int spawnId = _spawnId++;
		if (_rejectedSpecIds.contains(spawnId)) {
			if (name.equals(_currentMethod.name()) && desc.equals(_currentMethod.desc()) &&
				owner.equals(_currentMethod.infoClass().type().asmName())) {
				// Workaround experimental para o problema com overspeculation em métodos
				// recursivos --- assim, alguns dos branches do método deixam de causar novas
				// especulações.
				Log.warn("Applying recursion workaround to " + _currentMethod.fullJavaName());
				name = name.replace("$speculative", "$non_speculative");
			}
			mv.visitMethodInsn(opcode, owner, name, desc);
			return;
		}

		//Log.debug("visitMethodInsn: " + _currentMethod.fullJavaName() + " || " + m.name());

		// Transformar invocações em criações de Callable + chamada a spawnSpeculation
		Type codegenClassType = CodegenHelper.methodToCodegenType(m);

		if (m.isStatic() && m.argumentTypes().isEmpty()) {
			// Neste caso não temos argumentos para passar, usamos um singleton que o
			// CodegenHelper gerou
			mv.visitFieldInsn(GETSTATIC, codegenClassType.asmName(), "INSTANCE",
				codegenClassType.bytecodeName());
		} else {
			// speculation.runtime.codegen.Codegen$ID$class.method.newInstance(args)
			String ctorTypes = m.isStatic() ? "" : m.owner().bytecodeName();
			for (Type type : m.argumentTypes()) ctorTypes += type.bytecodeName();
			mv.visitMethodInsn(INVOKESTATIC, codegenClassType.asmName(), "newInstance",
				"(" + ctorTypes + ")" + codegenClassType.bytecodeName());
		}

		if (!_firstPass && Log.isDebugEnabled()) { // Imprimir esta info apenas no 2º pass
			if (_insertedSpeculations == null) {
				_insertedSpeculations = new UtilArrayList<InvokedMethod>();
			}

			_insertedSpeculations.add(m);
		}

		// O spawnSpeculation pode receber como argumento info extra para debug
		if (jaspex.Options.TXSTATS || jaspex.Options.TXABORTSTATS || jaspex.Options.PROFILE) {
			mv.visitLdcInsn("S" + spawnId + "$" + _currentMethod.infoClass().type() + "." + cleanName(_currentMethod.name()));
		} else {
			mv.visitInsn(ACONST_NULL);
		}

		// Chamar spawnSpeculation
		// Nota: Abusamos aqui um pouco dos nomes dos tipos para passar o verdadeiro tipo de retorno
		//	do future como se fosse um tipo novo. Assim o DelayGetFutureMethod tem informação
		//	para saber qual o tipo esperado da chamada original, mesmo que o Future ande às
		//	voltas pela stack e por variáveis locais.
		//	Depois de o usar, o DelayGetFutureMethod remove o tipo e volta a repor um Future
		//	normal.
		// Nota: Também introduzimos aqui um id local ao método, que vai permitir que o RemoveOverspeculation
		//	consiga determinar a qual spawnSpeculation a concretização de um Future pertence.

		mv.visitMethodInsn(INVOKESTATIC,
			CommonTypes.CONTSPECULATIONCONTROL.asmName(),
			"spawnSpeculation",
			"(" + CommonTypes.CALLABLE.bytecodeName() + Type.STRING.bytecodeName() + ")" +
				new FutureMetadata(m.returnType(), spawnId).bytecodeName());

		if (m.returnType().equals(Type.fromBytecode("V"))) {
			mv.visitInsn(POP);
		}
	}

	@Override
	// As alterações para especulação podem alterar o maxStack (por exemplo num método que chama um ou mais
	// métodos static que não recebem argumentos)
	public void visitMaxs(int maxStack, int maxLocals) {
		mv.visitMaxs(maxStack + 2, maxLocals);
	}

	@Override
	public void visitEnd() {
		if (_insertedSpeculations != null) {
			StringBuilder s = new StringBuilder();
			s.append("Inserted speculation " + _currentMethod.infoClass().type().commonName() +
					"." + cleanName(_currentMethod.name()) + " --> {");
			while (_insertedSpeculations.size() > 0) {
				InvokedMethod m = _insertedSpeculations.removeFirst();
				int count = 1;
				while (_insertedSpeculations.remove(m)) count++;
				if (count > 1) s.append("[" + count + "] ");
				if (!m.owner().equals(_currentMethod.infoClass().type())) {
					s.append(m.owner().commonName() + ".");
				}
				s.append(cleanName(m.name()));
				//s.append(" (" + CodegenHelper.codegenId(m) + ")");
				if (_insertedSpeculations.size() > 0) s.append(", ");
			}
			s.append("}");

			Log.debug(s.toString());
		}

		mv.visitEnd();
	}

	private static String cleanName(String methodName) {
		return FixPrivateMethodAccessMethodVisitor.stripPrivate(
			methodName.substring(0, methodName.indexOf("$speculative")));
	}
}
