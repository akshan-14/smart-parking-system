package com.smartparking.ui.panels;

import com.smartparking.model.alert.Alert;
import com.smartparking.model.enums.UserRole;
import com.smartparking.model.user.User;
import com.smartparking.security.AccessControl;
import com.smartparking.security.UserSession;
import com.smartparking.service.AlertService;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.util.DateFormat;
import com.smartparking.util.I18n;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Operational Alerts & Real-Time Anomaly Management Panel.
 * Supports filtering by severity (INFO, WARNING, CRITICAL), type, and status (OPEN, ACKNOWLEDGED, RESOLVED).
 * Provides one-click Acknowledge, Resolve with Note, and Navigation to Floor Map.
 */
public class AlertsPanel extends JPanel {

    private final AlertService alertService;
    private final Consumer<String> navigationHandler;

    private JComboBox<String> comboSeverity;
    private JComboBox<String> comboStatus;
    private DefaultTableModel tableModel;
    private JTable alertsTable;
    private List<Alert> currentAlerts;
    private JLabel countBadge;

    public AlertsPanel(AlertService alertService, Consumer<String> navigationHandler) {
        this.alertService = alertService;
        this.navigationHandler = navigationHandler;

        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.BG_CANVAS);
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        initUI();
        refreshAlerts();
    }

    public AlertsPanel() {
        this(new AlertService(), key -> {});
    }

    private void initUI() {
        // --- 1. Header Bar ---
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);

        JLabel title = new JLabel(I18n.get("nav.alerts"));
        title.setFont(UITheme.font(Font.BOLD, 22));
        title.setForeground(UITheme.TEXT_DARK);

        JLabel sub = new JLabel("Live Facility Anomaly Detection, Bay Issues, Hardware Faults & Overstay Tracking");
        sub.setFont(UITheme.font(Font.PLAIN, 12));
        sub.setForeground(UITheme.TEXT_MUTED);

        left.add(title);
        left.add(Box.createVerticalStrut(4));
        left.add(sub);
        header.add(left, BorderLayout.WEST);

        // Header Actions: Scan Anomaly Now + Open Count Badge
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        countBadge = new JLabel("0 Open Alerts");
        countBadge.setFont(UITheme.font(Font.BOLD, 12));
        countBadge.setForeground(new Color(232, 114, 114));
        countBadge.setBackground(UITheme.BG_HOVER);
        countBadge.setOpaque(true);
        countBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(121, 30, 36), 1, true),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));

        JButton btnScan = new JButton("Run Anomaly Scan");
        btnScan.setFont(UITheme.font(Font.BOLD, 12));
        btnScan.setBackground(UITheme.ACCENT);
        btnScan.setForeground(Color.WHITE);
        btnScan.setFocusPainted(false);
        btnScan.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnScan.addActionListener(e -> {
            alertService.runScheduledAnomalyCheck();
            refreshAlerts();
            JOptionPane.showMessageDialog(this, "Anomaly check completed across all garage bays, tickets, and chargers.",
                    "Scan Completed", JOptionPane.INFORMATION_MESSAGE);
        });

        right.add(countBadge);
        right.add(btnScan);
        header.add(right, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // --- 2. Filter Bar ---
        JPanel filterCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 8));
        filterCard.setBackground(UITheme.BG_CARD);
        filterCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER_SUBTLE, 1, true),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        filterCard.add(new JLabel("Severity:"));
        comboSeverity = new JComboBox<>(new String[]{"ALL", "CRITICAL", "WARNING", "INFO"});
        comboSeverity.addActionListener(e -> refreshAlerts());
        filterCard.add(comboSeverity);

        filterCard.add(Box.createHorizontalStrut(10));
        filterCard.add(new JLabel("Status:"));
        comboStatus = new JComboBox<>(new String[]{"OPEN", "ACKNOWLEDGED", "RESOLVED", "ALL"});
        comboStatus.addActionListener(e -> refreshAlerts());
        filterCard.add(comboStatus);

        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.setFont(UITheme.font(Font.PLAIN, 12));
        btnRefresh.addActionListener(e -> refreshAlerts());
        filterCard.add(btnRefresh);

        // --- 3. Table of Alerts ---
        String[] cols = {"ID", "Severity", "Type", "Entity", "Message", "Created (IST)", "Status", "Resolution"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        alertsTable = new JTable(tableModel);
        alertsTable.setRowHeight(32);
        alertsTable.setFont(UITheme.font(Font.PLAIN, 12));
        alertsTable.getTableHeader().setFont(UITheme.font(Font.BOLD, 12));
        alertsTable.getColumnModel().getColumn(1).setCellRenderer(new SeverityRenderer());
        alertsTable.getColumnModel().getColumn(6).setCellRenderer(new StatusRenderer());

        JScrollPane scrollPane = new JScrollPane(alertsTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_SUBTLE, 1, true));
        scrollPane.getViewport().setBackground(UITheme.BG_CARD);

        // --- 4. Action Buttons Footer ---
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        footer.setOpaque(false);

        JButton btnViewBay = new JButton("Show on Floor Map \u2192");
        btnViewBay.setFont(UITheme.font(Font.BOLD, 12));
        btnViewBay.setBackground(UITheme.BG_ELEVATED);
        btnViewBay.setForeground(Color.WHITE);
        btnViewBay.setFocusPainted(false);
        btnViewBay.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnViewBay.addActionListener(e -> onShowOnFloorMap());

        JButton btnAck = new JButton("Acknowledge");
        btnAck.setFont(UITheme.font(Font.BOLD, 12));
        btnAck.setBackground(new Color(245, 158, 11));
        btnAck.setForeground(Color.WHITE);
        btnAck.setFocusPainted(false);
        btnAck.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnAck.addActionListener(e -> onAcknowledge());

        JButton btnResolve = new JButton("Resolve Alert");
        btnResolve.setFont(UITheme.font(Font.BOLD, 12));
        btnResolve.setBackground(new Color(16, 185, 129));
        btnResolve.setForeground(Color.WHITE);
        btnResolve.setFocusPainted(false);
        btnResolve.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnResolve.addActionListener(e -> onResolve());

        footer.add(btnViewBay);
        footer.add(btnAck);
        footer.add(btnResolve);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.setOpaque(false);
        centerPanel.add(filterCard, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        centerPanel.add(footer, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);
    }

    public void refreshAlerts() {
        String severity = (String) comboSeverity.getSelectedItem();
        String status = (String) comboStatus.getSelectedItem();

        currentAlerts = alertService.getFilteredAlerts(severity, null, status, 100);
        tableModel.setRowCount(0);

        for (Alert a : currentAlerts) {
            String createdStr = DateFormat.display(a.getCreatedAt());
            String resNote = a.getResolutionNote() != null ? a.getResolutionNote() : "—";
            tableModel.addRow(new Object[]{
                    "#" + a.getId(),
                    a.getSeverity(),
                    a.getType(),
                    a.getEntityType() + " " + a.getEntityId(),
                    a.getMessage(),
                    createdStr,
                    a.getStatus(),
                    resNote
            });
        }

        int openCount = alertService.getOpenAlertCount();
        countBadge.setText(openCount + " Open Alert" + (openCount == 1 ? "" : "s"));
    }

    private void onShowOnFloorMap() {
        int row = alertsTable.getSelectedRow();
        if (row < 0 || row >= currentAlerts.size()) {
            JOptionPane.showMessageDialog(this, "Please select an alert row first.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Alert selected = currentAlerts.get(row);
        if ("SLOT".equalsIgnoreCase(selected.getEntityType())) {
            navigationHandler.accept("FLOORS:" + selected.getEntityId());
        } else {
            navigationHandler.accept("FLOORS");
        }
    }

    private void onAcknowledge() {
        int row = alertsTable.getSelectedRow();
        if (row < 0 || row >= currentAlerts.size()) {
            JOptionPane.showMessageDialog(this, "Please select an open alert row to acknowledge.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Alert selected = currentAlerts.get(row);
        if (!selected.isOpen()) {
            JOptionPane.showMessageDialog(this, "Alert is already " + selected.getStatus(), "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        User user = UserSession.getInstance().getCurrentUser().orElse(null);
        try {
            alertService.acknowledgeAlert(selected.getId(), user);
            refreshAlerts();
            JOptionPane.showMessageDialog(this, "Alert #" + selected.getId() + " acknowledged.", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onResolve() {
        int row = alertsTable.getSelectedRow();
        if (row < 0 || row >= currentAlerts.size()) {
            JOptionPane.showMessageDialog(this, "Please select an alert row to resolve.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Alert selected = currentAlerts.get(row);
        if (selected.isResolved()) {
            JOptionPane.showMessageDialog(this, "Alert is already resolved.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String note = JOptionPane.showInputDialog(this, "Enter resolution explanation note:", "Resolve Alert #" + selected.getId(), JOptionPane.PLAIN_MESSAGE);
        if (note == null || note.trim().isEmpty()) {
            return;
        }

        User user = UserSession.getInstance().getCurrentUser().orElse(null);
        try {
            alertService.resolveAlert(selected.getId(), user, note.trim());
            refreshAlerts();
            JOptionPane.showMessageDialog(this, "Alert #" + selected.getId() + " marked RESOLVED.", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Custom cell renderers for high-contrast pills
    private static class SeverityRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
            String sev = String.valueOf(value);
            if ("CRITICAL".equalsIgnoreCase(sev)) {
                lbl.setBackground(UITheme.BG_HOVER);
                lbl.setForeground(new Color(242, 136, 136));
            } else if ("WARNING".equalsIgnoreCase(sev)) {
                lbl.setBackground(new Color(52, 51, 35));
                lbl.setForeground(new Color(250, 174, 127));
            } else {
                lbl.setBackground(UITheme.BG_HOVER);
                lbl.setForeground(new Color(134, 129, 218));
            }
            lbl.setOpaque(true);
            return lbl;
        }
    }

    private static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
            String st = String.valueOf(value);
            if ("OPEN".equalsIgnoreCase(st)) {
                lbl.setBackground(UITheme.BG_HOVER);
                lbl.setForeground(new Color(234, 113, 113));
            } else if ("ACKNOWLEDGED".equalsIgnoreCase(st)) {
                lbl.setBackground(new Color(254, 240, 138));
                lbl.setForeground(new Color(249, 192, 128));
            } else {
                lbl.setBackground(UITheme.BG_ELEVATED);
                lbl.setForeground(new Color(139, 238, 177));
            }
            lbl.setOpaque(true);
            return lbl;
        }
    }
}
