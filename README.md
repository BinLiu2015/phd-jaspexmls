# JaSPEx-MLS Java Software Speculative Parallelization Framework

In this repository you will find the JaSPEx-MLS framework for automatically parallelizing sequential Java applications, speeding them up on modern multicore hardware.

The framework does this by analyzing Java applications --- from compiled bytecode, **no source code access is needed** --- and then by modifying them as they are loaded by the JVM to use Software Transactional Memory (STM) and Method-Level Speculation (MLS).

The framework also requires a Java VM with support for continuations, and I include a special build of OpenJDK for this purpose based on [Hiroshi2012](http://hiroshiyamauchi.blogspot.pt/2012/10/the-jvm-continuation-contribution.html).

The end result is that sequential applications, when executed by JaSPEx-MLS on top of the supplied OpenJDK build can surpass normal OpenJDK builds in performance. Hopefully ;)

I developed JaSPEx-MLS as part of my PhD dissertation, which you will also find [here](./dissertation/anjo-phdthesis.pdf). I currently don't have plans to develop it further, but I wanted to get it out there, so that others can learn and take from the code, if they find it useful, instead of it just bit-rotting in my SSD.

## What will you find in this repository?

* **asmlib**, a library of useful bytecode analysis and manipulation tools built on top of the [ASM framework](http://asm.ow2.org/)

* **contlib**, an API for using first-class continuations on top of a specially-modified OpenJDK JVM

* **openjdk-continuations**, an OpenJDK build (for 64-bit Linux) with support for continuations, and instructions on how it can be built

* **jaspex-mls**, the JaSPEx-MLS framework itself, which uses all of the above

* **articles/presentations**, several articles and presentations I did on the JaSPEx-MLS framework over the years

## Running JaSPEx-MLS

See [RUNNING.md](RUNNING.md)

## Why now?

I feel that if I don't put this out there now, I may never do it :)

I was born in sunny Portugal and as I was working on this codebase alone, I ended up writing many of the comments in the code in Portuguese (the code itself is in English). My original plan was to clean up the code and translate the comments before pushing it to github, but as in the meantime I started on other projects I never got around to it so publishing it right now is a way of ~shaming~ motivating me to not give up on that plan.

All of my code is GPLv3+, so feel free to play with it, and have a nice day!

-- Ivo Anjo
