package com.spritepaint;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

/**
 * The actual pixel-editing canvas. Displays a sprite at a zoomed-in scale
 * with optional grid lines, and handles mouse input for painting tools.
 *
 * Tools:
 *   PENCIL  - Paint pixels with the active color
 *   ERASER  - Set pixels to fully transparent
 *   EYEDROP - Pick color from the sprite under cursor
 *   FILL    - Flood fill a contiguous region
 */
public class PixelEditorPanel extends JPanel
{
    public enum Tool
    {
        PENCIL,
        ERASER,
        EYEDROP,
        FILL
    }

    // The sprite being edited (ARGB)
    private BufferedImage sprite;

    // Undo/redo stacks (stored as full image snapshots)
    private final List<BufferedImage> undoStack = new ArrayList<>();
    private final List<BufferedImage> redoStack = new ArrayList<>();
    private static final int MAX_UNDO = 50;

    // Display settings
    private int zoom = 10;
    private boolean showGrid = true;
    private boolean showCheckerboard = true;

    // Tool state
    private Tool activeTool = Tool.PENCIL;
    private Color activeColor = Color.WHITE;
    private int brushSize = 1;

    // Tracking
    private Point hoveredPixel = null;
    private boolean isDirty = false;
    private boolean isPainting = false;

    // Callback for when edits occur
    private Runnable onEdit;

    // Callback for eyedropper color pick
    private java.util.function.Consumer<Color> onColorPicked;

    public PixelEditorPanel()
    {
        setBackground(new Color(40, 40, 40));
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (sprite == null) return;
                Point px = screenToPixel(e.getPoint());
                if (px == null) return;

                if (activeTool == Tool.EYEDROP)
                {
                    pickColor(px);
                    return;
                }

                if (activeTool == Tool.FILL)
                {
                    pushUndo();
                    floodFill(px.x, px.y);
                    markDirty();
                    return;
                }

                // Pencil or Eraser: start painting
                pushUndo();
                isPainting = true;
                applyBrush(px);
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if (isPainting)
                {
                    isPainting = false;
                    markDirty();
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter()
        {
            @Override
            public void mouseMoved(MouseEvent e)
            {
                hoveredPixel = screenToPixel(e.getPoint());
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e)
            {
                Point px = screenToPixel(e.getPoint());
                hoveredPixel = px;
                if (isPainting && px != null)
                {
                    applyBrush(px);
                }
                repaint();
            }
        });
    }

    /**
     * Load a sprite image into the editor.
     */
    public void setSprite(BufferedImage source)
    {
        // Always work with ARGB copies
        sprite = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sprite.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();

        undoStack.clear();
        redoStack.clear();
        isDirty = false;
        hoveredPixel = null;

        updatePreferredSize();
        repaint();
    }

