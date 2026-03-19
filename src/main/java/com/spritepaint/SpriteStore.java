package com.spritepaint;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages persistence of edited sprites to disk.
 * All edited sprites are stored as PNG files in ~/.runelite/spritepaint/
 * with filenames matching their storage key (e.g., item_4151.png, sprite_1234.png).
 *
 * The store maintains an in-memory cache of all loaded overrides for fast lookup
 * during rendering.
 */
@Slf4j
public class SpriteStore
{
    private static final String SAVE_DIR_NAME = "spritepaint";
    private static final String IMAGE_FORMAT = "png";

    private final File saveDir;
    private final Map<String, BufferedImage> overrides = new ConcurrentHashMap<>();

    public SpriteStore()
    {
        saveDir = new File(RuneLite.RUNELITE_DIR, SAVE_DIR_NAME);
        if (!saveDir.exists())
        {
            if (!saveDir.mkdirs())
            {
                log.error("Failed to create SpritePaint save directory: {}", saveDir.getAbsolutePath());
            }
        }
    }

    /**
     * Load all saved sprite overrides from disk into memory.
     * Called on plugin startup.
     */
    public void loadAll()
    {
        overrides.clear();

        File[] files = saveDir.listFiles((dir, name) -> name.endsWith("." + IMAGE_FORMAT));
        if (files == null)
        {
            log.info("SpritePaint: No saved sprites found in {}", saveDir.getAbsolutePath());
            return;
        }

        int loaded = 0;
        for (File file : files)
        {
            String key = file.getName().replace("." + IMAGE_FORMAT, "");
            try
            {
                BufferedImage image = ImageIO.read(file);
                if (image != null)
                {
                    // Ensure the image has an alpha channel
                    if (image.getType() != BufferedImage.TYPE_INT_ARGB)
                    {
                        BufferedImage argb = new BufferedImage(
                            image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        argb.getGraphics().drawImage(image, 0, 0, null);
                        image = argb;
                    }
                    overrides.put(key, image);
                    loaded++;
                }
            }
            catch (IOException e)
            {
                log.warn("SpritePaint: Failed to load sprite override: {}", file.getName(), e);
            }
        }

        log.info("SpritePaint: Loaded {} sprite overrides from {}", loaded, saveDir.getAbsolutePath());
    }

    /**
     * Save a sprite override to disk and update the in-memory cache.
     * This is called both by auto-save and manual save.
     *
     * @param key   The storage key (e.g., "item_4151")
     * @param image The edited sprite image
     */
    public void save(String key, BufferedImage image)
    {
        // Update in-memory cache immediately
        overrides.put(key, copyImage(image));

        // Write to disk
        File file = new File(saveDir, key + "." + IMAGE_FORMAT);
        try
        {
            ImageIO.write(image, IMAGE_FORMAT, file);
            log.debug("SpritePaint: Saved sprite override: {}", file.getName());
        }
        catch (IOException e)
        {
            log.error("SpritePaint: Failed to save sprite override: {}", file.getName(), e);
        }
    }

    /**
     * Delete a sprite override from disk and memory.
     *
     * @param key The storage key to remove
     */
    public void delete(String key)
    {
        overrides.remove(key);

        File file = new File(saveDir, key + "." + IMAGE_FORMAT);
        if (file.exists())
        {
            if (!file.delete())
            {
                log.warn("SpritePaint: Failed to delete sprite override: {}", file.getName());
            }
        }
    }

    /**
     * Get an override image by key, or null if none exists.
     */
    public BufferedImage getOverride(String key)
    {
        return overrides.get(key);
    }

    /**
     * Check if an override exists for the given key.
     */
    public boolean hasOverride(String key)
    {
        return overrides.containsKey(key);
    }

    /**
     * Get all override keys (unmodifiable).
     */
    public Set<String> getKeys()
    {
        return Collections.unmodifiableSet(overrides.keySet());
    }

    /**
     * Get the save directory path (for display in UI).
     */
    public String getSavePath()
    {
        return saveDir.getAbsolutePath();
    }

    /**
     * Deep copy a BufferedImage to prevent aliasing issues.
     */
    private static BufferedImage copyImage(BufferedImage source)
    {
        BufferedImage copy = new BufferedImage(
            source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(source, 0, 0, null);
        return copy;
    }
}
