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

package util;

import java.util.*;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
  * List class extended with some quick string operations
  **/
public class StringList extends UtilArrayList<String> {

	private static final long serialVersionUID = 1L;

	public StringList() {
		super();
	}

	public StringList(List<String> list) {
		super(list);
	}

	public StringList(String ... list) {
		super(Arrays.asList(list));
	}

	public static StringList split(String s, String sep) {
		return split(s, Pattern.compile(Pattern.quote(sep)));
	}

	public static StringList split(String s, Pattern regexp) {
		return new StringList(regexp.split(s, 0));
	}

	public String join(String sep) {
		return StringUtils.join(toArray(), sep);
	}

	@Override
	public String[] toArray() {
		return toArray(new String[0]);
	}

}
