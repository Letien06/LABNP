package lab1.chat;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Offline framing checks and real UDP delivery/membership checks. */
public final class UdpChannelTest {
  private static final UdpChatChannel.Mode MC=UdpChatChannel.Mode.MULTICAST, BC=UdpChatChannel.Mode.BROADCAST;
  private UdpChannelTest() {}

  public static void main(String[] args) throws Exception {
    offlineChecks();
    if(Arrays.asList(args).contains("--offline")) {
      System.out.println("SKIP UDP sockets: disabled by --offline");
      return;
    }
    try { multicastChecks(); broadcastChecks(); }
    catch(Exception error) {
      if(accessDenied(error)) { System.out.println("SKIP socket-policy: "+rootMessage(error)); return; }
      throw error;
    }
  }

  private static void offlineChecks() {
    String group="239.255.42.101",id=UUID.randomUUID().toString(),user="Thiên | thử",text="Xin chào 😀 | dòng 1\ndòng 2";
    int port=15556;
    byte[] frame=UdpChatChannel.encode(MC,group,port,id,user,text,123456);
    UdpChatChannel.Message decoded=UdpChatChannel.decode(MC,group,port,frame,0,frame.length,"10.0.0.2");
    check(decoded!=null&&decoded.id().equals(id)&&decoded.user().equals(user)&&decoded.text().equals(text)&&decoded.timestamp()==123456&&decoded.senderAddress().equals("10.0.0.2"),"Unicode/delimiter frame roundtrip");
    byte[] padded=new byte[frame.length+7];System.arraycopy(frame,0,padded,3,frame.length);
    check(UdpChatChannel.decode(MC,group,port,padded,3,frame.length,"peer")!=null,"datagram offset decoding");
    check(UdpChatChannel.decode(BC,group,port,frame,0,frame.length,"peer")==null,"mode isolation");
    check(UdpChatChannel.decode(MC,"239.255.42.102",port,frame,0,frame.length,"peer")==null,"multicast group isolation");
    check(UdpChatChannel.decode(MC,group,port+1,frame,0,frame.length,"peer")==null,"port isolation");
    String[] fields=new String(frame,StandardCharsets.UTF_8).split("\\|",-1);
    rejectEdited(fields,0,"WRONG",group,port);rejectEdited(fields,5,"invalid-id",group,port);rejectEdited(fields,6,"-1",group,port);rejectEdited(fields,6,"NaN",group,port);rejectEdited(fields,7,"%%%",group,port);rejectEdited(fields,8,"",group,port);
    byte[] malformed="not a lab datagram".getBytes(StandardCharsets.UTF_8);
    check(UdpChatChannel.decode(MC,group,port,malformed,0,malformed.length,"peer")==null,"malformed frame rejection");
    check(UdpChatChannel.decode(MC,group,port,new byte[0],0,0,"peer")==null,"empty datagram rejection");
    byte[] tooLarge=new byte[UdpChatChannel.MAX_PACKET_BYTES+1];check(UdpChatChannel.decode(MC,group,port,tooLarge,0,tooLarge.length,"peer")==null,"oversize incoming datagram rejection");
    expectInvalid(()->UdpChatChannel.encode(MC,group,port,id," ","hi",1),"blank name");
    expectInvalid(()->UdpChatChannel.encode(MC,group,port,id,"a".repeat(65),"hi",1),"oversize name");
    expectInvalid(()->UdpChatChannel.encode(MC,group,port,id,"user"," ",1),"blank text");
    expectInvalid(()->UdpChatChannel.encode(MC,group,port,id,"user","a".repeat(UdpChatChannel.MAX_TEXT_CHARS+1),1),"text character limit");
    expectInvalid(()->UdpChatChannel.encode(MC,group,port,id,"user","界".repeat(2500),1),"encoded byte limit");
    byte[] broadcast=UdpChatChannel.encode(BC,"192.168.1.255",port,id,"user","LAN",1);
    check(UdpChatChannel.decode(BC,"255.255.255.255",port,broadcast,0,broadcast.length,"peer")!=null,"limited/directed broadcast interoperability");
    System.out.println("PASS UDP offline framing, validation, mode/group/port isolation");
  }

