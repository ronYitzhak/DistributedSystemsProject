package Impl;

import io.grpc.StatusRuntimeException;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protos.ElectionsServerOuterClass;

import java.util.*;
import java.util.stream.Collectors;

public class CommitteeClient {
    private static final Logger LOG = LoggerFactory.getLogger(CommitteeClient.class);

    private static List<Pair<String, Integer>> servers = CustomCSVParser.getServers(); // (host, port)
    private static HashSet<String> candidates = CustomCSVParser.getCandidates();
    private static List<String> states = CustomCSVParser.getStates();
    private static Random random = new Random();
    private static final int maxNoRetries = servers.size() / 2;

    // lazy cache of clients from any state
    private static List<ElectionsClient> electionsClients = new LinkedList<>();

    private static ElectionsClient getRandomElectionsClient(Boolean refresh) {
        if (!refresh && !electionsClients.isEmpty()) {
            var index = random.nextInt(electionsClients.size());
            LOG.info("getRandomElectionsClient returned client of index: " + index);
            return electionsClients.get(index);
        }
        // handle failures - if connection failed, call again with refresh = true
        var index = random.nextInt(servers.size());
        var hostPort = servers.get(index);
        String host = hostPort.getValue0();
        int port = hostPort.getValue1();
        var electionsClient = new ElectionsClient(host + ":" + port);
        electionsClients.add(electionsClient);
        LOG.info("getRandomElectionsClient returned server of index: " + index + ", host: " + host + " port: " + port + ". referesh: " + refresh.toString());
        return electionsClient;
    }

    public void startElections() {
        var electionsClient = getRandomElectionsClient(false);
        int noRetries = 0;
        while (noRetries < maxNoRetries) {
            try {
                LOG.info("CommitteeClient calling start");
                LOG.info("CommitteeClient calling start from server: " + electionsClient.toString());
                electionsClient.broadcastStart();
                LOG.info("Elections started");
                return;
            } catch (StatusRuntimeException e) {
                electionsClient = getRandomElectionsClient(true);
                LOG.info("CommitteeClient could not start elections. current no retries: " + noRetries);
                noRetries++;
            }
        }
        LOG.error("CommitteeClient error: max no of retries reached, elections couldn't be started.");
    }

    public void stopElections() {
        var electionsClient = getRandomElectionsClient(false);
        int noRetries = 0;
        while (noRetries < maxNoRetries) {
            try {
                LOG.info("CommitteeClient calling stop");
                LOG.info("CommitteeClient calling stop from server: " + electionsClient.toString());
                electionsClient.broadcastStop();
                LOG.info("Elections stopped");
                return;
            } catch (StatusRuntimeException e) {
                electionsClient = getRandomElectionsClient(true);
                LOG.info("CommitteeClient could not stop elections. current no retries: " + noRetries);
                noRetries++;
            }
        }
        LOG.error("CommitteeClient error: max no of retries reached, elections couldn't be stopped.");
    }

    /**
     * get status per state
     * committee client asks for state1 status from a random server from state2, s2
     * s2 asks for state1 status from a random server from state 1, s1
     * s1 returns its on-memory status
     * request - state name
     * responseObserver - state name, list of candidates status on the state, no. electors, leading candidate name
     */
    public ElectionsServerOuterClass.StateStatusResponse getStatus(String state) {
        var electionsClient = getRandomElectionsClient(false);
        ElectionsServerOuterClass.StateStatusResponse stateStatus;
        int noRetries = 0;

        while (noRetries < maxNoRetries) {
            try {
                stateStatus = electionsClient.electionsGetStatus(state);
                LOG.debug("CommitteeClient returned state status for state: " + state + ". no retries: " + noRetries);
                return stateStatus;
            } catch (StatusRuntimeException e) {
                LOG.debug("CommitteeClient could not get state status for state: " + state + ". retrying. current no retries: " + noRetries);
                electionsClient = getRandomElectionsClient(true);
                noRetries++;
            }
        }
        LOG.error("CommitteeClient error: could not get state status for state: " + state);
        return null;
    }

