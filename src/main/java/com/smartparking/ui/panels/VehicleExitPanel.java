package com.smartparking.ui.panels;

import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.model.fastag.FastagAccount;
import com.smartparking.model.invoice.Invoice;
import com.smartparking.model.payment.PaymentTransaction;
import com.smartparking.model.tariff.FeeBreakdown;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.service.GstCalculator;
import com.smartparking.service.ParkingService;
import com.smartparking.ui.components.ModernButton;
import com.smartparking.ui.components.RoundedPanel;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.ImageLoader;
import com.smartparking.util.BarcodeGenerator;
import com.smartparking.util.DateFormat;
import com.smartparking.util.MoneyFormat;
import com.smartparking.util.PlateUtil;
import com.smartparking.util.UpiUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Gate 2 Vehicle Exit & Automated Checkout Panel.
 * Supports barcode/QR scanner input, comprehensive GST breakdown (CGST, SGST, round-off),
 * payment channels (Cash, UPI QR, Card, Wallet, FASTag auto-debit),
 * dynamic barrier gate status, responsive styled HTML receipts, and A4 PDF export.
 */
public class VehicleExitPanel extends JPanel {

    private final ParkingService parkingService;
    private final Runnable onStateChange;

    private JTextField searchField;
    private JCheckBox lostTicketCheck;
    private ModernButton btnCalculatePreview;
    private ModernButton btnOverrideFee;

    // Fee Preview labels
    private JPanel previewContainer;
    private JLabel lblRuleNotice;
    private JLabel lblVehicleClass;
    private JLabel lblDuration;
    private JLabel lblBaseFee;
    private JLabel lblPeakMultiplier;
    private JLabel lblPassDiscount;
    private JLabel lblPenalty;
    private JLabel lblTaxBreakdown;
    private JLabel lblTotalDue;

    // Payment method buttons & sub-panels
    private PaymentMethodType selectedPaymentMethod = PaymentMethodType.UPI;
    private JButton btnCash;
    private JButton btnCard;
    private JButton btnUpi;
    private JButton btnWallet;
    private JButton btnFastag;
    private JPanel paymentDetailsContainer;
    private CardLayout paymentCardLayout;

    // Cash sub-panel components
    private JTextField cashReceivedField;
    private JLabel cashChangeLabel;

    // UPI sub-panel components
    private JLabel upiQrLabel;
    private JLabel upiStringLabel;

    // Card sub-panel components
    private JTextField cardLast4Field;
    private JTextField cardHolderField;
    private boolean simulatedCardAuthorized = true;

    // FASTag sub-panel components
    private JLabel fastagStatusLabel;

    // Wallet sub-panel components
    private JComboBox<String> walletChoiceBox;

    private ModernButton btnCompleteCheckout;

    // Right-side Barrier Gate & Receipt
    private JLabel barrierGateBanner;
    private JEditorPane receiptEditorPane;
    private JLabel exitVehicleBadge;
    private JLabel exitVehicleTitle;
    private JLabel exitVehicleSubtitle;
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

