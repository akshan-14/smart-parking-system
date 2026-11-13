package com.smartparking.ui.panels;

import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.fastag.FastagAccount;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.vehicle.*;
import com.smartparking.patterns.strategy.allocation.BestFitAllocationStrategy;
import com.smartparking.patterns.strategy.allocation.NearestFirstAllocationStrategy;
import com.smartparking.patterns.strategy.allocation.RandomAllocationStrategy;
import com.smartparking.service.ParkingService;
import com.smartparking.ui.components.ModernButton;
import com.smartparking.ui.components.RoundedPanel;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.ImageLoader;
import com.smartparking.util.BarcodeGenerator;
import com.smartparking.util.DateFormat;
import com.smartparking.util.MoneyFormat;
import com.smartparking.util.PlateUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gate 1 Vehicle Entry Panel.
 * Features high-resolution circular vehicle badge selection tiles (Bike, Car, Electric, Truck),
 * real-time ANPR plate generator, accessibility & EV charging toggles, and live ticket dispensary.
 */
public class VehicleEntryPanel extends JPanel {

    private final ParkingService parkingService;
    private final Runnable onStateChange;

    private VehicleType selectedVehicleType = VehicleType.CAR;
    private JTextField plateField;
    private JLabel fastagBadgeLabel;
    private JCheckBox permitCheck;
    private JCheckBox evChargingCheck;
    private JComboBox<String> strategyBox;
    private JTextArea ticketPreviewArea;
    private JLabel ticketVehicleBadge;
    private JLabel ticketVehicleDesc;
    private JLabel ticketBarcodeLabel;
    private JLabel ticketQrLabel;

    // Type selection cards for 7 classes
    private final List<VehicleTypeCard> typeCards = new ArrayList<>();

