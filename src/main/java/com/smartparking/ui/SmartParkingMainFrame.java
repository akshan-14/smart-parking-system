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

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.util.HashMap;
import java.util.Map;

/**
 * Main application window faithfully matching the modern Tailwind-based dashboard:
 * - TopBar (#0B1329): Blue logo tile, facility address, pulsing "SYSTEM ACTIVE" capsule,
 *   admin profile badge with role tag, and blue "Sign Out" action.
 * - Sidebar Navigation (#0D1527, 240px): Dynamically filtered by RBAC permissions,
 *   high-contrast links with specialized icon accents, active blue pill highlight, divider.
 * - Center CardLayout: Seamless switching between allowed screens.
 * - Session Management: Automatic inactivity detection, warning toast, and logout timer.
 */
public class SmartParkingMainFrame extends JFrame {

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
    private String currentCard = "DASHBOARD";
    private javax.swing.Timer sessionTimer;

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
        setSize(1320, 840);
        setMinimumSize(new Dimension(1080, 700));
        setLocationRelativeTo(null);

        // Window Icon
        Image appIcon = ImageLoader.loadImage("images/logo_small.png");
        if (appIcon != null) {
            setIconImage(appIcon);
        }

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UITheme.BG_CANVAS);

        // 1. Header (North)
        root.add(buildHeader(), BorderLayout.NORTH);

        // 2. Sidebar Navigation (West)
        root.add(buildSidebar(), BorderLayout.WEST);

        // 3. Central Card Deck (Center)
        cardLayout = new CardLayout();
        cardsPanel = new JPanel(cardLayout);
        cardsPanel.setBackground(UITheme.BG_CANVAS);

        // Build Panels with state synchronization
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

        root.add(cardsPanel, BorderLayout.CENTER);
        setContentPane(root);

        // Select initial card based on RBAC allowance
        String initialCard = AccessControl.isScreenAllowed(currentUser.getRole(), "DASHBOARD") ? "DASHBOARD" : "RESERVATIONS";
        navigateTo(initialCard);
    }

    private void initSessionTracking() {
        // Record user activity on any mouse or keyboard input
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
                UserSession.getInstance().recordActivity();
            }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        } catch (SecurityException ignored) {
        }

        UserSession.getInstance().addListener(new UserSession.SessionListener() {
            @Override
            public void onWarning(int minutesRemaining) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(SmartParkingMainFrame.this,
                            "Your session will expire in 1 minute due to inactivity.",
                            "Session Timeout Warning",
                            JOptionPane.WARNING_MESSAGE);
                });
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

    /**
     * Top persistent header bar (#0B1329) matching the HTML Tailwind TopBar:
     * - Left: Blue brand square with parking "P" contour, title, subtitle.
     * - Right: Operational pill, profile badge, and blue Sign Out button.
     */
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.TOPBAR_BG);
        header.setPreferredSize(new Dimension(getWidth(), 56));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 41, 59)),
                BorderFactory.createEmptyBorder(8, 18, 8, 18)
        ));

        // Left Branding
        JPanel brandPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        brandPanel.setOpaque(false);

        JPanel logoTile = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UITheme.PRIMARY_BLUE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g2.drawRoundRect(cx - 9, cy - 9, 18, 18, 4, 4);
                g2.drawLine(cx - 3, cy + 5, cx - 3, cy - 5);
                g2.drawArc(cx - 3, cy - 5, 8, 6, -90, 180);
                g2.dispose();
            }
        };
        logoTile.setPreferredSize(new Dimension(36, 36));
        logoTile.setOpaque(false);
        brandPanel.add(logoTile);

        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);

        JLabel mainTitle = new JLabel("Smart Parking System");
        mainTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        mainTitle.setForeground(Color.WHITE);

        JLabel subTitle = new JLabel("Grand Tech Park Garage • 100 Innovation Blvd");
        subTitle.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subTitle.setForeground(new Color(148, 163, 184));

        titles.add(mainTitle);
        titles.add(Box.createVerticalStrut(2));
        titles.add(subTitle);
        brandPanel.add(titles);
        header.add(brandPanel, BorderLayout.WEST);

        // Right Header Actions
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.setOpaque(false);

        // Language Toggle
        JButton btnLang = new JButton(com.smartparking.util.I18n.isHindi() ? "हिंदी | EN" : "EN | हिंदी");
        btnLang.setFont(com.smartparking.util.I18n.getFont(Font.BOLD, 11));
        btnLang.setForeground(new Color(226, 232, 240));
        btnLang.setBackground(new Color(30, 41, 59));
        btnLang.setFocusPainted(false);
        btnLang.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLang.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(71, 85, 105), 1, true),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        btnLang.setIcon(VectorIconUtil.getIcon(VectorIconUtil.IconType.GLOBE, 13, new Color(148, 163, 184)));
        btnLang.addActionListener(e -> {
            if (com.smartparking.util.I18n.isHindi()) {
                com.smartparking.util.I18n.setLocale(com.smartparking.util.I18n.EN);
                btnLang.setText("EN | हिंदी");
            } else {
                com.smartparking.util.I18n.setLocale(com.smartparking.util.I18n.HI);
                btnLang.setText("हिंदी | EN");
            }
        });
        rightPanel.add(btnLang);

        // Operational Pill
        JPanel statusPill = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(6, 78, 59, 140));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(new Color(16, 185, 129, 160));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        statusPill.setOpaque(false);
        JLabel dot = new JLabel("●");
        dot.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        dot.setForeground(new Color(52, 211, 153));
        JLabel statusText = new JLabel("SYSTEM ACTIVE");
        statusText.setFont(new Font("Segoe UI", Font.BOLD, 10));
        statusText.setForeground(new Color(52, 211, 153));
        statusPill.add(dot);
        statusPill.add(statusText);
        rightPanel.add(statusPill);

        // User Profile Tag with Role Badge
        JPanel userTag = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        userTag.setOpaque(false);
        JLabel userIcon = new JLabel(VectorIconUtil.getIcon(VectorIconUtil.IconType.USER, 13, new Color(148, 163, 184)));
        userTag.add(userIcon);

        String displayName = (currentUser.getFullName() != null && !currentUser.getFullName().isBlank())
                ? currentUser.getFullName() : (currentUser.getRole() == UserRole.ADMIN ? "System Administrator" : currentUser.getUsername());
        JLabel userName = new JLabel(displayName);
        userName.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        userName.setForeground(new Color(226, 232, 240));
        userTag.add(userName);

        JLabel roleBadge = new JLabel(currentUser.getRole().name());
        roleBadge.setFont(new Font("Consolas", Font.BOLD, 10));
        if (currentUser.getRole() == UserRole.ADMIN) {
            roleBadge.setForeground(new Color(248, 113, 113));
            roleBadge.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(153, 27, 27), 1, true),
                    BorderFactory.createEmptyBorder(1, 4, 1, 4)
            ));
        } else if (currentUser.getRole() == UserRole.ATTENDANT) {
            roleBadge.setForeground(new Color(96, 165, 250));
            roleBadge.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(30, 64, 175), 1, true),
                    BorderFactory.createEmptyBorder(1, 4, 1, 4)
            ));
        } else {
            roleBadge.setForeground(new Color(52, 211, 153));
            roleBadge.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(6, 95, 70), 1, true),
                    BorderFactory.createEmptyBorder(1, 4, 1, 4)
            ));
        }
        userTag.add(roleBadge);
        rightPanel.add(userTag);

        // Blue Sign Out Button
        ModernButton btnSignOut = new ModernButton("Sign Out", VectorIconUtil.getIcon(VectorIconUtil.IconType.LOGOUT, 14, Color.WHITE),
                UITheme.PRIMARY_BLUE, UITheme.PRIMARY_HOVER, Color.WHITE);
        btnSignOut.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSignOut.setPreferredSize(new Dimension(125, 34));
        btnSignOut.addActionListener(e -> logout());
        rightPanel.add(btnSignOut);

        header.add(rightPanel, BorderLayout.EAST);
        return header;
    }

    /**
     * Sidebar menu dynamically constructed from Role-Based Access Control (RBAC) permissions.
     */
    private JPanel buildSidebar() {
        sidebarPanel = new JPanel(new BorderLayout());
        sidebarPanel.setPreferredSize(new Dimension(240, getHeight()));
        sidebarPanel.setBackground(UITheme.SIDEBAR_BG);
        sidebarPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(30, 41, 59)));

        JPanel menuList = new JPanel();
        menuList.setLayout(new BoxLayout(menuList, BoxLayout.Y_AXIS));
        menuList.setOpaque(false);
        menuList.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));

        // Core Operational Links (Filtered by Role)
        if (AccessControl.isScreenAllowed(currentUser.getRole(), "DASHBOARD")) {
            addNavButton(menuList, "DASHBOARD", com.smartparking.util.I18n.get("nav.overview"), VectorIconUtil.IconType.DASHBOARD, new Color(255, 255, 255));
        }
        if (AccessControl.isScreenAllowed(currentUser.getRole(), "FLOORS")) {
            addNavButton(menuList, "FLOORS", com.smartparking.util.I18n.get("nav.floors"), VectorIconUtil.IconType.MAP, new Color(148, 163, 184));
        }
        if (AccessControl.isScreenAllowed(currentUser.getRole(), "ENTRY")) {
            addNavButton(menuList, "ENTRY", com.smartparking.util.I18n.get("nav.entry"), VectorIconUtil.IconType.TICKET, new Color(52, 211, 153));
        }
        if (AccessControl.isScreenAllowed(currentUser.getRole(), "EXIT")) {
            addNavButton(menuList, "EXIT", com.smartparking.util.I18n.get("nav.exit"), VectorIconUtil.IconType.PAYMENT, new Color(248, 113, 113));
        }
        if (AccessControl.isScreenAllowed(currentUser.getRole(), "RESERVATIONS")) {
            addNavButton(menuList, "RESERVATIONS", com.smartparking.util.I18n.get("nav.reservations"), VectorIconUtil.IconType.CLOCK, new Color(251, 191, 36));
        }
        if (AccessControl.isScreenAllowed(currentUser.getRole(), "MEMBERSHIP")) {
            addNavButton(menuList, "MEMBERSHIP", com.smartparking.util.I18n.get("nav.membership"), VectorIconUtil.IconType.MEMBERSHIP, new Color(253, 224, 71));
        }
        if (AccessControl.isScreenAllowed(currentUser.getRole(), "SEARCH")) {
            addNavButton(menuList, "SEARCH", com.smartparking.util.I18n.get("nav.search"), VectorIconUtil.IconType.SEARCH, new Color(34, 211, 238));
        }

        // Divider
        menuList.add(Box.createVerticalStrut(6));
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(30, 41, 59));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        menuList.add(sep);
        menuList.add(Box.createVerticalStrut(6));

        // Advanced Administrative / Customer Privacy Links
        if (AccessControl.isScreenAllowed(currentUser.getRole(), "REPORTS")) {
            addNavButton(menuList, "REPORTS", com.smartparking.util.I18n.get("nav.reports"), VectorIconUtil.IconType.REPORTS, new Color(129, 140, 248));
        }
        if (AccessControl.isScreenAllowed(currentUser.getRole(), "AUDIT_LOG")) {
            addNavButton(menuList, "AUDIT_LOG", "Security Audit Trail", VectorIconUtil.IconType.USER, new Color(56, 189, 248));
        }
        if (AccessControl.isScreenAllowed(currentUser.getRole(), "USERS")) {
            addNavButton(menuList, "USERS", "User Management", VectorIconUtil.IconType.USER, new Color(167, 139, 250));
        }
        if (AccessControl.isScreenAllowed(currentUser.getRole(), "SETTINGS")) {
            addNavButton(menuList, "SETTINGS", com.smartparking.util.I18n.get("nav.settings"), VectorIconUtil.IconType.SETTINGS, new Color(245, 158, 11));
        }
        if (AccessControl.isScreenAllowed(currentUser.getRole(), "MY_DATA")) {
            addNavButton(menuList, "MY_DATA", "My Data & Privacy", VectorIconUtil.IconType.USER, new Color(96, 165, 250));
        }
        if (AccessControl.isScreenAllowed(currentUser.getRole(), "PRIVACY")) {
            addNavButton(menuList, "PRIVACY", "Privacy Notice (DPDP)", VectorIconUtil.IconType.ALERT, new Color(244, 114, 182));
        }

        sidebarPanel.add(menuList, BorderLayout.NORTH);

        // Bottom Metadata Badge
        JPanel bottomBadge = new JPanel();
        bottomBadge.setLayout(new BoxLayout(bottomBadge, BoxLayout.Y_AXIS));
        bottomBadge.setBackground(new Color(11, 17, 33));
        bottomBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(30, 41, 59)),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JLabel capText = new JLabel(parkingLot.getTotalCapacity() + " Total Capacity");
        capText.setFont(new Font("Segoe UI", Font.BOLD, 11));
        capText.setForeground(new Color(203, 213, 225));

        JLabel verText = new JLabel("v1.0.0 • Java 17 / SQLite");
        verText.setFont(new Font("Consolas", Font.PLAIN, 10));
        verText.setForeground(new Color(148, 163, 184));

        bottomBadge.add(capText);
        bottomBadge.add(Box.createVerticalStrut(2));
        bottomBadge.add(verText);

        sidebarPanel.add(bottomBadge, BorderLayout.SOUTH);

        // Dynamic i18n updater for sidebar items
        com.smartparking.util.I18n.addListener(() -> {
            updateNavLabel("DASHBOARD", "nav.overview");
            updateNavLabel("FLOORS", "nav.floors");
            updateNavLabel("ENTRY", "nav.entry");
            updateNavLabel("EXIT", "nav.exit");
            updateNavLabel("RESERVATIONS", "nav.reservations");
            updateNavLabel("MEMBERSHIP", "nav.membership");
            updateNavLabel("SEARCH", "nav.search");
            updateNavLabel("REPORTS", "nav.reports");
            updateNavLabel("SETTINGS", "nav.settings");
        });

        // Add component listener for responsive collapsing (<900px)
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                setSidebarCollapsed(getWidth() < 900);
            }
        });

        return sidebarPanel;
    }

    private void updateNavLabel(String cardKey, String i18nKey) {
        NavButton b = navButtons.get(cardKey);
        if (b != null) {
            b.setNavText(com.smartparking.util.I18n.get(i18nKey));
        }
    }

    private JPanel sidebarPanel;
    private boolean isSidebarCollapsed = false;

    private void addNavButton(JPanel container, String cardKey, String text, VectorIconUtil.IconType iconType, Color iconColor) {
        Icon icon = VectorIconUtil.getIcon(iconType, 16, iconColor);
        NavButton btn = new NavButton(cardKey, text, icon);
        btn.addActionListener(e -> navigateTo(cardKey));
        navButtons.put(cardKey, btn);
        container.add(btn);
        container.add(Box.createVerticalStrut(4));
    }

    private void setSidebarCollapsed(boolean collapsed) {
        if (this.isSidebarCollapsed == collapsed) return;
        this.isSidebarCollapsed = collapsed;
        if (sidebarPanel != null) {
            sidebarPanel.setPreferredSize(new Dimension(collapsed ? 68 : 240, getHeight()));
            for (NavButton btn : navButtons.values()) {
                btn.setCollapsed(collapsed);
            }
            sidebarPanel.revalidate();
            sidebarPanel.repaint();
        }
    }

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
                "Are you sure you want to log out of the current session?",
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

    /**
     * Custom styled navigation button matching Tailwind active/hover pill styles.
     */
    private static class NavButton extends JButton {
        private final String cardKey;
        private String originalText;
        private boolean isActive = false;
        private boolean isHovered = false;
        private boolean isCollapsed = false;

        public NavButton(String cardKey, String text, Icon icon) {
            super(text, icon);
            this.cardKey = cardKey;
            this.originalText = text;

            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setForeground(new Color(148, 163, 184));
            setHorizontalAlignment(SwingConstants.LEFT);
            setIconTextGap(12);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            setPreferredSize(new Dimension(216, 38));

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    isHovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    isHovered = false;
                    repaint();
                }
            });
        }

        public void setNavText(String text) {
            this.originalText = text;
            if (!isCollapsed) {
                setText(text);
            }
        }

        public void setActive(boolean active) {
            this.isActive = active;
            repaint();
        }

        public void setCollapsed(boolean collapsed) {
            this.isCollapsed = collapsed;
            if (collapsed) {
                setText("");
                setHorizontalAlignment(SwingConstants.CENTER);
                setPreferredSize(new Dimension(48, 38));
                setToolTipText(originalText);
            } else {
                setText(originalText);
                setHorizontalAlignment(SwingConstants.LEFT);
                setPreferredSize(new Dimension(216, 38));
                setToolTipText(null);
            }
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (isActive) {
                g2.setColor(new Color(30, 58, 138, 220));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(96, 165, 250));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                setForeground(Color.WHITE);
            } else if (isHovered) {
                g2.setColor(new Color(30, 41, 59, 180));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                setForeground(new Color(241, 245, 249));
            } else {
                setForeground(new Color(148, 163, 184));
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
