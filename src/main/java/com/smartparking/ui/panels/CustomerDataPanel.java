package com.smartparking.ui.panels;

import com.smartparking.model.invoice.Invoice;
import com.smartparking.model.membership.MembershipPass;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.user.User;
import com.smartparking.repository.ConsentDao;
import com.smartparking.repository.InvoiceDao;
import com.smartparking.repository.MembershipDao;
import com.smartparking.repository.TicketDao;
import com.smartparking.repository.UserVehicleDao;
import com.smartparking.security.AccessControl;
import com.smartparking.security.Permission;
import com.smartparking.security.UserSession;
import com.smartparking.service.DataRetentionService;
import com.smartparking.ui.components.ModernButton;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.util.DateFormat;
import com.smartparking.util.PlateUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Customer Self-Service "My Data" portal under DPDP Act, 2023.
 * Grants customers transparency to review personal records, download portable data (JSON/CSV),
 * manage linked UP vehicles, and request personal data erasure.
 */
public class CustomerDataPanel extends JPanel {

    private final UserVehicleDao vehicleDao;
    private final TicketDao ticketDao;
    private final MembershipDao membershipDao;
    private final InvoiceDao invoiceDao;
    private final ConsentDao consentDao;
    private final DataRetentionService retentionService;

    private final JLabel profileLabel;
    private final DefaultTableModel vehicleModel;
    private final DefaultTableModel ticketModel;
    private final DefaultTableModel passModel;

    public CustomerDataPanel() {
        this.vehicleDao = new UserVehicleDao();
        this.ticketDao = new TicketDao();
        this.membershipDao = new MembershipDao();
        this.invoiceDao = new InvoiceDao();
        this.consentDao = new ConsentDao();
        this.retentionService = new DataRetentionService();

        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_CANVAS);
        setBorder(new EmptyBorder(24, 24, 24, 24));

