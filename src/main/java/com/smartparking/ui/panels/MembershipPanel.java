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
    private JPanel lookupResult;
    private JLabel issueError;
    private JLabel countLabel;
    private CardLayout tableCards;
    private JPanel tableHolder;
    private ModernButton btnRenew;
    private ModernButton btnCancelPass;
    /** Passes in table order, so actions use the real record instead of re-parsing display text. */
    private final java.util.List<MembershipPass> rows = new java.util.ArrayList<>();

    private DefaultTableModel memberTableModel;
    private JTable memberTable;

    public MembershipPanel(MembershipService membershipService) {
        this.membershipService = membershipService;
        this.tariffService = new TariffService();

        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_CANVAS);
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        initUI();
        refreshMemberList();
    }

    private void initUI() {
        com.smartparking.ui.components.PageHeader header = new com.smartparking.ui.components.PageHeader(
                "Monthly passes", "Flat-rate subscriptions · vehicles with a valid pass park free at the gate");
        ModernButton btnRefresh = ModernButton.createSecondary("Refresh",
                com.smartparking.ui.util.VectorIconUtil.getIcon(com.smartparking.ui.util.VectorIconUtil.IconType.REFRESH, 14, UITheme.TEXT_SECONDARY));
        btnRefresh.addActionListener(e -> refreshMemberList());
        header.addAction(btnRefresh);
        add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.gridy = 0;
        g.weighty = 0;
        g.gridx = 0;
        g.weightx = 0.5;
        g.insets = new Insets(0, 0, 16, 8);
        center.add(buildIssueCard(), g);
        g.gridx = 1;
        g.insets = new Insets(0, 8, 16, 0);
        center.add(buildLookupCard(), g);
        g.gridy = 1;
        g.gridx = 0;
        g.gridwidth = 2;
        g.weighty = 1;
        g.insets = new Insets(0, 0, 0, 0);
        center.add(buildTableCard(), g);
        add(center, BorderLayout.CENTER);

        updatePricingPreview();
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UITheme.font(Font.BOLD, 12));
        l.setForeground(UITheme.TEXT_BODY);
        return l;
    }

    private static JComponent kv(String k, JLabel v) {
        JPanel r = new JPanel(new BorderLayout(10, 0));
        r.setOpaque(false);
        r.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        JLabel kl = new JLabel(k);
        kl.setFont(UITheme.font(Font.PLAIN, 13));
        kl.setForeground(UITheme.TEXT_SECONDARY);
        v.setHorizontalAlignment(SwingConstants.RIGHT);
        r.add(kl, BorderLayout.WEST);
        r.add(v, BorderLayout.EAST);
        return r;
    }

    private JComponent buildIssueCard() {
        RoundedPanel card = new RoundedPanel(UITheme.RADIUS_LG, UITheme.BG_CARD, UITheme.BORDER_SUBTLE);
        card.setLayout(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 14, 0);
        card.add(com.smartparking.ui.components.PageHeader.cardTitle("Issue a pass"), c);

        c.gridy++;
        c.insets = new Insets(0, 0, 6, 0);
        card.add(fieldLabel("Licence plate"), c);
        JPanel plateRow = new JPanel(new BorderLayout(8, 0));
        plateRow.setOpaque(false);
        plateField = new JTextField(PlateUtil.generateRandomUp());
        UITheme.styleTextField(plateField);
        plateField.setFont(UITheme.mono(Font.BOLD, 14));
        plateField.getAccessibleContext().setAccessibleName("Licence plate");
        plateRow.add(plateField, BorderLayout.CENTER);
        ModernButton btnGen = ModernButton.createSecondary(null,
                com.smartparking.ui.util.VectorIconUtil.getIcon(com.smartparking.ui.util.VectorIconUtil.IconType.DICE, 14, UITheme.TEXT_SECONDARY));
        btnGen.setToolTipText("Generate a random UP plate");
        btnGen.getAccessibleContext().setAccessibleName("Random plate");
        btnGen.addActionListener(e -> plateField.setText(PlateUtil.generateRandomUp()));
        plateRow.add(btnGen, BorderLayout.EAST);
        c.gridy++;
        c.insets = new Insets(0, 0, 12, 0);
        card.add(plateRow, c);

        c.gridy++;
        c.gridwidth = 1;
        c.weightx = 0.6;
        c.insets = new Insets(0, 0, 6, 10);
        card.add(fieldLabel("Vehicle class"), c);
        c.gridx = 1;
        c.weightx = 0.4;
        c.insets = new Insets(0, 0, 6, 0);
        card.add(fieldLabel("Duration"), c);

        vehicleTypeBox = new JComboBox<>(new VehicleType[]{VehicleType.MOTORCYCLE, VehicleType.AUTO_RICKSHAW, VehicleType.CAR,
                VehicleType.EV_CAR, VehicleType.EV_TWO_WHEELER, VehicleType.LCV, VehicleType.HCV});
        vehicleTypeBox.setSelectedItem(VehicleType.CAR);
        UITheme.styleComboBox(vehicleTypeBox);
        vehicleTypeBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof VehicleType vt) setText(vt.getDisplayName());
                return this;
            }
        });
        vehicleTypeBox.addActionListener(e -> updatePricingPreview());
        durationBox = new JComboBox<>(new Integer[]{1, 3, 6, 12});
        UITheme.styleComboBox(durationBox);
        durationBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Integer m) setText(m + (m == 1 ? " month" : " months"));
                return this;
            }
        });
        durationBox.addActionListener(e -> updatePricingPreview());
        c.gridy++;
        c.gridx = 0;
        c.insets = new Insets(0, 0, 14, 10);
        card.add(vehicleTypeBox, c);
        c.gridx = 1;
        c.insets = new Insets(0, 0, 14, 0);
        card.add(durationBox, c);

        // price summary
        JPanel summary = new JPanel(new GridLayout(0, 1));
        summary.setOpaque(true);
        summary.setBackground(UITheme.BG_SURFACE);
        summary.setBorder(BorderFactory.createCompoundBorder(
                new com.smartparking.ui.theme.DarkTheme.RoundBorder(UITheme.BORDER_SUBTLE, UITheme.RADIUS),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        lblRatePreview = new JLabel("—");
        lblRatePreview.setFont(UITheme.font(Font.BOLD, 13));
        lblRatePreview.setForeground(UITheme.TEXT_PRIMARY);
        lblExpiryPreview = new JLabel("—");
        lblExpiryPreview.setFont(UITheme.font(Font.BOLD, 13));
        lblExpiryPreview.setForeground(UITheme.TEXT_PRIMARY);
        lblTotalCostPreview = new JLabel("—");
        lblTotalCostPreview.setFont(UITheme.font(Font.BOLD, 16));
        lblTotalCostPreview.setForeground(UITheme.TEXT_PRIMARY);
        summary.add(kv("Monthly fee", lblRatePreview));
        summary.add(kv("Valid until", lblExpiryPreview));
        summary.add(kv("Total before GST", lblTotalCostPreview));
        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 6, 0);
        card.add(summary, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 8, 0);
        issueError = new JLabel(" ");
        issueError.setFont(UITheme.font(Font.PLAIN, 12));
        issueError.setForeground(UITheme.ERROR_TEXT);
        card.add(issueError, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        ModernButton btnBuy = new ModernButton("Issue pass…", null);
        btnBuy.setToolTipText("Review GST and choose a payment method (simulated)");
        btnBuy.setPreferredSize(new Dimension(200, 40));
        btnBuy.addActionListener(e -> purchasePass());
        card.add(btnBuy, c);
        return card;
    }

    private JComponent buildLookupCard() {
        RoundedPanel card = new RoundedPanel(UITheme.RADIUS_LG, UITheme.BG_CARD, UITheme.BORDER_SUBTLE);
        card.setLayout(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        card.add(com.smartparking.ui.components.PageHeader.cardTitle("Check a pass"), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        lookupField = new JTextField("UP16CD2345");
        UITheme.styleTextField(lookupField);
        lookupField.setFont(UITheme.mono(Font.BOLD, 14));
        lookupField.getAccessibleContext().setAccessibleName("Plate to check");
        lookupField.addActionListener(e -> checkPass());
        row.add(lookupField, BorderLayout.CENTER);
        ModernButton btnLookup = ModernButton.createSecondary("Check",
                com.smartparking.ui.util.VectorIconUtil.getIcon(com.smartparking.ui.util.VectorIconUtil.IconType.SEARCH, 14, UITheme.TEXT_SECONDARY));
        btnLookup.addActionListener(e -> checkPass());
        row.add(btnLookup, BorderLayout.EAST);
        body.add(row, BorderLayout.NORTH);

        lookupResult = new JPanel(new BorderLayout());
        lookupResult.setOpaque(false);
        lookupResult.add(hintLabel("Enter a plate and press Enter to see its pass status, validity and days left."), BorderLayout.NORTH);
        body.add(lookupResult, BorderLayout.CENTER);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private static JLabel hintLabel(String t) {
        JLabel l = new JLabel("<html>" + t + "</html>");
        l.setFont(UITheme.font(Font.PLAIN, 13));
        l.setForeground(UITheme.TEXT_SECONDARY);
        return l;
    }

    private JComponent buildTableCard() {
        RoundedPanel tableCard = new RoundedPanel(UITheme.RADIUS_LG, UITheme.BG_CARD, UITheme.BORDER_SUBTLE);
        tableCard.setLayout(new BorderLayout(0, 12));
        tableCard.setBorder(BorderFactory.createEmptyBorder(16, 18, 18, 18));

        JPanel tableHeader = new JPanel(new BorderLayout());
        tableHeader.setOpaque(false);
        JPanel titles = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titles.setOpaque(false);
        titles.add(com.smartparking.ui.components.PageHeader.cardTitle("Pass holders"));
        countLabel = new JLabel("0");
        countLabel.setFont(UITheme.font(Font.BOLD, 12));
        countLabel.setForeground(UITheme.TEXT_TERTIARY);
        titles.add(countLabel);
        JPanel tw = new JPanel(new GridBagLayout());
        tw.setOpaque(false);
        tw.add(titles);
        tableHeader.add(tw, BorderLayout.WEST);

        JPanel tableActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tableActions.setOpaque(false);
        btnRenew = ModernButton.createSecondary("Renew +1 month", null).compact();
        btnRenew.setEnabled(false);
        btnRenew.addActionListener(e -> renewSelectedPass());
        btnCancelPass = ModernButton.createDanger("Cancel pass", null).compact();
        btnCancelPass.setEnabled(false);
        btnCancelPass.addActionListener(e -> cancelSelectedPass());
        tableActions.add(btnRenew);
        tableActions.add(btnCancelPass);
        tableHeader.add(tableActions, BorderLayout.EAST);
        tableCard.add(tableHeader, BorderLayout.NORTH);

        String[] cols = {"Pass ID", "Licence plate", "District", "Vehicle class", "Duration", "Monthly fee", "Total paid", "Starts", "Expires", "Days left", "Status"};
        memberTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        memberTable = new JTable(memberTableModel);
        memberTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        memberTable.getSelectionModel().addListSelectionListener(e -> {
            boolean sel = memberTable.getSelectedRow() >= 0;
            btnRenew.setEnabled(sel);
            btnCancelPass.setEnabled(sel);
        });
        JScrollPane sp = new JScrollPane(memberTable);
        sp.setBorder(new com.smartparking.ui.theme.DarkTheme.RoundBorder(UITheme.BORDER_SUBTLE, UITheme.RADIUS));

        tableCards = new CardLayout();
        tableHolder = new JPanel(tableCards);
        tableHolder.setOpaque(false);
        tableHolder.add(sp, "TABLE");
        tableHolder.add(new com.smartparking.ui.components.EmptyState(com.smartparking.ui.util.VectorIconUtil.IconType.MEMBERSHIP,
                "No passes issued yet", "Issue a monthly pass above. Pass holders are listed here with their expiry and status."), "EMPTY");
        tableCard.add(tableHolder, BorderLayout.CENTER);
        return tableCard;
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

        lblRatePreview.setText(MoneyFormat.format(monthly) + " / month");
        lblExpiryPreview.setText(DateFormat.displayDate(expiry));
        lblTotalCostPreview.setText(MoneyFormat.format(total));
    }

    private PaymentMethodType promptPassPayment(String title, String plate, VehicleType type, int durationMonths, double baseAmount) {
        GstCalculator gstCalc = new GstCalculator();
        GstCalculator.GstResult gst = gstCalc.calculate(baseAmount);

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 2));
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(380, 230));
        JLabel who = new JLabel(String.format("%s · %s · %d month%s", PlateUtil.format(plate), type.getDisplayName(), durationMonths, durationMonths == 1 ? "" : "s"));
        who.setFont(UITheme.font(Font.BOLD, 13));
        who.setForeground(UITheme.TEXT_PRIMARY);
        panel.add(who);
        panel.add(kv("Pass fee (taxable)", new JLabel(MoneyFormat.format(gst.taxableValue()))));
        panel.add(kv("CGST 9%", new JLabel(MoneyFormat.format(gst.cgstAmount()))));
        panel.add(kv("SGST 9%", new JLabel(MoneyFormat.format(gst.sgstAmount()))));
        panel.add(kv("Round-off", new JLabel(String.format("%+.2f", gst.roundOff()))));
        JLabel lblTotal = new JLabel(MoneyFormat.format(gst.grandTotal()));
        lblTotal.setFont(UITheme.font(Font.BOLD, 16));
        lblTotal.setForeground(UITheme.TEXT_PRIMARY);
        panel.add(kv("Total due", lblTotal));

        JComboBox<PaymentMethodType> methodCombo = new JComboBox<>(new PaymentMethodType[]{
                PaymentMethodType.UPI, PaymentMethodType.CARD, PaymentMethodType.CASH, PaymentMethodType.WALLET
        });
        UITheme.styleComboBox(methodCombo);
        methodCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PaymentMethodType pm) setText("Pay by " + pm.getDescription() + " (simulated)");
                return this;
            }
        });
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
            issueError.setText(plate.isEmpty() ? "Enter the vehicle's licence plate." : "Enter a valid UP plate, e.g. UP32 AB 1234.");
            issueError.setIcon(com.smartparking.ui.util.VectorIconUtil.getIcon(com.smartparking.ui.util.VectorIconUtil.IconType.ALERT, 13, UITheme.ERROR_TEXT));
            plateField.requestFocusInWindow();
            return;
        }
        issueError.setText(" ");
        issueError.setIcon(null);

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
            com.smartparking.ui.components.Toast.error(this, "Pass not issued", ex.getMessage());
        }
    }

    private void checkPass() {
        String rawPlate = lookupField.getText().trim();
        String normPlate = PlateUtil.normalize(rawPlate);
        Optional<MembershipPass> passOpt = membershipService.getLatestMembership(normPlate);
        lookupResult.removeAll();

        if (passOpt.isPresent()) {
            MembershipPass p = passOpt.get();
            JPanel res = new JPanel(new GridLayout(0, 1, 0, 0));
            res.setOpaque(false);
            JPanel top = new JPanel(new BorderLayout(10, 0));
            top.setOpaque(false);
            top.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
            JLabel pl = new JLabel(PlateUtil.format(p.getLicensePlate()) + "  ·  " + PlateUtil.district(p.getLicensePlate()));
            pl.setFont(UITheme.font(Font.BOLD, 14));
            pl.setForeground(UITheme.TEXT_PRIMARY);
            top.add(pl, BorderLayout.WEST);
            JPanel bw = new JPanel(new GridBagLayout());
            bw.setOpaque(false);
            bw.add(com.smartparking.ui.components.Badge.forStatus(p.isValid() ? "VALID" : String.valueOf(p.getStatus())));
            top.add(bw, BorderLayout.EAST);
            res.add(top);
            res.add(kv("Pass", strong("#" + p.getMembershipId() + " · " + p.getVehicleType().getDisplayName())));
            res.add(kv("Valid", strong(DateFormat.displayDate(p.getStartDate()) + " to " + DateFormat.displayDate(p.getExpiryDate()))));
            res.add(kv("Days left", strong(String.valueOf(p.getDaysLeft()))));
            res.add(kv("At the gate", strong(p.isValid() ? "Parks free" : "Standard hourly rate")));
            lookupResult.add(res, BorderLayout.NORTH);
        } else {
            lookupResult.add(hintLabel(PlateUtil.format(rawPlate) + " has no monthly pass on file."), BorderLayout.NORTH);
        }
        lookupResult.revalidate();
        lookupResult.repaint();
    }

    private static JLabel strong(String t) {
        JLabel l = new JLabel(t);
        l.setFont(UITheme.font(Font.BOLD, 13));
        l.setForeground(UITheme.TEXT_PRIMARY);
        return l;
    }

    private void renewSelectedPass() {
        int row = memberTable.getSelectedRow();
        if (row < 0) {
            com.smartparking.ui.components.Toast.info(this, "Select a pass to renew", null);
            return;
        }

        // Use the underlying record (the previous code read the District column as the vehicle class)
        MembershipPass selected = rows.get(memberTable.convertRowIndexToModel(row));
        int passId = selected.getMembershipId();
        String plate = selected.getLicensePlate();
        VehicleType vType = selected.getVehicleType();

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
            com.smartparking.ui.components.Toast.error(this, "Renewal failed", ex.getMessage());
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
                com.smartparking.ui.components.Toast.success(this, "Invoice saved", target.getAbsolutePath());
            } catch (Exception ex) {
                com.smartparking.ui.components.Toast.error(this, "PDF export failed", ex.getMessage());
            }
        }
    }

    private void cancelSelectedPass() {
        int row = memberTable.getSelectedRow();
        if (row < 0) {
            com.smartparking.ui.components.Toast.info(this, "Select a pass to cancel", null);
            return;
        }

        MembershipPass selected = rows.get(memberTable.convertRowIndexToModel(row));
        int passId = selected.getMembershipId();
        int confirm = JOptionPane.showConfirmDialog(this,
                "Cancel pass #" + passId + " for " + PlateUtil.format(selected.getLicensePlate()) + "?\nThe vehicle will pay the hourly rate from now on. This can't be undone.",
                "Cancel pass", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            membershipService.cancelMembership(passId);
            refreshMemberList();
            com.smartparking.ui.components.Toast.success(this, "Pass #" + passId + " cancelled", null);
        }
    }

    public void refreshMemberList() {
        memberTableModel.setRowCount(0);
        rows.clear();
        List<MembershipPass> list = membershipService.getAllMemberships();
        for (MembershipPass p : list) {
            rows.add(p);
            memberTableModel.addRow(new Object[]{
                    p.getMembershipId(),
                    PlateUtil.format(p.getLicensePlate()),
                    PlateUtil.district(p.getLicensePlate()),
                    p.getVehicleType().getDisplayName(),
                    p.getDurationMonths() + (p.getDurationMonths() == 1 ? " month" : " months"),
                    MoneyFormat.format(p.getMonthlyFee()),
                    MoneyFormat.format(p.getTotalPrice()),
                    DateFormat.displayDate(p.getStartDate()),
                    DateFormat.displayDate(p.getExpiryDate()),
                    p.getDaysLeft(),
                    p.getStatus()
            });
        }
        if (countLabel != null) countLabel.setText(String.valueOf(list.size()));
        if (tableCards != null) tableCards.show(tableHolder, list.isEmpty() ? "EMPTY" : "TABLE");
    }
}
