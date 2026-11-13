package com.smartparking.ui.panels;

import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.reservation.Reservation;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.repository.ParkingSlotDao;
import com.smartparking.repository.PaymentDao;
import com.smartparking.repository.ReservationDao;
import com.smartparking.repository.TicketDao;
import com.smartparking.service.ParkingService;
import com.smartparking.ui.components.*;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.VectorIconUtil;
import com.smartparking.ui.util.VectorIconUtil.IconType;
import com.smartparking.util.DateFormat;
import com.smartparking.util.MoneyFormat;
import com.smartparking.util.PlateUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Overview dashboard. Every number on this screen comes from a live SQLite query:
 * <ul>
 *   <li>KPI row — slot status counts, occupancy, today's revenue (vs. yesterday), today's entries/exits</li>
 *   <li>Vehicle turnover — entries (bars) and exits (line) per day/week/month from ticket timestamps</li>
 *   <li>Occupancy by level — per-floor stacked bars from slot statuses</li>
 *   <li>Revenue — completed payments for each of the last 7 days (vs. previous 7)</li>
 *   <li>Vehicle mix — vehicle types of currently active tickets</li>
 *   <li>Alerts — overstays (&gt;4h), confirmed reservations, bays under maintenance</li>
 *   <li>Recent activity — the 8 most recent tickets</li>
 * </ul>
 * When a dataset is empty the screen shows an explicit empty state rather than sample data.
 */
public class DashboardPanel extends JPanel {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int OVERSTAY_HOURS = 4;

    private final ParkingLot parkingLot;
    private final ParkingService parkingService;
    private final ParkingSlotDao slotDao;
    private final TicketDao ticketDao;
    private final PaymentDao paymentDao;
    private final ReservationDao reservationDao;
    private final Consumer<String> navigationHandler;

    // KPI cards
    private MetricCard availableCard;
    private MetricCard occupiedCard;
    private MetricCard reservedCard;
    private MetricCard occupancyCard;
    private MetricCard revenueCard;
    private MetricCard trafficCard;

    private PageHeader pageHeader;
    private TurnoverChart turnoverChart;
    private LevelOccupancy levelOccupancy;
    private RevenueChart revenueChart;
    private DonutChart donutChart;
    private JPanel alertsList;
    private JLabel alertsCount;
    private DefaultTableModel activityTableModel;
    private JPanel activityBody;
    private JScrollPane activityScroll;
    private EmptyState activityEmpty;

    public DashboardPanel(ParkingLot parkingLot, ParkingService parkingService, Consumer<String> navigationHandler) {
        this.parkingLot = parkingLot;
        this.parkingService = parkingService;
        this.slotDao = new ParkingSlotDao();
        this.ticketDao = new TicketDao();
        this.paymentDao = new PaymentDao();
        this.reservationDao = new ReservationDao();
        this.navigationHandler = navigationHandler;

        setLayout(new BorderLayout());
        setBackground(UITheme.BG_CANVAS);

        initUI();
        refreshData();
    }

    // =====================================================================================
    // Layout
    // =====================================================================================
    private void initUI() {
        JPanel content = new ScrollablePanel(new GridBagLayout());
        content.setBackground(UITheme.BG_CANVAS);
        content.setBorder(new EmptyBorder(24, 28, 28, 28));
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.weightx = 1;
        g.fill = GridBagConstraints.BOTH;

        // Header + quick actions
        pageHeader = new PageHeader("Overview", "Live status across all levels");
        ModernButton refresh = ModernButton.createGhost("Refresh", VectorIconUtil.getIcon(IconType.REFRESH, 14, UITheme.TEXT_SECONDARY));
        refresh.setToolTipText("Reload all figures from the database");
        refresh.addActionListener(e -> refreshData());
        ModernButton reserve = ModernButton.createSecondary("Reserve bay", VectorIconUtil.getIcon(IconType.CALENDAR, 14, UITheme.TEXT_SECONDARY));
        reserve.addActionListener(e -> navigationHandler.accept("RESERVATIONS"));
        ModernButton checkout = ModernButton.createSecondary("Checkout", VectorIconUtil.getIcon(IconType.PAYMENT, 14, UITheme.TEXT_SECONDARY));
        checkout.addActionListener(e -> navigationHandler.accept("EXIT"));
        ModernButton entry = new ModernButton("New entry", VectorIconUtil.getIcon(IconType.PLUS, 14, Color.WHITE));
        entry.addActionListener(e -> navigationHandler.accept("ENTRY"));
        pageHeader.addAction(refresh).addAction(reserve).addAction(checkout).addAction(entry);
        g.gridy = 0;
        content.add(pageHeader, g);

        // KPI row
        JPanel kpis = new JPanel(new GridLayout(1, 6, 12, 0));
        kpis.setOpaque(false);
        availableCard = new MetricCard("Available", "—", icon(IconType.CHECK, UITheme.SUCCESS_TEXT), UITheme.SUCCESS, "");
        occupiedCard = new MetricCard("Occupied", "—", icon(IconType.CAR, UITheme.ERROR_TEXT), UITheme.ERROR, "");
        reservedCard = new MetricCard("Reserved", "—", icon(IconType.CALENDAR, UITheme.WARNING_TEXT), UITheme.WARNING, "");
        occupancyCard = new MetricCard("Occupancy", "—", icon(IconType.GRID, UITheme.ACCENT_TEXT), UITheme.ACCENT, "");
        revenueCard = new MetricCard("Revenue today", "—", icon(IconType.PAYMENT, UITheme.SUCCESS_TEXT), UITheme.SUCCESS, "");
        trafficCard = new MetricCard("Traffic today", "—", icon(IconType.TICKET, UITheme.SLOT_EV_TEXT), UITheme.SLOT_EV, "");
        kpis.add(availableCard);
        kpis.add(occupiedCard);
        kpis.add(reservedCard);
        kpis.add(occupancyCard);
        kpis.add(revenueCard);
        kpis.add(trafficCard);
        g.gridy = 1;
        g.insets = new Insets(0, 0, 16, 0);
        content.add(kpis, g);

        // Row: turnover | occupancy by level
        JPanel row2 = new JPanel(new GridBagLayout());
        row2.setOpaque(false);
        turnoverChart = new TurnoverChart();
        levelOccupancy = new LevelOccupancy();
        addWeighted(row2, cardWith("Vehicle turnover", "Entries and exits recorded at the gates", turnoverChart, turnoverChart.rangeControl()), 0, 2.0, 16);
        addWeighted(row2, cardWith("Occupancy by level", "Live bay status per floor", levelOccupancy, null), 1, 1.0, 0);
        row2.setPreferredSize(new Dimension(10, 318));
        g.gridy = 2;
        content.add(row2, g);

        // Row: revenue | vehicle mix | alerts
        JPanel row3 = new JPanel(new GridBagLayout());
        row3.setOpaque(false);
        revenueChart = new RevenueChart();
        donutChart = new DonutChart();
        addWeighted(row3, cardWith("Revenue", "Completed payments, last 7 days", revenueChart, null), 0, 1.25, 16);
        addWeighted(row3, cardWith("Vehicle mix", "Vehicles currently parked", donutChart, null), 1, 1.0, 16);
        addWeighted(row3, buildAlertsCard(), 2, 1.0, 0);
        row3.setPreferredSize(new Dimension(10, 300));
        g.gridy = 3;
        content.add(row3, g);

        // Row: recent activity
        g.gridy = 4;
        g.insets = new Insets(0, 0, 0, 0);
        content.add(buildActivityCard(), g);

        // filler
        g.gridy = 5;
        g.weighty = 1;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        content.add(filler, g);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(UITheme.BG_CANVAS);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
    }

