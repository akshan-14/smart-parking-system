package com.smartparking.service;

import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.invoice.Invoice;
import com.smartparking.model.membership.MembershipPass;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.repository.InvoiceDao;
import com.smartparking.util.DateFormat;
import com.smartparking.util.MoneyFormat;
import com.smartparking.util.NumberToWords;
import com.smartparking.util.PlateUtil;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * High-level service managing GST Tax Invoices, PDF exports, and styled on-screen receipts.
 */
public class InvoiceService {

    private final InvoiceDao invoiceDao;
    private final InvoiceNumberGenerator numberGenerator;
    private final GstCalculator gstCalculator;
    private final PdfInvoiceGenerator pdfGenerator;

    public InvoiceService(InvoiceDao invoiceDao, InvoiceNumberGenerator numberGenerator,
                          GstCalculator gstCalculator, PdfInvoiceGenerator pdfGenerator) {
        this.invoiceDao = invoiceDao != null ? invoiceDao : new InvoiceDao();
        this.numberGenerator = numberGenerator != null ? numberGenerator : new InvoiceNumberGenerator();
        this.gstCalculator = gstCalculator != null ? gstCalculator : new GstCalculator();
        this.pdfGenerator = pdfGenerator != null ? pdfGenerator : new PdfInvoiceGenerator(this.gstCalculator);
    }

    public InvoiceService() {
        this(new InvoiceDao(), new InvoiceNumberGenerator(), new GstCalculator(), null);
    }

    /**
     * Creates and persists a GST Tax Invoice for a checkout parking ticket inside an open transaction.
     */
    public Invoice createTicketInvoice(Connection conn, ParkingTicket ticket, GstCalculator.GstResult gst,
                                       String paymentMethod, String txnRef) throws SQLException {
        String invNum = numberGenerator.nextInvoiceNumber(conn);
        String fy = InvoiceNumberGenerator.getFinancialYear();
        String dateStr = DateFormat.display(LocalDateTime.now());

        String qrPayload = String.format("GSTIN:%s|INV:%s|DATE:%s|AMT:%.2f|VEH:%s",
                gstCalculator.getGstin(), invNum, dateStr, gst.grandTotal(), ticket.getLicensePlate());

        String words = NumberToWords.convert(gst.grandTotal());

        long durationMinutes = (ticket.getEntryTime() != null && ticket.getExitTime() != null)
                ? Duration.between(ticket.getEntryTime(), ticket.getExitTime()).toMinutes() : 0;

        Invoice invoice = new Invoice(
                0, invNum, fy, dateStr,
                ticket.getTicketId(), null,
                ticket.getLicensePlate(), ticket.getVehicleType(),
                ticket.getEntryTime(), ticket.getExitTime(), durationMinutes,
                gst.taxableValue(), gst.cgstRatePercent(), gst.cgstAmount(),
                gst.sgstRatePercent(), gst.sgstAmount(), gst.roundOff(),
                gst.grandTotal(), words, paymentMethod, txnRef, qrPayload
        );

        invoiceDao.save(conn, invoice);
        return invoice;
    }

    /**
     * Creates and persists a GST Tax Invoice for a monthly pass subscription.
     */
    public Invoice createMembershipInvoice(Connection conn, MembershipPass pass, GstCalculator.GstResult gst,
                                           String paymentMethod, String txnRef) throws SQLException {
        String invNum = numberGenerator.nextInvoiceNumber(conn);
        String fy = InvoiceNumberGenerator.getFinancialYear();
        String dateStr = DateFormat.display(LocalDateTime.now());

        String qrPayload = String.format("GSTIN:%s|INV:%s|PASS:%d|AMT:%.2f|VEH:%s",
                gstCalculator.getGstin(), invNum, pass.getMembershipId(), gst.grandTotal(), pass.getLicensePlate());

        String words = NumberToWords.convert(gst.grandTotal());

        Invoice invoice = new Invoice(
                0, invNum, fy, dateStr,
                null, pass.getMembershipId(),
                pass.getLicensePlate(), pass.getVehicleType(),
                null, null, 0,
                gst.taxableValue(), gst.cgstRatePercent(), gst.cgstAmount(),
                gst.sgstRatePercent(), gst.sgstAmount(), gst.roundOff(),
                gst.grandTotal(), words, paymentMethod, txnRef, qrPayload
        );

        invoiceDao.save(conn, invoice);
        return invoice;
    }

    public Optional<Invoice> findByInvoiceNumber(String num) {
        return invoiceDao.findByInvoiceNumber(num);
    }

    public List<Invoice> findByPlate(String plate) {
        return invoiceDao.findByPlate(plate);
    }

    public List<Invoice> findRecent(int limit) {
        return invoiceDao.findRecent(limit);
    }

    public void exportToPdf(Invoice invoice, File destination) throws Exception {
        pdfGenerator.generateInvoicePdfFile(invoice, destination);
    }

    public void exportPdf(Invoice invoice, File destination) throws Exception {
        exportToPdf(invoice, destination);
    }

    public byte[] exportToPdfBytes(Invoice invoice) throws Exception {
        return pdfGenerator.generateInvoicePdfBytes(invoice);
    }

    public String generateHtmlReceipt(Invoice invoice) {
        return buildHtmlReceipt(invoice);
    }

