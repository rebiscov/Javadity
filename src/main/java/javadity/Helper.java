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

// The Helper class has mostly static methods that outputs often used Java code (for example the Uint256 type, or the require function)

public class Helper {
    public static final String UINT = "Uint256";
    public static final int mappingSize = 50;

    public static ClassOrInterfaceType getUintType() {
	return new ClassOrInterfaceType(null, UINT);
    }

    public static ClassOrInterfaceType getUintTypeIntImplem() {
	return new ClassOrInterfaceType(null, "Uint256Int");
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

    public static ExpressionStmt getCallToPayableModifier() {
	NodeList<Expression> arguments = new NodeList<>();
	arguments.add(new NameExpr("msg"));
	MethodCallExpr payable = new MethodCallExpr(null, "payable", arguments);

	return new ExpressionStmt(payable);
    }

    public static MethodDeclaration getUpdateBlockchainVariables() {
	EnumSet<Modifier> modifiers = EnumSet.of(Modifier.PUBLIC);
	NodeList<Parameter> parameters = new NodeList<>();

	parameters.add(new Parameter(getMessageType(), "_msg"));
	parameters.add(new Parameter(getBlockType(), "_block"));
	parameters.add(new Parameter(getTransactionType(), "_tx"));

	MethodDeclaration updateBlockchainVars = new MethodDeclaration(modifiers, "updateBlockchainVariables", new VoidType(), parameters);

	NodeList<Statement> statements = new NodeList<>();

	AssignExpr assign1 = new AssignExpr(new NameExpr("msg"), new NameExpr("_msg"), AssignExpr.Operator.ASSIGN);
	AssignExpr assign2 = new AssignExpr(new NameExpr("block"), new NameExpr("_block"), AssignExpr.Operator.ASSIGN);
	AssignExpr assign3 = new AssignExpr(new NameExpr("tx"), new NameExpr("_tx"), AssignExpr.Operator.ASSIGN);
	
	statements.add(new ExpressionStmt(assign1));
	statements.add(new ExpressionStmt(assign2));
	statements.add(new ExpressionStmt(assign3));

	updateBlockchainVars.setBody(new BlockStmt(statements));

	return updateBlockchainVars;
    }

    // Returns the function that can be called from outside (it creates a new message, see Translation_details.md)
    public static MethodDeclaration getFunctionCallable(MethodDeclaration method) {
	EnumSet<Modifier> modifiers = EnumSet.of(Modifier.PUBLIC);
	NodeList<Parameter> parameters = new NodeList<>(method.getParameters());

	Parameter msg = new Parameter(getMessageType(), "_msg");
	Parameter block = new Parameter(getBlockType(), "_block");
	Parameter tx = new Parameter(getTransactionType(), "_tx");

	parameters.add(msg);
	parameters.add(block);
	parameters.add(tx);	


	MethodDeclaration callable = new MethodDeclaration(modifiers, "call_" + method.getName().asString(), method.getType(), parameters);

	NodeList<Expression> arguments = new NodeList<>();
	arguments.add(new NameExpr(msg.getName()));
	arguments.add(new NameExpr(block.getName()));
	arguments.add(new NameExpr(tx.getName()));

	// updateBlockchainVariables
	NodeList<Statement> statements = new NodeList<>();
	statements.add(new ExpressionStmt(new MethodCallExpr(null, "updateBlockchainVariables", new NodeList<Expression>(arguments))));

	// method call
	arguments.clear();

	method.getParameters()
	    .forEach(elt ->
		     arguments.add(new NameExpr(elt.getName())));

	Statement call = null;

	if (method.getType().asString().equals("void"))
	    call = new ExpressionStmt(new MethodCallExpr(null, method.getName().asString(), arguments));
	else
	    call = new ReturnStmt(new MethodCallExpr(null, method.getName().asString(), arguments));

	// TryCatch
	BlockStmt tryBlock = new BlockStmt(NodeList.nodeList(call));

	ExpressionStmt printExc = new ExpressionStmt(new MethodCallExpr(new NameExpr("System.out"), "println", NodeList.nodeList(new NameExpr("e"))));
	BlockStmt catchBlock = new BlockStmt(NodeList.nodeList(printExc));
	CatchClause catchClause = new CatchClause(new Parameter(new ClassOrInterfaceType(null, "Exception"), "e"), catchBlock);

	TryStmt tryStmt = new TryStmt(tryBlock, NodeList.nodeList(catchClause), null);

	statements.add(tryStmt);
	if (!method.getType().asString().equals("void"))
	    statements.add(new ReturnStmt(new NullLiteralExpr()));

	callable.setBody(new BlockStmt(statements));

	return callable;
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
	IfStmt ifStatement = new IfStmt(new UnaryExpr(b, UnaryExpr.Operator.LOGICAL_COMPLEMENT), throwException, null);

	require.setBody(new BlockStmt(NodeList.nodeList(ifStatement)));
	
	return require;
    }

    public static MethodDeclaration getKeccak() {
	EnumSet<Modifier> modifiers = EnumSet.of(Modifier.PUBLIC);
	NameExpr x = new NameExpr("x");
	NodeList<Parameter> parameters = NodeList.nodeList(new Parameter(getUintType(), x.toString()));

	MethodCallExpr keccak = new MethodCallExpr(x, "keccak256");
	ReturnStmt ret = new ReturnStmt(keccak); // TODO: finish implementation of keccak

	MethodDeclaration method = new MethodDeclaration(modifiers, "keccak256", new ClassOrInterfaceType(null, "Uint256"), parameters); // Need to come up with a proper fix
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
