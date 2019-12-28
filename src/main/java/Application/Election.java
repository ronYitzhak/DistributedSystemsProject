package Application;

import Impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;

import java.util.HashMap;

/*
* TODOs:
* 1. impl committeeServer - start(GAL), stop(GAL), status (RON)
*       What happens with "start on start?" nothing,
*       "global master fall on start?" start adminRPC will call next global master.
* 1.2 handle error flows (gRPC falls in the middle and such)
* 2. submit candidates + servers lists (REST or gRPC) by committee (RON if Dolev answers)
* 3. implement REST - vote function - to fix with server send broadcast (GAL)
* 4. TESTING - docker (RON)
* 5. change admin name to committeeServer
* */

@SpringBootApplication
@ComponentScan("Application.RestClient.Controllers")
public class Election {
    private static final Logger LOG = LoggerFactory.getLogger(ElectionsServerImpl.class);

    public static void main(String[] args) {
//        org.apache.log4j.BasicConfigurator.configure();
        //TODO: get parameter for builder from user\commandline\somehow
        String host = "127.0.0.1";
        int grpcPort = 55500;
        String state = "california";

//        var serversPerState = CustomCSVParser.getServersPerState();
//        var servers = serversPerState.entrySet().stream()
//                .flatMap(e -> e.getValue().stream().map(p -> new Triplet(e.getKey(), p.getValue0(), p.getValue1())))
//                .map(t -> {
//                    String s = (String)t.getValue0();
//                    String h = (String)t.getValue1();
//                    int p = (int)t.getValue2();
//                    var res = new ElectionsServerImpl();
//                    res.init(h + ":" + p, s, p);
//                    return res;
//                })
//                .collect(Collectors.toList());

        var electionsServer = ElectionServerFactory.instance();
        String zkHost = "127.0.0.1:2181";
        ZooKeeperService.init(zkHost, electionsServer);

        electionsServer.init(host + ":" + grpcPort, state, grpcPort);
        System.out.println("Hello");

        int restPort = 9999;
        HashMap<String, Object> props = new HashMap<>();
        props.put("server.port", restPort);
        new SpringApplicationBuilder()
                .sources(Election.class)
                .properties(props)
                .run();

        LOG.info("rest initialized on port " + restPort);

        var electionsClient = new ElectionsClient(host + ":" + grpcPort);
        electionsClient.broadcastStart();

        var committeeClient = new CommitteeClient();
        var status = committeeClient.getStatus("california");
        LOG.info(status.toString());
//        var globalStatus = committeeClient.getGlobalStatus();
//        LOG.info(globalStatus.toString());

/*
        Scanner input = new Scanner(System.in);
        System.out.print("gRPC self ip: ");
        String host = input.nextLine();
        System.out.print("gRPC port: ");
        int grpcPort = input.nextInt();
        String zkHost = "127.0.0.1:2181";

        try {
            ElectionsServerImpl electionsServer = new ElectionsServerImpl(host+":"+grpcPort, "California", grpcPort);
            LOG.info("ElectionsServerImpl initialized on host: " + host + " and port: " + grpcPort);
            ZooKeeperService.init(zkHost, electionsServer);
            LOG.info("ZooKeeperService initialized on host: " + zkHost);
            electionsServer.propose();
            LOG.info("ElectionsServerImpl proposed on host: " + host + " and port: " + grpcPort);
            System.out.println("Hello");
 */
        while (true) {
        }
    }
}
