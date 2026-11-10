package lab1.chat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Append-only persistence. Private records are scoped to their two participants. */
public final class MessageStore {
  private final Path log;
  private final Path privateLog;
  public MessageStore(Path root) throws IOException {
    Files.createDirectories(root); log = root.resolve("messages.log");
    if (!Files.exists(log)) Files.createFile(log);
    privateLog = root.resolve("private-messages.log");
    if (!Files.exists(privateLog)) Files.createFile(privateLog);
  }
  public synchronized void append(String user, String type, String payload) {
    String line = Instant.now().toEpochMilli()+"\t"+Protocol.enc(user)+"\t"+type+"\t"+Protocol.enc(payload)+System.lineSeparator();
    try { Files.writeString(log, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND); } catch(IOException e) { System.err.println("Persistence warning: "+e); }
  }
  public synchronized List<String> history(int max) {
    try {
      List<String> all=Files.readAllLines(log, StandardCharsets.UTF_8), out=new ArrayList<>();
      int from=Math.max(0, all.size()-max); for(int i=from;i<all.size();i++) out.add(all.get(i)); return out;
    } catch(IOException e){ return List.of(); }
  }
  public static String[] parse(String line) { return line.split("\\t", -1); }

  public record PrivateMessage(long timestamp, String sender, String recipient, String type,
                               String text, String filename, String mime, String storedFile, long size) { }

  /** Persist successfully before the relay acknowledges/delivers the private message. */
  public synchronized void appendPrivate(PrivateMessage message) throws IOException {
    String line = String.join("\t", Long.toString(message.timestamp()), Protocol.enc(message.sender()),
        Protocol.enc(message.recipient()), message.type(), Protocol.enc(message.text()),
        Protocol.enc(message.filename()), Protocol.enc(message.mime()), Protocol.enc(message.storedFile()),
        Long.toString(message.size())) + System.lineSeparator();
    Files.writeString(privateLog, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
  }

  /** Return only records in the requested bilateral conversation; never a global history. */
  public synchronized List<PrivateMessage> privateHistory(String user, String peer, int max) throws IOException {
    if (max <= 0) return List.of();
    Deque<PrivateMessage> result = new ArrayDeque<>();
    try (BufferedReader reader = Files.newBufferedReader(privateLog, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        try {
          String[] p = parse(line);
          if (p.length != 9) continue;
          PrivateMessage message = new PrivateMessage(Long.parseLong(p[0]), Protocol.dec(p[1]), Protocol.dec(p[2]),
              p[3], Protocol.dec(p[4]), Protocol.dec(p[5]), Protocol.dec(p[6]), Protocol.dec(p[7]), Long.parseLong(p[8]));
          boolean participants = (message.sender().equalsIgnoreCase(user) && message.recipient().equalsIgnoreCase(peer))
              || (message.sender().equalsIgnoreCase(peer) && message.recipient().equalsIgnoreCase(user));
          if (!participants || !("MSG".equals(message.type()) || "FILE".equals(message.type()))) continue;
          result.addLast(message);
          if (result.size() > max) result.removeFirst();
        } catch (IllegalArgumentException ignored) { /* Keep valid records if a final/log record is damaged. */ }
      }
    }
    return List.copyOf(result);
  }
}
