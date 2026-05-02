import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Arrays;

import javax.imageio.ImageIO;
import java.io.*;
import java.net.URI;
import java.awt.image.*;

// Class for loading and managing SpriteSheet. See: https://en.wikipedia.org/wiki/Texture_atlas
class SpriteSheet {
    public String name = "null"; // Name of the sprite sheet
    protected int tileSize; // Size of each tile

    protected int numTilesX; // Number of tiles in the X direction
    protected int numTilesY; // Number of tiles in the Y direction

    protected boolean tilesPurged = false; // Whether tiles have been purged

    protected BufferedImage image; // The original image
    protected VolatileImage GPUImage; // Optimized image for GPU

    protected boolean hasAlpha = false; // Whether the image has transparency

    protected String imagePath; // Path to the image

    ArrayList<Tile> tiles; // List of tiles in the sprite sheet

    // Returns the original CPU image
    public BufferedImage GetCPUImage() {
        return this.image;
    }

    // Returns the image for rendering (either GPU or CPU)
    public Image GetImage(Graphics2D g) {
        if (this.GPUImage != null && this.VolatileImageNeedsCreation(g)) {
            this.RenderGPUImage();
        }

        if (this.GPUImage != null) {
            return this.GPUImage; // Return GPU image if available
        }

        return (Image)this.image; // Otherwise, return the original image
    }

    // Renders the image to the GPU-optimized VolatileImage
    public void RenderGPUImage() {
        Graphics2D vg = (Graphics2D) this.GPUImage.createGraphics();
        vg.setComposite(AlphaComposite.Src); // Set blending mode
        vg.drawImage(this.image, 0, 0, null); // Draw the image
        vg.dispose(); // Dispose of the Graphics context
    }

    // Checks if the VolatileImage needs recreation
    public boolean VolatileImageNeedsCreation(Graphics2D g) {
        return this.GPUImage.contentsLost(); // Return true if the contents are lost
    }

    // Sets whether the sprite sheet has transparency (alpha)
    public void SetHasAlpha(boolean hasAlpha) {
        this.hasAlpha = hasAlpha;
        // Create a compatible VolatileImage with or without alpha
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                                    .getDefaultScreenDevice()
                                                    .getDefaultConfiguration();
        this.GPUImage = gc.createCompatibleVolatileImage(this.image.getWidth(), this.image.getHeight(), hasAlpha ? Transparency.BITMASK : Transparency.OPAQUE);
        this.RenderGPUImage(); // Render the image to the GPU image
    }

    // Initializes the sprite sheet with image data, path, and tile size
    private void Init(BufferedImage image, String imagePath, int tileSize) {
        this.tileSize = tileSize;
        this.image = image;
        this.imagePath = imagePath;

        int imageWidth = this.image.getWidth();
        int imageHeight = this.image.getHeight();

        this.numTilesX = imageWidth / tileSize; // Calculate number of tiles in X direction
        this.numTilesY = imageHeight / tileSize; // Calculate number of tiles in Y direction

        this.UpdateTilesSize(); // Update the size of tiles
        this.SetHasAlpha(false); // Set alpha transparency to false

        if (this.GPUImage == null) {
            new Message("[ERROR]: Error creating volatile image (GPU Image) for sprite sheet: `" + this.name + "`. Expect performance degradations.", true);
        } else {
            this.RenderGPUImage(); // Render the image to the GPU image
            this.GPUImage.setAccelerationPriority(1.0f); // Set high priority for GPU image acceleration
        }

        // Check if tile size is not divisible evenly by image dimensions
        if (imageWidth % tileSize != 0 || imageHeight % tileSize != 0) {
            new Message("Warning atlas tile size not equally divisible by it's width or height.", true);
        }
    }

    // Loads sprite sheet data from a file
    public void LoadFromFile(BufferedReader br, TileMap map) throws IOException {
        String name = TileMap.readString(br, "name");
        String imagePath = TileMap.readString(br, "image_path");
        int tileSize = TileMap.readInt(br, "tile_size");

        String deletedIndicies = TileMap.readString(br, "delted_tiles_indicies");
        int numModifiedTiles = TileMap.readInt(br, "num_modified_tiles");

        // Load and prepare the image
        File f = new File(imagePath);
        BufferedImage loadedImage = ImageIO.read(f);
        GraphicsConfiguration gc = GraphicsEnvironment
                                    .getLocalGraphicsEnvironment()
                                    .getDefaultScreenDevice()
                                    .getDefaultConfiguration();

        BufferedImage optimizedImage = gc.createCompatibleImage(
                                            loadedImage.getWidth(),
                                            loadedImage.getHeight(),
                                            Transparency.BITMASK
                                        );
        Graphics2D g2d = optimizedImage.createGraphics();
        g2d.drawImage(loadedImage, 0, 0, null);
        g2d.dispose();

        this.Init(optimizedImage, imagePath, tileSize);
        this.name = name;

        // Handle deleted tiles
        if (deletedIndicies != null) {
            String[] stringIndiciesRaw = deletedIndicies.split(",");
            String[] stringIndicies = Arrays.copyOfRange(stringIndiciesRaw, 0, stringIndiciesRaw.length - 1);

            for (String indexStr : stringIndicies) {
                int index = -1;
                try {
                    index = Integer.parseInt(indexStr);
                } catch (NumberFormatException e) {
                    System.err.println("[ERROR]: Error in loading tiles, expected index got: " + indexStr);
                    continue;
                }
                this.ClearTileAtIndex(index); // Clear the tile at index
            }
        }

        // Load modified tiles
        for (int i = 0; i < numModifiedTiles; i++) {
            Tile t = new Tile(0, 0, null, -1);
            t.LoadFromFile(br, map);
            t.textureSheet = this;
            this.tiles.set(t.y * this.numTilesX + t.x, t);
        }

        // Set alpha transparency based on the file data
        String hasAlpha = TileMap.readString(br, "has_alpha");
        if (hasAlpha.equals("true")) {
            this.SetHasAlpha(true);
        } else {
            this.SetHasAlpha(false);
        }

        TileMap.GoToEnd(br); // Skip to the end of the file
    }

