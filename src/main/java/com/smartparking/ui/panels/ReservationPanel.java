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
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        initUI();
        refreshSuitableBays();
        refreshTable();
    }

    private JLabel formError;
    private JLabel countLabel;
    private CardLayout tableCards;
    private JPanel tableHolder;

    private void initUI() {
        // --- Header ---
        com.smartparking.ui.components.PageHeader header = new com.smartparking.ui.components.PageHeader(
                "Advance booking", "Hold a bay ahead of arrival · bookings not claimed within 15 minutes are released");
        ModernButton btnPurge = ModernButton.createSecondary("Release no-shows",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.REFRESH, 14, UITheme.TEXT_SECONDARY));
        btnPurge.setToolTipText("Expire bookings more than 15 minutes past their start time and free the bays");
        btnPurge.addActionListener(e -> purgeExpired());
        header.addAction(btnPurge);
        add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(16, 0));
        center.setOpaque(false);
        center.add(buildFormCard(), BorderLayout.WEST);
        center.add(buildTableCard(), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UITheme.font(Font.BOLD, 12));
        l.setForeground(UITheme.TEXT_BODY);
        return l;
    }

    private JComponent buildFormCard() {
        RoundedPanel formCard = new RoundedPanel(UITheme.RADIUS_LG, UITheme.BG_CARD, UITheme.BORDER_SUBTLE);
        formCard.setLayout(new BorderLayout(0, 16));
        formCard.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        formCard.setPreferredSize(new Dimension(340, 0));
        formCard.add(com.smartparking.ui.components.PageHeader.cardTitle("New booking"), BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        // Vehicle Classification
        c.insets = new Insets(0, 0, 6, 0);
        form.add(fieldLabel("Vehicle type"), c);
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
        vehicleTypeBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof VehicleType vt) setText(vt.getDisplayName());
                return this;
            }
        });
        vehicleTypeBox.addActionListener(e -> refreshSuitableBays());
        c.gridy++;
        c.insets = new Insets(0, 0, 14, 0);
        form.add(vehicleTypeBox, c);

        // Bay picker
        c.gridy++;
        c.insets = new Insets(0, 0, 6, 0);
        form.add(fieldLabel("Bay"), c);
        bayPickerBox = new JComboBox<>();
        UITheme.styleComboBox(bayPickerBox);
        bayPickerBox.setToolTipText("Only free bays that fit the selected vehicle are listed");
        c.gridy++;
        c.insets = new Insets(0, 0, 14, 0);
        form.add(bayPickerBox, c);

        // License Plate Input
        c.gridy++;
        c.insets = new Insets(0, 0, 6, 0);
        form.add(fieldLabel("Licence plate"), c);
        JPanel plateRow = new JPanel(new BorderLayout(8, 0));
        plateRow.setOpaque(false);
        plateField = new JTextField(PlateUtil.generateRandomUp(), 11);
        UITheme.styleTextField(plateField);
        plateField.setFont(UITheme.mono(Font.BOLD, 14));
        plateField.getAccessibleContext().setAccessibleName("Licence plate");
        plateField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { clearFormError(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { clearFormError(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        plateRow.add(plateField, BorderLayout.CENTER);
        ModernButton btnRegenPlate = ModernButton.createSecondary(null, VectorIconUtil.getIcon(VectorIconUtil.IconType.DICE, 14, UITheme.TEXT_SECONDARY));
        btnRegenPlate.setToolTipText("Generate a random valid UP plate");
        btnRegenPlate.getAccessibleContext().setAccessibleName("Random plate");
        btnRegenPlate.addActionListener(e -> plateField.setText(PlateUtil.generateRandomUp()));
        plateRow.add(btnRegenPlate, BorderLayout.EAST);
        c.gridy++;
        c.insets = new Insets(0, 0, 14, 0);
        form.add(plateRow, c);

        // Start Date/Time (Rounded to next 15-min mark)
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        int curMin = now.getMinute();
        int rem = curMin % 15;
        int addMin = rem == 0 ? 0 : (15 - rem);
        LocalDateTime roundedStart = now.plusMinutes(addMin);

        c.gridy++;
        c.gridwidth = 1;
        c.insets = new Insets(0, 0, 6, 10);
        form.add(fieldLabel("Starts (IST, today)"), c);
        c.gridx = 1;
        c.insets = new Insets(0, 0, 6, 0);
        form.add(fieldLabel("Duration"), c);

        startHourSpinner = new JSpinner(new SpinnerNumberModel(roundedStart.getHour(), 0, 23, 1));
        startMinuteSpinner = new JSpinner(new SpinnerNumberModel(roundedStart.getMinute(), 0, 45, 15));
        ((JSpinner.DefaultEditor) startHourSpinner.getEditor()).getTextField().setColumns(2);
        ((JSpinner.DefaultEditor) startMinuteSpinner.getEditor()).getTextField().setColumns(2);
        startHourSpinner.getAccessibleContext().setAccessibleName("Start hour");
        startMinuteSpinner.getAccessibleContext().setAccessibleName("Start minute");
        JPanel time = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        time.setOpaque(false);
        time.add(startHourSpinner);
        JLabel colon = new JLabel(":");
        colon.setForeground(UITheme.TEXT_SECONDARY);
        time.add(colon);
        time.add(startMinuteSpinner);

        durationSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 24, 1));
        ((JSpinner.DefaultEditor) durationSpinner.getEditor()).getTextField().setColumns(2);
        durationSpinner.getAccessibleContext().setAccessibleName("Duration in hours");
        JPanel dur = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dur.setOpaque(false);
        dur.add(durationSpinner);
        JLabel hrs = new JLabel("hours");
        hrs.setFont(UITheme.font(Font.PLAIN, 12));
        hrs.setForeground(UITheme.TEXT_SECONDARY);
        dur.add(hrs);

        durationSpinner.addChangeListener(e -> updateCalculatedEndTime());
        startHourSpinner.addChangeListener(e -> updateCalculatedEndTime());
        startMinuteSpinner.addChangeListener(e -> updateCalculatedEndTime());

        c.gridy++;
        c.gridx = 0;
        c.insets = new Insets(0, -4, 10, 10);
        form.add(time, c);
        c.gridx = 1;
        c.insets = new Insets(0, -6, 10, 0);
        form.add(dur, c);

        // Calculated End Time
        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 2;
        c.insets = new Insets(0, 0, 6, 0);
        calculatedEndLabel = new JLabel();
        calculatedEndLabel.setFont(UITheme.font(Font.PLAIN, 12));
        calculatedEndLabel.setForeground(UITheme.TEXT_SECONDARY);
        calculatedEndLabel.setIcon(VectorIconUtil.getIcon(VectorIconUtil.IconType.CLOCK, 13, UITheme.TEXT_TERTIARY));
        calculatedEndLabel.setIconTextGap(8);
        form.add(calculatedEndLabel, c);
        updateCalculatedEndTime();

        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        formError = new JLabel(" ");
        formError.setFont(UITheme.font(Font.PLAIN, 12));
        formError.setForeground(UITheme.ERROR_TEXT);
        form.add(formError, c);

        c.gridy++;
        c.weighty = 1;
        form.add(Box.createGlue(), c);
        formCard.add(form, BorderLayout.CENTER);

        ModernButton btnBook = new ModernButton("Confirm booking", VectorIconUtil.getIcon(VectorIconUtil.IconType.CHECK, 14, Color.WHITE));
        btnBook.setPreferredSize(new Dimension(200, 42));
        btnBook.addActionListener(e -> bookReservation());
        formCard.add(btnBook, BorderLayout.SOUTH);
        return formCard;
    }

    private JComponent buildTableCard() {
        RoundedPanel card = new RoundedPanel(UITheme.RADIUS_LG, UITheme.BG_CARD, UITheme.BORDER_SUBTLE);
        card.setLayout(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 18, 18));

        JPanel head = new JPanel(new BorderLayout(12, 0));
        head.setOpaque(false);
        JPanel titles = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titles.setOpaque(false);
        titles.add(com.smartparking.ui.components.PageHeader.cardTitle("Upcoming bookings"));
        countLabel = new JLabel("0");
        countLabel.setFont(UITheme.font(Font.BOLD, 12));
        countLabel.setForeground(UITheme.TEXT_TERTIARY);
        titles.add(countLabel);
        JPanel tw = new JPanel(new GridBagLayout());
        tw.setOpaque(false);
        tw.add(titles);
        head.add(tw, BorderLayout.WEST);

        JPanel actionsBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionsBar.setOpaque(false);
        btnExtend = ModernButton.createSecondary("Extend +1 h", VectorIconUtil.getIcon(VectorIconUtil.IconType.CLOCK, 13, UITheme.TEXT_SECONDARY)).compact();
        btnExtend.setToolTipText("Extend the selected booking by one hour");
        btnExtend.setEnabled(false);
        btnExtend.addActionListener(e -> handleExtend());
        actionsBar.add(btnExtend);
        btnCancel = ModernButton.createDanger("Cancel booking", null).compact();
        btnCancel.setToolTipText("Cancel the selected booking and release its bay");
        btnCancel.setEnabled(false);
        btnCancel.addActionListener(e -> handleCancel());
        actionsBar.add(btnCancel);
        head.add(actionsBar, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        String[] cols = {"ID", "Code", "Bay", "Licence plate", "Vehicle", "Starts (IST)", "Ends (IST)", "Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        reservationsTable = new JTable(tableModel);
        reservationsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        reservationsTable.getColumnModel().getColumn(7).setCellRenderer(new com.smartparking.ui.components.Badge.Renderer());
        reservationsTable.getSelectionModel().addListSelectionListener(e -> {
            boolean hasSelection = reservationsTable.getSelectedRow() >= 0;
            btnExtend.setEnabled(hasSelection);
            btnCancel.setEnabled(hasSelection);
        });

        JScrollPane scroll = new JScrollPane(reservationsTable);
        scroll.setBorder(new com.smartparking.ui.theme.DarkTheme.RoundBorder(UITheme.BORDER_SUBTLE, UITheme.RADIUS));

        tableCards = new CardLayout();
        tableHolder = new JPanel(tableCards);
        tableHolder.setOpaque(false);
        tableHolder.add(scroll, "TABLE");
        tableHolder.add(new com.smartparking.ui.components.EmptyState(VectorIconUtil.IconType.CALENDAR, "No upcoming bookings",
                "Bookings you create appear here until the vehicle arrives or the booking expires."), "EMPTY");
        card.add(tableHolder, BorderLayout.CENTER);
        return card;
    }

    private void showFormError(String msg) {
        formError.setText("<html>" + msg + "</html>");
        formError.setIcon(VectorIconUtil.getIcon(VectorIconUtil.IconType.ALERT, 13, UITheme.ERROR_TEXT));
    }

    private void clearFormError() {
        if (formError != null) {
            formError.setText(" ");
            formError.setIcon(null);
        }
    }

    private void updateCalculatedEndTime() {
        try {
            int h = (Integer) startHourSpinner.getValue();
            int m = (Integer) startMinuteSpinner.getValue();
            int dur = (Integer) durationSpinner.getValue();

            LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Kolkata"))
                    .withHour(h).withMinute(m).withSecond(0).withNano(0);
            LocalDateTime end = start.plusHours(dur);
            calculatedEndLabel.setText("Ends at " + end.format(DateTimeFormatter.ofPattern("HH:mm, dd MMM")));
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
                suitableBays.add(s.getSlotId() + "  ·  Level " + s.getFloorNumber());
            }
        }

        if (suitableBays.isEmpty()) {
            bayPickerBox.addItem("No free bays for this vehicle");
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
        if (selectedBayItem == null || selectedBayItem.startsWith("No free bays")) {
            showFormError("Choose a free bay for this vehicle.");
            return;
        }

        String slotId = selectedBayItem.split(" ")[0].trim();
        String plate = plateField.getText().trim();
        VehicleType vType = (VehicleType) vehicleTypeBox.getSelectedItem();
        int dur = (Integer) durationSpinner.getValue();
        int h = (Integer) startHourSpinner.getValue();
        int m = (Integer) startMinuteSpinner.getValue();

        if (plate.isEmpty()) {
            showFormError("Enter the vehicle's licence plate.");
            plateField.requestFocusInWindow();
            return;
        }

        if (!PlateUtil.isValid(plate)) {
            showFormError("Enter a valid UP or Bharat (BH) plate, e.g. UP32AB1234 or 22BH1234AA.");
            plateField.requestFocusInWindow();
            return;
        }

        LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Kolkata"))
                .withHour(h).withMinute(m).withSecond(0).withNano(0);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        if (start.isBefore(now.minusMinutes(2))) {
            showFormError("The start time can't be in the past.");
            return;
        }

        Optional<ParkingSlot> sOpt = slotDao.findById(slotId);
        if (sOpt.isPresent() && vType != null && !sOpt.get().canFitVehicle(vType)) {
            showFormError("Bay " + slotId + " doesn't fit a " + vType.getDisplayName() + ".");
            return;
        }

        LocalDateTime end = start.plusHours(dur);

        try {
            Reservation res = reservationService.makeReservation(1, slotId, plate, vType, start, end);
            clearFormError();
            showConfirmationCard(res);

            plateField.setText(PlateUtil.generateRandomUp());
            refreshSuitableBays();
            refreshTable();
            if (onStateChange != null) {
                onStateChange.run();
            }
        } catch (Exception ex) {
            showFormError(ex.getMessage());
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
        content.setBackground(UITheme.BG_CARD);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JLabel title = new JLabel("Booking confirmed", VectorIconUtil.getIcon(VectorIconUtil.IconType.CHECK, 16, UITheme.SUCCESS_TEXT), SwingConstants.CENTER);
        title.setIconTextGap(8);
        title.setFont(UITheme.font(Font.BOLD, 17));
        title.setForeground(UITheme.TEXT_PRIMARY);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(12));

        // ZXing QR Code
        BufferedImage qrImg = BarcodeGenerator.generateQrCode("RES:" + res.getReservationCode() + ":" + res.getSlotId(), 130);
        JLabel qrLabel = new JLabel(new ImageIcon(qrImg));
        qrLabel.setOpaque(true);
        qrLabel.setBackground(Color.WHITE);
        qrLabel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        qrLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(qrLabel);
        content.add(Box.createVerticalStrut(14));

        // Details Grid
        JPanel grid = new JPanel(new GridLayout(6, 2, 6, 6));
        grid.setOpaque(false);
        grid.setMaximumSize(new Dimension(360, 160));

        grid.add(muted("Booking code"));
        JLabel codeLbl = new JLabel(res.getReservationCode());
        codeLbl.setFont(UITheme.mono(Font.BOLD, 13));
        codeLbl.setForeground(UITheme.ACCENT_TEXT);
        grid.add(codeLbl);

        grid.add(muted("Bay"));
        JLabel bayLbl = new JLabel(res.getSlotId());
        bayLbl.setFont(UITheme.font(Font.BOLD, 12));
        grid.add(bayLbl);

        grid.add(muted("Vehicle"));
        grid.add(strong(res.getVehicleType().getDisplayName()));

        grid.add(muted("Licence plate"));
        grid.add(strong(PlateUtil.format(res.getLicensePlate())));

        grid.add(muted("Starts"));
        grid.add(strong(DateFormat.display(res.getStartTime())));

        grid.add(muted("Valid until"));
        grid.add(strong(DateFormat.display(res.getEndTime())));

        content.add(grid);
        content.add(Box.createVerticalStrut(16));

        // Footer buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        footer.setOpaque(false);

        ModernButton btnPrint = ModernButton.createSecondary("Print slip (simulated)", VectorIconUtil.getIcon(VectorIconUtil.IconType.PRINT, 14, UITheme.TEXT_SECONDARY));
        btnPrint.addActionListener(e -> com.smartparking.ui.components.Toast.success(dialog, "Booking slip sent to the printer (simulated)", res.getReservationCode()));
        footer.add(btnPrint);

        ModernButton btnClose = new ModernButton("Done", null);
        dialog.getRootPane().setDefaultButton(btnClose);
        btnClose.addActionListener(e -> dialog.dispose());
        footer.add(btnClose);

        content.add(footer);
        dialog.add(content);
        dialog.setVisible(true);
    }

    private static JLabel muted(String t) {
        JLabel l = new JLabel(t);
        l.setFont(UITheme.font(Font.PLAIN, 13));
        l.setForeground(UITheme.TEXT_SECONDARY);
        return l;
    }

    private static JLabel strong(String t) {
        JLabel l = new JLabel(t);
        l.setFont(UITheme.font(Font.BOLD, 13));
        l.setForeground(UITheme.TEXT_PRIMARY);
        return l;
    }

    private void handleExtend() {
        int row = reservationsTable.getSelectedRow();
        if (row < 0) return;

        String code = (String) tableModel.getValueAt(row, 1);
        try {
            boolean extended = reservationService.extendReservation(code, 1);
            if (extended) {
                com.smartparking.ui.components.Toast.success(this, "Booking extended by 1 hour", code);
                refreshTable();
                if (onStateChange != null) onStateChange.run();
            }
        } catch (Exception ex) {
            com.smartparking.ui.components.Toast.error(this, "Couldn't extend booking", ex.getMessage());
        }
    }

    private void handleCancel() {
        int row = reservationsTable.getSelectedRow();
        if (row < 0) return;

        String code = (String) tableModel.getValueAt(row, 1);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Cancel booking " + code + "?\nThe bay is released immediately. This can't be undone.",
                "Cancel booking", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            boolean ok = reservationService.cancelReservation(code);
            if (ok) {
                com.smartparking.ui.components.Toast.success(this, "Booking cancelled", code + " · bay released");
                refreshSuitableBays();
                refreshTable();
                if (onStateChange != null) onStateChange.run();
            }
        }
    }

    private void purgeExpired() {
        int purged = reservationService.purgeExpiredReservations(15);
        if (purged > 0) {
            com.smartparking.ui.components.Toast.success(this, purged + (purged == 1 ? " no-show released" : " no-shows released"), "Bays are free again");
        } else {
            com.smartparking.ui.components.Toast.info(this, "No no-shows to release", "Every booking is within its 15-minute grace period");
        }
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
                    PlateUtil.format(r.getLicensePlate()) + "  ·  " + PlateUtil.district(r.getLicensePlate()),
                    r.getVehicleType() != null ? r.getVehicleType().getDisplayName() : "—",
                    DateFormat.display(r.getStartTime()),
                    DateFormat.display(r.getEndTime()),
                    r.getStatus().name()
            });
        }
        if (countLabel != null) countLabel.setText(String.valueOf(list.size()));
        if (tableCards != null) tableCards.show(tableHolder, list.isEmpty() ? "EMPTY" : "TABLE");
    }

    /**
     * High-contrast badge renderer for reservation statuses.
     */
    private static class StatusBadgeRenderer extends DefaultTableCellRenderer {
        public StatusBadgeRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(UITheme.font(Font.BOLD, 11));
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String status = value != null ? value.toString().toUpperCase() : "";
            setText(status);

            switch (status) {
                case "CONFIRMED" -> {
                    setBackground(UITheme.BG_ELEVATED); // emerald-100
                    setForeground(new Color(139, 238, 177));   // emerald-800
                }
                case "CHECKED_IN" -> {
                    setBackground(UITheme.BG_HOVER); // blue-100
                    setForeground(UITheme.ACCENT_TEXT);   // blue-800
                }
                case "CANCELLED" -> {
                    setBackground(UITheme.BG_HOVER); // red-100
                    setForeground(new Color(242, 136, 136));   // red-800
                }
                case "EXPIRED" -> {
                    setBackground(new Color(52, 51, 35)); // amber-100
                    setForeground(new Color(250, 174, 127));   // amber-800
                }
                default -> {
                    setBackground(UITheme.BG_ELEVATED);
                    setForeground(UITheme.TEXT_BODY);
                }
            }
            setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            return this;
        }
    }
}
