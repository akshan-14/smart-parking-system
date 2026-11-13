package com.smartparking.ui.panels;

import com.smartparking.model.enums.ReservationStatus;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.reservation.Reservation;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.repository.ParkingSlotDao;
import com.smartparking.repository.ReservationDao;
import com.smartparking.service.ReservationService;
import com.smartparking.ui.components.ModernButton;
import com.smartparking.ui.components.RoundedPanel;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.ImageLoader;
import com.smartparking.ui.util.VectorIconUtil;
import com.smartparking.util.BarcodeGenerator;
import com.smartparking.util.DateFormat;
import com.smartparking.util.PlateUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Modern Advance Parking Slot Reservations Panel:
 * - Interactive mini floor bay picker (suitable slots filtered by vehicle classification)
 * - Start date/time picker rounded to next 15-minute mark with dynamic IST end time display
 * - Robust validations: no past times, no overlapping bookings, vehicle compatibility, valid UP plate
 * - Booking confirmation dialog with ZXing QR code, print/close support, ESC dismissal
 * - Row actions: Extend (+1 hr) and Cancel Reservation
 * - High-contrast colored status badges for table records
 */
public class ReservationPanel extends JPanel {

    private final ReservationService reservationService;
    private final ReservationDao reservationDao;
    private final ParkingSlotDao slotDao;
    private final Runnable onStateChange;

    private JComboBox<String> bayPickerBox;
    private JTextField plateField;
    private JComboBox<VehicleType> vehicleTypeBox;
    private JSpinner startHourSpinner;
    private JSpinner startMinuteSpinner;
    private JSpinner durationSpinner;
    private JLabel calculatedEndLabel;

    private DefaultTableModel tableModel;
    private JTable reservationsTable;
    private ModernButton btnExtend;
    private ModernButton btnCancel;

