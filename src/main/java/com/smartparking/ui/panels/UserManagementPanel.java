package com.smartparking.ui.panels;

import com.smartparking.model.enums.UserRole;
import com.smartparking.model.user.User;
import com.smartparking.repository.UserDao;
import com.smartparking.security.AccessControl;
import com.smartparking.security.Permission;
import com.smartparking.service.AuthService;
import com.smartparking.ui.components.ModernButton;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.util.DateFormat;
import com.smartparking.util.InputValidator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Administrative panel for managing user accounts, role assignments,
 * security lockouts, and credential resets.
 */
public class UserManagementPanel extends JPanel {

    private final UserDao userDao;
    private final AuthService authService;
    private final JTable userTable;
    private final DefaultTableModel tableModel;

    public UserManagementPanel() {
        this.userDao = new UserDao();
        this.authService = new AuthService();

        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_CANVAS);
        setBorder(new EmptyBorder(24, 28, 24, 28));


        // Center Table
        String[] columns = {"User ID", "Username", "Full Name", "Mobile (Masked)", "Role", "Status", "Failed Attempts", "Last Login"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        userTable = new JTable(tableModel);
        userTable.setRowHeight(32);
        userTable.setFont(UITheme.font(Font.PLAIN, 12));
        userTable.getTableHeader().setFont(UITheme.font(Font.BOLD, 12));
        userTable.getTableHeader().setBackground(UITheme.BG_ELEVATED);
        userTable.getTableHeader().setForeground(UITheme.TEXT_PRIMARY);
        userTable.setSelectionBackground(UITheme.BG_HOVER);
        userTable.setSelectionForeground(UITheme.TEXT_PRIMARY);

        // Status column: shared status-pill renderer
        userTable.getColumnModel().getColumn(5).setCellRenderer(new com.smartparking.ui.components.Badge.Renderer());
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(userTable);
        scrollPane.setBorder(new com.smartparking.ui.theme.DarkTheme.RoundBorder(UITheme.BORDER_SUBTLE, UITheme.RADIUS));
        scrollPane.getViewport().setBackground(UITheme.BG_CARD);

        add(scrollPane, BorderLayout.CENTER);
        add(buildHeader(), BorderLayout.NORTH);


        // Load eagerly only for roles allowed to see this screen (the main window builds every panel up front;
        // without this guard a customer sign-in threw SecurityException). refreshData() still enforces RBAC.
        if (AccessControl.hasPermission(Permission.MANAGE_USERS)) refreshData();
    }

    private JPanel buildHeader() {
        com.smartparking.ui.components.PageHeader header = new com.smartparking.ui.components.PageHeader(
                "Users & access", "Operator and customer accounts, roles and sign-in status");

        ModernButton btnToggle = ModernButton.createSecondary("Activate / deactivate", null);
        btnToggle.setToolTipText("Enable or disable the selected account");
        btnToggle.addActionListener(e -> toggleUserStatus());
        ModernButton btnResetPw = ModernButton.createSecondary("Reset password",
                com.smartparking.ui.util.VectorIconUtil.getIcon(com.smartparking.ui.util.VectorIconUtil.IconType.SHIELD, 14, UITheme.TEXT_SECONDARY));
        btnResetPw.addActionListener(e -> showResetPasswordDialog());
        ModernButton btnCreate = new ModernButton("New user",
                com.smartparking.ui.util.VectorIconUtil.getIcon(com.smartparking.ui.util.VectorIconUtil.IconType.PLUS, 13, Color.WHITE));
        btnCreate.addActionListener(e -> showCreateUserDialog());

        btnToggle.setEnabled(false);
        btnResetPw.setEnabled(false);
        userTable.getSelectionModel().addListSelectionListener(e -> {
            boolean sel = userTable.getSelectedRow() >= 0;
            btnToggle.setEnabled(sel);
            btnResetPw.setEnabled(sel);
        });

        header.addAction(btnToggle);
        header.addAction(btnResetPw);
        header.addAction(btnCreate);
        return header;
    }

