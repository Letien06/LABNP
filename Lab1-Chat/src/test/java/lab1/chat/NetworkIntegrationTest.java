package lab1.chat;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Private TCP tests: six identities, isolated bilateral delivery/history and lifecycle. */
public final class NetworkIntegrationTest {
  private static final int TIMEOUT_MS = 5000;
  private static final byte[] ATTACHMENT = "lab1-private-attachment\n".getBytes(StandardCharsets.UTF_8);
  private NetworkIntegrationTest() { }

  public static void main(String[] args) throws Exception {
    Files.createDirectories(Path.of(".qa"));
    Path data = Files.createTempDirectory(Path.of(".qa"), "lab1-private-");
    ChatServer server = null;
    List<Peer> peers = new ArrayList<>();
    try {
      server = new ChatServer(0, 5556, "239.255.42.99", "255.255.255.255", data);
      ChatServer activeServer = server;
      Thread thread = new Thread(() -> { try { activeServer.start(); } catch (IOException ignored) { } }, "lab1-test-server");
      thread.setDaemon(true);
      thread.start();
      waitUntil(activeServer::isRunning, "server startup");
      for (int i = 1; i <= 6; i++) {
        Peer peer = new Peer(activeServer.port());
        peers.add(peer);
        peer.send("JOIN", Protocol.enc("client" + i));
        require(peer.expect("WELCOME")[1].equals(Protocol.enc("client" + i)), "incorrect welcome identity");
      }
      for (Peer peer : peers) peer.expectUsers(6);
      require(server.clientSnapshot().size() == 6, "expected six online clients");
      System.out.println("PASS six TCP identities + USERS presence; no auto/global history");

      try (Peer duplicate = new Peer(server.port())) {
        duplicate.send("JOIN", Protocol.enc("CLIENT1"));
        require(Protocol.dec(duplicate.expect("ERROR")[1]).contains("đã được sử dụng"), "duplicate name was not rejected");
        require(duplicate.reader.readLine() == null, "duplicate identity socket should close");
      }
      require(server.clientSnapshot().size() == 6, "duplicate must not replace original identity");
      System.out.println("PASS case-insensitive duplicate identity rejection");

      try (Peer raceA = new Peer(server.port()); Peer raceB = new Peer(server.port())) {
        java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Exception> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread a = new Thread(() -> { try { gate.await(); raceA.send("JOIN", Protocol.enc("Race")); } catch (Exception error) { failure.set(error); } });
        Thread b = new Thread(() -> { try { gate.await(); raceB.send("JOIN", Protocol.enc("RACE")); } catch (Exception error) { failure.set(error); } });
        a.start(); b.start(); gate.countDown(); a.join(); b.join();
        if (failure.get() != null) throw failure.get();
        String firstKind = Protocol.split(Objects.requireNonNull(raceA.reader.readLine()))[0];
        String secondKind = Protocol.split(Objects.requireNonNull(raceB.reader.readLine()))[0];
        require((firstKind.equals("WELCOME") && secondKind.equals("ERROR"))
            || (firstKind.equals("ERROR") && secondKind.equals("WELCOME")), "simultaneous duplicate joins must select exactly one identity");
        for (Peer peer : peers) peer.expectUsers(7);
      }
      for (Peer peer : peers) peer.expectUsers(6);
      System.out.println("PASS atomic duplicate rejection under concurrent JOIN");

      Peer first = peers.get(0), second = peers.get(1), third = peers.get(2);
      first.send("DM", Protocol.enc("CLIENT2"), Protocol.enc("Private hello 👋"));
      String[] sent = first.expect("PRIVATE"), received = second.expect("PRIVATE");
      require(Arrays.equals(sent, received), "sender and recipient must receive the same frame");
      require(sent.length == 6 && sent[4].equals("MSG") && Protocol.dec(sent[1]).equals("client1")
          && Protocol.dec(sent[2]).equals("client2") && Protocol.dec(sent[5]).equals("Private hello 👋"), "private text changed");
      for (int i = 2; i < peers.size(); i++) peers.get(i).expectQuiet();
      System.out.println("PASS private text + Unicode; other four clients receive nothing");

      first.send("DMFILE", Protocol.enc("client2"), Protocol.enc("private.txt"), Protocol.enc("text/plain"), Base64.getEncoder().encodeToString(ATTACHMENT));
      String[] sentFile = first.expect("PRIVATE"), receivedFile = second.expect("PRIVATE");
      require(Arrays.equals(sentFile, receivedFile), "private file sender/receiver frames differ");
      require(sentFile.length == 9 && sentFile[4].equals("FILE") && Protocol.dec(sentFile[5]).equals("private.txt")
          && Arrays.equals(ATTACHMENT, Base64.getDecoder().decode(sentFile[8])), "private attachment changed");
      for (int i = 2; i < peers.size(); i++) peers.get(i).expectQuiet();
      System.out.println("PASS private attachment byte integrity + isolation");

      first.send("HISTORY", Protocol.enc("client2"));
      List<String[]> history = first.history("client2");
      require(history.size() == 2 && samePrivatePayload(history.get(0), sent) && samePrivatePayload(history.get(1), sentFile), "private history should replay exact bilateral text/file payloads under PRIVATE_HISTORY");
      third.send("HISTORY", Protocol.enc("client2"));
      require(third.history("client2").isEmpty(), "third-party history leaked private messages");
      second.send("HISTORY", Protocol.enc("client1"));
      require(second.history("client1").size() == 2, "recipient cannot retrieve bilateral history");
      MessageStore reopened = new MessageStore(data);
      require(reopened.privateHistory("client1", "client2", 50).size() == 2, "private persistence missing after reopen");
      require(reopened.privateHistory("client3", "client2", 50).isEmpty(), "persisted privacy filter leaked");
      System.out.println("PASS scoped HISTORY + timestamp/file replay + persistent participant filtering");

      first.send("DM", Protocol.enc("client2"), Protocol.enc("Repeated identical text"));
      first.send("DM", Protocol.enc("client2"), Protocol.enc("Repeated identical text"));
      String[] repeatA = first.expect("PRIVATE"), repeatB = first.expect("PRIVATE");
      second.expect("PRIVATE"); second.expect("PRIVATE");
      require(Long.parseLong(repeatB[3]) > Long.parseLong(repeatA[3]), "distinct identical messages must have monotonic timestamps");
      third.expectQuiet();
      System.out.println("PASS repeated identical messages retain distinct timestamps");

      first.send("MSG", Protocol.enc("must not broadcast"));
      first.expect("ERROR");
      first.send("FILE", Protocol.enc("global.txt"), Protocol.enc("text/plain"), "YWJj");
      first.expect("ERROR");
      second.expectQuiet(); third.expectQuiet();
      System.out.println("PASS old global MSG/FILE rejected");

      peers.get(5).close();
      for (int i = 0; i < 5; i++) peers.get(i).expectUsers(5);
      waitUntil(() -> activeServer.clientSnapshot().size() == 5, "offline presence");
      first.send("DM", Protocol.enc("client6"), Protocol.enc("offline message"));
      require(Protocol.dec(first.expect("ERROR")[1]).contains("không online"), "offline recipient should return targeted failure");
      require(new MessageStore(data).privateHistory("client1", "client6", 50).isEmpty(), "failed offline send should not be persisted");
      second.expectQuiet();
      System.out.println("PASS offline recipient failure + leave presence");

      server.close();
      thread.join(TIMEOUT_MS);
      require(!server.isRunning() && !thread.isAlive(), "server did not stop");
      for (int i = 0; i < 5; i++) {
        peers.get(i).socket.setSoTimeout(1000);
        require(peers.get(i).reader.readLine() == null, "client socket not closed on server stop");
      }
      System.out.println("PASS server stop + all private sockets closed");
    } catch (Exception error) {
      if (isAccessDenied(error)) { System.out.println("SKIP socket-policy: " + rootMessage(error)); return; }
      throw error;
    } finally {
      if (server != null) server.close();
      for (Peer peer : peers) peer.close();
      deleteTree(data);
    }
  }

