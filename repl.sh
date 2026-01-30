#!/bin/bash

mvn -q  compile || exit 1

# JLine provides readline functionality (arrow keys, history) built-in
java --enable-native-access=ALL-UNNAMED -cp "target/classes:$HOME/.m2/repository/org/ow2/asm/asm/9.6/asm-9.6.jar:$HOME/.m2/repository/org/ow2/asm/asm-util/9.6/asm-util-9.6.jar:$HOME/.m2/repository/org/jline/jline/3.25.1/jline-3.25.1.jar" com.miniml.Repl 
