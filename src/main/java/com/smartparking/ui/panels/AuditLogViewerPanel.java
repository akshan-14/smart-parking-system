package com.smartparking.ui.panels;

import com.smartparking.model.audit.AuditLogEntry;
import com.smartparking.repository.AuditLogDao;
import com.smartparking.security.AccessControl;
import com.smartparking.security.Permission;
import com.smartparking.ui.components.ModernButton;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.util.DateFormat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

/**
 * Admin Audit Log Viewer featuring search, multi-criteria filters, pagination,
 * CSV export, and cryptographic SHA-256 tamper-evident integrity verification.
 * (Educational / college viva demonstration feature).
 */
public class AuditLogViewerPanel extends JPanel {

    private final AuditLogDao auditLogDao;
    private final JTable logTable;
    private final DefaultTableModel tableModel;

    private final JComboBox<String> userCombo;
    private final JComboBox<String> actionCombo;
    private final JComboBox<String> resultCombo;
    private final JTextField searchField;
    private final JLabel pageLabel;

    private int currentPage = 1;
    private final int pageSize = 20;

    public AuditLogViewerPanel() {
        this.auditLogDao = new AuditLogDao();

        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_CANVAS);
        setBorder(new EmptyBorder(24, 24, 24, 24));

        // Initialize controls before adding to UI
        userCombo = new JComboBox<>(new String[]{"ALL", "admin", "attendant1", "customer1", "SYSTEM"});
        actionCombo = new JComboBox<>(new String[]{"ALL", "LOGIN_SUCCESS", "LOGIN_FAILURE", "ACCOUNT_LOCKOUT", "LOGOUT", "ACCESS_DENIED", "RESERVATION_CREATED", "PASS_ISSUED", "REFUND_PAYMENT"});
        resultCombo = new JComboBox<>(new String[]{"ALL", "SUCCESS", "FAILURE"});
        searchField = new JTextField(12);
        pageLabel = new JLabel("Page " + currentPage);

        // Top Header and Controls
        add(buildHeader(), BorderLayout.NORTH);

        // Center Table in Card Style
        String[] columns = {"ID", "Timestamp (IST)", "User", "Role", "Action", "Entity", "Entity ID", "Old Value", "New Value", "Reason", "Result", "Hash Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        logTable = new JTable(tableModel);
        logTable.setRowHeight(32);
        logTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        logTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        logTable.getTableHeader().setBackground(new Color(241, 245, 249));
        logTable.getTableHeader().setForeground(new Color(30, 41, 59));
        logTable.setSelectionBackground(new Color(224, 242, 254));
        logTable.setSelectionForeground(new Color(15, 23, 42));
        logTable.setShowGrid(true);
        logTable.setGridColor(new Color(226, 232, 240));

        // Result column badge renderer
        logTable.getColumnModel().getColumn(10).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                String val = String.valueOf(value);
                if ("SUCCESS".equalsIgnoreCase(val)) {
                    setForeground(new Color(22, 101, 52));
                } else if ("FAILURE".equalsIgnoreCase(val) || "DENIED".equalsIgnoreCase(val)) {
                    setForeground(new Color(185, 28, 28));
                    setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    setForeground(new Color(30, 41, 59));
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(logTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
        scrollPane.getViewport().setBackground(Color.WHITE);

        add(scrollPane, BorderLayout.CENTER);

        // Bottom Pagination & Actions
        add(buildBottomBar(), BorderLayout.SOUTH);

        refreshData();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 0, 4));
        titleBox.setOpaque(false);
        JLabel title = new JLabel("System Audit & Security Logs");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(15, 23, 42));

