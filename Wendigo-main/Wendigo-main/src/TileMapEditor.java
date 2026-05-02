import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.*;
import java.awt.image.*;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;

/*
 * Note to Mr. Anthony: Partially missing documentation. It is 7 A.M and I have been up all night documenting code.
 * The player is not even likely to interact with the editor. Therefore some parts of this file are undocumented.
 * Forgive us Mr. Anthony.
 */

class TileMapEditor {
    public TileMap map;  // The tile map being edited

    static Font ED_FONT;  // The font used for displaying text in the editor
    static int ED_FONT_SIZE = 14;  // The font size for editor text

    private TileMapLayer currentLayer;  // The currently selected layer in the map
    private SpriteSheet currentSelectedSheet;  // The current sprite sheet selected for editing
    private JFileChooser fileChooser;  // File chooser for opening/saving files

    private ArrayList<Tile> mapSelection = new ArrayList<>();  // List of tiles selected for map editing

    private SpriteSheet sslSheet = null;  // The sprite sheet for the selection tool
    private String sslError = null;  // Error message if there is an issue with the selection tool
    private boolean sslVisible = false;  // Whether the selection tool is visible
    private BufferedImage sslImage = null;  // The image used for the selection tool
    private boolean sslIsNew = true;  // Flag indicating if the selection tool is newly created
    private String sslImgPath = null;  // Path to the image file used by the selection tool
    private boolean sslLeftPanelOpen = true;  // Whether the left panel of the editor is open
    private boolean sslNextSelectionIsCollidorRect = false;  // Whether the next selection should be a collider rectangle
    private boolean sslVisualizeCollidors = false;  // Whether to visualize colliders in the selection tool
    
    private double sslZoomTarget = 3.0;  // The target zoom level for the selection tool
    private double sslZoom = 3.0;  // The current zoom level for the selection tool
    private Vector2 sslScroll = new Vector2();  // The scroll position for the selection tool

    private ArrayList<Tile> sslSelection = new ArrayList<>();  // Tiles selected by the selection tool
    private Tile sslSelectedKeyTile;  // The key tile selected for the current operation
    private Vector2 sslSelectionMouseStart = null;  // Starting mouse position for the selection tool

    Panel sheetEdPanel = new Panel();
    
    // Resets the selection tool (ssl) to its initial state
    private void sslReset() {
        this.sslVisible = false;  // Hide the selection tool
        this.sslImgPath = null;  // Clear the image path for selection tool
        this.sslImage = null;  // Clear the selection image
        this.sslError = null;  // Reset any errors
        this.sslLeftPanelOpen = true;  // Keep the left panel open by default
        this.sslSelection = new ArrayList<>();  // Clear the selection
        this.sslSheet = null;  // Clear the selected sprite sheet
        this.sslSelectionMouseStart = null;  // Clear the starting mouse position for selection
        this.sslIsNew = true;  // Mark the selection as new
        this.sslScroll = new Vector2();  // Reset scroll position
        this.sslZoomTarget = 3.0;  // Default zoom target
        this.sslZoom = 3.0;  // Default zoom
        this.sslNextSelectionIsCollidorRect = false;  // Don't mark the next selection as a collider rectangle by default
        this.sslVisualizeCollidors = false;  // Don't visualize colliders by default
    }

    // Groups the selected tiles into a single "group" tile (used for multi-tile selections)
    private void SSLGroupSelection() {
        // Ensure there are at least two selected tiles to form a group
        if (this.sslSelection.size() < 2) return;

        int topLeftX = Integer.MAX_VALUE, topLeftY = Integer.MAX_VALUE;
        int bottomRightX = Integer.MIN_VALUE, bottomRightY = Integer.MIN_VALUE;
        
        Tile topLeftTile = null;
        Tile bottomRightTile = null;

        // Loop through selected tiles to find the boundaries of the selection (top-left and bottom-right)
        for (Tile t : this.sslSelection) {
            if (t.x < topLeftX) {
                topLeftTile = t;
                topLeftX = t.x;
            }
            if (t.y < topLeftY) {
                topLeftY = t.y;
            }
            if (t.x > bottomRightX) {
                bottomRightTile = t;
                bottomRightX = t.x;
            }
            if (t.y > bottomRightY) {
                bottomRightY = t.y;
            }
        }

        // If we found the top-left and bottom-right tiles, group the selection
        if (topLeftTile != null && bottomRightTile != null) {
            // Clear tiles within the defined rectangle
            for (int x = topLeftX; x <= bottomRightX; x++) {
                for (int y = topLeftY; y <= bottomRightY; y++) {
                    this.sslSheet.tiles.set(y * this.sslSheet.numTilesX + x, new Tile(x, y, this.sslSheet, -1));
                }
            }

            // Update the top-left tile with the calculated width and height of the group
            topLeftTile.x = topLeftX;
            topLeftTile.y = topLeftY;

            int tlIndex = topLeftTile.y * this.sslSheet.numTilesX + topLeftTile.x;
            topLeftTile.w = bottomRightX - topLeftX + 1;
            topLeftTile.h = bottomRightY - topLeftY + 1;
            topLeftTile.textureIndex = tlIndex;

            // Provide feedback to the user about the group tile created
            new Message("Created group tile of size " + topLeftTile.w + "x" + topLeftTile.h + ".", 4.0);

            // Place the grouped tile into the sheet at the calculated position
            this.sslSheet.tiles.set(tlIndex, topLeftTile);

            // Clear the current selection and add the new group tile
            this.sslSelection.clear();
            this.sslSelection.add(topLeftTile);
            this.sslSelectedKeyTile = topLeftTile;  // Set the selected key tile to the grouped tile
        }
    }

    // Ungroups a previously grouped tile selection into individual tiles
    private void SSLUnGroupSelection() {
        // Ensure there is only one tile selected (a grouped tile)
        if (this.sslSelection.size() != 1) return;

        // Get the single selected tile (the grouped one)
        Tile selection = this.sslSelection.get(0);

        // Remove the grouped tile from the sprite sheet by setting it to null
        this.sslSheet.tiles.set(selection.GetSheetIndex(), null);

        // Clear the current selection
        this.sslSelection.clear();

        // Loop through the original grouped tile's area and create new individual tiles
        for (int x = selection.x; x < selection.x + selection.w; x++) {
            for (int y = selection.y; y < selection.y + selection.h; y++) {
                // Calculate the index for the new tile
                int index = y * this.sslSheet.numTilesX + x;
                
                // Create a new tile at the current position (x, y)
                Tile newTile = new Tile(x, y, this.sslSheet, index);

                // If the tiles are purged and the tile is blank, skip it
                if (this.sslSheet.tilesPurged && newTile.IsBlank()) {
                    continue;
                }

                // Place the new tile back into the sheet
                this.sslSheet.tiles.set(index, newTile);

                // Add the new tile to the selection
                this.sslSelection.add(newTile);
            }
        }
    }
    
