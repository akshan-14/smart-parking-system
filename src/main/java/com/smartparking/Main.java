package com.smartparking;

import com.smartparking.ui.ConsoleApp;
import com.smartparking.ui.LoginFrame;

import javax.swing.*;
import java.awt.GraphicsEnvironment;

/**
 * Main application entry point for the Smart Parking Management System.
 * Supports Modern Swing GUI with rich image assets, as well as Headless / CLI modes.
 */
public class Main {
    public static void main(String[] args) {
        boolean forceCli = false;
        boolean forceDemo = false;

        if (args != null && args.length > 0) {
            for (String arg : args) {
                if ("--cli".equalsIgnoreCase(arg) || "--console".equalsIgnoreCase(arg)) {
                    forceCli = true;
                } else if ("--demo".equalsIgnoreCase(arg)) {
                    forceDemo = true;
                }
            }
        }

        if (forceDemo) {
            ConsoleApp app = new ConsoleApp();
            app.start(new String[]{"--demo"});
        } else if (forceCli || GraphicsEnvironment.isHeadless()) {
            ConsoleApp app = new ConsoleApp();
            app.start(args);
        } else {
            // Launch Modern Swing GUI
            try {
                com.smartparking.ui.theme.DarkTheme.install();
            } catch (Exception ignored) {
            }
            SwingUtilities.invokeLater(() -> {
                LoginFrame loginFrame = new LoginFrame();
                loginFrame.setVisible(true);
            });
        }
    }
}
