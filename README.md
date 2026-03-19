# SpritePaint

An in-game pixel editor for OSRS sprites in RuneLite. Paint on your world, Animal Crossing style.

## What it does

SpritePaint lets you hover over any UI sprite in the game (inventory items, prayer icons, spellbook icons, skill tab icons, bank items, etc.), click to capture it, open it in a floating pixel editor, paint your changes, and see them applied live in-game. Your edits persist across sessions as PNG files on disk.

## Installation

This is an external RuneLite plugin. To use it:

1. Clone or unzip this project alongside your RuneLite plugin workspace
2. Build with Gradle: `./gradlew build`
3. The resulting JAR goes in your RuneLite external plugins directory
4. Alternatively, add it to a RuneLite plugin project template and run in development mode

### Development Mode

If you want to test in the RuneLite development client:

1. Clone the [RuneLite plugin template](https://github.com/runelite/example-plugin)
2. Replace or add the `com.spritepaint` package into the source tree
3. Update the `build.gradle` if needed to match your RuneLite version
4. Run via your IDE with the RuneLite development launcher

## Usage

### Quick Start

1. Enable the plugin in RuneLite's plugin list
2. Press **F8** (configurable) to enter Edit Mode
3. Hover over any UI sprite — it will highlight with a colored border and label
4. Click the sprite to open the pixel editor
5. Paint your changes
6. Click **Save & Apply** (or Ctrl+S) to see changes live in-game
7. Press F8 again to exit Edit Mode

### Pixel Editor

The editor is a floating window with:

- **Tools:** Pencil (B), Eraser (E), Eyedropper (I), Flood Fill (G)
- **Undo/Redo:** Ctrl+Z / Ctrl+Y (up to 50 steps)
- **Zoom:** +/- buttons or set default in config
- **Grid:** Toggle pixel grid lines
- **Color palette:** 16 OSRS-themed colors, plus custom color picker
- **Recent colors:** Strip of recently used colors
- **Auto-save:** Continuously saves to disk at a configurable interval (default 3s)

### Sidebar Panel

Click the SpritePaint icon in the RuneLite sidebar to see:

- All your saved sprite overrides as thumbnails
- Edit button to re-open any override in the editor
- Delete button to remove an override and restore the original
- Open Folder button to access the save directory
- Refresh button to reload from disk

### Config Options

- **Edit Mode Hotkey:** Key to toggle edit mode (default F8)
- **Highlight Color:** Color of the hover highlight in edit mode
- **Show Overrides In Game:** Toggle rendering of your custom sprites
- **Show Grid:** Default grid visibility in the editor
- **Default Zoom:** Zoom level for the editor (4-20)
- **Auto-save Interval:** Seconds between auto-saves (1-30)

## How it works

### Sprite Detection

In edit mode, the overlay scans ~19 known widget group IDs (inventory, bank, equipment, prayer, spellbook, skills, etc.) on every frame. It recursively walks the widget tree, hit-testing each widget's bounds against the mouse position. When a widget with a sprite ID or item ID is found under the cursor, it's highlighted and becomes clickable.

### Image Capture

For items, `ItemManager.getImage()` renders a clean sprite. For UI sprites and other widget types, a screen capture of the widget bounds is used as a fallback.

### Persistence

Edited sprites are stored as PNG files in `~/.runelite/spritepaint/` with deterministic filenames:
- Items: `item_4151.png` (by item ID)
- UI Sprites: `sprite_1234.png` (by sprite ID)
- Widgets: `widget_149_0.png` (by group and child ID)

### Override Rendering

On every frame, the overlay scans all visible widgets. For each widget with a sprite ID or item ID, it checks if a matching PNG exists in the store. If so, it draws the custom image on top of the original widget at the exact same position and size.

## File Structure

```
com.spritepaint/
├── SpritePaintPlugin.java      # Main plugin lifecycle, input handling, coordination
├── SpritePaintConfig.java      # Configuration interface (hotkeys, zoom, auto-save)
├── SpritePaintOverlay.java     # Widget detection, highlighting, override rendering
├── SpritePaintPanel.java       # Sidebar panel for managing saved overrides
├── PixelEditor.java            # Floating editor window with toolbar and color palette
├── PixelEditorPanel.java       # Pixel canvas with paint/erase/fill/eyedrop tools
├── SpriteTarget.java           # Data class for detected sprite targets
└── SpriteStore.java            # PNG persistence and in-memory cache
```

## Known Limitations

- **UI sprites only.** Ground items, 3D models, and world objects use a completely different rendering pipeline and are not editable with this approach.
- **Widget group coverage.** The `SCAN_GROUPS` array covers the most common interfaces but may miss some. You can add more group IDs to the array.
- **Item ID offset.** Depending on your RuneLite version, `widget.getItemId()` may or may not need a -1 offset. A comment in the code marks this.
- **Screen capture fallback.** For non-item sprites, the capture method grabs what's on screen including any overlays. Item sprites use the clean `ItemManager` render.
- **Thread safety.** The editor runs on Swing's EDT while the game runs on its own thread. Override application happens via the overlay's render pass, which is safe.

## Extending

To add support for more interfaces, add their widget group IDs to `SCAN_GROUPS` in `SpritePaintOverlay.java`. You can find group IDs using RuneLite's built-in Widget Inspector developer tool.

## Credits

Concept and design by Travis @ ICraft Creative Solutions. Built with RuneLite's plugin API.
