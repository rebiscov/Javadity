# Javadity

Javadity is a Solidity to Java translator. It aims at generating a Java program that can be verified using the [KeY tool](https://www.key-project.org/).

## Build

It is highly recommended that you use [Maven](https://maven.apache.org/). To install Javadity, just type `make grammar` (to generate a Solidity parser) and then `make` (to build Javadity). Then you can get the jar into the folder target (the jar should be called javadity-X.Y-SNAPSHOT.jar).

## Run

Once compiled, you can easily run some test by doing `make run EX=aSolidityFile.sol`. Otherwise run `java javadity-X.Y-SNAPSHOT.jar --help` for more information.
