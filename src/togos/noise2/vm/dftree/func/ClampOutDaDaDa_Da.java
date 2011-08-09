package togos.noise2.vm.dftree.func;

import togos.noise2.rewrite.ExpressionRewriter;
import togos.noise2.vm.dftree.data.DataDa;
import togos.noise2.vm.dftree.data.DataDaDaDa;
import togos.noise2.vm.dftree.lang.FunctionUtil;

public class ClampOutDaDaDa_Da extends TNLFunctionDaDaDa_Da
{
	FunctionDaDaDa_Da lower;
	FunctionDaDaDa_Da upper;
	FunctionDaDaDa_Da clamped;
	
	public ClampOutDaDaDa_Da( FunctionDaDaDa_Da lower, FunctionDaDaDa_Da upper, FunctionDaDaDa_Da clamped ) {
		this.lower = lower;
		this.upper = upper;
		this.clamped = clamped;
	}
	
	public DataDa apply( DataDaDaDa in ) {
		final int len = in.getLength();
		double[] lower = this.lower.apply(in).x;
		double[] upper = this.upper.apply(in).x;
		double[] clamped = this.clamped.apply(in).x;
		double[] out = new double[len];
		for( int i=in.getLength()-1; i>=0; --i ) {
			if( clamped[i] < lower[i] ) out[i] = lower[i];
			else if( clamped[i] > upper[i] ) out[i] = upper[i];
			else out[i] = clamped[i];
		}
		return new DataDa(len,out);
	}
	
	public boolean isConstant() {
		return FunctionUtil.isConstant(lower) && FunctionUtil.isConstant(upper) && FunctionUtil.isConstant(clamped);
	}
	
	public Object rewriteSubExpressions(ExpressionRewriter rw) {
		return new ClampOutDaDaDa_Da(
			(TNLFunctionDaDaDa_Da)rw.rewrite(lower),
			(TNLFunctionDaDaDa_Da)rw.rewrite(upper),
			(TNLFunctionDaDaDa_Da)rw.rewrite(clamped)
		);
	}
	
	public String toString() {
		return "clamp("+lower+", "+upper+", "+clamped+")";
	}
	
	public String toTnl() {
		return "clamp("+FunctionUtil.toTnl(lower)+", "+FunctionUtil.toTnl(upper)+", "+FunctionUtil.toTnl(clamped)+")";
	}
	
	public Object[] directSubExpressions() {
		return new Object[]{ lower, upper, clamped };
	}
}
