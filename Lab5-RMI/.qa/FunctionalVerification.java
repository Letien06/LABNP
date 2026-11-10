package lab5rmi.client;

import lab5rmi.api.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.nio.file.*;
import java.rmi.registry.LocateRegistry;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Real socket/RMI feature checks. Each node runs in its own JVM and data directory. */
public final class FunctionalVerification {
    static final Path QA=Path.of(".qa");
    static final int AP=32199, AS=32299, BP=32200, BS=32300;
    static Path data;
    static Process a,b;
    static int checks;
    interface Check { boolean test() throws Exception; }
    static void check(boolean ok,String description) {
        if(!ok) throw new AssertionError(description);
        System.out.println("PASS "+(++checks)+": "+description);
    }
    static void await(Check condition,String description) throws Exception {
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        do { if(condition.test()){check(true,description); return;} Thread.sleep(100); } while(System.nanoTime()<limit);
        check(false,description);
    }
    static RecordService lookup(int port) throws Exception {
        return (RecordService)LocateRegistry.getRegistry("127.0.0.1",port).lookup("RecordService");
    }
    static Process start(boolean primary) throws Exception {
        String node=primary?"A":"B";
        String executable=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        var command=new ArrayList<>(java.util.List.of(executable,"-cp",System.getProperty("java.class.path"),"lab5rmi.server.NodeServer",
            "--id",node,"--host","127.0.0.1","--port",String.valueOf(primary?AP:BP),"--service-port",String.valueOf(primary?AS:BS),
            "--data",data.resolve(node).toString(),"--primary",String.valueOf(primary),"--primary-host","127.0.0.1","--primary-port",String.valueOf(AP),"--replica-host","127.0.0.1","--replica-port",String.valueOf(BP)));
        Process process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(QA.resolve("features-node-"+node+".log").toFile())).start();
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<limit){
            if(!process.isAlive())throw new AssertionError("Node "+node+" exited, see node log");
            try {if(lookup(primary?AP:BP).info().nodeId().equals(node))return process;}catch(Exception ignored){}
            Thread.sleep(100);
        }
        process.destroyForcibly(); throw new AssertionError("Node "+node+" failed to start");
    }
    static void stop(Process process) throws Exception {
        if(process!=null){process.destroyForcibly(); if(!process.waitFor(10,TimeUnit.SECONDS))throw new AssertionError("Node did not stop"); Thread.sleep(150);}
    }
    static void equalRecord(RecordService primary,RecordService replica,String id,String label) throws Exception {
        await(()->Objects.equals(primary.get(id),replica.get(id)),label);
    }
    static void layout(Container c){c.doLayout(); for(Component component:c.getComponents())if(component instanceof Container child)layout(child);}
    static JButton button(Container container,String title){
        for(Component component:container.getComponents()){
            if(component instanceof JButton button&&button.getText().equals(title))return button;
            if(component instanceof Container child){JButton found=button(child,title);if(found!=null)return found;}
        }
        return null;
    }
    static void verifyGui(RecordService service)throws Exception {
        Constructor<RecordDashboard> constructor=RecordDashboard.class.getDeclaredConstructor(RecordService.class);constructor.setAccessible(true);
        SwingUtilities.invokeAndWait(()->{
            RecordDashboard frame=null;
            try {
                frame=constructor.newInstance(service); frame.pack(); frame.setSize(900,570);frame.addNotify();frame.validate();layout(frame);
                for(String title:new String[]{"Create","Update","Delete","Refresh"}){
                    JButton action=button(frame,title);
                    check(action!=null&&action.getBounds().x+action.getWidth()<=action.getParent().getWidth()&&action.getBounds().y+action.getHeight()<=action.getParent().getHeight(),"Dashboard "+title+" button is fully inside the visible form");
                }
                var idField=RecordDashboard.class.getDeclaredField("id");idField.setAccessible(true);
                var valueField=RecordDashboard.class.getDeclaredField("value");valueField.setAccessible(true);
                var statusField=RecordDashboard.class.getDeclaredField("operationStatus");statusField.setAccessible(true);
                JTextField id=(JTextField)idField.get(frame),value=(JTextField)valueField.get(frame);
                JLabel status=(JLabel)statusField.get(frame);
                id.setText("gui-check");value.setText("Created using dashboard action");button(frame,"Create").doClick();
                check(service.get("gui-check").value().equals(value.getText())&&status.getText().equals("create applied"),"Dashboard Create button writes real RMI data and retains success feedback");
                button(frame,"Create").doClick();
                check(status.getText().equals("record already exists"),"Dashboard duplicate-record error remains visible after data refresh");
                value.setText("Updated using dashboard action");button(frame,"Update").doClick();
                check(service.get("gui-check").value().equals(value.getText()),"Dashboard Update button writes real RMI data");
                button(frame,"Delete").doClick();
                check(service.get("gui-check").deleted(),"Dashboard Delete button writes real RMI tombstone");
                var modelField=RecordDashboard.class.getDeclaredField("model");modelField.setAccessible(true);
                var model=(javax.swing.table.DefaultTableModel)modelField.get(frame);
                check(!model.isCellEditable(0,0),"Record table is read only, preventing unpersisted edits");
            }catch(Exception e){throw new RuntimeException(e);}finally{if(frame!=null)frame.dispose();}
        });
    }
    static void snapshot(RecordService primary,RecordService replica) throws Exception {
        Constructor<RecordDashboard> constructor=RecordDashboard.class.getDeclaredConstructor(RecordService.class);
        constructor.setAccessible(true);
        SwingUtilities.invokeAndWait(()->{
            JFrame left=null,right=null;
            try {
                left=constructor.newInstance(primary); right=constructor.newInstance(replica);
                BufferedImage image=new BufferedImage(1800,570,BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics=image.createGraphics();
                int x=0;
                for(JFrame frame:new JFrame[]{left,right}){
                    frame.getContentPane().setPreferredSize(new Dimension(900,570)); frame.pack(); frame.validate(); layout(frame);
                    Graphics2D panel=(Graphics2D)graphics.create(x,0,900,570);
                    frame.getContentPane().printAll(panel); panel.dispose(); x+=900;
                }
                graphics.dispose(); ImageIO.write(image,"png",QA.resolve("lab5-functional.png").toFile());
            }catch(Exception e){throw new RuntimeException(e);}
            finally{if(left!=null)left.dispose();if(right!=null)right.dispose();}
        });
        check(true,"Rendered two actual dashboard component trees, each backed by its own live RMI node, to lab5-functional.png");
    }
    public static void main(String[] args)throws Exception {
        Files.writeString(QA.resolve("features-node-A.log"),"");
        Files.writeString(QA.resolve("features-node-B.log"),"");
        data=Files.createTempDirectory(QA,"feature-data-");
        System.out.println("Java: "+System.getProperty("java.version"));
        System.out.println("Data: "+data.toAbsolutePath());
        System.out.println("Topology: localhost; separate JVM nodes A="+AP+"/"+AS+" B="+BP+"/"+BS);
        try {
            a=start(true);b=start(false);
            RecordService primary=lookup(AP),replica=lookup(BP);
            check(primary.info().primary()&&!replica.info().primary(),"Actual RMI lookup identifies primary A and replica B");
            MutationResult created=replica.create("create-1","course-1","Network programming");
            check(created.success()&&created.record().version()==1,"Create via replica forwards to primary");
            check(replica.create("create-1","course-1","ignored replay value").equals(created),"Same request id returns original result without creating another version");
            equalRecord(primary,replica,"course-1","Created record synchronizes with equal id, value, version and state");
            check(!primary.create("duplicate-id","course-1","duplicate").success(),"Duplicate record id is rejected");
            MutationResult updated=replica.update("update-1","course-1","RMI synchronization verified");
            check(updated.success()&&updated.record().version()==2,"Update via replica forwards and increments version");
            equalRecord(primary,replica,"course-1","Updated record synchronizes exactly");
            check(!replica.applyReplication(Mutation.update("update-1","course-1","wrong",2)).applied(),"Repeated replication request is ignored");
            check(!replica.applyReplication(Mutation.update("older-version","course-1","stale",1)).applied(),"Out-of-order older version is ignored");
            check(replica.get("course-1").equals(updated.record()),"Duplicate/stale replication preserves current value");
            check(replica.delete("delete-1","course-1").success(),"Delete via replica forwards to primary");
            equalRecord(primary,replica,"course-1","Delete tombstone synchronizes exactly, including value");
            check(primary.list().isEmpty()&&replica.list().isEmpty(),"Deleted rows excluded from both list results");
            check(!replica.update("missing-update","missing","x").success()&&!replica.delete("missing-delete","missing").success(),"Updating and deleting missing records return failures");
            stop(b); b=null;
            check(primary.create("offline-create","offline-1","Queued while replica stopped").success(),"Primary accepts real write while replica process is offline");
            check(primary.update("offline-update","offline-1","Durable after primary restart").success(),"Primary accepts offline update");
            check(primary.pendingReplication()>=2,"Offline mutations remain in primary outbox");
            stop(a);a=null;
            a=start(true);primary=lookup(AP);
            check(primary.get("offline-1").value().equals("Durable after primary restart"),"Record survives forced primary process restart");
            check(primary.pendingReplication()>=2,"Pending outbox survives forced primary process restart");
            check(primary.create("offline-create","offline-1","replayed").record().value().equals("Queued while replica stopped"),"Request idempotency result survives primary restart");
            b=start(false);replica=lookup(BP);
            equalRecord(primary,replica,"offline-1","Replica restart catches up queued create/update through RMI");
            final RecordService restartedPrimary=primary;
            await(()->restartedPrimary.pendingReplication()==0,"Primary outbox drains after replica acknowledges retry");
            equalRecord(primary,replica,"course-1","Delete tombstone persists across both process restarts");
            stop(a);a=null;
            check(replica.get("offline-1").value().equals("Durable after primary restart"),"Replica serves durable reads while primary is stopped");
            boolean unavailable=false;
            try{replica.create("unavailable","should-not-exist","x");}catch(java.rmi.RemoteException expected){unavailable=true;}
            check(unavailable&&replica.get("should-not-exist")==null,"Replica write reports primary unavailability and does not create local data");
            a=start(true);primary=lookup(AP);
            verifyGui(primary);
            equalRecord(primary,replica,"gui-check","Dashboard CRUD tombstone synchronizes to replica");
            check(primary.create("show-a","node-a-write","Created on primary A").success(),"Screenshot seed created through live primary RMI");
            check(replica.create("show-b","node-b-write","Created on replica B; forwarded to A").success(),"Screenshot seed created through live replica RMI");
            equalRecord(primary,replica,"node-a-write","Screenshot record A replicated");
            equalRecord(primary,replica,"node-b-write","Screenshot record B replicated");
            check(primary.list().equals(replica.list())&&primary.list().size()==3,"Both nodes expose identical three active records");
            snapshot(primary,replica);
            System.out.println("FunctionalVerification: PASS ("+checks+" checks)");
        }finally{stop(b);stop(a);}
    }
}
