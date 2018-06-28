import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import blockchain.types.Uint256;
import blockchain.types.Uint256Int;
import blockchain.types.Address;
import blockchain.Block;
import blockchain.Message;
import blockchain.Transaction;

import java.io.File;

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
	assert(args.length > 0);

	 CharStream input = CharStreams.fromFileName(args[0]);
	 SolidityLexer lexer = new SolidityLexer(input);
	 CommonTokenStream tokens = new CommonTokenStream(lexer);
	 SolidityParser parser = new SolidityParser(tokens);

	 ParseTree tree = parser.sourceUnit();
	 TranslateVisitor visitor = new TranslateVisitor();

	 CompilationUnit cu = (CompilationUnit) visitor.visit(tree);

	 cu = SymbolSolver.refineTranslation(cu);

	 System.out.println(cu.toString());
    }
}
