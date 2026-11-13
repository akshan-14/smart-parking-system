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
        setBorder(new EmptyBorder(24, 28, 24, 28));

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
        logTable.setFont(UITheme.font(Font.PLAIN, 12));
        logTable.getTableHeader().setFont(UITheme.font(Font.BOLD, 12));
        logTable.getTableHeader().setBackground(UITheme.BG_ELEVATED);
        logTable.getTableHeader().setForeground(UITheme.TEXT_PRIMARY);
        logTable.setSelectionBackground(UITheme.BG_HOVER);
        logTable.setSelectionForeground(UITheme.TEXT_PRIMARY);
        // Result column: shared status-pill renderer
        logTable.getColumnModel().getColumn(10).setCellRenderer(new com.smartparking.ui.components.Badge.Renderer());
        logTable.getColumnModel().getColumn(11).setCellRenderer(new com.smartparking.ui.theme.DarkTheme.CellRenderer(SwingConstants.LEFT) {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setFont(UITheme.mono(Font.PLAIN, 12));
                setForeground(UITheme.TEXT_SECONDARY);
                return this;
            }
        });

        JScrollPane scrollPane = new JScrollPane(logTable);
        scrollPane.setBorder(new com.smartparking.ui.theme.DarkTheme.RoundBorder(UITheme.BORDER_SUBTLE, UITheme.RADIUS));
        scrollPane.getViewport().setBackground(UITheme.BG_CARD);

        add(scrollPane, BorderLayout.CENTER);

        // Bottom Pagination & Actions
        add(buildBottomBar(), BorderLayout.SOUTH);

        // Load eagerly only for roles allowed to see this screen (the main window builds every panel up front;
        // without this guard a customer sign-in threw SecurityException). refreshData() still enforces RBAC.
        if (AccessControl.hasPermission(Permission.VIEW_AUDIT_LOG)) refreshData();
    }

    private JPanel buildHeader() {
        JPanel top = new JPanel(new BorderLayout(0, 0));
        top.setOpaque(false);
        com.smartparking.ui.components.PageHeader header = new com.smartparking.ui.components.PageHeader(
                "Security audit trail", "Append-only log with SHA-256 hash chaining · tampering is detectable");

        ModernButton btnVerify = ModernButton.createSecondary("Verify integrity",
                com.smartparking.ui.util.VectorIconUtil.getIcon(com.smartparking.ui.util.VectorIconUtil.IconType.SHIELD, 14, UITheme.TEXT_SECONDARY));
        btnVerify.setToolTipText("Recompute the hash chain across every record");
        btnVerify.addActionListener(e -> verifyIntegrity());
        ModernButton btnExport = ModernButton.createSecondary("Export CSV",
                com.smartparking.ui.util.VectorIconUtil.getIcon(com.smartparking.ui.util.VectorIconUtil.IconType.DOWNLOAD, 14, UITheme.TEXT_SECONDARY));
        btnExport.addActionListener(e -> exportToCsv());
        header.addAction(btnVerify);
        header.addAction(btnExport);
        top.add(header, BorderLayout.NORTH);

        // Filter bar (wires the existing filter controls to AuditLogDao.findFiltered)
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);
        filters.add(filterLabel("User"));
        UITheme.styleComboBox(userCombo);
        filters.add(userCombo);
        filters.add(Box.createHorizontalStrut(6));
        filters.add(filterLabel("Action"));
        UITheme.styleComboBox(actionCombo);
        filters.add(actionCombo);
        filters.add(Box.createHorizontalStrut(6));
        filters.add(filterLabel("Result"));
        UITheme.styleComboBox(resultCombo);
        filters.add(resultCombo);
        filters.add(Box.createHorizontalStrut(6));
        UITheme.styleTextField(searchField);
        searchField.setToolTipText("Search reason, entity type or entity ID (Enter to apply)");
        searchField.getAccessibleContext().setAccessibleName("Search audit log");
        filters.add(filterLabel("Search"));
        filters.add(searchField);
        java.awt.event.ActionListener apply = e -> {
            currentPage = 1;
            refreshData();
        };
        userCombo.addActionListener(apply);
        actionCombo.addActionListener(apply);
        resultCombo.addActionListener(apply);
        searchField.addActionListener(apply);
        top.add(filters, BorderLayout.SOUTH);
        return top;
    }

    private static JLabel filterLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(UITheme.font(Font.PLAIN, 12));
        l.setForeground(UITheme.TEXT_SECONDARY);
        return l;
    }

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);

        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        navPanel.setOpaque(false);

        ModernButton prevBtn = ModernButton.createSecondary("Previous", com.smartparking.ui.util.VectorIconUtil.getIcon(com.smartparking.ui.util.VectorIconUtil.IconType.ARROW_LEFT, 12, UITheme.TEXT_SECONDARY)).compact();
        prevBtn.addActionListener(e -> {
            if (currentPage > 1) {
                currentPage--;
                refreshData();
            }
        });

        ModernButton nextBtn = ModernButton.createSecondary("Next", com.smartparking.ui.util.VectorIconUtil.getIcon(com.smartparking.ui.util.VectorIconUtil.IconType.ARROW_RIGHT, 12, UITheme.TEXT_SECONDARY)).compact();
        nextBtn.setHorizontalTextPosition(SwingConstants.LEFT);
        nextBtn.addActionListener(e -> {
            currentPage++;
            refreshData();
        });

        pageLabel.setFont(UITheme.font(Font.PLAIN, 12));
        pageLabel.setForeground(UITheme.TEXT_SECONDARY);
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
        List<AuditLogEntry> entries = auditLogDao.findFiltered(
                (String) userCombo.getSelectedItem(), (String) actionCombo.getSelectedItem(),
                (String) resultCombo.getSelectedItem(), searchField.getText(), offset, pageSize);

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
        pageLabel.setText("Page " + currentPage + (entries.isEmpty() ? " · no matching entries" : ""));
    }

    private void verifyIntegrity() {
        AuditLogDao.IntegrityResult res = auditLogDao.verifyIntegrity();
        if (res.isValid()) {
            JOptionPane.showMessageDialog(this,
                    "Audit log integrity verified.\n\n" + res.message() +
                            "\n\nCryptographic SHA-256 hash chaining is intact across all records.",
                    "Audit Log Verification", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Security alert: log tampering detected.\n\n" + res.message() +
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
                com.smartparking.ui.components.Toast.success(this, "Exported " + all.size() + " log entries", target.getAbsolutePath());
            } catch (Exception ex) {
                com.smartparking.ui.components.Toast.error(this, "CSV export failed", ex.getMessage());
            }
        }
    }
}
