/*
 * asmlib: a toolkit based on ASM for working with java bytecode
 * Copyright (C) 2015 Ivo Anjo <ivo.anjo@ist.utl.pt>
 *
 * This file is part of asmlib.
 *
 * asmlib is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * asmlib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with asmlib.  If not, see <http://www.gnu.org/licenses/>.
 */

package asmlib;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;

import org.objectweb.asm.*;

/**
  * Generic ClassVisitor adapter for MethodVisitors.
  *
  * The idea of this class is to avoid having to write a boilerplate ClassVisitor when all we want is
  * for the MethodVisitor to be included in the chain of Visitors.
  **/
public class GenericMethodVisitorAdapter extends ClassVisitor {

	private final Class<? extends MethodVisitor> _methodVisitorClass;
	private final Object[] _extraInfo;

	public GenericMethodVisitorAdapter(ClassVisitor cv, Class<? extends MethodVisitor> methodVisitorClass, Object ... extraInfo) {
		super(Opcodes.ASM4, cv);
		_methodVisitorClass = methodVisitorClass;
		_extraInfo = extraInfo;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		// Signature for the constructor
		ArrayList<Class<?>> ctorArgTypes = new ArrayList<Class<?>>();
		ctorArgTypes.addAll(Arrays.<Class<?>>asList(
			int.class, String.class, String.class, String.class, String[].class, ClassVisitor.class));
		// Add types from the extra arguments
		for (Object o : _extraInfo) ctorArgTypes.add(o.getClass());

		// Create list of arguments
		ArrayList<Object> ctorArgs = new ArrayList<Object>();
		ctorArgs.addAll(Arrays.asList(access, name, desc, signature, exceptions, cv));
		ctorArgs.addAll(Arrays.asList(_extraInfo));

		Exception inner = null;
		try {
			// Instantiate the MethodVisitor using reflection
			Constructor<? extends MethodVisitor> ctor =
				_methodVisitorClass.getConstructor(ctorArgTypes.toArray(new Class[0]));
			return ctor.newInstance(ctorArgs.toArray());
		} catch (NoSuchMethodException e)  { inner = e; }
		  catch (InstantiationException e) { inner = e; }
		  catch (IllegalAccessException e) { inner = e; }
		  catch (InvocationTargetException e) {
			  Throwable cause = e.getCause();
			  if (cause instanceof RuntimeException) throw (RuntimeException) cause;
			  if (cause instanceof Error) throw (Error) cause;
			  throw new Error(cause);
		}
		throw new Error("Error while using reflection to create a new instance", inner);
	}

}
