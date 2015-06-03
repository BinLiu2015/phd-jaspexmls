#!/bin/bash

export CLASSPATH=$CLASSPATH:`pwd`:.

find -type f -name *.class -exec rm '{}' \;

cd jgfutil
javac *.java
#cd ../section1
#javac *.java
cd ../section2
javac *.java
cd ../section3
javac *.java
