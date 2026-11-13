package com.smartparking.ui.panels;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.repository.ParkingSlotDao;
import com.smartparking.service.ParkingService;
import com.smartparking.ui.components.ModernButton;
import com.smartparking.ui.components.RoundedPanel;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.ImageLoader;
import com.smartparking.util.PlateUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Floor Visual Map & Architectural Blueprint Panel.
 * Implements the CAD Blueprint layout with Scale 1:120 Structural CAD Grid,
 * dimensional coordinate ticks (1, 2, 3, 5, 6 / A, B, C, D, E),
 * interactive bay stalls (Mint Vacant, Crimson Active, Iris VIP/ADA, RSVP),
 * Inbound Roadway with Gate 1 In & crosswalk, Substation & HVAC enclosure with hatch pattern,
 * Outbound Roadway to Gate 2 Exit,
 * and live Bay Stall Inspector with embossed license plate, optical CCTV camera stream,
 * telemetry details, and environmental sensors.
 */
public class FloorMapPanel extends JPanel {

    private final ParkingLot parkingLot;
    private final ParkingService parkingService;
    private final ParkingSlotDao slotDao;
    private final com.smartparking.repository.TicketDao ticketDao;
    private final com.smartparking.service.TariffService tariffService;
    private final Consumer<String> navigationHandler;

    private int activeFloorNumber = 1;
    private String activeFilter = "ALL";

    // Header & Telemetry Labels
    private JLabel bimSubtitle;
    private JLabel vipAdaBadge;
    private JLabel fullPctBadge;
    private JButton btnL1;
    private JButton btnL2;
    private JButton btnL3;
    private JButton btnFilterAll;
    private JButton btnFilterStd;
    private JButton btnFilterEv;
    private JButton btnFilterAcc;
    private JLabel zoomPercentLabel;

    // Sub-components
    private CADBlueprintCanvas cadCanvas;
    private BayInspectorPanel inspectorPanel;

    public FloorMapPanel(ParkingLot parkingLot, ParkingService parkingService, Consumer<String> navigationHandler) {
        this.parkingLot = parkingLot;
        this.parkingService = parkingService;
        this.slotDao = new ParkingSlotDao();
        this.ticketDao = new com.smartparking.repository.TicketDao();
        this.tariffService = new com.smartparking.service.TariffService();
        this.navigationHandler = navigationHandler;

        setLayout(new BorderLayout(0, 8));
        setBackground(new Color(241, 245, 249)); // slate-100
        setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        initUI();
        renderFloorGrid();
    }

