package fitvlmc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import ECFEntity.Edge;
import vlmc.NextSymbolsDistribution;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class EcfNavigator {

	public static fitVlmc learner=null;
	private VlmcRoot vlmc=null;
	Integer exist=0;
	Integer total=0;

	// Depth limiting parameters
	private int maxNavigationDepth = 25;  // Default depth limit

	/**
	 * Set maximum navigation depth to prevent infinite recursion in cyclic ECF models
	 * @param maxDepth Maximum depth for ECF navigation (default: 25)
	 */
	public void setMaxNavigationDepth(int maxDepth) {
		this.maxNavigationDepth = maxDepth;
		System.out.println("🔧 ECF Navigation max depth set to: " + maxDepth);
	}
	
	public EcfNavigator(fitVlmc learner) {
		EcfNavigator.learner=learner;
		//initialize the vlmc
		this.vlmc=new VlmcRoot();
		this.vlmc.setLabel("root");
		this.vlmc.setDist(new NextSymbolsDistribution());
	}
	
	public void visit() {
		TreeMap<String, Edge> edges = learner.getEcfModel().getEdges();
		double done=0;
		for (String label : edges.keySet()) { 
			//System.out.println(edges.get(label).getLabel());
			long start=System.currentTimeMillis();
			//VlmcNode vn=this.visit(edges.get(label),this.vlmc,new ArrayList<Integer>(Arrays.asList()));
			VlmcNode vn=this.visit(edges.get(label),this.vlmc,new ArrayList<String>(Arrays.asList()));
			//System.out.println(String.format("Visit:%d", System.currentTimeMillis()-start));
			//System.out.println(vn.toString(new String[]{""}));
			start=System.currentTimeMillis();
			this.prune(vn);
			//System.out.println(String.format("Prune:%d", System.currentTimeMillis()-start));
			this.vlmc.addChild(vn);
			done++;
			System.out.println(done/edges.size());
//			if(label==20)
//				break;
		}
	}
	
	
	
	private VlmcNode visit(Edge e, VlmcNode parent, ArrayList<String> ctx) {
		return visit(e, parent, ctx, 0, new HashSet<>());
	}

	private VlmcNode visit(Edge e, VlmcNode parent, ArrayList<String> ctx, int depth, Set<String> visitedContexts) {
		VlmcNode vn = new VlmcNode();
		vn.setLabel(e.getLabel());
		vn.setParent(parent);
		VlmcRoot.nNodes++;

		ArrayList<String> ctx_new = new ArrayList<String>(1);
		ctx_new.add(e.getLabel());
		ctx_new.addAll(1, ctx);

		// Track visited contexts to avoid redundant exploration in cyclic ECFs
		String ctxKey = String.join(" ", ctx_new);
		if (!visitedContexts.add(ctxKey)) {
			// Context already explored — return as leaf to prevent duplicate subtrees
			vn.setDist(createNextSymbolDistribution(ctx_new));
			return vn;
		}

		vn.setDist(createNextSymbolDistribution(ctx_new));

		if (depth >= maxNavigationDepth) return vn;

		ArrayList<String> toVisit = new ArrayList<String>(1);
		toVisit.add(null);
		toVisit.addAll(1, ctx_new);
		if(e.getOut().size() > 0) {
			for(Edge inEdge : e.getIn()) {
				toVisit.set(0, inEdge.getLabel());
				if(this.checkIfObserved(toVisit)) {
					vn.addChild(this.visit(inEdge, vn, ctx_new, depth + 1, visitedContexts));
				}
			}
		}
		return vn;
	}
	
//	private VlmcNode visit(Edge e, VlmcNode parent,ArrayList<Integer> ctx) {
//		//istanzio il nodo vlmc
//		VlmcNode vn=null;
//		
//		ArrayList<Integer> ctx_new = new ArrayList<Integer>(1);
//		ctx_new.add(Integer.valueOf(e.getLabel()));
//		//vado all'indientro con il contesto
//		ctx_new.addAll(1, ctx);
//		
//		ArrayList<Integer> toVisit=new ArrayList<Integer>(1);
//		toVisit.add(null);
//		toVisit.addAll(1,ctx_new);
//		
//		//se il padre di questo nodo ha una sola path all'indientro che posso esplorare allora posso aggiungere 
//		//la lebel corrente al padre alrimenti devo aggiungere un nodo
//		ArrayList<Edge> toVisitEdge=new ArrayList<Edge>();
//		
//		if(ctx.size()>0) {
//			Edge parentEdge=learner.getEcfModel().getEdges().get(ctx.get(0));
//			ArrayList<Integer> toVisitParent=new ArrayList<Integer>(1);
//			toVisitParent.add(null);
//			toVisitParent.addAll(1,ctx);
//			for(Edge inEdge : parentEdge.getIn()){
//				toVisitParent.set(0, Integer.valueOf(inEdge.getLabel()));
//				//System.out.println("checking "+toVisitParent.toString());
//				if(this.checkIfObserved(toVisitParent)) {
//					toVisitEdge.add(inEdge);
//				}
//			}
//		}
//		//System.out.println(ctx.toString());
//		if(!(parent instanceof VlmcRoot) && (toVisitEdge.size()<=1)) {
//			//System.out.println("condition met");
//			parent.setLabel(parent.getLabel()+"_"+e.getLabel());			
//			//lancio la visita in profondità ai padri
//			for(Edge inEdge : e.getIn()){
//				toVisit.set(0, Integer.valueOf(inEdge.getLabel()));
//				if(this.checkIfObserved(toVisit)) {
//					parent.addChild(this.visit(inEdge,parent,ctx_new));
//				}
//			}
//		}else {
//			//System.out.println("condition not met creating node "+e.getLabel()+"addinf to parent "+parent.getLabel());
//			vn=new VlmcNode();
//			vn.setLabel(e.getLabel());
//			vn.setParent(parent);
//			
//			
//			if(e.getOut().size()>0) {
//				//lancio la visita in profondità ai padri
//				for(Edge inEdge : e.getIn()){
//					toVisit.set(0, Integer.valueOf(inEdge.getLabel()));
//					if(this.checkIfObserved(toVisit)) {
//						vn.addChild(this.visit(inEdge,vn,ctx_new));
//					}
//				}
//			}
//		}
//		return vn;
//	}

	
	public static NextSymbolsDistribution createNextSymbolDistribution(ArrayList<String> ctx) {
		
		NextSymbolsDistribution dist=new NextSymbolsDistribution();
		//original branch
		Edge e=learner.getEcfModel().getEdges().get(ctx.get(ctx.size()-1));
		
		int[] suffInfo=learner.getSa().count(String.join(" ", ctx));
		
		double totalCtx=(new Integer(suffInfo[1])).doubleValue();
		
		dist.totalCtx=totalCtx;
		
		ArrayList<String> ctxNew=new ArrayList<String>(ctx);
		ctxNew.add(null);
		int LastId=ctxNew.size()-1;
		
		if(e.getOut().size()==1) {
			dist.getProbability().add(1.0);
			dist.getSymbols().add(e.getOut().get(0).getLabel());
		}else {
			//valuto i next symbols sempre relativi al contesto
			double usedCtx = 0;
			for (Edge outEdge : e.getOut()) {
				ctxNew.set(LastId, outEdge.getLabel());
				double count = new Integer(learner.getSa().count(String.join(" ",ctxNew))[1]).doubleValue();
				if(count<=0) {
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
				// No observations — uniform prior over all out-edges
				double uniform = 1.0 / e.getOut().size();
				for (Edge outEdge : e.getOut()) {
					dist.getProbability().add(uniform);
					dist.getSymbols().add(outEdge.getLabel());
				}
			}
		}
		return dist;
	}
	
	public static void prune(VlmcNode vn) { 
		//call prune for each child of vn
		ArrayList<VlmcNode> localChildren = new ArrayList<VlmcNode>(vn.getChildren());
		for (VlmcNode vnChild : localChildren) {
			EcfNavigator.prune(vnChild);
		}
		//if the node is a leaf actually prune it
		if(vn.getParent()!=null && !(vn.getParent() instanceof VlmcRoot) && vn.getChildren().size()==0) {
			vn.prune();
		}else if(vn.getParent() instanceof VlmcRoot && vn.getChildren().size()==0 && vn.getDist()==null) {
			vn.setDist(EcfNavigator.createNextSymbolDistribution(vn.getCtx()));
		}
		
	}
	
	private boolean checkIfObserved(ArrayList<String> ctx) {
		return learner.getSa().count(String.join(" ", ctx))[1]>=learner.k;
	}
	
	public VlmcRoot getVlmc() {
		return vlmc;
	}
}
