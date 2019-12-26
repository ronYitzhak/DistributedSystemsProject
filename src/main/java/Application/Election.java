package Application;

import Impl.ElectionsServerImpl;
import Impl.ZooKeeperService;
import RestClient.Controllers.VotesController;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;
import java.util.HashMap;
import java.util.Scanner;

/*
* TODOs:
* 1. impl committee client - start, stop, status
* 2. submit candidates + servers lists (REST or gRPC) by committee
* 3. implement REST - vote function
* 4 TESTING
* */

@SpringBootApplication
@ComponentScan("RestClient.Controllers")
public class Election {
    private static final Logger LOG = LoggerFactory.getLogger(ElectionsServerImpl.class);

    public static void main(String[] args) {
//        org.apache.log4j.BasicConfigurator.configure();
        //TODO: get parameter for builder from user\commandline\somehow

        int restPort = 9999;
        HashMap<String, Object> props = new HashMap<>();
        props.put("server.port", restPort);
        new SpringApplicationBuilder()
                .sources(Election.class)
                .properties(props)
                .run();

        LOG.info("rest initialized on port " + restPort);

        Scanner input = new Scanner(System.in);
        System.out.print("gRPC self ip: ");
        String host = input.nextLine();
        System.out.print("gRPC port: ");
        int grpcPort = input.nextInt();
        String zkHost = "127.0.0.1:2181";

        try {
            ElectionsServerImpl electionsServer = new ElectionsServerImpl(host+":"+grpcPort, "California", grpcPort);
            LOG.info("VoteImpl initialized on host: " + host + " and port: " + grpcPort);
            ZooKeeperService.init(zkHost, electionsServer);
            LOG.info("ZooKeeperService initialized on host: " + zkHost);
            electionsServer.propose();
            LOG.info("VoteImpl proposed on host: " + host + " and port: " + grpcPort);
            System.out.println("Hello");
            while (true) {}
        } catch (IOException e) {
            LOG.info("IOException - should not get here");
            e.printStackTrace();
        } catch (InterruptedException e) {
            LOG.info("InterruptedException - should not get here");
            e.printStackTrace();
        } catch (KeeperException e) {
            LOG.info("KeeperException - should not get here");
            e.printStackTrace();
        }
    }
}
