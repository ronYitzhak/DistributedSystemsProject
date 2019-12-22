import io.grpc.stub.StreamObserver;
import protos.AdminGrpc;
import protos.AdminOuterClass;

// TODO: to implement
public class AdminImpl extends AdminGrpc.AdminImplBase {
    @Override
    public void start(AdminOuterClass.Void request, StreamObserver<AdminOuterClass.Void> responseObserver) {
        super.start(request, responseObserver);
    }

    @Override
    public void stop(AdminOuterClass.Void request, StreamObserver<AdminOuterClass.Void> responseObserver) {
        super.stop(request, responseObserver);
    }

    @Override
    public void getStatus(AdminOuterClass.StateStatusRequest request, StreamObserver<AdminOuterClass.StateStatusResponse> responseObserver) {
        super.getStatus(request, responseObserver);
    }

    @Override
    public void getGlobalStatus(AdminOuterClass.Void request, StreamObserver<AdminOuterClass.GlobalStatusResponse> responseObserver) {
        super.getGlobalStatus(request, responseObserver);
    }
}
