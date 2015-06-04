# Sample run of JaSPEx-MLS

## Setup environment

```bash
$ cd jaspex-mls/
jaspex-mls$ tar xvjpf ../openjdk-continuations/openjdk-continuation-vm2013-linux-amd64.tar.bz2
openjdk-continuation-vm2013-linux-amd64/
...

jaspex-mls$ export JASPEXDIR=`pwd`
jaspex-mls$ export CLASSPATH=$JASPEXDIR/libs/asm-debug-all-4.0.jar:$JASPEXDIR/libs/asmlib.jar:$JASPEXDIR/libs/commons-lang3-3.1.jar:$JASPEXDIR/libs/contlib.jar:$JASPEXDIR/libs/logback-classic-1.0.13.jar:$JASPEXDIR/libs/logback-core-1.0.13.jar:$JASPEXDIR/libs/quality-check-0.11-SNAPSHOT.jar:$JASPEXDIR/libs/slf4j-api-1.7.5.jar:$JASPEXDIR/build/:$JASPEXDIR/benchmarks/javagrande_2.0/:$JASPEXDIR/benchmarks/javagrande_2.0/section2/
jaspex-mls$ export PATH=openjdk-continuation-vm2013-linux-amd64/bin/:$PATH
```

## Running JaSPEx-MLS

```bash
jaspex-mls$ java jaspex.Jaspex
JaSPEx
        Usage: java jaspex.Jaspex [-options] Class [args...]

where options include:
    -fast                (PERFORMANCE) disable extra verifications/assertions and debug output
    -nospeculation       (DEBUG) apply all bytecode modifications, but never actually accept any speculation
    -noinsertspeculation (DEBUG) apply bytecode modifications, except the transformation to call spawnSpeculation()
    -printclass          (DEBUG) print modified classes before loading them
    -writeclass          (DEBUG) write modified classes to output/ directory before loading them
    -silent              disables (almost) all debug output
    -nolinenumber        (DEBUG) remove LINENUMBER bytecodes
    -removesync          (DEBUG) remove ACC_SYNCHRONIZED from transactified classes (use with care)
    -removemonitors      (DEBUG) remove MONITORENTER/MONITOREXIT from transactified classes (use with EXTREME care)
    -debug               (DEBUG) disable pretty printing in debug outputs and class names, mainly for use when running inside a debugger
    -jar                 similar to java -jar <jarfile.jar>
    -skipspeculation     list of files containing methods and classes which are not accepted as targets for speculation
    -contfreeze          (PERFORMANCE) allow the current SpeculationTask to be frozen inside a continuation instead of waiting for its parent to finish working, allowing threads to return to the thread pool earlier
    -wssizehack          (PERFORMANCE,HACK) forces speculation to commit if writeset grows above a certain size
    -rvp                 (PERFORMANCE,EXPERIMENTAL) employ return value prediction
    -agressivervp        (PERFORMANCE,EXPERIMENTAL) employ more agressive return value prediction (requires -rvp)
    -txstats             (DEBUG) prints detailed statistics for every committed transaction
    -aqtweaks            (PERFORMANCE,EXPERIMENTAL) tweaks thread pool behavior for task buffering
    -classcache          (PERFORMANCE,EXPERIMENTAL) cache and reuse classes modified for speculation
    -noremoveoverspec    (DEBUG) disable RemoveOverspeculation for testing purposes
    -profile             (DEBUG,EXPERIMENTAL) execute in single-threaded mode with more statistics
    -nttracker           (PERFORMANCE,EXPERIMENTAL,HACK) allow limited speculative access to non-transactional objects (note that this assumes that, for instance in the case of collections, operations like equals and hashcode on the objects contained therein are always safe to use; this assumption is not verified and if broken will lead to wrong results)
    -nojdkchanges        (DEBUG) disable transactification of some JDK classes (JDK changes are enabled by default if the'JDK_HACK_PACKAGE' environment variable is defined)
    -readmap             (PERFORMANCE,EXPERIMENTAL) use hashmap instead of linked-list to represent transaction read-sets
    -notaskbuffering     forces thread pool task buffering to off (replaces -alternativequeue/-hybridqueue)
    -counttasks          (DEBUG) counts the number of times each method is called via spawnSpeculation for profiling
    -allowdummytx        (HACK) allows bypassing transactification in a per-case basis; use with EXTREME care
    -txabortstats        (DEBUG) prints detailed statistics for every aborted transaction
    -detectlocal         (PERFORMANCE,EXPERIMENTAL) directly access objects created by current transaction
    -nofreeze            (DEBUG) disable speculation freeze
```

## Executing test suite (colored output)

