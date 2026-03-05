package vlmc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ECFEntity.Edge;
import fitvlmc.EcfNavigator;
import fitvlmc.fitVlmc;

public class VlmcRoot extends VlmcNode {

	public static int order = -1;
	public static int nNodes = -1;
	public static int nLeaves = -1;

	public VlmcRoot() {
		super();
	}

	private TreeMap<Integer, Double> pdfCost = null;

//	public TreeMap<Integer, Integer> simulate(int nrun, fitVlmc learner) {
//		pdfCost=new TreeMap<Integer, Integer>(); 
//		for(int r=0; r<nrun; r++) {
//			int cost = 0;
//			//initial state
//			ArrayList<Integer> ctx=new ArrayList<Integer>(Arrays.asList(0)); 
//			//get initial state
//			VlmcNode state=this.getChidByLabel("0");
//			//System.out.println(state.toString(new String[] {""}));
//			//va avanti finchè non vai in uno stato finale (il costo dello stato finale lo considero)
//			while(state.getEdge().getOut().size()>0) { 
//				//System.out.println(String.format("Present Context %s", ctx));
//				String newState=state.getDist().getNextState();
//				//System.out.println(String.format("New symbol %s dist %s", newState,state.getDist().toString()));
//				ctx.add(Integer.valueOf(newState));
//				//System.out.println(String.format("New Context %s", ctx));
//				state=this.getState(ctx);
//				//System.out.println("Selected Context "+state.getCtx());
//				cost+=learner.getEcfModel().getEdges().get(Integer.valueOf(newState)).getCost();
//				//System.out.println("");
//			}
//			//System.out.println(cost);
//			//System.out.println(String.format("ctx:%s, cost:%d",ctx,cost));
//			if(this.pdfCost.get(cost)==null) {
//				this.pdfCost.put(cost, 1);
//			}else {
//				this.pdfCost.put(cost, this.pdfCost.get(cost)+1);
//			}
//		}
//		return pdfCost;
//	}

//	public TreeMap<Integer, Double> simulate2(int nrun, fitVlmc learner) {
//		
//		pdfCost=new TreeMap<Integer, Double>();
//		TreeMap<String, Edge> edges = learner.getEcfModel().getEdges();
//		for(int r=0; r<nrun; r++) {
//			int cost = 0;
//			//get initial state
//			VlmcNode state=this.getChidByLabel(String.valueOf(edges.firstEntry().getKey()));
//			//initial state
//			ArrayList<Integer> ctx=new ArrayList<Integer>(Arrays.asList(Integer.valueOf(state.getLabel()))); 
//			//System.out.println(state.toString(new String[] {""}));
//			//va avanti finchè non vai in uno stato finale (il costo dello stato finale lo considero)
//			while(state.getDist().symbols.size()>0) {
//				//System.out.println(String.format("Present Context %s", ctx));
//				String newState=state.getDist().getNextState();
//				//System.out.println(String.format("New symbol %s dist %s", newState,state.getDist().toString()));
//				ctx.add(Integer.valueOf(newState));
//				//System.out.println(String.format("New Context %s", ctx));
//				state=this.getState(ctx);
//				//System.out.println("Selected Context "+state.getCtx());
//				cost+=edges.get(Integer.valueOf(newState)).getCost();
//				//System.out.println("");
//			}
//			//System.out.println(cost);
//			//System.out.println(String.format("ctx:%s, cost:%d",ctx,cost));
//			if(this.pdfCost.get(cost)==null) {
//				this.pdfCost.put(cost, 1d);
//			}else {
//				this.pdfCost.put(cost, this.pdfCost.get(cost)+1);
//			}
//		}
//		return pdfCost;
//	}

	public ArrayList<ArrayList<String>> simulate(int nrun, fitVlmc learner) {
		ArrayList<ArrayList<String>> initCtxs = null;
		ArrayList<ArrayList<String>> traces = new ArrayList<ArrayList<String>>();
		TreeMap<String, Edge> edges = learner.getEcfModel().getEdges();
		for (int r = 0; r < nrun; r++) {
			VlmcNode state = null;
			ArrayList<String> ctx = null;
			if (initCtxs == null || initCtxs.get(r) == null) {
				// get initial state
				state = this.getChidByLabel(String.valueOf(edges.firstEntry().getKey()));
			} else {
				state = this.getState(initCtxs.get(r));
			}
			// initial ctx
			ctx = state.getCtx();
			// va avanti finchè non vai in uno stato finale (il costo dello stato finale lo
			// considero)
			while (state.getDist().symbols.size() > 0) {
				// System.out.println(String.format("Present Context %s", ctx));
				String newState = state.getDist().getNextState();
				// System.out.println(String.format("New symbol %s dist %s",
				// newState,state.getDist().toString()));
				ctx.add(newState);
				// System.out.println(String.format("New Context %s", ctx));
				state = this.getState(ctx);
				// System.out.println("Selected Context "+state.getCtx());
				// System.out.println("");
			}
			traces.add(ctx);
		}
		return traces;
	}

