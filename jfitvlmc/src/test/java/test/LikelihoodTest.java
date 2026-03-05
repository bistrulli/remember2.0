package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vlmc.NextSymbolsDistribution;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class LikelihoodTest {

	private VlmcRoot vlmc;

	/**
	 * Builds a simple VLMC by hand:
	 *
	 * root
	 * ├── "A" — P(B)=0.7, P(end$)=0.3
	 * └── "B" — P(A)=0.6, P(end$)=0.4
	 */
	@BeforeEach
	public void buildVlmc() {
		vlmc = new VlmcRoot();
		vlmc.setLabel("root");

		VlmcNode nodeA = new VlmcNode();
		nodeA.setLabel("A");
		NextSymbolsDistribution distA = new NextSymbolsDistribution();
		distA.getSymbols().add("B");
		distA.getSymbols().add("end$");
		distA.getProbability().add(0.7);
		distA.getProbability().add(0.3);
		distA.totalCtx = 10;
		nodeA.setDist(distA);
		nodeA.setParent(vlmc);
		vlmc.getChildren().add(nodeA);

		VlmcNode nodeB = new VlmcNode();
		nodeB.setLabel("B");
		NextSymbolsDistribution distB = new NextSymbolsDistribution();
		distB.getSymbols().add("A");
		distB.getSymbols().add("end$");
		distB.getProbability().add(0.6);
		distB.getProbability().add(0.4);
		distB.totalCtx = 10;
		nodeB.setDist(distB);
		nodeB.setParent(vlmc);
		vlmc.getChildren().add(nodeB);
	}

	@Test
	public void testKnownTraceLikelihood() {
		// Trace: A -> B -> end$
		// Step 1: state=A, P(B|A)=0.7, cumulative=0.7
		// Step 2: state=B (getState reverses [A,B] -> looks for B then A under B, no A child -> stays at B)
		//         P(end$|B)=0.4, cumulative=0.7*0.4=0.28
		ArrayList<String> trace = new ArrayList<>(Arrays.asList("A", "B", "end$"));
		ArrayList<Double> lik = vlmc.getLikelihood(trace);

		assertEquals(2, lik.size());
		assertEquals(0.7, lik.get(0), 1e-9);
		assertEquals(0.28, lik.get(1), 1e-9);
	}

	@Test
	public void testSingleStepLikelihood() {
		// Trace: A -> end$
		// Step 1: state=A, P(end$|A)=0.3, cumulative=0.3
		ArrayList<String> trace = new ArrayList<>(Arrays.asList("A", "end$"));
		ArrayList<Double> lik = vlmc.getLikelihood(trace);

		assertEquals(1, lik.size());
		assertEquals(0.3, lik.get(0), 1e-9);
	}

	@Test
	public void testUnknownSymbolLikelihoodIsZero() {
		// Trace: A -> X -> end$
		// Step 1: state=A, P(X|A)=null -> p=0, breaks early
		ArrayList<String> trace = new ArrayList<>(Arrays.asList("A", "X", "end$"));
		ArrayList<Double> lik = vlmc.getLikelihood(trace);

		assertEquals(1, lik.size());
		assertEquals(0.0, lik.get(0), 1e-9);
	}

	@Test
	public void testLongerTrace() {
		// Trace: A -> B -> A -> end$
		// Step 1: state=A, P(B|A)=0.7, cumulative=0.7
		// Step 2: state=B, P(A|B)=0.6, cumulative=0.7*0.6=0.42
		// Step 3: state=A, P(end$|A)=0.3, cumulative=0.42*0.3=0.126
		ArrayList<String> trace = new ArrayList<>(Arrays.asList("A", "B", "A", "end$"));
		ArrayList<Double> lik = vlmc.getLikelihood(trace);

		assertEquals(3, lik.size());
		assertEquals(0.7, lik.get(0), 1e-9);
		assertEquals(0.42, lik.get(1), 1e-9);
		assertEquals(0.126, lik.get(2), 1e-9);
	}

	@Test
	public void testLogLikelihoodAggregation() {
		// Verify that log-likelihood aggregation works correctly
		// Trace 1: A -> B -> end$ => final lik = 0.28
		// Trace 2: B -> A -> end$ => final lik = P(A|B)*P(end$|A) = 0.6*0.3 = 0.18
		ArrayList<String> trace1 = new ArrayList<>(Arrays.asList("A", "B", "end$"));
		ArrayList<String> trace2 = new ArrayList<>(Arrays.asList("B", "A", "end$"));

		ArrayList<Double> lik1 = vlmc.getLikelihood(trace1);
		ArrayList<Double> lik2 = vlmc.getLikelihood(trace2);

		double finalLik1 = lik1.get(lik1.size() - 1);
		double finalLik2 = lik2.get(lik2.size() - 1);

		double aggregateLogLik = Math.log(finalLik1) + Math.log(finalLik2);

		assertEquals(0.28, finalLik1, 1e-9);
		assertEquals(0.18, finalLik2, 1e-9);
		assertEquals(Math.log(0.28) + Math.log(0.18), aggregateLogLik, 1e-9);
	}

	@Test
	public void testZeroLikelihoodLogHandling() {
		// When likelihood is 0, log should be -Infinity
		ArrayList<String> trace = new ArrayList<>(Arrays.asList("A", "X", "end$"));
		ArrayList<Double> lik = vlmc.getLikelihood(trace);

		double finalLik = lik.get(lik.size() - 1);
		assertEquals(0.0, finalLik, 1e-9);

		double logLik = finalLik > 0 ? Math.log(finalLik) : Double.NEGATIVE_INFINITY;
		assertEquals(Double.NEGATIVE_INFINITY, logLik);
	}

	@Test
	public void testEmptyDistributionTrace() {
		// Single-element trace: no transitions to evaluate
		ArrayList<String> trace = new ArrayList<>(Arrays.asList("A"));
		ArrayList<Double> lik = vlmc.getLikelihood(trace);

		assertTrue(lik.isEmpty());
	}
}
