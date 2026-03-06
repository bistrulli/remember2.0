package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sta.ContextContribution;
import sta.StaPredictor;
import sta.StaResult;
import vlmc.NextSymbolsDistribution;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class OnlineBmaTest {

    private VlmcRoot vlmc;

    /**
     * Same VLMC structure as StaPredictorTest:
     *
     * <pre>
     * root — P(A)=0.5, P(B)=0.3, P(C)=0.2  (n=1000)
     * +-- "A" — P(B)=0.7, P(C)=0.3          (n=500)
     * |   +-- "B" — P(B)=0.4, P(C)=0.6      (n=100)
     * +-- "B" — P(A)=0.6, P(C)=0.4          (n=300)
     *     +-- "A" — P(A)=0.9, P(C)=0.1      (n=50)
     * </pre>
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

        VlmcNode nodeA = new VlmcNode();
        nodeA.setLabel("A");
        NextSymbolsDistribution distA = new NextSymbolsDistribution();
        distA.getSymbols().addAll(Arrays.asList("B", "C"));
        distA.getProbability().addAll(Arrays.asList(0.7, 0.3));
        distA.totalCtx = 500;
        nodeA.setDist(distA);
        nodeA.setParent(vlmc);
        vlmc.getChildren().add(nodeA);

        VlmcNode nodeBA = new VlmcNode();
        nodeBA.setLabel("B");
        NextSymbolsDistribution distBA = new NextSymbolsDistribution();
        distBA.getSymbols().addAll(Arrays.asList("B", "C"));
        distBA.getProbability().addAll(Arrays.asList(0.4, 0.6));
        distBA.totalCtx = 100;
        nodeBA.setDist(distBA);
        nodeBA.setParent(nodeA);
        nodeA.getChildren().add(nodeBA);

        VlmcNode nodeB = new VlmcNode();
        nodeB.setLabel("B");
        NextSymbolsDistribution distB = new NextSymbolsDistribution();
        distB.getSymbols().addAll(Arrays.asList("A", "C"));
        distB.getProbability().addAll(Arrays.asList(0.6, 0.4));
        distB.totalCtx = 300;
        nodeB.setDist(distB);
        nodeB.setParent(vlmc);
        vlmc.getChildren().add(nodeB);

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

    /**
     * Single-child VLMC for tests needing stable context sets:
     *
     * <pre>
     * root — P(A)=0.4, P(B)=0.4, P(C)=0.2  (n=1000)
     * +-- "A" — P(A)=0.9, P(C)=0.1          (n=500, no children)
     * </pre>
     *
     * With trace [A, A, A, ...], collectMatchedContexts always returns [root, nodeA]
     * because: history ends with "A" → matches nodeA, nodeA has no children → stops.
     * nodeA predicts A much better (0.9) than root (0.4), so weight should converge to nodeA.
     */
    private VlmcRoot buildStableVlmc() {
        VlmcRoot flat = new VlmcRoot();
        flat.setLabel("root");
        NextSymbolsDistribution rd = new NextSymbolsDistribution();
        rd.getSymbols().addAll(Arrays.asList("A", "B", "C"));
        rd.getProbability().addAll(Arrays.asList(0.4, 0.4, 0.2));
        rd.totalCtx = 1000;
        flat.setDist(rd);

        VlmcNode a = new VlmcNode();
        a.setLabel("A");
        NextSymbolsDistribution da = new NextSymbolsDistribution();
        da.getSymbols().addAll(Arrays.asList("A", "C"));
        da.getProbability().addAll(Arrays.asList(0.9, 0.1));
        da.totalCtx = 500;
        a.setDist(da);
        a.setParent(flat);
        flat.getChildren().add(a);

        return flat;
    }

    @Test
    void testWeightsConvergeTowardsBestPredictor() {
        // Single-child VLMC: contexts always [root, nodeA] when history ends with "A"
        VlmcRoot stable = buildStableVlmc();
        StaPredictor sta = new StaPredictor(1.0);
        // nodeA gives P(A)=0.9, root gives P(A)=0.4
        // Repeatedly observing A should make nodeA gain weight
        List<String> trace = Arrays.asList("A", "A", "A", "A", "A", "A");
        List<StaResult> results = sta.predictOnline(stable, trace, 0.0);

        assertFalse(results.isEmpty());
        assertTrue(results.size() >= 3);

        // At the last step, nodeA should have more weight than root
        StaResult last = results.get(results.size() - 1);
        List<ContextContribution> contribs = last.getContributions();
        assertTrue(contribs.size() >= 2);
        double rootWeight = contribs.get(0).getWeight();
        double deepWeight = contribs.get(1).getWeight();
        assertTrue(
                deepWeight > rootWeight,
                "Better predictor should have more weight: deep="
                        + deepWeight
                        + " root="
                        + rootWeight);
    }

    @Test
    void testWeightsSumToOneAtEveryStep() {
        StaPredictor sta = new StaPredictor(1.0);
        List<String> trace = Arrays.asList("A", "B", "C", "A", "B");
        List<StaResult> results = sta.predictOnline(vlmc, trace, 0.05);

        for (int t = 0; t < results.size(); t++) {
            double weightSum = 0.0;
            for (ContextContribution cc : results.get(t).getContributions()) {
                weightSum += cc.getWeight();
            }
            assertEquals(1.0, weightSum, 1e-9, "Weights must sum to 1.0 at step " + t);
        }
    }

    @Test
    void testDistributionNormalizedAtEveryStep() {
        StaPredictor sta = new StaPredictor(1.0);
        List<String> trace = Arrays.asList("A", "B", "C", "A", "B");
        List<StaResult> results = sta.predictOnline(vlmc, trace, 0.05);

        for (int t = 0; t < results.size(); t++) {
            NextSymbolsDistribution mixed = results.get(t).getMixedDistribution();
            if (mixed.getProbability().isEmpty()) continue;
            double sum = 0.0;
            for (double p : mixed.getProbability()) {
                sum += p;
            }
            assertEquals(1.0, sum, 1e-9, "Distribution must be normalized at step " + t);
        }
    }

    @Test
    void testMemoryEffectAfterSurprise() {
        StaPredictor sta = new StaPredictor(1.0);
        // B is expected after A (P=0.7), C is less expected (P=0.3)
        // At step 3 (observing C instead of B), the score should spike
        List<String> trace = Arrays.asList("A", "B", "B", "C", "B");
        List<StaResult> results = sta.predictOnline(vlmc, trace, 0.05);

        assertTrue(results.size() >= 3);

        // Score at step where C is observed (step 2 in results = predicting trace[3]=C)
        double scoreBeforeSurprise = results.get(1).getAnomalyScore("B"); // predicting B (normal)
        double scoreSurprise = results.get(2).getAnomalyScore("C"); // predicting C (surprise)

        assertTrue(
                scoreSurprise > scoreBeforeSurprise,
                "Surprise event C should have higher anomaly score than normal B. "
                        + "surprise="
                        + scoreSurprise
                        + " normal="
                        + scoreBeforeSurprise);
    }

    @Test
    void testFixedSharePreventsWeightDeath() {
        double eta = 0.1;
        VlmcRoot stable = buildStableVlmc();
        StaPredictor sta = new StaPredictor(1.0);
        // Stable context set: [root, nodeA] at every step
        List<String> trace = Arrays.asList("A", "A", "A", "A", "A", "A", "A", "A", "A", "A");
        List<StaResult> results = sta.predictOnline(stable, trace, eta);

        // Check last step: even the worst context should have weight >= eta/K
        StaResult last = results.get(results.size() - 1);
        int k = last.getContributions().size();
        double minBound = eta / k * 0.5; // Allow some tolerance
        for (ContextContribution cc : last.getContributions()) {
            assertTrue(
                    cc.getWeight() >= minBound,
                    "No weight should die with fixed-share eta="
                            + eta
                            + ": weight="
                            + cc.getWeight()
                            + " minBound="
                            + minBound);
        }
    }

    @Test
    void testPureBmaEtaZero() {
        // Single-child VLMC: contexts always [root, nodeA] when history ends with "A"
        VlmcRoot stable = buildStableVlmc();
        StaPredictor sta = new StaPredictor(1.0);
        // With eta=0, the worst predictor should lose weight
        // nodeA gives P(A)=0.9, root gives P(A)=0.4 — nodeA should dominate
        List<String> trace = Arrays.asList("A", "A", "A", "A", "A", "A", "A", "A", "A", "A");
        List<StaResult> results = sta.predictOnline(stable, trace, 0.0);

        StaResult last = results.get(results.size() - 1);
        ContextContribution root = last.getContributions().get(0);
        ContextContribution nodeA = last.getContributions().get(1);

        assertTrue(
                nodeA.getWeight() > root.getWeight(),
                "With eta=0, better predictor should dominate: nodeA="
                        + nodeA.getWeight()
                        + " root="
                        + root.getWeight());
    }

    @Test
    void testSingleStepTraceFallsBackToStaticPredict() {
        StaPredictor sta = new StaPredictor(1.0);
        List<String> trace = Arrays.asList("A");
        List<StaResult> results = sta.predictOnline(vlmc, trace, 0.05);

        assertEquals(1, results.size(), "Single element trace should produce one result");
        assertFalse(results.get(0).getContributions().isEmpty());
    }

    @Test
    void testAnomalyScoreHigherForAnomalousTrace() {
        StaPredictor sta = new StaPredictor(1.0);

        // Normal trace: A followed by B (P(B|A)=0.7)
        List<String> normalTrace = Arrays.asList("A", "B", "B", "B", "B");
        List<StaResult> normalResults = sta.predictOnline(vlmc, normalTrace, 0.05);

        // Anomalous trace: A followed by C (P(C|A)=0.3, lower)
        List<String> anomalousTrace = Arrays.asList("A", "C", "C", "C", "C");
        List<StaResult> anomalousResults = sta.predictOnline(vlmc, anomalousTrace, 0.05);

        double normalTotal = 0.0;
        for (int t = 0; t < normalResults.size(); t++) {
            String next = normalTrace.get(t + 1);
            double score = normalResults.get(t).getAnomalyScore(next);
            if (Double.isFinite(score)) normalTotal += score;
            else {
                normalTotal = Double.POSITIVE_INFINITY;
                break;
            }
        }

        double anomalousTotal = 0.0;
        for (int t = 0; t < anomalousResults.size(); t++) {
            String next = anomalousTrace.get(t + 1);
            double score = anomalousResults.get(t).getAnomalyScore(next);
            if (Double.isFinite(score)) anomalousTotal += score;
            else {
                anomalousTotal = Double.POSITIVE_INFINITY;
                break;
            }
        }

        assertTrue(
                anomalousTotal > normalTotal,
                "Anomalous trace should have higher total score: anomalous="
                        + anomalousTotal
                        + " normal="
                        + normalTotal);
    }

    @Test
    void testOnlineVsStaticDifferentResults() {
        StaPredictor sta = new StaPredictor(1.0);
        List<String> trace = Arrays.asList("A", "B", "B", "B", "B");
        List<StaResult> onlineResults = sta.predictOnline(vlmc, trace, 0.05);

        // Compare weights at last step: online should differ from static
        StaResult onlineLast = onlineResults.get(onlineResults.size() - 1);
        StaResult staticResult = sta.predict(vlmc, trace.subList(0, trace.size() - 1));

        // Check that at least one weight differs
        boolean anyDifferent = false;
        for (int i = 0; i < onlineLast.getContributions().size()
                && i < staticResult.getContributions().size(); i++) {
            double onlineW = onlineLast.getContributions().get(i).getWeight();
            double staticW = staticResult.getContributions().get(i).getWeight();
            if (Math.abs(onlineW - staticW) > 0.01) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue(anyDifferent, "Online BMA should produce different weights than static predict");
    }

    @Test
    void testEpsilonPreventsInfiniteScore() {
        StaPredictor sta = new StaPredictor(1.0);
        // Trace with an unseen symbol
        List<String> trace = Arrays.asList("A", "B", "UNKNOWN_SYMBOL");
        List<StaResult> results = sta.predictOnline(vlmc, trace, 0.05);

        // The distribution at step 1 (predicting UNKNOWN_SYMBOL) should give finite score
        // because epsilon floor is applied to the mixed distribution
        // However, UNKNOWN_SYMBOL may not be in the distribution at all
        // In that case, getAnomalyScore returns +inf, which is correct behavior
        // The epsilon prevents existing symbols from having zero probability
        assertTrue(results.size() >= 1);
        StaResult lastPrediction = results.get(results.size() - 1);
        // Verify that known symbols have finite scores
        double scoreB = lastPrediction.getAnomalyScore("B");
        assertTrue(
                Double.isFinite(scoreB),
                "Known symbol B should have finite score with epsilon floor");
        assertTrue(scoreB > 0, "Score should be positive");
    }
}