	public TreeMap<Integer, Double> simulate(int nrun, fitVlmc learner, ArrayList<ArrayList<String>> initCtxs) {
		this.pdfCost = new TreeMap<Integer, Double>();
		// ArrayList<Integer> pdfCost=new ArrayList<Integer>();
		TreeMap<String, Edge> edges = learner.getEcfModel().getEdges();
		for (int r = 0; r < nrun; r++) {
			int cost = 0;
			VlmcNode state = null;
			ArrayList<String> ctx = null;
			if (initCtxs.get(r) == null) {
				// get initial state
				state = this.getChidByLabel(String.valueOf(edges.firstEntry().getKey()));
			} else {
				state = this.getState(initCtxs.get(r));
			}
			// initial ctx
			// ArrayList<String> ctx = new
			// ArrayList<String>(Arrays.asList(state.getLabel()));
			ctx = state.getCtx();
			// va avanti finchè non vai in uno stato finale (il costo dello stato finale lo
			// considero)
			while (state.getDist().symbols.size() > 0) {
				// System.out.println(String.format("Present Context %s", ctx));
				String newState = state.getDist().getNextState();
				// System.out.println(String.format("New symbol %s dist %s",
				// newState,state.getDist().toString()));
				ctx.add(newState);
				// System.out.println(String.format("New Context %s", ctx));
				state = this.getState(ctx);
				// System.out.println("Selected Context "+state.getCtx());
				cost += edges.get(newState).getCost();
				// System.out.println("");
			}
			// System.out.println(cost);
			// System.out.println(String.format("ctx:%s, cost:%d",ctx,cost));
			if (this.pdfCost.get(cost) == null) {
				this.pdfCost.put(cost, 1d);
			} else {
				this.pdfCost.put(cost, this.pdfCost.get(cost) + 1);
			}
			// pdfCost.add(cost);
		}
		return this.pdfCost;
	}

////	compute the log-likelihood of all prefix of a given path
//	public ArrayList<Double> LogLik(ArrayList<Integer> ctx) {
//		//navigate the tree and compute the probability of each prefix
//		//for each prefix navigate tree, get the correspongin vlmc node anche get the probability
//		//of the next symbol
//		ArrayList<Double> LogLik=new ArrayList<Double>();
//		double p=1.0;
//		//LogLik.add(Math.log(p));
//		ArrayList<Integer> tmpCtx = new ArrayList<Integer>(Arrays.asList(ctx.get(0)));
//		VlmcNode state = this.getState(tmpCtx);
//		for(int i=0; i<ctx.size()-1;i++) {
//			//get the new state and compute the probability of the context;
//			//System.out.println(String.format("ctx:%s symbol:%s",tmpCtx,ctx.get(i+1)));
//			p=p*state.getDist().getProbBySymbol(String.valueOf(ctx.get(i+1)));
//			//System.out.println(state.getDist().getProbBySymbol(String.valueOf(ctx.get(i+1))));
//			LogLik.add(Math.log(p));
//			tmpCtx.add(ctx.get(i+1));
//			state=this.getState(tmpCtx);
//		}
//		//LogLik.add(Math.log(p));
//		return LogLik;
//	}

	// compute the likelihood of all prefix of a given path
	public ArrayList<Double> getLikelihood(ArrayList<String> ctx) {
		// navigate the tree and compute the probability of each prefix
		// for each prefix navigate tree, get the correspongin vlmc node anche get the
		// probability
		// of the next symbol
		ArrayList<Double> lik = new ArrayList<Double>();
		double p = 1.0;

		Double dist = null;

		ArrayList<String> tmpCtx = new ArrayList<String>(Arrays.asList(ctx.get(0)));
		VlmcNode state = this.getState(tmpCtx);
		for (int i = 0; i < ctx.size() - 1; i++) {
			if (state.getDist() == null) {
				p = 0;
				lik.add(p);
				break;
			}
			dist = state.getDist().getProbBySymbol(String.valueOf(ctx.get(i + 1)));
			if (dist != null) {
				p = p * state.getDist().getProbBySymbol(String.valueOf(ctx.get(i + 1)));
			} else {
				p = 0;
			}
			//System.out.println(state.getDist());
			//System.out.println(state.getDist().getProbBySymbol(String.valueOf(ctx.get(i + 1))));
			lik.add(p);
			tmpCtx.add(ctx.get(i + 1));
			state = this.getState(tmpCtx);
			if (p == 0)
				break;
		}

		return lik;
	}

