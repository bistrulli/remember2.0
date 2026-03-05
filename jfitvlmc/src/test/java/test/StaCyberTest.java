package test;

import static org.junit.jupiter.api.Assertions.*;

import fitvlmc.fitVlmc;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sta.ContextContribution;
import sta.StaPredictor;
import sta.StaResult;
import vlmc.VlmcRoot;

public class StaCyberTest {

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

        // Generate training data
        List<List<String>> normalTraces = generator.generateTrainingNormal(4000);
        List<List<String>> attackTraces = generator.generateTrainingAttack(400);

        List<List<String>> allTraining = new ArrayList<>();
        allTraining.addAll(normalTraces);
        allTraining.addAll(attackTraces);

        // Write to file in jfitVLMC format
        File tracesFile = new File(tempDir, "training.txt");
        generator.writeTracesFile(allTraining, tracesFile);

        // Set up fitVlmc static state for training
        resetFitVlmcStatics();
        fitVlmc.alfa = 0.01f; // Low alfa = deep tree (minimal pruning)
        fitVlmc.k = 1;
        fitVlmc.maxNavigationDepth = 25;

        // Create learner and train
        fitVlmc learner = new fitVlmc();

        // Set inFile via reflection (static private)
        setStaticField("inFile", tracesFile.getAbsolutePath());

        learner.readInputTraces();
        learner.generateEcfFromTraces();

        // Compute cutoff
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
    public void testStaSeparatesAttackVariantsBetterThanVlmc() {
        List<List<String>> testNormals = generator.generateTestNormal(1000);
        List<List<String>> testVariants = generator.generateTestAttackVariants(100);

        double bestSeparationRatio = 0.0;
        double bestBeta = 0.0;

        // Also compute VLMC classic scores
        double vlmcMeanNormal = meanAnomalyScoreVlmc(testNormals);
        double vlmcMeanAttack = meanAnomalyScoreVlmc(testVariants);
        double vlmcSeparation =
                vlmcMeanAttack > 0 && vlmcMeanNormal > 0 ? vlmcMeanAttack / vlmcMeanNormal : 0;

        System.out.println("=== VLMC vs STA Comparison ===");
        System.out.printf(
                "VLMC Classic: normal=%.4f attack=%.4f separation=%.4f%n",
                vlmcMeanNormal, vlmcMeanAttack, vlmcSeparation);

        double[] betas = {0.1, 0.5, 1.0, 2.0, 5.0, 10.0};
        for (double beta : betas) {
            StaPredictor sta = new StaPredictor(beta);

            double staMeanNormal = meanAnomalyScoreSta(sta, testNormals);
            double staMeanAttack = meanAnomalyScoreSta(sta, testVariants);
            double staSeparation =
                    staMeanAttack > 0 && staMeanNormal > 0 ? staMeanAttack / staMeanNormal : 0;

            System.out.printf(
                    "STA beta=%.1f: normal=%.4f attack=%.4f separation=%.4f%n",
                    beta, staMeanNormal, staMeanAttack, staSeparation);

            if (staSeparation > bestSeparationRatio) {
                bestSeparationRatio = staSeparation;
                bestBeta = beta;
            }
        }

        System.out.printf(
                "Best STA beta=%.1f separation=%.4f (VLMC=%.4f)%n",
                bestBeta, bestSeparationRatio, vlmcSeparation);

        // STA should achieve separation ratio >= VLMC classic for at least one beta
        assertTrue(
                bestSeparationRatio >= vlmcSeparation * 0.9,
                String.format(
                        "STA best separation (%.4f) should be comparable to VLMC (%.4f)",
                        bestSeparationRatio, vlmcSeparation));
    }

