package togos.minecraft.mapgen.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Transparency;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;

import togos.lang.ScriptError;
import togos.minecraft.mapgen.LFunctionDaDa_Da_Ia;
import togos.minecraft.mapgen.ScriptUtil;
import togos.minecraft.mapgen.util.FileUpdateListener;
import togos.minecraft.mapgen.util.FileWatcher;
import togos.minecraft.mapgen.util.GeneratorUpdateListener;
import togos.minecraft.mapgen.util.ServiceManager;
import togos.minecraft.mapgen.world.gen.LayeredTerrainFunction;
import togos.minecraft.mapgen.world.gen.LayeredTerrainFunction.LayerBuffer;
import togos.minecraft.mapgen.world.gen.LayeredTerrainFunction.TerrainBuffer;
import togos.minecraft.mapgen.world.gen.MinecraftWorldGenerator;
import togos.minecraft.mapgen.world.structure.ChunkData;
import togos.noise.v3.parser.ParseUtil;
import togos.noise.v3.parser.TokenizerSettings;
import togos.noise.v3.vector.function.LFunctionIa_Ia;
import togos.service.Service;

public class ColumnSideCanvas extends WorldExplorerViewCanvas
{
    private static final long serialVersionUID = 1L;
	
    public int getSkyColor() { return 0xFF00AAFF; }
	public final int worldFloor = 0, worldCeiling = ChunkData.NORMAL_CHUNK_HEIGHT;
    
	class ColumnSideRenderer implements Runnable, Service {
		final LFunctionDaDa_Da_Ia cFunc;
		int width, height;
		double worldX, worldZ, worldXPerPixel;
		
		public volatile BufferedImage buffer;
		protected volatile boolean stop = false;		
		
		public ColumnSideRenderer( LFunctionDaDa_Da_Ia cFunc, int width, int height,
			double worldX, double worldZ, double worldXPerPixel
		) {
			assert cFunc != null;
			
			this.cFunc = cFunc;
			this.width = width;
			this.height = height;
			this.worldX = worldX;
			this.worldZ = worldZ;
			this.worldXPerPixel = worldXPerPixel;
		}
		
		protected BufferedImage createBuffer() {
			GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			GraphicsDevice gs = ge.getDefaultScreenDevice();
			GraphicsConfiguration gc = gs.getDefaultConfiguration();
			
			// Create an image that does not support transparency
			return gc.createCompatibleImage(width, height, Transparency.OPAQUE);
		}
		
		public void run() {
			if( width == 0 || height == 0 ) return;
			
			buffer = createBuffer();
			Graphics g = buffer.getGraphics();
			
			final int sectWidth = 32; 
			
			double[] wx = new double[sectWidth];
			double[] wy = new double[height];
			double[] wz = new double[sectWidth];
			int[] colors = new int[sectWidth*height]; // ordered y, xz
			
			synchronized( buffer ) {
				g.setColor( Color.BLACK );
				g.fillRect(0,0,width,height);
			}
			
			for( int i=0; i<height; ++i ) {
				wy[i] = i;
			}
			
			for( int sx=0; sx<width && !stop; sx+=sectWidth ) {
				int sw = width - sx;
				if( sw > sectWidth ) sw = sectWidth;
				for( int i=0; i<sw; ++i ) {
					wx[i] = worldX + (sx+i)*worldXPerPixel;
					wz[i] = worldZ;
				}
				cFunc.apply( sw, wx, wz, height, wy, colors );
				
				synchronized( buffer ) {
					for( int i=0; i<sw; ++i ) {
						// Draw it upside-down!
						buffer.setRGB(sx+i, 0, 1, height, colors, (i+1)*height-1, -1);
					}
				}
				repaint();
			}
		}
		
		public void halt() {
			this.stop = true;
		}
		
		public void start() {
			new Thread(this).start();
		}
	}
	
	public static class ColumnTerrainFunction implements LFunctionDaDa_Da_Ia
	{
		protected final LayeredTerrainFunction ltf;
		
