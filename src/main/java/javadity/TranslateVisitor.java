import com.github.javaparser.ast.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.modules.*;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Collections;
import java.util.regex.Pattern;
import java.util.stream.*;

class Helper {
    public static final String UINT = "Uint256Int";

    public static ClassOrInterfaceType getUintType() {
	return new ClassOrInterfaceType(null, UINT);
    }

    public static ClassOrInterfaceType getAddressType() {
	return new ClassOrInterfaceType(null, "Address");
    }

    public static ClassOrInterfaceType getMessageType() {
	return new ClassOrInterfaceType(null, "Message");
    }

    public static ClassOrInterfaceType getTransactionType() {
	return new ClassOrInterfaceType(null, "Transaction");
    }

    public static ClassOrInterfaceType getBlockType() {
	return new ClassOrInterfaceType(null, "Block");
    }

    public static MethodDeclaration getSelfdestruct() {
	EnumSet<Modifier> modifiers = EnumSet.of(Modifier.PRIVATE);
	NodeList<Parameter> parameters = NodeList.nodeList(new Parameter(getAddressType(), "rcv"));

	MethodDeclaration selfdestruct = new MethodDeclaration(modifiers, "selfdestruct", new VoidType(), parameters);

	AssignExpr assign = new AssignExpr(new NameExpr("destroyed"), new BooleanLiteralExpr(true), AssignExpr.Operator.ASSIGN);
	ExpressionStmt assignStmt = new ExpressionStmt(assign);

	selfdestruct.setBody(new BlockStmt(NodeList.nodeList(assignStmt)));

	return selfdestruct;
    }

    public static MethodDeclaration getRequire() {
	EnumSet<Modifier> modifiers = EnumSet.of(Modifier.PUBLIC);
	NameExpr b = new NameExpr("b");
	NodeList<Parameter> parameters = NodeList.nodeList(new Parameter(PrimitiveType.booleanType(), b.toString()));
	
	MethodDeclaration require = new MethodDeclaration(modifiers, "require", new VoidType(), parameters);

	NodeList<ReferenceType> exceptions = NodeList.nodeList(new ClassOrInterfaceType(null, "Exception"));
	require.setThrownExceptions(exceptions);

	ThrowStmt throwException = new ThrowStmt(new ObjectCreationExpr(null, (ClassOrInterfaceType) exceptions.get(0), new NodeList<Expression>()));
	IfStmt ifStatement = new IfStmt(b, throwException, null);

	require.setBody(new BlockStmt(NodeList.nodeList(ifStatement)));
	
	return require;
    }

    public static MethodDeclaration getKeccak() {
	EnumSet<Modifier> modifiers = EnumSet.of(Modifier.PUBLIC);
	NameExpr x = new NameExpr("x");
	NodeList<Parameter> parameters = NodeList.nodeList(new Parameter(getUintType(), x.toString()));

	MethodCallExpr keccak = new MethodCallExpr(x, "keccak256");
	ReturnStmt ret = new ReturnStmt("x"); // TODO: finish implementation of keccak

	MethodDeclaration method = new MethodDeclaration(modifiers, "keccak256", getUintType(), parameters);
	method.setBody(new BlockStmt(NodeList.nodeList(ret)));

	return method;
    }

    public static NodeList<FieldDeclaration> getMagicVariables() {
	EnumSet<Modifier> modifiers = EnumSet.of(Modifier.PUBLIC);
	NodeList<FieldDeclaration> variables = new NodeList<>();

	// now
	variables.add(new FieldDeclaration(modifiers, getUintType(), "now"));

	// msg
	variables.add(new FieldDeclaration(modifiers, getMessageType(), "msg"));

	// block
	variables.add(new FieldDeclaration(modifiers, getBlockType(), "block"));

	// tx
	variables.add(new FieldDeclaration(modifiers, getTransactionType(), "tx"));

	// destroyed
	variables.add(new FieldDeclaration(modifiers, PrimitiveType.booleanType(), "destroyed"));

	return variables;
    }
}

/*********************/
/* TRANSLATE VISITOR */
/*********************/

