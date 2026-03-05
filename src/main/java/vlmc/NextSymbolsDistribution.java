package vlmc;

import java.util.ArrayList;

import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.util.Pair;

public class NextSymbolsDistribution {
	ArrayList<Double> probability=null;
	ArrayList<String> symbols=null;
	EnumeratedDistribution nextState=null;
	public double totalCtx=0;
	
	public NextSymbolsDistribution() {
		this.probability=new ArrayList<Double>();
		this.symbols=new ArrayList<String>();
	}
	
	public ArrayList<String> getSymbols() {
		return symbols;
	}
	
	public ArrayList<Double> getProbability() {
		return probability;
	}
	
	public void setProbability(ArrayList<Double> prob) {
		this.probability=prob;
	}
	
	public Double getProbBySymbol(String symbol) {
		if(this.symbols.indexOf(symbol)==-1)
			return null;
		else
		return this.probability.get(this.symbols.indexOf(symbol));
	}
	
	public String getNextState() {		
		if(nextState==null) {
			ArrayList<Pair<String,Double>> itemWeights = new ArrayList<Pair<String,Double>>();
			for(int k=0; k<this.getProbability().size(); k++) {
			    itemWeights.add(new Pair<String, Double>(this.getSymbols().get(k), this.getProbability().get(k)));
			}
			this.nextState=new EnumeratedDistribution(new MersenneTwister(),itemWeights);
		}
		
		return this.nextState.sample().toString();
	}
	
	@Override
	public String toString() {
		StringBuilder sb=new StringBuilder("");
		for(int i=0; i<this.getProbability().size(); i++) {
			sb.append(String.format("[%f,\"%s\"]", this.getProbability().get(i),this.getSymbols().get(i)));
		}
		sb.append(String.format("-%d", Double.valueOf(this.totalCtx).intValue()));
		return sb.toString();
	}
}
