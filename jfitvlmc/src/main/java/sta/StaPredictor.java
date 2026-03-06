package sta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vlmc.NextSymbolsDistribution;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class StaPredictor {

    private final double beta;
    private final StaWeightFunction weightFunction;
    private final boolean includeRoot;
    private final double epsilon;

    public StaPredictor(
            double beta, StaWeightFunction weightFunction, boolean includeRoot, double epsilon) {
        this.beta = beta;
        this.weightFunction = weightFunction;
        this.includeRoot = includeRoot;
        this.epsilon = epsilon;
    }

    public StaPredictor(double beta, StaWeightFunction weightFunction, boolean includeRoot) {
        this(beta, weightFunction, includeRoot, 1e-10);
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

    public NextSymbolsDistribution mixDistributions(List<VlmcNode> contexts, double[] weights) {

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

        applyEpsilonFloor(mixed);
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

    public List<StaResult> predictOnline(VlmcRoot tree, List<String> trace) {
        return predictOnline(tree, trace, 0.05);
    }

    public List<StaResult> predictOnline(VlmcRoot tree, List<String> trace, double eta) {
        List<StaResult> results = new ArrayList<>();
        if (trace.size() <= 1) {
            if (!trace.isEmpty()) {
                results.add(predict(tree, trace));
            }
            return results;
        }

        // Weights keyed by node identity (object reference)
        Map<VlmcNode, Double> weights = new HashMap<>();

        for (int t = 0; t < trace.size() - 1; t++) {
            List<String> history = trace.subList(0, t + 1);
            String nextSymbol = trace.get(t + 1);
            List<VlmcNode> contexts = collectMatchedContexts(tree, history);

            if (contexts.isEmpty()) {
                results.add(new StaResult(new NextSymbolsDistribution(), new ArrayList<>()));
                continue;
            }

            int k = contexts.size();

            // Handle context set changes: assign weight to new contexts
            if (weights.isEmpty()) {
                // First step: uniform init
                for (VlmcNode ctx : contexts) {
                    weights.put(ctx, 1.0 / k);
                }
            } else {
                // Assign eta/k to new contexts, renormalize existing
                Map<VlmcNode, Double> newWeights = new HashMap<>();
                double existingSum = 0.0;
                int newCount = 0;
                for (VlmcNode ctx : contexts) {
                    Double w = weights.get(ctx);
                    if (w != null) {
                        newWeights.put(ctx, w);
                        existingSum += w;
                    } else {
                        newCount++;
                    }
                }
                double shareForNew = (newCount > 0) ? eta / k : 0.0;
                double totalNew = shareForNew * newCount;
                // Renormalize existing to make room for new contexts
                double scale = (existingSum > 0 && totalNew < 1.0)
                        ? (1.0 - totalNew) / existingSum
                        : 1.0 / k;
                for (Map.Entry<VlmcNode, Double> entry : newWeights.entrySet()) {
                    entry.setValue(entry.getValue() * scale);
                }
                for (VlmcNode ctx : contexts) {
                    if (!newWeights.containsKey(ctx)) {
                        newWeights.put(ctx, shareForNew);
                    }
                }
                weights = newWeights;
            }

            // Build current weight array aligned with contexts list
            double[] w = new double[k];
            for (int i = 0; i < k; i++) {
                Double wVal = weights.get(contexts.get(i));
                w[i] = (wVal != null) ? wVal : 1.0 / k;
            }

            // Mix distributions using current weights
            NextSymbolsDistribution mixed = mixWithWeights(contexts, w);

            // Build contributions
            List<ContextContribution> contributions = new ArrayList<>();
            for (int i = 0; i < k; i++) {
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
                                node.getCtx(), depthOf(node), w[i], kl, totalCtx, node.getDist());
                cc.setWeight(w[i]);
                contributions.add(cc);
            }

            results.add(new StaResult(mixed, contributions));

            // Bayesian update with fixed-share
            double[] pi = new double[k];
            for (int i = 0; i < k; i++) {
                VlmcNode node = contexts.get(i);
                Double prob = (node.getDist() != null)
                        ? node.getDist().getProbBySymbol(nextSymbol)
                        : null;
                pi[i] = (prob != null && prob > epsilon) ? prob : epsilon;
            }

            double pMix = 0.0;
            for (int i = 0; i < k; i++) {
                pMix += w[i] * pi[i];
            }
            if (pMix <= 0) pMix = epsilon;

            double[] wNew = new double[k];
            double wSum = 0.0;
            for (int i = 0; i < k; i++) {
                wNew[i] = (1.0 - eta) * w[i] * pi[i] / pMix + eta / k;
                wSum += wNew[i];
            }
            // Renormalize for numerical stability
            if (wSum > 0) {
                for (int i = 0; i < k; i++) {
                    wNew[i] /= wSum;
                }
            }

            // Store updated weights
            weights.clear();
            for (int i = 0; i < k; i++) {
                weights.put(contexts.get(i), wNew[i]);
            }
        }

        return results;
    }

    private NextSymbolsDistribution mixWithWeights(List<VlmcNode> contexts, double[] weights) {
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

        applyEpsilonFloor(mixed);
        normalize(mixed);
        return mixed;
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

    public double getEpsilon() {
        return epsilon;
    }

    private void applyEpsilonFloor(NextSymbolsDistribution dist) {
        for (int i = 0; i < dist.getProbability().size(); i++) {
            if (dist.getProbability().get(i) < epsilon) {
                dist.getProbability().set(i, epsilon);
            }
        }
    }
}
