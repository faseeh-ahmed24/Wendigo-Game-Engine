/* Game.Java
* Author: Ibraheem Mustafa, Faseeh Ahmed
* Date Created: Dec 11th, 2024
* Date Last Edited: Dec 11th, 2024
*/

/* GamePanel class acts as the main "game loop" - continuously runs the game and calls whatever needs to be called
Child of JPanel because JPanel contains methods for drawing to the screen
Implements KeyListener interface to listen for keyboard input
Implements Runnable interface to use "threading" - let the game do two things at once
*/

import javax.swing.*;

import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;

public class Game extends JPanel implements Runnable, KeyListener {
    private Thread gameThread; // Thread game is ran on
    private JFrame parentJFrame; // Parent frame passed through constructor
    
    /*
        Many different things can block and unblock inputs at once.
        For example if this was a boolean many things could set it to true and false many times a frame.
        For that reason input is free when the size of this array is zero.
    */
    public static ArrayList<String> inputBlockers = new ArrayList<>();
    
    /*  Having keysDownLastFrame and keys down this frame so we can implement Game.isKeyDown
        that can be called at any time, instead of events.
    */
    public static final int MAX_KEYS = 256; // We track most keys 0->256
    public static boolean[] keysDownLastFrame = new boolean[MAX_KEYS]; // Last frame
    public static boolean[] keysDown = new boolean[MAX_KEYS]; // Current frame

    /*
        Text the user has inputted, taking into account the capitals and modifers (!@#$%^&*) and such.
        Will start overwriting after 1024 characters.
    */
    public static String textInputBuffer = "";

    /*
     * Same as above
     */
    public static final int MAX_MOUSE_BUTTONS = 5;
    public static boolean[] mouseButtonsDown = new boolean[MAX_MOUSE_BUTTONS]; // LMB, MMB, RMB, etc.
    public static boolean[] mouseButtonsDownLastFrame = new boolean[MAX_MOUSE_BUTTONS];

    private static double scrollThisFrame;
    private static double scrollLastFrame;
    public static double deltaScroll;

    public static boolean gameRunning = true; // Game running, modifiable
    
    public static int WINDOW_WIDTH = 1600; // Variable Game size
    public static int WINDOW_HEIGHT = 900;

    public static int WIDTH = WINDOW_WIDTH;
    public static int HEIGHT = WINDOW_HEIGHT;

    public static int score = 0;

    // This one is in the world (relative to the camera)
    static Vector2 worldMousePos = new Vector2(); // Mouse position so classes can access Game.mousePos
    static Vector2 mousePos = new Vector2();  // Relative to the top left
    
    // Last reported FPS
    public static double FPS;
    public static double deltaTime;

    // Physics stuff
    public static Physics physics = new Physics();

    // All fonts
    public static Font font16;
    public static Font font32;
    public static Font font64;

    // Time stuff
    public static double gameStart = System.currentTimeMillis()/1000.0;
    public static double now() {
        return (double)System.nanoTime() / (double)1e9;
    }

    // Load default map
    static TileMap currentMap = new TileMap(100, 100);

    // Current cursor
    public static Cursor currentCursor = null;

    // Good Graphics
    public static GG gg = new GG();

    public static AffineTransform worldTransform = new AffineTransform();

    //create an array list of humans
    public static ArrayList<Humanoid> humanoids = new ArrayList<>();

    public static EnemyManager em;
    public static BulletManager bm;

    public static NoiseGenerator ng = new NoiseGenerator();
    
    //Create UI
    public static HUD hud;

    TileMapEditor editor;
    public boolean editorEnabled = false;
    
    // Initialize the player
    public static Humanoid player;
    
    public static GFXManager gfxManager;

    public static MainMenu menu;

