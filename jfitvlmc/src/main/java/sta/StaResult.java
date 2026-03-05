package sta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vlmc.NextSymbolsDistribution;

public class StaResult {

    private final NextSymbolsDistribution mixedDistribution;
    private final List<ContextContribution> contributions;

    public StaResult(
            NextSymbolsDistribution mixedDistribution, List<ContextContribution> contributions) {
        this.mixedDistribution = mixedDistribution;
        this.contributions = contributions;
    }

    public NextSymbolsDistribution getMixedDistribution() {
        return mixedDistribution;
    }

    public List<ContextContribution> getContributions() {
        return contributions;
    }

    public double getAnomalyScore(String observedSymbol) {
        Double prob = mixedDistribution.getProbBySymbol(observedSymbol);
        if (prob == null || prob <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return -Math.log(prob);
    }

    public Map<String, Double> getContextRelevanceMap() {
        Map<String, Double> map = new LinkedHashMap<>();
        for (ContextContribution cc : contributions) {
            String key = String.join(" ", cc.getContext());
            map.put(key, cc.getWeight());
        }
        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("STA Result:\n");
        sb.append("  Distribution: ").append(mixedDistribution).append("\n");
        sb.append("  Contributions:\n");
        for (ContextContribution cc : contributions) {
            sb.append("    ").append(cc).append("\n");
        }
        return sb.toString();
    }
}
