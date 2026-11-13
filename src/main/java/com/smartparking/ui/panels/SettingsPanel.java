package com.smartparking.ui.panels;

import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.tariff.TariffRate;
import com.smartparking.security.AccessControl;
import com.smartparking.security.Permission;
import com.smartparking.service.SettingsService;
import com.smartparking.ui.components.ModernButton;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.VectorIconUtil;
import com.smartparking.util.DateFormat;
import com.smartparking.util.I18n;
import com.smartparking.util.MoneyFormat;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * Administrative System Settings and Tariffs Management Screen (ADMIN Only).
 * Single source of truth for all operational thresholds, tariffs, tax, and security policies.
 * Changes apply strictly to future transactions and are cryptographically audited.
 */
public class SettingsPanel extends JPanel {

    private final SettingsService settingsService;
    private final Consumer<String> navigationHandler;

    // Header info
    private JLabel effectiveLabel;

    // Tariffs Tab
    private DefaultTableModel tariffsTableModel;
    private JTable tariffsTable;

    // Billing Tab Fields
    private JTextField txtGracePeriod;
    private JTextField txtLostPenalty;
    private JTextField txtPeakMultiplier;
    private JCheckBox chkPeakEnabled;
    private JTextField txtPeakSchedule;
    private JTextField txtPeakFestivals;

    // Tax & Business Tab Fields
    private JTextField txtGstRate;
    private JTextField txtGstin;
    private JTextField txtBusinessName;
    private JTextField txtBusinessAddress;
    private JTextField txtUpiVpa;

    // Passes Tab Fields
    private final JTextField[] passRateFields = new JTextField[7];
    private final VehicleType[] passTypes = {
            VehicleType.MOTORCYCLE, VehicleType.EV_TWO_WHEELER, VehicleType.AUTO_RICKSHAW,
            VehicleType.CAR, VehicleType.EV_CAR, VehicleType.LCV, VehicleType.HCV
    };

    // Security Tab Fields
    private JTextField txtMaxAttempts;
    private JTextField txtLockoutMinutes;
    private JTextField txtSessionTimeout;
    private JTextField txtRetentionTickets;
    private JTextField txtRetentionImages;
    private JTextField txtBackupRetention;

    // EV Charging Tab Fields
    private JTextField txtEvKwhRate;
    private JTextField txtEvIdleFee;
    private JTextField txtEvGraceMinutes;
    private JTextField txtEvGstRate;

    // Sample Preview Components
    private JComboBox<VehicleType> previewVehicleCombo;
    private JSpinner previewDurationSpinner;
    private JCheckBox previewOvernightChk;
    private JCheckBox previewLostTicketChk;
    private JLabel previewResultLabel;

    public SettingsPanel() {
        this(new SettingsService(), key -> {});
    }

