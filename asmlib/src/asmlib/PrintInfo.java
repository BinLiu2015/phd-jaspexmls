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

import util.StringList;
import java.io.*;

import org.objectweb.asm.*;

public class PrintInfo {

	public static void main(String[] argsArr) {
		StringList args = new StringList(argsArr);

		if (args.size() < 1) {
			System.err.println("Usage: java PrintInfo <class1> <class2> ... <classn>");
			System.exit(1);
		}

		for (String classStr : args) {
			Type type = Type.fromFilePath(classStr);
			try {
				new PrintInfo(type);
			} catch (IOException e) {
				throw new Error(e);
			}
		}
	}

	private ClassReader cr;

	private InfoClass currentClass;

	public PrintInfo(Type type) throws IOException {
		cr = new ClassReader(type.commonName());

		// Popular informação da classe
		currentClass = new InfoClass(cr.getClassName(), cr.getSuperName());

		cr.accept(new InfoClassAdapter(currentClass), 0);

		InfoClass info = currentClass;
		while (info != null) {
			System.out.println(info);
			info = info.superclass();
		}

		for (InfoField f : currentClass.fields()) {
			System.out.println("\t" + f);
		}
		for (InfoMethod m : currentClass.methods()) {
			System.out.println("\t" + m);
		}
	}

}
