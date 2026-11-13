package com.smartparking.ui;

import com.smartparking.model.enums.UserRole;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.user.User;
import com.smartparking.security.AccessControl;
import com.smartparking.security.UserSession;
import com.smartparking.service.AuthService;
import com.smartparking.service.MembershipService;
import com.smartparking.service.ParkingService;
import com.smartparking.service.ReservationService;
import com.smartparking.ui.components.ModernButton;
import com.smartparking.ui.panels.*;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.ImageLoader;
import com.smartparking.ui.util.VectorIconUtil;
import com.smartparking.ui.util.VectorIconUtil.IconType;
import com.smartparking.util.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application shell.
 * <ul>
 *   <li><b>Sidebar</b> (248px, collapses to 68px below 900px window width): brand, RBAC-filtered
 *       navigation grouped into Operations / Administration / Account, capacity footer.</li>
 *   <li><b>Top bar</b> (56px): breadcrumb of the current screen, language toggle, live status pill,
 *       user chip (initials avatar, name, role) and Sign Out.</li>
 *   <li><b>Content</b>: CardLayout with every screen; panels refresh on arrival.</li>
 *   <li><b>Session management</b>: inactivity tracking with warning and automatic logout.</li>
 * </ul>
 */
public class SmartParkingMainFrame extends JFrame {

    private static final int SIDEBAR_WIDTH = 248;
    private static final int SIDEBAR_COLLAPSED = 68;
    private static final Color SIDEBAR_HOVER = UITheme.blend(Color.WHITE, UITheme.BG_APP, 0.045);
    private static final Color SIDEBAR_ACTIVE = UITheme.blend(UITheme.ACCENT, UITheme.BG_APP, 0.16);

    private final User currentUser;
    private final ParkingLot parkingLot;
    private final ParkingService parkingService;
    private final ReservationService reservationService;
    private final MembershipService membershipService;

    // Card Layout container
    private JPanel cardsPanel;
    private CardLayout cardLayout;

    // Sub-panels
    private DashboardPanel dashboardPanel;
    private FloorMapPanel floorMapPanel;
    private VehicleEntryPanel entryPanel;
    private VehicleExitPanel exitPanel;
    private ReservationPanel reservationPanel;
    private MembershipPanel membershipPanel;
    private VehicleSearchPanel searchPanel;
    private AdminReportsPanel reportsPanel;
    private AuditLogViewerPanel auditLogPanel;
    private UserManagementPanel userManagementPanel;
    private PrivacyNoticePanel privacyNoticePanel;
    private CustomerDataPanel customerDataPanel;
    private SettingsPanel settingsPanel;

    // Navigation tracking
    private final Map<String, NavButton> navButtons = new HashMap<>();
    private final java.util.List<JComponent> sectionLabels = new java.util.ArrayList<>();
    private String currentCard = "DASHBOARD";
    private javax.swing.Timer sessionTimer;

    // Shell widgets that change with navigation / collapse
    private JLabel breadcrumbPage;
    private JPanel sidebarPanel;
    private JPanel brandText;
    private JPanel sidebarFooter;
    private boolean isSidebarCollapsed = false;

    public SmartParkingMainFrame(User currentUser, ParkingLot parkingLot, ParkingService parkingService) {
        super("Smart Parking System - Grand Tech Park Garage");
        this.currentUser = currentUser;
        this.parkingLot = parkingLot;
        this.parkingService = parkingService;
        this.reservationService = new ReservationService();
        this.membershipService = new MembershipService();

        if (currentUser != null) {
            UserSession.getInstance().startSession(currentUser, 15);
        }

        initUI();
        initSessionTracking();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1360, 860);
        setMinimumSize(new Dimension(1080, 700));
        setLocationRelativeTo(null);
        getContentPane().setBackground(UITheme.BG_APP);

