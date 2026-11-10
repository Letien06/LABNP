package lab1.chat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.net.NetworkInterface;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** UDP transport with room membership and the connected client's display identity. */
public final class UdpChannelPanel extends JPanel implements AutoCloseable {
  private static final Color BG=new Color(245,247,251), INK=new Color(28,42,63), MUTED=new Color(111,127,147), LINE=new Color(224,231,240);
  private static final Font BODY=new Font("Dialog",Font.PLAIN,13), SMALL=new Font("Dialog",Font.PLAIN,11);
  private final UdpChatChannel.Mode mode;
  private final Supplier<String> displayName;
  private final Color accent, tint;
  private final JTextField destination=new JTextField(), port=new JTextField(), nickname=new JTextField(), composer=new PlaceholderField();
  private final JComboBox<InterfaceChoice> interfaces=new JComboBox<>();
  private final JButton join,send;
  private JButton emoji;
  private final JLabel status,feedback;
  private final JPanel timeline=new TimelinePanel();
  private final JScrollPane scroll;
  private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"udp-panel-worker");t.setDaemon(true);return t;});
  private final AtomicInteger generation=new AtomicInteger();
  private final List<UdpChatChannel.Message> messages=new ArrayList<>();
  private volatile UdpChatChannel channel;
  private volatile boolean closed;
  private boolean active,busy,sending,failed,identityAvailable;
  private String conversationKey="";

  public UdpChannelPanel(UdpChatChannel.Mode mode,String defaultAddress,int defaultPort,Supplier<String> displayName) {
    this.mode=mode;this.displayName=displayName;
    boolean multicast=mode==UdpChatChannel.Mode.MULTICAST;
    accent=multicast?new Color(116,84,212):new Color(169,106,16);
    tint=multicast?new Color(240,235,255):new Color(255,245,223);
    setLayout(new BorderLayout(0,16));setBackground(BG);setBorder(new EmptyBorder(22,24,20,24));
    status=label("Chưa tham gia",SMALL,MUTED);status.setOpaque(true);status.setBackground(new Color(232,237,244));status.setBorder(new EmptyBorder(7,10,7,10));
    feedback=label(" ",SMALL,MUTED);feedback.setPreferredSize(new Dimension(100,18));
    join=button(multicast?"Tham gia phòng":"Bắt đầu nghe",accent,Color.WHITE);
    send=button("Gửi →",accent,Color.WHITE);
    destination.setText(defaultAddress);port.setText(Integer.toString(defaultPort));nickname.setEditable(false);refreshIdentity();
    interfaces.addItem(new InterfaceChoice("Tự động chọn", ""));
    try { for(NetworkInterface ni:UdpChatChannel.interfaces())interfaces.addItem(new InterfaceChoice(ni.getName()+" · "+ni.getDisplayName(),ni.getName())); }
    catch(Exception e){feedback.setText("Không đọc được danh sách card mạng: "+e.getMessage());}
    interfaces.setFont(BODY);interfaces.setBackground(Color.WHITE);interfaces.setForeground(INK);
    add(buildTop(multicast),BorderLayout.NORTH);
    timeline.setLayout(new BoxLayout(timeline,BoxLayout.Y_AXIS));timeline.setBackground(BG);
    scroll=new JScrollPane(timeline);ChatTheme.styleScrollPane(scroll,false);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);add(scroll,BorderLayout.CENTER);
    add(buildComposer(),BorderLayout.SOUTH);
    join.addActionListener(e->{if(active)leave();else start();});
    send.addActionListener(e->send());composer.addActionListener(e->send());
    setInteractive();renderEmpty();
    addComponentListener(new ComponentAdapter(){@Override public void componentResized(ComponentEvent e){if(active&&!messages.isEmpty())renderMessages();}});
  }

  private JPanel buildTop(boolean multicast) {
    JPanel top=new JPanel(new BorderLayout(0,16));top.setOpaque(false);
    JPanel titleRow=new JPanel(new BorderLayout(12,0));titleRow.setOpaque(false);
    JPanel titles=new JPanel();titles.setOpaque(false);titles.setLayout(new BoxLayout(titles,BoxLayout.Y_AXIS));
    titles.add(label(multicast?"Phòng chung":"Thông báo LAN",new Font("Dialog",Font.BOLD,23),INK));
    JLabel subtitle=label(multicast?"UDP Multicast · Trò chuyện với thành viên trong phòng":"Gửi đến các máy đang lắng nghe trong mạng LAN",BODY,MUTED);subtitle.setBorder(new EmptyBorder(4,0,0,0));titles.add(subtitle);
    titleRow.add(titles,BorderLayout.CENTER);JPanel badge=new JPanel(new FlowLayout(FlowLayout.RIGHT,0,4));badge.setOpaque(false);badge.add(status);titleRow.add(badge,BorderLayout.EAST);top.add(titleRow,BorderLayout.NORTH);
    JPanel card=new RoundedPanel(Color.WHITE,16);card.setLayout(new BorderLayout(0,12));card.setBorder(new EmptyBorder(14,16,12,16));
    JPanel fields=new JPanel(new GridLayout(2,2,16,10));fields.setOpaque(false);
    fields.add(field(multicast?"Địa chỉ nhóm":"Địa chỉ broadcast",destination));fields.add(field("Cổng UDP",port));
    fields.add(field("Card mạng",interfaces));fields.add(field("Danh tính đang kết nối",nickname));card.add(fields,BorderLayout.CENTER);
    JPanel bottom=new JPanel(new BorderLayout(12,0));bottom.setOpaque(false);
    JLabel hint=label(multicast?"Chỉ người đã tham gia nhận tin. Rời phòng sẽ ngừng nhận.":"Các máy cùng LAN, cùng cổng có thể nhận tin.",SMALL,MUTED);bottom.add(hint,BorderLayout.CENTER);bottom.add(join,BorderLayout.EAST);card.add(bottom,BorderLayout.SOUTH);top.add(card,BorderLayout.CENTER);return top;
  }

  private JPanel field(String caption,JComponent component) {
    JPanel p=new JPanel(new BorderLayout(0,5));p.setOpaque(false);p.add(label(caption,SMALL,MUTED),BorderLayout.NORTH);
    if(component instanceof JTextField text){text.setFont(BODY);text.setForeground(INK);text.setBackground(new Color(249,251,254));text.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),new EmptyBorder(7,9,7,9)));text.setCaretColor(accent);}
    component.setPreferredSize(new Dimension(130,33));p.add(component,BorderLayout.CENTER);return p;
  }

  private JPanel buildComposer() {
    JPanel footer=new JPanel(new BorderLayout(0,7));footer.setOpaque(false);
    feedback.setBorder(new EmptyBorder(0,2,0,0));footer.add(feedback,BorderLayout.NORTH);
    JPanel box=new RoundedPanel(Color.WHITE,14);box.setLayout(new BorderLayout(12,0));box.setBorder(new EmptyBorder(10,13,10,10));
    composer.setFont(BODY);composer.setForeground(INK);composer.setBorder(new EmptyBorder(3,2,3,2));composer.setBackground(Color.WHITE);composer.setCaretColor(accent);composer.setToolTipText("Nhập tin nhắn rồi nhấn Enter");box.add(composer,BorderLayout.CENTER);
    emoji=button("☺",new Color(246,248,252),accent);emoji.setFont(new Font("Dialog",Font.PLAIN,20));emoji.setBorder(new EmptyBorder(4,8,4,8));emoji.setToolTipText("Chọn biểu cảm");emoji.addActionListener(e->showEmoji());box.add(emoji,BorderLayout.WEST);box.add(send,BorderLayout.EAST);footer.add(box,BorderLayout.CENTER);
    JLabel note=label("UDP không bảo đảm giao nhận. Hãy kiểm tra mạng và Firewall nếu chưa thấy tin.",SMALL,MUTED);note.setBorder(new EmptyBorder(1,2,0,0));footer.add(note,BorderLayout.SOUTH);return footer;
  }

  private void start() {
    if(busy||closed)return;if(!identityAvailable){showError("Hãy kết nối máy chủ bằng danh tính của bạn trước.");return;}refreshIdentity();
    String address=destination.getText().trim(), name=nickname.getText().trim();int udpPort;
    try{udpPort=Integer.parseInt(port.getText().trim());if(udpPort<1||udpPort>65535)throw new NumberFormatException();}catch(NumberFormatException e){showError("Cổng UDP phải từ 1 đến 65535.");return;}
    if(address.isBlank()){showError("Vui lòng nhập địa chỉ đích.");return;}if(name.isBlank()){showError("Vui lòng nhập tên hiển thị.");return;}
    InterfaceChoice selected=(InterfaceChoice)interfaces.getSelectedItem();String nic=selected==null?"":selected.name;
    busy=true;failed=false;int ticket=generation.incrementAndGet();feedback.setText("Đang mở kênh UDP…");feedback.setForeground(MUTED);setInteractive();
    worker.execute(()->{
      UdpChatChannel candidate=null;
      try{
        candidate=new UdpChatChannel(mode,address,udpPort,nic,m->SwingUtilities.invokeLater(()->receive(ticket,m)),s->SwingUtilities.invokeLater(()->{if(ticket==generation.get()&&!closed){showError(s);UdpChatChannel current=channel;if(current!=null&&!current.isRunning()){active=false;failed=true;channel=null;current.close();setInteractive();renderEmpty();}}}));
        candidate.start();
        UdpChatChannel ready=candidate;
        SwingUtilities.invokeLater(()->{if(closed||!identityAvailable||ticket!=generation.get()){ready.close();return;}String key=address+":"+udpPort;if(!conversationKey.isBlank()&&!conversationKey.equals(key))messages.clear();conversationKey=key;channel=ready;active=true;busy=false;failed=false;String detail="Đang nghe "+address+":"+udpPort+" · "+ready.networkInterface().getDisplayName()+" ("+ready.sourceAddress()+")";feedback.setText(detail);feedback.setToolTipText(detail);feedback.setForeground(MUTED);setInteractive();renderMessages();composer.requestFocusInWindow();});
      }catch(Exception e){if(candidate!=null)candidate.close();SwingUtilities.invokeLater(()->{if(ticket==generation.get()&&!closed){busy=false;active=false;failed=true;showError("Không mở được kênh: "+friendly(e));setInteractive();renderEmpty();}});}
    });
  }

  private void leave() {
    generation.incrementAndGet();UdpChatChannel old=channel;channel=null;active=false;busy=false;sending=false;failed=false;
    if(old!=null)old.close();composer.setText("");feedback.setText(mode==UdpChatChannel.Mode.MULTICAST?"Đã rời phòng. Bạn sẽ không nhận tin mới cho đến khi tham gia lại.":"Đã dừng nghe thông báo LAN.");feedback.setForeground(MUTED);setInteractive();renderEmpty();
  }

  private void send() {
    UdpChatChannel ready=channel;if(!identityAvailable||!active||ready==null||closed||sending)return;
    String text=composer.getText().trim();if(text.isBlank())return;String name=nickname.getText().trim();int ticket=generation.get();
    sending=true;send.setEnabled(false);
    worker.execute(()->{try{ready.send(name,text);SwingUtilities.invokeLater(()->{if(ticket==generation.get()&&!closed){sending=false;if(composer.getText().trim().equals(text))composer.setText("");feedback.setText("Đã gửi datagram UDP.");feedback.setForeground(MUTED);send.setEnabled(active);composer.requestFocusInWindow();}});}catch(Exception e){SwingUtilities.invokeLater(()->{if(ticket==generation.get()&&!closed){sending=false;showError("Gửi thất bại: "+friendly(e));send.setEnabled(active);}});}});
  }

  private void receive(int ticket,UdpChatChannel.Message message) {
    if(closed||!identityAvailable||!active||ticket!=generation.get())return;
    messages.add(message);if(messages.size()>250)messages.remove(0);renderMessages();
  }

  private void renderEmpty() {
    timeline.removeAll();JPanel empty=new JPanel(new GridBagLayout());empty.setBackground(BG);empty.setPreferredSize(new Dimension(200,150));
    JPanel content=new JPanel();content.setOpaque(false);content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));
    boolean multicast=mode==UdpChatChannel.Mode.MULTICAST;
    String heading=!identityAvailable?"Kết nối để tiếp tục":!active?(multicast?"Bạn chưa ở trong Phòng chung":"Bạn chưa nghe thông báo LAN"):(multicast?"Bạn đã tham gia Phòng chung":"Đang nghe thông báo LAN");
    String detail=!identityAvailable?"Kết nối máy chủ để sử dụng danh tính của bạn.":!active?(multicast?"Bấm Tham gia phòng để nhận và gửi tin nhắn.":"Bấm Bắt đầu nghe để nhận và gửi thông báo."):(multicast?"Tin mới từ thành viên trong phòng sẽ xuất hiện ở đây.":"Thông báo mới từ mạng LAN sẽ xuất hiện ở đây.");
    JLabel title=label(heading,new Font("Dialog",Font.BOLD,15),INK);title.setAlignmentX(.5f);content.add(title);
    JLabel hint=label(detail,BODY,MUTED);hint.setAlignmentX(.5f);hint.setBorder(new EmptyBorder(7,0,0,0));content.add(hint);empty.add(content);timeline.add(empty);timeline.revalidate();timeline.repaint();
  }

  private void renderMessages() {
    if(!identityAvailable||!active||messages.isEmpty()){renderEmpty();return;}timeline.removeAll();int width=Math.max(230,Math.min(440,getWidth()-140));
    for(UdpChatChannel.Message message:messages){boolean mine=message.user().equals(nickname.getText().trim());
      JPanel row=new JPanel(new BorderLayout());row.setOpaque(false);row.setBorder(new EmptyBorder(4,0,8,0));
      JPanel bubble=new RoundedPanel(mine?tint:Color.WHITE,15);bubble.setLayout(new BorderLayout(0,6));bubble.setBorder(new EmptyBorder(11,14,10,14));
      JLabel who=label(message.user(),new Font("Dialog",Font.BOLD,12),mine?accent:INK);bubble.add(who,BorderLayout.NORTH);
      JTextArea text=new JTextArea(message.text());text.setFont(BODY);text.setForeground(INK);text.setOpaque(false);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setBorder(null);
      int desired=Math.max(170,Math.min(width,message.text().length()*7+8));text.setSize(new Dimension(desired,Integer.MAX_VALUE));Dimension pref=text.getPreferredSize();text.setPreferredSize(new Dimension(desired,pref.height));bubble.add(text,BorderLayout.CENTER);
      String time=DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(message.timestamp()));
      JLabel meta=label(time+"  ·  "+message.senderAddress(),SMALL,MUTED);bubble.add(meta,BorderLayout.SOUTH);row.add(bubble,mine?BorderLayout.EAST:BorderLayout.WEST);row.setMaximumSize(new Dimension(Integer.MAX_VALUE,row.getPreferredSize().height));timeline.add(row);
    }
    timeline.add(Box.createVerticalGlue());timeline.revalidate();timeline.repaint();SwingUtilities.invokeLater(()->scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum()));
  }

  private void setInteractive() {
    boolean editable=identityAvailable&&!active&&!busy;destination.setEnabled(editable);port.setEnabled(editable);interfaces.setEnabled(editable);nickname.setEnabled(identityAvailable);join.setEnabled(identityAvailable&&!busy&&!closed);
    join.setText(active?(mode==UdpChatChannel.Mode.MULTICAST?"Rời phòng":"Dừng nghe"):(mode==UdpChatChannel.Mode.MULTICAST?"Tham gia phòng":"Bắt đầu nghe"));
    status.setText(!identityAvailable?"Chưa kết nối":active?(mode==UdpChatChannel.Mode.MULTICAST?"●  Trong phòng":"●  Đang nghe"):busy?"Đang tham gia":failed?"Lỗi kết nối":mode==UdpChatChannel.Mode.MULTICAST?"Ngoài phòng":"Chưa lắng nghe");status.setBackground(active?tint:failed?new Color(255,235,237):new Color(232,237,244));status.setForeground(active?accent:failed?new Color(181,60,66):MUTED);send.setEnabled(identityAvailable&&active&&!closed&&!sending);composer.setEnabled(identityAvailable&&active&&!closed);if(emoji!=null)emoji.setEnabled(identityAvailable&&active&&!closed);
  }

  public void refreshIdentity() {String value=displayName==null?"":displayName.get();String next=value==null||value.isBlank()?"Guest":value;if(!conversationKey.isBlank()&&!nickname.getText().equals(next))messages.clear();nickname.setText(next);}
  public void setIdentityAvailable(boolean connected) {if(closed)return;identityAvailable=connected;refreshIdentity();if(!connected){leave();feedback.setText("Kết nối máy chủ để tham gia bằng danh tính của bạn.");}else{setInteractive();if(!active){feedback.setText(mode==UdpChatChannel.Mode.MULTICAST?"Chọn Tham gia phòng để bắt đầu. Rời phòng sẽ ngừng nhận tin.":"Bạn có thể bắt đầu nghe thông báo LAN.");feedback.setForeground(MUTED);renderEmpty();}}}
  public boolean isActive(){return active&&!closed;}
  private void showEmoji(){if(!active||!identityAvailable)return;JPopupMenu popup=new JPopupMenu();popup.setLayout(new GridLayout(3,5,3,3));popup.setBorder(new EmptyBorder(8,8,8,8));popup.setBackground(Color.WHITE);String[] items={"😀","😂","😍","👍","🎉","❤️","🔥","😎","🙏","✨","🚀","✅","👏","💙","🙌"};for(String value:items){JButton choice=button(value,Color.WHITE,INK);choice.setFont(new Font("Dialog",Font.PLAIN,20));choice.setBorder(new EmptyBorder(5,7,5,7));choice.addActionListener(e->{composer.replaceSelection(value);composer.requestFocusInWindow();popup.setVisible(false);});popup.add(choice);}popup.show(emoji,0,-popup.getPreferredSize().height-6);}
  private void showError(String message){feedback.setText(message);feedback.setForeground(new Color(181,60,66));feedback.setToolTipText(message);}
  private static String friendly(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
  private static JLabel label(String text,Font font,Color fg){return ChatTheme.label(text,font,fg);}
  private static JButton button(String text,Color bg,Color fg){JButton b=ChatTheme.button(text,bg,fg);b.setFont(new Font("Dialog",Font.BOLD,12));b.setBorder(new EmptyBorder(9,13,9,13));return b;}
  @Override public void close(){if(closed)return;closed=true;active=false;busy=false;sending=false;generation.incrementAndGet();UdpChatChannel old=channel;channel=null;if(old!=null)old.close();worker.shutdownNow();}
  private record InterfaceChoice(String label,String name){@Override public String toString(){return label;}}
  private static final class PlaceholderField extends JTextField {@Override protected void paintComponent(Graphics graphics){super.paintComponent(graphics);if(getText().isEmpty()){Graphics2D g=(Graphics2D)graphics.create();g.setFont(getFont());g.setColor(isEnabled()?MUTED:new Color(156,167,181));FontMetrics fm=g.getFontMetrics();g.drawString(isEnabled()?"Nhập tin nhắn UDP…":"Tham gia kênh để gửi tin nhắn",getInsets().left+2,(getHeight()-fm.getHeight())/2+fm.getAscent());g.dispose();}}}
  private static final class TimelinePanel extends JPanel implements Scrollable {public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}public int getScrollableUnitIncrement(Rectangle r,int o,int d){return 18;}public int getScrollableBlockIncrement(Rectangle r,int o,int d){return Math.max(18,r.height-30);}public boolean getScrollableTracksViewportWidth(){return true;}public boolean getScrollableTracksViewportHeight(){return false;}}
  private static final class RoundedPanel extends JPanel {private final Color fill;private final int radius;RoundedPanel(Color fill,int radius){this.fill=fill;this.radius=radius;setOpaque(false);}@Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(fill);g.fillRoundRect(0,0,getWidth(),getHeight(),radius,radius);g.dispose();super.paintComponent(graphics);}}
}
