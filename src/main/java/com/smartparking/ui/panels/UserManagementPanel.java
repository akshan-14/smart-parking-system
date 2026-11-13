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
        setBorder(new EmptyBorder(24, 24, 24, 24));

        // Top Header and Controls
        add(buildHeader(), BorderLayout.NORTH);

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
        userTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        userTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        userTable.getTableHeader().setBackground(new Color(241, 245, 249));
        userTable.getTableHeader().setForeground(new Color(30, 41, 59));
        userTable.setSelectionBackground(new Color(224, 242, 254));
        userTable.setSelectionForeground(new Color(15, 23, 42));

        // Status Renderer
        userTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                String val = String.valueOf(value);
                if ("ACTIVE".equalsIgnoreCase(val)) {
                    setForeground(new Color(22, 101, 52));
                    setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    setForeground(new Color(185, 28, 28));
                    setFont(getFont().deriveFont(Font.BOLD));
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(userTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
        scrollPane.getViewport().setBackground(Color.WHITE);

        add(scrollPane, BorderLayout.CENTER);

        // Actions toolbar
        add(buildActionsToolbar(), BorderLayout.SOUTH);

        refreshData();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 0, 4));
        titleBox.setOpaque(false);
        JLabel title = new JLabel("User & Access Management");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(15, 23, 42));

        JLabel sub = new JLabel("System Administrator role management, account provisioning, and access controls");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(100, 116, 139));
        titleBox.add(title);
        titleBox.add(sub);
        header.add(titleBox, BorderLayout.WEST);

        return header;
    }

    private JPanel buildActionsToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        bar.setOpaque(false);

        ModernButton btnCreate = new ModernButton("+ Create User", null,
                new Color(37, 99, 235), new Color(29, 78, 216), Color.WHITE);
        btnCreate.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCreate.addActionListener(e -> showCreateUserDialog());

        ModernButton btnToggle = new ModernButton("Activate / Deactivate", null,
                new Color(245, 158, 11), new Color(217, 119, 6), Color.WHITE);
        btnToggle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnToggle.addActionListener(e -> toggleUserStatus());

        ModernButton btnResetPw = new ModernButton("Reset Password", null,
                new Color(239, 68, 68), new Color(220, 38, 38), Color.WHITE);
        btnResetPw.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnResetPw.addActionListener(e -> showResetPasswordDialog());

        bar.add(btnCreate);
        bar.add(btnToggle);
        bar.add(btnResetPw);

        return bar;
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
                JOptionPane.showMessageDialog(this, "User created successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                refreshData();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error creating user: " + ex.getMessage(), "Validation Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void toggleUserStatus() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a user to activate/deactivate.", "Notice", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int userId = (int) tableModel.getValueAt(selectedRow, 0);
        String currentStatus = (String) tableModel.getValueAt(selectedRow, 5);

        try {
            if ("ACTIVE".equalsIgnoreCase(currentStatus)) {
                authService.adminDeactivateUser(userId);
                JOptionPane.showMessageDialog(this, "User #" + userId + " deactivated.", "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                authService.adminActivateUser(userId);
                JOptionPane.showMessageDialog(this, "User #" + userId + " reactivated.", "Success", JOptionPane.INFORMATION_MESSAGE);
            }
            refreshData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Action failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showResetPasswordDialog() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a user to reset password.", "Notice", JOptionPane.WARNING_MESSAGE);
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
                JOptionPane.showMessageDialog(this, "Password reset successfully for @" + username, "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to reset password: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
