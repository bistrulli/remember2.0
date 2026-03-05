package sta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import vlmc.NextSymbolsDistribution;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class StaPredictor {

    private final double beta;
    private final StaWeightFunction weightFunction;
    private final boolean includeRoot;

    public StaPredictor(double beta, StaWeightFunction weightFunction, boolean includeRoot) {
        this.beta = beta;
        this.weightFunction = weightFunction;
        this.includeRoot = includeRoot;
    }

    public StaPredictor(double beta) {
        this(beta, StaWeightFunction.klBased(), true);
    }

    public StaPredictor() {
        this(1.0);
    }

    public List<VlmcNode> collectMatchedContexts(VlmcRoot tree, List<String> history) {
        List<VlmcNode> matched = new ArrayList<>();
        VlmcNode node = tree;

        if (includeRoot && node.getDist() != null && !node.getDist().getSymbols().isEmpty()) {
            matched.add(node);
        }

        for (int i = history.size() - 1; i >= 0; i--) {
            VlmcNode child = node.getChidByLabel(history.get(i));
            if (child == null) {
                break;
            }
            node = child;
            if (node.getDist() != null && !node.getDist().getSymbols().isEmpty()) {
                matched.add(node);
            }
        }

        return matched;
    }

    public double[] computeWeights(List<VlmcNode> contexts) {
        double[] scores = new double[contexts.size()];

        for (int i = 0; i < contexts.size(); i++) {
            scores[i] = weightFunction.score(contexts.get(i));
        }

        return softmax(scores, beta);
    }

    public NextSymbolsDistribution mixDistributions(
            List<VlmcNode> contexts, double[] weights) {

        Set<String> allSymbols = new LinkedHashSet<>();
        for (VlmcNode node : contexts) {
            if (node.getDist() != null) {
                allSymbols.addAll(node.getDist().getSymbols());
            }
        }

        NextSymbolsDistribution mixed = new NextSymbolsDistribution();
        for (String symbol : allSymbols) {
            double mixedProb = 0.0;
            for (int i = 0; i < contexts.size(); i++) {
                VlmcNode node = contexts.get(i);
                if (node.getDist() != null) {
                    Double p = node.getDist().getProbBySymbol(symbol);
                    if (p != null) {
                        mixedProb += weights[i] * p;
                    }
                }
            }
            mixed.getSymbols().add(symbol);
            mixed.getProbability().add(mixedProb);
        }

        normalize(mixed);
        return mixed;
    }

    public StaResult predict(VlmcRoot tree, List<String> history) {
        List<VlmcNode> contexts = collectMatchedContexts(tree, history);

        if (contexts.isEmpty()) {
            return new StaResult(new NextSymbolsDistribution(), new ArrayList<>());
        }

        double[] weights = computeWeights(contexts);
        NextSymbolsDistribution mixed = mixDistributions(contexts, weights);

        List<ContextContribution> contributions = new ArrayList<>();
        for (int i = 0; i < contexts.size(); i++) {
            VlmcNode node = contexts.get(i);
            double kl = 0.0;
            if (node.getParent() != null && !(node.getParent() instanceof VlmcRoot)) {
                kl = node.KullbackLeibler();
                if (Double.isInfinite(kl) || Double.isNaN(kl)) {
                    kl = 0.0;
                }
            }

            double totalCtx = node.getDist() != null ? node.getDist().totalCtx : 0;

            ContextContribution cc =
                    new ContextContribution(
                            node.getCtx(),
                            depthOf(node),
                            weightFunction.score(node),
                            kl,
                            totalCtx,
                            node.getDist());
            cc.setWeight(weights[i]);
            contributions.add(cc);
        }

        return new StaResult(mixed, contributions);
    }

    private static double[] softmax(double[] scores, double beta) {
        double[] result = new double[scores.length];

        if (scores.length == 0) return result;
        if (scores.length == 1) {
            result[0] = 1.0;
            return result;
        }

        double maxScore = Double.NEGATIVE_INFINITY;
        for (double s : scores) {
            if (s > maxScore) maxScore = s;
        }

        double sumExp = 0.0;
        for (int i = 0; i < scores.length; i++) {
            result[i] = Math.exp(beta * (scores[i] - maxScore));
            sumExp += result[i];
        }

        if (sumExp > 0) {
            for (int i = 0; i < result.length; i++) {
                result[i] /= sumExp;
            }
        } else {
            double uniform = 1.0 / scores.length;
            for (int i = 0; i < result.length; i++) {
                result[i] = uniform;
            }
        }

        return result;
    }

    private static void normalize(NextSymbolsDistribution dist) {
        double sum = 0.0;
        for (double p : dist.getProbability()) {
            sum += p;
        }
        if (sum > 0 && Math.abs(sum - 1.0) > 1e-10) {
            for (int i = 0; i < dist.getProbability().size(); i++) {
                dist.getProbability().set(i, dist.getProbability().get(i) / sum);
            }
        }
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

    public double getBeta() {
        return beta;
    }
}