    /**
     * Builds responsive, modern HTML string for presenting on-screen tax invoice in UI (no monospace).
     */
    public String buildHtmlReceipt(Invoice invoice) {
        String district = PlateUtil.district(invoice.getLicensePlate());
        String plateFormatted = PlateUtil.format(invoice.getLicensePlate());

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family: Segoe UI, sans-serif; margin: 0; padding: 12px; color: #1e293b; background: #ffffff;'>");

        // Header
        sb.append("<div style='text-align: center; border-bottom: 2px solid #2563eb; padding-bottom: 8px; margin-bottom: 12px;'>");
        sb.append("<h2 style='margin: 0; color: #1e3a8a; font-size: 16px;'>").append(gstCalculator.getBusinessName()).append("</h2>");
        sb.append("<div style='font-size: 11px; color: #475569;'>").append(gstCalculator.getBusinessAddress()).append("</div>");
        sb.append("<div style='font-size: 11px; font-weight: bold; color: #0f172a; margin-top: 2px;'>GSTIN: ").append(gstCalculator.getGstin()).append(" (Uttar Pradesh - 09)</div>");
        sb.append("<div style='display: inline-block; background: #dcfce7; color: #166534; font-size: 11px; font-weight: bold; padding: 2px 10px; border-radius: 12px; margin-top: 4px;'>TAX INVOICE</div>");
        sb.append("</div>");

        // Invoice & Vehicle Meta Table
        sb.append("<table style='width: 100%; font-size: 11px; margin-bottom: 12px;'>");
        sb.append("<tr>");
        sb.append("<td><b>Invoice No:</b> ").append(invoice.getInvoiceNumber()).append("</td>");
        sb.append("<td style='text-align: right;'><b>Vehicle:</b> <span style='background: #fef08a; padding: 2px 6px; border-radius: 4px; font-weight: bold;'>").append(plateFormatted).append("</span> [").append(district).append("]</td>");
        sb.append("</tr>");
        sb.append("<tr>");
        sb.append("<td><b>Date:</b> ").append(invoice.getInvoiceDate()).append("</td>");
        sb.append("<td style='text-align: right;'><b>Class:</b> ").append(invoice.getVehicleType().getDisplayName()).append("</td>");
        sb.append("</tr>");
        if (invoice.getDurationMinutes() > 0) {
            long hrs = invoice.getDurationMinutes() / 60;
            long mins = invoice.getDurationMinutes() % 60;
            sb.append("<tr>");
            sb.append("<td><b>Duration:</b> ").append(hrs).append("h ").append(mins).append("m</td>");
            sb.append("<td style='text-align: right;'><b>Mode:</b> ").append(invoice.getPaymentMethod()).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");

        // Itemized Charges Table
        sb.append("<table style='width: 100%; border-collapse: collapse; font-size: 11px; margin-bottom: 10px;'>");
        sb.append("<tr style='background: #f1f5f9; border-bottom: 1px solid #cbd5e1;'>");
        sb.append("<th style='text-align: left; padding: 6px;'>Description</th>");
        sb.append("<th style='text-align: right; padding: 6px;'>Taxable</th>");
        sb.append("<th style='text-align: right; padding: 6px;'>CGST 9%</th>");
        sb.append("<th style='text-align: right; padding: 6px;'>SGST 9%</th>");
        sb.append("<th style='text-align: right; padding: 6px;'>Total</th>");
        sb.append("</tr>");

        double lineTot = invoice.getTaxableValue() + invoice.getCgstAmount() + invoice.getSgstAmount();
        sb.append("<tr style='border-bottom: 1px solid #e2e8f0;'>");
        sb.append("<td style='padding: 6px;'>Parking Services (SAC 9967)</td>");
        sb.append("<td style='text-align: right; padding: 6px;'>").append(MoneyFormat.format(invoice.getTaxableValue())).append("</td>");
        sb.append("<td style='text-align: right; padding: 6px;'>").append(MoneyFormat.format(invoice.getCgstAmount())).append("</td>");
        sb.append("<td style='text-align: right; padding: 6px;'>").append(MoneyFormat.format(invoice.getSgstAmount())).append("</td>");
        sb.append("<td style='text-align: right; padding: 6px; font-weight: bold;'>").append(MoneyFormat.format(lineTot)).append("</td>");
        sb.append("</tr>");

        if (Math.abs(invoice.getRoundOff()) > 0.001) {
            sb.append("<tr>");
            sb.append("<td colspan='4' style='text-align: right; padding: 4px; color: #64748b;'>Round Off:</td>");
            sb.append("<td style='text-align: right; padding: 4px; color: #64748b;'>").append(String.format("%+.2f", invoice.getRoundOff())).append("</td>");
            sb.append("</tr>");
        }

        // Grand Total Row
        sb.append("<tr style='background: #f0fdf4;'>");
        sb.append("<td colspan='4' style='text-align: right; padding: 8px; font-weight: bold; font-size: 12px; color: #166534;'>GRAND TOTAL (INR):</td>");
        sb.append("<td style='text-align: right; padding: 8px; font-weight: bold; font-size: 13px; color: #166534;'>").append(MoneyFormat.format(invoice.getTotalAmount())).append("</td>");
        sb.append("</tr>");
        sb.append("</table>");

        // Amount in words
        sb.append("<div style='font-size: 11px; font-weight: bold; color: #334155; margin-bottom: 6px;'>In Words: <span style='font-weight: normal;'>").append(invoice.getAmountInWords()).append("</span></div>");
        sb.append("<div style='font-size: 10px; color: #64748b;'>Txn Ref: ").append(invoice.getTransactionRef()).append(" | Status: COMPLETED</div>");

        // Footer
        sb.append("<div style='margin-top: 10px; padding-top: 6px; border-top: 1px dashed #cbd5e1; font-size: 10px; color: #94a3b8; text-align: center;'>");
        sb.append("Thank you for choosing Grand Tech Park Garage. Have a safe journey!");
        sb.append("</div>");

        sb.append("</body></html>");
        return sb.toString();
    }
}
