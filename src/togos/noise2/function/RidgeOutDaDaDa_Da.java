package togos.noise2.function;

import togos.noise2.data.DataDa;
import togos.noise2.data.DataDaDaDa;
import togos.noise2.lang.Expression;
import togos.noise2.rewrite.ExpressionRewriter;

public class RidgeOutDaDaDa_Da extends TNLFunctionDaDaDa_Da
{
	TNLFunctionDaDaDa_Da lower;
	TNLFunctionDaDaDa_Da upper;
	TNLFunctionDaDaDa_Da ridged;
	
	public RidgeOutDaDaDa_Da( TNLFunctionDaDaDa_Da lower, TNLFunctionDaDaDa_Da upper, TNLFunctionDaDaDa_Da ridged ) {
		this.lower = lower;
		this.upper = upper;
		this.ridged = ridged;
	}
	
	public DataDa apply( DataDaDaDa in ) {
		double[] lower = this.lower.apply(in).v;
		double[] upper = this.upper.apply(in).v;
		double[] ridged = this.ridged.apply(in).v;
		double[] out = new double[in.getLength()];
		for( int i=in.getLength()-1; i>=0; --i ) {
			double d = upper[i]-lower[i];

			// TODO: I'm guessing there's a better way to do this
			if( d == 0 ) {
				out[i] = lower[i];
			} else {
				/*
				// coorect but presumably slow:
				while( out[i] > upper[i] || out[i] < lower[i] ) {
					if( out[i] > upper[i] ) {
						out[i] = upper[i]-(out[i]-upper[i]);
					}
					if( out[i] < lower[i] ) {
						out[i] = lower[i]+(lower[i]-out[i]);
					}
				}
				*/
				
				double k = (ridged[i]-lower[i])/(d*2);
				double c = Math.floor(k);
				k -= c;
				out[i] = lower[i] + d*2*(k - 2*Math.floor(2*k)*(k-0.5));
			}
		}
		return new DataDa(out);
	}
	
	public boolean isConstant() {
		return lower.isConstant() && upper.isConstant() && ridged.isConstant();
	}
	
	public Object rewriteSubExpressions(ExpressionRewriter rw) {
		return new RidgeOutDaDaDa_Da(
			(TNLFunctionDaDaDa_Da)rw.rewrite(lower),
			(TNLFunctionDaDaDa_Da)rw.rewrite(upper),
			(TNLFunctionDaDaDa_Da)rw.rewrite(ridged)
		);
	}
	
	public Expression[] directSubExpressions() {
		return new Expression[]{ upper, lower, ridged };
	}
	
	public String toTnl() {
		return "ridge("+lower.toTnl()+", "+upper.toTnl()+", "+ridged.toTnl()+")";
	}
}