    public VehicleEntryPanel(ParkingService parkingService, Runnable onStateChange) {
        this.parkingService = parkingService;
        this.onStateChange = onStateChange;

        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_CANVAS);
        setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        initUI();
    }

    private void initUI() {
        // --- TOP: Header Title ---
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("GATE 1: VEHICLE ARRIVAL & TICKET DISPENSARY", ImageLoader.getNavIcon("entry", 24), SwingConstants.LEFT);
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.TEXT_DARK);
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        // --- CENTER: Split Layout (Entry Form on Left, Ticket Display on Right) ---
        JPanel centerSplit = new JPanel(new GridLayout(1, 2, 20, 0));
        centerSplit.setOpaque(false);

        // LEFT: Entry Configuration Card
        RoundedPanel formCard = new RoundedPanel(16, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        formCard.setLayout(new BoxLayout(formCard, BoxLayout.Y_AXIS));
        formCard.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));

        JLabel step1 = new JLabel("1. Select Vehicle Classification:");
        step1.setFont(UITheme.FONT_SUBTITLE);
        step1.setForeground(UITheme.TEXT_DARK);
        formCard.add(step1);
        formCard.add(Box.createVerticalStrut(10));

        // High-Resolution Vehicle Selection Grid (2 rows x 4 columns)
        JPanel typeGrid = new JPanel(new GridLayout(2, 4, 8, 8));
        typeGrid.setOpaque(false);
        typeGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        typeGrid.setPreferredSize(new Dimension(500, 160));

        typeCards.clear();
        typeCards.add(new VehicleTypeCard("Motorcycle", "Two-Wheeler", VehicleType.MOTORCYCLE, new Color(139, 92, 246)));
        typeCards.add(new VehicleTypeCard("Auto Rickshaw", "Three-Wheeler", VehicleType.AUTO_RICKSHAW, new Color(217, 119, 6)));
        typeCards.add(new VehicleTypeCard("Standard Car", "Four-Wheeler", VehicleType.CAR, new Color(16, 185, 129)));
        typeCards.add(new VehicleTypeCard("EV Car", "Electric 4W", VehicleType.EV_CAR, UITheme.PRIMARY_BLUE));
        typeCards.add(new VehicleTypeCard("EV Bike", "Electric 2W", VehicleType.EV_TWO_WHEELER, new Color(6, 182, 212)));
        typeCards.add(new VehicleTypeCard("LCV Mini-Truck", "Light Comm.", VehicleType.LCV, new Color(234, 88, 12)));
        typeCards.add(new VehicleTypeCard("HCV Truck/Bus", "Heavy Comm.", VehicleType.HCV, new Color(180, 83, 9)));

        for (VehicleTypeCard c : typeCards) {
            typeGrid.add(c);
        }
        // Add empty placeholder for 8th cell to keep layout balanced
        JPanel emptyCell = new JPanel();
        emptyCell.setOpaque(false);
        typeGrid.add(emptyCell);

        formCard.add(typeGrid);
        formCard.add(Box.createVerticalStrut(14));

        // License Plate Input
        JLabel step2 = new JLabel("2. License Plate Number (ANPR Read - Simulated):");
        step2.setFont(UITheme.FONT_SUBTITLE);
        step2.setForeground(UITheme.TEXT_DARK);
        formCard.add(step2);
        formCard.add(Box.createVerticalStrut(6));

        JPanel plateRow = new JPanel(new BorderLayout(8, 0));
        plateRow.setOpaque(false);
        plateRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        plateField = new JTextField(PlateUtil.generateRandomUp());
        UITheme.styleTextField(plateField);
        plateField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateFastagDetection(); }
            public void removeUpdate(DocumentEvent e) { updateFastagDetection(); }
            public void changedUpdate(DocumentEvent e) { updateFastagDetection(); }
        });
        plateRow.add(plateField, BorderLayout.CENTER);

        JButton btnRegen = new JButton("New Plate", com.smartparking.ui.util.VectorIconUtil.getIcon(com.smartparking.ui.util.VectorIconUtil.IconType.DICE, 14, UITheme.TEXT_DARK));
        btnRegen.setFont(UITheme.FONT_SMALL);
        btnRegen.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnRegen.addActionListener(e -> {
            plateField.setText(PlateUtil.generateRandomUp());
            updateFastagDetection();
        });
        plateRow.add(btnRegen, BorderLayout.EAST);

        formCard.add(plateRow);
        formCard.add(Box.createVerticalStrut(4));

        fastagBadgeLabel = new JLabel("Standard Paper / Barcode Ticket Required (No FASTag)");
        fastagBadgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        fastagBadgeLabel.setForeground(new Color(100, 116, 139));
        fastagBadgeLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        formCard.add(fastagBadgeLabel);
        formCard.add(Box.createVerticalStrut(10));

        // Options Checkboxes with crisp vector icons (No broken font glyphs)
        JPanel permitRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        permitRow.setOpaque(false);
        permitRow.add(new JLabel(com.smartparking.ui.util.VectorIconUtil.getIcon(com.smartparking.ui.util.VectorIconUtil.IconType.WHEELCHAIR, 16, UITheme.PRIMARY_BLUE)));
        permitCheck = new JCheckBox("Persons with Disabilities (Divyang) Parking Permit");
        permitCheck.setFont(UITheme.FONT_REGULAR);
        permitCheck.setOpaque(false);
        permitCheck.setForeground(UITheme.TEXT_DARK);
        permitRow.add(permitCheck);
        formCard.add(permitRow);
        formCard.add(Box.createVerticalStrut(6));

        JPanel evRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        evRow.setOpaque(false);
        evRow.add(new JLabel(com.smartparking.ui.util.VectorIconUtil.getIcon(com.smartparking.ui.util.VectorIconUtil.IconType.PLUG, 16, new Color(16, 185, 129))));
        evChargingCheck = new JCheckBox("Request Active EV Fast-Charging Station");
        evChargingCheck.setFont(UITheme.FONT_REGULAR);
        evChargingCheck.setOpaque(false);
        evChargingCheck.setForeground(UITheme.TEXT_DARK);
        evChargingCheck.setEnabled(false); // enabled only when EV is active
        evRow.add(evChargingCheck);
        formCard.add(evRow);
        formCard.add(Box.createVerticalStrut(12));

        // Allocation Mode Dropdown
        JLabel step3 = new JLabel("3. Bay Allocation Mode:");
        step3.setFont(UITheme.FONT_SUBTITLE);
        step3.setForeground(UITheme.TEXT_DARK);
        formCard.add(step3);
        formCard.add(Box.createVerticalStrut(6));

        String[] strategies = {
                "Nearest Available Bay (Ground Floor First)",
                "Best Fit (Optimal Vehicle Sizing)",
                "Automated Distributed Assignment"
        };
        strategyBox = new JComboBox<>(strategies);
        strategyBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        UITheme.styleComboBox(strategyBox);
        formCard.add(strategyBox);
        formCard.add(Box.createVerticalStrut(20));

        // Park Action Button
        ModernButton btnPark = new ModernButton("PARK VEHICLE & ISSUE TICKET", ImageLoader.getNavIcon("entry", 22));
        btnPark.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btnPark.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        btnPark.addActionListener(e -> processEntry());
        formCard.add(btnPark);

        centerSplit.add(formCard);

        // RIGHT: Real-Time Ticket Preview Card
        RoundedPanel previewCard = new RoundedPanel(16, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        previewCard.setLayout(new BorderLayout(0, 12));
        previewCard.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));

        // Header with live vehicle badge
        JPanel previewHeader = new JPanel(new BorderLayout());
        previewHeader.setOpaque(false);

        JPanel leftTitle = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftTitle.setOpaque(false);
        JLabel pt = new JLabel("ISSUED PARKING TICKET", ImageLoader.loadIcon("images/logo_nav.png", 22, 22), SwingConstants.LEFT);
        pt.setFont(UITheme.FONT_SUBTITLE);
        pt.setForeground(UITheme.TEXT_DARK);
        leftTitle.add(pt);
        previewHeader.add(leftTitle, BorderLayout.WEST);

        // Vehicle badge thumbnail
        JPanel badgeThumb = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        badgeThumb.setOpaque(false);
        ticketVehicleBadge = new JLabel(ImageLoader.getVehicleIcon(VehicleType.CAR, 48, 48));
        ticketVehicleDesc = new JLabel("Standard Car");
        ticketVehicleDesc.setFont(new Font("Segoe UI", Font.BOLD, 12));
        ticketVehicleDesc.setForeground(new Color(16, 185, 129));
        badgeThumb.add(ticketVehicleBadge);
        badgeThumb.add(ticketVehicleDesc);
        previewHeader.add(badgeThumb, BorderLayout.EAST);

        previewCard.add(previewHeader, BorderLayout.NORTH);

        ticketPreviewArea = new JTextArea();
        ticketPreviewArea.setFont(UITheme.FONT_MONO);
        ticketPreviewArea.setEditable(false);
        ticketPreviewArea.setBackground(new Color(248, 250, 252));
        ticketPreviewArea.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        ticketPreviewArea.setText("\n  [GATE STATUS: READY]\n\n  Select vehicle type, verify license plate, and click\n  'PARK VEHICLE & ISSUE TICKET' to dispense ticket.");

        previewCard.add(new JScrollPane(ticketPreviewArea), BorderLayout.CENTER);

        // ZXing Code128 Barcode & QR Code container
        JPanel barcodeContainer = new JPanel(new BorderLayout(10, 0));
        barcodeContainer.setOpaque(false);
        barcodeContainer.setBorder(BorderFactory.createEmptyBorder(8, 6, 4, 6));

        ticketBarcodeLabel = new JLabel();
        ticketBarcodeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        ticketQrLabel = new JLabel();
        ticketQrLabel.setHorizontalAlignment(SwingConstants.CENTER);

        barcodeContainer.add(ticketBarcodeLabel, BorderLayout.CENTER);
        barcodeContainer.add(ticketQrLabel, BorderLayout.EAST);
        previewCard.add(barcodeContainer, BorderLayout.SOUTH);

        centerSplit.add(previewCard);

        add(centerSplit, BorderLayout.CENTER);

        updateSelectionUI();
        updateFastagDetection();
    }

    private void updateSelectionUI() {
        for (VehicleTypeCard card : typeCards) {
            card.setSelected(card.getType() == selectedVehicleType);
        }

        ticketVehicleBadge.setIcon(ImageLoader.getVehicleIcon(selectedVehicleType, 48, 48));
        ticketVehicleDesc.setText(switch (selectedVehicleType) {
            case MOTORCYCLE, BIKE -> "Motorcycle (2-Wheeler)";
            case AUTO_RICKSHAW -> "Auto Rickshaw (3-Wheeler)";
            case CAR -> "Standard Car (4-Wheeler)";
            case EV_CAR, ELECTRIC_CAR -> "Electric Car (EV 4-Wheeler)";
            case EV_TWO_WHEELER -> "Electric Two-Wheeler (EV Bike)";
            case LCV -> "Light Commercial Vehicle (LCV)";
            case HCV, TRUCK -> "Heavy Commercial Vehicle (HCV)";
        });
        ticketVehicleDesc.setForeground(switch (selectedVehicleType) {
            case MOTORCYCLE, BIKE -> new Color(139, 92, 246);
            case AUTO_RICKSHAW -> new Color(217, 119, 6);
            case CAR -> new Color(16, 185, 129);
            case EV_CAR, ELECTRIC_CAR -> UITheme.PRIMARY_BLUE;
            case EV_TWO_WHEELER -> new Color(6, 182, 212);
            case LCV -> new Color(234, 88, 12);
            case HCV, TRUCK -> new Color(180, 83, 9);
        });

        boolean isEv = (selectedVehicleType == VehicleType.EV_CAR || selectedVehicleType == VehicleType.EV_TWO_WHEELER || selectedVehicleType == VehicleType.ELECTRIC_CAR);
        evChargingCheck.setEnabled(isEv);
        if (!isEv) {
            evChargingCheck.setSelected(false);
        }
    }

    private void processEntry() {
        String plate = plateField.getText().trim();
        if (plate.isEmpty() || !PlateUtil.isValid(plate)) {
            JOptionPane.showMessageDialog(this, "Enter a valid UP plate, e.g. UP32 AB 1234", "Input Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

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

        try {
            ParkingTicket ticket = parkingService.processVehicleEntry(vehicle);

            StringBuilder sb = new StringBuilder();
            sb.append("====================================================\n");
            sb.append("          GRAND TECH PARK SMART GARAGE              \n");
            sb.append("               OFFICIAL PARKING PASS                \n");
            sb.append("====================================================\n");
            sb.append(String.format("TICKET NUMBER : %s\n", ticket.getTicketNumber()));
            sb.append(String.format("LICENSE PLATE : %s  [%s]\n", PlateUtil.format(ticket.getLicensePlate()), PlateUtil.district(ticket.getLicensePlate())));
            sb.append(String.format("VEHICLE TYPE  : %s\n", ticket.getVehicleType()));
            sb.append(String.format("ASSIGNED SLOT : %s  (Floor %d)\n", ticket.getSlotId(), ticket.getFloorNumber()));
            sb.append(String.format("ENTRY TIME    : %s\n", DateFormat.display(ticket.getEntryTime())));
            String stratName = switch (strategyBox.getSelectedIndex()) {
                case 1 -> "Best Fit Allocation";
                case 2 -> "Distributed Automated Allocation";
                default -> "Nearest Available Bay (Ground Floor First)";
            };
            sb.append(String.format("ALLOCATION MODE: %s\n", stratName));
            if (hasPermit) sb.append("ACCESSIBILITY : [DIVYANG PERMIT VERIFIED]\n");
            if (requestCharge) sb.append("EV CHARGER    : [ACTIVE CHARGING SESSION STARTED]\n");
            sb.append("----------------------------------------------------\n");
            sb.append("BARCODE: ||| ||||| |||| |||| ||| ||||||| ||||\n");
            sb.append("====================================================\n");
            sb.append("      Please keep this ticket for checkout!         \n");
            sb.append("====================================================\n");

            ticketPreviewArea.setText(sb.toString());

            // Render real ZXing Code128 barcode and QR code
            BufferedImage barcodeImg = BarcodeGenerator.generateBarcode(ticket.getTicketNumber(), 240, 42);
            if (barcodeImg != null) ticketBarcodeLabel.setIcon(new ImageIcon(barcodeImg));
            BufferedImage qrImg = BarcodeGenerator.generateQrCode(ticket.getTicketNumber(), 75);
            if (qrImg != null) ticketQrLabel.setIcon(new ImageIcon(qrImg));

            // Prepare next entry
            plateField.setText(PlateUtil.generateRandomUp());
            updateFastagDetection();

            if (onStateChange != null) {
                onStateChange.run();
            }

            JOptionPane.showMessageDialog(this,
                    "Vehicle parked successfully in Slot " + ticket.getSlotId() + " (Floor " + ticket.getFloorNumber() + ")!",
                    "Entry Gate Success", JOptionPane.INFORMATION_MESSAGE);

        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Slot Allocation Notice", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Entry Gate Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateFastagDetection() {
        if (plateField == null || fastagBadgeLabel == null) return;
        String raw = plateField.getText().trim();
        Optional<FastagAccount> accOpt = parkingService.getFastagService().detectTag(raw);
        if (accOpt.isPresent()) {
            FastagAccount acc = accOpt.get();
            fastagBadgeLabel.setText(String.format("\uD83D\uDCF6 FASTag Detected (Simulated): %s (Balance: %s)",
                    acc.getTagId(), MoneyFormat.format(acc.getBalance())));
            fastagBadgeLabel.setForeground(new Color(22, 101, 52));
            fastagBadgeLabel.setBackground(new Color(220, 252, 231));
            fastagBadgeLabel.setOpaque(true);
            fastagBadgeLabel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(134, 239, 172), 1, true),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)
            ));
        } else {
            fastagBadgeLabel.setText("Standard Paper / Barcode Ticket Required (No FASTag)");
            fastagBadgeLabel.setForeground(new Color(100, 116, 139));
            fastagBadgeLabel.setOpaque(false);
            fastagBadgeLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        }
    }

    /**
     * Interactive card representing a vehicle classification with custom badge icon,
     * label, subtitle, hover animation, and active state highlight.
     */
    private class VehicleTypeCard extends JPanel {
        private final String title;
        private final String subtitle;
        private final VehicleType type;
        private final Color accentColor;
        private boolean isSelected = false;
        private boolean isHovered = false;

        public VehicleTypeCard(String title, String subtitle, VehicleType type, Color accentColor) {
            this.title = title;
            this.subtitle = subtitle;
            this.type = type;
            this.accentColor = accentColor;

            setLayout(new BorderLayout(0, 4));
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));

            // Icon (52x52) with vector fallback
            Icon vIcon = ImageLoader.getVehicleIcon(type, 52, 52);
            if (vIcon == null) {
                vIcon = com.smartparking.ui.util.VectorIconUtil.getVehicleIcon(type, 44, accentColor);
            }
            JLabel iconLabel = new JLabel(vIcon, SwingConstants.CENTER);
            add(iconLabel, BorderLayout.CENTER);

            // Labels
            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);

            JLabel tLbl = new JLabel(title);
            tLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
            tLbl.setForeground(new Color(30, 41, 59));
            tLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel sLbl = new JLabel(subtitle);
            sLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            sLbl.setForeground(new Color(100, 116, 139));
            sLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            textPanel.add(tLbl);
            textPanel.add(sLbl);
            add(textPanel, BorderLayout.SOUTH);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    selectedVehicleType = type;
                    updateSelectionUI();
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
        }

        public VehicleType getType() {
            return type;
        }

        public void setSelected(boolean sel) {
            this.isSelected = sel;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 12;

            if (isSelected) {
                // Soft accent pastel background
                Color bgTint = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 28);
                g2.setColor(bgTint);
                g2.fillRoundRect(1, 1, w - 2, h - 2, arc, arc);

                // Bold colored border
                g2.setColor(accentColor);
                g2.setStroke(new BasicStroke(2.2f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
            } else if (isHovered) {
                g2.setColor(new Color(248, 250, 252));
                g2.fillRoundRect(1, 1, w - 2, h - 2, arc, arc);

                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
            } else {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(1, 1, w - 2, h - 2, arc, arc);

                g2.setColor(new Color(226, 232, 240));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