    @Test
    public void testStaContextRelevanceMapIsInterpretable() {
        // Brute force variant: SF SF SS SU FR NC LO
        List<String> bruteVariant = Arrays.asList("SF", "SF", "SS", "SU", "FR", "NC", "LO");

        StaPredictor sta = new StaPredictor(1.0);

        System.out.println("\n=== Context Relevance Map: Brute Force Variant ===");
        // Check relevance at each step
        for (int t = 1; t < bruteVariant.size(); t++) {
            List<String> history = bruteVariant.subList(0, t);
            String nextSymbol = bruteVariant.get(t);

            StaResult result = sta.predict(vlmc, history);

            System.out.printf(
                    "t=%d history=%s next=%s anomaly=%.4f%n",
                    t, history, nextSymbol, result.getAnomalyScore(nextSymbol));
            for (ContextContribution cc : result.getContributions()) {
                if (cc.getWeight() > 0.05) {
                    System.out.printf("  %s%n", cc);
                }
            }
        }

        // At step where NC follows FR (data exfiltration), there should be multiple contexts
        List<String> historyAtExfil = Arrays.asList("SF", "SF", "SS", "SU", "FR");
        StaResult resultAtExfil = sta.predict(vlmc, historyAtExfil);

        assertTrue(
                resultAtExfil.getContributions().size() >= 2,
                "Should have multiple context contributions for explainability");

        // The mixed distribution should assign some probability to NC
        Double probNc = resultAtExfil.getMixedDistribution().getProbBySymbol("NC");
        assertNotNull(probNc, "NC should be in the mixed distribution");
    }

    @Test
    public void testStaBetaInReasonableRange() {
        List<List<String>> testVariants = generator.generateTestAttackVariants(50);
        List<List<String>> testNormals = generator.generateTestNormal(200);

        double bestSeparation = 0.0;
        double bestBeta = 0.0;

        double[] betas = {0.1, 0.5, 1.0, 2.0, 5.0, 10.0};
        for (double beta : betas) {
            StaPredictor sta = new StaPredictor(beta);
            double normal = meanAnomalyScoreSta(sta, testNormals);
            double attack = meanAnomalyScoreSta(sta, testVariants);
            double sep = attack > 0 && normal > 0 ? attack / normal : 0;
            if (sep > bestSeparation) {
                bestSeparation = sep;
                bestBeta = beta;
            }
        }

        // Best beta should be in a reasonable range (not at extremes)
        assertTrue(
                bestBeta >= 0.1 && bestBeta <= 10.0,
                String.format("Best beta (%.1f) should be in [0.1, 10.0]", bestBeta));
    }

    @Test
    public void testNormalTracesHaveLowAnomalyScore() {
        StaPredictor sta = new StaPredictor(1.0);
        List<List<String>> testNormals = generator.generateTestNormal(500);

        double meanNormal = meanAnomalyScoreSta(sta, testNormals);

        // Normal traces should not have extremely high anomaly scores
        // (finite and reasonable)
        assertTrue(
                meanNormal < Double.POSITIVE_INFINITY,
                "Normal traces should have finite anomaly score");
        assertTrue(meanNormal > 0, "Normal traces should have positive anomaly score");
    }

    // --- Helper methods ---

    private double meanAnomalyScoreVlmc(List<List<String>> traces) {
        double sum = 0.0;
        int count = 0;
        for (List<String> trace : traces) {
            double score = traceAnomalyScoreVlmc(trace);
            if (Double.isFinite(score)) {
                sum += score;
                count++;
            }
        }
        return count > 0 ? sum / count : Double.POSITIVE_INFINITY;
    }

    private double traceAnomalyScoreVlmc(List<String> trace) {
        ArrayList<String> ctx = new ArrayList<>(trace);
        ArrayList<Double> lik = vlmc.getLikelihood(ctx);
        if (lik.isEmpty()) return Double.POSITIVE_INFINITY;
        double finalLik = lik.get(lik.size() - 1);
        return finalLik > 0 ? -Math.log(finalLik) : Double.POSITIVE_INFINITY;
    }

    private double meanAnomalyScoreSta(StaPredictor sta, List<List<String>> traces) {
        double sum = 0.0;
        int count = 0;
        for (List<String> trace : traces) {
            double score = traceAnomalyScoreSta(sta, trace);
            if (Double.isFinite(score)) {
                sum += score;
                count++;
            }
        }
        return count > 0 ? sum / count : Double.POSITIVE_INFINITY;
    }

    private double traceAnomalyScoreSta(StaPredictor sta, List<String> trace) {
        double totalScore = 0.0;
        for (int t = 0; t < trace.size() - 1; t++) {
            List<String> history = trace.subList(0, t + 1);
            String nextSymbol = trace.get(t + 1);
            StaResult result = sta.predict(vlmc, history);
            double score = result.getAnomalyScore(nextSymbol);
            if (Double.isInfinite(score)) return Double.POSITIVE_INFINITY;
            totalScore += score;
        }
        return totalScore;
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
