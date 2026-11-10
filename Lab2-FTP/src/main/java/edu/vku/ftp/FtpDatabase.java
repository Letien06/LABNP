package edu.vku.ftp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/** Tiny embedded database using tab-separated, base64-encoded records. */
public final class FtpDatabase {
  private final Path file;
  private final Map<String,User> users = new LinkedHashMap<>();
  private final Map<String,FileRecord> files = new LinkedHashMap<>();
  private final List<Transfer> transfers = new ArrayList<>();
  public record User(String username,String passwordHash,String home,boolean enabled) {}
  public record FileRecord(String path,long size,long modified,String owner) {}
  public record Transfer(String username,String operation,String path,long bytes,String status,long at) {}
  public FtpDatabase(Path file) throws IOException { this.file=file; Files.createDirectories(file.toAbsolutePath().getParent()); load(); if(users.isEmpty()) addUser("student","vku2026","/",true); }
  public synchronized boolean authenticate(String name,String password){ User u=users.get(name); return u!=null&&u.enabled&&u.passwordHash.equals(hash(password)); }
  public synchronized void addUser(String name,String password,String home,boolean enabled) throws IOException { users.put(name,new User(name,hash(password),home,enabled)); save(); }
  public synchronized void recordFile(String path,long size,long modified,String owner) throws IOException { files.put(path,new FileRecord(path,size,modified,owner)); save(); }
  public synchronized void removeFile(String path) throws IOException { files.remove(path); save(); }
  public synchronized void recordTransfer(String user,String operation,String path,long bytes,String status) throws IOException { transfers.add(new Transfer(user,operation,path,bytes,status,System.currentTimeMillis())); save(); }
  public synchronized List<FileRecord> listFiles(){ return List.copyOf(files.values()); }
  public synchronized List<Transfer> transfers(){ return List.copyOf(transfers); }
  public Path path(){ return file; }
  private void load() throws IOException { if(!Files.exists(file)) return; for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){ if(line.isBlank()||line.startsWith("#"))continue; String[] p=line.split("\\t",-1); try { if(p[0].equals("USER")) users.put(dec(p[1]),new User(dec(p[1]),dec(p[2]),dec(p[3]),Boolean.parseBoolean(p[4]))); else if(p[0].equals("FILE")) files.put(dec(p[1]),new FileRecord(dec(p[1]),Long.parseLong(p[2]),Long.parseLong(p[3]),dec(p[4]))); else if(p[0].equals("TRANSFER")) transfers.add(new Transfer(dec(p[1]),dec(p[2]),dec(p[3]),Long.parseLong(p[4]),dec(p[5]),Long.parseLong(p[6]))); } catch(Exception ignored){} } }
  private void save() throws IOException { Path tmp=file.resolveSibling(file.getFileName()+".tmp"); try(BufferedWriter w=Files.newBufferedWriter(tmp,StandardCharsets.UTF_8)){ w.write("# Lab2 FTP embedded database\n"); for(User u:users.values()) w.write(String.join("\t","USER",enc(u.username),enc(u.passwordHash),enc(u.home),Boolean.toString(u.enabled))+"\n"); for(FileRecord f:files.values()) w.write(String.join("\t","FILE",enc(f.path),Long.toString(f.size),Long.toString(f.modified),enc(f.owner))+"\n"); for(Transfer t:transfers) w.write(String.join("\t","TRANSFER",enc(t.username),enc(t.operation),enc(t.path),Long.toString(t.bytes),enc(t.status),Long.toString(t.at))+"\n"); } Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
  private static String enc(String s){ return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
  private static String dec(String s){ return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8); }
  public static String hash(String s){ try { byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)); StringBuilder x=new StringBuilder(); for(byte v:b)x.append(String.format("%02x",v)); return x.toString(); }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);} }
}