    public SettingsPanel(SettingsService settingsService, Consumer<String> navigationHandler) {
        this.settingsService = settingsService;
        this.navigationHandler = navigationHandler;

        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.BG_CANVAS);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        initUI();
        refreshAllData();
    }

    private void initUI() {
        // --- 1. Top Header Bar ---
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);

        JLabel title = new JLabel(I18n.get("settings.title"));
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(UITheme.TEXT_DARK);

        JLabel sub = new JLabel(I18n.get("settings.subtitle"));
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setForeground(UITheme.TEXT_MUTED);

        left.add(title);
        left.add(Box.createVerticalStrut(4));
        left.add(sub);
        header.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        effectiveLabel = new JLabel("Effective from: " + DateFormat.formatIST(LocalDateTime.now()));
        effectiveLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        effectiveLabel.setForeground(new Color(22, 101, 52)); // emerald-800
        effectiveLabel.setBackground(new Color(220, 252, 231)); // emerald-100
        effectiveLabel.setOpaque(true);
        effectiveLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(134, 239, 172), 1, true),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        right.add(effectiveLabel);

        ModernButton btnTestNotification = new ModernButton(
                "Send Test Notification (Simulated)",
                VectorIconUtil.getIcon(VectorIconUtil.IconType.BELL, 14, Color.WHITE),
                UITheme.PRIMARY_BLUE, UITheme.PRIMARY_HOVER, Color.WHITE
        );
        btnTestNotification.addActionListener(e -> sendTestNotificationDialog());
        right.add(btnTestNotification);

        header.add(right, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // --- 2. Main Tabbed Pane ---
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        tabbedPane.addTab(I18n.get("settings.tab.tariffs"), buildTariffsTab());
        tabbedPane.addTab(I18n.get("settings.tab.billing"), buildBillingTab());
        tabbedPane.addTab(I18n.get("settings.tab.tax"), buildTaxBusinessTab());
        tabbedPane.addTab(I18n.get("settings.tab.passes"), buildPassesTab());
        tabbedPane.addTab(I18n.get("settings.tab.security"), buildSecurityTab());
        tabbedPane.addTab(I18n.get("settings.tab.ev"), buildEvChargingTab());

        add(tabbedPane, BorderLayout.CENTER);
    }

    // =========================================================================
    // TAB 1: TARIFFS (Editable Table + Sample Preview)
    // =========================================================================

    private JPanel buildTariffsTab() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Table
        String[] columns = {"Vehicle Class", "First Hour (₹)", "Extra Hour (₹)", "Daily Cap (₹)", "Overnight Rate (₹)"};
        tariffsTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return col > 0; // Class name is fixed, rates are editable
            }
        };

        tariffsTable = new JTable(tariffsTableModel);
        tariffsTable.setFont(UITheme.FONT_REGULAR);
        tariffsTable.setRowHeight(32);
        tariffsTable.getTableHeader().setFont(UITheme.FONT_BOLD);
        tariffsTable.getTableHeader().setBackground(new Color(241, 245, 249));

        panel.add(new JScrollPane(tariffsTable), BorderLayout.CENTER);

        // Bottom: Action Buttons & Live Preview Box
        JPanel bottom = new JPanel(new BorderLayout(14, 10));
        bottom.setOpaque(false);

        // Preview Box
        JPanel previewCard = buildPreviewCard();
        bottom.add(previewCard, BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btnBar.setOpaque(false);

        ModernButton btnReset = ModernButton.createSecondary("Reset Tariffs to Defaults", null);
        btnReset.addActionListener(e -> {
            if (confirmReset("Tariffs")) {
                settingsService.resetTariffsToDefaults();
                refreshAllData();
                showSuccess("Tariffs reset to system defaults.");
            }
        });

        ModernButton btnSave = new ModernButton("Save Tariffs", VectorIconUtil.getIcon(VectorIconUtil.IconType.CHECK, 14, Color.WHITE),
                new Color(22, 163, 74), new Color(21, 128, 61), Color.WHITE);
        btnSave.addActionListener(e -> saveTariffs());

        btnBar.add(btnReset);
        btnBar.add(btnSave);
        bottom.add(btnBar, BorderLayout.SOUTH);

        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPreviewCard() {
        JPanel card = new JPanel(new BorderLayout(10, 8));
        card.setBackground(new Color(248, 250, 252));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1, true),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        top.setOpaque(false);

        JLabel lblTitle = new JLabel("Live Tariff Fee Calculator (Preview Before Saving):");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTitle.setForeground(UITheme.TEXT_DARK);
        top.add(lblTitle);

        top.add(new JLabel("Vehicle:"));
        previewVehicleCombo = new JComboBox<>(VehicleType.values());
        top.add(previewVehicleCombo);

        top.add(new JLabel("Duration (mins):"));
        previewDurationSpinner = new JSpinner(new SpinnerNumberModel(120, 15, 2880, 15));
        top.add(previewDurationSpinner);

        previewOvernightChk = new JCheckBox("Crosses Midnight");
        previewOvernightChk.setOpaque(false);
        top.add(previewOvernightChk);

        previewLostTicketChk = new JCheckBox("Lost Ticket");
        previewLostTicketChk.setOpaque(false);
        top.add(previewLostTicketChk);

        JButton btnCalc = new JButton("Recalculate Preview");
        btnCalc.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnCalc.addActionListener(e -> updatePreviewCalculation());
        top.add(btnCalc);

        card.add(top, BorderLayout.NORTH);

        previewResultLabel = new JLabel("Sample Estimated Fee: Ready to compute");
        previewResultLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        previewResultLabel.setForeground(new Color(29, 78, 216));
        previewResultLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
        card.add(previewResultLabel, BorderLayout.CENTER);

        return card;
    }

    private void updatePreviewCalculation() {
        try {
            VehicleType vt = (VehicleType) previewVehicleCombo.getSelectedItem();
            int mins = (Integer) previewDurationSpinner.getValue();
            boolean overnight = previewOvernightChk.isSelected();
            boolean lost = previewLostTicketChk.isSelected();

            // Find row for this vehicle type in table
            int targetRow = 0;
            for (int r = 0; r < tariffsTableModel.getRowCount(); r++) {
                if (vt.name().equalsIgnoreCase(String.valueOf(tariffsTableModel.getValueAt(r, 0)))) {
                    targetRow = r;
                    break;
                }
            }

            double firstHr = Double.parseDouble(String.valueOf(tariffsTableModel.getValueAt(targetRow, 1)));
            double extraHr = Double.parseDouble(String.valueOf(tariffsTableModel.getValueAt(targetRow, 2)));
            double cap = Double.parseDouble(String.valueOf(tariffsTableModel.getValueAt(targetRow, 3)));
            double overRate = Double.parseDouble(String.valueOf(tariffsTableModel.getValueAt(targetRow, 4)));

            double multiplier = settingsService.getDoubleSetting("peak_multiplier", 1.0);
            boolean peakOn = settingsService.getBooleanSetting("peak_enabled", false);
            int grace = settingsService.getIntSetting("grace_period_minutes", 15);
            double penalty = settingsService.getDoubleSetting("lost_ticket_penalty", 250.0);

            var res = settingsService.previewFee(vt, mins, overnight, lost, firstHr, extraHr, cap, overRate, multiplier, peakOn, grace, penalty);

            previewResultLabel.setText(String.format(
                    "Calculated Preview for %s (%d mins): Base Fee = %s | Peak Surge = %s | Overnight = %s | Penalty = %s | Estimated Total = %s (Effective from %s)",
                    vt.name(), mins, MoneyFormat.formatRupees(res.baseFee()), MoneyFormat.formatRupees(res.peakSurge()),
                    MoneyFormat.formatRupees(res.overnightFee()), MoneyFormat.formatRupees(res.penaltyFee()),
                    MoneyFormat.formatRupees(res.totalFee()), DateFormat.formatISTTime(LocalDateTime.now())
            ));
        } catch (Exception ex) {
            previewResultLabel.setText("Error in table rates: " + ex.getMessage());
        }
    }

    private void saveTariffs() {
        if (!confirmSave("Tariffs")) return;
        try {
            for (int r = 0; r < tariffsTableModel.getRowCount(); r++) {
                String vTypeName = String.valueOf(tariffsTableModel.getValueAt(r, 0));
                VehicleType vt = VehicleType.fromString(vTypeName);
                double firstHr = Double.parseDouble(String.valueOf(tariffsTableModel.getValueAt(r, 1)));
                double extraHr = Double.parseDouble(String.valueOf(tariffsTableModel.getValueAt(r, 2)));
                double cap = Double.parseDouble(String.valueOf(tariffsTableModel.getValueAt(r, 3)));
                double overRate = Double.parseDouble(String.valueOf(tariffsTableModel.getValueAt(r, 4)));

                // preserve monthly pass rate from existing record
                double passRate = settingsService.getAllTariffs().stream()
                        .filter(t -> t.getVehicleType() == vt)
                        .findFirst().map(TariffRate::getMonthlyPassRate).orElse(1500.0);

                settingsService.updateTariff(new TariffRate(vt, firstHr, extraHr, cap, overRate, passRate));
            }
            refreshAllData();
            showSuccess("All vehicle tariffs saved successfully. New rates are now effective.");
        } catch (Exception ex) {
            showError("Failed to save tariffs: " + ex.getMessage());
        }
    }

    // =========================================================================
    // TAB 2: BILLING & PEAK MULTIPLIER
    // =========================================================================

    private JPanel buildBillingTab() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel form = new JPanel(new GridLayout(6, 2, 14, 14));
        form.setOpaque(false);

        form.add(createLabel("Free Grace Period Duration (Minutes):"));
        txtGracePeriod = new JTextField("15");
        UITheme.styleTextField(txtGracePeriod);
        form.add(txtGracePeriod);

        form.add(createLabel("Lost Ticket Fixed Penalty (₹):"));
        txtLostPenalty = new JTextField("250.0");
        UITheme.styleTextField(txtLostPenalty);
        form.add(txtLostPenalty);

        form.add(createLabel("Dynamic Peak Multiplier (1.00 to 3.00):"));
        txtPeakMultiplier = new JTextField("1.00");
        UITheme.styleTextField(txtPeakMultiplier);
        form.add(txtPeakMultiplier);

        form.add(createLabel("Peak Multiplier Active Toggle:"));
        chkPeakEnabled = new JCheckBox("Enable Peak Multiplier Surge");
        chkPeakEnabled.setFont(UITheme.FONT_BOLD);
        chkPeakEnabled.setOpaque(false);
        form.add(chkPeakEnabled);

        form.add(createLabel("Peak Schedule (e.g. WEEKENDS, FESTIVALS):"));
        txtPeakSchedule = new JTextField("WEEKENDS,FESTIVALS");
        UITheme.styleTextField(txtPeakSchedule);
        form.add(txtPeakSchedule);

        form.add(createLabel("Festival Date Ranges (YYYY-MM-DD:YYYY-MM-DD:Name,...):"));
        txtPeakFestivals = new JTextField("2026-11-01:2026-11-05:Diwali,2026-03-24:2026-03-26:Holi");
        UITheme.styleTextField(txtPeakFestivals);
        form.add(txtPeakFestivals);

        panel.add(form, BorderLayout.NORTH);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btnBar.setOpaque(false);

        ModernButton btnReset = ModernButton.createSecondary("Reset Billing to Defaults", null);
        btnReset.addActionListener(e -> {
            if (confirmReset("Billing Settings")) {
                settingsService.resetBillingToDefaults();
                refreshAllData();
                showSuccess("Billing settings reset to defaults.");
            }
        });

        ModernButton btnSave = new ModernButton("Save Billing Settings", VectorIconUtil.getIcon(VectorIconUtil.IconType.CHECK, 14, Color.WHITE),
                new Color(22, 163, 74), new Color(21, 128, 61), Color.WHITE);
        btnSave.addActionListener(e -> saveBilling());

        btnBar.add(btnReset);
        btnBar.add(btnSave);
        panel.add(btnBar, BorderLayout.SOUTH);

        return panel;
    }

    private void saveBilling() {
        if (!confirmSave("Billing Settings")) return;
        try {
            int grace = Integer.parseInt(txtGracePeriod.getText().trim());
            settingsService.validatePositiveInt(grace, "Grace period");

            double penalty = Double.parseDouble(txtLostPenalty.getText().trim());
            settingsService.validatePositive(penalty, "Lost ticket penalty");

            double multiplier = Double.parseDouble(txtPeakMultiplier.getText().trim());
            settingsService.validateMultiplier(multiplier);

            boolean peakOn = chkPeakEnabled.isSelected();
            String schedule = txtPeakSchedule.getText().trim();
            String festivals = txtPeakFestivals.getText().trim();

            settingsService.updateSetting("grace_period_minutes", String.valueOf(grace), "Grace period in minutes");
            settingsService.updateSetting("lost_ticket_penalty", String.valueOf(penalty), "Lost ticket fixed fine");
            settingsService.updateSetting("peak_multiplier", String.valueOf(multiplier), "Surge peak multiplier");
            settingsService.updateSetting("peak_enabled", String.valueOf(peakOn), "Peak surge enabled flag");
            settingsService.updateSetting("peak_schedule", schedule, "Configured peak multiplier schedule");
            settingsService.updateSetting("peak_festivals", festivals, "Configured peak festival dates");

            refreshAllData();
            showSuccess("Billing and peak settings updated successfully.");
        } catch (Exception ex) {
            showError("Failed to save billing settings: " + ex.getMessage());
        }
    }

    // =========================================================================
    // TAB 3: TAX & REGISTERED BUSINESS
    // =========================================================================

    private JPanel buildTaxBusinessTab() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel form = new JPanel(new GridLayout(6, 2, 14, 14));
        form.setOpaque(false);

        // Note: verify applicable GST rate per circular
        form.add(createLabel("Statutory GST Rate % (Verify Circular, default 18%):"));
        txtGstRate = new JTextField("18.0");
        UITheme.styleTextField(txtGstRate);
        form.add(txtGstRate);

        form.add(createLabel("Registered GSTIN (15-character UP format):"));
        txtGstin = new JTextField("09AAAAA0000A1Z5");
        UITheme.styleTextField(txtGstin);
        form.add(txtGstin);

        form.add(createLabel("Registered Business Name:"));
        txtBusinessName = new JTextField("Grand Tech Park Parking Ltd");
        UITheme.styleTextField(txtBusinessName);
        form.add(txtBusinessName);

        form.add(createLabel("Registered Business Address:"));
        txtBusinessAddress = new JTextField("Plot 42, Knowledge Park III, Greater Noida, UP 201306");
        UITheme.styleTextField(txtBusinessAddress);
        form.add(txtBusinessAddress);

        form.add(createLabel("UPI Virtual Payment Address (VPA):"));
        txtUpiVpa = new JTextField("parking@upi");
        UITheme.styleTextField(txtUpiVpa);
        form.add(txtUpiVpa);

        JLabel statutoryNote = new JLabel("<html><span style='color:#dc2626;font-size:11px;'>* Note: Verify applicable GST rate per current statutory circular. Historical invoices are immutable and never updated.</span></html>");
        form.add(statutoryNote);
        form.add(new JLabel(""));

        panel.add(form, BorderLayout.NORTH);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btnBar.setOpaque(false);

        ModernButton btnReset = ModernButton.createSecondary("Reset Tax & Business to Defaults", null);
        btnReset.addActionListener(e -> {
            if (confirmReset("Tax & Business Details")) {
                settingsService.resetTaxAndBusinessToDefaults();
                refreshAllData();
                showSuccess("Tax and business details reset to defaults.");
            }
        });

        ModernButton btnSave = new ModernButton("Save Tax & Business", VectorIconUtil.getIcon(VectorIconUtil.IconType.CHECK, 14, Color.WHITE),
                new Color(22, 163, 74), new Color(21, 128, 61), Color.WHITE);
        btnSave.addActionListener(e -> saveTaxBusiness());

        btnBar.add(btnReset);
        btnBar.add(btnSave);
        panel.add(btnBar, BorderLayout.SOUTH);

        return panel;
    }

    private void saveTaxBusiness() {
        if (!confirmSave("Tax and Business Settings")) return;
        try {
            double gst = Double.parseDouble(txtGstRate.getText().trim());
            settingsService.validateGstRate(gst);

            String gstin = txtGstin.getText().trim();
            settingsService.validateGstin(gstin);

            String name = txtBusinessName.getText().trim();
            if (name.isEmpty()) throw new IllegalArgumentException("Business name cannot be empty.");

            String addr = txtBusinessAddress.getText().trim();
            if (addr.isEmpty()) throw new IllegalArgumentException("Business address cannot be empty.");

            String upi = txtUpiVpa.getText().trim();
            settingsService.validateUpiVpa(upi);

            settingsService.updateSetting("gst_rate_percent", String.valueOf(gst), "GST tax percentage");
            settingsService.updateSetting("gst_percentage", String.valueOf(gst), "GST tax percentage");
            settingsService.updateSetting("gstin", gstin, "Registered business GSTIN");
            settingsService.updateSetting("business_name", name, "Registered business name");
            settingsService.updateSetting("business_address", addr, "Registered business address");
            settingsService.updateSetting("upi_vpa", upi, "UPI Virtual Payment Address");

            refreshAllData();
            showSuccess("Tax and business details saved. Applies to all future issued invoices.");
        } catch (Exception ex) {
            showError("Failed to save tax and business details: " + ex.getMessage());
        }
    }

    // =========================================================================
    // TAB 4: PASSES (Monthly Flat Fee Per Vehicle Class)
    // =========================================================================

    private JPanel buildPassesTab() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel form = new JPanel(new GridLayout(7, 2, 14, 10));
        form.setOpaque(false);

        for (int i = 0; i < passTypes.length; i++) {
            form.add(createLabel(passTypes[i].name() + " Monthly Flat Pass Fee (₹):"));
            passRateFields[i] = new JTextField("1500.0");
            UITheme.styleTextField(passRateFields[i]);
            form.add(passRateFields[i]);
        }

        panel.add(form, BorderLayout.NORTH);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btnBar.setOpaque(false);

        ModernButton btnReset = ModernButton.createSecondary("Reset Passes to Defaults", null);
        btnReset.addActionListener(e -> {
            if (confirmReset("Monthly Passes Tariffs")) {
                settingsService.resetPassesToDefaults();
                refreshAllData();
                showSuccess("Monthly pass rates reset to defaults.");
            }
        });

        ModernButton btnSave = new ModernButton("Save Pass Tariffs", VectorIconUtil.getIcon(VectorIconUtil.IconType.CHECK, 14, Color.WHITE),
                new Color(22, 163, 74), new Color(21, 128, 61), Color.WHITE);
        btnSave.addActionListener(e -> savePasses());

        btnBar.add(btnReset);
        btnBar.add(btnSave);
        panel.add(btnBar, BorderLayout.SOUTH);

        return panel;
    }

    private void savePasses() {
        if (!confirmSave("Monthly Pass Tariffs")) return;
        try {
            for (int i = 0; i < passTypes.length; i++) {
                double rate = Double.parseDouble(passRateFields[i].getText().trim());
                settingsService.validatePositive(rate, passTypes[i].name() + " pass fee");

                VehicleType vt = passTypes[i];
                TariffRate existing = settingsService.getAllTariffs().stream()
                        .filter(t -> t.getVehicleType() == vt).findFirst().orElse(null);

                double fHr = existing != null ? existing.getFirstHourRate() : 40.0;
                double eHr = existing != null ? existing.getExtraHourRate() : 20.0;
                double cap = existing != null ? existing.getDailyCap() : 200.0;
                double on = existing != null ? existing.getOvernightRate() : 100.0;

                settingsService.updateTariff(new TariffRate(vt, fHr, eHr, cap, on, rate));
            }
            refreshAllData();
            showSuccess("Monthly pass tariffs updated successfully.");
        } catch (Exception ex) {
            showError("Failed to save pass tariffs: " + ex.getMessage());
        }
    }

    // =========================================================================
    // TAB 5: SECURITY & PRIVACY
    // =========================================================================

    private JPanel buildSecurityTab() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel form = new JPanel(new GridLayout(6, 2, 14, 14));
        form.setOpaque(false);

        form.add(createLabel("Maximum Consecutive Failed Login Attempts:"));
        txtMaxAttempts = new JTextField("5");
        UITheme.styleTextField(txtMaxAttempts);
        form.add(txtMaxAttempts);

        form.add(createLabel("Account Lockout Duration (Minutes):"));
        txtLockoutMinutes = new JTextField("15");
        UITheme.styleTextField(txtLockoutMinutes);
        form.add(txtLockoutMinutes);

        form.add(createLabel("User Session Inactivity Timeout (Minutes):"));
        txtSessionTimeout = new JTextField("15");
        UITheme.styleTextField(txtSessionTimeout);
        form.add(txtSessionTimeout);

        form.add(createLabel("DPDP Ticket Data Anonymization Retention (Days):"));
        txtRetentionTickets = new JTextField("365");
        UITheme.styleTextField(txtRetentionTickets);
        form.add(txtRetentionTickets);

        form.add(createLabel("ANPR Image Snapshots Purge Retention (Days):"));
        txtRetentionImages = new JTextField("30");
        UITheme.styleTextField(txtRetentionImages);
        form.add(txtRetentionImages);

        form.add(createLabel("Live Rolling Database Backups Retained (Count):"));
        txtBackupRetention = new JTextField("7");
        UITheme.styleTextField(txtBackupRetention);
        form.add(txtBackupRetention);

        panel.add(form, BorderLayout.NORTH);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btnBar.setOpaque(false);

        ModernButton btnReset = ModernButton.createSecondary("Reset Security to Defaults", null);
        btnReset.addActionListener(e -> {
            if (confirmReset("Security & DPDP Retention")) {
                settingsService.resetSecurityToDefaults();
                refreshAllData();
                showSuccess("Security and retention settings reset to defaults.");
            }
        });

        ModernButton btnSave = new ModernButton("Save Security Policies", VectorIconUtil.getIcon(VectorIconUtil.IconType.CHECK, 14, Color.WHITE),
                new Color(22, 163, 74), new Color(21, 128, 61), Color.WHITE);
        btnSave.addActionListener(e -> saveSecurity());

        btnBar.add(btnReset);
        btnBar.add(btnSave);
        panel.add(btnBar, BorderLayout.SOUTH);

        return panel;
    }

    private void saveSecurity() {
        if (!confirmSave("Security and Retention Policies")) return;
        try {
            int attempts = Integer.parseInt(txtMaxAttempts.getText().trim());
            settingsService.validatePositiveInt(attempts, "Max attempts");

            int lockout = Integer.parseInt(txtLockoutMinutes.getText().trim());
            settingsService.validatePositiveInt(lockout, "Lockout duration");

            int timeout = Integer.parseInt(txtSessionTimeout.getText().trim());
            settingsService.validatePositiveInt(timeout, "Session timeout");

            int ticketDays = Integer.parseInt(txtRetentionTickets.getText().trim());
            settingsService.validatePositiveInt(ticketDays, "Ticket retention days");

            int imgDays = Integer.parseInt(txtRetentionImages.getText().trim());
            settingsService.validatePositiveInt(imgDays, "Image retention days");

            int backupCount = Integer.parseInt(txtBackupRetention.getText().trim());
            settingsService.validatePositiveInt(backupCount, "Backup retention count");

            settingsService.updateSetting("auth.max_failed_attempts", String.valueOf(attempts), "Max failed login attempts before lockout");
            settingsService.updateSetting("auth.lockout_duration_minutes", String.valueOf(lockout), "Account lockout duration in minutes");
            settingsService.updateSetting("auth.session_timeout_minutes", String.valueOf(timeout), "Session inactivity timeout in minutes");
            settingsService.updateSetting("retention.ticket_days", String.valueOf(ticketDays), "DPDP ticket retention in days");
            settingsService.updateSetting("retention.image_days", String.valueOf(imgDays), "ANPR image retention in days");
            settingsService.updateSetting("backup.retention_count", String.valueOf(backupCount), "Rolling backup retention count");

            refreshAllData();
            showSuccess("Security and privacy retention policies saved.");
        } catch (Exception ex) {
            showError("Failed to save security policies: " + ex.getMessage());
        }
    }

    // =========================================================================
    // TAB 6: EV CHARGING CONFIGURATION
    // =========================================================================

    private JPanel buildEvChargingTab() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel form = new JPanel(new GridLayout(5, 2, 14, 14));
        form.setOpaque(false);

        form.add(createLabel("EV Electricity Tariff Rate (₹ per kWh):"));
        txtEvKwhRate = new JTextField("15.0");
        UITheme.styleTextField(txtEvKwhRate);
        form.add(txtEvKwhRate);

        form.add(createLabel("Post-Charging Idle Penalty (₹ per minute):"));
        txtEvIdleFee = new JTextField("2.0");
        UITheme.styleTextField(txtEvIdleFee);
        form.add(txtEvIdleFee);

        form.add(createLabel("Post-Charging Idle Grace Duration (Minutes):"));
        txtEvGraceMinutes = new JTextField("15");
        UITheme.styleTextField(txtEvGraceMinutes);
        form.add(txtEvGraceMinutes);

        // Verify applicable GST rate on EV charging per circular
        form.add(createLabel("EV Charging Statutory GST Rate % (default 18%):"));
        txtEvGstRate = new JTextField("18.0");
        UITheme.styleTextField(txtEvGstRate);
        form.add(txtEvGstRate);

        JLabel statutoryNote = new JLabel("<html><span style='color:#dc2626;font-size:11px;'>* Note: EV charging line items are itemized separately on GST Tax Invoices.</span></html>");
        form.add(statutoryNote);
        form.add(new JLabel(""));

        panel.add(form, BorderLayout.NORTH);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btnBar.setOpaque(false);

        ModernButton btnReset = ModernButton.createSecondary("Reset EV Charging to Defaults", null);
        btnReset.addActionListener(e -> {
            if (confirmReset("EV Charging Parameters")) {
                settingsService.resetEvChargingToDefaults();
                refreshAllData();
                showSuccess("EV charging settings reset to defaults.");
            }
        });

        ModernButton btnSave = new ModernButton("Save EV Charging Settings", VectorIconUtil.getIcon(VectorIconUtil.IconType.CHECK, 14, Color.WHITE),
                new Color(22, 163, 74), new Color(21, 128, 61), Color.WHITE);
        btnSave.addActionListener(e -> saveEvCharging());

        btnBar.add(btnReset);
        btnBar.add(btnSave);
        panel.add(btnBar, BorderLayout.SOUTH);

        return panel;
    }

    private void saveEvCharging() {
        if (!confirmSave("EV Charging Parameters")) return;
        try {
            double kwhRate = Double.parseDouble(txtEvKwhRate.getText().trim());
            settingsService.validatePositive(kwhRate, "Per kWh rate");

            double idleFee = Double.parseDouble(txtEvIdleFee.getText().trim());
            settingsService.validatePositive(idleFee, "Idle fee per minute");

            int grace = Integer.parseInt(txtEvGraceMinutes.getText().trim());
            settingsService.validatePositiveInt(grace, "Idle grace minutes");

            double evGst = Double.parseDouble(txtEvGstRate.getText().trim());
            settingsService.validateGstRate(evGst);

            settingsService.updateSetting("ev_charging_rate_per_kwh", String.valueOf(kwhRate), "EV Charging Rate per kWh");
            settingsService.updateSetting("ev_charging_idle_fee_per_min", String.valueOf(idleFee), "Idle fee per min after charging");
            settingsService.updateSetting("ev_charging_grace_minutes", String.valueOf(grace), "Idle grace minutes before fee applies");
            settingsService.updateSetting("ev_charging_gst_rate_percent", String.valueOf(evGst), "GST rate for EV charging services (verify circular)");

            refreshAllData();
            showSuccess("EV charging parameters saved successfully.");
        } catch (Exception ex) {
            showError("Failed to save EV charging settings: " + ex.getMessage());
        }
    }

    // =========================================================================
    // HELPER & DIALOG METHODS
    // =========================================================================

    public void refreshAllData() {
        effectiveLabel.setText("Effective from: " + DateFormat.formatIST(LocalDateTime.now()));

        // Tariffs
        tariffsTableModel.setRowCount(0);
        List<TariffRate> tariffs = settingsService.getAllTariffs();
        for (TariffRate t : tariffs) {
            tariffsTableModel.addRow(new Object[]{
                    t.getVehicleType().name(),
                    t.getFirstHourRate(),
                    t.getExtraHourRate(),
                    t.getDailyCap(),
                    t.getOvernightRate()
            });
        }

        // Billing
        txtGracePeriod.setText(settingsService.getSetting("grace_period_minutes", "15"));
        txtLostPenalty.setText(settingsService.getSetting("lost_ticket_penalty", "250.0"));
        txtPeakMultiplier.setText(settingsService.getSetting("peak_multiplier", "1.00"));
        chkPeakEnabled.setSelected(settingsService.getBooleanSetting("peak_enabled", false));
        txtPeakSchedule.setText(settingsService.getSetting("peak_schedule", "WEEKENDS,FESTIVALS"));
        txtPeakFestivals.setText(settingsService.getSetting("peak_festivals", "2026-11-01:2026-11-05:Diwali,2026-03-24:2026-03-26:Holi"));

        // Tax
        txtGstRate.setText(settingsService.getSetting("gst_rate_percent", "18.0"));
        txtGstin.setText(settingsService.getSetting("gstin", "09AAAAA0000A1Z5"));
        txtBusinessName.setText(settingsService.getSetting("business_name", "Grand Tech Park Parking Ltd"));
        txtBusinessAddress.setText(settingsService.getSetting("business_address", "Plot 42, Knowledge Park III, Greater Noida, UP 201306"));
        txtUpiVpa.setText(settingsService.getSetting("upi_vpa", "parking@upi"));

        // Passes
        for (int i = 0; i < passTypes.length; i++) {
            VehicleType vt = passTypes[i];
            double rate = tariffs.stream().filter(t -> t.getVehicleType() == vt)
                    .findFirst().map(TariffRate::getMonthlyPassRate).orElse(1500.0);
            passRateFields[i].setText(String.format("%.1f", rate));
        }

        // Security
        txtMaxAttempts.setText(settingsService.getSetting("auth.max_failed_attempts", "5"));
        txtLockoutMinutes.setText(settingsService.getSetting("auth.lockout_duration_minutes", "15"));
        txtSessionTimeout.setText(settingsService.getSetting("auth.session_timeout_minutes", "15"));
        txtRetentionTickets.setText(settingsService.getSetting("retention.ticket_days", "365"));
        txtRetentionImages.setText(settingsService.getSetting("retention.image_days", "30"));
        txtBackupRetention.setText(settingsService.getSetting("backup.retention_count", "7"));

        // EV
        txtEvKwhRate.setText(settingsService.getSetting("ev_charging_rate_per_kwh", "15.0"));
        txtEvIdleFee.setText(settingsService.getSetting("ev_charging_idle_fee_per_min", "2.0"));
        txtEvGraceMinutes.setText(settingsService.getSetting("ev_charging_grace_minutes", "15"));
        txtEvGstRate.setText(settingsService.getSetting("ev_charging_gst_rate_percent", "18.0"));

        updatePreviewCalculation();
    }

    private void sendTestNotificationDialog() {
        JTextField phoneField = new JTextField("9876543210");
        JComboBox<String> channelCombo = new JComboBox<>(new String[]{"SMS", "WHATSAPP", "EMAIL"});
        JComboBox<String> templateCombo = new JComboBox<>(new String[]{
                "BOOKING_CONFIRMED", "BOOKING_EXPIRING", "BOOKING_EXPIRED", "PARKING_RECEIPT",
                "OVERSTAY_WARNING", "PASS_EXPIRING", "PASS_RENEWED", "FASTAG_LOW_BALANCE",
                "EV_CHARGING_COMPLETE", "PASSWORD_CHANGED", "DATA_DELETION_STATUS"
        });

        JPanel p = new JPanel(new GridLayout(3, 2, 8, 8));
        p.add(new JLabel("Customer Mobile (UP/India):"));
        p.add(phoneField);
        p.add(new JLabel("Delivery Channel:"));
        p.add(channelCombo);
        p.add(new JLabel("Message Template:"));
        p.add(templateCombo);

        int choice = JOptionPane.showConfirmDialog(this, p, "Send Test Transactional Notification (Simulated)", JOptionPane.OK_CANCEL_OPTION);
        if (choice == JOptionPane.OK_OPTION) {
            String mobile = phoneField.getText().trim();
            String channel = (String) channelCombo.getSelectedItem();
            String template = (String) templateCombo.getSelectedItem();
            try {
                // Will route through NotificationService in Priority 4
                JOptionPane.showMessageDialog(this,
                        String.format("Test notification queued successfully!\nChannel: %s (Simulated)\nRecipient: %s\nTemplate: %s",
                                channel, mobile, template),
                        "Notification Dispatched (Simulated)", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        }
    }

    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UITheme.FONT_BOLD);
        l.setForeground(UITheme.TEXT_DARK);
        return l;
    }

    private boolean confirmSave(String sectionName) {
        int opt = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to save changes to " + sectionName + "?\n" +
                        "New rates will be effective immediately from " + DateFormat.formatISTTime(LocalDateTime.now()) + ".\n" +
                        "Existing receipts and tax invoices will remain unchanged.",
                "Confirm Settings Update", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return opt == JOptionPane.YES_OPTION;
    }

    private boolean confirmReset(String sectionName) {
        int opt = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to restore factory default values for " + sectionName + "?",
                "Confirm Factory Reset", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return opt == JOptionPane.YES_OPTION;
    }

    private void showSuccess(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Settings Applied", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Validation Error", JOptionPane.ERROR_MESSAGE);
    }
}
