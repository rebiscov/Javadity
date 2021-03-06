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
import java.lang.RuntimeException;


/***********************/
/** TRANSLATE VISITOR **/
/***********************/

public class TranslateVisitor extends SolidityBaseVisitor<Node> {
    // Array containing all the necessary imports
    private static final String[] imports = {"blockchain.Block", "blockchain.Message", "blockchain.Transaction",
					     "blockchain.types.Address", "blockchain.types.Uint256", "blockchain.types.Uint256Int", "blockchain.types.Crypto"};

    // The string containing the name of the type Uint256
    private static final String UINT = Helper.UINT;

    // Name of the current contract (we need it to create the constructor of the contract)
    private String currentContractName;

    // Mapping that keeps track of the type of some identifiers
    private HashMap<String, String> typesMap;

    // List that stores the constructors of the structs
    private ArrayList<MethodDeclaration> structConstructors = new ArrayList<>();

    // Mapping from the name of a modifier to the Solidity AST of this modifier
    private HashMap<String, SolidityModifier> modifiersMap = new HashMap<>();
    
    @Override
    public Node visitSourceUnit(SolidityParser.SourceUnitContext ctx) {

	// Create import declarations (import classes to simulate Solidity behaviour like Uint256 or Address)
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

	// Method declaration for the constructor of the struct (here the constructor is a method of the contract that outputs a structure, it is not a method of the class representing the structure)
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

	// Create and define the struct object
	VariableDeclarator structCreation = new VariableDeclarator(t,
								   ret.toString(),
								   initializer);
	
	ReturnStmt returnStmt = new ReturnStmt(ret);
	
	body.add(new ExpressionStmt(new VariableDeclarationExpr(structCreation)));

	// Add the assignments of the elements of the struct
	parameters.stream()
	    .forEach(elt -> {
		    FieldAccessExpr field = new FieldAccessExpr(ret, elt.getName().asString());
		    NameExpr expr = new NameExpr(elt.getName());
		    AssignExpr assign = new AssignExpr(field, expr, AssignExpr.Operator.ASSIGN);
		    
		    body.add(new ExpressionStmt(assign));
		});

	// Add the returned statement returning the struct
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
	type.addMember(Helper.getSelfdestruct());
	type.addMember(Helper.getUpdateBlockchainVariables());

	Helper.getMagicVariables().stream()
	    .forEach(elt -> type.addMember(elt));
	
	contractPartList.stream()
	    .filter(elt -> elt.modifierDefinition() == null ) // Do not take the modifiers
	    .forEach(elt -> {
		    BodyDeclaration decl = ((BodyDeclaration) this.visit(elt));
		    
		    // If it is a public function, make it callable
		    if (decl.isMethodDeclaration() && decl.asMethodDeclaration().getModifiers().contains(Modifier.PUBLIC)) {
			MethodDeclaration method = decl.asMethodDeclaration();
			MethodDeclaration callable = Helper.getFunctionCallable(method);

			EnumSet<Modifier> modifiers = method.getModifiers();
			modifiers.remove(Modifier.PUBLIC);
			modifiers.add(Modifier.PRIVATE);
			
			method.setModifiers(modifiers);

			type.addMember(callable);
			type.addMember(method);
		    }
		    else
			type.addMember(decl);
		});

	// Add the struct constructor
	structConstructors.stream()
	    .forEach(elt -> type.addMember(elt));
	structConstructors.clear();

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
	    modifiers.add(Modifier.PRIVATE);


	// Identifier of the state variable
	String id = ((SimpleName) this.visit(ctx.identifier())).asString();
	

	// If it exists, get the expression that initializes the state variable
	Expression expr = null;
	if (ctx.expression() != null)
	    expr = (Expression) this.visit(ctx.expression());

	// If is is an array or a mapping, initializes it
	else if (type.isArrayType()) {
	    Type t = type.clone();

	    NodeList<ArrayCreationLevel> dimensions = new NodeList<>();

	    SolidityParser.TypeNameContext typeContext = ctx.typeName();

	    // If it is a mapping...
	    if (typeContext.mapping() != null) {
		NodeList<Expression> size = NodeList.nodeList(new IntegerLiteralExpr(Helper.mappingSize));
		dimensions.add(new ArrayCreationLevel(new ObjectCreationExpr(null, Helper.getUintType(), size)));;
		t = t.asArrayType().getComponentType();
	    }

	    // If it is an array
	    else {
		// Get the dimensions
		while (t.isArrayType()) {
		    Expression dimension = (Expression) this.visit(typeContext.expression());

		    dimensions.add(new ArrayCreationLevel(dimension));
		    t =  t.asArrayType().getComponentType();
		    typeContext = typeContext.typeName();
		}
	    }
	    expr = new ArrayCreationExpr(t, dimensions, null);
	}


	return new FieldDeclaration(modifiers, new VariableDeclarator(type, id, expr));
    }

    /* TYPE NAME */
    
