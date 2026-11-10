package lab5rmi.api;
import java.io.Serializable;
public record ReplicationAck(boolean applied, String message) implements Serializable {}
