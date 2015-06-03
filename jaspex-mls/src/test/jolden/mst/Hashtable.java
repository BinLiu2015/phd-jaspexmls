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

public class Hashtable
{
  protected final HashEntry array[];
  protected final int size;

  public Hashtable(int sz)
  {
    size = sz;
    array = new HashEntry[size];
    for (int i=0; i<size; i++)
      array[i] = null;
  }

  private int hashMap(Object key)
  {
    return ((key.hashCode() >> 3 ) % size);
  }

  public Object get(Object key)
  {
    int j = hashMap(key);

    HashEntry ent = null;

    for (ent = array[j]; ent != null && ent._key != key; ent = ent._next);

    if (ent != null) return ent._entry;
    return null;
  }

  public void put(Object key, Object value)
  {
    int j = hashMap(key);
    HashEntry ent = new HashEntry(key, value, array[j]);
    array[j] = ent;
  }

  public void remove(Object key)
  {
    int j = hashMap(key);
    HashEntry ent = array[j];
    if (ent != null && ent._key == key)
      array[j] = ent._next;
    else {
      HashEntry prev = ent;
      for (ent = ent._next; ent != null && ent._key != key;
	   prev = ent, ent = ent._next);
      prev._next = ent._next;
    }
  }

}

class HashEntry
{
  final Object _key;
  final Object _entry;
  HashEntry _next;

  public HashEntry(Object key, Object entry, HashEntry next)
  {
    this._key = key;
    this._entry = entry;
    this._next = next;
  }

}
