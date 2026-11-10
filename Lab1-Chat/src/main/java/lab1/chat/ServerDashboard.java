package lab1.chat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/** Modern monitoring window for the chat server. The networking loop remains in ChatServer. */
public final class ServerDashboard extends JFrame {
  private static final Color BG = new Color(12, 18, 31);
  private static final Color PANEL = new Color(21, 29, 47);
  private static final Color PANEL_2 = new Color(27, 37, 60);
  private static final Color TEXT = new Color(238, 243, 252);
  private static final Color MUTED = new Color(150, 165, 190);
  private static final Color ACCENT = new Color(61, 211, 173);
  private static final Color BLUE = new Color(105, 151, 255);

  private final ChatServer server;
  private final JLabel state = new JLabel("  STARTING  ");
  private final JLabel clientCount = new JLabel("0");
  private final JLabel tcpValue = new JLabel("—");
  private final JLabel udpValue = new JLabel("—");
  private final DefaultListModel<String> clientsModel = new DefaultListModel<>();
  private final DefaultListModel<String> eventsModel = new DefaultListModel<>();
  private final JList<String> clientsList = new JList<>(clientsModel);
  private final JList<String> eventsList = new JList<>(eventsModel);
  private final Timer refreshTimer;
  private List<String> displayedEvents = List.of();
  private List<ChatServer.ClientInfo> displayedClients = List.of();
  private boolean firstRefresh = true;

