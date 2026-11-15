package com.ronial.app.views;

import com.ronial.app.context.ContextProvider;
import com.ronial.app.mail.MailService;
import com.ronial.app.mail.ProtocolMailService;
import com.ronial.app.models.Email;
import com.ronial.app.models.User;
import com.ronial.app.views.utils.Toast;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.io.IOException;
import java.awt.event.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import static com.ronial.app.views.MailChrome.*;

public class MailView extends JFrame {
    private enum Folder { INBOX, SENT, DRAFTS }
    private record Message(Email email, String uid, boolean sent) {}

    private final User user;
    private DefaultListModel<String> inboxModel, sentModel;
    private JList<String> emailList;
    private JEditorPane emailContent;
    private List<Email> inboxEmails, sentEmails;
    private final MailService mailService;
    private final SwingBrowser swingBrowser;
    private JTextField searchField;
    private final CardLayout cards = new CardLayout();
    private final JPanel mailbox = transparent(cards);
    private final CardLayout messageCards = new CardLayout();
    private final JPanel messageListArea = transparent(messageCards);
    private final CardLayout draftCards = new CardLayout();
    private final JPanel draftListArea = transparent(draftCards);
    private final Map<Folder, JButton> folders = new HashMap<>();
    private final DefaultListModel<String> visibleModel = new DefaultListModel<>();
    private final JLabel title = label("", Font.PLAIN, 23, INK);
    private final JLabel count = label("", Font.PLAIN, 12, MUTED);
    private final JLabel empty = label("", Font.PLAIN, 16, MUTED);
    private final JLabel subjectLine = label("", Font.PLAIN, 25, INK);
    private final JLabel senderLine = label("", Font.BOLD, 14, INK);
    private final JLabel senderAvatar = avatar(40);
    private final JLabel recipientLine = label("", Font.PLAIN, 13, MUTED);
    private final JLabel dateLine = label("", Font.PLAIN, 12, MUTED);
    private final JButton deleteAction = button("", Symbol.DELETE, "Xóa thư");
    private final JButton unreadAction = button("", Symbol.UNREAD, "Đánh dấu chưa đọc");
    private final DefaultListModel<ProtocolMailService.Draft> draftModel = new DefaultListModel<>();
    private final JLabel draftStatus = label("Đang tải thư nháp…", Font.PLAIN, 14, MUTED);
    private final JLabel draftCount = label("", Font.PLAIN, 12, MUTED);
    private final JButton compose = button("Soạn thư", Symbol.COMPOSE, "Soạn thư mới");
    private JPanel sidebar;
    private Folder folder = Folder.INBOX;
    private boolean sidebarCollapsed;
    private boolean suppressSearch;
    private boolean closingMailbox;
    private boolean draftRefreshRequested;
    private List<Message> visibleMessages = List.of();
    private Message opened;
    private SwingWorker<List<ProtocolMailService.Draft>, Void> draftsWorker;
    private Timer searchTimer;
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
        setTitle("Ya-mail · " + user.getEmail());
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/assets/Email-icon.png"))).getImage());
        setSize(1280, 790);
        setMinimumSize(new Dimension(790, 510));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { leaveMailbox(() -> System.exit(0)); }
        });
        setLocationRelativeTo(null);
        inboxEmails = List.of();
        sentEmails = List.of();
        inboxModel = new DefaultListModel<>();
        sentModel = new DefaultListModel<>();

        JPanel surface = new JPanel(new BorderLayout());
        surface.setBackground(CANVAS);
        surface.add(createHeader(), BorderLayout.NORTH);
        surface.add(createSidebar(), BorderLayout.WEST);
        JPanel content = rounded(new BorderLayout(), Color.WHITE, 28);
        content.add(mailbox);
        mailbox.add(createMessageList(), "messages");
        mailbox.add(createReader(), "reader");
        mailbox.add(createDraftList(), "drafts");
        JPanel gutter = transparent(new BorderLayout());
        gutter.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 16));
        gutter.add(content);
        surface.add(gutter);
        setContentPane(surface);
        installShortcuts();
        openFolder(Folder.INBOX);
        setVisible(true);
        refreshInbox();
        if (localService() != null) {
            refreshTimer = new Timer(30_000, e -> refreshMailbox(false));
            refreshTimer.start();
        }
    }

    private ProtocolMailService localService() {
        return mailService instanceof ProtocolMailService protocol && protocol.usesLocalServer() ? protocol : null;
    }

    private JPanel createHeader() {
        JPanel header = transparent(new BorderLayout(12, 0));
        header.setBorder(BorderFactory.createEmptyBorder(9, 13, 9, 20));
        header.setPreferredSize(new Dimension(0, 66));
        JButton menu = button("", Symbol.MENU, "Thu gọn hoặc mở thanh bên");
        menu.setPreferredSize(new Dimension(42, 44));
        menu.addActionListener(e -> toggleSidebar());
        JLabel wordmark = label("Ya-mail", Font.PLAIN, 23, new Color(0x3c4043));
        wordmark.setIcon(icon(Symbol.BRAND, 31, MUTED));
        wordmark.setIconTextGap(10);
        JPanel brand = transparent(new BorderLayout(6, 0));
        brand.setPreferredSize(new Dimension(199, 44));
        brand.add(menu, BorderLayout.WEST);
        brand.add(wordmark);
        header.add(brand, BorderLayout.WEST);

        JPanel search = rounded(new BorderLayout(), SEARCH, 46);
        search.setPreferredSize(new Dimension(690, 46));
        search.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 6));
        JButton lookup = button("", Symbol.SEARCH, "Tìm kiếm thư");
        lookup.addActionListener(e -> { searchField.requestFocusInWindow(); applySearch(); });
        searchField = new JTextField();
        searchField.setName("ya.mail.search");
        searchField.getAccessibleContext().setAccessibleName("Tìm trong thư");
        searchField.setOpaque(false);
        searchField.setBackground(SEARCH);
        searchField.setBorder(BorderFactory.createEmptyBorder());
        searchField.setFont(font(Font.PLAIN, 16));
        searchField.putClientProperty("JTextField.placeholderText", "Tìm trong thư");
        searchField.addActionListener(e -> applySearch());
        JButton clear = button("", Symbol.CLOSE, "Xóa tìm kiếm");
        clear.setVisible(false);
        clear.addActionListener(e -> searchField.setText(""));
        searchTimer = new Timer(140, e -> applySearch());
        searchTimer.setRepeats(false);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                clear.setVisible(!searchField.getText().isEmpty());
                if (!suppressSearch) searchTimer.restart();
            }
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        });
        search.add(lookup, BorderLayout.WEST);
        search.add(searchField);
        search.add(clear, BorderLayout.EAST);
        JPanel searchAlign = transparent(new GridBagLayout());
        GridBagConstraints searchPosition = new GridBagConstraints();
        searchPosition.fill = GridBagConstraints.HORIZONTAL;
        searchPosition.weightx = 1;
        searchAlign.add(search, searchPosition);
        searchPosition.weightx = .25;
        searchAlign.add(Box.createHorizontalStrut(0), searchPosition);
        header.add(searchAlign);

        JButton account = button(initial(correspondent(user.getEmail())), null, "Tài khoản " + user.getEmail());
        account.putClientProperty("FlatLaf.style", "arc: 44; borderWidth: 0; focusWidth: 0; background: #0b8043; "
                + "hoverBackground: #116a3b; focusedBackground: #116a3b; pressedBackground: #0a5930");
        account.setForeground(Color.WHITE);
        account.setFont(font(Font.BOLD, 16));
        account.setPreferredSize(new Dimension(37, 37));
        account.addActionListener(e -> accountMenu(account));
        JPanel accountAlign = transparent(new GridBagLayout());
        accountAlign.add(account);
        header.add(accountAlign, BorderLayout.EAST);
        return header;
    }

    private void accountMenu(JButton anchor) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem address = new JMenuItem(user.getEmail());
        address.putClientProperty("html.disable", true);
        address.setEnabled(false);
        menu.add(address);
        menu.addSeparator();
        ProtocolMailService protocol = localService();
        if (protocol != null) {
            JMenuItem gmail = new JMenuItem("Gmail gửi đi", icon(Symbol.MAIL, 16, BLUE));
            gmail.addActionListener(e -> new GmailRelayDialog(this, protocol).setVisible(true));
            menu.add(gmail);
            menu.addSeparator();
        }
        JMenuItem logout = new JMenuItem("Đăng xuất");
        logout.addActionListener(e -> leaveMailbox(MailLaunch::launch));
        menu.add(logout);
        JMenuItem quit = new JMenuItem("Thoát Ya-mail");
        quit.addActionListener(e -> leaveMailbox(() -> System.exit(0)));
        menu.add(quit);
        menu.show(anchor, anchor.getWidth() - menu.getPreferredSize().width, anchor.getHeight() + 6);
    }

    private JPanel createSidebar() {
        sidebar = transparent(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(216, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 12));
        JPanel stack = transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        compose.setFont(font(Font.BOLD, 14));
        compose.setPreferredSize(new Dimension(139, 53));
        compose.putClientProperty("JButton.buttonType", null);
        compose.putClientProperty("FlatLaf.style", "arc: 18; borderWidth: 0; focusWidth: 0; "
                + "background: #c2e7ff; hoverBackground: #b2ddfa; focusedBackground: #b2ddfa; pressedBackground: #a0d4f4");
        compose.addActionListener(e -> new CreateMailView(this));
        JPanel composeAlign = transparent(new FlowLayout(FlowLayout.LEADING, 9, 0));
        composeAlign.add(compose);
        c.insets = new Insets(0, 0, 15, 0);
        stack.add(composeAlign, c);
        c.insets = new Insets(1, 0, 1, 0);
        for (Folder destination : Folder.values()) {
            if (destination == Folder.DRAFTS && localService() == null) continue;
            Symbol symbol = switch (destination) {
                case INBOX -> Symbol.INBOX;
                case SENT -> Symbol.SENT;
                case DRAFTS -> Symbol.DRAFT;
            };
            JButton item = button(folderName(destination), symbol, folderName(destination));
            item.setPreferredSize(new Dimension(0, 35));
            item.addActionListener(e -> openFolder(destination));
            folders.put(destination, item);
            c.gridy++;
            stack.add(item, c);
        }
        sidebar.add(stack, BorderLayout.NORTH);
        return sidebar;
    }

    private static String folderName(Folder destination) {
        return switch (destination) {
            case INBOX -> "Hộp thư đến";
            case SENT -> "Đã gửi";
            case DRAFTS -> "Thư nháp";
        };
    }

    private void toggleSidebar() {
        sidebarCollapsed = !sidebarCollapsed;
        sidebar.setPreferredSize(new Dimension(sidebarCollapsed ? 72 : 216, 0));
        compose.setPreferredSize(new Dimension(sidebarCollapsed ? 48 : 139, 53));
        compose.setText(sidebarCollapsed ? "" : "Soạn thư");
        updateNavigation();
        sidebar.revalidate();
    }

    private void updateNavigation() {
        long unread = inboxEmails.stream().filter(email -> !email.isSeen()).count();
        for (var entry : folders.entrySet()) {
            JButton item = entry.getValue();
            boolean inbox = entry.getKey() == Folder.INBOX;
            boolean active = folder == entry.getKey() && searchField.getText().isBlank();
            String text = folderName(entry.getKey()) + (inbox && unread > 0 ? "  " + unread : "");
            item.setText(sidebarCollapsed ? "" : text);
            item.setToolTipText(text);
            item.setHorizontalAlignment(sidebarCollapsed ? SwingConstants.CENTER : SwingConstants.LEADING);
            item.setFont(font(active || inbox && unread > 0 ? Font.BOLD : Font.PLAIN, 14));
            item.putClientProperty("FlatLaf.style", "arc: 34; borderWidth: 0; focusWidth: 0; margin: 0,14,0,14; "
                    + "hoverBackground: #e8edf5; focusedBackground: #e8edf5; pressedBackground: #d8e4f4; background: "
                    + (active ? "#d3e3fd" : "#00000000"));
        }
    }

    private JPanel createMessageList() {
        JPanel page = transparent(new BorderLayout());
        JButton reload = button("", Symbol.REFRESH, "Làm mới hộp thư");
        reload.addActionListener(e -> refreshInbox());
        page.add(toolbar(title, count, reload), BorderLayout.NORTH);
        emailList = new JList<>(visibleModel);
        emailList.setName("ya.mail.messages");
        emailList.setFixedCellHeight(44);
        emailList.setFixedCellWidth(1);
        emailList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        emailList.setBackground(Color.WHITE);
        emailList.setCellRenderer((list, value, index, selected, focused) -> {
            Message item = visibleMessages.get(index);
            Email email = item.email();
            return MailMessageRow.create(correspondent(item.sent() ? email.getTo() : email.getFrom()),
                    value, previewText(email.getBody()), formatDate(email.getCreatedAt(), false),
                    !item.sent() && !email.isSeen(), selected, focused);
        });
        emailList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !applyingMailbox) displayEmailContent();
        });
        messageListArea.add(scroll(emailList), "list");
        empty.setIcon(icon(Symbol.MAIL, 34, new Color(0x6990c5)));
        empty.setIconTextGap(12);
        JPanel emptyPage = transparent(new GridBagLayout());
        emptyPage.add(empty);
        messageListArea.add(emptyPage, "empty");
        page.add(messageListArea);
        return page;
    }

    private JPanel createReader() {
        JPanel page = transparent(new BorderLayout());
        JButton back = button("", Symbol.BACK, "Quay lại danh sách thư");
        back.addActionListener(e -> showList());
        deleteAction.addActionListener(e -> deleteSelectedMessage());
        unreadAction.setVisible(mailService instanceof ProtocolMailService);
        unreadAction.addActionListener(e -> {
            if (opened != null && !opened.sent()) changeRead(opened.uid(), false);
            showList();
        });
        JButton forward = button("Chuyển tiếp", Symbol.FORWARD, "Chuyển tiếp thư đang mở");
        forward.addActionListener(e -> {
            if (opened != null) new TransferMailView(opened.email(), this);
        });
        page.add(toolbar(null, null, back, deleteAction, unreadAction, forward), BorderLayout.NORTH);

        JPanel message = new JPanel(new BorderLayout(0, 18));
        message.setBackground(Color.WHITE);
        message.setBorder(BorderFactory.createEmptyBorder(23, 28, 25, 28));
        JPanel heading = transparent(new BorderLayout(0, 14));
        heading.add(subjectLine, BorderLayout.NORTH);
        JPanel sender = transparent(new BorderLayout(12, 0));
        JPanel identity = transparent(new GridLayout(2, 1));
        identity.add(senderLine); identity.add(recipientLine);
        sender.add(senderAvatar, BorderLayout.WEST);
        sender.add(identity); sender.add(dateLine, BorderLayout.EAST);
        heading.add(sender);
        message.add(heading, BorderLayout.NORTH);

        emailContent = new JEditorPane();
        emailContent.setEditorKit(new HTMLEditorKit());
        emailContent.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        emailContent.setFont(font(Font.PLAIN, 15));
        emailContent.setForeground(INK);
        emailContent.setBackground(Color.WHITE);
        emailContent.setEditable(false);
        emailContent.setBorder(BorderFactory.createEmptyBorder());
        emailContent.addHyperlinkListener(e -> {
            if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED || e.getURL() == null || swingBrowser == null) return;
            String scheme = e.getURL().getProtocol();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) swingBrowser.open(e.getURL().toString());
        });
        message.add(scroll(emailContent));
        page.add(message);
        return page;
    }

    private JPanel createDraftList() {
        JPanel page = transparent(new BorderLayout());
        JButton reload = button("", Symbol.REFRESH, "Làm mới thư nháp");
        reload.addActionListener(e -> loadDrafts());
        page.add(toolbar(label("Thư nháp", Font.PLAIN, 23, INK), draftCount, reload), BorderLayout.NORTH);
        JList<ProtocolMailService.Draft> list = new JList<>(draftModel);
        list.setName("ya.mail.drafts");
        list.setFixedCellHeight(44);
        list.setFixedCellWidth(1);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((component, draft, index, selected, focused) -> MailMessageRow.create(
                draft.to().isBlank() ? "Thư nháp" : correspondent(draft.to()),
                subject(draft.subject()), draft.body(), "", false, selected, focused));
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (SwingUtilities.isLeftMouseButton(e) && index >= 0 && list.getCellBounds(index, index).contains(e.getPoint())) {
                    resumeDraft(draftModel.get(index));
                }
            }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "resume");
        list.getActionMap().put("resume", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (list.getSelectedValue() != null) resumeDraft(list.getSelectedValue());
            }
        });
        draftListArea.add(scroll(list), "list");
        draftStatus.setIcon(icon(Symbol.DRAFT, 30, new Color(0x6990c5)));
        draftStatus.setIconTextGap(12);
        JPanel status = transparent(new GridBagLayout());
        status.add(draftStatus);
        draftListArea.add(status, "status");
        draftCards.show(draftListArea, "status");
        page.add(draftListArea);
        return page;
    }

    private static JPanel toolbar(JLabel label, JLabel counter, JButton... controls) {
        JPanel header = transparent(new BorderLayout(8, 0));
        header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DIVIDER),
                BorderFactory.createEmptyBorder(6, 14, 6, 19)));
        JPanel actions = transparent(new FlowLayout(FlowLayout.LEADING, 3, 2));
        if (label != null) header.add(label, BorderLayout.WEST);
        for (JButton control : controls) actions.add(control);
        header.add(actions);
        if (counter != null) header.add(counter, BorderLayout.EAST);
        return header;
    }

    private static JScrollPane scroll(Component content) {
        JScrollPane pane = new JScrollPane(content);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.getViewport().setBackground(Color.WHITE);
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pane.getVerticalScrollBar().setUnitIncrement(24);
        return pane;
    }

    public MailService getMailService() {
        return mailService;
    }

    void registerComposer(String id, CreateMailView composer) {
        composers.put(id, composer);
    }

    void releaseComposer(String id) {
        composers.remove(id);
        if (!closingMailbox && folder == Folder.DRAFTS) loadDrafts();
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
        closingMailbox = true;
        if (refreshTimer != null) refreshTimer.stop();
        if (searchTimer != null) searchTimer.stop();
        if (refreshWorker != null) refreshWorker.cancel(true);
        if (draftsWorker != null) draftsWorker.cancel(true);
        super.dispose();
    }

    private void installShortcuts() {
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "compose", compose::doClick);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "search", () -> searchField.requestFocusInWindow());
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "reload", () -> {
            if (folder == Folder.DRAFTS && searchField.getText().isBlank()) loadDrafts();
            else refreshInbox();
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "back", () -> {
            if (opened != null) showList();
            else if (!searchField.getText().isBlank()) searchField.setText("");
        });
    }

    private void bind(KeyStroke keys, String name, Runnable task) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keys, name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { task.run(); }
        });
    }

    private void openFolder(Folder destination) {
        folder = destination;
        opened = null;
        suppressSearch = true;
        searchTimer.stop();
        searchField.setText("");
        suppressSearch = false;
        if (destination == Folder.DRAFTS) {
            cards.show(mailbox, "drafts");
            loadDrafts();
        } else rebuildVisible(false);
        updateNavigation();
    }

    private void applySearch() {
        searchTimer.stop();
        opened = null;
        if (folder == Folder.DRAFTS && searchField.getText().isBlank()) {
            cards.show(mailbox, "drafts");
            updateNavigation();
        } else rebuildVisible(false);
    }

    private void rebuildVisible(boolean retainReader) {
        String query = searchField.getText().strip().toLowerCase(Locale.ROOT);
        updateNavigation();
        if (folder == Folder.DRAFTS && query.isEmpty()) return;
        List<Message> matches = new ArrayList<>();
        if (folder == Folder.INBOX || !query.isEmpty()) appendMatches(matches, inboxEmails, inboxRows, false, query);
        if (folder == Folder.SENT || !query.isEmpty()) appendMatches(matches, sentEmails, sentRows, true, query);
        matches.sort(Comparator.comparingInt((Message item) -> item.email().getId()).reversed());
        Message previous = retainReader ? opened : null;
        applyingMailbox = true;
        try {
            emailList.clearSelection();
            visibleModel.clear();
            visibleMessages = matches;
            for (Message item : matches) visibleModel.addElement(subject(item.email().getSubject()));
            opened = null;
            if (previous != null) {
                for (int i = 0; i < matches.size(); i++) {
                    Message item = matches.get(i);
                    if (sameMessage(previous, item)) {
                        emailList.setSelectedIndex(i);
                        openMessage(item, false);
                        break;
                    }
                }
            }
        } finally {
            applyingMailbox = false;
        }
        title.setText(query.isEmpty() ? folderName(folder) : "Kết quả tìm kiếm");
        count.setText(matches.size() + " thư");
        empty.setText(query.isEmpty() ? "Chưa có thư" : "Không tìm thấy thư phù hợp");
        messageCards.show(messageListArea, matches.isEmpty() ? "empty" : "list");
        if (opened == null) cards.show(mailbox, "messages");
    }

    private static void appendMatches(List<Message> items, List<Email> emails, List<ProtocolMailService.MailRow> rows,
                                      boolean sent, String query) {
        for (int i = 0; i < emails.size(); i++) {
            Email email = emails.get(i);
            if (!query.isEmpty() && !contains(email, query)) continue;
            items.add(new Message(email, i < rows.size() ? rows.get(i).uid() : null, sent));
        }
    }

    private static boolean contains(Email email, String query) {
        return subject(email.getSubject()).toLowerCase(Locale.ROOT).contains(query)
                || decode(email.getFrom()).toLowerCase(Locale.ROOT).contains(query)
                || decode(email.getTo()).toLowerCase(Locale.ROOT).contains(query)
                || bodyText(email.getBody()).toLowerCase(Locale.ROOT).contains(query);
    }

    private static boolean sameMessage(Message left, Message right) {
        return left.sent() == right.sent() && (left.uid() != null ? left.uid().equals(right.uid())
                : left.email().getId() == right.email().getId());
    }

    private void loadDrafts() {
        ProtocolMailService protocol = localService();
        if (protocol == null) return;
        if (draftsWorker != null) {
            draftRefreshRequested = true;
            return;
        }
        draftStatus.setText("Đang tải thư nháp…");
        if (draftModel.isEmpty()) draftCards.show(draftListArea, "status");
        draftsWorker = new SwingWorker<>() {
            @Override protected List<ProtocolMailService.Draft> doInBackground() throws IOException {
                return protocol.listDrafts();
            }
            @Override protected void done() {
                draftsWorker = null;
                if (!isDisplayable() || isCancelled()) return;
                try {
                    List<ProtocolMailService.Draft> drafts = get();
                    draftModel.clear();
                    for (var draft : drafts) draftModel.addElement(draft);
                    draftCount.setText(drafts.size() + " thư nháp");
                    draftStatus.setText("Chưa có thư nháp");
                    draftCards.show(draftListArea, drafts.isEmpty() ? "status" : "list");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    draftStatus.setText("Chưa tải được thư nháp. Bấm Làm mới để thử lại.");
                    draftCount.setText("");
                    draftCards.show(draftListArea, "status");
                } finally {
                    if (draftRefreshRequested) {
                        draftRefreshRequested = false;
                        loadDrafts();
                    }
                }
            }
        };
        draftsWorker.execute();
    }

    private void resumeDraft(ProtocolMailService.Draft draft) {
        CreateMailView editor = composers.get(draft.id());
        if (editor == null) new CreateMailView(this, draft);
        else {
            editor.setState(JFrame.NORMAL);
            editor.toFront();
        }
    }

    private Message selectedMessage() {
        if (opened != null) return opened;
        int selection = emailList.getSelectedIndex();
        return selection >= 0 && selection < visibleMessages.size() ? visibleMessages.get(selection) : null;
    }

    private void showList() {
        opened = null;
        emailList.clearSelection();
        cards.show(mailbox, "messages");
        emailList.requestFocusInWindow();
    }

    private void openMessage(Message item, boolean markRead) {
        if (item == null) return;
        opened = item;
        displayEmailContent(item.email());
        unreadAction.setEnabled(!item.sent() && item.uid() != null);
        cards.show(mailbox, "reader");
        if (!markRead) return;
        if (mailService instanceof ProtocolMailService) {
            if (!item.sent()) changeRead(item.uid(), true);
        } else {
            try {
                mailService.readMail(this, item.email());
            } catch (IOException ex) {
                Toast.error(ex.getMessage());
            }
        }
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
        if (!mailbox.hasReadState()) {
            Map<String, Boolean> existingRead = new HashMap<>();
            for (var row : inboxRows) existingRead.put(row.uid(), row.email().isSeen());
            for (var row : mailbox.inbox()) row.email().setSeen(existingRead.getOrDefault(row.uid(), false));
        }
        if (sameRows(inboxRows, mailbox.inbox()) && sameRows(sentRows, mailbox.sent())) return;
        inboxRows = mailbox.inbox();
        sentRows = mailbox.sent();
        inboxEmails = inboxRows.stream().map(ProtocolMailService.MailRow::email).toList();
        sentEmails = sentRows.stream().map(ProtocolMailService.MailRow::email).toList();
        inboxModel.clear();
        for (Email email : inboxEmails) inboxModel.addElement(subject(email.getSubject()));
        sentModel.clear();
        for (Email email : sentEmails) sentModel.addElement(subject(email.getSubject()));
        rebuildVisible(true);
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
    public String getSelectedMessageUid() {
        Message item = selectedMessage();
        return item == null ? null : item.uid();
    }

    private void deleteSelectedMessage() {
        if (selectedMessage() == null || deletingMessage) return;
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

        deletingMessage = true;
        deleteAction.setEnabled(false);
        readRevision++;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws IOException {
                protocol.deleteMessage(uid);
                return null;
            }

            @Override protected void done() {
                deletingMessage = false;
                deleteAction.setEnabled(true);
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
        inboxModel.set(index, subject(email.getSubject())); emailList.repaint(); updateNavigation();
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
                    inboxModel.set(index, subject(email.getSubject())); emailList.repaint(); updateNavigation();
                    refreshIfRequested();
                }
            }
        }.execute();
    }
    public Email getEmailFromTabbedPane(int selectedIndex) {
        return visibleMessages.get(selectedIndex).email();
    }

    public void displayEmailContent(Email email) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> {
                if (opened != null && opened.email().getId() == email.getId()) displayEmailContent(email);
            });
            return;
        }
        if (opened != null && opened.email() != email && opened.email().getId() == email.getId()) {
            // The optional legacy server fetches the body only when a mail item opens.
            opened.email().setBody(email.getBody());
            opened.email().setLinks(email.getLinks());
            opened.email().setTransferFrom(email.getTransferFrom());
            opened.email().setSeen(email.isSeen());
            updateNavigation();
        }
        subjectLine.setText(subject(email.getSubject()));
        String sender = correspondent(email.getFrom());
        senderLine.setText(sender);
        senderAvatar.setText(initial(sender));
        senderLine.setToolTipText(decode(email.getFrom()));
        recipientLine.setText("đến " + correspondent(email.getTo()));
        recipientLine.setToolTipText(decode(email.getTo()));
        dateLine.setText(formatDate(email.getCreatedAt(), true));
        emailContent.setText(MailChrome.messageHtml(email));
        emailContent.setCaretPosition(0);
    }

    private void displayEmailContent() {
        int selection = emailList.getSelectedIndex();
        if (selection >= 0 && selection < visibleMessages.size()) openMessage(visibleMessages.get(selection), true);
    }

    private static String formatDate(String value, boolean full) {
        if (value == null || value.isBlank()) return "";
        try {
            var local = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(ZoneId.systemDefault());
            String pattern = full ? "HH:mm, dd/MM/yyyy" : local.toLocalDate().equals(LocalDate.now()) ? "HH:mm" : "dd/MM";
            return DateTimeFormatter.ofPattern(pattern).format(local);
        } catch (DateTimeParseException ex) {
            return full ? value : "";
        }
    }

    public JEditorPane getEmailContent() { return emailContent; }
    public User getUser() { return user; }
    public DefaultListModel<String> getInboxModel() { return inboxModel; }
    public DefaultListModel<String> getSentModel() { return sentModel; }
    public JList<String> getEmailList() { return emailList; }

    public void setInboxEmails(List<Email> emails) {
        inboxEmails = emails;
        rebuildVisible(true);
    }
    public void setSentEmails(List<Email> emails) {
        sentEmails = emails;
        rebuildVisible(true);
    }
    public List<Email> getSentEmails() { return sentEmails; }
    public List<Email> getInboxEmails() { return inboxEmails; }
}