    /**
     * get global status
     * committee client iterates over all states, for each state: choose random server and sends it getStatus request
     * after receiving all the StateStatusResponses, it calculates the leader over all
     * request - empty request
     * responseObserver - list of StateStatusResponse (per state), leading candidate name over all
     */
    public ElectionsServerOuterClass.GlobalStatusResponse getGlobalStatus() {
        // retrieve status from all the states
        var stateStatusResponseList = states.stream().map(this::getStatus).collect(Collectors.toList());

        // init vote counters
        HashMap<String, Integer> candidateToCount = new HashMap<>();
        candidates.forEach(candidate -> candidateToCount.put(candidate, 0));

        // put vote counters to candidates
        stateStatusResponseList.stream()
                .flatMap(s -> s.getCandidatesStatusList().stream())
                .collect(Collectors.groupingBy(ElectionsServerOuterClass.CandidateStateStatus::getCandidateName))
                .forEach((key, value) -> {
                    var count = value.stream().mapToInt(ElectionsServerOuterClass.CandidateStateStatus::getCount).sum();
                    candidateToCount.put(key, count);
                });

        String leadingCandidate = candidateToCount.entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .get();

        var resBuilder = ElectionsServerOuterClass.GlobalStatusResponse.newBuilder();
        stateStatusResponseList.forEach(resBuilder::addStateStatus);

        var eleBuilder = ElectionsServerOuterClass.ElectorsStatusResponse.newBuilder();
        stateStatusResponseList.stream().map(status -> ElectionsServerOuterClass.CandidateGlobalStatus
                .newBuilder()
                .setCandidateName(status.getLeadingCandidateName())
                .setElectorsCount(status.getNumberOfElectors())
                .build()).
                collect(Collectors.groupingBy(status -> status.getCandidateName(),Collectors.summingInt(status -> status.getElectorsCount())))
                .forEach((candidateName, candidateElectors) -> eleBuilder.addCandidatesStatus(ElectionsServerOuterClass.CandidateGlobalStatus
                        .newBuilder()
                        .setCandidateName(candidateName)
                        .setElectorsCount(candidateElectors)
                        .build()));

        return resBuilder
                .setLeadingCandidateName(leadingCandidate)
                .setElectorsStatus(eleBuilder)
                .build();
    }

    public static void main(String[] args) {
        var committeeClient = new CommitteeClient();

        Scanner input = new Scanner(System.in);
        while (true) {
            System.out.print("supported commands: start, stop, status, global_status\n");
            System.out.print("please insert command: ");
            String command = input.nextLine();

            if (command.equals("start")) {
                try {
                    System.out.print("executing command: " + command + "...");
                    committeeClient.startElections();
                } catch (Exception e) {
                    System.out.print("****Exception****\n" + e);
                }
            } else if (command.equals("stop")) {
                try {
                    System.out.print("executing command: " + command + "...");
                    committeeClient.stopElections();
                } catch (Exception e) {
                    System.out.print("****Exception****\n" + e);
                }
            } else if (command.equals("status")) {
                try {
                    System.out.print("please choose a state: ");
                    String state = input.nextLine();
                    System.out.print("executing command: " + command + " for country" + state + "...");
                    var status = committeeClient.getStatus(state);
                    System.out.print("status: " + status.toString());
                } catch (Exception e) {
                    System.out.print("****Exception****\n" + e);
                }
            } else if (command.equals("global_status")) {
                try {
                    System.out.print("executing command: " + command + "...");
                    var globalStatus = committeeClient.getGlobalStatus();
                    System.out.print("global status: " + globalStatus.toString());
                } catch (Exception e) {
                    System.out.print("****Exception****\n" + e);
                }
            } else {
                System.out.print("command: " + command + " is not valid. please insert a valid command.");
            }
        }
    }
}