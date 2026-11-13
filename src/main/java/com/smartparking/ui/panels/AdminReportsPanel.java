package com.smartparking.ui.panels;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.smartparking.model.fastag.FastagAccount;
import com.smartparking.model.invoice.Invoice;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.payment.PaymentTransaction;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.repository.FastagDao;
import com.smartparking.repository.InvoiceDao;
import com.smartparking.repository.ParkingSlotDao;
import com.smartparking.repository.PaymentDao;
import com.smartparking.repository.TicketDao;
import com.smartparking.service.DatabaseBackupService;
import com.smartparking.service.DataRetentionService;
import com.smartparking.service.FastagService;
import com.smartparking.service.InvoiceService;
import com.smartparking.service.ReportService;
import com.smartparking.service.TariffService;
import com.smartparking.ui.components.ModernButton;
import com.smartparking.ui.components.RoundedPanel;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.VectorIconUtil;
import com.smartparking.util.DateFormat;
import com.smartparking.util.MoneyFormat;
import com.smartparking.util.PlateUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Modern Admin Management Dashboard & Audit Reporting Panel:
 * 1. Date range picker toolbar (Today, 7 Days, 30 Days, All Time) with CSV & PDF Export.
 * 2. 8 KPI metric cards computed directly from SQLite (Total, Occupied, Reserved, Maintenance, Available, Occupancy %, Today Revenue, Month Revenue).
 * 3. Floor utilization horizontal progress bars (L1, L2, L3).
 * 4. Revenue breakdown by payment method and vehicle classification.
 * 5. Currently parked active vehicles table with dwell time and accrued fee.
 * 6. Settled payment audit trail with search, filter, and pagination.
 * 7. Past Invoices search, HTML preview, and PDF reprint.
 * 8. FASTag Accounts Management (Simulated).
 */
public class AdminReportsPanel extends JPanel {

    private final ParkingLot parkingLot;
    private final ReportService reportService;
    private final PaymentDao paymentDao;
    private final InvoiceDao invoiceDao;
    private final FastagDao fastagDao;
    private final FastagService fastagService;
    private final InvoiceService invoiceService;
    private final ParkingSlotDao slotDao;
    private final TicketDao ticketDao;
    private final TariffService tariffService;
    private final Runnable onStateChange;

    // 8 KPI Card components
    private ReportKPICard kpiTotal;
    private ReportKPICard kpiOccupied;
    private ReportKPICard kpiReserved;
    private ReportKPICard kpiMaint;
    private ReportKPICard kpiAvail;
    private ReportKPICard kpiOccPct;
    private ReportKPICard kpiTodayRev;
    private ReportKPICard kpiMonthRev;

    // Floor progress bars
    private JProgressBar barFloor1;
    private JLabel lblFloor1;
    private JProgressBar barFloor2;
    private JLabel lblFloor2;
    private JProgressBar barFloor3;
    private JLabel lblFloor3;

    // Revenue distribution labels
    private JLabel lblRevUpi;
    private JLabel lblRevFastag;
    private JLabel lblRevCash;
    private JLabel lblRevCard;

    // Currently Parked Table
    private DefaultTableModel parkedTableModel;
    private JTable parkedTable;

    // Settled Payments Table with filter and search
    private JTextField auditSearchField;
    private JComboBox<String> methodFilterBox;
    private JComboBox<String> statusFilterBox;
    private DefaultTableModel txnTableModel;
    private JTable txnTable;
    private JLabel paginationLabel;
    private int currentPage = 1;
    private final int pageSize = 12;
    private List<PaymentTransaction> filteredTransactions = new ArrayList<>();

    // Past Invoices
    private JTextField invoiceSearchField;
    private DefaultTableModel invoiceTableModel;
    private JTable invoiceTable;

    // FASTag Management
    private DefaultTableModel fastagTableModel;
    private JTable fastagTable;

    // Active Range Filter
    private String activeRange = "Today";

    public AdminReportsPanel(ParkingLot parkingLot, Runnable onStateChange) {
        this.parkingLot = parkingLot;
        this.reportService = new ReportService(parkingLot);
        this.paymentDao = new PaymentDao();
        this.invoiceDao = new InvoiceDao();
        this.fastagDao = new FastagDao();
        this.fastagService = new FastagService(fastagDao, new com.smartparking.repository.AuditLogDao());
        this.invoiceService = new InvoiceService();
        this.slotDao = new ParkingSlotDao();
        this.ticketDao = new TicketDao();
        this.tariffService = new TariffService();
        this.onStateChange = onStateChange;

        setLayout(new BorderLayout(0, 14));
        setBackground(UITheme.BG_CANVAS);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        initUI();
        refreshAllData();
    }

