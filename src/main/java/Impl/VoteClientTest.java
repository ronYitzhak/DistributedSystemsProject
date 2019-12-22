package Impl;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import protos.VoterGrpc;
import protos.VoterOuterClass;

public class VoteClientTest {
    private VoterGrpc.VoterBlockingStub stub;
    private ManagedChannel channel;

    public VoteClientTest(String host) {
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
        stub.masterVote(v);
    }

    public static void main(String[] args) {
//        org.apache.log4j.BasicConfigurator.configure();
        var galsIp = "192.168.43.247";
        VoteClientTest client = new VoteClientTest("127.0.0.1:55555");
        client.vote("gal", "0", "California");
        client.shutdown();
    }

}
