import org.apache.zookeeper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;

public class VoteServer implements Watcher {
    private static final Logger LOG = LoggerFactory.getLogger(VoteServer.class);
    private static ZooKeeper zooKeeper;
    private static String root = "/ELECTION";

    private HashMap<String, Integer> votes = new HashMap<>();
    private int id;
    private int stateNumber;

    public VoteServer(int id, int stateNumber, int port, String zkHost) throws IOException {
        this.id = id;
        this.stateNumber = stateNumber;
        zooKeeper = new ZooKeeper(zkHost, 3000, this);
        // TODO: initialize gRPC port
        LOG.info("VoteServer ");
    }

    public void propose() throws KeeperException, InterruptedException {
        if (zooKeeper.exists(root, true) == null) {
            zooKeeper.create(root, new byte[] {}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        zooKeeper.create(root + "/ephemeral", String.valueOf(id).getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);
    }
    @Override
    public void process(WatchedEvent watchedEvent) {
        var nodeName = watchedEvent.getState().name();
        switch (watchedEvent.getType()) {
            case NodeChildrenChanged:
            case ChildWatchRemoved:
            case DataWatchRemoved:
            case NodeDataChanged:
            case NodeDeleted:
            case NodeCreated:
            case None:
        }
    }

    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
        org.apache.log4j.BasicConfigurator.configure();
        var voteServer = new VoteServer(500, 2, 2020, "127.0.0.1:2181");
        voteServer.propose();
        System.out.println("Hello");
        while (true) {}
    }
}
