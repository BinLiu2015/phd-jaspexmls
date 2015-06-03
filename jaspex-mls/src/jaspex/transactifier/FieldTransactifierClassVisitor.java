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

import jaspex.Options;
import jaspex.speculation.CommonTypes;

import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

import asmlib.Type;
import asmlib.*;

/** ClassVisitor que implementa a nova táctica de transactificação "unsafetrans", usando acesso
  * directo aos fields.
  **/
public class FieldTransactifierClassVisitor extends ClassVisitor {

	private boolean _injectStaticFieldBase = false;
	private boolean _injectClinit = true;
	private boolean _foundFields = false;
	private boolean _propagateOriginal = true;

	public FieldTransactifierClassVisitor(ClassVisitor cv) {
		super(Opcodes.ASM4, cv);
	}

	public FieldTransactifierClassVisitor(ClassVisitor cv, boolean propagateOriginal) {
		this(cv);
		_propagateOriginal = propagateOriginal;
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		InfoField f = new InfoField(access, name, Type.fromBytecode(desc), null, value, null);

		if (!f.isFinal()) {
			_foundFields = true;

			// Criar field public static final long $offset_nomeoriginal,
			// que vai conter o offset do field na classe (usado pelo Unsafe)
			cv.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC,
				"$offset_" + f.name(), "I", null, null);

			// StaticFieldBase só é injectado se encontrarmos algum field static (não-final)
			if (f.isStatic()) _injectStaticFieldBase = true;
		}

		// Propagar field original
		return _propagateOriginal ? cv.visitField(access, name, desc, signature, value) : null;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		if (name.equals("<clinit>")) _injectClinit = false;
		return cv.visitMethod(access, name, desc, signature, exceptions);
	}

	@Override
	public void visitEnd() {
		if (_injectStaticFieldBase) {
			cv.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC, "$staticFieldBase",
				Options.STATICWORKAROUND ?
					CommonTypes.STATICFIELDBASE.bytecodeName() : Type.OBJECT.bytecodeName(),
				null, null);
		}

		// Adicionar <clinit> para classes que não o tinham já
		if (_injectClinit && _foundFields) {
			MethodVisitor mv = cv.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
			mv.visitCode();
			mv.visitInsn(RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}

		cv.visitEnd();
	}

}
