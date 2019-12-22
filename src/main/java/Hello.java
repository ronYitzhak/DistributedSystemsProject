import com.opencsv.CSVReader;

import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello");
        readDataLineByLine("./stateCandidates.csv");
    }

    // Java code to illustrate reading a
    // CSV file line by line
    private static void readDataLineByLine(String file)
    {
        try {

            // Create an object of filereader class with CSV file name as a parameter.
            FileReader filereader = new FileReader(file);

            // create csvReader object passing file reader as a parameter
            CSVReader csvReader = new CSVReader(filereader);
            String[] nextRecord;

            // TODO: revisit
            HashMap<Integer, Set> stateIdToClients = new HashMap<>();
            HashMap<String, Integer> stateNameToStateId = new HashMap<>();
            HashMap<Integer, String> stateIdToStateName = new HashMap<>();
            var stateCounter = -1; // stating from zero

            // we are going to read data line by line
            while ((nextRecord = csvReader.readNext()) != null) {
                for (String cell : nextRecord) {
                    System.out.print(cell + "\t");
                }
                for (int i = 0; i < nextRecord.length; i++) {
                    var cell = nextRecord[i];
                    if (i == 0) {
                        // cell is a new state
                        stateCounter++;
                        stateNameToStateId.put(cell, stateCounter);
                        stateIdToStateName.put(stateCounter, cell);
                        stateIdToClients.put(stateCounter, new HashSet());
                    } else {
                        // cell is a client

                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
