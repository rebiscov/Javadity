import blockchain.types.Uint256;
import blockchain.types.Uint256Int;
import blockchain.types.Address;
import blockchain.Block;
import blockchain.Message;
import blockchain.Transaction;

import java.io.File;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;


public class SymbolSolver {
    public static final String PATH_TO_TYPES = "/home/vincent/Documents/M1/Internship/translator/javadity/src/main/java/javadity/";
    public static final String ADDRESS_TYPE = "blockchain.types.Address";
    public static final String UINT_TYPE = "blockchain.types.Uint256Int";

    private static void setDefaultValue(CompilationUnit cu) {
	cu.findAll(VariableDeclarator.class).forEach(vd -> {
		Type type = vd.getType();


		// If there is no initialization for a non-primitive type, add a default one
		if (!vd.getInitializer().isPresent() && !type.isPrimitiveType()) {
		    ClassOrInterfaceType clazz = new ClassOrInterfaceType(null, type.toString());

		    vd.setInitializer(new ObjectCreationExpr(null, clazz, new NodeList<Expression>()));
		}
	    });
    }

    private static void correctAddressTransferMethod(CompilationUnit cu) {
	cu.findAll(MethodCallExpr.class).forEach(mce -> {
		if (mce.getScope().isPresent()) { // If the method call has a scope...
		    Expression scope = mce.getScope().get();
		    ResolvedType resolvedTypeScope = scope.calculateResolvedType();

		    // If this scope is an Address and it is a call the method transfer
		    if (resolvedTypeScope.describe().equals(ADDRESS_TYPE) && mce.getNameAsString().equals("transfer")) {
			NodeList<Expression> transferArgs = mce.getArguments();
			transferArgs.add(new ThisExpr());
			transferArgs.add(new NameExpr("block"));
			transferArgs.add(new NameExpr("tx"));
		    }
		}
	    });
    }

    private static void correctArrayAccess(CompilationUnit cu) {
	cu.findAll(ArrayAccessExpr.class).forEach(aae -> {
		Expression expr = aae.getIndex();
		ResolvedType resolvedTypeExpr;
		resolvedTypeExpr = expr.calculateResolvedType();

		if (resolvedTypeExpr.describe().equals(UINT_TYPE))
		    aae.setIndex(new MethodCallExpr(expr, "asInt", new NodeList<Expression>()));
	    });

	cu.findAll(ArrayCreationLevel.class).forEach(acl -> {
		if (acl.getDimension().isPresent()) {
		    Expression dim = acl.getDimension().get();

		    acl.setDimension(new MethodCallExpr(dim, "asInt", new NodeList<Expression>()));
		}
	    });
    }

    public static CompilationUnit refineTranslation(CompilationUnit cu) {
	TypeSolver javaParserTypeSolver = new JavaParserTypeSolver(new File(PATH_TO_TYPES));
	TypeSolver reflectionTypeSolver = new ReflectionTypeSolver();

	CombinedTypeSolver combined = new CombinedTypeSolver();
	combined.add(javaParserTypeSolver);
	combined.add(reflectionTypeSolver);

	JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combined);
	JavaParser.getStaticConfiguration().setSymbolResolver(symbolSolver);

	cu = JavaParser.parse(cu.toString());
	correctArrayAccess(cu);
	cu = JavaParser.parse(cu.toString());
	correctAddressTransferMethod(cu);
	cu = JavaParser.parse(cu.toString());
	setDefaultValue(cu);

	return cu;
    }
}
