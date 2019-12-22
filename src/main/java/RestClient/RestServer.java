package RestClient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.HashMap;

@SpringBootApplication
public class RestServer {
    public static void run(int port) {
        HashMap<String, Object> props = new HashMap<>();
        props.put("server.port", port);
        new SpringApplicationBuilder()
                .sources(RestServer.class)
                .properties(props)
                .run();
    }
}

