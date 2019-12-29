package Impl;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.zookeeper.*;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import protos.ElectionsServerGrpc;
import protos.ElectionsServerOuterClass;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


public class ElectionsServerImpl extends ElectionsServerGrpc.ElectionsServerImplBase implements Watcher {
    private static final Logger LOG = LoggerFactory.getLogger(ElectionsServerImpl.class);
    private static Random rand = new Random();
    private static String root = "/Application.Election";
    private static String globalPath = "/Application.Election/Global";
    private static HashMap<String, HashSet<String>> stateToVoters = new HashMap<>();
    private ConcurrentHashMap<String, String> votes = new ConcurrentHashMap<>(); // clientName -> candidateName
    private ConcurrentHashMap<String, Integer> votesCount = new ConcurrentHashMap<>(); // candidateName -> total votes count
    private Pair<String, String> lastVote = null; // vote pending to be committed (clientName -> candidateName)
    private AtomicBoolean isPending = new AtomicBoolean(false);
    private boolean isActive = false;
    private final Object lockStartStop = new Object();
    private final Object lockVote = new Object();
    private String selfAddress; // the gRPC address of the server
    private String state;
    private String serverPath;
    private String globalServerPath;
    private String statePath;
    private String masterPath;
    private String globalMasterPath;
    private List<Pair<String,ElectionsClient>> slaves = new ArrayList<>();
    private List<Pair<String,ElectionsClient>> globalSlaves = new ArrayList<>();
    private ElectionsClient master;
    private ElectionsClient globalMaster;
    // gRPC:
    private Server grpcElectionServer;

    /*** Global methods ***/

    /*** START: Init methods ***/
    public ElectionsServerImpl() {
        LOG.info("ElectionServer created!");
    }

    public void init(String selfAddress, String state, int grpcPort) {
        this.selfAddress = selfAddress;
        this.state = state;
        this.statePath = root + "/" + state;
        initGrpcElectionsServer(grpcPort);
        initZKnodes();
        stateToVoters = CustomCSVParser.getVotersPerState();
        LOG.info("ElectionsServer of state " + state + " init!");
    }

    private void initGrpcElectionsServer(int grpcPort) {
        try {
            grpcElectionServer = ServerBuilder.forPort(grpcPort)
                    .addService(this)
                    .build()
                    .start();
        } catch (IOException e) {
            e.printStackTrace();
            LOG.error("could not init grpc vote server");
            System.exit(1);
        }
    }

    private void initZKnodes()  {
        ZooKeeperService.createNodeIfNotExists(root, CreateMode.PERSISTENT, new byte[]{});
        ZooKeeperService.createNodeIfNotExists(globalPath, CreateMode.PERSISTENT, new byte[]{});
        ZooKeeperService.createNodeIfNotExists(statePath, CreateMode.PERSISTENT, new byte[]{});
        ZooKeeperService.createNodeIfNotExists(statePath + "/LiveNodes", CreateMode.PERSISTENT, new byte[]{});
        ZooKeeperService.createNodeIfNotExists(globalPath + "/LiveNodes", CreateMode.PERSISTENT, new byte[]{});
        ZooKeeperService.createNodeIfNotExists(statePath + "/Commit", CreateMode.PERSISTENT, new byte[]{0});
        serverPath = ZooKeeperService.createSeqNode(statePath + "/LiveNodes/", CreateMode.EPHEMERAL_SEQUENTIAL, selfAddress.getBytes());
        globalServerPath = ZooKeeperService.createSeqNode(globalPath + "/LiveNodes/", CreateMode.EPHEMERAL_SEQUENTIAL, selfAddress.getBytes());
        globalMasterPath = ZooKeeperService.getGlobalMaster(false);
        if(globalMasterPath.equals(globalServerPath)) configureGlobalMaster();
        else connectGlobalMaster();
    }

