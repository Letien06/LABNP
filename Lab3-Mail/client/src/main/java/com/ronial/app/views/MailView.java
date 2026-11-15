package com.ronial.app.views;

import com.ronial.app.context.ContextProvider;
import com.ronial.app.mail.MailHtmlFormat;
import com.ronial.app.mail.MailService;
import com.ronial.app.mail.ProtocolMailService;
import com.ronial.app.models.Email;
import com.ronial.app.models.User;
import com.ronial.app.views.utils.OpenBrowserUtils;
import com.ronial.app.views.utils.Toast;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class MailView extends JFrame {
    private final User user;
    private DefaultListModel<String> inboxModel, sentModel;
    private JList<String> emailList;
    private JEditorPane emailContent;
    private List<Email> inboxEmails, sentEmails;
    private final MailService mailService;
    private JTabbedPane tabbedPane;
    private final SwingBrowser swingBrowser;
    private WebEngine webEngine;
    private JTextField searchField;
    private List<ProtocolMailService.MailRow> inboxRows = List.of(), sentRows = List.of();
    private final Map<String, CreateMailView> composers = new HashMap<>();
    private final Map<String, Boolean> changingRead = new HashMap<>();
    private SwingWorker<ProtocolMailService.Mailbox, Void> refreshWorker;
    private Timer refreshTimer;
    private boolean refreshRequested;
    private boolean applyingMailbox;
    private boolean deletingMessage;
    private int readRevision;

    public static void launch(User user) {
        new MailView(user);
    }

    public static void launch(User user, MailService session) {
        new MailView(user, session);
    }

    public MailView(User user) {
        this(user, ContextProvider.get(MailService.class));
    }

    private MailView(User user, MailService session) {
        swingBrowser = ContextProvider.get(SwingBrowser.class);
        mailService = session;
        this.user = user;
        setTitle("Mail - " + user.getEmail() + " - " + user.getName());
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/assets/Email-icon.png"))).getImage());
        setSize(1300, 800);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                leaveMailbox(() -> System.exit(0));
            }
        });
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(247, 249, 252));

        createMenuBar();

        JPanel leftPanel = createLeftPanel();
        JPanel rightPanel = createRightPanel();

        JPanel top = new JPanel(new BorderLayout(16, 0));
        top.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        top.setBackground(Color.WHITE);
        JLabel brand = new JLabel("Ya-mail");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 20));
        brand.setForeground(new Color(32, 84, 150));
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Tìm kiếm thư");
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(215, 222, 232)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        JButton search = new JButton("Tìm");
        styleToolbarButton(search);
        search.addActionListener(e -> filterMessages(searchField.getText()));
        searchField.addActionListener(e -> filterMessages(searchField.getText()));
        JPanel searchBox = new JPanel(new BorderLayout(8, 0));
        searchBox.setOpaque(false); searchBox.add(searchField, BorderLayout.CENTER); searchBox.add(search, BorderLayout.EAST);
        top.add(brand, BorderLayout.WEST); top.add(searchBox, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.CENTER);

        inboxEmails = new ArrayList<>();
        sentEmails = new ArrayList<>();

        setVisible(true);
        refreshInbox();
        if (mailService instanceof ProtocolMailService protocol && protocol.usesLocalServer()) {
            refreshTimer = new Timer(30_000, e -> refreshMailbox(false));
            refreshTimer.start();
        }
    }

    private void styleToolbarButton(JButton b) {
        b.setFont(new Font("Segoe UI", Font.PLAIN, 13)); b.setFocusPainted(false);
        b.setBackground(new Color(232, 240, 254)); b.setForeground(new Color(25, 72, 125));
        b.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
    }

    private void filterMessages(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        DefaultListModel<String> source = tabbedPane.getSelectedIndex() == 0 ? inboxModel : sentModel;
        emailList.setModel(source);
        if (q.isEmpty()) { emailList.clearSelection(); return; }
        for (int i = 0; i < source.size(); i++) {
            if (source.get(i).toLowerCase().contains(q)) { emailList.setSelectedIndex(i); emailList.ensureIndexIsVisible(i); return; }
        }
        emailList.clearSelection();
    }

    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(new Color(245, 245, 245)); // Màu nền sáng

        JMenu menuOptions = new JMenu("♨️ Hệ thống");
        JMenu menuMails = new JMenu("📩 Tùy chọn thư");

        JMenuItem newEmailItem = createMenuItem("📝 Tạo Mail", "Soạn email mới");
        JMenuItem refreshItem = createMenuItem("🔄 Làm mới", "Làm mới danh sách email");
        JMenuItem deleteMail = createMenuItem("❌ Xóa mail hiện tại", "Xóa email đang mở");
        JMenuItem transferMail = createMenuItem("🚚 Chuyển tiếp mail", "Chuyển tiếp email hiện tại");
        JMenuItem logout = createMenuItem("☠️ Đăng xuất", "Đăng xuất khỏi tài khoản");
        JMenuItem exit = createMenuItem("❌ Thoát", "Thoát ứng dụng");

        newEmailItem.addActionListener(e -> new CreateMailView(this));
        refreshItem.addActionListener(e -> refreshInbox());
        transferMail.addActionListener(e -> {
            int selectedEmailIndex = emailList.getSelectedIndex();
            if (selectedEmailIndex > -1) {
                Email email = getEmailFromTabbedPane(selectedEmailIndex);
                new TransferMailView(email, this);
            } else {
                Toast.error("Vui lòng chọn mail cần chuyển tiếp");
            }
        });
        deleteMail.addActionListener(e -> deleteSelectedMessage());
        logout.addActionListener(e -> leaveMailbox(MailLaunch::launch));
        exit.addActionListener(e -> leaveMailbox(() -> System.exit(0)));

        if (mailService instanceof ProtocolMailService protocol && protocol.usesLocalServer()) {
            JMenuItem unread = createMenuItem("Đánh dấu chưa đọc", "Đánh dấu thư đến đang mở là chưa đọc");
            unread.addActionListener(e -> {
                if (tabbedPane.getSelectedIndex() == 0) changeRead(getSelectedMessageUid(), false);
            });
            JMenuItem drafts = createMenuItem("Thư nháp", "Xem và tiếp tục soạn thư nháp");
            drafts.addActionListener(e -> showDrafts(drafts, protocol));
            menuMails.add(drafts);
            menuMails.add(unread);
        }
        menuMails.add(newEmailItem);
        menuMails.add(refreshItem);
        menuMails.add(deleteMail);
        menuMails.add(transferMail);

        menuOptions.add(logout);
        menuOptions.addSeparator();
        menuOptions.add(exit);

        menuBar.add(menuOptions);
        menuBar.add(menuMails);
        setJMenuBar(menuBar);
    }

    private JMenuItem createMenuItem(String title, String tooltip) {
        JMenuItem item = new JMenuItem(title);
        item.setOpaque(true);
        item.setFont(item.getFont().deriveFont(14f));
        item.setBackground(new Color(245, 245, 245));  // Màu nền sáng
        item.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        item.setToolTipText(tooltip);

        // Thêm hiệu ứng hover cho mục menu
        item.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                item.setBackground(new Color(230, 230, 230));  // Màu nền khi hover
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                item.setBackground(new Color(245, 245, 245));  // Màu nền khi không hover
            }
        });

        return item;
    }

    private JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(300, 0));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 8));
        leftPanel.setBackground(new Color(247, 249, 252));

        inboxModel = new DefaultListModel<>();
        sentModel = new DefaultListModel<>();
        emailList = new JList<>(inboxModel);
        emailList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        emailList.setFixedCellHeight(42);
        emailList.setSelectionBackground(new Color(218, 232, 252));
        emailList.setSelectionForeground(new Color(20, 45, 80));
        emailList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !applyingMailbox) displayEmailContent();
        });

        // Thêm Border cho bảng
        JScrollPane emailScrollPane = new JScrollPane(emailList);
        emailScrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        emailScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);


        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Hộp thư đến", null);
        tabbedPane.addTab("Hộp thư đi", null);
        tabbedPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0)); // Border dưới tab
        tabbedPane.setBackground(Color.WHITE);

        tabbedPane.addChangeListener(e -> switchEmailList());

        JButton compose = new JButton("Soạn thư");
        styleToolbarButton(compose);
        compose.addActionListener(e -> new CreateMailView(this));
        JPanel navigation = new JPanel(new BorderLayout(0, 12));
        navigation.setOpaque(false);
        navigation.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        navigation.add(compose, BorderLayout.NORTH);
        navigation.add(tabbedPane, BorderLayout.SOUTH);
        leftPanel.add(navigation, BorderLayout.NORTH);
        leftPanel.add(emailScrollPane, BorderLayout.CENTER);

        return leftPanel;
    }

    private JPanel createRightPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(10, 8, 10, 12),
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(220, 226, 234)), "  Nội dung thư  ")));

        emailContent = new JEditorPane();
        emailContent.setEditorKit(new HTMLEditorKit());
        emailContent.setContentType("text/html");
        emailContent.setEditable(false);
        emailContent.setBackground(new Color(245, 245, 245));
        emailContent.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    swingBrowser.open(e.getURL().toString());
                } catch (NullPointerException ex) {
                    System.err.println(ex.getMessage());
                }
            }
        });
        JScrollPane contentScrollPane = new JScrollPane(emailContent);
        contentScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED); // Show vertical scrollbar if needed
        rightPanel.add(contentScrollPane, BorderLayout.CENTER);
        return rightPanel;
    }

    private JPanel createRightPanelFX() {
        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBorder(BorderFactory.createTitledBorder("📜 Nội dung thư"));

        JFXPanel fxPanel = new JFXPanel(); // Nhúng JavaFX vào Swing
        Platform.runLater(() -> {
            WebView webView = new WebView();
            webEngine = webView.getEngine();
            webEngine.setJavaScriptEnabled(true);
            BorderPane root = new BorderPane();
            root.setCenter(webView);
            fxPanel.setScene(new Scene(root));
        });

        rightPanel.add(fxPanel, BorderLayout.CENTER);
        return rightPanel;
    }

    public MailService getMailService() {
        return mailService;
    }

    void registerComposer(String id, CreateMailView composer) {
        composers.put(id, composer);
    }

    void releaseComposer(String id) {
        composers.remove(id);
    }

    private void leaveMailbox(Runnable action) {
        if (!composers.isEmpty()) {
            Toast.info("Đóng các cửa sổ soạn thư để lưu thư nháp trước khi rời hộp thư.");
            return;
        }
        dispose();
        action.run();
    }

    @Override public void dispose() {
        if (refreshTimer != null) refreshTimer.stop();
        if (refreshWorker != null) refreshWorker.cancel(true);
        super.dispose();
    }

    private void showDrafts(JMenuItem item, ProtocolMailService protocol) {
        item.setEnabled(false);
        new SwingWorker<List<ProtocolMailService.Draft>, Void>() {
            @Override protected List<ProtocolMailService.Draft> doInBackground() throws IOException {
                return protocol.listDrafts();
            }

            @Override protected void done() {
                item.setEnabled(true);
                if (!isDisplayable()) return;
                try {
                    List<ProtocolMailService.Draft> drafts = get();
                    if (drafts.isEmpty()) {
                        Toast.info("Chưa có thư nháp.");
                        return;
                    }
                    JList<ProtocolMailService.Draft> choices = new JList<>(
                            drafts.toArray(ProtocolMailService.Draft[]::new));
                    choices.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                    JTextArea preview = new JTextArea(10, 46);
                    preview.setEditable(false);
                    preview.setLineWrap(true);
                    preview.setWrapStyleWord(true);
                    choices.addListSelectionListener(e -> {
                        ProtocolMailService.Draft draft = choices.getSelectedValue();
                        if (draft == null) return;
                        preview.setText("Đến: " + draft.to() + "\nTiêu đề: " + draft.subject()
                                + "\nLiên kết: " + draft.links() + "\n\n" + draft.body());
                        preview.setCaretPosition(0);
                    });
                    choices.setSelectedIndex(0);
                    JPanel panel = new JPanel(new BorderLayout(0, 8));
                    JScrollPane listScroll = new JScrollPane(choices);
                    listScroll.setPreferredSize(new Dimension(480, 120));
                    panel.add(listScroll, BorderLayout.NORTH);
                    panel.add(new JScrollPane(preview), BorderLayout.CENTER);
                    Object[] options = {"Tiếp tục soạn", "Đóng"};
                    int choice = JOptionPane.showOptionDialog(MailView.this, panel, "Thư nháp",
                            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
                    ProtocolMailService.Draft draft = choices.getSelectedValue();
                    if (choice == 0 && draft != null) {
                        CreateMailView existing = composers.get(draft.id());
                        if (existing == null) new CreateMailView(MailView.this, draft);
                        else existing.toFront();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Toast.error(ex.getCause().getMessage());
                }
            }
        }.execute();
    }

    public void refreshInbox() {
        refreshMailbox(true);
    }

    private void refreshMailbox(boolean reportError) {
        if (!(mailService instanceof ProtocolMailService protocol)) {
            try {
                emailContent.setText("");
                mailService.loadMails(this);
            } catch (IOException e) {
                Toast.error(e.getMessage());
            }
            return;
        }
        if (refreshWorker != null || deletingMessage || !changingRead.isEmpty()) {
            refreshRequested |= reportError;
            return;
        }
        int revision = readRevision;
        refreshWorker = new SwingWorker<>() {
            @Override protected ProtocolMailService.Mailbox doInBackground() throws IOException {
                return protocol.fetchMailbox();
            }

            @Override protected void done() {
                refreshWorker = null;
                if (!isDisplayable() || isCancelled()) return;
                try {
                    ProtocolMailService.Mailbox mailbox = get();
                    if (revision == readRevision && !deletingMessage) applyMailbox(mailbox);
                    else refreshRequested = true;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    if (reportError) Toast.error(ex.getCause().getMessage());
                } finally {
                    refreshIfRequested();
                }
            }
        };
        refreshWorker.execute();
    }

    private void refreshIfRequested() {
        if (refreshRequested && refreshWorker == null && !deletingMessage && changingRead.isEmpty()) {
            refreshRequested = false;
            refreshMailbox(true);
        }
    }

    public void applyMailbox(ProtocolMailService.Mailbox mailbox) {
        String selectedUid = getSelectedMessageUid();
        if (!mailbox.hasReadState()) {
            Map<String, Boolean> existingRead = new HashMap<>();
            for (var row : inboxRows) existingRead.put(row.uid(), row.email().isSeen());
            for (var row : mailbox.inbox()) row.email().setSeen(existingRead.getOrDefault(row.uid(), false));
        }
        if (sameRows(inboxRows, mailbox.inbox()) && sameRows(sentRows, mailbox.sent())) return;
        applyingMailbox = true;
        try {
            emailList.clearSelection();
            inboxRows = mailbox.inbox();
            sentRows = mailbox.sent();
            inboxEmails = inboxRows.stream().map(ProtocolMailService.MailRow::email).toList();
            sentEmails = sentRows.stream().map(ProtocolMailService.MailRow::email).toList();
            inboxModel.clear();
            for (Email email : inboxEmails) inboxModel.addElement(inboxLabel(email));
            sentModel.clear();
            for (Email email : sentEmails) sentModel.addElement(email.getSubject());
            List<ProtocolMailService.MailRow> rows = selectedRows();
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).uid().equals(selectedUid)) {
                    emailList.setSelectedIndex(i);
                    return;
                }
            }
            emailContent.setText("");
        } finally {
            applyingMailbox = false;
        }
    }

    private static boolean sameRows(List<ProtocolMailService.MailRow> current, List<ProtocolMailService.MailRow> next) {
        if (current.size() != next.size()) return false;
        for (int i = 0; i < current.size(); i++) {
            var before = current.get(i);
            var after = next.get(i);
            if (!before.uid().equals(after.uid()) || before.email().getId() != after.email().getId()
                    || before.email().isSeen() != after.email().isSeen()) return false;
        }
        return true;
    }
    private static String inboxLabel(Email email) {
        return (email.isSeen() ? "" : "● ") + email.getSubject();
    }

    private List<ProtocolMailService.MailRow> selectedRows() {
        return tabbedPane.getSelectedIndex() == 0 ? inboxRows : sentRows;
    }

    public String getSelectedMessageUid() {
        int index = emailList.getSelectedIndex();
        List<ProtocolMailService.MailRow> rows = selectedRows();
        return index >= 0 && index < rows.size() ? rows.get(index).uid() : null;
    }

    private void deleteSelectedMessage() {
        if (!(mailService instanceof ProtocolMailService protocol)) {
            try {
                mailService.deleteMail(this);
            } catch (IOException ex) {
                Toast.error(ex.getMessage());
            }
            return;
        }
        String uid = getSelectedMessageUid();
        if (uid == null) {
            Toast.error("Vui lòng chọn thư cần xóa.");
            return;
        }
        if (deletingMessage) return;
        deletingMessage = true;
        readRevision++;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws IOException {
                protocol.deleteMessage(uid);
                return null;
            }

            @Override protected void done() {
                deletingMessage = false;
                if (!isDisplayable()) return;
                try {
                    get();
                    refreshRequested = true;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Toast.error(ex.getCause().getMessage());
                } finally {
                    refreshIfRequested();
                }
            }
        }.execute();
    }

    private void changeRead(String uid, boolean read) {
        if (!(mailService instanceof ProtocolMailService protocol) || uid == null || deletingMessage) return;
        int index = 0;
        for (; index < inboxRows.size(); index++) {
            if (inboxRows.get(index).uid().equals(uid)) break;
        }
        if (index == inboxRows.size()) return;
        Email email = inboxRows.get(index).email();
        if (email.isSeen() == read) return;
        readRevision++;
        boolean savedRead = email.isSeen();
        email.setSeen(read);
        inboxModel.set(index, inboxLabel(email));
        if (changingRead.put(uid, read) == null) persistRead(protocol, uid, index, savedRead, read);
    }

    private void persistRead(ProtocolMailService protocol, String uid, int index, boolean savedRead, boolean read) {
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws IOException {
                protocol.setRead(uid, read);
                return null;
            }

            @Override protected void done() {
                if (!isDisplayable()) return;
                boolean persisted = savedRead;
                try {
                    get();
                    persisted = read;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Toast.error(ex.getCause().getMessage());
                }
                boolean desiredRead = changingRead.get(uid);
                if (desiredRead != read && desiredRead != persisted) {
                    persistRead(protocol, uid, index, persisted, desiredRead);
                } else {
                    changingRead.remove(uid);
                    Email email = inboxRows.get(index).email();
                    email.setSeen(persisted);
                    inboxModel.set(index, inboxLabel(email));
                    refreshIfRequested();
                }
            }
        }.execute();
    }
    private void switchEmailList() {
        if (tabbedPane.getSelectedIndex() == 0) {
            emailList.setModel(inboxModel);
        } else {
            emailList.setModel(sentModel);
        }
        emailContent.setText("");
    }

    public Email getEmailFromTabbedPane(int selectedIndex) {
        Email email;
        if (tabbedPane.getSelectedIndex() == 0) {
            email = inboxEmails.get(selectedIndex);
        } else {
            email = sentEmails.get(selectedIndex);
        }
        return email;
    }

    public void displayEmailContent(Email email) {
        SwingUtilities.invokeLater(() -> {
            emailContent.setText(MailHtmlFormat.toContentHtml(email));
        });
//        Platform.runLater(() -> {
//            String htmlContent = "<html><head><meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>" +
//                    "<style>body { font-family: 'Segoe UI Emoji', 'Noto Color Emoji', sans-serif; }</style>" +
//                    "</head><body>" +
//                    MailHtmlFormat.toContentHtml(email) +
//                    "</body></html>";
//
//            String base64Content = Base64.getEncoder().encodeToString(htmlContent.getBytes(StandardCharsets.UTF_8));
//            String htmlData = "data:text/html;charset=UTF-8;base64," + base64Content;
//
//            webEngine.load(htmlData);
//        });
    }

    private void displayEmailContent() {
        int selectedEmailIndex = emailList.getSelectedIndex();
        if (selectedEmailIndex > -1) {
            Email email = getEmailFromTabbedPane(selectedEmailIndex);
            displayEmailContent(email);
            if (mailService instanceof ProtocolMailService) {
                if (tabbedPane.getSelectedIndex() == 0) changeRead(getSelectedMessageUid(), true);
                return;
            }
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    mailService.readMail(this, email);
                } catch (IOException e) {
                    System.out.println(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }
    }

    public JEditorPane getEmailContent() {
        return emailContent;
    }

    public User getUser() {
        return user;
    }

    public DefaultListModel<String> getInboxModel() {
        return inboxModel;
    }

    public DefaultListModel<String> getSentModel() {
        return sentModel;
    }

    public void setInboxEmails(List<Email> inboxEmails) {
        this.inboxEmails = inboxEmails;
    }

    public void setSentEmails(List<Email> sentEmails) {
        this.sentEmails = sentEmails;
    }

    public JList<String> getEmailList() {
        return emailList;
    }

    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    public List<Email> getSentEmails() {
        return sentEmails;
    }

    public List<Email> getInboxEmails() {
        return inboxEmails;
    }
}