		class Buf {
			public final int colSize;
			public LayeredTerrainFunction.TerrainBuffer terrainBuffer;
			public final double[] colX, colY, colZ;
			public final int[] colData;
			
			public Buf( int colSize ) {
				this.colSize = colSize;
				this.colX = new double[colSize];
				this.colY = new double[colSize];
				this.colZ = new double[colSize];
				this.colData = new int[colSize];
			}
		}
		
		ThreadLocal<Buf> bufVar = new ThreadLocal<Buf>();
		
		final int defaultValue;
		
		private Buf getBuf(int colSize) {
			Buf buf = bufVar.get();
			if( buf == null || buf.colSize < colSize ) {
				bufVar.set(buf = new Buf(colSize));
			}
			return buf;
		}
		
		public ColumnTerrainFunction( LayeredTerrainFunction ltf, int defaultValue ) {
			this.ltf = ltf;
			this.defaultValue = defaultValue;
		}
		
		@Override
        public void apply( int xzCount, double[] x, double[] z, int yCount, double[] y, int[] data ) {
			assert xzCount >= 0;
			assert yCount >= 0;
			
			final Buf buf = getBuf( yCount );
			final TerrainBuffer tBuf = (buf.terrainBuffer = ltf.apply( xzCount, x, z, buf.terrainBuffer ));
			
			for( int i=xzCount*yCount-1; i>=0; --i ) {
				data[i] = defaultValue;
			}
			
			for( int l=0; l<tBuf.layerCount; ++l ) {
				LayerBuffer layer = tBuf.layerData[l];
				for( int i=xzCount-1; i>=0; --i ) {
					int rFloor = (int)Math.round(layer.floorHeight[i]);
					int rCeil  = (int)Math.round(layer.ceilingHeight[i]);
					if( rFloor < 0 ) rFloor = 0;
					if( rCeil > yCount ) rCeil = yCount;
					int layerHeight = rCeil - rFloor;
					for( int j=0; j<layerHeight; ++j ) {
						buf.colX[j] = x[i];
						buf.colY[j] = rFloor + j;
						buf.colZ[j] = z[i];
					}
					if( layerHeight > 0 ) {
						// More importantly >= 0.  But as long as we're checking, skip zero-height columns.
						layer.materialFunction.apply( layerHeight, buf.colX, buf.colY, buf.colZ, buf.colData);
						for( int j=0, h=rFloor; h<rCeil; ++h, ++j ) {
							if( buf.colData[j] != -1 ) data[i*yCount+h] = buf.colData[j];
						}
					}
				}
			}
        }
	}
	
	public static class ColumnColorFunction implements LFunctionDaDa_Da_Ia
	{
		protected final LFunctionDaDa_Da_Ia materialFunction;
		protected final LFunctionIa_Ia colorMap;
		
		ColumnColorFunction( LFunctionDaDa_Da_Ia materialFunction, LFunctionIa_Ia colorMap ) {
			this.materialFunction = materialFunction;
			this.colorMap = colorMap;
		}
		
		@Override public void apply( int xzCount, double[] x, double[] z, int yCount, double[] y, int[] color ) {
			materialFunction.apply( xzCount, x, z, yCount, y, color );
			colorMap.apply( xzCount*yCount, color, color );
        }
	}
	
	protected LFunctionDaDa_Da_Ia cFunc;
	ColumnSideRenderer cnr;
	
	public ColumnSideCanvas() {
		super();
    }
	
	protected void stateUpdated() {
		double mpp = 1/zoom;
		double leftX = wx-mpp*getWidth()/2;
		stopRenderer();
		
		if( wg == null ) {
			cFunc = null;
		} else {
			cFunc = new ColumnColorFunction( new ColumnTerrainFunction(wg.getTerrainFunction(), -1), colorMap );
		}
		
		if( cFunc != null ) {
			startRenderer(new ColumnSideRenderer(cFunc,getWidth(),getHeight(),leftX,wy,mpp));
		}
	}
	
	
	
