package Application;

import Impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

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
    private static final Logger LOG = LoggerFactory.getLogger(Election.class);

    public static void main(String[] args) {
        //args list: self-address, state, zkhost, grpcPort, restport
        ch.qos.logback.classic.Logger tmpLogger = (ch.qos.logback.classic.Logger)LOG;
        tmpLogger.setLevel(ch.qos.logback.classic.Level.toLevel("info"));

        List<String> listArgs = new ArrayList<String>();
        Collections.addAll(listArgs, args);
        switch (listArgs.size()) {
            case 0:
                listArgs.add("127.0.0.1");
            case 1:
                listArgs.add("california");
            case 2:
                listArgs.add("127.0.0.1:2181");
            case 3:
                listArgs.add("55555");
            case 4:
                listArgs.add("9999");
        }

        String host = listArgs.get(0);
        String state = listArgs.get(1);
        String zkHost = listArgs.get(2);
        int grpcPort = Integer.parseInt(listArgs.get(3));
        int restPort = Integer.parseInt(listArgs.get(4));

        HashMap<String, Object> props = new HashMap<>();
        props.put("server.port", restPort);
        new SpringApplicationBuilder()
                .sources(Election.class)
                .properties(props)
                .run();

        var electionsServer = ElectionServerFactory.instance();

        ZooKeeperService.init(zkHost, electionsServer);
        electionsServer.init(host + ":" + grpcPort, state, grpcPort);
        LOG.info("grpc initialized on port " + grpcPort);
        LOG.info("rest initialized on port " + restPort);


        while (true) {
        }
    }
}
