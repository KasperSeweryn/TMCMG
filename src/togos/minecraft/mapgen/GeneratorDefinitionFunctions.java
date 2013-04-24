package togos.minecraft.mapgen;

import java.util.ArrayList;

import togos.lang.BaseSourceLocation;
import togos.lang.CompileError;
import togos.lang.ScriptError;
import togos.minecraft.mapgen.world.gen.ChunkMunger;
import togos.minecraft.mapgen.world.gen.LayeredTerrainFunction;
import togos.minecraft.mapgen.world.gen.MinecraftWorldGenerator;
import togos.noise.v3.functions.MathFunctions;
import togos.noise.v3.program.compiler.ExpressionVectorProgramCompiler;
import togos.noise.v3.program.compiler.UnvectorizableError;
import togos.noise.v3.program.runtime.Binding;
import togos.noise.v3.program.runtime.BoundArgumentList;
import togos.noise.v3.program.runtime.Context;
import togos.noise.v3.program.runtime.Function;
import togos.noise.v3.vector.function.LFunctionDaDaDa_Ia;
import togos.noise.v3.vector.function.LFunctionDaDa_Da;
import togos.noise.v3.vector.vm.Program;
import togos.noise.v3.vector.vm.Program.Instance;
import togos.noise.v3.vector.vm.Program.RegisterBankID;
import togos.noise.v3.vector.vm.Program.RegisterBankID.DVar;
import togos.noise.v3.vector.vm.Program.RegisterBankID.IVar;
import togos.noise.v3.vector.vm.Program.RegisterID;

public class GeneratorDefinitionFunctions
{
	static final BaseSourceLocation BUILTIN_LOC = new BaseSourceLocation( GeneratorDefinitionFunctions.class.getName()+".java", 0, 0);
	protected static <V> Binding<? extends V> builtinBinding( V v ) {
		return Binding.forValue( v, BUILTIN_LOC );
	}
	
	protected static <V> Function<V> toFunc( Binding<?> b, Class<V> vClass ) throws CompileError {
		Object v;
        try {
	        v = b.getValue();
        } catch( Exception e ) {
        	throw new CompileError(e, b.sLoc);
        }
		if( v instanceof Function ) {
			return (Function<V>)v;
		} else if( vClass.isAssignableFrom(v.getClass()) ) {
			return new MathFunctions.ConstantBindingFunction<V>( (Binding<? extends V>)b );
		} else {
			throw new CompileError("Can't convert "+v.getClass()+" to a number function", b.sLoc);
		}
	}
	
	protected static final <V> V applyFunction( Function<V> func, Object...argValues ) throws Exception {
		BoundArgumentList bal = new BoundArgumentList(BUILTIN_LOC, BUILTIN_LOC);
		for( Object argValue : argValues ) {
			bal.add( "", Binding.forValue(argValue, BUILTIN_LOC), BUILTIN_LOC );
		}
		return func.apply(bal).getValue();
	}
	
	static class FunctionAdapterDaDaDa_Ia implements LFunctionDaDaDa_Ia {
		Function<? extends Number> sFunc;
		public FunctionAdapterDaDaDa_Ia( Function<? extends Number> sFunc ) {
			this.sFunc = sFunc;
		}
		
		@Override
		public void apply(int vectorSize, double[] x, double[] y, double[] z, int[] dest) {
			try {
				for( int i=vectorSize-1; i>=0; --i ) {
					dest[i] = applyFunction(sFunc, x[i], y[i], z[i]).intValue();
				}
			} catch( Exception e ) {
				throw new RuntimeException(e);
			}
		}
	}
	
	static class FunctionAdapterDaDa_Da implements LFunctionDaDa_Da {
		Function<? extends Number> sFunc;
		public FunctionAdapterDaDa_Da( Function<? extends Number> sFunc ) {
			this.sFunc = sFunc;
		}
		
		@Override
		public void apply(int vectorSize, double[] x, double[] y, double[] dest) {
			try {
				for( int i=vectorSize-1; i>=0; --i ) {
					dest[i] = applyFunction(sFunc, x[i], y[i]).intValue();
				}
			} catch( Exception e ) {
				throw new RuntimeException(e);
			}
		}
	}
	
