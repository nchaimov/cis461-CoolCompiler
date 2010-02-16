#
# Makefile for Cool compiler components 
#

CUPHOME = $(HOME)/tools/java_cup_11a
CUP = java -cp $(CUPHOME)/java-cup-11a.jar java_cup.Main 
CUPLIB = ./lib/java-cup-11a-runtime.jar
JFLEX = $(HOME)/tools/jflex-1.4.3/bin/jflex
STRTEMPL = ./lib/stringtemplate-3.2.1.jar
ANTLR = ./lib/antlr-2.7.7.jar
CLI = ./lib/commons-cli-1.2.jar

## Eventually we'll use all of these 
LIBS = $(CUPLIB):$(STRTEMPL):$(ANTLR):$(CLI)

JAVACOPT =  -Xlint:unchecked 
# JAVACOPT =  

all:   Cool.class

Cool.class:	Cool.java ScanDriver.class parser.class Util.class TypeChecker.class
	javac -classpath .:$(LIBS) $(JAVACOPT) $< 

ScanDriver.class:	ScanDriver.java coolScanner.java
	javac -classpath .:$(LIBS) $(JAVACOPT) $< 

Util.java: Nodes.java
	ruby gen_idToName.rb > Util.java

Util.class: Util.java Environment.class
	javac -classpath .:$(LIBS) $(JAVACOPT) Util.java 

Environment.class: Environment.java HashStack.class
	javac -classpath .:$(LIBS) $(JAVACOPT) Environment.java

ASTnode.class: ASTnode.java Util.class
	javac -classpath .:$(LIBS) $(JAVACOPT) ASTnode.java

%.class:	%.java
	javac -classpath .:$(LIBS) $(JAVACOPT) $< 

sym.java parser.java: Cool.cup ASTnode.class Nodes.class
	$(CUP)   Cool.cup 

tables:	Cool.cup
	$(CUP) -dump Cool.cup &> tables

coolScanner.java:	Cool.jflex  sym.java
	$(JFLEX) --skel lib/jflex-skeleton-nested Cool.jflex



#=================

clean: ; rm *.class parser.java Util.java coolScanner.java



