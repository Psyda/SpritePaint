package com.spritepaint;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

@ConfigGroup("spritepaint")
public interface SpritePaintConfig extends Config
{
    @ConfigSection(
        name = "General",
        description = "General plugin settings",
        position = 0
    )
    String generalSection = "general";

    @ConfigSection(
        name = "Editor",
        description = "Pixel editor settings",
        position = 1
    )
    String editorSection = "editor";

    // --- General ---

    @ConfigItem(
        keyName = "editModeHotkey",
        name = "Edit Mode Hotkey",
        description = "Hotkey to toggle edit mode on/off",
        section = generalSection,
        position = 0
    )
    default Keybind editModeHotkey()
    {
        return new Keybind(KeyEvent.VK_F8, 0);
    }

    @ConfigItem(
        keyName = "highlightColor",
        name = "Highlight Color",
        description = "Color used to highlight hoverable sprites in edit mode",
        section = generalSection,
        position = 1
    )
    default Color highlightColor()
    {
        return new Color(0, 255, 200, 100);
    }

    @ConfigItem(
        keyName = "showOverridesInGame",
        name = "Show Overrides In Game",
        description = "Render your custom sprite edits in the game world",
        section = generalSection,
        position = 2
    )
    default boolean showOverridesInGame()
    {
        return true;
    }

    // --- Editor ---

    @ConfigItem(
        keyName = "showGrid",
        name = "Show Grid",
        description = "Show pixel grid lines in the editor",
        section = editorSection,
        position = 0
    )
    default boolean showGrid()
    {
        return true;
    }

    @Range(min = 4, max = 20)
    @ConfigItem(
        keyName = "zoomLevel",
        name = "Default Zoom",
        description = "Default zoom level for the pixel editor (pixels per cell)",
        section = editorSection,
        position = 1
    )
    default int zoomLevel()
    {
        return 10;
    }

    @Range(min = 1, max = 30)
    @ConfigItem(
        keyName = "autoSaveSeconds",
        name = "Auto-save Interval",
        description = "Seconds between auto-saves (0 = disabled)",
        section = editorSection,
        position = 2
    )
    default int autoSaveSeconds()
    {
        return 3;
    }
}
