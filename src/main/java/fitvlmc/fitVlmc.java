package fitvlmc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.TokenStream;

import ECFEntity.ECFListener;
import ECFEntity.Flow;
import suffixarray.SuffixArray;
import antlr.ECFLexer;
import antlr.ECFParser;
import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import com.github.lalyos.jfiglet.FigletFont;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress; 

@SuppressWarnings("restriction")
public class fitVlmc {

	private Flow ecfModel = null;
	private String content = null;
	private SuffixArray sa = null;
	public static double cutoff = -1;
	VlmcRoot vlmc = null;

	private static String ecfModelPath = null;
	private static String inFile = null;
	private static String outFile = null;
	private static String vlmcOutFile = null;
	private static String ecfOutFile = null;
	private static int nSim = -1;
	public static int k = -1;
	public static Float alfa = null;
	private static String vlmcFile = null;
	private static String initCtx = null;
	private static String cmpLik = null;
	private static boolean rnd=false;
	private static boolean pred=false;
	private static Integer pred_rest_port=null;
	// Maximum navigation depth for ECF traversal (prevents infinite recursion in cyclic models)
	public static int maxNavigationDepth = 25;

	public static void main(String[] args) {
		Locale.setDefault(new Locale("en", "US"));
		fitVlmc learner = new fitVlmc();
		learner.getCliOptions(args);

		// NEW: Support automatic ECF generation from traces
		if (fitVlmc.ecfModelPath == null) {
			System.out.println("No ECF model provided. Will generate ECF automatically from input traces.");
			learner.readInputTraces(); // Need to read traces first for auto-generation
			learner.generateEcfFromTraces();
		} else {
			learner.parseEcfModel();
		}

		// compute the pruning as a quantile of the chi square distribution
		// Skip cutoff calculation in prediction mode with pre-trained model
		boolean isPredictionMode = fitVlmc.pred || fitVlmc.pred_rest_port != null;
		if (!isPredictionMode || fitVlmc.alfa != null) {
			fitVlmc.cutoff = jdistlib.ChiSquare.quantile(fitVlmc.alfa,
					Math.max(0.1, learner.ecfModel.getEdges().size() - 1), false, false) / 2;
		} else {
			// In prediction mode with pre-trained model, cutoff is not needed
			fitVlmc.cutoff = 0.0;
		}
		// fitVlmc.cutoff=fitVlmc.alfa;

		System.out.println(cutoff);

		if (fitVlmc.vlmcFile == null) {

			// Read traces only if not already read during ECF auto-generation
			if (fitVlmc.ecfModelPath != null) {
				long startReading = System.currentTimeMillis();
				learner.readInputTraces();
				System.out.println(String.format("Reading time %dms", System.currentTimeMillis() - startReading));
			} else {
				System.out.println("Traces already loaded for ECF generation.");
			}

			long startSA = System.currentTimeMillis();
			learner.createSuffixArray();
			long saTime = System.currentTimeMillis() - startSA;
			System.out.println(String.format("Create suffix array time %dms", saTime));

			long startFitVlmc = System.currentTimeMillis();
			learner.fit();
			long fitTime = System.currentTimeMillis() - startFitVlmc;
			System.out.println(String.format("VLMC fitting time %dms", fitTime));

			System.out.println(String.format("Total:=%dms", fitTime + saTime));

			FileWriter vlmcWrite;
			try {
				vlmcWrite = new FileWriter(new File(vlmcOutFile));
				vlmcWrite.write(learner.vlmc.toString(new String[] { "" }));
				vlmcWrite.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			learner.vlmc.computeOrder(0);
			System.out.println(String.format("Order:=%d", VlmcRoot.order));
			System.out.println(String.format("VLMC total nodes %d", learner.vlmc.nNodes));
		} else {
			learner.vlmc = new VlmcRoot();
			EcfNavigator ecfNav = new EcfNavigator(learner);
			ecfNav.setMaxNavigationDepth(fitVlmc.maxNavigationDepth);
			learner.vlmc.setLabel("root");
			learner.vlmc.parseVLMC(vlmcFile);

			learner.vlmc.computeOrder(0);

			System.out.println(String.format("Order:=%d", VlmcRoot.order));
			System.out.println(String.format("VLMC total nodes %d", learner.vlmc.nNodes));

			if (fitVlmc.cmpLik != null) {

				File ctxFile = new File(fitVlmc.cmpLik);
				FileReader fr = null;
				String ctxStr = null;
				try {
					fr = new FileReader(ctxFile);
					BufferedReader br = new BufferedReader(fr);
					ctxStr = br.readLine();
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				ArrayList<ArrayList<String>> inCtx = new ArrayList<>();
				String[] ctxs = ctxStr.toString().split("end\\$");
				for (int i = 0; i < ctxs.length; i++) {
					ArrayList<String> ctx = new ArrayList<>();
					String[] pieces = (ctxs[i] + "end$").trim().split(" ");
					for (int j = 0; j < pieces.length; j++) {
						ctx.add(pieces[j]);
					}
					inCtx.add(ctx);
				}

				String[] pathPicies = fitVlmc.vlmcFile.split("/");
				File likFile = new File(pathPicies[pathPicies.length - 1] + ".lik");
				FileWriter fw = null;
				try {
					fw = new FileWriter(likFile);
				} catch (IOException e) {
					e.printStackTrace();
				}
				for (ArrayList<String> ctx : inCtx) {
					ArrayList<Double> l = learner.vlmc.getLikelihood(ctx);
					try {
						fw.write(String.format("%f,%d\n", l.get(l.size() - 1), l.size()));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				try {
					fw.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else if (fitVlmc.rnd) {
				// genero una una nuova VLMC a partire dalla precedente sporcandone le
				// probabilita'
				learner.vlmc.DFS((VlmcNode node) -> {
					ArrayList<String> labels = node.getDist().getSymbols();
					int n = labels.size();

					ArrayList<Double> a = new ArrayList<Double>();
					for (int i = 0; i < n; i++) {
						a.add(0d);
					}
					double s = 0.0d;
					Random random = new Random();
					for (int i = 0; i < n; i++) {
						a.set(i, 1.0d - random.nextDouble());
						a.set(i, -1 * Math.log(a.get(i)));
						s += a.get(i);
					}
					for (int i = 0; i < n; i++) {
						a.set(i, a.get(i) / s);
					}
					node.getDist().setProbability(a);
				});
			}else if(fitVlmc.pred) {
				if(fitVlmc.initCtx==null) {
					try {
						throw new Exception("initial ctx is required");
					}catch (Exception e) {
						e.printStackTrace();
					}
				}
				
				String[] ctxs = fitVlmc.initCtx.split(" ");
				ArrayList<String> initCtx=new ArrayList<String>();
				for (String s : ctxs) {
					initCtx.add(s);
				}
				VlmcNode node = learner.vlmc.getState(initCtx);
				System.out.println(node.getDist());
			}else if(fitVlmc.pred_rest_port!=null) {
				//lancio il server http che fa le predizioni di continuo
				// Create a simple HTTP server on port 8080
		        HttpServer server;
				try {
					server = HttpServer.create(new InetSocketAddress(fitVlmc.pred_rest_port), 0);
					// Create a context for the root path ("/") and set the handler
			        server.createContext("/",new RESTVlmc(learner.vlmc) );
			        // Start the server
			        server.start();
			        System.out.println("Server is running on port "+fitVlmc.pred_rest_port+". Press Ctrl+C to stop.");

			        // In REST server mode, keep the server running and exit
			        // Don't continue with model saving or simulation
			        while (true) {
			        	try {
			        		Thread.sleep(1000);
			        	} catch (InterruptedException e) {
			        		break;
			        	}
			        }
			        return;
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}
			}

			FileWriter vlmcWrite;
			try {
				vlmcWrite = new FileWriter(new File(vlmcOutFile));
				vlmcWrite.write(learner.vlmc.toString(new String[] { "" }));
				vlmcWrite.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		long startSimVlmc = System.currentTimeMillis();
		ArrayList<ArrayList<String>> traces = learner.vlmc.simulate(nSim, learner);
		System.out.println(String.format("VLMC simulation time %dms", System.currentTimeMillis() - startSimVlmc));

		File outFile;
		if (fitVlmc.outFile != null) {
			outFile = new File(fitVlmc.outFile);
		} else {
			String[] pieces = fitVlmc.vlmcOutFile.split("/");
			pieces = pieces[pieces.length - 1].split(".vlmc");
			outFile = new File(pieces[0] + ".dcdt");
		}
		fitVlmc.saveTraces(traces, outFile);
	}

	public void readInputTraces() {
		File inFile = new File(fitVlmc.inFile);

		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(new FileInputStream(inFile), "UTF8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		String str = null;
		String contentStr = "";
		try {
			while ((str = in.readLine()) != null) {
				contentStr += str;
			}
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.content = contentStr;
//		this.content=new ArrayList<String>();
//		String[] trace= contentStr.split(" ");
//		for (String state : trace) {
//			content.add(state);
//		}
	}

	public void createSuffixArray() {
		// this.sa = new SuffixArrayInt(content);
		this.sa = new SuffixArray(content);
	}

	public void parseEcfModel() {
		CharStream charStream = null;
		try {
			System.out.println(ecfModelPath);
			charStream = CharStreams.fromPath(Paths.get(ecfModelPath));
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		TokenStream tokens = new CommonTokenStream(new ECFLexer(charStream));

		ECFParser p = new ECFParser(tokens);

		p.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
					int charPositionInLine, String msg, RecognitionException e) {
				throw new IllegalStateException("failed to parse at line " + line + " due to " + msg, e);
			}
		});
		ECFListener listner = new ECFListener();
		p.addParseListener(listner);
		p.ecf();
		ecfModel = listner.getEcfModel();
	}

	public void generateEcfFromTraces() {
		if (this.content == null || this.content.trim().isEmpty()) {
			throw new RuntimeException("Cannot generate ECF: trace content is empty. Make sure to call readInputTraces() first.");
		}

		System.out.println("Generating ECF model from traces...");
		long startEcfGen = System.currentTimeMillis();

		this.ecfModel = Trace2EcfIntegrator.createEcfFromContentWithValidation(this.content);

		long ecfGenTime = System.currentTimeMillis() - startEcfGen;
		System.out.println(String.format("ECF generation time: %dms", ecfGenTime));

		System.out.println("ECF model generated successfully from input traces.");

		// Save ECF model to file if requested
		if (fitVlmc.ecfOutFile != null) {
			saveEcfModel(this.ecfModel, fitVlmc.ecfOutFile);
		}
	}

	/**
	 * Save ECF model to file for deployment with VLMC
	 */
	private void saveEcfModel(Flow ecfModel, String ecfOutFile) {
		try {
			System.out.println("Saving ECF model to: " + ecfOutFile);
			FileWriter ecfWriter = new FileWriter(new File(ecfOutFile));
			ecfWriter.write(ecfModel.toString());
			ecfWriter.close();
			System.out.println("ECF model saved successfully.");
		} catch (IOException e) {
			System.err.println("Failed to save ECF model: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void fit() {
		EcfNavigator ecfNav = new EcfNavigator(this);
		ecfNav.setMaxNavigationDepth(fitVlmc.maxNavigationDepth);
		ecfNav.visit();
		this.vlmc = ecfNav.getVlmc();
	}

	public static void saveTraces(ArrayList<ArrayList<String>> traces, File outfile) {
		System.out.println("saving traces");
		System.out.println(outfile.toString());
		if (fitVlmc.nSim > 0) {
			try {
				FileWriter w = new FileWriter(outfile);
				for (ArrayList<String> trace : traces) {
					for (String activity : trace) {
						if(!activity.equals(trace.get(trace.size()-1))) {
							w.write(String.format("%s,,", activity));
						}else {
							w.write(String.format("%s", activity));
						}
					}
					w.write("\n");
				}
				w.flush();
				w.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	private void printHelp() {
		// Create fancy ASCII art banner
		try {
			String asciiArt = FigletFont.convertOneLine("REMEMBER");
			System.out.println(asciiArt);
		} catch (Exception e) {
			// Fallback if ASCII art fails
			System.out.println("██████╗ ███████╗███╗   ███╗███████╗███╗   ███╗██████╗ ███████╗██████╗");
			System.out.println("██╔══██╗██╔════╝████╗ ████║██╔════╝████╗ ████║██╔══██╗██╔════╝██╔══██╗");
			System.out.println("██████╔╝█████╗  ██╔████╔██║█████╗  ██╔████╔██║██████╔╝█████╗  ██████╔╝");
			System.out.println("██╔══██╗██╔══╝  ██║╚██╔╝██║██╔══╝  ██║╚██╔╝██║██╔══██╗██╔══╝  ██╔══██╗");
			System.out.println("██║  ██║███████╗██║ ╚═╝ ██║███████╗██║ ╚═╝ ██║██████╔╝███████╗██║  ██║");
			System.out.println("╚═╝  ╚═╝╚══════╝╚═╝     ╚═╝╚══════╝╚═╝     ╚═╝╚═════╝ ╚══════╝╚═╝  ╚═╝");
		}
		System.out.println();
		System.out.println("🧠 Variable Length Markov Chain Learning with Adaptive Memory 🧠");
		System.out.println("================================================================");
		System.out.println();
		System.out.println("USAGE:");
		System.out.println("  java -jar remember.jar [OPTIONS]");
		System.out.println();
		System.out.println("DESCRIPTION:");
		System.out.println("  REMEMBER learns Variable Length Markov Chains from execution traces,");
		System.out.println("  automatically adapting memory length for optimal pattern recognition.");
		System.out.println("  Supports ECF generation, model simulation, predictions, and likelihood analysis.");
		System.out.println();
		System.out.println("OPTIONS:");
		System.out.println("  Core Options:");
		System.out.println("    --infile <file>       Input traces file (required for learning)");
		System.out.println("    --vlmcfile <file>     Output VLMC model file (default: model.vlmc)");
		System.out.println("    --alfa <float>        Alpha parameter for statistical pruning (required)");
		System.out.println("    --nsim <int>          Number of simulations to generate");
		System.out.println();
		System.out.println("  ECF Model Options:");
		System.out.println("    --ecf <file>          ECF model file (optional - auto-generated if not provided)");
		System.out.println("    --ecfoutfile <file>   Save auto-generated ECF model to file");
		System.out.println("    --outfile <file>      Output file for generated traces");
		System.out.println();
		System.out.println("  Advanced Options:");
		System.out.println("    --vlmc <file>         Load pre-computed VLMC model instead of learning");
		System.out.println("    --ntime <int>         Time parameter for processing");
		System.out.println("    --initCtx <context>   Initial context for prediction (space-separated)");
		System.out.println("    --maxdepth <int>      Maximum ECF navigation depth to prevent infinite loops (default: 25)");
		System.out.println();
		System.out.println("  Analysis Modes:");
		System.out.println("    --lik <file>          Calculate likelihood for contexts in file");
		System.out.println("    --pred                Prediction mode (requires --initCtx)");
		System.out.println("    --pred_rest <port>    Start REST API prediction server on specified port");
		System.out.println("    --rnd                 Generate randomized VLMC from existing model");
		System.out.println();
		System.out.println("  Help:");
		System.out.println("    -h, --help            Show this help message and exit");
		System.out.println();
		System.out.println("EXAMPLES:");
		System.out.println("  🔥 Learn adaptive memory model from traces:");
		System.out.println("  java -jar remember.jar --infile traces.txt --vlmcfile memory.vlmc --alfa 0.05 --nsim 1000");
		System.out.println();
		System.out.println("  💾 Learn VLMC and save ECF for deployment:");
		System.out.println("  java -jar remember.jar --infile traces.txt --vlmcfile memory.vlmc --ecfoutfile memory.ecf --alfa 0.05 --nsim 1000");
		System.out.println();
		System.out.println("  📊 Learn with existing ECF control flow:");
		System.out.println("  java -jar remember.jar --ecf workflow.ecf --infile traces.txt --vlmcfile memory.vlmc --alfa 0.01");
		System.out.println();
		System.out.println("  🎯 Make intelligent predictions:");
		System.out.println("  java -jar remember.jar --vlmc memory.vlmc --pred --initCtx \"login navigate checkout\"");
		System.out.println();
		System.out.println("  🚀 Launch prediction microservice:");
		System.out.println("  java -jar remember.jar --vlmc memory.vlmc --pred_rest 8080");
		System.out.println();
		System.out.println("  🧮 Analyze sequence likelihood:");
		System.out.println("  java -jar remember.jar --vlmc memory.vlmc --lik test_sequences.txt");
		System.out.println();
		System.out.println("💡 NOTES:");
		System.out.println("  • REMEMBER auto-generates ECF models when not provided - zero configuration!");
		System.out.println("  • Alpha parameter controls memory depth (lower = longer adaptive memory)");
		System.out.println("  • REST API endpoint: POST / with {\"context\": [\"state1\", \"state2\"]}");
		System.out.println("  • Variable memory length adapts automatically to your data patterns");
		System.out.println();
		System.out.println("🌟 REMEMBER: Because patterns have memory, and memory has patterns");
		System.out.println("   GitHub: https://github.com/sysma/remember");
	}

	public void getCliOptions(String[] args) {
		// Check for help first
		if (args.length == 0 || Arrays.asList(args).contains("--help") || Arrays.asList(args).contains("-h")) {
			printHelp();
			System.exit(0);
		}

		int c;
		LongOpt[] longopts = new LongOpt[16];
		longopts[0] = new LongOpt("ecf", LongOpt.REQUIRED_ARGUMENT, null, 0);
		longopts[1] = new LongOpt("outfile", LongOpt.REQUIRED_ARGUMENT, null, 1);
		longopts[2] = new LongOpt("infile", LongOpt.REQUIRED_ARGUMENT, null, 2);
		// file di ouput della vlmc
		longopts[3] = new LongOpt("vlmcfile", LongOpt.REQUIRED_ARGUMENT, null, 3);
		longopts[4] = new LongOpt("nsim", LongOpt.REQUIRED_ARGUMENT, null, 4);
		longopts[5] = new LongOpt("ntime", LongOpt.REQUIRED_ARGUMENT, null, 5);
		longopts[6] = new LongOpt("alfa", LongOpt.REQUIRED_ARGUMENT, null, 6);
		// file di input di una vlmc appresa precedentemente
		longopts[7] = new LongOpt("vlmc", LongOpt.REQUIRED_ARGUMENT, null, 7);
		longopts[8] = new LongOpt("initCtx", LongOpt.REQUIRED_ARGUMENT, null, 8);
		// qualora si vuole usare solo per calcolare la likelyhood di un set di prefissi
		longopts[9] = new LongOpt("lik", LongOpt.REQUIRED_ARGUMENT, null, 9);
		// qualora volessi generare una nuova vlmc a partire da un altra
		longopts[10] = new LongOpt("rnd", LongOpt.NO_ARGUMENT, null, 10);
		//qualora volessi solo conoscere la next symbol distribution a partire da un prefisso
		longopts[11] = new LongOpt("pred", LongOpt.NO_ARGUMENT, null, 11);
		//qualora volessi  conoscere la next symbol distribution a partire da un prefisso ma attraverso un API Rest
		longopts[12] = new LongOpt("pred_rest", LongOpt.REQUIRED_ARGUMENT, null, 12);
		// Help option
		longopts[13] = new LongOpt("help", LongOpt.NO_ARGUMENT, null, 13);
		// Maximum navigation depth for cyclic ECF models
		longopts[14] = new LongOpt("maxdepth", LongOpt.REQUIRED_ARGUMENT, null, 14);
		longopts[15] = new LongOpt("ecfoutfile", LongOpt.REQUIRED_ARGUMENT, null, 15);

		Getopt g = new Getopt("fitVlmc", args, "h", longopts);
		g.setOpterr(true);
		while ((c = g.getopt()) != -1) {
			switch (c) {
			case 0:
				fitVlmc.ecfModelPath = g.getOptarg();
				break;
			case 1:
				fitVlmc.outFile = g.getOptarg();
				break;
			case 2:
				fitVlmc.inFile = g.getOptarg();
				break;
			case 3:
				fitVlmc.vlmcOutFile = g.getOptarg();
				break;
			case 4:
				fitVlmc.nSim = Integer.valueOf(g.getOptarg()).intValue();
				break;
			case 5:
				fitVlmc.k = Integer.valueOf(g.getOptarg()).intValue();
				break;
			case 6:
				fitVlmc.alfa = Float.valueOf(g.getOptarg());
				break;
			case 7:
				fitVlmc.vlmcFile = String.valueOf(g.getOptarg());
				break;
			case 8:
				fitVlmc.initCtx = String.valueOf(g.getOptarg());
				break;
			case 9:
				fitVlmc.cmpLik = String.valueOf(g.getOptarg());
				break;
			case 10:
				fitVlmc.rnd = true;
				break;
			case 11:
				fitVlmc.pred = true;
				break;
			case 12:
				fitVlmc.pred_rest_port=Integer.valueOf(g.getOptarg());
				break;
			case 13:
			case 'h':
				printHelp();
				System.exit(0);
				break;
			case 14:
				int maxDepth = Integer.valueOf(g.getOptarg());
				fitVlmc.maxNavigationDepth = maxDepth;
				break;
			case 15:
				fitVlmc.ecfOutFile = g.getOptarg();
				break;
			default:
				System.err.println("Unknown option. Use --help for usage information.");
				System.exit(1);
				break;
			}
		}

		// Validate required parameters
		validateParameters();
	}

	private void validateParameters() {
		boolean hasError = false;

		// Mode detection
		boolean isLearningMode = fitVlmc.vlmcFile == null;
		boolean isPredictionMode = fitVlmc.pred || fitVlmc.pred_rest_port != null;
		boolean isLikelihoodMode = fitVlmc.cmpLik != null;

		if (isLearningMode) {
			// Learning mode requires input file and alpha
			if (fitVlmc.inFile == null) {
				System.err.println("ERROR: --infile is required for learning mode");
				hasError = true;
			}
			if (fitVlmc.alfa == null) {
				System.err.println("ERROR: --alfa is required for learning mode");
				hasError = true;
			}
			if (fitVlmc.vlmcOutFile == null) {
				System.err.println("WARNING: --vlmcfile not specified, using default 'model.vlmc'");
				fitVlmc.vlmcOutFile = "model.vlmc";
			}
			if (fitVlmc.nSim == -1) {
				System.err.println("WARNING: --nsim not specified, using default '1'");
				fitVlmc.nSim = 1;
			}
			if (fitVlmc.outFile == null) {
				System.err.println("WARNING: --outfile not specified, using default 'simulation_output.mat'");
				fitVlmc.outFile = "simulation_output.mat";
			}
			if (fitVlmc.k == -1) {
				System.err.println("WARNING: --ntime not specified, using default '1'");
				fitVlmc.k = 1;
			}
		} else {
			// Model loading mode requires existing VLMC file
			if (fitVlmc.vlmcFile == null) {
				System.err.println("ERROR: --vlmc is required when not in learning mode");
				hasError = true;
			}
		}

		// Prediction mode validation
		if (isPredictionMode && fitVlmc.pred && fitVlmc.initCtx == null) {
			System.err.println("ERROR: --initCtx is required with --pred");
			hasError = true;
		}

		// REST server validation
		if (fitVlmc.pred_rest_port != null) {
			if (fitVlmc.pred_rest_port < 1 || fitVlmc.pred_rest_port > 65535) {
				System.err.println("ERROR: --pred_rest port must be between 1 and 65535");
				hasError = true;
			}
		}

		if (hasError) {
			System.err.println("\nUse --help for usage information.");
			System.exit(1);
		}
	}

	public Flow getEcfModel() {
		return ecfModel;
	}

	public SuffixArray getSa() {
		return sa;
	}

}
