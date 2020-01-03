package Application.RestClient.Controllers;

import Impl.ElectionServerFactory;
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

@ResponseStatus(code = HttpStatus.FORBIDDEN, reason = "Election not started")
class ElectionNotStartedException extends RuntimeException {
    ElectionNotStartedException() {
        super("Election not started");
    }
}

@RestController
public class VotesController {
    private ElectionsServerImpl server = ElectionServerFactory.instance();

    VotesController() {}

    @PostMapping("/states/{stateName}/voters/{voterName}/vote")
    ResponseEntity<Void> vote(@PathVariable String stateName, @PathVariable String voterName, @RequestBody Map<String, Object> payload) {

        if (!server.getStateToVoters().containsKey(stateName))
            throw new StateNotFoundException(stateName);
        var voters = server.getStateToVoters().get(stateName);
        if (!voters.contains(voterName))
            throw new VoterNotFoundException(voterName);
        var candidate = payload.get("candidate").toString();
        ElectionsServerOuterClass.VoteStatus.Status status = server.sendVote(voterName, candidate, stateName);
        if(status == ElectionsServerOuterClass.VoteStatus.Status.ELECTION_NOT_STARTED)
            throw new ElectionNotStartedException();
        return new ResponseEntity<Void>(HttpStatus.OK);
    }
}
