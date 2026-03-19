package com.spritepaint;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Handles two responsibilities:
 * 1. In edit mode: highlights the widget under the cursor so users can see what's selectable.
 * 2. Always (when enabled): renders sprite overrides on top of original widgets,
 *    covering them with the user's edited sprites.
 */
@Slf4j
public class SpritePaintOverlay extends Overlay
{
    private final Client client;
    private final SpritePaintConfig config;
    private final SpriteStore store;
    private final ItemManager itemManager;

    // Edit mode state
    private boolean editMode = false;
    private SpriteTarget hoveredTarget = null;

    // Known interface group IDs to scan for editable widgets.
    // This covers the most common interfaces. Can be expanded.
    private static final int[] SCAN_GROUPS = {
        149,  // Inventory (resizable)
        388,  // Inventory (fixed mode)
        387,  // Equipment
        541,  // Prayer book
        218,  // Standard spellbook
        593,  // Clan tab / chat channel
        12,   // Bank
        15,   // Bank inventory
        13,   // Bank tab bar
        320,  // Skills tab
        629,  // Combat options (resizable)
        160,  // Quest list
        399,  // Music tab
        182,  // Logout panel
        261,  // Friends list
        429,  // Emotes
        216,  // Minigame tab
        69,   // Deposit box
        270,  // Shop
    };

    public SpritePaintOverlay(Client client, SpritePaintConfig config,
                              SpriteStore store, ItemManager itemManager)
    {
        this.client = client;
        this.config = config;
        this.store = store;
        this.itemManager = itemManager;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
    }

    public void setEditMode(boolean enabled)
    {
        this.editMode = enabled;
        if (!enabled)
        {
            hoveredTarget = null;
        }
    }

    public boolean isEditMode()
    {
        return editMode;
    }

    /**
     * Get the currently hovered sprite target (only valid in edit mode).
     */
    public SpriteTarget getHoveredTarget()
    {
        return hoveredTarget;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return null;
        }

        // Always render sprite overrides when enabled
        if (config.showOverridesInGame())
        {
            renderOverrides(graphics);
        }

        // Edit mode: detect and highlight widgets under cursor
        if (editMode)
        {
            Point mouse = client.getMouseCanvasPosition() != null
                ? new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY())
                : null;

            if (mouse != null)
            {
                hoveredTarget = findTargetAtPoint(mouse);
                if (hoveredTarget != null)
                {
                    drawHighlight(graphics, hoveredTarget);
                }
            }

