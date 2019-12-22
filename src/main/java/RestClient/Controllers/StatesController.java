package RestClient.Controllers;

import RestClient.Models.State;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "state not found")
class StateNotFoundException extends RuntimeException {
    StateNotFoundException(String name) {
        super("Could not find state " + name);
    }
}

@RestController
public class StatesController {
    private static HashMap<String, State> states = new HashMap<>(); // name -> state.  TODO: retrieve

    StatesController() {}

    @GetMapping("/states")
    Set<State> all() {
        return new HashSet<>(states.values());
    }

    @GetMapping("/states/{name}")
    Integer one(@PathVariable String name) {
        if (states.containsKey(name))
            //return states.get(name);
            return 800;
        else throw new StateNotFoundException(name);
    }
}