public class TranslateVisitor extends SolidityBaseVisitor<Node> {
    private static final String[] imports = {"blockchain.Block", "blockchain.Message", "blockchain.Transaction",
					     "blockchain.types.Address", "blockchain.types.Uint256", "blockchain.types.Uint256Int"};
    private static final String UINT = Helper.UINT;
    private String currentContractName;
    private HashMap<String, String> typesMap;
    private ArrayList<MethodDeclaration> structConstructors = new ArrayList<>();
    
    private HashMap<String, SolidityModifier> modifiersMap = new HashMap<>();
    
    @Override
    public Node visitSourceUnit(SolidityParser.SourceUnitContext ctx) {

	// Create import declarations
	NodeList<ImportDeclaration> importDeclarations = new NodeList<>();
	for (String importDeclaration: imports)
	    importDeclarations.add(new ImportDeclaration(importDeclaration, false, false));

	
	// Add all the contracts the list of contracts
	NodeList contractsList = new NodeList(ctx.contractDefinition().stream() // for all contracts...
					      .map(elt -> this.visit(elt)) //..translate them in Java
					      .collect(Collectors.toList())
					      );

	// Add all the contracts to the compilation unit
	CompilationUnit cu = new CompilationUnit(null, importDeclarations, contractsList, null);

	return cu;
    }

    @Override
    public Node visitStructDefinition(SolidityParser.StructDefinitionContext ctx) {

	// Get the name of the structure
	String structName = ((SimpleName) this.visit(ctx.identifier())).asString();

	// Create a class for this structure
	ClassOrInterfaceDeclaration type = new ClassOrInterfaceDeclaration(EnumSet.of(Modifier.PUBLIC),
									   false, // Not an interface
									   structName);

	// Get all the fields to put in the structure
	List<SolidityParser.VariableDeclarationContext> fieldList = ctx.variableDeclaration();

	// Method declaration for the constructor of the struct
	EnumSet<Modifier> modifiers = EnumSet.of(Modifier.PRIVATE);
	ClassOrInterfaceType t = new ClassOrInterfaceType(null, structName);
	MethodDeclaration constructor = new MethodDeclaration(modifiers, t, structName);
	NodeList<Parameter> parameters = new NodeList<>();
	
	// Add the fields to the class 
	fieldList.stream()
	    .forEach(elt -> {
		    VariableDeclarator var = (VariableDeclarator) this.visit(elt); // Record the parameters type for the constructor
		    parameters.add(new Parameter(var.getType(), var.getName().asString()));
		    type.addMember(new FieldDeclaration(EnumSet.of(Modifier.PUBLIC),
							       var));
		});

	// Add the parameters for the constructor
	constructor.setParameters(parameters);

	// Create the body of the constructor
	NodeList<Statement> body = new NodeList<>();
	NameExpr ret = new NameExpr("ret");

	ObjectCreationExpr initializer = new ObjectCreationExpr(null,
								t,
								new NodeList<Expression>());
	VariableDeclarator structCreation = new VariableDeclarator(t,
								   ret.toString(),
								   initializer);
	
	ReturnStmt returnStmt = new ReturnStmt(ret);
	
	body.add(new ExpressionStmt(new VariableDeclarationExpr(structCreation)));

	parameters.stream()
	    .forEach(elt -> {
		    FieldAccessExpr field = new FieldAccessExpr(ret, elt.getName().asString());
		    NameExpr expr = new NameExpr(elt.getName());
		    AssignExpr assign = new AssignExpr(field, expr, AssignExpr.Operator.ASSIGN);
		    
		    body.add(new ExpressionStmt(assign));
		});
	
	body.add(returnStmt);

	constructor.setBody(new BlockStmt(body));

	// Add the constructor to the list of constructor
	structConstructors.add(constructor);


	return type;
    }

    @Override
    public Node visitEnumDefinition(SolidityParser.EnumDefinitionContext ctx) {
	// The modifiers of an Enum are always public & static
	EnumSet<Modifier> modifiers = EnumSet.of(Modifier.PUBLIC);
	modifiers.add(Modifier.STATIC);

	// Creation of the class that represents an Enum
	String enumName = ((SimpleName) this.visit(ctx.identifier())).asString();
	ClassOrInterfaceDeclaration type = new ClassOrInterfaceDeclaration(modifiers,
									   false, // Not an interface
									   enumName);
	
	// Get the enum IDs
	List<String> IDs = ctx.enumValue().stream()
	    .map(elt -> elt.getText())
	    .collect(Collectors.toList());

	// Enum values will be represented as Uint256
	ClassOrInterfaceType t = new ClassOrInterfaceType(null, UINT);
	
	NodeList<VariableDeclarator> values = new NodeList<>();

	// For each ID, associate a number.
	for (int i = 0; i < IDs.size(); i ++) {
	    // Create the value
	    ObjectCreationExpr expr = new ObjectCreationExpr(null,
							     t,
							     NodeList.nodeList(new IntegerLiteralExpr(i)));
	    
	    // Associate the ID to the value
	    VariableDeclarator var = new VariableDeclarator(t, IDs.get(i), expr);
	    values.add(var);
	}

	// Add the declaration to the class
	type.addMember(new FieldDeclaration(modifiers, values));

	return type;
    }