```bash
jaspex-mls$ java jaspex.tools.SimpleTester
test.NewSpecExample01 test.NewSpecExample02 test.NewSpecExample03 test.NewSpecExample04
test.NewSpecExample05 test.NewSpecExample06 test.NewSpecExample07 test.NewSpecExample08
test.NewSpecExample09 test.NewSpecExample10 test.NewSpecExample11 test.NewSpecExample12
test.NewSpecExample13 test.NewSpecExample14 test.NewSpecExample15 test.NewSpecExample16
test.NewSpecExample17 test.NewSpecExample18 test.NewSpecExample19 test.NewSpecExample20
test.NewSpecExample21 test.NewSpecExample22 test.NewSpecExample23 test.NewSpecExample24
test.NewSpecExample25 test.NewSpecExample26 test.NewSpecExample27 test.NewSpecExample28
test.NewSpecExample29 test.NewSpecExample30 test.NewSpecExample31 test.NewSpecExample32
test.NewSpecExample33 test.NewSpecExample34 test.NewSpecExample35 test.NewSpecExample36
test.NewSpecExample37 test.NewSpecExample38 test.NewSpecExample39 test.NewSpecExample40
test.NewSpecExample41 test.NewSpecExample42 test.NewSpecExample43 test.NewSpecExample44
test.NewSpecExample45 test.NewSpecExample46 test.NewSpecExample47 test.NewSpecExample48
test.NewSpecExample49 test.NewSpecExample50 test.NewSpecExample51 test.NewSpecExample52
test.NewSpecExample53 test.NewSpecExample54 test.NewSpecExample55 test.NewSpecExample56
test.NewSpecExample57 test.NewSpecExample58 test.NewSpecExample59 test.NewSpecExample60
test.NewSpecExample61 test.NewSpecExample62 test.NewSpecExample63 test.NewSpecExample64
test.NewSpecExample65 test.NewSpecExample66 test.NewSpecExample67 test.NewSpecExample68
test.NewSpecExample69 test.NewSpecExample70 test.NewSpecExample71 test.NewSpecExample72
test.NewSpecExample73 test.NewSpecExample74 test.NewSpecExample75 test.NewSpecExample76
test.NewSpecExample77 test.NewSpecExample78 test.NewSpecExample79 test.NewSpecExample80
test.NewSpecExample81 test.NewSpecExample82 test.NewSpecExample83 test.NewSpecExample84
test.NewSpecExample85
```

## Running some benchmarks

See articles for more details and scientific testing; this is just a sample taken on a Intel Core i5-4210U laptop.

### aparapi life

Output is in generations/s (more is better)

* Sequential:

```bash
jaspex-mls$ java test.com.amd.aparapi.sample.life.Main -benchmark
108.73
```

* Parallel:

```bash
jaspex-mls$ java jaspex.Jaspex -fast -aqtweaks -allowdummytx test.com.amd.aparapi.sample.life.Main -benchmark
23:11:02.900 [main] INFO  j.speculation.SpeculativeClassLoader - JaSPEx SpeculativeClassLoader (Start time: 23:11:02, Args: 'test.com.amd.aparapi.sample.life.Main -benchmark')
23:11:03.974 [WorkT0] INFO  j.speculation.runtime.CodegenHelper - Setting isDummy transaction flag for test.com.amd.aparapi.sample.life.Main$LifeKernel.processPixel$speculative
229.22
23:11:38.985 [WorkT1] INFO  j.s.nsruntime.ContSpeculationControl - Stats: 0m36.216s, 31994 speculations (31994 committed, 0 aborted / 0 failed validation, 0 early rejected, 0 late rejected, 32000 tasks completed by thread pool), CPU Usage 388% / 97%
```

### jgf series

* Sequential:

```bash
jaspex-mls$ java JGFSeriesBenchSizeB
Java Grande Forum Benchmark Suite - Version 2.0 - Section 2 - Size B

Section2:Series:Kernel:SizeB    52.744 (s)       3791.8816       (coefficients/s)
```

* Parallel:

```bash
jaspex-mls$ java jaspex.Jaspex -fast -contfreeze -aqtweaks JGFSeriesBenchSizeB
23:13:20.067 [main] INFO  j.speculation.SpeculativeClassLoader - JaSPEx SpeculativeClassLoader (Start time: 23:13:20, Args: 'JGFSeriesBenchSizeB')
23:13:20.129 [WorkT0] WARN  jaspex.transactifier.Transactifier - Class JGFSeriesBenchSizeB is compiled for Java 7 or newer
23:13:20.194 [WorkT0] WARN  jaspex.transactifier.Transactifier - Class jgfutil/JGFInstrumentor is compiled for Java 7 or newer
Java Grande Forum Benchmark Suite - Version 2.0 - Section 2 - Size B

23:13:20.385 [WorkT0] WARN  jaspex.transactifier.Transactifier - Class series/JGFSeriesBench is compiled for Java 7 or newer
23:13:20.464 [WorkT0] WARN  jaspex.transactifier.Transactifier - Class jgfutil/JGFSection2 is compiled for Java 7 or newer
23:13:20.477 [WorkT0] WARN  jaspex.transactifier.Transactifier - Class series/SeriesTest is compiled for Java 7 or newer
23:13:20.551 [WorkT0] WARN  jaspex.transactifier.Transactifier - Class jgfutil/JGFTimer is compiled for Java 7 or newer
Section2:Series:Kernel:SizeB    23.479 (s)       8518.208        (coefficients/s)
23:13:44.261 [WorkT1] INFO  j.s.nsruntime.ContSpeculationControl - Stats: 0m24.305s, 199765 speculations (199765 committed, 0 aborted / 0 failed validation, 0 early rejected, 0 late rejected, 199998 tasks completed by thread pool), CPU Usage 384% / 96%
```
