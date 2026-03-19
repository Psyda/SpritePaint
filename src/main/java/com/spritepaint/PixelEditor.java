package com.spritepaint;

import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.function.BiConsumer;

/**
 * Floating pixel editor window. Opens when a sprite is selected for editing
 * in edit mode. Provides tools for painting, erasing, color picking, and
 * flood filling. Auto-saves to the SpriteStore at a configurable interval.
 */
@Slf4j
public class PixelEditor extends JFrame
{
    private final PixelEditorPanel canvas;
    private final SpriteStore store;
    private final JLabel statusLabel;
    private final JPanel colorSwatch;
    private final JLabel coordLabel;

    private SpriteTarget currentTarget;
    private Timer autoSaveTimer;

    // Callback: (storageKey, editedImage) -> applied in-game
    private BiConsumer<String, BufferedImage> onSaveApply;

    // Recently used colors
    private final java.util.List<Color> recentColors = new java.util.ArrayList<>();
    private final JPanel recentColorsPanel;

    // Default OSRS-inspired palette
    private static final Color[] DEFAULT_PALETTE = {
        new Color(0, 0, 0),        // Black
        new Color(255, 255, 255),   // White
        new Color(255, 0, 0),       // Red
        new Color(0, 255, 0),       // Green
        new Color(0, 0, 255),       // Blue
        new Color(255, 255, 0),     // Yellow
        new Color(255, 152, 56),    // Orange (OSRS text)
        new Color(0, 255, 128),     // OSRS green
        new Color(128, 0, 0),       // Dark red
        new Color(64, 64, 64),      // Dark gray
        new Color(128, 128, 128),   // Gray
        new Color(192, 192, 192),   // Light gray
        new Color(78, 74, 61),      // OSRS brown bg
        new Color(93, 84, 71),      // OSRS tan
        new Color(62, 53, 41),      // OSRS dark brown
        new Color(0, 152, 0),       // OSRS darker green
    };

    public PixelEditor(SpriteStore store, boolean showGrid, int defaultZoom, int autoSaveSeconds)
    {
        super("SpritePaint Editor");
        this.store = store;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(500, 450));
        setPreferredSize(new Dimension(650, 600));