	protected static void copy( int vectorSize, double[] src, double[] dest ) {
		for( int i=vectorSize-1; i>=0; --i ) dest[i] = src[i]; 
	}
	protected static void copy( int vectorSize, int[] src, int[] dest ) {
		for( int i=vectorSize-1; i>=0; --i ) dest[i] = src[i]; 
	}
	
	static class WorldGeneratorDefinition implements MinecraftWorldGenerator
	{
		ArrayList<LayerDefinition> layerDefs = new ArrayList<LayerDefinition>();
		Function<Number> biomeFunction = new MathFunctions.ConstantBindingFunction<Number>( Binding.forValue(0, BUILTIN_LOC) );
		
		@Override
        public LayeredTerrainFunction getTerrainFunction() {
			return new LayeredTerrainFunction() {
				@Override
                public TerrainBuffer apply( int vectorSize, double[] x, double[] z, TerrainBuffer buffer ) {
					try {
						buffer = TerrainBuffer.getInstance( buffer, vectorSize, layerDefs.size() );
		                for( int i=vectorSize-1; i>=0; --i ) {
		                	buffer.biomeData[i] = applyFunction( biomeFunction, x[i], z[i] ).intValue();
		                }
		                for( int i=0; i<layerDefs.size(); ++i ) {
		                	FunctionAdapterDaDa_Da floorHeightFunction = new FunctionAdapterDaDa_Da( layerDefs.get(i).floorHeight );
		                	FunctionAdapterDaDa_Da ceilingHeightFunction = new FunctionAdapterDaDa_Da( layerDefs.get(i).ceilingHeight );
		                	floorHeightFunction.apply( vectorSize, x, z, buffer.layerData[i].floorHeight );
		                	ceilingHeightFunction.apply( vectorSize, x, z, buffer.layerData[i].ceilingHeight );
		                	buffer.layerData[i].blockTypeFunction = new FunctionAdapterDaDaDa_Ia( layerDefs.get(i).blockType );
		                }
		                return buffer;
					} catch( Exception e ) {
						throw new RuntimeException(e);
					}
                }
			};
        }
		
		@Override
        public ChunkMunger getChunkMunger() {
	        // TODO Auto-generated method stub
	        return null;
        }
		
		protected <T> Binding<? extends T> bind( Function<T> f, Binding.Variable<?>[] varBindings )
			throws CompileError
		{
			BaseSourceLocation sl = BaseSourceLocation.fake("world generator vectorization");
			BoundArgumentList args = new BoundArgumentList(sl, sl);
			for( Binding.Variable<?> var : varBindings ) { 
				args.add( "", var, sl );
			}
			return f.apply( args );
		}
		
		protected <T, Bank extends RegisterBankID<?>> RegisterID<Bank> vectorize( Function<T> f, Binding.Variable<?>[] varBindings, ExpressionVectorProgramCompiler compiler, Bank resultBank )
			throws CompileError
		{
			return compiler.compile( bind( f, varBindings ), resultBank );
		}
		
		protected LFunctionDaDaDa_Ia compileDaDaDa_Ia( Function<?> f ) throws CompileError {
			ExpressionVectorProgramCompiler compiler = new ExpressionVectorProgramCompiler();
			Binding.Variable<Double> xVar = Binding.variable("x", Double.class);
			Binding.Variable<Double> yVar = Binding.variable("y", Double.class);
			Binding.Variable<Double> zVar = Binding.variable("z", Double.class);
			final RegisterID<DVar> xReg = compiler.declareVariable("x", DVar.INSTANCE);
			final RegisterID<DVar> yReg = compiler.declareVariable("y", DVar.INSTANCE);
			final RegisterID<DVar> zReg = compiler.declareVariable("z", DVar.INSTANCE);
			@SuppressWarnings("unchecked")
			Binding.Variable<Double>[] functionArguments = new Binding.Variable[] { xVar, yVar, zVar };
			final RegisterID<IVar> typeReg = compiler.compile( bind(f, functionArguments), IVar.INSTANCE ); 
			final Program p = compiler.pb.toProgram();
			
			return new LFunctionDaDaDa_Ia() {
				@Override public void apply( int vectorSize, double[] x, double[] y, double[] z, int[] dest ) {
					assert vectorSize >= 0;
					Instance instance = p.getInstance( vectorSize );
					instance.setDVar( xReg.number, x, vectorSize );
					instance.setDVar( yReg.number, y, vectorSize );
					instance.setDVar( zReg.number, z, vectorSize );
					instance.run( vectorSize );
					copy( vectorSize, instance.getIVector(typeReg), dest );
				}
			};
		}
		
