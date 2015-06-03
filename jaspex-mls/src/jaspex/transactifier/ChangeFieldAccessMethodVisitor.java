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

import java.util.*;

import jaspex.ClassFilter;
import jaspex.Options;
import jaspex.speculation.CommonTypes;
import jaspex.speculation.newspec.SpeculationSkiplist;
import jaspex.stm.ExternalAccessHelper;

import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.objectweb.asm.Opcodes.*;

import asmlib.*;
import asmlib.Type;

public class ChangeFieldAccessMethodVisitor extends MethodVisitor {

	private static final Logger Log = LoggerFactory.getLogger(ChangeFieldAccessMethodVisitor.class);

	private final InfoClass _currentClass;
	private final boolean _active;

	public ChangeFieldAccessMethodVisitor(int access, String name, String desc, String signature,
		String[] exceptions, ClassVisitor cv, InfoClass currentClass, Boolean JDKClass) {
		super(Opcodes.ASM4, cv.visitMethod(access, name, desc, signature, exceptions));
		_currentClass = currentClass;
		// FIXME: Crappy workaround. <clinit>, mesmo dentro de uma especulação, faz sempre escrita normal,
		// com a espectativa de que não depende de nada que seja estado especulativo.
		// A alternativa antiga era fazer as escritas especulativas, e rezar para que não fosse feito o
		// abort da transacção que acidentalmente tinha feito o trigger do <clinit>. Nenhuma das duas
		// opções é solução, mas talvez esta seja mais "prática".
		_active = !(name.equals("<clinit>") /*&& JDKClass*/);
	}

	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		if (!_active) { mv.visitFieldInsn(opcode, owner, name, desc); return; }

		Type ownerClass = Type.fromAsm(owner);
		// Classe que contém o StaticFieldBase e os offsets
		Type offsetAccessClass = ownerClass;
		Type sfbAccessClass = ownerClass;

		Type fieldType = Type.fromBytecode(desc);
		Type simpleType = fieldType.isPrimitive() ? fieldType : Type.OBJECT;
		String simpleTypeName = jaspex.transactifier.ChangeArrayAccessMethodVisitor.getName(simpleType);

		if (!ClassFilter.isTransactifiable(ownerClass)) {
			offsetAccessClass = ExternalAccessHelper.typeToExternalAccess(ownerClass);
			sfbAccessClass = offsetAccessClass;
		}

		// Determinar se field é final
		InfoField targetField = ownerClass.equals(_currentClass.type()) ?
			_currentClass.getField(name, fieldType) : null;
		if (targetField == null) targetField = getField(ownerClass, name, fieldType);

		// Não fazer transactificação de fields final
		if (targetField.isFinal()) {
			mv.visitFieldInsn(opcode, owner, name, desc);
			return;
		}

		// Permitir a utilização da SpeculationSkiplist para ignorar a transactificação de fields
		if (SpeculationSkiplist.skipFieldTx(ownerClass, name, fieldType)) {
			logSkip(fieldType, ownerClass, name);
			mv.visitFieldInsn(opcode, owner, name, desc);
			return;
		}

		if (!ClassFilter.isTransactifiable(targetField.infoClass().type())) {
			/*if (!Options.FASTMODE) {
				Log.debug("Field " + ownerClass.commonName() + "." + name + " belongs to "
					+ "non-transactional class " + targetField.infoClass().name().commonName()
					+ ", patching access to offset");
			}*/
			offsetAccessClass = ExternalAccessHelper.typeToExternalAccess(targetField.infoClass().type());
		}

