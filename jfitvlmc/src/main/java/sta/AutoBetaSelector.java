package sta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class AutoBetaSelector {

    public static class TreeStats {
        public final int depth;
        public final double meanDepth;
        public final double meanKL;
        public final double medianKL;
        public final int nNodes;
        public final int nLeaves;
        public final double meanN;

        public TreeStats(
                int depth,
                double meanDepth,
                double meanKL,
                double medianKL,
                int nNodes,
                int nLeaves,
                double meanN) {
            this.depth = depth;
            this.meanDepth = meanDepth;
            this.meanKL = meanKL;
            this.medianKL = medianKL;
            this.nNodes = nNodes;
            this.nLeaves = nLeaves;
            this.meanN = meanN;
        }

        @Override
        public String toString() {
            return String.format(
                    "TreeStats{depth=%d, meanDepth=%.2f, meanKL=%.4f, medianKL=%.4f,"
                            + " nNodes=%d, nLeaves=%d, meanN=%.1f}",
                    depth, meanDepth, meanKL, medianKL, nNodes, nLeaves, meanN);
        }
    }

    private final double constant;

    public AutoBetaSelector(double constant) {
        this.constant = constant;
    }

    public AutoBetaSelector() {
        this(1.0);
    }

    public double heuristicBeta(VlmcRoot vlmc) {
        TreeStats stats = computeTreeStats(vlmc);
        if (stats.meanKL <= 0 || stats.meanDepth <= 0) {
            return 1.0;
        }
        return constant / (stats.meanKL * Math.sqrt(stats.meanDepth));
    }

    public double crossValidateBeta(
            VlmcRoot vlmc,
            List<List<String>> normalTraces,
            List<List<String>> anomalyTraces,
            double[] candidates) {
        return crossValidateBeta(vlmc, normalTraces, anomalyTraces, candidates, 5);
    }

    public double crossValidateBeta(
            VlmcRoot vlmc,
            List<List<String>> normalTraces,
            List<List<String>> anomalyTraces,
            double[] candidates,
            int kFolds) {

        double bestBeta = candidates[0];
        double bestF1 = -1.0;

        for (double beta : candidates) {
            double f1Sum = 0.0;
            int foldCount = 0;

            for (int fold = 0; fold < kFolds; fold++) {
                List<List<String>> testNormals = new ArrayList<>();
                List<List<String>> testAnomalies = new ArrayList<>();

                for (int i = 0; i < normalTraces.size(); i++) {
                    if (i % kFolds == fold) {
                        testNormals.add(normalTraces.get(i));
                    }
                }
                for (int i = 0; i < anomalyTraces.size(); i++) {
                    if (i % kFolds == fold) {
                        testAnomalies.add(anomalyTraces.get(i));
                    }
                }

                if (testNormals.isEmpty() || testAnomalies.isEmpty()) continue;

                List<BenchmarkMetrics.ScoredTrace> scored = new ArrayList<>();
                StaPredictor sta = new StaPredictor(beta);

                for (List<String> trace : testNormals) {
                    double score = scoreTrace(sta, vlmc, trace);
                    scored.add(new BenchmarkMetrics.ScoredTrace("n", score, false));
                }
                for (List<String> trace : testAnomalies) {
                    double score = scoreTrace(sta, vlmc, trace);
                    scored.add(new BenchmarkMetrics.ScoredTrace("a", score, true));
                }

                BenchmarkMetrics bm = new BenchmarkMetrics(scored);
                BenchmarkMetrics.MetricsResult result = bm.findBestF1();
                f1Sum += result.f1;
                foldCount++;
            }

            if (foldCount > 0) {
                double meanF1 = f1Sum / foldCount;
                if (meanF1 > bestF1) {
                    bestF1 = meanF1;
                    bestBeta = beta;
                }
            }
        }

        return bestBeta;
    }

    public TreeStats computeTreeStats(VlmcRoot vlmc) {
        List<Double> klValues = new ArrayList<>();
        List<Integer> leafDepths = new ArrayList<>();
        List<Double> nValues = new ArrayList<>();
        int[] maxDepth = {0};
        int[] nodeCount = {0};
        int[] leafCount = {0};

        vlmc.DFS(
                node -> {
                    nodeCount[0]++;
                    int d = depthOf(node);
                    if (d > maxDepth[0]) maxDepth[0] = d;

                    if (node.getDist() != null) {
                        nValues.add((double) node.getDist().totalCtx);
                    }

                    if (node.getParent() != null && !(node.getParent() instanceof VlmcRoot)) {
                        double kl = node.KullbackLeibler();
                        if (Double.isFinite(kl) && kl >= 0) {
                            klValues.add(kl);
                        }
                    }

                    if (node.getChildren() == null || node.getChildren().isEmpty()) {
                        leafCount[0]++;
                        leafDepths.add(d);
                    }
                });

        double meanDepth = 0;
        if (!leafDepths.isEmpty()) {
            for (int d : leafDepths) meanDepth += d;
            meanDepth /= leafDepths.size();
        }

        double meanKL = 0;
        if (!klValues.isEmpty()) {
            for (double kl : klValues) meanKL += kl;
            meanKL /= klValues.size();
        }

        double medianKL = 0;
        if (!klValues.isEmpty()) {
            Collections.sort(klValues);
            int mid = klValues.size() / 2;
            medianKL =
                    klValues.size() % 2 == 0
                            ? (klValues.get(mid - 1) + klValues.get(mid)) / 2.0
                            : klValues.get(mid);
        }

        double meanN = 0;
        if (!nValues.isEmpty()) {
            for (double n : nValues) meanN += n;
            meanN /= nValues.size();
        }

        return new TreeStats(
                maxDepth[0], meanDepth, meanKL, medianKL, nodeCount[0], leafCount[0], meanN);
    }

    private double scoreTrace(StaPredictor sta, VlmcRoot vlmc, List<String> trace) {
        double total = 0.0;
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

    private static int depthOf(VlmcNode node) {
        int depth = 0;
        VlmcNode p = node.getParent();
        while (p != null) {
            depth++;
            p = p.getParent();
        }
        return depth;
    }
}
