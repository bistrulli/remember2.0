package test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fitvlmc.fitVlmc;
import vlmc.NextSymbolsDistribution;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class PruningTest {

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

	private NextSymbolsDistribution makeDist(String[] symbols, double[] probs, double totalCtx) {
		NextSymbolsDistribution dist = new NextSymbolsDistribution();
		for (String s : symbols) dist.getSymbols().add(s);
		for (double p : probs) dist.getProbability().add(p);
		dist.totalCtx = totalCtx;
		return dist;
	}

	@Test
	public void testKLIdenticalDistributions() {
		VlmcRoot root = new VlmcRoot();
		root.setLabel("root");

		VlmcNode parent = new VlmcNode();
		parent.setLabel("parent");
		parent.setDist(makeDist(new String[]{"A", "B"}, new double[]{0.5, 0.5}, 10));
		parent.setParent(root);

		VlmcNode child = new VlmcNode();
		child.setLabel("child");
		child.setDist(makeDist(new String[]{"A", "B"}, new double[]{0.5, 0.5}, 10));
		child.setParent(parent);

		assertEquals(0.0, child.KullbackLeibler(), 1e-10);
	}

	@Test
	public void testKLDifferentDistributions() {
		VlmcRoot root = new VlmcRoot();
		root.setLabel("root");

		VlmcNode parent = new VlmcNode();
		parent.setLabel("parent");
		parent.setDist(makeDist(new String[]{"A", "B"}, new double[]{0.5, 0.5}, 10));
		parent.setParent(root);

		VlmcNode child = new VlmcNode();
		child.setLabel("child");
		child.setDist(makeDist(new String[]{"A", "B"}, new double[]{0.8, 0.2}, 10));
		child.setParent(parent);

		// KL = 0.8*ln(0.8/0.5) + 0.2*ln(0.2/0.5)
		double expected = 0.8 * Math.log(0.8 / 0.5) + 0.2 * Math.log(0.2 / 0.5);
		assertEquals(expected, child.KullbackLeibler(), 1e-10);
		assertTrue(child.KullbackLeibler() > 0);
	}

	@Test
	public void testKLWithZeroProbInChild() {
		VlmcRoot root = new VlmcRoot();
		root.setLabel("root");

		VlmcNode parent = new VlmcNode();
		parent.setLabel("parent");
		parent.setDist(makeDist(new String[]{"A", "B"}, new double[]{0.5, 0.5}, 10));
		parent.setParent(root);

		// Child has symbol with prob 0
		VlmcNode child = new VlmcNode();
		child.setLabel("child");
		child.setDist(makeDist(new String[]{"A", "B"}, new double[]{1.0, 0.0}, 10));
		child.setParent(parent);

		double kl = child.KullbackLeibler();
		assertFalse(Double.isNaN(kl), "KL should not be NaN");
		assertFalse(Double.isInfinite(kl), "KL should not be Infinite");
	}

	@Test
	public void testKLWithSymbolAbsentInParent() {
		VlmcRoot root = new VlmcRoot();
		root.setLabel("root");

		VlmcNode parent = new VlmcNode();
		parent.setLabel("parent");
		// Parent only has symbol A
		parent.setDist(makeDist(new String[]{"A"}, new double[]{1.0}, 10));
		parent.setParent(root);

		// Child has A and B
		VlmcNode child = new VlmcNode();
		child.setLabel("child");
		child.setDist(makeDist(new String[]{"A", "B"}, new double[]{0.7, 0.3}, 10));
		child.setParent(parent);

		double kl = child.KullbackLeibler();
		assertFalse(Double.isNaN(kl), "KL should not be NaN when parent lacks symbol");
		assertFalse(Double.isInfinite(kl), "KL should not be Infinite when parent lacks symbol");
	}

	@Test
	public void testKLNeverNaNFuzz() {
		// Fuzz test: random distributions should never produce NaN or Infinity
		VlmcRoot root = new VlmcRoot();
		root.setLabel("root");

		java.util.Random rng = new java.util.Random(42);
		for (int trial = 0; trial < 100; trial++) {
			double pA = rng.nextDouble();
			double pB = 1.0 - pA;
			VlmcNode parent = new VlmcNode();
			parent.setLabel("parent");
			parent.setDist(makeDist(new String[]{"A", "B"}, new double[]{pA, pB}, 10));
			parent.setParent(root);

			double cA = rng.nextDouble();
			double cB = 1.0 - cA;
			VlmcNode child = new VlmcNode();
			child.setLabel("child");
			child.setDist(makeDist(new String[]{"A", "B"}, new double[]{cA, cB}, 10));
			child.setParent(parent);

			double kl = child.KullbackLeibler();
			assertFalse(Double.isNaN(kl), "KL NaN at trial " + trial);
			assertFalse(Double.isInfinite(kl), "KL Infinite at trial " + trial);
		}
	}

	@Test
	public void testPruneRemovesNodeWhenKLLow() {
		fitVlmc.cutoff = 100.0; // High cutoff -> easy to prune
		fitVlmc.k = 1;

		VlmcRoot root = new VlmcRoot();
		root.setLabel("root");
		root.setDist(new NextSymbolsDistribution());

		VlmcNode parent = new VlmcNode();
		parent.setLabel("A");
		parent.setDist(makeDist(new String[]{"B", "end$"}, new double[]{0.5, 0.5}, 10));
		parent.setParent(root);
		root.getChildren().add(parent);

		// Child with similar distribution -> KL low -> should be pruned
		VlmcNode child = new VlmcNode();
		child.setLabel("C");
		child.setDist(makeDist(new String[]{"B", "end$"}, new double[]{0.5, 0.5}, 10));
		child.setParent(parent);
		parent.getChildren().add(child);

		// KL = 0 (identical), 0 * 10 = 0 <= 100.0 -> prune
		child.prune();
		assertEquals(0, parent.getChildren().size(), "Child should have been pruned");
	}

	@Test
	public void testPruneKeepsNodeWhenKLHigh() {
		fitVlmc.cutoff = 0.001; // Very low cutoff -> hard to prune
		fitVlmc.k = 1;

		VlmcRoot root = new VlmcRoot();
		root.setLabel("root");
		root.setDist(new NextSymbolsDistribution());

		VlmcNode parent = new VlmcNode();
		parent.setLabel("A");
		parent.setDist(makeDist(new String[]{"B", "end$"}, new double[]{0.5, 0.5}, 100));
		parent.setParent(root);
		root.getChildren().add(parent);

		// Child with very different distribution -> KL high -> should NOT be pruned
		VlmcNode child = new VlmcNode();
		child.setLabel("C");
		child.setDist(makeDist(new String[]{"B", "end$"}, new double[]{0.99, 0.01}, 100));
		child.setParent(parent);
		parent.getChildren().add(child);

		VlmcRoot.nLeaves = 0;
		child.prune();
		assertEquals(1, parent.getChildren().size(), "Child should NOT have been pruned");
	}

	@Test
	public void testPruneDoesNotRemoveRootChildren() {
		fitVlmc.cutoff = 1000.0; // Very high cutoff
		fitVlmc.k = 1;

		VlmcRoot root = new VlmcRoot();
		root.setLabel("root");
		root.setDist(new NextSymbolsDistribution());

		VlmcNode child = new VlmcNode();
		child.setLabel("A");
		child.setDist(makeDist(new String[]{"B"}, new double[]{1.0}, 10));
		child.setParent(root);
		root.getChildren().add(child);

		// prune() checks parent.getLabel() != "root" — should skip
		child.prune();
		assertEquals(1, root.getChildren().size(), "Root children should never be pruned");
	}
}