    private static Icon icon(IconType t, Color c) {
        return VectorIconUtil.getIcon(t, 15, c);
    }

    private static void addWeighted(JPanel row, JComponent c, int x, double weight, int gap) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = x;
        g.gridy = 0;
        g.weightx = weight;
        g.weighty = 1;
        g.fill = GridBagConstraints.BOTH;
        g.insets = new Insets(0, 0, 16, gap);
        row.add(c, g);
    }

    /** Card with title, caption, optional header control and body. */
    private static RoundedPanel cardWith(String title, String caption, JComponent body, JComponent headerControl) {
        RoundedPanel card = RoundedPanel.card(18);
        card.setLayout(new BorderLayout(0, 14));
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(PageHeader.cardTitle(title));
        titles.add(Box.createVerticalStrut(3));
        titles.add(PageHeader.caption(caption));
        header.add(titles, BorderLayout.WEST);
        if (headerControl != null) {
            JPanel w = new JPanel(new GridBagLayout());
            w.setOpaque(false);
            w.add(headerControl);
            header.add(w, BorderLayout.EAST);
        }
        card.add(header, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private RoundedPanel buildAlertsCard() {
        RoundedPanel card = RoundedPanel.card(18);
        card.setLayout(new BorderLayout(0, 12));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(PageHeader.cardTitle("Operational alerts"));
        titles.add(Box.createVerticalStrut(3));
        titles.add(PageHeader.caption("Click an alert to locate the bay"));
        header.add(titles, BorderLayout.WEST);
        alertsCount = new JLabel();
        alertsCount.setFont(UITheme.font(Font.BOLD, 11));
        alertsCount.setForeground(UITheme.TEXT_SECONDARY);
        JPanel w = new JPanel(new GridBagLayout());
        w.setOpaque(false);
        w.add(alertsCount);
        header.add(w, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        alertsList = new JPanel();
        alertsList.setOpaque(false);
        alertsList.setLayout(new BoxLayout(alertsList, BoxLayout.Y_AXIS));
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(alertsList, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    private RoundedPanel buildActivityCard() {
        String[] columns = {"Time", "Vehicle", "Event", "Bay", "Type", "Fee"};
        activityTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        JTable table = new JTable(activityTableModel);
        com.smartparking.ui.theme.DarkTheme.styleTable(table);
        table.getColumnModel().getColumn(2).setCellRenderer(new Badge.Renderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(170);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(60);
        table.getColumnModel().getColumn(4).setPreferredWidth(110);
        table.getColumnModel().getColumn(5).setPreferredWidth(90);
        DarkThemeRight right = new DarkThemeRight();
        table.getColumnModel().getColumn(5).setCellRenderer(right);
        table.getColumnModel().getColumn(1).setCellRenderer(new MonoRenderer());

        activityScroll = new JScrollPane(table);
        activityScroll.setBorder(new com.smartparking.ui.theme.DarkTheme.RoundBorder(UITheme.BORDER_SUBTLE, UITheme.RADIUS));
        activityEmpty = new EmptyState(IconType.TICKET, "No parking activity yet",
                "Tickets issued at the entry gate will appear here as soon as the first vehicle arrives.");

        activityBody = new JPanel(new CardLayout());
        activityBody.setOpaque(false);
        activityBody.add(activityScroll, "TABLE");
        activityBody.add(activityEmpty, "EMPTY");
        activityBody.setPreferredSize(new Dimension(10, 8 * 34 + 40));

        JButton viewAll = ModernButton.createGhost("Find a vehicle", VectorIconUtil.getIcon(IconType.ARROW_RIGHT, 13, UITheme.TEXT_SECONDARY));
        viewAll.setHorizontalTextPosition(SwingConstants.LEFT);
        viewAll.addActionListener(e -> navigationHandler.accept("SEARCH"));
        return cardWith("Recent activity", "Latest tickets issued and closed", activityBody, viewAll);
    }

    // =====================================================================================
    // Data
    // =====================================================================================
    /**
     * Refreshes all metrics, charts, alerts and the activity table from live SQLite records.
     */
    public void refreshData() {
        LocalDate today = LocalDate.now(IST);

        int total = slotDao.countTotalSlots();
        int available = slotDao.countByStatus("AVAILABLE");
        int occupied = slotDao.countByStatus("OCCUPIED");
        int reserved = slotDao.countByStatus("RESERVED");
        int maintenance = slotDao.countByStatus("MAINTENANCE");
        double occPct = total > 0 ? occupied * 100.0 / total : 0;
        double availPct = total > 0 ? available * 100.0 / total : 0;

        availableCard.setValue(String.valueOf(available));
        availableCard.setSubtitle(String.format("%.0f%% of %d bays", availPct, total));
        occupiedCard.setValue(String.valueOf(occupied));
        occupiedCard.setSubtitle(maintenance > 0 ? maintenance + " in maintenance" : "Vehicles on site");
        reservedCard.setValue(String.valueOf(reserved));
        reservedCard.setSubtitle("Held for bookings");
        occupancyCard.setValue(String.format("%.1f%%", occPct));
        boolean high = occPct >= 80;
        occupancyCard.setSubtitle(high ? "High load — consider overflow" : (occPct >= 55 ? "Moderate load" : "Normal load"));
        occupancyCard.setSubtitleColor(high ? UITheme.WARNING_TEXT : UITheme.TEXT_TERTIARY);

        double revToday = paymentDao.getTodayRevenue();
        double revYesterday = paymentDao.getRevenueForDate(today.minusDays(1));
        revenueCard.setValue(MoneyFormat.format(revToday));
        setDelta(revenueCard, revToday, revYesterday, "vs yesterday");

        Map<LocalDate, int[]> todayTurnover = ticketDao.getTurnoverMetrics(today, today);
        int[] tt = todayTurnover.getOrDefault(today, new int[]{0, 0});
        trafficCard.setValue(String.valueOf(tt[0] + tt[1]));
        trafficCard.setSubtitle(tt[0] + " entries  ·  " + tt[1] + " exits");

        // Charts
        turnoverChart.reload();
        levelOccupancy.reload();
        revenueChart.reload(today);

        Map<VehicleType, Integer> typeCounts = ticketDao.getOccupiedVehicleTypeCounts();
        int cars = typeCounts.getOrDefault(VehicleType.CAR, 0) + typeCounts.getOrDefault(VehicleType.AUTO_RICKSHAW, 0);
        int bikes = typeCounts.getOrDefault(VehicleType.MOTORCYCLE, 0) + typeCounts.getOrDefault(VehicleType.EV_TWO_WHEELER, 0) + typeCounts.getOrDefault(VehicleType.BIKE, 0);
        int trucks = typeCounts.getOrDefault(VehicleType.HCV, 0) + typeCounts.getOrDefault(VehicleType.LCV, 0) + typeCounts.getOrDefault(VehicleType.TRUCK, 0);
        int evs = typeCounts.getOrDefault(VehicleType.EV_CAR, 0) + typeCounts.getOrDefault(VehicleType.ELECTRIC_CAR, 0);
        // Bays marked OCCUPIED without a matching active ticket are shown honestly as "Unmatched"
        int unmatched = Math.max(0, occupied - (cars + bikes + trucks + evs));
        donutChart.setData(new int[]{cars, bikes, trucks, evs, unmatched});

        refreshAlerts();
        refreshActivity();

        pageHeader.setSubtitle("Live status across " + Math.max(1, parkingLot.getFloors().size()) + " levels  ·  Updated "
                + LocalDateTime.now(IST).format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        revalidate();
        repaint();
    }

    private static void setDelta(MetricCard card, double now, double before, String suffix) {
        if (before <= 0 && now <= 0) {
            card.setSubtitle("No payments yet");
            card.setSubtitleColor(UITheme.TEXT_TERTIARY);
        } else if (before <= 0) {
            card.setSubtitle("None " + suffix.replace("vs ", ""));
            card.setSubtitleColor(UITheme.SUCCESS_TEXT);
        } else {
            double d = (now - before) / before * 100.0;
            card.setSubtitle(String.format("%s%.0f%% %s", d >= 0 ? "+" : "\u2212", Math.abs(d), suffix));
            card.setSubtitleColor(d >= 0 ? UITheme.SUCCESS_TEXT : UITheme.ERROR_TEXT);
        }
    }

    private void refreshActivity() {
        activityTableModel.setRowCount(0);
        List<ParkingTicket> recent = ticketDao.findRecent(8);
        for (ParkingTicket t : recent) {
            boolean active = "ACTIVE".equalsIgnoreCase(t.getStatus().name());
            LocalDateTime when = active || t.getExitTime() == null ? t.getEntryTime() : t.getExitTime();
            activityTableModel.addRow(new Object[]{
                    when != null ? DateFormat.display(when) : "—",
                    PlateUtil.format(t.getLicensePlate()),
                    active ? "Entry" : "Exit",
                    t.getSlotId(),
                    prettyType(t.getVehicleType()),
                    t.getTotalAmount() > 0 ? MoneyFormat.format(t.getTotalAmount()) : "—"
            });
        }
        ((CardLayout) activityBody.getLayout()).show(activityBody, recent.isEmpty() ? "EMPTY" : "TABLE");
    }

    private void refreshAlerts() {
        alertsList.removeAll();
        int count = 0;
        LocalDateTime now = LocalDateTime.now(IST);

        for (ParkingTicket t : ticketDao.findOverstayTickets(OVERSTAY_HOURS)) {
            if (count >= 6) break;
            long hours = Duration.between(t.getEntryTime(), now).toHours();
            addAlert(UITheme.ERROR, IconType.CLOCK, "Overstay · " + t.getSlotId(),
                    PlateUtil.format(t.getLicensePlate()) + " parked " + hours + "h (limit " + OVERSTAY_HOURS + "h)", t.getSlotId());
            count++;
        }
        for (Reservation r : reservationDao.findAll()) {
            if (count >= 6) break;
            if (r.getStatus() != null && "CONFIRMED".equalsIgnoreCase(r.getStatus().name())) {
                String when = r.getStartTime() != null ? r.getStartTime().format(DateTimeFormatter.ofPattern("d MMM, h:mm a")) : "";
                addAlert(UITheme.WARNING, IconType.CALENDAR, "Reservation · " + r.getSlotId(),
                        r.getReservationCode() + (when.isEmpty() ? "" : "  ·  from " + when), r.getSlotId());
                count++;
            }
        }
        List<ParkingSlot> maint = slotDao.findAllByStatus("MAINTENANCE");
        if (!maint.isEmpty() && count < 6) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(4, maint.size()); i++) {
                if (i > 0) sb.append(", ");
                sb.append(maint.get(i).getSlotId());
            }
            if (maint.size() > 4) sb.append(" +").append(maint.size() - 4);
            addAlert(UITheme.SLOT_MAINTENANCE, IconType.WRENCH, maint.size() + " bays offline", sb.toString(), maint.get(0).getSlotId());
            count++;
        }
        if (count == 0) {
            EmptyState ok = new EmptyState(IconType.CHECK, "All clear", "No overstays, pending reservations or offline bays.");
            ok.setAlignmentX(LEFT_ALIGNMENT);
            alertsList.add(ok);
            alertsCount.setText("");
        } else {
            alertsCount.setText(count + " open");
        }
        alertsList.revalidate();
        alertsList.repaint();
    }

    private void addAlert(Color color, IconType iconType, String title, String detail, String slotId) {
        AlertRow row = new AlertRow(color, iconType, title, detail);
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                navigationHandler.accept(slotId != null && !slotId.isBlank() ? "FLOORS:" + slotId : "FLOORS");
            }
        });
        alertsList.add(row);
        alertsList.add(Box.createVerticalStrut(6));
    }

    static String prettyType(VehicleType t) {
        if (t == null) return "—";
        return switch (t) {
            case HCV, LCV -> t.name();
            case EV_CAR, ELECTRIC_CAR -> "EV car";
            case EV_TWO_WHEELER -> "EV two-wheeler";
            default -> {
                String s = t.name().replace('_', ' ').toLowerCase();
                yield Character.toUpperCase(s.charAt(0)) + s.substring(1);
            }
        };
    }

    // =====================================================================================
    // Components
    // =====================================================================================

    /** Clickable alert row: tinted icon, title, detail, chevron; hover highlight. */
    private static class AlertRow extends JPanel {
        private boolean hover;

        AlertRow(Color color, IconType iconType, String title, String detail) {
            super(new BorderLayout(12, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(9, 10, 9, 10));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));

            add(new MetricCard.IconBadge(new JLabel(VectorIconUtil.getIcon(iconType, 14, Badge.textFor(color))), color, 30), BorderLayout.WEST);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JLabel t = new JLabel(title);
            t.setFont(UITheme.font(Font.BOLD, 12));
            t.setForeground(UITheme.TEXT_PRIMARY);
            JLabel d = new JLabel(detail);
            d.setFont(UITheme.font(Font.PLAIN, 11));
            d.setForeground(UITheme.TEXT_SECONDARY);
            text.add(t);
            text.add(Box.createVerticalStrut(2));
            text.add(d);
            add(text, BorderLayout.CENTER);
            add(new JLabel(VectorIconUtil.getIcon(IconType.ARROW_RIGHT, 12, UITheme.TEXT_TERTIARY)), BorderLayout.EAST);
            setToolTipText(title + " — " + detail);

            addMouseListener(new MouseAdapter() {
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
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hover ? UITheme.BG_HOVER : UITheme.BG_ELEVATED);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, UITheme.RADIUS, UITheme.RADIUS);
            g2.setColor(hover ? UITheme.BORDER_STRONG : UITheme.BORDER_SUBTLE);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, UITheme.RADIUS, UITheme.RADIUS);
            g2.dispose();
        }
    }

    /** Right-aligned tabular numbers. */
    private static class DarkThemeRight extends com.smartparking.ui.theme.DarkTheme.CellRenderer {
        DarkThemeRight() {
            super(SwingConstants.RIGHT);
        }
    }

    /** Licence plates in the mono face. */
    private static class MonoRenderer extends com.smartparking.ui.theme.DarkTheme.CellRenderer {
        MonoRenderer() {
            super(SwingConstants.LEFT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            c.setFont(UITheme.font(Font.BOLD, 12));
            return c;
        }
    }

    /** Shared helpers for the custom charts. */
    private abstract static class ChartCanvas extends JComponent {
        protected int hoverIdx = -1;

        ChartCanvas() {
            setOpaque(false);
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int i = indexAt(e.getX());
                    if (i != hoverIdx) {
                        hoverIdx = i;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverIdx = -1;
                    repaint();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        abstract int indexAt(int x);

        static Graphics2D prep(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            return g2;
        }

        /** Rounds an axis maximum up to a "nice" number. */
        static int niceMax(double v) {
            if (v <= 1) return 1;
            double exp = Math.pow(10, Math.floor(Math.log10(v)));
            double f = v / exp;
            for (double c : new double[]{1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10}) {
                if (f <= c + 1e-9) return (int) Math.ceil(c * exp);
            }
            return (int) Math.ceil(10 * exp);
        }

        /** Floating tooltip card drawn inside the chart. */
        static void tooltip(Graphics2D g2, int anchorX, int anchorY, int bounds, String title, String[] lines, Color[] colors) {
            Font tf = UITheme.font(Font.BOLD, 12), lf = UITheme.font(Font.PLAIN, 12);
            FontMetrics tm = g2.getFontMetrics(tf), lm = g2.getFontMetrics(lf);
            int w = tm.stringWidth(title);
            for (String l : lines) w = Math.max(w, lm.stringWidth(l) + 16);
            w += 24;
            int h = 14 + tm.getHeight() + lines.length * (lm.getHeight() + 2) + 8;
            int x = Math.min(Math.max(4, anchorX + 12), bounds - w - 4);
            int y = Math.max(4, anchorY - h / 2);
            g2.setColor(new Color(0, 0, 0, 90));
            g2.fill(new RoundRectangle2D.Float(x + 1, y + 3, w, h, 10, 10));
            g2.setColor(UITheme.BG_HOVER);
            g2.fill(new RoundRectangle2D.Float(x, y, w, h, 10, 10));
            g2.setColor(UITheme.BORDER_STRONG);
            g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, 10, 10));
            g2.setFont(tf);
            g2.setColor(UITheme.TEXT_PRIMARY);
            int ty = y + 10 + tm.getAscent();
            g2.drawString(title, x + 12, ty);
            g2.setFont(lf);
            ty += 6;
            for (int i = 0; i < lines.length; i++) {
                ty += lm.getHeight() + 2;
                g2.setColor(colors[i]);
                g2.fillRoundRect(x + 12, ty - lm.getAscent() + 3, 8, 8, 3, 3);
                g2.setColor(UITheme.TEXT_BODY);
                g2.drawString(lines[i], x + 26, ty);
            }
        }
    }

    // ---------------------------------------------------------------- turnover chart
    private class TurnoverChart extends ChartCanvas {
        private String range = "7D";
        private String[] labels = new String[0];
        private String[] fullLabels = new String[0];
        private int[] entered = new int[0];
        private int[] exited = new int[0];
        private static final int LEFT = 36, RIGHT = 8, TOP = 12, BOTTOM = 28;

        JComponent rangeControl() {
            return new SegmentedControl(new String[]{"7D", "30D", "90D", "1Y"}, 0, r -> {
                range = r;
                reload();
                repaint();
            });
        }

        void reload() {
            LocalDate today = LocalDate.now(IST);
            switch (range) {
                case "30D" -> bucketDays(today, 30, 1, "d MMM");
                case "90D" -> bucketDays(today, 91, 7, "d MMM");
                case "1Y" -> bucketMonths(today, 12);
                default -> bucketDays(today, 7, 1, "EEE");
            }
            repaint();
        }

        /** Groups daily metrics into buckets of {@code step} days, ending today. */
        private void bucketDays(LocalDate today, int days, int step, String pattern) {
            LocalDate from = today.minusDays(days - 1L);
            Map<LocalDate, int[]> m = ticketDao.getTurnoverMetrics(from, today);
            int n = (int) Math.ceil(days / (double) step);
            labels = new String[n];
            fullLabels = new String[n];
            entered = new int[n];
            exited = new int[n];
            DateTimeFormatter f = DateTimeFormatter.ofPattern(pattern);
            DateTimeFormatter ff = DateTimeFormatter.ofPattern("EEE, d MMM yyyy");
            for (int i = 0; i < n; i++) {
                LocalDate bStart = from.plusDays((long) i * step);
                LocalDate bEnd = bStart.plusDays(step - 1L);
                if (bEnd.isAfter(today)) bEnd = today;
                for (LocalDate d = bStart; !d.isAfter(bEnd); d = d.plusDays(1)) {
                    int[] v = m.get(d);
                    if (v != null) {
                        entered[i] += v[0];
                        exited[i] += v[1];
                    }
                }
                labels[i] = bStart.format(f);
                fullLabels[i] = step == 1 ? bStart.format(ff) : "Week of " + bStart.format(DateTimeFormatter.ofPattern("d MMM"));
            }
        }

        private void bucketMonths(LocalDate today, int months) {
            LocalDate first = today.withDayOfMonth(1).minusMonths(months - 1L);
            Map<LocalDate, int[]> m = ticketDao.getTurnoverMetrics(first, today);
            labels = new String[months];
            fullLabels = new String[months];
            entered = new int[months];
            exited = new int[months];
            for (int i = 0; i < months; i++) {
                LocalDate ms = first.plusMonths(i);
                for (Map.Entry<LocalDate, int[]> e : m.entrySet()) {
                    if (e.getKey().getYear() == ms.getYear() && e.getKey().getMonth() == ms.getMonth()) {
                        entered[i] += e.getValue()[0];
                        exited[i] += e.getValue()[1];
                    }
                }
                labels[i] = ms.format(DateTimeFormatter.ofPattern("MMM"));
                fullLabels[i] = ms.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            }
        }

        @Override
        int indexAt(int x) {
            int n = entered.length;
            if (n == 0) return -1;
            double slot = (getWidth() - LEFT - RIGHT) / (double) n;
            int i = (int) ((x - LEFT) / slot);
            return i >= 0 && i < n ? i : -1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prep(g);
            int w = getWidth(), h = getHeight();
            int n = entered.length;
            int cw = w - LEFT - RIGHT, ch = h - TOP - BOTTOM;
            int max = 0;
            for (int i = 0; i < n; i++) max = Math.max(max, Math.max(entered[i], exited[i]));

            // Legend (top-right inside chart)
            Font small = UITheme.font(Font.PLAIN, 11);
            g2.setFont(small);
            FontMetrics sm = g2.getFontMetrics();

            if (max == 0) {
                g2.setColor(UITheme.BORDER_SUBTLE);
                for (int s = 0; s <= 4; s++) {
                    int y = TOP + ch - ch * s / 4;
                    g2.drawLine(LEFT, y, w - RIGHT, y);
                }
                String msg = "No gate activity recorded in this period";
                g2.setColor(UITheme.TEXT_SECONDARY);
                g2.setFont(UITheme.font(Font.BOLD, 13));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, TOP + ch / 2);
                g2.dispose();
                return;
            }
            int step = niceMax(max * 1.05 / 4.0);
            if (step < 1) step = 1;
            int top = step * 4;
            if (top < max) top += step;

            // grid + y labels
            for (int s = 0; s <= 4; s++) {
                int y = TOP + ch - ch * s / 4;
                g2.setColor(s == 0 ? UITheme.BORDER : UITheme.BORDER_SUBTLE);
                if (s == 0) g2.drawLine(LEFT, y, w - RIGHT, y);
                else {
                    g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{3f, 4f}, 0));
                    g2.drawLine(LEFT, y, w - RIGHT, y);
                    g2.setStroke(new BasicStroke(1f));
                }
                String lbl = String.valueOf(top * s / 4);
                g2.setColor(UITheme.TEXT_TERTIARY);
                g2.drawString(lbl, LEFT - 8 - sm.stringWidth(lbl), y + 4);
            }

            double slot = cw / (double) n;
            int barW = (int) Math.max(3, Math.min(28, slot * 0.5));
            int labelEvery = n > 14 ? (int) Math.ceil(n / 8.0) : 1;
            int[] lx = new int[n], ly = new int[n];

            // hover band
            if (hoverIdx >= 0) {
                g2.setColor(UITheme.alpha(Color.WHITE, 10));
                g2.fillRoundRect((int) (LEFT + hoverIdx * slot) + 2, TOP, (int) slot - 4, ch, 8, 8);
            }

            for (int i = 0; i < n; i++) {
                int cx = (int) (LEFT + i * slot + slot / 2);
                int bh = (int) Math.round(entered[i] / (double) top * ch);
                int by = TOP + ch - bh;
                Color barTop = hoverIdx == i ? UITheme.ACCENT_HOVER : UITheme.ACCENT;
                if (bh > 0) {
                    g2.setPaint(new GradientPaint(0, by, barTop, 0, TOP + ch, UITheme.blend(UITheme.ACCENT, UITheme.BG_CARD, 0.55)));
                    g2.fill(new RoundRectangle2D.Float(cx - barW / 2f, by, barW, bh + 4, 6, 6));
                    g2.setColor(UITheme.BG_CARD);
                    g2.fillRect(cx - barW / 2, TOP + ch, barW, 5);
                }
                lx[i] = cx;
                ly[i] = TOP + ch - (int) Math.round(exited[i] / (double) top * ch);
                if (i % labelEvery == 0 || i == n - 1) {
                    g2.setColor(hoverIdx == i ? UITheme.TEXT_PRIMARY : UITheme.TEXT_TERTIARY);
                    g2.setFont(small);
                    g2.drawString(labels[i], cx - sm.stringWidth(labels[i]) / 2, h - 8);
                }
            }

            // exits line with soft area
            if (n > 1) {
                Path2D.Float area = new Path2D.Float();
                area.moveTo(lx[0], TOP + ch);
                for (int i = 0; i < n; i++) area.lineTo(lx[i], ly[i]);
                area.lineTo(lx[n - 1], TOP + ch);
                area.closePath();
                g2.setPaint(new GradientPaint(0, TOP, UITheme.alpha(UITheme.SLOT_EV, 50), 0, TOP + ch, UITheme.alpha(UITheme.SLOT_EV, 0)));
                g2.fill(area);
                g2.setColor(UITheme.SLOT_EV);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(lx, ly, n);
            }
            for (int i = 0; i < n; i++) {
                if (n <= 14 || i == hoverIdx) {
                    int r = i == hoverIdx ? 5 : 3;
                    g2.setColor(UITheme.BG_CARD);
                    g2.fillOval(lx[i] - r - 1, ly[i] - r - 1, (r + 1) * 2, (r + 1) * 2);
                    g2.setColor(UITheme.SLOT_EV);
                    g2.fillOval(lx[i] - r, ly[i] - r, r * 2, r * 2);
                }
            }

            // legend
            g2.setFont(small);
            int lxp = w - RIGHT - sm.stringWidth("Entries") - sm.stringWidth("Exits") - 50;
            g2.setColor(UITheme.ACCENT);
            g2.fillRoundRect(lxp, 2, 9, 9, 3, 3);
            g2.setColor(UITheme.TEXT_SECONDARY);
            g2.drawString("Entries", lxp + 14, 11);
            int lxp2 = lxp + 14 + sm.stringWidth("Entries") + 16;
            g2.setColor(UITheme.SLOT_EV);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(lxp2, 7, lxp2 + 10, 7);
            g2.setColor(UITheme.TEXT_SECONDARY);
            g2.drawString("Exits", lxp2 + 15, 11);

            if (hoverIdx >= 0 && hoverIdx < n) {
                tooltip(g2, lx[hoverIdx], TOP + ch / 2, w, fullLabels[hoverIdx],
                        new String[]{entered[hoverIdx] + " entries", exited[hoverIdx] + " exits"},
                        new Color[]{UITheme.ACCENT, UITheme.SLOT_EV});
            }
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------- occupancy by level
    private class LevelOccupancy extends JComponent {
        private final List<int[]> levels = new ArrayList<>(); // {floor, occupied, reserved, maintenance, available, total}
        private int hover = -1;

        LevelOccupancy() {
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int i = rowAt(e.getY());
                    if (i != hover) {
                        hover = i;
                        setCursor(Cursor.getPredefinedCursor(i >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (rowAt(e.getY()) >= 0) navigationHandler.accept("FLOORS");
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            setToolTipText("Open the floor map");
        }

        private int rowH() {
            return 56;
        }

        private int rowAt(int y) {
            int i = y / rowH();
            return i >= 0 && i < levels.size() ? i : -1;
        }

        void reload() {
            levels.clear();
            int floors = Math.max(1, parkingLot.getFloors().size());
            for (int f = 1; f <= floors; f++) {
                int occ = 0, res = 0, mnt = 0, av = 0;
                List<ParkingSlot> slots = slotDao.findByFloor(f);
                for (ParkingSlot s : slots) {
                    String st = s.getStatus() == null ? "" : s.getStatus().toUpperCase();
                    switch (st) {
                        case "OCCUPIED" -> occ++;
                        case "RESERVED" -> res++;
                        case "MAINTENANCE" -> mnt++;
                        default -> av++;
                    }
                }
                if (!slots.isEmpty()) levels.add(new int[]{f, occ, res, mnt, av, slots.size()});
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = ChartCanvas.prep(g);
            int w = getWidth();
            Font bold = UITheme.font(Font.BOLD, 13), small = UITheme.font(Font.PLAIN, 11);
            FontMetrics bm = g2.getFontMetrics(bold), sm = g2.getFontMetrics(small);
            String[] names = {"Ground", "Level 2", "Level 3", "Level 4", "Level 5"};
            Color[] segColors = {UITheme.SLOT_OCCUPIED, UITheme.SLOT_RESERVED, UITheme.SLOT_MAINTENANCE};
            for (int i = 0; i < levels.size(); i++) {
                int[] l = levels.get(i);
                int y = i * rowH();
                if (hover == i) {
                    g2.setColor(UITheme.BG_ELEVATED);
                    g2.fillRoundRect(-6, y, w + 12, rowH() - 6, 8, 8);
                }
                String name = "L" + l[0] + " · " + (l[0] - 1 < names.length ? names[l[0] - 1] : "Level " + l[0]);
                g2.setFont(bold);
                g2.setColor(UITheme.TEXT_PRIMARY);
                g2.drawString(name, 0, y + 6 + bm.getAscent());
                int used = l[1] + l[2] + l[3];
                String pct = Math.round(l[1] * 100.0 / Math.max(1, l[5])) + "%";
                String right = l[1] + " / " + l[5] + " occupied";
                g2.setFont(small);
                g2.setColor(UITheme.TEXT_SECONDARY);
                g2.drawString(right, w - sm.stringWidth(right), y + 6 + bm.getAscent());
                g2.setFont(bold);
                g2.setColor(UITheme.TEXT_PRIMARY);
                g2.drawString(pct, w - sm.stringWidth(right) - 10 - bm.stringWidth(pct), y + 6 + bm.getAscent());

                int by = y + 30, bh = 10;
                g2.setColor(UITheme.tint(UITheme.SLOT_AVAILABLE));
                g2.fillRoundRect(0, by, w, bh, bh, bh);
                Shape clip = g2.getClip();
                g2.clip(new RoundRectangle2D.Float(0, by, w, bh, bh, bh));
                int x = 0;
                int[] parts = {l[1], l[2], l[3]};
                for (int p = 0; p < 3; p++) {
                    int sw = (int) Math.round(parts[p] / (double) l[5] * w);
                    if (sw > 0) {
                        g2.setColor(segColors[p]);
                        g2.fillRect(x, by, sw, bh);
                        g2.setColor(UITheme.BG_CARD);
                        g2.fillRect(x + sw - 1, by, 2, bh);
                        x += sw;
                    }
                }
                g2.setClip(clip);
                if (used == 0) {
                    g2.setColor(UITheme.blend(UITheme.SLOT_AVAILABLE, UITheme.BG_CARD, 0.4));
                    g2.drawRoundRect(0, by, w - 1, bh - 1, bh, bh);
                }
            }
            // legend
            int ly = levels.size() * rowH() + 8;
            String[] lbl = {"Occupied", "Reserved", "Maintenance", "Free"};
            Color[] lc = {UITheme.SLOT_OCCUPIED, UITheme.SLOT_RESERVED, UITheme.SLOT_MAINTENANCE, UITheme.tintBorder(UITheme.SLOT_AVAILABLE)};
            int lx = 0;
            g2.setFont(small);
            for (int i = 0; i < lbl.length; i++) {
                g2.setColor(lc[i]);
                g2.fillRoundRect(lx, ly, 8, 8, 3, 3);
                g2.setColor(UITheme.TEXT_SECONDARY);
                g2.drawString(lbl[i], lx + 13, ly + 8);
                lx += 13 + sm.stringWidth(lbl[i]) + 14;
            }
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------- revenue chart
    private class RevenueChart extends ChartCanvas {
        private final double[] values = new double[7];
        private final String[] labels = new String[7];
        private final String[] fullLabels = new String[7];
        private double weekTotal, prevWeekTotal;
        private static final int TOP = 46, BOTTOM = 24;

        void reload(LocalDate today) {
            weekTotal = 0;
            prevWeekTotal = 0;
            for (int i = 0; i < 7; i++) {
                LocalDate d = today.minusDays(6 - i);
                values[i] = paymentDao.getRevenueForDate(d);
                weekTotal += values[i];
                labels[i] = d.format(DateTimeFormatter.ofPattern("EEE"));
                fullLabels[i] = d.format(DateTimeFormatter.ofPattern("EEE, d MMM"));
                prevWeekTotal += paymentDao.getRevenueForDate(d.minusDays(7));
            }
            repaint();
        }

        @Override
        int indexAt(int x) {
            int i = (int) (x / (getWidth() / 7.0));
            return i >= 0 && i < 7 ? i : -1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prep(g);
            int w = getWidth(), h = getHeight();
            // headline
            g2.setFont(UITheme.font(Font.BOLD, 22));
            g2.setColor(UITheme.TEXT_PRIMARY);
            String total = MoneyFormat.format(weekTotal);
            g2.drawString(total, 0, 24);
            int tw = g2.getFontMetrics().stringWidth(total);
            g2.setFont(UITheme.font(Font.BOLD, 12));
            String delta;
            Color dc;
            if (prevWeekTotal > 0) {
                double d = (weekTotal - prevWeekTotal) / prevWeekTotal * 100;
                delta = String.format("%s%.0f%% vs prior week", d >= 0 ? "+" : "\u2212", Math.abs(d));
                dc = d >= 0 ? UITheme.SUCCESS_TEXT : UITheme.ERROR_TEXT;
            } else {
                delta = weekTotal > 0 ? "No revenue in prior week" : "No completed payments";
                dc = UITheme.TEXT_TERTIARY;
            }
            g2.setColor(dc);
            g2.drawString(delta, tw + 12, 23);

            int ch = h - TOP - BOTTOM;
            double max = 0;
            for (double v : values) max = Math.max(max, v);
            double slot = w / 7.0;
            int barW = (int) Math.min(30, slot * 0.56);
            FontMetrics sm = g2.getFontMetrics(UITheme.font(Font.PLAIN, 11));
            for (int i = 0; i < 7; i++) {
                int cx = (int) (i * slot + slot / 2);
                int bh = max > 0 ? (int) Math.round(values[i] / max * ch) : 0;
                // track
                g2.setColor(UITheme.BG_ELEVATED);
                g2.fillRoundRect(cx - barW / 2, TOP, barW, ch, 6, 6);
                if (bh > 0) {
                    boolean isToday = i == 6;
                    Color c = hoverIdx == i ? UITheme.SUCCESS_TEXT : (isToday ? UITheme.SUCCESS : UITheme.blend(UITheme.SUCCESS, UITheme.BG_CARD, 0.6));
                    g2.setColor(c);
                    g2.fillRoundRect(cx - barW / 2, TOP + ch - bh, barW, bh, 6, 6);
                }
                g2.setFont(UITheme.font(i == 6 ? Font.BOLD : Font.PLAIN, 11));
                g2.setColor(i == 6 || hoverIdx == i ? UITheme.TEXT_PRIMARY : UITheme.TEXT_TERTIARY);
                g2.drawString(labels[i] == null ? "" : labels[i], cx - sm.stringWidth(labels[i] == null ? "" : labels[i]) / 2, h - 6);
            }
            if (hoverIdx >= 0 && fullLabels[hoverIdx] != null) {
                int cx = (int) (hoverIdx * slot + slot / 2);
                tooltip(g2, cx, TOP + ch / 2, w, fullLabels[hoverIdx],
                        new String[]{MoneyFormat.format(values[hoverIdx])}, new Color[]{UITheme.SUCCESS});
            }
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------- donut
    private static class DonutChart extends JComponent {
        private static final String[] NAMES = {"Cars", "Bikes", "Trucks", "EVs", "Unmatched"};
        private static final Color[] COLORS = {UITheme.ACCENT, UITheme.WARNING, new Color(236, 72, 153), UITheme.SLOT_EV, UITheme.SLOT_MAINTENANCE};
        private int[] data = new int[5];
        private int hover = -1;

        DonutChart() {
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int i = legendAt(e.getX(), e.getY());
                    if (i != hover) {
                        hover = i;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        void setData(int[] d) {
            this.data = d;
            repaint();
        }

        private int ringSize() {
            return Math.max(90, Math.min(getHeight() - 4, Math.min(150, (int) (getWidth() * 0.42))));
        }

        private int legendAt(int x, int y) {
            int lx = ringSize() + 24;
            if (x < lx) return -1;
            int row = (y - legendTop()) / 28;
            int visible = 0;
            for (int i = 0; i < data.length; i++) {
                if (i == 4 && data[4] == 0) continue;
                if (visible == row) return i;
                visible++;
            }
            return -1;
        }

        private int legendTop() {
            int rows = data[4] > 0 ? 5 : 4;
            return Math.max(0, (getHeight() - rows * 28) / 2);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = ChartCanvas.prep(g);
            int total = 0;
            for (int v : data) total += v;
            int s = ringSize();
            int x = 4, y = (getHeight() - s) / 2;
            float stroke = 16f;
            Arc2D.Float base = new Arc2D.Float(x + stroke / 2, y + stroke / 2, s - stroke, s - stroke, 0, 360, Arc2D.OPEN);
            g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g2.setColor(UITheme.BG_ELEVATED);
            g2.draw(base);
            if (total > 0) {
                double start = 90;
                int nonZero = 0;
                for (int v : data) if (v > 0) nonZero++;
                double gap = nonZero > 1 ? 2.5 : 0;
                for (int i = 0; i < data.length; i++) {
                    if (data[i] == 0) continue;
                    double ext = data[i] * 360.0 / total;
                    g2.setStroke(new BasicStroke(hover == i ? stroke + 4 : stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
                    g2.setColor(hover >= 0 && hover != i ? UITheme.blend(COLORS[i], UITheme.BG_CARD, 0.45) : COLORS[i]);
                    g2.draw(new Arc2D.Double(x + stroke / 2, y + stroke / 2, s - stroke, s - stroke, start - gap / 2, -(ext - gap), Arc2D.OPEN));
                    start -= ext;
                }
            }
            // centre
            String num = hover >= 0 ? String.valueOf(data[hover]) : String.valueOf(total);
            String cap = hover >= 0 ? NAMES[hover] : "parked now";
            g2.setFont(UITheme.font(Font.BOLD, 26));
            FontMetrics nm = g2.getFontMetrics();
            g2.setColor(UITheme.TEXT_PRIMARY);
            g2.drawString(num, x + (s - nm.stringWidth(num)) / 2, y + s / 2 + 6);
            g2.setFont(UITheme.font(Font.PLAIN, 11));
            FontMetrics cm = g2.getFontMetrics();
            g2.setColor(UITheme.TEXT_SECONDARY);
            g2.drawString(cap, x + (s - cm.stringWidth(cap)) / 2, y + s / 2 + 24);

            // legend
            int lx = s + 24, ly = legendTop();
            int lw = getWidth() - lx;
            Font lf = UITheme.font(Font.PLAIN, 12), vf = UITheme.font(Font.BOLD, 12);
            for (int i = 0; i < data.length; i++) {
                if (i == 4 && data[4] == 0) continue;
                int cy = ly + 14;
                if (hover == i) {
                    g2.setColor(UITheme.BG_ELEVATED);
                    g2.fillRoundRect(lx - 6, ly + 1, lw + 6, 26, 6, 6);
                }
                g2.setColor(COLORS[i]);
                g2.fillRoundRect(lx, cy - 4, 8, 8, 3, 3);
                g2.setFont(lf);
                g2.setColor(UITheme.TEXT_BODY);
                g2.drawString(NAMES[i], lx + 16, cy + 4);
                String v = String.valueOf(data[i]);
                String pc = total > 0 ? Math.round(data[i] * 100.0 / total) + "%" : "0%";
                g2.setFont(lf);
                FontMetrics pm = g2.getFontMetrics();
                g2.setColor(UITheme.TEXT_TERTIARY);
                g2.drawString(pc, lx + lw - 6 - pm.stringWidth(pc), cy + 4);
                g2.setFont(vf);
                g2.setColor(UITheme.TEXT_PRIMARY);
                g2.drawString(v, lx + lw - 6 - 40 - g2.getFontMetrics().stringWidth(v), cy + 4);
                ly += 28;
            }
            if (total == 0) {
                g2.setFont(UITheme.font(Font.PLAIN, 11));
                g2.setColor(UITheme.TEXT_TERTIARY);
                g2.drawString("No vehicles currently parked", lx, ly + 16);
            }
            g2.dispose();
        }
    }
}
