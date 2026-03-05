package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vlmc.NextSymbolsDistribution;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class UemscTest {

	private VlmcRoot vlmc;

	/**
	 * Model:
	 *   root
	 *   +-- "A" P(B)=0.6, P(C)=0.4
	 *   +-- "B" P(end$)=1.0
	 *   +-- "C" P(end$)=1.0
	 *
	 * This gives:
	 *   P(A B end$) = P(B|A) * P(end$|B) = 0.6 * 1.0 = 0.6
	 *   P(A C end$) = P(C|A) * P(end$|C) = 0.4 * 1.0 = 0.4
	 */
	@BeforeEach
	public void buildVlmc() {
		vlmc = new VlmcRoot();
		vlmc.setLabel("root");
		vlmc.setDist(new NextSymbolsDistribution());

		VlmcNode nodeA = new VlmcNode();
		nodeA.setLabel("A");
		NextSymbolsDistribution distA = new NextSymbolsDistribution();
		distA.getSymbols().add("B");
		distA.getSymbols().add("C");
		distA.getProbability().add(0.6);
		distA.getProbability().add(0.4);
		distA.totalCtx = 100;
		nodeA.setDist(distA);
		nodeA.setParent(vlmc);
		vlmc.getChildren().add(nodeA);

		VlmcNode nodeB = new VlmcNode();
		nodeB.setLabel("B");
		NextSymbolsDistribution distB = new NextSymbolsDistribution();
		distB.getSymbols().add("end$");
		distB.getProbability().add(1.0);
		distB.totalCtx = 60;
		nodeB.setDist(distB);
		nodeB.setParent(vlmc);
		vlmc.getChildren().add(nodeB);

		VlmcNode nodeC = new VlmcNode();
		nodeC.setLabel("C");
		NextSymbolsDistribution distC = new NextSymbolsDistribution();
		distC.getSymbols().add("end$");
		distC.getProbability().add(1.0);
		distC.totalCtx = 40;
		nodeC.setDist(distC);
		nodeC.setParent(vlmc);
		vlmc.getChildren().add(nodeC);
	}

	/**
	 * Computes uEMSC = 1 - sum_{t in T} max(L(t) - M(t), 0)
	 */
	private double computeUemsc(VlmcRoot model, List<ArrayList<String>> traces) {
		// Count distinct traces
		HashMap<String, Integer> traceCounts = new HashMap<>();
		for (ArrayList<String> trace : traces) {
			String key = String.join(" ", trace);
			traceCounts.merge(key, 1, Integer::sum);
		}
		int total = traces.size();
		double surplus = 0.0;

		for (Map.Entry<String, Integer> entry : traceCounts.entrySet()) {
			double lt = (double) entry.getValue() / total;
			String[] parts = entry.getKey().split(" ");
			ArrayList<String> traceList = new ArrayList<>(Arrays.asList(parts));
			ArrayList<Double> likValues = model.getLikelihood(traceList);
			double mt = likValues.isEmpty() ? 0.0 : likValues.get(likValues.size() - 1);
			double diff = lt - mt;
			if (diff > 0) surplus += diff;
		}
		return 1.0 - surplus;
	}

	@Test
	public void testPerfectConformance() {
		// Log frequencies exactly match model probabilities
		// M(A B end$) = 0.6, M(A C end$) = 0.4
		// Log: 600 "A B end$", 400 "A C end$" -> L(t) = M(t) -> uEMSC = 1.0
		List<ArrayList<String>> traces = new ArrayList<>();
		for (int i = 0; i < 600; i++) traces.add(new ArrayList<>(Arrays.asList("A", "B", "end$")));
		for (int i = 0; i < 400; i++) traces.add(new ArrayList<>(Arrays.asList("A", "C", "end$")));

		double uemsc = computeUemsc(vlmc, traces);
		assertEquals(1.0, uemsc, 1e-9, "Perfect conformance should give uEMSC = 1.0");
	}

	@Test
	public void testPartialConformance() {
		// Log: 800 "A B end$", 200 "A C end$"
		// L("A B end$") = 0.8, M("A B end$") = 0.6 -> diff = 0.2
		// L("A C end$") = 0.2, M("A C end$") = 0.4 -> diff = -0.2 (clamped to 0)
		// surplus = 0.2, uEMSC = 0.8
		List<ArrayList<String>> traces = new ArrayList<>();
		for (int i = 0; i < 800; i++) traces.add(new ArrayList<>(Arrays.asList("A", "B", "end$")));
		for (int i = 0; i < 200; i++) traces.add(new ArrayList<>(Arrays.asList("A", "C", "end$")));

		double uemsc = computeUemsc(vlmc, traces);
		assertEquals(0.8, uemsc, 1e-9, "Partial conformance");
	}

	@Test
	public void testUnknownTrace() {
		// Log: 500 "A B end$", 500 "X Y end$"
		// L("A B end$") = 0.5, M("A B end$") = 0.6 -> diff = -0.1 (clamped)
		// L("X Y end$") = 0.5, M("X Y end$") = 0 -> diff = 0.5
		// surplus = 0.5, uEMSC = 0.5
		List<ArrayList<String>> traces = new ArrayList<>();
		for (int i = 0; i < 500; i++) traces.add(new ArrayList<>(Arrays.asList("A", "B", "end$")));
		for (int i = 0; i < 500; i++) traces.add(new ArrayList<>(Arrays.asList("X", "Y", "end$")));

		double uemsc = computeUemsc(vlmc, traces);
		assertEquals(0.5, uemsc, 1e-9, "Unknown trace should lower uEMSC");
	}

	@Test
	public void testSingleDistinctTrace() {
		// Log: 100 "A B end$"
		// L("A B end$") = 1.0, M("A B end$") = 0.6 -> diff = 0.4
		// surplus = 0.4, uEMSC = 0.6
		List<ArrayList<String>> traces = new ArrayList<>();
		for (int i = 0; i < 100; i++) traces.add(new ArrayList<>(Arrays.asList("A", "B", "end$")));

		double uemsc = computeUemsc(vlmc, traces);
		assertEquals(0.6, uemsc, 1e-9, "Single trace degenerate case");
	}

	@Test
	public void testUemscBounds() {
		// uEMSC should always be in [0, 1] regardless of input
		List<ArrayList<String>> traces1 = new ArrayList<>();
		for (int i = 0; i < 100; i++) traces1.add(new ArrayList<>(Arrays.asList("X", "end$")));
		double uemsc1 = computeUemsc(vlmc, traces1);
		assertTrue(uemsc1 >= 0.0 && uemsc1 <= 1.0, "uEMSC in [0,1], got: " + uemsc1);

		List<ArrayList<String>> traces2 = new ArrayList<>();
		for (int i = 0; i < 60; i++) traces2.add(new ArrayList<>(Arrays.asList("A", "B", "end$")));
		for (int i = 0; i < 40; i++) traces2.add(new ArrayList<>(Arrays.asList("A", "C", "end$")));
		double uemsc2 = computeUemsc(vlmc, traces2);
		assertTrue(uemsc2 >= 0.0 && uemsc2 <= 1.0, "uEMSC in [0,1], got: " + uemsc2);
	}
}
