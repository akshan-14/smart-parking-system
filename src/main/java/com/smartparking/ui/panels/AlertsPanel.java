package com.smartparking.ui.panels;

import com.smartparking.model.alert.Alert;
import com.smartparking.model.enums.UserRole;
import com.smartparking.model.user.User;
import com.smartparking.security.AccessControl;
import com.smartparking.security.UserSession;
import com.smartparking.service.AlertService;
import com.smartparking.ui.components.ModernButton;
import com.smartparking.ui.components.Toast;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.VectorIconUtil;
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

    private CardLayout tableCards;
    private JPanel tableHolder;
    private ModernButton btnViewBay;
    private ModernButton btnAck;
    private ModernButton btnResolve;

    private void initUI() {
        // --- Header ---
        com.smartparking.ui.components.PageHeader header = new com.smartparking.ui.components.PageHeader(
                "Operational alerts", "Overstays, bay issues and charger faults detected across the garage");
        countBadge = new JLabel("0 open");
        countBadge.setFont(UITheme.font(Font.BOLD, 12));
        countBadge.setForeground(UITheme.TEXT_SECONDARY);
        ModernButton btnScan = ModernButton.createSecondary("Run anomaly scan",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.REFRESH, 14, UITheme.TEXT_SECONDARY));
        btnScan.setToolTipText("Check every bay, ticket and charger now (also runs automatically)");
        btnScan.addActionListener(e -> {
            alertService.runScheduledAnomalyCheck();
            refreshAlerts();
            Toast.success(this, "Anomaly scan complete", alertService.getOpenAlertCount() + " open alert(s)");
        });
        header.addAction(countBadge);
        header.addAction(btnScan);

        // --- Filter + actions bar ---
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);
        filters.add(filterLabel("Severity"));
        comboSeverity = new JComboBox<>(new String[]{"ALL", "CRITICAL", "WARNING", "INFO"});
        UITheme.styleComboBox(comboSeverity);
        comboSeverity.addActionListener(e -> refreshAlerts());
        filters.add(comboSeverity);
        filters.add(Box.createHorizontalStrut(6));
        filters.add(filterLabel("Status"));
        comboStatus = new JComboBox<>(new String[]{"OPEN", "ACKNOWLEDGED", "RESOLVED", "ALL"});
        UITheme.styleComboBox(comboStatus);
        comboStatus.addActionListener(e -> refreshAlerts());
        filters.add(comboStatus);
        bar.add(filters, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        btnViewBay = ModernButton.createSecondary("Show on floor map",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.MAP, 13, UITheme.TEXT_SECONDARY)).compact();
        btnViewBay.addActionListener(e -> onShowOnFloorMap());
        btnAck = ModernButton.createSecondary("Acknowledge", null).compact();
        btnAck.addActionListener(e -> onAcknowledge());
        btnResolve = new ModernButton("Resolve…", VectorIconUtil.getIcon(VectorIconUtil.IconType.CHECK, 13, Color.WHITE)).compact();
        btnResolve.addActionListener(e -> onResolve());
        actions.add(btnViewBay);
        actions.add(btnAck);
        actions.add(btnResolve);
        bar.add(actions, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);
        top.add(bar, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        // --- Table ---
        String[] cols = {"ID", "Severity", "Type", "Entity", "Message", "Created (IST)", "Status", "Resolution"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        alertsTable = new JTable(tableModel);
        alertsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        alertsTable.getColumnModel().getColumn(1).setCellRenderer(new PillRenderer());
        alertsTable.getColumnModel().getColumn(6).setCellRenderer(new PillRenderer());
        alertsTable.getSelectionModel().addListSelectionListener(e -> updateActionState());
        alertsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && alertsTable.getSelectedRow() >= 0) onShowOnFloorMap();
            }
        });

        JScrollPane scrollPane = new JScrollPane(alertsTable);
        scrollPane.setBorder(new com.smartparking.ui.theme.DarkTheme.RoundBorder(UITheme.BORDER_SUBTLE, UITheme.RADIUS));
        scrollPane.getViewport().setBackground(UITheme.BG_CARD);

        tableCards = new CardLayout();
        tableHolder = new JPanel(tableCards);
        tableHolder.setOpaque(false);
        tableHolder.add(scrollPane, "TABLE");
        com.smartparking.ui.components.RoundedPanel emptyCard = new com.smartparking.ui.components.RoundedPanel(
                UITheme.RADIUS_LG, UITheme.BG_CARD, UITheme.BORDER_SUBTLE);
        emptyCard.setLayout(new BorderLayout());
        emptyCard.add(new com.smartparking.ui.components.EmptyState(VectorIconUtil.IconType.BELL,
                "No alerts match these filters", "Change the severity or status filter, or run an anomaly scan."), BorderLayout.CENTER);
        tableHolder.add(emptyCard, "EMPTY");
        add(tableHolder, BorderLayout.CENTER);
        updateActionState();
    }

    private static JLabel filterLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(UITheme.font(Font.PLAIN, 12));
        l.setForeground(UITheme.TEXT_SECONDARY);
        return l;
    }

    private Alert selectedAlert() {
        int row = alertsTable.getSelectedRow();
        if (row < 0 || currentAlerts == null || row >= currentAlerts.size()) return null;
        return currentAlerts.get(alertsTable.convertRowIndexToModel(row));
    }

    private void updateActionState() {
        Alert a = selectedAlert();
        btnViewBay.setEnabled(a != null);
        btnAck.setEnabled(a != null && a.isOpen());
        btnResolve.setEnabled(a != null && !a.isResolved());
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
        countBadge.setText(openCount + " open");
        countBadge.setForeground(openCount > 0 ? UITheme.ERROR_TEXT : UITheme.TEXT_SECONDARY);
        tableCards.show(tableHolder, currentAlerts.isEmpty() ? "EMPTY" : "TABLE");
        updateActionState();
    }

    private void onShowOnFloorMap() {
        Alert selected = selectedAlert();
        if (selected == null) {
            Toast.info(this, "Select an alert first", null);
            return;
        }
        if ("SLOT".equalsIgnoreCase(selected.getEntityType())) {
            navigationHandler.accept("FLOORS:" + selected.getEntityId());
        } else if ("TICKET".equalsIgnoreCase(selected.getEntityType())) {
            // Overstay alerts reference the ticket: jump to the bay the vehicle occupies
            String bay = null;
            try {
                bay = new com.smartparking.repository.TicketDao().findById(Integer.parseInt(selected.getEntityId()))
                        .map(com.smartparking.model.ticket.ParkingTicket::getSlotId).orElse(null);
            } catch (Exception ignored) {
                // non-numeric id or ticket removed: fall back to the map
            }
            navigationHandler.accept(bay != null ? "FLOORS:" + bay : "FLOORS");
        } else {
            navigationHandler.accept("FLOORS");
        }
    }

    private void onAcknowledge() {
        Alert selected = selectedAlert();
        if (selected == null) {
            Toast.info(this, "Select an open alert to acknowledge", null);
            return;
        }
        if (!selected.isOpen()) {
            Toast.info(this, "Alert #" + selected.getId() + " is already " + String.valueOf(selected.getStatus()).toLowerCase(), null);
            return;
        }

        User user = UserSession.getInstance().getCurrentUser().orElse(null);
        try {
            alertService.acknowledgeAlert(selected.getId(), user);
            refreshAlerts();
            Toast.success(this, "Alert #" + selected.getId() + " acknowledged", null);
        } catch (Exception ex) {
            Toast.error(this, "Couldn't acknowledge alert", ex.getMessage());
        }
    }

    private void onResolve() {
        Alert selected = selectedAlert();
        if (selected == null) {
            Toast.info(this, "Select an alert to resolve", null);
            return;
        }
        if (selected.isResolved()) {
            Toast.info(this, "Alert #" + selected.getId() + " is already resolved", null);
            return;
        }

        String note = (String) JOptionPane.showInputDialog(this, "How was it resolved? (recorded with the alert)",
                "Resolve alert #" + selected.getId(), JOptionPane.PLAIN_MESSAGE, null, null, "");
        if (note == null) return;
        if (note.trim().isEmpty()) {
            Toast.error(this, "Alert not resolved", "A resolution note is required.");
            return;
        }

        User user = UserSession.getInstance().getCurrentUser().orElse(null);
        try {
            alertService.resolveAlert(selected.getId(), user, note.trim());
            refreshAlerts();
            Toast.success(this, "Alert #" + selected.getId() + " resolved", note.trim());
        } catch (Exception ex) {
            Toast.error(this, "Couldn't resolve alert", ex.getMessage());
        }
    }

    /** Severity / status pills using the shared badge painter (dark-theme safe colours). */
    private static class PillRenderer extends com.smartparking.ui.components.Badge.Renderer {
        private Object value;
        private boolean selected;
        private int row;
        private JTable table;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            this.value = value;
            this.selected = isSelected;
            this.row = row;
            this.table = table;
            setText("");
            return this;
        }

        private static Color colorOf(String v) {
            switch (v) {
                case "CRITICAL": case "OPEN": return UITheme.ERROR;
                case "WARNING": case "ACKNOWLEDGED": return UITheme.WARNING;
                case "RESOLVED": return UITheme.SUCCESS;
                default: return UITheme.ACCENT;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Object hr = table.getClientProperty("hoverRow");
            boolean hover = hr instanceof Integer && (Integer) hr == row;
            g.setColor(selected ? UITheme.BG_SELECTED : hover ? UITheme.BG_HOVER : (row % 2 == 0 ? UITheme.BG_CARD : UITheme.BG_SURFACE));
            g.fillRect(0, 0, getWidth(), getHeight());
            String s = value == null ? "" : value.toString().toUpperCase();
            Font f = UITheme.font(Font.BOLD, 11);
            int w = g.getFontMetrics(f).stringWidth(s) + 26;
            com.smartparking.ui.components.Badge.paintBadge((Graphics2D) g, s, colorOf(s), true, f, 8, (getHeight() - 22) / 2, Math.min(w, getWidth() - 12));
        }
    }
}
