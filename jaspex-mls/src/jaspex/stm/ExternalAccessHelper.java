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

package jaspex.stm;

import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asmlib.*;
import asmlib.Type;
import jaspex.ClassFilter;
import jaspex.Options;

import jaspex.transactifier.ChangeClinitMethodVisitor;
import jaspex.transactifier.FieldTransactifierClassVisitor;

import static jaspex.util.ShellColor.color;

/** Classe usada para gerar classes que contêm o StaticFieldBase e offset dos fields de classes
  * não-transactificáveis.
  *
  * A ideia base é algo semelhante em funcionamento ao jaspex.speculation.runtime.CodegenHelper
  **/
public class ExternalAccessHelper {

	private static final Logger Log = LoggerFactory.getLogger(ExternalAccessHelper.class);

	/** Prefixo usado para classes geradas.
	  * Derivado do nome do ExternalAccessHelper, para permitir refactorizações automáticas.
	  **/
	private static final String EXTERNALACCESS_CLASS_PREFIX =
		ExternalAccessHelper.class.getPackage().getName() + ".externalaccess.";

	public static Type typeToExternalAccess(Type type) {
		assert (!ClassFilter.isTransactifiable(type));
		return Type.fromCommon(EXTERNALACCESS_CLASS_PREFIX + type.commonName());
	}

	public static boolean isExternalAccessClass(Type type) {
		return type.commonName().startsWith(EXTERNALACCESS_CLASS_PREFIX);
	}

	public static byte[] generateClass(Type extAccessType) throws java.io.IOException {
		// Obter tipo original
		Type originalType =
			Type.fromCommon(extAccessType.commonName().replace(EXTERNALACCESS_CLASS_PREFIX, ""));

		Class<?> javaClass = null;
		try {
			 javaClass = Class.forName(originalType.commonName());
		} catch (ClassNotFoundException e) { throw new Error(e); }

		Log.trace(color("[ ExternalAccessHelper ]", "31") + " Generating wrapper for {}", originalType);

		// Criar classe
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		ClassVisitor cv = cw;

		List<InfoField> fieldList = InfoClass.fromType(originalType).fields();

		// Adicionar inicialização de offsets usados pela unsafetrans ao clinit da classe
		// Nota: Visitor tem que estar *depois* do FieldTransactifierClassVisitor
		cv = new GenericMethodVisitorAdapter(cv, ChangeClinitMethodVisitor.class,
							originalType, extAccessType, fieldList);

		// Visitor que cria os campos offset e o staticfieldbase
		cv = new FieldTransactifierClassVisitor(cv, false);

		cv.visit(V1_6, ACC_PUBLIC | ACC_FINAL, extAccessType.asmName(), null, Type.OBJECT.asmName(), null);
		cv.visitSource(color("JaSPEx Generated External Access Class", "31"), null);

		// Visitar campos, para serem apanhados pelos visitors
		Iterator<InfoField> it = fieldList.iterator();
		while (it.hasNext()) {
			InfoField f = it.next();

			// Alguns campos (como o java.lang.System.security) não são visíveis por reflection,
			// e portanto não geramos entradas para eles (hopefully nunca serão necessárias)
			try {
				Transaction.getFieldOffset(javaClass, f.name());
			} catch (SecurityException e)    { it.remove(); continue; }

			cv.visitField(f.access(), f.name(), f.desc().bytecodeName(), null, null);
		}

		cv.visitEnd();

		byte[] newClass = cw.toByteArray();

		if (Options.WRITECLASS) {
			try {
				new java.io.File("output" + java.io.File.separatorChar).mkdir();
				java.io.FileOutputStream fos = new java.io.FileOutputStream("output" +
					java.io.File.separatorChar + extAccessType.commonName() + ".class");
				fos.write(newClass);
				fos.close();
			} catch (java.io.FileNotFoundException e) { throw new Error(e);	}
			  catch (java.io.IOException e) { throw new Error(e); }
		}

		return newClass;
	}

}