    private void SpriteSheetLoader(Graphics2D g) {
        if (this.sslLeftPanelOpen) {
            this.layersPanel.disabled = true;
            this.sheetsPanel.disabled = true;
        } else {
            this.layersPanel.disabled = false;
            this.sheetsPanel.disabled = false;    
        }

        if (this.sslSheet != null) {
            this.sslImage = this.sslSheet.GetCPUImage();
            this.sslImgPath = null;
        }

        if (Game.IsKeyPressed(KeyEvent.VK_H)) {
            this.sslLeftPanelOpen = !this.sslLeftPanelOpen;
        }

        if (this.sslImgPath != null) {
            try {
                BufferedImage loadedImage = ImageIO.read(new File(this.sslImgPath));
                if (loadedImage != null) {
                    this.sslImage = loadedImage;
                    this.sslError = null;
                } else {
                    throw new Exception("Tried to load image but got NULL.");
                }
            } catch (Exception e) {
                this.sslError = e.getLocalizedMessage();
            }
        }

        if (this.sslSheet == null && this.sslImage != null) {
            this.sslSheet = new SpriteSheet((BufferedImage)this.sslImage, this.sslImgPath, 16);
            this.sslSheet.name = new File(this.sslImgPath).getName();
        }
        
        double width = Game.WINDOW_WIDTH/2.0, height = Game.WINDOW_HEIGHT/2.0;
        sheetEdPanel.Begin(g, this.sheetsPanel.position.sub(new Vector2(width + 20, 0)), new Vector2(width, height));

        sheetEdPanel.Name("Sprite Sheet Editor");
        if (sheetEdPanel.CloseButton()) {
            this.sslReset();
            return;
        }

        sheetEdPanel.size.y -= Panel.PADDING;

        Vector2 prevPosition = sheetEdPanel.position.scale(1.0);
        Vector2 prevSize = sheetEdPanel.size.scale(1.0);

        if (this.sslError == null && this.sslImage != null && this.sslSheet != null) {
            Vector2 leftCSize = new Vector2();
            if (this.sslLeftPanelOpen) {
                leftCSize = sheetEdPanel.ListBegin("SSEdLeftC", new Vector2(), new Vector2(0.33, -40));
                    sheetEdPanel.size.x -= 2*Panel.PADDING;
                    sheetEdPanel.size.y -= Panel.PADDING;
    
                    sheetEdPanel.EntryBegin("Sprite Sheet Properties");
                    sheetEdPanel.EntryEnd();
    
                    sheetEdPanel.ListBegin("SSEditorProperties", new Vector2(), new Vector2(1.0, 0.5));
                        sheetEdPanel.EntryBegin("Tile Size: ");
                        
                        if (sheetEdPanel.EntryButton("Update & Reset")) {
                            this.sslSheet.UpdateTilesSize();
                        }
                        this.sslSheet.tileSize = (int)sheetEdPanel.EntrySlider(this.sslSheet.tileSize, 2, 128);
    
                        sheetEdPanel.EntryEnd();
    
                        sheetEdPanel.EntryBegin("Grid Visibiltiy: ");
                        if (sheetEdPanel.EntryButton("Toggle")) {
                            this.sslGridVisible = !this.sslGridVisible;
                        }
                        sheetEdPanel.EntryEnd();
    
                        sheetEdPanel.EntryBegin("Auto Purge: ");
                        sheetEdPanel.nextButtonDisabled = this.sslSheet.tilesPurged;
                        if (sheetEdPanel.EntryButton("Purge Blank Tiles")) {
                            this.sslSheet.PurgeBlankTiles();
                        }
                        sheetEdPanel.EntryEnd();
    
                        sheetEdPanel.EntryBegin("Group Selection: ");
                        sheetEdPanel.nextButtonDisabled = this.sslSelection.size() < 2;
                        if (!sheetEdPanel.nextButtonDisabled && Game.IsKeyPressed(KeyEvent.VK_G) && Game.IsKeyDown(KeyEvent.VK_SHIFT)) {
                            this.SSLGroupSelection();
                        }
                        if (sheetEdPanel.EntryButton("Group")) {
                            this.SSLGroupSelection();
                        }
    
                        sheetEdPanel.nextButtonDisabled = this.sslSelection.size() == 1 && !this.sslSelection.get(0).IsCompoundTile();
                        if (sheetEdPanel.EntryButton("Ungroup")) {
                            this.SSLUnGroupSelection();
                        }
                        sheetEdPanel.EntryEnd();

                        sheetEdPanel.EntryBegin("Sheet Alpha: ");
                        sheetEdPanel.nextButtonHighlight = this.sslSheet.hasAlpha;
                        if (sheetEdPanel.EntryButton(this.sslSheet.hasAlpha ? "Enabled" : "Disabled")) {
                            this.sslSheet.SetHasAlpha(!this.sslSheet.hasAlpha);
                        }
    
                        sheetEdPanel.EntryEnd();

                        sheetEdPanel.EntryBegin("View all Collidors: ");
                        sheetEdPanel.nextButtonHighlight = this.sslVisualizeCollidors;
                        if (sheetEdPanel.EntryButton(this.sslVisualizeCollidors ? "Enabled" : "Disabled")) {
                            this.sslVisualizeCollidors = !this.sslVisualizeCollidors;
                        }
    
                        sheetEdPanel.EntryEnd();
                    sheetEdPanel.ListEnd();
    
                    sheetEdPanel.EntryBegin("Tile Properties");
                    sheetEdPanel.EntryEnd();
                    
                    /* If no prime tile is selected but we have a group, just select something. */
                    if (this.sslSelectedKeyTile == null && this.sslSelection.size() > 0) {
                        this.sslSelectedKeyTile = this.sslSelection.get(0);
                    }

                    sheetEdPanel.ListBegin("SSTileProperties", new Vector2(), new Vector2(1.0, 1.0));
                    if (this.sslSelection.size() == 1) {
                        Tile t = this.sslSelection.get(0);

                        sheetEdPanel.EntryBegin("Update in Map");
                        if (sheetEdPanel.EntryButton("Update")) {
                            int numUpdated = 0;

                            for (TileMapLayer l : this.map.layers) {
                                for (Tile mapT : l.tiles) {
                                    if (mapT.textureSheet == t.textureSheet && mapT.textureIndex == t.textureIndex
                                            && mapT.w == t.w && mapT.h == t.h) {
                                        mapT.Set(t);
                                        numUpdated++;
                                    }
                                }
                            }

                            new Message("Updated " + numUpdated + " similar tiles to selected tile.");
                        }
                        sheetEdPanel.EntryEnd();

                        sheetEdPanel.EntryBegin("Collidable");

                        sheetEdPanel.nextButtonHighlight = (t.collidable);
                        if (sheetEdPanel.EntryButton(t.collidable ? "Yes" : "No")) {
                            t.collidable = !t.collidable;
                        }

                        sheetEdPanel.EntryEnd();

                        if (t.collidable) {
                            sheetEdPanel.EntryBegin("Collider Offset X");
                            t.collidorPos.x = sheetEdPanel.EntrySlider(t.collidorPos.x, 0, 1);
                            sheetEdPanel.EntryEnd();
                            
                            sheetEdPanel.EntryBegin("Collider Offset Y");
                            t.collidorPos.y = sheetEdPanel.EntrySlider(t.collidorPos.y, 0, 1);
                            sheetEdPanel.EntryEnd();
                            
                            sheetEdPanel.EntryBegin("Collider Size X");
                            t.collidorSize.x = sheetEdPanel.EntrySlider(t.collidorSize.x, 0, 1);
                            sheetEdPanel.EntryEnd();

                            sheetEdPanel.EntryBegin("Collider Size Y");
                            t.collidorSize.y = sheetEdPanel.EntrySlider(t.collidorSize.y, 0, 1);
                            sheetEdPanel.EntryEnd();

                            sheetEdPanel.EntryBegin("Draw Collidor");
                            sheetEdPanel.nextButtonDisabled = (this.sslNextSelectionIsCollidorRect);
                            if (sheetEdPanel.EntryButton("Draw")) {
                                this.sslNextSelectionIsCollidorRect = true;
                            }

                            sheetEdPanel.EntryEnd();
                        }

                        // Short cuts to input collidor
                        if (Game.IsKeyPressed(KeyEvent.VK_C) && Game.IsKeyDown(KeyEvent.VK_SHIFT)) {
                            t.collidable = true;
                            this.sslNextSelectionIsCollidorRect = true;
                        }

                        // Animations
                        sheetEdPanel.EntryBegin("Animated");

                        sheetEdPanel.nextButtonHighlight = (t.animated);
                        if (sheetEdPanel.EntryButton(t.animated ? "Yes" : "No")) {
                            t.animated = !t.animated;
                        }

                        sheetEdPanel.EntryEnd();

                        if (t.animated) {
                            sheetEdPanel.EntryBegin("Num Frames X");
                            t.animNumFramesX = (int)Math.round(sheetEdPanel.EntrySlider(t.animNumFramesX, 1, 15));
                            sheetEdPanel.EntryEnd();

                            sheetEdPanel.EntryBegin("Num Frames Y");
                            t.animNumFramesY = (int)Math.round(sheetEdPanel.EntrySlider(t.animNumFramesY, 1, 15));
                            sheetEdPanel.EntryEnd();

                            sheetEdPanel.EntryBegin("FPS");
                            t.animFPS = (int)Math.round(sheetEdPanel.EntrySlider(t.animFPS, 1, 60));
                            sheetEdPanel.EntryEnd();
                        }
                    }

                    if (this.sslSelection.size() == 1) {
                        Tile t = this.sslSelection.get(0);
                        sheetEdPanel.EntryBegin("Tile Tags");
                        if (sheetEdPanel.EntryButton("Add")) {
                            this.enteringNewTag = true;
                        }
                        if (this.enteringNewTag == true) {
                            if (Panel.InputField("Enter tag:", null)) {
                                String tag = Panel.inputInput;
                                if (tag.contains(",")) {
                                    new Message("Tag cannot have commas in them.", true);
                                } else {
                                    t.tags.add(tag);
                                }
                                this.enteringNewTag = false;
                            }
                        }
                        sheetEdPanel.EntryEnd();

                        Iterator<String> iterator = t.tags.iterator();
                        while (iterator.hasNext()) {
                            String tag = iterator.next();

                            sheetEdPanel.EntryBegin("Tag: `" + tag + "`");

                            if (sheetEdPanel.EntryButton("Delete")) {
                                iterator.remove(); // Remove method to avoid exception
                            }
                            
                            sheetEdPanel.EntryEnd();
                        }
                    }
                    sheetEdPanel.ListEnd();

    
                    if (sheetEdPanel.Button("Cancel", new Vector2(Panel.PADDING, 0), new Vector2(0, 40))) {
                        this.sslReset();
                        return;
                    }
    
                    if (sheetEdPanel.Button(this.sslIsNew ? "Create Sheet" : "Save Sheet", new Vector2(sheetEdPanel.lastButtonSize.x + 4*Panel.PADDING, 0), new Vector2(0, 40))) {
                        if (this.sslIsNew) {
                            this.map.ownedSheets.add(this.sslSheet);
                        }
    
                        new Message("Added sprite sheet: `" + this.sslSheet.name + "` to map.", 5.0);
                        this.sslReset();
    
                        return;
                    }
                sheetEdPanel.ListEnd();
            }

            prevPosition.x += leftCSize.x + Panel.PADDING;
            prevSize.x -= leftCSize.x + 2*Panel.PADDING;

            sheetEdPanel.position = prevPosition;
            sheetEdPanel.size = prevSize;

            sheetEdPanel.ListBegin("spriteSheetEdSheet", new Vector2(0, 0), new Vector2(1.0, 1.0));

            AffineTransform prevTrans = g.getTransform();
            Vector2 imagePos = new Vector2(-this.sslImage.getWidth(null) / 2.0, -this.sslImage.getHeight(null) / 2.0);
            boolean hovering = Vector2.AABBContainsPoint(sheetEdPanel.position, sheetEdPanel.size, Game.worldMousePos);

            // Update smooth zoom
            this.sslZoom = Vector2.lerpFRI(this.sslZoom, this.sslZoomTarget, 0.99, Game.deltaTime);

            // Apply transformations
            g.translate(this.sslScroll.x, this.sslScroll.y);
            g.translate(sheetEdPanel.position.x + sheetEdPanel.size.x / 2.0, sheetEdPanel.position.y + sheetEdPanel.size.y / 2.0);
            g.scale(this.sslZoom, this.sslZoom);

            AffineTransform currentTransform = g.getTransform();
            
            // Transform the mouse position relative to the current view
            Point relativeMousePos = new Point();
            try {
                prevTrans.transform(
                    new Point((int) Game.worldMousePos.x, (int) Game.worldMousePos.y),
                    relativeMousePos
                );
                currentTransform.inverseTransform(
                    relativeMousePos,
                    relativeMousePos
                );
                // relativeMousePos = new Point((int) Game.worldMousePos.x, (int) Game.worldMousePos.y);
            } catch (Exception e) {
                e.printStackTrace(); // Handle any exceptions from inverse transform
            }
            if (hovering) {
                // Adjust zoom based on scroll input
                this.sslZoomTarget -= Game.deltaScroll * 0.06;

                // Handle panning (mouse drag with middle button)
                if (Game.IsMousePressed(MouseEvent.BUTTON3) && this.sslMovMouseStart == null) {
                    this.sslMovMouseStart = Game.worldMousePos.scale(1.0);
                    this.sslMovInitialScroll = this.sslScroll.scale(1.0);
                }

                if (this.sslMovMouseStart != null) {
                    Vector2 delta = this.sslMovMouseStart.sub(Game.worldMousePos);
                    this.sslScroll = this.sslMovInitialScroll.sub(delta);
                    Game.currentCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
                }

                if (Game.IsMouseReleased(MouseEvent.BUTTON3)) {
                    this.sslMovMouseStart = null;
                }

                if (Game.IsMousePressed(MouseEvent.BUTTON1)) {
                    if (!this.sslNextSelectionIsCollidorRect && !Game.IsKeyDown(KeyEvent.VK_CONTROL)) {
                        this.sslSelection.clear();
                    }
                    this.sslSelectionMouseStart = new Vector2(relativeMousePos.x, relativeMousePos.y);
                }
                if (Game.IsMouseReleased(MouseEvent.BUTTON1)) {
                    this.sslSelectionMouseStart = null;

                    if (this.sslNextSelectionIsCollidorRect) {
                        this.sslNextSelectionIsCollidorRect = false;
                    }
                }
            }

            Rectangle selectionRectangle = new Rectangle();
            if (this.sslSelectionMouseStart != null) {
                // Calculate the initial rectangle with potentially negative width/height
                int startX = (int) this.sslSelectionMouseStart.x;
                int startY = (int) this.sslSelectionMouseStart.y;
                int endX = (int) relativeMousePos.x;
                int endY = (int) relativeMousePos.y;

                // Normalize the rectangle to ensure positive width and height
                int normalizedX = Math.min(startX, endX);
                int normalizedY = Math.min(startY, endY);
                int normalizedWidth = Math.abs(endX - startX) + 1;
                int normalizedHeight = Math.abs(endY - startY) + 1; // Add one to always select

                // Set the normalized rectangle
                selectionRectangle.setBounds(normalizedX, normalizedY, normalizedWidth, normalizedHeight);

                if (!this.sslNextSelectionIsCollidorRect) {
                    this.sslSelection.clear();
                }
            }

            if (this.sslNextSelectionIsCollidorRect) {
                if (this.sslSelection.size() == 1) {
                    Tile t = this.sslSelection.get(0);

                    Rectangle tileRect = new Rectangle(
                        (int)(imagePos.x + t.x * this.sslSheet.tileSize),
                        (int)(imagePos.y + t.y * this.sslSheet.tileSize),
                        this.sslSheet.tileSize * t.w,
                        this.sslSheet.tileSize * t.h
                    );

                    // Normalize collidor position within tileRect
                    t.collidorPos.x = ((double)selectionRectangle.x - (double)tileRect.x)/(double)tileRect.width;
                    t.collidorPos.y = ((double)selectionRectangle.y - (double)tileRect.y)/(double)tileRect.height;

                    // Normalize collidor size within tileRect
                    t.collidorSize.x = (double)selectionRectangle.width / (double)tileRect.width;
                    t.collidorSize.y = (double)selectionRectangle.height / (double)tileRect.height;
                } else {
                    new Message("[ERROR]: Attempted to set collision rect by drawing but something went wrong.", true);
                }
            }

            for (int y = 0; y < this.sslSheet.numTilesY; y++) {
                for (int x = 0; x < this.sslSheet.numTilesX; x++) {
                    Tile t = this.sslSheet.tiles.get(y * this.sslSheet.numTilesX + x);
                    if (t != null && !t.IsNull()) {
                        Rectangle tileRect = new Rectangle((int)(imagePos.x + x*this.sslSheet.tileSize),
                                                           (int)(imagePos.y + y*this.sslSheet.tileSize), 
                                                           this.sslSheet.tileSize*t.w, this.sslSheet.tileSize*t.h);


                        boolean selectionPurposeReserved = this.sslNextSelectionIsCollidorRect;
                        if (!selectionPurposeReserved && (selectionRectangle.intersects(tileRect) || tileRect.intersects(selectionRectangle))) {
                            this.sslSelection.add(t);

                            if (tileRect.contains(relativeMousePos)) {
                                this.sslSelectedKeyTile = t;
                            }
                        }

                        t.Draw(g, tileRect.x, tileRect.y, tileRect.width, tileRect.height);
                    }
                }
            }

            for (int y = 0; y < this.sslSheet.numTilesY; y++) {
                for (int x = 0; x < this.sslSheet.numTilesX; x++) {
                    Tile t = this.sslSheet.tiles.get(y * this.sslSheet.numTilesX + x);
                    if (t == null || t.IsNull())
                        continue;

                    Color tileOutlineColor = new Color(175, 50, 200, 100);
                    Rectangle tileRectangle = new Rectangle();

                    boolean drawOutline = this.sslGridVisible;
                    boolean hoveringTile = false;
                    boolean selected = (this.sslSelection.indexOf(t) != -1);
                    boolean lastSelected = this.sslSelectedKeyTile == t;
                    boolean drawCollidor = false;

                    tileRectangle.x = (int)(imagePos.x + x * this.sslSheet.tileSize);
                    tileRectangle.y = (int)(imagePos.y + y * this.sslSheet.tileSize);
                    tileRectangle.width = this.sslSheet.tileSize * t.w;
                    tileRectangle.height = this.sslSheet.tileSize * t.h;

                    if (tileRectangle.contains(relativeMousePos)) {
                        tileOutlineColor = new Color(175, 50, 200);
                        hoveringTile = true;
                        drawOutline = true;
                    }

                    if (selected) {
                        tileOutlineColor = Panel.BUTTON_HILI_BG;
                        drawOutline = true;

                        if (lastSelected) {
                            tileOutlineColor = new Color(91, 207, 117);
                        }

                    }
                    if (t.collidable && (selected || this.sslVisualizeCollidors)) {
                        drawCollidor = true;
                    }

                    g.setColor(tileOutlineColor);
                    if (drawOutline) {
                        g.setStroke(new BasicStroke((hoveringTile || lastSelected) ? 0.6f : 0.25f));

                        GG.drawRect(tileRectangle.x, tileRectangle.y, tileRectangle.width, tileRectangle.height);
                    }

                    if (drawCollidor) {
                        g.setColor(Color.RED);
                        
                        GG.drawRect(tileRectangle.x + tileRectangle.width*t.collidorPos.x,
                                    tileRectangle.y + tileRectangle.height*t.collidorPos.y,
                                    tileRectangle.width*t.collidorSize.x,
                                    tileRectangle.height*t.collidorSize.y);
                    }
                }
            }

            g.setColor(new Color(175, 50, 200, 100));
            g.fillRect(selectionRectangle.x, selectionRectangle.y, selectionRectangle.width, selectionRectangle.height);

            g.setTransform(prevTrans);

            sheetEdPanel.ListEnd();
        } else {
            sheetEdPanel.EntryBegin("Error: " + this.sslError);
            sheetEdPanel.EntryEnd();
        }

        sheetEdPanel.End();
    }

