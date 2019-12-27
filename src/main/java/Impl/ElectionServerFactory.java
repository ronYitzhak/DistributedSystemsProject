package Impl;

public class ElectionServerFactory {
    private static ElectionsServerImpl electionsServer = new ElectionsServerImpl();

    public static ElectionsServerImpl instance() {
        return electionsServer;
    }
}
