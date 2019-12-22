import RestClient.RestServer;
import org.apache.zookeeper.KeeperException;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.Scanner;

@SpringBootApplication
public class Election {
    // TODO: init all servers, zk, rest, etc.
    // main function here

    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
//        org.apache.log4j.BasicConfigurator.configure();
        //TODO: get parameter for builder from user\commandline\somehow
        Scanner input = new Scanner(System.in);
        System.out.print("gRPC self ip: ");
        String host = input.nextLine();
        System.out.print("gRPC port: ");
        int grpcPort = input.nextInt();
        String zkHost = "127.0.0.1:2181";

        var voteServer = new VoteImpl(host+":"+grpcPort, "California", grpcPort); // zkHost: "127.0.0.1:2181"
        voteServer.propose();
        ZooKeeperService.init(zkHost, voteServer);

        int restPort = 9999;
        RestServer.run(restPort);

        System.out.println("Hello");

        while (true) {}
    }
}