    @Override
    public Node visitContractDefinition(SolidityParser.ContractDefinitionContext ctx) {

	// Name of the contract
	String id = ((SimpleName)this.visit(ctx.identifier())).asString();
	currentContractName = id;

	// Get all the parts of the contract
	List<SolidityParser.ContractPartContext> contractPartList = ctx.contractPart();

	// Get the mapping of names of contract part to their type
	typesMap = getTypesMap(contractPartList);
	
	// Record all the user defined modifiers
	contractPartList.stream()
	    .map(elt -> elt.modifierDefinition())
	    .filter(elt -> elt != null) // Only keep the definitions of a modifier
	    .forEach(elt -> { // Put the modifiers in the mapping
		    String name = elt.identifier().getText();
		    ParserRuleContext code = elt.block();
		    List<String> parameters = elt.parameterList().parameter().stream()
			.map(e -> e.identifier().getText())
			.collect(Collectors.toList());

		    SolidityModifier modifier = new SolidityModifier(name, code, new ArrayList(parameters));

		    modifiersMap.put(name, modifier);
		});


	// Create a new class representing the contract
	ClassOrInterfaceDeclaration type = new ClassOrInterfaceDeclaration(EnumSet.of(Modifier.PUBLIC), false, id);

	// Deal with inheritance
	ctx.inheritanceSpecifier().stream()
	    .forEach(elt -> type.addExtendedType(elt.userDefinedTypeName().getText()));
	type.addExtendedType(Helper.getAddressType());

	// Add the members
	type.addMember(Helper.getRequire());
	type.addMember(Helper.getKeccak());
	type.addMember(Helper.getSelfdestruct());	

	Helper.getMagicVariables().stream()
	    .forEach(elt -> type.addMember(elt));
	
	contractPartList.stream()
	    .filter(elt -> elt.modifierDefinition() == null ) // Do not take the modifiers
	    .forEach(elt -> type.addMember((BodyDeclaration) this.visit(elt)));

	// Add the struct constructor
	structConstructors.stream()
	    .forEach(elt -> type.addMember(elt));

	return type;
    }

    @Override
    public Node visitStateVariableDeclaration(SolidityParser.StateVariableDeclarationContext ctx) {

	// Type of the state variable
	Type type = (Type) this.visit(ctx.typeName());
	
	// Get the modifier (public or private)
	EnumSet modifiers = EnumSet.noneOf(Modifier.class);
	if (!ctx.PublicKeyword().isEmpty())
	    modifiers.add(Modifier.PUBLIC);
	if (!ctx.PrivateKeyword().isEmpty() || !ctx.InternalKeyword().isEmpty())
	    modifiers.add(Modifier.PUBLIC);


	// Identifier of the state variable
	String id = ((SimpleName) this.visit(ctx.identifier())).asString();
	

	// If it exists, get the expression that initialize the state variable
	Expression expr;
	try {
	    expr = (Expression) this.visit(ctx.expression());
	}
	catch (java.lang.NullPointerException e){
	    expr = null;

	    // If there is not initialization and it is not a primitive type then
	    // put a default initialization
	    if (!type.isPrimitiveType()) { 
		ClassOrInterfaceType clazz =
		    new ClassOrInterfaceType(null, type.toString());
		expr = new ObjectCreationExpr(null, clazz, new NodeList<Expression>());
	    }
	}
	
	
	return new FieldDeclaration(modifiers, new VariableDeclarator(type, id, expr));
    }

    /* TYPE NAME */
    
