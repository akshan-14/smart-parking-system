package com.smartparking.ui.components;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.slot.ElectricSlot;
import com.smartparking.model.slot.HandicappedSlot;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.ImageLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Interactive, graphical parking slot widget for multi-floor map visualization.
 * Shows status colors, vehicle icons, type badges, and handles click-to-inspect events.
 */
public class SlotVisualWidget extends JPanel {

    private final ParkingSlot slot;
    private final Consumer<ParkingSlot> onSlotClick;
    private boolean isHovered = false;

    public SlotVisualWidget(ParkingSlot slot, Consumer<ParkingSlot> onSlotClick) {
        this.slot = slot;
        this.onSlotClick = onSlotClick;

        setPreferredSize(new Dimension(140, 115));
        setOpaque(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        updateToolTip();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (onSlotClick != null) {
                    onSlotClick.accept(slot);
                }
            }
        });
    }

    private void updateToolTip() {
        String vehicleInfo = (slot.getCurrentVehicle() != null)
                ? " | Vehicle: " + slot.getCurrentVehicle().getLicensePlate() + " (" + slot.getCurrentVehicle().getType() + ")"
                : "";
        setToolTipText(String.format("<html><b>Slot %s</b> (Floor %d)<br>Type: %s<br>Rate: \u20B9%.2f/hr<br>Status: <b>%s</b>%s</html>",
                slot.getSlotId(), slot.getFloorNumber(), slot.getSlotType(), slot.getHourlyRate(), slot.getStatus(), vehicleInfo));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // 1. Resolve Colors based on state
        Color statusColor;
        Color bgLight;
        String status = slot.getStatus();

        if ("OCCUPIED".equalsIgnoreCase(status)) {
            statusColor = UITheme.COLOR_OCCUPIED;
            bgLight = UITheme.BG_ELEVATED;
        } else if ("RESERVED".equalsIgnoreCase(status)) {
            statusColor = UITheme.COLOR_RESERVED;
            bgLight = new Color(52, 51, 35);
        } else if ("OUT_OF_SERVICE".equalsIgnoreCase(status)) {
            statusColor = UITheme.COLOR_MAINTENANCE;
            bgLight = UITheme.BG_ELEVATED;
        } else {
            statusColor = UITheme.COLOR_AVAILABLE;
            bgLight = UITheme.BG_ELEVATED;
        }

        // Fill parking bay background
        g2.setColor(bgLight);
        g2.fillRoundRect(2, 2, w - 4, h - 4, 10, 10);

        // Parking Bay Stalls (White/Yellow stall borders)
        g2.setColor(isHovered ? statusColor.darker() : statusColor);
        g2.setStroke(new BasicStroke(isHovered ? 2.5f : 1.5f));
        g2.drawRoundRect(2, 2, w - 4, h - 4, 10, 10);

        // Top Status Header Strip
        g2.setColor(statusColor);
        g2.fillRoundRect(2, 2, w - 4, 24, 10, 10);
        g2.fillRect(2, 16, w - 4, 10); // Flatten bottom of top header

        // Slot ID Text
        g2.setColor(Color.WHITE);
        g2.setFont(UITheme.font(Font.BOLD, 12));
        g2.drawString(slot.getSlotId(), 8, 18);

        // Slot Type Pill on Top Right
        String typeLabel = slot.getSlotType().name().substring(0, Math.min(3, slot.getSlotType().name().length()));
        g2.setFont(UITheme.font(Font.BOLD, 10));
        FontMetrics fm = g2.getFontMetrics();
        int typeX = w - fm.stringWidth(typeLabel) - 10;
        g2.drawString(typeLabel, typeX, 18);

        // Center Content: Vehicle Icon / Status Badge
        int centerY = 34;
        if ("OCCUPIED".equalsIgnoreCase(slot.getStatus()) && slot.getCurrentVehicle() != null) {
            ImageIcon vIcon = ImageLoader.getVehicleIcon(slot.getCurrentVehicle().getType(), 40, 40);
            if (vIcon != null && vIcon.getImage() != null) {
                g2.drawImage(vIcon.getImage(), (w - 40) / 2, centerY, 40, 40, null);
            }
            // License Plate
            g2.setColor(UITheme.TEXT_DARK);
            g2.setFont(UITheme.font(Font.BOLD, 11));
            String plate = slot.getCurrentVehicle().getLicensePlate();
            FontMetrics pFm = g2.getFontMetrics();
            g2.drawString(plate, (w - pFm.stringWidth(plate)) / 2, h - 14);
        } else {
            // Available or Reserved or Out of Service icon
            ImageIcon sIcon = ImageLoader.getSlotStatusIcon(slot.getStatus(), 32);
            if (sIcon != null) {
                g2.drawImage(sIcon.getImage(), (w - 32) / 2, centerY + 4, null);
            }

            g2.setColor(statusColor.darker());
            g2.setFont(UITheme.font(Font.BOLD, 11));
            FontMetrics sFm = g2.getFontMetrics();
            g2.drawString(status, (w - sFm.stringWidth(status)) / 2, h - 14);
        }

        // Special Badges (Bottom Left / Right)
        if (slot instanceof ElectricSlot) {
            ImageIcon evIcon = ImageLoader.loadIcon("icons/slots/ev_charging.png", 18, 18);
            if (evIcon != null) {
                g2.drawImage(evIcon.getImage(), 6, h - 24, null);
            }
        } else if (slot instanceof HandicappedSlot) {
            ImageIcon hcIcon = ImageLoader.loadIcon("icons/slots/handicapped_badge.png", 18, 18);
            if (hcIcon != null) {
                g2.drawImage(hcIcon.getImage(), 6, h - 24, null);
            }
        }

        // Hourly Rate on Bottom Right
        g2.setColor(UITheme.TEXT_MUTED);
        g2.setFont(UITheme.font(Font.PLAIN, 10));
        String rateText = String.format("\u20B9%.0f/h", slot.getHourlyRate());
        FontMetrics rFm = g2.getFontMetrics();
        g2.drawString(rateText, w - rFm.stringWidth(rateText) - 6, h - 6);

        g2.dispose();
    }

    public ParkingSlot getSlot() {
        return slot;
    }
}
