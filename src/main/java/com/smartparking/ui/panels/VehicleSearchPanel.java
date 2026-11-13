package com.smartparking.ui.panels;

import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.service.ParkingService;
import com.smartparking.service.TariffService;
import com.smartparking.ui.components.ModernButton;
import com.smartparking.ui.components.RoundedPanel;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.ImageLoader;
import com.smartparking.ui.util.VectorIconUtil;
import com.smartparking.util.DateFormat;
import com.smartparking.util.MoneyFormat;
import com.smartparking.util.PlateUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Modern Vehicle Lookup Panel:
 * - Partial plate search (e.g. "AB1234", "UP32")
 * - Rich result card displaying formatted UP plate + district badge, vehicle classification,
 *   floor level, bay ID, parked since, elapsed duration, accrued fee in INR (₹),
 *   and direct "Show on map" button that deep-links to Floor Map.
 * - "Send directions to phone (Simulated)" with Indian mobile validation (^[6-9][0-9]{9}$).
 * - Friendly in-line empty state when no match is found.
 */
public class VehicleSearchPanel extends JPanel {

    private final ParkingService parkingService;
    private final TariffService tariffService;
    private final Consumer<String> navigationHandler;

    private JTextField searchField;
    private JLabel iconLabel;
    private JLabel typeBadgeLabel;
    private JLabel plateResultLabel;
    private JLabel vehicleTypeResultLabel;
    private JLabel slotResultLabel;
    private JLabel floorResultLabel;
    private JLabel entryResultLabel;
    private JLabel durationResultLabel;
    private JLabel feeResultLabel;

    private JPanel resultCard;
    private ModernButton btnLocateMap;
    private ModernButton btnSendDirections;

    private RoundedPanel tableCard;
    private DefaultTableModel matchesTableModel;
    private List<ParkingTicket> currentMatches;

    private JPanel emptyStatePanel;
    private JLabel emptyStateMsg;

