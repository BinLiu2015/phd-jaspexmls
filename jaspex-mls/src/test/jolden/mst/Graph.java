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
 * A class that represents a graph data structure.
 **/
class Graph
{
  /**
   * List of vertices in the graph.
   **/
  private Vertex[] nodes;

  // parameters for the random number generater
  private final static int CONST_m1 = 10000;
  private final static int CONST_b = 31415821;
  private final static int RANGE = 2048;

  /**
   * Create a graph.
   * @param numvert the number of vertices in the graph
   **/
  public Graph(int numvert)
  {
    nodes = new Vertex[numvert];
    Vertex v = null;
    // the original C code creates them in reverse order
    for (int i=numvert-1; i>=0; i--) {
      Vertex tmp = nodes[i] = new Vertex(v, numvert);
      v = tmp;
    }
    addEdges(numvert);
  }

  /**
   * Create a graph.  This is just another method for
   * creating the graph data structure.
   * @param numvert the size of the graph
   **/
  public void createGraph(int numvert)
  {
    nodes = new Vertex[numvert];
    Vertex v = null;
    // the original C code creates them in reverse order
    for (int i=numvert-1; i>=0; i--) {
      Vertex tmp = nodes[i] = new Vertex(v, numvert);
      v = tmp;
    }

    addEdges(numvert);
  }

  /**
   * Return the first node in the graph.
   * @return the first node in the graph.
   **/
  public Vertex firstNode()
  {
    return nodes[0];
  }

  /**
   * Add edges to the graph.  Edges are added to/from every node
   * in the graph and a distance is computed for each of them.
   * @param numvert the number of nodes in the graph
   **/
  private void addEdges(int numvert)
  {
    int count1 = 0;

    for (Vertex tmp = nodes[0]; tmp != null; tmp = tmp._next) {
      Hashtable hash = tmp.neighbors;
      for (int i = 0; i < numvert; i++) {
	if (i != count1) {
	  int dist = computeDist(i, count1, numvert);
	  hash.put(nodes[i], new Integer(dist));
	}
      }
      count1++;
    }
  }

  /**
   * Compute the distance between two edges.  A random number generator
   * is used to compute the distance.
   **/
  private int computeDist(int i, int j, int numvert)
  {
    int less, gt;
    if (i < j) {
      less = i; gt = j;
    } else {
      less = j; gt = i;
    }
    return (random(less * numvert + gt) % RANGE) + 1;
  }

  private static int mult(int p, int q)
  {
    int p1, p0, q1, q0;

    p1=p/CONST_m1; p0=p%CONST_m1;
    q1=q/CONST_m1; q0=q%CONST_m1;
    return (((p0*q1+p1*q0) % CONST_m1)*CONST_m1+p0*q0);
  }

  private static int random(int seed)
  {
    return mult(seed, CONST_b) + 1;
  }

}
