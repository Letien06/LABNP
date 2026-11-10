package lab5rmi.server;

import lab5rmi.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Durable, atomic file-backed state for one node. */
final class DataStore {
    private static final String FILE = "state.bin";
    private final Path dir;
    private final Path file;
    private State state;
    static final class State implements Serializable {
        Map<String,RecordData> records = new LinkedHashMap<>();
        Map<String,MutationResult> requests = new HashMap<>();
        Map<String,Mutation> outbox = new LinkedHashMap<>();
        Set<String> applied = new HashSet<>();
        long nextVersion = 1;
    }
    DataStore(Path dir) throws IOException {
        this.dir = dir; this.file = dir.resolve(FILE);
        Files.createDirectories(dir); load();
    }
    private synchronized void load() throws IOException {
        if (!Files.exists(file)) { state = new State(); return; }
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
            state = (State) in.readObject();
        } catch (ClassNotFoundException | EOFException e) { throw new IOException("Cannot read " + file, e); }
    }
    private synchronized void persist() throws IOException {
        Path tmp = dir.resolve(FILE + ".tmp");
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) { out.writeObject(state); out.flush(); }
        try { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
    }
    synchronized RecordData get(String id) { return state.records.get(id); }
    synchronized List<RecordData> list() { return new ArrayList<>(state.records.values().stream().filter(r -> !r.deleted()).toList()); }
    synchronized MutationResult request(String requestId) { return state.requests.get(requestId); }
    synchronized long nextVersion() { return state.nextVersion++; }
    synchronized void saveRequest(MutationResult result) throws IOException { state.requests.put(result.requestId(), result); persist(); }
    synchronized void putRecord(RecordData r, Mutation m) throws IOException { state.records.put(r.id(), r); state.outbox.put(m.mutationId(),m); persist(); }
    synchronized void commit(RecordData r, Mutation m, MutationResult result) throws IOException { state.records.put(r.id(), r); state.outbox.put(m.mutationId(), m); state.requests.put(result.requestId(), result); persist(); }
    synchronized List<Mutation> pending() { return new ArrayList<>(state.outbox.values()); }
    synchronized void removePending(String id) throws IOException { if (state.outbox.remove(id)!=null) persist(); }
    synchronized ReplicationAck apply(Mutation m) throws IOException {
        if (state.applied.contains(m.mutationId())) return new ReplicationAck(false,"already applied");
        RecordData old = state.records.get(m.recordId());
        if (old != null && old.version() >= m.version()) { state.applied.add(m.mutationId()); persist(); return new ReplicationAck(false,"older version ignored"); }
        RecordData r = switch(m.operation()) {
            case "CREATE", "UPDATE" -> new RecordData(m.recordId(), m.value(), m.version(), false);
            case "DELETE" -> new RecordData(m.recordId(), null, m.version(), true);
            default -> throw new IllegalArgumentException("Unknown operation " + m.operation());
        };
        state.records.put(m.recordId(),r); state.applied.add(m.mutationId()); state.nextVersion=Math.max(state.nextVersion,m.version()+1); persist();
        return new ReplicationAck(true,"applied");
    }
    synchronized int pendingCount() { return state.outbox.size(); }
}
