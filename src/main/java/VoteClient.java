import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.zookeeper.server.quorum.Vote;
import protos.VoterGrpc;
import protos.VoterOuterClass;

public class VoteClient {
    private VoterGrpc.VoterBlockingStub stub;
    private ManagedChannel channel;

    public VoteClient(String host) {
        channel = ManagedChannelBuilder
                .forTarget(host)
                .usePlaintext()
                .build();
        stub = VoterGrpc.newBlockingStub(channel);
    }

    public void shutdown() {
        channel.shutdown();
    }

    public void vote(String voterName, String candidateName, String state) {
        VoterOuterClass.VoteRequest v = VoterOuterClass.VoteRequest.newBuilder()
                .setVoterName(voterName)
                .setCandidateName(candidateName)
                .setState(state)
                .build();
        stub.vote(v);
    }
}
