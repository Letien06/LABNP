package lab1.chat;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Reliable TCP relay. The client UDP screens communicate directly over the LAN. */
public final class ChatServer implements AutoCloseable {
  private final int port, udpPort;
  private final String multicast, broadcast;
  private final Path root;
  private final MessageStore store;
  private final ExecutorService pool = Executors.newCachedThreadPool();
  private final Set<Client> clients = ConcurrentHashMap.newKeySet();
  private final ConcurrentMap<String, Client> identities = new ConcurrentHashMap<>();
  private final Deque<String> events = new ConcurrentLinkedDeque<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicLong lastMessageTimestamp = new AtomicLong();
  private volatile boolean running;
  private volatile ServerSocket serverSocket;
  private volatile String failure;

  public ChatServer(int port, int udpPort, String multicast, String broadcast, Path root) throws Exception {
    this.port = port; this.udpPort = udpPort;
    this.multicast = multicast; this.broadcast = broadcast; this.root = root;
    store = new MessageStore(root);
  }

  public void start() throws IOException {
    if (closed.get()) throw new IOException("Server has already stopped");
    try (ServerSocket listener = new ServerSocket()) {
      serverSocket = listener;
      listener.setReuseAddress(true);
      listener.bind(new InetSocketAddress(port));
      if (closed.get()) return;
      running = true;
      event("Đang chạy · TCP " + port());
      System.out.printf("Lab1 Chat Server TCP %d | UDP channels operate directly between clients%n", port());
      while (!closed.get()) {
        try {
          Socket socket = listener.accept();
          if (closed.get() || clients.size() >= 64) { socket.close(); continue; }
          socket.setKeepAlive(true);
          Client client = new Client(socket);
          clients.add(client);
          pool.submit(client);
        } catch (SocketException e) {
          if (!closed.get()) throw e;
        }
      }
    } catch (IOException e) {
      if (!closed.get()) {
        failure = e.getMessage();
        event("Không thể chạy máy chủ · " + failure);
        throw e;
      }
    } finally {
      running = false;
      serverSocket = null;
      close();
    }
  }