    @Override
    public Node visitElementaryTypeName(SolidityParser.ElementaryTypeNameContext ctx) {
	// RE-DO THIS PLEASE!

	if (ctx.Int() != null || ctx.Uint() != null)
	    return new ClassOrInterfaceType(null, UINT);
	
	switch (ctx.getText()) {
	case "address":
	    return new ClassOrInterfaceType(null, "Address");
	case "bool":
	    return PrimitiveType.booleanType();
	case "string":
	    return new ClassOrInterfaceType(null, "String");
	default:
	    return new ClassOrInterfaceType(null, UINT);

	}
    }

    @Override
    public Node visitUserDefinedTypeName(SolidityParser.UserDefinedTypeNameContext ctx) {
	// A user defined type name is a typename defined by the user and it may contain dots

	// Get all the identifiers making the typename
	List<String> identifiers = ctx.identifier().stream()
	    .map(elt -> ((SimpleName) this.visit(elt)).asString())
	    .collect(Collectors.toList());

	// Get the type
	String type = String.join(".", identifiers);
	
	// Check if it is an enum
	if (typesMap.containsKey(type) && typesMap.get(type).equals("enum"))
	    type = UINT;

	// Return the typename
	return new ClassOrInterfaceType(null, type);
    }

    /* EXPRESSION */

    @Override
    public Node visitFunctionCallExpression(SolidityParser.FunctionCallExpressionContext ctx) {
	NodeList<Expression> arguments = new NodeList<>(); // List to store the arguments
	
	// Get the name of the method
	NameExpr method = (NameExpr) this.visit(ctx.expression());

	String[] methodNameParts = method.toString().split(Pattern.quote("."));
	int length = methodNameParts.length - 1;

	// If it is a transfer, add the argument 'this' which is implicit in Solidity
	if (length > 0 && methodNameParts[length].equals("transfer"))
	    arguments.add(new ThisExpr());

	// Put all the arguments in the list (if there are some)

	try {
	    ctx.functionCallArguments().expressionList().expression().stream()
		.forEach(elt -> arguments.add((Expression) this.visit(elt)));
	}
	catch (Exception e) { // If it is not an expression list, it my be a nameValue list
	    try {
		ctx.functionCallArguments().nameValueList().nameValue().stream()
		    .forEach(elt -> arguments.add((Expression) this.visit(elt.expression())));
	    }
	    catch (Exception f) {}
	}


	
	// Return the method call
	return new MethodCallExpr(null, method.getName(), arguments);
    }
    
    @Override
    public Node visitAdditiveExpression(SolidityParser.AdditiveExpressionContext ctx) {
	Expression expr1 = (Expression) this.visit(ctx.expression(0));
	Expression expr2 = (Expression) this.visit(ctx.expression(1));
	
	NodeList<Expression> parameters = new NodeList<>(expr2);
	SimpleName op;

	if (ctx.binop.getType() == SolidityParser.PLUS)
	    op = new SimpleName("sum");
	else
	    op = new SimpleName("sub");

	return new MethodCallExpr(expr1, op, parameters);
    }

    @Override
    public Node visitMultiplicativeExpression(SolidityParser.MultiplicativeExpressionContext ctx) {
	Expression expr1 = (Expression) this.visit(ctx.expression(0));
	Expression expr2 = (Expression) this.visit(ctx.expression(1));

	NodeList<Expression> parameters = new NodeList<>(expr2);
	SimpleName op;

	if (ctx.binop.getType() == SolidityParser.MULT)
	    op = new SimpleName("mul");
	else if (ctx.binop.getType() == SolidityParser.DIV)
	    op = new SimpleName("div");
	else
	    op = new SimpleName("mod");

	return new MethodCallExpr(expr1, op, parameters);
    }

    @Override
    public Node visitAndExpression(SolidityParser.AndExpressionContext ctx) {
	Expression expr1 = (Expression) this.visit(ctx.expression(0));
	Expression expr2 = (Expression) this.visit(ctx.expression(1));

	return new BinaryExpr(expr1, expr2, BinaryExpr.Operator.AND);
    }

    @Override
    public Node visitOrExpression(SolidityParser.OrExpressionContext ctx) {
	Expression expr1 = (Expression) this.visit(ctx.expression(0));
	Expression expr2 = (Expression) this.visit(ctx.expression(1));

	return new BinaryExpr(expr1, expr2, BinaryExpr.Operator.OR);
    }

