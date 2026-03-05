package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vlmc.NextSymbolsDistribution;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class VlmcNavigationTest {

	private VlmcRoot vlmc;
	private VlmcNode nodeA;
	private VlmcNode nodeB;
	private VlmcNode nodeBA; // child of A with label "B" (context: after B then A)

	/**
	 * Builds a VLMC with depth 2:
	 *
	 * root
	 * +-- "A" P(B)=0.7, P(end$)=0.3
	 * |   +-- "B" P(A)=0.5, P(end$)=0.5   (context: ...B A, meaning "B came before A")
	 * +-- "B" P(A)=0.6, P(end$)=0.4
	 */
	@BeforeEach
	public void buildVlmc() {
		vlmc = new VlmcRoot();
		vlmc.setLabel("root");
		vlmc.setDist(new NextSymbolsDistribution());

		nodeA = new VlmcNode();
		nodeA.setLabel("A");
		NextSymbolsDistribution distA = new NextSymbolsDistribution();
		distA.getSymbols().add("B");
		distA.getSymbols().add("end$");
		distA.getProbability().add(0.7);
		distA.getProbability().add(0.3);
		distA.totalCtx = 20;
		nodeA.setDist(distA);
		nodeA.setParent(vlmc);
		vlmc.getChildren().add(nodeA);

		// Child of A: label "B" means "B is the predecessor of A"
		// getState navigates backwards: for ctx [A, B, A], it looks for
		// root -> A (last) -> B (second-to-last)
		nodeBA = new VlmcNode();
		nodeBA.setLabel("B");
		NextSymbolsDistribution distBA = new NextSymbolsDistribution();
		distBA.getSymbols().add("A");
		distBA.getSymbols().add("end$");
		distBA.getProbability().add(0.5);
		distBA.getProbability().add(0.5);
		distBA.totalCtx = 10;
		nodeBA.setDist(distBA);
		nodeBA.setParent(nodeA);
		nodeA.getChildren().add(nodeBA);

		nodeB = new VlmcNode();
		nodeB.setLabel("B");
		NextSymbolsDistribution distB = new NextSymbolsDistribution();
		distB.getSymbols().add("A");
		distB.getSymbols().add("end$");
		distB.getProbability().add(0.6);
		distB.getProbability().add(0.4);
		distB.totalCtx = 15;
		nodeB.setDist(distB);
		nodeB.setParent(vlmc);
		vlmc.getChildren().add(nodeB);
	}

	@Test
	public void testGetStateExactMatch() {
		// ctx = [A] -> getState traverses from last element: root.getChidByLabel("A") = nodeA
		ArrayList<String> ctx = new ArrayList<>(Arrays.asList("A"));
		VlmcNode state = vlmc.getState(ctx);
		assertEquals("A", state.getLabel());
		assertSame(nodeA, state);
	}

	@Test
	public void testGetStateDeepMatch() {
		// ctx = [A, B, A] -> getState:
		//   i=2: root.getChidByLabel("A") = nodeA
		//   i=1: nodeA.getChidByLabel("B") = nodeBA
		//   i=0: nodeBA.getChidByLabel("A") = null -> stop
		// Returns nodeBA (deepest match)
		ArrayList<String> ctx = new ArrayList<>(Arrays.asList("A", "B", "A"));
		VlmcNode state = vlmc.getState(ctx);
		assertSame(nodeBA, state, "Should navigate to the deepest matching node");
	}

	@Test
	public void testGetStatePartialMatch() {
		// ctx = [X, A] -> getState:
		//   i=1: root.getChidByLabel("A") = nodeA
		//   i=0: nodeA.getChidByLabel("X") = null -> stop
		// Returns nodeA (longest prefix match)
		ArrayList<String> ctx = new ArrayList<>(Arrays.asList("X", "A"));
		VlmcNode state = vlmc.getState(ctx);
		assertSame(nodeA, state);
	}

	@Test
	public void testGetStateUnknownContext() {
		// ctx = [X] -> root.getChidByLabel("X") = null -> returns root
		ArrayList<String> ctx = new ArrayList<>(Arrays.asList("X"));
		VlmcNode state = vlmc.getState(ctx);
		assertSame(vlmc, state, "Unknown context should return root");
	}

	@Test
	public void testGetLikelihoodCoherenceWithGetState() {
		// Trace: A, B, A, end$
		// Manual calculation:
		// Step 0: state = getState([A]) = nodeA, P(B|A) = 0.7, cumP = 0.7
		// Step 1: state = getState([A,B]) = nodeB (root->B, since last elem is B), P(A|B) = 0.6, cumP = 0.42
		// Step 2: state = getState([A,B,A]) = nodeBA (root->A->B), P(end$|BA) = 0.5, cumP = 0.21
		ArrayList<String> trace = new ArrayList<>(Arrays.asList("A", "B", "A", "end$"));
		ArrayList<Double> lik = vlmc.getLikelihood(trace);

		assertEquals(3, lik.size());
		assertEquals(0.7, lik.get(0), 1e-9, "P(B|ctx=[A])");
		assertEquals(0.42, lik.get(1), 1e-9, "P(A|ctx=[A,B])");
		assertEquals(0.21, lik.get(2), 1e-9, "P(end$|ctx=[A,B,A])");
	}

	@Test
	public void testGetLikelihoodUnknownSymbol() {
		// Trace: A, X, end$ -> P(X|A) = null -> p=0, break
		ArrayList<String> trace = new ArrayList<>(Arrays.asList("A", "X", "end$"));
		ArrayList<Double> lik = vlmc.getLikelihood(trace);

		assertEquals(1, lik.size());
		assertEquals(0.0, lik.get(0), 1e-9);
	}

	@Test
	public void testGetStateNavigationIsBackward() {
		// Verify that getState navigates from last element of ctx backward
		// ctx = [B, A] -> i starts at 1 (last): root.getChidByLabel("A") = nodeA
		//                  i=0: nodeA.getChidByLabel("B") = nodeBA
		ArrayList<String> ctx = new ArrayList<>(Arrays.asList("B", "A"));
		VlmcNode state = vlmc.getState(ctx);
		assertSame(nodeBA, state, "Navigation should go from last to first element");
	}
}