	public void addCost(String newState) {
		// scaleno
		if (newState.equals("49")) {
			if (this.pdfCost.get(3) == null) {
				this.pdfCost.put(3, 1d);
			} else {
				this.pdfCost.put(3, this.pdfCost.get(3) + 1);
			}
		}

		// isoscele
		if (newState.equals("51") || newState.equals("53") || newState.equals("55")) {
			if (this.pdfCost.get(2) == null) {
				this.pdfCost.put(2, 1d);
			} else {
				this.pdfCost.put(2, this.pdfCost.get(2) + 1);
			}
		}
		// scaleno
		if (newState.equals("47")) {
			if (this.pdfCost.get(1) == null) {
				this.pdfCost.put(1, 1d);
			} else {
				this.pdfCost.put(1, this.pdfCost.get(1) + 1);
			}
		}
		// illegal
		if (newState.equals("35") || newState.equals("46") || newState.equals("57")) {
			if (this.pdfCost.get(4) == null) {
				this.pdfCost.put(4, 1d);
			} else {
				this.pdfCost.put(4, this.pdfCost.get(4) + 1);
			}
		}
	}

	public TreeMap<Integer, Double> simulateSA(int nrun, fitVlmc learner) {
		pdfCost = new TreeMap<Integer, Double>();
		for (int r = 0; r < nrun; r++) {
			int cost = 0;
			// initial state
			String init = learner.getEcfModel().getEdges().firstEntry().getValue().getLabel();
			ArrayList<String> ctx = new ArrayList<String>(Arrays.asList(init));
			Edge state = learner.getEcfModel().getEdges().get(ctx.get(ctx.size() - 1));
			NextSymbolsDistribution dist = null;
			while (state.getOut().size() > 0) {
				dist = this.createNextSymbolDistribution(ctx, learner);
				ctx.add(dist.getNextState());
				state = learner.getEcfModel().getEdges().get(ctx.get(ctx.size() - 1));
				cost += state.getCost();
			}
			System.out.println(r * 1.0 / nrun);
			// System.out.println(cost);
			// System.out.println(String.format("ctx:%s, cost:%d",ctx,cost));
			if (this.pdfCost.get(cost) == null) {
				this.pdfCost.put(cost, 1d);
			} else {
				this.pdfCost.put(cost, this.pdfCost.get(cost) + 1);
			}
		}
		return pdfCost;
	}

	private NextSymbolsDistribution createNextSymbolDistribution(ArrayList<String> ctx, fitVlmc learner) {
		NextSymbolsDistribution dist = new NextSymbolsDistribution();
		// original branch
		Edge e = learner.getEcfModel().getEdges().get(ctx.get(ctx.size() - 1));

		int[] suffInfo = learner.getSa().count(String.join(" ", ctx));

		double totalCtx = (new Integer(suffInfo[1])).doubleValue();

		dist.totalCtx = totalCtx;

		ArrayList<String> ctxNew = new ArrayList<String>(ctx);
		ctxNew.add(null);
		int LastId = ctxNew.size() - 1;

		if (e.getOut().size() == 1) {
			dist.getProbability().add(1.0);
			dist.getSymbols().add(e.getOut().get(0).getLabel());
		} else {
			// valuto i next symbols sempre relativi al contesto
			double usedCtx = 0;
			for (Edge outEdge : e.getOut()) {
				ctxNew.set(LastId, outEdge.getLabel());
				double count = new Integer(learner.getSa().count(String.join(" ", ctxNew))[1]).doubleValue();
				if (count <= 0) {
					continue;
				}
				usedCtx += count;
				dist.getProbability().add(count);
				dist.getSymbols().add(outEdge.getLabel());
			}
			if (usedCtx > 0) {
				for (int i = 0; i < dist.getProbability().size(); i++) {
					dist.getProbability().set(i, dist.getProbability().get(i) / usedCtx);
				}
				dist.totalCtx = usedCtx;
			} else {
				double uniform = 1.0 / e.getOut().size();
				for (Edge outEdge : e.getOut()) {
					dist.getProbability().add(uniform);
					dist.getSymbols().add(outEdge.getLabel());
				}
			}
		}
		return dist;
	}

