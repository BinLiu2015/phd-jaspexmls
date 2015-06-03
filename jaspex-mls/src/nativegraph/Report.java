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

package nativegraph;

import util.StringList;
import java.util.*;
import java.io.*;

import asmlib.*;

public abstract class Report {

	private static final Report[] reports = {
		new AllNativeMethodsTableReport(),
		new NativeMethodsTableReport(),
		new GenerateDotReport()
	};

	public abstract String getName();

	public static Report get(String reportType) {
		for (Report rpt : reports) {
			if (rpt.getName().equalsIgnoreCase(reportType)) return rpt;
		}
		return null;
	}

	public abstract void execute(StringList args, NavigableSet<Type> examineSet, Map<Type, InfoClass> doneMap) throws IOException;

}