    private void configureGlobalMaster() {
        LOG.info("Server: " + this.toString() + " is the global master");
        globalSlaves = new ArrayList<>();
        var hosts = ZooKeeperService.getChildrenData(globalPath + "/LiveNodes", true);
        if (hosts != null) {
            for (Pair<String,String> host : hosts) {
                globalSlaves.add(new Pair<>(host.getValue0(),new ElectionsClient(host.getValue1())));
            }
        }
    }

    private void connectGlobalMaster() {
        LOG.info("Server: " + this.toString() + " is connecting to global master");
        String host = ZooKeeperService.getData(globalMasterPath, true);
        if (host == null) {
            onGlobalMasterDelete();
        }
        globalMaster = new ElectionsClient(host);
    }

    private void configureMaster(){
        LOG.info("Server: " + this.toString() + " is the state master");
        slaves = new ArrayList<>();
        var hosts = ZooKeeperService.getChildrenData(statePath+"/LiveNodes", false);
        if (hosts != null) {
            for (Pair<String,String> host : hosts) {
                slaves.add(new Pair<>(host.getValue0(),new ElectionsClient(host.getValue1())));
            }
        }
    }

    private void connectMaster() {
        LOG.info("Server: " + this.toString() + " is connecting to master");
        String host = ZooKeeperService.getData(masterPath, true);
        if (host == null) {
            onMasterDelete();
        }
        master = new ElectionsClient(host);
    }
    /*** END: Init methods ***/

    /*** START: ElectionsServer methods ***/
    public HashMap<String, HashSet<String>> getStateToVoters() {
        return stateToVoters;
    }

    private void onStart() {
        if(isActive) {
            LOG.info("Server: " + this.toString() + "already started!");
            return;
        }
        LOG.info("Server: " + this.toString() + " started!");
        votes = new ConcurrentHashMap<>();
        votesCount = new ConcurrentHashMap<>();
        HashSet<String> candidates = CustomCSVParser.getCandidates();
        for(String candidate: candidates) {
            votesCount.put(candidate,0);
        }
        lastVote = null;
        isPending = new AtomicBoolean(false);
        slaves = new ArrayList<>();
        isActive = true;
        masterPath = ZooKeeperService.getMasterByState(state, true);
        LOG.info("Server: " + this.toString() + " chose master: " + masterPath);
        if(masterPath.equals(serverPath)) configureMaster();
        else connectMaster();
    }

    private void onStop() {
        if(!isActive) {
            LOG.info("Server: " + this.toString() + "already stopped!");
            return;
        }
        LOG.info("Server: " + this.toString() + " stopped!");
        isActive = false;
    }

    public ElectionsServerOuterClass.VoteStatus.Status sendVote(String voterName, String candidateName, String voterState) {
        ElectionsServerOuterClass.VoteStatus.Status status;
        while (true) {
            try {
                if (this.state.equals(voterState)) {
                    status = broadcastVote(voterName, candidateName, voterState);
                    break;
                } else {
                    ElectionsClient chosenServer = null;
                    try {
                        chosenServer = getStateElectionsClient(voterState);
                        status = chosenServer.broadcastVote(voterName, candidateName, voterState);
                        break;
                    } catch (NullPointerException e) {
                        LOG.error("NullPointerException - should not get here");
                        e.printStackTrace();
                    } finally {
                        chosenServer.shutdown();
                    }
                }
            } catch (StatusRuntimeException e) {
            }
        }
        return status;
    }

