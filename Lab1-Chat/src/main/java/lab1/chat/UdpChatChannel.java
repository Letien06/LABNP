package lab1.chat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A peer-to-peer UDP text room, independent of the TCP chat server.
 * Multicast and broadcast have separate destinations, sockets and envelopes.
 * Callbacks are invoked on the receive thread, or the send caller for local echo.
 */
public final class UdpChatChannel implements AutoCloseable {
  public enum Mode { MULTICAST, BROADCAST }
  public record Message(String id, String user, String text, long timestamp, String senderAddress) { }

  public static final int MAX_PACKET_BYTES = 8192;
  public static final int MAX_TEXT_CHARS = 4000;
  private static final int MAX_SEEN = 2048;
  private static final String PREFIX = "LAB1UDP2";
  private final Mode mode;
  private final InetAddress destination;
  private final int port;
  private final NetworkInterface networkInterface;
  private final Inet4Address sourceAddress;
  private final Consumer<Message> onMessage;
  private final Consumer<String> onError;
  private final Map<String, Boolean> seen = new LinkedHashMap<>();
  private volatile boolean running;
  private volatile boolean closed;
  private DatagramSocket receiveSocket;
  private DatagramSocket sendSocket;
  private Thread worker;

  public UdpChatChannel(Mode mode, String destination, int port, String interfaceName,
                        Consumer<Message> onMessage, Consumer<String> onError) throws Exception {
    this.mode = Objects.requireNonNull(mode, "UDP mode is required.");
    this.onMessage = Objects.requireNonNull(onMessage, "Message callback is required.");
    this.onError = Objects.requireNonNull(onError, "Error callback is required.");
    if (port < 1 || port > 65535) throw new IllegalArgumentException("UDP port must be between 1 and 65535.");
    this.port = port;
    this.destination = parseIpv4(destination);
    this.networkInterface = chooseInterface(interfaceName, mode);
    this.sourceAddress = ipv4Address(networkInterface);
    if (mode == Mode.MULTICAST && !this.destination.isMulticastAddress()) {
      throw new IllegalArgumentException("Multicast destination must be an IPv4 multicast group (224.0.0.0 to 239.255.255.255).");
    }
    if (mode == Mode.BROADCAST && !isBroadcast(this.destination, networkInterface)) {
      throw new IllegalArgumentException("Broadcast destination must be 255.255.255.255 or the selected interface's subnet broadcast address.");
    }
  }

  public Mode mode() { return mode; }
  public String destination() { return destination.getHostAddress(); }
  public int port() { return port; }
  public NetworkInterface networkInterface() { return networkInterface; }
  public String sourceAddress() { return sourceAddress.getHostAddress(); }
  public boolean isRunning() { return running; }