            // Draw edit mode indicator
            drawEditModeIndicator(graphics);
        }

        return null;
    }

    // --- Override rendering ---

    /**
     * Scan visible widgets and draw custom sprites over any that have saved overrides.
     */
    private void renderOverrides(Graphics2D g)
    {
        if (store.getKeys().isEmpty()) return;

        for (int groupId : SCAN_GROUPS)
        {
            Widget root = client.getWidget(groupId, 0);
            if (root == null || root.isHidden()) continue;

            renderWidgetOverrides(g, root);
        }
    }

    private void renderWidgetOverrides(Graphics2D g, Widget widget)
    {
        if (widget == null || widget.isHidden()) return;

        // Check this widget for an override
        String key = getStorageKeyForWidget(widget);
        if (key != null)
        {
            BufferedImage override = store.getOverride(key);
            if (override != null)
            {
                Rectangle bounds = widget.getBounds();
                if (bounds != null && bounds.width > 0 && bounds.height > 0)
                {
                    // Draw override image covering the original
                    g.drawImage(override, bounds.x, bounds.y,
                        bounds.width, bounds.height, null);
                }
            }
        }

        // Recurse into children
        Widget[] staticChildren = widget.getStaticChildren();
        if (staticChildren != null)
        {
            for (Widget child : staticChildren)
            {
                renderWidgetOverrides(g, child);
            }
        }

        Widget[] dynamicChildren = widget.getDynamicChildren();
        if (dynamicChildren != null)
        {
            for (Widget child : dynamicChildren)
            {
                renderWidgetOverrides(g, child);
            }
        }

        Widget[] nestedChildren = widget.getNestedChildren();
        if (nestedChildren != null)
        {
            for (Widget child : nestedChildren)
            {
                renderWidgetOverrides(g, child);
            }
        }
    }

    // --- Edit mode: widget detection ---

    /**
     * Find the most specific (deepest) widget at the given screen point
     * that has an identifiable sprite or item.
     */
    private SpriteTarget findTargetAtPoint(Point point)
    {
        SpriteTarget best = null;

        for (int groupId : SCAN_GROUPS)
        {
            Widget root = client.getWidget(groupId, 0);
            if (root == null || root.isHidden()) continue;

            SpriteTarget found = findTargetInWidget(root, point, groupId);
            if (found != null)
            {
                // Prefer deepest (most specific) match
                best = found;
            }
        }

        return best;
    }

    private SpriteTarget findTargetInWidget(Widget widget, Point point, int groupId)
    {
        if (widget == null || widget.isHidden()) return null;

        SpriteTarget result = null;

        // Check children first (depth-first, deepest match wins)
        Widget[] dynamicChildren = widget.getDynamicChildren();
        if (dynamicChildren != null)
        {
            for (Widget child : dynamicChildren)
            {
                SpriteTarget found = findTargetInWidget(child, point, groupId);
                if (found != null) result = found;
            }
        }

        Widget[] staticChildren = widget.getStaticChildren();
        if (staticChildren != null)
        {
            for (Widget child : staticChildren)
            {
                SpriteTarget found = findTargetInWidget(child, point, groupId);
                if (found != null) result = found;
            }
        }

        Widget[] nestedChildren = widget.getNestedChildren();
        if (nestedChildren != null)
        {
            for (Widget child : nestedChildren)
            {
                SpriteTarget found = findTargetInWidget(child, point, groupId);
                if (found != null) result = found;
            }
        }

        // If no child matched, check this widget
        if (result == null)
        {
            Rectangle bounds = widget.getBounds();
            if (bounds != null && bounds.contains(point))
            {
                result = createTarget(widget, groupId);
            }
        }

        return result;
    }

    /**
     * Try to create a SpriteTarget from a widget. Returns null if the widget
     * isn't something we can meaningfully edit (e.g., a text-only widget).
     */
    private SpriteTarget createTarget(Widget widget, int groupId)
    {
        Rectangle bounds = widget.getBounds();
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) return null;

        int childId = widget.getIndex();
        int itemId = widget.getItemId();
        int spriteId = widget.getSpriteId();

        // Item widget (inventory slot, bank item, etc.)
        if (itemId > 0)
        {
            // NOTE: In some RuneLite versions, widget.getItemId() already returns
            // the real item ID. If items look wrong, try using (itemId - 1) here.
            int realItemId = itemId;
            int quantity = widget.getItemQuantity();

            try
            {
                BufferedImage img = itemManager.getImage(realItemId, quantity, false);
                if (img != null)
                {
                    return SpriteTarget.forItem(realItemId, quantity, groupId, childId, bounds, img);
                }
            }
            catch (Exception e)
            {
                log.debug("Failed to get item image for id {}", realItemId, e);
            }
        }

        // Sprite widget (UI icon, button, etc.)
        if (spriteId > 0)
        {
            try
            {
                // Capture the widget area from the game canvas as a fallback.
                // The SpriteManager may not directly expose all sprites,
                // so we screenshot the widget bounds from the canvas.
                BufferedImage img = captureWidgetArea(bounds);
                if (img != null)
                {
                    return SpriteTarget.forSprite(spriteId, groupId, childId, bounds, img);
                }
            }
            catch (Exception e)
            {
                log.debug("Failed to capture sprite {} area", spriteId, e);
            }
        }

        // Generic widget with visible content (has a model, or is graphical in nature)
        // Only capture if the widget type suggests it's graphical
        int type = widget.getType();
        if (type == WidgetType.GRAPHIC || type == WidgetType.MODEL)
        {
            BufferedImage img = captureWidgetArea(bounds);
            if (img != null)
            {
                return SpriteTarget.forWidget(groupId, childId, bounds, img);
            }
        }

        return null;
    }

    /**
     * Capture a region of the game canvas as a BufferedImage.
     * This is the fallback method for getting sprite images when
     * direct sprite access isn't available.
     */
    private BufferedImage captureWidgetArea(Rectangle bounds)
    {
        try
        {
            // Use the client's canvas to grab the area
            Canvas canvas = client.getCanvas();
            if (canvas == null) return null;

            // Create a robot to capture the screen region
            // Note: This captures what's actually rendered, including any overlays
            BufferedImage img = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();

            // Get the canvas screen location and capture from there
            Point screenLoc = canvas.getLocationOnScreen();
            Robot robot = new Robot();
            BufferedImage capture = robot.createScreenCapture(new Rectangle(
                screenLoc.x + bounds.x,
                screenLoc.y + bounds.y,
                bounds.width,
                bounds.height
            ));
            g.drawImage(capture, 0, 0, null);
            g.dispose();
            return img;
        }
        catch (Exception e)
        {
            log.debug("Screen capture failed for widget area", e);
            return null;
        }
    }

    // --- Drawing helpers ---

    private void drawHighlight(Graphics2D g, SpriteTarget target)
    {
        Rectangle bounds = target.getBounds();
        Color highlight = config.highlightColor();

        // Semi-transparent fill
        g.setColor(new Color(highlight.getRed(), highlight.getGreen(),
            highlight.getBlue(), 50));
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        // Border
        g.setColor(highlight);
        g.setStroke(new BasicStroke(2));
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        // Label
        String label = target.getDisplayName();
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        int labelX = bounds.x;
        int labelY = bounds.y - 4;
        if (labelY < 12) labelY = bounds.y + bounds.height + fm.getHeight() + 2;

        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(labelX - 2, labelY - fm.getAscent() - 1,
            fm.stringWidth(label) + 4, fm.getHeight() + 2);
        g.setColor(highlight);
        g.drawString(label, labelX, labelY);
    }

    private void drawEditModeIndicator(Graphics2D g)
    {
        String text = "[EDIT MODE] Click a sprite to edit";
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();

        int x = 10;
        int y = 30;

        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(x - 4, y - fm.getAscent() - 2,
            fm.stringWidth(text) + 8, fm.getHeight() + 4, 6, 6);

        g.setColor(new Color(0, 255, 200));
        g.drawString(text, x, y);
    }

    // --- Utility ---

    /**
     * Get the storage key for a widget, if applicable.
     * Matches the same logic used in SpriteTarget.getStorageKey().
     */
    private String getStorageKeyForWidget(Widget widget)
    {
        int itemId = widget.getItemId();
        if (itemId > 0)
        {
            return "item_" + itemId;
        }

        int spriteId = widget.getSpriteId();
        if (spriteId > 0)
        {
            return "sprite_" + spriteId;
        }

        return null;
    }
}
