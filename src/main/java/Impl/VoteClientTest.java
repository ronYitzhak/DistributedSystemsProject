package Impl;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import protos.AdminOuterClass;
import protos.ElectionsServerGrpc;
import protos.ElectionsServerOuterClass;

public class VoteClientTest {
    private ElectionsServerGrpc.ElectionsServerBlockingStub stub;
    private ManagedChannel channel;

    public VoteClientTest(String host) {
        channel = ManagedChannelBuilder
                .forTarget(host)
                .usePlaintext()
                .build();
        stub = ElectionsServerGrpc.newBlockingStub(channel);
    }

    public void shutdown() {
        channel.shutdown();
    }

    public void vote(String voterName, String candidateName, String state) {
        ElectionsServerOuterClass.VoteRequest v = ElectionsServerOuterClass.VoteRequest.newBuilder()
                .setVoterName(voterName)
                .setCandidateName(candidateName)
                .setState(state)
                .build();
        stub.broadcastVote(v);
    }

    public void start() {
        AdminOuterClass.Void v = AdminOuterClass.Void.newBuilder()
                .build();
        stub.broadcastStart(v);
    }

    public static void main(String[] args) {
//        org.apache.log4j.BasicConfigurator.configure();
        var galsIp = "192.168.43.247";
        VoteClientTest client = new VoteClientTest("127.0.0.1:55550");
        client.start();
        //client.vote("gal", "0", "California");
        client.shutdown();
    }

}
