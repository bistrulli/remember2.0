package sta;

import java.util.ArrayList;
import java.util.List;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class StaAnomalyScorer {

    private final StaPredictor staPredictor;
    private final VlmcRoot vlmc;

    public StaAnomalyScorer(VlmcRoot vlmc, double beta) {
        this.vlmc = vlmc;
        this.staPredictor = new StaPredictor(beta);
    }

    public StaAnomalyScorer(VlmcRoot vlmc) {
        this(vlmc, 1.0);
    }

    public TraceScore scoreTrace(List<String> trace) {
        double staTotal = 0.0;
        double vlmcTotal = 0.0;
        List<StepDetail> steps = new ArrayList<>();

        for (int t = 0; t < trace.size() - 1; t++) {
            List<String> history = trace.subList(0, t + 1);
            String nextSymbol = trace.get(t + 1);

            // STA score
            StaResult staResult = staPredictor.predict(vlmc, history);
            double staScore = staResult.getAnomalyScore(nextSymbol);

            // VLMC classic score
            VlmcNode classicNode = vlmc.getState(new ArrayList<>(history));
            double vlmcScore = Double.POSITIVE_INFINITY;
            if (classicNode.getDist() != null) {
                Double prob = classicNode.getDist().getProbBySymbol(nextSymbol);
                if (prob != null && prob > 0) {
                    vlmcScore = -Math.log(prob);
                }
            }

            steps.add(new StepDetail(t, nextSymbol, staScore, vlmcScore, staResult));

            if (Double.isFinite(staScore)) staTotal += staScore;
            else staTotal = Double.POSITIVE_INFINITY;

            if (Double.isFinite(vlmcScore)) vlmcTotal += vlmcScore;
            else vlmcTotal = Double.POSITIVE_INFINITY;
        }

        return new TraceScore(trace, staTotal, vlmcTotal, steps);
    }

    public String formatTraceReport(List<String> trace) {
        TraceScore ts = scoreTrace(trace);
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("Trace: %s%n", String.join(" ", trace)));
        sb.append(String.format("Anomaly score (STA):  %.4f%n", ts.staScore));
        sb.append(String.format("Anomaly score (VLMC): %.4f%n", ts.vlmcScore));
        sb.append(
                String.format(
                        "Improvement: %.2fx%n", ts.vlmcScore > 0 ? ts.staScore / ts.vlmcScore : 0));
        sb.append("\nPer-step detail:\n");

        for (StepDetail step : ts.steps) {
            sb.append(
                    String.format(
                            "  t=%d next=%s  STA=%.4f VLMC=%.4f%n",
                            step.time, step.nextSymbol, step.staScore, step.vlmcScore));

            if (step.staResult != null) {
                for (ContextContribution cc : step.staResult.getContributions()) {
                    if (cc.getWeight() > 0.05) {
                        sb.append(String.format("    %s%n", cc));
                    }
                }
            }
        }
        return sb.toString();
    }

    public String formatCsvHeader() {
        return "trace,sta_score,vlmc_score,trace_length";
    }

    public String formatCsvLine(List<String> trace) {
        TraceScore ts = scoreTrace(trace);
        return String.format(
                "%s,%.6f,%.6f,%d",
                String.join(" ", trace), ts.staScore, ts.vlmcScore, trace.size());
    }

    public static class TraceScore {
        public final List<String> trace;
        public final double staScore;
        public final double vlmcScore;
        public final List<StepDetail> steps;

        TraceScore(List<String> trace, double staScore, double vlmcScore, List<StepDetail> steps) {
            this.trace = trace;
            this.staScore = staScore;
            this.vlmcScore = vlmcScore;
            this.steps = steps;
        }
    }

    public static class StepDetail {
        public final int time;
        public final String nextSymbol;
        public final double staScore;
        public final double vlmcScore;
        public final StaResult staResult;

        StepDetail(
                int time,
                String nextSymbol,
                double staScore,
                double vlmcScore,
                StaResult staResult) {
            this.time = time;
            this.nextSymbol = nextSymbol;
            this.staScore = staScore;
            this.vlmcScore = vlmcScore;
            this.staResult = staResult;
        }
    }
}
