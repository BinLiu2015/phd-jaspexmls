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

package jaspex.util;

// Howto 256 cores na shell:
// Lista de cores
// $ for code in {0..255}; do echo -e -n "\e[48;05;${code}m$code\e[0m "; done; echo ""
// Foreground: 38;5;COR
// Background: 48;5;COR
// Ambos: 38;5;COR1;48;5;COR2

public class ShellColor {

	public static String startColor(String color) {
		if (jaspex.Options.DEBUG) return "";
		return "\033[" + color + "m";
	}

	public static String endColor() {
		if (jaspex.Options.DEBUG) return "";
		return "\033[0m";
	}

	public static String color(String s, String color) {
		if (jaspex.Options.DEBUG) return s;
		return startColor(color) + s + endColor();
	}

	public static void main(String[] args) {
		System.out.println("Color table:");
		for (int i = 0; i <= 255; i++) {
			if (i % 20 == 0) System.out.println();
			System.out.print(color(String.format(" %03d ", i), "48;5;" + i));
		}
		System.out.println();
	}

}