  public ServerDashboard(ChatServer server, Path dataRoot) {
    super("Lab1 Chat · Server Console");
    this.server = server;
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    setMinimumSize(new Dimension(980, 650));
    setSize(1120, 720);
    setLocationByPlatform(true);
    getContentPane().setBackground(BG);
    setIconImage(createIcon());
    setContentPane(buildContent(dataRoot));
    addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { shutdown(); } });
    refreshTimer = new Timer(450, e -> refresh());
    refreshTimer.start();
  }

  private JPanel buildContent(Path dataRoot) {
    JPanel root = new JPanel(new BorderLayout(0, 18));
    root.setBorder(new EmptyBorder(24, 28, 20, 28));
    root.setBackground(BG);
    root.add(header(), BorderLayout.NORTH);
    root.add(center(), BorderLayout.CENTER);
    JPanel footer = new JPanel(new BorderLayout()); footer.setOpaque(false);
    JLabel data = label("Data  ·  " + dataRoot.toAbsolutePath().normalize(), 12, MUTED);
    footer.add(data, BorderLayout.WEST);
    JButton copy = button("Sao chép địa chỉ", PANEL_2, TEXT);
    copy.addActionListener(e -> { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection("127.0.0.1:" + server.port()), null); copy.setText("Đã sao chép"); new Timer(1200, x -> copy.setText("Sao chép địa chỉ")) {{ setRepeats(false); }}.start(); });
    copy.setToolTipText("Địa chỉ kết nối trên cùng máy. Khi chạy LAN, dùng IP của máy chủ.");
    JButton stop = button("Dừng máy chủ", new Color(118, 48, 65), TEXT);
    stop.addActionListener(e -> { shutdown(); });
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); actions.setOpaque(false); actions.add(copy); actions.add(stop); footer.add(actions, BorderLayout.EAST);
    root.add(footer, BorderLayout.SOUTH);
    return root;
  }

  private JPanel header() {
    JPanel header = new JPanel(new BorderLayout(18, 0)); header.setOpaque(false);
    JPanel title = new JPanel(); title.setOpaque(false); title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));
    JLabel h = label("Chatter Server", 28, TEXT); h.setFont(new Font("Dialog", Font.BOLD, 28));
    JLabel sub = label("Lab 1 · Bảng điều khiển máy chủ", 13, MUTED); sub.setBorder(new EmptyBorder(6, 1, 0, 0));
    title.add(h); title.add(sub); header.add(title, BorderLayout.WEST);
    state.setOpaque(true); state.setForeground(new Color(7, 38, 32)); state.setBackground(ACCENT); state.setFont(new Font("Dialog", Font.BOLD, 11)); state.setBorder(new EmptyBorder(8, 12, 8, 12));
    JPanel badge = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 10)); badge.setOpaque(false); badge.add(state); header.add(badge, BorderLayout.EAST);
    return header;
  }

  private JComponent center() {
    JPanel wrapper = new JPanel(new BorderLayout(0, 16)); wrapper.setOpaque(false);
    JPanel metrics = new JPanel(new GridLayout(1, 3, 12, 0)); metrics.setOpaque(false);
    metrics.add(metric("THÀNH VIÊN ONLINE", clientCount, ACCENT)); metrics.add(metric("KẾT NỐI TCP", tcpValue, BLUE)); metrics.add(metric("CÁC KÊNH CỦA ỨNG DỤNG", udpValue, new Color(241, 181, 88)));
    wrapper.add(metrics, BorderLayout.NORTH);
    JPanel split = new JPanel(new GridLayout(1, 2, 16, 0)); split.setOpaque(false);
    split.add(listCard("Đang kết nối", "Tên thành viên và địa chỉ truy cập", clientsList, true));
    split.add(listCard("Hoạt động gần đây", "Kết nối, tin nhắn và tệp được chia sẻ", eventsList, false));
    wrapper.add(split, BorderLayout.CENTER);
    return wrapper;
  }

  private JPanel metric(String title, JLabel value, Color accent) {
    JPanel card = panel(); card.setLayout(new BorderLayout(0, 8)); card.setBorder(new EmptyBorder(16, 18, 15, 18));
    JLabel t = label(title, 11, MUTED); t.setFont(new Font("Dialog", Font.BOLD, 11)); card.add(t, BorderLayout.NORTH);
    value.setForeground(TEXT); value.setFont(new Font("Dialog", Font.BOLD, 24)); card.add(value, BorderLayout.CENTER);
    JPanel line = new JPanel(); line.setBackground(accent); line.setPreferredSize(new Dimension(42, 3)); card.add(line, BorderLayout.SOUTH); return card;
  }

  private JPanel listCard(String title, String caption, JList<String> list, boolean clients) {
    JPanel card = panel(); card.setLayout(new BorderLayout()); card.setBorder(new EmptyBorder(16, 16, 14, 16));
    JPanel top = new JPanel(); top.setOpaque(false); top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
    JLabel h = label(title, 16, TEXT); h.setFont(new Font("Dialog", Font.BOLD, 16)); top.add(h);
    JLabel c = label(caption, 12, MUTED); c.setBorder(new EmptyBorder(4, 0, 12, 0)); top.add(c); card.add(top, BorderLayout.NORTH);
    list.setBackground(PANEL); list.setForeground(TEXT); list.setSelectionBackground(new Color(43, 67, 92)); list.setSelectionForeground(TEXT); list.setBorder(new EmptyBorder(6, 7, 6, 7)); list.setFont(new Font("Dialog", Font.PLAIN, 13)); list.setFixedCellHeight(clients ? 42 : 28);
    list.setCellRenderer(new DefaultListCellRenderer() { @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) { JLabel x=(JLabel)super.getListCellRendererComponent(l,v,i,s,f); x.setBorder(new EmptyBorder(4, 8, 4, 8)); x.setOpaque(true); if(!s)x.setBackground(PANEL); x.setForeground(s?TEXT:(clients?new Color(207,224,240):new Color(185,199,220))); return x; }});
    JScrollPane scroll = new JScrollPane(list);
    ChatTheme.styleScrollPane(scroll, true);
    card.add(scroll, BorderLayout.CENTER); return card;
  }

  private JPanel panel() { JPanel p = new JPanel() {
    @Override protected void paintComponent(Graphics graphics) {
      Graphics2D g = (Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(PANEL); g.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20); g.dispose();
      super.paintComponent(graphics);
    }
  }; p.setOpaque(false); return p; }
  private JLabel label(String text, int size, Color color) { return ChatTheme.label(text, new Font("Dialog", Font.PLAIN, size), color); }
  private JButton button(String text, Color bg, Color fg) { JButton b = ChatTheme.button(text, bg, fg); b.setFont(new Font("Dialog", Font.BOLD, 12)); b.setBorder(new EmptyBorder(9, 14, 9, 14)); return b; }

  private void refresh() {
    boolean running = server.isRunning();
    String error = server.failure();
    state.setText(error != null ? "  KHÔNG THỂ KHỞI ĐỘNG  " : running ? "  ĐANG CHẠY  " : "  CHƯA CHẠY  ");
    state.setBackground(error != null ? new Color(153, 54, 72) : running ? ACCENT : new Color(99, 108, 125));
    state.setForeground(running ? new Color(7, 38, 32) : TEXT);
    state.setToolTipText(error);
    List<ChatServer.ClientInfo> online = server.clientSnapshot();
    clientCount.setText(Integer.toString(online.size()));
    tcpValue.setText("0.0.0.0:" + server.port());
    tcpValue.setToolTipText("Máy chủ lắng nghe trên các giao diện mạng");
    udpValue.setText("TCP + UDP");
    udpValue.setToolTipText("Server chuyển tiếp chat riêng qua TCP Unicast. Phòng chung Multicast và Thông báo LAN được gửi trực tiếp giữa các client.");
    if (firstRefresh || !online.equals(displayedClients)) {
      clientsModel.clear();
      if (online.isEmpty()) clientsModel.addElement("Chưa có thành viên kết nối");
      else for (ChatServer.ClientInfo c : online) clientsModel.addElement(c.user() + "  ·  " + c.address());
      displayedClients = online;
    }
    List<String> events = server.eventSnapshot();
    if (firstRefresh || !events.equals(displayedEvents)) {
      int start = Math.max(0, events.size() - 180);
      eventsModel.clear();
      for (int i = start; i < events.size(); i++) eventsModel.addElement(events.get(i));
      if (!events.isEmpty()) eventsList.ensureIndexIsVisible(eventsModel.size() - 1);
      displayedEvents = events;
    }
    firstRefresh = false;
  }

  private void shutdown() { refreshTimer.stop(); server.close(); dispose(); }
  private Image createIcon() { Image image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB); Graphics2D g=(Graphics2D)image.getGraphics(); g.setColor(ACCENT); g.fillRoundRect(2,2,28,28,10,10); g.setColor(BG); g.fillOval(9,10,5,5); g.fillOval(18,10,5,5); g.drawArc(9,10,14,12,205,130); g.dispose(); return image; }
}
