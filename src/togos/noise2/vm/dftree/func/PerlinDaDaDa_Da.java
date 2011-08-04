package togos.noise2.vm.dftree.func;

import togos.noise2.vm.dftree.data.DataDa;
import togos.noise2.vm.dftree.data.DataDaDaDa;

public class PerlinDaDaDa_Da extends ThreeArgDaDaDa_Da
{
	public PerlinDaDaDa_Da( FunctionDaDaDa_Da inX, FunctionDaDaDa_Da inY, FunctionDaDaDa_Da inZ ) {
		super( inX, inY, inZ );
	}
	
	public PerlinDaDaDa_Da() {
		this(X.instance, Y.instance, Z.instance);
	}
	
	public String getMacroName() {  return "perlin";  }
	
	public DataDa apply( DataDaDaDa in ) {
		double[] out = new double[in.getLength()];
		D5_2Perlin.instance.apply( in.getLength(), inX.apply(in).x, inY.apply(in).x, inZ.apply(in).x, out );
		return new DataDa(out);
	}
}