    public ReservationPanel(ReservationService reservationService, Runnable onStateChange) {
        this.reservationService = reservationService;
        this.reservationDao = new ReservationDao();
        this.slotDao = new ParkingSlotDao();
        this.onStateChange = onStateChange;

        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_CANVAS);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        initUI();
        refreshSuitableBays();
        refreshTable();
    }

    private void initUI() {
        // --- TOP: Header Title & Purge Action ---
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("ADVANCE PARKING SLOT RESERVATIONS",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.CLOCK, 22, UITheme.TEXT_DARK), SwingConstants.LEFT);
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.TEXT_DARK);
        header.add(title, BorderLayout.WEST);

        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerActions.setOpaque(false);

        ModernButton btnPurge = ModernButton.createSecondary("Purge No-Shows (15m Grace)",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.REFRESH, 16, UITheme.TEXT_DARK));
        btnPurge.addActionListener(e -> purgeExpired());
        headerActions.add(btnPurge);

        header.add(headerActions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // --- CENTER: Form + Table Split ---
        JPanel centerPanel = new JPanel(new BorderLayout(0, 14));
        centerPanel.setOpaque(false);

        // 1. Booking Form Card
        RoundedPanel formCard = new RoundedPanel(14, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        formCard.setLayout(new FlowLayout(FlowLayout.LEFT, 14, 12));
        formCard.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        // Vehicle Classification
        formCard.add(new JLabel("Vehicle:"));
        VehicleType[] supportedTypes = {
                VehicleType.CAR,
                VehicleType.MOTORCYCLE,
                VehicleType.AUTO_RICKSHAW,
                VehicleType.EV_CAR,
                VehicleType.EV_TWO_WHEELER,
                VehicleType.LCV,
                VehicleType.HCV
        };
        vehicleTypeBox = new JComboBox<>(supportedTypes);
        UITheme.styleComboBox(vehicleTypeBox);
        vehicleTypeBox.addActionListener(e -> refreshSuitableBays());
        formCard.add(vehicleTypeBox);

        // Mini Floor Bay Picker
        formCard.add(new JLabel("Select Bay:"));
        bayPickerBox = new JComboBox<>();
        UITheme.styleComboBox(bayPickerBox);
        bayPickerBox.setPreferredSize(new Dimension(130, 32));
        formCard.add(bayPickerBox);

        // License Plate Input
        formCard.add(new JLabel("License Plate:"));
        plateField = new JTextField(PlateUtil.generateRandomUp(), 11);
        UITheme.styleTextField(plateField);
        formCard.add(plateField);

        JButton btnRegenPlate = new JButton(VectorIconUtil.getIcon(VectorIconUtil.IconType.DICE, 14, UITheme.TEXT_DARK));
        btnRegenPlate.setToolTipText("Generate random valid UP plate");
        btnRegenPlate.setPreferredSize(new Dimension(32, 32));
        btnRegenPlate.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnRegenPlate.addActionListener(e -> plateField.setText(PlateUtil.generateRandomUp()));
        formCard.add(btnRegenPlate);

        // Start Date/Time (Rounded to next 15-min mark)
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        int curMin = now.getMinute();
        int rem = curMin % 15;
        int addMin = rem == 0 ? 0 : (15 - rem);
        LocalDateTime roundedStart = now.plusMinutes(addMin);

        formCard.add(new JLabel("Start Time (IST):"));
        startHourSpinner = new JSpinner(new SpinnerNumberModel(roundedStart.getHour(), 0, 23, 1));
        startHourSpinner.setPreferredSize(new Dimension(48, 30));
        startMinuteSpinner = new JSpinner(new SpinnerNumberModel(roundedStart.getMinute(), 0, 45, 15));
        startMinuteSpinner.setPreferredSize(new Dimension(48, 30));
        formCard.add(startHourSpinner);
        formCard.add(new JLabel(":"));
        formCard.add(startMinuteSpinner);

        // Duration Spinner (1 to 24 hours)
        formCard.add(new JLabel("Duration:"));
        durationSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 24, 1));
        durationSpinner.setPreferredSize(new Dimension(50, 30));
        durationSpinner.addChangeListener(e -> updateCalculatedEndTime());
        startHourSpinner.addChangeListener(e -> updateCalculatedEndTime());
        startMinuteSpinner.addChangeListener(e -> updateCalculatedEndTime());
        formCard.add(durationSpinner);
        formCard.add(new JLabel("hrs"));

        // Calculated End Time
        calculatedEndLabel = new JLabel();
        calculatedEndLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        calculatedEndLabel.setForeground(new Color(29, 78, 216));
        formCard.add(calculatedEndLabel);
        updateCalculatedEndTime();

        // Reserve Button
        ModernButton btnBook = new ModernButton("Confirm Booking", VectorIconUtil.getIcon(VectorIconUtil.IconType.CHECK, 14, Color.WHITE),
                UITheme.PRIMARY_BLUE, UITheme.PRIMARY_HOVER, Color.WHITE);
        btnBook.addActionListener(e -> bookReservation());
        formCard.add(btnBook);

        centerPanel.add(formCard, BorderLayout.NORTH);

        // 2. Table Card with Action Footer
        JPanel tableContainer = new JPanel(new BorderLayout(0, 8));
        tableContainer.setOpaque(false);

        String[] cols = {"ID", "Code", "Slot ID", "License Plate", "Vehicle Type", "Start Time (IST)", "End Time (IST)", "Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        reservationsTable = new JTable(tableModel);
        reservationsTable.setFont(UITheme.FONT_REGULAR);
        reservationsTable.setRowHeight(32);
        reservationsTable.getTableHeader().setFont(UITheme.FONT_BOLD);
        reservationsTable.getTableHeader().setBackground(new Color(241, 245, 249));
        reservationsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Status Column Renderer (High-Contrast Colored Pills)
        reservationsTable.getColumnModel().getColumn(7).setCellRenderer(new StatusBadgeRenderer());

        reservationsTable.getSelectionModel().addListSelectionListener(e -> {
            boolean hasSelection = reservationsTable.getSelectedRow() >= 0;
            btnExtend.setEnabled(hasSelection);
            btnCancel.setEnabled(hasSelection);
        });

        JScrollPane scroll = new JScrollPane(reservationsTable);
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_LIGHT, 1, true));
        tableContainer.add(scroll, BorderLayout.CENTER);

        // Bottom Action Bar: Extend & Cancel
        JPanel actionsBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        actionsBar.setOpaque(false);

        btnExtend = new ModernButton("Extend Reservation (+1 hr)", VectorIconUtil.getIcon(VectorIconUtil.IconType.CLOCK, 14, Color.WHITE),
                new Color(37, 99, 235), new Color(29, 78, 216), Color.WHITE);
        btnExtend.setEnabled(false);
        btnExtend.addActionListener(e -> handleExtend());
        actionsBar.add(btnExtend);

        btnCancel = new ModernButton("Cancel Selected Booking", VectorIconUtil.getIcon(VectorIconUtil.IconType.CROSS, 14, Color.WHITE),
                new Color(220, 38, 38), new Color(185, 28, 28), Color.WHITE);
        btnCancel.setEnabled(false);
        btnCancel.addActionListener(e -> handleCancel());
        actionsBar.add(btnCancel);

        tableContainer.add(actionsBar, BorderLayout.SOUTH);
        centerPanel.add(tableContainer, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);
    }

    private void updateCalculatedEndTime() {
        try {
            int h = (Integer) startHourSpinner.getValue();
            int m = (Integer) startMinuteSpinner.getValue();
            int dur = (Integer) durationSpinner.getValue();

            LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Kolkata"))
                    .withHour(h).withMinute(m).withSecond(0).withNano(0);
            LocalDateTime end = start.plusHours(dur);
            calculatedEndLabel.setText("Ends: " + end.format(DateTimeFormatter.ofPattern("HH:mm (dd MMM)")));
        } catch (Exception ignored) {
            calculatedEndLabel.setText("");
        }
    }

    private void refreshSuitableBays() {
        VehicleType selectedType = (VehicleType) vehicleTypeBox.getSelectedItem();
        bayPickerBox.removeAllItems();

        List<ParkingSlot> allSlots = slotDao.findAll();
        List<String> suitableBays = new ArrayList<>();
        for (ParkingSlot s : allSlots) {
            if (s.isAvailable() && (selectedType == null || s.canFitVehicle(selectedType))) {
                suitableBays.add(s.getSlotId() + " (Floor " + s.getFloorNumber() + ")");
            }
        }

        if (suitableBays.isEmpty()) {
            bayPickerBox.addItem("No slots available");
            bayPickerBox.setEnabled(false);
        } else {
            bayPickerBox.setEnabled(true);
            for (String b : suitableBays) {
                bayPickerBox.addItem(b);
            }
        }
    }

    private void bookReservation() {
        String selectedBayItem = (String) bayPickerBox.getSelectedItem();
        if (selectedBayItem == null || selectedBayItem.startsWith("No slots")) {
            JOptionPane.showMessageDialog(this, "Please select an available parking bay.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String slotId = selectedBayItem.split(" ")[0].trim();
        String plate = plateField.getText().trim();
        VehicleType vType = (VehicleType) vehicleTypeBox.getSelectedItem();
        int dur = (Integer) durationSpinner.getValue();
        int h = (Integer) startHourSpinner.getValue();
        int m = (Integer) startMinuteSpinner.getValue();

        if (plate.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a vehicle license plate number.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!PlateUtil.isValid(plate)) {
            JOptionPane.showMessageDialog(this,
                    "Invalid plate format. Please enter a valid Uttar Pradesh (UP) or Bharat (BH) plate.\nExample: UP32AB1234 or 22BH1234AA",
                    "Invalid Plate", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Kolkata"))
                .withHour(h).withMinute(m).withSecond(0).withNano(0);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        if (start.isBefore(now.minusMinutes(2))) {
            JOptionPane.showMessageDialog(this, "Start time cannot be in the past.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Optional<ParkingSlot> sOpt = slotDao.findById(slotId);
        if (sOpt.isPresent() && vType != null && !sOpt.get().canFitVehicle(vType)) {
            JOptionPane.showMessageDialog(this, "Bay " + slotId + " is not suitable for vehicle type " + vType.name() + ".", "Slot Suitability", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDateTime end = start.plusHours(dur);

        try {
            Reservation res = reservationService.makeReservation(1, slotId, plate, vType, start, end);
            showConfirmationCard(res);

            plateField.setText(PlateUtil.generateRandomUp());
            refreshSuitableBays();
            refreshTable();
            if (onStateChange != null) {
                onStateChange.run();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Reservation Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showConfirmationCard(Reservation res) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Booking Confirmed", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(420, 480);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        // Escape key listener to close cleanly
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JLabel title = new JLabel("PARKING RESERVATION CONFIRMED");
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(new Color(22, 101, 52));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(12));

        // ZXing QR Code
        BufferedImage qrImg = BarcodeGenerator.generateQrCode("RES:" + res.getReservationCode() + ":" + res.getSlotId(), 130);
        JLabel qrLabel = new JLabel(new ImageIcon(qrImg));
        qrLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(qrLabel);
        content.add(Box.createVerticalStrut(14));

        // Details Grid
        JPanel grid = new JPanel(new GridLayout(6, 2, 6, 6));
        grid.setOpaque(false);
        grid.setMaximumSize(new Dimension(360, 160));

        grid.add(new JLabel("Booking Code:"));
        JLabel codeLbl = new JLabel(res.getReservationCode());
        codeLbl.setFont(new Font("Consolas", Font.BOLD, 13));
        codeLbl.setForeground(new Color(29, 78, 216));
        grid.add(codeLbl);

        grid.add(new JLabel("Allocated Bay:"));
        JLabel bayLbl = new JLabel(res.getSlotId());
        bayLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        grid.add(bayLbl);

        grid.add(new JLabel("Vehicle:"));
        grid.add(new JLabel(res.getVehicleType().name()));

        grid.add(new JLabel("License Plate:"));
        grid.add(new JLabel(PlateUtil.format(res.getLicensePlate())));

        grid.add(new JLabel("Start Window:"));
        grid.add(new JLabel(DateFormat.display(res.getStartTime())));

        grid.add(new JLabel("Valid Until:"));
        grid.add(new JLabel(DateFormat.display(res.getEndTime())));

        content.add(grid);
        content.add(Box.createVerticalStrut(16));

        // Footer buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        footer.setOpaque(false);

        ModernButton btnPrint = new ModernButton("Print Slip", VectorIconUtil.getIcon(VectorIconUtil.IconType.PRINT, 14, Color.WHITE),
                new Color(30, 41, 59), new Color(15, 23, 42), Color.WHITE);
        btnPrint.addActionListener(e -> JOptionPane.showMessageDialog(dialog, "Reservation pass sent to receipt printer.", "Print", JOptionPane.INFORMATION_MESSAGE));
        footer.add(btnPrint);

        ModernButton btnClose = ModernButton.createSecondary("Close (Esc)", null);
        btnClose.addActionListener(e -> dialog.dispose());
        footer.add(btnClose);

        content.add(footer);
        dialog.add(content);
        dialog.setVisible(true);
    }

    private void handleExtend() {
        int row = reservationsTable.getSelectedRow();
        if (row < 0) return;

        String code = (String) tableModel.getValueAt(row, 1);
        try {
            boolean extended = reservationService.extendReservation(code, 1);
            if (extended) {
                JOptionPane.showMessageDialog(this, "Reservation " + code + " extended by 1 hour.", "Extended", JOptionPane.INFORMATION_MESSAGE);
                refreshTable();
                if (onStateChange != null) onStateChange.run();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot Extend", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleCancel() {
        int row = reservationsTable.getSelectedRow();
        if (row < 0) return;

        String code = (String) tableModel.getValueAt(row, 1);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to cancel reservation " + code + "?\nThe bay will be immediately released.",
                "Confirm Cancellation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            boolean ok = reservationService.cancelReservation(code);
            if (ok) {
                JOptionPane.showMessageDialog(this, "Reservation cancelled and bay released.", "Cancelled", JOptionPane.INFORMATION_MESSAGE);
                refreshSuitableBays();
                refreshTable();
                if (onStateChange != null) onStateChange.run();
            }
        }
    }

    private void purgeExpired() {
        int purged = reservationService.purgeExpiredReservations(15);
        JOptionPane.showMessageDialog(this,
                "Audit complete: Purged " + purged + " overdue no-shows past the 15-minute grace period.",
                "Purge Complete", JOptionPane.INFORMATION_MESSAGE);
        refreshSuitableBays();
        refreshTable();
        if (onStateChange != null) {
            onStateChange.run();
        }
    }

    public void refreshTable() {
        tableModel.setRowCount(0);
        List<Reservation> list = reservationDao.findConfirmedReservationsPastStartTime(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).plusDays(30));
        for (Reservation r : list) {
            tableModel.addRow(new Object[]{
                    r.getReservationId(),
                    r.getReservationCode(),
                    r.getSlotId(),
                    PlateUtil.format(r.getLicensePlate()) + " [" + PlateUtil.district(r.getLicensePlate()) + "]",
                    r.getVehicleType(),
                    DateFormat.display(r.getStartTime()),
                    DateFormat.display(r.getEndTime()),
                    r.getStatus().name()
            });
        }
    }

    /**
     * High-contrast badge renderer for reservation statuses.
     */
    private static class StatusBadgeRenderer extends DefaultTableCellRenderer {
        public StatusBadgeRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(new Font("Segoe UI", Font.BOLD, 11));
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String status = value != null ? value.toString().toUpperCase() : "";
            setText(status);

            switch (status) {
                case "CONFIRMED" -> {
                    setBackground(new Color(220, 252, 231)); // emerald-100
                    setForeground(new Color(22, 101, 52));   // emerald-800
                }
                case "CHECKED_IN" -> {
                    setBackground(new Color(219, 234, 254)); // blue-100
                    setForeground(new Color(30, 64, 175));   // blue-800
                }
                case "CANCELLED" -> {
                    setBackground(new Color(254, 226, 226)); // red-100
                    setForeground(new Color(153, 27, 27));   // red-800
                }
                case "EXPIRED" -> {
                    setBackground(new Color(254, 243, 199)); // amber-100
                    setForeground(new Color(146, 64, 14));   // amber-800
                }
                default -> {
                    setBackground(new Color(241, 245, 249));
                    setForeground(new Color(51, 65, 85));
                }
            }
            setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            return this;
        }
    }
}
