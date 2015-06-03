/***
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.objectweb.asm.tree.analysis;

import java.io.IOException;
import java.util.HashMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

// FIXME: Não esquecer corrigir identação, code style, ctores e alterar o verifier do asm para usar esta classe

/**
 * An extended {@link SimpleVerifier} that does not load classes in the VM while performing
 * verifications.
 *
 * @author Ivo Anjo
 */
public class ExtendedVerifier extends SimpleVerifier {

	private static HashMap<Type, ClassInfo> classInfoMap = new HashMap<Type, ClassInfo>();

	private static class ClassInfo {
		public final Type classType;
		public final Type superclass;
		public final Type[] interfaces;
		public final int access;

		ClassInfo(Type type) {
			classType = type;

			ClassReader cr = null;
			try {
				cr = new ClassReader(type.getClassName());
			} catch (IOException e) {
				throw new RuntimeException("Error loading " + type.getClassName(), e);
			}

			superclass = cr.getSuperName() == null ? null : Type.getObjectType(cr.getSuperName());

			String[] classInterfaces = cr.getInterfaces();
			if (classInterfaces != null) {
				interfaces = new Type[classInterfaces.length];
				for (int i = 0; i < interfaces.length; i++) {
					interfaces[i] = Type.getObjectType(classInterfaces[i]);
				}
			} else {
				interfaces = null;
			}

			access = cr.getAccess();

			classInfoMap.put(classType, this);
		}
	}

	private static ClassInfo getClassInfo(Type type) {
		ClassInfo info = classInfoMap.get(type);
		return info != null ? info : new ClassInfo(type);
	}

	@Override
	protected Class<?> getClass(Type t) {
		throw new Error("This method should not be called");
	}

	@Override
	public void setClassLoader(ClassLoader loader) {
		throw new Error("This method should not be called");
	}

	@Override
	public BasicValue merge(final BasicValue v, final BasicValue w) {
		throw new Error("FIXME");
	}

	@Override
	protected boolean isInterface(final Type t) {
		return (t != null) && (t.getSort() == Type.OBJECT) &&
			((getClassInfo(t).access & Opcodes.ACC_INTERFACE) != 0);
	}

	@Override
	protected Type getSuperClass(Type t) {
		// FIXME: Verificar que isto está bem
		if (t.getClassName().equals(Object.class.getCanonicalName())) return null;
		return getClassInfo(t).superclass;
	}

	@Override
	protected boolean isAssignableFrom(final Type t, final Type u) {
		throw new Error("FIXME");
	}

}