        // Top Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 0, 4));
        titleBox.setOpaque(false);
        JLabel title = new JLabel("My Personal Data & Privacy Portal");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(15, 23, 42));

        profileLabel = new JLabel("DPDP Act, 2023 — Data Principal Access & Portability");
        profileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        profileLabel.setForeground(new Color(100, 116, 139));
        titleBox.add(title);
        titleBox.add(profileLabel);
        header.add(titleBox, BorderLayout.WEST);

        // Right Download & Erasure Actions
        JPanel actionBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionBox.setOpaque(false);

        ModernButton btnJson = new ModernButton("Download Data (JSON)", null,
                new Color(37, 99, 235), new Color(29, 78, 216), Color.WHITE);
        btnJson.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnJson.addActionListener(e -> exportJson());

        ModernButton btnCsv = new ModernButton("Download Data (CSV)", null,
                new Color(16, 185, 129), new Color(5, 150, 105), Color.WHITE);
        btnCsv.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCsv.addActionListener(e -> exportCsv());

        ModernButton btnDelete = new ModernButton("Request Data Deletion", null,
                new Color(239, 68, 68), new Color(220, 38, 38), Color.WHITE);
        btnDelete.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnDelete.addActionListener(e -> requestDataDeletion());

        actionBox.add(btnJson);
        actionBox.add(btnCsv);
        actionBox.add(btnDelete);
        header.add(actionBox, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // Center Content with Tabs
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 12));

        // Tab 1: Linked Vehicles
        vehicleModel = new DefaultTableModel(new String[]{"License Plate", "State / Series", "Registered Date"}, 0);
        JTable vehicleTable = new JTable(vehicleModel);
        vehicleTable.setRowHeight(28);
        JPanel vehPanel = new JPanel(new BorderLayout(0, 10));
        vehPanel.setOpaque(false);
        vehPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        vehPanel.add(new JScrollPane(vehicleTable), BorderLayout.CENTER);

        JPanel vehBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        vehBar.setOpaque(false);
        JButton btnAddPlate = new JButton("+ Link New UP Plate");
        btnAddPlate.addActionListener(e -> linkNewVehicle());
        vehBar.add(btnAddPlate);
        vehPanel.add(vehBar, BorderLayout.SOUTH);
        tabs.addTab("Linked Vehicles", vehPanel);

        // Tab 2: Parking Activity
        ticketModel = new DefaultTableModel(new String[]{"Ticket #", "Vehicle Plate", "Entry Time (IST)", "Exit Time (IST)", "Status", "Total Fee"}, 0);
        JTable ticketTable = new JTable(ticketModel);
        ticketTable.setRowHeight(28);
        tabs.addTab("Parking Tickets", new JScrollPane(ticketTable));

        // Tab 3: Monthly Passes
        passModel = new DefaultTableModel(new String[]{"Pass #", "Vehicle Plate", "Class", "Start Date", "Expiry Date", "Status"}, 0);
        JTable passTable = new JTable(passModel);
        passTable.setRowHeight(28);
        tabs.addTab("Monthly Passes", new JScrollPane(passTable));

        add(tabs, BorderLayout.CENTER);

        refreshData();
    }

    public void refreshData() {
        AccessControl.checkPermission(Permission.CUSTOMER_VIEW_OWN_DATA);

        User user = UserSession.getInstance().getCurrentUser().orElse(null);
        if (user == null) return;

        profileLabel.setText(String.format("Logged in: %s (@%s) | Mobile: %s | DPDP Status: Active Consent",
                user.getFullName(), user.getUsername(), user.getMobile()));

        // Populate Linked Vehicles
        vehicleModel.setRowCount(0);
        List<String> plates = vehicleDao.findPlatesByUserId(user.getUserId());
        for (String p : plates) {
            vehicleModel.addRow(new Object[]{p, "Uttar Pradesh / Bharat", "Active"});
        }

        // Populate Tickets
        ticketModel.setRowCount(0);
        List<ParkingTicket> allTickets = ticketDao.findAll();
        for (ParkingTicket t : allTickets) {
            if (plates.stream().anyMatch(p -> p.equalsIgnoreCase(PlateUtil.normalize(t.getLicensePlate())))) {
                ticketModel.addRow(new Object[]{
                        t.getTicketNumber(),
                        t.getLicensePlate(),
                        DateFormat.display(t.getEntryTime()),
                        t.getExitTime() != null ? DateFormat.display(t.getExitTime()) : "Parked Inside",
                        t.getStatus().name(),
                        "₹" + String.format("%.2f", t.getTotalAmount())
                });
            }
        }

        // Populate Passes
        passModel.setRowCount(0);
        List<MembershipPass> passes = membershipDao.findByUserId(user.getUserId());
        for (MembershipPass mp : passes) {
            passModel.addRow(new Object[]{
                    "PASS-" + mp.getMembershipId(),
                    mp.getLicensePlate(),
                    mp.getVehicleType().name(),
                    DateFormat.displayDate(mp.getStartDate()),
                    DateFormat.displayDate(mp.getExpiryDate()),
                    mp.isValid() ? "ACTIVE" : "EXPIRED"
            });
        }
    }

    private void linkNewVehicle() {
        User user = UserSession.getInstance().getCurrentUser().orElse(null);
        if (user == null) return;

        String plate = JOptionPane.showInputDialog(this, "Enter UP Vehicle License Plate (e.g. UP32 AB 1234):", "Link Vehicle", JOptionPane.PLAIN_MESSAGE);
        if (plate != null && !plate.isBlank()) {
            try {
                vehicleDao.linkVehicle(user.getUserId(), plate);
                JOptionPane.showMessageDialog(this, "Vehicle " + PlateUtil.normalize(plate) + " successfully linked to your account.", "Linked", JOptionPane.INFORMATION_MESSAGE);
                refreshData();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error linking vehicle: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void requestDataDeletion() {
        User user = UserSession.getInstance().getCurrentUser().orElse(null);
        if (user == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Under DPDP Act 2023 Section 12(3), submitting an erasure request will:\n" +
                        "1. Queue your personal profile, username, and mobile number for administrative anonymization.\n" +
                        "2. Anonymize your vehicle plate records in past completed tickets.\n" +
                        "3. Note: Tax Invoices MUST be preserved for 72 months per CGST Act statutory compliance.\n\n" +
                        "Do you wish to proceed?",
                "Confirm DPDP Erasure Request", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                retentionService.requestDeletion(user.getUserId(), user.getUsername(), user.getMobile());
                JOptionPane.showMessageDialog(this,
                        "Your data deletion request has been submitted to the Data Protection Officer.\n" +
                                "You will receive confirmation once the administrative review is completed.",
                        "Request Submitted", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to submit request: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportJson() {
        User user = UserSession.getInstance().getCurrentUser().orElse(null);
        if (user == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("my_parking_data_" + user.getUsername() + ".json"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            List<String> plates = vehicleDao.findPlatesByUserId(user.getUserId());
            try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
                out.println("{");
                out.printf("  \"user_id\": %d,%n", user.getUserId());
                out.printf("  \"username\": \"%s\",%n", user.getUsername());
                out.printf("  \"full_name\": \"%s\",%n", user.getFullName());
                out.printf("  \"mobile\": \"%s\",%n", user.getMobile());
                out.printf("  \"dpdp_compliance\": \"Digital Personal Data Protection Act, 2023\",%n");
                out.printf("  \"exported_at\": \"%s\",%n", DateFormat.display(java.time.LocalDateTime.now()));
                out.println("  \"registered_vehicles\": [");
                for (int i = 0; i < plates.size(); i++) {
                    out.printf("    \"%s\"%s%n", plates.get(i), (i < plates.size() - 1 ? "," : ""));
                }
                out.println("  ]");
                out.println("}");
                JOptionPane.showMessageDialog(this, "Personal data successfully exported to: " + file.getName(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportCsv() {
        User user = UserSession.getInstance().getCurrentUser().orElse(null);
        if (user == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("my_parking_records_" + user.getUsername() + ".csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            List<String> plates = vehicleDao.findPlatesByUserId(user.getUserId());
            try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
                out.println("TicketNumber,LicensePlate,EntryTime,ExitTime,Status,TotalFeeINR");
                List<ParkingTicket> allTickets = ticketDao.findAll();
                for (ParkingTicket t : allTickets) {
                    if (plates.stream().anyMatch(p -> p.equalsIgnoreCase(PlateUtil.normalize(t.getLicensePlate())))) {
                        out.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%.2f%n",
                                t.getTicketNumber(),
                                t.getLicensePlate(),
                                DateFormat.toDbString(t.getEntryTime()),
                                t.getExitTime() != null ? DateFormat.toDbString(t.getExitTime()) : "",
                                t.getStatus().name(),
                                t.getTotalAmount());
                    }
                }
                JOptionPane.showMessageDialog(this, "Personal records successfully exported to: " + file.getName(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
