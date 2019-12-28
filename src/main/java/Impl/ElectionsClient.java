package Impl;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import protos.CommitteeClientOuterClass;
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
        CommitteeClientOuterClass.Void v = CommitteeClientOuterClass.Void.newBuilder()
                .build();
        stub.start(v);
    }

    public void stop() {
        CommitteeClientOuterClass.Void v = CommitteeClientOuterClass.Void.newBuilder()
                .build();
        stub.stop(v);
    }

    public void broadcastVote(String voterName, String candidateName, String state) {
        ElectionsServerOuterClass.VoteRequest v = ElectionsServerOuterClass.VoteRequest.newBuilder()
                .setVoterName(voterName)
                .setCandidateName(candidateName)
                .setState(state)
                .build();
        stub.broadcastVote(v);
    }

    public void broadcastStart() {
        CommitteeClientOuterClass.Void v = CommitteeClientOuterClass.Void.newBuilder()
                .build();
        stub.broadcastStart(v);
    }

    public void broadcastStop() {
        CommitteeClientOuterClass.Void v = CommitteeClientOuterClass.Void.newBuilder()
                .build();
        stub.broadcastStop(v);
    }
}
