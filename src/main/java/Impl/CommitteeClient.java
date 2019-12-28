package Impl;

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
    private static final int maxNoRetries = servers.size() / 2; // TODO: revisit

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

    public void start() {
        // TODO: impl
    }

    public void stop() {
        // TODO: impl
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

        do {
            stateStatus = electionsClient.electionsGetStatus(state);
            if (stateStatus != null) {
                LOG.info("CommitteeClient returned state status for state: " + state + ": " + stateStatus.toString() + ". no retries: " + noRetries);
                return stateStatus;
            } else {
                LOG.info("CommitteeClient could not get state status for state: " + state + ". retrying. current no retries: " + noRetries);
                electionsClient = getRandomElectionsClient(true);
                noRetries++;
            }
        } while (noRetries < maxNoRetries);

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
        var stateStatusResponseList = states.parallelStream().map(this::getStatus).collect(Collectors.toList());

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

        // TODO: random leading or list of leading?
        String leadingCandidate = candidateToCount.entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .get();

        var resBuilder = ElectionsServerOuterClass.GlobalStatusResponse.newBuilder();
        stateStatusResponseList.forEach(resBuilder::addCandidatesStatus);

        return resBuilder
                .setLeadingCandidateName(leadingCandidate)
                .build();
    }
}
