import com.github.javaparser.ast.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.modules.*;

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.stream.*;

class Helper {
}

public class TranslateVisitor extends SolidityBaseVisitor<Node> {
    @Override
    public Node visitSourceUnit(SolidityParser.SourceUnitContext ctx) {
	NodeList contractsList = new NodeList();

	// Add all the contracts the list of contracts
	for (SolidityParser.ContractDefinitionContext e: ctx.contractDefinition())
	    contractsList.add(this.visit(e));

	// Add all the contracts to the compilation unit
	CompilationUnit cu = new CompilationUnit(null, new NodeList<ImportDeclaration>(), contractsList, null);

	return cu;
    }

    @Override
    public Node visitStructDefinition(SolidityParser.StructDefinitionContext ctx) {
	ClassOrInterfaceDeclaration type = new ClassOrInterfaceDeclaration(EnumSet.of(Modifier.PUBLIC), false, ((SimpleName) this.visit(ctx.identifier())).asString());

	List<SolidityParser.VariableDeclarationContext> fieldList = ctx.variableDeclaration();
	
	// Add the variables
	fieldList.stream()
	    .forEach(elt -> type.addMember(new FieldDeclaration(EnumSet.of(Modifier.PUBLIC) ,(VariableDeclarator) this.visit(elt))));

	return type;
    }

    @Override
    public Node visitEnumDefinition(SolidityParser.EnumDefinitionContext ctx) {
	EnumSet<Modifier> modifiers = EnumSet.of(Modifier.PUBLIC);
	modifiers.add(Modifier.STATIC);
	
	ClassOrInterfaceDeclaration type = new ClassOrInterfaceDeclaration(modifiers, false, ((SimpleName) this.visit(ctx.identifier())).asString());

	List<SolidityParser.EnumValueContext> enumValues = ctx.enumValue();
	List<String> IDs = enumValues.stream()
	    .map(elt -> elt.getText())
	    .collect(Collectors.toList());

	Type t = PrimitiveType.intType();
	NodeList<VariableDeclarator> values = new NodeList<>();
	
	for (int i = 0; i < IDs.size(); i ++) {
	    IntegerLiteralExpr expr = new IntegerLiteralExpr(i);
	    VariableDeclarator var = new VariableDeclarator(t, IDs.get(i), expr);
	    values.add(var);
	}
	type.addMember(new FieldDeclaration(modifiers, values));

	return type;
    }

    @Override
    public Node visitContractDefinition(SolidityParser.ContractDefinitionContext ctx) {
	// Identifier
	String id = ((SimpleName)this.visit(ctx.identifier())).asString();
	
	// Create a new class
	ClassOrInterfaceDeclaration type = new ClassOrInterfaceDeclaration(EnumSet.of(Modifier.PUBLIC), false, id);

	// Add the parent classes to the class
	ctx.inheritanceSpecifier().stream()
	    .forEach(elt -> type.addExtendedType(elt.userDefinedTypeName().getText()));

	List<SolidityParser.ContractPartContext>  contractPartList = ctx.contractPart();

	// Add the members
	contractPartList.stream()
	    .forEach(elt -> type.addMember((BodyDeclaration) this.visit(elt)));


	return type;
    }

    @Override
    public Node visitStateVariableDeclaration(SolidityParser.StateVariableDeclarationContext ctx) {

	// Type
	Type type = (Type) this.visit(ctx.typeName());
	
	// Modifier
	EnumSet modifiers = EnumSet.noneOf(Modifier.class);
	if (!ctx.PublicKeyword().isEmpty())
	    modifiers.add(Modifier.PUBLIC);
	if (!ctx.PrivateKeyword().isEmpty() || !ctx.InternalKeyword().isEmpty())
	    modifiers.add(Modifier.PUBLIC);


	// Identifier
	String id = ((SimpleName) this.visit(ctx.identifier())).asString();
	

	// Expression
	Expression expr;
	try {
	    expr = (Expression) this.visit(ctx.expression());
	}
	catch (java.lang.NullPointerException e){
	    expr = null;
	}
	
	
	return new FieldDeclaration(modifiers, new VariableDeclarator(type, id, expr));
    }

    @Override
    public Node visitElementaryTypeName(SolidityParser.ElementaryTypeNameContext ctx) {
	// RE-DO THIS PLEASE!

	if (ctx.Int() != null || ctx.Uint() != null)
	    return new ClassOrInterfaceType(null, "Uint256");
	
	switch (ctx.getText()) {
	case "address":
	    return new ClassOrInterfaceType(null, "Address");
	case "bool":
	    return PrimitiveType.booleanType();
	case "string":
	    return new ClassOrInterfaceType(null, "String");
	default:
	    return new ClassOrInterfaceType(null, "Uint256");

	}
    }

    @Override
    public Node visitAdditiveExpression(SolidityParser.AdditiveExpressionContext ctx) {
	Expression expr1 = (Expression) this.visit(ctx.expression(0));
	Expression expr2 = (Expression) this.visit(ctx.expression(1));

	if (ctx.binop.getType() == SolidityParser.PLUS)
	    return new BinaryExpr(expr1, expr2, BinaryExpr.Operator.PLUS);
	else
	    return new BinaryExpr(expr1, expr2, BinaryExpr.Operator.MINUS);
    }

