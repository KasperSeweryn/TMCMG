package togos.noise.v3.parser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import togos.lang.BaseSourceLocation;
import togos.lang.ParseError;
import togos.lang.SourceLocation;
import togos.noise.v3.asyncstream.BaseStreamSource;
import togos.noise.v3.asyncstream.Collector;
import togos.noise.v3.asyncstream.StreamDestination;
import togos.noise.v3.asyncstream.StreamUtil;
import togos.noise.v3.parser.ast.ASTNode;
import togos.noise.v3.parser.ast.InfixNode;
import togos.noise.v3.parser.ast.ParenApplicationNode;
import togos.noise.v3.parser.ast.TextNode;
import togos.noise.v3.parser.ast.VoidNode;

public class Parser extends BaseStreamSource<ASTNode> implements StreamDestination<Token>
{
	protected static class SuperToken {
		enum Type {
			ATOM, PAREN_BLOCK
		};
		public final SuperToken parent;
		public final Type type;
		public final Token token;
		public final List<SuperToken> subTokens;
		public final SourceLocation sLoc;
		
		private SuperToken( SuperToken parent, Type type, Token token ) {
			this.parent = parent;
			this.type = type;
			this.token = token;
			this.subTokens = Collections.<SuperToken>emptyList();
			this.sLoc = token;
		}

		private SuperToken( SuperToken parent, Type type, SourceLocation sLoc ) {
			this.parent = parent;
			this.type = type;
			this.token = null;
			this.subTokens = new ArrayList<SuperToken>();
			this.sLoc = sLoc;
		}
		
		public String toString() {
			if( type == Type.ATOM ) return token.toString();
			
			String r = "(";
			boolean first = true;
			for( SuperToken st : subTokens ) {
				if( !first ) r += " ";
				r += st.toString();
				first = false;
			}
			r += ")";
			return r;
		}
	}
	
	public static String escape( String s ) {
		char[] buf = new char[s.length()*2];
		int i, j;
		for( i=0, j=0; i<s.length(); ++i ) {
			char c = s.charAt(i);
			switch( c ) {
			case '\r':
				buf[j++] = '\\';
				buf[j++] = 'r';
				break;
			case '\n':
				buf[j++] = '\\';
				buf[j++] = 'n';
				break;
			case '\t':
				buf[j++] = '\\';
				buf[j++] = 't';
				break;
			case '"': case '\\':
				buf[j++] = '\\';
			default:
				buf[j++] = c;
			}
		}
		return new String(buf,0,j);
	}
	
	public static String quote( String s ) {
		return "\"" + escape(s) + "\"";
	}
	
	/**
	 * This is (somewhat unfortunately)
	 * used by the binding system to identify bindings
	 * for purposes of ignoring duplicates.  Therefore
	 * it needs to be able to identify any constants that
	 * might end up being used in a program (ot the code
	 * that calls it should be changed not to).
	 *  
	 * @param value
	 * @return
	 */
	public static String toLiteral( Object value )  {
		if( value instanceof Number ) {
			return value.toString();
		} else if( value instanceof String ) {
			return quote((String)value);
		} else if( value instanceof Boolean ) {
			return value.toString();
		} else {
			throw new RuntimeException("Don't know how to convert "+value.getClass()+" to string");
		}
	}
	
	/**
	 * If true, will output after each semicolon in the top-level program.
	 * Otherwise, produces nothing until end() is called.
	 */
	final boolean eager;
	SuperToken block;
	SourceLocation currentLocation = new BaseSourceLocation("unknown source", 1, 1);
	
	public Parser( boolean eager ) {
		this.eager = eager;
	}
	
	public void setSourceLocation( SourceLocation loc ) {
		this.currentLocation = loc;
	}
	
	protected static class OperationReader {
		Map<String,Integer> operatorPrecedence = Operators.PRECEDENCE;
		List<SuperToken> tokens;
		public int offset;
		
		public OperationReader( List<SuperToken> tokens, int offset ) {
			this.tokens = tokens;
			this.offset = offset;
		}
		
		/**
		 * If there is a paren block at the current offset,
		 * parses it as an argument list and applies it to the passed-in function expression,
		 * then repeats for offset+1.  Returns the passed-in function expression if the
		 * next token is not a paren block or if the end of the token list has been reached.
		 */
		protected ASTNode readFunctionApplication( ASTNode functionExpression ) throws ParseError {
			if( offset == tokens.size() || tokens.get(offset).type != SuperToken.Type.PAREN_BLOCK ) return functionExpression;
			
			SuperToken argToken = tokens.get(offset++);
			return readFunctionApplication( new ParenApplicationNode(
				functionExpression,
				buildAstNode(argToken),
				argToken.sLoc )
			);
		}
		
