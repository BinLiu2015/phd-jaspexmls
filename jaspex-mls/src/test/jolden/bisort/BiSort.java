package test.jolden.bisort;

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
 * A Java implementation of the <tt>bisort</tt> Olden benchmark.  The Olden
 * benchmark implements a Bitonic Sort as described in :
 * <p><cite>
 * G. Bilardi and A. Nicolau, "Adaptive Bitonic Sorting: An optimal parallel
 * algorithm for shared-memory machines." SIAM J. Comput. 18(2):216-228, 1998.
 * </cite>
 * <p>
 * The benchmarks sorts N numbers where N is a power of 2.  If the user provides
 * an input value that is not a power of 2, then we use the nearest power of
 * 2 value that is less than the input value.
 **/
public class BiSort
{
  /**
   * The number of values to sort.
   **/
  private static int size = 0;

  /**
   * Print information messages
   **/
  private static boolean printMsgs = false;
  /**
   * Print the tree after each step
   **/
  private static boolean printResults = false;

  /**
   * The main routine which creates a tree and sorts it a couple of times.
   * @param args the command line arguments
   **/
  public static final void main(String args[])
  {
    parseCmdLine(args);

    if (printMsgs)
      System.out.println("Bisort with " + size + " values");

    long start2 = System.currentTimeMillis();
    Value tree = Value.createTree(size, 12345768);
    long end2 = System.currentTimeMillis();
    int sval = Value.random(245867) % Value.RANGE;

    if (printResults) {
      tree.inOrder();
      System.out.println(sval);
    }

    if (printMsgs)
      System.out.println("BEGINNING BITONIC SORT ALGORITHM HERE");

    long start0 = System.currentTimeMillis();
    sval = tree.bisort(sval, Value.FORWARD);
    long end0 = System.currentTimeMillis();

    if (printResults) {
      tree.inOrder();
      System.out.println(sval);
    }

    long start1 = System.currentTimeMillis();
    sval = tree.bisort(sval, Value.BACKWARD);
    long end1 = System.currentTimeMillis();

    if (printResults) {
      tree.inOrder();
      System.out.println(sval);
    }

    if (printMsgs) {
      System.out.println("Creation time: " + (end2 - start2)/1000.0);
      System.out.println("Time to sort forward = " + (end0 - start0)/1000.0);
      System.out.println("Time to sort backward = " + (end1 - start1)/1000.0);
      System.out.println("Total: " + (end1 - start0)/1000.0);
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

      // check for options that require arguments
      if (arg.equals("-s")) {
	if (i < args.length) {
	  size = new Integer(args[i++]).intValue();
	} else {
	  throw new Error("-l requires the number of levels");
	}
      } else if (arg.equals("-m")) {
	printMsgs = true;
      } else if (arg.equals("-p")) {
	printResults = true;
      } else if (arg.equals("-h")) {
	usage();
      }
    }
    if (size == 0) usage();
  }

  /**
   * The usage routine which describes the program options.
   **/
  private static final void usage()
  {
    System.err.println("usage: java BiSort -s <size> [-p] [-i] [-h]");
    System.err.println("    -s the number of values to sort");
    System.err.println("    -m (print informative messages)");
    System.err.println("    -p (print the binary tree after each step)");
    System.err.println("    -h (print this message)");
    System.exit(0);
  }

}