  private void dispatch(Client from, String line) {
    String[] p = Protocol.split(line);
    if (p.length == 0) return;
    try {
      if ("DM".equals(p[0]) && p.length == 3) {
        Client recipient = recipient(Protocol.dec(p[1]));
        String text = Protocol.dec(p[2]);
        if (text.isBlank() || text.length() > 4000) throw new IOException("Tin nhắn tối đa 4.000 ký tự");
        MessageStore.PrivateMessage message = new MessageStore.PrivateMessage(nextTimestamp(), from.user,
            recipient.user, "MSG", text, "", "", "", 0);
        store.appendPrivate(message);
        relayPrivate(from, recipient, privateFrame(message));
        event("Tin nhắn riêng · " + from.user + " → " + recipient.user);
      } else if ("DMFILE".equals(p[0]) && p.length == 5) {
        Client recipient = recipient(Protocol.dec(p[1]));
        String name = Protocol.dec(p[2]), mime = Protocol.dec(p[3]);
        if (p[4].length() > 14_000_000) throw new IOException("Tệp vượt quá 10 MB");
        byte[] bytes = Base64.getDecoder().decode(p[4]);
        if (name.isBlank() || name.length() > 120 || name.indexOf('\0') >= 0 || bytes.length > 10 * 1024 * 1024
            || mime.isBlank() || mime.length() > 160 || mime.chars().anyMatch(Character::isISOControl))
          throw new IOException("Tệp không hợp lệ hoặc vượt quá 10 MB");
        String safe = Path.of(name.replace('\\', '/')).getFileName().toString();
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) throw new IOException("Tên tệp không hợp lệ");
        String storedFile = UUID.randomUUID() + "_" + safe;
        Path upload = root.resolve("uploads").resolve(storedFile);
        Files.createDirectories(upload.getParent());
        Files.write(upload, bytes);
        MessageStore.PrivateMessage message = new MessageStore.PrivateMessage(nextTimestamp(), from.user,
            recipient.user, "FILE", "", safe, mime, storedFile, bytes.length);
        try { store.appendPrivate(message); }
        catch (IOException error) { Files.deleteIfExists(upload); throw error; }
        relayPrivate(from, recipient, privateFrame(message));
        event("Tệp riêng · " + from.user + " → " + recipient.user + " · " + safe);
      } else if ("HISTORY".equals(p[0]) && p.length == 2) {
        String peer = validName(Protocol.dec(p[1]));
        try {
          for (MessageStore.PrivateMessage message : store.privateHistory(from.user, peer, 50)) {
            try { from.send(privateFrame(message).replaceFirst("^PRIVATE\\|", "PRIVATE_HISTORY|")); }
            catch (IOException error) { from.send(Protocol.join("ERROR", Protocol.enc("Không thể đọc tệp lịch sử: " + message.filename()))); }
          }
        } finally { from.send(Protocol.join("HISTORY_END", Protocol.enc(peer))); }
      } else {
        throw new IOException("TCP chỉ hỗ trợ tin nhắn riêng DM/DMFILE và HISTORY. Hãy chọn một người nhận.");
      }
    } catch (Exception e) { from.send(Protocol.join("ERROR", Protocol.enc(e.getMessage() == null ? "Invalid message" : e.getMessage()))); }
  }

  private static String validName(String name) throws IOException {
    String value = name.trim();
    if (value.isBlank() || value.length() > 40 || value.chars().anyMatch(Character::isISOControl))
      throw new IOException("Tên hiển thị phải có 1–40 ký tự và không chứa ký tự điều khiển");
    return value;
  }

  private long nextTimestamp() {
    // PRIVATE frames use the timestamp as part of their client dedupe key.
    // Distinct sends must stay distinct even within one clock millisecond.
    return lastMessageTimestamp.updateAndGet(previous -> Math.max(System.currentTimeMillis(), previous + 1));
  }

  private static String identityKey(String name) {
    StringBuilder folded = new StringBuilder();
    name.codePoints().forEach(codePoint -> folded.appendCodePoint(Character.toLowerCase(Character.toUpperCase(codePoint))));
    return folded.toString();
  }

  private Client recipient(String name) throws IOException {
    String valid = validName(name);
    Client recipient = identities.get(identityKey(valid));
    if (recipient == null || !recipient.joined || recipient.socket.isClosed()) throw new IOException("Người nhận hiện không online: " + valid);
    return recipient;
  }

  private void relayPrivate(Client sender, Client recipient, String frame) {
    recipient.send(frame);
    if (sender != recipient) sender.send(frame);
  }

  private String privateFrame(MessageStore.PrivateMessage message) throws IOException {
    String header = Protocol.join("PRIVATE", Protocol.enc(message.sender()), Protocol.enc(message.recipient()),
        Long.toString(message.timestamp()), message.type());
    if ("MSG".equals(message.type())) return header + "|" + Protocol.enc(message.text());
    Path uploads = root.resolve("uploads").toAbsolutePath().normalize();
    Path file = uploads.resolve(message.storedFile()).normalize();
    if (!file.startsWith(uploads) || !Files.isRegularFile(file) || Files.size(file) > 10 * 1024 * 1024)
      throw new IOException("Tệp lưu trữ không khả dụng");
    byte[] bytes = Files.readAllBytes(file);
    return header + "|" + Protocol.enc(message.filename()) + "|" + Protocol.enc(message.mime())
        + "|" + bytes.length + "|" + Base64.getEncoder().encodeToString(bytes);
  }

  private void broadcast(String line) { for (Client client : clients) if (client.joined) client.send(line); }

  /** Membership uses an additive frame; older clients safely ignore it. */
  private synchronized void broadcastUsers() {
    if (closed.get()) return;
    List<String> names = identities.values().stream().filter(c -> c.joined).map(c -> c.user).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    String frame = "USERS";
    for (String name : names) frame += "|" + Protocol.enc(name);
    broadcast(frame);
  }

  private final class Client implements Runnable {
    final Socket socket;
    PrintWriter out;
    volatile String user = "Guest";
    volatile boolean joined;
    String identityKey;
    Client(Socket socket) { this.socket = socket; }

    public void run() {
      try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        String first = reader.readLine();
        if (first == null) return;
        String[] p = Protocol.split(first);
        if (p.length < 2 || !"JOIN".equals(p[0])) { send(Protocol.join("ERROR", Protocol.enc("JOIN required"))); return; }
        user = validName(Protocol.dec(p[1]));
        identityKey = identityKey(user);
        if (identities.putIfAbsent(identityKey, this) != null) {
          send(Protocol.join("ERROR", Protocol.enc("Tên hiển thị đã được sử dụng. Hãy chọn tên khác.")));
          return;
        }
        send(Protocol.join("WELCOME", Protocol.enc(user)));
        joined = true;
        event(user + " online · " + socket.getRemoteSocketAddress());
        broadcastUsers();
        String line;
        while (!closed.get() && (line = reader.readLine()) != null) dispatch(this, line);
      } catch (Exception e) {
        if (!closed.get() && joined) event("Mất kết nối · " + user);
        else if (!closed.get()) send(Protocol.join("ERROR", Protocol.enc(e.getMessage() == null ? "JOIN không hợp lệ" : e.getMessage())));
      } finally {
        if (identityKey != null) identities.remove(identityKey, this);
        clients.remove(this);
        try { socket.close(); } catch (IOException ignored) { }
        if (joined) {
          joined = false;
          event(user + " offline");
          if (!closed.get()) {
            broadcastUsers();
          }
        }
      }
    }
    synchronized void send(String line) { if (out != null) out.println(line); }
  }

  public boolean isRunning() { return running; }
  public String failure() { return failure; }
  public int port() { ServerSocket listener = serverSocket; return listener != null && listener.isBound() ? listener.getLocalPort() : port; }
  public int udpPort() { return udpPort; }
  public String multicast() { return multicast; }
  public String broadcast() { return broadcast; }
  public List<ClientInfo> clientSnapshot() {
    List<ClientInfo> snapshot = new ArrayList<>();
    for (Client c : clients) if (c.joined) snapshot.add(new ClientInfo(c.user, String.valueOf(c.socket.getRemoteSocketAddress())));
    snapshot.sort(Comparator.comparing(ClientInfo::user, String.CASE_INSENSITIVE_ORDER));
    return snapshot;
  }
  public List<String> eventSnapshot() { return new ArrayList<>(events); }
  private void event(String message) {
    String time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.now());
    events.addLast(time + "  " + message.replace('\n', ' ').replace('\r', ' '));
    while (events.size() > 250) events.pollFirst();
  }
  public record ClientInfo(String user, String address) { }

  @Override public void close() {
    if (!closed.compareAndSet(false, true)) return;
    running = false;
    try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) { }
    for (Client client : clients) try { client.socket.close(); } catch (IOException ignored) { }
    pool.shutdownNow();
    event("Máy chủ đã dừng");
  }

  public static void main(String[] args) throws Exception {
    int port = getInt(args, "--port", 5555), udpPort = getInt(args, "--udp-port", 5556);
    try (ChatServer server = new ChatServer(port, udpPort, get(args, "--multicast", "239.255.42.99"), get(args, "--broadcast", "255.255.255.255"), Path.of(get(args, "--data", "data")))) {
      Runtime.getRuntime().addShutdownHook(new Thread(server::close, "chat-server-shutdown"));
      server.start();
    }
  }
  static String get(String[] args, String key, String fallback) { for (int i = 0; i < args.length - 1; i++) if (key.equals(args[i])) return args[i + 1]; return fallback; }
  static int getInt(String[] args, String key, int fallback) { try { return Integer.parseInt(get(args, key, Integer.toString(fallback))); } catch (Exception e) { return fallback; } }
}
