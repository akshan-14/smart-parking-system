package com.smartparking.ui.components;

import javax.swing.*;
import java.awt.*;

/** Panel that always matches the viewport width inside a JScrollPane (vertical scrolling only). */
public class ScrollablePanel extends JPanel implements Scrollable {
    public ScrollablePanel(LayoutManager layout) {
        super(layout);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 18;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(visibleRect.height - 40, 40);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        Container p = getParent();
        return p instanceof JViewport && p.getHeight() > getPreferredSize().height;
    }
}
