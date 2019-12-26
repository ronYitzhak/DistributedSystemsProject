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
    private static String globalPath = "/Application.Election/Global";

    private ConcurrentHashMap<String, String> votes = new ConcurrentHashMap<>(); // clientName -> candidateName
    private ConcurrentHashMap<String, Integer> votesCount = new ConcurrentHashMap<>(); // candidateName -> total votes count
    private Pair<String, String> lastVote = null; // vote pending to be committed (clientName -> candidateName)
    private AtomicBoolean isPending = new AtomicBoolean(false);
    private boolean isActive = false;
    private Object lockStartStop = new Object();
    private Object lockVote = new Object();
    private String selfAddress; // the gRPC address of the server
    private String state;
    private String serverPath;
    private String globalServerPath;
    private String statePath;
    private String masterPath;
    private String globalMasterPath;
    private List<ElectionsClient> slaves = new ArrayList<>();
    private List<ElectionsClient> globalSlaves = new ArrayList<>();
    private ElectionsClient master;
    private ElectionsClient globalMaster;



    // gRPC:
    private Server grpcElectionServer;

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

    public void propose() throws KeeperException, InterruptedException {
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

    private void configureGlobalMaster() {
        LOG.info("Server: " + this.toString() + " is the global master");
        slaves = new ArrayList<>();
        var hosts = ZooKeeperService.getChildrenData(globalPath + "/LiveNodes", true);
        if (hosts != null) {
            for (String host : hosts) {
                globalSlaves.add(new ElectionsClient(host));
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
            for (String host : hosts) {
                slaves.add(new ElectionsClient(host));
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

    private void onStart() {
        LOG.info("Server: " + this.toString() + " started!");
        votes = new ConcurrentHashMap<>();
        votesCount = new ConcurrentHashMap<>();
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
        LOG.info("Server: " + this.toString() + " stoped!");
        isActive = false;
    }

    private void onNodeDeleted(String nodePath) {
        if (nodePath.equals(masterPath)) onMasterDelete();
        else if (nodePath.equals(globalMasterPath)) onGlobalMasterDelete();
    }

    private void onMasterDelete() {
        LOG.info("Server: " + this.toString() + " master NodeDeleted");
        //TODO: lock when getting master?
        //lastVote = null;
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
        isPending.set(false);
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
                onNodeDataChanged(nodePath);
                break;
            case NodeDeleted:
                onNodeDeleted(nodePath);
                break;
            case NodeChildrenChanged:
                onNodeChildrenChanged(nodePath);
            case NodeCreated:
            case ChildWatchRemoved:
            case DataWatchRemoved:
            case None:
                break;
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
    public void start(AdminOuterClass.Void request, StreamObserver<AdminOuterClass.Void> responseObserver) {
        AdminOuterClass.Void rep = AdminOuterClass.Void
                .newBuilder()
                .build();
        responseObserver.onNext(rep);
        onStart();
        responseObserver.onCompleted();
    }

    @Override
    public void stop(AdminOuterClass.Void request, StreamObserver<AdminOuterClass.Void> responseObserver) {
        AdminOuterClass.Void rep = AdminOuterClass.Void
                .newBuilder()
                .build();
        responseObserver.onNext(rep);
        onStop();
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
        if (!request.getState().equals(state)) {
            LOG.warn("Not the server state");
            responseObserver.onCompleted();
            return;
        }
        if (!masterPath.equals(serverPath)) {
            master.broadcastVote(request.getVoterName(), request.getCandidateName(), request.getState());
        } else {
            synchronized (lockVote) {
                for (ElectionsClient slave : slaves) {
                    slave.vote(request.getVoterName(), request.getCandidateName(), request.getState());
                }
                ZooKeeperService.incDataByOne(statePath + "/Commit");
            }
        }
        responseObserver.onCompleted();
    }

    @Override
    public void broadcastStart(AdminOuterClass.Void request, StreamObserver<AdminOuterClass.Void> responseObserver) {
        AdminOuterClass.Void rep = AdminOuterClass.Void
                .newBuilder()
                .build();
        responseObserver.onNext(rep);
        if (!globalMasterPath.equals(globalServerPath)) {
            globalMaster.broadcastStart();
        } else {
            //TODO: start on start?
            synchronized (lockStartStop) {
                for (ElectionsClient slave : globalSlaves) {
                    slave.start();
                }
            }
        }
        responseObserver.onCompleted();
    }

    @Override
    public void broadcastStop(AdminOuterClass.Void request, StreamObserver<AdminOuterClass.Void> responseObserver) {
        AdminOuterClass.Void rep = AdminOuterClass.Void
                .newBuilder()
                .build();
        responseObserver.onNext(rep);
        if (!globalMasterPath.equals(globalServerPath)) {
            globalMaster.broadcastStop();
        } else {
            synchronized (lockStartStop) {
                for (ElectionsClient slave : globalSlaves) {
                    slave.stop();
                }
            }
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
