package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import vlmc.NextSymbolsDistribution;
import vlmc.VlmcRoot;

public class NextSymbolsDistributionTest {

	@Test
	public void testGetProbBySymbolPresent() {
		NextSymbolsDistribution dist = new NextSymbolsDistribution();
		dist.getSymbols().add("A");
		dist.getSymbols().add("B");
		dist.getProbability().add(0.6);
		dist.getProbability().add(0.4);

		assertEquals(0.6, dist.getProbBySymbol("A"), 1e-10);
		assertEquals(0.4, dist.getProbBySymbol("B"), 1e-10);
	}

	@Test
	public void testGetProbBySymbolAbsent() {
		NextSymbolsDistribution dist = new NextSymbolsDistribution();
		dist.getSymbols().add("A");
		dist.getProbability().add(1.0);

		assertNull(dist.getProbBySymbol("X"));
	}

	@Test
	public void testNormalizedDistribution() {
		NextSymbolsDistribution dist = new NextSymbolsDistribution();
		dist.getSymbols().add("A");
		dist.getSymbols().add("B");
		dist.getSymbols().add("C");
		dist.getProbability().add(0.5);
		dist.getProbability().add(0.3);
		dist.getProbability().add(0.2);

		double sum = 0;
		for (Double p : dist.getProbability()) {
			sum += p;
		}
		assertEquals(1.0, sum, 1e-10);
	}

	@Test
	public void testToStringAndRoundTrip() {
		// toString uses String.format(%f) which is locale-dependent.
		// parseNextSymbolDistribution regex expects "." as decimal separator.
		// Force US locale for this test to match the parser.
		Locale original_locale = Locale.getDefault();
		Locale.setDefault(Locale.US);
		try {
			NextSymbolsDistribution original = new NextSymbolsDistribution();
			original.getSymbols().add("A");
			original.getSymbols().add("B");
			original.getProbability().add(0.7);
			original.getProbability().add(0.3);
			original.totalCtx = 10;

			String serialized = original.toString();
			String wrapped = "{" + serialized + "}";

			VlmcRoot root = new VlmcRoot();
			NextSymbolsDistribution parsed = root.parseNextSymbolDistribution(wrapped);

			assertEquals(original.getSymbols().size(), parsed.getSymbols().size());
			for (int i = 0; i < original.getSymbols().size(); i++) {
				assertEquals(original.getSymbols().get(i), parsed.getSymbols().get(i));
				assertEquals(original.getProbability().get(i), parsed.getProbability().get(i), 1e-6);
			}
			assertEquals(original.totalCtx, parsed.totalCtx, 1e-6);
		} finally {
			Locale.setDefault(original_locale);
		}
	}

	@Test
	public void testEmptyDistribution() {
		NextSymbolsDistribution dist = new NextSymbolsDistribution();
		assertNull(dist.getProbBySymbol("anything"));
		assertTrue(dist.getSymbols().isEmpty());
		assertTrue(dist.getProbability().isEmpty());
	}

	@Test
	public void testSingleSymbol() {
		NextSymbolsDistribution dist = new NextSymbolsDistribution();
		dist.getSymbols().add("only");
		dist.getProbability().add(1.0);

		assertEquals(1.0, dist.getProbBySymbol("only"), 1e-10);
		assertNull(dist.getProbBySymbol("other"));
	}
}
