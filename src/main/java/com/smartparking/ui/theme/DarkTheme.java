package com.smartparking.ui.theme;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static com.smartparking.ui.theme.UITheme.*;

/**
 * Installs the application-wide dark Look &amp; Feel.
 *
 * <p>Built on the JDK's cross-platform Metal L&amp;F (no third-party dependency) with:
 * <ul>
 *     <li>a dark {@link DefaultMetalTheme} so Metal-derived colours are correct,</li>
 *     <li>~150 explicit {@link UIManager} defaults (tables, inputs, dialogs, menus, tooltips),</li>
 *     <li>flat custom delegates for buttons, scroll bars, tabs, combo boxes and spinners,</li>
 *     <li>vector check-box / radio / option-pane icons,</li>
 *     <li>a global focus-ring repaint hook so inputs highlight with the accent colour.</li>
 * </ul>
 * Call {@link #install()} once, before any window is created.
 */
public final class DarkTheme {

    private static boolean installed;

    private DarkTheme() {
    }

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        // Crisp text on all platforms
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");
        try {
            MetalLookAndFeel.setCurrentTheme(new Theme());
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception e) {
            // Fall back silently: defaults below still apply to whatever L&F is active.
        }
        applyDefaults(UIManager.getDefaults());
        installFocusRepaintHook();
        installTableAutoStyling();
        installFocusVisibleTracking();
    }

    // -------------------------------------------------------------------------------------
    // Focus-visible: show focus rings only while the user navigates with the keyboard
    // -------------------------------------------------------------------------------------
    private static volatile boolean keyboardMode;

    /** True after Tab/arrow navigation, false again after the next mouse press. */
    public static boolean isFocusVisible() {
        return keyboardMode;
    }

    private static void installFocusVisibleTracking() {
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e instanceof KeyEvent) {
                int k = ((KeyEvent) e).getKeyCode();
                if (e.getID() == KeyEvent.KEY_PRESSED && (k == KeyEvent.VK_TAB || k == KeyEvent.VK_UP || k == KeyEvent.VK_DOWN
                        || k == KeyEvent.VK_LEFT || k == KeyEvent.VK_RIGHT)) {
                    if (!keyboardMode) { keyboardMode = true; repaintAll(); }
                }
            } else if (e.getID() == MouseEvent.MOUSE_PRESSED && keyboardMode) {
                keyboardMode = false;
                repaintAll();
            }
        }, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
    }

    private static void repaintAll() {
        for (Window w : Window.getWindows()) if (w.isShowing()) w.repaint();
    }

    // =====================================================================================
    // Defaults
    // =====================================================================================
    private static void applyDefaults(UIDefaults d) {
        ColorUIResource bg = res(BG_CANVAS), card = res(BG_CARD), elev = res(BG_ELEVATED), input = res(BG_INPUT);
        ColorUIResource fg = res(TEXT_PRIMARY), fg2 = res(TEXT_SECONDARY), fg3 = res(TEXT_TERTIARY), dis = res(TEXT_DISABLED);
        ColorUIResource sel = res(BG_SELECTED), accent = res(ACCENT), border = res(BORDER);

        // Fonts: every *.font key -> the design-system UI font
        FontUIResource regular = new FontUIResource(FONT_REGULAR);
        List<Object> keys = new ArrayList<>();
        for (Enumeration<Object> e = d.keys(); e.hasMoreElements(); ) keys.add(e.nextElement());
        for (Object k : keys) {
            if (k instanceof String && ((String) k).endsWith(".font")) d.put(k, regular);
        }
        d.put("TableHeader.font", new FontUIResource(FONT_CAPTION));
        d.put("TitledBorder.font", new FontUIResource(FONT_BOLD));
        d.put("ToolTip.font", new FontUIResource(FONT_SMALL));

        // Flatten Metal gradients
        for (String g : new String[]{"Button.gradient", "ToggleButton.gradient", "CheckBox.gradient", "RadioButton.gradient",
                "ScrollBar.gradient", "Slider.gradient", "Slider.focusGradient", "MenuBar.gradient",
                "InternalFrame.activeTitleGradient", "CheckBoxMenuItem.gradient", "RadioButtonMenuItem.gradient"}) {
            d.put(g, null);
        }

        // Base containers
        d.put("control", bg);
        d.put("Panel.background", bg);
        d.put("Panel.foreground", fg);
        d.put("Viewport.background", card);
        d.put("ScrollPane.background", card);
        d.put("ScrollPane.border", new BorderUIResource(new RoundBorder(BORDER_SUBTLE, RADIUS)));
        d.put("ScrollPane.viewportBorder", null);
        d.put("SplitPane.background", bg);
        d.put("SplitPane.dividerSize", 6);
        d.put("SplitPane.border", null);
        d.put("SplitPaneDivider.border", null);
        d.put("Separator.foreground", res(BORDER_SUBTLE));
        d.put("Separator.background", res(BG_CANVAS));
        d.put("Separator.highlight", res(BORDER_SUBTLE));
        d.put("Separator.shadow", res(BORDER_SUBTLE));
        d.put("TitledBorder.titleColor", fg2);
        d.put("TitledBorder.border", new BorderUIResource(new RoundBorder(BORDER, RADIUS)));

        // Labels
        d.put("Label.foreground", fg);
        d.put("Label.disabledForeground", dis);

        // Buttons
        d.put("ButtonUI", FlatButtonUI.class.getName());
        d.put("ToggleButtonUI", FlatButtonUI.class.getName());
        d.put("Button.background", elev);
        d.put("Button.foreground", fg);
        d.put("Button.select", res(BG_HOVER));
        d.put("Button.focus", res(ACCENT_BORDER));
        d.put("Button.disabledText", dis);
        d.put("Button.border", new BorderUIResource(new EmptyBorder(7, 14, 7, 14)));
        d.put("Button.margin", new InsetsUIResource(6, 14, 6, 14));
        d.put("ToggleButton.background", elev);
        d.put("ToggleButton.foreground", fg);
        d.put("ToggleButton.select", res(ACCENT_SOFT));
        d.put("ToggleButton.border", new BorderUIResource(new EmptyBorder(7, 14, 7, 14)));

        // Check boxes / radios
        d.put("CheckBox.background", bg);
        d.put("CheckBox.foreground", fg);
        d.put("CheckBox.disabledText", dis);
        d.put("CheckBox.icon", new CheckIcon(false));
        d.put("CheckBox.border", new BorderUIResource(new EmptyBorder(2, 2, 2, 2)));
        d.put("RadioButton.background", bg);
        d.put("RadioButton.foreground", fg);
        d.put("RadioButton.disabledText", dis);
        d.put("RadioButton.icon", new CheckIcon(true));
        d.put("RadioButton.border", new BorderUIResource(new EmptyBorder(2, 2, 2, 2)));
        d.put("CheckBoxMenuItem.checkIcon", new CheckIcon(false));
        d.put("RadioButtonMenuItem.checkIcon", new CheckIcon(true));

        // Text inputs
        BorderUIResource inputBorder = new BorderUIResource(BorderFactory.createCompoundBorder(
                new RoundBorder(BORDER, RADIUS), new EmptyBorder(7, 11, 7, 11)));
        for (String p : new String[]{"TextField", "PasswordField", "FormattedTextField"}) {
            d.put(p + ".background", input);
            d.put(p + ".foreground", fg);
            d.put(p + ".caretForeground", res(ACCENT_TEXT));
            d.put(p + ".selectionBackground", sel);
            d.put(p + ".selectionForeground", fg);
            d.put(p + ".inactiveForeground", fg3);
            d.put(p + ".inactiveBackground", res(BG_SURFACE));
            d.put(p + ".disabledBackground", res(BG_SURFACE));
            d.put(p + ".border", inputBorder);
        }
        for (String p : new String[]{"TextArea", "TextPane", "EditorPane"}) {
            d.put(p + ".background", input);
            d.put(p + ".foreground", fg);
            d.put(p + ".caretForeground", res(ACCENT_TEXT));
            d.put(p + ".selectionBackground", sel);
            d.put(p + ".selectionForeground", fg);
            d.put(p + ".inactiveForeground", fg2);
        }

        // Combo boxes
        d.put("ComboBoxUI", FlatComboBoxUI.class.getName());
        d.put("ComboBox.background", input);
        d.put("ComboBox.foreground", fg);
        d.put("ComboBox.buttonBackground", input);
        d.put("ComboBox.selectionBackground", sel);
        d.put("ComboBox.selectionForeground", fg);
        d.put("ComboBox.disabledBackground", res(BG_SURFACE));
        d.put("ComboBox.disabledForeground", dis);
        d.put("ComboBox.border", new BorderUIResource(new RoundBorder(BORDER, RADIUS)));

        // Spinners
        d.put("SpinnerUI", FlatSpinnerUI.class.getName());
        d.put("Spinner.background", input);
        d.put("Spinner.foreground", fg);
        d.put("Spinner.border", new BorderUIResource(new RoundBorder(BORDER, RADIUS)));
        d.put("Spinner.arrowButtonBorder", new BorderUIResource(new EmptyBorder(0, 0, 0, 0)));
        d.put("FormattedTextField.border", inputBorder);

        // Lists
        d.put("List.background", card);
        d.put("List.foreground", fg);
        d.put("List.selectionBackground", sel);
        d.put("List.selectionForeground", fg);
        d.put("List.focusCellHighlightBorder", new BorderUIResource(new EmptyBorder(1, 1, 1, 1)));

        // Tables
        d.put("Table.background", card);
        d.put("Table.foreground", fg);
        d.put("Table.selectionBackground", sel);
        d.put("Table.selectionForeground", fg);
        d.put("Table.gridColor", res(BORDER_SUBTLE));
        d.put("Table.focusCellHighlightBorder", new BorderUIResource(new EmptyBorder(1, 1, 1, 1)));
        d.put("Table.focusCellBackground", sel);
        d.put("Table.focusCellForeground", fg);
        d.put("Table.rowHeight", 34);
        d.put("Table.scrollPaneBorder", new BorderUIResource(new RoundBorder(BORDER_SUBTLE, RADIUS)));
        d.put("Table.dropLineColor", accent);
        d.put("TableHeader.background", elev);
        d.put("TableHeader.foreground", fg2);
        d.put("TableHeader.cellBorder", new BorderUIResource(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, BORDER), new EmptyBorder(9, 10, 9, 10))));

        // Tabs
        d.put("TabbedPaneUI", FlatTabbedPaneUI.class.getName());
        d.put("TabbedPane.background", bg);
        d.put("TabbedPane.foreground", fg2);
        d.put("TabbedPane.selected", bg);
        d.put("TabbedPane.contentAreaColor", bg);
        d.put("TabbedPane.tabInsets", new InsetsUIResource(9, 14, 9, 14));
        d.put("TabbedPane.contentBorderInsets", new InsetsUIResource(12, 0, 0, 0));
        d.put("TabbedPane.tabAreaInsets", new InsetsUIResource(0, 0, 0, 0));
        d.put("TabbedPane.selectedTabPadInsets", new InsetsUIResource(0, 0, 0, 0));
        d.put("TabbedPane.focus", res(BG_CANVAS));

        // Scroll bars
        d.put("ScrollBarUI", FlatScrollBarUI.class.getName());
        d.put("ScrollBar.width", 10);
        d.put("ScrollBar.background", card);
        d.put("ScrollBar.track", card);
        d.put("ScrollBar.thumb", res(BORDER_STRONG));

        // Progress / slider
        d.put("ProgressBar.background", elev);
        d.put("ProgressBar.foreground", accent);
        d.put("ProgressBar.selectionBackground", fg);
        d.put("ProgressBar.selectionForeground", fg);
        d.put("ProgressBar.border", new BorderUIResource(new EmptyBorder(0, 0, 0, 0)));
        d.put("Slider.background", bg);
        d.put("Slider.foreground", accent);
        d.put("Slider.focus", bg);

        // Menus & popups
        for (String p : new String[]{"MenuBar", "Menu", "MenuItem", "PopupMenu", "CheckBoxMenuItem", "RadioButtonMenuItem"}) {
            d.put(p + ".background", elev);
            d.put(p + ".foreground", fg);
            d.put(p + ".selectionBackground", res(BG_HOVER));
            d.put(p + ".selectionForeground", fg);
            d.put(p + ".acceleratorForeground", fg3);
            d.put(p + ".disabledForeground", dis);
        }
        d.put("PopupMenu.border", new BorderUIResource(BorderFactory.createCompoundBorder(
                new LineBorderRes(BORDER), new EmptyBorder(4, 0, 4, 0))));
        d.put("MenuItem.border", new BorderUIResource(new EmptyBorder(6, 12, 6, 12)));
        d.put("MenuBar.border", new BorderUIResource(new MatteBorder(0, 0, 1, 0, BORDER_SUBTLE)));

        // Tooltips
        d.put("ToolTip.background", res(BG_HOVER));
        d.put("ToolTip.foreground", fg);
        d.put("ToolTip.border", new BorderUIResource(BorderFactory.createCompoundBorder(
                new LineBorderRes(BORDER_STRONG), new EmptyBorder(5, 8, 5, 8))));

        // Dialogs / option panes
        d.put("OptionPane.background", card);
        d.put("OptionPane.messageForeground", fg);
        d.put("OptionPane.foreground", fg);
        d.put("OptionPane.messageFont", new FontUIResource(FONT_REGULAR));
        d.put("OptionPane.buttonFont", new FontUIResource(FONT_BOLD));
        d.put("OptionPane.border", new BorderUIResource(new EmptyBorder(18, 20, 14, 20)));
        d.put("OptionPane.messageAreaBorder", new BorderUIResource(new EmptyBorder(0, 0, 14, 0)));
        d.put("OptionPane.buttonAreaBorder", new BorderUIResource(new EmptyBorder(6, 0, 0, 0)));
        d.put("OptionPane.errorIcon", new DialogIcon(ERROR, '!'));
        d.put("OptionPane.warningIcon", new DialogIcon(WARNING, '!'));
        d.put("OptionPane.informationIcon", new DialogIcon(ACCENT, 'i'));
        d.put("OptionPane.questionIcon", new DialogIcon(ACCENT, '?'));
        d.put("RootPane.background", card);
        d.put("RootPane.frameBorder", null);
        d.put("RootPane.dialogBorder", new BorderUIResource(new LineBorderRes(BORDER)));
        d.put("RootPane.errorDialogBorder", new BorderUIResource(new LineBorderRes(ERROR)));
        d.put("RootPane.warningDialogBorder", new BorderUIResource(new LineBorderRes(WARNING)));
        d.put("RootPane.questionDialogBorder", new BorderUIResource(new LineBorderRes(ACCENT)));
        d.put("RootPane.informationDialogBorder", new BorderUIResource(new LineBorderRes(ACCENT)));

        // File chooser / trees
        d.put("FileChooser.listViewBackground", card);
        d.put("Tree.background", card);
        d.put("Tree.foreground", fg);
        d.put("Tree.textBackground", card);
        d.put("Tree.textForeground", fg);
        d.put("Tree.selectionBackground", sel);
        d.put("Tree.selectionForeground", fg);
        d.put("Tree.selectionBorderColor", sel);
        d.put("Tree.hash", res(BORDER));
    }

    private static ColorUIResource res(Color c) {
        return new ColorUIResource(c);
    }

    // =====================================================================================
    // Global hooks
    // =====================================================================================

    /** Repaints the previous and new focus owner so {@link RoundBorder} can draw an accent focus ring. */
    private static void installFocusRepaintHook() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("permanentFocusOwner", evt -> {
            repaintFocusTarget(evt.getOldValue());
            repaintFocusTarget(evt.getNewValue());
        });
    }

    private static void repaintFocusTarget(Object o) {
        if (o instanceof JComponent) {
            JComponent c = (JComponent) o;
            c.repaint();
            Container p = c.getParent();
            // Combo box editors and spinner fields: repaint the outer component that owns the border
            for (int i = 0; i < 3 && p != null; i++, p = p.getParent()) {
                if (p instanceof JComboBox || p instanceof JSpinner) {
                    ((JComponent) p).repaint();
                }
            }
        }
    }

    /**
     * Gives every JTable consistent dark styling the first time it is shown (row height,
     * grid, header renderer, striping). Screens may still override renderers per column.
     */
    private static void installTableAutoStyling() {
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e.getID() == java.awt.event.ContainerEvent.COMPONENT_ADDED) {
                Component child = ((java.awt.event.ContainerEvent) e).getChild();
                if (child instanceof JTable) styleTable((JTable) child);
            }
        }, AWTEvent.CONTAINER_EVENT_MASK);
    }

    /** Applies the dark table spec. Safe to call repeatedly. */
    public static void styleTable(JTable t) {
        if (Boolean.TRUE.equals(t.getClientProperty("darkStyled"))) return;
        t.putClientProperty("darkStyled", Boolean.TRUE);
        if (t.getRowHeight() < 30) t.setRowHeight(34);
        t.setShowVerticalLines(false);
        t.setShowHorizontalLines(true);
        t.setGridColor(BORDER_SUBTLE);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setBackground(BG_CARD);
        t.setForeground(TEXT_PRIMARY);
        t.setSelectionBackground(BG_SELECTED);
        t.setSelectionForeground(TEXT_PRIMARY);
        t.setFillsViewportHeight(true);
        if (t.getFont() == null || t.getFont() instanceof FontUIResource || "Segoe UI".equals(t.getFont().getFamily())) {
            t.setFont(FONT_REGULAR);
        }
        // Default renderer (only replaces the stock renderer; custom ones are left alone)
        for (Class<?> cls : new Class<?>[]{Object.class, String.class, Number.class, Integer.class, Double.class}) {
            if (t.getDefaultRenderer(cls) == null || t.getDefaultRenderer(cls).getClass().getName().startsWith("javax.swing")) {
                if (cls == Object.class || cls == String.class) t.setDefaultRenderer(cls, new CellRenderer(SwingConstants.LEFT));
                else t.setDefaultRenderer(cls, new CellRenderer(SwingConstants.RIGHT));
            }
        }
        JTableHeader h = t.getTableHeader();
        if (h != null) {
            h.setBackground(BG_ELEVATED);
            h.setForeground(TEXT_SECONDARY);
            h.setFont(FONT_CAPTION);
            h.setReorderingAllowed(h.getReorderingAllowed());
            h.setDefaultRenderer(new HeaderRenderer());
            h.setPreferredSize(new Dimension(h.getPreferredSize().width, 36));
        }
        // Row hover highlight
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int r = t.rowAtPoint(e.getPoint());
                Object old = t.getClientProperty("hoverRow");
                if (old == null || (Integer) old != r) {
                    t.putClientProperty("hoverRow", r);
                    t.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                t.putClientProperty("hoverRow", -1);
                t.repaint();
            }
        };
        t.addMouseMotionListener(hover);
        t.addMouseListener(hover);
        Container p = t.getParent() != null ? t.getParent().getParent() : null;
        if (p instanceof JScrollPane) {
            JScrollPane sp = (JScrollPane) p;
            sp.getViewport().setBackground(BG_CARD);
        }
        installColumnPolish(t);
    }

    // -------------------------------------------------------------------------------------
    // Column polish: content-aware widths + status badges
    // -------------------------------------------------------------------------------------

    /**
     * Keeps every table readable without per-screen tuning:
     * <ul>
     *   <li>Column widths follow their content (header + sampled rows) so short codes don't get
     *       huge columns while timestamps get truncated.</li>
     *   <li>When the content is wider than the viewport the table switches to horizontal
     *       scrolling instead of eliding every cell with "...".</li>
     *   <li>Columns named Status / Result render as tinted status pills.</li>
     * </ul>
     * Opt out per table with {@code table.putClientProperty("autoColumns", Boolean.FALSE)}.
     */
    private static void installColumnPolish(JTable t) {
        Runnable apply = () -> SwingUtilities.invokeLater(() -> polishColumns(t));
        t.addPropertyChangeListener("model", e -> {
            if (e.getNewValue() instanceof javax.swing.table.TableModel) {
                ((javax.swing.table.TableModel) e.getNewValue()).addTableModelListener(ev -> apply.run());
            }
            apply.run();
        });
        t.getModel().addTableModelListener(e -> apply.run());
        t.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                fitToViewport(t);
            }
        });
        t.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && t.isShowing()) apply.run();
        });
        apply.run();
    }

    private static boolean isStatusHeader(Object header) {
        if (header == null) return false;
        String h = header.toString().trim().toUpperCase();
        return h.equals("STATUS") || h.equals("RESULT") || h.equals("STATE") || h.equals("PAYMENT STATUS");
    }

    static void polishColumns(JTable t) {
        if (Boolean.FALSE.equals(t.getClientProperty("autoColumns"))) return;
        javax.swing.table.TableColumnModel cm = t.getColumnModel();
        int n = cm.getColumnCount();
        if (n == 0) return;
        FontMetrics bodyFm = t.getFontMetrics(t.getFont());
        FontMetrics headFm = t.getFontMetrics(FONT_CAPTION);
        int rows = Math.min(t.getRowCount(), 250);
        int[] pref = new int[n];
        for (int c = 0; c < n; c++) {
            javax.swing.table.TableColumn col = cm.getColumn(c);
            Object hv = col.getHeaderValue();
            if (isStatusHeader(hv) && (col.getCellRenderer() == null)) {
                col.setCellRenderer(new com.smartparking.ui.components.Badge.Renderer());
            }
            int w = headFm.stringWidth(hv == null ? "" : hv.toString().toUpperCase()) + 28;
            for (int r = 0; r < rows; r++) {
                Object v = t.getValueAt(r, c);
                if (v == null) continue;
                String s = v instanceof Double || v instanceof Float ? String.format("%,.2f", ((Number) v).doubleValue()) : v.toString();
                if (s.length() > 120) s = s.substring(0, 120);
                w = Math.max(w, bodyFm.stringWidth(s) + (col.getCellRenderer() instanceof com.smartparking.ui.components.Badge.Renderer ? 44 : 26));
            }
            pref[c] = Math.max(56, Math.min(w, 300));
        }
        for (int c = 0; c < n; c++) {
            javax.swing.table.TableColumn col = cm.getColumn(c);
            col.setMinWidth(Math.min(pref[c], 64));
            col.setPreferredWidth(pref[c]);
        }
        t.putClientProperty("prefTotal", java.util.Arrays.stream(pref).sum());
        fitToViewport(t);
    }

    private static void fitToViewport(JTable t) {
        if (Boolean.FALSE.equals(t.getClientProperty("autoColumns"))) return;
        Object total = t.getClientProperty("prefTotal");
        if (!(total instanceof Integer)) return;
        Container vp = t.getParent();
        if (!(vp instanceof JViewport)) return;
        int avail = vp.getWidth();
        if (avail <= 0) return;
        // up to 25% over: let columns compress a little (keeps every column visible); beyond that, scroll
        int mode = ((Integer) total) > avail * 1.25 ? JTable.AUTO_RESIZE_OFF : JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS;
        if (t.getAutoResizeMode() != mode) {
            t.setAutoResizeMode(mode);
            t.revalidate();
        }
    }

    /** Standard dark cell renderer with hover + subtle zebra striping. */
    public static class CellRenderer extends DefaultTableCellRenderer {
        public CellRenderer(int align) {
            setHorizontalAlignment(align);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            if (value instanceof Double || value instanceof Float) {
                setText(String.format("%,.2f", ((Number) value).doubleValue()));
            }
            setBorder(new EmptyBorder(0, 10, 0, 10));
            setFont(table.getFont());
            if (isSelected) {
                setBackground(BG_SELECTED);
                setForeground(TEXT_PRIMARY);
            } else {
                Object hr = table.getClientProperty("hoverRow");
                boolean hover = hr instanceof Integer && (Integer) hr == row;
                setBackground(hover ? BG_HOVER : (row % 2 == 0 ? BG_CARD : BG_SURFACE));
                setForeground(TEXT_PRIMARY);
            }
            return this;
        }
    }

    /** Flat uppercase-caption header renderer. */
    public static class HeaderRenderer extends DefaultTableCellRenderer {
        public HeaderRenderer() {
            setHorizontalAlignment(SwingConstants.LEFT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value == null ? "" : value.toString().toUpperCase(), false, false, row, column);
            setFont(FONT_CAPTION);
            setForeground(TEXT_SECONDARY);
            setBackground(BG_ELEVATED);
            setBorder(BorderFactory.createCompoundBorder(
                    new MatteBorder(0, 0, 1, 0, BORDER), new EmptyBorder(0, 10, 0, 10)));
            return this;
        }
    }

    /** Kept for API symmetry; focus rings are painted by {@link RoundBorder} automatically. */
    public static void installFocusRing(JComponent c) {
        // no-op: handled globally by installFocusRepaintHook()
    }

    // =====================================================================================
    // Borders & icons
    // =====================================================================================

    /** Anti-aliased rounded 1px border; turns accent-coloured when its component (or a child) has focus. */
    public static class RoundBorder extends AbstractBorder {
        private final Color color;
        private final int radius;

        public RoundBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean focused = isFocusedInput(c);
            g2.setColor(focused ? ACCENT : color);
            g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1f, h - 1f, radius, radius));
            if (focused) {
                g2.setColor(alpha(ACCENT, 60));
                g2.draw(new RoundRectangle2D.Float(x + 1.5f, y + 1.5f, w - 3f, h - 3f, radius - 2, radius - 2));
            }
            g2.dispose();
        }

        private static boolean isFocusedInput(Component c) {
            Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
            if (f == null || !c.isEnabled()) return false;
            boolean inputLike = c instanceof JTextComponent || c instanceof JComboBox || c instanceof JSpinner;
            if (!inputLike) return false;
            if (c instanceof JTextComponent && !((JTextComponent) c).isEditable()) return false;
            return f == c || SwingUtilities.isDescendingFrom(f, c);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(1, 1, 1, 1);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(1, 1, 1, 1);
            return insets;
        }
    }

    private static class LineBorderRes extends AbstractBorder {
        private final Color color;

        LineBorderRes(Color color) {
            this.color = color;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            g.setColor(color);
            g.drawRect(x, y, w - 1, h - 1);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(1, 1, 1, 1);
        }
    }

    /** Vector check box / radio icon. */
    static class CheckIcon implements Icon, UIResource {
        private final boolean radio;

        CheckIcon(boolean radio) {
            this.radio = radio;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel m = b.getModel();
            boolean on = m.isSelected();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            int s = 16;
            Color fill = !b.isEnabled() ? BG_SURFACE : on ? ACCENT : (m.isRollover() ? BG_HOVER : BG_INPUT);
            Color stroke = !b.isEnabled() ? BORDER_SUBTLE : on ? ACCENT : (m.isRollover() ? BORDER_STRONG : BORDER_STRONG);
            if (radio) {
                g2.setColor(fill);
                g2.fillOval(0, 0, s, s);
                g2.setColor(stroke);
                g2.drawOval(0, 0, s - 1, s - 1);
                if (on) {
                    g2.setColor(Color.WHITE);
                    g2.fillOval(5, 5, 6, 6);
                }
            } else {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, s, s, 5, 5);
                g2.setColor(stroke);
                g2.drawRoundRect(0, 0, s - 1, s - 1, 5, 5);
                if (on) {
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawPolyline(new int[]{4, 7, 12}, new int[]{8, 11, 5}, 3);
                }
            }
            if (b.isFocusOwner()) {
                g2.setColor(alpha(ACCENT, 90));
                g2.setStroke(new BasicStroke(2f));
                if (radio) g2.drawOval(-2, -2, s + 3, s + 3);
                else g2.drawRoundRect(-2, -2, s + 3, s + 3, 7, 7);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }

    /** Circular dialog icon with a glyph. */
    static class DialogIcon implements Icon, UIResource {
        private final Color color;
        private final char glyph;

        DialogIcon(Color color, char glyph) {
            this.color = color;
            this.glyph = glyph;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(tint(color));
            g2.fillOval(x, y, 36, 36);
            g2.setColor(tintBorder(color));
            g2.drawOval(x, y, 35, 35);
            g2.setColor(color);
            g2.setFont(font(Font.BOLD, 18));
            FontMetrics fm = g2.getFontMetrics();
            String s = String.valueOf(glyph);
            g2.drawString(s, x + 18 - fm.stringWidth(s) / 2f, y + 18 + fm.getAscent() / 2f - 3);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 36;
        }

        @Override
        public int getIconHeight() {
            return 36;
        }
    }

    /** Small chevron icon used by combo boxes and spinners. */
    static class ChevronIcon implements Icon {
        private final boolean up;
        private final int size;

        ChevronIcon(boolean up, int size) {
            this.up = up;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(c.isEnabled() ? TEXT_SECONDARY : TEXT_DISABLED);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int w = size, h = size / 2;
            int ox = x, oy = y + (size - h) / 2;
            if (up) g2.drawPolyline(new int[]{ox, ox + w / 2, ox + w}, new int[]{oy + h, oy, oy + h}, 3);
            else g2.drawPolyline(new int[]{ox, ox + w / 2, ox + w}, new int[]{oy, oy + h, oy}, 3);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    // =====================================================================================
    // UI delegates
    // =====================================================================================

    /** Flat rounded buttons. Buttons that set contentAreaFilled(false) paint themselves. */
    public static class FlatButtonUI extends BasicButtonUI {
        public static ComponentUI createUI(JComponent c) {
            return new FlatButtonUI();
        }

        @Override
        protected void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            b.setRolloverEnabled(true);
            b.setOpaque(false);
            if (b.getFont() instanceof UIResource) b.setFont(new FontUIResource(FONT_BOLD));
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            if (b.isContentAreaFilled()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                ButtonModel m = b.getModel();
                Color base = b.getBackground();
                boolean custom = !(base instanceof UIResource);
                boolean selected = m.isSelected() && b instanceof JToggleButton;
                Color fill;
                if (!b.isEnabled()) fill = BG_SURFACE;
                else if (selected) fill = ACCENT_SOFT;
                else if (m.isPressed()) fill = custom ? base.darker() : BG_HOVER;
                else if (m.isRollover()) fill = custom ? brighten(base) : BG_HOVER;
                else fill = custom ? base : BG_ELEVATED;
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), RADIUS, RADIUS);
                if (b.isBorderPainted() || !custom) {
                    g2.setColor(selected ? ACCENT_BORDER : (custom ? alpha(Color.WHITE, 18) : BORDER));
                    g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, RADIUS, RADIUS);
                }
                if (b.isFocusOwner() && b.isFocusPainted()) {
                    g2.setColor(alpha(ACCENT, 140));
                    g2.drawRoundRect(1, 1, c.getWidth() - 3, c.getHeight() - 3, RADIUS - 2, RADIUS - 2);
                }
                g2.dispose();
            }
            if (!b.isEnabled() && b.getForeground() != null) {
                // BasicButtonUI paints disabled text with an engraved effect; keep it flat instead.
            }
            super.paint(g, c);
        }

        @Override
        protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            FontMetrics fm = g.getFontMetrics(b.getFont());
            int idx = b.getDisplayedMnemonicIndex();
            Color fg = b.getForeground();
            if (b instanceof JToggleButton && b.getModel().isSelected() && fg instanceof UIResource) fg = ACCENT_TEXT;
            g.setColor(b.isEnabled() ? fg : TEXT_DISABLED);
            BasicGraphicsUtils.drawStringUnderlineCharAt(b, g2, text, idx, textRect.x, textRect.y + fm.getAscent());
        }

        @Override
        protected void paintFocus(Graphics g, AbstractButton b, Rectangle viewRect, Rectangle textRect, Rectangle iconRect) {
            // painted in paint()
        }

        @Override
        protected void paintButtonPressed(Graphics g, AbstractButton b) {
            // painted in paint()
        }

        private static Color brighten(Color c) {
            return blend(Color.WHITE, c, 0.08);
        }
    }

    /** Thin rounded scroll bar without arrow buttons. */
    public static class FlatScrollBarUI extends BasicScrollBarUI {
        private boolean hover;

        public static ComponentUI createUI(JComponent c) {
            return new FlatScrollBarUI();
        }

        @Override
        protected void installDefaults() {
            super.installDefaults();
            scrollbar.setOpaque(true);
            scrollbar.setBackground(BG_CARD);
        }

        @Override
        protected void installListeners() {
            super.installListeners();
            scrollbar.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    scrollbar.repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    scrollbar.repaint();
                }
            });
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zero();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zero();
        }

        private static JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            Color bg = c.getParent() != null && c.getParent().getBackground() != null ? c.getParent().getBackground() : BG_CARD;
            g.setColor(bg);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hover || isDragging ? new Color(71, 85, 105) : BORDER_STRONG);
            int pad = 2;
            boolean vertical = scrollbar.getOrientation() == JScrollBar.VERTICAL;
            int arc = vertical ? r.width - pad * 2 : r.height - pad * 2;
            g2.fillRoundRect(r.x + pad, r.y + pad, r.width - pad * 2, r.height - pad * 2, arc, arc);
            g2.dispose();
        }

        @Override
        protected Dimension getMinimumThumbSize() {
            return new Dimension(10, 32);
        }
    }

    /** Underline-style tabs. */
    public static class FlatTabbedPaneUI extends BasicTabbedPaneUI {
        private int hoverTab = -1;

        public static ComponentUI createUI(JComponent c) {
            return new FlatTabbedPaneUI();
        }

        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabPane.setOpaque(false);
            tabPane.setFont(FONT_BOLD);
        }

        @Override
        protected void installListeners() {
            super.installListeners();
            tabPane.addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int t = tabForCoordinate(tabPane, e.getX(), e.getY());
                    if (t != hoverTab) {
                        hoverTab = t;
                        tabPane.repaint();
                    }
                }
            });
            tabPane.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    hoverTab = -1;
                    tabPane.repaint();
                }
            });
        }

        @Override
        protected int calculateTabHeight(int placement, int idx, int fontHeight) {
            return Math.max(super.calculateTabHeight(placement, idx, fontHeight), 38);
        }

        @Override
        protected int calculateTabWidth(int placement, int idx, FontMetrics m) {
            return super.calculateTabWidth(placement, idx, m) + 6;
        }

        @Override
        protected void paintTabArea(Graphics g, int placement, int selectedIndex) {
            if (placement == TOP) {
                Rectangle b = tabPane.getBounds();
                int h = calculateTabAreaHeight(placement, runCount, maxTabHeight);
                g.setColor(BORDER_SUBTLE);
                g.fillRect(0, h - 1, b.width, 1);
            }
            super.paintTabArea(g, placement, selectedIndex);
        }

        @Override
        protected void paintTabBackground(Graphics g, int placement, int idx, int x, int y, int w, int h, boolean sel) {
            if (idx == hoverTab && !sel) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_ELEVATED);
                g2.fillRoundRect(x + 2, y + 4, w - 4, h - 10, RADIUS_SM, RADIUS_SM);
                g2.dispose();
            }
        }

        @Override
        protected void paintTabBorder(Graphics g, int placement, int idx, int x, int y, int w, int h, boolean sel) {
            if (sel) {
                g.setColor(ACCENT);
                if (placement == LEFT) g.fillRect(x, y + 4, 2, h - 8);
                else g.fillRect(x + 6, y + h - 2, w - 12, 2);
            }
        }

        @Override
        protected void paintContentBorder(Graphics g, int placement, int selectedIndex) {
            // borderless content
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rects, int idx, Rectangle iconRect, Rectangle textRect, boolean sel) {
        }

        @Override
        protected void paintText(Graphics g, int placement, Font font, FontMetrics metrics, int idx, String title, Rectangle textRect, boolean sel) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setFont(font);
            g2.setColor(!tabPane.isEnabledAt(idx) ? TEXT_DISABLED : sel ? TEXT_PRIMARY : (idx == hoverTab ? TEXT_PRIMARY : TEXT_SECONDARY));
            g2.drawString(title, textRect.x, textRect.y + metrics.getAscent());
        }

        @Override
        protected Insets getContentBorderInsets(int placement) {
            return new Insets(12, 0, 0, 0);
        }
    }

    /** Combo box with a flat chevron button and dark popup. */
    public static class FlatComboBoxUI extends BasicComboBoxUI {
        public static ComponentUI createUI(JComponent c) {
            return new FlatComboBoxUI();
        }

        @Override
        protected void installDefaults() {
            super.installDefaults();
            comboBox.setOpaque(true);
            if (comboBox.getFont() instanceof UIResource) comboBox.setFont(FONT_REGULAR);
        }

        @Override
        protected JButton createArrowButton() {
            JButton b = new JButton(new ChevronIcon(false, 10));
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusable(false);
            b.setBorder(new EmptyBorder(0, 8, 0, 10));
            b.setOpaque(false);
            return b;
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            g.setColor(comboBox.isEnabled() ? comboBox.getBackground() : BG_SURFACE);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
            // never draw the "selected" highlight in the closed box
            super.paintCurrentValue(g, bounds, false);
        }

        @Override
        protected ListCellRenderer<Object> createRenderer() {
            return new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, false);
                    setBorder(new EmptyBorder(6, 10, 6, 10));
                    setBackground(isSelected ? BG_HOVER : (index == -1 ? comboBox.getBackground() : BG_ELEVATED));
                    setForeground(comboBox.isEnabled() ? TEXT_PRIMARY : TEXT_DISABLED);
                    return this;
                }
            };
        }

        @Override
        protected ComboPopup createPopup() {
            BasicComboPopup p = new BasicComboPopup(comboBox) {
                @Override
                protected void configurePopup() {
                    super.configurePopup();
                    setBorder(new LineBorderRes(BORDER_STRONG));
                }
            };
            p.getList().setBackground(BG_ELEVATED);
            p.getList().setSelectionBackground(BG_HOVER);
            p.getList().setSelectionForeground(TEXT_PRIMARY);
            return p;
        }

        @Override
        protected Insets getInsets() {
            return new Insets(5, 8, 5, 4);
        }
    }

    /** Spinner with flat chevron buttons. */
    public static class FlatSpinnerUI extends BasicSpinnerUI {
        public static ComponentUI createUI(JComponent c) {
            return new FlatSpinnerUI();
        }

        @Override
        protected Component createNextButton() {
            Component b = button(true);
            installNextButtonListeners(b);
            return b;
        }

        @Override
        protected Component createPreviousButton() {
            Component b = button(false);
            installPreviousButtonListeners(b);
            return b;
        }

        private Component button(boolean up) {
            JButton b = new JButton(new ChevronIcon(up, 8));
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusable(false);
            b.setBorder(new EmptyBorder(1, 6, 1, 6));
            b.setOpaque(false);
            return b;
        }

        @Override
        protected JComponent createEditor() {
            JComponent ed = super.createEditor();
            styleEditor(ed);
            return ed;
        }

        @Override
        protected void replaceEditor(JComponent oldEditor, JComponent newEditor) {
            super.replaceEditor(oldEditor, newEditor);
            styleEditor(newEditor);
        }

        private void styleEditor(JComponent ed) {
            ed.setOpaque(false);
            if (ed instanceof JSpinner.DefaultEditor) {
                JFormattedTextField tf = ((JSpinner.DefaultEditor) ed).getTextField();
                tf.setBorder(new EmptyBorder(5, 9, 5, 4));
                tf.setBackground(BG_INPUT);
                tf.setForeground(TEXT_PRIMARY);
                tf.setCaretColor(ACCENT_TEXT);
                tf.setOpaque(false);
            }
        }

        @Override
        public void update(Graphics g, JComponent c) {
            g.setColor(c.isEnabled() ? BG_INPUT : BG_SURFACE);
            g.fillRect(0, 0, c.getWidth(), c.getHeight());
            paint(g, c);
        }
    }

    // =====================================================================================
    // Metal theme
    // =====================================================================================
    static class Theme extends DefaultMetalTheme {
        @Override
        public String getName() {
            return "SmartParkingDark";
        }

        @Override
        protected ColorUIResource getPrimary1() {
            return res(ACCENT);
        }

        @Override
        protected ColorUIResource getPrimary2() {
            return res(ACCENT_BORDER);
        }

        @Override
        protected ColorUIResource getPrimary3() {
            return res(BG_SELECTED);
        }

        @Override
        protected ColorUIResource getSecondary1() {
            return res(BORDER_STRONG);
        }

        @Override
        protected ColorUIResource getSecondary2() {
            return res(BORDER);
        }

        @Override
        protected ColorUIResource getSecondary3() {
            return res(BG_CANVAS);
        }

        @Override
        protected ColorUIResource getBlack() {
            return res(TEXT_PRIMARY);
        }

        @Override
        protected ColorUIResource getWhite() {
            return res(BG_CARD);
        }

        @Override
        public ColorUIResource getControlTextColor() {
            return res(TEXT_PRIMARY);
        }

        @Override
        public ColorUIResource getSystemTextColor() {
            return res(TEXT_PRIMARY);
        }

        @Override
        public ColorUIResource getUserTextColor() {
            return res(TEXT_PRIMARY);
        }

        @Override
        public ColorUIResource getInactiveSystemTextColor() {
            return res(TEXT_TERTIARY);
        }

        @Override
        public ColorUIResource getMenuForeground() {
            return res(TEXT_PRIMARY);
        }

        @Override
        public ColorUIResource getMenuBackground() {
            return res(BG_ELEVATED);
        }

        @Override
        public FontUIResource getControlTextFont() {
            return new FontUIResource(FONT_REGULAR);
        }

        @Override
        public FontUIResource getSystemTextFont() {
            return new FontUIResource(FONT_REGULAR);
        }

        @Override
        public FontUIResource getUserTextFont() {
            return new FontUIResource(FONT_REGULAR);
        }

        @Override
        public FontUIResource getMenuTextFont() {
            return new FontUIResource(FONT_REGULAR);
        }

        @Override
        public FontUIResource getWindowTitleFont() {
            return new FontUIResource(FONT_BOLD);
        }

        @Override
        public FontUIResource getSubTextFont() {
            return new FontUIResource(FONT_SMALL);
        }
    }
}