        setLayout(new BorderLayout(0, 14));
        setBackground(UITheme.BG_CANVAS);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        initUI();
    }

    private void initUI() {
        // --- TOP: Header Title ---
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("GATE 2: VEHICLE EXIT & AUTOMATED CHECKOUT", ImageLoader.getNavIcon("exit", 24), SwingConstants.LEFT);
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.TEXT_DARK);
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        // --- CENTER: Split Layout ---
        JPanel centerSplit = new JPanel(new GridLayout(1, 2, 18, 0));
        centerSplit.setOpaque(false);

        // LEFT: Checkout Form & Preview Card
        RoundedPanel formCard = new RoundedPanel(16, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        formCard.setLayout(new BoxLayout(formCard, BoxLayout.Y_AXIS));
        formCard.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        // 1. Vehicle Lookup (Supports Barcode Scanner Enter Key)
        JLabel step1 = new JLabel("1. Vehicle Lookup / Barcode Scan (Ticket # or UP Plate):");
        step1.setFont(UITheme.FONT_SUBTITLE);
        step1.setForeground(UITheme.TEXT_DARK);
        formCard.add(step1);
        formCard.add(Box.createVerticalStrut(6));

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        searchField = new JTextField();
        UITheme.styleTextField(searchField);
        // Barcode / QR scanner compatibility: hitting Enter triggers immediate preview
        searchField.addActionListener(e -> calculatePreview());
        searchRow.add(searchField, BorderLayout.CENTER);

        btnCalculatePreview = new ModernButton("Calculate Preview", ImageLoader.getNavIcon("search", 16));
        btnCalculatePreview.addActionListener(e -> calculatePreview());
        searchRow.add(btnCalculatePreview, BorderLayout.EAST);
        formCard.add(searchRow);
        formCard.add(Box.createVerticalStrut(4));

        lostTicketCheck = new JCheckBox("Ticket is Lost! (Look up by License Plate & Apply \u20B9250 Penalty)");
        lostTicketCheck.setFont(UITheme.FONT_REGULAR);
        lostTicketCheck.setForeground(UITheme.COLOR_OCCUPIED);
        lostTicketCheck.setOpaque(false);
        lostTicketCheck.addActionListener(e -> {
            if (currentTicket != null) calculatePreview();
        });
        formCard.add(lostTicketCheck);
        formCard.add(Box.createVerticalStrut(10));

        // 2. Fee Preview Card with GST breakdown
        JLabel step2 = new JLabel("2. Automated Tariff & GST Tax Preview:");
        step2.setFont(UITheme.FONT_SUBTITLE);
        step2.setForeground(UITheme.TEXT_DARK);
        formCard.add(step2);
        formCard.add(Box.createVerticalStrut(6));

        previewContainer = new JPanel();
        previewContainer.setLayout(new BoxLayout(previewContainer, BoxLayout.Y_AXIS));
        previewContainer.setBackground(new Color(248, 250, 252));
        previewContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        previewContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 185));

        lblRuleNotice = new JLabel("Rule: First 15 min free. Tariff billed per started hour. GST 18% (9% CGST + 9% SGST).");
        lblRuleNotice.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblRuleNotice.setForeground(new Color(30, 64, 175));

        lblVehicleClass = new JLabel("Vehicle Class: \u2014");
        lblVehicleClass.setFont(UITheme.FONT_REGULAR);

        lblDuration = new JLabel("Duration: \u2014");
        lblDuration.setFont(UITheme.FONT_REGULAR);

        lblBaseFee = new JLabel("Base Parking Fee: \u20B90.00");
        lblBaseFee.setFont(UITheme.FONT_REGULAR);

        lblPeakMultiplier = new JLabel("Peak Surge Multiplier: None (Normal Tariff)");
        lblPeakMultiplier.setFont(UITheme.FONT_REGULAR);

        lblPassDiscount = new JLabel("Monthly Pass: None");
        lblPassDiscount.setFont(UITheme.FONT_REGULAR);

        lblPenalty = new JLabel("Penalties: None");
        lblPenalty.setFont(UITheme.FONT_REGULAR);

        lblTaxBreakdown = new JLabel("GST (18%): Taxable \u20B90.00 | CGST \u20B90.00 | SGST \u20B90.00 | Round-off \u20B90.00");
        lblTaxBreakdown.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblTaxBreakdown.setForeground(new Color(71, 85, 105));

        lblTotalDue = new JLabel("TOTAL AMOUNT DUE: \u20B90.00");
        lblTotalDue.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTotalDue.setForeground(new Color(15, 23, 42));

        previewContainer.add(lblRuleNotice);
        previewContainer.add(Box.createVerticalStrut(3));
        previewContainer.add(lblVehicleClass);
        previewContainer.add(lblDuration);
        previewContainer.add(lblBaseFee);
        previewContainer.add(lblPeakMultiplier);
        previewContainer.add(lblPassDiscount);
        previewContainer.add(lblPenalty);
        previewContainer.add(lblTaxBreakdown);
        previewContainer.add(Box.createVerticalStrut(4));
        previewContainer.add(lblTotalDue);
        formCard.add(previewContainer);
        formCard.add(Box.createVerticalStrut(6));

        // Admin Override Button
        JPanel overrideRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        overrideRow.setOpaque(false);
        btnOverrideFee = ModernButton.createSecondary("\u2699 Override Fee (Admin)", ImageLoader.getNavIcon("users", 14));
        btnOverrideFee.setEnabled(false);
        btnOverrideFee.addActionListener(e -> promptFeeOverride());
        overrideRow.add(btnOverrideFee);
        formCard.add(overrideRow);
        formCard.add(Box.createVerticalStrut(8));

        // 3. Payment Method Selection Grid (5 channels: Cash, UPI, Card, Wallet, FASTag)
        JLabel step3 = new JLabel("3. Payment Method & Settlement (Simulated):");
        step3.setFont(UITheme.FONT_SUBTITLE);
        step3.setForeground(UITheme.TEXT_DARK);
        formCard.add(step3);
        formCard.add(Box.createVerticalStrut(6));

        JPanel payGrid = new JPanel(new GridLayout(1, 5, 6, 0));
        payGrid.setOpaque(false);
        payGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));

        btnCash = createPayButton("Cash", PaymentMethodType.CASH, "cash");
        btnUpi = createPayButton("UPI (Simulated)", PaymentMethodType.UPI, "upi");
        btnCard = createPayButton("Card (Simulated)", PaymentMethodType.CARD, "card");
        btnWallet = createPayButton("Wallet (Simulated)", PaymentMethodType.WALLET, "wallet");
        btnFastag = createPayButton("FASTag (Simulated)", PaymentMethodType.FASTAG, "card");

        payGrid.add(btnCash);
        payGrid.add(btnUpi);
        payGrid.add(btnCard);
        payGrid.add(btnWallet);
        payGrid.add(btnFastag);
        formCard.add(payGrid);
        formCard.add(Box.createVerticalStrut(8));

        // Payment Details Interactive Sub-Card
        paymentCardLayout = new CardLayout();
        paymentDetailsContainer = new JPanel(paymentCardLayout);
        paymentDetailsContainer.setBackground(new Color(241, 245, 249));
        paymentDetailsContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        paymentDetailsContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        paymentDetailsContainer.setPreferredSize(new Dimension(500, 130));

        paymentDetailsContainer.add(createCashPanel(), PaymentMethodType.CASH.name());
        paymentDetailsContainer.add(createUpiPanel(), PaymentMethodType.UPI.name());
        paymentDetailsContainer.add(createCardPanel(), PaymentMethodType.CARD.name());
        paymentDetailsContainer.add(createWalletPanel(), PaymentMethodType.WALLET.name());
        paymentDetailsContainer.add(createFastagPanel(), PaymentMethodType.FASTAG.name());

        formCard.add(paymentDetailsContainer);
        formCard.add(Box.createVerticalStrut(10));

        btnCompleteCheckout = ModernButton.createDanger("SETTLE PAYMENT & RAISE BARRIER", ImageLoader.getNavIcon("exit", 20));
        btnCompleteCheckout.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnCompleteCheckout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        btnCompleteCheckout.setEnabled(false);
        btnCompleteCheckout.addActionListener(e -> processExit());
        formCard.add(btnCompleteCheckout);

        centerSplit.add(formCard);

        // RIGHT: Barrier Gate Visual Status & Styled Customer Receipt Card
        RoundedPanel receiptCard = new RoundedPanel(16, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        receiptCard.setLayout(new BorderLayout(0, 10));
        receiptCard.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        JPanel receiptNorth = new JPanel();
        receiptNorth.setLayout(new BoxLayout(receiptNorth, BoxLayout.Y_AXIS));
        receiptNorth.setOpaque(false);

        // Prominent Barrier Gate Visual Indicator
        barrierGateBanner = new JLabel("\uD83D\uDD34 Barrier Gate Closed \u2014 Settle Payment to Proceed", SwingConstants.CENTER);
        barrierGateBanner.setFont(new Font("Segoe UI", Font.BOLD, 13));
        barrierGateBanner.setForeground(new Color(153, 27, 27));
        barrierGateBanner.setBackground(new Color(254, 226, 226));
        barrierGateBanner.setOpaque(true);
        barrierGateBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(252, 165, 165), 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        barrierGateBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        barrierGateBanner.setAlignmentX(Component.CENTER_ALIGNMENT);
        receiptNorth.add(barrierGateBanner);
        receiptNorth.add(Box.createVerticalStrut(8));

        // Vehicle Clearance Banner
        JPanel exitVehicleBanner = new JPanel(new BorderLayout(12, 0));
        exitVehicleBanner.setOpaque(true);
        exitVehicleBanner.setBackground(new Color(248, 250, 252));
        exitVehicleBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        exitVehicleBadge = new JLabel(ImageLoader.getVehicleIcon(null, 42, 42));
        exitVehicleTitle = new JLabel("Exit Gate: Ready for Vehicle Scan");
        exitVehicleTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        exitVehicleTitle.setForeground(new Color(30, 41, 59));

        exitVehicleSubtitle = new JLabel("Scan ticket barcode or enter registration plate to calculate charges.");
        exitVehicleSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        exitVehicleSubtitle.setForeground(new Color(100, 116, 139));

        JPanel textPane = new JPanel(new GridLayout(2, 1, 0, 2));
        textPane.setOpaque(false);
        textPane.add(exitVehicleTitle);
        textPane.add(exitVehicleSubtitle);

        exitVehicleBanner.add(exitVehicleBadge, BorderLayout.WEST);
        exitVehicleBanner.add(textPane, BorderLayout.CENTER);
        receiptNorth.add(exitVehicleBanner);

        receiptCard.add(receiptNorth, BorderLayout.NORTH);

        // Center: Styled HTML Receipt Viewer
        receiptEditorPane = new JEditorPane();
        receiptEditorPane.setContentType("text/html");
        receiptEditorPane.setEditable(false);
        receiptEditorPane.setBackground(Color.WHITE);
        receiptEditorPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 1, true));
        setInitialReceiptHtml();

        receiptCard.add(new JScrollPane(receiptEditorPane), BorderLayout.CENTER);

        // Bottom Action Bar: Download PDF, Print, Admin Refund
        JPanel receiptActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        receiptActions.setOpaque(false);

        btnAdminRefund = ModernButton.createSecondary("\u21A9 Refund (Admin)", ImageLoader.getNavIcon("reports", 14));
        btnAdminRefund.setEnabled(false);
        btnAdminRefund.addActionListener(e -> promptRefund());
        receiptActions.add(btnAdminRefund);

        btnPrintReceipt = ModernButton.createSecondary("\uD83D\uDDA8 Print", ImageLoader.getNavIcon("reports", 14));
        btnPrintReceipt.setEnabled(false);
        btnPrintReceipt.addActionListener(e -> printCurrentReceipt());
        receiptActions.add(btnPrintReceipt);

        btnDownloadPdf = new ModernButton("\uD83D\uDCE5 Download PDF", ImageLoader.getNavIcon("reports", 14));
        btnDownloadPdf.setEnabled(false);
        btnDownloadPdf.addActionListener(e -> downloadCurrentPdf());
        receiptActions.add(btnDownloadPdf);

        receiptCard.add(receiptActions, BorderLayout.SOUTH);
        centerSplit.add(receiptCard);

        add(centerSplit, BorderLayout.CENTER);

        setPaymentButtonsEnabled(false);
        updatePaymentSelectionUI();
    }

    private JPanel createCashPanel() {
        JPanel p = new JPanel(new GridLayout(2, 2, 8, 6));
        p.setOpaque(false);

        JLabel lblRec = new JLabel("Amount Received (\u20B9):");
        lblRec.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cashReceivedField = new JTextField();
        UITheme.styleTextField(cashReceivedField);

        cashChangeLabel = new JLabel("Change to Return: \u20B90.00");
        cashChangeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cashChangeLabel.setForeground(new Color(22, 101, 52));

        cashReceivedField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateCashChange(); }
            public void removeUpdate(DocumentEvent e) { updateCashChange(); }
            public void changedUpdate(DocumentEvent e) { updateCashChange(); }
        });

        p.add(lblRec);
        p.add(cashReceivedField);
        p.add(new JLabel("Cash Validation:"));
        p.add(cashChangeLabel);
        return p;
    }

    private void updateCashChange() {
        if (currentGst == null) return;
        double total = currentGst.grandTotal();
        try {
            String text = cashReceivedField.getText().trim();
            if (text.isEmpty()) {
                cashChangeLabel.setText("Enter amount received");
                cashChangeLabel.setForeground(UITheme.TEXT_DARK);
                btnCompleteCheckout.setEnabled(false);
                return;
            }
            double received = Double.parseDouble(text);
            double change = received - total;
            if (change < 0) {
                cashChangeLabel.setText(String.format("Short by %s", MoneyFormat.format(-change)));
                cashChangeLabel.setForeground(UITheme.COLOR_OCCUPIED);
                btnCompleteCheckout.setEnabled(false);
            } else {
                cashChangeLabel.setText(String.format("Change to Return: %s", MoneyFormat.format(change)));
                cashChangeLabel.setForeground(new Color(22, 101, 52));
                btnCompleteCheckout.setEnabled(true);
            }
        } catch (NumberFormatException ex) {
            cashChangeLabel.setText("Invalid numeric cash");
            cashChangeLabel.setForeground(UITheme.COLOR_OCCUPIED);
            btnCompleteCheckout.setEnabled(false);
        }
    }

    private JPanel createUpiPanel() {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);

        upiQrLabel = new JLabel();
        upiQrLabel.setPreferredSize(new Dimension(80, 80));
        upiQrLabel.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(upiQrLabel, BorderLayout.WEST);

        JPanel details = new JPanel(new GridLayout(3, 1, 0, 2));
        details.setOpaque(false);
        JLabel vpaLbl = new JLabel("Payee VPA: parking@upi (Grand Tech Park Parking Ltd)");
        vpaLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        upiStringLabel = new JLabel("Scan dynamic QR with BHIM, PhonePe, Google Pay, or Paytm.");
        upiStringLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        upiStringLabel.setForeground(new Color(71, 85, 105));

        JButton btnSimUpi = new JButton("\u2714 Simulate UPI Payment Confirmation");
        btnSimUpi.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnSimUpi.setBackground(new Color(220, 252, 231));
        btnSimUpi.setForeground(new Color(22, 101, 52));
        btnSimUpi.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSimUpi.addActionListener(e -> JOptionPane.showMessageDialog(this, "Simulated UPI authorization confirmed via payment gateway webhook.", "UPI Verified", JOptionPane.INFORMATION_MESSAGE));

        details.add(vpaLbl);
        details.add(upiStringLabel);
        details.add(btnSimUpi);
        p.add(details, BorderLayout.CENTER);
        return p;
    }

    private JPanel createCardPanel() {
        // Simulated Card Terminal — PCI-DSS Compliant: We do not capture or store full PAN or CVV.
        JPanel p = new JPanel(new GridLayout(3, 2, 8, 4));
        p.setOpaque(false);

        cardHolderField = new JTextField("Authorized Driver");
        cardLast4Field = new JTextField("4321");

        JButton btnSimSuccess = new JButton("\u2705 Simulate Success");
        btnSimSuccess.setFont(new Font("Segoe UI", Font.BOLD, 10));
        btnSimSuccess.setBackground(new Color(220, 252, 231));
        btnSimSuccess.addActionListener(e -> {
            simulatedCardAuthorized = true;
            btnCompleteCheckout.setEnabled(true);
            JOptionPane.showMessageDialog(this, "Card terminal approved transaction (Simulated Auth Code: " + (int)(Math.random() * 899999 + 100000) + ")", "Card Approved", JOptionPane.INFORMATION_MESSAGE);
        });

        JButton btnSimFail = new JButton("\u274C Simulate Failure");
        btnSimFail.setFont(new Font("Segoe UI", Font.BOLD, 10));
        btnSimFail.setBackground(new Color(254, 226, 226));
        btnSimFail.addActionListener(e -> {
            simulatedCardAuthorized = false;
            btnCompleteCheckout.setEnabled(false);
            JOptionPane.showMessageDialog(this, "Card terminal error: 51 Insufficient Funds / Bank Declined (Simulated)", "Card Declined", JOptionPane.ERROR_MESSAGE);
        });

        p.add(new JLabel("Cardholder Name:"));
        p.add(cardHolderField);
        p.add(new JLabel("Last 4 Digits (PCI-DSS):"));
        p.add(cardLast4Field);
        p.add(btnSimSuccess);
        p.add(btnSimFail);
        return p;
    }

    private JPanel createWalletPanel() {
        JPanel p = new JPanel(new GridLayout(2, 2, 8, 6));
        p.setOpaque(false);

        String[] wallets = {"Paytm Wallet", "PhonePe Wallet", "Amazon Pay"};
        walletChoiceBox = new JComboBox<>(wallets);
        UITheme.styleComboBox(walletChoiceBox);

        JButton btnSimWallet = new JButton("Simulate OTP / Debit");
        btnSimWallet.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnSimWallet.setBackground(new Color(237, 233, 254));
        btnSimWallet.setForeground(new Color(91, 33, 182));
        btnSimWallet.addActionListener(e -> JOptionPane.showMessageDialog(this, "Wallet balance verified and authorized (Simulated).", "Wallet Connected", JOptionPane.INFORMATION_MESSAGE));

        p.add(new JLabel("Select Digital Wallet:"));
        p.add(walletChoiceBox);
        p.add(new JLabel("Gateway Verification:"));
        p.add(btnSimWallet);
        return p;
    }

    private JPanel createFastagPanel() {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 4));
        p.setOpaque(false);

        fastagStatusLabel = new JLabel("FASTag status: Scan vehicle to detect RFID electronic tag.");
        fastagStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        fastagStatusLabel.setForeground(new Color(30, 41, 59));

        JLabel info = new JLabel("Automated debit at exit barrier. No stopping or physical contact required.");
        info.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        info.setForeground(new Color(71, 85, 105));

        p.add(fastagStatusLabel);
        p.add(info);
        return p;
    }

    private void calculatePreview() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a ticket number or license plate.", "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Optional<ParkingTicket> tktOpt = parkingService.searchVehicle(query);
        if (tktOpt.isEmpty()) {
            try {
                tktOpt = new com.smartparking.repository.TicketDao().findByTicketNumber(query);
            } catch (Exception ignored) {}
        }

        if (tktOpt.isEmpty() || tktOpt.get().getStatus() != com.smartparking.model.enums.TicketStatus.ACTIVE) {
            JOptionPane.showMessageDialog(this, "No active parking ticket found for: " + query, "Not Found", JOptionPane.ERROR_MESSAGE);
            resetPreview();
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

        // Populate Preview Labels
        lblVehicleClass.setText(String.format("Vehicle Class: %s (Bay: %s)",
                currentBreakdown.getVehicleType().getDisplayName(), currentTicket.getSlotId()));
        long hrs = currentBreakdown.getDurationMinutes() / 60;
        long mins = currentBreakdown.getDurationMinutes() % 60;
        lblDuration.setText(String.format("Stay Duration: %d hrs %d mins (%d billed hours)",
                hrs, mins, currentBreakdown.getBilledHours()));
        lblBaseFee.setText(String.format("Base Parking Fee: %s", MoneyFormat.format(currentBreakdown.getBaseFee())));

        if (currentBreakdown.isPeakApplied() && currentBreakdown.getPeakMultiplier() > 1.0) {
            lblPeakMultiplier.setText(String.format("Peak Surge Multiplier: %.2fx (Active)", currentBreakdown.getPeakMultiplier()));
            lblPeakMultiplier.setForeground(new Color(180, 83, 9));
        } else {
            lblPeakMultiplier.setText("Peak Surge Multiplier: 1.0x (Standard)");
            lblPeakMultiplier.setForeground(UITheme.TEXT_DARK);
        }

        if (currentBreakdown.isPassActive()) {
            lblPassDiscount.setText(String.format("Monthly Pass: ACTIVE until %s (-%s)",
                    DateFormat.displayDate(currentBreakdown.getPassExpiryDate()),
                    MoneyFormat.format(currentBreakdown.getPassDiscount())));
            lblPassDiscount.setForeground(new Color(22, 101, 52));
        } else {
            lblPassDiscount.setText("Monthly Pass: None (Hourly Fee Applies)");
            lblPassDiscount.setForeground(UITheme.TEXT_DARK);
        }

        if (currentBreakdown.getLostTicketPenalty() > 0.0) {
            lblPenalty.setText(String.format("Penalties: Lost Ticket (%s)", MoneyFormat.format(currentBreakdown.getLostTicketPenalty())));
            lblPenalty.setForeground(UITheme.COLOR_OCCUPIED);
        } else {
            lblPenalty.setText("Penalties: None");
            lblPenalty.setForeground(UITheme.TEXT_DARK);
        }

        lblTaxBreakdown.setText(String.format("GST (18%%): Taxable %s | CGST %s | SGST %s | Round-off %s",
                MoneyFormat.format(currentGst.taxableValue()),
                MoneyFormat.format(currentGst.cgstAmount()),
                MoneyFormat.format(currentGst.sgstAmount()),
                String.format("%+.2f", currentGst.roundOff())));

        lblTotalDue.setText("TOTAL AMOUNT DUE: " + MoneyFormat.format(currentGst.grandTotal()));
        lblTotalDue.setForeground(new Color(29, 78, 216));

        // FASTag Detection & Notification
        Optional<FastagAccount> fastagOpt = parkingService.getFastagService().detectTag(currentTicket.getLicensePlate());
        if (fastagOpt.isPresent()) {
            FastagAccount fa = fastagOpt.get();
            if (fa.getBalance() >= currentGst.grandTotal()) {
                fastagStatusLabel.setText(String.format("\u2705 FASTag %s Detected \u2014 Balance %s (Sufficient)",
                        fa.getTagId(), MoneyFormat.format(fa.getBalance())));
                fastagStatusLabel.setForeground(new Color(22, 101, 52));
                selectedPaymentMethod = PaymentMethodType.FASTAG;
            } else {
                fastagStatusLabel.setText(String.format("\u26A0 FASTag %s Low Balance (%s < %s) \u2014 Choose Cash/UPI/Card",
                        fa.getTagId(), MoneyFormat.format(fa.getBalance()), MoneyFormat.format(currentGst.grandTotal())));
                fastagStatusLabel.setForeground(UITheme.COLOR_OCCUPIED);
                selectedPaymentMethod = PaymentMethodType.UPI;
            }
        } else {
            fastagStatusLabel.setText("No active FASTag tag registered for plate " + PlateUtil.format(currentTicket.getLicensePlate()));
            fastagStatusLabel.setForeground(UITheme.TEXT_DARK);
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

        // Update preview HTML in receipt editor
        renderPreviewHtml();
    }

    private void updateDynamicUpiQr(double amount) {
        String vpa = "parking@upi";
        String name = "Grand Tech Park Parking Ltd";
        String note = currentTicket != null ? ("Parking Fee " + currentTicket.getTicketNumber()) : "Parking Fee";
        String upiIntent = UpiUtil.buildUpiIntentUrl(vpa, name, amount, note);
        upiStringLabel.setText("UPI Intent: " + upiIntent);

        java.awt.image.BufferedImage bi = BarcodeGenerator.generateQrCode(upiIntent, 80);
        if (bi != null) {
            upiQrLabel.setIcon(new ImageIcon(bi));
        }
    }

    private void renderPreviewHtml() {
        if (currentBreakdown == null || currentTicket == null || currentGst == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family: sans-serif; padding: 12px; color: #1e293b;'>");
        sb.append("<div style='text-align: center; border-bottom: 2px solid #2563eb; padding-bottom: 6px; margin-bottom: 8px;'>");
        sb.append("<h3 style='margin: 0; color: #1e3a8a;'>GRAND TECH PARK GARAGE</h3>");
        sb.append("<span style='font-size: 10px; color: #64748b;'>Fee Settlement Preview \u2022 Gate 2</span>");
        sb.append("</div>");

        sb.append("<table style='width: 100%; font-size: 11px; margin-bottom: 8px;'>");
        sb.append("<tr><td><b>Ticket #:</b> ").append(currentTicket.getTicketNumber()).append("</td>");
        sb.append("<td style='text-align: right;'><b>Plate:</b> ").append(PlateUtil.format(currentTicket.getLicensePlate())).append("</td></tr>");
        sb.append("<tr><td><b>Vehicle Class:</b> ").append(currentBreakdown.getVehicleType().getDisplayName()).append("</td>");
        sb.append("<td style='text-align: right;'><b>Bay:</b> ").append(currentTicket.getSlotId()).append("</td></tr>");
        sb.append("</table>");

        sb.append("<div style='background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 8px; font-size: 11px; margin-bottom: 8px;'>");
        sb.append("<div style='display: flex; justify-content: space-between;'><span>Taxable Value:</span><b>").append(MoneyFormat.format(currentGst.taxableValue())).append("</b></div>");
        sb.append("<div style='display: flex; justify-content: space-between;'><span>CGST (9.0%):</span><span>").append(MoneyFormat.format(currentGst.cgstAmount())).append("</span></div>");
        sb.append("<div style='display: flex; justify-content: space-between;'><span>SGST (9.0%):</span><span>").append(MoneyFormat.format(currentGst.sgstAmount())).append("</span></div>");
        sb.append("<div style='display: flex; justify-content: space-between;'><span>Round-off:</span><span>").append(String.format("%+.2f", currentGst.roundOff())).append("</span></div>");
        sb.append("<hr style='border: none; border-top: 1px dashed #cbd5e1; margin: 4px 0;'>");
        sb.append("<div style='display: flex; justify-content: space-between; font-size: 13px; color: #1e3a8a;'><b>GRAND TOTAL:</b><b>").append(MoneyFormat.format(currentGst.grandTotal())).append("</b></div>");
        sb.append("</div>");

        sb.append("<div style='font-size: 10px; color: #64748b; text-align: center;'>Select payment method on the left to settle fee and lift exit barrier.</div>");
        sb.append("</body></html>");
        receiptEditorPane.setText(sb.toString());
        receiptEditorPane.setCaretPosition(0);
    }

    private void promptFeeOverride() {
        if (currentBreakdown == null || currentTicket == null) return;

        JTextField feeField = new JTextField(String.valueOf(currentBreakdown.getTotalAmount()));
        JTextField reasonField = new JTextField();

        JPanel panel = new JPanel(new GridLayout(4, 1, 4, 4));
        panel.add(new JLabel("Current Calculated Total: " + MoneyFormat.format(currentBreakdown.getTotalAmount())));
        panel.add(new JLabel("Enter Overridden Fee (\u20B9):"));
        panel.add(feeField);
        panel.add(new JLabel("Mandatory Reason for Override (Audit Logged):"));
        panel.add(reasonField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Administrative Fee Override", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String rText = reasonField.getText().trim();
            if (rText.length() < 5) {
                JOptionPane.showMessageDialog(this, "Override aborted: A valid reason (min 5 characters) is mandatory.", "Validation Failed", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                double newFee = Double.parseDouble(feeField.getText().trim());
                if (newFee < 0) {
                    JOptionPane.showMessageDialog(this, "Fee cannot be negative.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                overriddenFee = newFee;
                overrideReason = rText;

                // Recompute GST on overridden taxable fee
                currentGst = parkingService.getGstCalculator().calculate(newFee);

                // Record in audit log
                parkingService.getPricingService().getTariffService().recordFeeOverride(
                        currentTicket.getTicketNumber(), currentBreakdown.getTotalAmount(), newFee, rText, "Duty Manager");

                lblTotalDue.setText(String.format("TOTAL DUE: %s [OVERRIDDEN: %s]",
                        MoneyFormat.format(currentGst.grandTotal()), rText));
                lblTotalDue.setForeground(new Color(180, 83, 9));

                updateDynamicUpiQr(currentGst.grandTotal());
                renderPreviewHtml();

                JOptionPane.showMessageDialog(this, "Fee overridden to " + MoneyFormat.format(currentGst.grandTotal()) + ".\nLogged in audit_log.", "Override Recorded", JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Please enter a valid numeric fee.", "Input Error", JOptionPane.ERROR_MESSAGE);
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

        lblVehicleClass.setText("Vehicle Class: \u2014");
        lblDuration.setText("Duration: \u2014");
        lblBaseFee.setText("Base Parking Fee: \u20B90.00");
        lblPeakMultiplier.setText("Peak Surge Multiplier: None");
        lblPassDiscount.setText("Monthly Pass: None");
        lblPenalty.setText("Penalties: None");
        lblTaxBreakdown.setText("GST (18%): Taxable \u20B90.00 | CGST \u20B90.00 | SGST \u20B90.00 | Round-off \u20B90.00");
        lblTotalDue.setText("TOTAL AMOUNT DUE: \u20B90.00");
        lblTotalDue.setForeground(new Color(15, 23, 42));

        barrierGateBanner.setText("\uD83D\uDD34 Barrier Gate Closed \u2014 Settle Payment to Proceed");
        barrierGateBanner.setBackground(new Color(254, 226, 226));
        barrierGateBanner.setForeground(new Color(153, 27, 27));

        setPaymentButtonsEnabled(false);
        btnOverrideFee.setEnabled(false);
        btnCompleteCheckout.setEnabled(false);
        btnDownloadPdf.setEnabled(false);
        btnPrintReceipt.setEnabled(false);
        btnAdminRefund.setEnabled(false);
        setInitialReceiptHtml();
    }

    private void setInitialReceiptHtml() {
        String html = "<html><body style='font-family: sans-serif; padding: 16px; color: #64748b; text-align: center;'>" +
                "<div style='margin-top: 40px;'>" +
                "<h3 style='color: #334155;'>Exit Gate: Ready for Vehicle Scan</h3>" +
                "<p style='font-size: 11px;'>1. Enter ticket number or registration plate on the left.<br>" +
                "2. Click 'Calculate Preview' or hit Enter from a barcode scanner.<br>" +
                "3. Choose settlement method to raise barrier gate & print GST Invoice.</p>" +
                "</div></body></html>";
        receiptEditorPane.setText(html);
    }

    private void setPaymentButtonsEnabled(boolean enabled) {
        btnCash.setEnabled(enabled);
        btnCard.setEnabled(enabled);
        btnUpi.setEnabled(enabled);
        btnWallet.setEnabled(enabled);
        btnFastag.setEnabled(enabled);
    }

    private JButton createPayButton(String label, PaymentMethodType method, String iconName) {
        JButton btn = new JButton(label, ImageLoader.loadIcon("icons/payments/" + iconName + ".png", 22, 22));
        btn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        btn.setVerticalTextPosition(SwingConstants.BOTTOM);
        btn.setHorizontalTextPosition(SwingConstants.CENTER);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            selectedPaymentMethod = method;
            updatePaymentSelectionUI();
        });
        return btn;
    }

    private void updatePaymentSelectionUI() {
        highlightPayButton(btnCash, selectedPaymentMethod == PaymentMethodType.CASH);
        highlightPayButton(btnUpi, selectedPaymentMethod == PaymentMethodType.UPI);
        highlightPayButton(btnCard, selectedPaymentMethod == PaymentMethodType.CARD);
        highlightPayButton(btnWallet, selectedPaymentMethod == PaymentMethodType.WALLET);
        highlightPayButton(btnFastag, selectedPaymentMethod == PaymentMethodType.FASTAG);

        paymentCardLayout.show(paymentDetailsContainer, selectedPaymentMethod.name());
        if (selectedPaymentMethod == PaymentMethodType.CASH) {
            updateCashChange();
        } else if (selectedPaymentMethod == PaymentMethodType.CARD) {
            btnCompleteCheckout.setEnabled(simulatedCardAuthorized && currentTicket != null);
        } else {
            btnCompleteCheckout.setEnabled(currentTicket != null);
        }
    }

    private void highlightPayButton(JButton btn, boolean selected) {
        if (selected) {
            btn.setBackground(new Color(224, 242, 254));
            btn.setBorder(BorderFactory.createLineBorder(UITheme.PRIMARY_BLUE, 2, true));
            btn.setForeground(UITheme.PRIMARY_BLUE);
        } else {
            btn.setBackground(Color.WHITE);
            btn.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_LIGHT, 1, true));
            btn.setForeground(UITheme.TEXT_DARK);
        }
    }

    private void processExit() {
        if (currentTicket == null) {
            JOptionPane.showMessageDialog(this, "Please calculate fee preview before completing checkout.", "Preview Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

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

            // 1. Visually Open Barrier Gate
            barrierGateBanner.setText("\uD83D\uDFE2 Barrier Gate Open \u2014 Proceed");
            barrierGateBanner.setBackground(new Color(220, 252, 231));
            barrierGateBanner.setForeground(new Color(22, 101, 52));
            barrierGateBanner.setBorder(BorderFactory.createLineBorder(new Color(134, 239, 172), 1, true));

            // 2. Render On-Screen Styled HTML GST Invoice
            String invoiceHtml = parkingService.getInvoiceService().generateHtmlReceipt(currentInvoice);
            receiptEditorPane.setText(invoiceHtml);
            receiptEditorPane.setCaretPosition(0);

            // 3. Update Vehicle Clearance Banner
            if (settledTicket.getVehicleType() != null) {
                exitVehicleBadge.setIcon(ImageLoader.getVehicleIcon(settledTicket.getVehicleType(), 42, 42));
                exitVehicleTitle.setText("Vehicle Cleared: " + PlateUtil.format(settledTicket.getLicensePlate()) +
                        " [" + PlateUtil.district(settledTicket.getLicensePlate()) + "] \u2022 " + settledTicket.getVehicleType().getDisplayName());
                exitVehicleTitle.setForeground(new Color(16, 185, 129));
                exitVehicleSubtitle.setText("Bay " + settledTicket.getSlotId() + " (Floor " + settledTicket.getFloorNumber() +
                        ") \u2022 Settled: " + MoneyFormat.format(settledTicket.getTotalAmount()) + " via " + selectedPaymentMethod.getDescription());
            }

            // Enable Invoice Export Actions
            btnDownloadPdf.setEnabled(true);
            btnPrintReceipt.setEnabled(true);
            btnAdminRefund.setEnabled(true);

            btnCompleteCheckout.setEnabled(false);
            btnOverrideFee.setEnabled(false);
            searchField.setText("");
            lostTicketCheck.setSelected(false);

            if (onStateChange != null) {
                onStateChange.run();
            }

            JOptionPane.showMessageDialog(this,
                    "Payment of " + MoneyFormat.format(settledTicket.getTotalAmount()) + " completed via " +
                            selectedPaymentMethod.getDescription() + ".\nTax Invoice Generated: " + currentInvoice.getInvoiceNumber() +
                            "\n\n\uD83D\uDFE2 Barrier gate is now open \u2014 Proceed safely!",
                    "Checkout Complete", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Exit Gate Checkout Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void downloadCurrentPdf() {
        if (currentInvoice == null) {
            JOptionPane.showMessageDialog(this, "No settled invoice available to export.", "Export Notice", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("Invoice_" + currentInvoice.getInvoiceNumber().replace("/", "_") + ".pdf"));
        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            try {
                File target = chooser.getSelectedFile();
                parkingService.getInvoiceService().exportPdf(currentInvoice, target);
                JOptionPane.showMessageDialog(this, "GST Tax Invoice successfully saved to:\n" + target.getAbsolutePath(), "PDF Exported", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to export PDF: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void printCurrentReceipt() {
        if (receiptEditorPane == null) return;
        try {
            boolean done = receiptEditorPane.print();
            if (done) {
                JOptionPane.showMessageDialog(this, "Print job sent successfully.", "Print Status", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error printing receipt: " + ex.getMessage(), "Print Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void promptRefund() {
        if (currentPayment == null) {
            JOptionPane.showMessageDialog(this, "No settled payment transaction available for refund.", "Refund Notice", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String reason = JOptionPane.showInputDialog(this,
                "Enter mandatory reason for refund of " + MoneyFormat.format(currentPayment.getAmount()) +
                        " (Logged to audit_log):",
                "Administrative Payment Refund",
                JOptionPane.WARNING_MESSAGE);

        if (reason == null) return; // User cancelled

        if (reason.trim().length() < 5) {
            JOptionPane.showMessageDialog(this, "Refund aborted: A valid reason (min 5 characters) is required.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            boolean success = parkingService.refundPayment(currentPayment.getPaymentId(), reason.trim(), "admin");
            if (success) {
                btnAdminRefund.setEnabled(false);
                JOptionPane.showMessageDialog(this,
                        "Payment #" + currentPayment.getPaymentId() + " successfully REFUNDED.\nLogged to audit_log.",
                        "Refund Confirmed", JOptionPane.INFORMATION_MESSAGE);
                if (onStateChange != null) onStateChange.run();
            } else {
                JOptionPane.showMessageDialog(this, "Could not refund: Transaction was already refunded or is not in COMPLETED status.", "Refund Failed", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Refund error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
