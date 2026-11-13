package com.smartparking.ui.panels;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.repository.ChargingSessionDao;
import com.smartparking.repository.ParkingSlotDao;
import com.smartparking.repository.TicketDao;
import com.smartparking.service.ParkingService;
import com.smartparking.service.TariffService;
import com.smartparking.ui.components.Badge;
import com.smartparking.ui.components.ModernButton;
import com.smartparking.ui.components.PageHeader;
import com.smartparking.ui.components.RoundedPanel;
import com.smartparking.ui.components.SegmentedControl;
import com.smartparking.ui.components.Toast;
import com.smartparking.ui.theme.DarkTheme;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.VectorIconUtil;
import com.smartparking.ui.util.VectorIconUtil.IconType;
import com.smartparking.util.PlateUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Floor map: a live, at-a-glance grid of every bay on a level.
 *
 * <pre>
 *  Floor map                                   [ L1 | L2 | L3 ]  [Refresh]
 *  ┌ Available 34 ┬ Occupied 12 ┬ Reserved 2 ┬ Maintenance 1 ┬ EV charging 1 ┐
 *  [All 50] [Standard 42] [EV 4] [Accessible 4]                  [-] 100% [+]
 *  ┌──────────── bay grid ─────────────┐ ┌── Bay inspector ──┐
 *  │ A1  A2  A3 ...                    │ │ A14  ● Occupied    │
 *  │ ──── drive lane ────              │ │ UP32 AB 1234       │
 *  └───────────────────────────────────┘ └────────────────────┘
 * </pre>
 *
 * <p>All states come from the {@code slots} table, active tickets and active charging sessions.
 * Bays are clickable and keyboard navigable (arrow keys + Enter to check out an occupied bay).
 */
public class FloorMapPanel extends JPanel {

    private static final String[] FLOOR_LABELS = {"Level 1", "Level 2", "Level 3"};
    private static final String[] FLOOR_NAMES = {"Ground floor", "EV hub", "Fleet & VIP"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter ENTRY = DateTimeFormatter.ofPattern("dd MMM, HH:mm");

    private final ParkingLot parkingLot;
    private final ParkingService parkingService;
    private final ParkingSlotDao slotDao;
    private final TicketDao ticketDao;
    private final TariffService tariffService;
    private final ChargingSessionDao chargingDao;
    private final Consumer<String> navigationHandler;

    private int activeFloorNumber = 1;
    private String activeFilter = "ALL";

    private PageHeader header;
    private SegmentedControl floorSwitch;
    private final Map<String, StatChip> statChips = new HashMap<>();
    private OccupancyBar occupancyBar;
    private final Map<String, FilterPill> filterPills = new HashMap<>();
    private JLabel zoomPercentLabel;

    private BayGrid bayGrid;
    private BayInspector inspector;

    public FloorMapPanel(ParkingLot parkingLot, ParkingService parkingService, Consumer<String> navigationHandler) {
        this.parkingLot = parkingLot;
        this.parkingService = parkingService;
        this.slotDao = new ParkingSlotDao();
        this.ticketDao = new TicketDao();
        this.tariffService = new TariffService();
        this.chargingDao = new ChargingSessionDao();
        this.navigationHandler = navigationHandler;

        setLayout(new BorderLayout(0, 0));
        setBackground(UITheme.BG_CANVAS);
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        initUI();
        renderFloorGrid();
    }

    // =====================================================================================
    // Layout
    // =====================================================================================
    private void initUI() {
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        header = new PageHeader("Floor map", "Live bay status");
        floorSwitch = new SegmentedControl(FLOOR_LABELS, 0, sel -> {
            for (int i = 0; i < FLOOR_LABELS.length; i++) {
                if (FLOOR_LABELS[i].equals(sel)) {
                    activeFloorNumber = i + 1;
                    renderFloorGrid();
                }
            }
        });
        floorSwitch.setToolTipText("Switch level (Left / Right arrows when focused)");
        header.addAction(floorSwitch);
        ModernButton refresh = ModernButton.createSecondary("Refresh",
                VectorIconUtil.getIcon(IconType.REFRESH, 14, UITheme.TEXT_SECONDARY));
        refresh.setToolTipText("Reload bay states from the database");
        refresh.addActionListener(e -> {
            renderFloorGrid();
            Toast.info(this, "Floor map refreshed", FLOOR_LABELS[activeFloorNumber - 1] + " · " + LocalDateTime.now().format(TIME));
        });
        header.addAction(refresh);
        header.setAlignmentX(LEFT_ALIGNMENT);
        top.add(header);

        JComponent stats = buildStatsStrip();
        stats.setAlignmentX(LEFT_ALIGNMENT);
        top.add(stats);
        top.add(Box.createVerticalStrut(16));
        JComponent toolbar = buildToolbar();
        toolbar.setAlignmentX(LEFT_ALIGNMENT);
        top.add(toolbar);
        top.add(Box.createVerticalStrut(12));
        add(top, BorderLayout.NORTH);

        // Grid card
        RoundedPanel gridCard = new RoundedPanel(UITheme.RADIUS_LG, UITheme.BG_CARD, UITheme.BORDER_SUBTLE);
        gridCard.setLayout(new BorderLayout());
        gridCard.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        bayGrid = new BayGrid();
        JScrollPane scroll = new JScrollPane(bayGrid);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getHorizontalScrollBar().setUnitIncrement(18);
        gridCard.add(scroll, BorderLayout.CENTER);

        inspector = new BayInspector();
        JScrollPane inspectorScroll = new JScrollPane(inspector);
        inspectorScroll.setBorder(null);
        inspectorScroll.setOpaque(false);
        inspectorScroll.getViewport().setOpaque(false);
        inspectorScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        inspectorScroll.getVerticalScrollBar().setUnitIncrement(16);
        inspectorScroll.setPreferredSize(new Dimension(336, 0));

        JPanel workspace = new JPanel(new BorderLayout(16, 0));
        workspace.setOpaque(false);
        workspace.add(gridCard, BorderLayout.CENTER);
        workspace.add(inspectorScroll, BorderLayout.EAST);
        add(workspace, BorderLayout.CENTER);
    }

    /** One row of state counters for the active level; doubles as the colour legend. */
    private JComponent buildStatsStrip() {
        RoundedPanel strip = new RoundedPanel(UITheme.RADIUS_LG, UITheme.BG_CARD, UITheme.BORDER_SUBTLE);
        strip.setLayout(new BorderLayout(0, 12));
        strip.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        JPanel chips = new JPanel(new GridLayout(1, 5, 12, 0));
        chips.setOpaque(false);
        String[][] defs = {
                {"AVAILABLE", "Available"}, {"OCCUPIED", "Occupied"}, {"RESERVED", "Reserved"},
                {"MAINTENANCE", "Maintenance"}, {"CHARGING", "EV charging"}};
        for (String[] d : defs) {
            StatChip chip = new StatChip(d[1], stateColor(d[0]));
            statChips.put(d[0], chip);
            chips.add(chip);
        }
        strip.add(chips, BorderLayout.CENTER);
        occupancyBar = new OccupancyBar();
        strip.add(occupancyBar, BorderLayout.SOUTH);
        strip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        return strip;
    }

    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);
        String[][] defs = {{"ALL", "All bays"}, {"STANDARD", "Standard"}, {"EV", "EV"}, {"ACCESSIBLE", "Accessible"}};
        for (String[] d : defs) {
            FilterPill pill = new FilterPill(d[0], d[1]);
            filterPills.put(d[0], pill);
            filters.add(pill);
        }
        bar.add(filters, BorderLayout.WEST);

