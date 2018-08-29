#!/bin/sh

# Where is the Mines JTK? (Where is your build.xml?)
export MINES_JTK_HOME=~/Home/research/jtk
export SBP_HOME=~/Home/research/sbp
export LIBS_HOME=~/Home/research/

# If Mac OS X, which version of Java should we use?
export JAVA_VERSION=1.8.0

# Where will Java look for classes? 
# Add other jars to this list as necessary.
export CLASSPATH=\
$MINES_JTK_HOME/core/build/libs/edu-mines-jtk.1.0.0.jar:\
$MINES_JTK_HOME/core/build/classes/main:\
$LIBS_HOME/libs/arpack-java.jar:\
$LIBS_HOME/libs/netlib-java.jar:\
$LIBS_HOME/libs/gluegen-rt.jar:\
$LIBS_HOME/libs/jogl-all.jar:\
$LIBS_HOME/libs/junit.jar:\
$SBP_HOME/build/libs/sbp.jar:\
.

# Run a server 64-bit VM with assertions enabled and a 1GB max Java heap.
# Modify these flags and properties as necessary for your system.
java -server -d64 -ea -Xmx7g \
-Djava.util.logging.config.file=$HOME/.java_logging_config \
$*
