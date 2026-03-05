package fitvlmc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads a CSV event log (process mining format) and converts it to the internal
 * trace format used by REMEMBER (states separated by spaces, traces separated by "end$").
 */
public class CsvEventLogReader {

	private String caseColumn;
	private String activityColumn;
	private String timestampColumn;
	private String separator;

	public CsvEventLogReader(String caseColumn, String activityColumn, String timestampColumn, String separator) {
		this.caseColumn = caseColumn;
		this.activityColumn = activityColumn;
		this.timestampColumn = timestampColumn;
		this.separator = separator;
	}

	/**
	 * Reads a CSV file and returns the trace content in internal format.
	 * Groups events by case_id, sorts each case by timestamp, and produces:
	 * "activity1 activity2 ... end$ activity3 activity4 ... end$"
	 */
	public String readCsv(File csvFile) throws IOException {
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(csvFile), "UTF-8"));

		String headerLine = reader.readLine();
		if (headerLine == null || headerLine.trim().isEmpty()) {
			reader.close();
			throw new IOException("CSV file is empty or has no header");
		}

		String[] headers = headerLine.split(separator, -1);
		for (int i = 0; i < headers.length; i++) {
			headers[i] = headers[i].trim().replace("\"", "");
		}

		int caseIdx = findColumnIndex(headers, caseColumn);
		int activityIdx = findColumnIndex(headers, activityColumn);
		int timestampIdx = findColumnIndex(headers, timestampColumn);

		if (caseIdx == -1) {
			reader.close();
			throw new IOException("Column '" + caseColumn + "' not found in CSV header: " + headerLine);
		}
		if (activityIdx == -1) {
			reader.close();
			throw new IOException("Column '" + activityColumn + "' not found in CSV header: " + headerLine);
		}

		// Read all events, grouped by case_id (preserve insertion order)
		LinkedHashMap<String, ArrayList<String[]>> caseEvents = new LinkedHashMap<>();
		String line;
		int lineNum = 1;
		while ((line = reader.readLine()) != null) {
			lineNum++;
			if (line.trim().isEmpty()) {
				continue;
			}
			String[] fields = line.split(separator, -1);
			for (int i = 0; i < fields.length; i++) {
				fields[i] = fields[i].trim().replace("\"", "");
			}

			if (fields.length <= Math.max(caseIdx, Math.max(activityIdx, timestampIdx >= 0 ? timestampIdx : 0))) {
				System.err.println("WARNING: Skipping malformed line " + lineNum + ": " + line);
				continue;
			}

			String caseId = fields[caseIdx];
			String activity = fields[activityIdx];
			String timestamp = timestampIdx >= 0 ? fields[timestampIdx] : String.valueOf(lineNum);

			caseEvents.computeIfAbsent(caseId, k -> new ArrayList<>());
			caseEvents.get(caseId).add(new String[]{activity, timestamp});
		}
		reader.close();

		if (caseEvents.isEmpty()) {
			throw new IOException("CSV file contains no data rows");
		}

		// Sort each case by timestamp and build output string
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, ArrayList<String[]>> entry : caseEvents.entrySet()) {
			ArrayList<String[]> events = entry.getValue();
			// Sort by timestamp (lexicographic works for ISO format dates)
			events.sort((a, b) -> a[1].compareTo(b[1]));

			for (String[] event : events) {
				if (sb.length() > 0) {
					sb.append(" ");
				}
				sb.append(event[0]);
			}
			sb.append(" end$");
		}

		return sb.toString();
	}

	/**
	 * Reads CSV and returns traces as list of lists (each inner list = one trace of activities).
	 * Useful for likelihood computation where traces need to be processed individually.
	 */
	public ArrayList<ArrayList<String>> readCsvAsTraces(File csvFile) throws IOException {
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(csvFile), "UTF-8"));

		String headerLine = reader.readLine();
		if (headerLine == null || headerLine.trim().isEmpty()) {
			reader.close();
			throw new IOException("CSV file is empty or has no header");
		}

		String[] headers = headerLine.split(separator, -1);
		for (int i = 0; i < headers.length; i++) {
			headers[i] = headers[i].trim().replace("\"", "");
		}

		int caseIdx = findColumnIndex(headers, caseColumn);
		int activityIdx = findColumnIndex(headers, activityColumn);
		int timestampIdx = findColumnIndex(headers, timestampColumn);

		if (caseIdx == -1) {
			reader.close();
			throw new IOException("Column '" + caseColumn + "' not found in CSV header: " + headerLine);
		}
		if (activityIdx == -1) {
			reader.close();
			throw new IOException("Column '" + activityColumn + "' not found in CSV header: " + headerLine);
		}

		LinkedHashMap<String, ArrayList<String[]>> caseEvents = new LinkedHashMap<>();
		String line;
		int lineNum = 1;
		while ((line = reader.readLine()) != null) {
			lineNum++;
			if (line.trim().isEmpty()) {
				continue;
			}
			String[] fields = line.split(separator, -1);
			for (int i = 0; i < fields.length; i++) {
				fields[i] = fields[i].trim().replace("\"", "");
			}

			if (fields.length <= Math.max(caseIdx, Math.max(activityIdx, timestampIdx >= 0 ? timestampIdx : 0))) {
				continue;
			}

			String caseId = fields[caseIdx];
			String activity = fields[activityIdx];
			String timestamp = timestampIdx >= 0 ? fields[timestampIdx] : String.valueOf(lineNum);

			caseEvents.computeIfAbsent(caseId, k -> new ArrayList<>());
			caseEvents.get(caseId).add(new String[]{activity, timestamp});
		}
		reader.close();

		ArrayList<ArrayList<String>> traces = new ArrayList<>();
		for (Map.Entry<String, ArrayList<String[]>> entry : caseEvents.entrySet()) {
			ArrayList<String[]> events = entry.getValue();
			events.sort((a, b) -> a[1].compareTo(b[1]));

			ArrayList<String> trace = new ArrayList<>();
			for (String[] event : events) {
				trace.add(event[0]);
			}
			trace.add("end$");
			traces.add(trace);
		}

		return traces;
	}

	/**
	 * Detects if a file is likely a CSV event log (vs internal trace format).
	 * Checks: file extension .csv, or first line contains commas and no "end$".
	 */
	public static boolean isCsvFile(File file) {
		if (file.getName().toLowerCase().endsWith(".csv")) {
			return true;
		}
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
			String firstLine = br.readLine();
			if (firstLine != null && firstLine.contains(",") && !firstLine.contains("end$")) {
				return true;
			}
		} catch (IOException e) {
			// Fall back to non-CSV
		}
		return false;
	}

	private int findColumnIndex(String[] headers, String columnName) {
		for (int i = 0; i < headers.length; i++) {
			if (headers[i].equalsIgnoreCase(columnName)) {
				return i;
			}
		}
		return -1;
	}
}