    @Override
    public Node visitElementaryTypeName(SolidityParser.ElementaryTypeNameContext ctx) {

	// For now, we consider all unsigned integers to be uint256
	if (ctx.Uint() != null)
	    return Helper.getUintType();

	switch (ctx.getText()) {
	case "address":
	    return Helper.getAddressType();
	case "bool":
	    return PrimitiveType.booleanType();
	case "string":
	    return new ClassOrInterfaceType(null, "String");
	case "bytes32": // For now, bytes32 are implemented as Uint256
	    return Helper.getUintType();
	default:
	    throw new UnsupportedTypeException(ctx.getText());

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

    @Override
    public Node visitTypeName(SolidityParser.TypeNameContext ctx) {

	// If it is the type of an array
	if (ctx.typeName() != null) {
	    Type type = (Type) this.visit(ctx.typeName());

	    return new ArrayType(type, ArrayType.Origin.TYPE, new NodeList<AnnotationExpr>());
	}

	// If it is not an array...
	else
	    return visitChildren(ctx);
    }

    @Override
    public Node visitMapping(SolidityParser.MappingContext ctx) {
	// For now we only handle mappings from addresses to uint or from uint to uint
	Type key = (Type) this.visit(ctx.elementaryTypeName());
	Type value = (Type) this.visit(ctx.typeName());

	if (! (key.asString().equals(UINT) || key.asString().equals("Address")))
	    throw new UnsupportedMappingTypeException(key.asString());

	return new ArrayType(value, ArrayType.Origin.TYPE, new NodeList<AnnotationExpr>());
    }

    /* EXPRESSION */

    @Override
    public Node visitFunctionCallExpression(SolidityParser.FunctionCallExpressionContext ctx) {
	NodeList<Expression> arguments = new NodeList<>(); // List to store the arguments

	// Get the name of the method
	NameExpr method = (NameExpr) this.visit(ctx.expression());

	String[] methodNameParts = method.toString().split(Pattern.quote("."));
	int length = methodNameParts.length - 1;

	// The name values are not supported
	if (ctx.functionCallArguments().nameValueList() != null)
	    throw new UnsupportedSolidityFeatureException("namevalue");

	// Put all the arguments in the list (if there are some)

	try {
	    ctx.functionCallArguments().expressionList().expression().stream()
		.forEach(elt -> arguments.add((Expression) this.visit(elt)));
	}
	catch (java.lang.NullPointerException e) {}



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
    public Node visitArrayAccessExpression(SolidityParser.ArrayAccessExpressionContext ctx) {
	Expression array = (Expression) this.visit(ctx.expression(0));
	Expression index = (Expression) this.visit(ctx.expression(1));

	return new ArrayAccessExpr(array, index);
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
     public Node visitNotExpression(SolidityParser.NotExpressionContext ctx) {
	 Expression expr = (Expression) this.visit(ctx.expression());

	 return new UnaryExpr(expr, UnaryExpr.Operator.LOGICAL_COMPLEMENT);
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
	ClassOrInterfaceType type = Helper.getUintTypeIntImplem();

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

	NodeList<Expression> parameters = NodeList.nodeList(expr2);
	SimpleName op = new SimpleName("eq");

	MethodCallExpr eq = new MethodCallExpr(expr1, op, parameters);
	
	if (ctx.binop.getText().equals("!="))
	    return new UnaryExpr(eq, UnaryExpr.Operator.LOGICAL_COMPLEMENT);
	
	return eq;
    }

    @Override
    public Node visitParenExpression(SolidityParser.ParenExpressionContext ctx) {
	Expression expr = (Expression) this.visit(ctx.expression());

	return expr;
    }

    @Override
    public Node visitTernaryExpression(SolidityParser.TernaryExpressionContext ctx) {
	Expression condition = (Expression) this.visit(ctx.expression(0));
	Expression expr1 = (Expression) this.visit(ctx.expression(1));
	Expression expr2 = (Expression) this.visit(ctx.expression(2));

	return new ConditionalExpr(condition, expr1, expr2);
    }


    /* CONTRACT PARTS */

    @Override
    public Node visitConstructorDefinition (SolidityParser.ConstructorDefinitionContext ctx) {
	// Get the name of the contract to define the Java constructor
	String id = currentContractName;

	// Set modifiers
	EnumSet modifiers = EnumSet.of(Modifier.PUBLIC); // Default is public
	SolidityParser.ModifierListContext modList = ctx.modifierList();

	if (!modList.PrivateKeyword().isEmpty() || !modList.InternalKeyword().isEmpty())
	    modifiers = EnumSet.of(Modifier.PRIVATE);

	// Get the parameters
	List<SolidityParser.ParameterContext> solParameterList = ctx.parameterList().parameter();
	NodeList<Parameter> javaParameterList = new NodeList<>();
	solParameterList.stream()
	    .forEach(elt -> javaParameterList.add((Parameter) this.visit(elt)));

	// Create the constructor declaration
	ConstructorDeclaration method = new ConstructorDeclaration(modifiers, id);
	method.setParameters(javaParameterList);

	// Set the exceptions that can be thrown
	NodeList<ReferenceType> exceptions = NodeList.nodeList(new ClassOrInterfaceType(null, "Exception"));
	method.setThrownExceptions(exceptions);

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


	// Set block
	method.setBody(block);


	return method;
    }
    
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


	BlockStmt block = (BlockStmt) this.visit(ctx.block());

	// User defined modifiers

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

	// Payable modifier
	try {
	    NodeList<Statement> statements = block.getStatements();
	    ctx.modifierList().stateMutability().stream()
		.filter(elt -> elt.PayableKeyword() != null)
		.forEach(elt -> statements.addFirst(Helper.getCallToPayableModifier()));
	}
	catch (java.lang.NullPointerException e) {}

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


	// Create the declaration of the method
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


/* Definition of some Exceptions */

class UnsupportedTypeException extends RuntimeException {

    UnsupportedTypeException(String type) {
	super("The type " + type + " is not supported.");
    }
}

class UnsupportedMappingTypeException extends RuntimeException {

    UnsupportedMappingTypeException(String type) {
	super("The type " + type + " cannot be used as the key type in a mapping.");
    } 
}

class UnsupportedSolidityFeatureException extends RuntimeException {

    UnsupportedSolidityFeatureException(String feature) {
	super("The Solidity feature " + feature + " is not supported.");
    }
}
