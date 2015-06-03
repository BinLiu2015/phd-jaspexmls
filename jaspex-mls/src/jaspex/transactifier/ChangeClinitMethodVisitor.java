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

import jaspex.ClassFilter;
import jaspex.Options;
import jaspex.speculation.CommonTypes;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.objectweb.asm.Opcodes.*;

import java.io.IOException;
import java.util.*;

import asmlib.*;
import asmlib.Type;

/** MethodVisitor que inicializa os offsets de fields usados pela unsafetrans **/
public class ChangeClinitMethodVisitor extends MethodVisitor {

	private static final Logger Log = LoggerFactory.getLogger(ChangeClinitMethodVisitor.class);

	private final boolean _active;

	private final Type _targetClassName;
	private final Type _currentClassName;
	private final List<InfoField> _fieldList;
	private final boolean _injectedClinit;

	// Construtor usado normalmente
	public ChangeClinitMethodVisitor(int access, String name, String desc, String signature,
			String[] exceptions, ClassVisitor cv, InfoClass currentClass) {
		super(Opcodes.ASM4, cv.visitMethod(access, name, desc, signature, exceptions));
		_active = name.equals("<clinit>");
		_targetClassName = currentClass.type();
		_currentClassName = currentClass.type();
		_fieldList = currentClass.fields();
		_injectedClinit = currentClass.getMethod("<clinit>", "()V") == null;
	}

	// Construtor usado pelo ExternalAccessHelper
	public ChangeClinitMethodVisitor(int access, String name, String desc, String signature,
			String[] exceptions, ClassVisitor cv, Type targetClassName, Type currentClassName,
			ArrayList<InfoField> fieldList) {
		super(Opcodes.ASM4, cv.visitMethod(access, name, desc, signature, exceptions));
		_active = name.equals("<clinit>");
		_targetClassName = targetClassName;
		_currentClassName = currentClassName;
		_fieldList = fieldList;
		_injectedClinit = true;
	}

	@Override
	public void visitCode() {
		if (!_active) return;

		boolean foundStatic = false;
		// Inicializar fields $offset
		for (InfoField f : _fieldList) {
			if (f.isFinal()) continue;
			if (f.isStatic()) foundStatic = true;
			mv.visitLdcInsn(org.objectweb.asm.Type.getObjectType(_targetClassName.asmName()));
			mv.visitLdcInsn(f.name());
			mv.visitMethodInsn(INVOKESTATIC, CommonTypes.TRANSACTION.asmName(), "getFieldOffset",
					"(Ljava/lang/Class;Ljava/lang/String;)I");
			mv.visitFieldInsn(PUTSTATIC, _currentClassName.asmName(), "$offset_" + f.name(), "I");
		}

		// Inicializar $staticFieldBase
		if (foundStatic) {
			mv.visitLdcInsn(org.objectweb.asm.Type.getObjectType(_targetClassName.asmName()));
			if (Options.STATICWORKAROUND) {
				mv.visitMethodInsn(INVOKESTATIC, CommonTypes.TRANSACTION.asmName(), "wrappedStaticFieldBase",
						"(Ljava/lang/Class;)" + CommonTypes.STATICFIELDBASE.bytecodeName());
				mv.visitFieldInsn(PUTSTATIC, _currentClassName.asmName(), "$staticFieldBase", CommonTypes.STATICFIELDBASE.bytecodeName());
			} else {
				mv.visitMethodInsn(INVOKESTATIC, CommonTypes.TRANSACTION.asmName(), "staticFieldBase",
						"(Ljava/lang/Class;)Ljava/lang/Object;");
				mv.visitFieldInsn(PUTSTATIC, _currentClassName.asmName(), "$staticFieldBase", Type.OBJECT.bytecodeName());
			}
		}

		if (!_injectedClinit && !clinitIsSafe(_currentClassName)) {
			// Se já existia um <clinit>, Adicionar chamada ao método que verifica que o <clinit>
			// está a ser executado correctamente
			// (Caso este esteja a ser injectado, as operações que faz são seguras, mesmo que
			// executadas na presença de especulação)
			mv.visitMethodInsn(INVOKESTATIC, CommonTypes.SPECULATIONCONTROL.asmName(), "inClinit", "()V");
		}
	}

	private static final List<Integer> unsafeInsnBytecodes = Arrays.asList(
		IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD,
		IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE,
		ARRAYLENGTH
	);

	// Método que tenta verificar que um <clinit> é seguro.
	// Um <clinit> seguro não lê estado partilhado e não executa métodos que não estejam whitelisted
	private static boolean clinitIsSafe(Type t) {
		try {
			ClassReader cr = new ClassReader(t.commonName());
			ClassNode cNode = new ClassNode();
			cr.accept(cNode, 0);

			for (MethodNode method : cNode.methods) {
				if (!method.name.equals("<clinit>")) continue;
				// Examinar instrucções
				Iterator<AbstractInsnNode> it = method.instructions.iterator();
				while (it.hasNext()) {
					AbstractInsnNode insn = it.next();
					switch (insn.getType()) {
						case AbstractInsnNode.FRAME:
						case AbstractInsnNode.INT_INSN:
						case AbstractInsnNode.JUMP_INSN:
						case AbstractInsnNode.LABEL:
						case AbstractInsnNode.LDC_INSN:
						case AbstractInsnNode.LINE:
						case AbstractInsnNode.LOOKUPSWITCH_INSN:
						case AbstractInsnNode.MULTIANEWARRAY_INSN:
						case AbstractInsnNode.TABLESWITCH_INSN:
						case AbstractInsnNode.TYPE_INSN:
						case AbstractInsnNode.VAR_INSN:
							break;
						case AbstractInsnNode.FIELD_INSN:
							FieldInsnNode fieldInsn = (FieldInsnNode) insn;
							if (fieldInsn.getOpcode() != PUTSTATIC) {
								// GETSTATIC, GETFIELD, PUTFIELD
								return false;
							}
							break;
						case AbstractInsnNode.IINC_INSN:
							return false;
						case AbstractInsnNode.INSN:
							if (unsafeInsnBytecodes.contains(insn.getOpcode())) {
								Log.debug(t.commonName() + ".<clinit>() is unsafe " +
									"because of bytecode " + insn.getOpcode());
								return false;
							}
							break;
						case AbstractInsnNode.METHOD_INSN:
							MethodInsnNode methodInsn = (MethodInsnNode) insn;
							if (!ClassFilter.isMethodWhitelisted(
								Type.fromAsm(methodInsn.owner),
								methodInsn.name,
								methodInsn.desc)) {
								Log.debug(t.commonName() + ".<clinit>() is unsafe " +
									"because it invokes " +
									Type.fromAsm(methodInsn.owner).commonName() +
									"." + methodInsn.name);
								return false;
							}
							break;
						default:
							throw new Error("Unexpected bytecode " + insn);
					}
				}

				//Log.debug(t.commonName() + ".<clinit>() for " + t + " is safe");
				return true;
			}

			return false;
		} catch (IOException e) {
			throw new Error(e);
		}
	}

}
