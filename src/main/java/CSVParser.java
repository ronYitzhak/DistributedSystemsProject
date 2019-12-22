import com.opencsv.CSVReader;

import java.io.FileReader;
import java.util.HashSet;

public class CSVParser {
    private static final String candidatesFileName = "candidates.csv";
    private static final String stateClientsFileName = "stateClients.csv";

    public static void getServers(String file) {
        // TODO: revisit - which format?
    }

    public static HashSet<String> getCandidates() {
        try {
            FileReader filereader = new FileReader(candidatesFileName);
            CSVReader csvReader = new CSVReader(filereader);
            String[] nextRecord;
            HashSet<String> candidates = new HashSet<>();
            while ((nextRecord = csvReader.readNext()) != null) {
                var candidate = nextRecord[0];
                candidates.add(candidate);
            }
            return candidates;
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static HashSet<String> getClientsPerState(String state)
    {
        try {
            FileReader filereader = new FileReader(stateClientsFileName);
            CSVReader csvReader = new CSVReader(filereader);
            String[] nextRecord;

            HashSet<String> clients = new HashSet<>();
            while ((nextRecord = csvReader.readNext()) != null) {
                for (int i = 0; i < nextRecord.length; i++) {
                    var cell = nextRecord[i];
                    if (i == 0 && !cell.equals(state)) break;
                    if (i != 0) clients.add(cell);
                }
            }
            return clients;
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
