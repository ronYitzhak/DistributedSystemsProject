import io.grpc.stub.StreamObserver;
import org.apache.zookeeper.*;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import protos.AdminOuterClass;
import protos.VoterGrpc;
import protos.VoterOuterClass;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoteImpl extends VoterGrpc.VoterImplBase implements Watcher {
    private static final Logger LOG = LoggerFactory.getLogger(VoteImpl.class);
    private static ZooKeeper zooKeeper;
    private static String root = "/Election";

    private HashMap<String, Integer> votes = new HashMap<>(); // Map from the voter to the candidate id
    private HashMap<Integer, Integer> votesCount = new HashMap<>(); // Map from candidate id to his total votes count
    private Pair<String,Integer> lastVote = null; // vote pending to be committed
    private AtomicBoolean isPending = new AtomicBoolean(false);
    private String selfAddress; // the gRPC address of the server
    private int stateNumber;
    private String serverPath;
    private String statePath;

    // gRPC:
    private Server grpcVoteServer;

    private VoteImpl(String selfAddress, int stateNumber, int grpcPort, String zkHost) throws IOException {
        //this.id = id;
        this.selfAddress = selfAddress;
        this.stateNumber = stateNumber;
        this.statePath = root + "/" + stateNumber;
        zooKeeper = new ZooKeeper(zkHost, 3000, this);
        initGrpcVoteServer(grpcPort);
        //TODO: init all canindates to count 0
        LOG.info("VoteServer of state " + stateNumber + " created!");
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

    private void propose() throws KeeperException, InterruptedException {
        createIfNotExists(root, CreateMode.PERSISTENT);
        createIfNotExists(statePath, CreateMode.PERSISTENT);
        createIfNotExists(statePath + "/LiveNodes", CreateMode.PERSISTENT);
        createIfNotExists(statePath + "/Commit", CreateMode.PERSISTENT);
        serverPath = zooKeeper.create(statePath + "/LiveNodes", selfAddress.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);
    }

    private void createIfNotExists(String path, CreateMode createMode) throws KeeperException, InterruptedException {
        if (zooKeeper.exists(path, true) == null) {
            zooKeeper.create(path, new byte[] {}, ZooDefs.Ids.OPEN_ACL_UNSAFE, createMode);
        }
    }

    private void onNodeDataChanged(String nodeName) {
        if (!nodeName.equals(statePath + "/Commit")) return;
        LOG.info("Server: " + this.toString() + " commit NodeDataChanged");
        if (!(isPending.get() && lastVote != null)) return; //for safety
        LOG.info("Server: " + this.toString() + " isPending");
        String voterName = lastVote.getValue0();
        int newVote = lastVote.getValue1();
        if (votes.containsKey(voterName)) {
            int oldVote = votes.get(voterName);
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

    @Override
    public void process(WatchedEvent watchedEvent) {
        var nodeName = watchedEvent.getState().name();
        LOG.info("Server: " + this.toString() + " got event: " + watchedEvent.getType().toString());
        switch (watchedEvent.getType()) {
            case NodeDataChanged:
                onNodeDataChanged(nodeName);
                break;
            case NodeDeleted:
            case NodeChildrenChanged:
            case ChildWatchRemoved:
            case DataWatchRemoved:
            case NodeCreated:
            case None:
                LOG.info("Server: " + this.toString() + " WE ARE DOOMED!");
                break;
        }
    }

    @Override
    public String toString() {
        return "VoteServer{" +
                "selfAddress='" + selfAddress + '\'' +
                ", stateNumber=" + stateNumber +
                ", serverPath='" + serverPath + '\'' +
                '}';
    }

    @Override
    public void vote(VoterOuterClass.VoteRequest request, StreamObserver<AdminOuterClass.Void> responseObserver) {
        var startPath = root + "/Start";
        try {
            if (zooKeeper.exists(startPath, true) == null) {
                LOG.warn("Application not started");
                return;
            }
        } catch (KeeperException e) {
            e.printStackTrace();
            LOG.error("KeeperException - should not get here");
        } catch (InterruptedException e) {
            e.printStackTrace();
            LOG.error("InterruptedException - should not get here");
        }
        while (isPending.compareAndExchange(false, true));
        lastVote = new Pair<>(request.getVoterName(), request.getCandidateId());
    }

    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
        org.apache.log4j.BasicConfigurator.configure();
        //TODO: get parameter for builder from user\commandline\somehow
        var voteServer = new VoteImpl("127.0.0.1:50051", 1, 50051, "127.0.0.1:2181");
        voteServer.propose();
        System.out.println("Hello");
        while (true) {}
    }
}
