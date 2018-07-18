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

// The class SymbolSolver allows the user to make some corrections to the Java AST using, for example, a symbol solver (this is not mandatory).

public class SymbolSolver {
    public static final String ADDRESS_TYPE = "blockchain.types.Address";
    public static final String UINT_TYPE = "blockchain.types.Uint256Int";
    public static final List<String> UNINITIALIZED_VARIABLES = Arrays.asList(new String[] {"msg", "tx", "block"}); // List of variables that must not be initialized

    // Add a default value to all the non-primitive, non-initialized variables (the goal is to have a behaviour as close as in Solidity)
    private static void setDefaultValue(CompilationUnit cu) {
	List<VariableDeclarator> nodeList = cu.findAll(VariableDeclarator.class);
	Collections.reverse(nodeList);
	nodeList.stream()
	    .filter(elt -> !UNINITIALIZED_VARIABLES.contains(elt.getNameAsString()))
	    .forEach(vd -> {
		Type type = vd.getType();

		if (type.asString().equals("Uint256"))
		    type = Helper.getUintTypeIntImplem();


		// If there is no initialization for a non-primitive type, add a default one
		if (!vd.getInitializer().isPresent() && !type.isPrimitiveType()) {
		    ClassOrInterfaceType clazz = new ClassOrInterfaceType(null, type.toString());

		    vd.setInitializer(new ObjectCreationExpr(null, clazz, new NodeList<Expression>()));
		}
	    });
    }

    // The transfer method in Solidity takes several implicit arguments (the block variable, the transaction variable etc...) and we need to make this explicit in Java
    // Note that a transfer in Solidity is an external call the the fallback function of the recipient
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

    // The index of an array access can be an Address or an Uint256 once the visitor made the translation, we need to convert these values to integers
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

    // Array initialization in Solidity and in Java does not work the same way leading to a incorrect translation of the visitor
    // (when translating a declaration into Java, the initial size is omitted by the visitor)
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

    // The keccak function in Java is in a file Crypto.java, thus all call keccak256(expr) must be translated in Crypto.keccak(expr)
    private static void crypto(CompilationUnit cu) {
	List<MethodCallExpr> nodeList = cu.findAll(MethodCallExpr.class);
	Collections.reverse(nodeList);
	nodeList.stream()
	    .filter(mce -> mce.getName().asString().equals("keccak256"))
	    .forEach(mce -> mce.setScope(new NameExpr("Crypto")));
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

	cu = JavaParser.parse(cu.toString());
	crypto(cu);

	return cu;
    }
}