    @Override
    public Node visitDotExpression(SolidityParser.DotExpressionContext ctx) {
	// A dot expression is of the form expr.identifier
	// In Java, this can be translated as a NameExpr (a name inside an expression)
	
	Expression expr = (Expression) this.visit(ctx.expression());
	SimpleName identifier = (SimpleName) this.visit(ctx.identifier());
	// Name name = new Name(new Name(expr.toString()), identifier.asString());

	return new NameExpr(expr.toString() + "." + identifier.asString());
    }

    @Override
    public Node visitNumberLiteral(SolidityParser.NumberLiteralContext ctx) {
	// We treat only integers TODO: use NumberUnit and HexNumber (see grammar)

	// The type is Uint256
	ClassOrInterfaceType type = new ClassOrInterfaceType(null, UINT);

	// Get the Integer
	NodeList<Expression> integer =
	    NodeList.nodeList(new IntegerLiteralExpr(ctx.DecimalNumber().getText()));

	return new ObjectCreationExpr(null, type, integer);
    }

    @Override
    public Node visitPrimaryExpression(SolidityParser.PrimaryExpressionContext ctx) {
	// TODO: implement more than booleans, integers and literals
	if (ctx.BooleanLiteral() != null) 
	    return new BooleanLiteralExpr(Boolean.parseBoolean(ctx.BooleanLiteral().getText()));
	else if (ctx.identifier() != null)
	    return new NameExpr((SimpleName) this.visit(ctx.identifier()));

	// If it is not a boolean or an identifier, it should be an integer
	return this.visitChildren(ctx);
    }

    
    @Override
    public Node visitCompExpression(SolidityParser.CompExpressionContext ctx) {
	Expression expr1 = (Expression) this.visit(ctx.expression(0));
	Expression expr2 = (Expression) this.visit(ctx.expression(1));

	NodeList<Expression> parameters = new NodeList<>(expr2);
	SimpleName op;
	
	if (ctx.binop.getText().equals("<"))
	    op = new SimpleName("le");
	else if (ctx.binop.getText().equals(">"))
	    op = new SimpleName("gr");
	else if (ctx.binop.getText().equals("<="))
	    op = new SimpleName("leq");
	else 
	    op = new SimpleName("geq");

	
	return new MethodCallExpr(expr1, op, parameters);
    }

    @Override
    public Node visitEqualityExpression(SolidityParser.EqualityExpressionContext ctx) {
	Expression expr1 = (Expression) this.visit(ctx.expression(0));
	Expression expr2 = (Expression) this.visit(ctx.expression(1));

	NodeList<Expression> parameters = new NodeList<>(expr2);
	SimpleName op = new SimpleName("eq");

	MethodCallExpr eq = new MethodCallExpr(expr1, op, parameters);
	
	if (ctx.binop.getText().equals("!="))
	    return new UnaryExpr(eq, UnaryExpr.Operator.LOGICAL_COMPLEMENT);
	
	return eq;
    }


    /* CONTRACT PARTS */

