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

package jaspex;

import java.util.Iterator;
import java.lang.reflect.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.StringList;

/** Colocando esta classe na hierarquia faz com que o seu bloco static corra antes de qualquer
  * outra coisa, activando assertions para todas as classes carregadas depois desta.
  **/
abstract class EnableAssertions {
	static { ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true); }
}

public class Jaspex extends EnableAssertions {

	static {
		// Testar estado assertions
		boolean assertsEnabled = false;
		assert (assertsEnabled = true);
		if (!assertsEnabled) System.err.println("WARNING: Assertions DISABLED");
	}

	private static final StringList _jaspexOptions = new StringList();
	private static final StringList _optionDescriptions = new StringList();

	/** Argumentos passados ao JaSPEx **/
	public static String _arguments;

	/** Método usado pelo construtor static do SpeculativeClassLoader para obter os valores para as suas
	  * opções.
	  * Usando este método, podemos parametrizar as opções em runtime, e continuam a ser final depois
	  * de inicializadas, permitindo que a VM as optimize.
	  **/
	public static boolean getOption(String optionName, String optionDescription) {
		_optionDescriptions.add(optionName); _optionDescriptions.add(optionDescription);

		return _jaspexOptions.remove("-".concat(optionName));
	}

	/** Extensão ao sistema de opções binárias para permitir strings como argumentos **/
	public static String getStringOption(String optionName, String optionDescription) {
		_optionDescriptions.add(optionName); _optionDescriptions.add(optionDescription);

		optionName = "-" + optionName + "=";
		Iterator<String> it = _jaspexOptions.iterator();
		while (it.hasNext()) {
			String s = it.next();
			if (s.startsWith(optionName)) {
				it.remove();
				s = s.substring(optionName.length());
				return s;
			}
		}
		return null;
	}

	public static void main(String[] argArray) throws Throwable {
		StringList args = new StringList(argArray);
		String allArguments = args.join(" ");

		while (!args.isEmpty() && args.first().startsWith("-")) _jaspexOptions.add(args.pollFirst().toLowerCase());

		// Causar inicialização da classe de opções
		Options.init();
		// Configurar logging
		configureLogging(null);

		if (args.isEmpty() || !_jaspexOptions.isEmpty()) {
			System.err.println("JaSPEx\n\tUsage: java jaspex.Jaspex [-options] Class [args...]");
			System.err.println("\nwhere options include:");

			while (_optionDescriptions.size() > 0) {
				String optionName = "-" + _optionDescriptions.poll();
				String optionDescription = _optionDescriptions.poll();
				System.err.printf("    %-20s %s%n", optionName, optionDescription);
			}

			System.err.println("");
			System.exit(1);
		}

		// Guardar cópia dos argumentos do JaSPEx
		_arguments = allArguments.replace(args.join(" "), "") + args.first();

		try {
			jaspex.speculation.SpeculativeClassLoader.INSTANCE.execute(args);
		} catch (UnsatisfiedLinkError e) {
			if (e.getMessage().equals("sun.misc.Continuation.registerNatives()V")) {
				System.err.println("Error initializing support for Continuations: " +
					"JaSPEx -newspec (default mode) needs a special patched JVM to work. " +
					"Switch to using -oldspec to run JaSPEx on a normal JVM.");
			}
		}
	}

	/** Utilizado pelo JDKTransactifier para inicializar o JaSPEx **/
	public static void initialize(StringList args) {
		while (!args.isEmpty() && args.first().startsWith("-")) _jaspexOptions.add(args.pollFirst());
		// Causar inicialização da classe de opções
		Options.init();
		// Configurar logging
		configureLogging("WARN");
		if (!_jaspexOptions.isEmpty()) throw new IllegalArgumentException("Unknown options received");
	}

	private static Object getLogLevel(String logLevel) throws ClassNotFoundException, IllegalArgumentException,
		SecurityException, IllegalAccessException, NoSuchFieldException {
		Class<?> logLevels = Class.forName("ch.qos.logback.classic.Level");
		return logLevels.getField(logLevel).get(null);
	}

	private static void configureLogging(String logLevel) {
		Exception error = null;
		try {
			// Usar reflection para configurar o logback, para não haver uma dependência explicita no jar deste
			// e no logback como backend do slf4j
			Logger rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
			Method setLevel = rootLogger.getClass().getMethod("setLevel", getLogLevel("ALL").getClass());

			if (logLevel != null) setLevel.invoke(rootLogger, getLogLevel(logLevel));
			else if (Options.SILENT) setLevel.invoke(rootLogger, getLogLevel("ERROR"));
			else if (Options.FASTMODE) setLevel.invoke(rootLogger, getLogLevel("INFO"));
		} catch (NoSuchMethodException e) { error = e; }
		  catch (InvocationTargetException e) { error = e; }
		  catch (IllegalArgumentException e) { error = e; }
		  catch (IllegalAccessException e) { error = e; }
		  catch (SecurityException e) { error = e; }
		  catch (ClassNotFoundException e) { error = e; }
		  catch (NoSuchFieldException e) { error = e; }
		if (error != null) {
			System.err.println("Problem while configuring the logging system. " +
					"You're probably not using logback as an slf4j backend.");
			error.printStackTrace();
		}
	}

}
