package RestClient.Controllers;

import RestClient.Models.State;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "voter not found")
class VoterNotFoundException extends RuntimeException {
    VoterNotFoundException(String name) {
        super("Could not find voter " + name);
    }
}

@RestController
public class VotesController {
    private static HashMap<String, HashSet<String>> stateToVoters = new HashMap<>(); // name -> state.  TODO: retrieve

    VotesController() {
        // TEST Data
        var voters = new HashSet<String>();
        voters.add("v1");
        voters.add("v2");
        voters.add("v3");
        stateToVoters.put("california", voters);
    }

    @GetMapping("/states")
    Set<State> all() {
        return new HashSet<>();
    }

    @GetMapping("/states/{stateName}/voters/{voterName}/vote")
    ResponseEntity<Void> vote(@PathVariable String stateName, @PathVariable String voterName) {
        if (!stateToVoters.containsKey(stateName))
            throw new StateNotFoundException(stateName);
        var voters = stateToVoters.get(stateName);
        if (!voters.contains(voterName))
            throw new VoterNotFoundException(voterName);
        // do voting

        return new ResponseEntity<Void>(HttpStatus.OK);
    }
}