    @Override
    public Node visitFunctionDefinition(SolidityParser.FunctionDefinitionContext ctx) {
	// Get the identifier, if there is none, then it is the fallback function
	String id;
	if (ctx.identifier() != null)
	    id = ((SimpleName) this.visit(ctx.identifier())).asString();
	else
	    id = "fallback";

	// Modifiers TODO: implement all modifiers
	EnumSet modifiers = EnumSet.of(Modifier.PUBLIC); // Default is public
	SolidityParser.ModifierListContext modList = ctx.modifierList();

	if (!modList.PrivateKeyword().isEmpty() || !modList.InternalKeyword().isEmpty())
	    modifiers = EnumSet.of(Modifier.PRIVATE);

	// User defined modifiers
	BlockStmt block = (BlockStmt) this.visit(ctx.block());
	
	List<SolidityParser.ModifierInvocationContext> modifierInvocations = ctx.modifierList().modifierInvocation();
	Collections.reverse(modifierInvocations);
	
	for (SolidityParser.ModifierInvocationContext mod: modifierInvocations) {
	    String name = mod.identifier().getText();
	    
	    List<String> params;
	    try {
		params = new ArrayList(mod.expressionList().expression().stream()
				       .map(elt -> elt.getText())
				       .collect(Collectors.toList()));

	    }
	    catch (NullPointerException e) {
		params = new ArrayList<String>();
	    }


	    SolidityModifier solMod = modifiersMap.get(name);

	    HashMap<String, String> map = new HashMap<>();

	    for (int i = 0; i < params.size(); i++)
		map.put(solMod.parameters.get(i), params.get(i));

	    TranslateModifierVisitor modVisitor = new TranslateModifierVisitor(map, block);

	    block = (BlockStmt) modVisitor.visit(solMod.code);
	}
	    

	// Parameters list
	List<SolidityParser.ParameterContext> solParameterList = ctx.parameterList().parameter();
	NodeList<Parameter> javaParameterList = new NodeList<>();
	solParameterList.stream()
	    .forEach(elt -> javaParameterList.add((Parameter) this.visit(elt)));

	// Return parameters (for now, we will only handle one) TODO: implement multiple returned type
	Type returnedType = new VoidType();
	String returnedVarId = null;

	// If a value is returned, change the returned type
	if (ctx.returnParameters() != null && !ctx.returnParameters().parameterList().parameter().isEmpty()) {
	    SolidityParser.ParameterContext solReturnedParameter = ctx.returnParameters().parameterList().parameter(0); // we assume that there is only one returned value
	    Parameter javaReturnedParameter = (Parameter) this.visit(solReturnedParameter);
	    returnedType = javaReturnedParameter.getType();
	    returnedVarId = javaReturnedParameter.getName().asString();
	}


	// MethodDeclaration
	MethodDeclaration method = new MethodDeclaration(modifiers, id, returnedType, javaParameterList);
	NodeList<ReferenceType> exceptions = NodeList.nodeList(new ClassOrInterfaceType(null, "Exception"));
	method.setThrownExceptions(exceptions);

	// Set block
	method.setBody(block);
	
	
	return method;
    }

    /* STATEMENT */

    @Override
    public Node visitReturnStatement(SolidityParser.ReturnStatementContext ctx) {
	ReturnStmt value;

	// If the return statement returns an expression, go get it
	if (ctx.expression() != null) {
	    Expression expr = (Expression) this.visit(ctx.expression());
	    value = new ReturnStmt(expr);
	}
	else
	    value = new ReturnStmt();

	return value;
    }

    @Override
    public Node visitBlock(SolidityParser.BlockContext ctx) {
	NodeList<Statement> statements = NodeList.nodeList(
							   ctx.statement().stream()
							   .map(elt -> (Statement) this.visit(elt))
							   .collect(Collectors.toList())
							   );

	return new BlockStmt(statements);
    }

    @Override
    public Node visitVariableDeclarationStatement(SolidityParser.VariableDeclarationStatementContext ctx) {
	VariableDeclarator var = (VariableDeclarator) this.visit(ctx.variableDeclaration());

	if (ctx.variableDeclaration() != null) { // If it is a declaration of one variable...
	    if (ctx.expression() != null){
		Expression expr = (Expression) this.visit(ctx.expression());
		var.setInitializer(expr);
	    }

	    return new ExpressionStmt(new VariableDeclarationExpr(var));
	}
	else { // If it is a list of identifiers...
	    // TODO
	}

	return null;
    }

    @Override
    public Node visitExpressionStatement(SolidityParser.ExpressionStatementContext ctx) {
	return new ExpressionStmt((Expression) this.visit(ctx.expression()));
    }

    @Override
    public Node visitIfStatement(SolidityParser.IfStatementContext ctx) {
	Statement ifStmt, elseStmt;
	Expression expr = (Expression) this.visit(ctx.expression());
	
	ifStmt = (Statement) this.visit(ctx.statement(0));

	// Get else statement if it exists
	try {
	    elseStmt = (Statement) this.visit(ctx.statement(1));
	}
	catch (java.lang.NullPointerException e) {
	    elseStmt = null;
	}


	return new IfStmt(expr, ifStmt, elseStmt);
    }

    @Override
    public Node visitForStatement(SolidityParser.ForStatementContext ctx) {
	NodeList<Expression> initialization = new NodeList<>(), update = new NodeList<>();
	Expression compare = null;

	if (ctx.simpleStatement() != null) // Get the expression associated to the initialization stmt
	    initialization.add(((Statement) this.visit(ctx.simpleStatement())).asExpressionStmt().getExpression());
	if (ctx.expression(0) != null) // Get the comparison expression
	    compare = (Expression) this.visit(ctx.expression(0));
	if (ctx.expression(1) != null) // Get the update expression
	    update.add((Expression) this.visit(ctx.expression(1)));
	
	return new ForStmt(initialization, compare,  update,(Statement) this.visit(ctx.statement()));
    }

