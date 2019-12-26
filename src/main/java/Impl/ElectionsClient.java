package Impl;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.zookeeper.server.quorum.Vote;
import protos.AdminOuterClass;
import protos.ElectionsServerGrpc;
import protos.ElectionsServerOuterClass;

public class ElectionsClient {
    private ElectionsServerGrpc.ElectionsServerBlockingStub stub;
    private ManagedChannel channel;

    public ElectionsClient(String host) {
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
        stub.vote(v);
    }

    public void start() {
        AdminOuterClass.Void v = AdminOuterClass.Void.newBuilder()
                .build();
        stub.start(v);
    }

    public void stop() {
        AdminOuterClass.Void v = AdminOuterClass.Void.newBuilder()
                .build();
        stub.stop(v);
    }
}