		if (opcode == PUTFIELD) {
			// Avisar o DelayGetFutureMethodVisitor que isto é um store inlined
			mv.visitMethodInsn(INVOKESTATIC, CommonTypes.MARKER_BEFOREINLINEDSTORE, "normalStoreDummy", "()V");

			mv.visitFieldInsn(GETSTATIC, offsetAccessClass.asmName(), "$offset_" + name, "I");
			mv.visitInsn(I2L);
			mv.visitMethodInsn(INVOKESTATIC, CommonTypes.TRANSACTION.asmName(), "store" + simpleTypeName,
					"(" + Type.OBJECT.bytecodeName() + simpleType.bytecodeName() + "J)V");
		} else if (opcode == GETFIELD) {
			mv.visitInsn(DUP);
			mv.visitFieldInsn(GETFIELD, ownerClass.asmName(), name, desc);
			mv.visitFieldInsn(GETSTATIC, offsetAccessClass.asmName(), "$offset_" + name, "I");
			mv.visitMethodInsn(INVOKESTATIC, CommonTypes.TRANSACTION.asmName(), "load" + simpleTypeName,
					"(" + Type.OBJECT.bytecodeName() + simpleType.bytecodeName() + "I)" + simpleType.bytecodeName());
			if (!fieldType.isPrimitive()) mv.visitTypeInsn(CHECKCAST, fieldType.asmName());
		} else if (opcode == PUTSTATIC) {
			// Avisar o DelayGetFutureMethodVisitor que isto é um store inlined
			mv.visitMethodInsn(INVOKESTATIC, CommonTypes.MARKER_BEFOREINLINEDSTORE, "staticStoreDummy", "()V");

			if (Options.STATICWORKAROUND) {
				mv.visitFieldInsn(GETSTATIC, sfbAccessClass.asmName(), "$staticFieldBase",
						CommonTypes.STATICFIELDBASE.bytecodeName());
				mv.visitFieldInsn(GETSTATIC, offsetAccessClass.asmName(), "$offset_" + name, "I");
				mv.visitInsn(I2L);
				mv.visitMethodInsn(INVOKESTATIC, CommonTypes.TRANSACTION.asmName(), "store" + simpleTypeName,
						"(" + simpleType.bytecodeName() + CommonTypes.STATICFIELDBASE.bytecodeName() + "J)V");
			} else {
				mv.visitFieldInsn(GETSTATIC, sfbAccessClass.asmName(), "$staticFieldBase",
						Type.OBJECT.bytecodeName());
				asmlib.Util.swap(mv, Type.OBJECT, fieldType);
				mv.visitFieldInsn(GETSTATIC, offsetAccessClass.asmName(), "$offset_" + name, "I");
				mv.visitInsn(I2L);
				mv.visitMethodInsn(INVOKESTATIC, CommonTypes.TRANSACTION.asmName(), "store" + simpleTypeName,
						"(" + Type.OBJECT.bytecodeName() + simpleType.bytecodeName() + "J)V");
			}
		} else if (opcode == GETSTATIC) {
			mv.visitFieldInsn(GETSTATIC, sfbAccessClass.asmName(), "$staticFieldBase",
					Options.STATICWORKAROUND ?
						CommonTypes.STATICFIELDBASE.bytecodeName() : Type.OBJECT.bytecodeName());
			mv.visitFieldInsn(GETSTATIC, ownerClass.asmName(), name, desc);
			mv.visitFieldInsn(GETSTATIC, offsetAccessClass.asmName(), "$offset_" + name, "I");
			mv.visitMethodInsn(INVOKESTATIC, CommonTypes.TRANSACTION.asmName(), "load" + simpleTypeName,
					"(" + (Options.STATICWORKAROUND ?
						CommonTypes.STATICFIELDBASE.bytecodeName() : Type.OBJECT.bytecodeName()) +
					simpleType.bytecodeName() + "I)" + simpleType.bytecodeName());
			if (!fieldType.isPrimitive()) mv.visitTypeInsn(CHECKCAST, fieldType.asmName());
		} else {
			throw new AssertionError("Unexpected opcode");
		}
	}

	private static InfoField getField(Type ownerClass, String fieldName, Type fieldType) {
		try {
			// Tentar encontrar field apenas na ownerClass
			// Nota: Se a classe já estava cached, este passo já inclui os abaixo
			InfoClass infoClass = getInfoClass(ownerClass);
			InfoField infoField = infoClass.getAllField(fieldName, fieldType);
			if (infoField != null) return infoField;

			// Procurar field nas suas superclasses
			asmlib.Util.populateSuperclasses(infoClass);
			infoField = infoClass.getAllField(fieldName, fieldType);
			if (infoField != null) return infoField;

			// Se não foi encontrado até agora, deve ser um field herdado de uma interface.
			// Fields herdados de interfaces são sempre final.
			if (Options.FASTMODE) {
				return new InfoField(ACC_FINAL, fieldName, fieldType, null, null, null);
			} else {
				// Verificar assumptions, mesmo assim
				InfoClass ic = infoClass;
				while (!ic.type().equals(Type.OBJECT)) {
					asmlib.Util.populateSuperinterfaces(ic);
					ic = ic.superclass();
				}
				infoField = infoClass.getAllField(fieldName, fieldType);
				// Se este assert falhar com uma NPE, ou há um bug na asmlib,
				// ou falta contemplar algum caso
				assert (infoField.isFinal());
				return infoField;
			}
		} catch (java.io.IOException e) { throw new Error(e); }
	}

	private static final Map<Type, InfoClass> infoClassCache = new HashMap<Type, InfoClass>();

	private static InfoClass getInfoClass(Type type) throws java.io.IOException {
		InfoClass infoClass = infoClassCache.get(type);
		if (infoClass == null) {
			infoClass = InfoClass.fromType(type);
			infoClassCache.put(type, infoClass);
		}
		return infoClass;
	}

	private static Set<String> _skippedFields;

	private static void logSkip(Type fieldType, Type ownerClass, String name) {
		if (!Log.isDebugEnabled()) return;

		String field = fieldType.commonName() + " " + ownerClass.commonName() + "." + name;

		if (_skippedFields == null) _skippedFields = new HashSet<String>();
		if (_skippedFields.add(field)) {
			Log.debug("Skipping transactification of accesses to field " + field);
		}
	}

}