		public MinecraftWorldGenerator vectorize() throws CompileError {
			ExpressionVectorProgramCompiler compiler = new ExpressionVectorProgramCompiler();
			Binding.Variable<Double> xVar = Binding.variable("x", Double.class);
			Binding.Variable<Double> zVar = Binding.variable("z", Double.class);
			RegisterID<DVar> xReg = compiler.declareVariable("x", DVar.INSTANCE);
			RegisterID<DVar> zReg = compiler.declareVariable("z", DVar.INSTANCE);
			@SuppressWarnings("unchecked")
			Binding.Variable<Double>[] xzVariables = new Binding.Variable[] { xVar, zVar };
			
			VectorWorldGenerator.VectorLayer[] vectorLayers = new VectorWorldGenerator.VectorLayer[layerDefs.size()];
			for( int i=0; i<layerDefs.size(); ++i ) {
				LayerDefinition ld = layerDefs.get(i);
				vectorLayers[i] = new VectorWorldGenerator.VectorLayer(
					compileDaDaDa_Ia( ld.blockType ),
					vectorize( ld.floorHeight, xzVariables, compiler, DVar.INSTANCE ),
					vectorize( ld.ceilingHeight, xzVariables, compiler, DVar.INSTANCE )
				);
			}
			
			RegisterID<IVar> biomeReg = vectorize( biomeFunction, xzVariables, compiler, IVar.INSTANCE );
			
			Program layerHeightProgram = compiler.pb.toProgram();
			return new VectorWorldGenerator( layerHeightProgram, vectorLayers, xReg, zReg, biomeReg );
		}
	}
	
	static class VectorWorldGenerator implements MinecraftWorldGenerator {
		static final class VectorLayer {
			final LFunctionDaDaDa_Ia typeFunction;
			final RegisterID<DVar> floorHeightRegister;
			final RegisterID<DVar> ceilingHeightRegister;
			
			public VectorLayer( LFunctionDaDaDa_Ia typeFunction, RegisterID<DVar> floorHeightRegister, RegisterID<DVar> ceilingHeightRegister ) {
				this.typeFunction = typeFunction;
				this.floorHeightRegister = floorHeightRegister;
				this.ceilingHeightRegister = ceilingHeightRegister;
			}
		}
		
		final Program layerHeightProgram;
		final VectorLayer[] layers;
		final RegisterID<DVar> xRegister;
		final RegisterID<DVar> zRegister;
		final RegisterID<IVar> biomeIdRegister;
		
		public VectorWorldGenerator( Program p, VectorLayer[] layers, RegisterID<DVar> xRegister, RegisterID<DVar> zRegister, RegisterID<IVar> biomeIdRegister ) {
			this.layerHeightProgram = p;
			this.layers = layers;
			this.xRegister = xRegister; 
			this.zRegister = zRegister;
			this.biomeIdRegister = biomeIdRegister;
		}
		
		@Override public LayeredTerrainFunction getTerrainFunction() {
			return new LayeredTerrainFunction() {
				@Override public TerrainBuffer apply( int vectorSize, double[] x, double[] z, TerrainBuffer buffer ) {
					buffer = TerrainBuffer.getInstance( buffer, vectorSize, layers.length );
					Instance instance = layerHeightProgram.getInstance(vectorSize);
					instance.setDVar( xRegister.number, x, vectorSize );
					instance.setDVar( zRegister.number, z, vectorSize );
					instance.run( vectorSize );
					for( int i=0; i<layers.length; ++i ) {
						buffer.layerData[i].blockTypeFunction = layers[i].typeFunction;
						copy( vectorSize, instance.getDVar(layers[i].floorHeightRegister.number), buffer.layerData[i].floorHeight );
						copy( vectorSize, instance.getDVar(layers[i].ceilingHeightRegister.number), buffer.layerData[i].ceilingHeight );
					}
					return buffer;
				}
			};
		}
		
