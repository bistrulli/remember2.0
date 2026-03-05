package sta;

import vlmc.VlmcNode;
import vlmc.VlmcRoot;

@FunctionalInterface
public interface StaWeightFunction {

    double score(VlmcNode node);

    static StaWeightFunction klBased() {
        return (node) -> {
            if (node.getParent() == null || node.getParent() instanceof VlmcRoot) {
                return 0.0;
            }
            if (node.getDist() == null || node.getParent().getDist() == null) {
                return 0.0;
            }
            double kl = node.KullbackLeibler();
            if (Double.isInfinite(kl) || Double.isNaN(kl)) {
                return 0.0;
            }
            double n = node.getDist().totalCtx;
            return Math.log(Math.max(1.0, n)) * kl;
        };
    }
}
