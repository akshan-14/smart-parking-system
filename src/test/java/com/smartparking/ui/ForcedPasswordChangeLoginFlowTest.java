package com.smartparking.ui;

import com.smartparking.exception.PasswordChangeRequiredException;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.user.User;
import com.smartparking.repository.UserDao;
import com.smartparking.security.UserSession;
import com.smartparking.service.AuthService;
import com.smartparking.service.ParkingService;
import com.smartparking.support.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GUI sign-in flow for force_password_change users. The modal dialog and the main-window opener are replaced with
 * recorders so the test can observe exactly what the login window does.
 */
@DisplayName("GUI: forced password change cannot be skipped from the login window")
public class ForcedPasswordChangeLoginFlowTest {

    private LoginFrame login;
    private final List<User> opened = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        TestDatabase.useFresh("test_forced_change_gui");
        opened.clear();
        if (GraphicsEnvironment.isHeadless()) return;
        SwingUtilities.invokeAndWait(() -> {
            login = new LoginFrame(); // also initialises the fresh database
            login.mainWindowOpener = opened::add;
            login.pack(); // realise the native window (without showing it) so dispose() is observable
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        UserSession.getInstance().clearSession();
        if (login != null) SwingUtilities.invokeAndWait(login::dispose);
    }

    private boolean signIn(String user, String password) throws Exception {
        AtomicBoolean result = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> result.set(login.attemptLogin(user, password)));
        SwingUtilities.invokeAndWait(() -> { }); // flush the queued main-window opening
        return result.get();
    }

    private boolean adminFlagInDb() {
        return new UserDao().findByUsername("admin").orElseThrow().isForcePasswordChange();
    }

    @Test
    @DisplayName("Cancelling the dialog returns to login and never opens the main window")
    void cancelReturnsToLogin() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("[GUI TEST] headless: skipping login window flow");
            return;
        }
        AtomicInteger prompts = new AtomicInteger();
        login.forcedPasswordChangePrompt = (u, current) -> { prompts.incrementAndGet(); return false; };

        assertFalse(signIn("admin", "Admin@123"));
        assertEquals(1, prompts.get(), "the flagged admin must be asked to change the password");
        assertTrue(opened.isEmpty(), "main window must not open after cancel");
        assertTrue(login.isDisplayable(), "login window stays open");
        assertTrue(UserSession.getInstance().getCurrentUser().isEmpty(), "user is signed out again");
        assertTrue(adminFlagInDb());
    }

    @Test
    @DisplayName("A prompt that reports success without changing the password is not trusted")
    void promptMustReallyChangePassword() throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;
        login.forcedPasswordChangePrompt = (u, current) -> true; // claims success, flag still set
        assertFalse(signIn("admin", "Admin@123"));
        assertTrue(opened.isEmpty());
        assertTrue(UserSession.getInstance().getCurrentUser().isEmpty());
    }

    @Test
    @DisplayName("Completing the change opens the main window with full access")
    void completedChangeOpensMainWindow() throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;
        AuthService auth = new AuthService();
        login.forcedPasswordChangePrompt = (u, current) -> {
            auth.completeForcedPasswordChange(u, current, "Rotated#Admin2026");
            return true;
        };
        assertTrue(signIn("admin", "Admin@123"));
        assertEquals(1, opened.size());
        assertFalse(opened.get(0).isForcePasswordChange());
        assertFalse(login.isDisplayable(), "login window is closed once the main window opens");
        assertFalse(adminFlagInDb());
    }

    @Test
    @DisplayName("Users without the flag sign in without a prompt")
    void normalLoginUnchanged() throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;
        login.forcedPasswordChangePrompt = (u, current) -> fail("no prompt expected for attendant1");
        assertTrue(signIn("attendant1", "Attendant@123"));
        assertEquals(1, opened.size());
        assertEquals("attendant1", opened.get(0).getUsername());
    }

    @Test
    @DisplayName("Invalid login opens nothing")
    void invalidLogin() throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;
        assertFalse(signIn("admin", "Wrong@Pass1"));
        assertTrue(opened.isEmpty());
        assertTrue(UserSession.getInstance().getCurrentUser().isEmpty());
    }

    @Test
    @DisplayName("The main window itself refuses a flagged user before building any UI")
    void mainWindowGateRejectsFlaggedUser() throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;
        User flagged = new UserDao().findByUsername("admin").orElseThrow();
        assertTrue(flagged.isForcePasswordChange());
        // Frame.getFrames() also lists frames from earlier tests until they are garbage collected, so compare the
        // set of main windows by identity rather than counting frames.
        java.util.Set<Frame> mainFramesBefore = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Frame f : Frame.getFrames()) if (f instanceof SmartParkingMainFrame) mainFramesBefore.add(f);
        SwingUtilities.invokeAndWait(() -> {
            ParkingLot lot = new ParkingLot("LOT-T", "Test", "Test");
            lot.addFloor(new ParkingFloor(1, "Floor 1"));
            assertThrows(PasswordChangeRequiredException.class,
                    () -> new SmartParkingMainFrame(flagged, lot, ParkingService.createDefault(lot)));
        });
        for (Frame f : Frame.getFrames()) {
            assertFalse(f instanceof SmartParkingMainFrame && !mainFramesBefore.contains(f), "no main frame may be created");
        }
        assertTrue(UserSession.getInstance().getCurrentUser().isEmpty(), "no session may be started");
    }
}
