package lab5rmi.api;
import java.io.Serializable;
public record MutationResult(boolean success, String message, RecordData record, String requestId) implements Serializable {
    public static MutationResult ok(String id, String message, RecordData record) { return new MutationResult(true,message,record,id); }
    public static MutationResult fail(String id, String message) { return new MutationResult(false,message,null,id); }
}
