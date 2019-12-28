package Impl;

import org.apache.zookeeper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ZooKeeperService {
    private static final Logger LOG = LoggerFactory.getLogger(ElectionsServerImpl.class);
    private static final int timeout = 3000000; // todo: session timeout?
    private static ZooKeeper zooKeeper;

    public static void init(String zkHost, Watcher watcher) {
        try {
            zooKeeper = new ZooKeeper(zkHost, timeout, watcher);
        } catch (IOException e) {
            LOG.error("Could not initialize zoo keeper. host: " + zkHost);
            e.printStackTrace();
        }
    }

    public static String createSeqNode(String path, CreateMode createMode, byte[] data) { // should have /
        try {
            return zooKeeper.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, createMode);
        } catch (KeeperException e) {
            LOG.error("KeeperException - should not get here");
            e.printStackTrace();
            return "";
        } catch (InterruptedException e) {
            LOG.error("InterruptedException - should not get here");
            e.printStackTrace();
            return "";
        }
    }

    public static String createNodeIfNotExists(String path, CreateMode createMode, byte[] data) {
        try {
            if (zooKeeper.exists(path, false) != null) return path;
            return zooKeeper.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, createMode);
        } catch (KeeperException e) {
            LOG.error("KeeperException - should not get here");
            e.printStackTrace();
            return "";
        } catch (InterruptedException e) {
            LOG.error("InterruptedException - should not get here");
            e.printStackTrace();
            return "";
        }
    }

    public static Boolean exists(String path, Boolean watch) {
        try {
            return zooKeeper.exists(path, watch) != null;
        } catch (KeeperException e) {
            LOG.error("KeeperException - should not get here");
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            LOG.error("InterruptedException - should not get here");
            e.printStackTrace();
            return false;
        }
    }

    public static void setWatcherOnNode(String path) {
        try {
            zooKeeper.exists(path, true);
        } catch (KeeperException e) {
            LOG.error("KeeperException - should not get here");
            e.printStackTrace();
        } catch (InterruptedException e) {
            LOG.error("InterruptedException - should not get here");
            e.printStackTrace();
        }
    }

    public static void setWatcherOnChildren(String path) {
        try {
            zooKeeper.getChildren(path,true);
        } catch (KeeperException e) {
            LOG.error("KeeperException - should not get here");
            e.printStackTrace();
        } catch (InterruptedException e) {
            LOG.error("InterruptedException - should not get here");
            e.printStackTrace();
        }
    }

    public static String getMasterByState(String state, Boolean watch) {
        List<String> nodes;
        var statePath = "/Application.Election/" + state;
        try {
            nodes = zooKeeper.getChildren(statePath + "/LiveNodes", watch);
            Collections.sort(nodes);
            return statePath + "/LiveNodes/" + nodes.get(0); // master path
        } catch (KeeperException e) {
            e.printStackTrace();
            LOG.error("KeeperException - should not get here");
            return "";
        } catch (InterruptedException e) {
            e.printStackTrace();
            LOG.error("InterruptedException - should not get here");
            return "";
        }
        //define watchers
//        try {
//            if(zooKeeper.exists(masterPath, true) == null) getMasterByState(state);
//        } catch (KeeperException e) {
//            e.printStackTrace();
//            LOG.error("KeeperException - should not get here");
//            return "";
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//            LOG.error("InterruptedException - should not get here");
//            return "";
//        }
    }

    public static String getGlobalMaster(Boolean watch) {
        List<String> nodes;
        var globalPath = "/Application.Election/Global";
        try {
            nodes = zooKeeper.getChildren(globalPath + "/LiveNodes", watch);
            Collections.sort(nodes);
            return globalPath + "/LiveNodes/" + nodes.get(0); // master path
        } catch (KeeperException e) {
            e.printStackTrace();
            LOG.error("KeeperException - should not get here");
            return "";
        } catch (InterruptedException e) {
            e.printStackTrace();
            LOG.error("InterruptedException - should not get here");
            return "";
        }
    }

    public static ArrayList<String> getChildrenData(String path, Boolean watch) { // path of live nodes
        try {
            var nodes = zooKeeper.getChildren(path, watch);
            ArrayList<String> result = new ArrayList<>();
            nodes.forEach(node -> {
                try {
                    var host = new String(zooKeeper.getData(path + "/" + node,false,null));
                    result.add(host);
                } catch (KeeperException e) {
                    LOG.error("KeeperException - should not get here");
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    LOG.error("InterruptedException - should not get here");
                    e.printStackTrace();
                }
            });
            return result;
        } catch (KeeperException e) {
            LOG.error("KeeperException - should not get here");
            e.printStackTrace();
            return null;
        } catch (InterruptedException e) {
            LOG.error("InterruptedException - should not get here");
            e.printStackTrace();
            return null;
        }
    }

    public static String getData(String path, Boolean watch) {
        try {
            return new String(zooKeeper.getData(path, watch, null));
        } catch (KeeperException e) {
            LOG.error("KeeperException - should not get here");
            e.printStackTrace();
            return null;
        } catch (InterruptedException e) {
            LOG.error("InterruptedException - should not get here");
            e.printStackTrace();
            return null;
        }
    }

    public static Integer getChildrenCount(String path, Boolean watch) { // path of live nodes
        try {
            var nodes = zooKeeper.getChildren(path, watch);
            return nodes.size();
        } catch (KeeperException e) {
            LOG.error("KeeperException - should not get here");
            e.printStackTrace();
            return 0;
        } catch (InterruptedException e) {
            LOG.error("InterruptedException - should not get here");
            e.printStackTrace();
            return 0;
        }
    }

    public static void incDataByOne(String path) {
        try {
            byte[] data = zooKeeper.getData(path, false, null);
            data[0]++;
            zooKeeper.setData(path, data, -1);
        } catch (KeeperException e) {
            e.printStackTrace();
            LOG.error("KeeperException - should not get here");
        } catch (InterruptedException e) {
            e.printStackTrace();
            LOG.error("InterruptedException - should not get here");
        }
    }
}