        // Dark theme for the frame
        getContentPane().setBackground(new Color(35, 35, 35));
        getRootPane().setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 1));

        // --- Canvas ---
        canvas = new PixelEditorPanel();
        canvas.setShowGrid(showGrid);
        canvas.setZoom(defaultZoom);
        canvas.setOnEdit(this::onCanvasEdit);
        canvas.setOnColorPicked(this::onColorPicked);

        JScrollPane scrollPane = new JScrollPane(canvas);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(40, 40, 40));

        // --- Toolbar ---
        JPanel toolbar = createToolbar();

        // --- Color panel (right side) ---
        JPanel colorPanel = createColorPanel();

        // --- Status bar ---
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(new Color(30, 30, 30));
        statusBar.setBorder(new EmptyBorder(4, 8, 4, 8));

        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(new Color(160, 160, 160));
        statusLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));

        coordLabel = new JLabel("");
        coordLabel.setForeground(new Color(120, 120, 120));
        coordLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));

        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(coordLabel, BorderLayout.EAST);

        // --- Recent colors ---
        recentColorsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        recentColorsPanel.setBackground(new Color(35, 35, 35));
        recentColorsPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 60)),
            "Recent", 0, 0, null, new Color(140, 140, 140)));
        recentColorsPanel.setPreferredSize(new Dimension(200, 45));

        // --- Layout ---
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(new Color(35, 35, 35));
        rightPanel.add(colorPanel, BorderLayout.CENTER);
        rightPanel.add(recentColorsPanel, BorderLayout.SOUTH);
        rightPanel.setPreferredSize(new Dimension(200, 0));

        setLayout(new BorderLayout());
        add(toolbar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);

        // --- Keyboard shortcuts ---
        setupKeyBindings();

        // --- Auto-save timer ---
        if (autoSaveSeconds > 0)
        {
            autoSaveTimer = new Timer(autoSaveSeconds * 1000, e -> doAutoSave());
            autoSaveTimer.setRepeats(true);
        }

        // --- Window close ---
        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                // Final save on close
                doSave();
                if (autoSaveTimer != null)
                {
                    autoSaveTimer.stop();
                }
            }
        });

        pack();
    }

    /**
     * Open the editor for a specific sprite target.
     */
    public void openTarget(SpriteTarget target)
    {
        // Save any current work first
        if (currentTarget != null && canvas.isDirty())
        {
            doSave();
        }

        currentTarget = target;

        // Check if we have a saved override already; use that instead of original
        BufferedImage existingOverride = store.getOverride(target.getStorageKey());
        if (existingOverride != null)
        {
            canvas.setSprite(existingOverride);
            statusLabel.setText("Loaded saved override: " + target.getStorageKey());
        }
        else
        {
            canvas.setSprite(target.getOriginalImage());
            statusLabel.setText("Editing: " + target.getDisplayName());
        }

        setTitle("SpritePaint - " + target.getDisplayName() + " [" + target.getStorageKey() + "]");

        if (autoSaveTimer != null)
        {
            autoSaveTimer.restart();
        }

        if (!isVisible())
        {
            setVisible(true);
        }

        toFront();
        requestFocus();
    }

    public void setOnSaveApply(BiConsumer<String, BufferedImage> callback)
    {
        this.onSaveApply = callback;
    }

    public boolean isEditing()
    {
        return isVisible() && currentTarget != null;
    }

    // --- Toolbar creation ---

    private JPanel createToolbar()
    {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        bar.setBackground(new Color(45, 45, 45));
        bar.setBorder(new EmptyBorder(2, 4, 2, 4));

        // Tool buttons
        JToggleButton pencilBtn = makeToolButton("Pencil (B)", PixelEditorPanel.Tool.PENCIL);
        JToggleButton eraserBtn = makeToolButton("Eraser (E)", PixelEditorPanel.Tool.ERASER);
        JToggleButton eyedropBtn = makeToolButton("Eyedrop (I)", PixelEditorPanel.Tool.EYEDROP);
        JToggleButton fillBtn = makeToolButton("Fill (G)", PixelEditorPanel.Tool.FILL);

        ButtonGroup toolGroup = new ButtonGroup();
        toolGroup.add(pencilBtn);
        toolGroup.add(eraserBtn);
        toolGroup.add(eyedropBtn);
        toolGroup.add(fillBtn);
        pencilBtn.setSelected(true);

        bar.add(pencilBtn);
        bar.add(eraserBtn);
        bar.add(eyedropBtn);
        bar.add(fillBtn);

        bar.add(makeSeparator());

        // Undo / Redo
        JButton undoBtn = makeButton("Undo (Z)", e -> canvas.undo());
        JButton redoBtn = makeButton("Redo (Y)", e -> canvas.redo());
        bar.add(undoBtn);
        bar.add(redoBtn);

        bar.add(makeSeparator());

        // Grid toggle
        JToggleButton gridBtn = new JToggleButton("Grid");
        gridBtn.setSelected(canvas.getZoom() >= 4);
        styleButton(gridBtn);
        gridBtn.addActionListener(e -> canvas.setShowGrid(gridBtn.isSelected()));
        bar.add(gridBtn);

        // Zoom
        JButton zoomIn = makeButton("+", e -> canvas.setZoom(canvas.getZoom() + 2));
        JButton zoomOut = makeButton("-", e -> canvas.setZoom(canvas.getZoom() - 2));
        bar.add(zoomOut);
        bar.add(zoomIn);

        bar.add(makeSeparator());

        // Save / Apply
        JButton saveBtn = makeButton("Save & Apply", e -> doSaveAndApply());
        saveBtn.setBackground(new Color(40, 100, 40));
        bar.add(saveBtn);

        // Reset
        JButton resetBtn = makeButton("Reset", e -> doReset());
        resetBtn.setBackground(new Color(100, 40, 40));
        bar.add(resetBtn);

        // Active color swatch in toolbar
        colorSwatch = new JPanel();
        colorSwatch.setPreferredSize(new Dimension(28, 28));
        colorSwatch.setBackground(canvas.getActiveColor());
        colorSwatch.setBorder(new LineBorder(Color.WHITE, 2));
        colorSwatch.setToolTipText("Current color (click to change)");
        colorSwatch.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                chooseColor();
            }
        });
        bar.add(colorSwatch);

        return bar;
    }

    // --- Color panel ---

    private JPanel createColorPanel()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(35, 35, 35));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("Palette");
        title.setForeground(new Color(180, 180, 180));
        title.setAlignmentX(0);
        panel.add(title);
        panel.add(Box.createVerticalStrut(4));

        JPanel grid = new JPanel(new GridLayout(0, 4, 2, 2));
        grid.setBackground(new Color(35, 35, 35));
        grid.setAlignmentX(0);

        for (Color c : DEFAULT_PALETTE)
        {
            JPanel swatch = new JPanel();
            swatch.setPreferredSize(new Dimension(32, 32));
            swatch.setBackground(c);
            swatch.setBorder(new LineBorder(new Color(80, 80, 80), 1));
            swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            swatch.addMouseListener(new java.awt.event.MouseAdapter()
            {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e)
                {
                    setEditorColor(c);
                }
            });
            grid.add(swatch);
        }

        // Transparent "color"
        JPanel transparentSwatch = new JPanel()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                // Draw a red X to indicate transparency
                g.setColor(Color.RED);
                g.drawLine(0, 0, getWidth(), getHeight());
                g.drawLine(getWidth(), 0, 0, getHeight());
            }
        };
        transparentSwatch.setPreferredSize(new Dimension(32, 32));
        transparentSwatch.setBackground(new Color(80, 80, 80));
        transparentSwatch.setBorder(new LineBorder(new Color(80, 80, 80), 1));
        transparentSwatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        transparentSwatch.setToolTipText("Transparent (eraser)");
        transparentSwatch.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                canvas.setActiveTool(PixelEditorPanel.Tool.ERASER);
            }
        });
        grid.add(transparentSwatch);

        panel.add(grid);
        panel.add(Box.createVerticalStrut(12));

        // Custom color button
        JButton customBtn = new JButton("Custom Color...");
        styleButton(customBtn);
        customBtn.setAlignmentX(0);
        customBtn.addActionListener(e -> chooseColor());
        panel.add(customBtn);

        panel.add(Box.createVerticalGlue());
        return panel;
    }

    // --- Key bindings ---

    private void setupKeyBindings()
    {
        InputMap im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getRootPane().getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, 0), "pencil");
        am.put("pencil", new AbstractAction()
        {
            @Override public void actionPerformed(ActionEvent e) { canvas.setActiveTool(PixelEditorPanel.Tool.PENCIL); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, 0), "eraser");
        am.put("eraser", new AbstractAction()
        {
            @Override public void actionPerformed(ActionEvent e) { canvas.setActiveTool(PixelEditorPanel.Tool.ERASER); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, 0), "eyedrop");
        am.put("eyedrop", new AbstractAction()
        {
            @Override public void actionPerformed(ActionEvent e) { canvas.setActiveTool(PixelEditorPanel.Tool.EYEDROP); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_G, 0), "fill");
        am.put("fill", new AbstractAction()
        {
            @Override public void actionPerformed(ActionEvent e) { canvas.setActiveTool(PixelEditorPanel.Tool.FILL); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
        am.put("undo", new AbstractAction()
        {
            @Override public void actionPerformed(ActionEvent e) { canvas.undo(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
        am.put("redo", new AbstractAction()
        {
            @Override public void actionPerformed(ActionEvent e) { canvas.redo(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save");
        am.put("save", new AbstractAction()
        {
            @Override public void actionPerformed(ActionEvent e) { doSaveAndApply(); }
        });
    }

    // --- Actions ---

    private void doAutoSave()
    {
        if (currentTarget != null && canvas.isDirty())
        {
            doSave();
            statusLabel.setText("Auto-saved: " + currentTarget.getStorageKey());
        }
    }

    private void doSave()
    {
        if (currentTarget == null) return;
        BufferedImage img = canvas.getSprite();
        if (img == null) return;

        store.save(currentTarget.getStorageKey(), img);
        canvas.clearDirty();
    }

    private void doSaveAndApply()
    {
        if (currentTarget == null) return;
        doSave();

        BufferedImage img = canvas.getSprite();
        if (onSaveApply != null && img != null)
        {
            onSaveApply.accept(currentTarget.getStorageKey(), img);
        }
        statusLabel.setText("Saved and applied: " + currentTarget.getStorageKey());
    }

    private void doReset()
    {
        if (currentTarget == null) return;
        int result = JOptionPane.showConfirmDialog(this,
            "Reset this sprite to the original? This will delete your edits.",
            "Reset Sprite", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION)
        {
            store.delete(currentTarget.getStorageKey());
            canvas.setSprite(currentTarget.getOriginalImage());
            if (onSaveApply != null)
            {
                // Pass null to signal removal of override
                onSaveApply.accept(currentTarget.getStorageKey(), null);
            }
            statusLabel.setText("Reset to original: " + currentTarget.getStorageKey());
        }
    }

    private void chooseColor()
    {
        Color chosen = JColorChooser.showDialog(this, "Choose Color", canvas.getActiveColor());
        if (chosen != null)
        {
            setEditorColor(chosen);
        }
    }

    private void setEditorColor(Color c)
    {
        canvas.setActiveColor(c);
        colorSwatch.setBackground(c);
        addRecentColor(c);

        // If currently on eraser/eyedrop, switch to pencil
        if (canvas.getActiveTool() == PixelEditorPanel.Tool.ERASER
            || canvas.getActiveTool() == PixelEditorPanel.Tool.EYEDROP)
        {
            canvas.setActiveTool(PixelEditorPanel.Tool.PENCIL);
        }
    }

    private void onCanvasEdit()
    {
        // Update status
        if (currentTarget != null)
        {
            statusLabel.setText("Editing: " + currentTarget.getDisplayName() + " (unsaved)");
        }
    }

    private void onColorPicked(Color c)
    {
        setEditorColor(c);
        // Switch back to pencil after picking
        canvas.setActiveTool(PixelEditorPanel.Tool.PENCIL);
    }

    private void addRecentColor(Color c)
    {
        recentColors.remove(c);
        recentColors.add(0, c);
        if (recentColors.size() > 12) recentColors.remove(recentColors.size() - 1);
        rebuildRecentColors();
    }

    private void rebuildRecentColors()
    {
        recentColorsPanel.removeAll();
        for (Color c : recentColors)
        {
            JPanel swatch = new JPanel();
            swatch.setPreferredSize(new Dimension(18, 18));
            swatch.setBackground(c);
            swatch.setBorder(new LineBorder(new Color(80, 80, 80), 1));
            swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            swatch.addMouseListener(new java.awt.event.MouseAdapter()
            {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e)
                {
                    setEditorColor(c);
                }
            });
            recentColorsPanel.add(swatch);
        }
        recentColorsPanel.revalidate();
        recentColorsPanel.repaint();
    }

    // --- UI helpers ---

    private JToggleButton makeToolButton(String text, PixelEditorPanel.Tool tool)
    {
        JToggleButton btn = new JToggleButton(text);
        styleButton(btn);
        btn.addActionListener(e -> canvas.setActiveTool(tool));
        return btn;
    }

    private JButton makeButton(String text, java.awt.event.ActionListener action)
    {
        JButton btn = new JButton(text);
        styleButton(btn);
        btn.addActionListener(action);
        return btn;
    }

    private void styleButton(AbstractButton btn)
    {
        btn.setBackground(new Color(55, 55, 55));
        btn.setForeground(new Color(200, 200, 200));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(70, 70, 70), 1),
            new EmptyBorder(4, 8, 4, 8)));
        btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
    }

    private JSeparator makeSeparator()
    {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(2, 24));
        sep.setForeground(new Color(70, 70, 70));
        return sep;
    }
}