    @Override
    public Node visitWhileStatement(SolidityParser.WhileStatementContext ctx) {
	Expression condition = (Expression) this.visit(ctx.expression());
	Statement statement = (Statement) this.visit(ctx.statement());
	
	return new WhileStmt(condition, statement);
    }

    @Override
    public Node visitAssignmentExpression(SolidityParser.AssignmentExpressionContext ctx) {
	if (ctx.binop.getText().equals("=")) // TODO: implement more than assignement
	    return new AssignExpr((Expression) this.visit(ctx.expression(0)), (Expression) this.visit(ctx.expression(1)),  AssignExpr.Operator.ASSIGN);


	System.out.println("WARNING: visitAssignmentExpression returned nothing");
	
	return null; // This should never happen
    }

    
    @Override
    public Node visitVariableDeclaration(SolidityParser.VariableDeclarationContext ctx) {
	VariableDeclarator var = new VariableDeclarator((Type) this.visit(ctx.typeName()), (SimpleName) this.visit(ctx.identifier()));

	// If the type of var is not a primitive type, one needs to initialize the variable
	if (!var.getType().isPrimitiveType()) {
	    ClassOrInterfaceType type = new ClassOrInterfaceType(null, var.getType().toString());
	    ObjectCreationExpr expr = new ObjectCreationExpr(null, type, NodeList.nodeList());

	    var.setInitializer(expr);
	}

	return var;
    }

    @Override
    public Node visitParameter(SolidityParser.ParameterContext ctx) {
	// Get the name if there is one (for return parameters, there can be none)
	SimpleName id = ctx.identifier() != null ? (SimpleName) this.visit(ctx.identifier()) : new SimpleName();

	return new Parameter((Type) this.visit(ctx.typeName()), id);
    }
    
    @Override
    public Node visitIdentifier(SolidityParser.IdentifierContext ctx) {
	return new SimpleName(ctx.getText());
    }


    // Given a list of ContractPart, this function outputs a mapping from names to their types (enum, struct, function or modifier)
    private HashMap<String, String> getTypesMap(List<SolidityParser.ContractPartContext> contractParts) {
	HashMap<String, String> typesMap = new HashMap<>();
	
	contractParts.stream()
	    .forEach(elt -> {
		    if (elt.modifierDefinition() != null)
			typesMap.put(elt.modifierDefinition().identifier().getText(), "modifier");
		    else if (elt.functionDefinition() != null && elt.functionDefinition().identifier() != null)
			typesMap.put(elt.functionDefinition().identifier().getText(), "function");
		    else if (elt.structDefinition() != null)
			typesMap.put(elt.structDefinition().identifier().getText(), "struct");
		    else if (elt.enumDefinition() != null)
			typesMap.put(elt.enumDefinition().identifier().getText(), "enum");
		});

	return typesMap;
    }
}

// Another visitor that does the same thing as TranslateVisitor but maps
// some identifiers to other identifers (it allows to rename some variables)
class TranslateModifierVisitor extends TranslateVisitor {
    private HashMap<String, String> map;
    BlockStmt code;
    

    TranslateModifierVisitor(HashMap<String, String> map, BlockStmt code) {
	super();
	
	this.map = map;
	this.code = code;
    }

    @Override
    public Node visitIdentifier(SolidityParser.IdentifierContext ctx) {
	// Change the identifier if needed
	
	if (this.map.containsKey(ctx.getText()))
	    return new SimpleName(map.get(ctx.getText()));
	else
	    return new SimpleName(ctx.getText());
    }

    @Override
    public Node visitPlaceHolderStatement(SolidityParser.PlaceHolderStatementContext ctx) {
	return this.code;
    }
}

// Class to represent a user defined Solidity modifier.
class SolidityModifier {
    String name;
    ParserRuleContext code;
    ArrayList<String> parameters;

    SolidityModifier(String name, ParserRuleContext code, ArrayList<String> parameters) {
	this.name = name;
	this.code = code;
	this.parameters = parameters;
    }
}
