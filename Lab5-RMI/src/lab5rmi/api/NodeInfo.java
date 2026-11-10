package lab5rmi.api;
import java.io.Serializable;
public record NodeInfo(String nodeId, boolean primary, String host, int port) implements Serializable {}
