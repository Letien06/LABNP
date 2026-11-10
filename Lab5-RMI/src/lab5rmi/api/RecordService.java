package lab5rmi.api;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
public interface RecordService extends Remote {
    NodeInfo info() throws RemoteException;
    RecordData get(String id) throws RemoteException;
    List<RecordData> list() throws RemoteException;
    MutationResult create(String requestId, String id, String value) throws RemoteException;
    MutationResult update(String requestId, String id, String value) throws RemoteException;
    MutationResult delete(String requestId, String id) throws RemoteException;
    ReplicationAck applyReplication(Mutation mutation) throws RemoteException;
    int pendingReplication() throws RemoteException;
}