    // Saves sprite sheet data to a file
    public void SaveToFile(FileWriter fw, TileMap map) throws IOException {
        File resFolder = new File("./");
        File imageFile = new File(this.imagePath);

        // Convert paths to relative format
        URI path1 = resFolder.toURI();
        URI path2 = imageFile.toURI();
        URI relativePath = path1.relativize(path2);
        String path = relativePath.getPath();

        fw.write("__SPRITE SHEET__\n");
        fw.write("name=" + this.name + "\n");
        fw.write("image_path=" + path + "\n");
        fw.write("tile_size=" + this.tileSize + "\n");

        // Write deleted tiles
        fw.write("delted_tiles_indicies=");
        int modifiedTilesCount = 0;

        // Count modified and deleted tiles
        for (Tile t : this.tiles) {
            if (!t.IsNull()) {
                if (t.isModified()) {
                    modifiedTilesCount++;
                }
            } else {
                int index = t.y * this.numTilesX + t.x;
                fw.write(index + ",");
            }
        }
        fw.write("\n");

        // Write the number of modified tiles
        fw.write("num_modified_tiles=" + modifiedTilesCount + "\n");

        // Save each modified tile
        for (int y = 0; y < this.numTilesY; y++) {
            for (int x = 0; x < this.numTilesX; x++) {
                Tile t = this.tiles.get(y * this.numTilesX + x);
                if (!t.IsNull() && t.isModified()) {
                    t.SaveToFile(fw, map);
                }
            }
        }

        // Save alpha transparency setting
        fw.write("has_alpha=" + this.hasAlpha + "\n");
        fw.write("END\n");
    }

    // Constructor: Loads sprite sheet data from file
    public SpriteSheet(BufferedReader br, TileMap map) throws IOException {
        this.LoadFromFile(br, map);
    }

    // Constructor: Initializes sprite sheet with given image, file path, and tile size
    public SpriteSheet(BufferedImage image, String filePath, int tileSize) {
        Init(image, filePath, tileSize);   
    }

    // Constructor: Loads sprite sheet from file path and initializes with tile size
    public SpriteSheet(String filePath, int tileSize) throws IOException {
        File f = new File(filePath);
        BufferedImage loadedImage = ImageIO.read(f);
        this.imagePath = filePath;
        this.name = f.getName();
        Init(loadedImage, filePath, tileSize);
    }

    // Clears a tile at the specified index
    public void ClearTileAtIndex(int index) {
        if (index < 0 || index >= this.tiles.size()) return;
        Tile t = this.tiles.get(index);
        t.w = 1;
        t.h = 1;
        t.textureIndex = -1;
        t.textureSheet = null;
    }

    // Purges blank tiles by replacing them with empty tiles
    public void PurgeBlankTiles() {
        int removalCount = 0;
        for (Tile t : this.tiles) {
            if (t != null && t.IsBlank()) {
                removalCount++;
                this.tiles.set(this.tiles.indexOf(t), new Tile(t.x, t.y, this, -1));
            }
        }

        new Message("Purged blank " + removalCount + " tiles in `" + this.name + "` sprite sheet.", 7.5);
        this.tilesPurged = true;
    }

    // Updates the size and layout of tiles based on the image dimensions
    public void UpdateTilesSize() {
        int imageWidth = this.image.getWidth();
        int imageHeight = this.image.getHeight();

        this.numTilesX = imageWidth / tileSize;
        this.numTilesY = imageHeight / tileSize;

        System.out.println("[LOG]: Reset blueprint tiles.");
        this.tiles = new ArrayList<>();
        for (int y = 0; y < this.numTilesY; y++) {
            for (int x = 0; x < this.numTilesX; x++) {
                this.tiles.add(new Tile(x, y, this, y * this.numTilesX + x));
            }
        }
        this.tilesPurged = false;
    }
}
// Tile class representing individual tiles in the sprite sheet.
class Tile {
    public SpriteSheet textureSheet; // Reference to the associated sprite sheet
    public int textureIndex; // Index of the texture in the sprite sheet

    protected int x, y; // Coordinates of the tile
    public int w = 1, h = 1; // Width and height of the tile

    public boolean collidable = false; // Whether the tile is collidable
    public Vector2 collidorPos = new Vector2(); // Position of the collider
    public Vector2 collidorSize = new Vector2(); // Size of the collider

    public double frictionCoefficient = 1.0; // Friction coefficient of the tile

    protected ArrayList<GameObject> objectsOnTile = new ArrayList<>(); // Objects occupying the tile

    public ArrayList<String> tags = new ArrayList<>(); // Tags associated with the tile

    public boolean animated = false; // Whether the tile is animated
    public boolean animationControl = false; // Control flag for animation

    public int animNumFramesX = 1; // Number of animation frames in the X direction
    public int animNumFramesY = 1; // Number of animation frames in the Y direction
    public int animFPS = 15; // Frames per second for animation

    protected int animCurrentFrame = 0; // Current frame in the animation
    private double animStart = Game.now(); // Timestamp for when the animation started
    protected int animPlayedCount = 0; // Counter for how many times the animation has played

    // Resets the animation by restarting the timer
    public void ResetAnimation() {
        this.animStart = Game.now();
    }

