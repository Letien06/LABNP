package lab5rmi.api;
import java.io.Serializable;
public record Mutation(String mutationId, String operation, String recordId, String value, long version) implements Serializable {
    public static Mutation create(String id, String recordId, String value, long version) { return new Mutation(id,"CREATE",recordId,value,version); }
    public static Mutation update(String id, String recordId, String value, long version) { return new Mutation(id,"UPDATE",recordId,value,version); }
    public static Mutation delete(String id, String recordId, long version) { return new Mutation(id,"DELETE",recordId,null,version); }
}
