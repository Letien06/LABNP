package lab5rmi;
import lab5rmi.api.*;
import lab5rmi.server.NodeServer;
import java.nio.file.*;
import java.rmi.registry.*;
import java.util.*;
public final class IntegrationTest {
  static void check(boolean x,String m){if(!x)throw new AssertionError(m);}
  public static void main(String[] args)throws Exception{
    Path root=Files.createTempDirectory(Paths.get("out"),"lab5-rmi-"); Path da=root.resolve("a"), db=root.resolve("b");
    NodeServer.Config ca=new NodeServer.Config("A","127.0.0.1",2199,2299,da,true,"127.0.0.1",2199,"127.0.0.1",2200);
    NodeServer.Config cb=new NodeServer.Config("B","127.0.0.1",2200,2300,db,false,"127.0.0.1",2199,"127.0.0.1",2200);
    NodeServer a=NodeServer.start(ca); NodeServer b=NodeServer.start(cb);
    try {
      RecordService client=(RecordService)LocateRegistry.getRegistry("127.0.0.1",2200).lookup("RecordService");
      String req="req-1"; MutationResult c=client.create(req,"k1","one"); check(c.success(),"create"); check(client.create(req,"k1","one").record().equals(c.record()),"idempotency");
      for(int i=0;i<30&&client.get("k1")==null;i++) Thread.sleep(100); check(client.get("k1")!=null,"replicated create");
      MutationResult update=client.update("u1","k1","two"); check(update.success(),"update");
      for(int i=0;i<30&&!Objects.equals(client.get("k1"),update.record());i++) Thread.sleep(100);
      check(Objects.equals(client.get("k1"),update.record()),"update replicated exactly");
      MutationResult deletion=client.delete("d1","k1"); check(deletion.success(),"delete");
      for(int i=0;i<30;i++){ RecordData deleted=client.get("k1"); if(deleted!=null&&deleted.deleted()) break; Thread.sleep(100); }
      check(client.get("k1")!=null&&client.get("k1").deleted(),"delete replicated");
      check(Objects.equals(client.get("k1"),deletion.record()),"delete tombstone replicated exactly");
      b.stop(); MutationResult c2=((RecordService)LocateRegistry.getRegistry("127.0.0.1",2199).lookup("RecordService")).create("r2","k2","offline"); check(c2.success(),"offline primary write");
      b=NodeServer.start(cb); for(int i=0;i<40&&((RecordService)LocateRegistry.getRegistry("127.0.0.1",2200).lookup("RecordService")).get("k2")==null;i++) Thread.sleep(100);
      check(((RecordService)LocateRegistry.getRegistry("127.0.0.1",2200).lookup("RecordService")).get("k2")!=null,"startup catchup");
      System.out.println("IntegrationTest: PASS");
    } finally { try{b.stop();}catch(Exception ignored){} a.stop(); }
  }
}
