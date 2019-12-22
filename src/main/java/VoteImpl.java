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
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoteImpl extends VoterGrpc.VoterImplBase implements Watcher {
    private static final Logger LOG = LoggerFactory.getLogger(VoteImpl.class);
    private static ZooKeeper zooKeeper;
    private static String root = "/Election";
    private static String startPath = "/Election/Start";

    private HashMap<String, String> votes = new HashMap<>(); // clientName -> candidateName
    private HashMap<String, Integer> votesCount = new HashMap<>(); // candidateName -> total votes count
    private Pair<String, String> lastVote = null; // vote pending to be committed (clientName -> candidateName)
    private AtomicBoolean isPending = new AtomicBoolean(false);
    private boolean isActive = false;
    private String selfAddress; // the gRPC address of the server
    private String state;
    private String serverPath;
    private String statePath;
    private String masterPath;
    private List<VoteClient> slaves = new ArrayList<>();

    // gRPC:
    private Server grpcVoteServer;

    private VoteImpl(String selfAddress, String state, int grpcPort, String zkHost) throws IOException {
        //this.id = id;
        this.selfAddress = selfAddress;
        this.state = state;
        this.statePath = root + "/" + state;
        zooKeeper = new ZooKeeper(zkHost, 3000, this);
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

    private void propose() throws KeeperException, InterruptedException {
        createIfNotExists(root, CreateMode.PERSISTENT);
        createIfNotExists(statePath, CreateMode.PERSISTENT);
        createIfNotExists(statePath + "/LiveNodes", CreateMode.PERSISTENT);
        createIfNotExists(statePath + "/Commit", CreateMode.PERSISTENT);
        serverPath = zooKeeper.create(statePath + "/LiveNodes/", selfAddress.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);
        //define watchers
        zooKeeper.exists(startPath, true);
        zooKeeper.getChildren(statePath + "/LiveNodes",true);
    }

    private void createIfNotExists(String path, CreateMode createMode) throws KeeperException, InterruptedException {
        if (zooKeeper.exists(path, false) == null) {
            if (path.equals(statePath + "/Commit"))
                zooKeeper.create(path, new byte[]{0}, ZooDefs.Ids.OPEN_ACL_UNSAFE, createMode);
            else
                zooKeeper.create(path, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, createMode);
        }
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

    private void getMaster() {
        List<String> nodes;
        try {
            nodes = zooKeeper.getChildren(statePath + "/LiveNodes", true);
        } catch (KeeperException e) {
            e.printStackTrace();
            LOG.error("KeeperException - should not get here");
            return;
        } catch (InterruptedException e) {
            e.printStackTrace();
            LOG.error("InterruptedException - should not get here");
            return;
        }
        Collections.sort(nodes);
        masterPath = statePath + "/LiveNodes/" + nodes.get(0);
        LOG.info("Server: " + this.toString() + " chose master: " + masterPath);
        //define watchers
        try {
            if(zooKeeper.exists(masterPath, true) == null) getMaster();
        } catch (KeeperException e) {
            e.printStackTrace();
            LOG.error("KeeperException - should not get here");
            return;
        } catch (InterruptedException e) {
            e.printStackTrace();
            LOG.error("InterruptedException - should not get here");
            return;
        }
    }

    private void configureMaster(){
        LOG.info("Server: " + this.toString() + " is the state master");
        slaves = new ArrayList<>();
        List<String> nodes;
        try {
            nodes = zooKeeper.getChildren(statePath+"/LiveNodes", true);
        } catch (KeeperException e) {
            e.printStackTrace();
            LOG.error("KeeperException - should not get here");
            return;
        } catch (InterruptedException e) {
            e.printStackTrace();
            LOG.error("InterruptedException - should not get here");
            return;
        }
        String host= null;
        for(String node: nodes){
            try {
                host = new String(zooKeeper.getData(statePath + "/LiveNodes/"+node,false,null));
                slaves.add(new VoteClient(host));
            } catch (KeeperException e) {
                e.printStackTrace();
                LOG.error("KeeperException - should not get here");
                return;
            } catch (InterruptedException e) {
                e.printStackTrace();
                LOG.error("InterruptedException - should not get here");
                return;
            }
        }
    }

    private void onNodeCreated(String nodePath) {
        if (!nodePath.equals(startPath)) return;
        LOG.info("Server: " + this.toString() + " start NodeCreated");
        votes = new HashMap<>();
        votesCount = new HashMap<>();
        lastVote = null;
        isPending = new AtomicBoolean(false);
        slaves = new ArrayList<>();
        isActive = true;
        getMaster();
        if(masterPath.equals(serverPath)) configureMaster();
    }

    private void onNodeDeleted(String nodePath) {
        if (!nodePath.equals(masterPath)) return;
        LOG.info("Server: " + this.toString() + " master NodeDeleted");
        //TODO: lock when getting master?
        //lastVote = null;
        isPending.set(false);
        getMaster();
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
    public void vote(VoterOuterClass.VoteRequest request, StreamObserver<AdminOuterClass.Void> responseObserver) {
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
        try {
            zooKeeper.exists(statePath + "/Commit", true);
        } catch (KeeperException e) {
            e.printStackTrace();
            LOG.error("KeeperException - should not get here");
            responseObserver.onCompleted();
            return;
        } catch (InterruptedException e) {
            e.printStackTrace();
            LOG.error("InterruptedException - should not get here");
            responseObserver.onCompleted();
            return;
        }
        lastVote = new Pair<>(request.getVoterName(), request.getCandidateName());
        responseObserver.onCompleted();
    }

    @Override
    public void masterVote(VoterOuterClass.VoteRequest request, StreamObserver<AdminOuterClass.Void> responseObserver) {
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
            for (VoteClient slave : slaves) {
                slave.vote(request.getVoterName(), request.getCandidateName(), request.getState());
            }
            try {
                byte[] data = zooKeeper.getData(statePath + "/Commit", true, null);
                data[0]++;
                zooKeeper.setData(statePath + "/Commit", data, -1);
            } catch (KeeperException e) {
                e.printStackTrace();
                LOG.error("KeeperException - should not get here");
            } catch (InterruptedException e) {
                e.printStackTrace();
                LOG.error("InterruptedException - should not get here");
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
        var voteServer = new VoteImpl(host+":"+grpcPort, "California", grpcPort, "127.0.0.1:2181");
        voteServer.propose();
        System.out.println("Hello");
        while (true) {}
    }
}
