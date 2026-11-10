package edu.vku.ftp;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Minimal passive-mode FTP client useful for demonstrations and tests. */
public final class FtpClient implements Closeable {
  private final Socket control; private final BufferedReader in; private final PrintWriter out;
  public FtpClient(String host,int port) throws IOException {control=new Socket(host,port);control.setSoTimeout(10000);in=new BufferedReader(new InputStreamReader(control.getInputStream(),StandardCharsets.UTF_8));out=new PrintWriter(new OutputStreamWriter(control.getOutputStream(),StandardCharsets.UTF_8),true); expect(220);}
  private String cmd(String c) throws IOException {out.print(c+"\r\n");out.flush();return in.readLine();}
  private String expect(int code) throws IOException {String r=in.readLine();if(r==null||!r.startsWith(Integer.toString(code)))throw new IOException("Expected "+code+", got "+r);return r;}
  public void login(String u,String p)throws IOException{expectCode(cmd("USER "+u),331);expectCode(cmd("PASS "+p),230);}
  private static void expectCode(String r,int c)throws IOException{if(r==null||!r.startsWith(Integer.toString(c)))throw new IOException("Expected "+c+", got "+r);}
  private ServerSocketProxy passive() throws IOException {String r=cmd("PASV");expectCode(r,227);int a=r.indexOf('('),b=r.indexOf(')');String[] p=r.substring(a+1,b).split(",");String host=String.join(".",Arrays.copyOf(p,4));int port=Integer.parseInt(p[4])*256+Integer.parseInt(p[5]);return new ServerSocketProxy(host,port);}
  public String list() throws IOException {try(ServerSocketProxy d=passive()){String pre=cmd("LIST");expectCode(pre,150);String text=d.readUtf8();expect(226);return text;}}
  public byte[] retr(String remote) throws IOException {try(ServerSocketProxy d=passive()){expectCode(cmd("RETR "+remote),150);byte[] b=d.readAll();expect(226);return b;}}
  public void stor(String remote,byte[] content) throws IOException {try(ServerSocketProxy d=passive()){expectCode(cmd("STOR "+remote),150);d.write(content);expect(226);}}
  public void quit() throws IOException {String r=cmd("QUIT");expectCode(r,221);}
  @Override public void close(){try{control.close();}catch(IOException ignored){}}
  private static final class ServerSocketProxy implements AutoCloseable { final Socket socket; ServerSocketProxy(String host,int port)throws IOException{socket=new Socket(host,port);} byte[] readAll()throws IOException{try(InputStream i=socket.getInputStream();ByteArrayOutputStream b=new ByteArrayOutputStream()){i.transferTo(b);return b.toByteArray();}}String readUtf8()throws IOException{return new String(readAll(),StandardCharsets.UTF_8);}void write(byte[] b)throws IOException{try(OutputStream o=socket.getOutputStream()){o.write(b);o.flush();}}public void close(){try{socket.close();}catch(IOException ignored){}} }
  public static void main(String[] args)throws Exception{String host=args.length>0?args[0]:"127.0.0.1";int port=args.length>1?Integer.parseInt(args[1]):2121;try(FtpClient c=new FtpClient(host,port);BufferedReader r=new BufferedReader(new InputStreamReader(System.in))){c.login("student","vku2026");System.out.println("Connected. Type LIST, RETR file, STOR file, QUIT.");for(String line;(line=r.readLine())!=null;){String[] a=line.split("\\s+",2);try{switch(a[0].toUpperCase(Locale.ROOT)){case "LIST"->System.out.print(c.list());case "RETR"->Files.write(Paths.get(a[1]),c.retr(a[1]));case "STOR"->c.stor(a[1],Files.readAllBytes(Paths.get(a[1])));case "QUIT"-> {c.quit();return;}default->System.out.println("Unknown command");}}catch(Exception e){System.err.println(e.getMessage());}}}}
}
