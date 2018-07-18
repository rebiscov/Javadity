import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.nio.file.*;

import java.io.File;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import com.github.javaparser.ast.CompilationUnit;

public class App {
    public static void main(String[] args) throws Exception {
	ArgumentParser argparser = ArgumentParsers.newFor("Javadity").build()
	    .defaultHelp(true)
	    .description("A Solidity to Java translator.");

	argparser.addArgument("file")
	    .help("Solidity file containing the contracts to translate");

	argparser.addArgument("--dst", "-d")
	    .help("Destination file")
	    .setDefault("NoName.java");

	Namespace ns = null;

	try {
            ns = argparser.parseArgs(args);
        } catch (ArgumentParserException e) {
            argparser.handleError(e);
            System.exit(1);
        }

	CharStream input = CharStreams.fromFileName(ns.getString("file"));
	SolidityLexer lexer = new SolidityLexer(input);
	CommonTokenStream tokens = new CommonTokenStream(lexer);
	SolidityParser parser = new SolidityParser(tokens);

	ParseTree tree = parser.sourceUnit();
	TranslateVisitor visitor = new TranslateVisitor();

	CompilationUnit cu = (CompilationUnit) visitor.visit(tree);

	cu = SymbolSolver.refineTranslation(cu);

	Path file = Paths.get(ns.getString("dst"));

	Files.write(file, cu.toString().getBytes());
    }
}