    public void refreshData() {
        AccessControl.checkPermission(Permission.MANAGE_USERS);

        tableModel.setRowCount(0);
        List<User> users = userDao.findAll();
        for (User u : users) {
            String maskedMobile = u.getMobile() != null && u.getMobile().length() == 10
                    ? u.getMobile().substring(0, 2) + "XXXXX" + u.getMobile().substring(7)
                    : (u.getMobile() != null ? u.getMobile() : "—");

            String lastLogin = u.getLastLoginAt() != null ? DateFormat.display(u.getLastLoginAt()) : "Never";

            tableModel.addRow(new Object[]{
                    u.getUserId(),
                    u.getUsername(),
                    u.getFullName(),
                    maskedMobile,
                    u.getRole().name(),
                    u.isActive() ? "ACTIVE" : "DISABLED",
                    u.getFailedAttempts(),
                    lastLogin
            });
        }
    }

    private void showCreateUserDialog() {
        JTextField nameField = new JTextField(15);
        JTextField mobileField = new JTextField(15);
        JTextField usernameField = new JTextField(15);
        JPasswordField passwordField = new JPasswordField(15);
        JComboBox<UserRole> roleCombo = new JComboBox<>(new UserRole[]{UserRole.ATTENDANT, UserRole.ADMIN});
        JTextField extraField = new JTextField(15);

        JPanel panel = new JPanel(new GridLayout(6, 2, 8, 8));
        panel.add(new JLabel("Full Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Mobile (10-digits, starts 6-9):"));
        panel.add(mobileField);
        panel.add(new JLabel("Username:"));
        panel.add(usernameField);
        panel.add(new JLabel("Password (min 8 chars, 1 letter, 1 digit):"));
        panel.add(passwordField);
        panel.add(new JLabel("Role:"));
        panel.add(roleCombo);
        panel.add(new JLabel("Shift / Notes:"));
        panel.add(extraField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Create System User", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try {
                String name = nameField.getText().trim();
                String mobile = mobileField.getText().trim();
                String user = usernameField.getText().trim();
                String pwd = new String(passwordField.getPassword());
                UserRole role = (UserRole) roleCombo.getSelectedItem();
                String extra = extraField.getText().trim();

                authService.adminCreateUser(name, mobile, user + "@smartparking.up.gov.in", user, pwd, role, extra);
                com.smartparking.ui.components.Toast.success(this, "User created. They must change this initial password at first sign-in.", null);
                refreshData();
            } catch (Exception ex) {
                com.smartparking.ui.components.Toast.error(this, "Error creating user: " + ex.getMessage(), null);
            }
        }
    }

    private void toggleUserStatus() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow < 0) {
            com.smartparking.ui.components.Toast.info(this, "Please select a user to activate/deactivate.", null);
            return;
        }

        int userId = (int) tableModel.getValueAt(selectedRow, 0);
        String currentStatus = (String) tableModel.getValueAt(selectedRow, 5);

        try {
            if ("ACTIVE".equalsIgnoreCase(currentStatus)) {
                String uname = String.valueOf(tableModel.getValueAt(selectedRow, 1));
                int ok = JOptionPane.showConfirmDialog(this,
                        "Deactivate @" + uname + "?\nThey won't be able to sign in until the account is reactivated.",
                        "Deactivate user", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (ok != JOptionPane.OK_OPTION) return;
                authService.adminDeactivateUser(userId);
                com.smartparking.ui.components.Toast.success(this, "User #" + userId + " deactivated.", null);
            } else {
                authService.adminActivateUser(userId);
                com.smartparking.ui.components.Toast.success(this, "User #" + userId + " reactivated.", null);
            }
            refreshData();
        } catch (Exception ex) {
            com.smartparking.ui.components.Toast.error(this, "Action failed: " + ex.getMessage(), null);
        }
    }

    private void showResetPasswordDialog() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow < 0) {
            com.smartparking.ui.components.Toast.info(this, "Please select a user to reset password.", null);
            return;
        }

        int userId = (int) tableModel.getValueAt(selectedRow, 0);
        String username = (String) tableModel.getValueAt(selectedRow, 1);

        JPasswordField newPass = new JPasswordField(15);
        JPanel p = new JPanel(new GridLayout(2, 1, 4, 4));
        p.add(new JLabel("Enter new password for @" + username + " (min 8 chars, 1 letter, 1 digit):"));
        p.add(newPass);

        int res = JOptionPane.showConfirmDialog(this, p, "Reset Password", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            String pwd = new String(newPass.getPassword());
            try {
                authService.adminResetPassword(userId, pwd);
                com.smartparking.ui.components.Toast.success(this, "Temporary password set for @" + username + ". They must change it at next sign-in.", null);
            } catch (Exception ex) {
                com.smartparking.ui.components.Toast.error(this, "Failed to reset password: " + ex.getMessage(), null);
            }
        }
    }
}
