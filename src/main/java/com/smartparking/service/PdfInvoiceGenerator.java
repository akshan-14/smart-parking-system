package com.smartparking.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.smartparking.model.invoice.Invoice;
import com.smartparking.util.BarcodeGenerator;
import com.smartparking.util.DateFormat;
import com.smartparking.util.MoneyFormat;
import com.smartparking.util.PlateUtil;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * PDF Invoice Generator using OpenPDF library.
 * Generates official, printable GST Tax Invoices compliant with Indian GST Law.
 */
public class PdfInvoiceGenerator {

    private final GstCalculator gstCalculator;

    public PdfInvoiceGenerator(GstCalculator gstCalculator) {
        this.gstCalculator = gstCalculator != null ? gstCalculator : new GstCalculator();
    }

    public PdfInvoiceGenerator() {
        this(new GstCalculator());
    }

    /**
     * Generates a GST Tax Invoice PDF document as byte array.
     */
    public byte[] generateInvoicePdfBytes(Invoice invoice) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        generateInvoicePdf(invoice, baos);
        return baos.toByteArray();
    }

    /**
     * Generates a GST Tax Invoice PDF saved directly to file.
     */
    public void generateInvoicePdfFile(Invoice invoice, File destination) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(destination)) {
            generateInvoicePdf(invoice, fos);
        }
    }

    /**
     * Core PDF generation routine.
     */
    public void generateInvoicePdf(Invoice invoice, OutputStream outputStream) throws Exception {
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, outputStream);
        document.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(15, 23, 42));
        Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(51, 65, 85));
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(15, 23, 42));
        Font regularFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(51, 65, 85));
        Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(100, 116, 139));
        Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(22, 101, 52));

        // 1. Header Table (Title & GSTIN)
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{70, 30});

        PdfPCell leftHeader = new PdfPCell();
        leftHeader.setBorder(Rectangle.NO_BORDER);
        leftHeader.addElement(new Paragraph(gstCalculator.getBusinessName().toUpperCase(), titleFont));
        leftHeader.addElement(new Paragraph("Official GST Tax Invoice (Rule 46, CGST Rules 2017)", subTitleFont));
        leftHeader.addElement(new Paragraph(gstCalculator.getBusinessAddress(), regularFont));
        leftHeader.addElement(new Paragraph("GSTIN: " + gstCalculator.getGstin() + "  |  State: Uttar Pradesh (Code 09)", boldFont));

        PdfPCell rightHeader = new PdfPCell();
        rightHeader.setBorder(Rectangle.NO_BORDER);
        rightHeader.setHorizontalAlignment(Element.ALIGN_RIGHT);

        // Embed QR Code
        java.awt.image.BufferedImage qrImg = BarcodeGenerator.generateQrCode(invoice.getQrData(), 110);
        Image qrPdfImg = Image.getInstance(qrImg, null);
        qrPdfImg.setAlignment(Element.ALIGN_RIGHT);
        rightHeader.addElement(qrPdfImg);

        headerTable.addCell(leftHeader);
        headerTable.addCell(rightHeader);
        document.add(headerTable);

        document.add(new Paragraph(" "));

        // 2. Invoice & Vehicle Info Box
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{50, 50});

        PdfPCell c1 = new PdfPCell();
        c1.setBackgroundColor(new Color(248, 250, 252));
        c1.setPadding(8);
        c1.setBorderColor(new Color(226, 232, 240));
        c1.addElement(new Paragraph("INVOICE DETAILS", boldFont));
        c1.addElement(new Paragraph("Invoice Number: " + invoice.getInvoiceNumber(), boldFont));
        c1.addElement(new Paragraph("Invoice Date  : " + invoice.getInvoiceDate(), regularFont));
        c1.addElement(new Paragraph("Payment Method: " + invoice.getPaymentMethod(), regularFont));
        c1.addElement(new Paragraph("Transaction Ref: " + invoice.getTransactionRef(), smallFont));

        PdfPCell c2 = new PdfPCell();
        c2.setBackgroundColor(new Color(248, 250, 252));
        c2.setPadding(8);
        c2.setBorderColor(new Color(226, 232, 240));
        c2.addElement(new Paragraph("VEHICLE & STAY DETAILS", boldFont));
        c2.addElement(new Paragraph("Vehicle Plate : " + PlateUtil.format(invoice.getLicensePlate()) + " [" + PlateUtil.district(invoice.getLicensePlate()) + "]", boldFont));
        c2.addElement(new Paragraph("Vehicle Class : " + invoice.getVehicleType().getDisplayName(), regularFont));
        if (invoice.getEntryTime() != null) {
            c2.addElement(new Paragraph("Entry Time    : " + DateFormat.display(invoice.getEntryTime()), regularFont));
        }
        if (invoice.getExitTime() != null) {
            c2.addElement(new Paragraph("Exit Time     : " + DateFormat.display(invoice.getExitTime()), regularFont));
        }
        if (invoice.getDurationMinutes() > 0) {
            long hrs = invoice.getDurationMinutes() / 60;
            long mins = invoice.getDurationMinutes() % 60;
            c2.addElement(new Paragraph(String.format("Billed Stay   : %d hr %d min (~%d hrs)", hrs, mins, (long) Math.ceil(invoice.getDurationMinutes() / 60.0)), regularFont));
        }

        infoTable.addCell(c1);
        infoTable.addCell(c2);
        document.add(infoTable);

        document.add(new Paragraph(" "));

        // 3. Line Items & Tax Breakdown Table
        PdfPTable itemsTable = new PdfPTable(6);
        itemsTable.setWidthPercentage(100);
        itemsTable.setWidths(new float[]{30, 14, 14, 14, 14, 14});

        String[] headers = {"Description of Service", "SAC Code", "Taxable (₹)", "CGST (9%)", "SGST (9%)", "Total (₹)"};
        for (String h : headers) {
            PdfPCell th = new PdfPCell(new Phrase(h, boldFont));
            th.setBackgroundColor(new Color(241, 245, 249));
            th.setPadding(6);
            th.setBorderColor(new Color(203, 213, 225));
            itemsTable.addCell(th);
        }

        String itemDesc = (invoice.getMembershipId() != null)
                ? "Monthly Parking Pass Subscription (" + invoice.getVehicleType().getDisplayName() + ")"
                : "Vehicle Parking Facility & Toll Access";

        addTableCell(itemsTable, itemDesc, regularFont, Element.ALIGN_LEFT);
        addTableCell(itemsTable, "9967", regularFont, Element.ALIGN_CENTER);
        addTableCell(itemsTable, String.format("%.2f", invoice.getTaxableValue()), regularFont, Element.ALIGN_RIGHT);
        addTableCell(itemsTable, String.format("%.2f", invoice.getCgstAmount()), regularFont, Element.ALIGN_RIGHT);
        addTableCell(itemsTable, String.format("%.2f", invoice.getSgstAmount()), regularFont, Element.ALIGN_RIGHT);
        double lineTotal = invoice.getTaxableValue() + invoice.getCgstAmount() + invoice.getSgstAmount();
        addTableCell(itemsTable, String.format("%.2f", lineTotal), regularFont, Element.ALIGN_RIGHT);

        // Round Off Row (if any)
        if (Math.abs(invoice.getRoundOff()) > 0.001) {
            PdfPCell roLabel = new PdfPCell(new Phrase("Round Off Adjustment", regularFont));
            roLabel.setColspan(5);
            roLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            roLabel.setPadding(5);
            itemsTable.addCell(roLabel);

            PdfPCell roVal = new PdfPCell(new Phrase(String.format("%+.2f", invoice.getRoundOff()), regularFont));
            roVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            roVal.setPadding(5);
            itemsTable.addCell(roVal);
        }

        // Grand Total Row
        PdfPCell gtLabel = new PdfPCell(new Phrase("GRAND TOTAL (INR ₹):", boldFont));
        gtLabel.setColspan(5);
        gtLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        gtLabel.setPadding(8);
        gtLabel.setBackgroundColor(new Color(240, 253, 244));
        itemsTable.addCell(gtLabel);

        PdfPCell gtVal = new PdfPCell(new Phrase(MoneyFormat.format(invoice.getTotalAmount()), totalFont));
        gtVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        gtVal.setPadding(8);
        gtVal.setBackgroundColor(new Color(240, 253, 244));
        itemsTable.addCell(gtVal);

        document.add(itemsTable);

        document.add(new Paragraph(" "));

        // 4. Amount in Words Box
        Paragraph wordsPara = new Paragraph("Amount in Words: " + invoice.getAmountInWords(), boldFont);
        wordsPara.setSpacingAfter(10);
        document.add(wordsPara);

        // 5. Footer & Declaration
        Paragraph decl = new Paragraph("Declaration: Certified that all particulars are true and correct. Electronic Tax Invoice generated under Computerized Parking System.", smallFont);
        decl.setAlignment(Element.ALIGN_CENTER);
        document.add(decl);

        document.close();
    }

    private void addTableCell(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(6);
        cell.setBorderColor(new Color(226, 232, 240));
        table.addCell(cell);
    }
}
