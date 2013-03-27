package togos.noise.v3.parser;

import togos.noise.v1.lang.BaseSourceLocation;
import togos.noise.v3.parser.ast.ASTNode;
import togos.noise.v3.parser.ast.InfixNode;
import togos.noise.v3.parser.ast.SymbolNode;
import togos.noise.v3.parser.ast.VoidNode;

public class ParserTest extends CoolTestCase
{
	BaseSourceLocation TEST_LOC = new BaseSourceLocation("test script", 1, 1);
	
	public void testVoid() throws Exception {
		ASTNode n = Parser.parse("", TEST_LOC);
		assertInstanceOf( VoidNode.class, n );
	}
	
	public void testSymbol() throws Exception {
		ASTNode n = Parser.parse("hello", TEST_LOC);
		assertInstanceOf( SymbolNode.class, n );
		assertEquals( "hello", ((SymbolNode)n).text );
	}
	
	protected void assertStringification( String expected, String input ) throws Exception {
		assertEquals( expected, Parser.parse(input, TEST_LOC).toString() );
	}
	
	protected void assertStringification( String inputAndExpected ) throws Exception {
		assertStringification( inputAndExpected, inputAndExpected );
	}
	
	//// Argument lists ////
	
	public void testFunctionWithArgument() throws Exception {
		assertStringification( "foo(bar)", "foo(bar)" );
	}
	
	public void testFunctionWithArguments() throws Exception {
		assertStringification( "foo(bar, baz)", "foo(bar,baz)" );
	}
	
	//// Infix operators ////
	
	public void testOperator() throws Exception {
		ASTNode n = Parser.parse("1 + 2", TEST_LOC);
		assertEquals( "1 + 2", n.toString() );
		
		assertInstanceOf( InfixNode.class, n );
		assertEquals("+", ((InfixNode)n).operator );
		assertInstanceOf( SymbolNode.class, ((InfixNode)n).n1 );
		assertEquals("1", ((SymbolNode)((InfixNode)n).n1).text );
		assertInstanceOf( SymbolNode.class, ((InfixNode)n).n2 );
		assertEquals("2", ((SymbolNode)((InfixNode)n).n2).text );
	}
	
	public void testSamePrecedenceOperators() throws Exception {
		ASTNode n = Parser.parse("1 + 2 - 3 + 4", TEST_LOC); // (((1 + 2) - 3) + 4)
		assertEquals( "((1 + 2) - 3) + 4", n.toString() );
		
		assertInstanceOf( InfixNode.class, n );
		assertEquals("+", ((InfixNode)n).operator );
		assertInstanceOf( SymbolNode.class, ((InfixNode)n).n2 );
		ASTNode o = ((InfixNode)n).n1;
		assertInstanceOf( InfixNode.class, o );
		// Blah blah blah
	}
		
	public void testMoreInfixOperators() throws Exception {
		assertStringification("(1 * 2) + (3 % 4)", "1 * 2 + 3 % 4");
		assertStringification("(1 + 2) * (3 - 4)");
	}
	
	//// Complex expressions ////
	
	public void testLambda() throws Exception {
		assertStringification( "(a, b, c) -> (a * (b + c))" );
	}
}
