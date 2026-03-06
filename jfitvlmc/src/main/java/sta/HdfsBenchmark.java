package sta;

import fitvlmc.HdfsLogParser;
import fitvlmc.HdfsLogParser.HdfsSession;
import fitvlmc.fitVlmc;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import vlmc.VlmcRoot;

public class HdfsBenchmark {

    private final double trainRatio;
    private final double alfa;
    private final double[] betas;

    public HdfsBenchmark(double trainRatio, double alfa, double[] betas) {
        this.trainRatio = trainRatio;
        this.alfa = alfa;
        this.betas = betas;
    }

    public HdfsBenchmark() {
        this(0.8, 0.01, new double[] {0.01, 0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 50.0});
    }

    public static class BenchmarkResult {
        public final VlmcRoot vlmc;
        public final Map<String, BenchmarkMetrics.MetricsResult> results;
        public final long trainingTimeMs;
        public final int nNodes;
        public final int nTraining;
        public final int nTest;
        public final int nAnomalies;

        public BenchmarkResult(
                VlmcRoot vlmc,
                Map<String, BenchmarkMetrics.MetricsResult> results,
                long trainingTimeMs,
                int nNodes,
                int nTraining,
                int nTest,
                int nAnomalies) {
            this.vlmc = vlmc;
            this.results = results;
            this.trainingTimeMs = trainingTimeMs;
            this.nNodes = nNodes;
            this.nTraining = nTraining;
            this.nTest = nTest;
            this.nAnomalies = nAnomalies;
        }
    }

    public BenchmarkResult run(List<HdfsSession> sessions, File workDir) throws Exception {
        return run(sessions, workDir, null);
    }

    public BenchmarkResult run(List<HdfsSession> sessions, File workDir, VlmcRoot preloaded)
            throws Exception {
        List<HdfsSession> normals = new ArrayList<>();
        List<HdfsSession> anomalies = new ArrayList<>();
        for (HdfsSession s : sessions) {
            if (s.isAnomaly) anomalies.add(s);
            else normals.add(s);
        }

        Collections.shuffle(normals, new Random(42));
        int splitIdx = (int) (normals.size() * trainRatio);
        List<HdfsSession> trainNormals = normals.subList(0, splitIdx);
        List<HdfsSession> testNormals = normals.subList(splitIdx, normals.size());

        List<HdfsSession> testAll = new ArrayList<>();
        testAll.addAll(testNormals);
        testAll.addAll(anomalies);

        VlmcRoot vlmc;
        if (preloaded != null) {
            vlmc = preloaded;
            System.out.println("Using pre-loaded VLMC model (skipping training)");
        } else {
            vlmc = trainVlmc(trainNormals, workDir);
        }

        Map<String, BenchmarkMetrics.MetricsResult> results = new LinkedHashMap<>();

        List<BenchmarkMetrics.ScoredTrace> vlmcScores = scoreWithVlmc(vlmc, testAll);
        BenchmarkMetrics vlmcBm = new BenchmarkMetrics(vlmcScores);
        results.put("VLMC classic", vlmcBm.findBestF1());

        for (double beta : betas) {
            List<BenchmarkMetrics.ScoredTrace> staScores = scoreWithSta(vlmc, testAll, beta);
            BenchmarkMetrics staBm = new BenchmarkMetrics(staScores);
            results.put(String.format("STA beta=%.2f", beta), staBm.findBestF1());
        }

        for (double beta : betas) {
            List<BenchmarkMetrics.ScoredTrace> onlineScores =
                    scoreWithStaOnline(vlmc, testAll, beta, 0.05);
            BenchmarkMetrics onlineBm = new BenchmarkMetrics(onlineScores);
            results.put(String.format("BMA beta=%.2f", beta), onlineBm.findBestF1());
        }

        return new BenchmarkResult(
                vlmc,
                results,
                0,
                VlmcRoot.nNodes,
                trainNormals.size(),
                testAll.size(),
                anomalies.size());
    }

    public VlmcRoot trainVlmc(List<HdfsSession> trainingSessions, File workDir) throws Exception {
        File tracesFile = new File(workDir, "training_traces.txt");
        HdfsLogParser parser = new HdfsLogParser();
        parser.writeTraceFile(trainingSessions, tracesFile);

        resetFitVlmcStatics();
        fitVlmc.alfa = (float) alfa;
        fitVlmc.k = 1;
        fitVlmc.maxNavigationDepth = 50;

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

        Field vlmcField = fitVlmc.class.getDeclaredField("vlmc");
        vlmcField.setAccessible(true);
        return (VlmcRoot) vlmcField.get(learner);
    }

