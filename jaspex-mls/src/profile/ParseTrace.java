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

package profile;

import java.io.*;
import java.util.*;

import util.StringList;

public class ParseTrace {

	private static final int TRACE_VERSION = 2;
	private static final String DEFAULT_TRACEFILE = "trace-latest.txt";

	private static final HashMap<String,TraceInfo> _traceMap = new HashMap<String,TraceInfo>();
	private static final int CUTOFF = 300;

	private static class TimeGroup {
		private final ArrayList<Integer> _times = new ArrayList<Integer>();

		public int average() { return sum() / size(); }
		public int size() { return _times.size(); }

		public void addTime(int time) {
			if (time != 0) _times.add(time);
		}

		private int min() {
			int val = _times.get(0);
			for (Integer i : _times) {
				if (i < val) val = i;
			}
			return val;
		}

		private int max() {
			int val = _times.get(0);
			for (Integer i : _times) {
				if (i > val) val = i;
			}
			return val;
		}

		private int belowCutoffCount() {
			int count = 0;
			for (Integer i : _times) {
				if (i <= CUTOFF) count++;
			}
			return count;
		}

		private String belowCutoff() {
			int count = belowCutoffCount();
			return String.format("%8.4f (%4s above)",
				((((double) count) / size()) * 100), size() - count);
		}

		private int sum() {
			int sum = 0;
			for (Integer i : _times) sum += i;
			return sum;
		}

		@Override
		public String toString() {
			if (_times.isEmpty()) return "--none--";
			return String.format("executed: %6s avg: %7s min: %7s max: %7s belowCutoff: %s",
				size(), average(), min(), max(), belowCutoff());
		}
	}

	private static class TraceInfo implements Comparable<TraceInfo> {
		public final String _identifier;
		public final TimeGroup _inTx = new TimeGroup();
		public final TimeGroup _outsideTx = new TimeGroup();
		public int _committed;
		public int _aborted;
		public int _noTransaction;

		public TraceInfo(String identifier) {
			_identifier = identifier;
		}

		@Override
		public String toString() {
			String ret = _identifier + "\n\t     inTx: " + _inTx + "\n\toutsideTx: " + _outsideTx + "\n\t";
			ret += String.format("committed: %6s ", _committed);
			if (((double) _aborted) / _committed > 0.3) ret += jaspex.util.ShellColor.startColor("48;5;160");
			ret += String.format("aborted: %6s", _aborted);
			ret += jaspex.util.ShellColor.endColor();
			ret += String.format(" noTransaction: %6s", _noTransaction);
			return ret;
		}

		@Override
		public int hashCode() {
			return _identifier.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			return o == this;
		}

		public int compareTo(TraceInfo other) {
			return _inTx.size() - other._inTx.size();
		}

		public void parseCommitStatus(String s) {
			if (s.equals("null")) _noTransaction++;
			else if (s.equals("true")) _committed++;
			else if (s.equals("false")) _aborted++;
			else throw new Error("Unexpected commit status");
		}

		public String getParentMethod() {
			String parent = _identifier.split("/")[1];
			return parent.substring(parent.indexOf('$') + 1);
		}
	}

	public static int allSum() {
		int sum = 0;
		for (TraceInfo traceInfo : _traceMap.values()) {
			sum += traceInfo._inTx.sum() + traceInfo._outsideTx.sum();
		}
		return sum;
	}

	public static List<TraceInfo> chooseSkip(List<TraceInfo> traces) {
		List<TraceInfo> rejectedTraces = new ArrayList<TraceInfo>();

		for (TraceInfo traceInfo : traces) {
			int size = traceInfo._inTx.size();
			if (size == 0) continue;
			double rate = ((double) traceInfo._inTx.belowCutoffCount()) / size;
			if (rate >= 0.99d) rejectedTraces.add(traceInfo);
		}

		return rejectedTraces;
	}

	public static void main(String[] args) throws IOException {
		String traceFile = DEFAULT_TRACEFILE;

		if (args.length > 0) traceFile = args[0];

		// Fazer parse do trace e popular traceMap
		BufferedReader br = new BufferedReader(new FileReader(traceFile));

		String version = br.readLine();
		if (version == null || !version.startsWith("META trace-version") ||
			!(Integer.parseInt(version.split(" ")[2]) == TRACE_VERSION)) {
			br.close();
			throw new Error("Unexpected version or wrong file type: " + version);
		}

		for (String line = br.readLine(); line != null; line = br.readLine()) {
			if (!br.ready() && !line.equals("META trace-end")) {
				System.err.println("Parsing incomplete trace!");
				break;
			}
			if (line.equals("META trace-end")) break;

			// Permitir META-info no tracefile
			if (line.startsWith("META ")) {
				System.out.println(line.substring(5));
				continue;
			}

			StringList sl = util.StringList.split(line, "\t");
			Integer inTx = Integer.parseInt(sl.poll());
			Integer outsideTx = Integer.parseInt(sl.poll());
			String commitStatus = sl.poll();
			String identifier = sl.poll();

			TraceInfo traceInfo = _traceMap.get(identifier);
			if (traceInfo == null) {
				traceInfo = new TraceInfo(identifier);
				_traceMap.put(identifier, traceInfo);
			}

			traceInfo._inTx.addTime(inTx);
			traceInfo._outsideTx.addTime(outsideTx);
			traceInfo.parseCommitStatus(commitStatus);
		}

		br.close();

		System.out.println("Results (in μs), (Task Source [spawn location] / Parent Task [parent spawned method])");

		// Apresentar resultados
		List<TraceInfo> sortedTraces = new ArrayList<TraceInfo>(_traceMap.values());
		Collections.sort(sortedTraces);

		for (TraceInfo traceInfo : sortedTraces) {
			System.out.println(traceInfo);
		}

		int allsum = allSum();
		String prettyAllsum = String.format("%dm%d.%03ds", (allsum/1000000)/60,
				(allsum/1000000)%60, allsum%1000000);
		System.out.println("Allsum: " + prettyAllsum);

		// Gerar skiplist a partir dos resultados
		String skiplistFile = "autoskip-" + traceFile;
		if (traceFile.equals(DEFAULT_TRACEFILE)) {
			skiplistFile = skiplistFile.replace(".txt",
					"-" + Integer.toHexString(new Random().nextInt()) + ".txt");
		}

		if (new File(skiplistFile).exists()) {
			System.out.println("Skiplist already exists (" + skiplistFile + "), skipping generation");
		} else {
			BufferedWriter bw = new BufferedWriter(new FileWriter(skiplistFile), 4194304);
			bw.write("# Skiplist automatically generated from " + traceFile +
					" (" + jaspex.util.HashUtils.hashFile(new File(traceFile)) + ")\n\n");

			List<TraceInfo> rejectedTraces = chooseSkip(sortedTraces);
			for (TraceInfo traceInfo : rejectedTraces) {
				bw.write("# " + traceInfo.toString().replace("\n", "\n#") + '\n');
				bw.write(traceInfo.getParentMethod() + '(' + "\n\n");
			}

			bw.close();

			System.out.println("Wrote " + skiplistFile);
		}
	}

}
