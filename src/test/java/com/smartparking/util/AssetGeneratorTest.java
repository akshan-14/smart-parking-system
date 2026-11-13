package com.smartparking.util;

import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.ui.util.ImageLoader;
import org.junit.jupiter.api.Test;
import java.io.File;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetGeneratorTest {

    @Test
    void testGenerateAllAssets() {
        AssetGenerator.main(new String[0]);

        // Verify key assets exist on disk
        assertTrue(new File("src/main/resources/images/logo.png").exists());
        assertTrue(new File("src/main/resources/images/parking_background.png").exists());
        assertTrue(new File("src/main/resources/images/dashboard_banner.png").exists());
        assertTrue(new File("src/main/resources/icons/vehicles/car.png").exists());
        assertTrue(new File("src/main/resources/icons/payments/card.png").exists());
        assertTrue(new File("src/main/resources/icons/dashboard/total_slots.png").exists());
        assertTrue(new File("src/main/resources/icons/navigation/dashboard.png").exists());
    }

    @Test
    void testImageLoaderClasspathResolution() {
        // Verify ImageLoader loads assets cleanly via ClassLoader/getResource
        assertNotNull(ImageLoader.loadImage("images/logo.png"));
        assertNotNull(ImageLoader.loadImage("images/parking_background.png"));
        assertNotNull(ImageLoader.loadImage("images/dashboard_banner.png"));

        assertNotNull(ImageLoader.getVehicleIcon(VehicleType.CAR, 24, 24));
        assertNotNull(ImageLoader.getVehicleIcon(VehicleType.BIKE, 24, 24));
        assertNotNull(ImageLoader.getVehicleIcon(VehicleType.ELECTRIC_CAR, 24, 24));
        assertNotNull(ImageLoader.getVehicleIcon(VehicleType.TRUCK, 24, 24));

        assertNotNull(ImageLoader.getPaymentIcon(PaymentMethodType.UPI, 24, 24));
        assertNotNull(ImageLoader.getPaymentIcon(PaymentMethodType.CARD, 24, 24));
        assertNotNull(ImageLoader.getPaymentIcon(PaymentMethodType.CASH, 24, 24));
        assertNotNull(ImageLoader.getPaymentIcon(PaymentMethodType.WALLET, 24, 24));

        assertNotNull(ImageLoader.getNavIcon("dashboard", 24));
        assertNotNull(ImageLoader.getNavIcon("entry", 24));
        assertNotNull(ImageLoader.getNavIcon("exit", 24));
        assertNotNull(ImageLoader.getNavIcon("floors", 24));
        assertNotNull(ImageLoader.getNavIcon("reservation", 24));
        assertNotNull(ImageLoader.getNavIcon("membership", 24));
        assertNotNull(ImageLoader.getNavIcon("search", 24));
        assertNotNull(ImageLoader.getNavIcon("reports", 24));
        assertNotNull(ImageLoader.getNavIcon("logout", 24));

        assertNotNull(ImageLoader.getSlotStatusIcon("AVAILABLE", 24));
        assertNotNull(ImageLoader.getSlotStatusIcon("OCCUPIED", 24));
        assertNotNull(ImageLoader.getSlotStatusIcon("RESERVED", 24));
        assertNotNull(ImageLoader.getSlotStatusIcon("OUT_OF_SERVICE", 24));

        assertNotNull(ImageLoader.getAuthIcon("user", 24));
        assertNotNull(ImageLoader.getAuthIcon("password", 24));
        assertNotNull(ImageLoader.getAuthIcon("shield", 24));
    }
}
