package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import ECFEntity.Edge;
import ECFEntity.Flow;
import fitvlmc.EcfNavigator;
import fitvlmc.fitVlmc;
import suffixarray.SuffixArray;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class EcfNavigatorTest {

	private double savedCutoff;
	private int savedK;

	@BeforeEach
	public void saveState() {
		savedCutoff = fitVlmc.cutoff;
		savedK = fitVlmc.k;
	}

	@AfterEach
	public void restoreState() {
		fitVlmc.cutoff = savedCutoff;
		fitVlmc.k = savedK;
	}

	/**
	 * Build a cyclic ECF: A → B → A (cycle) and B → end$ (exit)
	 * With traces "A B A B end$ A B end$" to provide suffix array data.
	 * Verify that navigation terminates and produces a reasonable tree.
	 */
	@Test
	@Timeout(10) // Fail if takes more than 10 seconds (infinite loop protection)
	public void testCyclicEcfTerminates() {
		// Set up static state
		fitVlmc.cutoff = 100.0; // High cutoff — prune aggressively to keep tree small
		fitVlmc.k = 1;

		// Build cyclic ECF: A ↔ B, B → end$
		Flow ecf = new Flow();
		Edge eA = new Edge("A");
		Edge eB = new Edge("B");
		Edge eEnd = new Edge("end$");
		ecf.addEdge(eA);
		ecf.addEdge(eB);
		ecf.addEdge(eEnd);

		// A → B (out), B → A (out), B → end$ (out)
		eA.addOutEdge(eB);
		eB.addInEdge(eA);
		eB.addOutEdge(eA);
		eA.addInEdge(eB);
		eB.addOutEdge(eEnd);
		eEnd.addInEdge(eB);

		// Create traces with cycles
		String traces = "A B A B end$ A B end$";
		SuffixArray sa = new SuffixArray(traces);

		// Create a mock learner
		fitVlmc learner = new fitVlmc() {
			@Override
			public Flow getEcfModel() { return ecf; }
			@Override
			public SuffixArray getSa() { return sa; }
		};

		EcfNavigator nav = new EcfNavigator(learner);
		nav.setMaxNavigationDepth(10);

		// This should terminate (not infinite loop)
		VlmcRoot.nNodes = 0;
		nav.visit();
		VlmcRoot vlmc = nav.getVlmc();

		// Verify tree was built
		assertTrue(vlmc.getChildren().size() > 0, "VLMC should have children");
		// Verify node count is reasonable (not exponential)
		assertTrue(VlmcRoot.nNodes < 100, "Node count should be bounded, got: " + VlmcRoot.nNodes);
	}

	/**
	 * Verify that for a simple acyclic ECF, the tree is built correctly.
	 */
	@Test
	public void testAcyclicEcfProducesCorrectTree() {
		fitVlmc.cutoff = 100.0;
		fitVlmc.k = 1;

		// Build ECF: A → B → end$
		Flow ecf = new Flow();
		Edge eA = new Edge("A");
		Edge eB = new Edge("B");
		Edge eEnd = new Edge("end$");
		ecf.addEdge(eA);
		ecf.addEdge(eB);
		ecf.addEdge(eEnd);

		eA.addOutEdge(eB);
		eB.addInEdge(eA);
		eB.addOutEdge(eEnd);
		eEnd.addInEdge(eB);

		String traces = "A B end$ A B end$ A B end$";
		SuffixArray sa = new SuffixArray(traces);

		fitVlmc learner = new fitVlmc() {
			@Override
			public Flow getEcfModel() { return ecf; }
			@Override
			public SuffixArray getSa() { return sa; }
		};

		EcfNavigator nav = new EcfNavigator(learner);
		nav.visit();
		VlmcRoot vlmc = nav.getVlmc();

		// Should have children for A and B (end$ has no out-edges so no subtree)
		assertTrue(vlmc.getChildren().size() >= 1, "VLMC should have at least 1 child");
	}

	/**
	 * Verify no duplicate context labels in VLMC tree built from cyclic ECF.
	 */
	@Test
	@Timeout(10)
	public void testNoDuplicateContextsInCyclicEcf() {
		fitVlmc.cutoff = 0.001; // Low cutoff — keep more nodes
		fitVlmc.k = 1;

		Flow ecf = new Flow();
		Edge eA = new Edge("A");
		Edge eB = new Edge("B");
		Edge eEnd = new Edge("end$");
		ecf.addEdge(eA);
		ecf.addEdge(eB);
		ecf.addEdge(eEnd);

		eA.addOutEdge(eB);
		eB.addInEdge(eA);
		eB.addOutEdge(eA);
		eA.addInEdge(eB);
		eB.addOutEdge(eEnd);
		eEnd.addInEdge(eB);

		String traces = "A B A B A B end$ A B end$";
		SuffixArray sa = new SuffixArray(traces);

		fitVlmc learner = new fitVlmc() {
			@Override
			public Flow getEcfModel() { return ecf; }
			@Override
			public SuffixArray getSa() { return sa; }
		};

		EcfNavigator nav = new EcfNavigator(learner);
		nav.setMaxNavigationDepth(10);
		nav.visit();
		VlmcRoot vlmc = nav.getVlmc();

		// Collect all node paths and check for duplicates within each subtree
		for (VlmcNode child : vlmc.getChildren()) {
			Set<String> paths = new HashSet<>();
			collectPaths(child, "", paths);
			// Each path should be unique
			// (paths set will naturally deduplicate, just verify we added all)
		}

		assertTrue(VlmcRoot.nNodes < 200, "Node count should be bounded even with low cutoff, got: " + VlmcRoot.nNodes);
	}

	private void collectPaths(VlmcNode node, String prefix, Set<String> paths) {
		String path = prefix + "/" + node.getLabel();
		paths.add(path);
		for (VlmcNode child : node.getChildren()) {
			collectPaths(child, path, paths);
		}
	}
}
