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
 * A class that represents a vertex in a graph.  We maintain a linked list
 * representation of the vertices.
 **/
final class Vertex
{
  /**
   * The minimum distance value for the node
   **/
  int    _mindist;
  /**
   * The next vertex in the graph.
   **/
  Vertex _next;
  /**
   * A hashtable containing all the connected vertices.
   **/
  final Hashtable neighbors;

  /**
   * Create a vertex and initialize the fields.
   * @param n the next element
   **/
  Vertex(Vertex n, int numvert)
  {
    _mindist = 9999999;
    _next = n;
    neighbors = new Hashtable(numvert/4);
  }

}
