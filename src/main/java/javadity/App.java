import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

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

	 System.out.println(cu.toString());
    }
}
