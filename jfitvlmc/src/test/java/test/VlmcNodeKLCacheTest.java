package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import vlmc.NextSymbolsDistribution;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class VlmcNodeKLCacheTest {

    private VlmcNode createNodeWithDist(
            String label, String[] symbols, double[] probs, double totalCtx, VlmcNode parent) {
        VlmcNode node = new VlmcNode();
        node.setLabel(label);
        NextSymbolsDistribution dist = new NextSymbolsDistribution();
        for (String s : symbols) dist.getSymbols().add(s);
        for (double p : probs) dist.getProbability().add(p);
        dist.totalCtx = totalCtx;
        node.setDist(dist);
        node.setParent(parent);
        if (parent != null) {
            parent.getChildren().add(node);
        }
        return node;
    }

    @Test
    void testCachedKLMatchesDirectComputation() {
        VlmcRoot root = new VlmcRoot();
        root.setLabel("root");
        NextSymbolsDistribution rootDist = new NextSymbolsDistribution();
        rootDist.getSymbols().addAll(Arrays.asList("X", "Y"));
        rootDist.getProbability().addAll(Arrays.asList(0.5, 0.5));
        rootDist.totalCtx = 100;
        root.setDist(rootDist);

        VlmcNode parent =
                createNodeWithDist(
                        "A", new String[] {"X", "Y"}, new double[] {0.5, 0.5}, 100, root);
        VlmcNode child =
                createNodeWithDist(
                        "B", new String[] {"X", "Y"}, new double[] {0.8, 0.2}, 50, parent);

        // Manual KL computation: 0.8*ln(0.8/0.5) + 0.2*ln(0.2/0.5)
        double expectedKL = 0.8 * Math.log(0.8 / 0.5) + 0.2 * Math.log(0.2 / 0.5);

        double first = child.KullbackLeibler();
        double second = child.KullbackLeibler();

        assertEquals(expectedKL, first, 1e-12, "First call should match manual computation");
        assertEquals(first, second, 0.0, "Second call should return exact same value (cached)");
    }

    @Test
    void testCacheIsReused() {
        VlmcRoot root = new VlmcRoot();
        root.setLabel("root");
        NextSymbolsDistribution rootDist = new NextSymbolsDistribution();
        rootDist.getSymbols().addAll(Arrays.asList("X", "Y"));
        rootDist.getProbability().addAll(Arrays.asList(0.5, 0.5));
        rootDist.totalCtx = 100;
        root.setDist(rootDist);

        VlmcNode parent =
                createNodeWithDist(
                        "A", new String[] {"X", "Y"}, new double[] {0.5, 0.5}, 100, root);
        VlmcNode child =
                createNodeWithDist(
                        "B", new String[] {"X", "Y"}, new double[] {0.9, 0.1}, 50, parent);

        double val1 = child.KullbackLeibler();
        double val2 = child.KullbackLeibler();
        double val3 = child.KullbackLeibler();

        // Bit-exact equality (same Double object returned from cache)
        assertEquals(val1, val2, 0.0);
        assertEquals(val2, val3, 0.0);
    }

    @Test
    void testInvalidateCacheForcesRecalculation() {
        VlmcRoot root = new VlmcRoot();
        root.setLabel("root");
        NextSymbolsDistribution rootDist = new NextSymbolsDistribution();
        rootDist.getSymbols().addAll(Arrays.asList("X", "Y"));
        rootDist.getProbability().addAll(Arrays.asList(0.5, 0.5));
        rootDist.totalCtx = 100;
        root.setDist(rootDist);

        VlmcNode parent =
                createNodeWithDist(
                        "A", new String[] {"X", "Y"}, new double[] {0.5, 0.5}, 100, root);
        VlmcNode child =
                createNodeWithDist(
                        "B", new String[] {"X", "Y"}, new double[] {0.8, 0.2}, 50, parent);

        double before = child.KullbackLeibler();

        // Change parent distribution and invalidate
        parent.getDist().getProbability().set(0, 0.9);
        parent.getDist().getProbability().set(1, 0.1);
        child.invalidateKLCache();

        double after = child.KullbackLeibler();

        assertNotEquals(before, after, "After invalidation and dist change, KL should differ");
    }

    @Test
    void testCloneDoesNotCopyCache() {
        VlmcRoot root = new VlmcRoot();
        root.setLabel("root");
        NextSymbolsDistribution rootDist = new NextSymbolsDistribution();
        rootDist.getSymbols().addAll(Arrays.asList("X", "Y"));
        rootDist.getProbability().addAll(Arrays.asList(0.5, 0.5));
        rootDist.totalCtx = 100;
        root.setDist(rootDist);

        VlmcNode parent =
                createNodeWithDist(
                        "A", new String[] {"X", "Y"}, new double[] {0.5, 0.5}, 100, root);
        VlmcNode child =
                createNodeWithDist(
                        "B", new String[] {"X", "Y"}, new double[] {0.8, 0.2}, 50, parent);

        // Warm the cache
        child.KullbackLeibler();

        // Clone the child
        VlmcNode cloned = (VlmcNode) child.clone();
        cloned.setParent(parent);

        // The clone should compute its own KL (not reuse cached from original)
        // Since parent is the same, the value should be the same but independently computed
        double clonedKL = cloned.KullbackLeibler();
        assertEquals(child.KullbackLeibler(), clonedKL, 1e-12, "Clone KL should match original");
    }

    @Test
    void testCopyConstructorDoesNotCopyCache() {
        VlmcRoot root = new VlmcRoot();
        root.setLabel("root");
        NextSymbolsDistribution rootDist = new NextSymbolsDistribution();
        rootDist.getSymbols().addAll(Arrays.asList("X", "Y"));
        rootDist.getProbability().addAll(Arrays.asList(0.5, 0.5));
        rootDist.totalCtx = 100;
        root.setDist(rootDist);

        VlmcNode parent =
                createNodeWithDist(
                        "A", new String[] {"X", "Y"}, new double[] {0.5, 0.5}, 100, root);
        VlmcNode child =
                createNodeWithDist(
                        "B", new String[] {"X", "Y"}, new double[] {0.8, 0.2}, 50, parent);

        // Warm the cache
        child.KullbackLeibler();

        // Copy constructor
        VlmcNode copied = new VlmcNode(child);
        copied.setParent(parent);

        double copiedKL = copied.KullbackLeibler();
        assertEquals(child.KullbackLeibler(), copiedKL, 1e-12, "Copy KL should match original");
    }

    @Test
    void testCacheWithInfiniteKL() {
        VlmcRoot root = new VlmcRoot();
        root.setLabel("root");
        NextSymbolsDistribution rootDist = new NextSymbolsDistribution();
        rootDist.getSymbols().addAll(Arrays.asList("X", "Y"));
        rootDist.getProbability().addAll(Arrays.asList(1.0, 0.0));
        rootDist.totalCtx = 100;
        root.setDist(rootDist);

        VlmcNode parent =
                createNodeWithDist(
                        "A", new String[] {"X", "Y"}, new double[] {1.0, 0.0}, 100, root);
        // Child has P(Y)=0.5 but parent has P(Y)=0 → KL = +infinity
        VlmcNode child =
                createNodeWithDist(
                        "B", new String[] {"X", "Y"}, new double[] {0.5, 0.5}, 50, parent);

        double kl1 = child.KullbackLeibler();
        double kl2 = child.KullbackLeibler();

        assertEquals(Double.POSITIVE_INFINITY, kl1);
        assertEquals(Double.POSITIVE_INFINITY, kl2);
    }

    @Test
    void testCacheWithZeroKL() {
        VlmcRoot root = new VlmcRoot();
        root.setLabel("root");
        NextSymbolsDistribution rootDist = new NextSymbolsDistribution();
        rootDist.getSymbols().addAll(Arrays.asList("X", "Y"));
        rootDist.getProbability().addAll(Arrays.asList(0.5, 0.5));
        rootDist.totalCtx = 100;
        root.setDist(rootDist);

        VlmcNode parent =
                createNodeWithDist(
                        "A", new String[] {"X", "Y"}, new double[] {0.6, 0.4}, 100, root);
        // Same distribution as parent → KL = 0
        VlmcNode child =
                createNodeWithDist(
                        "B", new String[] {"X", "Y"}, new double[] {0.6, 0.4}, 50, parent);

        double kl1 = child.KullbackLeibler();
        double kl2 = child.KullbackLeibler();

        assertEquals(0.0, kl1, 1e-15, "Identical distributions should have KL=0");
        assertEquals(0.0, kl2, 1e-15, "Cached KL=0 should be returned");
    }
}