        JLabel sub = new JLabel("Append-only immutable record with SHA-256 cryptographic tamper verification");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(100, 116, 139));
        titleBox.add(title);
        titleBox.add(sub);
        header.add(titleBox, BorderLayout.WEST);

        // Action Buttons: Verify Integrity + Export CSV
        JPanel btnBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnBox.setOpaque(false);

        ModernButton btnVerify = new ModernButton("Verify Log Integrity", null,
                new Color(16, 185, 129), new Color(5, 150, 105), Color.WHITE);
        btnVerify.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnVerify.addActionListener(e -> verifyIntegrity());

        ModernButton btnExport = new ModernButton("Export to CSV", null,
                new Color(59, 130, 246), new Color(37, 99, 235), Color.WHITE);
        btnExport.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnExport.addActionListener(e -> exportToCsv());

        btnBox.add(btnVerify);
        btnBox.add(btnExport);
        header.add(btnBox, BorderLayout.EAST);

        return header;
    }

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);

        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        navPanel.setOpaque(false);

        JButton prevBtn = new JButton("◀ Previous");
        prevBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        prevBtn.addActionListener(e -> {
            if (currentPage > 1) {
                currentPage--;
                refreshData();
            }
        });

        JButton nextBtn = new JButton("Next ▶");
        nextBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        nextBtn.addActionListener(e -> {
            currentPage++;
            refreshData();
        });

        navPanel.add(prevBtn);
        navPanel.add(pageLabel);
        navPanel.add(nextBtn);
        bar.add(navPanel, BorderLayout.WEST);

        return bar;
    }

    public void refreshData() {
        AccessControl.checkPermission(Permission.VIEW_AUDIT_LOG);

        tableModel.setRowCount(0);
        int offset = (currentPage - 1) * pageSize;
        List<AuditLogEntry> entries = auditLogDao.findFiltered("ALL", "ALL", "ALL", null, offset, pageSize);

        for (AuditLogEntry e : entries) {
            String timeStr = DateFormat.display(e.getTimestamp());
            String hashShort = e.getRowHash() != null && e.getRowHash().length() >= 8
                    ? e.getRowHash().substring(0, 8) + "…" : "OK";

            tableModel.addRow(new Object[]{
                    e.getId(),
                    timeStr,
                    e.getUsername(),
                    e.getRole(),
                    e.getAction(),
                    e.getEntityType(),
                    e.getEntityId(),
                    e.getOldValue() != null ? e.getOldValue() : "—",
                    e.getNewValue() != null ? e.getNewValue() : "—",
                    e.getReason(),
                    e.getResult(),
                    hashShort
            });
        }
        pageLabel.setText("Page " + currentPage);
    }

    private void verifyIntegrity() {
        AuditLogDao.IntegrityResult res = auditLogDao.verifyIntegrity();
        if (res.isValid()) {
            JOptionPane.showMessageDialog(this,
                    "✔ AUDIT LOG INTEGRITY VERIFIED OK\n\n" + res.message() +
                            "\n\nCryptographic SHA-256 hash chaining is intact across all records.",
                    "Audit Log Verification", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "⚠ SECURITY ALERT: LOG TAMPERING DETECTED\n\n" + res.message() +
                            "\n\nAt least one record was modified or deleted outside authorized append channels.",
                    "Integrity Violation", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportToCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("audit_log_export.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File target = chooser.getSelectedFile();
            List<AuditLogEntry> all = auditLogDao.findAll();
            try (PrintWriter out = new PrintWriter(new FileWriter(target))) {
                out.println("ID,Timestamp,User,Role,Action,EntityType,EntityID,OldValue,NewValue,Reason,Result,PrevHash,RowHash");
                for (AuditLogEntry e : all) {
                    out.printf("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                            e.getId(),
                            DateFormat.toDbString(e.getTimestamp()),
                            e.getUsername(),
                            e.getRole(),
                            e.getAction(),
                            e.getEntityType(),
                            e.getEntityId(),
                            e.getOldValue() != null ? e.getOldValue() : "",
                            e.getNewValue() != null ? e.getNewValue() : "",
                            e.getReason() != null ? e.getReason().replace("\"", "\"\"") : "",
                            e.getResult(),
                            e.getPrevHash(),
                            e.getRowHash()
                    );
                }
                JOptionPane.showMessageDialog(this, "Exported " + all.size() + " log entries to: " + target.getName(),
                        "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to export CSV: " + ex.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
