package com.spritepaint;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Represents a detected sprite target in the game UI.
 * This is what gets identified when hovering in edit mode.
 */
public class SpriteTarget
{
    public enum TargetType
    {
        ITEM,       // An item icon (inventory, bank, equipment, etc.)
        SPRITE,     // A UI sprite (button, icon, border element)
        WIDGET      // A widget with no clear sprite/item ID (fallback)
    }

    private final TargetType type;
    private final int spriteId;
    private final int itemId;
    private final int itemQuantity;
    private final int widgetGroupId;
    private final int widgetChildId;
    private final Rectangle bounds;
    private final BufferedImage originalImage;

    private SpriteTarget(TargetType type, int spriteId, int itemId, int itemQuantity,
                         int widgetGroupId, int widgetChildId, Rectangle bounds,
                         BufferedImage originalImage)
    {
        this.type = type;
        this.spriteId = spriteId;
        this.itemId = itemId;
        this.itemQuantity = itemQuantity;
        this.widgetGroupId = widgetGroupId;
        this.widgetChildId = widgetChildId;
        this.bounds = bounds;
        this.originalImage = originalImage;
    }

    public static SpriteTarget forItem(int itemId, int quantity, int groupId, int childId,
                                       Rectangle bounds, BufferedImage image)
    {
        return new SpriteTarget(TargetType.ITEM, -1, itemId, quantity,
            groupId, childId, bounds, image);
    }

    public static SpriteTarget forSprite(int spriteId, int groupId, int childId,
                                         Rectangle bounds, BufferedImage image)
    {
        return new SpriteTarget(TargetType.SPRITE, spriteId, -1, 0,
            groupId, childId, bounds, image);
    }

    public static SpriteTarget forWidget(int groupId, int childId,
                                         Rectangle bounds, BufferedImage image)
    {
        return new SpriteTarget(TargetType.WIDGET, -1, -1, 0,
            groupId, childId, bounds, image);
    }

    /**
     * Returns a unique storage key for this sprite target.
     * Used as the filename (without extension) for persistence.
     */
    public String getStorageKey()
    {
        switch (type)
        {
            case ITEM:
                return "item_" + itemId;
            case SPRITE:
                return "sprite_" + spriteId;
            case WIDGET:
                return "widget_" + widgetGroupId + "_" + widgetChildId;
            default:
                return "unknown_" + System.currentTimeMillis();
        }
    }

    /**
     * Human-readable name for display in the editor title bar.
     */
    public String getDisplayName()
    {
        switch (type)
        {
            case ITEM:
                return "Item #" + itemId;
            case SPRITE:
                return "Sprite #" + spriteId;
            case WIDGET:
                return "Widget [" + widgetGroupId + ":" + widgetChildId + "]";
            default:
                return "Unknown";
        }
    }

    public TargetType getType()
    {
        return type;
    }

    public int getSpriteId()
    {
        return spriteId;
    }

    public int getItemId()
    {
        return itemId;
    }

    public int getItemQuantity()
    {
        return itemQuantity;
    }

    public int getWidgetGroupId()
    {
        return widgetGroupId;
    }

    public int getWidgetChildId()
    {
        return widgetChildId;
    }

    public Rectangle getBounds()
    {
        return bounds;
    }

    public BufferedImage getOriginalImage()
    {
        return originalImage;
    }
}
