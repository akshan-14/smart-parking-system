package com.smartparking.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for generating high-resolution QR codes and barcodes using ZXing.
 * Used for UPI payment QR codes, GST invoice QR validation, and ticket scanner barcodes.
 */
public final class BarcodeGenerator {

    private BarcodeGenerator() {
        // Utility class
    }

    /**
     * Generates a square QR Code BufferedImage for a given string payload.
     */
    public static BufferedImage generateQrCode(String content, int size) {
        if (content == null || content.isEmpty()) content = "N/A";
        int dimension = Math.max(80, size);

        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, dimension, dimension, hints);
            return MatrixToImageWriter.toBufferedImage(bitMatrix);
        } catch (Exception e) {
            // Fallback placeholder image if encoding fails
            return createFallbackImage(dimension, dimension, "QR Error");
        }
    }

    /**
     * Generates a 1D Code 128 barcode for entry ticket scanner compatibility.
     */
    public static BufferedImage generateBarcode(String content, int width, int height) {
        if (content == null || content.isEmpty()) content = "000000";
        int w = Math.max(120, width);
        int h = Math.max(40, height);

        try {
            com.google.zxing.oned.Code128Writer writer = new com.google.zxing.oned.Code128Writer();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 2);
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.CODE_128, w, h, hints);
            return MatrixToImageWriter.toBufferedImage(bitMatrix);
        } catch (Exception e) {
            return createFallbackImage(w, h, content);
        }
    }

    public static ImageIcon generateQrIcon(String content, int size) {
        return new ImageIcon(generateQrCode(content, size));
    }

    public static ImageIcon generateBarcodeIcon(String content, int width, int height) {
        return new ImageIcon(generateBarcode(content, width, height));
    }

    private static BufferedImage createFallbackImage(int width, int height, String text) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        g2.setColor(Color.GRAY);
        g2.drawRect(0, 0, width - 1, height - 1);
        g2.drawString(text, 10, height / 2);
        g2.dispose();
        return img;
    }
}
