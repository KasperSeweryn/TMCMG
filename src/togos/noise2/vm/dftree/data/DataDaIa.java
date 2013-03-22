package togos.noise2.vm.dftree.data;

public class DataDaIa extends DataArray
{
	public final double[] d;
	public final int[] i;
	
	public DataDaIa( int length, double[] d, int[] i ) {
		super( length );
		this.d = d;
		this.i = i;
	}
}
