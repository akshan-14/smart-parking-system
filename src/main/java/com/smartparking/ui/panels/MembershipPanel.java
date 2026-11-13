package com.smartparking.ui.panels;

import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.invoice.Invoice;
import com.smartparking.model.membership.MembershipPass;
import com.smartparking.model.tariff.TariffRate;
import com.smartparking.service.GstCalculator;
import com.smartparking.service.InvoiceService;
import com.smartparking.service.MembershipService;
import com.smartparking.service.TariffService;
import com.smartparking.ui.components.ModernButton;
import com.smartparking.ui.components.RoundedPanel;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.ImageLoader;
import com.smartparking.util.DateFormat;
import com.smartparking.util.MoneyFormat;
import com.smartparking.util.PlateUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Panel managing flat monthly vehicle passes, pass issuance, verification,
 * renewals, cancellations, and member audits.
 */
public class MembershipPanel extends JPanel {

    private final MembershipService membershipService;
    private final TariffService tariffService;

    private JTextField plateField;
    private JComboBox<VehicleType> vehicleTypeBox;
    private JComboBox<Integer> durationBox;
    private JLabel lblRatePreview;
    private JLabel lblExpiryPreview;
    private JLabel lblTotalCostPreview;

    private JTextField lookupField;
    private JTextArea statusArea;

    private DefaultTableModel memberTableModel;
    private JTable memberTable;