    // Loads tile data from a file
    public void LoadFromFile(BufferedReader br, TileMap map) throws IOException {
        int x = TileMap.readInt(br, "x");
        int y = TileMap.readInt(br, "y");
        int w = TileMap.readInt(br, "w");
        int h = TileMap.readInt(br, "h");
        int textureIndex = TileMap.readInt(br, "texture_index");
        int spriteSheetIndex = TileMap.readInt(br, "sprite_sheet_index");

        Double cx = TileMap.readNumber(br, "cx");
        Double cy = TileMap.readNumber(br, "cy");
        Double cw = TileMap.readNumber(br, "cw");
        Double ch = TileMap.readNumber(br, "ch");

        String collidable = TileMap.readString(br, "collidable");

        String animated = TileMap.readString(br, "animated");
        Integer animFPS = TileMap.readInt(br, "anim_fps");
        Integer animFramesX = TileMap.readInt(br, "anim_frames_x");
        Integer animFramesY = TileMap.readInt(br, "anim_frames_y");

        String tags = TileMap.readString(br, "tags");

        // Set collider position and size if specified
        if (cx != null && cy != null && cw != null && ch != null) {
            this.collidorPos = new Vector2(cx, cy);
            this.collidorSize = new Vector2(cw, ch);
        } else {
            this.collidorPos = new Vector2();
            this.collidorSize = new Vector2(1.0, 1.0);
        }

        // Set collidable status
        this.collidable = collidable != null && collidable.equals("true");

        // Handle animation settings
        if (animated != null && animated.equals("true")) {
            if (animFPS != null && animFramesX != null && animFramesY != null) {
                this.animated = true;
                this.animFPS = animFPS;
                this.animNumFramesX = animFramesX;
                this.animNumFramesY = animFramesY;
            }
        } else {
            this.animated = false;
        }

        // Parse and add tags
        this.tags.clear();
        if (tags != null && tags.length() > 0) {
            String[] tagsSplit = tags.split(",");
            for (String tag : tagsSplit) {
                this.tags.add(tag);
            }
        }

        // Go to the end of the file
        TileMap.GoToEnd(br);
        
        // Set tile properties
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.textureIndex = textureIndex;

        // Set the associated sprite sheet if valid
        if (spriteSheetIndex >= 0 && spriteSheetIndex < map.ownedSheets.size()) {
            this.textureSheet = map.ownedSheets.get(spriteSheetIndex);
        }
    }

    // Check if the tile has been modified by comparing its properties to defaults
    public boolean isModified() {
        return (this.w != 1 || this.h != 1 || this.collidable != false || this.animated == true || this.tags.size() > 0);
    }

    // Set the properties of this tile to match another tile's properties
    public void Set(Tile newTile) {
        this.w = newTile.w;
        this.h = newTile.h;
        this.textureIndex = newTile.textureIndex;
        this.textureSheet = newTile.textureSheet;
        this.collidable = newTile.collidable;
        this.collidorPos = newTile.collidorPos;
        this.collidorSize = newTile.collidorSize;
        this.animated = newTile.animated;
        this.animFPS = newTile.animFPS;
        this.animNumFramesX = newTile.animNumFramesX;
        this.animNumFramesY = newTile.animNumFramesY;
        this.animCurrentFrame = newTile.animCurrentFrame;
        this.animPlayedCount = newTile.animPlayedCount;
        this.animStart = newTile.animStart;
        this.animationControl = newTile.animationControl;
        this.tags = new ArrayList<>(newTile.tags); // Copy tags to avoid reference issues
    }

    // Save the tile's properties to a file
    public void SaveToFile(FileWriter fw, TileMap map) throws IOException {
        fw.write("__TILE__\n");
        fw.write("x=" + this.x + "\n");
        fw.write("y=" + this.y + "\n");
        fw.write("w=" + this.w + "\n");
        fw.write("h=" + this.h + "\n");
        fw.write("texture_index=" + this.textureIndex + "\n");

        // Write sprite sheet index if it exists
        if (this.textureSheet != null) {
            int index = map.ownedSheets.indexOf(this.textureSheet);
            if (index == -1) {
                System.err.println("[ERRORRRRRRRR]: " + this);
            }
            fw.write("sprite_sheet_index=" + index + "\n");
        } else {
            fw.write("sprite_sheet_index=-1\n");
        }

        // Write collider properties
        fw.write("cx=" + this.collidorPos.x + "\n");
        fw.write("cy=" + this.collidorPos.y + "\n");
        fw.write("cw=" + this.collidorSize.x + "\n");
        fw.write("ch=" + this.collidorSize.y + "\n");
        fw.write("collidable=" + this.collidable + "\n");

        // Write animation properties
        fw.write("animated=" + this.animated + "\n");
        fw.write("anim_fps=" + this.animFPS + "\n");
        fw.write("anim_frames_x=" + this.animNumFramesX + "\n");
        fw.write("anim_frames_y=" + this.animNumFramesY + "\n");

        // Write tags as a comma-separated string
        String tagString = "";
        for (String tag : this.tags) {
            tagString += (tag + ",");
        }
        fw.write("tags=" + tagString + "\n");

        fw.write("END\n");
    }

    // Clone the tile by creating a new tile with the same properties
    public Tile Clone() {
        Tile t = new Tile(this.x, this.y, this.textureSheet, this.textureIndex);
        t.Set(this); // Copy properties from this tile
        return t;
    }

    // Constructor initializes the tile with position, sprite sheet, and texture index
    public Tile(int x, int y, SpriteSheet sheet, int textureIndex) {
        this.Clear(); // Reset tile properties
        this.x = x;
        this.y = y;
        this.textureSheet = sheet;
        this.textureIndex = textureIndex;
    }