    public Game(JFrame parentFrame) {
        this.parentJFrame = parentFrame;
        
        this.setFocusable(true); // make everything in this class appear on the screen
        this.addKeyListener(this); // start listening for keyboard input

        Game game = this;
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int mouseButtonIndex = e.getButton();
                if (mouseButtonIndex >= 0 && mouseButtonIndex < Game.mouseButtonsDown.length) { // If we keep track of it
                    if (e.getID() == MouseEvent.MOUSE_PRESSED) { // If it's pressed store it in the array, otherwise reset it
                        Game.mouseButtonsDown[mouseButtonIndex] = true;
                    }
                }
            }
            public void mouseReleased(MouseEvent e) {
                int mouseButtonIndex = e.getButton();
                if (mouseButtonIndex >= 0 && mouseButtonIndex < Game.mouseButtonsDown.length) { // If we keep track of it
                    if (e.getID() == MouseEvent.MOUSE_RELEASED) { // If it's released store that info  in the array
                        Game.mouseButtonsDown[mouseButtonIndex] = false;
                    }
                }
            }
        });
        addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                Game.scrollThisFrame += e.getPreciseWheelRotation() * e.getScrollAmount(); // Update scroll value
            }
        });
        
        addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (Game.currentCursor != null) {
                    game.setCursor(Game.currentCursor); // Update cursor on mouse move
                }
            }
        
            @Override
            public void mouseDragged(MouseEvent e) {
                if (Game.currentCursor != null) {
                    game.setCursor(Game.currentCursor); // Keep cursor during drag
                }
            }
        });

        System.out.println("[LOG]: Loading fonts");
        // Try to load fonts from file or else print errors and load fallback font
        try {
            Game.LoadFontsFromFile();
            System.out.println("[LOG]: Loaded fonts succesfully.");
        } catch (IOException e) {
            System.out.println("[ERROR]: Encountered `IOException` while loading fonts: " + e.getLocalizedMessage());
            Game.SetFonts(null);
        } catch (FontFormatException e) {
            System.out.println("[ERROR]: Encountered `FontFormatException` while loading fonts: " + e.getLocalizedMessage());
            Game.SetFonts(null);
        }

        this.gameThread = new Thread(this);
        System.out.println("[LOG]: Starting game thread.");
        this.gameThread.start(); // Start game 

        // Default sheet
        // SpriteSheet def = Game.currentMap.LoadSpriteSheet("res/Tile_set.png", 16);
        
        // Load map
        Game.currentMap.LoadFromFile("./res/map.wmap");

        // Setup editor
        this.editor = new TileMapEditor(currentMap);

        // Create menu
        menu = new MainMenu();

        // Maximize window
        this.parentJFrame.setExtendedState( this.parentJFrame.getExtendedState()|JFrame.MAXIMIZED_BOTH );
    }

    public static void LoadGame() {
        physics = new Physics();
    
        Game.currentMap.LoadFromFile("./res/map.wmap"); // Load the map file
        
        player = new Humanoid("dino", 1000, 1000); // Initialize the player
        gfxManager = new GFXManager();
        
        em = new EnemyManager();
        bm = new BulletManager();
        hud = new HUD();
        
        player.LoadAnimations();
        player.collisionLayers.add("player");
        gameStart = Game.now();
    
        // Set the player's starting position from the first spawn tile, if available
        ArrayList<Tile> spawnTiles = Game.currentMap.GetMapTilesByTag("player_spawn", null);
        if (spawnTiles.size() > 0) {
            Tile spawnTile = spawnTiles.get(0);
            player.position = Game.currentMap.LocalToWorldVectorPositional(new Vector2(spawnTile.x, spawnTile.y));
        }
    
        humanoids.add(player); // Add the player to the humanoids list
    
        Game.physics.SetCollidable("humanoid", "humanoid", false); // Disable collisions between humanoids
    }

    // Unloads the current game, saves the score, resets the map, and clears all game objects.
    public static void UnLoadGame() {
        if (Game.score > Game.currentMap.highScore) {
            Game.currentMap.highScore = Game.score; // Update high score if needed
        }

        Game.currentMap.Save("./res/map.wmap"); // Save the map data

        Game.currentMap = new TileMap(100, 100); // Reset the map

        Game.currentMap.LoadFromFile("./res/map.wmap"); // Reload the map file
        
        player = null;
        gfxManager = null;
        
        em = null;
        bm = null;
        hud = null;
        
        physics = null;

        humanoids.clear(); // Clear all humanoids

        Game.menu = new MainMenu(); // Set the menu to the main menu
    }

    // Updates the game state, including player, menu, physics, and editor (if enabled).
    public void Update(double deltaTime) {
        if (Game.player != null && Game.player.health > 0) {
            score = (int)(Game.now() - Game.gameStart) * 20; // Update score based on game time
        }

        if (Game.menu != null) {
            Game.menu.Update(deltaTime); // Update the menu state

            if (Game.menu.state == MenuState.Play) {
                Game.menu = null;
                LoadGame(); // Load the game when "Play" is selected
            } else if (Game.menu.state == MenuState.Quit) {
                gameRunning = false; // Stop the game when "Quit" is selected
            }

            return;
        }

        Game.physics.PreUpdate(); // Run physics pre-update

        // Camera transformation logic
        worldTransform = new AffineTransform();
        if (Game.player != null) {
            worldTransform.translate(Game.WINDOW_WIDTH / 2.0 - player.size.x / 2.0,
                                    Game.WINDOW_HEIGHT / 2.0 - player.size.y / 2.0);
            worldTransform.translate(-player.position.x, -player.position.y);
        }

        // Calculate world mouse position
        Point mousePoint = new Point((int)mousePos.x, (int)mousePos.y);
        Point worldMousePoint = new Point();
        try {
            worldTransform.inverseTransform(mousePoint, worldMousePoint);
        } catch (NoninvertibleTransformException e) {
            // Handle exception if needed
        }
        Game.worldMousePos = new Vector2(worldMousePoint.x, worldMousePoint.y);

        Game.physics.currentMap = currentMap;

        // Update all humanoids, remove dead ones
        ArrayList<Integer> humansToRemove = new ArrayList<>();
        for (int i = 0; i < Game.humanoids.size(); i++) {
            Humanoid e = Game.humanoids.get(i);

            if (e.state == State.DEAD) {
                humansToRemove.add(i);
            } else {
                if (e.type == HumanoidType.HUMAN || !this.editorEnabled) {
                    e.Update(deltaTime);
                }
                Game.physics.physicsObjects.add(e);
            }
        }
        for (int i : humansToRemove) {
            Game.humanoids.remove(i);
        }

        // Update game managers
        if (Game.em != null) {
            em.Update(deltaTime);
        }
        if (Game.bm != null) {
            bm.Update(deltaTime);
        }

        // Update editor if enabled
        if (this.editorEnabled) {
            editor.Update(deltaTime);
        }

        // Toggle editor mode with 'E' key
        if (Game.IsKeyPressed(KeyEvent.VK_E)) {
            this.editorEnabled = !this.editorEnabled;
            new Message("Editor " + (this.editorEnabled ? "enabled" : "disabled.") + " press E.", 5.0);
            if (this.editorEnabled && Game.IsKeyDown(KeyEvent.VK_SHIFT)) {
                this.editor = new TileMapEditor(Game.currentMap);
            }
        }

        Game.physics.Update(deltaTime); // Run physics update
    }

    // Draw FPS
    private void DrawFPS(Graphics2D g) {
        if (TileMapEditor.ED_FONT != null) {
            g.setFont(TileMapEditor.ED_FONT);
        } else {
            g.setFont(Game.font16);
        }
        
        String text = Long.toString(Math.round(Game.FPS)) + " FPS";
        FontMetrics m = g.getFontMetrics();
        int textWidth = m.stringWidth(text);

        g.setColor(Color.GREEN);
        g.drawString(text, Game.WIDTH - textWidth - 10, 32 + 10);
    }

    // Draws the game world, UI, and handles editor and menu rendering.
    public void Draw(Graphics2D g) {
        AffineTransform defaultTransform = g.getTransform();

        g.setTransform(Game.worldTransform);
        currentMap.Draw(g); // Draw the current map

        currentMap.ResetResponsiblities();

        if (Game.em != null)
            em.Draw(g); // Draw enemies
        if (Game.bm != null)
            bm.Draw(g); // Draw bullets

        if (Game.gfxManager != null)
            gfxManager.Draw(g); // Draw graphics manager

        if (this.editorEnabled) {
            try {
                // Temporarily set mouse to world mouse for editor
                Vector2 origPos = Game.mousePos.scale(1);
                Game.mousePos = Game.worldMousePos; 
                this.editor.Draw(g); // Draw editor
                Game.mousePos = origPos;
            } catch (NoninvertibleTransformException e) {
                // Exception handling (unreachable)
            }
        }

        // Draw enemies on the map
        for (Humanoid h : humanoids) {
            currentMap.RenderResponsibly(h);
        }

        g.setTransform(defaultTransform);

        // Draw the HUD and UI elements
        if (Game.hud != null)
            hud.Draw(g); // Draw HUD

        this.DrawFPS(g); // Draw FPS

        if (Game.menu != null) {
            Game.menu.Draw(g); // Draw menu
        } else {
            g.setFont(Game.font32);
            FontMetrics fm = g.getFontMetrics();
            String text = "Score: " + Game.score;
            int textWidth = fm.stringWidth(text);
            g.drawString(text, Game.WINDOW_WIDTH / 2 - textWidth / 2, 90); // Draw score
        }

        Panel.Draw(g); // Draw panel
    }

    // Resets the mouse state, including scroll and button presses.
    public static void ResetMouse() {
        Game.deltaScroll = 0; // Reset scroll
        for (int i = 0; i < Game.mouseButtonsDown.length; i++) {
            Game.mouseButtonsDown[i] = false; // Reset current button states
            Game.mouseButtonsDownLastFrame[i] = false; // Reset button states from the last frame
        }
    }

    private double lastDraw = Game.now();

    // Custom paint method for updating and rendering the game.
    @Override
    public void paint(Graphics gAbs) { 
        double now = Game.now();
        double deltaTime = now - this.lastDraw;

        // Update mouse position
        Point mp = this.getMousePosition();
        if (mp != null)
            Game.mousePos = new Vector2(mp.x, mp.y);

        // Update window dimensions
        Game.WINDOW_WIDTH = this.getWidth();
        Game.WINDOW_HEIGHT = this.getHeight();
        Game.WIDTH = Game.WINDOW_WIDTH;
        Game.HEIGHT = Game.WINDOW_HEIGHT;    

        // Calculate FPS and deltaTime
        Game.FPS = 1.0 / deltaTime;
        lastDraw = Game.now();
        Game.deltaTime = deltaTime;

        // Handle mouse scroll and update
        Game.deltaScroll = Game.scrollThisFrame - Game.scrollLastFrame;
        Game.scrollLastFrame = Game.scrollThisFrame;

        // Game update
        Update(deltaTime);

        Graphics2D g = (Graphics2D) gAbs;

        // Set rendering hints for performance and visuals
        GG.g = g;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Clear the screen
        g.setColor(new Color(28, 115, 255));
        g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

        // Draw the game elements
        Game.currentCursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
        this.Draw(g);

        // Update mouse button and key states
        for (int i = 0; i < Game.mouseButtonsDown.length; i++) {
            Game.mouseButtonsDownLastFrame[i] = Game.mouseButtonsDown[i];
        }
        for (int i = 0; i < Game.keysDown.length; i++) {
            Game.keysDownLastFrame[i] = Game.keysDown[i];
        }

        g.dispose();
    }


    // Function to close game & window
    public void Close() {
        System.out.println("[LOG]: Closing window.");

        // Close the window properly
        this.parentJFrame.dispose();
        this.parentJFrame.dispatchEvent(new WindowEvent(this.parentJFrame, WindowEvent.WINDOW_CLOSING)); // Call close event
    }

    @Override
    public void run() {
        final double refreshRate = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode().getRefreshRate();
        final double TARGET_FPS = refreshRate; // FPS we're aiming for'
        
        // this.targetFrameTime = 1.0 / TARGET_FPS;

        System.out.println("[LOG]: Monitor refresh rate is: " + refreshRate + " hz. Targetting.");

        this.setDoubleBuffered(true);
        
        // Last time
        double lastTick = 0;
        double updateDT = 0;

        while (Game.gameRunning) { // Loop while game is running
            updateDT = Game.now() - lastTick; // Calculate delta

            if (updateDT >= (1.0/TARGET_FPS)/2.0) { // If we're ready to render a frame render it
                lastTick = Game.now(); //Update( Update last tick)
                repaint(); // Tell the panel to call paint
            } else {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException err) {
                    System.err.println(err);
                }
            }
        }

        this.Close();
    }

    // Function to set the games fonts, if we failed to load fall back
    public static void SetFonts(String fontName) {
        if (fontName == null) {
            System.out.println("[WARN]: Using fallback fonts. You may encounter issues..");
            fontName = "Cascadia Code";
        }

        // Set the fonts
        Game.font16 = new Font(fontName, Font.PLAIN, 24);
        Game.font32 = new Font(fontName, Font.PLAIN, 32);
        Game.font64 = new Font(fontName, Font.PLAIN, 64);
    }

    // Function to load our custom fonts from disk and register them
    public static void LoadFontsFromFile() throws IOException, FontFormatException {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        
        // List of all fonts we need
        Font[] PixelifySans = {
            Font.createFont(Font.TRUETYPE_FONT, new File("./res/fonts/PixelifySans-Regular.ttf")),
            Font.createFont(Font.TRUETYPE_FONT, new File("./res/fonts/PixelifySans-Bold.ttf")),
            Font.createFont(Font.TRUETYPE_FONT, new File("./res/fonts/PixelifySans-SemiBold.ttf"))
        };

        // Log and register them
        for (Font f : PixelifySans) {
            System.out.println("[LOG]: Font Loaded \"" + f.getFontName() + "\" from file successfully!");

            ge.registerFont(f);
        }

        // Set them as the base font
        Game.SetFonts(PixelifySans[0].getFontName());
    }

    static boolean IsKeyDown(int keycode) {
        if (Game.inputBlockers.size() > 0) return false;
        
        return Game.keysDown[keycode] == true; // Check if it's down in our array'
    }
    static boolean IsKeyPressed(int keycode) {
        if (Game.inputBlockers.size() > 0) return false;
        
        return Game.keysDown[keycode] == true && Game.keysDownLastFrame[keycode] == false; // It's just pressed if it wasn't pressed last frame but is now
    }
    static boolean IsKeyReleased(int keycode) {
        if (Game.inputBlockers.size() > 0) return false;
        
        return Game.keysDown[keycode] == false && Game.keysDownLastFrame[keycode] == true; // Opposite for this
    }

    static boolean IsMouseDown(int mouseButton) {
        if (Game.inputBlockers.size() > 0) return false;
        
        return Game.mouseButtonsDown[mouseButton] == true; // Check if it's down in our array'
    }
    static boolean IsMousePressed(int mouseButton) {
        if (Game.inputBlockers.size() > 0) return false;
        
        return Game.mouseButtonsDown[mouseButton] == true && Game.mouseButtonsDownLastFrame[mouseButton] == false; // It's just pressed if it wasn't pressed last frame but is now
    }
    static boolean IsMouseReleased(int mouseButton) {
        if (Game.inputBlockers.size() > 0) return false;
        
        return Game.mouseButtonsDown[mouseButton] == false && Game.mouseButtonsDownLastFrame[mouseButton] == true; // Opposite for this
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        
        // If it's escape close the game.
        if (keyCode == KeyEvent.VK_ESCAPE && Game.keysDown[KeyEvent.VK_SHIFT]) {
            gameRunning = false;
        }
        
        if (keyCode > MAX_KEYS) {
            System.err.println("[WARN]: Encountered a key in key down larger than " + MAX_KEYS + ": " + keyCode);
        } else {
            Game.keysDown[keyCode] = true; // Set the keycode in our keying system to true
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int keyCode = e.getKeyCode();
        
        if (keyCode > MAX_KEYS) {
            System.err.println("[WARN]: Encountered a key in key up larger  than " + MAX_KEYS + ": " + keyCode);
        } else {
            Game.keysDown[keyCode] = false; // But false
        }
    }
    
    @Override
    public void keyTyped(KeyEvent e) {
        char c = e.getKeyChar();
        
        if (!Character.isISOControl(c)) {
            Game.textInputBuffer = Game.textInputBuffer.concat(Character.toString(c));
        }
        if ((c == 8 || c == 127) && Game.textInputBuffer.length() > 0) {
            if (Game.keysDown[KeyEvent.VK_CONTROL] == true) { // Manually checking as input is disabled (Game.IsKeyDown(...) will return false).
                int spaceIndex = Game.textInputBuffer.lastIndexOf(" ");

                spaceIndex = spaceIndex >= 0 ? spaceIndex : 0;
                
                Game.textInputBuffer = Game.textInputBuffer.substring(0, spaceIndex);
            } else {
                Game.textInputBuffer = Game.textInputBuffer.substring(0, Game.textInputBuffer.length() - 1);
            }
        }
        
        if (Game.textInputBuffer.length() >= 1024) {
            Game.textInputBuffer = Game.textInputBuffer.substring(1, Game.textInputBuffer.length());
        }
    }
}
