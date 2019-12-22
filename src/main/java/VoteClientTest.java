import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.zookeeper.server.quorum.Vote;
import protos.VoterGrpc;
import protos.VoterOuterClass;

import java.util.Iterator;

public class VoteClientTest {
    private VoterGrpc.VoterBlockingStub stub;
    private ManagedChannel channel;

    public VoteClientTest(String host, int port) {
        channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        stub = VoterGrpc.newBlockingStub(channel);
    }

    public void shutdown() {
        channel.shutdown();
    }

    public void vote(String voterName, int candidateId, int state) {
        VoterOuterClass.VoteRequest v = VoterOuterClass.VoteRequest.newBuilder()
                .setVoterName(voterName)
                .setCandidateId(candidateId)
                .setState(state)
                .build();
        stub.vote(v);
    }

    public static void main(String[] args) throws Exception {
        org.apache.log4j.BasicConfigurator.configure();
        var galsIp = "192.168.43.247";
        VoteClientTest client = new VoteClientTest(galsIp, 50051);
        client.vote("gal", 0, 1);
        client.shutdown();
    }

}
