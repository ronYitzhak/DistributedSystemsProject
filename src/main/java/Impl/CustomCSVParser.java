package Impl;

import com.opencsv.CSVReader;
import org.javatuples.Pair;

import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class CustomCSVParser {
    private static final String candidatesFileName = "candidates.csv";
    private static final String stateClientsFileName = "stateClients.csv";
    private static final String serversFileName = "servers.csv";
    private static final String statesFileName = "states.csv";

    public static HashMap<String, List<Pair<String, Integer>>> getServersPerState() { // state -> set of servers: (host, port)
        try {
            FileReader filereader = new FileReader(serversFileName);
            CSVReader csvReader = new CSVReader(filereader);
            String[] nextRecord;
            HashMap<String, List<Pair<String, Integer>>> serversPerState = new HashMap<>();
            while ((nextRecord = csvReader.readNext()) != null) {
                var serverState = nextRecord[0];
                if (!serversPerState.containsKey(serverState)) {
                    serversPerState.put(serverState, new LinkedList<>());
                }
                String host = nextRecord[1];
                Integer port = Integer.valueOf(nextRecord[2]);
                serversPerState.get(serverState).add(new Pair<>(host, port));
            }
            return serversPerState;
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static List<Pair<String, Integer>> getServers() {
        try {
            FileReader filereader = new FileReader(serversFileName);
            CSVReader csvReader = new CSVReader(filereader);
            String[] nextRecord;
            List<Pair<String, Integer>> servers = new LinkedList<>();
            while ((nextRecord = csvReader.readNext()) != null) {
                String host = nextRecord[1];
                Integer port = Integer.valueOf(nextRecord[2]);
                servers.add(new Pair<>(host, port));
            }
            return servers;
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static List<String> getStates() {
        try {
            FileReader filereader = new FileReader(statesFileName);
            CSVReader csvReader = new CSVReader(filereader);
            String[] nextRecord;
            List<String> states = new LinkedList<>();
            while ((nextRecord = csvReader.readNext()) != null) {
                String state = nextRecord[0];
                states.add(state);
            }
            return states;
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static int getElectorsOfState(String state) {
        try {
            FileReader filereader = new FileReader(statesFileName);
            CSVReader csvReader = new CSVReader(filereader);
            String[] nextRecord;
            while ((nextRecord = csvReader.readNext()) != null) {
                if(nextRecord[0].equals(state))
                    return Integer.parseInt(nextRecord[1]);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        return -1;
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

    public static HashMap<String, HashSet<String>> getVotersPerState()
    {
        try {
            FileReader filereader = new FileReader(stateClientsFileName);
            CSVReader csvReader = new CSVReader(filereader);
            String[] nextRecord;

            HashMap<String,HashSet<String>> votersPerState = new HashMap<>();

            HashSet<String> voters = new HashSet<>();
            String state = "";
            while ((nextRecord = csvReader.readNext()) != null) {
                for (int i = 0; i < nextRecord.length; i++) {
                    var cell = nextRecord[i];
                    if (i == 0) state = cell;
                    if (i != 0) voters.add(cell);
                }
                votersPerState.put(state,voters);
                voters = new HashSet<>();
            }
            return votersPerState;
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