    private ElectionsServerOuterClass.VoteStatus.Status broadcastVote(String voterName, String candidateName, String state) {
        if (!masterPath.equals(serverPath)) {
            return master.broadcastVote(voterName, candidateName, state);
        } else {
            if (!isActive) {
                LOG.warn("Application not started");
                return ElectionsServerOuterClass.VoteStatus.Status.ELECTION_NOT_STARTED;
            }
            synchronized (lockVote) {
                for (int i = 0; i < slaves.size();){
                    try {
                        slaves.get(i).getValue1().vote(voterName, candidateName, state);
                        ++i;
                    } catch (StatusRuntimeException e) {
                        if(!ZooKeeperService.exists(slaves.get(i).getValue0(),false)) {
                            slaves.get(i).getValue1().shutdown();
                            slaves.remove(i);
                        }
                    }
                }
                ZooKeeperService.incDataByOne(statePath + "/Commit");
            }
            return ElectionsServerOuterClass.VoteStatus.Status.OK;
        }
    }
    @Override
    public String toString() {
        return "ElectionsServer{" +
                "selfAddress='" + selfAddress + '\'' +
                ", stateNumber=" + state +
                ", serverPath='" + serverPath + '\'' +
                '}';
    }

    private ElectionsClient getStateElectionsClient(String state) {
        List<Pair<String, String>> stateServers = ZooKeeperService.getChildrenData(root + "/" + state + "/LiveNodes", false);
        String chosenHost = stateServers.get(rand.nextInt(stateServers.size())).getValue1();
        return new ElectionsClient(chosenHost);
    }

    private int getNoElectors() {
        // TODO: impl
        return 3;
    }
    /*** END: ElectionsServer methods ***/

    /*** START: watcher methods ***/
    private void onNodeDataChanged(String nodePath){
        if (!nodePath.equals(statePath + "/Commit")) return;
        LOG.info("Server: " + this.toString() + " commit NodeDataChanged");
        if (!(isPending.get() && lastVote != null)) return; //for safety
        LOG.info("Server: " + this.toString() + " isPending");
        String voterName = lastVote.getValue0();
        String newVote = lastVote.getValue1();
        if (votes.containsKey(voterName)) {
            String oldVote = votes.get(voterName);
            int oldCount = votesCount.get(oldVote);
            votesCount.put(oldVote, oldCount - 1);
            LOG.info("Server: " + this.toString() + " voterName: "+voterName +" oldVote: "+ oldVote);
        }
        votes.put(voterName, newVote);
        int oldCount = votesCount.get(newVote);
        votesCount.put(newVote, oldCount + 1);
        LOG.info("Server: " + this.toString() + " voterName: "+voterName +" newVote: "+ newVote);
        lastVote = null;
        isPending.set(false);
    }

    private void onNodeDeleted(String nodePath) {
        if (nodePath.equals(masterPath)) onMasterDelete();
        else if (nodePath.equals(globalMasterPath)) onGlobalMasterDelete();
    }

    private void onMasterDelete() {
        LOG.info("Server: " + this.toString() + " master NodeDeleted");
        //TODO: lock when getting master?
        //lastVote = null;
        master.shutdown();
        isPending.set(false);
        masterPath = ZooKeeperService.getMasterByState(state, false);
        LOG.info("Server: " + this.toString() + " chose master: " + masterPath);
        if(masterPath.equals(serverPath)) configureMaster();
        else connectMaster();
    }

    private void onGlobalMasterDelete() {
        LOG.info("Server: " + this.toString() + "global master NodeDeleted");
        //TODO: lock when getting master?
        //lastVote = null;
        globalMaster.shutdown();
        globalMasterPath = ZooKeeperService.getGlobalMaster(false);
        LOG.info("Server: " + this.toString() + " chose global master: " + globalMasterPath);
        if(globalMasterPath.equals(globalServerPath)) configureGlobalMaster();
        else connectGlobalMaster();
    }

    private void onNodeChildrenChanged(String nodePath) {
        if (nodePath.equals(globalPath + "/LiveNodes")) {
            if (globalServerPath.equals(globalMasterPath)) {
                if (globalSlaves.size() >= ZooKeeperService.getChildrenCount(globalPath + "/LiveNodes", false))
                    return;
                configureGlobalMaster();
            }/* else if(!ZooKeeperService.exists(globalMasterPath, true)){
                onGlobalMasterDelete();
            }*/
        }
        /*if (nodePath.equals(statePath + "/LiveNodes")) {
            if (serverPath.equals(masterPath)) {
                    return;
            } else if(!ZooKeeperService.exists(masterPath, true)){
                onMasterDelete();
            }
        }*/
    }

