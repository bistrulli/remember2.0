package ECFEntity;

import antlr.ECFBaseListener;
import antlr.ECFParser.In_state_refContext;
import antlr.ECFParser.Out_state_refContext;
import antlr.ECFParser.StateContext;

public class ECFListener extends ECFBaseListener{
	
	private Flow ecfModel=null;
	
	public ECFListener() {
		this.ecfModel=new Flow(); 
	}
	
	public Flow getEcfModel() {
		return ecfModel;
	}

	
	@Override
	public void exitState(StateContext ctx) {
		super.exitState(ctx);
		//System.out.println(String.format("Adding state %s", ctx.edge_name().getText()));
		String eId=ctx.edge_name().getText();
		if(this.ecfModel.getEdges().get(eId)==null) {
			//creo l'edge se non esiste e gli assegno il cost
			Edge newEdge=new Edge(eId);
			this.ecfModel.addEdge(newEdge);
		}
		Edge e=this.ecfModel.getEdges().get(eId);
		e.setCost(Integer.valueOf(ctx.cost().getText()));
		
		//adding all exiting edge
		for(int i=0; i< ctx.out_state_ref().size(); i++) {
			Out_state_refContext out_ref = ctx.out_state_ref().get(i);
			String outId=out_ref.getText();
			if(this.ecfModel.getEdges().get(outId)==null) {
				//creo l'edge se non esiste
				this.ecfModel.addEdge(new Edge(outId));
			}
			Edge out=this.ecfModel.getEdges().get(outId);
			//verifico che gia non l'ho aggiunto nella lista degli archi uscenti
			if(!e.getOut().contains(out)) {
				e.getOut().add(out);
			}
		}
		//adding all incoming edge
		for(int i=0; i< ctx.in_state_ref().size(); i++) {
			In_state_refContext in_ref = ctx.in_state_ref().get(i);
			String inId=in_ref.getText();
			if(this.ecfModel.getEdges().get(inId)==null) {
				//creo l'edge se non esiste
				this.ecfModel.addEdge(new Edge(inId));
			}
			Edge in=this.ecfModel.getEdges().get(inId);
			//verifico che gia non l'ho aggiunto nella lista degli archi uscenti
			if(!e.getIn().contains(in)) {
				e.getIn().add(in);
			}
		}
		
	}
}
