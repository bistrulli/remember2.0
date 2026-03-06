package vlmc;

import java.util.ArrayList;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import fitvlmc.EcfNavigator;
import fitvlmc.fitVlmc;

public class VlmcNode implements Cloneable {
	private String label = null;
	private NextSymbolsDistribution dist = null;
	protected ArrayList<VlmcNode> children = null;
	private VlmcNode parent = null;
	private Double cachedKL = null;

	public VlmcNode() {
		this.children = new ArrayList<VlmcNode>();
		this.dist = new NextSymbolsDistribution();
	}

	public VlmcNode(VlmcNode node) {
		this.children = new ArrayList<VlmcNode>();
		for (VlmcNode vlmcNode : node.children) {
			this.children.add(new VlmcNode(vlmcNode));
			this.children.get(this.children.size() - 1).setParent(this);
		}
		this.label = node.getLabel();
		this.dist = copyDist(node.dist);
		this.cachedKL = null;
	}

	private static NextSymbolsDistribution copyDist(NextSymbolsDistribution src) {
		if (src == null) return new NextSymbolsDistribution();
		NextSymbolsDistribution copy = new NextSymbolsDistribution();
		copy.getSymbols().addAll(src.getSymbols());
		copy.getProbability().addAll(src.getProbability());
		copy.totalCtx = src.totalCtx;
		return copy;
	}

	public String getEdge() {
		// recupero l'edge relativo alla path scritta nella label
		String[] nodesLabel = this.getLabel().split("_");
		return nodesLabel[0];
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public void setDist(NextSymbolsDistribution dist) {
		this.dist = dist;
	}

	public NextSymbolsDistribution getDist() {
		return dist;
	}

	public ArrayList<VlmcNode> getChildren() {
		return this.children;
	}

//	public VlmcNode getChidByLabel(String l) {
//		if (this.getLabel().equals(l)) {
//			return this;
//		} else {
//			for (VlmcNode vlmcNode : this.children) {
//				if (vlmcNode.getLabel().equals(l)) {
//					return vlmcNode;
//				}
//			}
//		}
//		return null;
//	}
	
	public VlmcNode getChidByLabel(String l) {
		VlmcNode node=null; 
		for (VlmcNode vlmcNode : this.children) {
			if (vlmcNode.getLabel().equals(l)) {
				node=vlmcNode;
			}
		}
		return node;
	}

	public void addChild(VlmcNode c) {
		if (c != null) {
			boolean found = false;
			for (VlmcNode vlmcNode : children) {
				if (vlmcNode.getLabel().equals(c.getLabel())) {
					found = true;
					break;
				}
			}
			if (!found) {
				this.getChildren().add(c);
			} else {
				System.err.println("child already exist " + this.getLabel() + " " + c.getLabel());
				System.exit(1);
			}
		}
	}

	public String toString(String[] indent) {
		String tabs = StringUtils.join(indent, "");
		StringBuilder sb = new StringBuilder();
		try {
			sb.append(String.format("%s\"%s\"-{%s}\n", tabs, this.getLabel(), this.getDist().toString()));
		} catch (java.lang.NullPointerException e) {
			System.err.println(String.format("dump:%s %s", this.getLabel(), this.getDist()));
			e.printStackTrace();
			System.exit(1);
		}
		for (VlmcNode node : this.getChildren()) {
			sb.append(node.toString(ArrayUtils.addAll(indent, new String[] { "\t" })));
		}
		return sb.toString();
	}

	public VlmcNode getParent() {
		return parent;
	}

	public void setParent(VlmcNode parent) {
		this.parent = parent;
	}

	public ArrayList<String> getCtx() {
		ArrayList<String> ctx = label2Ctx(this.getLabel());
		VlmcNode p = this.getParent();
		while (p != null && p.getParent() != null) {
			// ctx.add(Integer.valueOf(p.getLabel()));
			ctx.addAll(label2Ctx(p.getLabel()));
			p = p.getParent();
		}
		return ctx;
	}

	private static ArrayList<String> label2Ctx(String label) {
		ArrayList<String> ctx = new ArrayList<String>();
		String[] nodeLabels = label.split("_");
		for (int j = nodeLabels.length - 1; j >= 0; j--) {
			ctx.add(nodeLabels[j]);
		}
		return ctx;
	}

	public double KullbackLeibler() {
		if (cachedKL != null) {
			return cachedKL;
		}
		double kl = 0.0;
		for (String symbol : this.getDist().symbols) {
			Double pChild = this.dist.getProbBySymbol(symbol);
			Double pParent = this.getParent().getDist().getProbBySymbol(symbol);
			if (pChild == null || Math.abs(pChild) < 1e-15) {
				continue;
			}
			if (pParent == null || Math.abs(pParent) < 1e-15) {
				cachedKL = Double.POSITIVE_INFINITY;
				return cachedKL;
			}
			kl += pChild * Math.log(pChild / pParent);
		}
		cachedKL = kl;
		return cachedKL;
	}

	public void invalidateKLCache() {
		cachedKL = null;
	}

	public void prune() {
		// compare the distribution of this node with its parent
		// if they are similar remove this node from the parent's child list

		// compute the next symbol distibution
		if (this.getDist() == null)
			this.setDist(EcfNavigator.createNextSymbolDistribution(this.getCtx()));

		if (this.getParent().getDist() == null)
			this.parent.setDist(EcfNavigator.createNextSymbolDistribution(this.getParent().getCtx()));

		if (!"root".equals(this.parent.getLabel())
				&& (this.KullbackLeibler() * this.dist.totalCtx <= fitVlmc.cutoff || this.dist.totalCtx < fitVlmc.k)) {
			// if(this.parent.getLabel()!="root" && this.maxDifference()<=fitVlmc.cutoff) {
			if (!this.getParent().getChildren().remove(this)) {
				System.out.println("not removed");
			}
		} else {
			// System.out.println(this.KullbackLeibler());
			VlmcRoot.nLeaves += 1;
		}
	}

	private double maxDifference() {
		double maxDifference = 0.0;
		for (String symbol : this.getDist().symbols) {
			if (Math.abs(this.dist.getProbBySymbol(symbol)
					- this.getParent().getDist().getProbBySymbol(symbol)) >= maxDifference) {
				maxDifference = Math
						.abs(this.dist.getProbBySymbol(symbol) - this.getParent().getDist().getProbBySymbol(symbol));
			}
		}
		return maxDifference;
	}

	@Override
	public Object clone() {
		VlmcNode node = null;
		try {
			node = (VlmcNode) super.clone();
		} catch (CloneNotSupportedException e) {
			System.out.println(e.getStackTrace());
		}
		node.children = new ArrayList<VlmcNode>();
		node.dist = copyDist(this.dist);
		node.cachedKL = null;
		for (VlmcNode child : this.children) {
			node.children.add((VlmcNode) child.clone());
			node.children.get(node.children.size() - 1).setParent(node);
		}
		return node;
	}

	public void computeOrder(int order) {
		if (!(this instanceof VlmcRoot))
			order++;

		if (VlmcRoot.order < order)
			VlmcRoot.order = order;

		for (VlmcNode vlmcNode : children) {
			vlmcNode.computeOrder(order);
		}
	}

}
