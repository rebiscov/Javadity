import java.io.File;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;

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
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;


public class SymbolSolver {
    public static final String PATH_TO_TYPES = ".";
    public static final String ADDRESS_TYPE = "blockchain.types.Address";
    public static final String UINT_TYPE = "blockchain.types.Uint256Int";
    public static final List<String> UNINITIALIZED_VARIABLES = Arrays.asList(new String[] {"msg", "tx", "block"});

    private static void setDefaultValue(CompilationUnit cu) {
	List<VariableDeclarator> nodeList = cu.findAll(VariableDeclarator.class);
	Collections.reverse(nodeList);
	nodeList.stream()
	    .filter(elt -> !UNINITIALIZED_VARIABLES.contains(elt.getNameAsString()))
	    .forEach(vd -> {
		Type type = vd.getType();


		// If there is no initialization for a non-primitive type, add a default one
		if (!vd.getInitializer().isPresent() && !type.isPrimitiveType()) {
		    ClassOrInterfaceType clazz = new ClassOrInterfaceType(null, type.toString());

		    vd.setInitializer(new ObjectCreationExpr(null, clazz, new NodeList<Expression>()));
		}
	    });
    }

    private static void correctAddressTransferMethod(CompilationUnit cu) {
	List<MethodCallExpr> nodeList = cu.findAll(MethodCallExpr.class);
	Collections.reverse(nodeList);
	nodeList.forEach(mce -> {
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
	List<ArrayAccessExpr> nodeList = cu.findAll(ArrayAccessExpr.class);
	Collections.reverse(nodeList);
	nodeList.forEach(aae -> {
		Expression expr = aae.getIndex();
		ResolvedType resolvedTypeExpr;
		resolvedTypeExpr = expr.calculateResolvedType();

		if (resolvedTypeExpr.describe().equals(UINT_TYPE))
		    aae.setIndex(new MethodCallExpr(expr, "asInt", new NodeList<Expression>()));
		else if (resolvedTypeExpr.describe().equals(ADDRESS_TYPE))
		    aae.setIndex(new FieldAccessExpr(expr, "ID"));
	    });
    }

    private static void setArrayDimensions(CompilationUnit cu) {
	List<ArrayCreationLevel> nodeList = cu.findAll(ArrayCreationLevel.class);
	Collections.reverse(nodeList);
	nodeList.forEach(acl -> {
		if (acl.getDimension().isPresent()) {
		    Expression dim = acl.getDimension().get();

		    acl.setDimension(new MethodCallExpr(dim, "asInt", new NodeList<Expression>()));
		}
	    });
    }

    public static CompilationUnit refineTranslation(CompilationUnit cu) {
	TypeSolver javaParserTypeSolver = null;
	try {
	    javaParserTypeSolver = new JarTypeSolver(SymbolSolver.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
	}
	catch (Exception e) {
	    System.out.println(e);
	    System.exit(1);
	}
	TypeSolver reflectionTypeSolver = new ReflectionTypeSolver();

	CombinedTypeSolver combined = new CombinedTypeSolver();
	combined.add(javaParserTypeSolver);
	combined.add(reflectionTypeSolver);

	JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combined);
	JavaParser.getStaticConfiguration().setSymbolResolver(symbolSolver);

	cu = JavaParser.parse(cu.toString());
	correctArrayAccess(cu);

	cu = JavaParser.parse(cu.toString());
	setArrayDimensions(cu);

	cu = JavaParser.parse(cu.toString());
	correctAddressTransferMethod(cu);

	cu = JavaParser.parse(cu.toString());
	setDefaultValue(cu);

	return cu;
    }
}