  /** Return usable interfaces without opening a socket. Loopback is listed last. */
  public static List<NetworkInterface> interfaces() throws SocketException {
    List<NetworkInterface> result = new ArrayList<>();
    for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
      if (nic.isUp() && ipv4Address(nic) != null) result.add(nic);
    }
    result.sort(Comparator.comparing((NetworkInterface nic) -> {
      try { return nic.isLoopback(); } catch (SocketException e) { return true; }
    }).thenComparing(UdpChatChannel::likelyVirtual).thenComparing(NetworkInterface::getName));
    return List.copyOf(result);
  }

  /** Open a reusable listener and join the multicast group on the chosen NIC. */
  public synchronized void start() throws Exception {
    if (closed) throw new IllegalStateException("This UDP channel has been closed. Create a new channel to reconnect.");
    if (running) return;
    try {
      if (mode == Mode.MULTICAST) {
        MulticastSocket multicast = new MulticastSocket(null);
        receiveSocket = multicast;
        sendSocket = multicast;
        multicast.setReuseAddress(true);
        multicast.bind(new InetSocketAddress("0.0.0.0", port));
        multicast.setNetworkInterface(networkInterface);
        multicast.setTimeToLive(1);
        multicast.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true);
        multicast.joinGroup(new InetSocketAddress(destination, port), networkInterface);
      } else {
        DatagramSocket listener = new DatagramSocket(null);
        receiveSocket = listener;
        listener.setReuseAddress(true);
        listener.setBroadcast(true);
        listener.bind(new InetSocketAddress("0.0.0.0", port));
        // A separate sender binds the selected IPv4 NIC while the wildcard
        // listener still receives broadcast packets addressed to this port.
        DatagramSocket sender = new DatagramSocket(null);
        sendSocket = sender;
        sender.setBroadcast(true);
        sender.bind(new InetSocketAddress(sourceAddress, 0));
      }
      running = true;
      worker = new Thread(this::receiveLoop, "udp-chat-" + mode.name().toLowerCase() + "-" + port);
      worker.setDaemon(true);
      worker.start();
    } catch (Exception error) {
      cleanupSockets();
      throw error;
    }
  }

  /** Send one text datagram and show one immediate local echo after send succeeds. */
  public void send(String user, String text) throws Exception {
    DatagramSocket sender;
    synchronized (this) {
      if (!running || closed || sendSocket == null) throw new IllegalStateException("Connect to the UDP room first.");
      sender = sendSocket;
    }
    String id = UUID.randomUUID().toString();
    long timestamp = System.currentTimeMillis();
    byte[] bytes = encode(mode, destination.getHostAddress(), port, id, user, text, timestamp);
    remember(id); // Loopback packets cannot race ahead of the local echo.
    sender.send(new DatagramPacket(bytes, bytes.length, destination, port));
    deliver(new Message(id, user.trim(), text, timestamp, sourceAddress.getHostAddress()));
  }

  private void receiveLoop() {
    byte[] buffer = new byte[MAX_PACKET_BYTES + 1];
    while (running) {
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
      try {
        receiveSocket.receive(packet);
        if (packet.getLength() > MAX_PACKET_BYTES) continue;
        Message message = decode(mode, destination.getHostAddress(), port, packet.getData(), packet.getOffset(),
            packet.getLength(), packet.getAddress().getHostAddress());
        if (message != null && remember(message.id())) deliver(message);
      } catch (SocketException error) {
        if (running) {
          running = false;
          cleanupSockets();
          report("UDP socket error: " + error.getMessage());
        }
      } catch (IOException error) {
        if (running) report("UDP receive error: " + error.getMessage());
      } catch (RuntimeException error) {
        // A malformed peer packet or UI callback must not kill the room.
        if (running) report("UDP packet error: " + error.getMessage());
      }
    }
  }

  private boolean remember(String id) {
    synchronized (seen) {
      if (seen.containsKey(id)) return false;
      seen.put(id, Boolean.TRUE);
      if (seen.size() > MAX_SEEN) seen.remove(seen.keySet().iterator().next());
      return true;
    }
  }

  private void deliver(Message message) {
    try { onMessage.accept(message); }
    catch (RuntimeException error) { report("UDP message callback error: " + error.getMessage()); }
  }

  private void report(String message) {
    try { onError.accept(message); } catch (RuntimeException ignored) { }
  }

  @Override public synchronized void close() {
    if (closed) return;
    closed = true;
    running = false;
    cleanupSockets();
  }

  private void cleanupSockets() {
    if (receiveSocket instanceof MulticastSocket multicast && !multicast.isClosed()) {
      try { multicast.leaveGroup(new InetSocketAddress(destination, port), networkInterface); }
      catch (IOException ignored) { }
    }
    if (receiveSocket != null) receiveSocket.close();
    if (sendSocket != null && sendSocket != receiveSocket) sendSocket.close();
  }

  static byte[] encode(Mode mode, String destination, int port, String id, String user, String text, long timestamp) {
    if (user == null || user.isBlank() || user.trim().length() > 64) throw new IllegalArgumentException("Display name must contain 1 to 64 characters.");
    if (text == null || text.isBlank()) throw new IllegalArgumentException("Message cannot be empty.");
    if (text.length() > MAX_TEXT_CHARS) throw new IllegalArgumentException("UDP messages are limited to " + MAX_TEXT_CHARS + " characters.");
    String frame = String.join("|", PREFIX, "CHAT", mode.name(), destination, Integer.toString(port), id,
        Long.toString(timestamp), b64(user.trim()), b64(text));
    byte[] bytes = frame.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_PACKET_BYTES) throw new IllegalArgumentException("UDP message exceeds the 8 KB packet limit. Shorten the message.");
    return bytes;
  }

  /** Return null for other rooms, other modes, and malformed/untrusted packets. */
  static Message decode(Mode expectedMode, String expectedDestination, int expectedPort,
                        byte[] bytes, int offset, int length, String senderAddress) {
    if (length < 1 || length > MAX_PACKET_BYTES) return null;
    try {
      String[] fields = new String(bytes, offset, length, StandardCharsets.UTF_8).split("\\|", -1);
      if (fields.length != 9 || !PREFIX.equals(fields[0]) || !"CHAT".equals(fields[1])
          || !expectedMode.name().equals(fields[2])
          || (expectedMode == Mode.MULTICAST && !expectedDestination.equals(fields[3]))
          || expectedPort != Integer.parseInt(fields[4])) return null;
      if (!UUID.fromString(fields[5]).toString().equals(fields[5])) return null;
      long timestamp = Long.parseLong(fields[6]);
      if (timestamp <= 0) return null;
      String user = unb64(fields[7]);
      String text = unb64(fields[8]);
      if (user.isBlank() || user.length() > 64 || text.isBlank() || text.length() > MAX_TEXT_CHARS) return null;
      return new Message(fields[5], user, text, timestamp, senderAddress);
    } catch (IllegalArgumentException error) { return null; }
  }

  private static String b64(String value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String unb64(String value) {
    return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
  }

  private static InetAddress parseIpv4(String value) throws Exception {
    if (value == null || !value.matches("\\d{1,3}(\\.\\d{1,3}){3}")) throw new IllegalArgumentException("Enter a numeric IPv4 destination address.");
    for (String part : value.split("\\.")) if (Integer.parseInt(part) > 255) throw new IllegalArgumentException("Invalid IPv4 destination address.");
    InetAddress address = InetAddress.getByName(value);
    if (!(address instanceof Inet4Address)) throw new IllegalArgumentException("IPv4 is required for this lab.");
    return address;
  }

  private static NetworkInterface chooseInterface(String requested, Mode mode) throws Exception {
    List<NetworkInterface> candidates = interfaces();
    if (requested != null && !requested.isBlank()) {
      for (NetworkInterface nic : candidates) {
        if (requested.equals(nic.getName()) || requested.equals(nic.getDisplayName())) {
          if (mode == Mode.MULTICAST && !nic.supportsMulticast()) throw new IllegalArgumentException("Selected interface does not support multicast.");
          return nic;
        }
      }
      throw new IllegalArgumentException("Selected network interface is unavailable or has no IPv4 address: " + requested);
    }
    // Selecting a UDP route does not transmit a packet. Prefer the OS route
    // rather than lexical names such as eth0/eth1 (which can be virtual NICs).
    try (DatagramSocket route = new DatagramSocket(null)) {
      route.connect(new InetSocketAddress("192.0.2.1", 9));
      NetworkInterface routed = NetworkInterface.getByInetAddress(route.getLocalAddress());
      if (routed != null && routed.isUp() && !routed.isLoopback() && ipv4Address(routed) != null
          && (mode != Mode.MULTICAST || routed.supportsMulticast())) return routed;
    } catch (IOException | SecurityException ignored) { }
    for (NetworkInterface nic : candidates) {
      if (!nic.isLoopback() && (mode != Mode.MULTICAST || nic.supportsMulticast())) return nic;
    }
    for (NetworkInterface nic : candidates) {
      if (mode != Mode.MULTICAST || nic.supportsMulticast()) return nic;
    }
    throw new SocketException("No active IPv4 interface is available for " + mode.name().toLowerCase() + ".");
  }

  private static boolean likelyVirtual(NetworkInterface nic) {
    String name = (nic.getName() + " " + nic.getDisplayName()).toLowerCase(java.util.Locale.ROOT);
    return nic.isVirtual() || name.contains("virtual") || name.contains("vethernet") || name.contains("hyper-v")
        || name.contains("vmware") || name.contains("vbox") || name.contains("vpn") || name.contains("tunnel")
        || name.contains("wireguard") || name.contains("tailscale") || name.contains("tap-") || name.contains("wsl");
  }

  private static Inet4Address ipv4Address(NetworkInterface nic) {
    for (InetAddress address : Collections.list(nic.getInetAddresses())) {
      if (address instanceof Inet4Address ipv4 && !address.isAnyLocalAddress()) return ipv4;
    }
    return null;
  }

  private static boolean isBroadcast(InetAddress address, NetworkInterface nic) {
    if ("255.255.255.255".equals(address.getHostAddress())) return true;
    for (InterfaceAddress binding : nic.getInterfaceAddresses()) if (address.equals(binding.getBroadcast())) return true;
    return false;
  }
}
