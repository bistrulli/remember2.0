package test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

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
}