    private double ssDisplayZoom = 17.0;
    private boolean sslGridVisible = true;
    private Vector2 sslMovMouseStart = null;
    private Vector2 sslMovInitialScroll = null;

    public TileMapEditor(TileMap m) {
        this.map = m;

        if (m.layers.size() > 0) {
            this.currentLayer = m.layers.get(0);
        } else {
            this.currentLayer = null;
        }

        LoadInterfaceFont();
    }

    private static void LoadInterfaceFont() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            Font f = Font.createFont(Font.TRUETYPE_FONT, new File("res/fonts/RobotoMono-Regular.ttf"));
            ge.registerFont(f);
            TileMapEditor.ED_FONT =  new Font(f.getFontName(), Font.PLAIN, ED_FONT_SIZE);
        } catch (FontFormatException | IOException e) {
            new Message("[ERROR]: Error loading editor font from file! Your game may be borked.", 20.0, true);
            TileMapEditor.ED_FONT = new Font("Courier New", Font.PLAIN, ED_FONT_SIZE);
        }
    }
    
    Panel sheetsPanel = new Panel();
    Panel layersPanel = new Panel();
    Panel tools = new Panel();
    Panel layerProperties;

    public void LayerPropertiesPanel(Graphics2D g) {
        final Vector2 initPanelSize = new Vector2(225, 200);

        layerProperties.Begin(g, this.layersPanel.position.sub(new Vector2(initPanelSize.x + 20, 0)), initPanelSize);

        layerProperties.Name("`" + this.currentLayer.name + "` Properties");
        if (layerProperties.CloseButton()) {
            this.layerProperties = null;
            return;
        }

        layerProperties.ListBegin("Properties", new Vector2(0, Panel.PADDING), new Vector2(1.0, 1.0));
            layerProperties.EntryBegin("Is Ground Layer");
            layerProperties.nextButtonHighlight = this.currentLayer.isGroundLayer;
            if (layerProperties.EntryButton(this.currentLayer.isGroundLayer ? "Yes" : "No")) {
                if (!this.currentLayer.isGroundLayer) {
                    for (TileMapLayer l : this.map.layers) {
                        l.isGroundLayer = false;
                    }
                    this.currentLayer.isGroundLayer = true;
                } else {
                    this.currentLayer.isGroundLayer = false;
                }
            }    
            layerProperties.EntryEnd();

            layerProperties.EntryBegin("Visalize All Collidors");
            layerProperties.nextButtonHighlight = this.currentLayer.visualizeCollidors;
            if (layerProperties.EntryButton(this.currentLayer.visualizeCollidors ? "Yes" : "No")) {
                this.currentLayer.visualizeCollidors = !this.currentLayer.visualizeCollidors;
            }
            layerProperties.EntryEnd();
        layerProperties.ListEnd();

        layerProperties.End();
    }

    public void Update(double dt) {
        if (this.map != null) {
            if (Game.IsKeyPressed(KeyEvent.VK_S) && Game.IsKeyDown(KeyEvent.VK_CONTROL)) {
                this.map.Save("./res/map.wmap");
            }

            if (Game.IsKeyPressed(KeyEvent.VK_Z) && Game.IsKeyDown(KeyEvent.VK_CONTROL)) {
                this.map.LoadFromFile("./res/map.wmap");
                this.sslReset();
                this.mapSelection.clear();
                this.currentTool = "Select";
            }
        }
    }

    String[] toolButtons = {"Select", "Paint", "Fill", "Delete", "Urethra"};
    String currentTool = "Select";

    private boolean mapCanSelectBlank = false;
    private double paintBrushRadius = 1;
    private double paintBrushStrokeChance = 100;
    private boolean paintBrushRandomTiles = false;
    private boolean paintBrushIsEraser = false;
    private boolean paintBrushCanPaintBlankTiles = true;
    private Vector2 mapSelectionMouseStart = null;

    private boolean enteringNewTag = false;

    public void Draw(Graphics2D g) throws NoninvertibleTransformException {
        sheetEdPanel.disabled = false;
        sheetsPanel.disabled = false;
        layersPanel.disabled = false;
        tools.disabled = false;

        Panel.context = "Editor";

        if (this.layerProperties != null) {
            this.LayerPropertiesPanel(g);
        }

        if (this.map != null) {
            if (this.currentLayer != null && !this.map.layers.contains(this.currentLayer)) {
                this.currentLayer = null;
            }

            if (this.currentLayer == null) {
                tools.disabled = true;
            }

            for (int y = 0; y < this.map.height; y++) {
                for (int x = 0; x < this.map.height; x++) {
                    Color tileOutlineColor = new Color(100, 100, 100, 40);

                    Vector2 position = this.map.LocalToWorldVectorScalar(new Vector2(x, y));
                    Vector2 tileSize = this.map.LocalToWorldVectorScalar(new Vector2(1, 1));
                    
                    g.setColor(tileOutlineColor);
                    GG.drawRect(position, tileSize);
                }
            }
            if (this.currentLayer != null && tools.windowPosition != null) {
                boolean mouseInPanel = Vector2.AABBContainsPoint(tools.windowPosition, tools.windowSize, Game.worldMousePos);

                Rectangle selectionRectangle = new Rectangle();

                if (!mouseInPanel && this.currentTool.equals("Select") && Game.IsMousePressed(MouseEvent.BUTTON3)) {
                    this.mapSelection.clear();
                }
                if (!mouseInPanel && this.currentTool.equals("Select") && Game.IsMousePressed(MouseEvent.BUTTON1)) {
                    if (this.mapSelectionMouseStart == null) {
                        mapSelectionMouseStart = Game.worldMousePos.scale(1.0);
                    }
                } else if (Game.IsMouseReleased(MouseEvent.BUTTON1)) {
                    this.mapSelectionMouseStart = null;
                }

                if (this.mapSelectionMouseStart != null) {
                    // Calculate the initial rectangle with potentially negative width/height
                    int startX = (int) this.mapSelectionMouseStart.x;
                    int startY = (int) this.mapSelectionMouseStart.y;
                    int endX = (int) Game.worldMousePos.x;
                    int endY = (int) Game.worldMousePos.y;

                    // Normalize the rectangle to ensure positive width and height
                    int normalizedX = Math.min(startX, endX);
                    int normalizedY = Math.min(startY, endY);
                    int normalizedWidth = Math.abs(endX - startX) + 2;
                    int normalizedHeight = Math.abs(endY - startY) + 2; // Add one or two to always select

                    // Set the normalized rectangle
                    selectionRectangle.setBounds(normalizedX, normalizedY, normalizedWidth, normalizedHeight);
                    
                    this.mapSelection.clear();
                }

                g.setColor(new Color(99, 134, 224, 100));
                g.fillRect(selectionRectangle.x, selectionRectangle.y, selectionRectangle.width, selectionRectangle.height);
                g.drawRect(selectionRectangle.x, selectionRectangle.y, selectionRectangle.width-1, selectionRectangle.height-1); // Border

                for (Tile t : this.currentLayer.tiles) {
                    boolean isNull = t.IsNull();

                    if (isNull && this.currentTool.equals("Select") && !this.mapCanSelectBlank) continue;

                    Color tileOutlineColor = new Color(60, 106, 171, 190);
                    Rectangle tileRectangle = new Rectangle();
                    
                    Vector2 tilePosition = this.map.LocalToWorldVectorScalar(new Vector2(t.x, t.y));
                    Vector2 tileSize = this.map.LocalToWorldVectorScalar(new Vector2(t.w, t.h));
                    
                    tileRectangle.x = (int)(tilePosition.x);
                    tileRectangle.y = (int)(tilePosition.y);
                    tileRectangle.width = (int)(tileSize.x);
                    tileRectangle.height = (int)(tileSize.y);
                    
                    if (tileRectangle.intersects(selectionRectangle) || selectionRectangle.intersects(tileRectangle)) {
                        if ((isNull && this.mapCanSelectBlank) || (!isNull)) {
                            this.mapSelection.add(t);
                        } 
                    }
                    boolean drawOutline = true;
                    boolean drawCollidor = this.currentLayer.visualizeCollidors;
                    boolean selected = (this.mapSelection.indexOf(t) != -1);

                    if (this.currentTool.equals("Paint")) {
                        Vector2 mouseInMap = this.map.WorldToLocalVector(Game.worldMousePos);

                        mouseInMap.x = Math.floor(mouseInMap.x) + 0.5;
                        mouseInMap.y = Math.floor(mouseInMap.y) + 0.5;

                        Vector2 tileCentre = new Vector2(t.x + 0.5, t.y + 0.5);
                        double distance = tileCentre.distance(mouseInMap);
                        
                        if (!mouseInPanel && distance < 0.5*Math.floor(this.paintBrushRadius)) {
                            if ((isNull && this.paintBrushCanPaintBlankTiles) || (!isNull)) {
                                tileOutlineColor = this.paintBrushIsEraser ? new Color(196, 49, 78) : new Color(49, 196, 103);
                                drawOutline = true;

                                if (Game.IsMouseDown(MouseEvent.BUTTON1)) {
                                    // Place tile
                                    if (this.paintBrushIsEraser) {
                                        t.Clear();
                                    } else {
                                        double strokeChance = Math.random();
                                        if (strokeChance <= Math.pow(this.paintBrushStrokeChance/100.0, 3.f)) {
                                            if (!this.paintBrushRandomTiles) {
                                                if (this.sslSelectedKeyTile != null) {
                                                    t.Set(this.sslSelectedKeyTile);
                                                }
                                                if (this.sslSelection.size() > 1) {
                                                    t.Set(this.sslSelection.get(0));
                                                }
                                            } else {
                                                if (this.sslSelection.size() > 1) {
                                                    int randomTileIndex = (int)(Math.random() * this.sslSelection.size());
                                                    Tile randomTile = this.sslSelection.get(randomTileIndex);
                                                    t.Set(randomTile);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (tileRectangle.contains(new Point((int)Game.worldMousePos.x, (int)Game.worldMousePos.y))) {
                            tileOutlineColor = new Color(80, 150, 250);
                            drawOutline = true;
                        }
                    }

                    if (!drawOutline && isNull) continue;
                    
                    if (selected) {
                        tileOutlineColor = Color.getHSBColor(223.f/360.f, (26.f + (float)Math.sin(4.0 * Game.now()) * 100.f)/360.f, 300.f/360.f);
                        drawOutline = true;
                    }
    
                    if (drawOutline) {
                        g.setColor(tileOutlineColor);
                        g.setStroke(new BasicStroke(selected ? 0.6f : 0.25f));
                        GG.drawRect(tileRectangle.x, tileRectangle.y, tileRectangle.width, tileRectangle.height);
                    }

                    if (drawCollidor) {
                        g.setColor(Color.RED);
                        
                        GG.drawRect(tileRectangle.x + tileRectangle.width*t.collidorPos.x,
                                    tileRectangle.y + tileRectangle.height*t.collidorPos.y,
                                    tileRectangle.width*t.collidorSize.x,
                                    tileRectangle.height*t.collidorSize.y);
                    }
                }
            }
        }

        tools.Begin(g, new Vector2(-800, 100), new Vector2(400, 300));
        tools.Name("Tools");

        tools.ListBegin("Tools", new Vector2(), new Vector2(1.0, 41.0));
        tools.FlowLayoutBegin();
        
        for (String tool : toolButtons) {
            String text = tool;
            
            Rectangle buttonDims = tools.CalculateButtonDims(text, new Vector2(), new Vector2());
            Vector2 buttonPosition = tools.FlowLayoutAdd(new Vector2(buttonDims.width, buttonDims.height));
            
            tools.nextButtonHighlight = (tool == this.currentTool);
            tools.nextButtonAbsPos = true;

            if (tool.equals("Fill") || tool.equals("Delete")) {
                tools.nextButtonDisabled = this.mapSelection.size() <= 0;
            }

            if (tools.Button(text, buttonPosition, new Vector2(buttonDims.width, buttonDims.height))) {
                    // Its been clicked
                this.currentTool = tool;

                if (this.currentTool.equals("Select")) {
                    this.mapSelectionMouseStart = null;
                    this.mapSelection.clear();
                }

                /* Editor Actions */
                if (this.currentTool.equals("Fill")) {
                    if (this.mapSelection.size() > 0 && this.sslSelection.size() > 0) {
                        for (Tile t : this.mapSelection) {
                            int randomIndex = (int)(Math.random() * this.sslSelection.size());
                            Tile randomTile = this.sslSelection.get(randomIndex);
                            t.Set(randomTile);
                        }
                    }
                    this.currentTool = "Select";
                } else if (this.currentTool.equals("Delete")) {
                    if (this.mapSelection.size() > 0) {
                        for (Tile t : this.mapSelection) {
                            t.Clear();
                        }
                    }
                    this.currentTool = "Select";
                }
            }
        }
            
        tools.FlowLayoutEnd();
        tools.ListEnd();
            
        tools.ListBegin("Tools Properties", new Vector2(), new Vector2(1.0, 1.0));
            tools.EntryBegin("Tool Properties");
            tools.EntryEnd();
            if (this.currentTool.equals("Select")) {
                tools.EntryBegin("Blank Tiles Selectable");
                tools.nextButtonHighlight = this.mapCanSelectBlank;
                if (tools.EntryButton(this.mapCanSelectBlank ? "Yes" : "No")) {
                    this.mapCanSelectBlank = !this.mapCanSelectBlank;
                }
                tools.EntryEnd();
            } else if (this.currentTool.equals("Paint")) {
                tools.EntryBegin("Brush Radius");
                this.paintBrushRadius = tools.EntrySlider(this.paintBrushRadius, 1.0, 15.0);
                tools.EntryEnd();

                tools.EntryBegin("Brush Stroke Chance");
                this.paintBrushStrokeChance = tools.EntrySlider(this.paintBrushStrokeChance, 0.0, 100.0);
                tools.EntryEnd();

                tools.EntryBegin("Brush Pick Random");
                tools.nextButtonHighlight = this.paintBrushRandomTiles;
                if (tools.EntryButton(this.paintBrushRandomTiles ? "Yes" : "No")) {
                    this.paintBrushRandomTiles = !this.paintBrushRandomTiles;
                }
                tools.EntryEnd();

                tools.EntryBegin("Erase Mode");
                tools.nextButtonHighlight = this.paintBrushIsEraser;
                if (tools.EntryButton(this.paintBrushIsEraser ? "Yes" : "No")) {
                    this.paintBrushIsEraser = !this.paintBrushIsEraser;
                }
                tools.EntryEnd();

                tools.EntryBegin("Can Paint on Blank");
                tools.nextButtonHighlight = this.paintBrushCanPaintBlankTiles;
                if (tools.EntryButton(this.paintBrushCanPaintBlankTiles ? "Yes" : "No")) {
                    this.paintBrushCanPaintBlankTiles = !this.paintBrushCanPaintBlankTiles;
                }
                tools.EntryEnd();
            }
            tools.ListBegin("MSelectionProp", new Vector2(), new Vector2(1.0, 300));
                tools.EntryBegin("Selection Properties");
                tools.EntryEnd();

                if (this.mapSelection.size() == 1) {
                    Tile t = this.mapSelection.get(0);

                    tools.EntryBegin("Selection Tags");
                    if (tools.EntryButton("Add")) {
                        this.enteringNewTag = true;
                    }
                    if (this.enteringNewTag == true) {
                        if (Panel.InputField("Enter tag:", null)) {
                            t.tags.add(Panel.inputInput);
                            this.enteringNewTag = false;
                        }
                    }
                    tools.EntryEnd();
                    tools.ListBegin("SelectionTags", new Vector2(), new Vector2(1.0, 1.0));

                    Iterator<String> iterator = t.tags.iterator();
                    while (iterator.hasNext()) {
                        String tag = iterator.next();
                        if (tools.Button(tag, new Vector2(), new Vector2(1.0, 0.0))) {
                            iterator.remove(); // Remove method to avoid exception
                        }
                        tools.LayoutVertBAdded(0.0);
                    }
                    tools.ListEnd();
                }

                tools.EntryEnd();
            tools.ListEnd();
        tools.ListEnd();
                
        tools.End();
        
        if (this.currentTool.equals("Paint")) {
            layersPanel.disabled = true;
            sheetsPanel.disabled = true;
            sheetEdPanel.disabled = true;
        }
        
        layersPanel.Begin(g, new Vector2(tools.windowPosition.x, tools.windowPosition.y + tools.windowSize.y + 20), new Vector2(300, 300));
        
        layersPanel.Name("Layers");
        
        layersPanel.EntryBegin("Layers");
        if (layersPanel.EntryButton("New")) {
            TileMapLayer newLayer = new TileMapLayer(map, map.width, map.height);
            
            newLayer.name = "Layer " + (this.map.layers.size()+1);
            this.currentLayer = newLayer;
            this.mapSelection.clear();

            this.map.layers.add(newLayer);
        }
        
        layersPanel.nextButtonDisabled = (currentLayer == null);
        if (layersPanel.EntryButton("Delete")) {
            this.map.layers.remove(this.currentLayer);
            if (this.map.layers.size() == 0) {
                this.currentLayer = null;
                this.mapSelection.clear();
            } else {
                this.currentLayer = this.map.layers.get(this.map.layers.size()-1);
                this.mapSelection.clear();
            }
        }
        layersPanel.nextButtonDisabled = (currentLayer == null);
        if (layersPanel.EntryButton("Rename")) {
            this.currentLayer.name = null;
        }
        if (this.currentLayer != null && this.currentLayer.name == null) {
            if (Panel.InputField("Enter Name for Layer:", this.currentLayer.name)) {
                this.currentLayer.name = Panel.inputInput + "";
            }
        }

        layersPanel.nextButtonDisabled = (this.currentLayer == null) || (this.layerProperties != null);
        if (layersPanel.EntryButton("Edit")) {
            this.layerProperties = new Panel();
        }
        layersPanel.EntryEnd();
        
        layersPanel.ListBegin("Layers", new Vector2(), new Vector2(0, -40.0));   
            for (TileMapLayer layer : this.map.layers) {
                String buttonText = layer.name;

                if (layer.isGroundLayer) {
                    buttonText += " [GRND]";
                }

                if (layer == currentLayer) {
                    layersPanel.nextButtonHighlight = true;
                }
                if (layersPanel.Button(buttonText, new Vector2(), new Vector2(1.0, 0.0))) {
                    currentLayer = layer;
                }
                layersPanel.LayoutVertBAdded(0.0);
            }
        layersPanel.ListEnd();

        int currentIndex = this.map.layers.indexOf(this.currentLayer);
        
        layersPanel.nextButtonDisabled = (currentIndex == this.map.layers.size()-1);
        if (layersPanel.Button("Down", new Vector2(0.0, 0), new Vector2(0.5, 1.0))) {
            this.map.layers.remove(this.currentLayer);
            map.layers.add(currentIndex + 1, this.currentLayer);
        }
        
        layersPanel.nextButtonDisabled = (currentIndex == 0);
        if (layersPanel.Button("Up", new Vector2(0.5, 0), new Vector2(0.5, 1.0))) {
            this.map.layers.remove(this.currentLayer);
            map.layers.add(currentIndex - 1, this.currentLayer);
        }
        layersPanel.End();
            
            
        sheetsPanel.Begin(g, new Vector2(layersPanel.windowPosition.x, layersPanel.windowPosition.y + layersPanel.windowSize.y + 20), new Vector2(300, 300));
        sheetsPanel.Name("Sprite Sheets");
            
        sheetsPanel.EntryBegin("Sheets");
            if (sheetsPanel.EntryButton("Add New")) {
                if (this.fileChooser == null) {
                    this.sheetsPanel.disabled = true;

                    Game.ResetMouse();

                    this.fileChooser = new JFileChooser("./res");
                    this.fileChooser.setVisible(true);
                    
                    int r = this.fileChooser.showSaveDialog(null);
                    if (r == JFileChooser.APPROVE_OPTION) {
                        File f = this.fileChooser.getSelectedFile();
                        
                        this.sslReset();

                        this.sslImgPath = f.getPath();
                        this.sslVisible = true;
                        this.sslIsNew = true;
                    }
                    
                    Game.ResetMouse(); // Reseting mouse after file picker is neccesarry as it skips frames

                    this.fileChooser = null;
                }
            }
            
            sheetsPanel.nextSliderWidth = 100;
            this.ssDisplayZoom = sheetsPanel.EntrySlider(this.ssDisplayZoom, 0, 100);
        sheetsPanel.EntryEnd();

        Vector2 listSize = sheetsPanel.ListBegin("SpriteSheetList", new Vector2(), new Vector2(0, -(Panel.LINE_HEIGHT + 3*Panel.PADDING)));
            final double minDisplayWidth = 50, maxDisplayWidth = 250;
            Vector2 boxSize = new Vector2(Vector2.lerp(minDisplayWidth, maxDisplayWidth, this.ssDisplayZoom/100.0),
                                          Vector2.lerp(minDisplayWidth/0.75, maxDisplayWidth/0.75, this.ssDisplayZoom/100.0));

            if (Vector2.AABBContainsPoint(sheetsPanel.currentListTopLeft, listSize, Game.worldMousePos)) {
                if (!sheetsPanel.disabled && Game.IsMousePressed(MouseEvent.BUTTON1)) {
                    this.currentSelectedSheet = null;
                }
            }

            sheetsPanel.FlowLayoutBegin();
            
            for (SpriteSheet sheet : this.map.ownedSheets) {
                Rectangle imageArea = new Rectangle();
                Vector2 pos = sheetsPanel.FlowLayoutAdd(boxSize);
                double textAreaHeight = Panel.LINE_HEIGHT+Panel.PADDING;
                
                Shape prevClip = g.getClip();

                boolean hovering = Vector2.AABBContainsPoint(pos, boxSize, Game.worldMousePos);
                boolean selected = (this.currentSelectedSheet == sheet);

                if (!sheetsPanel.disabled && hovering) {
                    if (Game.IsMousePressed(MouseEvent.BUTTON1)) {
                        this.currentSelectedSheet = sheet;
                    }
                }

                g.setColor(selected ? Panel.BUTTON_HILI_BG : (hovering ? Panel.BUTTON_HOV_BG : Panel.BUTTON_BG));
                GG.fillRect(pos, boxSize);
    
                g.setColor(Panel.BUTTON_BORDER);
                GG.drawRect(pos, boxSize.sub(new Vector2(1)));
                
                imageArea.x = (int)(pos.x + Panel.PADDING);
                imageArea.y = (int)(pos.y + Panel.PADDING);
                
                imageArea.width = (int)(boxSize.x - 2*Panel.PADDING);
                imageArea.height = (int)(boxSize.y - textAreaHeight);
                
                g.setColor(Color.BLACK);
                GG.fillRect(imageArea.x, imageArea.y, imageArea.width, imageArea.height);

                g.clipRect((int)pos.x, (int)pos.y, (int)boxSize.x, (int)boxSize.y);

                // Calculate the aspect ratio of the image and the image area
                double imageAspect = (double) sheet.image.getWidth() / sheet.image.getHeight();
                double areaAspect = (double) imageArea.width / imageArea.height;

                // Determine the dimensions of the drawn image while maintaining the aspect ratio
                int drawWidth, drawHeight;
                if (imageAspect > areaAspect) {
                    // Image is wider than the area; scale based on width
                    drawWidth = imageArea.width;
                    drawHeight = (int) (drawWidth / imageAspect);
                } else {
                    // Image is taller than the area; scale based on height
                    drawHeight = imageArea.height;
                    drawWidth = (int) (drawHeight * imageAspect);
                }

                // Center the image within the imageArea
                int drawX = imageArea.x + (imageArea.width - drawWidth) / 2;
                int drawY = imageArea.y + (imageArea.height - drawHeight) / 2;

                // Draw the image
                g.drawImage(
                    sheet.image,
                    drawX, drawY, drawX + drawWidth, drawY + drawHeight, // Destination rectangle
                    0, 0, sheet.GetImage(g).getWidth(null), sheet.GetImage(g).getHeight(null),       // Source rectangle
                    null                                                // Image observer
                );

                sheetsPanel.nextLabelAbsPosition = true;
                sheetsPanel.CenteredLabel(sheet.name, pos.add(new Vector2(0, imageArea.height)), new Vector2(boxSize.x, textAreaHeight));

                g.setClip(prevClip);
            }

            sheetsPanel.FlowLayoutEnd();
        sheetsPanel.ListEnd();

        listSize = sheetsPanel.ListBegin("SpriteSheetListActions", new Vector2(), new Vector2(0.0, 0.0));
            sheetsPanel.FlowLayoutBegin();
            
            Vector2 buttonSize = new Vector2(sheetsPanel.size.x / 3.0 - 1.5*Panel.PADDING, listSize.y - 2*Panel.PADDING);
            Vector2 position = sheetsPanel.FlowLayoutAdd(buttonSize);
            
            sheetsPanel.nextButtonAbsPos = true;
            sheetsPanel.nextButtonDisabled = (this.currentSelectedSheet == null);
            if (sheetsPanel.Button("Delete", position, buttonSize)) {
                if (this.currentSelectedSheet != null) {
                    this.map.DeleteSheet(this.currentSelectedSheet);
                    this.currentSelectedSheet = null;
                }
            }

            position = sheetsPanel.FlowLayoutAdd(buttonSize);
            sheetsPanel.nextButtonAbsPos = true;
            sheetsPanel.nextButtonDisabled = (this.currentSelectedSheet == null);
            if (sheetsPanel.Button("Edit", position, buttonSize)) {
                this.sslReset();
                this.sslIsNew = false;
                this.sslSheet = this.currentSelectedSheet;
                this.sslVisible = true;
            }

            position = sheetsPanel.FlowLayoutAdd(buttonSize);
            sheetsPanel.nextButtonAbsPos = true;
            sheetsPanel.nextButtonDisabled = (this.currentSelectedSheet == null || (!(this.sslVisible == true && this.sslLeftPanelOpen == false) && (this.sslVisible)));
            if (sheetsPanel.Button("Open", position, buttonSize)) {
                this.sslReset();
                this.sslIsNew = false;
                this.sslSheet = this.currentSelectedSheet;
                this.sslLeftPanelOpen = false;
                this.sslVisible = true;
            }

            sheetsPanel.FlowLayoutEnd();
        sheetsPanel.ListEnd();

        sheetsPanel.End();
        
        if (sslVisible) {
            SpriteSheetLoader(g);
        } else {
            this.layersPanel.disabled = false;
            this.sheetsPanel.disabled = false;
        }
    }
}
