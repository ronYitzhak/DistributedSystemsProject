package Impl;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
        ElectionsServerOuterClass.Void v = ElectionsServerOuterClass.Void.newBuilder()
                .build();
        stub.start(v);
    }

    public void stop() {
        ElectionsServerOuterClass.Void v = ElectionsServerOuterClass.Void.newBuilder()
                .build();
        stub.stop(v);
    }

    public ElectionsServerOuterClass.VoteStatus.Status broadcastVote(String voterName, String candidateName, String state) {
        ElectionsServerOuterClass.VoteRequest v = ElectionsServerOuterClass.VoteRequest.newBuilder()
                .setVoterName(voterName)
                .setCandidateName(candidateName)
                .setState(state)
                .build();
        ElectionsServerOuterClass.VoteStatus rep = stub.broadcastVote(v);
        return rep.getStatus();
    }

    public void broadcastStart() {
        ElectionsServerOuterClass.Void v = ElectionsServerOuterClass.Void.newBuilder()
                .build();
        stub.broadcastStart(v);
    }

    public void broadcastStop() {
        ElectionsServerOuterClass.Void v = ElectionsServerOuterClass.Void.newBuilder()
                .build();
        stub.broadcastStop(v);
    }

    public ElectionsServerOuterClass.StateStatusResponse electionsGetStatus(String state) {
        var request = ElectionsServerOuterClass.StateStatusRequest.newBuilder()
                .setState(state)
                .build();
        return stub.electionsGetStatus(request);
    }
}
