package Application.RestClient.Controllers;

import Impl.ElectionServerFactory;
import Application.RestClient.Models.State;
import Impl.ElectionsServerImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import protos.ElectionsServerOuterClass;

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
    private static HashMap<String, HashSet<String>> stateToVoters = new HashMap<>(); // state -> set of voters.  TODO: retrieve
    private ElectionsServerImpl server = ElectionServerFactory.instance();

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

    @PostMapping("/states/{stateName}/voters/{voterName}/vote")
    ResponseEntity<Void> vote(@PathVariable String stateName, @PathVariable String voterName, @RequestBody Map<String, Object> payload) {

        if (!stateToVoters.containsKey(stateName))
            throw new StateNotFoundException(stateName);
        var voters = stateToVoters.get(stateName);
        if (!voters.contains(voterName))
            throw new VoterNotFoundException(voterName);
        if (!payload.containsKey("candidate")) {
            throw new VoterNotFoundException(voterName); // TODO: impl CandidateNotFoundException(); assume candidate is valid if exists
        }
        var candidate = payload.get("candidate").toString();
        server.sendVote(voterName,candidate,stateName);
        return new ResponseEntity<Void>(HttpStatus.OK);
    }
}
