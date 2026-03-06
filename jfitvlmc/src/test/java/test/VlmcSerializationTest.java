package test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import vlmc.NextSymbolsDistribution;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class VlmcSerializationTest {

	@TempDir
	File tempDir;

	private Locale originalLocale;

	@BeforeEach
	public void forceUsLocale() {
		// toString uses %f which is locale-dependent; parser expects "."
		originalLocale = Locale.getDefault();
		Locale.setDefault(Locale.US);
	}

	@org.junit.jupiter.api.AfterEach
	public void restoreLocale() {
		Locale.setDefault(originalLocale);
	}

	private VlmcRoot buildSimpleVlmc() {
		VlmcRoot vlmc = new VlmcRoot();
		vlmc.setLabel("root");
		vlmc.setDist(new NextSymbolsDistribution());

		VlmcNode nodeA = new VlmcNode();
		nodeA.setLabel("A");
		NextSymbolsDistribution distA = new NextSymbolsDistribution();
		distA.getSymbols().add("B");
		distA.getSymbols().add("end$");
		distA.getProbability().add(0.7);
		distA.getProbability().add(0.3);
		distA.totalCtx = 10;
		nodeA.setDist(distA);
		nodeA.setParent(vlmc);
		vlmc.getChildren().add(nodeA);

		VlmcNode nodeB = new VlmcNode();
		nodeB.setLabel("B");
		NextSymbolsDistribution distB = new NextSymbolsDistribution();
		distB.getSymbols().add("A");
		distB.getSymbols().add("end$");
		distB.getProbability().add(0.6);
		distB.getProbability().add(0.4);
		distB.totalCtx = 10;
		nodeB.setDist(distB);
		nodeB.setParent(vlmc);
		vlmc.getChildren().add(nodeB);

		return vlmc;
	}

	private VlmcRoot buildDeepVlmc() {
		VlmcRoot vlmc = buildSimpleVlmc();

		// Add child under A: label "C" (predecessor C before A)
		VlmcNode nodeA = vlmc.getChidByLabel("A");
		VlmcNode nodeCA = new VlmcNode();
		nodeCA.setLabel("C");
		NextSymbolsDistribution distCA = new NextSymbolsDistribution();
		distCA.getSymbols().add("B");
		distCA.getSymbols().add("end$");
		distCA.getProbability().add(0.8);
		distCA.getProbability().add(0.2);
		distCA.totalCtx = 5;
		nodeCA.setDist(distCA);
		nodeCA.setParent(nodeA);
		nodeA.getChildren().add(nodeCA);

		return vlmc;
	}

	private File writeVlmcToFile(VlmcRoot vlmc) throws IOException {
		File file = new File(tempDir, "test.vlmc");
		try (FileWriter fw = new FileWriter(file)) {
			fw.write(vlmc.toString(new String[]{}));
		}
		return file;
	}

	private void assertDistributionsEqual(NextSymbolsDistribution expected, NextSymbolsDistribution actual, double tol) {
		assertEquals(expected.getSymbols().size(), actual.getSymbols().size(), "Symbol count mismatch");
		for (int i = 0; i < expected.getSymbols().size(); i++) {
			assertEquals(expected.getSymbols().get(i), actual.getSymbols().get(i), "Symbol mismatch at " + i);
			assertEquals(expected.getProbability().get(i), actual.getProbability().get(i), tol,
				"Probability mismatch for " + expected.getSymbols().get(i));
		}
		assertEquals(expected.totalCtx, actual.totalCtx, tol, "totalCtx mismatch");
	}

	@Test
	public void testRoundTripBase() throws IOException {
		VlmcRoot original = buildSimpleVlmc();
		File file = writeVlmcToFile(original);

		VlmcRoot loaded = new VlmcRoot();
		loaded.setLabel("root");
		VlmcRoot.nNodes = 0;
		loaded.parseVLMC(file.getAbsolutePath());

		assertEquals(original.getChildren().size(), loaded.getChildren().size(),
			"Number of top-level children should match");

		for (int i = 0; i < original.getChildren().size(); i++) {
			VlmcNode origChild = original.getChildren().get(i);
			VlmcNode loadedChild = loaded.getChidByLabel(origChild.getLabel());
			assertNotNull(loadedChild, "Missing child: " + origChild.getLabel());
			assertDistributionsEqual(origChild.getDist(), loadedChild.getDist(), 1e-6);
		}
	}

	@Test
	public void testRoundTripDeep() throws IOException {
		VlmcRoot original = buildDeepVlmc();
		File file = writeVlmcToFile(original);

		VlmcRoot loaded = new VlmcRoot();
		loaded.setLabel("root");
		VlmcRoot.nNodes = 0;
		loaded.parseVLMC(file.getAbsolutePath());

		// Check depth: A should have child C
		VlmcNode loadedA = loaded.getChidByLabel("A");
		assertNotNull(loadedA);
		VlmcNode loadedCA = loadedA.getChidByLabel("C");
		assertNotNull(loadedCA, "Deep child C under A should be preserved");
		assertDistributionsEqual(
			original.getChidByLabel("A").getChidByLabel("C").getDist(),
			loadedCA.getDist(), 1e-6);
	}

	@Test
	public void testRoundTripPreservesTotalCtx() throws IOException {
		VlmcRoot original = buildSimpleVlmc();
		File file = writeVlmcToFile(original);

		VlmcRoot loaded = new VlmcRoot();
		loaded.setLabel("root");
		VlmcRoot.nNodes = 0;
		loaded.parseVLMC(file.getAbsolutePath());

		for (VlmcNode origChild : original.getChildren()) {
			VlmcNode loadedChild = loaded.getChidByLabel(origChild.getLabel());
			assertEquals(origChild.getDist().totalCtx, loadedChild.getDist().totalCtx, 1e-6,
				"totalCtx should be preserved for " + origChild.getLabel());
		}
	}

	@Test
	public void testLikelihoodInvariantAfterRoundTrip() throws IOException {
		VlmcRoot original = buildSimpleVlmc();
		File file = writeVlmcToFile(original);

		VlmcRoot loaded = new VlmcRoot();
		loaded.setLabel("root");
		VlmcRoot.nNodes = 0;
		loaded.parseVLMC(file.getAbsolutePath());

		// Test likelihood on both
		ArrayList<String> trace = new ArrayList<>(Arrays.asList("A", "B", "end$"));
		ArrayList<Double> likOriginal = original.getLikelihood(trace);
		ArrayList<Double> likLoaded = loaded.getLikelihood(trace);

		assertEquals(likOriginal.size(), likLoaded.size());
		for (int i = 0; i < likOriginal.size(); i++) {
			assertEquals(likOriginal.get(i), likLoaded.get(i), 1e-6,
				"Likelihood mismatch at step " + i);
		}
	}

	@Test
	public void testComprehensiveRoundTrip() throws IOException {
		// Build a multi-level VLMC: root -> {A, B}, A -> {C, D}, C -> {E}
		VlmcRoot original = buildDeepVlmc();

		// Add D under A
		VlmcNode nodeA = original.getChidByLabel("A");
		VlmcNode nodeDA = new VlmcNode();
		nodeDA.setLabel("D");
		NextSymbolsDistribution distDA = new NextSymbolsDistribution();
		distDA.getSymbols().addAll(Arrays.asList("A", "B", "end$"));
		distDA.getProbability().addAll(Arrays.asList(0.5, 0.3, 0.2));
		distDA.totalCtx = 20;
		nodeDA.setDist(distDA);
		nodeDA.setParent(nodeA);
		nodeA.getChildren().add(nodeDA);

		// Add E under C (depth 3)
		VlmcNode nodeC = nodeA.getChidByLabel("C");
		VlmcNode nodeEC = new VlmcNode();
		nodeEC.setLabel("E");
		NextSymbolsDistribution distEC = new NextSymbolsDistribution();
		distEC.getSymbols().addAll(Arrays.asList("B", "A"));
		distEC.getProbability().addAll(Arrays.asList(0.9, 0.1));
		distEC.totalCtx = 3;
		nodeEC.setDist(distEC);
		nodeEC.setParent(nodeC);
		nodeC.getChildren().add(nodeEC);

		// Count nodes in original
		int expectedNodeCount = countNodes(original);

		// Step 1: Save
		File file1 = new File(tempDir, "model1.vlmc");
		try (FileWriter fw = new FileWriter(file1)) {
			fw.write(original.toString(new String[]{""}));
		}

		// Step 2: Load and verify structure
		VlmcRoot loaded = new VlmcRoot();
		loaded.setLabel("root");
		VlmcRoot.nNodes = 0;
		loaded.parseVLMC(file1.getAbsolutePath());
		int loadedNodeCount = VlmcRoot.nNodes;

		assertEquals(expectedNodeCount, loadedNodeCount,
			"Node count should match after load");

		// Verify order
		VlmcRoot.order = -1;
		loaded.computeOrder(0);
		assertTrue(VlmcRoot.order >= 3,
			"Order should be at least 3 (root->A->C->E), got " + VlmcRoot.order);

		// Verify every node has a distribution with correct content
		assertAllDistributionsPresent(original, loaded);

		// Step 3: Save loaded model to second file
		File file2 = new File(tempDir, "model2.vlmc");
		try (FileWriter fw = new FileWriter(file2)) {
			fw.write(loaded.toString(new String[]{""}));
		}

		// Step 4: Compare files — they should be identical
		String content1 = new String(Files.readAllBytes(file1.toPath()));
		String content2 = new String(Files.readAllBytes(file2.toPath()));
		assertEquals(content1, content2,
			"Re-serialized model file should be identical to original");
	}

	@Test
	public void testRoundTripWithItalianLocale() throws IOException {
		// Temporarily set Italian locale (uses comma as decimal separator)
		Locale.setDefault(Locale.ITALY);

		VlmcRoot original = buildDeepVlmc();

		// Save with Italian locale — should still use dots (Locale.US in toString)
		File file1 = new File(tempDir, "italian1.vlmc");
		try (FileWriter fw = new FileWriter(file1)) {
			fw.write(original.toString(new String[]{""}));
		}

		// Verify file content uses dots, not commas
		String content = new String(Files.readAllBytes(file1.toPath()));
		assertFalse(content.contains("[0,"),
			"Serialized output should use dot decimals even with Italian locale");
		assertTrue(content.contains("[0."),
			"Serialized output should contain dot decimal probabilities");

		// Load back and verify
		VlmcRoot loaded = new VlmcRoot();
		loaded.setLabel("root");
		VlmcRoot.nNodes = 0;
		loaded.parseVLMC(file1.getAbsolutePath());

		// Verify distributions match
		assertAllDistributionsPresent(original, loaded);

		// Re-save and compare
		File file2 = new File(tempDir, "italian2.vlmc");
		try (FileWriter fw = new FileWriter(file2)) {
			fw.write(loaded.toString(new String[]{""}));
		}

		String content1 = new String(Files.readAllBytes(file1.toPath()));
		String content2 = new String(Files.readAllBytes(file2.toPath()));
		assertEquals(content1, content2,
			"Round-trip under Italian locale should produce identical files");
	}

	private int countNodes(VlmcRoot root) {
		AtomicInteger count = new AtomicInteger(0);
		root.DFS(node -> count.incrementAndGet());
		return count.get();
	}

	private void assertAllDistributionsPresent(VlmcRoot expected, VlmcRoot actual) {
		// DFS through expected, find corresponding node in actual, compare distributions
		expected.DFS(origNode -> {
			// Navigate to the same node in the loaded tree
			ArrayList<String> ctx = origNode.getCtx();
			VlmcNode loadedNode = actual;
			for (int i = ctx.size() - 1; i >= 0; i--) {
				loadedNode = loadedNode.getChidByLabel(ctx.get(i));
				assertNotNull(loadedNode,
					"Missing node in loaded tree for context: " + ctx);
			}
			assertNotNull(loadedNode.getDist(),
				"Distribution should not be null for node with context: " + ctx);
			assertFalse(loadedNode.getDist().getSymbols().isEmpty(),
				"Distribution should have symbols for node with context: " + ctx);
			assertDistributionsEqual(origNode.getDist(), loadedNode.getDist(), 1e-5);
		});
	}
}