    // Clear the tile's properties, resetting them to default values
    public void Clear() {
        this.textureSheet = null;
        this.textureIndex = -1;
        this.w = 1;
        this.h = 1;
        this.collidable = false;
        this.collidorPos = new Vector2();
        this.collidorSize = new Vector2(1.0, 1.0);
        this.animated = false;
        this.animNumFramesX = 1;
        this.animNumFramesY = 1;
        this.animFPS = 15;
        this.animPlayedCount = 0;
        this.animStart = Game.now();
        this.tags.clear();
    }

    // Check if the tile is a "null" tile (i.e., it has no texture)
    public boolean IsNull() {
        return this.textureIndex == -1;
    }

    // Check if the tile is a compound tile (i.e., its width or height is greater than 1)
    public boolean IsCompoundTile() {
        return this.w > 1 || this.h > 1;
    }

    // Calculate the index of the tile on its sprite sheet based on its coordinates
    public int GetSheetIndex() {
        return this.y * this.textureSheet.numTilesX + this.x;
    }

    public boolean IsBlank() {
        if (this.textureIndex == -1) return false;

        int tileSize = this.textureSheet.tileSize;
        int sx = this.textureIndex % this.textureSheet.numTilesX;
        int sy = this.textureIndex / this.textureSheet.numTilesX;
        BufferedImage image = this.textureSheet.GetCPUImage();
    
        if (image == null)
            return true;

        // Calculate the top-left corner of the tile in the texture sheet
        int startX = sx * tileSize;
        int startY = sy * tileSize;
    
        // Get the alpha raster of the image
        WritableRaster alphaRaster = image.getAlphaRaster();
        if (alphaRaster == null)
            return false;
    
        // Loop through each pixel in the tile
        for (int y = 0; y < tileSize; y++) {
            for (int x = 0; x < tileSize; x++) {
                // Get the alpha value of the pixel (0 = fully transparent, 255 = fully opaque)
                int alpha = alphaRaster.getSample(startX + x, startY + y, 0);
    
                // If any pixel is not fully transparent, the tile is not blank
                if (alpha > 0) {
                    return false;
                }
            }
        }
    
        // All pixels are fully transparent; the tile is blank
        return true;
    }

    // Draw the tile on the graphics context, with optional flipping and transparency
    public void Draw(Graphics2D g, double x, double y, double w, double h, boolean flip, double transparency) {
        if (this.textureIndex == -1 || this.textureSheet == null) return; // Skip if tile is invalid

        int tileSize = this.textureSheet.tileSize;

        // Calculate source rectangle for the tile texture
        int sx = this.textureIndex % this.textureSheet.numTilesX;
        int sy = this.textureIndex / this.textureSheet.numTilesX;

        int sw = (tileSize * this.w); // Tile width
        int sh = (tileSize * this.h); // Tile height

        int frameOffsetX = 0, frameOffsetY = 0;

        // Handle tile animation if enabled
        if (this.animated) {
            if (!this.animationControl) {
                // Update current frame and frame count based on time and FPS
                this.animCurrentFrame = (int)(((Game.now() - animStart) * this.animFPS) % (this.animNumFramesX * this.animNumFramesY));
                this.animPlayedCount = ((int)((Game.now() - animStart) * this.animFPS) / (this.animNumFramesX * this.animNumFramesY)) % (this.animNumFramesX * this.animNumFramesY);
            }
        }

        // Adjust frame offsets for animation
        frameOffsetX = (this.animCurrentFrame % this.animNumFramesX);
        frameOffsetY = 0; // Currently no vertical animation frame adjustment

        sx = (sx + (frameOffsetX * this.w)) * tileSize;
        sy = (sy + (frameOffsetY * this.h)) * tileSize;

        // Set transparency if needed
        Composite prevComp = g.getComposite();
        float alpha = (float) transparency;
        AlphaComposite composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha);
        g.setComposite(composite);

        // Draw the image (flip horizontally if necessary)
        if (flip) {
            g.drawImage(
                this.textureSheet.GetImage(g),
                (int) x, (int) y, (int) (x + w), (int) (y + h),  // Destination rectangle
                sx + sw, sy, sx, sy + sh,                        // Source rectangle (flipped horizontally)
                GG.COLOR_OPAQUE, null      // Transparency and observer
            );
        } else {
            g.drawImage(
                this.textureSheet.GetImage(g),
                (int) x, (int) y, (int) (x + w), (int) (y + h),
                sx, sy, sx + sw, sy + sh,
                GG.COLOR_OPAQUE, null      // Transparency and observer
            );
        }

        // Restore the original composite (after drawing)
        g.setComposite(prevComp);
    }

    // Draw the tile without flipping and full opacity
    public void Draw(Graphics2D g, double x, double y, double w, double h) {
        this.Draw(g, x, y, w, h, false, 1.0);
    }

    // Draw the tile without transparency but with optional flipping
    public void Draw(Graphics2D g, double x, double y, double w, double h, boolean flip) {
        this.Draw(g, x, y, w, h, flip, 1.0);
    }

    // Return a string representation of the tile, including its position and texture sheet information
    public String toString() {
        String sheetName = this.textureSheet != null ? this.textureSheet.name : "NULL"; 
        return "Tile @(" + this.x + ", " + this.y + ") Sheet : { Name: `" + sheetName + "`, Texture Index: " + this.textureIndex + " }";
    }
}

class TileMapLayer {
    public String name = "Layer 1";
    protected TileMap parentMap;
    protected int width, height;
    protected ArrayList<Tile> tiles;

    public boolean isGroundLayer = false;
    public boolean visualizeCollidors = false;

