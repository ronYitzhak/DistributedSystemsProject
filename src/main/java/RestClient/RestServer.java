package RestClient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.HashMap;

@SpringBootApplication
public class RestServer {
    public static void main(String argv[]) {

//        SpringApplication.run(RestServer.class, argv);
        HashMap<String, Object> props = new HashMap<>();
        props.put("server.port", 9999);

        new SpringApplicationBuilder()
                .sources(RestServer.class)
                .properties(props)
                .run();
    }
}

