package com.spritepaint;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * SpritePaint - An in-game pixel editor for OSRS sprites.
 *
 * Lets you hover over UI sprites in edit mode, click to open a pixel editor,
 * paint your changes, and have them persist across sessions. Think Animal Crossing
 * meets OSRS. Your edits are saved as PNGs in ~/.runelite/spritepaint/ and
 * rendered as overlays on top of the original sprites whenever visible.
 *
 * Workflow:
 * 1. Press F8 (configurable) to enter Edit Mode
 * 2. Hover over any UI sprite (inventory items, prayer icons, etc.)
 * 3. Click to capture and open in the pixel editor
 * 4. Paint, erase, fill, pick colors
 * 5. Save & Apply to see changes live in-game
 * 6. Edits persist across sessions automatically
 */
@Slf4j
@PluginDescriptor(
    name = "SpritePaint",
    description = "In-game pixel editor for UI sprites. Paint on your world, Animal Crossing style.",
    tags = {"sprite", "paint", "pixel", "editor", "art", "customize", "resource", "pack"}
)
public class SpritePaintPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private SpritePaintConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KeyManager keyManager;

    @Inject
    private MouseManager mouseManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ClientToolbar clientToolbar;

    private SpritePaintOverlay overlay;
    private SpriteStore store;
    private PixelEditor editor;
    private SpritePaintPanel panel;
    private NavigationButton navButton;

    // Input listeners
    private final KeyListener keyListener = new KeyListener()
    {
        @Override
        public void keyTyped(KeyEvent e) {}

        @Override
        public void keyPressed(KeyEvent e)
        {
            if (config.editModeHotkey().matches(e))
            {
                toggleEditMode();
                e.consume();
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {}
    };

    private final MouseListener mouseListener = new MouseListener()
    {
        @Override
        public MouseEvent mouseClicked(MouseEvent e)
        {
            return e;
        }

        @Override
        public MouseEvent mousePressed(MouseEvent e)
        {
            if (overlay == null || !overlay.isEditMode())
            {
                return e;
            }

            // In edit mode, check if we have a hovered target
            SpriteTarget target = overlay.getHoveredTarget();
            if (target != null && e.getButton() == MouseEvent.BUTTON1)
            {
                openEditorForTarget(target);
                e.consume();
                return e;
            }

            return e;
        }

        @Override
        public MouseEvent mouseReleased(MouseEvent e)
        {
            return e;
        }

        @Override
        public MouseEvent mouseEntered(MouseEvent e)
        {
            return e;
        }

        @Override
        public MouseEvent mouseExited(MouseEvent e)
        {
            return e;
        }

        @Override
        public MouseEvent mouseDragged(MouseEvent e)
        {
            return e;
        }

        @Override
        public MouseEvent mouseMoved(MouseEvent e)
        {
            return e;
        }
    };

    @Override
    protected void startUp()
    {
        log.info("SpritePaint starting up");

        // Initialize the sprite store and load any existing overrides
        store = new SpriteStore();
        store.loadAll();

        // Create the overlay (manual construction since SpriteStore isn't in Guice)
        overlay = new SpritePaintOverlay(client, config, store, itemManager);
        overlayManager.add(overlay);

        // Register input listeners
        keyManager.registerKeyListener(keyListener);
        mouseManager.registerMouseListener(mouseListener);

        // Create the sidebar panel
        panel = new SpritePaintPanel(store);
        panel.setOnToggleEditMode(this::toggleEditMode);
        panel.setOnReEdit(this::reEditFromPanel);

        // Create the navigation button for the sidebar
        final BufferedImage icon = createPluginIcon();
        navButton = NavigationButton.builder()
            .tooltip("SpritePaint")
            .icon(icon)
            .priority(8)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);

        // Create the pixel editor window (starts hidden)
        createEditor();

        log.info("SpritePaint started. {} saved overrides loaded from {}",
            store.getKeys().size(), store.getSavePath());
    }

    @Override
    protected void shutDown()
    {
        log.info("SpritePaint shutting down");

        // Disable edit mode
        if (overlay != null)
        {
            overlay.setEditMode(false);
        }

        // Close editor
        if (editor != null)
        {
            editor.dispose();
            editor = null;
        }

        // Remove sidebar
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        panel = null;

        // Remove overlay
        if (overlay != null)
        {
            overlayManager.remove(overlay);
            overlay = null;
        }

        // Unregister inputs
        keyManager.unregisterKeyListener(keyListener);
        mouseManager.unregisterMouseListener(mouseListener);

        store = null;
    }

    @Provides
    SpritePaintConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(SpritePaintConfig.class);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            if (store != null)
            {
                store.loadAll();
                log.debug("SpritePaint: Reloaded sprite overrides on login");
                if (panel != null)
                {
                    SwingUtilities.invokeLater(() -> panel.rebuild());
                }
            }
        }
    }

    // --- Edit mode ---

    private void toggleEditMode()
    {
        if (overlay == null) return;

        boolean newMode = !overlay.isEditMode();
        overlay.setEditMode(newMode);

        if (panel != null)
        {
            SwingUtilities.invokeLater(() -> panel.setEditModeActive(newMode));
        }

        if (newMode)
        {
            log.info("SpritePaint: Edit mode ENABLED. Hover over sprites and click to edit.");
        }
        else
        {
            log.info("SpritePaint: Edit mode DISABLED.");
        }
    }

    // --- Editor management ---

    private void createEditor()
    {
        SwingUtilities.invokeLater(() ->
        {
            editor = new PixelEditor(
                store,
                config.showGrid(),
                config.zoomLevel(),
                config.autoSaveSeconds()
            );

            editor.setOnSaveApply((key, image) ->
            {
                if (image != null)
                {
                    store.save(key, image);
                    log.info("SpritePaint: Applied override for {}", key);
                }
                else
                {
                    store.delete(key);
                    log.info("SpritePaint: Removed override for {}", key);
                }

                // Refresh the sidebar panel
                if (panel != null)
                {
                    SwingUtilities.invokeLater(() -> panel.rebuild());
                }
            });
        });
    }

    private void openEditorForTarget(SpriteTarget target)
    {
        if (editor == null)
        {
            createEditor();
        }

        SwingUtilities.invokeLater(() ->
        {
            if (editor != null)
            {
                editor.openTarget(target);
            }
        });
    }

    /**
     * Re-open the editor for a saved override from the sidebar panel.
     * Reconstructs a synthetic SpriteTarget from the stored image and key.
     */
    private void reEditFromPanel(String key)
    {
        BufferedImage img = store.getOverride(key);
        if (img == null)
        {
            log.warn("SpritePaint: Cannot re-edit '{}', no image found in store", key);
            return;
        }

        // Parse the key to reconstruct a SpriteTarget
        SpriteTarget target;
        java.awt.Rectangle fakeBounds = new java.awt.Rectangle(0, 0, img.getWidth(), img.getHeight());

        if (key.startsWith("item_"))
        {
            int itemId = Integer.parseInt(key.substring(5));
            target = SpriteTarget.forItem(itemId, 1, 0, 0, fakeBounds, img);
        }
        else if (key.startsWith("sprite_"))
        {
            int spriteId = Integer.parseInt(key.substring(7));
            target = SpriteTarget.forSprite(spriteId, 0, 0, fakeBounds, img);
        }
        else if (key.startsWith("widget_"))
        {
            String[] parts = key.substring(7).split("_");
            int groupId = Integer.parseInt(parts[0]);
            int childId = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            target = SpriteTarget.forWidget(groupId, childId, fakeBounds, img);
        }
        else
        {
            log.warn("SpritePaint: Unknown key format for re-edit: {}", key);
            return;
        }

        openEditorForTarget(target);
    }

    /**
     * Generate a simple 16x16 icon for the sidebar navigation button.
     */
    private BufferedImage createPluginIcon()
    {
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = icon.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        // Paint palette circle
        g.setColor(new java.awt.Color(0, 255, 200));
        g.fillOval(2, 2, 12, 12);

        // Color dots
        g.setColor(java.awt.Color.RED);
        g.fillOval(4, 4, 3, 3);
        g.setColor(java.awt.Color.YELLOW);
        g.fillOval(9, 4, 3, 3);
        g.setColor(java.awt.Color.BLUE);
        g.fillOval(4, 9, 3, 3);
        g.setColor(java.awt.Color.WHITE);
        g.fillOval(9, 9, 3, 3);

        g.dispose();
        return icon;
    }
}