    // Load layer data from a file, including tile information and properties
    public void LoadFromFile(BufferedReader br, TileMap map) throws IOException {
        String name = TileMap.readString(br, "name");
        int width = TileMap.readInt(br, "width");
        int height = TileMap.readInt(br, "height");

        int num_tiles = TileMap.readInt(br, "num_tiles");

        this.width = width;
        this.height = height;
        this.name = name;

        // Initialize all tiles as blank
        this.tiles.clear();
        for (int y = 0; y < width; y++) {
            for (int x = 0; x < height; x++) {
                this.tiles.add(new Tile(x, y, null, -1)); // Add all blank tiles
            }
        }

        // Load non-blank tiles from file
        for (int i = 0; i < num_tiles; i++) {
            Tile t = new Tile(0, 0, null, -1);
            t.LoadFromFile(br, map);
            this.SetTile(t.x, t.y, t);
        }

        // Set ground layer flag if present in the file
        String isGroundLayer = TileMap.readString(br, "is_ground_layer");
        if (isGroundLayer != null && isGroundLayer.equals("true")) {
            this.isGroundLayer = true;
        } else {
            this.isGroundLayer = false;
        }

        TileMap.GoToEnd(br); // Skip any remaining data in the file
    }

    // Save the layer's data to a file, including tiles and properties
    public void SaveToFile(FileWriter fw, TileMap map) throws IOException {
        fw.write("__LAYER__\n");
        fw.write("name=" + this.name + "\n");
        fw.write("width=" + this.width + "\n");
        fw.write("height=" + this.height + "\n");

        int numNonNullTiles = 0;

        // Count non-null tiles
        for (Tile t : this.tiles) {
            if (!t.IsNull()) {
                numNonNullTiles++;
            }
        }

        fw.write("num_tiles=" + numNonNullTiles + "\n");

        // Save non-null tiles to file
        for (Tile t : this.tiles) {
            if (!t.IsNull()) {
                t.SaveToFile(fw, map);
            }
        }

        fw.write("is_ground_layer=" + this.isGroundLayer + "\n");

        fw.write("END\n");
    }

    // Constructor to initialize the layer with dimensions and blank tiles
    public TileMapLayer(TileMap map, int width, int height) {
        this.parentMap = map;
        this.tiles = new ArrayList<>();

        this.width = width;
        this.height = height;

        // Initialize all tiles as blank
        for (int y = 0; y < width; y++) {
            for (int x = 0; x < height; x++) {
                this.tiles.add(new Tile(x, y, null, -1)); // Add all blank tiles
            }
        }
    }

    // Set a tile at specified coordinates by assigning its properties
    public void SetTile(int x, int y, Tile t) {
        int tileIndex = y * this.parentMap.width + x;

        if (tileIndex < 0 || tileIndex > this.tiles.size()-1) return;

        try {
            Tile current = this.tiles.get(tileIndex);
            current.Set(t);  // Copy tile properties
        } catch (IndexOutOfBoundsException e) {
            new Message("Attempt to set a tile out of bounds. @(" + x + ", " + y +")", true);
        }
    }

    // Set a tile at specified coordinates with a texture from the sprite sheet
    public void SetTile(int x, int y, SpriteSheet sheet, int textureIndex) {
        int tileIndex = y * this.parentMap.width + x;

        if (tileIndex < 0 || tileIndex > this.tiles.size()) return;

        try {
            Tile current = this.tiles.get(tileIndex);
            current.Clear();  // Reset the tile before setting new texture
            current.textureSheet = sheet;
            current.textureIndex = textureIndex;
        } catch (IndexOutOfBoundsException e) {
            new Message("Attempt to set a tile out of bounds. @(" + x + ", " + y +")", true);
        }
    }

    // Retrieve the tile at specified coordinates
    public Tile GetTile(int x, int y) {
        int tileIndex = y * this.parentMap.width + x;

        if (tileIndex < 0 || tileIndex > this.tiles.size()) return null;

        try {
            return this.tiles.get(tileIndex);  // Return the tile at the given position
        } catch (IndexOutOfBoundsException e) {
            new Message("[ERROR]: Attempt to get a tile out of bounds. @(" + x + ", " + y +")", true);
            return null;
        }
    }
}

class TileMap {
    // Static field defining the rendering scale factor
    static double RENDERSCALE = 50.0;

    // Offset for rendering
    public Vector2 renderOffset = new Vector2();

    // Affine transform for scaling during rendering
    public AffineTransform transform;

    // Map dimensions
    protected int width;
    protected int height;

    // List of sprite sheets owned by the map
    protected ArrayList<SpriteSheet> ownedSheets = new ArrayList<>();

    // Layers of tiles in the map
    protected ArrayList<TileMapLayer> layers = new ArrayList<>();

    // Objects responsible for rendering within the map
    protected ArrayList<GameObject> renderingResponsiblity = new ArrayList<>();

    // High score associated with the map
    public int highScore = 0;

    // Constructor to initialize the map with given dimensions
    public TileMap(int width, int height) {
        this.width = width;
        this.height = height;

        // Initialize affine transform for scaling
        this.transform = new AffineTransform();
        this.transform.scale(30.0, 30.0);

        // Create a default layer of tiles
        this.layers.add(new TileMapLayer(this, width, height));
    }

    // Register a game object for rendering responsibility within the map
    public void RenderResponsibly(GameObject o) {
        this.renderingResponsiblity.add(o);
    }

    // Delete a sprite sheet from all layers and remove it from owned sheets
    public void DeleteSheet(SpriteSheet sheet) {
        for (TileMapLayer layer : this.layers) {
            for (Tile t : layer.tiles) {
                if (t.textureSheet == sheet) {
                    t.Clear();  // Clear all tiles using the specified sprite sheet
                }
            }
        }
        this.ownedSheets.remove(sheet);  // Remove the sprite sheet from the owned sheets list
    }