    /**
     * Get the current (edited) sprite image.
     */
    public BufferedImage getSprite()
    {
        if (sprite == null) return null;
        BufferedImage copy = new BufferedImage(sprite.getWidth(), sprite.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(sprite, 0, 0, null);
        return copy;
    }

    // --- Tool setters ---

    public void setActiveTool(Tool tool)
    {
        this.activeTool = tool;
        switch (tool)
        {
            case EYEDROP:
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                break;
            default:
                setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                break;
        }
    }

    public Tool getActiveTool()
    {
        return activeTool;
    }

    public void setActiveColor(Color color)
    {
        this.activeColor = color;
    }

    public Color getActiveColor()
    {
        return activeColor;
    }

    public void setBrushSize(int size)
    {
        this.brushSize = Math.max(1, Math.min(5, size));
    }

    public void setZoom(int zoom)
    {
        this.zoom = Math.max(2, Math.min(24, zoom));
        updatePreferredSize();
        repaint();
    }

    public int getZoom()
    {
        return zoom;
    }

    public void setShowGrid(boolean show)
    {
        this.showGrid = show;
        repaint();
    }

    public boolean isDirty()
    {
        return isDirty;
    }

    public void clearDirty()
    {
        isDirty = false;
    }

    public void setOnEdit(Runnable callback)
    {
        this.onEdit = callback;
    }

    public void setOnColorPicked(java.util.function.Consumer<Color> callback)
    {
        this.onColorPicked = callback;
    }

    // --- Undo / Redo ---

    public void undo()
    {
        if (undoStack.isEmpty() || sprite == null) return;
        redoStack.add(copyImage(sprite));
        sprite = undoStack.remove(undoStack.size() - 1);
        markDirty();
    }

    public void redo()
    {
        if (redoStack.isEmpty() || sprite == null) return;
        undoStack.add(copyImage(sprite));
        sprite = redoStack.remove(redoStack.size() - 1);
        markDirty();
    }

    public boolean canUndo()
    {
        return !undoStack.isEmpty();
    }

    public boolean canRedo()
    {
        return !redoStack.isEmpty();
    }

    private void pushUndo()
    {
        if (sprite == null) return;
        undoStack.add(copyImage(sprite));
        if (undoStack.size() > MAX_UNDO)
        {
            undoStack.remove(0);
        }
        redoStack.clear();
    }

    // --- Painting logic ---

    private void applyBrush(Point px)
    {
        if (sprite == null) return;
        int half = brushSize / 2;

        for (int dy = -half; dy < brushSize - half; dy++)
        {
            for (int dx = -half; dx < brushSize - half; dx++)
            {
                int x = px.x + dx;
                int y = px.y + dy;
                if (x >= 0 && x < sprite.getWidth() && y >= 0 && y < sprite.getHeight())
                {
                    if (activeTool == Tool.ERASER)
                    {
                        sprite.setRGB(x, y, 0x00000000); // Fully transparent
                    }
                    else
                    {
                        sprite.setRGB(x, y, activeColor.getRGB());
                    }
                }
            }
        }
    }

    private void pickColor(Point px)
    {
        if (sprite == null) return;
        int argb = sprite.getRGB(px.x, px.y);
        Color picked = new Color(argb, true);
        activeColor = picked;
        if (onColorPicked != null)
        {
            onColorPicked.accept(picked);
        }
    }

    private void floodFill(int startX, int startY)
    {
        if (sprite == null) return;

        int targetColor = sprite.getRGB(startX, startY);
        int fillColor = (activeTool == Tool.ERASER) ? 0x00000000 : activeColor.getRGB();
        if (targetColor == fillColor) return;

        int w = sprite.getWidth();
        int h = sprite.getHeight();

        boolean[][] visited = new boolean[w][h];
        List<Point> stack = new ArrayList<>();
        stack.add(new Point(startX, startY));

        while (!stack.isEmpty())
        {
            Point p = stack.remove(stack.size() - 1);
            int x = p.x, y = p.y;

            if (x < 0 || x >= w || y < 0 || y >= h) continue;
            if (visited[x][y]) continue;
            if (sprite.getRGB(x, y) != targetColor) continue;

            visited[x][y] = true;
            sprite.setRGB(x, y, fillColor);

            stack.add(new Point(x + 1, y));
            stack.add(new Point(x - 1, y));
            stack.add(new Point(x, y + 1));
            stack.add(new Point(x, y - 1));
        }
    }

    // --- Rendering ---

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        if (sprite == null) return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int w = sprite.getWidth();
        int h = sprite.getHeight();
        int canvasW = w * zoom;
        int canvasH = h * zoom;

        // Center offset
        int ox = (getWidth() - canvasW) / 2;
        int oy = (getHeight() - canvasH) / 2;

        // Draw checkerboard background (indicates transparency)
        if (showCheckerboard)
        {
            int checkSize = Math.max(zoom / 2, 3);
            for (int py = 0; py < h; py++)
            {
                for (int px = 0; px < w; px++)
                {
                    int sx = ox + px * zoom;
                    int sy = oy + py * zoom;
                    // Alternate between light and dark gray for checkerboard
                    for (int cy = 0; cy < zoom; cy += checkSize)
                    {
                        for (int cx = 0; cx < zoom; cx += checkSize)
                        {
                            boolean light = ((px * zoom + cx) / checkSize + (py * zoom + cy) / checkSize) % 2 == 0;
                            g2.setColor(light ? new Color(180, 180, 180) : new Color(140, 140, 140));
                            g2.fillRect(sx + cx, sy + cy,
                                Math.min(checkSize, zoom - cx), Math.min(checkSize, zoom - cy));
                        }
                    }
                }
            }
        }

        // Draw the sprite at zoom
        g2.drawImage(sprite, ox, oy, canvasW, canvasH, null);

        // Draw grid
        if (showGrid && zoom >= 4)
        {
            g2.setColor(new Color(0, 0, 0, 60));
            g2.setStroke(new BasicStroke(1));
            for (int x = 0; x <= w; x++)
            {
                g2.drawLine(ox + x * zoom, oy, ox + x * zoom, oy + canvasH);
            }
            for (int y = 0; y <= h; y++)
            {
                g2.drawLine(ox, oy + y * zoom, ox + canvasW, oy + y * zoom);
            }
        }

        // Draw canvas border
        g2.setColor(new Color(100, 100, 100));
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(ox - 1, oy - 1, canvasW + 1, canvasH + 1);

        // Draw hovered pixel highlight
        if (hoveredPixel != null)
        {
            int hx = ox + hoveredPixel.x * zoom;
            int hy = oy + hoveredPixel.y * zoom;
            int half = brushSize / 2;
            int bx = hx - half * zoom;
            int by = hy - half * zoom;
            int bw = brushSize * zoom;

            g2.setColor(new Color(255, 255, 0, 120));
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(bx, by, bw, bw);

            // Show preview color
            if (activeTool == Tool.PENCIL)
            {
                g2.setColor(new Color(activeColor.getRed(), activeColor.getGreen(),
                    activeColor.getBlue(), 100));
                g2.fillRect(bx + 1, by + 1, bw - 1, bw - 1);
            }
            else if (activeTool == Tool.ERASER)
            {
                // Draw an X for eraser
                g2.setColor(new Color(255, 80, 80, 150));
                g2.drawLine(bx, by, bx + bw, by + bw);
                g2.drawLine(bx + bw, by, bx, by + bw);
            }
        }
    }

    // --- Coordinate conversion ---

    private Point screenToPixel(Point screen)
    {
        if (sprite == null) return null;

        int w = sprite.getWidth();
        int h = sprite.getHeight();
        int canvasW = w * zoom;
        int canvasH = h * zoom;
        int ox = (getWidth() - canvasW) / 2;
        int oy = (getHeight() - canvasH) / 2;

        int px = (screen.x - ox) / zoom;
        int py = (screen.y - oy) / zoom;

        if (px >= 0 && px < w && py >= 0 && py < h)
        {
            return new Point(px, py);
        }
        return null;
    }

    // --- Utility ---

    private void updatePreferredSize()
    {
        if (sprite != null)
        {
            int pw = sprite.getWidth() * zoom + 40;
            int ph = sprite.getHeight() * zoom + 40;
            setPreferredSize(new Dimension(Math.max(pw, 200), Math.max(ph, 200)));
            revalidate();
        }
    }

    private void markDirty()
    {
        isDirty = true;
        repaint();
        if (onEdit != null)
        {
            onEdit.run();
        }
    }

    private static BufferedImage copyImage(BufferedImage source)
    {
        BufferedImage copy = new BufferedImage(
            source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(source, 0, 0, null);
        return copy;
    }
}
