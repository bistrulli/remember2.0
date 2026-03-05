package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import sta.StaWeightFunction;
import vlmc.NextSymbolsDistribution;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class StaWeightFunctionTest {

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
        return node;
    }

    @Test
    public void testKlBasedOnRootReturnsZero() {
        VlmcRoot root = new VlmcRoot();
        root.setLabel("root");
        StaWeightFunction fn = StaWeightFunction.klBased();

        VlmcNode child = createNodeWithDist("A", new String[] {"B"}, new double[] {1.0}, 10, root);

        // Child of root should return 0 (parent is VlmcRoot)
        assertEquals(0.0, fn.score(child), 1e-15);
    }

    @Test
    public void testKlBasedIdenticalDistributionsReturnsZero() {
        VlmcRoot root = new VlmcRoot();
        root.setLabel("root");

        VlmcNode parent =
                createNodeWithDist("A", new String[] {"X", "Y"}, new double[] {0.5, 0.5}, 100, root);
        root.getChildren().add(parent);

        VlmcNode child =
                createNodeWithDist("B", new String[] {"X", "Y"}, new double[] {0.5, 0.5}, 50, parent);
        parent.getChildren().add(child);

        StaWeightFunction fn = StaWeightFunction.klBased();
        assertEquals(0.0, fn.score(child), 1e-10, "Identical distributions should have KL=0, score=0");
    }

    @Test
    public void testKlBasedDifferentDistributionsReturnsPositive() {
        VlmcRoot root = new VlmcRoot();
        root.setLabel("root");

        VlmcNode parent =
                createNodeWithDist("A", new String[] {"X", "Y"}, new double[] {0.5, 0.5}, 100, root);
        root.getChildren().add(parent);

        VlmcNode child =
                createNodeWithDist("B", new String[] {"X", "Y"}, new double[] {0.9, 0.1}, 50, parent);
        parent.getChildren().add(child);

        StaWeightFunction fn = StaWeightFunction.klBased();
        double score = fn.score(child);
        assertTrue(score > 0, "Different distributions should produce positive score, got: " + score);
    }

    @Test
    public void testKlBasedHigherKlAndNGivesHigherScore() {
        VlmcRoot root = new VlmcRoot();
        root.setLabel("root");

        VlmcNode parent =
                createNodeWithDist("A", new String[] {"X", "Y"}, new double[] {0.5, 0.5}, 200, root);
        root.getChildren().add(parent);

        // Low KL, low n
        VlmcNode childLow =
                createNodeWithDist("B", new String[] {"X", "Y"}, new double[] {0.6, 0.4}, 10, parent);
        childLow.setParent(parent);

        // High KL, high n
        VlmcNode childHigh =
                createNodeWithDist("C", new String[] {"X", "Y"}, new double[] {0.95, 0.05}, 200, parent);
        childHigh.setParent(parent);

        StaWeightFunction fn = StaWeightFunction.klBased();
        assertTrue(
                fn.score(childHigh) > fn.score(childLow),
                "Higher KL + higher n should produce higher score");
    }

    @Test
    public void testKlBasedNullParentHandledGracefully() {
        VlmcNode orphan = new VlmcNode();
        orphan.setLabel("X");

        StaWeightFunction fn = StaWeightFunction.klBased();
        // Should not throw, should return 0
        assertEquals(0.0, fn.score(orphan), 1e-15);
    }
}
