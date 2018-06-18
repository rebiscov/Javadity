EX = field_declaration.sol
SRC = src/main/java/javadity
ANTLR = java -Xmx500M -cp "/usr/local/lib/antlr-4.7.1-complete.jar:$(CLASSPATH)" org.antlr.v4.Tool

all:
	mvn clean package

run:
	java -jar target/javadity*.jar $(EX)

grammar:
	$(ANTLR) $(SRC)/Solidity.g4 -visitor -no-listener
	
clean:
	rm $(SRC)/*.tokens $(SRC)/*.interp

clemacs:
	rm $(SRC)/*~
