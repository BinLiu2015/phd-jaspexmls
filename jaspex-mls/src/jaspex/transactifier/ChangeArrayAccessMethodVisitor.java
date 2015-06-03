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

package jaspex.transactifier;

import jaspex.speculation.CommonTypes;
import asmlib.InfoClass;
import asmlib.Type;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.AnalyzerAdapter;

import static org.objectweb.asm.Opcodes.*;

/** Transactificação de arrays com nova API backed por Unsafe **/
public class ChangeArrayAccessMethodVisitor extends MethodVisitor {

	//private static final Logger Log = LoggerFactory.getLogger(ChangeArrayAccessMethodVisitor.class);

	private final AnalyzerAdapter _analyzerAdapter;
	private final boolean _active;

	public ChangeArrayAccessMethodVisitor(int access, String name, String desc, String signature,
		String[] exceptions, ClassVisitor cv, InfoClass currentClass, Boolean JDKClass) {
		super(Opcodes.ASM4, new AnalyzerAdapter(currentClass.type().asmName(), access, name, desc,
			cv.visitMethod(access, name, desc, signature, exceptions)));
		_analyzerAdapter = (AnalyzerAdapter) mv;
		_active = !(name.equals("<clinit>") && JDKClass);
	}

	@Override
	public void visitInsn(int opcode) {
		if (!_active) { mv.visitInsn(opcode); return; }

		switch (opcode) {
			// Array loads
			case AALOAD: {
				Type arrayType = typeFromRelPos(-2);
				Type componentType = arrayType.stripArray();
				if (arrayType.arrayDimensions() > 1) {
					componentType = componentType.toArray(arrayType.arrayDimensions()-1);
				}
				arrayLoad(Type.OBJECT);
				mv.visitTypeInsn(CHECKCAST, componentType.asmName());
				break;
			}
			case BALOAD: {
				Type arrayType = typeFromRelPos(-2);
				arrayLoad(arrayType.stripArray());
				break;
			}
			case CALOAD: arrayLoad(Type.PRIM_CHAR); break;
			case DALOAD: arrayLoad(Type.PRIM_DOUBLE); break;
			case FALOAD: arrayLoad(Type.PRIM_FLOAT); break;
			case IALOAD: arrayLoad(Type.PRIM_INT); break;
			case LALOAD: arrayLoad(Type.PRIM_LONG); break;
			case SALOAD: arrayLoad(Type.PRIM_SHORT); break;

			// Array stores
			case AASTORE: arrayStore(Type.OBJECT); break;
			case BASTORE: {
				Type arrayType = typeFromRelPos(-3);
				arrayStore(arrayType.stripArray());
				break;
			}
			case CASTORE: arrayStore(Type.PRIM_CHAR); break;
			case DASTORE: arrayStore(Type.PRIM_DOUBLE); break;
			case FASTORE: arrayStore(Type.PRIM_FLOAT); break;
			case IASTORE: arrayStore(Type.PRIM_INT); break;
			case LASTORE: arrayStore(Type.PRIM_LONG); break;
			case SASTORE: arrayStore(Type.PRIM_SHORT); break;

			default: mv.visitInsn(opcode);
		}
	}

	private void arrayLoad(Type t) {
		mv.visitMethodInsn(INVOKESTATIC, CommonTypes.TRANSACTION.asmName(), "arrayLoad" + getName(t),
			"([" + t.bytecodeName() + "I)" + t.bytecodeName());
	}

	private void arrayStore(Type t) {
		mv.visitMethodInsn(INVOKESTATIC, CommonTypes.TRANSACTION.asmName(), "arrayStore" + getName(t),
			"([" + t.bytecodeName() + "I" + t.bytecodeName() + ")V");
	}

	protected static String getName(Type t) {
		return t.equals(Type.OBJECT) ? "Object" :
			org.apache.commons.lang3.StringUtils.capitalize(t.primitiveTypeName());
	}

	private Type typeFromRelPos(int pos) {
		Object type = _analyzerAdapter.stack.get(_analyzerAdapter.stack.size() + pos);
		if (type.equals(Opcodes.NULL)) {
			// Operação será feita sobre um null (== NPE)
			return Type.PRIM_BYTE;
		}
		return Type.fromBytecode((String) type);
	}

}
