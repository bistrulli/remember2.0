package test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class EndToEndTest {

	@TempDir
	File tempDir;

	/**
	 * Generates a synthetic CSV with 1000 traces from 5 distinct patterns:
	 *   A B C      400 traces (40%)
	 *   A C        300 traces (30%)
	 *   B A C      150 traces (15%)
	 *   B C        100 traces (10%)
	 *   A B A C     50 traces  (5%)
	 *
	 * No underscores in activity names to avoid label2Ctx splitting issues.
	 */
	private File generateSyntheticCsv() throws IOException {
		File csv = new File(tempDir, "synthetic.csv");
		String[][] patterns = {
			{"A", "B", "C"},
			{"A", "C"},
			{"B", "A", "C"},
			{"B", "C"},
			{"A", "B", "A", "C"}
		};
		int[] counts = {400, 300, 150, 100, 50};

		try (FileWriter fw = new FileWriter(csv)) {
			fw.write("case_id,activity,timestamp\n");
			int caseId = 1;
			int ts = 1000000;
			for (int p = 0; p < patterns.length; p++) {
				for (int c = 0; c < counts[p]; c++) {
					for (String activity : patterns[p]) {
						fw.write(String.format("%d,%s,2024-01-01 %02d:%02d:%02d\n",
							caseId, activity,
							(ts / 3600) % 24, (ts / 60) % 60, ts % 60));
						ts++;
					}
					caseId++;
					ts += 10; // gap between traces
				}
			}
		}
		return csv;
	}

	private String runJar(String... args) throws IOException, InterruptedException {
		// Find the fat JAR — check both multi-module and single-module paths
		File jar = new File("jfitvlmc/target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar");
		if (!jar.exists()) {
			jar = new File("target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar");
		}
		Assumptions.assumeTrue(jar.exists(),
			"Fat JAR not found — skip integration test. Build with 'mvn package -DskipTests' first.");

		List<String> cmd = new ArrayList<>();
		cmd.add("java");
		cmd.add("-jar");
		cmd.add(jar.getAbsolutePath());
		for (String arg : args) cmd.add(arg);

		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true);
		Process proc = pb.start();
		String output = new String(proc.getInputStream().readAllBytes());
		int exitCode = proc.waitFor();
		if (exitCode != 0) {
			fail("JAR exited with code " + exitCode + "\nOutput:\n" + output);
		}
		return output;
	}

	@Test
	public void testFullPipelineUemscNearOne() throws IOException, InterruptedException {
		File csv = generateSyntheticCsv();
		File vlmcFile = new File(tempDir, "model.vlmc");
		File outFile = new File(tempDir, "sim_output.mat");

		// Step 1: Fitting with alfa=1 (no pruning), nsim=1
		String fitOutput = runJar(
			"--infile", csv.getAbsolutePath(),
			"--csv-case", "case_id",
			"--csv-activity", "activity",
			"--csv-timestamp", "timestamp",
			"--vlmcfile", vlmcFile.getAbsolutePath(),
			"--outfile", outFile.getAbsolutePath(),
			"--alfa", "1",
			"--nsim", "1"
		);

		assertTrue(vlmcFile.exists(), "VLMC file should be created");

		// Step 2: Likelihood + uEMSC
		String likOutput = runJar(
			"--vlmc", vlmcFile.getAbsolutePath(),
			"--lik", csv.getAbsolutePath(),
			"--csv-case", "case_id",
			"--csv-activity", "activity",
			"--csv-timestamp", "timestamp"
		);

		// Step 3: Verify uEMSC
		assertTrue(likOutput.contains("uEMSC"), "Output should contain uEMSC");

		// Extract uEMSC value
		Pattern uemscPattern = Pattern.compile("uEMSC.*?([0-9]+\\.[0-9]+)");
		Matcher m = uemscPattern.matcher(likOutput);
		assertTrue(m.find(), "Should find uEMSC value in output:\n" + likOutput);

		double uemsc = Double.parseDouble(m.group(1));
		assertTrue(uemsc >= 0.0 && uemsc <= 1.0, "uEMSC should be in [0,1], got: " + uemsc);

		// With alfa=1 (no pruning) and training=test, uEMSC should be very close to 1
		if (uemsc < 0.99) {
			// Print diagnostic info for debugging
			System.err.println("=== DIAGNOSTIC: uEMSC < 0.99 ===");
			System.err.println("uEMSC = " + uemsc);
			System.err.println("Fit output:\n" + fitOutput);
			System.err.println("Lik output:\n" + likOutput);
		}
		assertTrue(uemsc >= 0.90,
			"uEMSC should be >= 0.90 with alfa=1 and training=test, got: " + uemsc);
	}
}
