package lab5rmi.api;
import java.io.Serializable;
public record RecordData(String id, String value, long version, boolean deleted) implements Serializable {}