    public List<BenchmarkMetrics.ScoredTrace> scoreWithSta(
            VlmcRoot vlmc, List<HdfsSession> sessions, double beta) {
        StaPredictor sta = new StaPredictor(beta);
        List<BenchmarkMetrics.ScoredTrace> scored = new ArrayList<>();

        for (HdfsSession session : sessions) {
            double totalScore = 0.0;
            List<String> events = session.events;
            int nSteps = events.size() - 1;
            if (nSteps <= 0) {
                scored.add(
                        new BenchmarkMetrics.ScoredTrace(session.blockId, 0.0, session.isAnomaly));
                continue;
            }
            for (int t = 0; t < nSteps; t++) {
                List<String> history = events.subList(0, t + 1);
                String next = events.get(t + 1);
                StaResult result = sta.predict(vlmc, history);
                double score = result.getAnomalyScore(next);
                if (Double.isInfinite(score)) {
                    totalScore = Double.POSITIVE_INFINITY;
                    break;
                }
                totalScore += score;
            }
            if (Double.isFinite(totalScore)) {
                totalScore /= nSteps;
            }
            scored.add(
                    new BenchmarkMetrics.ScoredTrace(
                            session.blockId, totalScore, session.isAnomaly));
        }
        return scored;
    }

    public List<BenchmarkMetrics.ScoredTrace> scoreWithStaOnline(
            VlmcRoot vlmc, List<HdfsSession> sessions, double beta, double eta) {
        StaPredictor sta = new StaPredictor(beta);
        List<BenchmarkMetrics.ScoredTrace> scored = new ArrayList<>();

        for (HdfsSession session : sessions) {
            List<String> events = session.events;
            int nSteps = events.size() - 1;
            if (nSteps <= 0) {
                scored.add(
                        new BenchmarkMetrics.ScoredTrace(session.blockId, 0.0, session.isAnomaly));
                continue;
            }
            List<StaResult> onlineResults = sta.predictOnline(vlmc, events, eta);
            double totalScore = 0.0;
            for (int t = 0; t < onlineResults.size(); t++) {
                String next = events.get(t + 1);
                double score = onlineResults.get(t).getAnomalyScore(next);
                if (Double.isInfinite(score)) {
                    totalScore = Double.POSITIVE_INFINITY;
                    break;
                }
                totalScore += score;
            }
            if (Double.isFinite(totalScore)) {
                totalScore /= nSteps;
            }
            scored.add(
                    new BenchmarkMetrics.ScoredTrace(
                            session.blockId, totalScore, session.isAnomaly));
        }
        return scored;
    }

    public List<BenchmarkMetrics.ScoredTrace> scoreWithVlmc(
            VlmcRoot vlmc, List<HdfsSession> sessions) {
        List<BenchmarkMetrics.ScoredTrace> scored = new ArrayList<>();

        for (HdfsSession session : sessions) {
            ArrayList<String> events = new ArrayList<>(session.events);
            int nSteps = events.size() - 1;
            double totalScore;
            if (nSteps <= 0) {
                totalScore = 0.0;
            } else {
                ArrayList<Double> lik = vlmc.getLikelihood(events);
                if (lik.isEmpty()) {
                    totalScore = Double.POSITIVE_INFINITY;
                } else {
                    double finalLik = lik.get(lik.size() - 1);
                    totalScore =
                            finalLik > 0 ? -Math.log(finalLik) / nSteps : Double.POSITIVE_INFINITY;
                }
            }
            scored.add(
                    new BenchmarkMetrics.ScoredTrace(
                            session.blockId, totalScore, session.isAnomaly));
        }
        return scored;
    }

    public String formatReport(BenchmarkResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("HDFS Benchmark — STA vs VLMC vs DeepLog\n");
        sb.append("========================================\n\n");

        sb.append(String.format("Training: %d normal sessions%n", result.nTraining));
        sb.append(
                String.format(
                        "Test: %d sessions (%d anomalies)%n", result.nTest, result.nAnomalies));
        sb.append(String.format("VLMC nodes: %d%n%n", result.nNodes));

        sb.append(BenchmarkMetrics.formatComparisonTable(result.results));

        sb.append(
                String.format(
                        "%n%-25s %-10s %-10s %-10s%n",
                        "DeepLog (published)", "0.9500", "0.9600", "0.9550"));

        return sb.toString();
    }

    public void writeCsvReport(BenchmarkResult result, File output) throws IOException {
        try (FileWriter fw = new FileWriter(output, StandardCharsets.UTF_8)) {
            fw.write("method,precision,recall,f1,threshold,tp,fp,fn,tn\n");
            for (Map.Entry<String, BenchmarkMetrics.MetricsResult> entry :
                    result.results.entrySet()) {
                BenchmarkMetrics.MetricsResult m = entry.getValue();
                fw.write(
                        String.format(
                                "%s,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%d%n",
                                entry.getKey(),
                                m.precision,
                                m.recall,
                                m.f1,
                                m.threshold,
                                m.tp,
                                m.fp,
                                m.fn,
                                m.tn));
            }
        }
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
        fitVlmc.maxNavigationDepth = 100;
        VlmcRoot.order = -1;
        VlmcRoot.nNodes = -1;
        VlmcRoot.nLeaves = -1;
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field f = fitVlmc.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }
}
