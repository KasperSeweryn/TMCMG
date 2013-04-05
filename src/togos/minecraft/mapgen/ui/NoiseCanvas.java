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
import togos.minecraft.mapgen.ScriptUtil;
import togos.minecraft.mapgen.util.FileUpdateListener;
import togos.minecraft.mapgen.util.FileWatcher;
import togos.minecraft.mapgen.util.ServiceManager;
import togos.minecraft.mapgen.world.gen.GroundColorFunction;
import togos.minecraft.mapgen.world.gen.LayeredTerrainFunction;
import togos.minecraft.mapgen.world.gen.MinecraftWorldGenerator;
import togos.minecraft.mapgen.world.gen.NormalShadingGroundColorFunction;
import togos.noise.v1.func.CacheDaDaDa_Da;
import togos.noise.v1.func.LFunctionDaDa_DaIa;
import togos.noise.v1.lang.ParseUtil;
import togos.service.Service;

public class NoiseCanvas extends WorldExplorerViewCanvas
{
    private static final long serialVersionUID = 1L;
    
    class LayeredTerrainGroundFunction implements LFunctionDaDa_DaIa
    {
    	protected final LayeredTerrainFunction ltf;
    	public LayeredTerrainGroundFunction( LayeredTerrainFunction ltf ) {
    		this.ltf = ltf;
    	}

		@Override
        public void apply( int vectorSize, double[] x, double[] z, double[] height, int[] type ) {
	        // TODO
        }
    }
    
	class NoiseRenderer implements Runnable, Service {
		LFunctionDaDa_DaIa colorFunction;
		int width, height;
		double worldX, worldY, worldXPerPixel, worldYPerPixel;
		
		public volatile BufferedImage buffer;
		protected volatile boolean stop = false;
		
		public NoiseRenderer( LFunctionDaDa_DaIa colorFunction, int width, int height,
			double worldX, double worldY, double worldXPerPixel, double worldYPerPixel
		) {
			this.colorFunction = colorFunction;
			this.width = width;
			this.height = height;
			this.worldX = worldX;
			this.worldY = worldY;
			this.worldXPerPixel = worldXPerPixel;
			this.worldYPerPixel = worldYPerPixel;
		}
		
		protected BufferedImage createBuffer() {
			GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			GraphicsDevice gs = ge.getDefaultScreenDevice();
			GraphicsConfiguration gc = gs.getDefaultConfiguration();
			
			// Create an image that does not support transparency
			return gc.createCompatibleImage(width, height, Transparency.OPAQUE);
		}
		
		protected Color color(int argb) {
			return new Color(argb);
		}
		
		class CoordPopulator {
			int currentX, currentY, currentScale = 32;
			boolean sub = false;
			protected int nextCoords( int[] px, int[] py, int[] scale ) {
				int i;
				for( i=0; i<px.length; currentX += currentScale ) {
					if( currentX >= width ) {
						currentX = 0;
						currentY += currentScale;
					}
					if( currentY >= height ) {
						currentScale /= 2;
						currentX = 0;
						currentY = 0;
						sub = true;
					}
					if( currentScale == 0 ) {
						//++i;
						break;
					}
					if( sub && (currentX / currentScale) % 2 == 0 && (currentY / currentScale) % 2 == 0 ) {
						continue;
					}
					px[i] = currentX;
					py[i] = currentY;
					scale[i] = currentScale;
					++i;
				}
				return i;
			}
		}
		
		CoordPopulator cpop = new CoordPopulator();
		
		long elapsedMs = 0;
		long samples = 0;
		
