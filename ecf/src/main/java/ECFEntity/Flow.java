package ECFEntity;

import java.util.TreeMap;

public class Flow {
	private TreeMap<String, Edge> edges=null;
	
	public Flow() {
		this.edges=new TreeMap<String, Edge>();
	}
	
	public TreeMap<String, Edge> getEdges() {
		return edges;
	}
	
	public void setEdges(TreeMap<String, Edge> edges) {
		this.edges = edges;
	}
	
	public void addEdge(Edge e) {
		this.edges.put(e.getLabel(), e);
	}
	
	@Override
	public String toString() {
		StringBuilder str=new StringBuilder();
		str.append("FLOW{\n");
		for (String key : this.edges.keySet()) {
			str.append(this.edges.get(key).toString());
		}
		str.append("}\n");
		return str.toString();
	}
}
