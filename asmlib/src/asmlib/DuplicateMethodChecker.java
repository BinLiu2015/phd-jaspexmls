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

import java.util.*;
import java.io.*;

import org.objectweb.asm.*;

public class DuplicateMethodChecker {

	public static void verify(ClassReader cr, PrintWriter pw) {
		InfoClass info = new InfoClass(cr.getClassName(), cr.getSuperName());
		cr.accept(new InfoClassAdapter(info), 0);

		List<InfoMethod> methodList = info.methods();
		NavigableSet<InfoMethod> methodSet = new TreeSet<InfoMethod>(methodList);

		if (methodList.size() == methodSet.size()) return;

		while (!methodSet.isEmpty() && (methodList.size() != methodSet.size())) {
			InfoMethod current = methodSet.pollFirst();
			methodList.remove(current);

			boolean duplicate = false;
			while (methodList.contains(current)) {
				duplicate = true;
				methodList.remove(current);
			}

			if (duplicate) pw.println("DUPLICATE METHOD DETECTED: " + current.name() + current.desc());
		}
	}

}
