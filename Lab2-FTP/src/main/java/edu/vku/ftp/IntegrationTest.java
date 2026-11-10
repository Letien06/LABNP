package edu.vku.ftp;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Real loopback integration checks; all files live in an isolated target folder. */
public final class IntegrationTest {
  private static void check(boolean condition,String label) { if(!condition)throw new AssertionError(label); System.out.println("PASS: "+label); }
  private static void rejected(IoAction action,String code) throws Exception {
    try { action.run(); throw new AssertionError("Expected rejection "+code); }
    catch(IOException e) { check(e.getMessage().contains(code),"negative command rejected with "+code); }
  }
  @FunctionalInterface private interface IoAction { void run() throws Exception; }
  public static void main(String[] args) throws Exception {
    Files.createDirectories(Path.of("target"));
    Path base=Files.createTempDirectory(Path.of("target"),"lab2-ftp-test-");
    Path root=base.resolve("root"), db=base.resolve("ftp.db");
    byte[] payload=new byte[131071]; new Random(20261109L).nextBytes(payload);
    String name="binary sample.bin";
    try(FtpServer server=new FtpServer(0,root,db)) {
      server.start();
      try(FtpClient guest=new FtpClient("127.0.0.1",server.getPort())) { rejected(guest::list,"530"); rejected(()->guest.login("student","wrong-password"),"530"); }
      server.database().addUser("disabled","secret","/",false);
      server.database().addUser("qa-persist","qa-secret","/",true);
      try(FtpClient disabled=new FtpClient("127.0.0.1",server.getPort())) { rejected(()->disabled.login("disabled","secret"),"530"); }
      try(FtpClient client=new FtpClient("127.0.0.1",server.getPort())) {
        client.login("student","vku2026"); check(client.list().isBlank(),"valid login and passive LIST on empty root");
        client.stor(name,payload); check(client.list().contains(name),"STOR then LIST exposes filename containing spaces");
        byte[] downloaded=client.retr(name); check(Arrays.equals(payload,downloaded),"RETR is byte-exact for 131071 binary bytes");
        System.out.println("SHA-256: "+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(downloaded)));
        rejected(()->client.retr("missing.bin"),"550");
        rejected(()->client.stor("../escape.bin",payload),"451");
        check(!Files.exists(base.resolve("escape.bin")),"path traversal cannot write outside FTP root");
        client.quit();
      }
      check(server.database().transfers().size()==2,"database records exactly STOR and RETR successes");
      check(server.database().transfers().stream().allMatch(t->t.bytes()==payload.length&&t.status().equals("OK")&&t.username().equals("student")),"audit stores correct byte count, user and status");
      check(server.database().listFiles().size()==1&&server.database().listFiles().get(0).path().equals("/"+name),"database stores uploaded file metadata");
    }
    try(FtpServer restarted=new FtpServer(0,root,db)) {
      restarted.start();
      check(restarted.database().transfers().size()==2&&restarted.database().listFiles().size()==1,"file metadata and transfer audit survive server restart");
      try(FtpClient client=new FtpClient("127.0.0.1",restarted.getPort())) {
        client.login("qa-persist","qa-secret"); check(Arrays.equals(payload,client.retr(name)),"custom user and file data survive server restart"); client.quit();
      }
      try(FtpClient disabled=new FtpClient("127.0.0.1",restarted.getPort())) { rejected(()->disabled.login("disabled","secret"),"530"); }
    }
    System.out.println("EVIDENCE: "+base.toAbsolutePath());
    System.out.println("PASS: Lab2 FTP functional integration complete");
  }
}