		@Override public ChunkMunger getChunkMunger() {
			// TODO Auto-generated method stub
			return null;
		}
		
		
	}
	
	static class LayerDefinition
	{
		final Function<Number> blockType;
		final Function<Number> floorHeight;
		final Function<Number> ceilingHeight;
		
		public LayerDefinition( Function<Number> blockType, Function<Number> floorHeight, Function<Number> ceilingHeight ) {
			this.blockType = blockType;
			this.floorHeight = floorHeight;
			this.ceilingHeight = ceilingHeight;
		}
		
		public LayerDefinition( Binding<?> blockType, Binding<?> floorHeight, Binding<?> ceilingHeight ) throws CompileError {
			this( toFunc(blockType, Number.class), toFunc(floorHeight, Number.class), toFunc(ceilingHeight, Number.class) );
		}
	}
	
	public static final Context CONTEXT = new Context();
	static {
		try {
	        CONTEXT.put("material", ScriptUtil.bind("(id, data @ 0) -> (id & 0xFFF) | ((data & 0xF) << 12)", MathFunctions.CONTEXT, BUILTIN_LOC) );
        } catch( ScriptError e ) {
        	throw new RuntimeException("Error compiling built-in function", e);
        }
		CONTEXT.put("layer", builtinBinding(new Function<LayerDefinition>() {
			@Override
            public Binding<LayerDefinition> apply( BoundArgumentList input ) throws CompileError {
				if( input.arguments.size() != 3 ) throw new CompileError(
					"'layer' requires exactly 3 arguments, but "+input.arguments.size()+" given", input.argListLocation);
				for( BoundArgumentList.BoundArgument<?> arg : input.arguments ) {
					if( !arg.name.isEmpty() ) throw new CompileError(
						"'layer' takes no named arguments, but got '"+arg.name+"'", input.argListLocation );
				}
				return Binding.forValue( new LayerDefinition(
					input.arguments.get(0).value,
					input.arguments.get(1).value,
					input.arguments.get(2).value
				), LayerDefinition.class, input.callLocation );
            }
		}));
		CONTEXT.put("layered-terrain", builtinBinding(new Function<MinecraftWorldGenerator>() {
			@Override
            public Binding<MinecraftWorldGenerator> apply( BoundArgumentList input ) throws CompileError {
				WorldGeneratorDefinition wgd = new WorldGeneratorDefinition();
				for( BoundArgumentList.BoundArgument<?> arg : input.arguments ) {
					Object v;
					try {
						v = arg.value.getValue();
					} catch( CompileError e ) {
						throw e;
					} catch( Exception e ) {
						throw new RuntimeException( e );
					}
					if( "biome".equals(arg.name) ) {
						wgd.biomeFunction = toFunc( arg.value, Number.class );
					} else if( "".equals(arg.name) && v instanceof LayerDefinition ) {
						wgd.layerDefs.add( (LayerDefinition)v );
					} else if( "".equals(arg.name) && v instanceof Iterable ) {
						for( Object o : (Iterable<?>)v ) {
							if( o instanceof LayerDefinition ) {
								wgd.layerDefs.add( (LayerDefinition)o );
							} else {
								throw new CompileError("Don't know how to handle item in world generator list argument: "+o, arg.sLoc);
							}
						}
					} else {
						String argName = arg.name.length() == 0 ? " " : " '"+arg.name+"' "; 
						throw new CompileError("Don't know how to handle"+argName+"argument with value: "+v, arg.sLoc);
					}
				}
				MinecraftWorldGenerator compiled;
				try {
					compiled = wgd.vectorize();
				} catch( UnvectorizableError e ) {
					System.err.println("Warning: terrain definition is not vectorizable and therefore");
					System.err.println("will run REALLY SLOW:");
					e.printStackTrace(System.err);
					compiled = wgd;
				}
				return Binding.forValue(compiled, MinecraftWorldGenerator.class, input.argListLocation);
            }
		}));
	}
}
