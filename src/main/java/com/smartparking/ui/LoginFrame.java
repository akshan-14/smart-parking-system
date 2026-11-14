package com.smartparking.ui;

import com.smartparking.exception.AccountLockedException;
import com.smartparking.exception.AuthException;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.user.User;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.ParkingSlotDao;
import com.smartparking.security.AccessControl;
import com.smartparking.service.AuthService;
import com.smartparking.service.ParkingService;
import com.smartparking.ui.components.ModernButton;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.ImageLoader;
import com.smartparking.util.InputValidator;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Optional;

/**
 * Modern desktop login screen featuring parking facility backdrop,
 * application branding, password visibility toggle, lockout protection,
 * DPDP Act 2023 customer self-registration, and quick demo role selection.
 */
public class LoginFrame extends JFrame {

    private final AuthService authService;
    private ParkingLot parkingLot;
    private ParkingService parkingService;

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JToggleButton eyeToggle;

    public LoginFrame() {
        super("Smart Parking Management System — Login");
        this.authService = new AuthService();

        initBackend();
        initUI();
    }

    private void initBackend() {
        DatabaseInitializer.initializeDatabase();
        this.parkingLot = new ParkingLot("LOT-001", "Grand Tech Park Garage", "100 Innovation Boulevard");
        ParkingSlotDao slotDao = new ParkingSlotDao();
        List<ParkingSlot> allSlots = slotDao.findAll();

        ParkingFloor f1 = new ParkingFloor(1, "Ground Floor - Standard & EV Station");
        ParkingFloor f2 = new ParkingFloor(2, "Level 2 - EV Fast Hub");
        ParkingFloor f3 = new ParkingFloor(3, "Level 3 - Fleet & VIP Roof");

        for (ParkingSlot s : allSlots) {
            switch (s.getFloorNumber()) {
                case 1 -> f1.addSlot(s);
                case 2 -> f2.addSlot(s);
                case 3 -> f3.addSlot(s);
            }
        }

        parkingLot.addFloor(f1);
        parkingLot.addFloor(f2);
        parkingLot.addFloor(f3);

        this.parkingService = ParkingService.createDefault(parkingLot);
    }

    private JLabel errorLabel;
    private ModernButton btnSignIn;

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(960, 620);
        setLocationRelativeTo(null);
        setResizable(false);

        Image appIcon = ImageLoader.loadImage("images/logo_small.png");
        if (appIcon != null) {
            setIconImage(appIcon);
        }

