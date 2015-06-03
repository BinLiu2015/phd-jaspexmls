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

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asmlib.InfoClass;
import asmlib.InfoMethod;
import asmlib.Type;

/** ClassVisitor que cria overloads para todos os métodos directamente herdados de superclasses
  * não-transactificáveis.
  **/
public class InjectOverloadsClassVisitor extends ClassVisitor {

	@SuppressWarnings("unused")
	private static final Logger Log = LoggerFactory.getLogger(InjectOverloadsClassVisitor.class);
	private final InfoClass _currentClass;

	public InjectOverloadsClassVisitor(ClassVisitor cv, InfoClass currentClass) {
		super(Opcodes.ASM4, cv);
		_currentClass = currentClass;
	}

	@Override
	public void visitEnd() {
		if (_currentClass.isInterface()) injectInterfaceOverloads();
		else checkOverloads();
		cv.visitEnd();
	}

	// Fazer overload a todos os métodos que são herdados de superclasses não-transactificáveis
	private void checkOverloads() {
		// Se superclasse pode ser transactificada, então não é necessário adicionar mais overloads
		if (_currentClass.superclassType().equals(Type.OBJECT)
			|| ClassFilter.isTransactifiable(_currentClass.superclassType())) {
			return;
		}

		try {
			asmlib.Util.populateSuperclasses(_currentClass);
		} catch (java.io.IOException e) { throw new Error(e); }

		// Métodos classe actual
		Set<InfoMethod> existingMethods =
			new TreeSet<InfoMethod>(new InfoMethod.InfoMethodNameDescOnlyComparator());
		existingMethods.addAll(_currentClass.methods());

		InfoClass infoClass = _currentClass.superclass();
		while (!infoClass.type().equals(Type.OBJECT)) {
			for (InfoMethod m : infoClass.methods()) {
				if (m.isCtor() || m.isStaticCtor()) continue;

				// Se método não existe na classe actual, nem foi já overloaded,
				// injectar um overload
				if (!existingMethods.contains(m)) {
					// Se é um método abstracto, não precisamos de fazer overload,
					// mesmo que depois exista uma implementação concreta numa superclasse
					if (!m.isAbstract()) {
						injectOverload(m);
					}

					existingMethods.add(m);
				}
			}
			infoClass = infoClass.superclass();
		}
	}

	private void injectOverload(InfoMethod method) {
		MethodVisitor mv = cv.visitMethod(method.access() & ~ACC_SYNCHRONIZED & ~ACC_NATIVE,
			method.name() + "$transactional", method.desc(), method.signature(), method.exceptions());
		mv.visitCode();
		// Contador de posições de argumentos
		int pos = 0;
		if (!method.isStatic()) mv.visitVarInsn(ALOAD, pos++);
		for (Type argType : method.argumentTypes()) {
			mv.visitVarInsn(argType.getLoadInsn(), pos);
			pos += argType.getNumberSlots();
		}
		mv.visitMethodInsn(method.isStatic() ? INVOKESTATIC : INVOKESPECIAL,
			method.infoClass().type().asmName(), method.name(), method.desc());
		mv.visitInsn(method.returnType().getReturnInsn());
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	/** Caso especial para interfaces:
	  * interface InterfaceA extends java.lang.Iterable { }
	  * public class NewSpecExample82 {
          *   public static InterfaceA getInterfaceAImpl() { return null; }
          *   public static void main(String[] args) { getInterfaceAImpl().iterator(); }
          * }
          *
          * Neste caso têm que ser injectados as versões $speculative dos métodos de Iterarable, porque a
          * chamada a iterator() será substituida por uma chamada a iterator$speculative(), que deverá estar
          * na interface, já que é uma interface transactificada!
	  **/
	void injectInterfaceOverloads() {
		// Nota: Métodos são colocados num set primeiro para evitar repetições
		Set<InfoMethod> injected =
			new TreeSet<InfoMethod>(new InfoMethod.InfoMethodNameDescOnlyComparator());

		// Para cada superinterface que seja não-transactificável
		for (Type superifaceType : _currentClass.interfaceTypes()) {
			if (ClassFilter.isTransactifiable(superifaceType)) continue;

			// Populamos completamente a sua lista de métodos
			InfoClass superiface = null;
			try {
				superiface = InfoClass.fromType(superifaceType);
				asmlib.Util.populateSuperinterfaces(superiface);
			} catch (IOException e) { throw new Error(e); }

			// E vemos se algum dos seus métodos ou de suas superinterfaces
			// não estão listados na interface actual
			for (InfoMethod m : superiface.allMethods()) {
				if ((_currentClass.getAllMethod(m.name(), m.desc()) == null) && !injected.contains(m)) {
				// Se não está listado, fazemos a sua injecção
					cv.visitMethod(m.access(), m.name() + "$transactional", m.desc(), m.signature(),
						m.exceptions()).visitEnd();
					injected.add(m);
				}
			}
		}
	}

}