	public void parseVLMC(String vlmcFilePath) {
		this.children = new ArrayList<VlmcNode>();
		try {
			List<String> result = Files.readAllLines(Paths.get(vlmcFilePath));
			int i = 1;
			while (i < result.size()) {
				i = parseNode(this, result, i, 0);
				i++;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public int getNodeOrder(String node) {
		Pattern pattern = Pattern.compile("(\\t*)", Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(node);

		if (matcher.find())
			return matcher.group(1).length();

		return -1;
	}

	public String getNodeDist(String node) {
		Pattern pattern = Pattern.compile("\\t*.*(\\{.*\\})", Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(node);

		if (matcher.find())
			return matcher.group(1);

		return null;
	}

	public String getNodeLabel(String node) {
		final Pattern labelPattern = Pattern.compile("\"([A-Za-z0-9\\$_!#]*)\"-", Pattern.MULTILINE);
		final Matcher labelMatcher = labelPattern.matcher(node);
		if (labelMatcher.find()) {
			return labelMatcher.group(1);
		}
		return null;
	}

	public NextSymbolsDistribution parseNextSymbolDistribution(String nodeContent) {

		nodeContent = nodeContent.replace("{", "");
		nodeContent = nodeContent.replace("}", "");

		String[] nodePieces = nodeContent.split("-");

		NextSymbolsDistribution nodeDist = new NextSymbolsDistribution();
		ArrayList<String> symbols = new ArrayList<String>();
		ArrayList<Double> prob = new ArrayList<Double>();

		nodeDist.totalCtx = Double.valueOf(nodePieces[1]);

		final Pattern probPattern = Pattern.compile("\\[([0-9]+\\.[0-9]*),\\\"([A-Za-z0-9\\$_!#]*)\\\"\\]");
		final Matcher probMatcher = probPattern.matcher(nodePieces[0]);
		while (probMatcher.find()) {
			prob.add(Double.valueOf(probMatcher.group(1)));
			symbols.add(probMatcher.group(2));
		}

		nodeDist.probability = prob;
		nodeDist.symbols = symbols;

		return nodeDist;
	}

	public int parseNode(VlmcNode parent, List<String> result, int i, int order) {
		// System.out.println(result.get(i));
		// verifico l'ordine di questo elemento con il padre
		int myOrder = this.getNodeOrder(result.get(i));
		if (myOrder == order + 1) {
			// aggiungo solo se è un figlio diretto
			VlmcNode n = new VlmcNode();
			n.setLabel(this.getNodeLabel(result.get(i)));
			n.setDist(this.parseNextSymbolDistribution(this.getNodeDist(result.get(i))));

			n.setParent(parent);
			VlmcRoot.nNodes++;

			// scendo in profondità
			while (i < result.size() - 1 && myOrder < this.getNodeOrder(result.get(i + 1))) {
				i = this.parseNode(n, result, i + 1, myOrder);
			}

			if (parent instanceof VlmcRoot) {
				EcfNavigator.prune(n);
			}

			parent.addChild(n);
		}
		return i;
	}

	// navigo l'albero alla ricerca del prefisso piu lungo
	public VlmcNode getState(ArrayList<String> ctx) {
		VlmcNode node = this;
		for (int i = ctx.size() - 1; i >= 0 && node.getChidByLabel(ctx.get(i)) != null; i--) {
			node = node.getChidByLabel(ctx.get(i));
		}
		return node;
	}

	public VlmcNode getState2(ArrayList<String> ctx) {
		VlmcNode node = this;
		for (int i = ctx.size() - 1; i >= 0 && node.getChidByLabel(ctx.get(i)) != null; i--) {
			node = node.getChidByLabel(ctx.get(i));
		}
		return node;
	}

	public void DFS(vlmcWalker walker) {
		ArrayList<VlmcNode> nodes = this.getChildren();
		for (VlmcNode node : nodes) {
			this.dfsVisitNode(walker, node);
		}
	}

	public void dfsVisitNode(vlmcWalker walker, VlmcNode node) {
		walker.visitNode(node);

		ArrayList<VlmcNode> nodes = node.getChildren();
		for (VlmcNode node1 : nodes) {
			this.dfsVisitNode(walker, node1);
		}
	}
}