    // Loads a sprite sheet from the specified file path and tile size
    public SpriteSheet LoadSpriteSheet(String filePath, int tileSize) {
        SpriteSheet sheet;
        try {
            sheet = new SpriteSheet(filePath, tileSize);  // Attempt to create a new SpriteSheet object
        } catch (IOException e) {
            // If loading fails, display an error message and return null
            new Message("Error loading spritesheet from file (" + filePath + ")\n" + e.getLocalizedMessage(), true);
            return null;
        }

        // Add the loaded sheet to the owned sheets list
        this.ownedSheets.add(sheet);

        return sheet;  // Return the successfully loaded SpriteSheet
    }

    // Converts a local vector (tile-based) to a world vector (scaled)
    public Vector2 LocalToWorldVectorScalar(Vector2 worldVector) {
        Vector2 n = new Vector2();

        n.x = worldVector.x * TileMap.RENDERSCALE;  // Scale the X-coordinate
        n.y = worldVector.y * TileMap.RENDERSCALE;  // Scale the Y-coordinate

        return n;  // Return the scaled vector
    }

    // Converts a local vector (tile-based) to a world vector with offset (render position)
    public Vector2 LocalToWorldVectorPositional(Vector2 worldVector) {
        Vector2 n = new Vector2();

        n.x = worldVector.x * TileMap.RENDERSCALE + this.renderOffset.x;  // Scale and apply offset for X
        n.y = worldVector.y * TileMap.RENDERSCALE + this.renderOffset.y;  // Scale and apply offset for Y

        return n;  // Return the transformed vector
    }

    // Converts a world vector (scaled and offset) back to a local vector
    public Vector2 WorldToLocalVector(Vector2 worldVector) {
        Vector2 n = new Vector2();

        n.x = worldVector.x / TileMap.RENDERSCALE - this.renderOffset.x;  // Reverse scale and offset for X
        n.y = worldVector.y / TileMap.RENDERSCALE - this.renderOffset.y;  // Reverse scale and offset for Y

        return n;  // Return the converted local vector
    }

    // Retrieves the tile at the given world position in the specified layer
    public Tile GetTileAtWorldPosition(Vector2 position, TileMapLayer layerMask) {
        // Convert world position to local position
        position = WorldToLocalVector(position);

        // Calculate the tile coordinates
        int x = (int)Math.floor(position.x);  // Round down for local X coordinate
        int y = (int)Math.floor(position.y);  // Round down for local Y coordinate

        // Check if coordinates are out of bounds
        if (x < 0 || x > this.width || y < 0 || y > this.height) return null;

        // If a specific layer mask is provided, return the tile from that layer
        if (layerMask != null) {
            return layerMask.GetTile(x, y);
        }

        // Search for the tile in all layers
        for (TileMapLayer layer : this.layers) {
            Tile t = layer.GetTile(x, y);
            if (!t.IsNull()) {
                return t;  // Return the first non-null tile found
            }
        }

        return null;  // Return null if no valid tile is found
    }

    // Clears the tile at the specified world position in the given layer
    public void ClearTileAtWorldPosition(Vector2 position, TileMapLayer layerMask) {
        position = WorldToLocalVector(position);  // Convert world position to local coordinates

        int x = (int)Math.floor(position.x);  // Get the local X coordinate
        int y = (int)Math.floor(position.y);  // Get the local Y coordinate

        // Check if the coordinates are out of bounds
        if (x < 0 || x > this.width || y < 0 || y > this.height) return;

        // If a layer mask is provided, clear the tile in that layer
        if (layerMask != null) {
            layerMask.SetTile(x, y, null, -1);  // Set the tile to null in the layer
            return;
        }
    }

    // Sets the tile at the specified world position in the given layer
    public void SetTileAtWorldPosition(Vector2 position, Tile t, TileMapLayer layerMask) {
        position = WorldToLocalVector(position);  // Convert world position to local coordinates

        int x = (int)Math.floor(position.x);  // Get the local X coordinate
        int y = (int)Math.floor(position.y);  // Get the local Y coordinate

        // Check if the coordinates are out of bounds
        if (x < 0 || x >= this.width || y < 0 || y > this.height) return;

        // If a layer mask is provided, set the tile in that layer
        if (layerMask != null) {
            layerMask.SetTile(x, y, t);  // Set the tile in the specified layer
            return;
        }

        // Otherwise, set the tile in the top-most layer
        TileMapLayer topMost = this.layers.get(0);
        if (topMost != null) {
            topMost.SetTile(x, y, t);  // Set the tile in the top-most layer
        }
    }

    // Returns the ground layer (if exists) from the list of layers
    public TileMapLayer GetGroundLayer() {
        TileMapLayer groundLayer = null;
        for (TileMapLayer l : this.layers) {
            if (l.isGroundLayer) {  // Check if the layer is a ground layer
                groundLayer = l;    // Assign the ground layer
                break;
            }
        }
        return groundLayer;  // Return the found ground layer or null if not found
    }

    // Resets the responsibilities (clears the rendering list and clears objects on ground tiles)
    public void ResetResponsiblities() {
        this.renderingResponsiblity.clear();  // Clear the list of objects with rendering responsibility

        TileMapLayer groundLayer = this.GetGroundLayer();  // Get the ground layer
        if (groundLayer != null) {
            for (Tile t : groundLayer.tiles) {
                t.objectsOnTile.clear();  // Clear objects on each tile of the ground layer
            }
        }
    }

    // Returns a list of tiles with the specified tag in the given layer or all layers
    public ArrayList<Tile> GetMapTilesByTag(String tag, TileMapLayer layerMask) {
        ArrayList<Tile> tiles = new ArrayList<>();
        
        // If no specific layer is provided, search all layers
        if (layerMask == null) {
            for (TileMapLayer l : this.layers) {
                for (Tile t : l.tiles) {
                    if (t.tags.contains(tag)) {  // If the tile has the specified tag
                        tiles.add(t);  // Add it to the list
                    }
                }
            }
        } else {
            // If a specific layer is provided, search only that layer
            for (Tile t : layerMask.tiles) {
                if (t.tags.contains(tag)) {  // If the tile has the specified tag
                    tiles.add(t);  // Add it to the list
                }
            }
        }

        return tiles;  // Return the list of matching tiles
    }

