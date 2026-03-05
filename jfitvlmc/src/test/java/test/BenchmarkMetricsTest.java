package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import sta.BenchmarkMetrics;
import sta.BenchmarkMetrics.MetricsResult;
import sta.BenchmarkMetrics.ScoredTrace;

public class BenchmarkMetricsTest {

    @Test
    public void testPerfectClassifier() {
        List<ScoredTrace> traces = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            traces.add(new ScoredTrace("normal_" + i, 1.0, false));
        }
        for (int i = 0; i < 50; i++) {
            traces.add(new ScoredTrace("anomaly_" + i, 10.0, true));
        }

        BenchmarkMetrics bm = new BenchmarkMetrics(traces);
        MetricsResult best = bm.findBestF1();

        assertEquals(1.0, best.precision, 1e-9);
        assertEquals(1.0, best.recall, 1e-9);
        assertEquals(1.0, best.f1, 1e-9);
    }

    @Test
    public void testPerfectClassifierAUC() {
        List<ScoredTrace> traces = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            traces.add(new ScoredTrace("normal_" + i, 1.0, false));
        }
        for (int i = 0; i < 50; i++) {
            traces.add(new ScoredTrace("anomaly_" + i, 10.0, true));
        }

        BenchmarkMetrics bm = new BenchmarkMetrics(traces);
        double auc = bm.computeAUC();

        assertEquals(1.0, auc, 0.01);
    }

    @Test
    public void testInvertedClassifierHasLowAUC() {
        List<ScoredTrace> traces = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            traces.add(new ScoredTrace("normal_" + i, 10.0, false));
        }
        for (int i = 0; i < 50; i++) {
            traces.add(new ScoredTrace("anomaly_" + i, 1.0, true));
        }

        BenchmarkMetrics bm = new BenchmarkMetrics(traces);
        double auc = bm.computeAUC();

        assertTrue(auc < 0.1, "Inverted classifier should have AUC near 0, got: " + auc);
    }

    @Test
    public void testAllSameClass() {
        List<ScoredTrace> traces = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            traces.add(new ScoredTrace("normal_" + i, i * 0.1, false));
        }

        BenchmarkMetrics bm = new BenchmarkMetrics(traces);
        MetricsResult best = bm.findBestF1();

        assertEquals(0.0, best.f1, 1e-9, "No positives means F1 = 0");
    }

    @Test
    public void testComputeAtThreshold() {
        List<ScoredTrace> traces = new ArrayList<>();
        traces.add(new ScoredTrace("n1", 1.0, false));
        traces.add(new ScoredTrace("n2", 2.0, false));
        traces.add(new ScoredTrace("a1", 3.0, true));
        traces.add(new ScoredTrace("a2", 4.0, true));

        BenchmarkMetrics bm = new BenchmarkMetrics(traces);
        MetricsResult result = bm.computeAtThreshold(3.0);

        assertEquals(2, result.tp);
        assertEquals(0, result.fp);
        assertEquals(0, result.fn);
        assertEquals(2, result.tn);
        assertEquals(1.0, result.precision, 1e-9);
        assertEquals(1.0, result.recall, 1e-9);
    }

    @Test
    public void testAUCMonotonicity() {
        List<ScoredTrace> good = new ArrayList<>();
        List<ScoredTrace> bad = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            good.add(new ScoredTrace("n_" + i, 1.0, false));
            good.add(new ScoredTrace("a_" + i, 10.0, true));
        }

        for (int i = 0; i < 50; i++) {
            double score = 5.0 + (i % 2 == 0 ? 1.0 : -1.0);
            bad.add(new ScoredTrace("n_" + i, score, false));
            bad.add(new ScoredTrace("a_" + i, score, true));
        }

        double aucGood = new BenchmarkMetrics(good).computeAUC();
        double aucBad = new BenchmarkMetrics(bad).computeAUC();

        assertTrue(aucGood > aucBad, "Better classifier should have higher AUC");
    }
}