    private void initUI() {
        // --- TOP: Header Title & Refresh ---
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("ADMIN DASHBOARD & AUDIT REPORTS",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.REPORTS, 24, UITheme.TEXT_DARK), SwingConstants.LEFT);
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.TEXT_DARK);
        header.add(title, BorderLayout.WEST);

        ModernButton btnRefresh = ModernButton.createSecondary("Refresh Live Audit",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.REFRESH, 16, UITheme.TEXT_DARK));
        btnRefresh.addActionListener(e -> refreshAllData());
        header.add(btnRefresh, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // --- CENTER: Tabbed Navigation ---
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 12));

        tabbedPane.addTab("Executive Audit & Payment Intelligence", buildExecutiveDashboardTab());
        tabbedPane.addTab("Past Invoices & PDF Reprint", buildPastInvoicesTab());
        tabbedPane.addTab("FASTag Accounts Management (Simulated)", buildFastagManagementTab());
        tabbedPane.addTab("Database Backup & DPDP Retention (Admin)", buildBackupAndRetentionTab());

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel buildExecutiveDashboardTab() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);
        container.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

        // 1. Control & Export Toolbar
        RoundedPanel toolbar = new RoundedPanel(12, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        toolbar.setLayout(new BorderLayout(10, 0));
        toolbar.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        toolbar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

        JPanel rangeGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        rangeGroup.setOpaque(false);
        rangeGroup.add(new JLabel("AUDIT TIMELINE:"));

        String[] ranges = {"Today", "7 Days", "30 Days", "All Time"};
        for (String r : ranges) {
            JButton btnR = new JButton(r);
            btnR.setFont(new Font("Segoe UI", Font.BOLD, 11));
            btnR.setFocusPainted(false);
            btnR.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnR.addActionListener(e -> {
                activeRange = r;
                refreshAllData();
            });
            rangeGroup.add(btnR);
        }
        toolbar.add(rangeGroup, BorderLayout.WEST);

        JPanel exportGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        exportGroup.setOpaque(false);

        ModernButton btnExportCsv = ModernButton.createSecondary("Export CSV",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.DOWNLOAD, 14, UITheme.TEXT_DARK));
        btnExportCsv.addActionListener(e -> exportCsv());
        exportGroup.add(btnExportCsv);

        ModernButton btnExportPdf = new ModernButton("Export PDF Audit",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.PRINT, 14, Color.WHITE),
                UITheme.PRIMARY_BLUE, UITheme.PRIMARY_HOVER, Color.WHITE);
        btnExportPdf.addActionListener(e -> exportPdf());
        exportGroup.add(btnExportPdf);

        toolbar.add(exportGroup, BorderLayout.EAST);
        container.add(toolbar);
        container.add(Box.createVerticalStrut(10));

        // 2. 8 Top KPI Metric Cards Grid (1 row x 8 cols)
        JPanel kpiGrid = new JPanel(new GridLayout(1, 8, 8, 0));
        kpiGrid.setOpaque(false);
        kpiGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));

        kpiTotal = new ReportKPICard("Capacity", "150", VectorIconUtil.IconType.GRID, new Color(219, 234, 254), new Color(29, 78, 216));
        kpiOccupied = new ReportKPICard("Occupied", "35", VectorIconUtil.IconType.CAR, new Color(254, 226, 226), new Color(185, 28, 28));
        kpiReserved = new ReportKPICard("Reserved", "6", VectorIconUtil.IconType.CLOCK, new Color(254, 243, 199), new Color(180, 83, 9));
        kpiMaint = new ReportKPICard("Maintenance", "3", VectorIconUtil.IconType.WRENCH, new Color(241, 245, 249), new Color(100, 116, 139));
        kpiAvail = new ReportKPICard("Available", "106", VectorIconUtil.IconType.CHECK, new Color(220, 252, 231), new Color(22, 101, 52));
        kpiOccPct = new ReportKPICard("Occupancy %", "23.3%", VectorIconUtil.IconType.DASHBOARD, new Color(237, 233, 254), new Color(109, 40, 217));
        kpiTodayRev = new ReportKPICard("Today Revenue", "\u20B91,580", VectorIconUtil.IconType.PAYMENT, new Color(220, 252, 231), new Color(22, 101, 52));
        kpiMonthRev = new ReportKPICard("Month Revenue", "\u20B942,650", VectorIconUtil.IconType.PAYMENT, new Color(219, 234, 254), new Color(29, 78, 216));

        kpiGrid.add(kpiTotal);
        kpiGrid.add(kpiOccupied);
        kpiGrid.add(kpiReserved);
        kpiGrid.add(kpiMaint);
        kpiGrid.add(kpiAvail);
        kpiGrid.add(kpiOccPct);
        kpiGrid.add(kpiTodayRev);
        kpiGrid.add(kpiMonthRev);

        container.add(kpiGrid);
        container.add(Box.createVerticalStrut(10));

        // 3. Middle Row: Floor Progress Bars & Revenue Distribution
        JPanel middleRow = new JPanel(new GridLayout(1, 2, 10, 0));
        middleRow.setOpaque(false);
        middleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        // Card A: Floor Level Utilization Bars
        RoundedPanel floorCard = new RoundedPanel(12, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        floorCard.setLayout(new GridLayout(3, 1, 0, 6));
        floorCard.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        barFloor1 = new JProgressBar(0, 100);
        lblFloor1 = new JLabel("L1 Ground:");
        lblFloor1.setFont(new Font("Segoe UI", Font.BOLD, 11));
        JPanel f1 = new JPanel(new BorderLayout(8, 0));
        f1.setOpaque(false);
        f1.add(lblFloor1, BorderLayout.WEST);
        f1.add(barFloor1, BorderLayout.CENTER);
        floorCard.add(f1);

        barFloor2 = new JProgressBar(0, 100);
        lblFloor2 = new JLabel("L2 EV Hub:");
        lblFloor2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        JPanel f2 = new JPanel(new BorderLayout(8, 0));
        f2.setOpaque(false);
        f2.add(lblFloor2, BorderLayout.WEST);
        f2.add(barFloor2, BorderLayout.CENTER);
        floorCard.add(f2);

        barFloor3 = new JProgressBar(0, 100);
        lblFloor3 = new JLabel("L3 Heavy/VIP:");
        lblFloor3.setFont(new Font("Segoe UI", Font.BOLD, 11));
        JPanel f3 = new JPanel(new BorderLayout(8, 0));
        f3.setOpaque(false);
        f3.add(lblFloor3, BorderLayout.WEST);
        f3.add(barFloor3, BorderLayout.CENTER);
        floorCard.add(f3);

        middleRow.add(floorCard);

        // Card B: Revenue by Method
        RoundedPanel revCard = new RoundedPanel(12, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        revCard.setLayout(new GridLayout(2, 2, 10, 6));
        revCard.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        lblRevUpi = new JLabel("UPI (QR): \u20B90");
        lblRevUpi.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblRevUpi.setForeground(new Color(22, 101, 52));

        lblRevFastag = new JLabel("FASTag: \u20B90");
        lblRevFastag.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblRevFastag.setForeground(new Color(29, 78, 216));

        lblRevCash = new JLabel("Cash: \u20B90");
        lblRevCash.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblRevCash.setForeground(new Color(180, 83, 9));

        lblRevCard = new JLabel("Card: \u20B90");
        lblRevCard.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblRevCard.setForeground(new Color(109, 40, 217));

        revCard.add(lblRevUpi);
        revCard.add(lblRevFastag);
        revCard.add(lblRevCash);
        revCard.add(lblRevCard);
        middleRow.add(revCard);

        container.add(middleRow);
        container.add(Box.createVerticalStrut(10));

        // 4. Settled Payment Audit Trail Table (Widened, Full GST Breakdown)
        RoundedPanel txnCard = new RoundedPanel(12, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        txnCard.setLayout(new BorderLayout(0, 8));
        txnCard.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        // Filter Bar for Audit Trail
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterBar.setOpaque(false);

        JLabel trailTitle = new JLabel("Settled Payment Audit Trail (Full GST Breakdown)");
        trailTitle.setFont(UITheme.FONT_SUBTITLE);
        trailTitle.setForeground(UITheme.TEXT_DARK);
        filterBar.add(trailTitle);

        filterBar.add(new JLabel("Search:"));
        auditSearchField = new JTextField(12);
        auditSearchField.addActionListener(e -> filterAuditTable());
        filterBar.add(auditSearchField);

        filterBar.add(new JLabel("Method:"));
        methodFilterBox = new JComboBox<>(new String[]{"ALL", "UPI", "FASTAG", "CASH", "CARD"});
        methodFilterBox.addActionListener(e -> filterAuditTable());
        filterBar.add(methodFilterBox);

        filterBar.add(new JLabel("Status:"));
        statusFilterBox = new JComboBox<>(new String[]{"ALL", "COMPLETED", "REFUNDED"});
        statusFilterBox.addActionListener(e -> filterAuditTable());
        filterBar.add(statusFilterBox);

        txnCard.add(filterBar, BorderLayout.NORTH);

        String[] cols = {
                "Invoice #", "Txn Ref", "Target", "Method", "Taxable (\u20B9)",
                "CGST (9%)", "SGST (9%)", "Round-Off", "Grand Total", "Status", "Timestamp"
        };
        txnTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        txnTable = new JTable(txnTableModel);
        txnTable.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        txnTable.setRowHeight(26);
        txnTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        txnTable.getTableHeader().setBackground(new Color(241, 245, 249));
        txnTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        int[] widths = {120, 130, 80, 80, 80, 75, 75, 75, 90, 80, 140};
        for (int i = 0; i < widths.length && i < txnTable.getColumnCount(); i++) {
            txnTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            txnTable.getColumnModel().getColumn(i).setMinWidth(widths[i] - 10);
        }

        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int c : new int[]{4, 5, 6, 7, 8}) {
            txnTable.getColumnModel().getColumn(c).setCellRenderer(rightRenderer);
        }

        txnCard.add(new JScrollPane(txnTable, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);

        // Pagination Bar
        JPanel paginationBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        paginationBar.setOpaque(false);

        JButton btnPrev = new JButton("<< Prev");
        btnPrev.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        btnPrev.addActionListener(e -> {
            if (currentPage > 1) {
                currentPage--;
                renderAuditPage();
            }
        });
        paginationBar.add(btnPrev);

        paginationLabel = new JLabel("Page 1 of 1");
        paginationLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        paginationBar.add(paginationLabel);

        JButton btnNext = new JButton("Next >>");
        btnNext.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        btnNext.addActionListener(e -> {
            int maxPages = Math.max(1, (int) Math.ceil((double) filteredTransactions.size() / pageSize));
            if (currentPage < maxPages) {
                currentPage++;
                renderAuditPage();
            }
        });
        paginationBar.add(btnNext);

        txnCard.add(paginationBar, BorderLayout.SOUTH);
        container.add(txnCard);

        JScrollPane scrollPane = new JScrollPane(container);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(scrollPane, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildPastInvoicesTab() {
        RoundedPanel card = new RoundedPanel(16, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

        // Top Filter Bar
        JPanel filterBar = new JPanel(new BorderLayout(10, 0));
        filterBar.setOpaque(false);

        JPanel leftSearch = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftSearch.setOpaque(false);
        leftSearch.add(new JLabel("Search Invoices (UP Plate or Invoice #):"));
        invoiceSearchField = new JTextField(18);
        UITheme.styleTextField(invoiceSearchField);
        invoiceSearchField.addActionListener(e -> searchInvoices());
        leftSearch.add(invoiceSearchField);

        JButton btnSearch = new JButton("Search", VectorIconUtil.getIcon(VectorIconUtil.IconType.SEARCH, 14, UITheme.TEXT_DARK));
        btnSearch.addActionListener(e -> searchInvoices());
        leftSearch.add(btnSearch);

        JButton btnReset = new JButton("Show All");
        btnReset.addActionListener(e -> {
            invoiceSearchField.setText("");
            refreshInvoices();
        });
        leftSearch.add(btnReset);

        filterBar.add(leftSearch, BorderLayout.WEST);

        // Action buttons
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightActions.setOpaque(false);

        JButton btnPreviewHtml = new JButton("View HTML Receipt",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.EYE, 14, UITheme.TEXT_DARK));
        btnPreviewHtml.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnPreviewHtml.addActionListener(e -> previewSelectedInvoiceHtml());
        rightActions.add(btnPreviewHtml);

        JButton btnDownloadPdf = new JButton("Download PDF (A4)",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.DOWNLOAD, 14, UITheme.TEXT_DARK));
        btnDownloadPdf.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnDownloadPdf.setBackground(new Color(224, 242, 254));
        btnDownloadPdf.addActionListener(e -> downloadSelectedInvoicePdf());
        rightActions.add(btnDownloadPdf);

        filterBar.add(rightActions, BorderLayout.EAST);
        card.add(filterBar, BorderLayout.NORTH);

        // Invoices Table
        String[] invCols = {"Invoice No", "Date", "License Plate", "District", "Class", "Taxable (\u20B9)", "Grand Total (\u20B9)", "Method", "Txn Ref"};
        invoiceTableModel = new DefaultTableModel(invCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        invoiceTable = new JTable(invoiceTableModel);
        invoiceTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        invoiceTable.setRowHeight(28);
        invoiceTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        invoiceTable.getTableHeader().setBackground(new Color(241, 245, 249));

        int[] invWidths = {130, 130, 100, 110, 90, 85, 95, 80, 140};
        for (int i = 0; i < invWidths.length && i < invoiceTable.getColumnCount(); i++) {
            invoiceTable.getColumnModel().getColumn(i).setPreferredWidth(invWidths[i]);
        }

        card.add(new JScrollPane(invoiceTable), BorderLayout.CENTER);
        return card;
    }

    private JPanel buildFastagManagementTab() {
        RoundedPanel card = new RoundedPanel(16, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

        // Header and Action Toolbar
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);

        JLabel fTitle = new JLabel("FASTag Electronic Toll & Parking RFID Tag Registry (Simulated)");
        fTitle.setFont(UITheme.FONT_SUBTITLE);
        fTitle.setForeground(UITheme.TEXT_DARK);
        topRow.add(fTitle, BorderLayout.WEST);

        JPanel actionBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionBtns.setOpaque(false);

        JButton btnTopUp = new JButton("Top Up Balance",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.PLUS, 14, UITheme.TEXT_DARK));
        btnTopUp.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnTopUp.setBackground(new Color(220, 252, 231));
        btnTopUp.addActionListener(e -> topUpFastag());
        actionBtns.add(btnTopUp);

        JButton btnToggleStatus = new JButton("Block / Activate Tag",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.CROSS, 14, UITheme.TEXT_DARK));
        btnToggleStatus.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnToggleStatus.addActionListener(e -> toggleFastagStatus());
        actionBtns.add(btnToggleStatus);

        JButton btnLinkNew = new JButton("Link New Vehicle Tag",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.TICKET, 14, UITheme.TEXT_DARK));
        btnLinkNew.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnLinkNew.addActionListener(e -> linkNewFastag());
        actionBtns.add(btnLinkNew);

        topRow.add(actionBtns, BorderLayout.EAST);
        card.add(topRow, BorderLayout.NORTH);

        // FASTag Table
        String[] fastagCols = {"Tag ID", "Vehicle Plate", "RTO District", "Balance (\u20B9)", "Status", "Updated At"};
        fastagTableModel = new DefaultTableModel(fastagCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        fastagTable = new JTable(fastagTableModel);
        fastagTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        fastagTable.setRowHeight(28);
        fastagTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        fastagTable.getTableHeader().setBackground(new Color(241, 245, 249));

        card.add(new JScrollPane(fastagTable), BorderLayout.CENTER);
        return card;
    }

    public void refreshAllData() {
        refreshKPICards();
        refreshFloorProgress();
        refreshAuditTable();
        refreshInvoices();
        refreshFastagAccounts();
    }

    public void refreshReport() {
        refreshAllData();
    }

    private void refreshKPICards() {
        int totalCap = slotDao.countTotalSlots();
        int occ = slotDao.countByStatus("OCCUPIED");
        int res = slotDao.countByStatus("RESERVED");
        int maint = slotDao.countByStatus("MAINTENANCE");
        int avail = slotDao.countByStatus("AVAILABLE");
        double occPct = totalCap > 0 ? (occ * 100.0 / totalCap) : 0.0;

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        double todayRev = paymentDao.getRevenueForDate(today);
        double monthRev = paymentDao.getMonthRevenue(today.getYear(), today.getMonthValue());

        kpiTotal.setValue(String.valueOf(totalCap));
        kpiOccupied.setValue(String.valueOf(occ));
        kpiReserved.setValue(String.valueOf(res));
        kpiMaint.setValue(String.valueOf(maint));
        kpiAvail.setValue(String.valueOf(avail));
        kpiOccPct.setValue(String.format("%.1f%%", occPct));
        kpiTodayRev.setValue(MoneyFormat.format(todayRev));
        kpiMonthRev.setValue(MoneyFormat.format(monthRev));
    }

    private void refreshFloorProgress() {
        for (int f = 1; f <= 3; f++) {
            var slots = slotDao.findByFloor(f);
            int total = slots.size();
            long occ = slots.stream().filter(s -> "OCCUPIED".equalsIgnoreCase(s.getStatus())).count();
            int pct = total > 0 ? (int) Math.round(occ * 100.0 / total) : 0;

            if (f == 1) {
                lblFloor1.setText(String.format("L1 Ground: %d/%d (%d%%)", occ, total, pct));
                barFloor1.setValue(pct);
            } else if (f == 2) {
                lblFloor2.setText(String.format("L2 EV Hub: %d/%d (%d%%)", occ, total, pct));
                barFloor2.setValue(pct);
            } else {
                lblFloor3.setText(String.format("L3 Heavy/VIP: %d/%d (%d%%)", occ, total, pct));
                barFloor3.setValue(pct);
            }
        }

        // Refresh Revenue Breakdown
        double upi = 0, fastag = 0, cash = 0, card = 0;
        List<PaymentTransaction> allTxns = paymentDao.findAll();
        for (PaymentTransaction t : allTxns) {
            if (t.getStatus() == com.smartparking.model.enums.PaymentStatus.COMPLETED) {
                switch (t.getPaymentMethod()) {
                    case UPI -> upi += t.getAmount();
                    case FASTAG -> fastag += t.getAmount();
                    case CASH -> cash += t.getAmount();
                    case CARD -> card += t.getAmount();
                }
            }
        }
        lblRevUpi.setText("UPI (QR): " + MoneyFormat.format(upi));
        lblRevFastag.setText("FASTag: " + MoneyFormat.format(fastag));
        lblRevCash.setText("Cash: " + MoneyFormat.format(cash));
        lblRevCard.setText("Card: " + MoneyFormat.format(card));
    }

    private void refreshAuditTable() {
        List<PaymentTransaction> txns = paymentDao.findAll();
        filteredTransactions = new ArrayList<>(txns);
        currentPage = 1;
        filterAuditTable();
    }

    private void filterAuditTable() {
        String q = (auditSearchField != null) ? auditSearchField.getText().trim().toLowerCase() : "";
        String m = (methodFilterBox != null) ? (String) methodFilterBox.getSelectedItem() : "ALL";
        String s = (statusFilterBox != null) ? (String) statusFilterBox.getSelectedItem() : "ALL";

        List<PaymentTransaction> all = paymentDao.findAll();
        filteredTransactions = all.stream().filter(t -> {
            boolean matchQ = q.isEmpty() ||
                    (t.getTransactionRef() != null && t.getTransactionRef().toLowerCase().contains(q)) ||
                    (t.getInvoiceNumber() != null && t.getInvoiceNumber().toLowerCase().contains(q));
            boolean matchM = "ALL".equalsIgnoreCase(m) || t.getPaymentMethod().name().equalsIgnoreCase(m);
            boolean matchS = "ALL".equalsIgnoreCase(s) || (t.getStatus() != null && t.getStatus().name().equalsIgnoreCase(s));
            return matchQ && matchM && matchS;
        }).collect(Collectors.toList());

        currentPage = 1;
        renderAuditPage();
    }

    private void renderAuditPage() {
        txnTableModel.setRowCount(0);
        int total = filteredTransactions.size();
        int maxPages = Math.max(1, (int) Math.ceil((double) total / pageSize));
        if (currentPage > maxPages) currentPage = maxPages;

        int start = (currentPage - 1) * pageSize;
        int end = Math.min(start + pageSize, total);

        for (int i = start; i < end; i++) {
            PaymentTransaction tx = filteredTransactions.get(i);
            String target = tx.getTicketId() != null ? ("Tkt #" + tx.getTicketId()) :
                    (tx.getMembershipId() != null ? ("Pass #" + tx.getMembershipId()) : ("Res #" + tx.getReservationId()));
            String inv = tx.getInvoiceNumber() != null ? tx.getInvoiceNumber() : "\u2014";

            txnTableModel.addRow(new Object[]{
                    inv,
                    tx.getTransactionRef(),
                    target,
                    tx.getPaymentMethod().name(),
                    MoneyFormat.format(tx.getTaxableValue()),
                    MoneyFormat.format(tx.getCgst()),
                    MoneyFormat.format(tx.getSgst()),
                    String.format("%+.2f", tx.getRoundOff()),
                    MoneyFormat.format(tx.getAmount()),
                    tx.getStatus() != null ? tx.getStatus().name() : "\u2014",
                    DateFormat.display(tx.getPaymentTime())
            });
        }

        if (paginationLabel != null) {
            paginationLabel.setText("Page " + currentPage + " of " + maxPages + " (" + total + " records)");
        }
    }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("parking_audit_report_" + LocalDate.now() + ".csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try (PrintWriter out = new PrintWriter(f)) {
                out.println("InvoiceNumber,TransactionRef,PaymentMethod,Taxable,CGST,SGST,RoundOff,GrandTotal,Status,Timestamp");
                for (PaymentTransaction tx : filteredTransactions) {
                    out.printf("%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%s,%s%n",
                            tx.getInvoiceNumber() != null ? tx.getInvoiceNumber() : "",
                            tx.getTransactionRef() != null ? tx.getTransactionRef() : "",
                            tx.getPaymentMethod().name(),
                            tx.getTaxableValue(),
                            tx.getCgst(),
                            tx.getSgst(),
                            tx.getRoundOff(),
                            tx.getAmount(),
                            tx.getStatus() != null ? tx.getStatus().name() : "",
                            tx.getPaymentTime() != null ? tx.getPaymentTime().toString() : "");
                }
                JOptionPane.showMessageDialog(this, "Audit records exported successfully to " + f.getName(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to export CSV: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportPdf() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("parking_executive_audit_" + LocalDate.now() + ".pdf"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try {
                Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
                PdfWriter.getInstance(doc, new FileOutputStream(f));
                doc.open();

                // Header
                var titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
                Paragraph p = new Paragraph("SMART PARKING SYSTEM — EXECUTIVE AUDIT REPORT", titleFont);
                p.setAlignment(Element.ALIGN_CENTER);
                doc.add(p);

                var subFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
                Paragraph sub = new Paragraph("Generated on: " + DateFormat.display(LocalDateTime.now()) + " (Asia/Kolkata) • Grand Tech Park Garage, Lucknow (UP)", subFont);
                sub.setAlignment(Element.ALIGN_CENTER);
                doc.add(sub);
                doc.add(new Paragraph(" "));

                // Audit Table
                PdfPTable table = new PdfPTable(9);
                table.setWidthPercentage(100);
                String[] pdfCols = {"Invoice #", "Txn Ref", "Method", "Taxable (₹)", "CGST (₹)", "SGST (₹)", "Total (₹)", "Status", "Timestamp"};
                for (String c : pdfCols) {
                    PdfPCell cell = new PdfPCell(new Phrase(c, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
                    cell.setBackgroundColor(new java.awt.Color(241, 245, 249));
                    table.addCell(cell);
                }

                for (PaymentTransaction tx : filteredTransactions) {
                    table.addCell(new Phrase(tx.getInvoiceNumber() != null ? tx.getInvoiceNumber() : "—", subFont));
                    table.addCell(new Phrase(tx.getTransactionRef() != null ? tx.getTransactionRef() : "—", subFont));
                    table.addCell(new Phrase(tx.getPaymentMethod().name(), subFont));
                    table.addCell(new Phrase(String.format("%.2f", tx.getTaxableValue()), subFont));
                    table.addCell(new Phrase(String.format("%.2f", tx.getCgst()), subFont));
                    table.addCell(new Phrase(String.format("%.2f", tx.getSgst()), subFont));
                    table.addCell(new Phrase(String.format("%.2f", tx.getAmount()), subFont));
                    table.addCell(new Phrase(tx.getStatus() != null ? tx.getStatus().name() : "—", subFont));
                    table.addCell(new Phrase(DateFormat.display(tx.getPaymentTime()), subFont));
                }

                doc.add(table);
                doc.close();

                JOptionPane.showMessageDialog(this, "Executive PDF Audit Report exported successfully to " + f.getName(), "PDF Exported", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to export PDF: " + ex.getMessage(), "PDF Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void refreshInvoices() {
        invoiceTableModel.setRowCount(0);
        List<Invoice> invoices = invoiceDao.findAll();
        for (Invoice inv : invoices) {
            invoiceTableModel.addRow(new Object[]{
                    inv.getInvoiceNumber(),
                    inv.getInvoiceDate(),
                    PlateUtil.format(inv.getLicensePlate()),
                    PlateUtil.district(inv.getLicensePlate()),
                    inv.getVehicleType().getDisplayName(),
                    MoneyFormat.format(inv.getTaxableValue()),
                    MoneyFormat.format(inv.getTotalAmount()),
                    inv.getPaymentMethod(),
                    inv.getTransactionRef()
            });
        }
    }

    private void searchInvoices() {
        String q = invoiceSearchField.getText().trim();
        if (q.isEmpty()) {
            refreshInvoices();
            return;
        }

        invoiceTableModel.setRowCount(0);
        List<Invoice> matches = invoiceDao.search(q);
        for (Invoice inv : matches) {
            invoiceTableModel.addRow(new Object[]{
                    inv.getInvoiceNumber(),
                    inv.getInvoiceDate(),
                    PlateUtil.format(inv.getLicensePlate()),
                    PlateUtil.district(inv.getLicensePlate()),
                    inv.getVehicleType().getDisplayName(),
                    MoneyFormat.format(inv.getTaxableValue()),
                    MoneyFormat.format(inv.getTotalAmount()),
                    inv.getPaymentMethod(),
                    inv.getTransactionRef()
            });
        }
    }

    private void previewSelectedInvoiceHtml() {
        int row = invoiceTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an invoice from the table first.", "Select Invoice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String invNo = (String) invoiceTable.getValueAt(row, 0);
        Optional<Invoice> opt = invoiceDao.findByInvoiceNumber(invNo);
        if (opt.isEmpty()) return;

        String html = invoiceService.generateHtmlReceipt(opt.get());
        JEditorPane editorPane = new JEditorPane("text/html", html);
        editorPane.setEditable(false);

        JScrollPane scroll = new JScrollPane(editorPane);
        scroll.setPreferredSize(new Dimension(520, 600));
        JOptionPane.showMessageDialog(this, scroll, "Tax Invoice Preview - " + invNo, JOptionPane.PLAIN_MESSAGE);
    }

    private void downloadSelectedInvoicePdf() {
        int row = invoiceTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an invoice from the table first.", "Select Invoice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String invNo = (String) invoiceTable.getValueAt(row, 0);
        Optional<Invoice> opt = invoiceDao.findByInvoiceNumber(invNo);
        if (opt.isEmpty()) return;

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("Tax_Invoice_" + invNo + ".pdf"));
        int userChoice = fileChooser.showSaveDialog(this);
        if (userChoice == JFileChooser.APPROVE_OPTION) {
            File targetFile = fileChooser.getSelectedFile();
            try {
                invoiceService.exportToPdf(opt.get(), targetFile);
                JOptionPane.showMessageDialog(this,
                        "Official GST Tax Invoice saved successfully to:\n" + targetFile.getAbsolutePath(),
                        "PDF Invoice Generated", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error generating PDF: " + ex.getMessage(), "PDF Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void refreshFastagAccounts() {
        fastagTableModel.setRowCount(0);
        List<FastagAccount> accounts = fastagDao.findAll();
        for (FastagAccount fa : accounts) {
            fastagTableModel.addRow(new Object[]{
                    fa.getTagId(),
                    PlateUtil.format(fa.getLicensePlate()),
                    PlateUtil.district(fa.getLicensePlate()),
                    MoneyFormat.format(fa.getBalance()),
                    fa.getStatus(),
                    fa.getUpdatedAt()
            });
        }
    }

    private void topUpFastag() {
        int row = fastagTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a FASTag account to top up.", "Top Up", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String tagId = (String) fastagTable.getValueAt(row, 0);
        String amountStr = JOptionPane.showInputDialog(this, "Enter top-up amount (\u20B9):", "Top Up FASTag", JOptionPane.PLAIN_MESSAGE);
        if (amountStr != null && !amountStr.isBlank()) {
            try {
                double amt = Double.parseDouble(amountStr.trim());
                if (amt <= 0) throw new IllegalArgumentException("Amount must be positive.");
                fastagService.topUp(tagId, amt, "admin", "Admin Portal manual top-up (Simulated)");
                JOptionPane.showMessageDialog(this, "\u20B9" + String.format("%.2f", amt) + " added to FASTag " + tagId, "Success", JOptionPane.INFORMATION_MESSAGE);
                refreshFastagAccounts();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid amount: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void toggleFastagStatus() {
        int row = fastagTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a FASTag account to block/activate.", "Toggle Status", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String tagId = (String) fastagTable.getValueAt(row, 0);
        String curStatus = (String) fastagTable.getValueAt(row, 4);
        try {
            if ("ACTIVE".equalsIgnoreCase(curStatus)) {
                fastagService.setStatus(tagId, "BLOCKED", "admin", "Administrative safety lock (Simulated)");
                JOptionPane.showMessageDialog(this, "FASTag " + tagId + " is now BLOCKED.", "Tag Blocked", JOptionPane.INFORMATION_MESSAGE);
            } else {
                fastagService.setStatus(tagId, "ACTIVE", "admin", "Administrative reactivation (Simulated)");
                JOptionPane.showMessageDialog(this, "FASTag " + tagId + " is now ACTIVE.", "Tag Activated", JOptionPane.INFORMATION_MESSAGE);
            }
            refreshFastagAccounts();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error updating status: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void linkNewFastag() {
        JTextField tagField = new JTextField("TAG-UP32-" + (int)(Math.random() * 899 + 100));
        JTextField plateField = new JTextField(PlateUtil.generateRandomUp());
        JTextField balField = new JTextField("500.00");

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.add(new JLabel("FASTag ID:"));
        form.add(tagField);
        form.add(new JLabel("License Plate:"));
        form.add(plateField);
        form.add(new JLabel("Initial Balance (\u20B9):"));
        form.add(balField);

        int res = JOptionPane.showConfirmDialog(this, form, "Link New FASTag RFID Account (Simulated)", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            try {
                String tid = tagField.getText().trim();
                String plate = plateField.getText().trim();
                double bal = Double.parseDouble(balField.getText().trim());

                if (!PlateUtil.isValid(plate)) {
                    JOptionPane.showMessageDialog(this, "Invalid UP plate format.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                fastagService.linkTag(tid, plate, bal, "admin");
                JOptionPane.showMessageDialog(this, "FASTag account linked successfully (Simulated).", "Success", JOptionPane.INFORMATION_MESSAGE);
                refreshFastagAccounts();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to register account: " + ex.getMessage(), "Registration Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Reusable KPI card for executive report header.
     */
    private static class ReportKPICard extends JPanel {
        private final JLabel valueLabel;

        public ReportKPICard(String title, String initialVal, VectorIconUtil.IconType iconType, Color iconBg, Color iconFg) {
            setLayout(new FlowLayout(FlowLayout.LEFT, 6, 6));
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(226, 232, 240), 1, true),
                    BorderFactory.createEmptyBorder(2, 4, 2, 4)
            ));

            JPanel badge = new JPanel(new GridBagLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(iconBg);
                    g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            badge.setPreferredSize(new Dimension(32, 32));
            badge.setOpaque(false);
            badge.add(new JLabel(VectorIconUtil.getIcon(iconType, 16, iconFg)));
            add(badge);

            JPanel text = new JPanel();
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setOpaque(false);

            JLabel tLbl = new JLabel(title);
            tLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
            tLbl.setForeground(new Color(100, 116, 139));

            valueLabel = new JLabel(initialVal);
            valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            valueLabel.setForeground(new Color(15, 23, 42));

            text.add(tLbl);
            text.add(valueLabel);
            add(text);
        }

        public void setValue(String val) {
            valueLabel.setText(val);
            revalidate();
            repaint();
        }
    }

    private JPanel buildBackupAndRetentionTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // 1. Live Database Backup Card
        RoundedPanel backupCard = new RoundedPanel(12, Color.WHITE);
        backupCard.setLayout(new BorderLayout(16, 16));
        backupCard.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JPanel bHeader = new JPanel(new GridLayout(2, 1, 0, 4));
        bHeader.setOpaque(false);
        JLabel bTitle = new JLabel("Live SQLite Database Backup (WAL Point-in-Time Snapshot)");
        bTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        bTitle.setForeground(new Color(15, 23, 42));
        JLabel bSub = new JLabel("Uses SQLite VACUUM INTO to capture safe live snapshots without database locking. Retains the latest 7 backups.");
        bSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        bSub.setForeground(new Color(100, 116, 139));
        bHeader.add(bTitle);
        bHeader.add(bSub);
        backupCard.add(bHeader, BorderLayout.CENTER);

        ModernButton btnBackup = new ModernButton("Backup Database Now", null,
                new Color(37, 99, 235), new Color(29, 78, 216), Color.WHITE);
        btnBackup.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnBackup.addActionListener(e -> {
            try {
                File f = DatabaseBackupService.getInstance().backupDatabaseNow();
                JOptionPane.showMessageDialog(this, "Live database backup successfully created at:\n" + f.getAbsolutePath(), "Backup Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Backup failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        backupCard.add(btnBackup, BorderLayout.EAST);
        panel.add(backupCard);
        panel.add(Box.createVerticalStrut(16));

        // 2. DPDP Statutory Retention Card
        RoundedPanel retentionCard = new RoundedPanel(12, Color.WHITE);
        retentionCard.setLayout(new BorderLayout(16, 16));
        retentionCard.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JPanel rHeader = new JPanel(new GridLayout(2, 1, 0, 4));
        rHeader.setOpaque(false);
        JLabel rTitle = new JLabel("DPDP Act, 2023 Statutory Data Retention & Anonymization");
        rTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        rTitle.setForeground(new Color(15, 23, 42));
        JLabel rSub = new JLabel("Anonymizes tickets older than 365 days (UP00XX0000) and deletes ANPR camera snapshots older than 30 days. Invoices preserved per GST compliance.");
        rSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        rSub.setForeground(new Color(100, 116, 139));
        rHeader.add(rTitle);
        rHeader.add(rSub);
        retentionCard.add(rHeader, BorderLayout.CENTER);

        ModernButton btnRetention = new ModernButton("Run Retention Job Now", null,
                new Color(16, 185, 129), new Color(5, 150, 105), Color.WHITE);
        btnRetention.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnRetention.addActionListener(e -> {
            try {
                DataRetentionService.RetentionReport report = DataRetentionService.getInstance().runRetentionJob();
                JOptionPane.showMessageDialog(this,
                        "DPDP Retention Cycle Completed:\n\n" +
                                "• Historical Tickets Anonymized (> " + report.ticketRetentionDays() + " days): " + report.ticketsAnonymized() + "\n" +
                                "• Expired ANPR Snapshots Purged (> " + report.imageRetentionDays() + " days): " + report.imagesDeleted() + "\n" +
                                "• GST Tax Invoices: Strictly Preserved per Statutory Accounting Rules.",
                        "Retention Job Report", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Retention job error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        retentionCard.add(btnRetention, BorderLayout.EAST);
        panel.add(retentionCard);
        panel.add(Box.createVerticalStrut(16));

        // 3. Customer Erasure Requests Card
        RoundedPanel reqCard = new RoundedPanel(12, Color.WHITE);
        reqCard.setLayout(new BorderLayout(12, 12));
        reqCard.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JLabel reqTitle = new JLabel("Pending DPDP Customer Erasure Requests (Section 12)");
        reqTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        reqTitle.setForeground(new Color(15, 23, 42));
        reqCard.add(reqTitle, BorderLayout.NORTH);

        DefaultTableModel reqModel = new DefaultTableModel(new String[]{"Req ID", "User ID", "Username", "Mobile", "Requested At (IST)", "Status"}, 0);
        JTable reqTable = new JTable(reqModel);
        reqTable.setRowHeight(28);

        List<DataRetentionService.DeletionRequest> pending = DataRetentionService.getInstance().getPendingDeletionRequests();
        for (DataRetentionService.DeletionRequest dr : pending) {
            reqModel.addRow(new Object[]{dr.id(), dr.userId(), dr.username(), dr.mobile(), DateFormat.display(dr.requestedAt()), dr.status()});
        }

        reqCard.add(new JScrollPane(reqTable), BorderLayout.CENTER);

        JPanel actionB = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actionB.setOpaque(false);
        ModernButton btnApprove = new ModernButton("Approve Selected Erasure Request", null,
                new Color(239, 68, 68), new Color(220, 38, 38), Color.WHITE);
        btnApprove.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnApprove.addActionListener(e -> {
            int sel = reqTable.getSelectedRow();
            if (sel >= 0) {
                int reqId = (int) reqModel.getValueAt(sel, 0);
                try {
                    DataRetentionService.getInstance().approveDeletion(reqId, "admin");
                    JOptionPane.showMessageDialog(this, "Erasure request #" + reqId + " approved and personal data anonymized.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    reqModel.removeRow(sel);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Approval failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Select a pending request to approve.", "Notice", JOptionPane.WARNING_MESSAGE);
            }
        });
        actionB.add(btnApprove);
        reqCard.add(actionB, BorderLayout.SOUTH);
        panel.add(reqCard);

        return panel;
    }
}
