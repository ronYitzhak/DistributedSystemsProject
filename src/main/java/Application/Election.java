package Application;

import Impl.VoteImpl;
import Impl.ZooKeeperService;
import org.apache.zookeeper.KeeperException;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Scanner;

@SpringBootApplication
public class Election {
    // TODO: init all servers, zk, rest, etc.
    // main function here

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

        Scanner input = new Scanner(System.in);
        System.out.print("gRPC self ip: ");
        String host = input.nextLine();
        System.out.print("gRPC port: ");
        int grpcPort = input.nextInt();
        String zkHost = "127.0.0.1:2181";

        VoteImpl voteServer = null; // zkHost: "127.0.0.1:2181"
        try {
            voteServer = new VoteImpl(host+":"+grpcPort, "California", grpcPort);
            ZooKeeperService.init(zkHost, voteServer);
            voteServer.propose();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }

        System.out.println("Hello");

        while (true) {}
    }
}