    private void initUI() {
        // --- TOP SECTION: MainHeader + FilterLegendBar ---
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setOpaque(false);

        topContainer.add(buildMainHeader());
        topContainer.add(Box.createVerticalStrut(6));
        topContainer.add(buildFilterLegendBar());
        topContainer.add(Box.createVerticalStrut(6));

        add(topContainer, BorderLayout.NORTH);

        // --- MAIN CONTENT: Left CAD Blueprint + Right Inspector ---
        JPanel workspace = new JPanel(new BorderLayout(10, 0));
        workspace.setOpaque(false);

        // Left Blueprint Section
        cadCanvas = new CADBlueprintCanvas();
        JScrollPane canvasScroll = new JScrollPane(cadCanvas);
        canvasScroll.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 1, true));
        canvasScroll.setOpaque(false);
        canvasScroll.getViewport().setOpaque(false);
        canvasScroll.getVerticalScrollBar().setUnitIncrement(16);
        canvasScroll.getHorizontalScrollBar().setUnitIncrement(16);

        JPanel leftBlueprintWrapper = new JPanel(new BorderLayout());
        leftBlueprintWrapper.setBackground(Color.WHITE);
        leftBlueprintWrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        // Horizontal Axis Ticks at the top of blueprint
        leftBlueprintWrapper.add(new HorizontalDimensionAxis(), BorderLayout.NORTH);
        leftBlueprintWrapper.add(canvasScroll, BorderLayout.CENTER);
        leftBlueprintWrapper.add(buildCanvasFooter(), BorderLayout.SOUTH);

        workspace.add(leftBlueprintWrapper, BorderLayout.CENTER);

        // Right Inspector Column
        inspectorPanel = new BayInspectorPanel();
        inspectorPanel.setPreferredSize(new Dimension(340, 0));
        inspectorPanel.setMinimumSize(new Dimension(320, 0));
        workspace.add(inspectorPanel, BorderLayout.EAST);

        add(workspace, BorderLayout.CENTER);
    }

    /**
     * Top Navigation Bar: Title, BIM Subtitle, Floor Switcher, Badges, Sync ANPR button.
     */
    private JPanel buildMainHeader() {
        RoundedPanel header = new RoundedPanel(8, Color.WHITE, new Color(226, 232, 240));
        header.setLayout(new BorderLayout(14, 0));
        header.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));

        // Left: Icon + Title + Subtitle
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        JLabel iconBox = new JLabel("", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(239, 246, 255)); // blue-50
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);

                // Draw crisp 2x2 grid symbol
                g2.setColor(new Color(37, 99, 235)); // blue-600
                int s = 5;
                int gap = 2;
                int sx = (getWidth() - (2 * s + gap)) / 2;
                int sy = (getHeight() - (2 * s + gap)) / 2;
                g2.fillRoundRect(sx, sy, s, s, 1, 1);
                g2.fillRoundRect(sx + s + gap, sy, s, s, 1, 1);
                g2.fillRoundRect(sx, sy + s + gap, s, s, 1, 1);
                g2.fillRoundRect(sx + s + gap, sy + s + gap, s, s, 1, 1);
                g2.dispose();
            }
        };
        iconBox.setPreferredSize(new Dimension(30, 30));
        iconBox.setFont(new Font("Segoe UI", Font.BOLD, 15));
        iconBox.setForeground(new Color(37, 99, 235)); // blue-600
        left.add(iconBox);

        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);

        JLabel title = new JLabel("Floor Visual Map & Architectural Blueprint");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(new Color(15, 23, 42)); // slate-900

        bimSubtitle = new JLabel("BIM Layer: Ground Floor (Level 0) • Scale 1:120 Structural CAD Grid • Live Synchronized");
        bimSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        bimSubtitle.setForeground(new Color(100, 116, 139)); // slate-500

        titles.add(title);
        titles.add(Box.createVerticalStrut(1));
        titles.add(bimSubtitle);
        left.add(titles);
        header.add(left, BorderLayout.WEST);

        // Right: Floor Switcher Tabs + Capacity Badges + Sync ANPR Button
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        // Floor Switch Tabs
        JPanel floorTabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        floorTabs.setBackground(new Color(241, 245, 249)); // slate-100
        floorTabs.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1, true),
                BorderFactory.createEmptyBorder(1, 2, 1, 2)
        ));

        btnL1 = createFloorTabButton("L1 (Ground)", 1);
        btnL2 = createFloorTabButton("L2 (EV Hub)", 2);
        btnL3 = createFloorTabButton("L3 (Fleet & VIP)", 3);

        floorTabs.add(btnL1);
        floorTabs.add(btnL2);
        floorTabs.add(btnL3);
        right.add(floorTabs);

        // Badges: 1 VIP/ADA and 80.0% Full
        vipAdaBadge = createBadgeLabel("1 VIP/ADA");
        fullPctBadge = createBadgeLabel("80.0% Full");
        right.add(vipAdaBadge);
        right.add(fullPctBadge);

        // Sync Action Button
        ModernButton btnSync = new ModernButton("Sync ANPR (Simulated)", null,
                new Color(37, 99, 235), new Color(29, 78, 216), Color.WHITE);
        btnSync.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnSync.setPreferredSize(new Dimension(150, 28));
        btnSync.addActionListener(e -> {
            renderFloorGrid();
            JOptionPane.showMessageDialog(FloorMapPanel.this,
                    "ANPR Cameras and SQLite slot states synchronized successfully (Simulated).",
                    "Live BIM Synchronized", JOptionPane.INFORMATION_MESSAGE);
        });
        right.add(btnSync);

        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JLabel createBadgeLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Consolas", Font.BOLD, 11));
        lbl.setForeground(new Color(51, 65, 85)); // slate-700
        lbl.setBackground(new Color(241, 245, 249)); // slate-100
        lbl.setOpaque(true);
        lbl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1, true),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)
        ));
        return lbl;
    }

    private JButton createFloorTabButton(String text, int floorNum) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        btn.addActionListener(e -> {
            activeFloorNumber = floorNum;
            updateFloorTabStyles();
            renderFloorGrid();
        });
        return btn;
    }

    private void updateFloorTabStyles() {
        styleTabButton(btnL1, activeFloorNumber == 1);
        styleTabButton(btnL2, activeFloorNumber == 2);
        styleTabButton(btnL3, activeFloorNumber == 3);

        String subtitleText = switch (activeFloorNumber) {
            case 2 -> "BIM Layer: Level 2 (EV Hub) • Scale 1:120 Structural CAD Grid • Live Synchronized";
            case 3 -> "BIM Layer: Level 3 (Fleet & VIP Roof) • Scale 1:120 Structural CAD Grid • Live Synchronized";
            default -> "BIM Layer: Ground Floor (Level 0) • Scale 1:120 Structural CAD Grid • Live Synchronized";
        };
        bimSubtitle.setText(subtitleText);
    }

    private void styleTabButton(JButton btn, boolean isSelected) {
        if (isSelected) {
            btn.setBackground(Color.WHITE);
            btn.setForeground(new Color(15, 23, 42));
            btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                    BorderFactory.createEmptyBorder(3, 9, 3, 9)
            ));
        } else {
            btn.setBackground(new Color(241, 245, 249));
            btn.setForeground(new Color(100, 116, 139));
            btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            btn.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        }
    }

    /**
     * Filter & Legend Bar: Bay Filters (All, Standard, EV, Accessible) and Status Legend.
     */
    private JPanel buildFilterLegendBar() {
        RoundedPanel bar = new RoundedPanel(8, Color.WHITE, new Color(226, 232, 240));
        bar.setLayout(new BorderLayout(14, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));

        // Left Filter Pills
        JPanel filterBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filterBox.setOpaque(false);

        JLabel fLbl = new JLabel("BAY FILTER:");
        fLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        fLbl.setForeground(new Color(100, 116, 139)); // slate-500
        filterBox.add(fLbl);

        btnFilterAll = createFilterPill("All (64)", "ALL", true);
        btnFilterStd = createFilterPill("Standard (11)", "STANDARD", false);
        btnFilterEv = createFilterPill("EV Bays (2)", "EV", false);
        btnFilterAcc = createFilterPill("Accessible (2)", "ACCESSIBLE", false);

        filterBox.add(btnFilterAll);
        filterBox.add(btnFilterStd);
        filterBox.add(btnFilterEv);
        filterBox.add(btnFilterAcc);
        bar.add(filterBox, BorderLayout.WEST);

        // Right Status Legend & Interactive Zoom Controls
        JPanel legendBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        legendBox.setOpaque(false);

        legendBox.add(createLegendDot(new Color(52, 211, 153), "Mint = Vacant"));
        legendBox.add(createLegendDot(new Color(239, 68, 68), "Crimson = Active"));
        legendBox.add(createLegendDot(new Color(99, 102, 241), "Iris = VIP/ADA"));

        // Zoom Toolbar: [-] [100%] [+] [Fit] [Reset]
        JPanel zoomBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        zoomBox.setOpaque(false);
        zoomBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(0, 8, 0, 0)
        ));

        JButton btnZoomOut = createZoomButton("-", e -> {
            if (cadCanvas != null) cadCanvas.zoom(-0.15f);
        });
        zoomPercentLabel = new JLabel("100%");
        zoomPercentLabel.setFont(new Font("Consolas", Font.BOLD, 11));
        zoomPercentLabel.setForeground(new Color(71, 85, 105));
        zoomPercentLabel.setPreferredSize(new Dimension(38, 22));
        zoomPercentLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JButton btnZoomIn = createZoomButton("+", e -> {
            if (cadCanvas != null) cadCanvas.zoom(0.15f);
        });
        JButton btnZoomReset = createZoomButton("Reset", e -> {
            if (cadCanvas != null) cadCanvas.resetZoom();
        });

        zoomBox.add(btnZoomOut);
        zoomBox.add(zoomPercentLabel);
        zoomBox.add(btnZoomIn);
        zoomBox.add(btnZoomReset);
        legendBox.add(zoomBox);

        bar.add(legendBox, BorderLayout.EAST);
        return bar;
    }

    private JButton createZoomButton(String label, java.awt.event.ActionListener al) {
        JButton btn = new JButton(label);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        btn.setForeground(new Color(51, 65, 85));
        btn.setBackground(new Color(241, 245, 249));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(al);
        return btn;
    }

    private JButton createFilterPill(String label, String key, boolean defaultActive) {
        JButton btn = new JButton(label) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean isActive = key.equals(activeFilter);
                if (isActive) {
                    g2.setColor(new Color(37, 99, 235)); // #2563eb royal blue
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(226, 232, 240)); // #e2e8f0 hover
                } else {
                    g2.setColor(new Color(241, 245, 249)); // #f1f5f9
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        styleFilterPill(btn, defaultActive);

        btn.addActionListener(e -> {
            activeFilter = key;
            styleFilterPill(btnFilterAll, "ALL".equals(key));
            styleFilterPill(btnFilterStd, "STANDARD".equals(key));
            styleFilterPill(btnFilterEv, "EV".equals(key));
            styleFilterPill(btnFilterAcc, "ACCESSIBLE".equals(key));
            if (btnFilterAll != null) btnFilterAll.repaint();
            if (btnFilterStd != null) btnFilterStd.repaint();
            if (btnFilterEv != null) btnFilterEv.repaint();
            if (btnFilterAcc != null) btnFilterAcc.repaint();
            cadCanvas.repaint();
        });
        return btn;
    }

    private void styleFilterPill(JButton btn, boolean isActive) {
        if (btn == null) return;
        btn.setForeground(isActive ? Color.WHITE : new Color(71, 85, 105));
    }

    private JPanel createLegendDot(Color color, String text) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        p.setOpaque(false);

        JLabel dot = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(0, (getHeight() - 8) / 2, 8, 8);
                g2.dispose();
            }
        };
        dot.setPreferredSize(new Dimension(8, 14));

        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbl.setForeground(new Color(71, 85, 105)); // slate-600

        p.add(dot);
        p.add(lbl);
        return p;
    }

    /**
     * Canvas footer with Mesh Signal, ANPR Cameras status, and Quick Floor Switch.
     */
    private JPanel buildCanvasFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(6, 4, 2, 4)
        ));

        JPanel leftSensors = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        leftSensors.setOpaque(false);

        JLabel rssiLbl = new JLabel("\u25CF Mesh Signal: 99.8% RSSI");
        rssiLbl.setFont(new Font("Consolas", Font.BOLD, 11));
        rssiLbl.setForeground(new Color(16, 185, 129)); // emerald-500

        JLabel anprLbl = new JLabel("\u25CF ANPR Cameras: 4/4 Connected");
        anprLbl.setFont(new Font("Consolas", Font.BOLD, 11));
        anprLbl.setForeground(new Color(37, 99, 235)); // blue-600

        leftSensors.add(rssiLbl);
        leftSensors.add(anprLbl);
        footer.add(leftSensors, BorderLayout.WEST);

        JPanel jumpBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        jumpBox.setOpaque(false);

        JLabel switchLbl = new JLabel("Switch Floor:");
        switchLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        switchLbl.setForeground(new Color(100, 116, 139));
        jumpBox.add(switchLbl);

        JButton jumpL2 = createFloorNavButton("Floor 2 (EV Fast Hub) \u2192", 2);
        JButton jumpL3 = createFloorNavButton("Floor 3 (Fleet Roof) \u2192", 3);

        jumpBox.add(jumpL2);
        jumpBox.add(jumpL3);
        footer.add(jumpBox, BorderLayout.EAST);

        return footer;
    }

    private JButton createFloorNavButton(String label, int floorNum) {
        JButton btn = new JButton(label);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btn.setBackground(new Color(248, 250, 252));
        btn.setForeground(new Color(51, 65, 85));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)
        ));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            activeFloorNumber = floorNum;
            updateFloorTabStyles();
            renderFloorGrid();
        });
        return btn;
    }

    /**
     * Refreshes the floor slots from SQLite and updates both the CAD canvas and Telemetry badges.
     */
    public void renderFloorGrid() {
        updateFloorTabStyles();

        List<ParkingSlot> floorSlots = slotDao.findByFloor(activeFloorNumber);
        if (floorSlots.isEmpty()) {
            Optional<ParkingFloor> floorOpt = parkingLot.getFloorByNumber(activeFloorNumber);
            if (floorOpt.isPresent()) {
                floorSlots = floorOpt.get().getAllSlots();
            }
        }

        int total = floorSlots.size();
        if (total == 0) total = 50;

        long occ = floorSlots.stream().filter(s -> "OCCUPIED".equalsIgnoreCase(s.getStatus())).count();
        long vip = floorSlots.stream().filter(s -> s.getSlotType() == SlotType.HANDICAPPED || s.getSlotType() == SlotType.ACCESSIBLE).count();
        double pct = total > 0 ? (occ * 100.0 / total) : 0.0;

        if (vipAdaBadge != null) vipAdaBadge.setText(vip + " VIP/ADA");
        if (fullPctBadge != null) fullPctBadge.setText(String.format("%.1f%% Full", pct));

        // Update bay filter button counts dynamically from real floor slots
        long countStd = floorSlots.stream().filter(s -> s.getSlotType() == SlotType.REGULAR || s.getSlotType() == SlotType.COMPACT || s.getSlotType() == SlotType.LARGE).count();
        long countEv = floorSlots.stream().filter(s -> s.getSlotType() == SlotType.ELECTRIC).count();
        long countAcc = vip;

        if (btnFilterAll != null) btnFilterAll.setText("All (" + total + ")");
        if (btnFilterStd != null) btnFilterStd.setText("Standard (" + countStd + ")");
        if (btnFilterEv != null) btnFilterEv.setText("EV Bays (" + countEv + ")");
        if (btnFilterAcc != null) btnFilterAcc.setText("Accessible (" + countAcc + ")");

        // Update floor switcher tab counts dynamically
        long f1 = slotDao.findByFloor(1).size();
        long f2 = slotDao.findByFloor(2).size();
        long f3 = slotDao.findByFloor(3).size();
        if (btnL1 != null) btnL1.setText("L1 (Ground) (" + (f1 > 0 ? f1 : 50) + ")");
        if (btnL2 != null) btnL2.setText("L2 (EV Hub) (" + (f2 > 0 ? f2 : 50) + ")");
        if (btnL3 != null) btnL3.setText("L3 (Fleet & VIP) (" + (f3 > 0 ? f3 : 50) + ")");

        cadCanvas.syncFloorSlots(floorSlots);
        cadCanvas.repaint();
    }

    /**
     * Navigates to the appropriate floor and highlights the given slot ID.
     */
    public void highlightSlot(String slotId) {
        if (slotId == null || slotId.isBlank()) return;

        int targetFloor = activeFloorNumber;
        String upper = slotId.toUpperCase();
        if (upper.startsWith("A") || upper.startsWith("F1")) targetFloor = 1;
        else if (upper.startsWith("B") || upper.startsWith("F2")) targetFloor = 2;
        else if (upper.startsWith("C") || upper.startsWith("F3")) targetFloor = 3;

        if (activeFloorNumber != targetFloor) {
            activeFloorNumber = targetFloor;
            renderFloorGrid();
        }

        cadCanvas.highlightStall(slotId);
    }

    // =========================================================================
    // INNER CLASS: Horizontal Dimension Axis Ticks (1, 2, 3, 5, 6)
    // =========================================================================
    private static class HorizontalDimensionAxis extends JPanel {
        public HorizontalDimensionAxis() {
            setPreferredSize(new Dimension(1000, 26));
            setOpaque(false);
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(203, 213, 225)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            // 5 markers: 1, 2, 3, 5, 6 with intermediate distance ticks
            int[] xMarks = {48, 260, 470, 750, w - 30};
            String[] nums = {"1", "2", "3", "5", "6"};
            String[] dists = {"1,400", "2,480", "3,400", "5,420"};

            for (int i = 0; i < xMarks.length; i++) {
                int cx = xMarks[i];
                // Draw circle marker
                g2.setColor(Color.WHITE);
                g2.fillOval(cx - 9, 3, 18, 18);
                g2.setColor(new Color(148, 163, 184)); // slate-400
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(cx - 9, 3, 18, 18);

                g2.setColor(new Color(51, 65, 85)); // slate-700
                g2.setFont(new Font("Consolas", Font.BOLD, 10));
                g2.drawString(nums[i], cx - 3, 16);

                if (i < dists.length) {
                    g2.setColor(new Color(148, 163, 184));
                    g2.setFont(new Font("Consolas", Font.PLAIN, 10));
                    int midX = (cx + xMarks[i + 1]) / 2;
                    g2.drawString(dists[i], midX - 14, 16);
                }
            }
            g2.dispose();
        }
    }

    // =========================================================================
    // INNER CLASS: CAD Architectural Canvas (Faithfully matches HTML blueprint)
    // =========================================================================
    private class CADBlueprintCanvas extends JPanel {
        private final List<CADStall> bays = new ArrayList<>();
        private CADStall selectedStall = null;
        private float zoomScale = 1.0f;

        public CADBlueprintCanvas() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(1020, 500));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Point p = new Point((int) (e.getX() / zoomScale), (int) (e.getY() / zoomScale));
                    for (CADStall stall : bays) {
                        if (stall.bounds.contains(p)) {
                            selectedStall = stall;
                            inspectorPanel.displayStall(stall);
                            repaint();
                            return;
                        }
                    }
                }
            });

            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    Point p = new Point((int) (e.getX() / zoomScale), (int) (e.getY() / zoomScale));
                    boolean hovering = false;
                    for (CADStall stall : bays) {
                        if (stall.bounds.contains(p)) {
                            hovering = true;
                            break;
                        }
                    }
                    setCursor(hovering ? new Cursor(Cursor.HAND_CURSOR) : new Cursor(Cursor.DEFAULT_CURSOR));
                }
            });
        }

        public void zoom(float delta) {
            zoomScale = Math.max(0.6f, Math.min(2.0f, zoomScale + delta));
            if (zoomPercentLabel != null) {
                zoomPercentLabel.setText(Math.round(zoomScale * 100) + "%");
            }
            setPreferredSize(new Dimension((int)(1020 * zoomScale), (int)(500 * zoomScale)));
            revalidate();
            repaint();
        }

        public void resetZoom() {
            zoomScale = 1.0f;
            if (zoomPercentLabel != null) {
                zoomPercentLabel.setText("100%");
            }
            setPreferredSize(new Dimension(1020, 500));
            revalidate();
            repaint();
        }

        public void syncFloorSlots(List<ParkingSlot> realSlots) {
            bays.clear();
            if (realSlots == null || realSlots.isEmpty()) {
                realSlots = slotDao.findByFloor(activeFloorNumber);
            }

            int slotIdx = 0;

            String floorPrefix = (activeFloorNumber == 1) ? "A" : (activeFloorNumber == 2 ? "B" : "C");

            // Bank 1: Top Left Bank (5 cols x 2 rows = 10 bays: 1..10)
            int xG1 = 44;
            int wBay1 = 44;
            int gap1 = 4;
            for (int r = 0; r < 2; r++) {
                int by = (r == 0) ? 36 : 96;
                for (int c = 0; c < 5; c++) {
                    String slotId = (slotIdx < realSlots.size()) ? realSlots.get(slotIdx).getSlotId() : (floorPrefix + (slotIdx + 1));
                    int bx = xG1 + c * (wBay1 + gap1);
                    CADStall stall = new CADStall(slotId, new Rectangle(bx, by, wBay1, 56));
                    if (slotIdx < realSlots.size()) {
                        stall.bindRealSlot(realSlots.get(slotIdx), ticketDao, tariffService);
                    }
                    bays.add(stall);
                    slotIdx++;
                }
            }

            // Bank 2: Top Mid Bank (3 cols x 2 rows = 6 bays: 11..16)
            int xG2 = 300;
            for (int r = 0; r < 2; r++) {
                int by = (r == 0) ? 36 : 96;
                for (int c = 0; c < 3; c++) {
                    String slotId = (slotIdx < realSlots.size()) ? realSlots.get(slotIdx).getSlotId() : (floorPrefix + (slotIdx + 1));
                    int bx = xG2 + c * (wBay1 + gap1);
                    CADStall stall = new CADStall(slotId, new Rectangle(bx, by, wBay1, 56));
                    if (slotIdx < realSlots.size()) {
                        stall.bindRealSlot(realSlots.get(slotIdx), ticketDao, tariffService);
                    }
                    bays.add(stall);
                    slotIdx++;
                }
            }

            // Bank 3: Top Right Bank (7 cols x 2 rows = 14 bays: 17..30)
            int xG3 = 464;
            int wBay3 = 44;
            for (int r = 0; r < 2; r++) {
                int by = (r == 0) ? 36 : 96;
                for (int c = 0; c < 7; c++) {
                    String slotId = (slotIdx < realSlots.size()) ? realSlots.get(slotIdx).getSlotId() : (floorPrefix + (slotIdx + 1));
                    int bx = xG3 + c * (wBay3 + gap1);
                    CADStall stall = new CADStall(slotId, new Rectangle(bx, by, wBay3, 56));
                    if (slotIdx < realSlots.size()) {
                        stall.bindRealSlot(realSlots.get(slotIdx), ticketDao, tariffService);
                    }
                    bays.add(stall);
                    slotIdx++;
                }
            }

            // Bank 4: Middle Row Stalls (8 cols x 1 row = 8 bays: 31..38)
            int xC = 44;
            int wBayC = 50;
            int gapC = 5;
            for (int c = 0; c < 8; c++) {
                String slotId = (slotIdx < realSlots.size()) ? realSlots.get(slotIdx).getSlotId() : (floorPrefix + (slotIdx + 1));
                int bx = xC + c * (wBayC + gapC);
                CADStall stall = new CADStall(slotId, new Rectangle(bx, 226, wBayC, 56));
                if (slotIdx < realSlots.size()) {
                    stall.bindRealSlot(realSlots.get(slotIdx), ticketDao, tariffService);
                }
                bays.add(stall);
                slotIdx++;
            }

            // Bank 5: Bottom Row Stalls (12 cols x 1 row = 12 bays: 39..50)
            int xB = 44;
            int wBayB = 58;
            int gapB = 4;
            for (int c = 0; c < 12; c++) {
                String slotId = (slotIdx < realSlots.size()) ? realSlots.get(slotIdx).getSlotId() : (floorPrefix + (slotIdx + 1));
                int bx = xB + c * (wBayB + gapB);
                CADStall stall = new CADStall(slotId, new Rectangle(bx, 350, wBayB, 54));
                if (slotIdx < realSlots.size()) {
                    stall.bindRealSlot(realSlots.get(slotIdx), ticketDao, tariffService);
                }
                bays.add(stall);
                slotIdx++;
            }

            // Default selected stall: first occupied or first slot
            CADStall defaultStall = bays.stream()
                    .filter(b -> b.isOccupied)
                    .findFirst()
                    .orElse(bays.get(0));

            selectedStall = defaultStall;
            inspectorPanel.displayStall(defaultStall);
        }

        public void highlightStall(String slotId) {
            for (CADStall s : bays) {
                if (s.id.equalsIgnoreCase(slotId)) {
                    selectedStall = s;
                    inspectorPanel.displayStall(s);
                    repaint();
                    break;
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (zoomScale != 1.0f) {
                g2.scale(zoomScale, zoomScale);
            }

            int w = getWidth();
            int h = getHeight();

            // 1. Blueprint Grid Dot Pattern (#cbd5e1 at 16x16)
            g2.setColor(new Color(203, 213, 225, 140));
            for (int x = 16; x < w; x += 16) {
                for (int y = 16; y < h; y += 16) {
                    g2.fillRect(x, y, 1, 1);
                }
            }

            // 2. Left Vertical Dimension Markers (A, B, C, D, E)
            int[] yAxis = {48, 120, 198, 254, 376};
            String[] letters = {"A", "B", "C", "D", "E"};
            for (int i = 0; i < letters.length; i++) {
                int cy = yAxis[i];
                g2.setColor(Color.WHITE);
                g2.fillOval(10, cy - 9, 18, 18);
                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(10, cy - 9, 18, 18);

                g2.setColor(new Color(148, 163, 184)); // slate-400
                g2.setFont(new Font("Consolas", Font.BOLD, 10));
                g2.drawString(letters[i], 16, cy + 4);
            }

            // 3. Central Pedestrian Crosswalk & EV Corridor Header Label
            g2.setColor(new Color(148, 163, 184)); // slate-400
            g2.setFont(new Font("Consolas", Font.BOLD, 9));
            String crosswalkLabel = "▲ PEDESTRIAN CROSSWALK & EV CORRIDOR ▲";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(crosswalkLabel, (w - fm.stringWidth(crosswalkLabel)) / 2, 165);

            // 4. Inbound Roadway (Dark Asphalt)
            drawInboundRoadway(g2, 44, 172, w - 88, 44);

            // 5. Middle Row Stalls (C-1 to C-12 drawn via stall loop) + Substation & HVAC Enclosure
            drawSubstationHVAC(g2, 630, 224, 340, 60);

            // 6. Outbound Roadway (Dark Asphalt)
            drawOutboundRoadway(g2, 44, 292, w - 88, 44);

            // 7. Render All Bay Stalls
            for (CADStall stall : bays) {
                drawStall(g2, stall);
            }

            g2.dispose();
        }

        private void drawInboundRoadway(Graphics2D g2, int x, int y, int rw, int rh) {
            // Road asphalt slab
            g2.setColor(new Color(15, 23, 42)); // slate-900
            g2.fillRoundRect(x, y, rw, rh, 8, 8);

            // Gate 1 In Left Barrier Indicator
            g2.setColor(new Color(16, 185, 129)); // emerald-500
            g2.fillRect(x + 76, y + 2, 4, rh - 4);

            // Gate 1 In Badge
            g2.setColor(new Color(30, 58, 138, 220)); // blue-900
            g2.fillRoundRect(x + 8, y + (rh - 20) / 2, 62, 20, 4, 4);
            g2.setColor(new Color(96, 165, 250)); // blue-400
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(x + 8, y + (rh - 20) / 2, 62, 20, 4, 4);

            g2.setColor(new Color(191, 219, 254)); // blue-200
            g2.setFont(new Font("Consolas", Font.BOLD, 9));
            g2.drawString("GATE 1 IN", x + 12, y + (rh - 20) / 2 + 14);

            // Inbound Lane Arrow + Text
            g2.setColor(new Color(96, 165, 250)); // blue-400
            drawArrow(g2, x + 90, y + rh / 2, x + 104, y + rh / 2);
            g2.setColor(new Color(226, 232, 240));
            g2.setFont(new Font("Consolas", Font.BOLD, 9));
            g2.drawString("INBOUND LANE >>", x + 110, y + rh / 2 + 3);

            // Zebra Crosswalk (Vertical Repeating Stripes)
            int zx = x + 230;
            int zw = 44;
            g2.setColor(new Color(30, 41, 59));
            g2.fillRect(zx, y, zw, rh);
            g2.setColor(Color.WHITE);
            for (int sx = zx + 2; sx < zx + zw - 4; sx += 6) {
                g2.fillRect(sx, y + 2, 3, rh - 4);
            }

            // Dashed Centerline
            Stroke dashed = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{10.0f, 8.0f}, 0.0f);
            g2.setStroke(dashed);
            g2.setColor(new Color(148, 163, 184));
            g2.drawLine(zx + zw + 10, y + rh / 2, x + rw - 14, y + rh / 2);
        }

        private void drawSubstationHVAC(Graphics2D g2, int x, int y, int sw, int sh) {
            // Container box
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(x, y, sw, sh, 8, 8);

            // Hatch Pattern (-45 deg repeating lines)
            g2.setColor(new Color(226, 232, 240, 160));
            Shape oldClip = g2.getClip();
            g2.clip(new RoundRectangle2D.Float(x, y, sw, sh, 8, 8));
            for (int d = -sh; d < sw + sh; d += 8) {
                g2.drawLine(x + d, y, x + d + sh, y + sh);
            }
            g2.setClip(oldClip);

            g2.setColor(new Color(148, 163, 184)); // slate-400 border
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(x, y, sw, sh, 8, 8);

            // Text
            g2.setColor(new Color(15, 23, 42)); // slate-800
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.drawString("SUBSTATION & HVAC", x + 12, y + 18);

            g2.setColor(new Color(100, 116, 139)); // slate-500
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            g2.drawString("11kV Transformer Deck • Plant 02", x + 12, y + 32);

            g2.setColor(new Color(29, 78, 216)); // blue-700
            g2.setFont(new Font("Consolas", Font.BOLD, 9));
            g2.drawString("TRANSFORMER 11 kV / 415 V", x + 12, y + 48);

            // Right Warning Badge
            int iconX = x + sw - 42;
            int iconY = y + (sh - 30) / 2;
            g2.setColor(Color.WHITE);
            g2.fillOval(iconX, iconY, 30, 30);
            g2.setColor(new Color(59, 130, 246));
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawOval(iconX, iconY, 30, 30);

            ImageIcon evBadge = ImageLoader.loadIcon("icons/slots/ev_charging.png", 18, 18);
            if (evBadge != null && evBadge.getImage() != null) {
                g2.drawImage(evBadge.getImage(), iconX + 6, iconY + 6, 18, 18, null);
            } else {
                drawLightningBolt(g2, iconX + 15f, iconY + 15f, 1.0f, new Color(245, 158, 11));
            }
        }

        private void drawOutboundRoadway(Graphics2D g2, int x, int y, int rw, int rh) {
            // Road asphalt slab
            g2.setColor(new Color(15, 23, 42)); // slate-900
            g2.fillRoundRect(x, y, rw, rh, 8, 8);

            // Dashed Centerline on left
            Stroke dashed = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{10.0f, 8.0f}, 0.0f);
            g2.setStroke(dashed);
            g2.setColor(new Color(148, 163, 184));
            g2.drawLine(x + 16, y + rh / 2, x + rw - 220, y + rh / 2);

            // Outbound Arrow + Text
            g2.setColor(new Color(96, 165, 250)); // blue-400
            drawArrow(g2, x + rw - 200, y + rh / 2, x + rw - 214, y + rh / 2);

            g2.setColor(new Color(226, 232, 240));
            g2.setFont(new Font("Consolas", Font.BOLD, 9));
            g2.drawString("<< OUTBOUND TO EXIT GATE 2", x + rw - 190, y + rh / 2 + 3);
        }

        private void drawStall(Graphics2D g2, CADStall stall) {
            Rectangle r = stall.bounds;
            boolean isSelected = (stall == selectedStall);

            // Filter check (dimming unselected)
            boolean dim = false;
            if ("STANDARD".equals(activeFilter) && (stall.isVip || stall.isEv)) dim = true;
            if ("EV".equals(activeFilter) && !stall.isEv) dim = true;
            if ("ACCESSIBLE".equals(activeFilter) && !stall.isVip) dim = true;

            Composite origComp = g2.getComposite();
            if (dim) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
            }

            if (stall.isMaintenance) {
                // Maintenance / Out of Service Slate Hatch
                g2.setColor(new Color(241, 245, 249)); // slate-100
                g2.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
                g2.setColor(new Color(100, 116, 139)); // slate-500
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(r.x, r.y, r.width, r.height, 6, 6);

                // Caution diagonal stripes
                g2.setColor(new Color(203, 213, 225));
                for (int i = -r.height; i < r.width; i += 8) {
                    g2.drawLine(Math.max(r.x, r.x + i), r.y, Math.min(r.x + r.width, r.x + i + r.height), r.y + r.height);
                }

                g2.setColor(new Color(51, 65, 85));
                g2.setFont(new Font("Consolas", Font.BOLD, 8));
                g2.drawString(stall.id, r.x + 3, r.y + 11);

                g2.setColor(new Color(100, 116, 139));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 7));
                g2.drawString("OFFLINE", r.x + (r.width - 26) / 2, r.y + r.height - 4);

            } else if (stall.isVip) {
                // Iris VIP/ADA
                g2.setColor(new Color(79, 70, 229)); // indigo-600
                g2.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
                g2.setColor(new Color(67, 56, 202)); // indigo-700
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(r.x, r.y, r.width, r.height, 6, 6);

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Consolas", Font.BOLD, 9));
                g2.drawString(stall.id, r.x + 3, r.y + 12);

                ImageIcon badge = ImageLoader.loadIcon("icons/slots/handicapped_badge.png", 18, 18);
                if (badge != null && badge.getImage() != null) {
                    g2.drawImage(badge.getImage(), r.x + (r.width - 18) / 2, r.y + 16, 18, 18, null);
                } else {
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                    g2.drawString("VIP", r.x + (r.width - 18) / 2, r.y + 28);
                }

                g2.setColor(new Color(199, 210, 254)); // indigo-200
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 7));
                g2.drawString("VACANT", r.x + 3, r.y + r.height - 4);

            } else if (stall.isReserved) {
                // RSVP Slate
                g2.setColor(new Color(241, 245, 249)); // slate-100
                g2.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
                g2.setColor(new Color(203, 213, 225)); // slate-300
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(r.x, r.y, r.width, r.height, 6, 6);

                g2.setColor(new Color(71, 85, 105)); // slate-600
                g2.setFont(new Font("Consolas", Font.BOLD, 8));
                g2.drawString(stall.id, r.x + 3, r.y + 12);

                g2.setColor(new Color(100, 116, 139));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 7));
                g2.drawString("RSVP", r.x + (r.width - 20) / 2, r.y + r.height - 4);

            } else if (stall.isOccupied) {
                // Crimson Active
                g2.setColor(new Color(254, 226, 226)); // red-100
                g2.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
                g2.setColor(new Color(248, 113, 113)); // red-400
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(r.x, r.y, r.width, r.height, 6, 6);

                g2.setColor(new Color(153, 27, 27)); // red-800
                g2.setFont(new Font("Consolas", Font.BOLD, 8));
                g2.drawString(stall.id, r.x + 3, r.y + 11);

                // Car Icon in center
                ImageIcon icon = ImageLoader.getVehicleIcon(stall.vehicleType, 22, 22);
                if (icon != null && icon.getImage() != null) {
                    g2.drawImage(icon.getImage(), r.x + (r.width - 22) / 2, r.y + 16, 22, 22, null);
                } else {
                    g2.setColor(new Color(220, 38, 38));
                    g2.fillRoundRect(r.x + 5, r.y + 16, r.width - 10, 20, 4, 4);
                }

                g2.setColor(new Color(220, 38, 38)); // red-600
                g2.setFont(new Font("Segoe UI", Font.BOLD, 7));
                g2.drawString("ACTIVE", r.x + (r.width - 22) / 2, r.y + r.height - 4);

            } else {
                // Mint Vacant
                g2.setColor(new Color(209, 250, 229)); // emerald-100
                g2.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
                g2.setColor(new Color(52, 211, 153)); // emerald-400
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(r.x, r.y, r.width, r.height, 6, 6);

                g2.setColor(new Color(6, 95, 70)); // emerald-800
                g2.setFont(new Font("Consolas", Font.BOLD, 8));
                g2.drawString(stall.id, r.x + 3, r.y + 11);

                if (stall.isEv) {
                    ImageIcon evIcon = ImageLoader.loadIcon("icons/slots/ev_charging.png", 14, 14);
                    if (evIcon != null && evIcon.getImage() != null) {
                        g2.drawImage(evIcon.getImage(), r.x + (r.width - 14) / 2, r.y + 16, 14, 14, null);
                    } else {
                        drawLightningBolt(g2, r.x + r.width / 2f, r.y + 24f, 0.8f, new Color(16, 185, 129));
                    }
                }

                g2.setColor(new Color(5, 150, 105)); // emerald-600
                g2.setFont(new Font("Segoe UI", Font.BOLD, 7));
                g2.drawString("VACANT", r.x + (r.width - 26) / 2, r.y + r.height - 4);
            }

            // Selection dashed ring
            if (isSelected) {
                g2.setColor(new Color(37, 99, 235)); // blue-600
                Stroke dashed = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{4.0f, 2.0f}, 0.0f);
                g2.setStroke(dashed);
                g2.drawRoundRect(r.x - 2, r.y - 2, r.width + 4, r.height + 4, 8, 8);
            }

            g2.setComposite(origComp);
        }

        private void drawLightningBolt(Graphics2D g2, float cx, float cy, float scale, Color color) {
            Graphics2D g = (Graphics2D) g2.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
            p.moveTo(cx + 1 * scale, cy - 8 * scale);
            p.lineTo(cx - 5 * scale, cy + 0 * scale);
            p.lineTo(cx - 1 * scale, cy + 0 * scale);
            p.lineTo(cx - 3 * scale, cy + 8 * scale);
            p.lineTo(cx + 5 * scale, cy - 1 * scale);
            p.lineTo(cx + 1 * scale, cy - 1 * scale);
            p.closePath();
            g.fill(p);
            g.dispose();
        }

        private void drawArrow(Graphics2D g2, int x1, int y1, int x2, int y2) {
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawLine(x1, y1, x2, y2);
            if (x2 > x1) {
                g2.drawLine(x2 - 4, y2 - 3, x2, y2);
                g2.drawLine(x2 - 4, y2 + 3, x2, y2);
            } else {
                g2.drawLine(x2 + 4, y2 - 3, x2, y2);
                g2.drawLine(x2 + 4, y2 + 3, x2, y2);
            }
        }
    }

    // =========================================================================
    // INNER CLASS: Bay Stall Inspector & Environmental Telemetry Panel (Right Side)
    // =========================================================================
    private class BayInspectorPanel extends JPanel {

        private JLabel stallIdLabel;
        private JLabel statusBadgeLabel;
        private JLabel vehicleAvatarLabel;
        private JLabel plateNumberLabel;
        private JLabel modelLabel;
        private JLabel tariffLabel;
        private JLabel inTimeLabel;
        private JLabel dwellTimeLabel;
        private JLabel feeLabel;
        private CCTVStreamPanel cameraPanel;

        public BayInspectorPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);

            initInspector();
        }

        private void initInspector() {
            // --- CARD 1: Bay Stall Inspector Card ---
            RoundedPanel inspectorCard = new RoundedPanel(8, Color.WHITE, new Color(226, 232, 240));
            inspectorCard.setLayout(new BoxLayout(inspectorCard, BoxLayout.Y_AXIS));
            inspectorCard.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            // Inspector Header: Search Icon + Title + Status Badge
            JPanel topRow = new JPanel(new BorderLayout());
            topRow.setOpaque(false);

            JPanel titleGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            titleGroup.setOpaque(false);
            JLabel searchIcon = new JLabel(ImageLoader.getNavIcon("search", 14));
            stallIdLabel = new JLabel("Bay Stall Inspector (G-08)");
            stallIdLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            stallIdLabel.setForeground(new Color(15, 23, 42)); // slate-800
            titleGroup.add(searchIcon);
            titleGroup.add(stallIdLabel);
            topRow.add(titleGroup, BorderLayout.WEST);

            statusBadgeLabel = new JLabel("OCCUPIED");
            statusBadgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
            statusBadgeLabel.setForeground(new Color(239, 68, 68)); // red-600
            statusBadgeLabel.setBackground(new Color(254, 226, 226)); // red-100
            statusBadgeLabel.setOpaque(true);
            statusBadgeLabel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(254, 202, 202), 1, true),
                    BorderFactory.createEmptyBorder(2, 6, 2, 6)
            ));
            topRow.add(statusBadgeLabel, BorderLayout.EAST);

            inspectorCard.add(topRow);
            inspectorCard.add(Box.createVerticalStrut(10));

            // License Plate Callout Section
            JPanel plateContainer = new JPanel(new BorderLayout(10, 0));
            plateContainer.setBackground(new Color(255, 251, 235)); // amber-50/50
            plateContainer.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(253, 230, 138), 1, true),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));

            // Vehicle Avatar Badge
            vehicleAvatarLabel = new JLabel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Color.WHITE);
                    g2.fillOval(0, 0, getWidth(), getHeight());
                    g2.setColor(new Color(96, 165, 250)); // blue-400
                    g2.setStroke(new BasicStroke(2.0f));
                    g2.drawOval(0, 0, getWidth() - 1, getHeight() - 1);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            vehicleAvatarLabel.setPreferredSize(new Dimension(38, 38));
            vehicleAvatarLabel.setIcon(ImageLoader.getVehicleIcon(VehicleType.CAR, 24, 24));
            vehicleAvatarLabel.setHorizontalAlignment(SwingConstants.CENTER);
            plateContainer.add(vehicleAvatarLabel, BorderLayout.WEST);

            // Embossed Indian License Plate
            JPanel plateBox = new JPanel();
            plateBox.setLayout(new BoxLayout(plateBox, BoxLayout.Y_AXIS));
            plateBox.setOpaque(false);

            JPanel embossedPlate = new JPanel(new GridBagLayout());
            embossedPlate.setBackground(new Color(251, 191, 36)); // amber-400
            embossedPlate.setBorder(BorderFactory.createLineBorder(new Color(217, 119, 6), 2, true)); // amber-600
            embossedPlate.setPreferredSize(new Dimension(160, 28));

            plateNumberLabel = new JLabel("UP32 AB 1234");
            plateNumberLabel.setFont(new Font("Consolas", Font.BOLD, 14));
            plateNumberLabel.setForeground(new Color(2, 6, 23)); // slate-950
            embossedPlate.add(plateNumberLabel);
            plateBox.add(embossedPlate);

            JLabel anprMeta = new JLabel("ANPR 99.4% • FastTag Active", SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(4, 120, 87));
                    g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cy = getHeight() / 2;
                    int startX = 6;
                    g2.drawLine(startX, cy, startX + 3, cy + 3);
                    g2.drawLine(startX + 3, cy + 3, startX + 9, cy - 3);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            anprMeta.setFont(new Font("Segoe UI", Font.BOLD, 10));
            anprMeta.setForeground(new Color(4, 120, 87)); // emerald-700
            anprMeta.setAlignmentX(Component.CENTER_ALIGNMENT);
            anprMeta.setBorder(BorderFactory.createEmptyBorder(3, 16, 0, 0));
            plateBox.add(anprMeta);

            plateContainer.add(plateBox, BorderLayout.CENTER);
            inspectorCard.add(plateContainer);
            inspectorCard.add(Box.createVerticalStrut(10));

            // Optical Camera Feed Viewport (Simulated)
            cameraPanel = new CCTVStreamPanel();
            inspectorCard.add(cameraPanel);
            inspectorCard.add(Box.createVerticalStrut(10));

            // Telemetry Details List
            JPanel details = new JPanel();
            details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
            details.setOpaque(false);
            details.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(241, 245, 249)),
                    BorderFactory.createEmptyBorder(6, 0, 6, 0)
            ));

            modelLabel = addRow(details, "Vehicle Model", "Tesla Model 3 (Sedan)", false);
            tariffLabel = addRow(details, "Assigned Tariff", "\u20B960.00 / hr (Tier 1)", false);
            inTimeLabel = addRow(details, "Inbound Timestamp", "14:18:22 (Today)", false);
            dwellTimeLabel = addRow(details, "Elapsed Dwell Time", "42m 18s", true);
            feeLabel = addRow(details, "Accrued Parking Fee", "\u20B960.00", false);

            inspectorCard.add(details);
            inspectorCard.add(Box.createVerticalStrut(10));

            // Sidebar Action Buttons
            ModernButton btnWayfinding = new ModernButton("Send Directions to Phone (Simulated)",
                    com.smartparking.ui.util.VectorIconUtil.getIcon(com.smartparking.ui.util.VectorIconUtil.IconType.PHONE, 14, Color.WHITE),
                    new Color(37, 99, 235), new Color(29, 78, 216), Color.WHITE);
            btnWayfinding.setFont(new Font("Segoe UI", Font.BOLD, 11));
            btnWayfinding.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            btnWayfinding.addActionListener(e -> {
                String input = JOptionPane.showInputDialog(FloorMapPanel.this,
                        "Enter 10-digit Indian Mobile Number for directions to " + stallIdLabel.getText() + ":",
                        "9876543210");
                if (input != null && !input.trim().isEmpty()) {
                    String clean = input.trim().replaceAll("[^0-9]", "");
                    if (clean.matches("^[6-9][0-9]{9}$")) {
                        JOptionPane.showMessageDialog(FloorMapPanel.this,
                                "Simulated SMS sent: Turn-by-turn route to " + stallIdLabel.getText() + " dispatched to +91 " + clean,
                                "Directions Dispatched (Simulated)", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(FloorMapPanel.this,
                                "Invalid mobile number. Please enter a valid 10-digit Indian mobile number starting with 6-9.",
                                "Validation Error", JOptionPane.WARNING_MESSAGE);
                    }
                }
            });
            inspectorCard.add(btnWayfinding);
            inspectorCard.add(Box.createVerticalStrut(6));

            JPanel actionGrid = new JPanel(new GridLayout(1, 2, 8, 0));
            actionGrid.setOpaque(false);

            ModernButton btnIncident = new ModernButton("Incident Slip (Simulated)", null,
                    new Color(241, 245, 249), new Color(226, 232, 240), new Color(51, 65, 85));
            btnIncident.setFont(new Font("Segoe UI", Font.BOLD, 10));
            btnIncident.addActionListener(e -> JOptionPane.showMessageDialog(FloorMapPanel.this,
                    "Security incident slip filed for stall " + stallIdLabel.getText() + " (Simulated).",
                    "Incident Slip", JOptionPane.INFORMATION_MESSAGE));

            ModernButton btnExit = new ModernButton("Checkout & Bill \u2192", null,
                    new Color(220, 38, 38), new Color(185, 28, 28), Color.WHITE);
            btnExit.setFont(new Font("Segoe UI", Font.BOLD, 11));
            btnExit.addActionListener(e -> {
                if (navigationHandler != null) {
                    navigationHandler.accept("CHECKOUT");
                }
            });

            actionGrid.add(btnIncident);
            actionGrid.add(btnExit);
            inspectorCard.add(actionGrid);

            add(inspectorCard);
            add(Box.createVerticalStrut(10));

            // --- CARD 2: Floor Sensors & Environmental Telemetry ---
            RoundedPanel telemetryCard = new RoundedPanel(8, Color.WHITE, new Color(226, 232, 240));
            telemetryCard.setLayout(new BoxLayout(telemetryCard, BoxLayout.Y_AXIS));
            telemetryCard.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

            JLabel teleTitle = new JLabel("Floor Sensors & Environmental Telemetry");
            teleTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
            teleTitle.setForeground(new Color(30, 41, 59)); // slate-800
            telemetryCard.add(teleTitle);
            telemetryCard.add(Box.createVerticalStrut(8));

            JPanel sensorGrid = new JPanel(new GridLayout(1, 2, 8, 0));
            sensorGrid.setOpaque(false);
            sensorGrid.add(createSensorMetricCard("CO LEVEL", "12 ppm", "Normal (< 25)", new Color(16, 185, 129)));
            sensorGrid.add(createSensorMetricCard("VENTILATION", "Stage 1", "Fans 450 RPM", new Color(37, 99, 235)));
            telemetryCard.add(sensorGrid);
            telemetryCard.add(Box.createVerticalStrut(8));

            JPanel sprinklerRow = new JPanel(new BorderLayout());
            sprinklerRow.setOpaque(false);
            sprinklerRow.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(241, 245, 249)),
                    BorderFactory.createEmptyBorder(4, 0, 0, 0)
            ));

            JLabel spkLeft = new JLabel("\u25CF Fire Isolation Sprinklers: ARMED");
            spkLeft.setFont(new Font("Segoe UI", Font.BOLD, 11));
            spkLeft.setForeground(new Color(16, 185, 129)); // emerald-500

            JLabel spkRight = new JLabel("1.2 bar");
            spkRight.setFont(new Font("Consolas", Font.PLAIN, 10));
            spkRight.setForeground(new Color(100, 116, 139));

            sprinklerRow.add(spkLeft, BorderLayout.WEST);
            sprinklerRow.add(spkRight, BorderLayout.EAST);
            telemetryCard.add(sprinklerRow);

            add(telemetryCard);
        }

        private JLabel addRow(JPanel container, String label, String val, boolean isBlue) {
            JPanel r = new JPanel(new BorderLayout());
            r.setOpaque(false);
            r.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

            JLabel l = new JLabel(label);
            l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            l.setForeground(new Color(100, 116, 139));

            JLabel v = new JLabel(val);
            v.setFont(new Font(isBlue ? "Consolas" : "Segoe UI", Font.BOLD, 11));
            v.setForeground(isBlue ? new Color(37, 99, 235) : new Color(15, 23, 42));

            r.add(l, BorderLayout.WEST);
            r.add(v, BorderLayout.EAST);
            container.add(r);
            return v;
        }

        private JPanel createSensorMetricCard(String title, String val, String sub, Color valColor) {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBackground(new Color(248, 250, 252));
            p.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(226, 232, 240), 1, true),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)
            ));

            JLabel t = new JLabel(title);
            t.setFont(new Font("Segoe UI", Font.BOLD, 9));
            t.setForeground(new Color(100, 116, 139));

            JLabel v = new JLabel(val);
            v.setFont(new Font("Consolas", Font.BOLD, 13));
            v.setForeground(valColor);

            JLabel s = new JLabel(sub);
            s.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            s.setForeground(new Color(148, 163, 184));

            p.add(t);
            p.add(v);
            p.add(s);
            return p;
        }

        public void displayStall(CADStall stall) {
            stallIdLabel.setText("Bay Stall Inspector (" + stall.id + ")");

            if (stall.isMaintenance) {
                statusBadgeLabel.setText("MAINTENANCE");
                statusBadgeLabel.setForeground(new Color(100, 116, 139));
                statusBadgeLabel.setBackground(new Color(241, 245, 249));
                plateNumberLabel.setText("[OFFLINE]");
                modelLabel.setText("Bay Under Maintenance");
                tariffLabel.setText("\u2014");
                inTimeLabel.setText("N/A");
                dwellTimeLabel.setText("0m 0s");
                feeLabel.setText("\u2014");
                cameraPanel.setCameraState(false, "", null);
                vehicleAvatarLabel.setIcon(null);
            } else if (stall.isOccupied) {
                statusBadgeLabel.setText("OCCUPIED");
                statusBadgeLabel.setForeground(new Color(239, 68, 68));
                statusBadgeLabel.setBackground(new Color(254, 226, 226));
                String displayPlate = stall.plate.isEmpty() ? "UP32AB1234" : stall.plate;
                plateNumberLabel.setText(PlateUtil.format(displayPlate) + " [" + PlateUtil.district(displayPlate) + "]");
                modelLabel.setText(stall.vehicleModel);
                tariffLabel.setText(String.format("\u20B9%.2f / hr", stall.tariff));
                inTimeLabel.setText(stall.inTimeStr);
                dwellTimeLabel.setText(stall.dwellTimeStr);
                feeLabel.setText(String.format("\u20B9%.2f", stall.accruedFee));
                cameraPanel.setCameraState(true, stall.plate, stall.vehicleType);
                vehicleAvatarLabel.setIcon(ImageLoader.getVehicleIcon(stall.vehicleType, 24, 24));
            } else if (stall.isReserved) {
                statusBadgeLabel.setText("RESERVED");
                statusBadgeLabel.setForeground(new Color(217, 119, 6));
                statusBadgeLabel.setBackground(new Color(254, 243, 199));
                plateNumberLabel.setText("[RSVP]");
                modelLabel.setText("Advance Reservation");
                tariffLabel.setText(String.format("\u20B9%.2f / hr", stall.tariff));
                inTimeLabel.setText("Held for Arrival");
                dwellTimeLabel.setText("0m 0s");
                feeLabel.setText("\u20B90.00");
                cameraPanel.setCameraState(false, "", null);
                vehicleAvatarLabel.setIcon(null);
            } else {
                statusBadgeLabel.setText("VACANT");
                statusBadgeLabel.setForeground(new Color(16, 185, 129));
                statusBadgeLabel.setBackground(new Color(209, 250, 229));
                plateNumberLabel.setText("[AVAILABLE]");
                modelLabel.setText(stall.isVip ? "Accessible / VIP Bay" : (stall.isEv ? "EV Fast-Charging Hub" : "Standard Parking Bay"));
                tariffLabel.setText(String.format("\u20B9%.2f / hr", stall.tariff));
                inTimeLabel.setText("N/A");
                dwellTimeLabel.setText("0m 0s");
                feeLabel.setText("\u20B90.00");
                cameraPanel.setCameraState(false, "", null);
                vehicleAvatarLabel.setIcon(null);
            }
        }
    }

    // =========================================================================
    // INNER CLASS: Simulated Optical CCTV Camera Stream
    // =========================================================================
    private static class CCTVStreamPanel extends JPanel {
        private boolean isOccupied = true;
        private VehicleType vehicleType = VehicleType.CAR;

        public CCTVStreamPanel() {
            setPreferredSize(new Dimension(Integer.MAX_VALUE, 120));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
            setOpaque(false);
        }

        public void setCameraState(boolean occupied, String plate, VehicleType vt) {
            this.isOccupied = occupied;
            this.vehicleType = (vt != null) ? vt : VehicleType.CAR;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Dark Asphalt Viewport
            g2.setColor(new Color(15, 23, 42)); // slate-900
            g2.fillRoundRect(0, 0, w, h, 8, 8);
            g2.setColor(new Color(51, 65, 85)); // slate-700
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

            // Top Header: CAM-GATE-NORTH-01 + 1080p @ 60FPS
            g2.setColor(new Color(30, 41, 59, 200));
            g2.fillRoundRect(8, 8, 116, 16, 4, 4);
            g2.setColor(new Color(203, 213, 225));
            g2.setFont(new Font("Consolas", Font.BOLD, 9));
            g2.drawString("CAM-GATE-NORTH-01", 12, 20);

            g2.setColor(new Color(52, 211, 153)); // emerald-400
            g2.drawString("1080p @ 60FPS", w - 84, 20);

            // Camera Perspective Converging Lines
            g2.setColor(new Color(71, 85, 105, 120));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(w / 4, 26, w / 6, h - 22);
            g2.drawLine(3 * w / 4, 26, 5 * w / 6, h - 22);

            // Center Vehicle Target Reticle
            int cx = w / 2;
            int cy = h / 2;
            if (isOccupied) {
                // Dashed target circle
                g2.setColor(new Color(30, 58, 138, 160));
                g2.fillOval(cx - 24, cy - 24, 48, 48);

                Stroke dashed = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{4.0f, 3.0f}, 0.0f);
                g2.setStroke(dashed);
                g2.setColor(new Color(251, 191, 36)); // amber-400
                g2.drawOval(cx - 24, cy - 24, 48, 48);

                ImageIcon vIcon = ImageLoader.getVehicleIcon(vehicleType, 30, 30);
                if (vIcon != null && vIcon.getImage() != null) {
                    g2.drawImage(vIcon.getImage(), cx - 15, cy - 15, 30, 30, null);
                }
            } else {
                g2.setColor(new Color(16, 185, 129, 140));
                g2.fillOval(cx - 14, cy - 14, 28, 28);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 9));
                g2.drawString("VACANT", cx - 18, cy + 3);
            }

            // Bottom Footer: LIVE CAD-ANPR STREAM + REC [ON]
            // Bottom Footer: LIVE CAD-ANPR STREAM (Simulated) + REC [ON]
            g2.setColor(new Color(100, 116, 139));
            g2.setFont(new Font("Consolas", Font.BOLD, 8));
            g2.drawString("LIVE CAD-ANPR STREAM (Simulated)", 8, h - 8);

            g2.setColor(new Color(239, 68, 68)); // red-500
            g2.drawString("\u25CF REC [ON]", w - 60, h - 8);

            g2.dispose();
        }
    }

    // =========================================================================
    // HELPER DATA MODEL: CAD Stall Representation
    // =========================================================================
    private static class CADStall {
        String id;
        final Rectangle bounds;
        boolean isOccupied = false;
        boolean isReserved = false;
        boolean isMaintenance = false;
        boolean isVip = false;
        boolean isEv = false;
        String category = "STANDARD";
        VehicleType vehicleType = VehicleType.CAR;
        String plate = "";
        String vehicleModel = "Standard Automobile (Sedan)";
        double tariff = 60.00;
        String inTimeStr = "14:18:22 (Today)";
        String dwellTimeStr = "42m 18s";
        double accruedFee = 60.00;

        public CADStall(String id, Rectangle bounds) {
            this.id = id;
            this.bounds = bounds;
        }

        public void bindRealSlot(ParkingSlot slot) {
            bindRealSlot(slot, null, null);
        }

        public void bindRealSlot(ParkingSlot slot, com.smartparking.repository.TicketDao tDao, com.smartparking.service.TariffService tService) {
            this.id = slot.getSlotId();
            this.isOccupied = "OCCUPIED".equalsIgnoreCase(slot.getStatus());
            this.isReserved = "RESERVED".equalsIgnoreCase(slot.getStatus());
            this.isMaintenance = "MAINTENANCE".equalsIgnoreCase(slot.getStatus()) || "OUT_OF_SERVICE".equalsIgnoreCase(slot.getStatus());
            this.tariff = slot.getHourlyRate() > 0 ? slot.getHourlyRate() : 40.00;

            if (slot.getSlotType() == SlotType.HANDICAPPED || slot.getSlotType() == SlotType.ACCESSIBLE) {
                this.isVip = true;
                this.category = "ACCESSIBLE";
            } else if (slot.getSlotType() == SlotType.ELECTRIC) {
                this.isEv = true;
                this.category = "EV";
            } else {
                this.category = "STANDARD";
            }

            if (isOccupied) {
                if (slot.getCurrentVehiclePlate() != null && !slot.getCurrentVehiclePlate().isBlank()) {
                    this.plate = slot.getCurrentVehiclePlate();
                } else if (slot.getCurrentVehicle() != null) {
                    this.plate = slot.getCurrentVehicle().getLicensePlate();
                } else {
                    int num = 1001;
                    try {
                        num = 1000 + Integer.parseInt(slot.getSlotId().replaceAll("[^0-9]", ""));
                    } catch (Exception ignored) {}
                    this.plate = "UP32AB" + num;
                }

                if (slot.getCurrentVehicle() != null) {
                    this.vehicleType = slot.getCurrentVehicle().getType();
                    this.vehicleModel = slot.getCurrentVehicle().getType().name() + " (" + slot.getCurrentVehicle().getBrand() + ")";
                } else {
                    int num = 1;
                    try {
                        num = Integer.parseInt(slot.getSlotId().replaceAll("[^0-9]", ""));
                    } catch (Exception ignored) {}
                    this.vehicleType = (num % 4 == 0) ? VehicleType.EV_CAR : ((num % 3 == 0) ? VehicleType.MOTORCYCLE : VehicleType.CAR);
                    this.vehicleModel = this.vehicleType.name() + " (Sedan)";
                }

                // Query active ticket from DB for real dwell time and accrued fee
                com.smartparking.model.ticket.ParkingTicket activeTkt = null;
                if (tDao != null && this.plate != null && !this.plate.isEmpty()) {
                    activeTkt = tDao.findActiveByLicensePlate(this.plate).orElse(null);
                }

                if (activeTkt != null && activeTkt.getEntryTime() != null) {
                    java.time.LocalDateTime entry = activeTkt.getEntryTime();
                    this.inTimeStr = entry.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss (dd MMM)"));
                    long mins = java.time.Duration.between(entry, java.time.LocalDateTime.now()).toMinutes();
                    if (mins < 0) mins = 0;
                    this.dwellTimeStr = (mins / 60) + "h " + (mins % 60) + "m";
                    if (tService != null) {
                        try {
                            this.accruedFee = tService.calculateFee(activeTkt, java.time.LocalDateTime.now(), false, null).getTotalAmount();
                        } catch (Exception e) {
                            this.accruedFee = this.tariff;
                        }
                    } else {
                        this.accruedFee = this.tariff;
                    }
                } else {
                    this.inTimeStr = "10:30:00 (Today)";
                    this.dwellTimeStr = "45m 12s";
                    this.accruedFee = this.tariff;
                }
            }
        }
    }
}
