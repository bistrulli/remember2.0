package sta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BenchmarkMetrics {

    public static class ScoredTrace {
        public final String id;
        public final double anomalyScore;
        public final boolean isAnomaly;

        public ScoredTrace(String id, double anomalyScore, boolean isAnomaly) {
            this.id = id;
            this.anomalyScore = anomalyScore;
            this.isAnomaly = isAnomaly;
        }
    }

    public static class MetricsResult {
        public final double precision;
        public final double recall;
        public final double f1;
        public final double threshold;
        public final int tp;
        public final int fp;
        public final int fn;
        public final int tn;

        public MetricsResult(
                double precision,
                double recall,
                double f1,
                double threshold,
                int tp,
                int fp,
                int fn,
                int tn) {
            this.precision = precision;
            this.recall = recall;
            this.f1 = f1;
            this.threshold = threshold;
            this.tp = tp;
            this.fp = fp;
            this.fn = fn;
            this.tn = tn;
        }
    }

    private final List<ScoredTrace> traces;

    public BenchmarkMetrics(List<ScoredTrace> traces) {
        this.traces = traces;
    }

    public MetricsResult computeAtThreshold(double threshold) {
        int tp = 0, fp = 0, fn = 0, tn = 0;
        for (ScoredTrace st : traces) {
            boolean predicted = st.anomalyScore >= threshold;
            if (predicted && st.isAnomaly) tp++;
            else if (predicted && !st.isAnomaly) fp++;
            else if (!predicted && st.isAnomaly) fn++;
            else tn++;
        }

        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
        double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
        double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0.0;

        return new MetricsResult(precision, recall, f1, threshold, tp, fp, fn, tn);
    }

    public MetricsResult findBestF1() {
        List<Double> scores = new ArrayList<>();
        for (ScoredTrace st : traces) {
            if (Double.isFinite(st.anomalyScore)) {
                scores.add(st.anomalyScore);
            }
        }
        Collections.sort(scores);

        MetricsResult best = computeAtThreshold(Double.NEGATIVE_INFINITY);
        for (int i = 0; i < scores.size(); i++) {
            double threshold = scores.get(i);
            MetricsResult result = computeAtThreshold(threshold);
            if (result.f1 > best.f1) {
                best = result;
            }
        }
        return best;
    }

    public double computeAUC() {
        List<Double> scores = new ArrayList<>();
        for (ScoredTrace st : traces) {
            scores.add(Double.isFinite(st.anomalyScore) ? st.anomalyScore : Double.MAX_VALUE);
        }
        Collections.sort(scores);

        int totalP = 0, totalN = 0;
        for (ScoredTrace st : traces) {
            if (st.isAnomaly) totalP++;
            else totalN++;
        }
        if (totalP == 0 || totalN == 0) return 0.0;

        double auc = 0.0;
        double prevFpr = 0.0;
        double prevTpr = 0.0;

        for (int i = scores.size() - 1; i >= 0; i--) {
            double threshold = scores.get(i);
            int tp = 0, fp = 0;
            for (ScoredTrace st : traces) {
                double s =
                        Double.isFinite(st.anomalyScore) ? st.anomalyScore : Double.MAX_VALUE;
                if (s >= threshold) {
                    if (st.isAnomaly) tp++;
                    else fp++;
                }
            }
            double tpr = (double) tp / totalP;
            double fpr = (double) fp / totalN;

            auc += (fpr - prevFpr) * (tpr + prevTpr) / 2.0;
            prevFpr = fpr;
            prevTpr = tpr;
        }
        auc += (1.0 - prevFpr) * (1.0 + prevTpr) / 2.0;

        return auc;
    }

    public static String formatComparisonTable(Map<String, MetricsResult> methods) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                String.format(
                        "%-25s %-10s %-10s %-10s %-10s%n",
                        "Method", "Precision", "Recall", "F1", "Threshold"));
        sb.append("-".repeat(65)).append("\n");

        for (Map.Entry<String, MetricsResult> entry : methods.entrySet()) {
            MetricsResult m = entry.getValue();
            sb.append(
                    String.format(
                            "%-25s %-10.4f %-10.4f %-10.4f %-10.4f%n",
                            entry.getKey(), m.precision, m.recall, m.f1, m.threshold));
        }
        return sb.toString();
    }
}