    @Override
    public Node visitMultiplicativeExpression(SolidityParser.MultiplicativeExpressionContext ctx) {
	Expression expr1 = (Expression) this.visit(ctx.expression(0));
	Expression expr2 = (Expression) this.visit(ctx.expression(1));

	if (ctx.binop.getType() == SolidityParser.MULT)
	    return new BinaryExpr(expr1, expr2, BinaryExpr.Operator.MULTIPLY);
	else if (ctx.binop.getType() == SolidityParser.DIV)
	    return new BinaryExpr(expr1, expr2, BinaryExpr.Operator.DIVIDE);
	else
	    return new BinaryExpr(expr1, expr2, BinaryExpr.Operator.REMAINDER);
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
    public Node visitNumberLiteral(SolidityParser.NumberLiteralContext ctx) {
	// We treat only integers TODO: use NumberUnit and HexNumber (see grammar)
	return new IntegerLiteralExpr(ctx.DecimalNumber().getText());
    }

    @Override
    public Node visitPrimaryExpression(SolidityParser.PrimaryExpressionContext ctx) {
	// TODO: implement more than booleans, integers and literals
	if (ctx.BooleanLiteral() != null) 
	    return new BooleanLiteralExpr(Boolean.parseBoolean(ctx.BooleanLiteral().getText()));
	else if (ctx.identifier() != null)
	    return new NameExpr((SimpleName) this.visit(ctx.identifier()));
	
	return this.visitChildren(ctx);
    }

    @Override
    public Node visitFunctionDefinition(SolidityParser.FunctionDefinitionContext ctx) {
	// Identifier
	String id;
	if (ctx.identifier() != null)
	    id = ((SimpleName) this.visit(ctx.identifier())).asString();
	else
	    id = "fallback";

	// Modifiers TODO: implement all modifiers
	EnumSet modifiers = EnumSet.of(Modifier.PUBLIC); // Default if public
	SolidityParser.ModifierListContext modList = ctx.modifierList();

	if (!modList.PrivateKeyword().isEmpty() || !modList.InternalKeyword().isEmpty())
	    modifiers = EnumSet.of(Modifier.PRIVATE);
	    

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
	
	// Get block
	BlockStmt block = (BlockStmt) this.visit(ctx.block());
	method.setBody(block);
	
	
	return method;
    }

    @Override
    public Node visitBlock(SolidityParser.BlockContext ctx) {
	NodeList<Statement> statements = new NodeList<>();
	ctx.statement().stream()
	    .forEach(elt -> statements.add((Statement) this.visit(elt)));
	
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
	return new WhileStmt((Expression) this.visit(ctx.expression()), (Statement) this.visit(ctx.statement()));
    }

    @Override
    public Node visitAssignmentExpression(SolidityParser.AssignmentExpressionContext ctx) {
	if (ctx.binop.getText().equals("=")) // TODO: implement more than assignement
	    return new AssignExpr((Expression) this.visit(ctx.expression(0)), (Expression) this.visit(ctx.expression(1)),  AssignExpr.Operator.ASSIGN);


	System.out.println("WARNING: visitAssignmentExpression returned nothing");
	
	return null; // This should never happen
    }

    @Override
    public Node visitCompExpression(SolidityParser.CompExpressionContext ctx) {
	Expression expr1 = (Expression) this.visit(ctx.expression(0));
	Expression expr2 = (Expression) this.visit(ctx.expression(1));
	
	if (ctx.binop.getText().equals("<"))
	    return new BinaryExpr(expr1, expr2, BinaryExpr.Operator.LESS);
	else if (ctx.binop.getText().equals(">"))
	    return new BinaryExpr(expr1, expr2, BinaryExpr.Operator.GREATER);
	else if (ctx.binop.getText().equals("<="))
	    return new BinaryExpr(expr1, expr2, BinaryExpr.Operator.LESS_EQUALS);
	else if (ctx.binop.getText().equals(">="))
	    return new BinaryExpr(expr1, expr2, BinaryExpr.Operator.GREATER_EQUALS);

	System.out.println("WARNING: visitCompExpression returned nothing");
	
	return null; // This should never happen
    }
    
    @Override
    public Node visitVariableDeclaration(SolidityParser.VariableDeclarationContext ctx) {
	return new VariableDeclarator((Type) this.visit(ctx.typeName()), (SimpleName) this.visit(ctx.identifier()));
    }

    @Override
    public Node visitParameter(SolidityParser.ParameterContext ctx) {
	// Get the name if there is one
	SimpleName id = ctx.identifier() != null ? (SimpleName) this.visit(ctx.identifier()) : new SimpleName();

	return new Parameter((Type) this.visit(ctx.typeName()), id);
    }
    
    @Override
    public Node visitIdentifier(SolidityParser.IdentifierContext ctx) {
	return new SimpleName(ctx.getText());
    }
    
}
