package lab5rmi.server;

import lab5rmi.api.*;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class NodeServer {
    public record Config(String nodeId, String host, int port, int servicePort, Path dataDir, boolean primary, String primaryHost, int primaryPort, String replicaHost, int replicaPort) {}
    private final Config config;
    private final DataStore store;
    private final ServiceImpl service;
    private Registry registry;
    private ScheduledExecutorService retry;
    private NodeServer(Config c) throws Exception { config=c; store=new DataStore(c.dataDir()); service=new ServiceImpl(); }
    public static NodeServer start(Config c) throws Exception {
        System.setProperty("java.rmi.server.hostname", c.host());
        NodeServer n=new NodeServer(c); n.registry=LocateRegistry.createRegistry(c.port());
        n.registry.rebind("RecordService", n.service);
        n.retry=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"replication-"+c.nodeId()); t.setDaemon(true); return t;});
        n.retry.scheduleWithFixedDelay(n::replicatePending,100,500,TimeUnit.MILLISECONDS);
        System.out.printf("Node %s listening registry=%s:%d service=%d (%s), data=%s%n",c.nodeId(),c.host(),c.port(),c.servicePort(),c.primary()?"PRIMARY":"REPLICA",c.dataDir());
        return n;
    }
    public void stop() {
        if(retry!=null) retry.shutdownNow();
        try { UnicastRemoteObject.unexportObject(service,true); } catch(Exception ignored){}
        try { if (registry != null) UnicastRemoteObject.unexportObject(registry,true); } catch(Exception ignored){}
    }
    private RecordService lookup(String host,int port) throws Exception { return (RecordService)LocateRegistry.getRegistry(host,port).lookup("RecordService"); }
    private void replicatePending() {
        if(!config.primary() || config.replicaHost()==null || config.replicaHost().isBlank()) return;
        try {
            RecordService replica=lookup(config.replicaHost(),config.replicaPort());
            for(Mutation m: store.pending()) {
                try { replica.applyReplication(m); store.removePending(m.mutationId()); }
                catch(Exception e) { break; }
            }
        } catch(Exception ignored) { }
    }
    private final class ServiceImpl extends UnicastRemoteObject implements RecordService {
        ServiceImpl() throws RemoteException { super(config.servicePort()); }
        private NodeInfo nodeInfo(){ return new NodeInfo(config.nodeId(),config.primary(),config.host(),config.port()); }
        @Override public NodeInfo info(){ return nodeInfo(); }
        @Override public synchronized RecordData get(String id){ return store.get(id); }
        @Override public synchronized List<RecordData> list(){ return store.list(); }
        @Override public int pendingReplication(){ return store.pendingCount(); }
        private RecordService primary() throws RemoteException {
            try { return lookup(config.primaryHost(),config.primaryPort()); }
            catch(Exception e){ throw new RemoteException("Primary unavailable at "+config.primaryHost()+":"+config.primaryPort(),e); }
        }
        @Override public MutationResult create(String req,String id,String value) throws RemoteException {
            if(!config.primary()) return primary().create(req,id,value);
            return write(req,id,value,"CREATE");
        }
        @Override public MutationResult update(String req,String id,String value) throws RemoteException {
            if(!config.primary()) return primary().update(req,id,value);
            return write(req,id,value,"UPDATE");
        }
        @Override public MutationResult delete(String req,String id) throws RemoteException {
            if(!config.primary()) return primary().delete(req,id);
            return write(req,id,null,"DELETE");
        }
        private synchronized MutationResult write(String req,String id,String value,String op) throws RemoteException {
            if(req==null||req.isBlank()) req=UUID.randomUUID().toString();
            MutationResult previous=store.request(req); if(previous!=null) return previous;
            if(id==null||id.isBlank()) return remember(MutationResult.fail(req,"id is required"));
            RecordData existing=store.get(id);
            if(op.equals("CREATE") && existing!=null && !existing.deleted()) return remember(MutationResult.fail(req,"record already exists"));
            if(op.equals("UPDATE") && (existing==null || existing.deleted())) return remember(MutationResult.fail(req,"record not found"));
            if(op.equals("DELETE") && (existing==null || existing.deleted())) return remember(MutationResult.fail(req,"record not found"));
            long v=store.nextVersion(); Mutation m=switch(op){case "CREATE"->Mutation.create(req,id,value,v);case "UPDATE"->Mutation.update(req,id,value,v);default->Mutation.delete(req,id,v);};
            RecordData r=new RecordData(id,value,v,op.equals("DELETE"));
            try { MutationResult result=MutationResult.ok(req,op.toLowerCase(Locale.ROOT)+" applied",r); store.commit(r,m,result); return result; }
            catch(Exception e){ throw new RemoteException("durable write failed",e); }
        }
        private MutationResult remember(MutationResult r) throws RemoteException { try { store.saveRequest(r); return r; } catch(Exception e){ throw new RemoteException("durable write failed",e); } }
        @Override public ReplicationAck applyReplication(Mutation m) throws RemoteException {
            if(config.primary()) return new ReplicationAck(false,"primary does not accept replication");
            try { return store.apply(m); } catch(Exception e){ throw new RemoteException("replication failed",e); }
        }
    }
    public static void main(String[] args) throws Exception {
        Map<String,String> a=parse(args); String id=req(a,"id"); String host=a.getOrDefault("host","127.0.0.1"); int port=Integer.parseInt(a.getOrDefault("port", "1099"));
        Path data=Paths.get(a.getOrDefault("data","data-"+id)); boolean primary=Boolean.parseBoolean(a.getOrDefault("primary", "false"));
        Config c=new Config(id,host,port,Integer.parseInt(a.getOrDefault("service-port", String.valueOf(port+1))),data,primary,a.getOrDefault("primary-host","127.0.0.1"),Integer.parseInt(a.getOrDefault("primary-port", "1099")),a.getOrDefault("replica-host","127.0.0.1"),Integer.parseInt(a.getOrDefault("replica-port", "1100")));
        NodeServer n=start(c); Runtime.getRuntime().addShutdownHook(new Thread(n::stop)); new CountDownLatch(1).await();
    }
    private static String req(Map<String,String> m,String k){ if(!m.containsKey(k)) throw new IllegalArgumentException("Missing --"+k); return m.get(k); }
    private static Map<String,String> parse(String[] args){ Map<String,String> m=new HashMap<>(); for(int i=0;i<args.length;i++){String s=args[i]; if(s.startsWith("--")){String k=s.substring(2); String v=(i+1<args.length&&!args[i+1].startsWith("--"))?args[++i]:"true"; m.put(k,v);}} return m; }
}