    public MembershipPanel(MembershipService membershipService) {
        this.membershipService = membershipService;
        this.tariffService = new TariffService();

        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_CANVAS);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        initUI();
        refreshMemberList();
    }

    private void initUI() {
        // --- TOP: Header Title ---
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("MONTHLY MEMBERSHIP PASSES & FLAT SUBSCRIPTIONS", ImageLoader.getNavIcon("membership", 24), SwingConstants.LEFT);
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.TEXT_DARK);
        header.add(title, BorderLayout.WEST);

        ModernButton btnRefresh = ModernButton.createSecondary("Refresh Directory", ImageLoader.getNavIcon("reports", 16));
        btnRefresh.addActionListener(e -> refreshMemberList());
        header.add(btnRefresh, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // --- CENTER: Split Layout (Top: Issue & Verify | Bottom: Active Member Directory) ---
        JPanel centerContainer = new JPanel();
        centerContainer.setLayout(new BoxLayout(centerContainer, BoxLayout.Y_AXIS));
        centerContainer.setOpaque(false);

        JPanel topSplit = new JPanel(new GridLayout(1, 2, 16, 0));
        topSplit.setOpaque(false);
        topSplit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 380));
        topSplit.setPreferredSize(new Dimension(1000, 360));

        // LEFT: Issue Flat Pass Card
        RoundedPanel purchaseCard = new RoundedPanel(16, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        purchaseCard.setLayout(new BoxLayout(purchaseCard, BoxLayout.Y_AXIS));
        purchaseCard.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));

        JLabel pTitle = new JLabel("Issue Flat Monthly Pass");
        pTitle.setFont(UITheme.FONT_SUBTITLE);
        pTitle.setForeground(UITheme.TEXT_DARK);
        purchaseCard.add(pTitle);
        purchaseCard.add(Box.createVerticalStrut(10));

        purchaseCard.add(new JLabel("License Plate Number:"));
        purchaseCard.add(Box.createVerticalStrut(4));
        JPanel plateRow = new JPanel(new BorderLayout(8, 0));
        plateRow.setOpaque(false);
        plateRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        plateField = new JTextField(PlateUtil.generateRandomUp());
        UITheme.styleTextField(plateField);
        plateRow.add(plateField, BorderLayout.CENTER);

        JButton btnGen = new JButton("🎲");
        btnGen.setToolTipText("Generate Random UP Plate");
        btnGen.addActionListener(e -> plateField.setText(PlateUtil.generateRandomUp()));
        plateRow.add(btnGen, BorderLayout.EAST);
        purchaseCard.add(plateRow);
        purchaseCard.add(Box.createVerticalStrut(8));

        purchaseCard.add(new JLabel("Vehicle Class (Flat Monthly Model):"));
        purchaseCard.add(Box.createVerticalStrut(4));
        vehicleTypeBox = new JComboBox<>(VehicleType.values());
        vehicleTypeBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        UITheme.styleComboBox(vehicleTypeBox);
        vehicleTypeBox.addActionListener(e -> updatePricingPreview());
        purchaseCard.add(vehicleTypeBox);
        purchaseCard.add(Box.createVerticalStrut(8));

        purchaseCard.add(new JLabel("Duration (Months):"));
        purchaseCard.add(Box.createVerticalStrut(4));
        durationBox = new JComboBox<>(new Integer[]{1, 3, 6, 12});
        durationBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        UITheme.styleComboBox(durationBox);
        durationBox.addActionListener(e -> updatePricingPreview());
        purchaseCard.add(durationBox);
        purchaseCard.add(Box.createVerticalStrut(10));

        // Pricing summary preview box
        JPanel priceSummary = new JPanel(new GridLayout(3, 1, 2, 2));
        priceSummary.setBackground(new Color(248, 250, 252));
        priceSummary.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        priceSummary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));

        lblRatePreview = new JLabel("Monthly Fee: ₹1,500.00 / month");
        lblRatePreview.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblExpiryPreview = new JLabel("Expires On: —");
        lblExpiryPreview.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblTotalCostPreview = new JLabel("TOTAL PRICE DUE: ₹1,500.00");
        lblTotalCostPreview.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblTotalCostPreview.setForeground(new Color(29, 78, 216));

        priceSummary.add(lblRatePreview);
        priceSummary.add(lblExpiryPreview);
        priceSummary.add(lblTotalCostPreview);
        purchaseCard.add(priceSummary);
        purchaseCard.add(Box.createVerticalStrut(10));

        ModernButton btnBuy = new ModernButton("Issue Pass (Simulated Payment)", ImageLoader.getNavIcon("membership", 18));
        btnBuy.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnBuy.addActionListener(e -> purchasePass());
        purchaseCard.add(btnBuy);

        topSplit.add(purchaseCard);

        // RIGHT: Pass Verification & Lookup Card
        RoundedPanel lookupCard = new RoundedPanel(16, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        lookupCard.setLayout(new BoxLayout(lookupCard, BoxLayout.Y_AXIS));
        lookupCard.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));

        JLabel vTitle = new JLabel("Verify Pass Status");
        vTitle.setFont(UITheme.FONT_SUBTITLE);
        vTitle.setForeground(UITheme.TEXT_DARK);
        lookupCard.add(vTitle);
        lookupCard.add(Box.createVerticalStrut(10));

        lookupCard.add(new JLabel("License Plate to Query:"));
        lookupCard.add(Box.createVerticalStrut(4));
        lookupField = new JTextField("UP16CD2345");
        lookupField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        UITheme.styleTextField(lookupField);
        lookupCard.add(lookupField);
        lookupCard.add(Box.createVerticalStrut(8));

        ModernButton btnLookup = ModernButton.createSecondary("Verify Pass Status", ImageLoader.getNavIcon("search", 16));
        btnLookup.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnLookup.addActionListener(e -> checkPass());
        lookupCard.add(btnLookup);
        lookupCard.add(Box.createVerticalStrut(10));

        statusArea = new JTextArea();
        statusArea.setFont(UITheme.FONT_MONO);
        statusArea.setEditable(false);
        statusArea.setBackground(new Color(248, 250, 252));
        statusArea.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        statusArea.setText("\n  Enter a license plate and click\n  'Verify Pass Status' above.");
        lookupCard.add(new JScrollPane(statusArea));

        topSplit.add(lookupCard);
        centerContainer.add(topSplit);
        centerContainer.add(Box.createVerticalStrut(14));

        // BOTTOM: Member Directory Table with Renew & Cancel Actions
        RoundedPanel tableCard = new RoundedPanel(16, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        tableCard.setLayout(new BorderLayout(0, 8));
        tableCard.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

        JPanel tableHeader = new JPanel(new BorderLayout());
        tableHeader.setOpaque(false);
        JLabel dirTitle = new JLabel("Registered Monthly Pass Holders Directory");
        dirTitle.setFont(UITheme.FONT_SUBTITLE);
        dirTitle.setForeground(UITheme.TEXT_DARK);
        tableHeader.add(dirTitle, BorderLayout.WEST);

        JPanel tableActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tableActions.setOpaque(false);
        ModernButton btnRenew = ModernButton.createSecondary("Renew Pass (+1 Mo)", ImageLoader.getNavIcon("entry", 14));
        btnRenew.addActionListener(e -> renewSelectedPass());
        ModernButton btnCancel = ModernButton.createDanger("Cancel Pass", ImageLoader.getNavIcon("exit", 14));
        btnCancel.addActionListener(e -> cancelSelectedPass());
        tableActions.add(btnRenew);
        tableActions.add(btnCancel);
        tableHeader.add(tableActions, BorderLayout.EAST);

        tableCard.add(tableHeader, BorderLayout.NORTH);

        String[] cols = {"Pass ID", "License Plate", "District", "Vehicle Class", "Duration", "Monthly Fee", "Total Paid", "Start Date", "Expiry Date", "Days Left", "Status"};
        memberTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        memberTable = new JTable(memberTableModel);
        memberTable.setFont(UITheme.FONT_REGULAR);
        memberTable.setRowHeight(30);
        memberTable.getTableHeader().setFont(UITheme.FONT_BOLD);
        memberTable.getTableHeader().setBackground(new Color(241, 245, 249));

        tableCard.add(new JScrollPane(memberTable), BorderLayout.CENTER);
        centerContainer.add(tableCard);

        add(centerContainer, BorderLayout.CENTER);

        updatePricingPreview();
    }

    private void updatePricingPreview() {
        VehicleType vt = (VehicleType) vehicleTypeBox.getSelectedItem();
        Integer duration = (Integer) durationBox.getSelectedItem();
        if (vt == null) vt = VehicleType.CAR;
        if (duration == null) duration = 1;

        TariffRate tariff = tariffService.getTariffRate(vt);
        double monthly = tariff.getMonthlyPassRate();
        double total = monthly * duration;
        LocalDate expiry = LocalDate.now().plusMonths(duration);

        lblRatePreview.setText(String.format("Monthly Fee: %s / month", MoneyFormat.format(monthly)));
        lblExpiryPreview.setText(String.format("Expires On: %s", DateFormat.displayDate(expiry)));
        lblTotalCostPreview.setText(String.format("TOTAL PRICE DUE: %s", MoneyFormat.format(total)));
    }

    private PaymentMethodType promptPassPayment(String title, String plate, VehicleType type, int durationMonths, double baseAmount) {
        GstCalculator gstCalc = new GstCalculator();
        GstCalculator.GstResult gst = gstCalc.calculate(baseAmount);

        JPanel panel = new JPanel(new GridLayout(6, 1, 4, 4));
        panel.add(new JLabel(String.format("Vehicle: %s (%s) \u2022 Duration: %d month(s)", PlateUtil.format(plate), type.getDisplayName(), durationMonths)));
        panel.add(new JLabel("Base Pass Fee (Taxable): " + MoneyFormat.format(gst.taxableValue())));
        panel.add(new JLabel(String.format("GST (18%%): CGST (9%%) %s + SGST (9%%) %s", MoneyFormat.format(gst.cgstAmount()), MoneyFormat.format(gst.sgstAmount()))));
        panel.add(new JLabel("Round-off: " + String.format("%+.2f", gst.roundOff())));

        JLabel lblTotal = new JLabel("TOTAL AMOUNT DUE: " + MoneyFormat.format(gst.grandTotal()));
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblTotal.setForeground(new Color(29, 78, 216));
        panel.add(lblTotal);

        JComboBox<PaymentMethodType> methodCombo = new JComboBox<>(new PaymentMethodType[]{
                PaymentMethodType.UPI, PaymentMethodType.CARD, PaymentMethodType.CASH, PaymentMethodType.WALLET
        });
        UITheme.styleComboBox(methodCombo);
        panel.add(methodCombo);

        int res = JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            return (PaymentMethodType) methodCombo.getSelectedItem();
        }
        return null;
    }

    private void purchasePass() {
        String plate = plateField.getText().trim();
        VehicleType type = (VehicleType) vehicleTypeBox.getSelectedItem();
        Integer duration = (Integer) durationBox.getSelectedItem();

        if (plate.isEmpty() || !PlateUtil.isValid(plate)) {
            JOptionPane.showMessageDialog(this, "Enter a valid UP plate, e.g. UP32 AB 1234", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double baseCost = tariffService.getTariffRate(type).getMonthlyPassRate() * duration;
        PaymentMethodType method = promptPassPayment("Payment for New Membership Pass", plate, type, duration, baseCost);
        if (method == null) return; // cancelled

        try {
            String normPlate = PlateUtil.normalize(plate);
            MembershipService.PassSubscriptionResult res = membershipService.issuePassWithPayment(3, normPlate, type, LocalDate.now(), duration, method);
            MembershipPass pass = res.pass();
            Invoice invoice = res.invoice();

            int opt = JOptionPane.showOptionDialog(this,
                    "Monthly Pass Issued & Activated Successfully!\n\n" +
                            "Pass ID         : #" + pass.getMembershipId() + "\n" +
                            "Tax Invoice No  : " + invoice.getInvoiceNumber() + "\n" +
                            "Vehicle         : " + pass.getVehicleType().getDisplayName() + "\n" +
                            "Plate           : " + PlateUtil.format(pass.getLicensePlate()) + "\n" +
                            "Total Settled   : " + MoneyFormat.format(invoice.getTotalAmount()) + " via " + method.getDescription() + "\n" +
                            "Valid Until     : " + DateFormat.displayDate(pass.getExpiryDate()),
                    "Subscription Confirmed",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    new Object[]{"Download PDF Invoice", "OK"},
                    "Download PDF Invoice");

            if (opt == 0) {
                exportInvoicePdf(invoice);
            }

            lookupField.setText(normPlate);
            checkPass();
            refreshMemberList();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Pass Issuance Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void checkPass() {
        String rawPlate = lookupField.getText().trim();
        String normPlate = PlateUtil.normalize(rawPlate);
        Optional<MembershipPass> passOpt = membershipService.getLatestMembership(normPlate);

        if (passOpt.isPresent()) {
            MembershipPass p = passOpt.get();
            StringBuilder sb = new StringBuilder();
            sb.append("=========================================\n");
            sb.append(p.isValid() ? "      VALID ACTIVE MEMBERSHIP PASS       \n" : "        MEMBERSHIP PASS EXPIRED          \n");
            sb.append("=========================================\n");
            sb.append("Pass ID       : #").append(p.getMembershipId()).append("\n");
            sb.append("Vehicle Class : ").append(p.getVehicleType().getDisplayName()).append("\n");
            sb.append("License Plate : ").append(PlateUtil.format(p.getLicensePlate())).append(" [").append(PlateUtil.district(p.getLicensePlate())).append("]\n");
            sb.append("Monthly Fee   : ").append(MoneyFormat.format(p.getMonthlyFee())).append("/mo\n");
            sb.append("Start Date    : ").append(DateFormat.displayDate(p.getStartDate())).append("\n");
            sb.append("Expiry Date   : ").append(DateFormat.displayDate(p.getExpiryDate())).append("\n");
            sb.append("Days Left     : ").append(p.getDaysLeft()).append(" day(s)\n");
            sb.append("Pass Status   : ").append(p.getStatus()).append("\n");
            sb.append("Parking Rate  : ").append(p.isValid() ? "₹0.00 (100% Free at Gate)" : "Standard Hourly Rate").append("\n");
            sb.append("=========================================\n");
            statusArea.setText(sb.toString());
        } else {
            statusArea.setText("\n  [NO MEMBERSHIP RECORD]\n\n  Vehicle " + PlateUtil.format(rawPlate) + " currently has\n  no membership pass on file.");
        }
    }

    private void renewSelectedPass() {
        int row = memberTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a pass in the table to renew.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int passId = (Integer) memberTableModel.getValueAt(row, 0);
        String plate = (String) memberTableModel.getValueAt(row, 1);
        String vTypeStr = (String) memberTableModel.getValueAt(row, 2);
        VehicleType vType = VehicleType.fromString(vTypeStr);

        double monthly = tariffService.getTariffRate(vType).getMonthlyPassRate();
        PaymentMethodType method = promptPassPayment("Payment to Renew Pass #" + passId, plate, vType, 1, monthly);
        if (method == null) return;

        try {
            MembershipService.PassSubscriptionResult res = membershipService.renewMembershipWithPayment(passId, 1, method);
            Invoice invoice = res.invoice();

            int opt = JOptionPane.showOptionDialog(this,
                    "Pass #" + passId + " Renewed Successfully for +1 Month!\n\n" +
                            "Tax Invoice No : " + invoice.getInvoiceNumber() + "\n" +
                            "Total Settled  : " + MoneyFormat.format(invoice.getTotalAmount()) + " via " + method.getDescription() + "\n" +
                            "New Expiry     : " + DateFormat.displayDate(res.pass().getExpiryDate()),
                    "Renewal Confirmed",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    new Object[]{"Download PDF Invoice", "OK"},
                    "Download PDF Invoice");

            if (opt == 0) {
                exportInvoicePdf(invoice);
            }
            refreshMemberList();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Renewal Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportInvoicePdf(Invoice invoice) {
        if (invoice == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("Invoice_" + invoice.getInvoiceNumber().replace("/", "_") + ".pdf"));
        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            try {
                File target = chooser.getSelectedFile();
                new InvoiceService().exportPdf(invoice, target);
                JOptionPane.showMessageDialog(this, "GST Tax Invoice saved successfully to:\n" + target.getAbsolutePath(), "PDF Exported", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error exporting PDF: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void cancelSelectedPass() {
        int row = memberTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a pass in the table to cancel.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int passId = (Integer) memberTableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to cancel Pass #" + passId + "?", "Confirm Cancellation", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            membershipService.cancelMembership(passId);
            refreshMemberList();
        }
    }

    public void refreshMemberList() {
        memberTableModel.setRowCount(0);
        List<MembershipPass> list = membershipService.getAllMemberships();
        for (MembershipPass p : list) {
            memberTableModel.addRow(new Object[]{
                    p.getMembershipId(),
                    PlateUtil.format(p.getLicensePlate()),
                    PlateUtil.district(p.getLicensePlate()),
                    p.getVehicleType().name(),
                    p.getDurationMonths() + " Mo",
                    MoneyFormat.format(p.getMonthlyFee()),
                    MoneyFormat.format(p.getTotalPrice()),
                    DateFormat.displayDate(p.getStartDate()),
                    DateFormat.displayDate(p.getExpiryDate()),
                    p.getDaysLeft(),
                    p.getStatus()
            });
        }
    }
}