        JPanel mainContainer = new JPanel(new GridLayout(1, 2));
        mainContainer.setBackground(UITheme.BG_APP);
        mainContainer.add(buildBrandPanel());
        mainContainer.add(buildFormPanel());
        setContentPane(mainContainer);
        getRootPane().setDefaultButton(btnSignIn);
    }

    /** Left half: brand, value proposition and a quiet painted parking-grid motif. */
    private JPanel buildBrandPanel() {
        JPanel hero = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g2.setColor(UITheme.BG_APP);
                g2.fillRect(0, 0, w, h);
                // dot grid
                g2.setColor(new Color(255, 255, 255, 10));
                for (int x = 16; x < w; x += 24) for (int y = 16; y < h; y += 24) g2.fillRect(x, y, 2, 2);
                // stylised parking bays at the bottom: thin outlines, a few accent-filled
                int bayW = 46, bayH = 74, gap = 10, ox = 48;
                int cols = Math.max(1, (w - ox * 2 + gap) / (bayW + gap));
                int oy = h - bayH - 48;
                for (int i = 0; i < cols; i++) {
                    int x = ox + i * (bayW + gap);
                    boolean filled = i == 1 || i == 2 || i == 5;
                    boolean ev = i == 6;
                    g2.setColor(filled ? UITheme.alpha(UITheme.ACCENT, 46) : ev ? UITheme.alpha(UITheme.SLOT_EV, 30) : new Color(255, 255, 255, 6));
                    g2.fillRoundRect(x, oy, bayW, bayH, 8, 8);
                    g2.setColor(filled ? UITheme.alpha(UITheme.ACCENT, 140) : ev ? UITheme.alpha(UITheme.SLOT_EV, 120) : new Color(255, 255, 255, 28));
                    g2.drawRoundRect(x, oy, bayW, bayH, 8, 8);
                }
                // right edge separator
                g2.setColor(UITheme.BORDER_SUBTLE);
                g2.drawLine(w - 1, 0, w - 1, h);
                g2.dispose();
            }
        };
        hero.setBorder(BorderFactory.createEmptyBorder(48, 48, 150, 48));

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JPanel brandRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        brandRow.setOpaque(false);
        brandRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandRow.add(new SmartParkingMainFrame.LogoMark(36));
        JLabel brand = new JLabel("Smart Parking");
        brand.setFont(UITheme.font(Font.BOLD, 17));
        brand.setForeground(UITheme.TEXT_PRIMARY);
        brandRow.add(brand);
        col.add(brandRow);
        col.add(Box.createVerticalStrut(40));

        JLabel heroTitle = new JLabel("<html>Run every level,<br>gate and bay from<br>one console.</html>");
        heroTitle.setFont(UITheme.font(Font.BOLD, 30));
        heroTitle.setForeground(UITheme.TEXT_PRIMARY);
        heroTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(heroTitle);
        col.add(Box.createVerticalStrut(14));

        JLabel heroSubtitle = new JLabel("<html>Grand Tech Park Garage &middot; Lucknow, UP</html>");
        heroSubtitle.setFont(UITheme.font(Font.PLAIN, 14));
        heroSubtitle.setForeground(UITheme.TEXT_SECONDARY);
        heroSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(heroSubtitle);
        col.add(Box.createVerticalStrut(28));

        addFeatureBullet(col, "Live bay status across 3 levels");
        addFeatureBullet(col, "GST invoices with UPI, card and FASTag");
        addFeatureBullet(col, "Role-based access and tamper-evident audit log");

        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.weightx = 1;
        gc.weighty = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        hero.add(col, gc);
        return hero;
    }

    /** Right half: sign-in form with inline validation and demo shortcuts. */
    private JPanel buildFormPanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(UITheme.BG_CANVAS);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        JLabel loginTitle = new JLabel("Sign in");
        loginTitle.setFont(UITheme.font(Font.BOLD, 24));
        loginTitle.setForeground(UITheme.TEXT_PRIMARY);
        form.add(loginTitle, c);

        c.gridy++;
        c.insets = new Insets(6, 0, 28, 0);
        JLabel loginSub = new JLabel("Use your operator or customer account.");
        loginSub.setFont(UITheme.font(Font.PLAIN, 13));
        loginSub.setForeground(UITheme.TEXT_SECONDARY);
        form.add(loginSub, c);

        // Username
        c.gridy++;
        c.insets = new Insets(0, 0, 6, 0);
        form.add(fieldLabel("Username"), c);
        c.gridy++;
        c.insets = new Insets(0, 0, 18, 0);
        usernameField = new JTextField("admin");
        UITheme.styleTextField(usernameField);
        usernameField.setPreferredSize(new Dimension(320, 40));
        usernameField.getAccessibleContext().setAccessibleName("Username");
        form.add(usernameField, c);

        // Password
        c.gridy++;
        c.insets = new Insets(0, 0, 6, 0);
        form.add(fieldLabel("Password"), c);
        c.gridy++;
        c.insets = new Insets(0, 0, 8, 0);
        passwordField = new JPasswordField("Admin@123");
        UITheme.styleTextField(passwordField);
        passwordField.getAccessibleContext().setAccessibleName("Password");

        eyeToggle = new JToggleButton(com.smartparking.ui.util.VectorIconUtil.getIcon(
                com.smartparking.ui.util.VectorIconUtil.IconType.EYE, 16, UITheme.TEXT_TERTIARY));
        eyeToggle.setSelectedIcon(com.smartparking.ui.util.VectorIconUtil.getIcon(
                com.smartparking.ui.util.VectorIconUtil.IconType.EYE, 16, UITheme.ACCENT_TEXT));
        eyeToggle.setToolTipText("Show / hide password");
        eyeToggle.getAccessibleContext().setAccessibleName("Show password");
        eyeToggle.setFocusPainted(false);
        eyeToggle.setBorderPainted(false);
        eyeToggle.setContentAreaFilled(false);
        eyeToggle.setOpaque(false);
        eyeToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        eyeToggle.setPreferredSize(new Dimension(36, 36));
        eyeToggle.addActionListener(e -> passwordField.setEchoChar(eyeToggle.isSelected() ? (char) 0 : '\u2022'));

        JPanel passBox = new JPanel(new BorderLayout());
        passBox.setOpaque(true);
        passBox.setBackground(UITheme.BG_INPUT);
        passBox.setBorder(new com.smartparking.ui.theme.DarkTheme.RoundBorder(UITheme.BORDER, UITheme.RADIUS));
        passwordField.setBorder(BorderFactory.createEmptyBorder(8, 11, 8, 4));
        passwordField.setOpaque(false);
        passBox.add(passwordField, BorderLayout.CENTER);
        passBox.add(eyeToggle, BorderLayout.EAST);
        passBox.setPreferredSize(new Dimension(320, 40));
        passwordField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { passBox.repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent e) { passBox.repaint(); }
        });
        form.add(passBox, c);

        // Inline error / validation feedback
        c.gridy++;
        c.insets = new Insets(0, 0, 14, 0);
        errorLabel = new JLabel(" ");
        errorLabel.setFont(UITheme.font(Font.PLAIN, 12));
        errorLabel.setForeground(UITheme.ERROR_TEXT);
        errorLabel.setIcon(null);
        form.add(errorLabel, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 16, 0);
        btnSignIn = new ModernButton("Sign in", null);
        btnSignIn.setFont(UITheme.font(Font.BOLD, 14));
        btnSignIn.setPreferredSize(new Dimension(320, 42));
        btnSignIn.addActionListener(e -> performLogin());
        form.add(btnSignIn, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 30, 0);
        JPanel regRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        regRow.setOpaque(false);
        JLabel newHere = new JLabel("New customer?");
        newHere.setFont(UITheme.font(Font.PLAIN, 13));
        newHere.setForeground(UITheme.TEXT_SECONDARY);
        JButton btnRegister = new JButton("Create an account");
        btnRegister.setFont(UITheme.font(Font.BOLD, 13));
        btnRegister.setForeground(UITheme.ACCENT_TEXT);
        btnRegister.setBorderPainted(false);
        btnRegister.setContentAreaFilled(false);
        btnRegister.setFocusPainted(false);
        btnRegister.setMargin(new Insets(0, 0, 0, 0));
        btnRegister.setBorder(BorderFactory.createEmptyBorder());
        btnRegister.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnRegister.addActionListener(e -> showCustomerRegistrationDialog());
        regRow.add(newHere);
        regRow.add(btnRegister);
        form.add(regRow, c);

        // Divider with caption
        c.gridy++;
        c.insets = new Insets(0, 0, 12, 0);
        JPanel divider = new JPanel(new GridBagLayout());
        divider.setOpaque(false);
        GridBagConstraints d = new GridBagConstraints();
        d.fill = GridBagConstraints.HORIZONTAL;
        d.weightx = 1;
        divider.add(hairline(), d);
        d.weightx = 0;
        d.insets = new Insets(0, 10, 0, 10);
        JLabel quickLbl = new JLabel("DEMO ACCOUNTS");
        quickLbl.setFont(UITheme.FONT_CAPTION);
        quickLbl.setForeground(UITheme.TEXT_TERTIARY);
        divider.add(quickLbl, d);
        d.weightx = 1;
        d.insets = new Insets(0, 0, 0, 0);
        divider.add(hairline(), d);
        form.add(divider, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        JPanel quickGrid = new JPanel(new GridLayout(1, 3, 8, 0));
        quickGrid.setOpaque(false);
        quickGrid.add(demoButton("Admin", "admin", "Admin@123"));
        quickGrid.add(demoButton("Attendant", "attendant1", "Attendant@123"));
        quickGrid.add(demoButton("Customer", "customer1", "Customer@123"));
        form.add(quickGrid, c);

        outer.add(form, new GridBagConstraints());
        return outer;
    }

    private ModernButton demoButton(String text, String user, String pass) {
        ModernButton b = ModernButton.createSecondary(text, null).compact();
        b.setToolTipText("Fill in the " + text.toLowerCase() + " demo credentials");
        b.addActionListener(e -> {
            usernameField.setText(user);
            passwordField.setText(pass);
            clearError();
            passwordField.requestFocusInWindow();
        });
        return b;
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UITheme.font(Font.BOLD, 12));
        l.setForeground(UITheme.TEXT_BODY);
        return l;
    }

    private static JComponent hairline() {
        JPanel line = new JPanel();
        line.setBackground(UITheme.BORDER_SUBTLE);
        line.setPreferredSize(new Dimension(10, 1));
        return line;
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setIcon(com.smartparking.ui.util.VectorIconUtil.getIcon(
                com.smartparking.ui.util.VectorIconUtil.IconType.ALERT, 13, UITheme.ERROR_TEXT));
    }

    private void clearError() {
        errorLabel.setText(" ");
        errorLabel.setIcon(null);
    }

    private void addFeatureBullet(JPanel panel, String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(text, com.smartparking.ui.util.VectorIconUtil.getIcon(
                com.smartparking.ui.util.VectorIconUtil.IconType.CHECK, 14, UITheme.ACCENT_TEXT), SwingConstants.LEFT);
        lbl.setIconTextGap(12);
        lbl.setFont(UITheme.font(Font.PLAIN, 14));
        lbl.setForeground(UITheme.TEXT_BODY);
        row.add(lbl);
        panel.add(row);
    }

    /** Asks a flagged user for a new password; returns true only if the password was actually changed. */
    @FunctionalInterface
    interface ForcedPasswordChangePrompt {
        boolean changePassword(User user, String currentPassword);
    }

    /** Replaceable in tests so the modal dialog does not block; production uses the Swing dialog. */
    ForcedPasswordChangePrompt forcedPasswordChangePrompt = this::showForcePasswordChangeDialog;

    /** Opens the main window for a fully authorised user. Replaceable in tests. */
    java.util.function.Consumer<User> mainWindowOpener = user -> {
        SmartParkingMainFrame mainFrame = new SmartParkingMainFrame(user, parkingLot, parkingService);
        mainFrame.setVisible(true);
    };

    private void performLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError(username.isEmpty() ? "Enter your username." : "Enter your password.");
            (username.isEmpty() ? usernameField : passwordField).requestFocusInWindow();
            return;
        }
        clearError();
        attemptLogin(username, password);
    }

    /**
     * Signs in and opens the main window only for a user with full access. A user flagged for a forced password
     * change must complete it first; cancelling (or failing) signs them out and keeps them on this login screen.
     * Returns true when the main window is being opened.
     */
    boolean attemptLogin(String username, String password) {
        try {
            Optional<User> userOpt = authService.authenticate(username, password);
            if (userOpt.isEmpty()) {
                return false;
            }
            User user = userOpt.get();

            if (AccessControl.requiresPasswordChange(user)) {
                boolean changed = forcedPasswordChangePrompt.changePassword(user, password);
                if (!changed || AccessControl.requiresPasswordChange(user)) {
                    authService.logout();
                    passwordField.setText("");
                    showError("You must set a new password before you can continue. Sign in again to retry.");
                    passwordField.requestFocusInWindow();
                    return false;
                }
            }

            AccessControl.requireFullAccess(user); // same rule the main window and the console enforce
            dispose();
            SwingUtilities.invokeLater(() -> mainWindowOpener.accept(user));
            return true;
        } catch (AccountLockedException ale) {
            showError(ale.getMessage());
        } catch (AuthException ae) {
            showError(ae.getMessage());
            passwordField.selectAll();
            passwordField.requestFocusInWindow();
        } catch (Exception ex) {
            authService.logout();
            showError("Sign-in failed: " + ex.getMessage());
        }
        return false;
    }

    /** Returns true only when the password was changed; Cancel or closing the dialog returns false. */
    private boolean showForcePasswordChangeDialog(User user, String oldPassword) {
        JPasswordField newPassField = new JPasswordField(15);
        JPasswordField confirmField = new JPasswordField(15);

        JPanel p = new JPanel(new GridLayout(5, 1, 4, 4));
        p.add(new JLabel("You must change your password before using the system."));
        p.add(new JLabel("New Password (min 8 characters, at least 1 letter and 1 digit):"));
        p.add(newPassField);
        p.add(new JLabel("Confirm New Password:"));
        p.add(confirmField);

        while (true) {
            int res = JOptionPane.showConfirmDialog(this, p, "Password Change Required", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (res != JOptionPane.OK_OPTION) {
                return false;
            }

            String newPwd = new String(newPassField.getPassword());
            String confirm = new String(confirmField.getPassword());

            if (!newPwd.equals(confirm)) {
                JOptionPane.showMessageDialog(this, "Passwords do not match. Please re-enter.", "Mismatch", JOptionPane.ERROR_MESSAGE);
                continue;
            }

            try {
                authService.completeForcedPasswordChange(user, oldPassword, newPwd);
                JOptionPane.showMessageDialog(this, "Password successfully updated!", "Success", JOptionPane.INFORMATION_MESSAGE);
                return true;
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Password rejected: " + e.getMessage(), "Validation Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showCustomerRegistrationDialog() {
        JTextField nameField = new JTextField(15);
        JTextField mobileField = new JTextField(15);
        JTextField userField = new JTextField(15);
        JPasswordField passField = new JPasswordField(15);
        JCheckBox consentBox = new JCheckBox("I agree to the collection and use of my mobile number and vehicle number for parking services.");
        consentBox.setFont(UITheme.font(Font.PLAIN, 11));

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(new JLabel("Full Name:"));
        p.add(nameField);
        p.add(Box.createVerticalStrut(6));
        p.add(new JLabel("Indian Mobile (10 digits starting with 6-9):"));
        p.add(mobileField);
        p.add(Box.createVerticalStrut(6));
        p.add(new JLabel("Choose Username:"));
        p.add(userField);
        p.add(Box.createVerticalStrut(6));
        p.add(new JLabel("Password (min 8 chars, 1 letter, 1 digit):"));
        p.add(passField);
        p.add(Box.createVerticalStrut(10));
        p.add(consentBox);

        int res = JOptionPane.showConfirmDialog(this, p, "Customer Self-Registration (DPDP Act, 2023)", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            try {
                String name = nameField.getText().trim();
                String mobile = mobileField.getText().trim();
                String uName = userField.getText().trim();
                String pwd = new String(passField.getPassword());
                boolean consent = consentBox.isSelected();

                authService.registerCustomer(name, mobile, uName, pwd, consent);
                JOptionPane.showMessageDialog(this,
                        "Registration successful! You may now log in with your credentials.",
                        "Registered", JOptionPane.INFORMATION_MESSAGE);
                usernameField.setText(uName);
                passwordField.setText(pwd);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Registration failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
