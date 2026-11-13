package com.smartparking.ui.panels;

import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.model.fastag.FastagAccount;
import com.smartparking.model.invoice.Invoice;
import com.smartparking.model.payment.PaymentTransaction;
import com.smartparking.model.tariff.FeeBreakdown;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.service.GstCalculator;
import com.smartparking.service.ParkingService;
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
import com.smartparking.util.UpiUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Gate 2 — checkout and billing, laid out as three steps that read left to right.
 *
 * <pre>
 *  Checkout & billing
 *  [ ticket number or plate ............ ] [Find ticket]  [ ] Lost ticket (+₹250)
 *  ┌ 1 Bill ───────────┐ ┌ 2 Payment ─────────┐ ┌ 3 Gate & receipt ─┐
 *  │ vehicle + stay    │ │ ( ) Cash           │ │ ● Barrier closed  │
 *  │ fee lines + GST   │ │ (•) UPI  [QR]      │ │ ┌ invoice paper ┐ │
 *  │ Total ₹ 236.00    │ │ ...                │ │ └───────────────┘ │
 *  │ Override (admin)  │ │ [Settle & open]    │ │ Refund Print PDF  │
 *  └───────────────────┘ └────────────────────┘ └───────────────────┘
 * </pre>
 * Business logic is unchanged: fee preview via the pricing service, GST via {@link GstCalculator},
 * settlement via {@link ParkingService#processVehicleCheckout} / {@code processLostTicketCheckout}.
 */
public class VehicleExitPanel extends JPanel {

    private final ParkingService parkingService;
    private final Runnable onStateChange;

    private JTextField searchField;
    private JLabel searchError;
    private JCheckBox lostTicketCheck;
    private ModernButton btnCalculatePreview;
    private ModernButton btnOverrideFee;

    // Bill
    private CardLayout billCards;
    private JPanel billHolder;
    private JLabel billPlate, billMeta;
    private JLabel billVehicleIcon;
    private JLabel lblDuration;
    private JLabel lblBaseFee;
    private JLabel lblPeakMultiplier;
    private JLabel lblPassDiscount;
    private JLabel lblPenalty;
    private JLabel lblTaxable, lblCgst, lblSgst, lblRoundOff;
    private JLabel lblTotalDue;
    private JLabel lblOverrideNote;

    // Payment
    private PaymentMethodType selectedPaymentMethod = PaymentMethodType.UPI;
    private final Map<PaymentMethodType, PayOption> payOptions = new EnumMap<>(PaymentMethodType.class);
    private JPanel paymentDetailsContainer;
    private CardLayout paymentCardLayout;

    private JTextField cashReceivedField;
    private JLabel cashChangeLabel;
    private JLabel upiQrLabel;
    private JLabel upiStringLabel;
    private JTextField cardLast4Field;
    private JTextField cardHolderField;
    private boolean simulatedCardAuthorized = true;
    private JLabel cardStatusLabel;
    private JLabel fastagStatusLabel;
    private JComboBox<String> walletChoiceBox;

    private ModernButton btnCompleteCheckout;

    // Gate & receipt
    private GateStatus gateStatus;
    private CardLayout receiptCards;
    private JPanel receiptHolder;
    private JEditorPane receiptEditorPane;
    private ModernButton btnDownloadPdf;
    private ModernButton btnPrintReceipt;
    private ModernButton btnAdminRefund;

    // Current State
    private ParkingTicket currentTicket = null;
    private FeeBreakdown currentBreakdown = null;
    private GstCalculator.GstResult currentGst = null;
    private Double overriddenFee = null;
    private String overrideReason = null;
    private Invoice currentInvoice = null;
    private PaymentTransaction currentPayment = null;

    public VehicleExitPanel(ParkingService parkingService, Runnable onStateChange) {
        this.parkingService = parkingService;
        this.onStateChange = onStateChange;

        setLayout(new BorderLayout(0, 0));
        setBackground(UITheme.BG_CANVAS);
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        initUI();
    }

    // =====================================================================================
    // Layout
    // =====================================================================================
    private void initUI() {
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        PageHeader header = new PageHeader("Checkout & billing", "Gate 2 · look up a ticket, settle the fee and open the barrier");
        header.setAlignmentX(LEFT_ALIGNMENT);
        top.add(header);
        JComponent lookup = buildLookupBar();
        lookup.setAlignmentX(LEFT_ALIGNMENT);
        top.add(lookup);
        top.add(Box.createVerticalStrut(16));
        add(top, BorderLayout.NORTH);

        JPanel cols = new JPanel(new GridLayout(1, 3, 16, 0));
        cols.setOpaque(false);
        cols.add(buildBillCard());
        cols.add(buildPaymentCard());
        cols.add(buildReceiptCard());
        add(cols, BorderLayout.CENTER);

        setPaymentButtonsEnabled(false);
        updatePaymentSelectionUI();
    }

    private JComponent buildLookupBar() {
        RoundedPanel bar = new RoundedPanel(UITheme.RADIUS_LG, UITheme.BG_CARD, UITheme.BORDER_SUBTLE);
        bar.setLayout(new GridBagLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(14, 16, 10, 16));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.insets = new Insets(0, 0, 0, 10);
        JLabel ic = new JLabel(VectorIconUtil.getIcon(IconType.SEARCH, 16, UITheme.TEXT_TERTIARY));
        bar.add(ic, c);

        c.gridx = 1;
        c.weightx = 1;
        searchField = new JTextField();
        UITheme.styleTextField(searchField);
        searchField.setFont(UITheme.font(Font.PLAIN, 14));
        searchField.setPreferredSize(new Dimension(300, 40));
        searchField.setToolTipText("Scan the ticket barcode or type a ticket number / licence plate, then press Enter");
        searchField.getAccessibleContext().setAccessibleName("Ticket number or licence plate");
        searchField.putClientProperty("JTextField.placeholderText", "Ticket number or plate");
        // Barcode / QR scanner compatibility: hitting Enter triggers immediate preview
        searchField.addActionListener(e -> calculatePreview());
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { searchError.setText(" "); searchError.setIcon(null); }
            public void removeUpdate(DocumentEvent e) { searchError.setText(" "); searchError.setIcon(null); }
            public void changedUpdate(DocumentEvent e) { }
        });
        bar.add(searchField, c);

        c.gridx = 2;
        c.weightx = 0;
        btnCalculatePreview = new ModernButton("Find ticket", null);
        btnCalculatePreview.setPreferredSize(new Dimension(130, 40));
        btnCalculatePreview.addActionListener(e -> calculatePreview());
        bar.add(btnCalculatePreview, c);

        c.gridx = 3;
        c.insets = new Insets(0, 8, 0, 0);
        lostTicketCheck = new JCheckBox("Lost ticket (look up by plate, ₹250 penalty)");
        lostTicketCheck.setFont(UITheme.font(Font.PLAIN, 13));
        lostTicketCheck.setForeground(UITheme.TEXT_BODY);
        lostTicketCheck.setOpaque(false);
        lostTicketCheck.setFocusPainted(false);
        lostTicketCheck.addActionListener(e -> {
            if (currentTicket != null) calculatePreview();
        });
        bar.add(lostTicketCheck, c);

        c.gridy = 1;
        c.gridx = 1;
        c.gridwidth = 3;
        c.insets = new Insets(4, 0, 0, 0);
        searchError = new JLabel(" ");
        searchError.setFont(UITheme.font(Font.PLAIN, 12));
        searchError.setForeground(UITheme.ERROR_TEXT);
        bar.add(searchError, c);
        return bar;
    }

    private static RoundedPanel stepCard(String number, String title) {
        RoundedPanel card = new RoundedPanel(UITheme.RADIUS_LG, UITheme.BG_CARD, UITheme.BORDER_SUBTLE);
        card.setLayout(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        head.setOpaque(false);
        JLabel n = new JLabel(number, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UITheme.ACCENT_SOFT);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.setColor(UITheme.ACCENT_BORDER);
                g2.drawOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        n.setFont(UITheme.font(Font.BOLD, 11));
        n.setForeground(UITheme.ACCENT_TEXT);
        n.setPreferredSize(new Dimension(22, 22));
        head.add(n);
        head.add(PageHeader.cardTitle(title));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(head, BorderLayout.WEST);
        card.add(wrap, BorderLayout.NORTH);
        return card;
    }

    // ------------------------------------------------------------------ 1. bill
    private JComponent buildBillCard() {
        RoundedPanel card = stepCard("1", "Bill");
        btnOverrideFee = ModernButton.createGhost("Override", VectorIconUtil.getIcon(IconType.SETTINGS, 13, UITheme.TEXT_SECONDARY)).compact();
        btnOverrideFee.setToolTipText("Administrative fee override (reason is audit-logged)");
        btnOverrideFee.setEnabled(false);
        btnOverrideFee.addActionListener(e -> promptFeeOverride());
        ((JPanel) card.getComponent(0)).add(btnOverrideFee, BorderLayout.EAST);

        billCards = new CardLayout();
        billHolder = new JPanel(billCards);
        billHolder.setOpaque(false);
        billHolder.add(new EmptyState(IconType.TICKET, "No ticket selected",
                "Scan a ticket or enter a plate above. Parking is free for the first 15 minutes, then billed per started hour plus 18% GST."), "EMPTY");

        JPanel bill = new JPanel(new GridBagLayout());
        bill.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;

        // vehicle summary
        JPanel veh = new JPanel(new BorderLayout(12, 0));
        veh.setOpaque(false);
        billVehicleIcon = new JLabel();
        billVehicleIcon.setPreferredSize(new Dimension(40, 40));
        billVehicleIcon.setHorizontalAlignment(SwingConstants.CENTER);
        billVehicleIcon.setOpaque(true);
        billVehicleIcon.setBackground(UITheme.BG_ELEVATED);
        billVehicleIcon.setBorder(new DarkTheme.RoundBorder(UITheme.BORDER, UITheme.RADIUS));
        veh.add(billVehicleIcon, BorderLayout.WEST);
        JPanel vt = new JPanel();
        vt.setOpaque(false);
        vt.setLayout(new BoxLayout(vt, BoxLayout.Y_AXIS));
        billPlate = new JLabel("—");
        billPlate.setFont(UITheme.mono(Font.BOLD, 15));
        billPlate.setForeground(UITheme.TEXT_PRIMARY);
        billMeta = new JLabel(" ");
        billMeta.setFont(UITheme.font(Font.PLAIN, 12));
        billMeta.setForeground(UITheme.TEXT_SECONDARY);
        vt.add(billPlate);
        vt.add(Box.createVerticalStrut(2));
        vt.add(billMeta);
        veh.add(vt, BorderLayout.CENTER);
        c.insets = new Insets(0, 0, 14, 0);
        bill.add(veh, c);

        c.insets = new Insets(0, 0, 0, 0);
        lblDuration = value();
        lblBaseFee = value();
        lblPeakMultiplier = value();
        lblPassDiscount = value();
        lblPenalty = value();
        lblTaxable = value();
        lblCgst = value();
        lblSgst = value();
        lblRoundOff = value();
        c.gridy++; bill.add(line("Stay", lblDuration, false), c);
        c.gridy++; bill.add(line("Parking fee", lblBaseFee, false), c);
        c.gridy++; bill.add(line("Peak surcharge", lblPeakMultiplier, false), c);
        c.gridy++; bill.add(line("Monthly pass", lblPassDiscount, false), c);
        c.gridy++; bill.add(line("Penalties", lblPenalty, true), c);
        c.gridy++; c.insets = new Insets(8, 0, 0, 0); bill.add(line("Taxable value", lblTaxable, false), c);
        c.gridy++; c.insets = new Insets(0, 0, 0, 0); bill.add(line("CGST 9%", lblCgst, false), c);
        c.gridy++; bill.add(line("SGST 9%", lblSgst, false), c);
        c.gridy++; bill.add(line("Round-off", lblRoundOff, true), c);

        c.gridy++;
        c.insets = new Insets(14, 0, 0, 0);
        JPanel total = new JPanel(new BorderLayout());
        total.setOpaque(false);
        JLabel tl = new JLabel("Total due");
        tl.setFont(UITheme.font(Font.BOLD, 14));
        tl.setForeground(UITheme.TEXT_BODY);
        lblTotalDue = new JLabel("₹0.00");
        lblTotalDue.setFont(UITheme.font(Font.BOLD, 26));
        lblTotalDue.setForeground(UITheme.TEXT_PRIMARY);
        total.add(tl, BorderLayout.WEST);
        total.add(lblTotalDue, BorderLayout.EAST);
        bill.add(total, c);
        c.gridy++;
        c.insets = new Insets(6, 0, 0, 0);
        lblOverrideNote = new JLabel(" ");
        lblOverrideNote.setFont(UITheme.font(Font.PLAIN, 12));
        lblOverrideNote.setForeground(UITheme.WARNING_TEXT);
        bill.add(lblOverrideNote, c);
        c.gridy++;
        c.weighty = 1;
        bill.add(Box.createGlue(), c);

        JScrollPane billScroll = scroll(bill);
        billHolder.add(billScroll, "BILL");
        billCards.show(billHolder, "EMPTY");
        card.add(billHolder, BorderLayout.CENTER);
        return card;
    }

    private static JLabel value() {
        JLabel l = new JLabel("—");
        l.setFont(UITheme.font(Font.BOLD, 13));
        l.setForeground(UITheme.TEXT_PRIMARY);
        l.setHorizontalAlignment(SwingConstants.RIGHT);
        return l;
    }

    private static JComponent line(String key, JLabel value, boolean divider) {
        JPanel r = new JPanel(new BorderLayout(10, 0));
        r.setOpaque(false);
        r.setBorder(divider
                ? BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER_SUBTLE), BorderFactory.createEmptyBorder(6, 0, 8, 0))
                : BorderFactory.createEmptyBorder(6, 0, 6, 0));
        JLabel k = new JLabel(key);
        k.setFont(UITheme.font(Font.PLAIN, 13));
        k.setForeground(UITheme.TEXT_SECONDARY);
        r.add(k, BorderLayout.WEST);
        r.add(value, BorderLayout.EAST);
        return r;
    }

    private static JScrollPane scroll(JComponent c) {
        com.smartparking.ui.components.ScrollablePanel track = new com.smartparking.ui.components.ScrollablePanel(new BorderLayout());
        track.setOpaque(false);
        track.add(c, BorderLayout.CENTER);
        JScrollPane s = new JScrollPane(track);
        s.setBorder(null);
        s.setOpaque(false);
        s.getViewport().setOpaque(false);
        s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        s.getVerticalScrollBar().setUnitIncrement(16);
        return s;
    }

    // ------------------------------------------------------------------ 2. payment
    private JComponent buildPaymentCard() {
        RoundedPanel card = stepCard("2", "Payment");

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);

        JPanel options = new JPanel(new GridLayout(0, 1, 0, 6));
        options.setOpaque(false);
        addPayOption(options, PaymentMethodType.CASH, "Cash", "Collect and give change", IconType.PAYMENT);
        addPayOption(options, PaymentMethodType.UPI, "UPI", "Dynamic QR (simulated)", IconType.GRID);
        addPayOption(options, PaymentMethodType.CARD, "Card", "Terminal (simulated)", IconType.MEMBERSHIP);
        addPayOption(options, PaymentMethodType.WALLET, "Wallet", "Paytm, PhonePe, Amazon Pay (simulated)", IconType.PAYMENT);
        addPayOption(options, PaymentMethodType.FASTAG, "FASTag", "Auto-debit at the barrier (simulated)", IconType.LIGHTNING);
        body.add(options, BorderLayout.NORTH);

        paymentCardLayout = new CardLayout();
        paymentDetailsContainer = new JPanel(paymentCardLayout);
        paymentDetailsContainer.setBackground(UITheme.BG_SURFACE);
        paymentDetailsContainer.setBorder(BorderFactory.createCompoundBorder(
                new DarkTheme.RoundBorder(UITheme.BORDER_SUBTLE, UITheme.RADIUS),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        paymentDetailsContainer.add(createCashPanel(), PaymentMethodType.CASH.name());
        paymentDetailsContainer.add(createUpiPanel(), PaymentMethodType.UPI.name());
        paymentDetailsContainer.add(createCardPanel(), PaymentMethodType.CARD.name());
        paymentDetailsContainer.add(createWalletPanel(), PaymentMethodType.WALLET.name());
        paymentDetailsContainer.add(createFastagPanel(), PaymentMethodType.FASTAG.name());
        body.add(paymentDetailsContainer, BorderLayout.CENTER);
        card.add(scroll(body), BorderLayout.CENTER);

        btnCompleteCheckout = new ModernButton("Settle & open barrier", VectorIconUtil.getIcon(IconType.CHECK, 15, Color.WHITE));
        btnCompleteCheckout.setFont(UITheme.font(Font.BOLD, 14));
        btnCompleteCheckout.setPreferredSize(new Dimension(200, 44));
        btnCompleteCheckout.setEnabled(false);
        btnCompleteCheckout.addActionListener(e -> processExit());
        card.add(btnCompleteCheckout, BorderLayout.SOUTH);
        return card;
    }

    private void addPayOption(JPanel parent, PaymentMethodType m, String title, String hint, IconType icon) {
        PayOption o = new PayOption(m, title, hint, icon);
        payOptions.put(m, o);
        parent.add(o);
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UITheme.font(Font.BOLD, 12));
        l.setForeground(UITheme.TEXT_BODY);
        return l;
    }

    private static JLabel hint(String text) {
        JLabel l = new JLabel("<html>" + text + "</html>");
        l.setFont(UITheme.font(Font.PLAIN, 12));
        l.setForeground(UITheme.TEXT_SECONDARY);
        return l;
    }

    private static JPanel vstack() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        return p;
    }

    private static void addRow(JPanel p, JComponent comp, int gap) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = p.getComponentCount();
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(0, 0, gap, 0);
        p.add(comp, c);
    }

    private JPanel createCashPanel() {
        JPanel p = vstack();
        addRow(p, fieldLabel("Amount received (₹)"), 6);
        cashReceivedField = new JTextField();
        UITheme.styleTextField(cashReceivedField);
        cashReceivedField.getAccessibleContext().setAccessibleName("Cash received");
        cashReceivedField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateCashChange(); }
            public void removeUpdate(DocumentEvent e) { updateCashChange(); }
            public void changedUpdate(DocumentEvent e) { updateCashChange(); }
        });
        addRow(p, cashReceivedField, 10);
        cashChangeLabel = new JLabel("Enter the amount received");
        cashChangeLabel.setFont(UITheme.font(Font.BOLD, 13));
        cashChangeLabel.setForeground(UITheme.TEXT_SECONDARY);
        addRow(p, cashChangeLabel, 0);
        return p;
    }

    private void updateCashChange() {
        if (currentGst == null) return;
        double total = currentGst.grandTotal();
        try {
            String text = cashReceivedField.getText().trim();
            if (text.isEmpty()) {
                cashChangeLabel.setText("Enter the amount received");
                cashChangeLabel.setForeground(UITheme.TEXT_SECONDARY);
                btnCompleteCheckout.setEnabled(false);
                return;
            }
            double received = Double.parseDouble(text);
            double change = received - total;
            if (change < 0) {
                cashChangeLabel.setText(String.format("Short by %s", MoneyFormat.format(-change)));
                cashChangeLabel.setForeground(UITheme.ERROR_TEXT);
                btnCompleteCheckout.setEnabled(false);
            } else {
                cashChangeLabel.setText(String.format("Change to return: %s", MoneyFormat.format(change)));
                cashChangeLabel.setForeground(UITheme.SUCCESS_TEXT);
                btnCompleteCheckout.setEnabled(true);
            }
        } catch (NumberFormatException ex) {
            cashChangeLabel.setText("Enter a number, e.g. 250");
            cashChangeLabel.setForeground(UITheme.ERROR_TEXT);
            btnCompleteCheckout.setEnabled(false);
        }
    }

    private JPanel createUpiPanel() {
        JPanel p = new JPanel(new BorderLayout(14, 0));
        p.setOpaque(false);
        upiQrLabel = new JLabel();
        upiQrLabel.setOpaque(true);
        upiQrLabel.setBackground(Color.WHITE);
        upiQrLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        upiQrLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel qrWrap = new JPanel(new BorderLayout());
        qrWrap.setOpaque(false);
        qrWrap.add(upiQrLabel, BorderLayout.NORTH);
        p.add(qrWrap, BorderLayout.WEST);

        JPanel d = vstack();
        addRow(d, fieldLabel("Pay to parking@upi"), 4);
        addRow(d, hint("Grand Tech Park Parking Ltd. Scan with BHIM, PhonePe, Google Pay or Paytm."), 10);
        upiStringLabel = new JLabel(" ");
        upiStringLabel.setFont(UITheme.mono(Font.PLAIN, 10));
        upiStringLabel.setForeground(UITheme.TEXT_TERTIARY);
        addRow(d, upiStringLabel, 10);
        ModernButton btnSimUpi = ModernButton.createSecondary("Simulate confirmation", null).compact();
        btnSimUpi.addActionListener(e -> Toast.success(this, "UPI payment confirmed (simulated)", "Gateway webhook received"));
        JPanel bw = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bw.setOpaque(false);
        bw.add(btnSimUpi);
        addRow(d, bw, 0);
        p.add(d, BorderLayout.CENTER);
        return p;
    }

    private JPanel createCardPanel() {
        // Simulated Card Terminal — PCI-DSS Compliant: We do not capture or store full PAN or CVV.
        JPanel p = vstack();
        cardHolderField = new JTextField("Authorized Driver");
        UITheme.styleTextField(cardHolderField);
        cardLast4Field = new JTextField("4321");
        UITheme.styleTextField(cardLast4Field);
        addRow(p, fieldLabel("Cardholder name"), 6);
        addRow(p, cardHolderField, 10);
        addRow(p, fieldLabel("Last 4 digits"), 6);
        addRow(p, cardLast4Field, 4);
        addRow(p, hint("Full card numbers and CVV are never captured (PCI-DSS)."), 10);

        JPanel btns = new JPanel(new GridLayout(1, 2, 8, 0));
        btns.setOpaque(false);
        ModernButton ok = ModernButton.createSecondary("Simulate approval", null).compact();
        ok.addActionListener(e -> {
            simulatedCardAuthorized = true;
            btnCompleteCheckout.setEnabled(currentTicket != null);
            cardStatusLabel.setText("Approved · auth code " + (int) (Math.random() * 899999 + 100000));
            cardStatusLabel.setForeground(UITheme.SUCCESS_TEXT);
        });
        ModernButton fail = ModernButton.createGhost("Simulate decline", null).compact();
        fail.setForeground(UITheme.ERROR_TEXT);
        fail.addActionListener(e -> {
            simulatedCardAuthorized = false;
            btnCompleteCheckout.setEnabled(false);
            cardStatusLabel.setText("Declined · 51 insufficient funds (simulated)");
            cardStatusLabel.setForeground(UITheme.ERROR_TEXT);
        });
        btns.add(ok);
        btns.add(fail);
        addRow(p, btns, 8);
        cardStatusLabel = new JLabel(" ");
        cardStatusLabel.setFont(UITheme.font(Font.BOLD, 12));
        addRow(p, cardStatusLabel, 0);
        return p;
    }

    private JPanel createWalletPanel() {
        JPanel p = vstack();
        String[] wallets = {"Paytm Wallet", "PhonePe Wallet", "Amazon Pay"};
        walletChoiceBox = new JComboBox<>(wallets);
        UITheme.styleComboBox(walletChoiceBox);
        addRow(p, fieldLabel("Wallet"), 6);
        addRow(p, walletChoiceBox, 12);
        ModernButton btnSimWallet = ModernButton.createSecondary("Simulate OTP & debit", null).compact();
        btnSimWallet.addActionListener(e -> Toast.success(this, "Wallet authorised (simulated)", (String) walletChoiceBox.getSelectedItem()));
        JPanel bw = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bw.setOpaque(false);
        bw.add(btnSimWallet);
        addRow(p, bw, 0);
        return p;
    }

    private JPanel createFastagPanel() {
        JPanel p = vstack();
        fastagStatusLabel = new JLabel("Find a ticket to check for a FASTag on the vehicle.");
        fastagStatusLabel.setFont(UITheme.font(Font.BOLD, 13));
        fastagStatusLabel.setForeground(UITheme.TEXT_PRIMARY);
        addRow(p, fastagStatusLabel, 6);
        addRow(p, hint("The fee is debited automatically at the exit barrier; the driver doesn't need to stop."), 0);
        return p;
    }

    // ------------------------------------------------------------------ 3. gate & receipt
    private JComponent buildReceiptCard() {
        RoundedPanel card = stepCard("3", "Gate & receipt");
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        gateStatus = new GateStatus();
        body.add(gateStatus, BorderLayout.NORTH);

        receiptCards = new CardLayout();
        receiptHolder = new JPanel(receiptCards);
        receiptHolder.setOpaque(false);
        receiptHolder.add(new EmptyState(IconType.DOCUMENT, "No receipt yet",
                "The GST tax invoice appears here once payment is settled."), "EMPTY");
        receiptEditorPane = new JEditorPane();
        receiptEditorPane.setContentType("text/html");
        receiptEditorPane.setEditable(false);
        receiptEditorPane.setBackground(Color.WHITE);
        JScrollPane paper = new JScrollPane(receiptEditorPane);
        paper.setBorder(new DarkTheme.RoundBorder(UITheme.BORDER, UITheme.RADIUS));
        paper.getViewport().setBackground(Color.WHITE);
        receiptHolder.add(paper, "RECEIPT");
        receiptCards.show(receiptHolder, "EMPTY");
        body.add(receiptHolder, BorderLayout.CENTER);
        card.add(body, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(1, 3, 8, 0));
        actions.setOpaque(false);
        btnAdminRefund = ModernButton.createDanger("Refund", null).compact();
        btnAdminRefund.setToolTipText("Refund this payment (admin, reason is audit-logged)");
        btnAdminRefund.setEnabled(false);
        btnAdminRefund.addActionListener(e -> promptRefund());
        btnPrintReceipt = ModernButton.createSecondary("Print", VectorIconUtil.getIcon(IconType.PRINT, 13, UITheme.TEXT_SECONDARY)).compact();
        btnPrintReceipt.setEnabled(false);
        btnPrintReceipt.addActionListener(e -> printCurrentReceipt());
        btnDownloadPdf = ModernButton.createSecondary("PDF", VectorIconUtil.getIcon(IconType.DOWNLOAD, 13, UITheme.TEXT_SECONDARY)).compact();
        btnDownloadPdf.setToolTipText("Download the GST invoice as PDF");
        btnDownloadPdf.setEnabled(false);
        btnDownloadPdf.addActionListener(e -> downloadCurrentPdf());
        actions.add(btnAdminRefund);
        actions.add(btnPrintReceipt);
        actions.add(btnDownloadPdf);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    // =====================================================================================
    // Behaviour
    // =====================================================================================

    /** Pre-fills the lookup with a ticket number or plate (deep link from Floor map / Find vehicle) and previews the bill. */
    public void prefillLookup(String ticketOrPlate) {
        if (ticketOrPlate == null || ticketOrPlate.isBlank()) return;
        searchField.setText(ticketOrPlate.trim());
        calculatePreview();
    }

    private void showLookupError(String msg) {
        searchError.setText(msg);
        searchError.setIcon(VectorIconUtil.getIcon(IconType.ALERT, 13, UITheme.ERROR_TEXT));
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    private void calculatePreview() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            showLookupError("Enter a ticket number or licence plate.");
            return;
        }

        Optional<ParkingTicket> tktOpt = parkingService.searchVehicle(query);
        if (tktOpt.isEmpty()) {
            try {
                tktOpt = new com.smartparking.repository.TicketDao().findByTicketNumber(query);
            } catch (Exception ignored) {}
        }

        if (tktOpt.isEmpty() || tktOpt.get().getStatus() != com.smartparking.model.enums.TicketStatus.ACTIVE) {
            resetPreview();
            showLookupError("No active ticket found for \"" + query + "\".");
            return;
        }

        currentTicket = tktOpt.get();
        boolean isLost = lostTicketCheck.isSelected();
        LocalDateTime now = LocalDateTime.now();

        currentBreakdown = parkingService.getPricingService().calculateFeePreview(currentTicket, now, isLost);
        overriddenFee = null;
        overrideReason = null;

        // Calculate GST on current fee
        double taxable = currentBreakdown.getTotalAmount();
        currentGst = parkingService.getGstCalculator().calculate(taxable);

        // Vehicle summary
        String district = PlateUtil.district(currentTicket.getLicensePlate());
        billPlate.setText(PlateUtil.format(currentTicket.getLicensePlate()));
        billMeta.setText(currentBreakdown.getVehicleType().getDisplayName() + " · Bay " + currentTicket.getSlotId()
                + (district == null || district.isBlank() ? "" : " · " + district));
        billVehicleIcon.setIcon(VectorIconUtil.getVehicleIcon(currentBreakdown.getVehicleType(), 22, UITheme.ACCENT_TEXT));

        long hrs = currentBreakdown.getDurationMinutes() / 60;
        long mins = currentBreakdown.getDurationMinutes() % 60;
        lblDuration.setText(String.format("%dh %dm · %d billed hr", hrs, mins, currentBreakdown.getBilledHours()));
        lblBaseFee.setText(MoneyFormat.format(currentBreakdown.getBaseFee()));

        if (currentBreakdown.isPeakApplied() && currentBreakdown.getPeakMultiplier() > 1.0) {
            lblPeakMultiplier.setText(String.format("%.2f× applied", currentBreakdown.getPeakMultiplier()));
            lblPeakMultiplier.setForeground(UITheme.WARNING_TEXT);
        } else {
            lblPeakMultiplier.setText("None");
            lblPeakMultiplier.setForeground(UITheme.TEXT_SECONDARY);
        }

        if (currentBreakdown.isPassActive()) {
            lblPassDiscount.setText(String.format("−%s · valid to %s",
                    MoneyFormat.format(currentBreakdown.getPassDiscount()),
                    DateFormat.displayDate(currentBreakdown.getPassExpiryDate())));
            lblPassDiscount.setForeground(UITheme.SUCCESS_TEXT);
        } else {
            lblPassDiscount.setText("None");
            lblPassDiscount.setForeground(UITheme.TEXT_SECONDARY);
        }

        if (currentBreakdown.getLostTicketPenalty() > 0.0) {
            lblPenalty.setText("Lost ticket " + MoneyFormat.format(currentBreakdown.getLostTicketPenalty()));
            lblPenalty.setForeground(UITheme.ERROR_TEXT);
        } else {
            lblPenalty.setText("None");
            lblPenalty.setForeground(UITheme.TEXT_SECONDARY);
        }

        renderGst();
        lblOverrideNote.setText(" ");

        // FASTag Detection & Notification
        Optional<FastagAccount> fastagOpt = parkingService.getFastagService().detectTag(currentTicket.getLicensePlate());
        if (fastagOpt.isPresent()) {
            FastagAccount fa = fastagOpt.get();
            if (fa.getBalance() >= currentGst.grandTotal()) {
                fastagStatusLabel.setText(String.format("<html>Tag %s detected<br><span style='font-weight:normal'>Balance %s · sufficient</span></html>",
                        fa.getTagId(), MoneyFormat.format(fa.getBalance())));
                fastagStatusLabel.setForeground(UITheme.SUCCESS_TEXT);
                selectedPaymentMethod = PaymentMethodType.FASTAG;
            } else {
                fastagStatusLabel.setText(String.format("<html>Tag %s · low balance<br><span style='font-weight:normal'>%s available, %s due. Choose another method.</span></html>",
                        fa.getTagId(), MoneyFormat.format(fa.getBalance()), MoneyFormat.format(currentGst.grandTotal())));
                fastagStatusLabel.setForeground(UITheme.WARNING_TEXT);
                selectedPaymentMethod = PaymentMethodType.UPI;
            }
        } else {
            fastagStatusLabel.setText("No FASTag registered for " + PlateUtil.format(currentTicket.getLicensePlate()));
            fastagStatusLabel.setForeground(UITheme.TEXT_SECONDARY);
            if (selectedPaymentMethod == PaymentMethodType.FASTAG) {
                selectedPaymentMethod = PaymentMethodType.UPI;
            }
        }

        // Generate dynamic UPI QR Code for instant scanning
        updateDynamicUpiQr(currentGst.grandTotal());

        // Enable payment buttons
        setPaymentButtonsEnabled(true);
        btnOverrideFee.setEnabled(true);
        btnCompleteCheckout.setEnabled(true);
        updatePaymentSelectionUI();

        billCards.show(billHolder, "BILL");
        gateStatus.set(false, "Barrier closed", "Waiting for payment of " + MoneyFormat.format(currentGst.grandTotal()));
        receiptCards.show(receiptHolder, "EMPTY");
        btnDownloadPdf.setEnabled(false);
        btnPrintReceipt.setEnabled(false);
        btnAdminRefund.setEnabled(false);
        currentInvoice = null;
        currentPayment = null;
    }

    private void renderGst() {
        lblTaxable.setText(MoneyFormat.format(currentGst.taxableValue()));
        lblCgst.setText(MoneyFormat.format(currentGst.cgstAmount()));
        lblSgst.setText(MoneyFormat.format(currentGst.sgstAmount()));
        lblRoundOff.setText(String.format("%+.2f", currentGst.roundOff()));
        lblTotalDue.setText(MoneyFormat.format(currentGst.grandTotal()));
        btnCompleteCheckout.setText("Settle " + MoneyFormat.format(currentGst.grandTotal()) + " & open barrier");
    }

    private void updateDynamicUpiQr(double amount) {
        String vpa = "parking@upi";
        String name = "Grand Tech Park Parking Ltd";
        String note = currentTicket != null ? ("Parking Fee " + currentTicket.getTicketNumber()) : "Parking Fee";
        String upiIntent = UpiUtil.buildUpiIntentUrl(vpa, name, amount, note);
        upiStringLabel.setText(upiIntent.length() > 44 ? upiIntent.substring(0, 44) + "…" : upiIntent);
        upiStringLabel.setToolTipText(upiIntent);

        java.awt.image.BufferedImage bi = BarcodeGenerator.generateQrCode(upiIntent, 96);
        if (bi != null) {
            upiQrLabel.setIcon(new ImageIcon(bi));
        }
    }

    private void promptFeeOverride() {
        if (currentBreakdown == null || currentTicket == null) return;

        JTextField feeField = new JTextField(String.valueOf(currentBreakdown.getTotalAmount()));
        UITheme.styleTextField(feeField);
        JTextField reasonField = new JTextField();
        UITheme.styleTextField(reasonField);

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 6));
        panel.setOpaque(false);
        panel.add(new JLabel("Calculated fee (before GST): " + MoneyFormat.format(currentBreakdown.getTotalAmount())));
        panel.add(new JLabel("New fee (₹)"));
        panel.add(feeField);
        panel.add(new JLabel("Reason (required, recorded in the audit log)"));
        panel.add(reasonField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Override fee", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String rText = reasonField.getText().trim();
            if (rText.length() < 5) {
                Toast.error(this, "Override not applied", "A reason of at least 5 characters is required.");
                return;
            }
            try {
                double newFee = Double.parseDouble(feeField.getText().trim());
                if (newFee < 0) {
                    Toast.error(this, "Override not applied", "The fee can't be negative.");
                    return;
                }
                overriddenFee = newFee;
                overrideReason = rText;

                // Recompute GST on overridden taxable fee
                currentGst = parkingService.getGstCalculator().calculate(newFee);

                // Record in audit log
                parkingService.getPricingService().getTariffService().recordFeeOverride(
                        currentTicket.getTicketNumber(), currentBreakdown.getTotalAmount(), newFee, rText, "Duty Manager");

                renderGst();
                lblTotalDue.setForeground(UITheme.WARNING_TEXT);
                lblOverrideNote.setText("Overridden: " + rText);
                updateDynamicUpiQr(currentGst.grandTotal());
                updateCashChange();

                Toast.success(this, "Fee overridden to " + MoneyFormat.format(currentGst.grandTotal()), "Recorded in the audit log");
            } catch (NumberFormatException ex) {
                Toast.error(this, "Override not applied", "Enter a valid number for the fee.");
            }
        }
    }

    private void resetPreview() {
        currentTicket = null;
        currentBreakdown = null;
        currentGst = null;
        overriddenFee = null;
        overrideReason = null;
        currentInvoice = null;
        currentPayment = null;

        billCards.show(billHolder, "EMPTY");
        lblTotalDue.setText("₹0.00");
        lblTotalDue.setForeground(UITheme.TEXT_PRIMARY);
        lblOverrideNote.setText(" ");
        btnCompleteCheckout.setText("Settle & open barrier");
        gateStatus.set(false, "Barrier closed", "Ready for the next vehicle");

        setPaymentButtonsEnabled(false);
        btnOverrideFee.setEnabled(false);
        btnCompleteCheckout.setEnabled(false);
        btnDownloadPdf.setEnabled(false);
        btnPrintReceipt.setEnabled(false);
        btnAdminRefund.setEnabled(false);
        receiptCards.show(receiptHolder, "EMPTY");
    }

    private void setPaymentButtonsEnabled(boolean enabled) {
        for (PayOption o : payOptions.values()) o.setEnabled(enabled);
    }

    private void updatePaymentSelectionUI() {
        for (Map.Entry<PaymentMethodType, PayOption> e : payOptions.entrySet()) {
            e.getValue().setSelected(e.getKey() == selectedPaymentMethod);
        }
        paymentCardLayout.show(paymentDetailsContainer, selectedPaymentMethod.name());
        if (selectedPaymentMethod == PaymentMethodType.CASH) {
            updateCashChange();
        } else if (selectedPaymentMethod == PaymentMethodType.CARD) {
            btnCompleteCheckout.setEnabled(simulatedCardAuthorized && currentTicket != null);
        } else {
            btnCompleteCheckout.setEnabled(currentTicket != null);
        }
    }

    private void processExit() {
        if (currentTicket == null) {
            showLookupError("Find the ticket before settling payment.");
            return;
        }

        btnCompleteCheckout.setEnabled(false);
        try {
            // Server-side double payment guard & atomic transaction
            ParkingService.CheckoutResult result;
            if (lostTicketCheck.isSelected()) {
                result = parkingService.processLostTicketCheckout(currentTicket.getLicensePlate(), selectedPaymentMethod);
            } else {
                result = parkingService.processVehicleCheckout(currentTicket.getTicketNumber(), selectedPaymentMethod);
            }

            currentInvoice = result.invoice();
            currentPayment = result.payment();
            ParkingTicket settledTicket = result.ticket();

            // 1. Open barrier
            gateStatus.set(true, "Barrier open",
                    PlateUtil.format(settledTicket.getLicensePlate()) + " · paid " + MoneyFormat.format(settledTicket.getTotalAmount())
                            + " via " + selectedPaymentMethod.getDescription());

            // 2. Render on-screen GST invoice
            String invoiceHtml = parkingService.getInvoiceService().generateHtmlReceipt(currentInvoice);
            receiptEditorPane.setText(invoiceHtml);
            receiptEditorPane.setCaretPosition(0);
            receiptCards.show(receiptHolder, "RECEIPT");

            // Enable invoice actions
            btnDownloadPdf.setEnabled(true);
            btnPrintReceipt.setEnabled(true);
            btnAdminRefund.setEnabled(true);

            btnOverrideFee.setEnabled(false);
            setPaymentButtonsEnabled(false);
            searchField.setText("");
            lostTicketCheck.setSelected(false);
            currentTicket = null;

            if (onStateChange != null) {
                onStateChange.run();
            }

            Toast.success(this, "Payment of " + MoneyFormat.format(settledTicket.getTotalAmount()) + " settled",
                    "Invoice " + currentInvoice.getInvoiceNumber() + " · barrier open");

        } catch (Exception ex) {
            btnCompleteCheckout.setEnabled(currentTicket != null);
            Toast.error(this, "Checkout failed", ex.getMessage());
        }
    }

    private void downloadCurrentPdf() {
        if (currentInvoice == null) {
            Toast.warning(this, "Nothing to export", "Settle a payment first.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("Invoice_" + currentInvoice.getInvoiceNumber().replace("/", "_") + ".pdf"));
        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            try {
                File target = chooser.getSelectedFile();
                parkingService.getInvoiceService().exportPdf(currentInvoice, target);
                Toast.success(this, "Invoice saved", target.getAbsolutePath());
            } catch (Exception ex) {
                Toast.error(this, "PDF export failed", ex.getMessage());
            }
        }
    }

    private void printCurrentReceipt() {
        if (receiptEditorPane == null) return;
        try {
            boolean done = receiptEditorPane.print();
            if (done) {
                Toast.success(this, "Sent to printer", null);
            }
        } catch (Exception ex) {
            Toast.error(this, "Print failed", ex.getMessage());
        }
    }

    private void promptRefund() {
        if (currentPayment == null) {
            Toast.warning(this, "Nothing to refund", "No settled payment is selected.");
            return;
        }

        String reason = (String) JOptionPane.showInputDialog(this,
                "Refund " + MoneyFormat.format(currentPayment.getAmount()) + " for payment #" + currentPayment.getPaymentId() + "?\n"
                        + "This can't be undone. Enter a reason (recorded in the audit log):",
                "Refund payment",
                JOptionPane.WARNING_MESSAGE, null, null, "");

        if (reason == null) return; // User cancelled

        if (reason.trim().length() < 5) {
            Toast.error(this, "Refund not processed", "A reason of at least 5 characters is required.");
            return;
        }

        try {
            boolean success = parkingService.refundPayment(currentPayment.getPaymentId(), reason.trim(), "admin");
            if (success) {
                btnAdminRefund.setEnabled(false);
                gateStatus.set(true, "Payment refunded", "Payment #" + currentPayment.getPaymentId() + " · recorded in the audit log");
                Toast.success(this, "Payment #" + currentPayment.getPaymentId() + " refunded", "Recorded in the audit log");
                if (onStateChange != null) onStateChange.run();
            } else {
                Toast.error(this, "Refund not processed", "The payment was already refunded or isn't completed.");
            }
        } catch (Exception ex) {
            Toast.error(this, "Refund failed", ex.getMessage());
        }
    }

    // =====================================================================================
    // Components
    // =====================================================================================

    /** Radio-style payment method row: icon, title, hint, selection ring. */
    private class PayOption extends JPanel {
        private final PaymentMethodType method;
        private final JLabel icon;
        private final JLabel title;
        private final JLabel sub;
        private final IconType iconType;
        private boolean selected;
        private boolean hover;

        PayOption(PaymentMethodType method, String titleText, String hintText, IconType iconType) {
            super(new BorderLayout(12, 0));
            this.method = method;
            this.iconType = iconType;
            setOpaque(false);
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
            getAccessibleContext().setAccessibleName("Pay by " + titleText);
            icon = new JLabel();
            icon.setPreferredSize(new Dimension(18, 18));
            add(icon, BorderLayout.WEST);
            JPanel t = new JPanel(new BorderLayout(8, 0));
            t.setOpaque(false);
            title = new JLabel(titleText);
            title.setFont(UITheme.font(Font.BOLD, 13));
            sub = new JLabel(hintText);
            sub.setFont(UITheme.font(Font.PLAIN, 12));
            t.add(title, BorderLayout.WEST);
            t.add(sub, BorderLayout.CENTER);
            add(t, BorderLayout.CENTER);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    choose();
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
            refresh();
        }

        private void choose() {
            if (!isEnabled()) return;
            selectedPaymentMethod = method;
            updatePaymentSelectionUI();
        }

        void setSelected(boolean s) {
            selected = s;
            refresh();
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            refresh();
        }

        private void refresh() {
            boolean en = isEnabled();
            icon.setIcon(VectorIconUtil.getIcon(iconType, 16, !en ? UITheme.TEXT_DISABLED : selected ? UITheme.ACCENT_TEXT : UITheme.TEXT_SECONDARY));
            title.setForeground(en ? UITheme.TEXT_PRIMARY : UITheme.TEXT_DISABLED);
            sub.setForeground(en ? UITheme.TEXT_TERTIARY : UITheme.TEXT_DISABLED);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight(), arc = UITheme.RADIUS;
            boolean en = isEnabled();
            g2.setColor(selected && en ? UITheme.ACCENT_SOFT : hover && en ? UITheme.BG_HOVER : UITheme.BG_SURFACE);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(selected && en ? UITheme.ACCENT : UITheme.BORDER_SUBTLE);
            g2.setStroke(new BasicStroke(selected && en ? 1.5f : 1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            // radio dot on the right
            int r = 14, cx = w - 14 - r, cy = (h - r) / 2;
            g2.setColor(selected && en ? UITheme.ACCENT : UITheme.BORDER_STRONG);
            g2.drawOval(cx, cy, r, r);
            if (selected && en) g2.fillOval(cx + 4, cy + 4, r - 7, r - 7);
            if (isFocusOwner() && DarkTheme.isFocusVisible()) {
                g2.setColor(UITheme.ACCENT_TEXT);
                g2.drawRoundRect(2, 2, w - 5, h - 5, arc - 2, arc - 2);
            }
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public Insets getInsets() {
            Insets i = super.getInsets();
            return new Insets(i.top, i.left, i.bottom, i.right + 26);
        }
    }

    /** Barrier status strip: red "closed" or green "open" with a short caption. */
    private static class GateStatus extends JPanel {
        private final JLabel title = new JLabel();
        private final JLabel sub = new JLabel();
        private boolean open;

        GateStatus() {
            super(new BorderLayout(12, 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            JPanel t = new JPanel();
            t.setOpaque(false);
            t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
            title.setFont(UITheme.font(Font.BOLD, 13));
            sub.setFont(UITheme.font(Font.PLAIN, 12));
            sub.setForeground(UITheme.TEXT_SECONDARY);
            t.add(title);
            t.add(Box.createVerticalStrut(2));
            t.add(sub);
            JComponent light = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color c = open ? UITheme.SUCCESS : UITheme.ERROR;
                    g2.setColor(UITheme.alpha(c, 50));
                    g2.fillOval(0, getHeight() / 2 - 9, 18, 18);
                    g2.setColor(c);
                    g2.fillOval(5, getHeight() / 2 - 4, 8, 8);
                    g2.dispose();
                }
            };
            light.setPreferredSize(new Dimension(18, 18));
            add(light, BorderLayout.WEST);
            add(t, BorderLayout.CENTER);
            set(false, "Barrier closed", "Ready for the next vehicle");
        }

        void set(boolean open, String t, String s) {
            this.open = open;
            title.setText(t);
            title.setForeground(open ? UITheme.SUCCESS_TEXT : UITheme.TEXT_PRIMARY);
            sub.setText(s);
            sub.setToolTipText(s);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = open ? UITheme.SUCCESS : UITheme.ERROR;
            g2.setColor(UITheme.blend(c, UITheme.BG_CARD, 0.08));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, UITheme.RADIUS, UITheme.RADIUS);
            g2.setColor(UITheme.blend(c, UITheme.BG_CARD, 0.30));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, UITheme.RADIUS, UITheme.RADIUS);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