        Image appIcon = ImageLoader.loadImage("images/logo_small.png");
        if (appIcon != null) {
            setIconImage(appIcon);
        }

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UITheme.BG_APP);

        root.add(buildSidebar(), BorderLayout.WEST);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(UITheme.BG_CANVAS);
        main.add(buildHeader(), BorderLayout.NORTH);

        cardLayout = new CardLayout();
        cardsPanel = new JPanel(cardLayout);
        cardsPanel.setBackground(UITheme.BG_CANVAS);

        Runnable onStateChange = this::handleStateChange;

        dashboardPanel = new DashboardPanel(parkingLot, parkingService, this::navigateTo);
        floorMapPanel = new FloorMapPanel(parkingLot, parkingService, this::navigateTo);
        entryPanel = new VehicleEntryPanel(parkingService, onStateChange);
        exitPanel = new VehicleExitPanel(parkingService, onStateChange);
        reservationPanel = new ReservationPanel(reservationService, onStateChange);
        membershipPanel = new MembershipPanel(membershipService);
        searchPanel = new VehicleSearchPanel(parkingService, this::navigateTo);
        reportsPanel = new AdminReportsPanel(parkingLot, onStateChange);
        auditLogPanel = new AuditLogViewerPanel();
        userManagementPanel = new UserManagementPanel();
        privacyNoticePanel = new PrivacyNoticePanel();
        customerDataPanel = new CustomerDataPanel();
        settingsPanel = new SettingsPanel();

        cardsPanel.add(dashboardPanel, "DASHBOARD");
        cardsPanel.add(floorMapPanel, "FLOORS");
        cardsPanel.add(entryPanel, "ENTRY");
        cardsPanel.add(exitPanel, "EXIT");
        cardsPanel.add(reservationPanel, "RESERVATIONS");
        cardsPanel.add(membershipPanel, "MEMBERSHIP");
        cardsPanel.add(searchPanel, "SEARCH");
        cardsPanel.add(reportsPanel, "REPORTS");
        cardsPanel.add(auditLogPanel, "AUDIT_LOG");
        cardsPanel.add(userManagementPanel, "USERS");
        cardsPanel.add(settingsPanel, "SETTINGS");
        cardsPanel.add(privacyNoticePanel, "PRIVACY");
        cardsPanel.add(customerDataPanel, "MY_DATA");

        main.add(cardsPanel, BorderLayout.CENTER);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);

        // Keyboard shortcuts: Ctrl+1..9 jump to the first nine permitted screens
        installNavShortcuts(root);

        String initialCard = AccessControl.isScreenAllowed(currentUser.getRole(), "DASHBOARD") ? "DASHBOARD" : "RESERVATIONS";
        navigateTo(initialCard);
    }

    private void installNavShortcuts(JComponent root) {
        int i = 1;
        for (String key : navOrder.keySet()) {
            if (!navButtons.containsKey(key) || i > 9) continue;
            final String target = key;
            KeyStroke ks = KeyStroke.getKeyStroke("ctrl " + i);
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, "nav-" + key);
            root.getActionMap().put("nav-" + key, new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    navigateTo(target);
                }
            });
            NavButton b = navButtons.get(key);
            b.setShortcutHint("Ctrl+" + i);
            i++;
        }
    }

    private void initSessionTracking() {
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(event -> UserSession.getInstance().recordActivity(),
                    AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        } catch (SecurityException ignored) {
        }

        UserSession.getInstance().addListener(new UserSession.SessionListener() {
            @Override
            public void onWarning(int minutesRemaining) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(SmartParkingMainFrame.this,
                        "Your session will expire in 1 minute due to inactivity.",
                        "Session Timeout Warning",
                        JOptionPane.WARNING_MESSAGE));
            }

            @Override
            public void onTimeout() {
                SwingUtilities.invokeLater(() -> {
                    if (sessionTimer != null) sessionTimer.stop();
                    dispose();
                    JOptionPane.showMessageDialog(null,
                            "Session expired due to inactivity. Please sign in again.",
                            "Session Expired",
                            JOptionPane.INFORMATION_MESSAGE);
                    new LoginFrame().setVisible(true);
                });
            }
        });

        sessionTimer = new javax.swing.Timer(10000, e -> UserSession.getInstance().checkInactivity());
        sessionTimer.start();
    }

    // =====================================================================================
    // Top bar
    // =====================================================================================
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.BG_APP);
        header.setPreferredSize(new Dimension(10, 56));
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, UITheme.BORDER_SUBTLE),
                new EmptyBorder(0, 24, 0, 16)));

        // Left: breadcrumb
        JPanel crumbs = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        crumbs.setOpaque(false);
        JLabel facility = new JLabel("Grand Tech Park Garage");
        facility.setFont(UITheme.font(Font.PLAIN, 13));
        facility.setForeground(UITheme.TEXT_TERTIARY);
        JLabel slash = new JLabel("/");
        slash.setFont(UITheme.font(Font.PLAIN, 13));
        slash.setForeground(UITheme.BORDER_STRONG);
        breadcrumbPage = new JLabel("Overview");
        breadcrumbPage.setFont(UITheme.font(Font.BOLD, 13));
        breadcrumbPage.setForeground(UITheme.TEXT_PRIMARY);
        crumbs.add(facility);
        crumbs.add(slash);
        crumbs.add(breadcrumbPage);
        JPanel crumbWrap = new JPanel(new GridBagLayout());
        crumbWrap.setOpaque(false);
        crumbWrap.add(crumbs);
        header.add(crumbWrap, BorderLayout.WEST);

        // Right: actions
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        // Language toggle (ghost button)
        JButton btnLang = new JButton(I18n.isHindi() ? "हिंदी" : "English");
        btnLang.setFont(I18n.getFont(Font.BOLD, 12));
        btnLang.setForeground(UITheme.TEXT_SECONDARY);
        btnLang.setBackground(UITheme.BG_APP);
        btnLang.setFocusPainted(false);
        btnLang.setToolTipText("Switch interface language (English / हिंदी)");
        btnLang.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnLang.setBorder(new EmptyBorder(6, 10, 6, 10));
        btnLang.setIcon(VectorIconUtil.getIcon(IconType.GLOBE, 14, UITheme.TEXT_SECONDARY));
        btnLang.setIconTextGap(6);
        btnLang.addActionListener(e -> {
            if (I18n.isHindi()) {
                I18n.setLocale(I18n.EN);
                btnLang.setText("English");
            } else {
                I18n.setLocale(I18n.HI);
                btnLang.setText("हिंदी");
            }
            btnLang.setFont(I18n.getFont(Font.BOLD, 12));
            NavButton active = navButtons.get(currentCard);
            if (active != null) breadcrumbPage.setText(active.getNavText());
        });
        right.add(wrapCenter(btnLang));

        // Live status pill
        right.add(wrapCenter(new StatusPill(I18n.get("nav.system_active"))));

        right.add(wrapCenter(verticalDivider()));

        // User chip
        right.add(wrapCenter(buildUserChip()));

        // Sign out (ghost)
        ModernButton btnSignOut = new ModernButton(I18n.get("nav.signout"),
                VectorIconUtil.getIcon(IconType.LOGOUT, 14, UITheme.TEXT_SECONDARY),
                UITheme.BG_APP, UITheme.BG_ELEVATED, UITheme.TEXT_SECONDARY);
        btnSignOut.setFont(UITheme.font(Font.BOLD, 12));
        btnSignOut.setBorder(new EmptyBorder(7, 12, 7, 12));
        btnSignOut.setIconTextGap(7);
        btnSignOut.setToolTipText("Sign out of this session");
        btnSignOut.addActionListener(e -> logout());
        right.add(wrapCenter(btnSignOut));

        header.add(right, BorderLayout.EAST);
        return header;
    }

    private static JComponent wrapCenter(JComponent c) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.add(c);
        p.setPreferredSize(new Dimension(c.getPreferredSize().width, 56));
        return p;
    }

    private static JComponent verticalDivider() {
        JPanel d = new JPanel();
        d.setBackground(UITheme.BORDER);
        d.setPreferredSize(new Dimension(1, 22));
        return d;
    }

    private JComponent buildUserChip() {
        String displayName = (currentUser.getFullName() != null && !currentUser.getFullName().isBlank())
                ? currentUser.getFullName()
                : (currentUser.getRole() == UserRole.ADMIN ? "System Administrator" : currentUser.getUsername());

        JPanel chip = new JPanel(new BorderLayout(10, 0));
        chip.setOpaque(false);

        Color roleColor = switch (currentUser.getRole()) {
            case ADMIN -> UITheme.ACCENT;
            case ATTENDANT -> UITheme.SLOT_EV;
            default -> UITheme.SUCCESS;
        };
        chip.add(new Avatar(initials(displayName), roleColor), BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(displayName);
        name.setFont(UITheme.font(Font.BOLD, 12));
        name.setForeground(UITheme.TEXT_PRIMARY);
        String roleLabel = switch (currentUser.getRole()) {
            case ADMIN -> "Administrator";
            case ATTENDANT -> "Gate Attendant";
            default -> "Customer";
        };
        JLabel role = new JLabel(roleLabel);
        role.setFont(UITheme.font(Font.PLAIN, 11));
        role.setForeground(UITheme.TEXT_TERTIARY);
        text.add(name);
        text.add(role);
        chip.add(text, BorderLayout.CENTER);
        chip.setToolTipText("Signed in as " + currentUser.getUsername() + " (" + currentUser.getRole().name() + ")");
        return chip;
    }

    private static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        String s = parts.length == 0 || parts[0].isEmpty() ? "?" : parts[0].substring(0, 1);
        if (parts.length > 1 && !parts[parts.length - 1].isEmpty()) s += parts[parts.length - 1].substring(0, 1);
        return s.toUpperCase();
    }

    // =====================================================================================
    // Sidebar
    // =====================================================================================
    /** Card key -> (i18n key or literal label, icon, section). Order = sidebar order. */
    private final Map<String, Object[]> navOrder = new LinkedHashMap<>();

    {
        navOrder.put("DASHBOARD", new Object[]{"nav.overview", IconType.DASHBOARD, "Operations"});
        navOrder.put("FLOORS", new Object[]{"nav.floors", IconType.MAP, "Operations"});
        navOrder.put("ENTRY", new Object[]{"nav.entry", IconType.TICKET, "Operations"});
        navOrder.put("EXIT", new Object[]{"nav.exit", IconType.PAYMENT, "Operations"});
        navOrder.put("RESERVATIONS", new Object[]{"nav.reservations", IconType.CALENDAR, "Operations"});
        navOrder.put("MEMBERSHIP", new Object[]{"nav.membership", IconType.MEMBERSHIP, "Operations"});
        navOrder.put("SEARCH", new Object[]{"nav.search", IconType.SEARCH, "Operations"});
        navOrder.put("REPORTS", new Object[]{"nav.reports", IconType.REPORTS, "Administration"});
        navOrder.put("AUDIT_LOG", new Object[]{"=Security Audit Trail", IconType.SHIELD, "Administration"});
        navOrder.put("USERS", new Object[]{"=User Management", IconType.USERS, "Administration"});
        navOrder.put("SETTINGS", new Object[]{"nav.settings", IconType.SETTINGS, "Administration"});
        navOrder.put("MY_DATA", new Object[]{"=My Data & Privacy", IconType.USER, "Account"});
        navOrder.put("PRIVACY", new Object[]{"=Privacy Notice (DPDP)", IconType.DOCUMENT, "Account"});
    }

    private static String label(String keyOrLiteral) {
        return keyOrLiteral.startsWith("=") ? keyOrLiteral.substring(1) : I18n.get(keyOrLiteral);
    }

    private JPanel buildSidebar() {
        sidebarPanel = new JPanel(new BorderLayout());
        sidebarPanel.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 10));
        sidebarPanel.setBackground(UITheme.BG_APP);
        sidebarPanel.setBorder(new MatteBorder(0, 0, 0, 1, UITheme.BORDER_SUBTLE));

        // Brand
        JPanel brand = new JPanel(new BorderLayout(12, 0));
        brand.setOpaque(false);
        brand.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, UITheme.BORDER_SUBTLE),
                new EmptyBorder(0, 18, 0, 14)));
        brand.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 56));
        JPanel logoWrap = new JPanel(new GridBagLayout());
        logoWrap.setOpaque(false);
        logoWrap.add(new LogoMark(30));
        brand.add(logoWrap, BorderLayout.WEST);
        brandText = new JPanel(new GridBagLayout());
        brandText.setOpaque(false);
        JPanel bt = new JPanel();
        bt.setOpaque(false);
        bt.setLayout(new BoxLayout(bt, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Smart Parking");
        title.setFont(UITheme.font(Font.BOLD, 14));
        title.setForeground(UITheme.TEXT_PRIMARY);
        JLabel sub = new JLabel("Operations Console");
        sub.setFont(UITheme.font(Font.PLAIN, 11));
        sub.setForeground(UITheme.TEXT_TERTIARY);
        bt.add(title);
        bt.add(sub);
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.WEST;
        gc.weightx = 1;
        brandText.add(bt, gc);
        brand.add(brandText, BorderLayout.CENTER);
        sidebarPanel.add(brand, BorderLayout.NORTH);

        // Navigation
        JPanel menuList = new JPanel();
        menuList.setLayout(new BoxLayout(menuList, BoxLayout.Y_AXIS));
        menuList.setOpaque(false);
        menuList.setBorder(new EmptyBorder(14, 12, 12, 12));

        String section = null;
        for (Map.Entry<String, Object[]> e : navOrder.entrySet()) {
            String key = e.getKey();
            if (!AccessControl.isScreenAllowed(currentUser.getRole(), key)) continue;
            String sec = (String) e.getValue()[2];
            if (!sec.equals(section)) {
                if (section != null) menuList.add(Box.createVerticalStrut(14));
                menuList.add(sectionLabel(sec));
                menuList.add(Box.createVerticalStrut(4));
                section = sec;
            }
            addNavButton(menuList, key, label((String) e.getValue()[0]), (IconType) e.getValue()[1]);
        }

        JScrollPane navScroll = new JScrollPane(menuList);
        navScroll.setBorder(null);
        navScroll.setOpaque(false);
        navScroll.getViewport().setOpaque(false);
        navScroll.getViewport().setBackground(UITheme.BG_APP);
        navScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        navScroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel navHolder = new JPanel(new BorderLayout());
        navHolder.setOpaque(false);
        navHolder.add(menuList, BorderLayout.NORTH);
        navScroll.setViewportView(navHolder);
        sidebarPanel.add(navScroll, BorderLayout.CENTER);

        // Footer: live capacity
        sidebarFooter = new JPanel();
        sidebarFooter.setLayout(new BoxLayout(sidebarFooter, BoxLayout.Y_AXIS));
        sidebarFooter.setOpaque(false);
        sidebarFooter.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, UITheme.BORDER_SUBTLE),
                new EmptyBorder(14, 18, 16, 18)));
        JLabel capCaption = new JLabel("FACILITY CAPACITY");
        capCaption.setFont(UITheme.font(Font.BOLD, 10));
        capCaption.setForeground(UITheme.TEXT_TERTIARY);
        JLabel capText = new JLabel(parkingLot.getTotalCapacity() + " bays  ·  " + parkingLot.getFloors().size() + " levels");
        capText.setFont(UITheme.font(Font.BOLD, 12));
        capText.setForeground(UITheme.TEXT_BODY);
        JLabel verText = new JLabel("v1.0.0  ·  Java 17 · SQLite");
        verText.setFont(UITheme.font(Font.PLAIN, 11));
        verText.setForeground(UITheme.TEXT_TERTIARY);
        sidebarFooter.add(capCaption);
        sidebarFooter.add(Box.createVerticalStrut(4));
        sidebarFooter.add(capText);
        sidebarFooter.add(Box.createVerticalStrut(2));
        sidebarFooter.add(verText);
        sidebarPanel.add(sidebarFooter, BorderLayout.SOUTH);

        // Dynamic i18n updater for sidebar items
        I18n.addListener(() -> {
            for (Map.Entry<String, Object[]> e : navOrder.entrySet()) {
                String k = (String) e.getValue()[0];
                if (!k.startsWith("=")) updateNavLabel(e.getKey(), k);
            }
        });

        // Responsive collapse (<900px)
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                setSidebarCollapsed(getWidth() < 900);
            }
        });

        return sidebarPanel;
    }

    private JComponent sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(UITheme.font(Font.BOLD, 10));
        l.setForeground(UITheme.TEXT_TERTIARY);
        l.setBorder(new EmptyBorder(0, 10, 0, 0));
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(l);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionLabels.add(p);
        return p;
    }

    private void updateNavLabel(String cardKey, String i18nKey) {
        NavButton b = navButtons.get(cardKey);
        if (b != null) {
            b.setNavText(I18n.get(i18nKey));
        }
    }

    private void addNavButton(JPanel container, String cardKey, String text, IconType iconType) {
        NavButton btn = new NavButton(cardKey, text, iconType);
        btn.addActionListener(e -> navigateTo(cardKey));
        navButtons.put(cardKey, btn);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.add(btn);
        container.add(Box.createVerticalStrut(2));
    }

    private void setSidebarCollapsed(boolean collapsed) {
        if (this.isSidebarCollapsed == collapsed) return;
        this.isSidebarCollapsed = collapsed;
        if (sidebarPanel != null) {
            sidebarPanel.setPreferredSize(new Dimension(collapsed ? SIDEBAR_COLLAPSED : SIDEBAR_WIDTH, 10));
            brandText.setVisible(!collapsed);
            sidebarFooter.setVisible(!collapsed);
            for (JComponent s : sectionLabels) s.setVisible(!collapsed);
            for (NavButton btn : navButtons.values()) {
                btn.setCollapsed(collapsed);
            }
            sidebarPanel.revalidate();
            sidebarPanel.repaint();
        }
    }

    // =====================================================================================
    // Navigation
    // =====================================================================================
    public void navigateTo(String cardKey) {
        if (cardKey == null || cardKey.isBlank()) return;

        // Support direct slot highlighting navigation e.g. "FLOORS:A15"
        if (cardKey.toUpperCase().startsWith("FLOORS:")) {
            String slotId = cardKey.substring("FLOORS:".length()).trim();
            this.currentCard = "FLOORS";
            cardLayout.show(cardsPanel, "FLOORS");
            updateSidebarStyles("FLOORS");
            floorMapPanel.highlightSlot(slotId);
            return;
        }

        // Deep link into checkout with a ticket number / plate, e.g. "EXIT:TKT-123"
        if (cardKey.toUpperCase().startsWith("EXIT:")) {
            String query = cardKey.substring("EXIT:".length()).trim();
            navigateTo("EXIT");
            if ("EXIT".equals(currentCard)) exitPanel.prefillLookup(query);
            return;
        }

        // Central RBAC Check
        if (!AccessControl.isScreenAllowed(currentUser.getRole(), cardKey)) {
            JOptionPane.showMessageDialog(this,
                    "Access Denied: Role [" + currentUser.getRole() + "] is not authorized to access " + cardKey + ".",
                    "Security Notification",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        this.currentCard = cardKey;
        cardLayout.show(cardsPanel, cardKey);
        updateSidebarStyles(cardKey);

        // Dynamic panel refresh on arrival
        switch (cardKey.toUpperCase()) {
            case "DASHBOARD" -> dashboardPanel.refreshData();
            case "FLOORS" -> floorMapPanel.renderFloorGrid();
            case "RESERVATIONS" -> reservationPanel.refreshTable();
            case "REPORTS" -> {
                if (reportsPanel != null) {
                    reportsPanel.refreshReport();
                }
            }
            case "AUDIT_LOG" -> {
                if (auditLogPanel != null) {
                    auditLogPanel.refreshData();
                }
            }
            case "USERS" -> {
                if (userManagementPanel != null) {
                    userManagementPanel.refreshData();
                }
            }
            case "MY_DATA" -> {
                if (customerDataPanel != null) {
                    customerDataPanel.refreshData();
                }
            }
        }
    }

    private void updateSidebarStyles(String activeCardKey) {
        for (Map.Entry<String, NavButton> entry : navButtons.entrySet()) {
            NavButton btn = entry.getValue();
            boolean isActive = entry.getKey().equalsIgnoreCase(activeCardKey);
            btn.setActive(isActive);
            if (isActive && breadcrumbPage != null) breadcrumbPage.setText(btn.getNavText());
        }
    }

    private void handleStateChange() {
        dashboardPanel.refreshData();
        floorMapPanel.renderFloorGrid();
        reservationPanel.refreshTable();
        if (reportsPanel != null) {
            reportsPanel.refreshReport();
        }
    }

    private void logout() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to sign out of the current session?",
                "Confirm Sign Out",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            if (sessionTimer != null) {
                sessionTimer.stop();
            }
            new AuthService().logout();
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        }
    }

    // =====================================================================================
    // Shell components
    // =====================================================================================

    /** Sidebar navigation item: monochrome icon, accent indicator + tinted pill when active. */
    private static class NavButton extends JButton {
        private final String cardKey;
        private final IconType iconType;
        private String originalText;
        private String shortcutHint;
        private boolean isActive = false;
        private boolean isHovered = false;
        private boolean isCollapsed = false;

        NavButton(String cardKey, String text, IconType iconType) {
            super(text);
            this.cardKey = cardKey;
            this.iconType = iconType;
            this.originalText = text;

            setFont(I18n.getFont(Font.PLAIN, 13));
            setHorizontalAlignment(SwingConstants.LEFT);
            setIconTextGap(12);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(new EmptyBorder(0, 12, 0, 10));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            setPreferredSize(new Dimension(SIDEBAR_WIDTH - 24, 36));
            applyColors();

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    isHovered = true;
                    applyColors();
                    repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    isHovered = false;
                    applyColors();
                    repaint();
                }
            });
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusGained(java.awt.event.FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    repaint();
                }
            });
        }

        String getNavText() {
            return originalText;
        }

        void setShortcutHint(String hint) {
            this.shortcutHint = hint;
            setToolTipText(originalText + "  (" + hint + ")");
        }

        void setNavText(String text) {
            this.originalText = text;
            setFont(I18n.getFont(isActive ? Font.BOLD : Font.PLAIN, 13));
            if (!isCollapsed) {
                setText(text);
            }
            if (shortcutHint != null) setToolTipText(text + "  (" + shortcutHint + ")");
        }

        void setActive(boolean active) {
            this.isActive = active;
            setFont(I18n.getFont(active ? Font.BOLD : Font.PLAIN, 13));
            applyColors();
            repaint();
        }

        private void applyColors() {
            Color fg = isActive ? UITheme.TEXT_PRIMARY : (isHovered ? UITheme.TEXT_PRIMARY : UITheme.TEXT_SECONDARY);
            Color ic = isActive ? UITheme.ACCENT_TEXT : (isHovered ? UITheme.TEXT_BODY : UITheme.TEXT_TERTIARY);
            setForeground(fg);
            setIcon(VectorIconUtil.getIcon(iconType, 16, ic));
        }

        void setCollapsed(boolean collapsed) {
            this.isCollapsed = collapsed;
            if (collapsed) {
                setText("");
                setHorizontalAlignment(SwingConstants.CENTER);
                setBorder(new EmptyBorder(0, 0, 0, 0));
                setPreferredSize(new Dimension(44, 36));
                setToolTipText(originalText);
            } else {
                setText(originalText);
                setHorizontalAlignment(SwingConstants.LEFT);
                setBorder(new EmptyBorder(0, 12, 0, 10));
                setPreferredSize(new Dimension(SIDEBAR_WIDTH - 24, 36));
                setToolTipText(shortcutHint != null ? originalText + "  (" + shortcutHint + ")" : null);
            }
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isActive) {
                g2.setColor(SIDEBAR_ACTIVE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), UITheme.RADIUS, UITheme.RADIUS);
                g2.setColor(UITheme.ACCENT);
                g2.fillRoundRect(0, 9, 3, getHeight() - 18, 3, 3);
            } else if (isHovered) {
                g2.setColor(SIDEBAR_HOVER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), UITheme.RADIUS, UITheme.RADIUS);
            }
            if (isFocusOwner() && com.smartparking.ui.theme.DarkTheme.isFocusVisible()) {
                g2.setColor(UITheme.alpha(UITheme.ACCENT, 150));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, UITheme.RADIUS, UITheme.RADIUS);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Brand mark: rounded indigo tile with a geometric "P". */
    static class LogoMark extends JComponent {
        private final int size;

        LogoMark(int size) {
            this.size = size;
            setPreferredSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            float s = size;
            g2.setPaint(new GradientPaint(0, 0, UITheme.ACCENT_HOVER, s, s, UITheme.ACCENT_PRESSED));
            g2.fill(new java.awt.geom.RoundRectangle2D.Float(0, 0, s, s, s * 0.32f, s * 0.32f));
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(s * 0.11f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
            p.moveTo(s * 0.36f, s * 0.76f);
            p.lineTo(s * 0.36f, s * 0.26f);
            p.lineTo(s * 0.55f, s * 0.26f);
            p.curveTo(s * 0.73f, s * 0.26f, s * 0.73f, s * 0.54f, s * 0.55f, s * 0.54f);
            p.lineTo(s * 0.36f, s * 0.54f);
            g2.draw(p);
            g2.dispose();
        }
    }

    /** Initials avatar tinted with the role colour. */
    private static class Avatar extends JComponent {
        private final String text;
        private final Color color;

        Avatar(String text, Color color) {
            this.text = text;
            this.color = color;
            setPreferredSize(new Dimension(30, 30));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(UITheme.blend(color, UITheme.BG_APP, 0.22));
            g2.fillOval(0, 0, 30, 30);
            g2.setColor(UITheme.blend(color, UITheme.BG_APP, 0.5));
            g2.drawOval(0, 0, 29, 29);
            g2.setFont(UITheme.font(Font.BOLD, 11));
            g2.setColor(UITheme.blend(color, Color.WHITE, 0.35));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (30 - fm.stringWidth(text)) / 2f, 15 + (fm.getAscent() - fm.getDescent()) / 2f);
            g2.dispose();
        }
    }

    /** "System active" pill with a softly pulsing dot. */
    private static class StatusPill extends JComponent {
        private final String text;
        private float phase = 0f;

        StatusPill(String text) {
            this.text = text;
            setFont(UITheme.font(Font.BOLD, 11));
            FontMetrics fm = getFontMetrics(getFont());
            setPreferredSize(new Dimension(fm.stringWidth(text) + 34, 24));
            setToolTipText("Database connected · services running");
            javax.swing.Timer t = new javax.swing.Timer(80, e -> {
                phase = (phase + 0.06f) % 1f;
                repaint();
            });
            addHierarchyListener(e -> {
                if (isShowing()) t.start();
                else t.stop();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(UITheme.blend(UITheme.SUCCESS, UITheme.BG_APP, 0.10));
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setColor(UITheme.blend(UITheme.SUCCESS, UITheme.BG_APP, 0.30));
            g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
            int cx = 13, cy = h / 2;
            float pulse = (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2));
            g2.setColor(UITheme.alpha(UITheme.SUCCESS, (int) (40 + 50 * pulse)));
            int r = (int) (5 + 2 * pulse);
            g2.fillOval(cx - r, cy - r, r * 2, r * 2);
            g2.setColor(UITheme.SUCCESS_TEXT);
            g2.fillOval(cx - 3, cy - 3, 6, 6);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, 24, cy + (fm.getAscent() - fm.getDescent()) / 2f);
            g2.dispose();
        }
    }
}
