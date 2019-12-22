import org.apache.zookeeper.*;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoteServer implements Watcher {
    protected static final Logger LOG = LoggerFactory.getLogger(VoteServer.class);
    private static ZooKeeper zooKeeper;
    private static String root = "/Election";

    //votes - Map from the voter to the candidate id
    private HashMap<String, Integer> votes = new HashMap<>();
    //votesCount - Map from candidate id to his total votes count
    private HashMap<Integer, Integer> votesCount = new HashMap<>();
    //lastVote - vote pending to be committed
    private Pair<String,Integer> lastVote = null;
    //isPending
    private AtomicBoolean isPending = new AtomicBoolean(false);
    //the gRPC address of the server
    private String selfAddress;
    //the state number of the server
    private int stateNumber;
    private String serverPath;

    private VoteServer(String selfAddress, int stateNumber, int port, String zkHost) throws IOException {
        //this.id = id;
        this.selfAddress = selfAddress;
        this.stateNumber = stateNumber;
        zooKeeper = new ZooKeeper(zkHost, 3000, this);
        // TODO: initialize gRPC port
        //TODO: init all canindates to count 0
        LOG.info("VoteServer of state " + String.valueOf(stateNumber) + " created!");
    }

    private void propose() throws KeeperException, InterruptedException {
        if (zooKeeper.exists(root, true) == null) {
            zooKeeper.create(root, new byte[] {}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        if (zooKeeper.exists(root + "/" + String.valueOf(stateNumber), true) == null) {
            zooKeeper.create(root + "/" + String.valueOf(stateNumber), new byte[] {}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        if (zooKeeper.exists(root + "/" + String.valueOf(stateNumber) + "/LiveNodes", true) == null) {
            zooKeeper.create(root + "/" + String.valueOf(stateNumber) + "/LiveNodes", new byte[] {}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        if (zooKeeper.exists(root + "/" + String.valueOf(stateNumber) + "/Commit", true) == null) {
            zooKeeper.create(root + "/" + String.valueOf(stateNumber) + "/Commit", String.valueOf(0).getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        serverPath = zooKeeper.create(root + "/", selfAddress.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);
    }

    private void onNodeDataChanged(String nodeName) {
        if (!nodeName.equals(root + "/" + String.valueOf(stateNumber) + "/Commit")) return;
        LOG.info("Server: " + this.toString() + " commit NodeDataChanged");
        if (!(isPending.get() && lastVote != null)) return; //for safety
        LOG.info("Server: " + this.toString() + " isPending");
        String voterName = lastVote.getValue0();
        int newVote = lastVote.getValue1();
        if (votes.containsKey(voterName)) {
            int oldVote = votes.get(voterName);
            int oldCount = votesCount.get(oldVote);
            votesCount.put(oldVote, oldCount - 1);
            LOG.info("Server: " + this.toString() + " voterName: "+voterName +" oldVote: "+ String.valueOf(oldVote));
        }
        votes.put(voterName, newVote);
        int oldCount = votesCount.get(newVote);
        votesCount.put(newVote, oldCount + 1);
        LOG.info("Server: " + this.toString() + " voterName: "+voterName +" newVote: "+ String.valueOf(newVote));
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

    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
        //TODO: get parameter for builder from user\commandline\somehow
        var voteServer = new VoteServer("127.0.0.1:2020", 2, 2020, "127.0.0.1:2181");
        voteServer.propose();
        System.out.println("Hello");
        while (true) {}
    }
}