    @Override
    public void process(WatchedEvent watchedEvent) {
        var nodePath = watchedEvent.getPath();
        LOG.info("Server: " + this.toString() + " got event: " + watchedEvent.getType().toString());
        switch (watchedEvent.getType()) {
            case NodeDataChanged:
                // master inc Commit's zNode data, time to process vote (from lastVote value)
                // TODO: (where is the watcher?), why lastVote==null is commented out?
                onNodeDataChanged(nodePath);
                break;
            case NodeDeleted:
                // master or global master have been deleted, should remove old vote (isPending=false) and choose new master
                // if its me - configure master. otherwise, connect the chosen master
                // TODO: (where is the watcher?)
                onNodeDeleted(nodePath);
                break;
            case NodeChildrenChanged:
                // TODO: what's that?
                onNodeChildrenChanged(nodePath);
            case NodeCreated:
            case ChildWatchRemoved:
            case DataWatchRemoved:
            case None:
                break;
        }
    }
    /*** END: watcher methods ***/

    /*** START: ElectionsServer gRPC methods ***/
    /*** START: ElectionsServer client operation ***/
    @Override
    public void vote(ElectionsServerOuterClass.VoteRequest request, StreamObserver<ElectionsServerOuterClass.Void> responseObserver) {
        ElectionsServerOuterClass.Void rep = ElectionsServerOuterClass.Void
                .newBuilder()
                .build();
        responseObserver.onNext(rep);
        while (isPending.compareAndExchange(false, true)) ;
        ZooKeeperService.setWatcherOnNode(statePath + "/Commit");
        lastVote = new Pair<>(request.getVoterName(), request.getCandidateName());
        responseObserver.onCompleted();
    }
    /*** End: ElectionsServer client operation ***/

    /*** START: ElectionsServer communication methods ***/
    @Override
    public void start(ElectionsServerOuterClass.Void request, StreamObserver<ElectionsServerOuterClass.Void> responseObserver) {
        ElectionsServerOuterClass.Void rep = ElectionsServerOuterClass.Void
                .newBuilder()
                .build();
        responseObserver.onNext(rep);
        onStart();
        responseObserver.onCompleted();
    }

    @Override
    public void stop(ElectionsServerOuterClass.Void request, StreamObserver<ElectionsServerOuterClass.Void> responseObserver) {
        ElectionsServerOuterClass.Void rep = ElectionsServerOuterClass.Void
                .newBuilder()
                .build();
        responseObserver.onNext(rep);
        onStop();
        responseObserver.onCompleted();
    }

    @Override
    public void broadcastVote(ElectionsServerOuterClass.VoteRequest request, StreamObserver<ElectionsServerOuterClass.VoteStatus> responseObserver) {
        ElectionsServerOuterClass.VoteStatus.Status status = broadcastVote(request.getVoterName(), request.getCandidateName(), request.getState());
        ElectionsServerOuterClass.VoteStatus rep = ElectionsServerOuterClass.VoteStatus
                .newBuilder()
                .setStatus(status)
                .build();
        responseObserver.onNext(rep);
        responseObserver.onCompleted();
    }

    @Override
    public void broadcastStart(ElectionsServerOuterClass.Void request, StreamObserver<ElectionsServerOuterClass.Void> responseObserver) {
        ElectionsServerOuterClass.Void rep = ElectionsServerOuterClass.Void
                .newBuilder()
                .build();
        responseObserver.onNext(rep);
        if (!globalMasterPath.equals(globalServerPath)) {
            globalMaster.broadcastStart();
        } else {
            //TODO: start on start?
            synchronized (lockStartStop) {
                for (int i = 0; i < globalSlaves.size();){
                    try {
                        globalSlaves.get(i).getValue1().start();
                        ++i;
                    } catch (StatusRuntimeException e) {
                        if(!ZooKeeperService.exists(globalSlaves.get(i).getValue0(),false)) {
                            globalSlaves.get(i).getValue1().shutdown();
                            globalSlaves.remove(i);
                        }
                    }
                }
            }
        }
        responseObserver.onCompleted();
    }

