package test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fitvlmc.CsvEventLogReader;

public class CsvEventLogReaderTest {

	@TempDir
	File tempDir;

	private File createCsvFile(String filename, String content) throws IOException {
		File file = new File(tempDir, filename);
		try (FileWriter fw = new FileWriter(file)) {
			fw.write(content);
		}
		return file;
	}

	@Test
	public void testStandardCsv() throws IOException {
		String csv = "case_id,activity,timestamp\n"
				+ "1,login,2024-01-01 10:00:00\n"
				+ "1,browse,2024-01-01 10:05:00\n"
				+ "1,checkout,2024-01-01 10:10:00\n"
				+ "2,login,2024-01-01 11:00:00\n"
				+ "2,browse,2024-01-01 11:03:00\n";

		File csvFile = createCsvFile("test.csv", csv);
		CsvEventLogReader reader = new CsvEventLogReader("case_id", "activity", "timestamp", ",");
		String result = reader.readCsv(csvFile);

		assertEquals("login browse checkout end$ login browse end$", result);
	}

	@Test
	public void testTimestampOrdering() throws IOException {
		// Events are NOT in order — reader should sort by timestamp
		String csv = "case_id,activity,timestamp\n"
				+ "1,checkout,2024-01-01 10:10:00\n"
				+ "1,login,2024-01-01 10:00:00\n"
				+ "1,browse,2024-01-01 10:05:00\n";

		File csvFile = createCsvFile("unordered.csv", csv);
		CsvEventLogReader reader = new CsvEventLogReader("case_id", "activity", "timestamp", ",");
		String result = reader.readCsv(csvFile);

		assertEquals("login browse checkout end$", result);
	}

	@Test
	public void testCustomColumnNames() throws IOException {
		String csv = "id;task;time\n"
				+ "A;start;2024-01-01\n"
				+ "A;end;2024-01-02\n";

		File csvFile = createCsvFile("custom.csv", csv);
		CsvEventLogReader reader = new CsvEventLogReader("id", "task", "time", ";");
		String result = reader.readCsv(csvFile);

		assertEquals("start end end$", result);
	}

	@Test
	public void testTabSeparator() throws IOException {
		String csv = "case_id\tactivity\ttimestamp\n"
				+ "1\tA\t2024-01-01\n"
				+ "1\tB\t2024-01-02\n";

		File csvFile = createCsvFile("tab.csv", csv);
		CsvEventLogReader reader = new CsvEventLogReader("case_id", "activity", "timestamp", "\t");
		String result = reader.readCsv(csvFile);

		assertEquals("A B end$", result);
	}

	@Test
	public void testSingleTrace() throws IOException {
		String csv = "case_id,activity,timestamp\n"
				+ "X,only_activity,2024-01-01\n";

		File csvFile = createCsvFile("single.csv", csv);
		CsvEventLogReader reader = new CsvEventLogReader("case_id", "activity", "timestamp", ",");
		String result = reader.readCsv(csvFile);

		assertEquals("only_activity end$", result);
	}

	@Test
	public void testEmptyFileThrows() throws IOException {
		File csvFile = createCsvFile("empty.csv", "");
		CsvEventLogReader reader = new CsvEventLogReader("case_id", "activity", "timestamp", ",");

		assertThrows(IOException.class, () -> reader.readCsv(csvFile));
	}

	@Test
	public void testMissingColumnThrows() throws IOException {
		String csv = "id,activity,timestamp\n"
				+ "1,A,2024-01-01\n";

		File csvFile = createCsvFile("missing.csv", csv);
		CsvEventLogReader reader = new CsvEventLogReader("case_id", "activity", "timestamp", ",");

		assertThrows(IOException.class, () -> reader.readCsv(csvFile));
	}

	@Test
	public void testReadCsvAsTraces() throws IOException {
		String csv = "case_id,activity,timestamp\n"
				+ "1,A,2024-01-01 10:00\n"
				+ "1,B,2024-01-01 10:05\n"
				+ "2,C,2024-01-01 11:00\n"
				+ "2,D,2024-01-01 11:05\n";

		File csvFile = createCsvFile("traces.csv", csv);
		CsvEventLogReader reader = new CsvEventLogReader("case_id", "activity", "timestamp", ",");
		ArrayList<ArrayList<String>> traces = reader.readCsvAsTraces(csvFile);

		assertEquals(2, traces.size());
		assertEquals("[A, B, end$]", traces.get(0).toString());
		assertEquals("[C, D, end$]", traces.get(1).toString());
	}

	@Test
	public void testIsCsvFileDetection() throws IOException {
		File csvFile = createCsvFile("data.csv", "case_id,activity,timestamp\n1,A,2024\n");
		File txtFile = createCsvFile("data.txt", "A B end$ C D end$");

		assertTrue(CsvEventLogReader.isCsvFile(csvFile));
		assertFalse(CsvEventLogReader.isCsvFile(txtFile));
	}

	@Test
	public void testMultipleTracesPreserveOrder() throws IOException {
		String csv = "case_id,activity,timestamp\n"
				+ "3,Z,2024-01-01\n"
				+ "1,A,2024-01-01\n"
				+ "2,B,2024-01-01\n"
				+ "1,C,2024-01-02\n";

		File csvFile = createCsvFile("order.csv", csv);
		CsvEventLogReader reader = new CsvEventLogReader("case_id", "activity", "timestamp", ",");
		String result = reader.readCsv(csvFile);

		// LinkedHashMap preserves insertion order: case 3 first, then 1, then 2
		assertEquals("Z end$ A C end$ B end$", result);
	}
}
