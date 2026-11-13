package com.smartparking.ui.panels;

import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.fastag.FastagAccount;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.vehicle.*;
import com.smartparking.patterns.strategy.allocation.BestFitAllocationStrategy;
import com.smartparking.patterns.strategy.allocation.NearestFirstAllocationStrategy;
import com.smartparking.patterns.strategy.allocation.RandomAllocationStrategy;
import com.smartparking.service.ParkingService;
import com.smartparking.ui.components.Badge;
import com.smartparking.ui.components.EmptyState;
import com.smartparking.ui.components.ModernButton;
import com.smartparking.ui.components.PageHeader;
import com.smartparking.ui.components.RoundedPanel;
import com.smartparking.ui.components.Toast;
import com.smartparking.ui.theme.DarkTheme;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.VectorIconUtil;
import com.smartparking.ui.util.VectorIconUtil.IconType;
import com.smartparking.util.BarcodeGenerator;
import com.smartparking.util.DateFormat;
import com.smartparking.util.MoneyFormat;
import com.smartparking.util.PlateUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gate 1 — vehicle entry.
 *
 * <pre>
 *  Vehicle entry
 *  ┌ Vehicle details ────────────────┐ ┌ Ticket ─────────────────┐
 *  │ Vehicle type  [tiles x7]        │ │ (empty state | ticket)   │
 *  │ Licence plate [field] [Random]  │ │  TKT-..  Bay A14 · L1    │
 *  │ Options  [permit] [EV charge]   │ │  plate, time, barcode    │
 *  │ Allocation [combo]              │ │                          │
 *  │ [ Park vehicle & issue ticket ] │ │                          │
 *  └─────────────────────────────────┘ └──────────────────────────┘
 * </pre>
 * Business flow is unchanged: the selected allocation strategy is applied to {@link ParkingService}
 * and {@link ParkingService#processVehicleEntry} issues the ticket.
 */
public class VehicleEntryPanel extends JPanel {

    private final ParkingService parkingService;
    private final Runnable onStateChange;

    private VehicleType selectedVehicleType = VehicleType.CAR;
    private JTextField plateField;
    private JLabel plateError;
    private JLabel fastagBadgeLabel;
    private JCheckBox permitCheck;
    private JCheckBox evChargingCheck;
    private JLabel evHint;
    private JComboBox<String> strategyBox;
    private ModernButton btnPark;

    // Ticket side
    private CardLayout ticketCards;
    private JPanel ticketHolder;
    private JLabel tNumber, tSlot, tFloor, tPlate, tDistrict, tType, tTime, tMode;
    private JPanel tFlags;
    private JLabel ticketBarcodeLabel;
    private JLabel ticketQrLabel;

    // Type selection tiles for 7 classes
    private final List<VehicleTypeCard> typeCards = new ArrayList<>();

    public VehicleEntryPanel(ParkingService parkingService, Runnable onStateChange) {
        this.parkingService = parkingService;
        this.onStateChange = onStateChange;

        setLayout(new BorderLayout(0, 0));
        setBackground(UITheme.BG_CANVAS);
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        initUI();
    }

    private void initUI() {
        add(new PageHeader("Vehicle entry", "Gate 1 · register an arriving vehicle, allocate a bay and issue its ticket"), BorderLayout.NORTH);

        JPanel split = new JPanel(new GridBagLayout());
        split.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1;
        gc.gridx = 0;
        gc.weightx = 0.56;
        gc.insets = new Insets(0, 0, 0, 16);
        split.add(buildFormCard(), gc);
        gc.gridx = 1;
        gc.weightx = 0.44;
        gc.insets = new Insets(0, 0, 0, 0);
        split.add(buildTicketCard(), gc);
        add(split, BorderLayout.CENTER);

        updateSelectionUI();
        updateFastagDetection();
    }

    // ------------------------------------------------------------------ form
    private JComponent buildFormCard() {
        RoundedPanel card = new RoundedPanel(UITheme.RADIUS_LG, UITheme.BG_CARD, UITheme.BORDER_SUBTLE);
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        // 1. Vehicle type
        c.insets = new Insets(0, 0, 10, 0);
        form.add(sectionLabel("Vehicle type", "Pick the class shown at the gate"), c);
        c.gridy++;
        c.insets = new Insets(0, 0, 22, 0);
        JPanel typeGrid = new JPanel(new GridLayout(2, 4, 8, 8));
        typeGrid.setOpaque(false);
        typeCards.clear();
        typeCards.add(new VehicleTypeCard("Motorcycle", "2-wheeler", VehicleType.MOTORCYCLE));
        typeCards.add(new VehicleTypeCard("Auto rickshaw", "3-wheeler", VehicleType.AUTO_RICKSHAW));
        typeCards.add(new VehicleTypeCard("Car", "4-wheeler", VehicleType.CAR));
        typeCards.add(new VehicleTypeCard("Electric car", "EV 4-wheeler", VehicleType.EV_CAR));
        typeCards.add(new VehicleTypeCard("Electric bike", "EV 2-wheeler", VehicleType.EV_TWO_WHEELER));
        typeCards.add(new VehicleTypeCard("Light commercial", "LCV", VehicleType.LCV));
        typeCards.add(new VehicleTypeCard("Heavy commercial", "Truck / bus", VehicleType.HCV));
        for (VehicleTypeCard t : typeCards) typeGrid.add(t);
        form.add(typeGrid, c);

        // 2. Plate
        c.gridy++;
        c.insets = new Insets(0, 0, 10, 0);
        form.add(sectionLabel("Licence plate", "Read by the ANPR camera (simulated); edit if needed"), c);
        c.gridy++;
        c.insets = new Insets(0, 0, 6, 0);
        JPanel plateRow = new JPanel(new BorderLayout(8, 0));
        plateRow.setOpaque(false);
        plateField = new JTextField(PlateUtil.generateRandomUp());
        UITheme.styleTextField(plateField);
        plateField.setFont(UITheme.mono(Font.BOLD, 15));
        plateField.setPreferredSize(new Dimension(200, 40));
        plateField.getAccessibleContext().setAccessibleName("Licence plate");
        plateField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { onPlateEdited(); }
            public void removeUpdate(DocumentEvent e) { onPlateEdited(); }
            public void changedUpdate(DocumentEvent e) { onPlateEdited(); }
        });
        plateField.addActionListener(e -> processEntry());
        plateRow.add(plateField, BorderLayout.CENTER);
        ModernButton btnRegen = ModernButton.createSecondary("Random plate",
                VectorIconUtil.getIcon(IconType.DICE, 14, UITheme.TEXT_SECONDARY));
        btnRegen.setToolTipText("Simulate a new ANPR read");
        btnRegen.addActionListener(e -> {
            plateField.setText(PlateUtil.generateRandomUp());
            plateField.requestFocusInWindow();
        });
        plateRow.add(btnRegen, BorderLayout.EAST);
        form.add(plateRow, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 4, 0);
        plateError = new JLabel(" ");
        plateError.setFont(UITheme.font(Font.PLAIN, 12));
        plateError.setForeground(UITheme.ERROR_TEXT);
        form.add(plateError, c);
        c.gridy++;
        c.insets = new Insets(0, 0, 22, 0);
        fastagBadgeLabel = new JLabel(" ");
        fastagBadgeLabel.setFont(UITheme.font(Font.PLAIN, 12));
        JPanel fastagWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        fastagWrap.setOpaque(false);
        fastagWrap.add(fastagBadgeLabel);
        form.add(fastagWrap, c);

        // 3. Options
        c.gridy++;
        c.insets = new Insets(0, 0, 10, 0);
        form.add(sectionLabel("Options", null), c);
        c.gridy++;
        c.insets = new Insets(0, 0, 4, 0);
        permitCheck = new JCheckBox("Disability (Divyang) parking permit verified");
        styleCheck(permitCheck);
        form.add(optionRow(IconType.WHEELCHAIR, UITheme.SLOT_ACCESSIBLE, permitCheck, null), c);
        c.gridy++;
        c.insets = new Insets(0, 0, 22, 0);
        evChargingCheck = new JCheckBox("Start an EV charging session");
        styleCheck(evChargingCheck);
        evChargingCheck.setEnabled(false);
        evHint = new JLabel("Electric vehicles only");
        form.add(optionRow(IconType.PLUG, UITheme.SLOT_EV, evChargingCheck, evHint), c);

        // 4. Allocation
        c.gridy++;
        c.insets = new Insets(0, 0, 10, 0);
        form.add(sectionLabel("Bay allocation", "How the system chooses a free bay"), c);
        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        String[] strategies = {
                "Nearest available bay (ground floor first)",
                "Best fit for vehicle size",
                "Distributed (spread across levels)"
        };
        strategyBox = new JComboBox<>(strategies);
        UITheme.styleComboBox(strategyBox);
        strategyBox.setPreferredSize(new Dimension(200, 40));
        strategyBox.getAccessibleContext().setAccessibleName("Bay allocation mode");
        form.add(strategyBox, c);

        // filler so the button stays at the bottom
        c.gridy++;
        c.weighty = 1;
        form.add(Box.createGlue(), c);

        com.smartparking.ui.components.ScrollablePanel track = new com.smartparking.ui.components.ScrollablePanel(new BorderLayout());
        track.setOpaque(false);
        track.add(form, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(track);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        card.add(scroll, BorderLayout.CENTER);

        JPanel foot = new JPanel(new BorderLayout());
        foot.setOpaque(false);
        foot.setBorder(BorderFactory.createEmptyBorder(18, 0, 0, 0));
        btnPark = new ModernButton("Park vehicle & issue ticket", VectorIconUtil.getIcon(IconType.TICKET, 16, Color.WHITE));
        btnPark.setFont(UITheme.font(Font.BOLD, 14));
        btnPark.setPreferredSize(new Dimension(200, 44));
        btnPark.setToolTipText("Allocate a bay and issue the ticket (Enter in the plate field)");
        btnPark.addActionListener(e -> processEntry());
        foot.add(btnPark, BorderLayout.CENTER);
        card.add(foot, BorderLayout.SOUTH);
        return card;
    }

    private static JComponent sectionLabel(String title, String hint) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(UITheme.font(Font.BOLD, 13));
        t.setForeground(UITheme.TEXT_PRIMARY);
        p.add(t);
        if (hint != null) {
            p.add(Box.createVerticalStrut(2));
            JLabel h = new JLabel(hint);
            h.setFont(UITheme.font(Font.PLAIN, 12));
            h.setForeground(UITheme.TEXT_TERTIARY);
            p.add(h);
        }
        return p;
    }

    private static void styleCheck(JCheckBox cb) {
        cb.setFont(UITheme.font(Font.PLAIN, 13));
        cb.setOpaque(false);
        cb.setForeground(UITheme.TEXT_BODY);
        cb.setFocusPainted(false);
    }

    private static JComponent optionRow(IconType icon, Color color, JCheckBox cb, JLabel hint) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        JLabel ic = new JLabel(VectorIconUtil.getIcon(icon, 16, color));
        ic.setPreferredSize(new Dimension(20, 20));
        row.add(ic, BorderLayout.WEST);
        row.add(cb, BorderLayout.CENTER);
        if (hint != null) {
            hint.setFont(UITheme.font(Font.PLAIN, 12));
            hint.setForeground(UITheme.TEXT_TERTIARY);
            row.add(hint, BorderLayout.EAST);
        }
        return row;
    }

    // ------------------------------------------------------------------ ticket
    private JComponent buildTicketCard() {
        RoundedPanel card = new RoundedPanel(UITheme.RADIUS_LG, UITheme.BG_CARD, UITheme.BORDER_SUBTLE);
        card.setLayout(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(PageHeader.cardTitle("Issued ticket"), BorderLayout.WEST);
        card.add(head, BorderLayout.NORTH);

        ticketCards = new CardLayout();
        ticketHolder = new JPanel(ticketCards);
        ticketHolder.setOpaque(false);
        EmptyState empty = new EmptyState(IconType.TICKET, "No ticket issued yet",
                "Choose the vehicle type, confirm the plate and park it. The ticket appears here.");
        ticketHolder.add(empty, "EMPTY");
        ticketHolder.add(buildTicketView(), "TICKET");
        ticketCards.show(ticketHolder, "EMPTY");
        card.add(ticketHolder, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildTicketView() {
        JPanel v = new JPanel(new GridBagLayout());
        v.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        // Hero: allocated bay
        JPanel hero = new RoundedPanel(UITheme.RADIUS_LG, UITheme.tint(UITheme.SUCCESS), UITheme.tintBorder(UITheme.SUCCESS));
        hero.setLayout(new BorderLayout(14, 0));
        hero.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        JPanel heroText = new JPanel();
        heroText.setOpaque(false);
        heroText.setLayout(new BoxLayout(heroText, BoxLayout.Y_AXIS));
        JLabel cap = new JLabel("PROCEED TO BAY");
        cap.setFont(UITheme.FONT_CAPTION);
        cap.setForeground(UITheme.SUCCESS_TEXT);
        tSlot = new JLabel("—");
        tSlot.setFont(UITheme.font(Font.BOLD, 34));
        tSlot.setForeground(UITheme.TEXT_PRIMARY);
        tFloor = new JLabel(" ");
        tFloor.setFont(UITheme.font(Font.PLAIN, 13));
        tFloor.setForeground(UITheme.TEXT_BODY);
        heroText.add(cap);
        heroText.add(Box.createVerticalStrut(4));
        heroText.add(tSlot);
        heroText.add(tFloor);
        hero.add(heroText, BorderLayout.CENTER);
        ticketQrLabel = new JLabel();
        ticketQrLabel.setOpaque(true);
        ticketQrLabel.setBackground(Color.WHITE);
        ticketQrLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JPanel qrWrap = new JPanel(new GridBagLayout());
        qrWrap.setOpaque(false);
        qrWrap.add(ticketQrLabel);
        hero.add(qrWrap, BorderLayout.EAST);
        c.insets = new Insets(0, 0, 18, 0);
        v.add(hero, c);

        // Details
        tNumber = detailValue();
        tPlate = detailValue();
        tPlate.setFont(UITheme.mono(Font.BOLD, 14));
        tDistrict = new JLabel(" ");
        tType = detailValue();
        tTime = detailValue();
        tMode = detailValue();
        c.insets = new Insets(0, 0, 0, 0);
        c.gridy++;
        v.add(detail("Ticket number", tNumber), c);
        c.gridy++;
        v.add(detail("Licence plate", tPlate), c);
        c.gridy++;
        v.add(detail("Vehicle", tType), c);
        c.gridy++;
        v.add(detail("Entry time", tTime), c);
        c.gridy++;
        v.add(detail("Allocation", tMode), c);

        c.gridy++;
        c.insets = new Insets(10, 0, 16, 0);
        tFlags = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tFlags.setOpaque(false);
        v.add(tFlags, c);

        // Barcode strip on a white "paper" surface
        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        ticketBarcodeLabel = new JLabel();
        ticketBarcodeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        ticketBarcodeLabel.setOpaque(true);
        ticketBarcodeLabel.setBackground(Color.WHITE);
        ticketBarcodeLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        v.add(ticketBarcodeLabel, c);

        c.gridy++;
        c.weighty = 1;
        v.add(Box.createGlue(), c);
        return v;
    }

    private static JLabel detailValue() {
        JLabel l = new JLabel("—");
        l.setFont(UITheme.font(Font.BOLD, 13));
        l.setForeground(UITheme.TEXT_PRIMARY);
        l.setHorizontalAlignment(SwingConstants.RIGHT);
        return l;
    }

    private static JComponent detail(String key, JLabel value) {
        JPanel r = new JPanel(new BorderLayout(12, 0));
        r.setOpaque(false);
        r.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(9, 0, 9, 0)));
        JLabel k = new JLabel(key);
        k.setFont(UITheme.font(Font.PLAIN, 13));
        k.setForeground(UITheme.TEXT_SECONDARY);
        r.add(k, BorderLayout.WEST);
        r.add(value, BorderLayout.EAST);
        return r;
    }

    // ------------------------------------------------------------------ behaviour
    private void onPlateEdited() {
        if (plateError != null) plateError.setText(" ");
        updateFastagDetection();
    }

    private void updateSelectionUI() {
        for (VehicleTypeCard card : typeCards) {
            card.setSelected(card.getType() == selectedVehicleType);
        }
        boolean isEv = (selectedVehicleType == VehicleType.EV_CAR || selectedVehicleType == VehicleType.EV_TWO_WHEELER || selectedVehicleType == VehicleType.ELECTRIC_CAR);
        evChargingCheck.setEnabled(isEv);
        evHint.setText(isEv ? " " : "Electric vehicles only");
        if (!isEv) {
            evChargingCheck.setSelected(false);
        }
    }

    private static String vehicleLabel(VehicleType t) {
        return switch (t) {
            case MOTORCYCLE, BIKE -> "Motorcycle (2-wheeler)";
            case AUTO_RICKSHAW -> "Auto rickshaw (3-wheeler)";
            case CAR -> "Car (4-wheeler)";
            case EV_CAR, ELECTRIC_CAR -> "Electric car";
            case EV_TWO_WHEELER -> "Electric two-wheeler";
            case LCV -> "Light commercial vehicle";
            case HCV, TRUCK -> "Heavy commercial vehicle";
        };
    }

    private void processEntry() {
        String plate = plateField.getText().trim();
        if (plate.isEmpty() || !PlateUtil.isValid(plate)) {
            plateError.setText(plate.isEmpty() ? "Enter the vehicle's licence plate." : "That doesn't look like a valid plate. Example: UP32 AB 1234");
            plateError.setIcon(VectorIconUtil.getIcon(IconType.ALERT, 13, UITheme.ERROR_TEXT));
            plateField.requestFocusInWindow();
            plateField.selectAll();
            return;
        }
        plateError.setIcon(null);

        // Apply selected Strategy Pattern
        int stratIndex = strategyBox.getSelectedIndex();
        switch (stratIndex) {
            case 1 -> parkingService.setAllocationStrategy(new BestFitAllocationStrategy());
            case 2 -> parkingService.setAllocationStrategy(new RandomAllocationStrategy());
            default -> parkingService.setAllocationStrategy(new NearestFirstAllocationStrategy());
        }

        boolean hasPermit = permitCheck.isSelected();
        boolean requestCharge = evChargingCheck.isSelected();

        // Instantiate vehicle through polymorphism
        Vehicle vehicle = switch (selectedVehicleType) {
            case MOTORCYCLE, BIKE -> new Motorcycle(plate);
            case AUTO_RICKSHAW -> new AutoRickshaw(plate);
            case EV_TWO_WHEELER -> new EvTwoWheeler(plate, "Black", "Ather", requestCharge);
            case EV_CAR, ELECTRIC_CAR -> new ElectricCar(plate, "Blue", "Tata Nexon EV", 75.0, 30.0, requestCharge, hasPermit);
            case LCV -> new Lcv(plate);
            case HCV, TRUCK -> new Hcv(plate);
            case CAR -> new Car(plate, "White", "Sedan", hasPermit);
        };
        if (hasPermit) {
            vehicle.setDivyangPermit(true);
        }

        btnPark.setEnabled(false);
        btnPark.setText("Allocating bay…");
        try {
            ParkingTicket ticket = parkingService.processVehicleEntry(vehicle);
            String stratName = switch (strategyBox.getSelectedIndex()) {
                case 1 -> "Best fit";
                case 2 -> "Distributed";
                default -> "Nearest available";
            };
            showTicket(ticket, stratName, hasPermit, requestCharge);

            // Prepare next entry
            plateField.setText(PlateUtil.generateRandomUp());
            updateFastagDetection();

            if (onStateChange != null) {
                onStateChange.run();
            }
            Toast.success(this, "Vehicle parked in bay " + ticket.getSlotId(),
                    PlateUtil.format(ticket.getLicensePlate()) + " · Level " + ticket.getFloorNumber() + " · " + ticket.getTicketNumber());
        } catch (IllegalStateException ex) {
            Toast.warning(this, "No bay allocated", ex.getMessage());
        } catch (Exception ex) {
            Toast.error(this, "Entry failed", ex.getMessage());
        } finally {
            btnPark.setEnabled(true);
            btnPark.setText("Park vehicle & issue ticket");
        }
    }

    private void showTicket(ParkingTicket ticket, String mode, boolean permit, boolean charging) {
        tSlot.setText("Bay " + ticket.getSlotId());
        tFloor.setText("Level " + ticket.getFloorNumber());
        tNumber.setText(ticket.getTicketNumber());
        String district = PlateUtil.district(ticket.getLicensePlate());
        tPlate.setText(PlateUtil.format(ticket.getLicensePlate()) + (district == null || district.isBlank() ? "" : "  ·  " + district));
        tType.setText(ticket.getVehicleType() != null ? vehicleLabel(ticket.getVehicleType()) : "—");
        tTime.setText(DateFormat.display(ticket.getEntryTime()));
        tMode.setText(mode);
        tFlags.removeAll();
        if (permit) {
            tFlags.add(new Badge("Divyang permit verified", UITheme.SLOT_ACCESSIBLE));
            tFlags.add(Box.createHorizontalStrut(8));
        }
        if (charging) tFlags.add(new Badge("EV charging started", UITheme.SLOT_EV));
        tFlags.setVisible(permit || charging);

        BufferedImage barcodeImg = BarcodeGenerator.generateBarcode(ticket.getTicketNumber(), 300, 48);
        ticketBarcodeLabel.setIcon(barcodeImg != null ? new ImageIcon(barcodeImg) : null);
        BufferedImage qrImg = BarcodeGenerator.generateQrCode(ticket.getTicketNumber(), 84);
        ticketQrLabel.setIcon(qrImg != null ? new ImageIcon(qrImg) : null);
        ticketCards.show(ticketHolder, "TICKET");
        ticketHolder.revalidate();
        ticketHolder.repaint();
    }

    private void updateFastagDetection() {
        if (plateField == null || fastagBadgeLabel == null) return;
        String raw = plateField.getText().trim();
        Optional<FastagAccount> accOpt = parkingService.getFastagService().detectTag(raw);
        if (accOpt.isPresent()) {
            FastagAccount acc = accOpt.get();
            fastagBadgeLabel.setText(String.format("FASTag detected (simulated) · %s · balance %s",
                    acc.getTagId(), MoneyFormat.format(acc.getBalance())));
            fastagBadgeLabel.setIcon(VectorIconUtil.getIcon(IconType.CHECK, 13, UITheme.SUCCESS_TEXT));
            fastagBadgeLabel.setForeground(UITheme.SUCCESS_TEXT);
            fastagBadgeLabel.setOpaque(true);
            fastagBadgeLabel.setBackground(UITheme.tint(UITheme.SUCCESS));
            fastagBadgeLabel.setBorder(BorderFactory.createCompoundBorder(
                    new DarkTheme.RoundBorder(UITheme.tintBorder(UITheme.SUCCESS), UITheme.RADIUS),
                    BorderFactory.createEmptyBorder(4, 8, 4, 10)));
        } else {
            fastagBadgeLabel.setText("No FASTag on this plate · a paper ticket will be issued");
            fastagBadgeLabel.setIcon(VectorIconUtil.getIcon(IconType.TICKET, 13, UITheme.TEXT_TERTIARY));
            fastagBadgeLabel.setForeground(UITheme.TEXT_TERTIARY);
            fastagBadgeLabel.setOpaque(false);
            fastagBadgeLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        }
        fastagBadgeLabel.setIconTextGap(8);
    }

    /**
     * Selectable vehicle-class tile: monochrome vector icon, title and subtitle.
     * Keyboard: Tab to focus, Space/Enter to select.
     */
    private class VehicleTypeCard extends JPanel {
        private final VehicleType type;
        private final JLabel iconLabel;
        private boolean isSelected = false;
        private boolean isHovered = false;

        public VehicleTypeCard(String title, String subtitle, VehicleType type) {
            this.type = type;
            setLayout(new BorderLayout(0, 6));
            setOpaque(false);
            setFocusable(true);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(12, 10, 10, 10));
            setToolTipText(title + " (" + subtitle + ")");
            getAccessibleContext().setAccessibleName("Vehicle type: " + title);

            iconLabel = new JLabel(VectorIconUtil.getVehicleIcon(type, 26, UITheme.TEXT_SECONDARY), SwingConstants.CENTER);
            add(iconLabel, BorderLayout.CENTER);

            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);
            JLabel tLbl = new JLabel(title);
            tLbl.setFont(UITheme.font(Font.BOLD, 12));
            tLbl.setForeground(UITheme.TEXT_PRIMARY);
            tLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            JLabel sLbl = new JLabel(subtitle);
            sLbl.setFont(UITheme.font(Font.PLAIN, 11));
            sLbl.setForeground(UITheme.TEXT_TERTIARY);
            sLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            textPanel.add(tLbl);
            textPanel.add(sLbl);
            add(textPanel, BorderLayout.SOUTH);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    choose();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    isHovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                }
            });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "choose");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "choose");
            getActionMap().put("choose", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    choose();
                }
            });
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusGained(java.awt.event.FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    repaint();
                }
            });
        }

        private void choose() {
            selectedVehicleType = type;
            updateSelectionUI();
        }

        public VehicleType getType() {
            return type;
        }

        public void setSelected(boolean sel) {
            this.isSelected = sel;
            iconLabel.setIcon(VectorIconUtil.getVehicleIcon(type, 26, sel ? UITheme.ACCENT_TEXT : UITheme.TEXT_SECONDARY));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = UITheme.RADIUS_LG;
            g2.setColor(isSelected ? UITheme.ACCENT_SOFT : isHovered ? UITheme.BG_HOVER : UITheme.BG_ELEVATED);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(isSelected ? UITheme.ACCENT : isHovered ? UITheme.BORDER_STRONG : UITheme.BORDER_SUBTLE);
            g2.setStroke(new BasicStroke(isSelected ? 1.6f : 1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            if (isFocusOwner() && DarkTheme.isFocusVisible()) {
                g2.setColor(UITheme.ACCENT_TEXT);
                g2.drawRoundRect(2, 2, w - 5, h - 5, arc - 2, arc - 2);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
