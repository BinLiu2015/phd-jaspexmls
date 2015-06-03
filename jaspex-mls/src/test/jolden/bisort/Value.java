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
 * A class that represents a value to be sorted by the <tt>BiSort</tt>
 * algorithm.  We represents a values as a node in a binary tree.
 **/
class Value
{
  private int value;
  private Value left;
  private Value right;

  static final boolean FORWARD = false;
  static final boolean BACKWARD = true;

  // These are used by the Olden benchmark random no. generator
  private static final int CONST_m1 = 10000;
  private static final int CONST_b = 31415821;
  static final int RANGE = 100;

  /**
   * Constructor for a node representing a value in the bitonic sort tree.
   * @param v the integer value which is the sort key
   **/
  Value(int value, Value left, Value right)
  {
    this.value = value;
    this.left = left;
    this.right = right;
  }

  /**
   * Create a random tree of value to be sorted using the bitonic sorting algorithm.
   *
   * @param size the number of values to create.
   * @param seed a random number generator seed value
   * @return the root of the (sub) tree.
   **/
  static Value createTree(int size, int seed)
  {
    if (size > 1) {
      seed = random(seed);
      int next_val = seed % RANGE;

      return new Value(next_val,
		      	createTree(size/2, seed),
		      	createTree(size/2, skiprand(seed, size+1)));
    } else {
      return null;
    }
  }

  /**
   * Perform a bitonic sort based upon the Bilardi and Nicolau algorithm.
   *
   * @param spr_val the "spare" value in the algorithm.
   * @param direction the direction of the sort (forward or backward)
   * @return the new "spare" value.
   **/
  int bisort(int spr_val, boolean direction)
  {
    if (left == null) {
      if ((value > spr_val) ^ direction) {
	int tmpval = spr_val;
	spr_val = value;
	value = tmpval;
      }
    } else {
      int val = value;
      value = left.bisort(val, direction);
      boolean ndir = !direction;
      spr_val = right.bisort(spr_val, ndir);
      spr_val = bimerge(spr_val, direction);
    }
    return spr_val;
  }

  /**
   * Perform the merge part of the bitonic sort.  The merge part does
   * the actualy sorting.
   * @param spr_val the "spare" value in the algorithm.
   * @param direction the direction of the sort (forward or backward)
   * @return the new "spare" value
   **/
  int bimerge(int spr_val, boolean direction)
  {
    int   rv = value;
    Value pl = left;
    Value pr = right;

    boolean rightexchange = (rv > spr_val) ^ direction;
    if (rightexchange) {
      value = spr_val;
      spr_val = rv;
    }

    while (pl != null) {
      int   lv  = pl.value;
      Value pll = pl.left;
      Value plr = pl.right;
      rv        = pr.value;
      Value prl = pr.left;
      Value prr = pr.right;

      boolean elementexchange = (lv > rv) ^ direction;
      if (rightexchange) {
	if (elementexchange) {
	  pl.swapValRight(pr);
	  pl = pll;
	  pr = prl;
	} else {
	  pl = plr;
	  pr = prr;
	}
      } else {
	if (elementexchange) {
	  pl.swapValLeft(pr);
	  pl = plr;
	  pr = prr;
	} else {
	  pl = pll;
	  pr = prl;
	}
      }
    }

    if (left != null) {
      value = left.bimerge(value, direction);
      return right.bimerge(spr_val, direction);
    } else {
      return spr_val;
    }
  }

  /**
   * Swap the values and the right subtrees.
   * @param n the other subtree involved in the swap.
   **/
  void swapValRight(Value n)
  {
    int   tmpv = n.value;
    Value tmpr = n.right;

    n.value = value;
    n.right = right;

    value = tmpv;
    right = tmpr;
  }

  /**
   * Swap the values and the left subtrees.
   * @param n the other subtree involved in the swap.
   **/
  void swapValLeft(Value n)
  {
    int   tmpv = n.value;
    Value tmpl = n.left;

    n.value = value;
    n.left  = left;

    value = tmpv;
    left  = tmpl;
  }

  /**
   * Print out the nodes in the binary tree in infix order.
   **/
  void inOrder()
  {
    if (left != null)
      left.inOrder();
    System.out.println(value + " " + hashCode());
    if (right != null)
      right.inOrder();
  }


  /**
   * A random generator.  The original Olden benchmark uses its
   * own random generator.  We use the same one in the Java version.
   * @return the next random number in the sequence.
   **/
  private static int mult(int p, int q)
  {
    int p1 = p/CONST_m1;
    int p0 = p%CONST_m1;
    int q1 = q/CONST_m1;
    int q0 = q%CONST_m1;
    return (((p0*q1+p1*q0) % CONST_m1) * CONST_m1+p0*q0);
  }

  /**
   * A routine to skip the next <i>n</i> random numbers.
   * @param seed the current random no. seed
   * @param n the number of numbers to skip
   **/
  private static int skiprand(int seed, int n)
  {
    for (; n != 0; n--) seed = random(seed);
    return seed;
  }

  /**
   * Return a random number based upon the seed value.
   * @param seed the random number seed value
   * @return a random number based upon the seed value.
   **/
  static int random(int seed)
  {
    int tmp = mult(seed, CONST_b) + 1;
    return tmp;
  }
}

