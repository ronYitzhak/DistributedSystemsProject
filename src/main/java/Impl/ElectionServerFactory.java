package Impl;

public class ElectionServerFactory {
    private static ElectionsServerImpl electionsServer;

    public static ElectionsServerImpl initElectionServer(String selfAddress, String state, int grpcPort){
        electionsServer = new ElectionsServerImpl(selfAddress, state, grpcPort);
        return electionsServer;
    }

    public static ElectionsServerImpl instance() {
        return electionsServer;
    }
}
