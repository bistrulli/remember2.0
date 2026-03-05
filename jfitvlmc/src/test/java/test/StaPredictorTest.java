package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sta.ContextContribution;
import sta.StaPredictor;
import sta.StaResult;
import sta.StaWeightFunction;
import vlmc.NextSymbolsDistribution;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class StaPredictorTest {

    private VlmcRoot vlmc;

    /**
     * Builds a 3-level VLMC by hand:
     *
     * <pre>
     * root — P(A)=0.5, P(B)=0.3, P(C)=0.2  (global prior, n=1000)
     * ├── "A" — P(B)=0.7, P(C)=0.3          (after A, n=500)
     * │   └── "B" — P(B)=0.4, P(C)=0.6      (after B→A, different from parent, n=100)
     * └── "B" — P(A)=0.6, P(C)=0.4          (after B, n=300)
     *     └── "A" — P(A)=0.9, P(C)=0.1      (after A→B, very different from parent, n=50)
     * </pre>
     *
     * Key properties for testing:
     * - Node B-under-A has KL > 0 (distribution differs from parent A)
     * - Node A-under-B has high KL (0.9/0.1 vs 0.6/0.4)
     * - Root has a broad distribution (low information)
     */
    @BeforeEach
    public void buildVlmc() {
        vlmc = new VlmcRoot();
        vlmc.setLabel("root");
        NextSymbolsDistribution rootDist = new NextSymbolsDistribution();
        rootDist.getSymbols().addAll(Arrays.asList("A", "B", "C"));
        rootDist.getProbability().addAll(Arrays.asList(0.5, 0.3, 0.2));
        rootDist.totalCtx = 1000;
        vlmc.setDist(rootDist);

        // Level 1: node "A" under root
        VlmcNode nodeA = new VlmcNode();
        nodeA.setLabel("A");
        NextSymbolsDistribution distA = new NextSymbolsDistribution();
        distA.getSymbols().addAll(Arrays.asList("B", "C"));
        distA.getProbability().addAll(Arrays.asList(0.7, 0.3));
        distA.totalCtx = 500;
        nodeA.setDist(distA);
        nodeA.setParent(vlmc);
        vlmc.getChildren().add(nodeA);

        // Level 2: node "B" under A (context: history ends with ...B, A)
        VlmcNode nodeBA = new VlmcNode();
        nodeBA.setLabel("B");
        NextSymbolsDistribution distBA = new NextSymbolsDistribution();
        distBA.getSymbols().addAll(Arrays.asList("B", "C"));
        distBA.getProbability().addAll(Arrays.asList(0.4, 0.6));
        distBA.totalCtx = 100;
        nodeBA.setDist(distBA);
        nodeBA.setParent(nodeA);
        nodeA.getChildren().add(nodeBA);

        // Level 1: node "B" under root
        VlmcNode nodeB = new VlmcNode();
        nodeB.setLabel("B");
        NextSymbolsDistribution distB = new NextSymbolsDistribution();
        distB.getSymbols().addAll(Arrays.asList("A", "C"));
        distB.getProbability().addAll(Arrays.asList(0.6, 0.4));
        distB.totalCtx = 300;
        nodeB.setDist(distB);
        nodeB.setParent(vlmc);
        vlmc.getChildren().add(nodeB);

        // Level 2: node "A" under B (context: history ends with ...A, B)
        VlmcNode nodeAB = new VlmcNode();
        nodeAB.setLabel("A");
        NextSymbolsDistribution distAB = new NextSymbolsDistribution();
        distAB.getSymbols().addAll(Arrays.asList("A", "C"));
        distAB.getProbability().addAll(Arrays.asList(0.9, 0.1));
        distAB.totalCtx = 50;
        nodeAB.setDist(distAB);
        nodeAB.setParent(nodeB);
        nodeB.getChildren().add(nodeAB);
    }

    @Test
    public void testConvergenceToVlmcClassicWithHighBeta() {
        StaPredictor sta = new StaPredictor(100.0);
        List<String> history = Arrays.asList("B", "A");

        StaResult result = sta.predict(vlmc, history);
        VlmcNode classicNode = vlmc.getState(new ArrayList<>(history));

        for (String symbol : classicNode.getDist().getSymbols()) {
            double staPr = result.getMixedDistribution().getProbBySymbol(symbol);
            double classicPr = classicNode.getDist().getProbBySymbol(symbol);
            assertEquals(classicPr, staPr, 0.01, "With high beta, STA should match VLMC for " + symbol);
        }
    }

    @Test
    public void testUniformWeightsWithZeroBeta() {
        StaPredictor sta = new StaPredictor(0.0);
        List<String> history = Arrays.asList("B", "A");

        StaResult result = sta.predict(vlmc, history);
        List<ContextContribution> contribs = result.getContributions();

        double expectedWeight = 1.0 / contribs.size();
        for (ContextContribution cc : contribs) {
            assertEquals(expectedWeight, cc.getWeight(), 1e-9, "With beta=0, all weights should be uniform");
        }
    }

    @Test
    public void testDistributionIsNormalized() {
        StaPredictor sta = new StaPredictor(1.0);
        List<String> history = Arrays.asList("B", "A");

        StaResult result = sta.predict(vlmc, history);
        NextSymbolsDistribution mixed = result.getMixedDistribution();

        double sum = 0.0;
        for (double p : mixed.getProbability()) {
            sum += p;
        }
        assertEquals(1.0, sum, 1e-9, "Mixed distribution must sum to 1.0");
    }

    @Test
    public void testWeightsSumToOne() {
        StaPredictor sta = new StaPredictor(1.0);
        List<String> history = Arrays.asList("B", "A");

        StaResult result = sta.predict(vlmc, history);

        double weightSum = 0.0;
        for (ContextContribution cc : result.getContributions()) {
            weightSum += cc.getWeight();
        }
        assertEquals(1.0, weightSum, 1e-9, "Weights must sum to 1.0");
    }

    @Test
    public void testRootContributes() {
        StaPredictor sta = new StaPredictor(1.0);
        List<String> history = Arrays.asList("A");

        StaResult result = sta.predict(vlmc, history);

        assertTrue(result.getContributions().size() >= 2, "Should have root + at least one matched context");
        assertEquals(0, result.getContributions().get(0).getDepth(), "First contribution should be root");
        assertTrue(result.getContributions().get(0).getWeight() > 0, "Root should have positive weight");
    }

    @Test
    public void testCollectsMultipleContexts() {
        StaPredictor sta = new StaPredictor(1.0);
        List<String> history = Arrays.asList("B", "A");

        StaResult result = sta.predict(vlmc, history);

        // Should collect: root, A (depth 1), B-under-A (depth 2)
        assertEquals(3, result.getContributions().size(), "Should collect root + 2 tree levels");
    }

    @Test
    public void testSymbolFromIntermediateContextAppears() {
        // Root has symbol "A" in its distribution, but node A (depth 1) does not have "A"
        // STA should still give P(A) > 0 thanks to root contribution
        StaPredictor sta = new StaPredictor(1.0);
        List<String> history = Arrays.asList("B", "A");

        StaResult result = sta.predict(vlmc, history);
        Double probA = result.getMixedDistribution().getProbBySymbol("A");

        assertNotNull(probA, "Symbol 'A' should appear in mixed distribution (from root)");
        assertTrue(probA > 0, "P(A) should be > 0 thanks to root contribution");
    }

    @Test
    public void testHighKlHighNContextGetsMoreWeight() {
        StaPredictor sta = new StaPredictor(1.0);

        // History A,B -> matched: root, B(depth1), A-under-B(depth2)
        // A-under-B has high KL (0.9/0.1 vs 0.6/0.4) and n=50
        List<String> history = Arrays.asList("A", "B");
        StaResult result = sta.predict(vlmc, history);

        List<ContextContribution> contribs = result.getContributions();
        // Deepest context (A-under-B) should have highest weight
        ContextContribution deepest = contribs.get(contribs.size() - 1);
        ContextContribution root = contribs.get(0);

        assertTrue(
                deepest.getWeight() > root.getWeight(),
                "Context with high KL should have more weight than root");
    }

    @Test
    public void testAnomalyScoreForUnseenSymbol() {
        StaPredictor sta = new StaPredictor(1.0);
        List<String> history = Arrays.asList("A");

        StaResult result = sta.predict(vlmc, history);
        double score = result.getAnomalyScore("UNKNOWN");

        assertEquals(Double.POSITIVE_INFINITY, score, "Unseen symbol should have infinite anomaly score");
    }

    @Test
    public void testAnomalyScoreForKnownSymbol() {
        StaPredictor sta = new StaPredictor(1.0);
        List<String> history = Arrays.asList("A");

        StaResult result = sta.predict(vlmc, history);
        double score = result.getAnomalyScore("B");

        assertTrue(score > 0 && score < Double.POSITIVE_INFINITY, "Known symbol should have finite positive score");
    }

    @Test
    public void testEmptyHistoryReturnsRootOnly() {
        StaPredictor sta = new StaPredictor(1.0);
        List<String> history = Arrays.asList();

        StaResult result = sta.predict(vlmc, history);

        assertEquals(1, result.getContributions().size(), "Empty history should return root only");
    }

    @Test
    public void testSingleContextWeight() {
        // If only one context matches, its weight should be 1.0
        StaPredictor sta = new StaPredictor(1.0, StaWeightFunction.klBased(), false);
        // With includeRoot=false and history="X" (not in tree), nothing matches
        // But with history "A", only nodeA matches (no root)
        List<String> history = Arrays.asList("A");

        StaResult result = sta.predict(vlmc, history);

        assertEquals(1, result.getContributions().size());
        assertEquals(1.0, result.getContributions().get(0).getWeight(), 1e-9);
    }
}
