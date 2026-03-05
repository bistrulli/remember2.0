package sta;

import java.util.List;
import vlmc.NextSymbolsDistribution;

public class ContextContribution {

    private final List<String> context;
    private final int depth;
    private double weight;
    private final double rawScore;
    private final double kl;
    private final double totalCtx;
    private final NextSymbolsDistribution distribution;

    public ContextContribution(
            List<String> context,
            int depth,
            double rawScore,
            double kl,
            double totalCtx,
            NextSymbolsDistribution distribution) {
        this.context = context;
        this.depth = depth;
        this.rawScore = rawScore;
        this.kl = kl;
        this.totalCtx = totalCtx;
        this.distribution = distribution;
        this.weight = 0.0;
    }

    public List<String> getContext() {
        return context;
    }

    public int getDepth() {
        return depth;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public double getRawScore() {
        return rawScore;
    }

    public double getKl() {
        return kl;
    }

    public double getTotalCtx() {
        return totalCtx;
    }

    public NextSymbolsDistribution getDistribution() {
        return distribution;
    }

    @Override
    public String toString() {
        return String.format(
                "[%s] depth=%d w=%.4f rawScore=%.4f KL=%.4f n=%.0f",
                String.join(" ", context), depth, weight, rawScore, kl, totalCtx);
    }
}
