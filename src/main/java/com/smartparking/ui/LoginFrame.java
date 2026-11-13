package com.smartparking.ui;

import com.smartparking.exception.AccountLockedException;
import com.smartparking.exception.AuthException;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.user.User;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.ParkingSlotDao;
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

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(940, 600);
        setLocationRelativeTo(null);
        setResizable(false);

        // App Icon
        Image appIcon = ImageLoader.loadImage("images/logo_small.png");
        if (appIcon != null) {
            setIconImage(appIcon);
        }

        JPanel mainContainer = new JPanel(new GridLayout(1, 2));

        // --- LEFT HERO PANEL with Parking Background Image ---
        JPanel heroPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                Image bg = ImageLoader.loadImage("images/parking_background.png");
                if (bg != null) {
                    g2.drawImage(bg, 0, 0, getWidth(), getHeight(), null);
                }
                // Gradient overlay
                GradientPaint gp = new GradientPaint(0, 0, new Color(15, 23, 42, 230), 0, getHeight(), new Color(30, 58, 138, 240));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        heroPanel.setLayout(new BoxLayout(heroPanel, BoxLayout.Y_AXIS));
        heroPanel.setBorder(BorderFactory.createEmptyBorder(50, 40, 50, 40));

        JLabel logoLabel = new JLabel(ImageLoader.loadIcon("images/logo.png", 96, 96));
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        heroPanel.add(logoLabel);
        heroPanel.add(Box.createVerticalStrut(20));

        JLabel heroTitle = new JLabel("Smart Parking");
        heroTitle.setFont(new Font("Segoe UI", Font.BOLD, 28));
        heroTitle.setForeground(Color.WHITE);
        heroTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        heroPanel.add(heroTitle);

        JLabel heroSubtitle = new JLabel("Intelligent Facility Management (UP / India)");
        heroSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        heroSubtitle.setForeground(new Color(148, 163, 184));
        heroSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        heroPanel.add(heroSubtitle);
        heroPanel.add(Box.createVerticalStrut(30));

        // Feature Bullets
        addFeatureBullet(heroPanel, "150 Unified Slots with Neutral Bay Codes");
        addFeatureBullet(heroPanel, "Automated UP / Bharat Plate ANPR (Simulated)");
        addFeatureBullet(heroPanel, "GST Compliant Invoices & UPI / FASTag");
        addFeatureBullet(heroPanel, "DPDP Act, 2023 Privacy & Consent Controls");
        addFeatureBullet(heroPanel, "Role-Based Security & Append-Only Audit Trail");

        mainContainer.add(heroPanel);

        // --- RIGHT LOGIN CARD ---
        JPanel loginPanel = new JPanel();
        loginPanel.setLayout(new BoxLayout(loginPanel, BoxLayout.Y_AXIS));
        loginPanel.setBackground(Color.WHITE);
        loginPanel.setBorder(BorderFactory.createEmptyBorder(35, 45, 35, 45));

        JLabel loginTitle = new JLabel("Welcome Back");
        loginTitle.setFont(new Font("Segoe UI", Font.BOLD, 24));
        loginTitle.setForeground(UITheme.TEXT_DARK);
        loginPanel.add(loginTitle);

        JLabel loginSub = new JLabel("Sign in with authorized system credentials");
        loginSub.setFont(UITheme.FONT_REGULAR);
        loginSub.setForeground(UITheme.TEXT_MUTED);
        loginPanel.add(loginSub);
        loginPanel.add(Box.createVerticalStrut(20));

        // Username
        JPanel userRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        userRow.setOpaque(false);
        userRow.add(new JLabel(ImageLoader.getAuthIcon("user", 16)));
        JLabel uLbl = new JLabel("Username:");
        uLbl.setFont(UITheme.FONT_BOLD);
        userRow.add(uLbl);
        loginPanel.add(userRow);
        loginPanel.add(Box.createVerticalStrut(4));

        usernameField = new JTextField("admin");
        usernameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        UITheme.styleTextField(usernameField);
        loginPanel.add(usernameField);
        loginPanel.add(Box.createVerticalStrut(14));

        // Password Row
        JPanel passRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        passRow.setOpaque(false);
        passRow.add(new JLabel(ImageLoader.getAuthIcon("password", 16)));
        JLabel pLbl = new JLabel("Password:");
        pLbl.setFont(UITheme.FONT_BOLD);
        passRow.add(pLbl);
        loginPanel.add(passRow);
        loginPanel.add(Box.createVerticalStrut(4));

        // Password Field with Show/Hide toggle
        JPanel passBox = new JPanel(new BorderLayout(4, 0));
        passBox.setOpaque(false);
        passBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        passwordField = new JPasswordField("Admin@123");
        UITheme.styleTextField(passwordField);

        eyeToggle = new JToggleButton("👁");
        eyeToggle.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 12));
        eyeToggle.setToolTipText("Show / Hide Password");
        eyeToggle.setFocusPainted(false);
        eyeToggle.setBackground(new Color(241, 245, 249));
        eyeToggle.addActionListener(e -> {
            if (eyeToggle.isSelected()) {
                passwordField.setEchoChar((char) 0);
            } else {
                passwordField.setEchoChar('•');
            }
        });

        passBox.add(passwordField, BorderLayout.CENTER);
        passBox.add(eyeToggle, BorderLayout.EAST);
        loginPanel.add(passBox);
        loginPanel.add(Box.createVerticalStrut(20));

        // Sign In Button
        ModernButton btnSignIn = new ModernButton("Sign In to System", ImageLoader.getAuthIcon("shield", 18));
        btnSignIn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnSignIn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnSignIn.addActionListener(e -> performLogin());
        loginPanel.add(btnSignIn);
        loginPanel.add(Box.createVerticalStrut(14));

        // Customer Self-Registration Link
        JPanel regRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        regRow.setOpaque(false);
        JButton btnRegister = new JButton("New Customer? Register here →");
        btnRegister.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnRegister.setForeground(new Color(37, 99, 235));
        btnRegister.setBorderPainted(false);
        btnRegister.setContentAreaFilled(false);
        btnRegister.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnRegister.addActionListener(e -> showCustomerRegistrationDialog());
        regRow.add(btnRegister);
        loginPanel.add(regRow);
        loginPanel.add(Box.createVerticalStrut(14));

        // Quick Role Login Shortcuts
        JLabel quickLbl = new JLabel("Quick Demo Role Previews:");
        quickLbl.setFont(UITheme.FONT_SMALL);
        quickLbl.setForeground(UITheme.TEXT_MUTED);
        loginPanel.add(quickLbl);
        loginPanel.add(Box.createVerticalStrut(6));

        JPanel quickGrid = new JPanel(new GridLayout(1, 3, 8, 0));
        quickGrid.setOpaque(false);
        quickGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JButton qAdmin = new JButton("Admin");
        qAdmin.setFont(UITheme.FONT_SMALL);
        qAdmin.addActionListener(e -> { usernameField.setText("admin"); passwordField.setText("Admin@123"); });

        JButton qAttendant = new JButton("Attendant");
        qAttendant.setFont(UITheme.FONT_SMALL);
        qAttendant.addActionListener(e -> { usernameField.setText("attendant1"); passwordField.setText("Attendant@123"); });

        JButton qCustomer = new JButton("Customer");
        qCustomer.setFont(UITheme.FONT_SMALL);
        qCustomer.addActionListener(e -> { usernameField.setText("customer1"); passwordField.setText("Customer@123"); });

        quickGrid.add(qAdmin);
        quickGrid.add(qAttendant);
        quickGrid.add(qCustomer);
        loginPanel.add(quickGrid);

        mainContainer.add(loginPanel);
        setContentPane(mainContainer);
    }

    private void addFeatureBullet(JPanel panel, String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        row.setOpaque(false);
        JLabel check = new JLabel("✓");
        check.setFont(new Font("Segoe UI", Font.BOLD, 14));
        check.setForeground(new Color(56, 189, 248));
        JLabel lbl = new JLabel(text);
        lbl.setFont(UITheme.FONT_REGULAR);
        lbl.setForeground(new Color(226, 232, 240));
        row.add(check);
        row.add(lbl);
        panel.add(row);
    }

    private void performLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter both username and password.", "Login", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Optional<User> userOpt = authService.authenticate(username, password);
            if (userOpt.isPresent()) {
                User user = userOpt.get();

                // Force password change on first administrator login
                if (user.isForcePasswordChange()) {
                    showForcePasswordChangeDialog(user, password);
                }

                dispose();
                SwingUtilities.invokeLater(() -> {
                    SmartParkingMainFrame mainFrame = new SmartParkingMainFrame(user, parkingLot, parkingService);
                    mainFrame.setVisible(true);
                });
            }
        } catch (AccountLockedException ale) {
            JOptionPane.showMessageDialog(this, ale.getMessage(), "Account Locked", JOptionPane.ERROR_MESSAGE);
        } catch (AuthException ae) {
            JOptionPane.showMessageDialog(this, ae.getMessage(), "Authentication Failed", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Login error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showForcePasswordChangeDialog(User user, String oldPassword) {
        JPasswordField newPassField = new JPasswordField(15);
        JPasswordField confirmField = new JPasswordField(15);

        JPanel p = new JPanel(new GridLayout(4, 1, 4, 4));
        p.add(new JLabel("First-time Login: You must change your default administrator password."));
        p.add(new JLabel("New Password (min 8 characters, at least 1 letter and 1 digit):"));
        p.add(newPassField);
        p.add(confirmField);

        boolean updated = false;
        while (!updated) {
            int res = JOptionPane.showConfirmDialog(this, p, "Update Initial Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (res != JOptionPane.OK_OPTION) {
                break;
            }

            String newPwd = new String(newPassField.getPassword());
            String confirm = new String(confirmField.getPassword());

            if (!newPwd.equals(confirm)) {
                JOptionPane.showMessageDialog(this, "Passwords do not match. Please re-enter.", "Mismatch", JOptionPane.ERROR_MESSAGE);
                continue;
            }

            try {
                authService.changePassword(user.getUserId(), oldPassword, newPwd);
                user.setForcePasswordChange(false);
                JOptionPane.showMessageDialog(this, "Administrator password successfully updated!", "Success", JOptionPane.INFORMATION_MESSAGE);
                updated = true;
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
        consentBox.setFont(new Font("Segoe UI", Font.PLAIN, 11));

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
