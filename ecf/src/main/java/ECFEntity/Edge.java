package ECFEntity;

import java.util.ArrayList;
import java.util.HashMap;

public class Edge {
	private String label=null;
	private Integer cost=0;
	private ArrayList<Edge> in=null;
	private ArrayList<Edge> out=null;
	public HashMap<ArrayList<Integer>, Boolean> sufDist=null; 
	
	public Edge(String label) {
		this.in=new ArrayList<Edge>();
		this.out=new ArrayList<Edge>();
		this.label=label;
		this.sufDist=new HashMap<ArrayList<Integer>, Boolean>();
	}
	
	public void setLabel(String label) { 
		this.label = label;
	}
	
	public String getLabel() {
		return label;
	}
	
	public void setIn(ArrayList<Edge> in) {
		this.in = in;
	}
	
	public ArrayList<Edge> getIn() {
		return in;
	}
	
	public void setOut(ArrayList<Edge> out) {
		this.out = out;
	}
	
	public ArrayList<Edge> getOut() {
		return out;
	}
	
	public void addInEdge(Edge e) {
		boolean found=false;
		for (Edge edge : in) {
			if(edge.getLabel().equals(e.getLabel())) {
				found=true;
				break;
			}
		}
		if(!found) {
			this.in.add(e);
		}
	}
	
	public void addOutEdge(Edge e) {
		boolean found=false;
		for (Edge edge : out) {
			if(edge.getLabel().equals(e.getLabel())) {
				found=true;
				break;
			}
		}
		if(!found) {
			this.out.add(e);
		}
	}
	
	public void setCost(Integer cost) {
		this.cost = cost;
	}
	
	public Integer getCost() {
		return cost;
	}
	
	@Override
	public String toString() {
		StringBuilder str=new StringBuilder();
		str.append("edge "+this.getLabel()+"{\n");
		str.append(String.format("\t cost %d ;\n", this.getCost()));
		str.append("\t in ");
		for (Edge edge : in) {
			if(in.indexOf(edge)!=0) {
				str.append(",");
			}
			str.append(edge.getLabel());
		}
		str.append(";\n");
		str.append("\t out ");
		for (Edge edge : out) {
			if(out.indexOf(edge)!=0) {
				str.append(",");
			}
			str.append(edge.getLabel());
		}
		str.append(";\n");
		str.append("\n};\n");
		return str.toString();
	}
}