	public void update(Graphics g) {
		paint(g);
	}
	
	/*
	 * If this is crashing, run with VM options:
	 * -Dsun.java2d.d3d=false -Dsun.java2d.noddraw=true
	 */
	public void paint(Graphics g) {
		BufferedImage buf;
		ColumnSideRenderer nr = cnr;
		if( nr != null && (buf = nr.buffer) != null ) {
			// Not sure if locking is really needed here...
			synchronized( buf ) {
				g.drawImage(buf, 0, 0, null);
			}
		} else {
			g.setColor(Color.BLACK);
			g.fillRect(0, 0, getWidth(), getHeight());
		}
		paintOverlays(g);
	}
	
	public void stopRenderer() {
		if( cnr != null ) {
			cnr.halt();
			cnr = null;
		}
	}
	
	public void startRenderer( ColumnSideRenderer nr ) {
		this.stopRenderer();
		this.cnr = nr;
		this.cnr.start();
	}
	
	public static void main( String[] args ) {
		String scriptFilename = null;
		boolean autoReload = false;
		int tnlTabWidth = TokenizerSettings.DEFAULT_TAB_WIDTH;
		for( int i=0; i<args.length; ++i ) {
			if( "-auto-reload".equals(args[i]) ) {
				autoReload = true;
			} else if( "-tab-width".equals(args[i]) ) {
				tnlTabWidth = Integer.parseInt(args[++i]);
			} else if( !args[i].startsWith("-") ) {
				scriptFilename = args[i];
			} else {
				System.err.println("Usage: NoiseCanvas <path/to/script.tnl>");
				System.exit(1);
			}
		}
		
		final ServiceManager sm = new ServiceManager();
		final Frame f = new Frame("Noise Canvas");
		final ColumnSideCanvas nc = new ColumnSideCanvas();
		
		final GeneratorUpdateListener gul = new GeneratorUpdateListener() {
			public void generatorUpdated( MinecraftWorldGenerator mwg ) {
				nc.setWorldGenerator( mwg );
			}
		};
		
		final int _tnlTabWidth = tnlTabWidth;
		
		final FileUpdateListener ful = new FileUpdateListener() {
			public void fileUpdated( File scriptFile ) {
				try {
					gul.generatorUpdated( ScriptUtil.loadWorldGenerator( scriptFile, _tnlTabWidth ) );
				} catch( ScriptError e ) {
					System.err.println(ParseUtil.formatScriptError(e));
				} catch( FileNotFoundException e ) {
					System.err.println(e.getMessage());
					System.exit(1);
					return;
				} catch( Exception e ) {
					throw new RuntimeException(e);
				}
			}
		};
		
		if( scriptFilename != null ) {
			File scriptFile = new File(scriptFilename);
			ful.fileUpdated( scriptFile );
			if( autoReload ) {
				FileWatcher fw = new FileWatcher( scriptFile, 500 );
				fw.addUpdateListener(ful);
				sm.add(fw);
			}
		} else {
			throw new RuntimeException("You need to provide a script!");
		}
		
		nc.setPreferredSize(new Dimension(512,128));
		nc.addKeyListener(new WorldExploreKeyListener(nc));

		f.add(nc);
		f.pack();
		f.addWindowListener(new WindowListener() {
			public void windowOpened( WindowEvent arg0 ) {}
			public void windowIconified( WindowEvent arg0 ) {}
			public void windowDeiconified( WindowEvent arg0 ) {}
			public void windowDeactivated( WindowEvent arg0 ) {}
			public void windowClosing( WindowEvent arg0 ) {
				nc.stopRenderer();
				f.dispose();
				sm.halt();
			}
			public void windowClosed( WindowEvent arg0 ) {}
			public void windowActivated( WindowEvent arg0 ) {}
		});
		sm.start();
		f.setVisible(true);
		nc.setWorldPos(0,0,1);
		nc.requestFocus();
	}
}