    public VehicleSearchPanel(ParkingService parkingService, Consumer<String> navigationHandler) {
        this.parkingService = parkingService;
        this.tariffService = new TariffService();
        this.navigationHandler = navigationHandler;

        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_CANVAS);
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        initUI();
    }

    private void initUI() {
        // --- TOP: Title ---
        add(new com.smartparking.ui.components.PageHeader("Find vehicle",
                "Search parked vehicles by full or partial licence plate"), BorderLayout.NORTH);

        // --- CENTER: Search Form & Result ---
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        // 1. Search Bar Card
        RoundedPanel searchCard = new RoundedPanel(14, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        searchCard.setLayout(new BorderLayout(10, 0));
        searchCard.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        searchCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        searchCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel queryLbl = new JLabel(VectorIconUtil.getIcon(VectorIconUtil.IconType.SEARCH, 16, UITheme.TEXT_TERTIARY));
        searchCard.add(queryLbl, BorderLayout.WEST);

        searchField = new JTextField(18);
        searchField.setToolTipText("Full or partial plate, e.g. UP32 or AB1234");
        searchField.getAccessibleContext().setAccessibleName("Licence plate search");
        searchField.setText("UP32");
        UITheme.styleTextField(searchField);
        searchField.setFont(UITheme.font(Font.PLAIN, 14));
        searchField.addActionListener(e -> executeSearch());
        searchCard.add(searchField, BorderLayout.CENTER);

        ModernButton btnSearch = new ModernButton("Search", null);
        btnSearch.setPreferredSize(new Dimension(120, 40));
        btnSearch.addActionListener(e -> executeSearch());
        searchCard.add(btnSearch, BorderLayout.EAST);

        center.add(searchCard);
        center.add(Box.createVerticalStrut(14));

        // 2. Empty State Card
        emptyStatePanel = new RoundedPanel(14, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        emptyStatePanel.setLayout(new BoxLayout(emptyStatePanel, BoxLayout.Y_AXIS));
        emptyStatePanel.setBorder(BorderFactory.createEmptyBorder(28, 20, 28, 20));
        emptyStatePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        emptyStatePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel emptyIcon = new JLabel(VectorIconUtil.getIcon(VectorIconUtil.IconType.SEARCH, 36, UITheme.TEXT_TERTIARY));
        emptyIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyStatePanel.add(emptyIcon);
        emptyStatePanel.add(Box.createVerticalStrut(10));

        emptyStateMsg = new JLabel("Enter a full or partial plate number (e.g. UP32 or AB1234) to locate your vehicle.");
        emptyStateMsg.setFont(UITheme.font(Font.PLAIN, 13));
        emptyStateMsg.setForeground(UITheme.TEXT_SECONDARY);
        emptyStateMsg.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyStatePanel.add(emptyStateMsg);

        center.add(emptyStatePanel);

        // 3. Multiple Matches Table Card
        tableCard = new RoundedPanel(14, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        tableCard.setLayout(new BorderLayout(0, 10));
        tableCard.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        tableCard.setMaximumSize(new Dimension(900, 180));

        JLabel tTitle = new JLabel("Multiple Matching Vehicles Parked (Select a row to view details):");
        tTitle.setFont(UITheme.FONT_BOLD);
        tTitle.setForeground(UITheme.TEXT_DARK);
        tableCard.add(tTitle, BorderLayout.NORTH);

        String[] cols = {"Plate Number", "RTO District", "Type", "Slot ID", "Floor", "Entry Time"};
        matchesTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        JTable matchesTable = new JTable(matchesTableModel);
        matchesTable.setFont(UITheme.font(Font.PLAIN, 12));
        matchesTable.setRowHeight(28);
        matchesTable.setShowGrid(false);
        matchesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        matchesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = matchesTable.getSelectedRow();
                if (row >= 0 && currentMatches != null && row < currentMatches.size()) {
                    displayTicketDetail(currentMatches.get(row));
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(matchesTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_SUBTLE));
        scrollPane.getViewport().setBackground(UITheme.BG_CARD);
        tableCard.add(scrollPane, BorderLayout.CENTER);
        tableCard.setVisible(false);

        center.add(tableCard);
        center.add(Box.createVerticalStrut(14));

        // 4. Single Vehicle Detail Inspector Card
        resultCard = new RoundedPanel(14, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        resultCard.setLayout(new BorderLayout(24, 0));
        resultCard.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        resultCard.setMaximumSize(new Dimension(900, 250));

        JPanel badgeBox = new JPanel(new BorderLayout(0, 8));
        badgeBox.setOpaque(false);
        iconLabel = new JLabel(VectorIconUtil.getIcon(VectorIconUtil.IconType.CAR, 56, UITheme.PRIMARY_BLUE), SwingConstants.CENTER);
        typeBadgeLabel = new JLabel("Vehicle Badge", SwingConstants.CENTER);
        typeBadgeLabel.setFont(UITheme.font(Font.BOLD, 11));
        typeBadgeLabel.setForeground(UITheme.TEXT_SECONDARY);
        badgeBox.add(iconLabel, BorderLayout.CENTER);
        badgeBox.add(typeBadgeLabel, BorderLayout.SOUTH);
        badgeBox.setPreferredSize(new Dimension(130, 140));
        resultCard.add(badgeBox, BorderLayout.WEST);

        JPanel details = new JPanel(new GridLayout(7, 2, 8, 6));
        details.setOpaque(false);

        details.add(new JLabel("License Plate:"));
        plateResultLabel = new JLabel("—");
        plateResultLabel.setFont(UITheme.FONT_BOLD);
        details.add(plateResultLabel);

        details.add(new JLabel("Vehicle Classification:"));
        vehicleTypeResultLabel = new JLabel("—");
        vehicleTypeResultLabel.setFont(UITheme.FONT_BOLD);
        details.add(vehicleTypeResultLabel);

        details.add(new JLabel("Allocated Parking Bay:"));
        slotResultLabel = new JLabel("—");
        slotResultLabel.setFont(UITheme.FONT_BOLD);
        slotResultLabel.setForeground(UITheme.PRIMARY_BLUE);
        details.add(slotResultLabel);

        details.add(new JLabel("Floor Level:"));
        floorResultLabel = new JLabel("—");
        details.add(floorResultLabel);

        details.add(new JLabel("Parked Since:"));
        entryResultLabel = new JLabel("—");
        details.add(entryResultLabel);

        details.add(new JLabel("Elapsed Duration:"));
        durationResultLabel = new JLabel("—");
        durationResultLabel.setFont(UITheme.FONT_BOLD);
        details.add(durationResultLabel);

        details.add(new JLabel("Accrued Parking Fee:"));
        feeResultLabel = new JLabel("—");
        feeResultLabel.setFont(UITheme.FONT_BOLD);
        feeResultLabel.setForeground(new Color(139, 238, 177));
        details.add(feeResultLabel);

        resultCard.add(details, BorderLayout.CENTER);

        // Right Action Column: "Show on Map" + "Send directions to phone (Simulated)"
        JPanel actionBox = new JPanel();
        actionBox.setLayout(new BoxLayout(actionBox, BoxLayout.Y_AXIS));
        actionBox.setOpaque(false);
        actionBox.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));

        btnLocateMap = new ModernButton("Show on Map \u2192",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.MAP, 14, Color.WHITE),
                UITheme.PRIMARY_BLUE, UITheme.PRIMARY_HOVER, Color.WHITE);
        btnLocateMap.setFont(UITheme.font(Font.BOLD, 12));
        btnLocateMap.setMaximumSize(new Dimension(210, 36));
        actionBox.add(btnLocateMap);
        actionBox.add(Box.createVerticalStrut(10));

        btnSendDirections = ModernButton.createSecondary("Send directions to phone (Simulated)",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.PHONE, 14, UITheme.TEXT_DARK));
        btnSendDirections.setFont(UITheme.font(Font.PLAIN, 11));
        btnSendDirections.setMaximumSize(new Dimension(210, 36));
        btnSendDirections.addActionListener(e -> promptSendDirections());
        actionBox.add(btnSendDirections);

        resultCard.add(actionBox, BorderLayout.EAST);
        resultCard.setVisible(false);

        center.add(resultCard);
        add(center, BorderLayout.CENTER);
    }

    private void executeSearch() {
        String rawQuery = searchField.getText().trim();
        if (rawQuery.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a license plate number to search.", "Search", JOptionPane.WARNING_MESSAGE);
            return;
        }

        currentMatches = parkingService.searchVehicles(rawQuery);
        matchesTableModel.setRowCount(0);

        if (currentMatches.isEmpty()) {
            tableCard.setVisible(false);
            resultCard.setVisible(false);
            emptyStatePanel.setVisible(true);
            emptyStateMsg.setText("No parked vehicle found matching: \"" + rawQuery + "\". Please verify plate number or check if vehicle has already exited.");
            emptyStateMsg.setForeground(new Color(234, 113, 113));
            return;
        }

        emptyStatePanel.setVisible(false);

        for (ParkingTicket t : currentMatches) {
            matchesTableModel.addRow(new Object[]{
                    PlateUtil.format(t.getLicensePlate()),
                    PlateUtil.district(t.getLicensePlate()),
                    t.getVehicleType().name(),
                    t.getSlotId(),
                    "Floor " + t.getFloorNumber(),
                    t.getEntryTime() != null ? DateFormat.display(t.getEntryTime()) : "—"
            });
        }

        if (currentMatches.size() > 1) {
            tableCard.setVisible(true);
        } else {
            tableCard.setVisible(false);
        }

        displayTicketDetail(currentMatches.get(0));
    }

    private void displayTicketDetail(ParkingTicket t) {
        iconLabel.setIcon(VectorIconUtil.getVehicleIcon(t.getVehicleType(), 56, UITheme.PRIMARY_BLUE));
        typeBadgeLabel.setText(t.getVehicleType().name());

        String typeDesc = switch (t.getVehicleType()) {
            case MOTORCYCLE, BIKE -> "Two-Wheeler / Motorcycle";
            case AUTO_RICKSHAW -> "Three-Wheeler / Auto Rickshaw";
            case CAR -> "Four-Wheeler / Standard Car";
            case EV_CAR, ELECTRIC_CAR -> "Electric Four-Wheeler (EV Car)";
            case EV_TWO_WHEELER -> "Electric Two-Wheeler (EV Bike)";
            case LCV -> "Light Commercial Vehicle (LCV)";
            case HCV, TRUCK -> "Heavy Commercial Vehicle (HCV)";
        };
        vehicleTypeResultLabel.setText(typeDesc);
        plateResultLabel.setText(PlateUtil.format(t.getLicensePlate()) + " [" + PlateUtil.district(t.getLicensePlate()) + "]");
        slotResultLabel.setText(t.getSlotId());
        floorResultLabel.setText("Floor " + t.getFloorNumber());

        LocalDateTime inTime = t.getEntryTime() != null ? t.getEntryTime() : LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        entryResultLabel.setText(DateFormat.display(inTime));

        Duration d = Duration.between(inTime, LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
        long hours = d.toHours();
        long mins = d.toMinutesPart();
        durationResultLabel.setText(hours + "h " + mins + "m");

        // Calculate dynamic accrued fee using TariffService
        try {
            double rate = tariffService.getHourlyRate(t.getVehicleType());
            long billableHours = Math.max(1, (d.toMinutes() + 59) / 60);
            double accrued = billableHours * rate;
            feeResultLabel.setText(MoneyFormat.format(accrued));
        } catch (Exception e) {
            feeResultLabel.setText(MoneyFormat.format(60.0));
        }

        for (java.awt.event.ActionListener al : btnLocateMap.getActionListeners()) {
            btnLocateMap.removeActionListener(al);
        }
        btnLocateMap.addActionListener(e -> {
            if (navigationHandler != null) {
                navigationHandler.accept("FLOORS:" + t.getSlotId());
            }
        });

        resultCard.setVisible(true);
        revalidate();
        repaint();
    }

    private void promptSendDirections() {
        String input = JOptionPane.showInputDialog(this,
                "Enter 10-digit Indian Mobile Number:\n(Directions will be sent via Simulated SMS)",
                "Send Wayfinding Directions (Simulated)",
                JOptionPane.PLAIN_MESSAGE);

        if (input == null) return;
        String mobile = input.trim();

        // Validate Indian 10-digit mobile number: ^[6-9][0-9]{9}$
        if (!Pattern.matches("^[6-9][0-9]{9}$", mobile)) {
            JOptionPane.showMessageDialog(this,
                    "Invalid Indian mobile number: \"" + mobile + "\".\nMust be exactly 10 digits starting with 6, 7, 8, or 9.",
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JOptionPane.showMessageDialog(this,
                "Simulated SMS dispatched to +91 " + mobile + ":\n" +
                        "\"Your vehicle " + plateResultLabel.getText() + " is parked at Bay " + slotResultLabel.getText() +
                        ", " + floorResultLabel.getText() + ". Follow Floor Guide 1 to elevator bank.\"",
                "Directions Sent (Simulated)", JOptionPane.INFORMATION_MESSAGE);
    }
}
