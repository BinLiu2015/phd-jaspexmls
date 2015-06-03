package test.jolden.mst;

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
 * A Java implementation of the <tt>mst</tt> Olden benchmark.  The Olden
 * benchmark computes the minimum spanning tree of a graph using
 * Bentley's algorithm.
 * <p><cite>
 * J. Bentley. "A Parallel Algorithm for Constructing Minimum Spanning Trees"
 * J. of Algorithms, 1:51-59, 1980.
 * </cite>
 * <p>
 * As with the original C version, this one uses its own implementation
 * of hashtable.
 **/
public class MST
{
  /**
   * The number of vertices in the graph.
   **/
  private static int vertices = 0;
  /**
   * Set to true to print the final result.
   **/
  private static boolean printResult = false;
  /**
   * Set to true to print information messages and timing values
   **/
  private static boolean printMsgs = false;

  public static void main(String args[])
  {
    parseCmdLine(args);

    if (printMsgs)
      System.out.println("Making graph of size " + vertices);
    long start0 = System.currentTimeMillis();
    Graph graph = new Graph(vertices);
    long end0 = System.currentTimeMillis();

    if (printMsgs)
      System.out.println("About to compute MST");
    long start1 = System.currentTimeMillis();
    int dist = computeMST(graph, vertices);
    long end1 = System.currentTimeMillis();

    if (printResult || printMsgs)
      System.out.println("MST has cost "+ dist);

    if (printMsgs) {
      System.out.println("Build graph time "+ (end0 - start0)/1000.0);
      System.out.println("Compute time " + (end1 - start1)/1000.0);
      System.out.println("Total time " + (end1 - start0)/1000.0);
    }

    System.out.println("Done!");
  }

  /**
   * The method to compute the minimum spanning tree.
   * @param graph the graph data structure
   * @param numvert the number of vertices in the graph
   * @return the minimum spanning tree cost
   **/
  static int computeMST(Graph graph, int numvert)
  {
    int cost=0;

    // Insert first node
    Vertex inserted = graph.firstNode();
    Vertex tmp = inserted._next;
    MyVertexList = tmp;
    numvert--;

    // Annonunce insertion and find next one
    while (numvert != 0) {
      //System.out.println("numvert= " +numvert);
      BlueReturn br = doAllBlueRule(inserted);
      inserted = br._vert;
      int dist = br._dist;
      numvert--;
      cost += dist;
    }
    return cost;
  }

  private static BlueReturn BlueRule(Vertex inserted, Vertex vlist)
  {
    BlueReturn retval = new BlueReturn();

    if (vlist == null) {
      retval._dist = (999999);
      return retval;
    }

    Vertex prev = vlist;
    retval._vert = vlist;
    retval._dist = (vlist._mindist);
    Hashtable hash = vlist.neighbors;
    Object o = hash.get(inserted);
    if (o != null) {
      int dist = ((Integer)o).intValue();
      if (dist < retval._dist) {
	vlist._mindist = dist;
	retval._dist = (dist);
      }
    } else
      System.out.println("Not found");

   // int count = 0;
    // We are guaranteed that inserted is not first in list
    for (Vertex tmp = vlist._next; tmp != null; prev = tmp, tmp = tmp._next) {
      //count++;
      if (tmp == inserted) {
	Vertex next = tmp._next;
	prev._next = next;
      }	else {
	hash = tmp.neighbors;
	int dist2 = tmp._mindist;
	o = hash.get(inserted);
	if (o != null) {
	  int dist = ((Integer)o).intValue();
	  if (dist < dist2) {
	    tmp._mindist = dist;
	    dist2 = dist;
	  }
	} else
	  System.out.println("Not found");

	if (dist2 < retval._dist) {
	  retval._vert = tmp;
	  retval._dist = (dist2);
	}
      } // else
    } // for
    return retval;
  }

  private static Vertex MyVertexList = null;

  private static BlueReturn doAllBlueRule(Vertex inserted)
  {
    if (inserted == MyVertexList)
      MyVertexList = MyVertexList._next;
    return BlueRule(inserted, MyVertexList);
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

      if (arg.equals("-v")) {
	if (i < args.length) {
	  vertices = new Integer(args[i++]).intValue();
	} else throw new RuntimeException("-v requires the number of vertices");
      } else if (arg.equals("-p")) {
	printResult = true;
      } else if (arg.equals("-m")) {
	printMsgs = true;
      } else if (arg.equals("-h")) {
	usage();
      }
    }
    if (vertices == 0) usage();
  }

  /**
   * The usage routine which describes the program options.
   **/
  private static final void usage()
  {
    System.err.println("usage: java MST -v <levels> [-p] [-m] [-h]");
    System.err.println("    -v the number of vertices in the graph");
    System.err.println("    -p (print the result>)");
    System.err.println("    -m (print informative messages)");
    System.err.println("    -h (this message)");
    System.exit(0);
  }

}

class BlueReturn {
  Vertex _vert;
  int _dist;
}
