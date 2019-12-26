package Impl;

import io.grpc.stub.StreamObserver;
import org.apache.zookeeper.*;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import protos.AdminOuterClass;
import protos.ElectionsServerGrpc;
import protos.ElectionsServerOuterClass;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ElectionsServerImpl extends ElectionsServerGrpc.ElectionsServerImplBase implements Watcher {
    private static final Logger LOG = LoggerFactory.getLogger(ElectionsServerImpl.class);
    private static String root = "/Application.Election";
    private static String startPath = "/Application.Election/Start";

    private ConcurrentHashMap<String, String> votes = new ConcurrentHashMap<>(); // clientName -> candidateName
    private ConcurrentHashMap<String, Integer> votesCount = new ConcurrentHashMap<>(); // candidateName -> total votes count
    private Pair<String, String> lastVote = null; // vote pending to be committed (clientName -> candidateName)
    private AtomicBoolean isPending = new AtomicBoolean(false);
    private boolean isActive = false;
    private String selfAddress; // the gRPC address of the server
    private String state;
    private String serverPath;
    private String statePath;
    private String masterPath;
    private List<ElectionsClient> slaves = new ArrayList<>();

    // gRPC:
    private Server grpcVoteServer;

    public ElectionsServerImpl(String selfAddress, String state, int grpcPort) throws IOException {
        this.selfAddress = selfAddress;
        this.state = state;
        this.statePath = root + "/" + state;
        initGrpcVoteServer(grpcPort);
        //TODO: init all canindates to count 0
        HashSet<String> stateClients = CustomCSVParser.getClientsPerState(state);
        HashSet<String> candidates = CustomCSVParser.getCandidates();
        LOG.info("VoteServer of state " + state + " created!");
    }

    private void initGrpcVoteServer(int grpcPort) {
        try {
            grpcVoteServer = ServerBuilder.forPort(grpcPort)
                    .addService(this)
                    .build()
                    .start();
        } catch (IOException e) {
            e.printStackTrace();
            LOG.error("could not init grpc vote server");
            System.exit(1);
        }
    }

    public void propose() throws KeeperException, InterruptedException {
        ZooKeeperService.createNodeIfNotExists(root, CreateMode.PERSISTENT, new byte[]{});
        ZooKeeperService.createNodeIfNotExists(statePath, CreateMode.PERSISTENT, new byte[]{});
        ZooKeeperService.createNodeIfNotExists(statePath + "/LiveNodes", CreateMode.PERSISTENT, new byte[]{});
        ZooKeeperService.createNodeIfNotExists(statePath + "/Commit", CreateMode.PERSISTENT, new byte[]{0});
        serverPath = ZooKeeperService.createSeqNode(statePath + "/LiveNodes/", CreateMode.EPHEMERAL_SEQUENTIAL, selfAddress.getBytes());
        //define watchers
        ZooKeeperService.setWatcherOnNode(startPath);
        ZooKeeperService.setWatcherOnChildren(statePath + "/LiveNodes");
    }

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

    private void configureMaster(){
        LOG.info("Server: " + this.toString() + " is the state master");
        slaves = new ArrayList<>();
        var hosts = ZooKeeperService.getChildrenData(statePath+"/LiveNodes");
        for(String host: hosts){
                slaves.add(new ElectionsClient(host));
        }
    }

    private void onNodeCreated(String nodePath) {
        if (!nodePath.equals(startPath)) return;
        LOG.info("Server: " + this.toString() + " start NodeCreated");
        votes = new ConcurrentHashMap<>();
        votesCount = new ConcurrentHashMap<>();
        lastVote = null;
        isPending = new AtomicBoolean(false);
        slaves = new ArrayList<>();
        isActive = true;
        masterPath = ZooKeeperService.getMasterByState(state);
        LOG.info("Server: " + this.toString() + " chose master: " + masterPath);
        if(masterPath.equals(serverPath)) configureMaster();
    }

    private void onNodeDeleted(String nodePath) {
        if (!nodePath.equals(masterPath)) return;
        LOG.info("Server: " + this.toString() + " master NodeDeleted");
        //TODO: lock when getting master?
        //lastVote = null;
        isPending.set(false);
        masterPath = ZooKeeperService.getMasterByState(state);
        LOG.info("Server: " + this.toString() + " chose master: " + masterPath);
        if(masterPath.equals(serverPath)) configureMaster();
    }

    @Override
    public void process(WatchedEvent watchedEvent) {
        var nodePath = watchedEvent.getPath();
        LOG.info("Server: " + this.toString() + " got event: " + watchedEvent.getType().toString());
        switch (watchedEvent.getType()) {
            case NodeDataChanged:
                onNodeDataChanged(nodePath);
                break;
            case NodeCreated:
                onNodeCreated(nodePath);
                break;
            case NodeDeleted:
                onNodeDeleted(nodePath);
                break;
            case NodeChildrenChanged:
            case ChildWatchRemoved:
            case DataWatchRemoved:
            case None:
                break;
        }
    }

    @Override
    public String toString() {
        return "VoteServer{" +
                "selfAddress='" + selfAddress + '\'' +
                ", stateNumber=" + state +
                ", serverPath='" + serverPath + '\'' +
                '}';
    }

    @Override
    public void vote(ElectionsServerOuterClass.VoteRequest request, StreamObserver<AdminOuterClass.Void> responseObserver) {
        AdminOuterClass.Void rep = AdminOuterClass.Void
                .newBuilder()
                .build();
        responseObserver.onNext(rep);
        if (!isActive) {
            LOG.warn("Application not started");
            responseObserver.onCompleted();
            return;
        }
        while (isPending.compareAndExchange(false, true)) ;
        ZooKeeperService.setWatcherOnNode(statePath + "/Commit");
        lastVote = new Pair<>(request.getVoterName(), request.getCandidateName());
        responseObserver.onCompleted();
    }

    @Override
    public void broadcastVote(ElectionsServerOuterClass.VoteRequest request, StreamObserver<AdminOuterClass.Void> responseObserver) {
        AdminOuterClass.Void rep = AdminOuterClass.Void
                .newBuilder()
                .build();
        responseObserver.onNext(rep);
        if (!isActive) {
            LOG.warn("Application not started");
            responseObserver.onCompleted();
            return;
        }
        if (!masterPath.equals(serverPath)) {
            LOG.warn("Not a Master");
            responseObserver.onCompleted();
            return;
        }
        if (!request.getState().equals(state)) {
            LOG.warn("Not the Master's state");
            responseObserver.onCompleted();
            return;
        }
        synchronized (this) {
            for (ElectionsClient slave : slaves) {
                slave.vote(request.getVoterName(), request.getCandidateName(), request.getState());
            }
            ZooKeeperService.incDataByOne(statePath + "/Commit");
        }
        responseObserver.onCompleted();
    }

    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
//        org.apache.log4j.BasicConfigurator.configure();
        //TODO: get parameter for builder from user\commandline\somehow
        Scanner input = new Scanner(System.in);
        System.out.print("gRPC self ip: ");
        String host = input.nextLine();
        System.out.print("gRPC port: ");
        int grpcPort = input.nextInt();
        var electionsServer = new ElectionsServerImpl(host+":"+grpcPort, "California", grpcPort); // zkHost: "127.0.0.1:2181"
        electionsServer.propose();
        System.out.println("Hello");
        while (true) {}
    }
}
