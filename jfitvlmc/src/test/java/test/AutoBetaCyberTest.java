package test;

import static org.junit.jupiter.api.Assertions.*;

import fitvlmc.fitVlmc;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sta.AutoBetaSelector;
import sta.AutoBetaSelector.TreeStats;
import sta.StaPredictor;
import sta.StaResult;
import vlmc.VlmcRoot;

public class AutoBetaCyberTest {

    @TempDir File tempDir;

    private VlmcRoot vlmc;
    private CyberDatasetGenerator generator;

    @AfterEach
    public void cleanupStatics() throws Exception {
        resetFitVlmcStatics();
    }

    @BeforeEach
    public void trainVlmc() throws Exception {
        generator = new CyberDatasetGenerator(42L);

        List<List<String>> normalTraces = generator.generateTrainingNormal(4000);
        List<List<String>> attackTraces = generator.generateTrainingAttack(400);

        List<List<String>> allTraining = new ArrayList<>();
        allTraining.addAll(normalTraces);
        allTraining.addAll(attackTraces);

        File tracesFile = new File(tempDir, "training.txt");
        generator.writeTracesFile(allTraining, tracesFile);

        resetFitVlmcStatics();
        fitVlmc.alfa = 0.01f;
        fitVlmc.k = 1;
        fitVlmc.maxNavigationDepth = 25;

        fitVlmc learner = new fitVlmc();
        setStaticField("inFile", tracesFile.getAbsolutePath());

        learner.readInputTraces();
        learner.generateEcfFromTraces();

        ChiSquaredDistribution chi2 =
                new ChiSquaredDistribution(
                        Math.max(0.1, learner.getEcfModel().getEdges().size() - 1));
        fitVlmc.cutoff = chi2.inverseCumulativeProbability(fitVlmc.alfa) / 2;

        learner.createSuffixArray();
        learner.fit();

        this.vlmc = (VlmcRoot) getField(learner, "vlmc");
        assertNotNull(this.vlmc, "VLMC should be trained");
    }

    @Test
    public void testHeuristicBetaInReasonableRange() {
        AutoBetaSelector selector = new AutoBetaSelector();
        double betaH = selector.heuristicBeta(vlmc);

        System.out.println("\n=== Auto-Beta on Cyber Dataset ===");
        System.out.printf("Heuristic beta: %.4f%n", betaH);

        TreeStats stats = selector.computeTreeStats(vlmc);
        System.out.println("Tree stats: " + stats);

        assertTrue(betaH > 0, "Beta should be positive");
        assertTrue(Double.isFinite(betaH), "Beta should be finite");
        assertTrue(betaH > 0.001, "Beta should not be near zero, got: " + betaH);
        assertTrue(betaH < 1000, "Beta should not be huge, got: " + betaH);
    }

    @Test
    public void testCrossValidationFindsBeta() {
        AutoBetaSelector selector = new AutoBetaSelector();

        List<List<String>> testNormals = generator.generateTestNormal(200);
        List<List<String>> testAttacks = generator.generateTestAttackVariants(50);

        double[] candidates = {0.1, 0.5, 1.0, 2.0, 5.0, 10.0};
        double betaCV = selector.crossValidateBeta(vlmc, testNormals, testAttacks, candidates);

        System.out.printf("Cross-validated beta: %.1f%n", betaCV);

        assertTrue(betaCV >= 0.1, "CV beta should be in candidate range");
        assertTrue(betaCV <= 10.0, "CV beta should be in candidate range");
    }

    @Test
    public void testHeuristicBetaSeparationIsReasonable() {
        AutoBetaSelector selector = new AutoBetaSelector();
        double betaH = selector.heuristicBeta(vlmc);

        List<List<String>> testNormals = generator.generateTestNormal(500);
        List<List<String>> testAttacks = generator.generateTestAttackVariants(100);

        double sepH = computeSeparation(betaH, testNormals, testAttacks);

        double[] betas = {0.1, 0.5, 1.0, 2.0, 5.0, 10.0};
        double bestSep = 0;
        double bestBeta = 0;
        for (double beta : betas) {
            double sep = computeSeparation(beta, testNormals, testAttacks);
            System.out.printf("  beta=%.1f separation=%.4f%n", beta, sep);
            if (sep > bestSep) {
                bestSep = sep;
                bestBeta = beta;
            }
        }

        System.out.printf("Heuristic beta=%.4f separation=%.4f%n", betaH, sepH);
        System.out.printf("Best sweep beta=%.1f separation=%.4f%n", bestBeta, bestSep);

        assertTrue(
                sepH > 1.0,
                "Heuristic beta should produce separation > 1 (attacks scored higher), got: "
                        + sepH);
    }

    private double computeSeparation(
            double beta, List<List<String>> normals, List<List<String>> attacks) {
        StaPredictor sta = new StaPredictor(beta);
        double normalSum = 0;
        int normalCount = 0;
        for (List<String> trace : normals) {
            double score = traceScore(sta, trace);
            if (Double.isFinite(score)) {
                normalSum += score;
                normalCount++;
            }
        }

        double attackSum = 0;
        int attackCount = 0;
        for (List<String> trace : attacks) {
            double score = traceScore(sta, trace);
            if (Double.isFinite(score)) {
                attackSum += score;
                attackCount++;
            }
        }

        double meanNormal = normalCount > 0 ? normalSum / normalCount : 0;
        double meanAttack = attackCount > 0 ? attackSum / attackCount : 0;
        return meanNormal > 0 ? meanAttack / meanNormal : 0;
    }

    private double traceScore(StaPredictor sta, List<String> trace) {
        double total = 0;
        for (int t = 0; t < trace.size() - 1; t++) {
            List<String> history = trace.subList(0, t + 1);
            String next = trace.get(t + 1);
            StaResult result = sta.predict(vlmc, history);
            double score = result.getAnomalyScore(next);
            if (Double.isInfinite(score)) return Double.POSITIVE_INFINITY;
            total += score;
        }
        return total;
    }

    private void resetFitVlmcStatics() throws Exception {
        setStaticField("ecfModelPath", null);
        setStaticField("inFile", null);
        setStaticField("outFile", null);
        setStaticField("vlmcOutFile", null);
        setStaticField("ecfOutFile", null);
        setStaticField("vlmcFile", null);
        setStaticField("initCtx", null);
        setStaticField("cmpLik", null);
        setStaticField("nSim", -1);
        setStaticField("rnd", false);
        setStaticField("pred", false);
        setStaticField("pred_rest_port", null);
        fitVlmc.k = -1;
        fitVlmc.alfa = null;
        fitVlmc.cutoff = -1;
        fitVlmc.maxNavigationDepth = 25;
        VlmcRoot.order = -1;
        VlmcRoot.nNodes = -1;
        VlmcRoot.nLeaves = -1;
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field f = fitVlmc.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    private static Object getField(Object obj, String name) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(obj);
    }
}