		protected ASTNode readAtomic() throws ParseError {
			return readFunctionApplication( buildAstNode( tokens.get(offset++) ) );
		}
		
		/**
		 * Return true for tokens that should always be treated
		 * as infix operators, even if they're in a funny place. 
		 */
		protected boolean isInfixOnly(Token t) {
			return t.type == Token.Type.SYMBOL && ";".equals(t.text);
		}
		
		protected boolean isInfixOnly(SuperToken t) {
			return t.type == SuperToken.Type.ATOM && isInfixOnly(t.token);
		}

		
		public ASTNode read( int minPrecedence ) throws ParseError {
			assert offset <= tokens.size();
			
			ASTNode v;
			if( offset == tokens.size() || isInfixOnly(tokens.get(offset)) ) {
				v = new VoidNode(tokens.get(0).token);
			} else {
				v = readAtomic();
			}
			
			while( offset < tokens.size() ) {
				if( tokens.size() - offset == 1 ) {
					if( !isInfixOnly(tokens.get(offset)) ) {
						throw new ParseError("Infix operator '"+tokens.get(offset)+"' at end of block", tokens.get(offset).sLoc );
					}
				}
				
				Token operator = tokens.get(offset++).token;
				assert operator != null;
				Integer precedence = operatorPrecedence.get(operator.text);
				if( precedence == null ) {
					throw new ParseError("'"+operator.text+"' is not registered as an infix operator", operator);
				} else if( precedence < minPrecedence ) {
					--offset;
					return v;
				} else {
					v = new InfixNode(operator, v, read(precedence+1));
				}
			}
			return v;
		}
	}
	
	protected static ASTNode buildAstNode( SuperToken block ) throws ParseError {
		switch( block.type ) {
		case ATOM:
			return new TextNode( TextNode.Type.fromTokenType(block.token.type), block.token );
		case PAREN_BLOCK:
			if( block.subTokens.size() == 0 ) {
				return new VoidNode(block.sLoc);
			} else if( block.subTokens.size() == 1 ) {
				return buildAstNode( block.subTokens.get(0) );
			} else {
				return new OperationReader(block.subTokens, 0).read(0);
			}
		default:
			throw new RuntimeException("Invalid SuperToken type: "+block.type);
		}
	}
	
	protected void flushBlock() throws Exception {
		if( block == null ) return;
		assert block.parent == null;
		_data( buildAstNode(block) );
		block = null;
	}
	
	@Override public void data(Token value) throws Exception {
		setSourceLocation(value);
		if( block == null ) {
			block = new SuperToken(null, SuperToken.Type.PAREN_BLOCK, (SourceLocation)value );
		}
		if( "(".equals(value.text) ) {
			block = new SuperToken(block, SuperToken.Type.PAREN_BLOCK, (SourceLocation)value);
		} else if( ")".equals(value.text) ) {
			if( block.parent == null ) {
				throw new ParseError("Parentheses mismatch; unexpected ')'", value);
			}
			block.parent.subTokens.add(block);
			block = block.parent;
		} else if( eager && block.parent == null && ";".equals(value.text) ) {
			flushBlock();
		} else {
			block.subTokens.add( new SuperToken(block, SuperToken.Type.ATOM, value) );
		}
	}
	
	@Override public void end() throws Exception {
		if( block != null && block.parent != null ) {
			throw new ParseError("Unexpected end of file; expected ')'", currentLocation);
		}
		flushBlock();
		_end();
	}
	
	protected static ASTNode parse( char[] source, Reader reader, TokenizerSettings tSet ) throws Exception {
		ArrayList<ASTNode> astNodes = new ArrayList<ASTNode>();
		Parser parser = new Parser(false);
		parser.setSourceLocation( tSet );
		parser.pipe(new Collector<ASTNode>(astNodes));
		Tokenizer tokenizer = new Tokenizer(tSet);
		tokenizer.setSourceLocation( tSet );
		tokenizer.pipe(parser);
		
		if( source != null ) tokenizer.data( source );
		if( reader != null ) StreamUtil.pipe( reader, tokenizer );
		tokenizer.end();
		
		if( astNodes.size() == 0 ) {
			return new VoidNode(tSet);
		} else {
			assert astNodes.size() == 1;
			return astNodes.get(0);
		}
	}
	
	public static ASTNode parse( Reader reader, TokenizerSettings tSet ) throws Exception {
		return parse( null, reader, tSet );
	}
	
	public static ASTNode parse( String source, TokenizerSettings tSet ) throws Exception {
		return parse( source.toCharArray(), null, tSet );
	}
}
