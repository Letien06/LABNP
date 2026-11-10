package lab1.chat;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.ImageIcon;
import javax.imageio.ImageIO;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Set;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Modern Swing client for the Lab1 TCP/UDP chat protocol. */
public final class ChatClient extends JFrame {
  private static final Color NAVY = new Color(15, 31, 52);
  private static final Color TEAL = new Color(37, 196, 171);
  private static final Color INK = new Color(33, 45, 60);
  private static final Color MUTED = new Color(125, 139, 157);
  private static final Color CANVAS = new Color(244, 247, 251);
  private static final Color MY_BUBBLE = new Color(31, 116, 194);
  private static final Color OTHER_BUBBLE = Color.WHITE;
  private static final Font UI_FONT = new Font("Dialog", Font.PLAIN, 14);
  private static final Font UI_MEDIUM = new Font("Dialog", Font.BOLD, 14);
  private static final Font UI_SMALL = new Font("Dialog", Font.PLAIN, 12);
  private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
  private static final Set<String> MEDIA = Set.of("txt", "mp3", "mp4", "png", "jpg", "pdf", "wav", "webm");

  private final String host;
  private final int port;
  private final int udpPort;
  private final String multicast;
  private final int broadcastPort;
  private final String broadcastAddress;
  private volatile Socket socket;
  private BufferedReader in;
  private volatile PrintWriter out;
  private volatile long connectionVersion;
  private boolean connecting;
  private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "chat-send"); t.setDaemon(true); return t; });
  private String username = "Guest";
  private JTextField input;
  private JLabel status;
  private CircleLabel statusDot;
  private CircleLabel profileAvatar;
  private JLabel roomMeta;
  private JLabel profileName;
  private JLabel onlineCount;
  private JButton connectButton;
  private JLabel connectionFeedback;
  private JLabel privateTitle;
  private CircleLabel privateAvatar;
  private JButton attachButton, emojiButton, sendButton;
  private boolean identityAvailable;
  private String selectedPeer;
  private String currentScreen = "multicast";
  private List<String> onlineUsers = List.of();
  private final Map<String, List<PrivateMessage>> conversations = new LinkedHashMap<>();
  private final Map<String, Integer> unread = new LinkedHashMap<>();
  private final Map<String, String> drafts = new LinkedHashMap<>();
  private final Set<String> historyRequested = new HashSet<>();
  private final Set<String> seenPrivateFrames = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private record PrivateMessage(String sender, String text, long timestamp, String name,
                                String mime, long size, Path saved, ImageIcon preview) { }
  private JPanel messageList;
  private JScrollPane messageScroll;
  private JPanel onlineList;
  private final CardLayout screenLayout = new CardLayout();
  private JPanel screens;
  private JButton multicastNavigation;
  private JButton broadcastNavigation;
  private UdpChannelPanel multicastScreen;
  private UdpChannelPanel broadcastScreen;

  public ChatClient(String host, int port, int udpPort, String multicast) {
    this(host, port, udpPort, multicast, 5557, "255.255.255.255");
  }

  public ChatClient(String host, int port, int udpPort, String multicast, int broadcastPort, String broadcastAddress) {
    super("Chatter · Lab1 Network Chat");
    this.host = host;
    this.port = port;
    this.udpPort = udpPort;
    this.multicast = multicast;
    this.broadcastPort = broadcastPort;
    this.broadcastAddress = broadcastAddress;
    build();
  }

  private void build() {
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setMinimumSize(new Dimension(1040, 680));
    setSize(new Dimension(1180, 760));
    setLocationByPlatform(true);
    getContentPane().setBackground(CANVAS);

    JPanel frame = new JPanel(new BorderLayout());
    frame.setBackground(CANVAS);
    frame.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
    setContentPane(frame);
    frame.add(buildSidebar(), BorderLayout.WEST);
    screens = new JPanel(screenLayout);
    screens.setOpaque(false);
    screens.add(buildConversation(), "private");
    multicastScreen = new UdpChannelPanel(UdpChatChannel.Mode.MULTICAST, multicast, udpPort, () -> username);
    broadcastScreen = new UdpChannelPanel(UdpChatChannel.Mode.BROADCAST, broadcastAddress, broadcastPort, () -> username);
    screens.add(multicastScreen, "multicast");
    screens.add(broadcastScreen, "broadcast");
    frame.add(screens, BorderLayout.CENTER);
    showScreen("multicast");

    addWindowListener(new WindowAdapter() {
      @Override public void windowClosing(WindowEvent e) {
        closeConnection();
        multicastScreen.close();
        broadcastScreen.close();
        sender.shutdownNow();
      }
    });
  }

  private JPanel buildSidebar() {
    JPanel sidebar = new RoundedPanel(22, NAVY);
    sidebar.setPreferredSize(new Dimension(286, 0));
    sidebar.setBorder(BorderFactory.createEmptyBorder(22, 20, 18, 20));
    sidebar.setLayout(new BorderLayout(0, 18));

    JPanel top = new JPanel();
    top.setOpaque(false);
    top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS));

    JPanel brand = new JPanel(new BorderLayout(10, 0));
    brand.setOpaque(false);
    CircleLabel logo = new CircleLabel("", TEAL, Color.WHITE, 40);
    logo.setIcon(new VectorIcon("chat", Color.WHITE, 22));
    brand.add(logo, BorderLayout.WEST);
    JPanel brandText = new JPanel();
    brandText.setOpaque(false);
    brandText.setLayout(new javax.swing.BoxLayout(brandText, javax.swing.BoxLayout.Y_AXIS));
    brandText.add(label("Chatter", Color.WHITE, new Font("Dialog", Font.BOLD, 21)));
    brandText.add(Box.createVerticalStrut(3));
    brandText.add(label("Cùng trò chuyện, cùng kết nối", new Color(164, 184, 207), new Font("Dialog", Font.PLAIN, 11)));
    brand.add(brandText, BorderLayout.CENTER);
    brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
    top.add(brand);
    top.add(Box.createVerticalStrut(28));

    top.add(label("TIN NHẮN", new Color(141, 165, 190), new Font("Dialog", Font.BOLD, 10)));
    top.add(Box.createVerticalStrut(8));
    JButton room = ChatTheme.button("", NAVY, Color.WHITE);
    room.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    room.setLayout(new BorderLayout(10, 0));
    room.add(new CircleLabel("L", new Color(72, 128, 209), Color.WHITE, 38), BorderLayout.WEST);
    JPanel roomText = new JPanel();
    roomText.setOpaque(false);
    roomText.setLayout(new javax.swing.BoxLayout(roomText, javax.swing.BoxLayout.Y_AXIS));
    roomText.add(label("Phòng chung", Color.WHITE, UI_MEDIUM));
    roomText.add(Box.createVerticalStrut(4));
    roomText.add(label("Multicast · Tham gia / Rời phòng", new Color(168, 196, 224), new Font("Dialog", Font.PLAIN, 10)));
    room.add(roomText, BorderLayout.CENTER);
    room.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
    room.addActionListener(e -> showScreen("multicast"));
    forwardNavigationClicks(room, room);
    room.getAccessibleContext().setAccessibleName("Phòng chung Multicast");
    multicastNavigation = room;
    top.add(room);
    top.add(Box.createVerticalStrut(8));
    broadcastNavigation = channelNavigation("Thông báo LAN", "Gửi đến người đang nghe", "B", new Color(217, 147, 49), "broadcast");
    top.add(broadcastNavigation);
    top.add(Box.createVerticalStrut(22));
    top.add(label("MỌI NGƯỜI · NHẤN ĐỂ CHAT RIÊNG", new Color(141, 165, 190), new Font("Dialog", Font.BOLD, 10)));
    top.add(Box.createVerticalStrut(8));
    onlineList = new JPanel();
    onlineList.setOpaque(false);
    onlineList.setLayout(new javax.swing.BoxLayout(onlineList, javax.swing.BoxLayout.Y_AXIS));
    onlineList.add(label("Kết nối để xem người đang online", new Color(145, 170, 196), UI_SMALL));
    JScrollPane members = new JScrollPane(onlineList);
    members.setBorder(BorderFactory.createEmptyBorder());
    members.setOpaque(false);
    members.getViewport().setOpaque(false);
    members.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    members.setPreferredSize(new Dimension(220, 104));
    ChatTheme.styleScrollPane(members, true);
    top.add(members);
    for (Component item : top.getComponents()) {
      if (item instanceof javax.swing.JComponent component) component.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
    JScrollPane navigation = new JScrollPane(top);
    navigation.setBorder(BorderFactory.createEmptyBorder());
    navigation.setOpaque(false);
    navigation.getViewport().setOpaque(false);
    navigation.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    navigation.getVerticalScrollBar().setUnitIncrement(16);
    navigation.getVerticalScrollBar().setPreferredSize(new Dimension(5, 0));
    ChatTheme.styleScrollPane(navigation, true);
    sidebar.add(navigation, BorderLayout.CENTER);

    JPanel bottom = new JPanel();
    bottom.setOpaque(false);
    bottom.setLayout(new javax.swing.BoxLayout(bottom, javax.swing.BoxLayout.Y_AXIS));
    JPanel profile = new JPanel(new BorderLayout(10, 0));
    profile.setOpaque(false);
    profileAvatar = new CircleLabel("G", new Color(255, 158, 104), Color.WHITE, 40);
    profile.add(profileAvatar, BorderLayout.WEST);
    JPanel profileText = new JPanel();
    profileText.setOpaque(false);
    profileText.setLayout(new javax.swing.BoxLayout(profileText, javax.swing.BoxLayout.Y_AXIS));
    profileName = label("Guest", Color.WHITE, UI_MEDIUM);
    profileText.add(profileName);
    profileText.add(Box.createVerticalStrut(3));
    profileText.add(label("Sẵn sàng trò chuyện", new Color(164, 184, 207), UI_SMALL));
    profile.add(profileText, BorderLayout.CENTER);
    bottom.add(profile);
    bottom.add(Box.createVerticalStrut(14));
    connectButton = primaryButton("Kết nối");
    connectButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
    connectButton.setAlignmentX(Component.LEFT_ALIGNMENT);
    connectButton.addActionListener(e -> toggleConnection());
    bottom.add(connectButton);
    bottom.add(Box.createVerticalStrut(9));
    connectionFeedback = label("Chưa kết nối máy chủ", new Color(145, 170, 196), new Font("Dialog", Font.PLAIN, 10));
    bottom.add(connectionFeedback);
    for (Component item : bottom.getComponents()) {
      if (item instanceof javax.swing.JComponent component) component.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
    sidebar.add(bottom, BorderLayout.SOUTH);
    return sidebar;
  }

  private JButton channelNavigation(String title, String subtitle, String initial, Color accent, String screen) {
    JButton row = ChatTheme.button("", NAVY, Color.WHITE);
    row.setLayout(new BorderLayout(10, 0));
    row.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
    row.add(new CircleLabel(initial, accent, Color.WHITE, 38), BorderLayout.WEST);
    JPanel text = new JPanel();
    text.setOpaque(false);
    text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
    text.add(label(title, Color.WHITE, new Font("Dialog", Font.BOLD, 13)));
    text.add(Box.createVerticalStrut(4));
    text.add(label(subtitle, new Color(168, 196, 224), new Font("Dialog", Font.PLAIN, 10)));
    row.add(text, BorderLayout.CENTER);
    row.addActionListener(e -> showScreen(screen));
    forwardNavigationClicks(row, row);
    row.getAccessibleContext().setAccessibleName(title);
    return row;
  }

  private void forwardNavigationClicks(Container parent, JButton button) {
    for (Component child : parent.getComponents()) {
      child.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      child.addMouseListener(new MouseAdapter() {
        @Override public void mouseClicked(MouseEvent e) {
          if (SwingUtilities.isLeftMouseButton(e)) button.doClick(0);
        }
      });
      if (child instanceof Container nested) forwardNavigationClicks(nested, button);
    }
  }

  private void showScreen(String screen) {
    if (screens == null) return;
    currentScreen = screen;
    screenLayout.show(screens, screen);
    Color active = new Color(31, 61, 94);
    multicastNavigation.setBackground("multicast".equals(screen) ? active : NAVY);
    broadcastNavigation.setBackground("broadcast".equals(screen) ? active : NAVY);
    if (multicastScreen != null) multicastScreen.refreshIdentity();
    if (broadcastScreen != null) broadcastScreen.refreshIdentity();
    if ("private".equals(screen) && selectedPeer != null) {
      unread.remove(selectedPeer);
      renderPrivateConversation();
      updateUsers(onlineUsers);
    }
  }

  private JPanel buildConversation() {
    JPanel conversation = new JPanel(new BorderLayout());
    conversation.setOpaque(false);
    conversation.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
    conversation.add(buildHeader(), BorderLayout.NORTH);
    messageList = new MessageList();
    messageList.setOpaque(false);
    messageList.setBorder(BorderFactory.createEmptyBorder(18, 4, 18, 4));
    messageList.setLayout(new javax.swing.BoxLayout(messageList, javax.swing.BoxLayout.Y_AXIS));
    messageScroll = new JScrollPane(messageList);
    messageScroll.setBorder(BorderFactory.createEmptyBorder());
    messageScroll.setOpaque(false);
    messageScroll.getViewport().setOpaque(false);
    messageScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    messageScroll.getVerticalScrollBar().setUnitIncrement(18);
    messageScroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
    ChatTheme.styleScrollPane(messageScroll, false);
    conversation.add(messageScroll, BorderLayout.CENTER);
    conversation.add(buildComposer(), BorderLayout.SOUTH);
    addMessage("SYSTEM", "Bấm avatar hoặc tên một người đang online để bắt đầu trò chuyện riêng.");
    setPrivateInteractive();
    return conversation;
  }

  private JPanel buildHeader() {
    JPanel header = new JPanel(new BorderLayout(16, 0));
    header.setOpaque(false);
    header.setBorder(BorderFactory.createEmptyBorder(6, 6, 16, 6));
    JPanel identity = new JPanel(new BorderLayout(12, 0));
    identity.setOpaque(false);
    privateAvatar = new CircleLabel("P", new Color(72, 128, 209), Color.WHITE, 48);
    identity.add(privateAvatar, BorderLayout.WEST);
    JPanel text = new JPanel();
    text.setOpaque(false);
    text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
    privateTitle = label("Tin nhắn riêng", INK, new Font("Dialog", Font.BOLD, 22));
    privateTitle.setPreferredSize(new Dimension(300, 30));
    text.add(privateTitle);
    roomMeta = label("Chọn người bạn muốn trò chuyện", MUTED, UI_SMALL);
    text.add(Box.createVerticalStrut(4));
    text.add(roomMeta);
    identity.add(text, BorderLayout.CENTER);
    header.add(identity, BorderLayout.WEST);

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
    right.setOpaque(false);
    JPanel online = new RoundedPanel(16, Color.WHITE);
    online.setBorder(BorderFactory.createEmptyBorder(7, 11, 7, 11));
    online.setLayout(new FlowLayout(FlowLayout.CENTER, 6, 0));
    statusDot = new CircleLabel("", new Color(201, 211, 222), new Color(201, 211, 222), 9);
    online.add(statusDot);
    status = label("Chưa kết nối", MUTED, UI_SMALL);
    online.add(status);
    right.add(online);
    onlineCount = label("0 thành viên", MUTED, UI_SMALL);
    right.add(onlineCount);
    header.add(right, BorderLayout.EAST);
    return header;
  }

  private JPanel buildComposer() {
    JPanel shell = new JPanel(new BorderLayout(0, 8));
    shell.setOpaque(false);
    shell.setBorder(BorderFactory.createEmptyBorder(10, 4, 4, 4));
    JPanel composer = new RoundedPanel(18, Color.WHITE) {
      @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(input != null && input.hasFocus() ? TEAL : new Color(227, 233, 241));
        g.setStroke(new BasicStroke(input != null && input.hasFocus() ? 1.5f : 1f));
        g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 18, 18);
        g.dispose();
      }
    };
    composer.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    composer.setLayout(new BorderLayout(8, 0));
    JButton attach = iconButton("＋", "Đính kèm tệp, ảnh, âm thanh hoặc video");
    attachButton = attach;
    attach.addActionListener(e -> chooseFile());
    composer.add(attach, BorderLayout.WEST);
    input = new JTextField() {
      @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (getText().isEmpty()) {
          Graphics2D g = (Graphics2D) graphics.create();
          g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
          g.setFont(getFont());
          g.setColor(new Color(151, 162, 178));
          g.drawString("Nhập tin nhắn...", getInsets().left, (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent());
          g.dispose();
        }
      }
    };
    input.setFont(UI_FONT);
    input.setForeground(INK);
    input.setBorder(BorderFactory.createEmptyBorder(8, 5, 8, 5));
    input.setOpaque(false);
    input.setToolTipText("Nhập tin nhắn...");
    input.addFocusListener(new FocusAdapter() {
      @Override public void focusGained(FocusEvent e) { composer.repaint(); }
      @Override public void focusLost(FocusEvent e) { composer.repaint(); }
    });
    input.addActionListener(e -> sendText());
    composer.add(input, BorderLayout.CENTER);
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
    actions.setOpaque(false);
    JButton emoji = iconButton("☺", "Chọn biểu cảm");
    emojiButton = emoji;
    emoji.addActionListener(e -> showEmoji(emoji));
    actions.add(emoji);
    JButton send = primaryIconButton("➤", "Gửi tin nhắn");
    sendButton = send;
    send.addActionListener(e -> sendText());
    actions.add(send);
    composer.add(actions, BorderLayout.EAST);
    shell.add(composer, BorderLayout.CENTER);
    JLabel hint = label("Unicast qua TCP · Chỉ hai người nhận tin và tệp · Tối đa 10 MB", MUTED, UI_SMALL);
    hint.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
    shell.add(hint, BorderLayout.SOUTH);
    return shell;
  }

  private void connectDialog() {
    JDialog dialog = new JDialog(this, "Kết nối phòng chat", true);
    dialog.setSize(440, 390);
    dialog.setResizable(false);
    dialog.setLocationRelativeTo(this);
    JPanel body = new JPanel(new BorderLayout());
    body.setBackground(CANVAS);
    body.setBorder(BorderFactory.createEmptyBorder(24, 26, 22, 26));
    JPanel top = new RoundedPanel(16, NAVY);
    top.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
    top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS));
    top.add(label("Tham gia trò chuyện", Color.WHITE, new Font("Dialog", Font.BOLD, 20)));
    top.add(Box.createVerticalStrut(5));
    top.add(label("Chọn tên hiển thị và địa chỉ máy chủ.", new Color(175, 197, 221), UI_SMALL));
    body.add(top, BorderLayout.NORTH);
    JPanel form = new JPanel(new GridLayout(0, 1, 0, 6));
    form.setOpaque(false);
    form.setBorder(BorderFactory.createEmptyBorder(18, 2, 12, 2));
    JTextField name = field(username);
    JTextField server = field(host);
    form.add(label("TÊN HIỂN THỊ", MUTED, new Font("Dialog", Font.BOLD, 10)));
    form.add(name);
    form.add(label("ĐỊA CHỈ MÁY CHỦ", MUTED, new Font("Dialog", Font.BOLD, 10)));
    form.add(server);
    body.add(form, BorderLayout.CENTER);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    buttons.setOpaque(false);
    JButton cancel = secondaryButton("Hủy");
    cancel.addActionListener(e -> dialog.dispose());
    JButton join = primaryButton("Kết nối");
    join.addActionListener(e -> {
      username = name.getText().trim().replace("|", "");
      if (username.isBlank()) username = "Guest";
      String selectedHost = server.getText().trim();
      if (selectedHost.isBlank()) selectedHost = host;
      profileName.setText(username);
      profileAvatar.setText(initials(username));
      dialog.dispose();
      connect(selectedHost);
    });
    buttons.add(cancel);
    buttons.add(join);
    body.add(buttons, BorderLayout.SOUTH);
    dialog.setContentPane(body);
    dialog.getRootPane().setDefaultButton(join);
    dialog.setVisible(true);
  }

  private JTextField field(String value) {
    JTextField field = new JTextField(value);
    field.setFont(UI_FONT);
    field.setForeground(INK);
    field.setBackground(Color.WHITE);
    field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(221, 228, 237)), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
    return field;
  }

  private void toggleConnection() {
    if (connecting || (socket != null && !socket.isClosed())) closeConnection();
    else connectDialog();
  }

  private void connect(String serverHost) {
    long version = ++connectionVersion;
    conversations.clear(); unread.clear(); drafts.clear(); historyRequested.clear(); seenPrivateFrames.clear();
    selectedPeer = null;
    input.setText("");
    privateTitle.setText("Tin nhắn riêng"); privateAvatar.setText("P");
    renderPrivateConversation();
    connecting = true;
    setConnectionState("Đang kết nối…", new Color(236, 165, 69), false);
    connectButton.setText("Hủy kết nối");
    Thread readerThread = new Thread(() -> {
      Socket active = new Socket();
      boolean connected = false;
      String failure = null;
      try {
        if (connectionVersion != version) return;
        socket = active;
        active.connect(new InetSocketAddress(serverHost, port), 5000);
        BufferedReader reader = new BufferedReader(new InputStreamReader(active.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(active.getOutputStream(), StandardCharsets.UTF_8), true);
        if (connectionVersion != version) return;
        in = reader;
        out = writer;
        writer.println(Protocol.join("JOIN", Protocol.enc(username)));
        connected = true;
        String line;
        while (connectionVersion == version && (line = reader.readLine()) != null) handle(line, version);
      } catch (Exception e) {
        failure = e.getMessage();
      } finally {
        try { active.close(); } catch (Exception ignored) { }
        final boolean wasConnected = connected;
        final String error = failure;
        SwingUtilities.invokeLater(() -> {
          if (connectionVersion != version) return;
          socket = null;
          out = null;
          in = null;
          connecting = false;
          resetDisconnected();
          if (error != null) reportError((wasConnected ? "Kết nối bị gián đoạn: " : "Không thể kết nối: ") + error);
        });
      }
    }, "tcp-reader");
    readerThread.setDaemon(true);
    readerThread.start();
  }

  private void closeConnection() {
    ++connectionVersion;
    try { if (socket != null) socket.close(); } catch (Exception ignored) { }
    socket = null;
    out = null;
    in = null;
    connecting = false;
    resetDisconnected();
  }

  private void resetDisconnected() {
    setConnectionState("Chưa kết nối", MUTED, false);
    if (multicastScreen != null) multicastScreen.setIdentityAvailable(false);
    if (broadcastScreen != null) broadcastScreen.setIdentityAvailable(false);
    updateUsers(List.of());
  }

  private void setConnectionState(String text, Color accent, boolean connected) {
    identityAvailable = connected;
    status.setText(text);
    status.setForeground(connected ? new Color(42, 150, 128) : accent);
    statusDot.setFill(accent);
    connectButton.setText(connected ? "Ngắt kết nối" : "Kết nối");
    connectionFeedback.setText(text);
    connectionFeedback.setForeground(connected ? TEAL : new Color(164, 184, 207));
    connectionFeedback.setToolTipText(text);
    setPrivateInteractive();
  }

  private void updateUsers(List<String> users) {
    onlineUsers = List.copyOf(users);
    onlineCount.setText(users.size() + " thành viên online");
    onlineList.removeAll();
    if (users.isEmpty()) onlineList.add(label("Kết nối để xem người đang online", new Color(145, 170, 196), UI_SMALL));
    List<String> contacts = new ArrayList<>(users);
    for (String peer : conversations.keySet()) if (contacts.stream().noneMatch(name -> name.equalsIgnoreCase(peer))) contacts.add(peer);
    for (String user : contacts) {
      boolean mine = username.equalsIgnoreCase(user), online = users.stream().anyMatch(name -> name.equalsIgnoreCase(user));
      String peerKey = conversationPeer(user);
      JButton row = ChatTheme.button("", user.equalsIgnoreCase(selectedPeer) && "private".equals(currentScreen) ? new Color(31,61,94) : NAVY, Color.WHITE);
      row.setLayout(new BorderLayout(8, 0));
      row.setBorder(BorderFactory.createEmptyBorder(7, 6, 7, 6));
      row.add(new CircleLabel(initials(user), new Color(72, 112, 157), Color.WHITE, 31), BorderLayout.WEST);
      JLabel name = label(user + (mine ? " (bạn)" : ""), new Color(214, 230, 246), UI_SMALL);
      name.setToolTipText(user);
      row.add(name, BorderLayout.CENTER);
      int count = unread.getOrDefault(peerKey, 0);
      JPanel indicatorSlot = new JPanel(new java.awt.GridBagLayout());
      indicatorSlot.setOpaque(false);
      indicatorSlot.setPreferredSize(new Dimension(23, 31));
      CircleLabel indicator = count > 0
          ? new CircleLabel(count > 9 ? "9+" : Integer.toString(count), TEAL, NAVY, 23)
          : new CircleLabel("", online ? TEAL : MUTED, online ? TEAL : MUTED, 7);
      indicatorSlot.add(indicator);
      row.add(indicatorSlot, BorderLayout.EAST);
      row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
      row.setToolTipText(mine ? "Đây là bạn" : "Nhắn tin riêng với " + user + (online ? "" : " · Ngoại tuyến"));
      row.getAccessibleContext().setAccessibleName("Nhắn tin riêng với " + user);
      if (!mine) { row.addActionListener(e -> openPrivate(user)); forwardNavigationClicks(row, row); }
      onlineList.add(row);
    }
    onlineList.revalidate();
    onlineList.repaint();
    setPrivateInteractive();
  }

  private void openPrivate(String peer) {
    if (selectedPeer != null) drafts.put(selectedPeer, input.getText());
    selectedPeer = conversationPeer(peer);
    input.setText(drafts.getOrDefault(selectedPeer, ""));
    conversations.computeIfAbsent(selectedPeer, key -> new ArrayList<>());
    privateTitle.setText(peer);
    privateTitle.setToolTipText(peer);
    privateAvatar.setText(initials(peer));
    showScreen("private");
    String recipient = selectedPeer;
    long version = connectionVersion;
    if (identityAvailable && historyRequested.add(recipient)) {
      PrintWriter writer = out;
      if (writer != null) sender.execute(() -> { writer.println(Protocol.join("HISTORY", Protocol.enc(recipient))); if (writer.checkError()) SwingUtilities.invokeLater(() -> { if (version == connectionVersion) { historyRequested.remove(recipient); reportError("Không tải được lịch sử. Hãy kết nối lại."); } }); });
      else historyRequested.remove(recipient);
    }
    input.requestFocusInWindow();
  }

  private void setPrivateInteractive() {
    boolean enabled = identityAvailable && isPeerOnline(selectedPeer);
    if (input != null) input.setEnabled(enabled);
    if (attachButton != null) attachButton.setEnabled(enabled);
    if (emojiButton != null) emojiButton.setEnabled(enabled);
    if (sendButton != null) sendButton.setEnabled(enabled);
    if (roomMeta != null) roomMeta.setText(selectedPeer == null ? "Chọn một người ở danh sách bên trái" : !identityAvailable ? "Đã ngắt kết nối · Kết nối lại để nhắn tin" : enabled ? "● Đang online · Trò chuyện riêng qua TCP Unicast" : "Ngoại tuyến · Có thể xem lại cuộc trò chuyện");
  }

  private String conversationPeer(String name) {
    for (String existing : conversations.keySet()) if (existing.equalsIgnoreCase(name)) return existing;
    return name;
  }

  private boolean isPeerOnline(String name) {
    return name != null && onlineUsers.stream().anyMatch(online -> online.equalsIgnoreCase(name));
  }

  private void renderPrivateConversation() {
    if (messageList == null) return;
    messageList.removeAll();
    List<PrivateMessage> messages = conversations.getOrDefault(selectedPeer, List.of());
    if (messages.isEmpty()) addMessage("SYSTEM", selectedPeer == null ? "Bấm avatar hoặc tên một người để mở tin nhắn riêng." : "Gửi lời chào đến " + selectedPeer + ". Tin nhắn chỉ gửi đến hai bạn.");
    else for (PrivateMessage m : messages) {
      if (m.saved() == null) addMessage(m.sender(), m.text(), m.timestamp());
      else addFileMessage(m.sender(), m.name(), m.mime(), m.size(), m.saved(), m.preview(), m.timestamp());
    }
    refreshMessages();
  }

  private void reportError(String message) {
    connectionFeedback.setText(message);
    connectionFeedback.setToolTipText(message);
    connectionFeedback.setForeground(new Color(255, 160, 163));
    addMessage("ERROR", message);
  }

  private void handle(String line, long version) {
    String[] p = Protocol.split(line);
    try {
      if (p.length == 0) return;
      if ("USERS".equals(p[0])) {
        List<String> users = Arrays.stream(p).skip(1).filter(value -> !value.isBlank()).map(Protocol::dec).toList();
        SwingUtilities.invokeLater(() -> { if (connectionVersion == version) updateUsers(users); });
      }
      else if ("WELCOME".equals(p[0]) && p.length >= 2) {
        String accepted = Protocol.dec(p[1]);
        SwingUtilities.invokeLater(() -> {
          if (connectionVersion != version) return;
          username = accepted; profileName.setText(accepted); profileAvatar.setText(initials(accepted));
          setTitle("Chatter · " + accepted + " · Lab1");
          connecting = false; setConnectionState("Đã kết nối · " + accepted, TEAL, true);
          multicastScreen.refreshIdentity(); broadcastScreen.refreshIdentity();
          multicastScreen.setIdentityAvailable(true); broadcastScreen.setIdentityAvailable(true);
        });
      }
      else if (("PRIVATE".equals(p[0]) || "PRIVATE_HISTORY".equals(p[0])) && p.length >= 6) {
        if (connectionVersion != version) return;
        boolean history = "PRIVATE_HISTORY".equals(p[0]);
        String[] identity = Arrays.copyOf(p, Math.min(p.length, 8)); identity[0] = "PRIVATE";
        String key = String.join("|", identity);
        if (seenPrivateFrames.contains(key)) return;
        String who = Protocol.dec(p[1]);
        String recipient = Protocol.dec(p[2]);
        if (!username.equalsIgnoreCase(who) && !username.equalsIgnoreCase(recipient)) return;
        String rawPeer = username.equalsIgnoreCase(who) ? recipient : who;
        long timestamp = Long.parseLong(p[3]);
        PrivateMessage message;
        if ("MSG".equals(p[4])) message = new PrivateMessage(who, Protocol.dec(p[5]), timestamp, null, null, 0, null, null);
        else if ("FILE".equals(p[4]) && p.length >= 9) {
          String name = Protocol.dec(p[5]);
          String mime = Protocol.dec(p[6]);
          byte[] bytes = Base64.getDecoder().decode(p[8]);
          if (bytes.length > 10 * 1024 * 1024 || bytes.length != Long.parseLong(p[7])) throw new IllegalArgumentException("Tệp nhận không hợp lệ");
          Path downloads = Path.of("downloads");
          Files.createDirectories(downloads);
          String savedName = timestamp + "_" + java.util.UUID.randomUUID().toString().substring(0, 8) + "_" + Path.of(name).getFileName();
          Path saved = downloads.resolve(savedName).toAbsolutePath();
          Files.write(saved, bytes);
          message = new PrivateMessage(who, null, timestamp, name, mime, bytes.length, saved, imagePreview(bytes, mime));
        } else return;
        seenPrivateFrames.add(key);
        SwingUtilities.invokeLater(() -> {
          if (connectionVersion != version) return;
          String peer = conversationPeer(rawPeer);
          List<PrivateMessage> messages = conversations.computeIfAbsent(peer, ignored -> new ArrayList<>());
          messages.add(message); messages.sort(java.util.Comparator.comparingLong(PrivateMessage::timestamp));
          if (peer.equals(selectedPeer) && "private".equals(currentScreen)) renderPrivateConversation();
          else if (!history && !username.equalsIgnoreCase(who)) unread.merge(peer, 1, Integer::sum);
          updateUsers(onlineUsers);
        });
      } else if ("ERROR".equals(p[0]) && p.length >= 2) {
        String error = Protocol.dec(p[1]);
        SwingUtilities.invokeLater(() -> { if (connectionVersion == version) { reportError(error); if (connecting) javax.swing.JOptionPane.showMessageDialog(this, error, "Không thể kết nối", javax.swing.JOptionPane.WARNING_MESSAGE); } });
      }
    } catch (Exception e) {
      SwingUtilities.invokeLater(() -> { if (connectionVersion == version) reportError("Không đọc được dữ liệu: " + e.getMessage()); });
    }
  }

  private void append(String who, String text) {
    append(who, text, System.currentTimeMillis());
  }

  private void append(String who, String text, long timestamp) {
    SwingUtilities.invokeLater(() -> addMessage(who, text, timestamp));
  }

  private void addMessage(String who, String text) {
    addMessage(who, text, System.currentTimeMillis());
  }

  private void addMessage(String who, String text, long timestamp) {
    if (messageList == null) return;
    if ("SYSTEM".equals(who) || "ERROR".equals(who) || "UDP".equals(who)) {
      Color bg = "ERROR".equals(who) ? new Color(255, 235, 237) : "UDP".equals(who) ? new Color(232, 249, 246) : new Color(232, 237, 244);
      Color fg = "ERROR".equals(who) ? new Color(180, 66, 83) : "UDP".equals(who) ? new Color(34, 130, 115) : MUTED;
      messageList.add(new MessageRow(who, text, false, bg, fg, true, null, timestamp));
    } else {
      boolean mine = "ME".equals(who) || username.equalsIgnoreCase(who);
      messageList.add(new MessageRow(who, text, mine, mine ? MY_BUBBLE : OTHER_BUBBLE, mine ? Color.WHITE : INK, false, null, timestamp));
    }
    refreshMessages();
  }

  private void addFileMessage(String who, String name, String mime, long size, Path saved, ImageIcon preview, long timestamp) {
    boolean mine = username.equalsIgnoreCase(who);
    JPanel details = new JPanel(new BorderLayout(0, 9));
    details.setOpaque(false);
    if (preview != null) {
      JLabel picture = new JLabel(preview);
      picture.setHorizontalAlignment(SwingConstants.LEFT);
      details.add(picture, BorderLayout.NORTH);
    }
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    buttons.setOpaque(false);
    JButton open = secondaryButton("Mở tệp");
    open.addActionListener(e -> openPath(saved));
    JButton folder = secondaryButton("Mở thư mục");
    folder.addActionListener(e -> openPath(saved.getParent()));
    buttons.add(open);
    buttons.add(folder);
    details.add(buttons, BorderLayout.SOUTH);
    String description = name + "\n" + humanSize(size) + " · " + mime;
    messageList.add(new MessageRow(who, description, mine, mine ? MY_BUBBLE : OTHER_BUBBLE, mine ? Color.WHITE : INK, false, details, timestamp));
    refreshMessages();
  }

  private void refreshMessages() {
    messageList.revalidate();
    messageList.repaint();
    SwingUtilities.invokeLater(() -> messageScroll.getVerticalScrollBar().setValue(messageScroll.getVerticalScrollBar().getMaximum()));
  }

  private ImageIcon imagePreview(byte[] bytes, String mime) {
    if (!mime.startsWith("image/")) return null;
    try {
      java.awt.image.BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null) return null;
      double scale = Math.min(1d, Math.min(280d / image.getWidth(), 180d / image.getHeight()));
      return new ImageIcon(image.getScaledInstance(Math.max(1, (int) (image.getWidth() * scale)), Math.max(1, (int) (image.getHeight() * scale)), Image.SCALE_SMOOTH));
    } catch (Exception ignored) { return null; }
  }

  private String humanSize(long size) {
    if (size < 1024) return size + " B";
    if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024d);
    return String.format("%.1f MB", size / (1024d * 1024d));
  }

  private void openPath(Path path) {
    sender.execute(() -> {
      try {
        if (!Desktop.isDesktopSupported()) throw new IllegalStateException("No desktop file handler is available.");
        Desktop.getDesktop().open(path.toFile());
      } catch (Exception e) { append("ERROR", "Cannot open file: " + e.getMessage()); }
    });
  }

  private void sendText() {
    PrintWriter writer = out;
    String recipient = selectedPeer;
    if (writer == null || !identityAvailable || !isPeerOnline(recipient)) return;
    long version = connectionVersion;
    String text = input.getText().trim();
    if (text.isBlank()) return;
    if (text.length() > 4000) { append("SYSTEM", "Tin nhắn tối đa 4.000 ký tự."); return; }
    sender.execute(() -> {
      writer.println(Protocol.join("DM", Protocol.enc(recipient), Protocol.enc(text)));
      if (writer.checkError()) reportSendError(recipient, version, "Chưa gửi được tin nhắn. Hãy kết nối lại máy chủ.");
    });
    input.setText("");
    drafts.remove(recipient);
    input.requestFocusInWindow();
  }

  private void chooseFile() {
    PrintWriter writer = out;
    String recipient = selectedPeer;
    if (writer == null || !identityAvailable || !isPeerOnline(recipient)) return;
    long version = connectionVersion;
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Gửi tệp riêng cho " + recipient);
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
    File file = chooser.getSelectedFile();
    String name = file.getName();
    String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
    if (!MEDIA.contains(ext)) { append("SYSTEM", "Hỗ trợ: .txt, .mp3, .mp4, .png, .jpg, .pdf, .wav, .webm"); return; }
    if (file.length() > 10 * 1024 * 1024) { append("SYSTEM", "Tệp đính kèm tối đa 10 MB."); return; }
    append("SYSTEM", "Đang gửi " + name + "…");
    sender.execute(() -> {
      try {
        byte[] bytes = Files.readAllBytes(file.toPath());
        String mime = Files.probeContentType(file.toPath());
        if (mime == null) mime = "application/octet-stream";
        writer.println(Protocol.join("DMFILE", Protocol.enc(recipient), Protocol.enc(name), Protocol.enc(mime), Base64.getEncoder().encodeToString(bytes)));
        if (writer.checkError()) throw new IllegalStateException("Connection interrupted. The file was not confirmed.");
      } catch (Exception e) { reportSendError(recipient, version, "Gửi tệp thất bại: " + e.getMessage()); }
    });
  }

  private void reportSendError(String peer, long version, String message) {
    SwingUtilities.invokeLater(() -> {
      if (connectionVersion != version) return;
      String detail = peer + ": " + message;
      connectionFeedback.setText(detail); connectionFeedback.setToolTipText(detail);
      connectionFeedback.setForeground(new Color(255, 160, 163));
      if (peer.equalsIgnoreCase(selectedPeer) && "private".equals(currentScreen)) addMessage("ERROR", message);
    });
  }

  private void showEmoji(Component anchor) {
    JPopupMenu popup = new JPopupMenu();
    popup.setBorder(BorderFactory.createLineBorder(new Color(216, 225, 236)));
    JPanel grid = new JPanel(new GridLayout(3, 5, 3, 3));
    grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    grid.setBackground(Color.WHITE);
    String[] emojis = {"😀", "😂", "😍", "👍", "🎉", "❤️", "🔥", "😎", "🙏", "✨", "🚀", "✅", "👏", "💙", "🙌"};
    for (String emoji : emojis) {
      JButton button = new JButton(emoji);
      button.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
      button.setMargin(new Insets(4, 4, 4, 4));
      button.setBorderPainted(false);
      button.setFocusPainted(false);
      button.setContentAreaFilled(false);
      button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      button.addActionListener(e -> { input.setText(input.getText() + emoji); input.requestFocusInWindow(); popup.setVisible(false); });
      grid.add(button);
    }
    popup.add(grid);
    popup.show(anchor, -148, -grid.getPreferredSize().height - 14);
  }

  private JButton iconButton(String text, String tip) {
    JButton button = new JButton();
    String type = "＋".equals(text) ? "attach" : "☺".equals(text) ? "smile" : "send";
    button.setIcon(new VectorIcon(type, MUTED, 22));
    button.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 22));
    button.setForeground(MUTED);
    button.setToolTipText(tip);
    button.setBorderPainted(false);
    button.setContentAreaFilled(false);
    button.setFocusPainted(false);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.setMargin(new Insets(4, 6, 4, 6));
    return button;
  }

  private JButton primaryIconButton(String text, String tip) {
    JButton button = ChatTheme.button("", MY_BUBBLE, Color.WHITE);
    button.setToolTipText(tip);
    button.getAccessibleContext().setAccessibleName(tip);
    button.setFont(new Font("Segoe UI Symbol", Font.BOLD, 20));
    button.setForeground(Color.WHITE);
    button.setIcon(new VectorIcon("send", Color.WHITE, 21));
    button.setOpaque(false);
    button.setBackground(MY_BUBBLE);
    button.setBorder(BorderFactory.createEmptyBorder(7, 13, 7, 13));
    return button;
  }

  private JButton primaryButton(String text) {
    JButton button = ChatTheme.button(text, TEAL, Color.WHITE);
    button.setFont(UI_MEDIUM);
    button.setForeground(Color.WHITE);
    button.setBackground(TEAL);
    button.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
    button.setFocusPainted(false);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return button;
  }

  private JButton secondaryButton(String text) {
    JButton button = ChatTheme.button(text, Color.WHITE, INK);
    button.setFont(UI_MEDIUM);
    button.setForeground(INK);
    button.setBackground(Color.WHITE);
    button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(217, 226, 237)), BorderFactory.createEmptyBorder(9, 14, 9, 14)));
    button.setFocusPainted(false);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return button;
  }

  private JLabel label(String text, Color color, Font font) {
    return ChatTheme.label(text, font, color);
  }

  private String initials(String value) {
    if (value == null || value.isBlank()) return "?";
    String[] words = value.trim().split("\\s+");
    if (words.length == 1) return words[0].substring(0, 1).toUpperCase();
    return (words[0].substring(0, 1) + words[words.length - 1].substring(0, 1)).toUpperCase();
  }

  public static void main(String[] args) {
    String host = get(args, "--host", "127.0.0.1");
    int port = ival(args, "--port", 5555);
    int udp = ival(args, "--udp-port", 5556);
    String group = get(args, "--multicast", "239.255.42.99");
    int lanPort = ival(args, "--broadcast-port", 5557);
    String broadcast = get(args, "--broadcast", "255.255.255.255");
    SwingUtilities.invokeLater(() -> new ChatClient(host, port, udp, group, lanPort, broadcast).setVisible(true));
  }

  static String get(String[] args, String key, String fallback) {
    for (int i = 0; i < args.length - 1; i++) if (key.equals(args[i])) return args[i + 1];
    return fallback;
  }

  static int ival(String[] args, String key, int fallback) {
    try { return Integer.parseInt(get(args, key, String.valueOf(fallback))); }
    catch (Exception ignored) { return fallback; }
  }

  /** Rows measure wrapped text against the viewport; no message is clipped. */
  private final class MessageRow extends JPanel {
    private final RoundedPanel bubble;
    private final JTextArea body;
    private final JLabel meta;
    private final CircleLabel avatar;
    private final JPanel extra;
    private final boolean mine;
    private final boolean event;

    MessageRow(String who, String text, boolean mine, Color background, Color foreground, boolean event, JPanel extra, long timestamp) {
      this.mine = mine;
      this.event = event;
      this.extra = extra;
      setOpaque(false);
      setLayout(null);
      bubble = new RoundedPanel(event ? 16 : 19, background);
      bubble.setLayout(null);
      add(bubble);
      body = new JTextArea(text);
      body.setFont(event ? UI_SMALL : UI_FONT);
      body.setForeground(foreground);
      body.setOpaque(false);
      body.setEditable(false);
      body.setLineWrap(true);
      body.setWrapStyleWord(true);
      body.setBorder(null);
      body.setMargin(new Insets(0, 0, 0, 0));
      bubble.add(body);
      if (!event) {
        String time = CLOCK.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()));
        meta = label((mine ? "Bạn" : who) + "  ·  " + time, mine ? new Color(201, 230, 255) : MUTED, new Font("Dialog", Font.BOLD, 11));
        bubble.add(meta);
      } else meta = null;
      if (!event && !mine) {
        avatar = new CircleLabel(initials(who), new Color(88, 143, 215), Color.WHITE, 32);
        add(avatar);
      } else avatar = null;
      if (extra != null) bubble.add(extra);
    }

    private int viewportWidth() {
      int width = messageScroll == null ? 760 : messageScroll.getViewport().getWidth();
      return Math.max(300, width - 12);
    }

    private int bubbleWidth(int available) {
      int max = Math.max(180, Math.min(event ? 640 : 540, (int) (available * (event ? .92 : .79))));
      FontMetrics fm = body.getFontMetrics(body.getFont());
      int longest = 1;
      for (String line : body.getText().split("\\R", -1)) longest = Math.max(longest, fm.stringWidth(line));
      int min = extra != null ? 330 : event ? 90 : 180;
      return Math.min(max, Math.max(Math.min(min, max), longest + 28));
    }

    private int bodyHeight(int width) {
      body.setSize(Math.max(1, width - 28), Integer.MAX_VALUE);
      return Math.max(event ? 17 : 21, body.getPreferredSize().height);
    }

    private int extraHeight() { return extra == null ? 0 : extra.getPreferredSize().height + 10; }

    @Override public Dimension getPreferredSize() {
      int width = viewportWidth();
      int bubbleWidth = bubbleWidth(width);
      int height = bodyHeight(bubbleWidth) + (event ? 16 : 45) + extraHeight();
      return new Dimension(width, height + 12);
    }

    @Override public Dimension getMaximumSize() {
      Dimension size = getPreferredSize();
      return new Dimension(Integer.MAX_VALUE, size.height);
    }

    @Override public Dimension getMinimumSize() { return getPreferredSize(); }

    @Override public void doLayout() {
      int width = getWidth();
      int bw = bubbleWidth(width);
      int textHeight = bodyHeight(bw);
      int bh = textHeight + (event ? 16 : 45) + extraHeight();
      int x = event ? (width - bw) / 2 : mine ? width - bw - 4 : 45;
      bubble.setBounds(Math.max(2, x), 6, bw, bh);
      if (avatar != null) avatar.setBounds(4, 10, 32, 32);
      if (meta != null) meta.setBounds(14, 8, bw - 28, 17);
      body.setBounds(14, event ? 8 : 29, bw - 28, textHeight);
      if (extra != null) {
        extra.setBounds(14, 29 + textHeight + 9, bw - 28, extraHeight() - 10);
        extra.doLayout();
      }
      bubble.doLayout();
    }
  }

  private static final class MessageList extends JPanel implements Scrollable {
    @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(760, 450); }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 18; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(18, visible.height - 30); }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
  }

  /** Resolution-independent toolbar artwork, so no system symbol font is required. */
  private static final class VectorIcon implements javax.swing.Icon {
    private final String type;
    private final Color color;
    private final int size;
    VectorIcon(String type, Color color, int size) { this.type = type; this.color = color; this.size = size; }
    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }
    @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
      Graphics2D g = (Graphics2D) graphics.create();
      g.translate(x, y);
      g.scale(size / 24d, size / 24d);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(color);
      g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      if ("chat".equals(type)) {
        g.drawRoundRect(3, 3, 18, 14, 5, 5);
        g.drawLine(7, 17, 7, 21);
        g.drawLine(7, 21, 12, 17);
        g.fillOval(7, 9, 2, 2); g.fillOval(11, 9, 2, 2); g.fillOval(15, 9, 2, 2);
      } else if ("smile".equals(type)) {
        g.drawOval(3, 3, 18, 18);
        g.fillOval(7, 8, 2, 2); g.fillOval(15, 8, 2, 2);
        g.drawArc(7, 9, 10, 8, 190, 160);
      } else if ("attach".equals(type)) {
        java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
        p.moveTo(9, 15); p.lineTo(15, 9); p.curveTo(18, 6, 21, 9, 18, 12);
        p.lineTo(10, 20); p.curveTo(5, 25, -1, 18, 4, 13); p.lineTo(13, 4);
        p.curveTo(20, -3, 28, 7, 21, 14); p.lineTo(14, 21);
        g.scale(.82, .82); g.translate(2, 1); g.draw(p);
      } else {
        java.awt.geom.Path2D plane = new java.awt.geom.Path2D.Double();
        plane.moveTo(3, 4); plane.lineTo(21, 12); plane.lineTo(3, 20); plane.lineTo(6, 13); plane.lineTo(15, 12); plane.lineTo(6, 11); plane.closePath();
        g.fill(plane);
      }
      g.dispose();
    }
  }

  private static class RoundedPanel extends JPanel {
    private final int radius;
    private final Color fill;
    RoundedPanel(int radius, Color fill) { this.radius = radius; this.fill = fill; setOpaque(false); }
    @Override protected void paintComponent(java.awt.Graphics graphics) {
      java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(fill);
      g.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
      g.dispose();
      super.paintComponent(graphics);
    }
  }

  private static final class CircleLabel extends JLabel {
    private Color fill;
    private final int diameter;
    CircleLabel(String text, Color fill, Color foreground, int diameter) {
      super(text, CENTER);
      this.fill = fill;
      this.diameter = diameter;
      setForeground(foreground);
      setFont(new Font("Dialog", Font.BOLD, Math.max(10, diameter / 3)));
      setPreferredSize(new Dimension(diameter, diameter));
      setMinimumSize(new Dimension(diameter, diameter));
      setMaximumSize(new Dimension(diameter, diameter));
      setOpaque(false);
    }
    void setFill(Color color) { fill = color; repaint(); }
    @Override protected void paintComponent(java.awt.Graphics graphics) {
      java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(fill);
      g.fillOval((getWidth() - diameter) / 2, (getHeight() - diameter) / 2, diameter, diameter);
      g.dispose();
      super.paintComponent(graphics);
    }
  }
}
