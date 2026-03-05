import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;

import fitvlmc.Trace2EcfIntegrator;
import ECFEntity.Flow;

public class TestEcfGenerator {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: TestEcfGenerator <input_trace_file> <output_ecf_file>");
            System.exit(1);
        }

        String inputFile = args[0];
        String outputFile = args[1];

        try {
            // Read trace file
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(inputFile), "UTF8")
            );

            String str;
            String content = "";
            while ((str = reader.readLine()) != null) {
                content += str;
            }
            reader.close();

            System.out.println("Read trace file: " + inputFile);
            System.out.println("Content length: " + content.length() + " characters");

            // Generate ECF using integrated implementation
            Flow ecfModel = Trace2EcfIntegrator.createEcfFromContentWithValidation(content);

            // Write ECF to file
            OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(new File(outputFile)),
                StandardCharsets.UTF_8
            );
            writer.write(ecfModel.toString());
            writer.close();

            System.out.println("ECF generated successfully: " + outputFile);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}