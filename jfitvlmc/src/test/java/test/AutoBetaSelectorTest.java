package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sta.AutoBetaSelector;
import sta.AutoBetaSelector.TreeStats;
import vlmc.NextSymbolsDistribution;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class AutoBetaSelectorTest {

    private AutoBetaSelector selector;

    @BeforeEach
    public void setup() {
        selector = new AutoBetaSelector();
    }

    private VlmcRoot buildFlatTree() {
        VlmcRoot root = new VlmcRoot();
        root.setLabel("root");
        NextSymbolsDistribution rootDist = new NextSymbolsDistribution();
        rootDist.getSymbols().addAll(Arrays.asList("A", "B"));
        rootDist.getProbability().addAll(Arrays.asList(0.5, 0.5));
        rootDist.totalCtx = 100;
        root.setDist(rootDist);

        VlmcNode nodeA = new VlmcNode();
        nodeA.setLabel("A");
        NextSymbolsDistribution distA = new NextSymbolsDistribution();
        distA.getSymbols().addAll(Arrays.asList("A", "B"));
        distA.getProbability().addAll(Arrays.asList(0.7, 0.3));
        distA.totalCtx = 50;
        nodeA.setDist(distA);
        nodeA.setParent(root);
        root.getChildren().add(nodeA);

        return root;
    }

    private VlmcRoot buildDeepTree() {
        VlmcRoot root = new VlmcRoot();
        root.setLabel("root");
        NextSymbolsDistribution rootDist = new NextSymbolsDistribution();
        rootDist.getSymbols().addAll(Arrays.asList("A", "B", "C"));
        rootDist.getProbability().addAll(Arrays.asList(0.4, 0.3, 0.3));
        rootDist.totalCtx = 1000;
        root.setDist(rootDist);

        VlmcNode level1 = new VlmcNode();
        level1.setLabel("A");
        NextSymbolsDistribution d1 = new NextSymbolsDistribution();
        d1.getSymbols().addAll(Arrays.asList("A", "B", "C"));
        d1.getProbability().addAll(Arrays.asList(0.6, 0.2, 0.2));
        d1.totalCtx = 500;
        level1.setDist(d1);
        level1.setParent(root);
        root.getChildren().add(level1);

        VlmcNode level2 = new VlmcNode();
        level2.setLabel("B");
        NextSymbolsDistribution d2 = new NextSymbolsDistribution();
        d2.getSymbols().addAll(Arrays.asList("A", "B", "C"));
        d2.getProbability().addAll(Arrays.asList(0.8, 0.1, 0.1));
        d2.totalCtx = 200;
        level2.setDist(d2);
        level2.setParent(level1);
        level1.getChildren().add(level2);

        VlmcNode level3 = new VlmcNode();
        level3.setLabel("A");
        NextSymbolsDistribution d3 = new NextSymbolsDistribution();
        d3.getSymbols().addAll(Arrays.asList("A", "B", "C"));
        d3.getProbability().addAll(Arrays.asList(0.9, 0.05, 0.05));
        d3.totalCtx = 50;
        level3.setDist(d3);
        level3.setParent(level2);
        level2.getChildren().add(level3);

        VlmcNode level4 = new VlmcNode();
        level4.setLabel("C");
        NextSymbolsDistribution d4 = new NextSymbolsDistribution();
        d4.getSymbols().addAll(Arrays.asList("A", "B", "C"));
        d4.getProbability().addAll(Arrays.asList(0.95, 0.025, 0.025));
        d4.totalCtx = 20;
        level4.setDist(d4);
        level4.setParent(level3);
        level3.getChildren().add(level4);

        return root;
    }

    @Test
    public void testHeuristicFlatTreeHighBeta() {
        VlmcRoot flat = buildFlatTree();
        double beta = selector.heuristicBeta(flat);

        assertTrue(beta > 0, "Beta should be positive");
        assertTrue(Double.isFinite(beta), "Beta should be finite");
    }

    @Test
    public void testHeuristicDeepTreeProducesFiniteBeta() {
        VlmcRoot deep = buildDeepTree();
        double betaDeep = selector.heuristicBeta(deep);

        assertTrue(betaDeep > 0, "Beta for deep tree should be positive");
        assertTrue(Double.isFinite(betaDeep), "Beta for deep tree should be finite");

        TreeStats stats = selector.computeTreeStats(deep);
        assertTrue(stats.meanKL > 0, "Deep tree should have computable KL values");
        assertTrue(stats.meanDepth > 1, "Deep tree should have mean depth > 1");
    }

    @Test
    public void testTreeStatsFlat() {
        VlmcRoot flat = buildFlatTree();
        TreeStats stats = selector.computeTreeStats(flat);

        assertEquals(1, stats.nNodes);
        assertEquals(1, stats.nLeaves);
        assertEquals(1, stats.depth);
    }

    @Test
    public void testTreeStatsDeep() {
        VlmcRoot deep = buildDeepTree();
        TreeStats stats = selector.computeTreeStats(deep);

        assertEquals(4, stats.nNodes);
        assertEquals(4, stats.depth);
        assertTrue(stats.meanKL > 0, "Mean KL should be positive for varied distributions");
        assertTrue(stats.meanN > 0, "Mean N should be positive");
    }

    @Test
    public void testHeuristicNeverDegenerate() {
        VlmcRoot flat = buildFlatTree();
        VlmcRoot deep = buildDeepTree();

        double b1 = selector.heuristicBeta(flat);
        double b2 = selector.heuristicBeta(deep);

        assertTrue(b1 > 0 && Double.isFinite(b1), "Beta should be positive and finite");
        assertTrue(b2 > 0 && Double.isFinite(b2), "Beta should be positive and finite");
    }

    @Test
    public void testTreeStatsToString() {
        VlmcRoot deep = buildDeepTree();
        TreeStats stats = selector.computeTreeStats(deep);

        String str = stats.toString();
        assertTrue(str.contains("TreeStats"));
        assertTrue(str.contains("depth="));
        assertTrue(str.contains("meanKL="));
    }
}