        JPanel zoom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        zoom.setOpaque(false);
        JLabel zl = new JLabel("Tile size");
        zl.setFont(UITheme.font(Font.PLAIN, 12));
        zl.setForeground(UITheme.TEXT_TERTIARY);
        zoom.add(zl);
        ModernButton minus = ModernButton.createSecondary(null, VectorIconUtil.getIcon(IconType.MINUS, 12, UITheme.TEXT_SECONDARY)).compact();
        minus.setToolTipText("Smaller tiles");
        minus.getAccessibleContext().setAccessibleName("Zoom out");
        minus.addActionListener(e -> bayGrid.zoom(-0.15f));
        zoomPercentLabel = new JLabel("100%", SwingConstants.CENTER);
        zoomPercentLabel.setFont(UITheme.font(Font.BOLD, 12));
        zoomPercentLabel.setForeground(UITheme.TEXT_BODY);
        zoomPercentLabel.setPreferredSize(new Dimension(44, 28));
        ModernButton plus = ModernButton.createSecondary(null, VectorIconUtil.getIcon(IconType.PLUS, 12, UITheme.TEXT_SECONDARY)).compact();
        plus.setToolTipText("Larger tiles");
        plus.getAccessibleContext().setAccessibleName("Zoom in");
        plus.addActionListener(e -> bayGrid.zoom(0.15f));
        ModernButton reset = ModernButton.createGhost("Reset", null).compact();
        reset.addActionListener(e -> bayGrid.resetZoom());
        zoom.add(minus);
        zoom.add(zoomPercentLabel);
        zoom.add(plus);
        zoom.add(reset);
        bar.add(zoom, BorderLayout.EAST);
        return bar;
    }

    // =====================================================================================
    // Data
    // =====================================================================================

    /** Reloads the active level from SQLite and refreshes counters, grid and inspector. */
    public void renderFloorGrid() {
        List<ParkingSlot> floorSlots = slotDao.findByFloor(activeFloorNumber);
        if (floorSlots.isEmpty()) {
            Optional<ParkingFloor> floorOpt = parkingLot.getFloorByNumber(activeFloorNumber);
            if (floorOpt.isPresent()) floorSlots = floorOpt.get().getAllSlots();
        }

        // one query for all active tickets, keyed by slot
        Map<String, ParkingTicket> activeBySlot = new HashMap<>();
        Map<String, ParkingTicket> activeByPlate = new HashMap<>();
        try {
            for (ParkingTicket t : ticketDao.findAllActive()) {
                if (t.getSlotId() != null) activeBySlot.put(t.getSlotId().toUpperCase(), t);
                if (t.getLicensePlate() != null) activeByPlate.put(PlateUtil.normalize(t.getLicensePlate()), t);
            }
        } catch (Exception ignored) {
            // map still renders from slot states alone
        }

        List<Bay> bays = new ArrayList<>();
        for (ParkingSlot s : floorSlots) {
            Bay b = new Bay(s);
            if (b.state == BayState.OCCUPIED) {
                ParkingTicket t = activeBySlot.get(b.id.toUpperCase());
                if (t == null && b.plate != null) t = activeByPlate.get(PlateUtil.normalize(b.plate));
                b.attachTicket(t);
                if (b.ev && t != null) {
                    try {
                        if (chargingDao.findActiveByTicketId(t.getTicketId()).isPresent()) b.state = BayState.CHARGING;
                    } catch (Exception ignored) {
                    }
                }
            }
            bays.add(b);
        }
        bays.sort(Comparator.comparingInt(Bay::sortKey).thenComparing(b -> b.id));

        // counters
        Map<BayState, Integer> counts = new HashMap<>();
        for (Bay b : bays) counts.merge(b.state, 1, Integer::sum);
        int total = bays.size();
        statChips.get("AVAILABLE").set(counts.getOrDefault(BayState.AVAILABLE, 0), total);
        statChips.get("OCCUPIED").set(counts.getOrDefault(BayState.OCCUPIED, 0), total);
        statChips.get("RESERVED").set(counts.getOrDefault(BayState.RESERVED, 0), total);
        statChips.get("MAINTENANCE").set(counts.getOrDefault(BayState.MAINTENANCE, 0), total);
        statChips.get("CHARGING").set(counts.getOrDefault(BayState.CHARGING, 0), total);
        occupancyBar.set(counts, total);

        filterPills.get("ALL").setCount(total);
        filterPills.get("STANDARD").setCount((int) bays.stream().filter(b -> "STANDARD".equals(b.category)).count());
        filterPills.get("EV").setCount((int) bays.stream().filter(b -> "EV".equals(b.category)).count());
        filterPills.get("ACCESSIBLE").setCount((int) bays.stream().filter(b -> "ACCESSIBLE".equals(b.category)).count());

        int occupiedNow = counts.getOrDefault(BayState.OCCUPIED, 0) + counts.getOrDefault(BayState.CHARGING, 0);
        double pct = total > 0 ? occupiedNow * 100.0 / total : 0;
        header.setSubtitle(String.format("%s · %s  ·  %d bays · %.0f%% occupied  ·  updated %s",
                FLOOR_LABELS[activeFloorNumber - 1], FLOOR_NAMES[activeFloorNumber - 1], total, pct,
                LocalDateTime.now().format(TIME)));
        floorSwitch.setSelectedIndex(activeFloorNumber - 1, false);

        bayGrid.setBays(bays);
    }

    /** Switches to the level owning {@code slotId} and selects that bay. */
    public void highlightSlot(String slotId) {
        if (slotId == null || slotId.isBlank()) return;
        int targetFloor = activeFloorNumber;
        Optional<ParkingSlot> real = Optional.empty();
        try {
            real = slotDao.findById(slotId.trim());
        } catch (Exception ignored) {
        }
        if (real.isPresent()) {
            targetFloor = real.get().getFloorNumber();
        } else {
            String upper = slotId.toUpperCase();
            if (upper.startsWith("A") || upper.startsWith("F1")) targetFloor = 1;
            else if (upper.startsWith("B") || upper.startsWith("F2")) targetFloor = 2;
            else if (upper.startsWith("C") || upper.startsWith("F3")) targetFloor = 3;
        }
        if (activeFloorNumber != targetFloor) {
            activeFloorNumber = targetFloor;
        }
        renderFloorGrid();
        bayGrid.selectById(slotId.trim());
        bayGrid.requestFocusInWindow();
    }

    private static Color stateColor(String key) {
        switch (key) {
            case "AVAILABLE": return UITheme.SLOT_AVAILABLE;
            case "OCCUPIED": return UITheme.SLOT_OCCUPIED;
            case "RESERVED": return UITheme.SLOT_RESERVED;
            case "MAINTENANCE": return UITheme.SLOT_MAINTENANCE;
            case "CHARGING": return UITheme.SLOT_EV;
            default: return UITheme.ACCENT;
        }
    }

    // =====================================================================================
    // Model
    // =====================================================================================
    private enum BayState {
        AVAILABLE("Available"), OCCUPIED("Occupied"), RESERVED("Reserved"), MAINTENANCE("Maintenance"), CHARGING("EV charging");
        final String label;

        BayState(String label) {
            this.label = label;
        }

        Color color() {
            return stateColor(name());
        }
    }

    private static final class Bay {
        final String id;
        final SlotType type;
        final int floor;
        final double rate;
        BayState state;
        final boolean ev;
        final boolean accessible;
        final String category;
        String plate;
        VehicleType vehicleType;
        String brand;
        ParkingTicket ticket;
        Rectangle bounds = new Rectangle();

        Bay(ParkingSlot s) {
            id = s.getSlotId();
            type = s.getSlotType();
            floor = s.getFloorNumber();
            rate = s.getHourlyRate() > 0 ? s.getHourlyRate() : (type != null ? type.getDefaultHourlyRate() : 0);
            String st = s.getStatus() == null ? "" : s.getStatus().toUpperCase();
            if (st.equals("OCCUPIED")) state = BayState.OCCUPIED;
            else if (st.equals("RESERVED")) state = BayState.RESERVED;
            else if (st.equals("MAINTENANCE") || st.equals("OUT_OF_SERVICE")) state = BayState.MAINTENANCE;
            else state = BayState.AVAILABLE;
            ev = type == SlotType.ELECTRIC;
            accessible = type == SlotType.ACCESSIBLE || type == SlotType.HANDICAPPED;
            category = ev ? "EV" : accessible ? "ACCESSIBLE" : "STANDARD";
            if (state == BayState.OCCUPIED) {
                if (s.getCurrentVehiclePlate() != null && !s.getCurrentVehiclePlate().isBlank()) plate = s.getCurrentVehiclePlate();
                else if (s.getCurrentVehicle() != null) plate = s.getCurrentVehicle().getLicensePlate();
                if (s.getCurrentVehicle() != null) {
                    vehicleType = s.getCurrentVehicle().getType();
                    brand = s.getCurrentVehicle().getBrand();
                }
            }
        }

        void attachTicket(ParkingTicket t) {
            ticket = t;
            if (t != null) {
                if (vehicleType == null) vehicleType = t.getVehicleType();
                if (plate == null || plate.isBlank()) plate = t.getLicensePlate();
            }
        }

        boolean occupiedLike() {
            return state == BayState.OCCUPIED || state == BayState.CHARGING;
        }

        int sortKey() {
            String digits = id.replaceAll("[^0-9]", "");
            try {
                return digits.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }

        String typeLabel() {
            if (type == null) return "Bay";
            switch (type) {
                case COMPACT: return "Compact bay";
                case REGULAR: return "Standard bay";
                case LARGE: return "Large vehicle bay";
                case ELECTRIC: return "EV charging bay";
                default: return "Accessible bay";
            }
        }
    }

    private static IconType vehicleIcon(VehicleType vt) {
        if (vt == null) return IconType.CAR;
        switch (vt) {
            case MOTORCYCLE:
            case EV_TWO_WHEELER: return IconType.MOTORCYCLE;
            case AUTO_RICKSHAW: return IconType.AUTO_RICKSHAW;
            case LCV:
            case HCV: return IconType.TRUCK;
            default: return IconType.CAR;
        }
    }

    // =====================================================================================
    // Bay grid canvas
    // =====================================================================================
    private class BayGrid extends AccessibleComponent implements Scrollable {
        private static final int BASE_W = 92, BASE_H = 84, GAP = 10, LANE = 30, PAD = 18, ROWS_PER_AISLE = 2;
        private List<Bay> bays = new ArrayList<>();
        private Bay selected;
        private Bay hovered;
        private float zoom = 1f;
        private int cols = 10;
        private int offsetX = PAD;

        BayGrid() {
            setOpaque(false);
            setFocusable(true);
            setToolTipText("");
            accessibleName = "Parking bay grid";
            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    Bay b = bayAt(e.getPoint());
                    if (b != null) select(b);
                    if (b != null && e.getClickCount() == 2 && b.occupiedLike()) checkout(b);
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    Bay b = bayAt(e.getPoint());
                    if (b != hovered) {
                        hovered = b;
                        setCursor(Cursor.getPredefinedCursor(b != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = null;
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
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
            bindKey(KeyEvent.VK_RIGHT, 1);
            bindKey(KeyEvent.VK_LEFT, -1);
            bindKey(KeyEvent.VK_DOWN, Integer.MAX_VALUE);
            bindKey(KeyEvent.VK_UP, Integer.MIN_VALUE);
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "checkout");
            getActionMap().put("checkout", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    if (selected != null && selected.occupiedLike()) checkout(selected);
                }
            });
        }

        private void bindKey(int key, int delta) {
            String name = "move" + key;
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), name);
            getActionMap().put(name, new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    if (bays.isEmpty()) return;
                    int i = selected == null ? 0 : bays.indexOf(selected);
                    int d = delta == Integer.MAX_VALUE ? cols : delta == Integer.MIN_VALUE ? -cols : delta;
                    int n = Math.max(0, Math.min(bays.size() - 1, i + d));
                    select(bays.get(n));
                    scrollRectToVisible(bays.get(n).bounds);
                }
            });
        }

        void setBays(List<Bay> list) {
            String keep = selected != null ? selected.id : null;
            bays = list;
            selected = null;
            hovered = null;
            Bay pick = null;
            if (keep != null) pick = list.stream().filter(b -> b.id.equalsIgnoreCase(keep)).findFirst().orElse(null);
            if (pick == null) pick = list.stream().filter(Bay::occupiedLike).findFirst().orElse(list.isEmpty() ? null : list.get(0));
            selected = pick;
            inspector.show(pick);
            revalidate();
            repaint();
        }

        void selectById(String id) {
            bays.stream().filter(b -> b.id.equalsIgnoreCase(id)).findFirst().ifPresent(b -> {
                select(b);
                SwingUtilities.invokeLater(() -> scrollRectToVisible(b.bounds));
            });
        }

        private void select(Bay b) {
            selected = b;
            inspector.show(b);
            repaint();
        }

        void zoom(float delta) {
            zoom = Math.max(0.7f, Math.min(1.6f, zoom + delta));
            zoomPercentLabel.setText(Math.round(zoom * 100) + "%");
            revalidate();
            repaint();
        }

        void resetZoom() {
            zoom = 1f;
            zoomPercentLabel.setText("100%");
            revalidate();
            repaint();
        }

        private int tileW() {
            return Math.round(BASE_W * zoom);
        }

        private int tileH() {
            return Math.round(BASE_H * zoom);
        }

        private boolean matchesFilter(Bay b) {
            return "ALL".equals(activeFilter) || activeFilter.equals(b.category);
        }

        private Bay bayAt(Point p) {
            for (Bay b : bays) if (b.bounds.contains(p)) return b;
            return null;
        }

        /** Lays tiles out to fill the available width (responsive), grouping rows into aisles. */
        private void layoutTiles(int width) {
            int tw = tileW(), th = tileH();
            cols = Math.max(4, (width - PAD * 2 + GAP) / (tw + GAP));
            offsetX = Math.max(PAD, (width - (cols * (tw + GAP) - GAP)) / 2);
            for (int i = 0; i < bays.size(); i++) {
                int r = i / cols, c = i % cols;
                int aisle = r / ROWS_PER_AISLE;
                int x = offsetX + c * (tw + GAP);
                int y = PAD + r * (th + GAP) + aisle * (LANE - GAP);
                bays.get(i).bounds.setBounds(x, y, tw, th);
            }
        }

        private int contentHeight(int width) {
            int tw = tileW(), th = tileH();
            int c = Math.max(4, (width - PAD * 2 + GAP) / (tw + GAP));
            int rows = (bays.size() + c - 1) / c;
            int aisles = (rows + ROWS_PER_AISLE - 1) / ROWS_PER_AISLE;
            return PAD * 2 + rows * (th + GAP) + Math.max(0, aisles - 1) * (LANE - GAP);
        }

        @Override
        public Dimension getPreferredSize() {
            Container vp = getParent();
            int w = vp instanceof JViewport ? Math.max(vp.getWidth(), 4 * (tileW() + GAP) + PAD * 2) : 900;
            return new Dimension(w, contentHeight(w));
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            Bay b = bayAt(e.getPoint());
            if (b == null) return null;
            StringBuilder sb = new StringBuilder("<html><b>").append(b.id).append("</b> · ").append(b.state.label)
                    .append("<br>").append(b.typeLabel()).append(String.format(" · ₹%.0f/hr", b.rate));
            if (b.occupiedLike() && b.plate != null) sb.append("<br>").append(PlateUtil.format(b.plate));
            return sb.append("</html>").toString();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            layoutTiles(getWidth());

            if (bays.isEmpty()) {
                g2.setColor(UITheme.TEXT_SECONDARY);
                g2.setFont(UITheme.font(Font.PLAIN, 13));
                String s = "No bays are configured on this level.";
                g2.drawString(s, (getWidth() - g2.getFontMetrics().stringWidth(s)) / 2, 80);
                g2.dispose();
                return;
            }

            // drive lanes between aisles
            int th = tileH();
            int rows = (bays.size() + cols - 1) / cols;
            for (int r = ROWS_PER_AISLE; r < rows; r += ROWS_PER_AISLE) {
                Bay first = bays.get(r * cols);
                int y = first.bounds.y - LANE / 2 - 1;
                int x2 = offsetX + cols * (tileW() + GAP) - GAP;
                g2.setColor(UITheme.BORDER_SUBTLE);
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{10f, 8f}, 0f));
                g2.drawLine(offsetX, y, x2, y);
                g2.setStroke(new BasicStroke(1f));
                g2.setFont(UITheme.font(Font.BOLD, 9));
                String lane = "DRIVE LANE";
                int lw = g2.getFontMetrics().stringWidth(lane) + 16;
                int lx = offsetX + 14;
                g2.setColor(UITheme.BG_CARD);
                g2.fillRect(lx, y - 7, lw, 14);
                g2.setColor(UITheme.TEXT_DISABLED);
                g2.drawString(lane, lx + 8, y + 4);
            }

            for (Bay b : bays) paintTile(g2, b);
            g2.dispose();
        }

        private void paintTile(Graphics2D g, Bay b) {
            Graphics2D g2 = (Graphics2D) g.create();
            Rectangle r = b.bounds;
            boolean dim = !matchesFilter(b);
            if (dim) g2.setComposite(AlphaComposite.SrcOver.derive(0.22f));
            boolean hover = b == hovered;
            boolean sel = b == selected;
            Color c = b.state.color();
            float arc = 10;

            RoundRectangle2D shape = new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, arc, arc);
            Color fill = b.state == BayState.AVAILABLE ? UITheme.blend(c, UITheme.BG_CARD, hover ? 0.10 : 0.05)
                    : UITheme.blend(c, UITheme.BG_CARD, hover ? 0.24 : 0.16);
            g2.setColor(fill);
            g2.fill(shape);

            if (b.state == BayState.MAINTENANCE) {
                Shape old = g2.getClip();
                g2.clip(shape);
                g2.setColor(UITheme.alpha(UITheme.SLOT_MAINTENANCE, 40));
                g2.setStroke(new BasicStroke(6f));
                for (int k = -r.height; k < r.width; k += 14) g2.drawLine(r.x + k, r.y + r.height, r.x + k + r.height, r.y);
                g2.setClip(old);
            }

            // status bar along the top edge
            Shape old = g2.getClip();
            g2.clip(shape);
            g2.setColor(c);
            g2.fillRect(r.x, r.y, r.width, 3);
            g2.setClip(old);

            g2.setStroke(new BasicStroke(1f));
            g2.setColor(hover ? UITheme.blend(c, UITheme.BG_CARD, 0.75) : UITheme.blend(c, UITheme.BG_CARD, b.state == BayState.AVAILABLE ? 0.30 : 0.45));
            g2.draw(new RoundRectangle2D.Float(r.x + 0.5f, r.y + 0.5f, r.width - 1, r.height - 1, arc, arc));

            if (sel) {
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(isFocusOwner() ? UITheme.ACCENT_TEXT : UITheme.ACCENT);
                g2.draw(new RoundRectangle2D.Float(r.x - 3, r.y - 3, r.width + 6, r.height + 6, arc + 4, arc + 4));
            }

            float z = zoom;
            int pad = Math.round(9 * z);
            // id
            g2.setFont(UITheme.font(Font.BOLD, Math.round(12 * z)));
            g2.setColor(UITheme.TEXT_PRIMARY);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(b.id, r.x + pad, r.y + pad + fm.getAscent());

            // bay-type marker (top right)
            int ms = Math.round(14 * z);
            if (b.ev) {
                VectorIconUtil.getIcon(IconType.LIGHTNING, ms, UITheme.SLOT_EV_TEXT).paintIcon(this, g2, r.x + r.width - pad - ms, r.y + pad);
            } else if (b.accessible) {
                VectorIconUtil.getIcon(IconType.WHEELCHAIR, ms, UITheme.SLOT_ACCESSIBLE).paintIcon(this, g2, r.x + r.width - pad - ms, r.y + pad);
            }

            // centre / bottom content
            int bottomY = r.y + r.height - pad;
            Color label = Badge.textFor(c);
            g2.setFont(UITheme.font(Font.BOLD, Math.round(10 * z)));
            FontMetrics sm = g2.getFontMetrics();
            switch (b.state) {
                case OCCUPIED:
                case CHARGING: {
                    int is = Math.round(22 * z);
                    VectorIconUtil.getIcon(vehicleIcon(b.vehicleType), is, label).paintIcon(this, g2, r.x + pad - 1, bottomY - sm.getAscent() - is - Math.round(5 * z));
                    String p = b.plate == null || b.plate.isBlank() ? "Unknown" : PlateUtil.format(b.plate);
                    g2.setFont(UITheme.mono(Font.BOLD, Math.round(10 * z)));
                    g2.setColor(UITheme.TEXT_BODY);
                    g2.drawString(clip(p, g2.getFontMetrics(), r.width - pad * 2), r.x + pad, bottomY);
                    if (b.state == BayState.CHARGING) {
                        int bs = Math.round(12 * z);
                        VectorIconUtil.getIcon(IconType.PLUG, bs, UITheme.SLOT_EV_TEXT).paintIcon(this, g2, r.x + r.width - pad - bs, bottomY - sm.getAscent() - is);
                    }
                    break;
                }
                case RESERVED: {
                    int is = Math.round(16 * z);
                    VectorIconUtil.getIcon(IconType.CLOCK, is, label).paintIcon(this, g2, r.x + pad, bottomY - sm.getAscent() - is - Math.round(6 * z));
                    g2.setColor(label);
                    g2.drawString("Reserved", r.x + pad, bottomY);
                    break;
                }
                case MAINTENANCE: {
                    int is = Math.round(16 * z);
                    VectorIconUtil.getIcon(IconType.WRENCH, is, UITheme.TEXT_SECONDARY).paintIcon(this, g2, r.x + pad, bottomY - sm.getAscent() - is - Math.round(6 * z));
                    g2.setColor(UITheme.TEXT_SECONDARY);
                    g2.drawString("Offline", r.x + pad, bottomY);
                    break;
                }
                default: {
                    g2.setColor(UITheme.alpha(label, 200));
                    g2.drawString("Free", r.x + pad, bottomY);
                    g2.setFont(UITheme.font(Font.PLAIN, Math.round(10 * z)));
                    g2.setColor(UITheme.TEXT_TERTIARY);
                    String rate = String.format("₹%.0f/h", b.rate);
                    g2.drawString(rate, r.x + r.width - pad - g2.getFontMetrics().stringWidth(rate), bottomY);
                }
            }
            g2.dispose();
        }

        // Scrollable: track viewport width so the grid reflows instead of scrolling sideways
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height - 40;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            Container vp = getParent();
            return vp instanceof JViewport && vp.getWidth() >= 4 * (tileW() + GAP) + PAD * 2;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            Container vp = getParent();
            return vp instanceof JViewport && vp.getHeight() > contentHeight(vp.getWidth());
        }
    }

    private static String clip(String s, FontMetrics fm, int max) {
        if (s == null) return "";
        if (fm.stringWidth(s) <= max) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private void checkout(Bay b) {
        if (navigationHandler == null || b == null) return;
        String q = b.ticket != null ? b.ticket.getTicketNumber() : b.plate;
        navigationHandler.accept(q != null && !q.isBlank() ? "EXIT:" + q : "EXIT");
    }

    // =====================================================================================
    // Bay inspector
    // =====================================================================================
    private class BayInspector extends JPanel implements Scrollable {
        private final JLabel idLabel = new JLabel("—");
        private final Badge statusBadge = new Badge("—", UITheme.TEXT_TERTIARY);
        private final JLabel metaLabel = new JLabel(" ");
        private final JPanel body = new JPanel();
        private final ModernButton btnCheckout;
        private final ModernButton btnDirections;
        private final ModernButton btnIncident;
        private final TypeBreakdown breakdown = new TypeBreakdown();
        private Bay current;

        BayInspector() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            RoundedPanel card = new RoundedPanel(UITheme.RADIUS_LG, UITheme.BG_CARD, UITheme.BORDER_SUBTLE);
            card.setLayout(new BorderLayout(0, 16));
            card.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
            card.setAlignmentX(LEFT_ALIGNMENT);

            JPanel head = new JPanel();
            head.setOpaque(false);
            head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
            JLabel cap = new JLabel("BAY INSPECTOR");
            cap.setFont(UITheme.FONT_CAPTION);
            cap.setForeground(UITheme.TEXT_TERTIARY);
            cap.setAlignmentX(LEFT_ALIGNMENT);
            head.add(cap);
            head.add(Box.createVerticalStrut(8));
            JPanel idRow = new JPanel(new BorderLayout(10, 0));
            idRow.setOpaque(false);
            idRow.setAlignmentX(LEFT_ALIGNMENT);
            idLabel.setFont(UITheme.font(Font.BOLD, 24));
            idLabel.setForeground(UITheme.TEXT_PRIMARY);
            idRow.add(idLabel, BorderLayout.WEST);
            JPanel badgeWrap = new JPanel(new GridBagLayout());
            badgeWrap.setOpaque(false);
            badgeWrap.add(statusBadge);
            idRow.add(badgeWrap, BorderLayout.EAST);
            head.add(idRow);
            head.add(Box.createVerticalStrut(4));
            metaLabel.setFont(UITheme.font(Font.PLAIN, 12));
            metaLabel.setForeground(UITheme.TEXT_SECONDARY);
            metaLabel.setAlignmentX(LEFT_ALIGNMENT);
            head.add(metaLabel);
            card.add(head, BorderLayout.NORTH);

            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            card.add(body, BorderLayout.CENTER);

            JPanel actions = new JPanel(new GridLayout(0, 1, 0, 8));
            actions.setOpaque(false);
            btnCheckout = new ModernButton("Check out & bill", VectorIconUtil.getIcon(IconType.ARROW_RIGHT, 14, Color.WHITE));
            btnCheckout.setHorizontalTextPosition(SwingConstants.LEFT);
            btnCheckout.setToolTipText("Open Checkout with this vehicle's ticket (Enter / double-click)");
            btnCheckout.addActionListener(e -> checkout(current));
            btnDirections = ModernButton.createSecondary("Send directions (simulated)",
                    VectorIconUtil.getIcon(IconType.PHONE, 14, UITheme.TEXT_SECONDARY));
            btnDirections.addActionListener(e -> sendDirections());
            btnIncident = ModernButton.createGhost("Log incident (simulated)",
                    VectorIconUtil.getIcon(IconType.ALERT, 14, UITheme.TEXT_SECONDARY));
            btnIncident.addActionListener(e -> {
                if (current == null) return;
                int ok = JOptionPane.showConfirmDialog(FloorMapPanel.this,
                        "File a security incident slip for bay " + current.id + "?\nThis is a simulated action; nothing is sent.",
                        "Log incident", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (ok == JOptionPane.OK_OPTION) Toast.success(FloorMapPanel.this, "Incident logged (simulated)", "Bay " + current.id);
            });
            actions.add(btnCheckout);
            actions.add(btnDirections);
            actions.add(btnIncident);
            card.add(actions, BorderLayout.SOUTH);

            add(card);
            add(Box.createVerticalStrut(16));
            breakdown.setAlignmentX(LEFT_ALIGNMENT);
            add(breakdown);
        }

        void show(Bay b) {
            current = b;
            body.removeAll();
            if (b == null) {
                idLabel.setText("—");
                statusBadge.set("No bay", UITheme.TEXT_TERTIARY);
                metaLabel.setText("Select a bay on the map");
                btnCheckout.setEnabled(false);
                btnDirections.setEnabled(false);
                btnIncident.setEnabled(false);
                revalidate();
                repaint();
                return;
            }
            idLabel.setText(b.id);
            statusBadge.set(b.state.label, b.state.color());
            metaLabel.setText(String.format("Level %d · %s · ₹%.0f/hr", b.floor, b.typeLabel(), b.rate));
            btnDirections.setEnabled(true);
            btnIncident.setEnabled(true);
            btnCheckout.setEnabled(b.occupiedLike());

            switch (b.state) {
                case OCCUPIED:
                case CHARGING: {
                    body.add(plateChip(b));
                    body.add(Box.createVerticalStrut(14));
                    body.add(divider());
                    body.add(Box.createVerticalStrut(10));
                    body.add(row("Vehicle", b.vehicleType != null ? b.vehicleType.getDisplayName() : "—"));
                    if (b.ticket != null && b.ticket.getEntryTime() != null) {
                        LocalDateTime entry = b.ticket.getEntryTime();
                        long mins = Math.max(0, Duration.between(entry, LocalDateTime.now()).toMinutes());
                        body.add(row("Ticket", b.ticket.getTicketNumber()));
                        body.add(row("Entered", entry.format(ENTRY)));
                        body.add(row("Parked for", (mins / 60) + "h " + (mins % 60) + "m"));
                        double fee;
                        try {
                            fee = tariffService.calculateFee(b.ticket, LocalDateTime.now(), false, null).getTotalAmount();
                        } catch (Exception e) {
                            fee = -1;
                        }
                        body.add(Box.createVerticalStrut(10));
                        body.add(divider());
                        body.add(Box.createVerticalStrut(10));
                        body.add(feeRow(fee >= 0 ? String.format("₹%,.2f", fee) : "—"));
                    } else {
                        body.add(row("Ticket", "No active ticket found"));
                        body.add(note("The bay is marked occupied but has no open ticket. Check it at the exit gate."));
                    }
                    if (b.state == BayState.CHARGING) {
                        body.add(Box.createVerticalStrut(8));
                        body.add(note("An EV charging session is active. It must be stopped before checkout."));
                    }
                    break;
                }
                case RESERVED:
                    body.add(note("Held for an advance booking. The bay is released automatically if the driver doesn't arrive within the grace period."));
                    break;
                case MAINTENANCE:
                    body.add(note("Out of service. Vehicles will not be allocated to this bay until it is restored."));
                    break;
                default:
                    body.add(note(b.ev ? "Free. Reserved for electric vehicles; charging is billed separately."
                            : b.accessible ? "Free. Requires a valid disability (Divyang) parking permit."
                            : "Free and ready for the next vehicle."));
            }
            body.add(Box.createVerticalGlue());
            breakdown.refresh();
            revalidate();
            repaint();
        }

        private JComponent plateChip(Bay b) {
            String plate = b.plate == null || b.plate.isBlank() ? "Unknown plate" : PlateUtil.format(b.plate);
            String district = b.plate == null || b.plate.isBlank() ? "" : PlateUtil.district(b.plate);
            JPanel wrap = new JPanel(new BorderLayout(12, 0));
            wrap.setOpaque(false);
            wrap.setAlignmentX(LEFT_ALIGNMENT);
            JLabel icon = new JLabel(VectorIconUtil.getIcon(vehicleIcon(b.vehicleType), 22, Badge.textFor(b.state.color())));
            icon.setPreferredSize(new Dimension(40, 40));
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            icon.setOpaque(true);
            icon.setBackground(UITheme.tint(b.state.color()));
            icon.setBorder(new DarkTheme.RoundBorder(UITheme.tintBorder(b.state.color()), UITheme.RADIUS));
            wrap.add(icon, BorderLayout.WEST);
            JPanel txt = new JPanel();
            txt.setOpaque(false);
            txt.setLayout(new BoxLayout(txt, BoxLayout.Y_AXIS));
            JLabel p = new JLabel(plate);
            p.setFont(UITheme.mono(Font.BOLD, 16));
            p.setForeground(UITheme.TEXT_PRIMARY);
            JLabel d = new JLabel(district.isEmpty() ? (b.brand != null ? b.brand : " ") : district + (b.brand != null && !b.brand.isBlank() ? " · " + b.brand : ""));
            d.setFont(UITheme.font(Font.PLAIN, 12));
            d.setForeground(UITheme.TEXT_SECONDARY);
            txt.add(p);
            txt.add(Box.createVerticalStrut(2));
            txt.add(d);
            wrap.add(txt, BorderLayout.CENTER);
            wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            return wrap;
        }

        private JComponent row(String k, String v) {
            JPanel r = new JPanel(new BorderLayout(12, 0));
            r.setOpaque(false);
            r.setAlignmentX(LEFT_ALIGNMENT);
            r.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
            JLabel kl = new JLabel(k);
            kl.setFont(UITheme.font(Font.PLAIN, 13));
            kl.setForeground(UITheme.TEXT_SECONDARY);
            JLabel vl = new JLabel(v);
            vl.setFont(UITheme.font(Font.BOLD, 13));
            vl.setForeground(UITheme.TEXT_PRIMARY);
            vl.setHorizontalAlignment(SwingConstants.RIGHT);
            r.add(kl, BorderLayout.WEST);
            r.add(vl, BorderLayout.EAST);
            r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            return r;
        }

        private JComponent feeRow(String v) {
            JPanel r = new JPanel(new BorderLayout(12, 0));
            r.setOpaque(false);
            r.setAlignmentX(LEFT_ALIGNMENT);
            JLabel kl = new JLabel("Accrued fee");
            kl.setFont(UITheme.font(Font.PLAIN, 13));
            kl.setForeground(UITheme.TEXT_SECONDARY);
            JLabel vl = new JLabel(v);
            vl.setFont(UITheme.font(Font.BOLD, 20));
            vl.setForeground(UITheme.TEXT_PRIMARY);
            r.add(kl, BorderLayout.WEST);
            r.add(vl, BorderLayout.EAST);
            r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            return r;
        }

        private JComponent note(String text) {
            JLabel l = new JLabel("<html><div style='width:270px'>" + text + "</div></html>");
            l.setFont(UITheme.font(Font.PLAIN, 13));
            l.setForeground(UITheme.TEXT_SECONDARY);
            l.setAlignmentX(LEFT_ALIGNMENT);
            l.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
            return l;
        }

        private JComponent divider() {
            JPanel line = new JPanel();
            line.setBackground(UITheme.BORDER_SUBTLE);
            line.setAlignmentX(LEFT_ALIGNMENT);
            line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            line.setPreferredSize(new Dimension(10, 1));
            return line;
        }

        private void sendDirections() {
            if (current == null) return;
            String input = (String) JOptionPane.showInputDialog(FloorMapPanel.this,
                    "Mobile number (10 digits) to receive directions to bay " + current.id + ":",
                    "Send directions (simulated)", JOptionPane.PLAIN_MESSAGE, null, null, "");
            if (input == null) return;
            String clean = input.trim().replaceAll("[^0-9]", "");
            if (clean.matches("^[6-9][0-9]{9}$")) {
                Toast.success(FloorMapPanel.this, "Directions sent (simulated)", "Route to " + current.id + " sent to +91 " + clean);
            } else {
                Toast.error(FloorMapPanel.this, "Invalid mobile number", "Enter a 10-digit Indian mobile number starting with 6-9.");
            }
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return r.height - 40;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** Occupancy by bay type for the active level (real counts). */
    private class TypeBreakdown extends RoundedPanel {
        private final JPanel rows = new JPanel();

        TypeBreakdown() {
            super(UITheme.RADIUS_LG, UITheme.BG_CARD, UITheme.BORDER_SUBTLE);
            setLayout(new BorderLayout(0, 12));
            setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
            JLabel t = PageHeader.cardTitle("By bay type");
            add(t, BorderLayout.NORTH);
            rows.setOpaque(false);
            rows.setLayout(new GridLayout(0, 1, 0, 10));
            add(rows, BorderLayout.CENTER);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        }

        void refresh() {
            rows.removeAll();
            List<Bay> list = bayGrid == null ? List.of() : bayGrid.bays;
            String[][] cats = {{"STANDARD", "Standard"}, {"EV", "EV charging"}, {"ACCESSIBLE", "Accessible"}};
            for (String[] c : cats) {
                int total = 0, used = 0;
                for (Bay b : list) {
                    if (!c[0].equals(b.category)) continue;
                    total++;
                    if (b.state != BayState.AVAILABLE) used++;
                }
                rows.add(new TypeRow(c[1], used, total, c[0].equals("EV") ? UITheme.SLOT_EV : c[0].equals("ACCESSIBLE") ? UITheme.SLOT_ACCESSIBLE : UITheme.ACCENT));
            }
            revalidate();
            repaint();
        }
    }

    private static class TypeRow extends JComponent {
        private final String label;
        private final int used, total;
        private final Color color;

        TypeRow(String label, int used, int total, Color color) {
            this.label = label;
            this.used = used;
            this.total = total;
            this.color = color;
            setPreferredSize(new Dimension(200, 34));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(UITheme.font(Font.PLAIN, 12));
            g2.setColor(UITheme.TEXT_BODY);
            g2.drawString(label, 0, 13);
            String v = total == 0 ? "none" : used + " / " + total + " in use";
            g2.setColor(UITheme.TEXT_SECONDARY);
            g2.drawString(v, getWidth() - g2.getFontMetrics().stringWidth(v), 13);
            int y = 22, h = 6, w = getWidth();
            g2.setColor(UITheme.BG_ELEVATED);
            g2.fillRoundRect(0, y, w, h, h, h);
            if (total > 0 && used > 0) {
                g2.setColor(color);
                g2.fillRoundRect(0, y, Math.max(h, (int) Math.round(w * used / (double) total)), h, h, h);
            }
            g2.dispose();
        }
    }

    // =====================================================================================
    // Small components
    // =====================================================================================

    /** JComponent with a real AccessibleContext so screen readers can name custom-painted controls. */
    private abstract static class AccessibleComponent extends JComponent {
        String accessibleName = "";

        @Override
        public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleJComponent() {
                    @Override
                    public javax.accessibility.AccessibleRole getAccessibleRole() {
                        return javax.accessibility.AccessibleRole.PUSH_BUTTON;
                    }

                    @Override
                    public String getAccessibleName() {
                        return accessibleName;
                    }
                };
            }
            return accessibleContext;
        }
    }
    private static class StatChip extends AccessibleComponent {
        private final String label;
        private final Color color;
        private int value;
        private int total;

        StatChip(String label, Color color) {
            this.label = label;
            this.color = color;
            setPreferredSize(new Dimension(150, 46));
        }

        void set(int value, int total) {
            this.value = value;
            this.total = total;
            accessibleName = label + ": " + value;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRoundRect(0, 5, 8, 8, 3, 3);
            g2.setFont(UITheme.font(Font.PLAIN, 12));
            g2.setColor(UITheme.TEXT_SECONDARY);
            g2.drawString(label, 16, 14);
            g2.setFont(UITheme.font(Font.BOLD, 22));
            g2.setColor(UITheme.TEXT_PRIMARY);
            String v = String.valueOf(value);
            g2.drawString(v, 0, 42);
            int vw = g2.getFontMetrics().stringWidth(v);
            if (total > 0) {
                g2.setFont(UITheme.font(Font.PLAIN, 12));
                g2.setColor(UITheme.TEXT_TERTIARY);
                g2.drawString(String.format("%.0f%%", value * 100.0 / total), vw + 8, 41);
            }
            g2.dispose();
        }
    }

    /** Stacked bar of bay states for the active level. */
    private static class OccupancyBar extends JComponent {
        private Map<BayState, Integer> counts = new HashMap<>();
        private int total;

        OccupancyBar() {
            setPreferredSize(new Dimension(100, 6));
        }

        void set(Map<BayState, Integer> counts, int total) {
            this.counts = counts;
            this.total = total;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            Shape clip = new RoundRectangle2D.Float(0, 0, w, h, h, h);
            g2.setColor(UITheme.BG_ELEVATED);
            g2.fill(clip);
            g2.clip(clip);
            if (total > 0) {
                double x = 0;
                BayState[] order = {BayState.OCCUPIED, BayState.CHARGING, BayState.RESERVED, BayState.MAINTENANCE, BayState.AVAILABLE};
                for (BayState s : order) {
                    int n = counts.getOrDefault(s, 0);
                    if (n == 0) continue;
                    double seg = w * n / (double) total;
                    g2.setColor(s == BayState.AVAILABLE ? UITheme.blend(s.color(), UITheme.BG_CARD, 0.35) : s.color());
                    g2.fillRect((int) Math.round(x), 0, (int) Math.ceil(seg), h);
                    x += seg;
                }
            }
            g2.dispose();
        }
    }

    /** Rounded filter pill with a count; selected pill uses the accent tint. */
    private class FilterPill extends AccessibleComponent {
        private final String key;
        private final String text;
        private int count;
        private boolean hover;

        FilterPill(String key, String text) {
            this.key = key;
            this.text = text;
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            accessibleName = "Filter: " + text;
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    activate();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "act");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "act");
            getActionMap().put("act", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    activate();
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

        private void activate() {
            activeFilter = key;
            for (FilterPill p : filterPills.values()) p.repaint();
            bayGrid.repaint();
        }

        void setCount(int c) {
            count = c;
            revalidate();
            repaint();
        }

        private String full() {
            return text + "  " + count;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(UITheme.font(Font.BOLD, 12));
            return new Dimension(fm.stringWidth(full()) + 26, 30);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean active = key.equals(activeFilter);
            int w = getWidth(), h = getHeight();
            g2.setColor(active ? UITheme.ACCENT_SOFT : hover ? UITheme.BG_HOVER : UITheme.BG_CARD);
            g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
            g2.setColor(active ? UITheme.ACCENT_BORDER : UITheme.BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
            if (isFocusOwner() && DarkTheme.isFocusVisible()) {
                g2.setColor(UITheme.ACCENT_TEXT);
                g2.drawRoundRect(1, 1, w - 3, h - 3, h - 2, h - 2);
            }
            g2.setFont(UITheme.font(Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int y = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(active ? UITheme.TEXT_PRIMARY : UITheme.TEXT_BODY);
            g2.drawString(text, 13, y);
            g2.setFont(UITheme.font(Font.PLAIN, 12));
            g2.setColor(active ? UITheme.ACCENT_TEXT : UITheme.TEXT_TERTIARY);
            g2.drawString(String.valueOf(count), 13 + fm.stringWidth(text + "  "), y);
            g2.dispose();
        }
    }
}