  private static void multicastChecks() throws Exception {
    List<UdpChatChannel.Message> a=new CopyOnWriteArrayList<>(),b=new CopyOnWriteArrayList<>(),other=new CopyOnWriteArrayList<>(),rejoinedMessages=new CopyOnWriteArrayList<>();
    List<String> errors=new CopyOnWriteArrayList<>();
    UdpChatChannel first=null,second=null,different=null,rejoined=null;
    try {
      int port=freePort();String group="239.255.42.101";
      first=new UdpChatChannel(MC,group,port,"",a::add,errors::add);
      String nic=first.networkInterface().getName();
      second=new UdpChatChannel(MC,group,port,nic,b::add,errors::add);
      different=new UdpChatChannel(MC,"239.255.42.102",port,nic,other::add,errors::add);
      first.start();different.start();
      first.send("peer-a","before-peer-b-joins");Thread.sleep(250);
      check(b.isEmpty(),"peer received before joining");check(other.isEmpty(),"different multicast group received message");
      check(a.size()==1,"sender local echo duplicated");
      second.start();first.send("peer-a","after-peer-b-joins");await(b,1,"joined multicast peer");Thread.sleep(200);
      check(b.size()==1&&b.get(0).text().equals("after-peer-b-joins"),"joined peer got wrong/duplicate message");check(a.size()==2,"sender multicast loopback duplicated local echo");check(other.isEmpty(),"different group received joined message");
      System.out.println("PASS multicast two peers, join isolation, different-group isolation, single local echo");

      try(MulticastSocket injector=new MulticastSocket(null)) {
        injector.setNetworkInterface(first.networkInterface());injector.setTimeToLive(1);
        byte[] packet=UdpChatChannel.encode(MC,group,port,UUID.randomUUID().toString(),"injected","dedupe-check",System.currentTimeMillis());
        inject(injector,group,port,packet);inject(injector,group,port,packet);await(b,2,"first unique injected datagram");await(a,3,"sender receives injected datagram");Thread.sleep(250);
        check(b.size()==2,"duplicate UUID delivered more than once");check(a.size()==3,"sender dedupe failed for injected packet");
        int before=b.size();inject(injector,group,port,"malformed".getBytes(StandardCharsets.UTF_8));
        inject(injector,group,port,UdpChatChannel.encode(BC,group,port,UUID.randomUUID().toString(),"bad","wrong mode",1));
        inject(injector,group,port,UdpChatChannel.encode(MC,"239.255.42.102",port,UUID.randomUUID().toString(),"bad","wrong group",1));
        inject(injector,group,port,UdpChatChannel.encode(MC,group,port+1,UUID.randomUUID().toString(),"bad","wrong port",1));
        Thread.sleep(300);check(b.size()==before,"malformed/mode/group/port packet passed filter");
      }
      System.out.println("PASS multicast UUID dedupe and malformed/mode/group/port packet filtering");

      int beforeLeave=b.size();second.close();check(!second.isRunning(),"closed multicast channel still running");
      first.send("peer-a","while-peer-b-left");Thread.sleep(250);check(b.size()==beforeLeave,"peer received after leaving");
      rejoined=new UdpChatChannel(MC,group,port,nic,rejoinedMessages::add,errors::add);rejoined.start();Thread.sleep(250);
      check(rejoinedMessages.isEmpty(),"rejoined peer received missed datagram backlog");
      first.send("peer-a","after-peer-b-rejoins");await(rejoinedMessages,1,"rejoined multicast peer");Thread.sleep(150);
      check(rejoinedMessages.size()==1&&rejoinedMessages.get(0).text().equals("after-peer-b-rejoins"),"rejoined peer got wrong/duplicate message");
      check(errors.isEmpty(),"unexpected multicast callback errors: "+errors);
      System.out.println("PASS multicast leave/rejoin semantics, no missed-message replay");
    } finally {if(rejoined!=null)rejoined.close();if(second!=null)second.close();if(different!=null)different.close();if(first!=null)first.close();}
  }

  private static void broadcastChecks() throws Exception {
    List<UdpChatChannel.Message> a=new CopyOnWriteArrayList<>(),b=new CopyOnWriteArrayList<>();List<String> errors=new CopyOnWriteArrayList<>();
    UdpChatChannel first=null,second=null;
    try {
      int port=freePort();first=new UdpChatChannel(BC,"255.255.255.255",port,"",a::add,errors::add);
      second=new UdpChatChannel(BC,"255.255.255.255",port,first.networkInterface().getName(),b::add,errors::add);
      first.start();second.start();first.send("lan-a","broadcast-to-peer-b");await(b,1,"broadcast receiver B");
      second.send("lan-b","broadcast-to-peer-a");await(a,2,"broadcast receiver A");Thread.sleep(250);
      check(a.size()==2&&b.size()==2,"broadcast local echo/loopback duplicated messages");
      check(b.get(0).text().equals("broadcast-to-peer-b")&&a.get(1).text().equals("broadcast-to-peer-a"),"broadcast delivered wrong payload");
      second.close();int before=b.size();first.send("lan-a","after-listener-left");Thread.sleep(250);check(b.size()==before,"broadcast channel received after close");
      check(errors.isEmpty(),"unexpected broadcast callback errors: "+errors);
      System.out.println("PASS broadcast two peers, bidirectional delivery, single local echo and close");
    } finally {if(second!=null)second.close();if(first!=null)first.close();}
  }

  private static void rejectEdited(String[] original,int index,String value,String group,int port) {String[] fields=original.clone();fields[index]=value;byte[] bad=String.join("|",fields).getBytes(StandardCharsets.UTF_8);check(UdpChatChannel.decode(MC,group,port,bad,0,bad.length,"peer")==null,"malformed field "+index+" accepted");}
  private static void inject(MulticastSocket socket,String group,int port,byte[] bytes)throws Exception {socket.send(new DatagramPacket(bytes,bytes.length,InetAddress.getByName(group),port));}
  private static void expectInvalid(Runnable action,String what) {try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("did not reject "+what);}
  private static int freePort() throws SocketException {try(DatagramSocket socket=new DatagramSocket(0)){return socket.getLocalPort();}}
  private static void await(List<?> messages,int count,String what)throws InterruptedException {long deadline=System.currentTimeMillis()+4000;while(messages.size()<count&&System.currentTimeMillis()<deadline)Thread.sleep(20);check(messages.size()>=count,"timeout waiting for "+what);}
  private static boolean accessDenied(Throwable error) {for(Throwable current=error;current!=null;current=current.getCause()){String text=(current.getMessage()==null?"":current.getMessage()).toLowerCase(Locale.ROOT);if(text.contains("10013")||text.contains("permission denied")||text.contains("access permissions")||text.contains("forbidden by its access"))return true;}return false;}
  private static String rootMessage(Throwable error) {Throwable current=error;while(current.getCause()!=null)current=current.getCause();return current.getMessage()==null?current.toString():current.getMessage();}
  private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}