  private static final class Peer implements AutoCloseable {
    final Socket socket = new Socket();
    final BufferedReader reader;
    final BufferedWriter writer;
    Peer(int port) throws IOException {
      socket.connect(new InetSocketAddress("127.0.0.1", port), TIMEOUT_MS);
      socket.setSoTimeout(TIMEOUT_MS);
      reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }
    void send(String... parts) throws IOException { writer.write(Protocol.join(parts)); writer.newLine(); writer.flush(); }
    String[] expect(String kind) throws IOException {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] frame = Protocol.split(line);
        if (frame[0].equals(kind)) return frame;
        require(frame[0].equals("USERS"), "Unexpected frame while expecting " + kind + ": " + frame[0]);
      }
      throw new AssertionError("Connection closed before " + kind);
    }
    void expectUsers(int count) throws IOException {
      String[] frame;
      do { frame = expect("USERS"); } while (frame.length != count + 1);
      Set<String> names = new HashSet<>();
      for (int i = 1; i < frame.length; i++) names.add(Protocol.dec(frame[i]));
      require(names.size() == count && names.contains("client1"), "bad USERS payload");
    }
    List<String[]> history(String peer) throws IOException {
      List<String[]> history = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        String[] frame = Protocol.split(line);
        if (frame[0].equals("HISTORY_END")) {
          require(Protocol.dec(frame[1]).equals(peer), "wrong HISTORY_END peer");
          return history;
        }
        if (frame[0].equals("USERS")) continue;
        require(frame[0].equals("PRIVATE_HISTORY"), "unexpected history frame: " + frame[0]);
        history.add(frame);
      }
      throw new AssertionError("Missing HISTORY_END");
    }
    void expectQuiet() throws IOException {
      socket.setSoTimeout(180);
      try {
        String line;
        while ((line = reader.readLine()) != null) {
          String kind = Protocol.split(line)[0];
          require(kind.equals("USERS"), "Private/global traffic leaked to another participant: " + kind);
        }
        throw new AssertionError("Unexpected disconnected peer");
      } catch (SocketTimeoutException expected) { }
      finally { socket.setSoTimeout(TIMEOUT_MS); }
    }
    @Override public void close() { try { socket.close(); } catch (IOException ignored) { } }
  }

  private static void waitUntil(Check condition, String label) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
    while (System.nanoTime() < deadline) { if (condition.ok()) return; Thread.sleep(20); }
    throw new AssertionError("Timeout: " + label);
  }
  private static boolean isAccessDenied(Throwable error) { String message = rootMessage(error).toLowerCase(Locale.ROOT); return message.contains("10013") || message.contains("permission denied") || message.contains("access permissions") || message.contains("forbidden by its access"); }
  private static String rootMessage(Throwable error) { Throwable root = error; while (root.getCause() != null) root = root.getCause(); return String.valueOf(root.getMessage()); }
  private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
  private static boolean samePrivatePayload(String[] history, String[] live) {
    return history.length == live.length && history[0].equals("PRIVATE_HISTORY") && live[0].equals("PRIVATE")
        && Arrays.equals(Arrays.copyOfRange(history, 1, history.length), Arrays.copyOfRange(live, 1, live.length));
  }
  private static void deleteTree(Path path) {
    try (var files = Files.walk(path)) { files.sorted(Comparator.reverseOrder()).forEach(file -> { try { Files.deleteIfExists(file); } catch (IOException ignored) { } }); }
    catch (IOException ignored) { }
  }
  @FunctionalInterface private interface Check { boolean ok(); }
}
