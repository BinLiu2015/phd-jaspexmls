package test.jolden.tsp;

/**
This directory contains Java versions of the Olden benchmarks.

The original Olden benchmarks are a suite of pointer intensive C
programs.  The benchmarks were used by Martin Carlisle and Anne Rogers
for evaluating a system that parallelizes programs with dynamic data
structures.  The original sources are located at
http://www.cs.princeton.edu/~mcc/olden_benchmarks.tar.Z.

Members of the Architecture and Language Implementation Laboratory
(http://ali-www.cs.umass.edu) rewrote the C programs in Java.  Since
the original version of the benchmarks use parallel constructs, we
first made the programs sequential before translating them to into
Java.

To compile the benchmarks, just type "make" or "make compile" which
compiles all of the programs.  You must use the GNU make - other
versions of make may not work.  All the class files are placed into
the individual benchmark subdirectory.

To run the benchmarks, type "make run" to run them all, or cd into a
specific directory to run just a single benchmark.  The Makefile list
the default parameters but, for most of the programs, the defaults can
easily be changed.

If you have any comments, suggestions, etc. about the benchmarks,
please send mail to cahoon@cs.umass.edu.
**/

/**
 * A Java implementation of the <tt>tsp</tt> Olden benchmark, the traveling
 * salesman problem.
 * <p>
 * <cite>
 * R. Karp, "Probabilistic analysis of partitioning algorithms for the
 * traveling-salesman problem in the plane."  Mathematics of Operations Research
 * 2(3):209-224, August 1977
 * </cite>
 **/
public class TSP
{
  /**
   * Number of cities in the problem.
   **/
  private static int cities;
  /**
   * Set to true if the result should be printed
   **/
  private static boolean printResult = false;
  /**
   * Set to true to print informative messages
   **/
  private static boolean printMsgs = false;

  /**
   * The main routine which creates a tree and traverses it.
   * @param args the arguments to the program
   **/
  public static void main(String args[])
  {
    parseCmdLine(args);

    if (printMsgs)
      System.out.println("Building tree of size " + cities);

    long start0 = System.currentTimeMillis();
    Tree  t = Tree.buildTree(cities, false, 0.0, 1.0, 0.0, 1.0);
    long end0 = System.currentTimeMillis();

    long start1 = System.currentTimeMillis();
    t.tsp(150);
    long end1 = System.currentTimeMillis();

    if (printResult) {
      // if the user specifies, print the final result
      t.printVisitOrder();
    }

    if (printMsgs) {
      System.out.println("Tsp build time  " + (end0 - start0)/1000.0);
      System.out.println("Tsp time " + (end1 - start1)/1000.0);
      System.out.println("Tsp total time " + (end1 - start0)/1000.0);
    }
    System.out.println("Done!");
  }

  /**
   * Parse the command line options.
   * @param args the command line options.
   **/
  private static final void parseCmdLine(String args[])
  {
    int i = 0;
    String arg;

    while (i < args.length && args[i].startsWith("-")) {
      arg = args[i++];

      if (arg.equals("-c")) {
	if (i < args.length)
	  cities = new Integer(args[i++]).intValue();
	else throw new Error("-c requires the size of tree");
      } else if (arg.equals("-p")) {
	printResult = true;
      } else if (arg.equals("-m")) {
	printMsgs = true;
      } else if (arg.equals("-h")) {
	usage();
      }
    }
    if (cities == 0) usage();
  }

  /**
   * The usage routine which describes the program options.
   **/
  private static final void usage()
  {
    System.err.println("usage: java TSP -c <num> [-p] [-m] [-h]");
    System.err.println("    -c number of cities (rounds up to the next power of 2 minus 1)");
    System.err.println("    -p (print the final result)");
    System.err.println("    -m (print informative messages)");
    System.err.println("    -h (print this message)");
    System.exit(0);
  }

}

