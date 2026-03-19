package com.spritepaint;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * RuneLite sidebar panel showing all saved sprite overrides as a thumbnail grid.
 * Provides controls to re-edit, delete, and manage your custom sprites.
 */
@Slf4j
public class SpritePaintPanel extends PluginPanel
{
    private final SpriteStore store;
    private final JPanel gridPanel;
    private final JLabel statusLabel;
    private final JLabel editModeLabel;

    // Callbacks
    private Consumer<String> onReEdit;
    private Runnable onToggleEditMode;

    private boolean editModeActive = false;

    public SpritePaintPanel(SpriteStore store)
    {
        super(false);
        this.store = store;

        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // --- Header ---
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("SpritePaint");
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        title.setForeground(new Color(0, 255, 200));
        header.add(title, BorderLayout.NORTH);

        // Edit mode toggle button
        JButton editToggle = new JButton("Toggle Edit Mode (F8)");
        editToggle.setBackground(new Color(50, 50, 50));
        editToggle.setForeground(new Color(200, 200, 200));
        editToggle.setFocusPainted(false);
        editToggle.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(0, 255, 200, 100), 1),
            new EmptyBorder(6, 10, 6, 10)));
        editToggle.addActionListener(e -> {
            if (onToggleEditMode != null) onToggleEditMode.run();
        });
        header.add(editToggle, BorderLayout.CENTER);

        editModeLabel = new JLabel("Edit Mode: OFF");
        editModeLabel.setFont(new Font("Monospaced", Font.BOLD, 11));
        editModeLabel.setForeground(new Color(120, 120, 120));
        editModeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        header.add(editModeLabel, BorderLayout.SOUTH);

        add(header, BorderLayout.NORTH);

        // --- Grid of saved overrides ---
        gridPanel = new JPanel();
        gridPanel.setLayout(new BoxLayout(gridPanel, BoxLayout.Y_AXIS));
        gridPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(gridPanel);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        // --- Footer ---
        JPanel footer = new JPanel(new BorderLayout(0, 4));
        footer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        statusLabel = new JLabel("0 overrides");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        statusLabel.setForeground(new Color(120, 120, 120));
        footer.add(statusLabel, BorderLayout.NORTH);

        JButton openFolderBtn = new JButton("Open Folder");
        openFolderBtn.setBackground(new Color(50, 50, 50));
        openFolderBtn.setForeground(new Color(180, 180, 180));
        openFolderBtn.setFocusPainted(false);
        openFolderBtn.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(70, 70, 70), 1),
            new EmptyBorder(4, 8, 4, 8)));
        openFolderBtn.setToolTipText("Open the sprite save folder in your file manager");
        openFolderBtn.addActionListener(e -> openSaveFolder());
        footer.add(openFolderBtn, BorderLayout.CENTER);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setBackground(new Color(50, 50, 50));
        refreshBtn.setForeground(new Color(180, 180, 180));
        refreshBtn.setFocusPainted(false);
        refreshBtn.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(70, 70, 70), 1),
            new EmptyBorder(4, 8, 4, 8)));
        refreshBtn.addActionListener(e -> {
            store.loadAll();
            rebuild();
        });
        footer.add(refreshBtn, BorderLayout.SOUTH);

        add(footer, BorderLayout.SOUTH);

        rebuild();
    }

    public void setOnReEdit(Consumer<String> callback)
    {
        this.onReEdit = callback;
    }

    public void setOnToggleEditMode(Runnable callback)
    {
        this.onToggleEditMode = callback;
    }

    public void setEditModeActive(boolean active)
    {
        this.editModeActive = active;
        if (active)
        {
            editModeLabel.setText("Edit Mode: ON");
            editModeLabel.setForeground(new Color(0, 255, 200));
        }
        else
        {
            editModeLabel.setText("Edit Mode: OFF");
            editModeLabel.setForeground(new Color(120, 120, 120));
        }
    }

    /**
     * Rebuild the grid of override thumbnails from the store.
     */
    public void rebuild()
    {
        gridPanel.removeAll();

        Set<String> keys = new TreeSet<>(store.getKeys()); // Sorted for consistent display

        if (keys.isEmpty())
        {
            JLabel empty = new JLabel("<html><center>No sprite overrides yet.<br>Press F8 and click a sprite to start!</center></html>");
            empty.setForeground(new Color(140, 140, 140));
            empty.setFont(new Font("SansSerif", Font.PLAIN, 11));
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            empty.setBorder(new EmptyBorder(20, 10, 20, 10));
            gridPanel.add(empty);
        }
        else
        {
            for (String key : keys)
            {
                BufferedImage img = store.getOverride(key);
                if (img == null) continue;

                JPanel card = createOverrideCard(key, img);
                gridPanel.add(card);
                gridPanel.add(Box.createVerticalStrut(4));
            }
        }

        statusLabel.setText(keys.size() + " override" + (keys.size() != 1 ? "s" : "")
            + " saved to " + store.getSavePath());

        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private JPanel createOverrideCard(String key, BufferedImage img)
    {
        JPanel card = new JPanel(new BorderLayout(8, 0));
        card.setBackground(new Color(45, 45, 45));
        card.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(60, 60, 60), 1),
            new EmptyBorder(6, 6, 6, 6)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

        // Thumbnail
        int thumbSize = 48;
        BufferedImage thumb = new BufferedImage(thumbSize, thumbSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // Checkerboard background for transparency
        int checkSize = 6;
        for (int y = 0; y < thumbSize; y += checkSize)
        {
            for (int x = 0; x < thumbSize; x += checkSize)
            {
                boolean light = ((x / checkSize) + (y / checkSize)) % 2 == 0;
                g.setColor(light ? new Color(160, 160, 160) : new Color(120, 120, 120));
                g.fillRect(x, y, checkSize, checkSize);
            }
        }

        // Scale sprite to fit thumb, maintaining aspect ratio
        double scale = Math.min((double) thumbSize / img.getWidth(),
            (double) thumbSize / img.getHeight());
        int sw = (int) (img.getWidth() * scale);
        int sh = (int) (img.getHeight() * scale);
        int sx = (thumbSize - sw) / 2;
        int sy = (thumbSize - sh) / 2;
        g.drawImage(img, sx, sy, sw, sh, null);
        g.dispose();

        JLabel thumbLabel = new JLabel(new ImageIcon(thumb));
        thumbLabel.setBorder(new LineBorder(new Color(80, 80, 80), 1));
        card.add(thumbLabel, BorderLayout.WEST);

        // Info
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBackground(new Color(45, 45, 45));

        JLabel nameLabel = new JLabel(key);
        nameLabel.setForeground(new Color(200, 200, 200));
        nameLabel.setFont(new Font("Monospaced", Font.BOLD, 11));
        info.add(nameLabel);

        JLabel sizeLabel = new JLabel(img.getWidth() + "x" + img.getHeight() + " px");
        sizeLabel.setForeground(new Color(130, 130, 130));
        sizeLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        info.add(sizeLabel);

        card.add(info, BorderLayout.CENTER);

        // Action buttons
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        actions.setBackground(new Color(45, 45, 45));

        JButton editBtn = new JButton("Edit");
        styleSmallButton(editBtn, new Color(50, 80, 50));
        editBtn.addActionListener(e -> {
            if (onReEdit != null) onReEdit.accept(key);
        });
        actions.add(editBtn);

        JButton deleteBtn = new JButton("X");
        styleSmallButton(deleteBtn, new Color(80, 40, 40));
        deleteBtn.setToolTipText("Delete this override");
        deleteBtn.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(this,
                "Delete override '" + key + "'? The original sprite will be restored.",
                "Delete Override", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION)
            {
                store.delete(key);
                rebuild();
            }
        });
        actions.add(deleteBtn);

        card.add(actions, BorderLayout.EAST);

        // Hover highlight
        card.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                card.setBackground(new Color(55, 55, 55));
                info.setBackground(new Color(55, 55, 55));
                actions.setBackground(new Color(55, 55, 55));
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                card.setBackground(new Color(45, 45, 45));
                info.setBackground(new Color(45, 45, 45));
                actions.setBackground(new Color(45, 45, 45));
            }
        });

        return card;
    }

    private void styleSmallButton(JButton btn, Color bg)
    {
        btn.setBackground(bg);
        btn.setForeground(new Color(200, 200, 200));
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btn.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(70, 70, 70), 1),
            new EmptyBorder(2, 6, 2, 6)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void openSaveFolder()
    {
        try
        {
            Desktop.getDesktop().open(new File(store.getSavePath()));
        }
        catch (Exception e)
        {
            log.warn("Failed to open save folder", e);
            JOptionPane.showMessageDialog(this,
                "Could not open folder. Path:\n" + store.getSavePath(),
                "Open Folder", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