    // Returns the first tile with the specified tag from any of the owned sprite sheets
    public Tile GetSheetTileByTag(String tag) {
        // Loop through all owned sprite sheets
        for (SpriteSheet ss : this.ownedSheets) {
            for (Tile t : ss.tiles) {
                if (!t.IsNull() && t.tags.contains(tag)) {  // If the tile is not null and has the specified tag
                    return t;  // Return the first matching tile
                }
            }
        }
        return null;  // Return null if no tile with the specified tag is found
    }

    // Function to draw tiles in specefic order
    public void Draw(Graphics2D g) {
        // I don't know how to document I just did random stuf

        // int drewCount = 0;
        
        TileMapLayer groundLayer = this.GetGroundLayer();
        if (groundLayer != null) {
            for (GameObject o : this.renderingResponsiblity) {
                g.setColor(Color.RED);
                GG.drawRect(o.position, o.size);
                
                Vector2 centreBottomPos = o.position.add(new Vector2(o.size.x/2.0, o.size.y));
                
                g.setColor(Color.RED);
                GG.drawOval(centreBottomPos, new Vector2(10, 10));
                
                Tile t1 = this.GetTileAtWorldPosition(centreBottomPos, groundLayer);
                Tile t2 = null;
                
                if (t1 != null)
                t2 = groundLayer.GetTile(t1.x, t1.y + 1);

                if (t2 != null) {
                    t2.objectsOnTile.add(o);
                } else if (t1 != null) {
                    t1.objectsOnTile.add(o);
                } else {
                    o.Draw(g);
                }
            }
        }
        
        // ? Surely this is fine for memory and performance. (We're in a time crunch.)
        ArrayList<ArrayList<Tile>> layerOrdered = new ArrayList<>();
        
        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                layerOrdered.add(new ArrayList<Tile>());
            }
        }
        
        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                int index = y * this.width + x;
                
                ArrayList<Tile> tilesAtPos = layerOrdered.get(index);
                
                for (TileMapLayer l : this.layers) {
                    if (l.width != this.width || l.height != this.height) {
                        System.err.println("[WARN]: Not rendering layer with non-matching width or height. Layer: " + l.name);
                        continue;
                    }
                    
                    Tile t = l.tiles.get(index);
                    tilesAtPos.add(t);
                }
            }
        }
        
        for (int i = 0; i < layerOrdered.size(); i++) {
            ArrayList<Tile> tilesAtPos = layerOrdered.get(i);
            
            for (int j = 0; j < tilesAtPos.size(); j++) {
                Tile t = tilesAtPos.get(j);
                if (t.h > 1) {
                    tilesAtPos.remove(j);
                    
                    int bottomY = t.y + t.h;
                    int newIndex = (bottomY * this.width + t.x);
                    
                    layerOrdered.get(newIndex).add(t);
                }
            }
        }
        
        // double start = Game.now();
        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                int index = y * this.width + x;
                ArrayList<Tile> tiles = layerOrdered.get(index);
                
                for (int l = 0; l < tiles.size(); l++) {
                    Tile t = tiles.get(l);
                    
                    if (!t.IsNull()) {
                        Vector2 tilePosition = LocalToWorldVectorPositional(new Vector2(t.x, t.y));
                        Vector2 tileSize = LocalToWorldVectorScalar(new Vector2(t.w, t.h));
                        
                        t.Draw(g, tilePosition.x, tilePosition.y, tileSize.x, tileSize.y);
                    }
                }
            }
            for (int x = 0; x < this.width; x++) {
                int index = y * this.width + x;
                ArrayList<Tile> tiles = layerOrdered.get(index);
                
                for (int l = 0; l < tiles.size(); l++) {
                    Tile t = tiles.get(l);
                    
                    for (GameObject o : t.objectsOnTile) {
                        o.Draw(g);
                    }
                }
            }
        }
        // System.err.println("Time: " + (Game.now() - start));
    }

    // This method saves the current map's data (including layers, sheets, and high score) to a file.
    public void Save(String filePath) {
        new Message("Saving map...");

        try {
            // Create or overwrite the map file
            File mapF = new File(filePath);
            if (mapF.createNewFile()) {
                new Message("New map file created: `" + mapF.getPath() + "`");
            } else {
                new Message("Overwriting, map file already exists: `" + mapF.getPath() + "`");
            }

            FileWriter fw = new FileWriter(mapF);

            // Write general map information
            fw.write("__MAP__\n");
            fw.write("width=" + this.width + "\n");
            fw.write("height=" + this.height + "\n");

            // Save owned sprite sheets
            fw.write("num_owned_sheets=" + this.ownedSheets.size() + "\n");
            for (SpriteSheet sheet : this.ownedSheets) {
                sheet.SaveToFile(fw, this);
            }

            // Save layers
            fw.write("num_layers=" + this.layers.size() + "\n");
            for (TileMapLayer l : this.layers) {
                l.SaveToFile(fw, this);
            }

            // Save high score
            fw.write("high_score=" + this.highScore + "\n");

            // End of file marker
            fw.write("END\n");

            fw.flush();
            fw.close();
        } catch (IOException e) {
            new Message("An error occurred: " + e.getLocalizedMessage(), true);
            e.printStackTrace();
        }
    }


    private static String loaderError = null;

    private static String getNextLine(BufferedReader br)  throws IOException {
        String line = br.readLine();

        if (line == null)  return null;

        while (line.strip().equals("") || line.startsWith("__")) {
            line = br.readLine();
        }

        return line;
    } 

    private static boolean gotEnd = false;
    public static void GoToEnd(BufferedReader br) throws IOException {
        if (gotEnd) {
            gotEnd = false;
            return;
        }
        String line = null;
        do {
            String newLine = br.readLine();
            if (newLine == null) {
                loaderError = "Unexpected EOF, Expecting END. `" + line + "`";
                return;
            }
            line = newLine;
        } while (line == null || !line.equals("END"));
    }

    // Reads an integer value from a BufferedReader, checking for a specific header
    public static Integer readInt(BufferedReader br, String expectedHeader) throws IOException {
        // If an end marker was reached, return null
        if (gotEnd) return null;

        // Read the next line of input
        String line = getNextLine(br);

        // If EOF is reached unexpectedly, set an error and return null
        if (line == null) {
            loaderError = "Unexpected EOF.\n";
            return null;
        }

        // Split the line into components by '='
        String components[] = line.split("=");

        // If the line doesn't have both header and value, set an error and return null
        if (components.length < 2) {
            loaderError = "Incomplete statement. Line: `" + line + "`";
            return null;
        }
        
        // Get the header part of the statement
        String header = components[0];

        // If the header doesn't match the expected header, set an error and return null
        if (!header.equals(expectedHeader)) {
            loaderError = "Expected `" + expectedHeader + "` got: `" + header + "`";
            return null;
        }

        // Concatenate all parts of the value (in case the value has multiple components)
        String valueStr = components[1];
        for (int i = 2; i < components.length; i++) {
            valueStr += components[i];
        }

        // Try to parse the value as an integer
        int value = 0;
        try {
            value = Integer.parseInt(valueStr);
        } catch (NumberFormatException e) {
            // If parsing fails, set an error and return null
            loaderError = "Expected integer got: " + valueStr + ".\n";
            return null;
        }

        // Return the parsed integer value
        return value;
    }

    // Reads a string value from a BufferedReader, checking for a specific header
    public static String readString(BufferedReader br, String expectedHeader) throws IOException {
        // If an end marker was reached, return null
        if (gotEnd) return null;

        // Read the next line of input
        String line = getNextLine(br);

        // If EOF is reached unexpectedly, set an error and return null
        if (line == null) {
            loaderError = "Unexpected EOF.\n";
            return null;
        }

        // If the line contains the "END" marker, set gotEnd to true and return null
        if (line.equals("END")) {
            gotEnd = true;
            return null;
        }

        // Split the line into components by '='
        String components[] = line.split("=");

        // If the line doesn't have both header and value, return an empty string
        if (components.length < 2) {
            return "";
        }
        
        // Get the header part of the statement
        String header = components[0];

        // If the header doesn't match the expected header, set an error and return null
        if (!header.equals(expectedHeader)) {
            loaderError = "Expected `" + expectedHeader + "` got: `" + header + "`";
            return null;
        }

        // Concatenate all parts of the value (in case the value has multiple components)
        String valueStr = components[1];
        for (int i = 2; i < components.length; i++) {
            valueStr += components[i];
        }

        // Return the concatenated string value
        return valueStr;
    }

    // Reads a number (Double) value from a BufferedReader, checking for a specific header
    public static Double readNumber(BufferedReader br, String expectedHeader) throws IOException {
        // Read the string value associated with the expected header
        String str = readString(br, expectedHeader);
        
        // If the string is not null, attempt to parse it as a Double
        if (str != null) {
            try {
                // Try parsing the string as a Double and return the parsed value
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                // If parsing fails, set an error indicating the issue
                loaderError = "Expected number got: `" + str + "`";
            }
        }
        
        // Return null if parsing failed or the string was null
        return null;
    }

    // Loads a map from a specified file path
    public void LoadFromFile(String filePath) {
        try {
            // Check if the file exists
            File mapF = new File(filePath);
            if (!mapF.exists()) {
                System.err.println("[ERROR]: Attempted to load map file that doesn't exist: `" + filePath + "`");
                return;
            }

            // Create a BufferedReader to read the map file
            BufferedReader br = new BufferedReader(new FileReader(mapF));

            // Read the map's width and height
            int width = readInt(br, "width");
            int height = readInt(br, "height");

            System.out.println("Width:" + width);
            System.out.println("Height:" + height);

            // Read the number of owned sprite sheets
            int numOwnedSheets = readInt(br, "num_owned_sheets");
            ArrayList<SpriteSheet> sheets = new ArrayList<>();

            // Load each sprite sheet
            for (int i = 0; i < numOwnedSheets; i++) {
                SpriteSheet s = new SpriteSheet(br, this);
                sheets.add(s);
            }

            // Check for errors loading sprite sheets
            if (loaderError != null) {
                new Message("[ERROR] Map loader error in loading sheets: " + loaderError, true);
                return;
            } else {
                this.ownedSheets = sheets;
            }

            // Read the number of layers
            int numLayers = readInt(br, "num_layers");
            ArrayList<TileMapLayer> layers = new ArrayList<>();
            
            // Load each tile map layer
            for (int i = 0; i < numLayers; i++) {
                TileMapLayer tl = new TileMapLayer(this, width, height);
                tl.LoadFromFile(br, this);
                layers.add(tl);
            }

            // Check for errors loading layers
            if (loaderError != null) {
                new Message("[ERROR] Map loader error in loading layers: " + loaderError, true);
                return;
            } else {
                this.layers = layers;
            }

            // Read the high score value (if available)
            Integer highScore = readInt(br, "high_score");
            if (highScore != null) {
                System.out.println("Read highscore: " + highScore);
                this.highScore = highScore;
            }

            // Move to the end of the file to ensure no unexpected data is left
            GoToEnd(br);

            // Close the BufferedReader after reading
            br.close();
        } catch (IOException e) {
            // Handle any IO exceptions
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

}
