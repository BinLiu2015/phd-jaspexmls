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

import java.util.*;

import asmlib.*;
import asmlib.Type;

import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.objectweb.asm.Opcodes.*;

/** Classe que gere a substituição de métodos estáticos, permitindo adicionar ou mudar o comportamento
  * de métodos da JDK (como o System.exit).
  **/
public class MethodReplacerMethodVisitor extends MethodVisitor {

	@SuppressWarnings("unused")
	private static final Logger Log = LoggerFactory.getLogger(MethodReplacerMethodVisitor.class);

	public static final List<MethodReplacement> REPLACEMENTS = Arrays.asList(
		new MethodReplacement(     System.class, "exit"),
		new MethodReplacement(     System.class, "arraycopy"),
		new MethodReplacement(      Class.class, "forName",
				"(Ljava/lang/String;ZLjava/lang/ClassLoader;)", null, null),
		new MethodReplacement(ClassLoader.class, "getSystemClassLoader"),
		new MethodReplacement(     Thread.class, "currentThread"),
		new MethodReplacement(     String.class, "valueOf", "(Ljava/lang/Object;)", null, null),
		new MethodReplacement(Collections.class, "synchronizedCollection"),
		new MethodReplacement(Collections.class, "synchronizedList"),
		new MethodReplacement(Collections.class, "synchronizedMap"),
		new MethodReplacement(Collections.class, "synchronizedSet"),
		new MethodReplacement(Collections.class, "synchronizedSortedMap"),
		new MethodReplacement(Collections.class, "synchronizedSortedSet"),
		new MethodReplacement(Type.fromCommon("sun.misc.Unsafe"), "getUnsafe"),
		new MethodReplacement(java.lang.management.ManagementFactory.class, "getGarbageCollectorMXBeans",
				null, ProcessedReplacements.class, null)
		);

	public static MethodReplacement getReplacement(Type originalClass, String methodName, String desc) {
		for (MethodReplacement r : REPLACEMENTS) {
			if (r._originalClass.equals(originalClass) && r._originalMethod.equals(methodName)
				&& (r._desc == null || desc.startsWith(r._desc))) {
				return r;
			}
		}
		return null;
	}

	private static class MethodReplacement {
		public final Type _originalClass;
		public final String _originalMethod;
		public final String _desc;
		public final Type _targetClass;
		public final String _targetMethod;

		private MethodReplacement(Class<?> originalClass, String originalMethod) {
			this(originalClass, originalMethod, null, null, null);
		}

		private MethodReplacement(Class<?> originalClass, String originalMethod, String desc, Class<?> targetClass, String targetMethod) {
			this(Type.fromClass(originalClass), originalMethod, desc, targetClass, targetMethod);
		}

		private MethodReplacement(Type originalClass, String originalMethod) {
			this(originalClass, originalMethod, null, null, null);
		}

		private MethodReplacement(Type originalClass, String originalMethod, String desc, Class<?> targetClass, String targetMethod) {
			_originalClass = originalClass;
			_originalMethod = originalMethod;
			_desc = desc;

			if (targetClass == null) targetClass = Replacements.class;
			_targetClass = Type.fromClass(targetClass);

			if (targetMethod == null) {
				targetMethod = originalClass.commonName().replace('.', '_') + '_' + originalMethod;
			}
			_targetMethod = targetMethod;
		}

		@Override
		public String toString() {
			return _originalClass.commonName() + "." + _originalMethod + " --> " +
					_targetClass.commonName() + "." + _targetMethod;
		}
	}

	private final boolean _active;

	public MethodReplacerMethodVisitor(int access, String name, String desc, String signature,
		String[] exceptions, ClassVisitor cv, InfoClass currentClass, Boolean JDKClass) {
		super(Opcodes.ASM4, cv.visitMethod(access, name, desc, signature, exceptions));
		_active = name.endsWith("$transactional")
			|| (name.equals("<clinit>") && !JDKClass) // Não modificar <clinit> quando usado com o JDKTransactifier
			|| (name.equals("<init>") && desc.contains("jaspex/MARKER/Transactional"));
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
		if (_active && opcode == INVOKESTATIC) {
			MethodReplacement rep = getReplacement(Type.fromAsm(owner), name, desc);
			if (rep != null) {
				owner = rep._targetClass.asmName();
				name = rep._targetMethod;
			}
			//Log.debug("Replacing " + rep);
		}
		mv.visitMethodInsn(opcode, owner, name, desc);
	}
}