		public void run() {
			if( width == 0 || height == 0 ) return;
			
			buffer = createBuffer();
			Graphics g = buffer.getGraphics();
			
			int batchSize = 1024;
			
			int[] px = new int[batchSize];
			int[] py = new int[batchSize];
			int[] scale = new int[batchSize];
			
			double[] wx = new double[batchSize];
			double[] wy = new double[batchSize];
			// Never used here, but coloring function gets to use it as scratch space.
			double[] height = new double[batchSize];
			int[] color = new int[batchSize];
			
			while( !stop ) {
				int coordCount = cpop.nextCoords(px, py, scale);
				if( coordCount == 0 ) return;
				
				for( int i=0; i<coordCount; ++i ) {
					wx[i] = worldX + px[i]*worldXPerPixel;
					wy[i] = worldY + py[i]*worldYPerPixel;
				}
				long beginTime = System.currentTimeMillis();
				colorFunction.apply( batchSize, wx, wy, height, color );
				long endTime = System.currentTimeMillis();
				elapsedMs += (endTime - beginTime);
				samples += batchSize;
				synchronized( buffer ) {
					for( int i=0; i<coordCount; ++i ) {
						int pcolor = color[i] == 0 ? 0xFF000000 : color[i];
						g.setColor( color(pcolor) );
						g.fillRect( px[i], py[i], scale[i], scale[i] );
					}
				}
				if( Stat.performanceLoggingEnabled && elapsedMs > 0 ) {
					System.err.println(samples / elapsedMs + " samples per ms");
					System.err.println(CacheDaDaDa_Da.GLOBAL_CACHE.cacheHits + " cache hits, " + CacheDaDaDa_Da.GLOBAL_CACHE.cacheMisses + " misses");
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
	
	public LFunctionDaDa_DaIa colorFunc;
	NoiseRenderer cnr;
	public boolean normalShadingEnabled = true;
	public boolean heightShadingEnabled = true;
	
	public NoiseCanvas() {
		super();
    }
	
	public void stateUpdated() {
		stopRenderer();
		
		double mpp = 1/zoom;
		double leftX = wx-mpp*getWidth()/2;
		double topY = wy-mpp*getHeight()/2;
		
		if( wg == null ) {
			colorFunc = null;
		} else if( normalShadingEnabled ) {
			NormalShadingGroundColorFunction gcf = new NormalShadingGroundColorFunction(
				new LayeredTerrainGroundFunction(wg.getTerrainFunction()),
				colorMap, mpp/2, mpp/2, 0.3/mpp, 0.5
			);
			gcf.heightShadingEnabled = heightShadingEnabled;
			colorFunc = gcf;
		} else {
			GroundColorFunction gcf = new GroundColorFunction(
				new LayeredTerrainGroundFunction(wg.getTerrainFunction()),
				colorMap
			);
			gcf.heightShadingEnabled = heightShadingEnabled;
			colorFunc = gcf;
		}
		
		if( colorFunc != null ) {
			startRenderer(new NoiseRenderer(colorFunc,getWidth(),getHeight(),leftX,topY,mpp,mpp));
		}
	}
	
	protected int getSkyColor() {
		return 0xFF000000;
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
		NoiseRenderer nr = cnr;
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
	
	public void startRenderer( NoiseRenderer nr ) {
		this.stopRenderer();
		this.cnr = nr;
		this.cnr.start();
	}
	
	interface GeneratorUpdateListener {
		public void generatorUpdated( MinecraftWorldGenerator wg );
	}
	
	public static void main( String[] args ) {
		String scriptFilename = null;
		boolean autoReload = false;
		for( int i=0; i<args.length; ++i ) {
			if( "-auto-reload".equals(args[i]) ) {
				autoReload = true;
			} else if( !args[i].startsWith("-") ) {
				scriptFilename = args[i];
			} else {
				System.err.println("Usage: NoiseCanvas <path/to/script.tnl>");
				System.exit(1);
			}
		}
		
		final ServiceManager sm = new ServiceManager();
		final Frame f = new Frame("Noise Canvas");
		final NoiseCanvas nc = new NoiseCanvas();
		
		final GeneratorUpdateListener gul = new GeneratorUpdateListener() {
			public void generatorUpdated( MinecraftWorldGenerator wg ) {
				nc.setWorldGenerator( wg );
			}
		};
		
		final FileUpdateListener ful = new FileUpdateListener() {
			public void fileUpdated( File scriptFile ) {
				try {
					MinecraftWorldGenerator worldGenerator = (MinecraftWorldGenerator)ScriptUtil.loadWorldGenerator( scriptFile );
					gul.generatorUpdated( worldGenerator );
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
		}
		
		nc.setPreferredSize(new Dimension(512,384));
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
		nc.addKeyListener(new WorldExploreKeyListener(nc));
		nc.requestFocus();
	}
}
