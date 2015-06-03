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

package jaspex.speculation.nsruntime;

import jaspex.speculation.runtime.Callable;
import jaspex.speculation.runtime.CodegenHelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TaskCountProfiler {

	private static final Logger Log = LoggerFactory.getLogger(TaskCountProfiler.class);

	private static final ConcurrentHashMap<Class<? extends Callable>, AtomicInteger> taskCounts =
		new ConcurrentHashMap<Class<? extends Callable>, AtomicInteger>();

	public static void addTask(Callable task) {
		Class<? extends Callable> taskClass = task.getClass();
		AtomicInteger counter = taskCounts.get(taskClass);

		if (counter == null) {
			AtomicInteger newCounter = new AtomicInteger();
			counter = taskCounts.putIfAbsent(taskClass, newCounter);
			if (counter == null) counter = newCounter;
		}

		counter.incrementAndGet();
	}

	public static void printResults() {
		@SuppressWarnings("unchecked")
		Map.Entry<Class<? extends Callable>, AtomicInteger>[] entries =
			taskCounts.entrySet().toArray(new Map.Entry[0]);
		Arrays.sort(entries, new Comparator<Map.Entry<Class<? extends Callable>, AtomicInteger>>() {
			public int compare(Map.Entry<Class<? extends Callable>, AtomicInteger> e1,
						Map.Entry<Class<? extends Callable>, AtomicInteger> e2) {
				return e1.getValue().get() - e2.getValue().get();
			}
		});

		StringBuilder out = new StringBuilder();
		out.append("spawnSpeculation counts:\n");
		for (Map.Entry<Class<? extends Callable>, AtomicInteger> entry : entries) {
			out.append(CodegenHelper.codegenToOriginal(entry.getKey().getName()) + ' ' + entry.getValue() + '\n');
		}
		Log.info(out.toString());
	}

}
