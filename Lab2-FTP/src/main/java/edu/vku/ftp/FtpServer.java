package edu.vku.ftp;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class FtpServer implements AutoCloseable {
  private final int port; private final Path root; private final FtpDatabase db; private ServerSocket server; private final ExecutorService pool=Executors.newCachedThreadPool(); private volatile boolean running;
  public FtpServer(int port,Path root,Path database) throws IOException { this.port=port; this.root=root.toAbsolutePath().normalize(); Files.createDirectories(this.root); this.db=new FtpDatabase(database); }
  public int getPort(){ return server==null?port:server.getLocalPort(); }
  public FtpDatabase database(){return db;}
  public void start() throws IOException { if(running)return; server=new ServerSocket(port); running=true; pool.submit(()->{while(running){try{Socket s=server.accept(); pool.submit(()->new Session(s).run());}catch(IOException e){if(running)e.printStackTrace();}}}); }
  public void stop(){running=false; try{if(server!=null)server.close();}catch(IOException ignored){} pool.shutdownNow();}
  @Override public void close(){stop();}
  private final class Session {
    final Socket control; BufferedReader in; PrintWriter out; boolean logged; String user; Path cwd=root; ServerSocket pasv;
    Session(Socket s){control=s;}
    void run(){try(control){in=new BufferedReader(new InputStreamReader(control.getInputStream(),StandardCharsets.UTF_8)); out=new PrintWriter(new OutputStreamWriter(control.getOutputStream(),StandardCharsets.UTF_8),true); reply(220,"Lab2 FTP server ready"); String line; while((line=in.readLine())!=null){ if(!handle(line.trim()))break; }}catch(IOException ignored){} finally{closePassive();}}
    boolean handle(String line){ if(line.isEmpty())return true; String[] a=line.split("\\s+",2); String cmd=a[0].toUpperCase(Locale.ROOT); String arg=a.length>1?a[1].trim():""; try { return switch(cmd){
      case "USER" -> { user=arg; logged=false; reply(331,"Password required"); yield true; }
      case "PASS" -> { if(user!=null&&db.authenticate(user,arg)){logged=true;cwd=root;reply(230,"Login successful");}else reply(530,"Login incorrect"); yield true; }
      case "SYST" -> {reply(215,"UNIX Type: L8");yield true; } case "TYPE" -> {reply(200,"Type set to "+arg);yield true; } case "NOOP" -> {reply(200,"OK");yield true; }
      case "PWD" -> {if(!auth())yield true; String rel=root.relativize(cwd).toString().replace('\\','/'); reply(257,"/"+rel+" is current directory");yield true; }
      case "CWD" -> {if(!auth())yield true; Path p=safe(arg); if(Files.isDirectory(p)){cwd=p;reply(250,"Directory changed");}else reply(550,"Directory unavailable");yield true; }
      case "PASV" -> {if(!auth())yield true; closePassive();pasv=new ServerSocket(0); InetAddress addr=control.getLocalAddress(); byte[] ip=addr.getAddress(); if(ip.length!=4)ip=new byte[]{127,0,0,1}; int p=pasv.getLocalPort(); reply(227,"Entering Passive Mode ("+(ip[0]&255)+","+(ip[1]&255)+","+(ip[2]&255)+","+(ip[3]&255)+","+(p/256)+","+(p%256)+")");yield true; }
      case "LIST" -> {if(!auth())yield true; Path dir=arg.isBlank()?cwd:safe(arg); if(!Files.isDirectory(dir)){reply(550,"Not a directory");yield true;} Socket data=openData(); if(data==null)yield true; reply(150,"Opening data connection"); long count=0; try(data; PrintWriter d=new PrintWriter(new OutputStreamWriter(data.getOutputStream(),StandardCharsets.UTF_8),true)){try(DirectoryStream<Path> ds=Files.newDirectoryStream(dir)){for(Path f:ds){String n=f.getFileName().toString(); long size=Files.isDirectory(f)?0:Files.size(f); String type=Files.isDirectory(f)?"d":"-"; d.printf("%s------ 1 owner group %10d %s %s%n",type,size,DateTimeFormatter.ofPattern("MMM dd HH:mm",Locale.ENGLISH).format(Instant.ofEpochMilli(Files.getLastModifiedTime(f).toMillis()).atZone(ZoneId.systemDefault())),n); count++;}}} reply(226,"Transfer complete ("+count+" entries)");yield true; }
      case "RETR" -> {if(!auth())yield true; Path f=safe(arg); if(!Files.isRegularFile(f)){reply(550,"File unavailable");yield true;} Socket data=openData();if(data==null)yield true;reply(150,"Opening binary data connection");long bytes=0;try(data;InputStream src=Files.newInputStream(f);OutputStream dst=data.getOutputStream()){bytes=src.transferTo(dst);}db.recordTransfer(user,"RETR",relative(f),bytes,"OK");reply(226,"Transfer complete");yield true; }
      case "STOR" -> {if(!auth())yield true; Path f=safe(arg); if(arg.isBlank()){reply(501,"Path required");yield true;} Files.createDirectories(f.getParent());Socket data=openData();if(data==null)yield true;reply(150,"Opening binary data connection");long bytes=0;try(data;InputStream src=data.getInputStream();OutputStream dst=Files.newOutputStream(f)){bytes=src.transferTo(dst);}db.recordFile(relative(f),bytes,Files.getLastModifiedTime(f).toMillis(),user);db.recordTransfer(user,"STOR",relative(f),bytes,"OK");reply(226,"Transfer complete");yield true; }
      case "QUIT" -> {reply(221,"Goodbye");yield false; } case "HELP" -> {reply(214,"USER PASS PWD CWD PASV LIST RETR STOR TYPE SYST NOOP QUIT");yield true; } default -> {reply(502,"Command not implemented");yield true;}
    }; }catch(Exception e){reply(451,"Requested action aborted: "+e.getMessage());return true;} }
    boolean auth(){if(!logged)reply(530,"Please login");return logged;}
    Path safe(String input) throws IOException {String x=input.replace('\\','/'); boolean absolute=x.startsWith("/"); if(absolute)x=x.substring(1); Path p=(absolute?root:cwd).resolve(x).normalize(); if(!p.startsWith(root))throw new IOException("Path escapes FTP root"); return p;}
    String relative(Path p){return "/"+root.relativize(p).toString().replace('\\','/');}
    Socket openData() throws IOException { if(pasv==null){reply(425,"Use PASV first");return null;} ServerSocket p=pasv;pasv=null; Socket s=p.accept();p.close();return s; }
    void closePassive(){if(pasv!=null)try{pasv.close();}catch(IOException ignored){}pasv=null;}
    void reply(int c,String m){out.print(c+" "+m+"\r\n");out.flush();}
  }
  public static void main(String[] args) throws Exception {int port=args.length>0?Integer.parseInt(args[0]):2121;Path base=Paths.get("data");try(FtpServer s=new FtpServer(port,base.resolve("root"),base.resolve("ftp.db"))){s.start();System.out.println("FTP server listening on "+s.getPort());new CountDownLatch(1).await();}}
}