    @Override
    public void broadcastStop(ElectionsServerOuterClass.Void request, StreamObserver<ElectionsServerOuterClass.Void> responseObserver) {
        ElectionsServerOuterClass.Void rep = ElectionsServerOuterClass.Void
                .newBuilder()
                .build();
        responseObserver.onNext(rep);
        if (!globalMasterPath.equals(globalServerPath)) {
            globalMaster.broadcastStop();
        } else {
            synchronized (lockStartStop) {
                for (int i = 0; i < globalSlaves.size();){
                    try {
                        globalSlaves.get(i).getValue1().stop();
                        ++i;
                    } catch (StatusRuntimeException e) {
                        if(!ZooKeeperService.exists(globalSlaves.get(i).getValue0(),false)) {
                            globalSlaves.get(i).getValue1().shutdown();
                            globalSlaves.remove(i);
                        }
                    }
                }
            }
        }
        responseObserver.onCompleted();
    }
    /*** END: ElectionsServer communication methods ***/

    /*** START: ElectionsServer committee methods ***/
    @Override
    public void electionsGetStatus(ElectionsServerOuterClass.StateStatusRequest request, StreamObserver<ElectionsServerOuterClass.StateStatusResponse> responseObserver) {
        ElectionsServerOuterClass.StateStatusResponse response;
        if (request.getState().equals(this.state)) {
            // if my state -> retrieve result, otherwise -> pick a server from the relevant state by zk and send it the req
            LOG.info("get status from the right state: " + this.state + ". returns the in-memory result");
            response = getStateStatusResponse(request);
        } else {
            // find a server from the relevant state and return its response
            LOG.info("get status from state " + request.getState() + " to state: " + this.state + ". sends the request to the relevant state");
            var electionsClient = getStateElectionsClient(request.getState());
            response = electionsClient.electionsGetStatus(this.state);
        }
        LOG.info("status has been returned: " + response.toString());
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private ElectionsServerOuterClass.StateStatusResponse getStateStatusResponse(ElectionsServerOuterClass.StateStatusRequest request) {
        var stateStatusBuilder = ElectionsServerOuterClass.StateStatusResponse.newBuilder();

        // create CandidateStateStatus objects from votes counts and add them to the response
        votesCount.entrySet().stream()
                .map(e ->
                    ElectionsServerOuterClass.CandidateStateStatus.newBuilder()
                            .setCandidateName(e.getKey())
                            .setCount(e.getValue())
                            .build()
                )
                .forEach(stateStatusBuilder::addCandidatesStatus);

        String leadingCandidate = votesCount.entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .get();

        return stateStatusBuilder
                .setState(request.getState())
                .setLeadingCandidateName(leadingCandidate)
                .setNumberOfElectors(getNoElectors())
                .build();
    }

    /*** END: ElectionsServer committee methods ***/
    /*** END: ElectionsServer gRPC methods ***/

    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
//        org.apache.log4j.BasicConfigurator.configure();
        //TODO: get parameter for builder from user\commandline\somehow
        Scanner input = new Scanner(System.in);
        System.out.print("gRPC self ip: ");
        String host = input.nextLine();
        System.out.print("gRPC port: ");
        int grpcPort = input.nextInt();
        var electionsServer = new ElectionsServerImpl(); // zkHost: "127.0.0.1:2181"
        electionsServer.init(host+":"+grpcPort, "California", grpcPort);
        System.out.println("Hello");
        while (true) {}
    }
}
