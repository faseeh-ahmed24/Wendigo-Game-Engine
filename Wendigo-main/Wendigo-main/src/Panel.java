
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;

/*
 * Message class used for notification that pop up.
 */
class Message {
    protected String message; // The message content
    protected double timeOut; // Duration the message should be displayed for
    protected boolean isError; // Flag to indicate whether the message is an error

    protected double deadLine; // Deadline when the message should be removed

    // Initializes the message, sets the deadline, and adds it to the displayed messages.
    private void Init() {
        this.deadLine = Game.now() + timeOut;

        // Log or error message output based on the flag
        if (this.isError) {
            System.err.println("[ERROR]: " + message); // Prints error message
        } else {
            System.out.println("[LOG]: " + message); // Prints log message
        }

        // Adds the message to the currently displayed messages list
        Panel.messages.add(this);
    }

    // Constructor to create a message with a specified content and timeout
    public Message(String message, double timeOut) {
        this.message = message;
        this.timeOut = timeOut;
        Init();
    }

    // Constructor to create a message with a specified content, timeout, and error flag
    public Message(String message, double timeOut, boolean error) {
        this.message = message;
        this.timeOut = timeOut;
        this.isError = error;
        Init();
    }

    // Constructor to create a message with a specified content and a default timeout of 7.5 seconds
    public Message(String message) {
        this.message = message;
        this.timeOut = 7.5;
        Init();
    }

    // Constructor to create a message with a specified content and an error flag, with a default timeout of 7.5 seconds
    public Message(String message, boolean error) {
        this.message = message;
        this.timeOut = 7.5;
        this.isError = error;
        Init();
    }
}

class Panel {
    /*
     * Basic Colours we use.
     */
    static Color PANEL_BG = new Color(0x2e2e2e);
    static Color BUTTON_BG = new Color(0x404040);
    static Color BUTTON_HOV_BG = new Color(0x666666);
    static Color BUTTON_DOWN_BG = new Color(0x252525);
    static Color BUTTON_BORDER = new Color(0x202020);
    static Color BUTTON_HILI_BG = new Color(0x3b8c4d);
    /* 
     * Some default settings. Can be modified.
     */
    static double PADDING = 6.0;
    static double LINE_HEIGHT = TileMapEditor.ED_FONT_SIZE + 2*PADDING;
    
    /*
     * Currently displayed messages. Logic is handled in Panel's static Draw(Graphics2D g).. function.
     */
    public static ArrayList<Message> messages = new ArrayList<>();

    /*
     * Where the window currently is.
     */
    public Vector2 windowPosition = null;
    public Vector2 windowSize = null;

    /*
     * Internal stuff used for layouting.
     */
    public Vector2 position = null;
    public Vector2 size = null;

    private Graphics2D g;

    /*
     * Context or "Title" of windows.
     */
    public static String context = null;
    public boolean open = true;

    /*
     * Flags for next button user requests.
     * TODO: Merge these flags into an integer and use bit masking.
     */
    public boolean nextButtonDisabled = false;
    public boolean nextButtonHighlight = false;
    public boolean nextButtonAbsPos = false;

    /*
     * Keeping track of the scroll state of each list, as we are
     * immediate mode we cannot easily do that.
     */
    private static HashMap<String, Double> scrolls = new HashMap<>();
    private static HashMap<String, Double> scrollsTarget = new HashMap<>();

    /*
     * Stack of graphics clippings, for when we have, say, nested lists.
     */
    private ArrayList<Shape> clipsStack = new ArrayList<>();

    /*
     * Resizing panel logic.
     * TODO: Reizing panels doesn't work properly on some corners.
     */
    private boolean isResizing = false;
    private Vector2 resizingStartMousePos;
    private Vector2 resizingStartPrevSize;
    private Vector2 resizingStartPrevPos;
    private Vector2 resizingRestriction = new Vector2(1.0);
    private boolean flipResizingAnchor = true;

    /*
     * Disable this panel.
     */
    public boolean disabled = false;

    /*
     * Are we dragging the panel.
     */
    private boolean isMoving = false;

    /*
     * Dragging logic.
     */
    private Vector2 movementPrevPos;
    private Vector2 movementStartMousePos;

    /*
     * Variables for the user input pop up.
     */
    private static String inputPrompt = null; // The prompt

    protected Vector2 lastButtonSize;
    
    /*
     * Max size we accept the input to be.
     */
    public    static int inputMaxSize = 128;
    private   static boolean inputOpen = false;
    private   static boolean inputJustClosed = false;
    protected static String inputInput = null; // The text
    
    /* For anything we need to draw on top of everything, we defer it to this function.
     * This function should usually be called towards the end of your entire paint job.
     */
    public static void Draw(Graphics2D g) {
        if (Panel.inputOpen) { // Display input field if it's active.
            // Temporarily block all input from game.
            if (!Game.inputBlockers.contains("PanelInputField"))
                Game.inputBlockers.add("PanelInputField");
            
            // Dim screen
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRect(0, 0, Game.WINDOW_WIDTH, Game.WINDOW_HEIGHT);
            
            // Visual rectangle
            Rectangle inputRectangle = new Rectangle();
            inputRectangle.width = Game.WINDOW_WIDTH/4 + 200;
            inputRectangle.height = 80;
            
            inputRectangle.x = (int)(Game.WINDOW_WIDTH/2.0 - inputRectangle.width/2.0);
            inputRectangle.y = (int)(Game.WINDOW_HEIGHT/2.0 - inputRectangle.height/2.0);
            
            // Draw it
            g.setColor(new Color(24, 24, 24));
            GG.fillRect(inputRectangle);
            
            g.setColor(Color.WHITE);

            // Set font and get font metrics.
            g.setFont(TileMapEditor.ED_FONT);
            FontMetrics fm = g.getFontMetrics();

            // Calculate layout stuff
            int y = (int)(inputRectangle.y + Panel.PADDING + fm.getHeight());
            int textWidth = fm.stringWidth(Game.textInputBuffer);

            // Trim the buffer if it's too long.
            if (Game.textInputBuffer.length() >= inputMaxSize) {
                Game.textInputBuffer = Game.textInputBuffer.substring(0, inputMaxSize);
            }

            // Draw the prompt
            g.drawString(inputPrompt, (int)(inputRectangle.x + Panel.PADDING), y);
            y += 2*Panel.PADDING;
            
            g.setColor(new Color(48, 48, 48));
            g.fillRect(inputRectangle.x, y, inputRectangle.width, fm.getHeight()*2);

            // Draw the current text
            g.setColor(new Color(200, 200, 200));
            g.drawString(Game.textInputBuffer, (int)(inputRectangle.x + Panel.PADDING), (int)(y + fm.getHeight()*1.25));

            y += fm.getHeight()*1.25;

            // Blinking transparency of cursor.
            int value = (int)((Math.sin(10*Game.now())+1)*0.5*255);
            int x = (int)(inputRectangle.x + PADDING + textWidth);

            // Draw the cursor
            g.setColor(new Color(255, 255, 255, value));
            g.drawLine(x, y+4, x, y - fm.getHeight()+4);

            // Input field close condition
            if (Game.keysDown[KeyEvent.VK_ENTER] == true || Game.keysDown[KeyEvent.VK_ESCAPE] == true) {
                System.out.println("Closing!");
                Panel.inputPrompt = null;
                inputInput = Game.textInputBuffer + ""; // Make a duplicate?  ¯\_(ツ)_/¯
                Panel.inputOpen = false;
                Panel.inputJustClosed = true;
            }
        } else {
            Game.inputBlockers.remove("PanelInputField"); // Stop blocking input
        }

        { /* Rendering the messages. */
            g.setFont(TileMapEditor.ED_FONT);

            int y = 4 * (int)Panel.PADDING;
            FontMetrics fm = g.getFontMetrics();

            double now = Game.now();

            /* Delete Expired Messages */
            Panel.messages.removeIf(n -> (now >= n.deadLine));

            for (Message m : Panel.messages) {
                final double messageFadeTime = 4.0;

                int       messageWidth = fm.stringWidth(m.message);
                Rectangle messageRectangle = new Rectangle();
                double    alpha = 1.0;
                
                double timeRemaining = m.deadLine - now;

                /* Save the original composite */
                Composite originalComposite = g.getComposite();

                messageRectangle.width = (int)(messageWidth + PADDING*2);
                messageRectangle.height = (int)(fm.getMaxAscent() + PADDING*2);

                messageRectangle.x = (Game.WINDOW_WIDTH/2 - messageRectangle.width/2);
                messageRectangle.y = y;

                if (timeRemaining < messageFadeTime) {
                    double timeRemainingNormalized = (timeRemaining/messageFadeTime);
                    alpha = timeRemainingNormalized;
                }

                /* Set everything transparent */
                if (alpha < 1.f)
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float)alpha));


                /* Background */
                g.setColor(new Color(12, 12, 12));
                GG.fillRect(messageRectangle);

                /* Border */
                g.setColor(new Color(64, 64, 64));
                GG.drawRect(messageRectangle);

                /* Draw message */
                g.setColor(m.isError ? Color.RED : Color.LIGHT_GRAY);
                g.drawString(m.message, (int)(messageRectangle.x + PADDING), (int)(messageRectangle.y + Panel.PADDING + fm.getMaxAscent()));
                
                /* Restore composite */
                g.setComposite(originalComposite);

                y += messageRectangle.height + Panel.PADDING;
            }
        }
    }

    /* Function to create an immediate input field
     * Returns: True if the user inputting in the field is finished (Enter).
     */
    public static boolean InputField(String context, String initialText) {
        if (Panel.inputJustClosed) { // If it's closed now, we had it open. So return it's closed.
            Panel.inputJustClosed = false;
            return true;
        } else {
            // if it's not open open it.
            if (Panel.inputPrompt == null) {
                Panel.inputPrompt = context;
                Panel.inputJustClosed = false;
                Panel.inputInput = null;
                Panel.inputOpen = true;
                Game.textInputBuffer = initialText != null ? initialText : "";
            }
        }
        
        return false;
    }

    public Panel() { }

    // Begins drawing and setting up the panel, including background, border, and resize interaction logic
    public void Begin(Graphics2D g, Vector2 position, Vector2 size) {
        this.g = g;

        // Initialize position and size if they are not set
        if (this.size == null) {
            this.size = size;
            this.windowSize = size.scale(1.0);
        }
        if (this.position == null) {
            this.position = position;
            this.windowPosition = position.scale(1.0);
        }

        // Handle scroll behavior for the panel
        for (HashMap.Entry<String, Double> entry : Panel.scrollsTarget.entrySet()) {
            String key = entry.getKey();
            Double targetValue = entry.getValue();
            Double currentValue = Panel.scrolls.get(key);

            if (targetValue == null) continue;
            if (currentValue == null) currentValue = 0.0;

            Panel.scrolls.put(key, Vector2.lerpFRI(currentValue, targetValue, 0.995, Game.deltaTime));
        }

        // Set the background color and fill the panel's background
        g.setColor(PANEL_BG);
        GG.fillRect(this.position, this.size);

        // Set the border color and draw the panel border
        g.setColor(this.disabled ? BUTTON_DOWN_BG : new Color(0x777777));
        GG.drawRect(this.position.sub(new Vector2(1, 1)), this.size.add(new Vector2(2, 2)));

        // Grabbing logic for resizing the panel
        double grabbingRadius = 10.0;
        boolean grabbingTop = Vector2.AABBContainsPoint(windowPosition.add(new Vector2(0, -grabbingRadius)),
                                                        new Vector2(windowSize.x, grabbingRadius),
                                                        Game.mousePos);
        boolean grabbingBottom = Vector2.AABBContainsPoint(windowPosition.add(new Vector2(0, this.windowSize.y)),
                                                            new Vector2(windowSize.x, grabbingRadius),
                                                            Game.mousePos);
        boolean grabbingLeft = Vector2.AABBContainsPoint(windowPosition.add(new Vector2(-grabbingRadius, 0)),
                                                        new Vector2(grabbingRadius, windowSize.y),
                                                        Game.mousePos);
        boolean grabbingRight = Vector2.AABBContainsPoint(windowPosition.add(new Vector2(windowSize.x, 0)),
                                                        new Vector2(grabbingRadius, windowSize.y),
                                                        Game.mousePos);

        Vector2 scalingDifference = new Vector2();

        // Mouse press and release logic for resizing the panel
        if (!this.disabled && Game.IsMousePressed(MouseEvent.BUTTON1)) {
            if (!this.isResizing && (grabbingTop || grabbingBottom || grabbingRight || grabbingLeft)) {
                if (this.resizingStartMousePos == null) {
                    this.resizingStartMousePos = Game.mousePos.scale(1.0);
                    this.resizingStartPrevSize = this.windowSize.scale(1.0);
                    this.resizingStartPrevPos = this.windowPosition.scale(1.0);

                    // Restrict resizing based on the direction of the grab
                    if (grabbingTop || grabbingBottom) {
                        this.resizingRestriction = new Vector2(0.0, 1.0);
                    } else if (grabbingRight || grabbingLeft) {
                        this.resizingRestriction = new Vector2(1.0, 0.0);
                    } else {
                        this.resizingRestriction = new Vector2(1.0);
                    }

                    // Flip resizing anchor based on the grab direction
                    this.flipResizingAnchor = !(grabbingTop || grabbingLeft);
                }
                this.isResizing = true;
            }
        }

        // Mouse release resets resizing
        if (!this.disabled && Game.IsMouseReleased(MouseEvent.BUTTON1)) {
            this.resizingStartMousePos = null;
            this.resizingStartPrevSize = null;
            this.isResizing = false;
        }

        // Adjust panel size if resizing is happening
        if (this.isResizing) {
            scalingDifference = this.resizingStartMousePos.sub(Game.mousePos).mult(this.resizingRestriction);
        }

        // Set the cursor to the appropriate resize cursor based on mouse position
        int cursor = -1;
        if (grabbingTop) {
            cursor = Cursor.N_RESIZE_CURSOR;
        } else if (grabbingRight) {
            cursor = Cursor.E_RESIZE_CURSOR;
        } else if (grabbingLeft) {
            cursor = Cursor.W_RESIZE_CURSOR;
        } else if (grabbingBottom) {
            cursor = Cursor.S_RESIZE_CURSOR;
        }
        if (!this.disabled && cursor != -1) {
            Game.currentCursor = Cursor.getPredefinedCursor(cursor);
        }

        // Update window size and position if resizing is occurring
        if (this.isResizing) {
            if (this.flipResizingAnchor) {
                this.windowSize = this.resizingStartPrevSize.sub(scalingDifference);
            } else {
                this.windowPosition = this.resizingStartPrevPos.sub(scalingDifference);
                this.windowSize = this.resizingStartPrevSize.sub(this.windowPosition.sub(this.resizingStartPrevPos));
            }
        }

        // Store the current clipping area and set a new clip for the panel
        this.clipsStack.add(g.getClip());
        g.setClip((int)this.windowPosition.x, (int)this.windowPosition.y,
                (int)this.windowSize.x, (int)this.windowSize.y);
    }

    // Ends drawing the panel and restores the previous clipping area
    public void End() {
        this.position = this.windowPosition.scale(1.0);
        this.size = this.windowSize.scale(1.0);

        // If the panel is disabled, draw a translucent overlay to indicate it's inactive
        if (this.disabled) {
            g.setColor(new Color(0, 0, 0, 200));
            GG.fillRect(this.windowPosition, this.windowSize);
        }

        // Restore the previous clipping area from the stack
        Shape firstClip = this.clipsStack.size() > 0 ? this.clipsStack.get(0) : null;
        if (firstClip != null) {
            g.setClip(firstClip);
        } else {
            g.setClip(null);
        }
        this.clipsStack.clear();
    }
    
    // Sets the name of the panel, optionally prefixing with context, and handles drag functionality for the panel header
    public void Name(String name) {
        // If a context is provided, prefix the name with the context
        if (context != null) {
            name = context + " - " + name;
        }

        // Calculate label dimensions and position it at the top of the panel
        Vector2 labelDims = this.CenteredLabel(name, new Vector2(0, PADDING), new Vector2(this.size.x, 4 + TileMapEditor.ED_FONT_SIZE));
        double y = 2 * PADDING + this.position.y + PADDING + labelDims.y;

        // Draw a horizontal line under the header
        g.setColor(new Color(0x777777));
        GG.drawLine(this.position.x, y, this.position.x + this.size.x, y);

        // Check if the mouse is hovering over the header, and handle dragging if the mouse is pressed
        boolean mouseInHeader = Vector2.AABBContainsPoint(this.position, labelDims, Game.mousePos);
        if (Game.IsMousePressed(MouseEvent.BUTTON1)) {
            if (!this.isResizing && mouseInHeader) {
                this.isMoving = true;
                this.movementPrevPos = this.windowPosition.scale(1.0);
                this.movementStartMousePos = Game.mousePos.scale(1.0);
            }
        }

        // Stop dragging when the mouse is released or resizing starts
        if (this.isResizing || Game.IsMouseReleased(MouseEvent.BUTTON1)) {
            this.isMoving = false;
        }

        // Move the panel if it's being dragged
        if (!this.disabled && this.isMoving) {
            Game.currentCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
            this.windowPosition = this.movementPrevPos.add(Game.mousePos.sub(this.movementStartMousePos));
        }

        // Adjust the panel position to account for the header
        this.position.y = y + PADDING;
    }

    // Creates and returns a close button ("x") for the panel, and handles its click functionality
    public boolean CloseButton() {
        // Define the button size
        final double buttonSize = 20.f;
        double x = (this.windowPosition.x + this.windowSize.x) - buttonSize - PADDING, y = this.windowPosition.y + PADDING;

        // Mark the button position for absolute positioning
        this.nextButtonAbsPos = true;

        // Return the result of the button press check
        return this.Button("x", new Vector2(x, y), new Vector2(buttonSize));
    }

    private Vector2 currentEntryPos;
    private Vector2 currentEntrySize;
    private Vector2 currentEntryPrevPos;
    private Vector2 currentEntryRCursor;
    private Vector2 currentEntryLabelDims;

    // Begins an entry with a header, drawing the header line and setting up position and size for subsequent UI elements
    public Vector2 EntryBegin(String headerName) {
        // Position for the entry and size based on line height
        Vector2 position = this.position;
        Vector2 size = new Vector2(this.size.x, LINE_HEIGHT);

        // Draw a line below the entry header
        g.setColor(BUTTON_BG);
        GG.drawLine(position.x + PADDING, position.y + size.y,
                    position.x + size.x - 2 * PADDING, position.y + size.y);

        // Set label dimensions for the entry header
        Vector2 labelDims = this.CenteredYLabel(headerName, new Vector2(PADDING, 0), new Vector2(0, LINE_HEIGHT));

        // Store current entry's dimensions and positions for further manipulation
        this.currentEntryLabelDims = labelDims;
        this.currentEntryPos = position;
        this.currentEntrySize = size;
        this.currentEntryPrevPos = this.position.scale(1.0);
        this.currentEntryRCursor = new Vector2(this.size.x - PADDING, 0);

        // Return size for the entry header
        return size;
    }

    // Creates and handles the button for an entry, drawing it and checking its pressed state
    public boolean EntryButton(String text) {
        // Define button height based on font size and padding
        double buttonHeight = TileMapEditor.ED_FONT_SIZE + PADDING;

        // Create button at the calculated position from the top-right of the entry
        boolean state = this.ButtonFromTopRight(text,
                                                this.currentEntryRCursor.add(new Vector2(0, LINE_HEIGHT / 2.0 - buttonHeight / 2.0)),
                                                new Vector2(0, buttonHeight));

        // Adjust the cursor position for the next button
        this.currentEntryRCursor.x -= this.lastButtonSize.x + PADDING;

        // Return the state of the button (whether it was pressed)
        return state;
    }
    
    public double nextSliderWidth = 0;
    // Creates and handles a slider for adjusting a value within a given range (min to max),
    // drawing the slider on the screen and interacting with user input (mouse/keyboard).
    public double EntrySlider(double value, double min, double max) {
        double sliderThickness = 6.0; // Thickness of the slider's graphical representation

        // Calculate the position and size of the slider
        double rightX = this.currentEntryRCursor.x + this.position.x - PADDING;
        double leftX = this.currentEntryPos.x + this.currentEntryLabelDims.x + 4 * PADDING;
        Vector2 sliderSize = new Vector2(rightX - leftX, sliderThickness);

        // If a specific width for the slider is set, adjust it
        if (this.nextSliderWidth != 0) {
            if (this.nextSliderWidth <= 1.0) {
                sliderSize.x = sliderSize.x * this.nextSliderWidth;
            } else {
                sliderSize.x = this.nextSliderWidth;
            }
        }
        this.nextSliderWidth = 0;

        // Position the slider within the entry section
        Vector2 sliderPos = this.position.add(this.currentEntryRCursor)
                                        .add(new Vector2(-(sliderSize.x + PADDING), LINE_HEIGHT / 2.0 - sliderThickness / 2.0));

        // Define the hitbox for the slider to detect mouse hover
        Rectangle sliderHitBox = new Rectangle((int)sliderPos.x, (int)(this.currentEntryRCursor.y + this.position.y), (int)sliderSize.x, (int)LINE_HEIGHT);
        boolean hovering = sliderHitBox.contains(Game.mousePos.x, Game.mousePos.y);
                                    
        // Handle keyboard input (left and right arrows) for adjusting the value
        if (hovering) {
            if (Game.IsKeyPressed(KeyEvent.VK_LEFT)) {
                value -= 1.0;
            } else if (Game.IsKeyPressed(KeyEvent.VK_RIGHT)) {
                value += 1.0;
            }
        }

        // Prepare to draw the slider and related text
        g.setFont(TileMapEditor.ED_FONT);
        FontMetrics m = g.getFontMetrics();

        String maxText = Integer.toString((int)max), minText = Integer.toString((int)min);
        double maxTextWidth = m.stringWidth(maxText), minTextWidth = m.stringWidth(minText);

        // Add padding around the text for spacing
        double dotSize = 12.0;
        double currentPercent = (value - min) / (max - min);

        // Adjust for the width of the text labels
        maxTextWidth += PADDING / 2.0;
        minTextWidth += PADDING / 2.0;

        sliderSize.x -= (maxTextWidth + minTextWidth);
        sliderPos.x += minTextWidth;

        // Determine the position of the slider's draggable dot based on the current value
        Vector2 dotPosition = new Vector2(sliderPos.x + sliderSize.x * currentPercent - dotSize / 2.0, sliderPos.y + sliderThickness / 2.0 - dotSize / 2.0);

        // Draw the slider background
        g.setColor(BUTTON_BG);
        GG.fillRoundRect(sliderPos.x, sliderPos.y, sliderSize.x, sliderSize.y, sliderThickness, sliderThickness);

        double textY = -sliderThickness / 2.0 + sliderPos.y + m.getHeight() - m.getHeight() / 2.0;

        // Draw the minimum and maximum value labels
        g.setColor(Color.WHITE);
        GG.drawString(minText, sliderPos.x - minTextWidth - PADDING / 2.0, textY);
        GG.drawString(maxText, sliderPos.x + sliderSize.x + PADDING / 2.0, textY);

        // Draw the draggable dot for the slider
        g.setColor(BUTTON_HILI_BG);
        GG.fillOval(dotPosition, new Vector2(dotSize));

        // Apply a color overlay when not hovering over the slider
        if (!hovering) {
            Color overlay = new Color(46, 46, 46, 100);
            g.setColor(overlay);
            g.fillRect(sliderHitBox.x, sliderHitBox.y, sliderHitBox.width, sliderHitBox.height);
        } else if (!this.disabled) {
            // Display the current value text at the slider's dot position
            String currentValueText = Integer.toString((int)Vector2.lerp(min, max, currentPercent));
            int currentValueWidth = m.stringWidth(currentValueText);

            g.setColor(BUTTON_BORDER);
            GG.fillRect(dotPosition.x + dotSize / 2.0 - currentValueWidth / 2.0, dotPosition.y + dotSize / 2.0 - m.getAscent() / 2.0, currentValueWidth, m.getAscent());
            g.setColor(Color.WHITE);
            GG.drawString(currentValueText, dotPosition.x + dotSize / 2.0 - currentValueWidth / 2.0, m.getAscent() + dotPosition.y + dotSize / 2.0 - m.getAscent() / 2.0);

            // Handle mouse drag to update slider value
            if (Game.IsMouseDown(MouseEvent.BUTTON1)) {
                double newPercentage = (Game.mousePos.x - sliderPos.x) / sliderSize.x;
                if (newPercentage > 1.0) newPercentage = 1.0;
                if (newPercentage < 0.0) newPercentage = 0.0;

                return min + (max - min) * newPercentage;
            }
        }

        // Return the adjusted value based on user interaction
        return value;
    }

    // Ends the current entry layout and adjusts the position for the next entry
    public void EntryEnd() {
        this.position = this.currentEntryPrevPos.add(new Vector2(0.0, this.currentEntrySize.y + PADDING));
    }

    // If true, the next label will be placed at an absolute position
    public boolean nextLabelAbsPosition = false;

    // Centers a label within the given position and size, drawing it on the screen
    public Vector2 CenteredLabel(String text, Vector2 position, Vector2 size) {
        if (!this.nextLabelAbsPosition)
            position = position.add(this.position);
        g.setFont(TileMapEditor.ED_FONT);

        FontMetrics m = g.getFontMetrics();
        int textWidth = m.stringWidth(text);

        g.setColor(Color.WHITE);
        g.drawString(text, (int)(position.x + size.x / 2 - textWidth / 2),
                    (int)(position.y + size.y / 2.0 + m.getHeight() / 2.0));
        
        this.nextLabelAbsPosition = false;

        return size;
    }

    // Adjusts the position vertically after adding a button with the given spacing
    public void LayoutVertBAdded(double spacing) {
        Vector2 buttonSize = this.lastButtonSize;
        this.position.y += buttonSize.y + spacing;
    }

    // Starts a flow layout, initializing cursor and height for layout elements
    private double flowLayoutCurrentHeight = 0;
    private Vector2 flowLayoutCursor = new Vector2();
    public void FlowLayoutBegin() {
        this.flowLayoutCursor = new Vector2(PADDING);
        this.flowLayoutCurrentHeight = 0;
    }

    // Ends the flow layout and adjusts the position to fit all elements
    public void FlowLayoutEnd() {
        this.position = new Vector2(this.position.x, this.position.y + this.flowLayoutCursor.y + this.flowLayoutCurrentHeight + 2 * PADDING);
    }

    // Adds an element to the flow layout, adjusting position and wrapping if necessary
    public Vector2 FlowLayoutAdd(Vector2 size) {
        if (this.flowLayoutCursor.x + size.x > this.size.x) {
            this.flowLayoutCursor.y += this.flowLayoutCurrentHeight + PADDING;
            this.flowLayoutCursor.x = PADDING;
            this.flowLayoutCurrentHeight = 0;
        }
        
        if (size.y > this.flowLayoutCurrentHeight) {
            this.flowLayoutCurrentHeight = size.y;
        }

        Vector2 elementPos = flowLayoutCursor.scale(1.0);
        flowLayoutCursor.x += size.x + PADDING;
        
        return elementPos.add(this.position);
    }

    // Retrieves the current scroll value for a list based on its unique name
    private Vector2 currentListPrevPosition;
    private Vector2 currentListPrevSize;
    protected Vector2 currentListTopLeft;
    private String currentListRandomName;
    public double GetListScroll() {
        Double value = Panel.scrolls.get(this.currentListRandomName);
        if (value != null) {
            return value.doubleValue();
        }
        return 0;   
    }

    // Begins a new list layout, adjusting the position and size based on the provided values
    public Vector2 ListBegin(String uniqueName, Vector2 offset, Vector2 size) {
        this.currentListRandomName = uniqueName;
        this.currentListPrevPosition = this.position.scale(1.0); 
        this.currentListPrevSize = this.size.scale(1.0);

        // Adjust offsets if given as relative values
        if (offset.x <= 1.0) {
            offset.x = this.size.x * offset.x;
        }
        if (offset.y <= 1.0) {
            offset.y = this.remainingVerticalSpace() * offset.y;
        }

        Vector2 position = this.position.add(new Vector2(PADDING, 0)).add(offset);
        this.currentListTopLeft = position.scale(1.0);

        // Adjust size based on relative values or expand to full width/height
        if (size.x >= -1.0 && size.x <= 1.0) {
            size.x = (this.size.x - 2 * PADDING) * size.x;
        }
        if (size.y >= -1.0 && size.y <= 1.0) {
            size.y = (this.remainingVerticalSpace() - PADDING) * size.y;
        }

        if (size.x == 0) {
            size.x = this.size.x - 2 * PADDING; // Expand to full width
        }
        if (size.y <= 0) {
            size.y = this.size.y - (this.position.y - this.windowPosition.y) - PADDING + size.y; // Remaining space
        }

        // Draw the border of the list container
        g.setColor(BUTTON_BORDER);
        GG.drawRect(position, size);

        this.clipsStack.add(g.getClip());
        g.setClip((int)position.x, (int)position.y, (int)size.x, (int)size.y);

        // Adjust for scroll offset
        Double scrollPixels = Panel.scrolls.get(this.currentListRandomName);
        if (scrollPixels == null) {
            scrollPixels = 0.0;
        }

        // g.translate(0, -scrollPixels);
        this.position.x = position.x;
        this.position.y = position.y;
        this.position.y -= scrollPixels;

        this.size = size;
        
        return size;
    }

    // Ends the current list layout, updates the scroll position, and adjusts the view
    public void ListEnd() {
        this.currentListPrevPosition.y += this.size.y + PADDING;
        
        Double scrollPixels = Panel.scrollsTarget.get(this.currentListRandomName);
        Double scrollPixelsNow = Panel.scrolls.get(this.currentListRandomName);

        if (scrollPixels == null || scrollPixelsNow == null) {
            scrollPixels = 0.0;
            scrollPixelsNow = 0.0;
        }

        double contentBottomCoord = ((this.position.y + scrollPixels) - this.currentListTopLeft.y);

        if ((contentBottomCoord != 0 && contentBottomCoord > this.size.y)) {
            double scrollBarWidth = 10.0;
            Vector2 scrollBarPos = this.currentListTopLeft.add(new Vector2(this.size.x - scrollBarWidth, 0));
            Vector2 scrollBarSize = new Vector2(scrollBarWidth, this.size.y);

            double percentContentVisible = this.size.y / contentBottomCoord;
            double scrollBarButtonSizeY = this.size.y * percentContentVisible;

            double minScroll = 0.0, maxScroll = contentBottomCoord - this.size.y + 2 * PADDING;
            double deltaScroll = Game.deltaScroll;

            // Clamping the scroll position within bounds
            if (scrollPixels < minScroll) {
                scrollPixels = minScroll;
                deltaScroll = 0;
            } else if (scrollPixels > maxScroll) {
                scrollPixels = maxScroll;
                deltaScroll = 0;
            }
            
            // Scroll action based on mouse interaction
            if (!this.disabled && Vector2.AABBContainsPoint(this.currentListTopLeft, this.size, Game.mousePos)) {
                scrollPixels += deltaScroll * 4.0;
            }

            // Update scroll bar button position
            double percentScroll = scrollPixelsNow / maxScroll;
            double scrollBarButtonOffset = percentScroll * (this.size.y - scrollBarButtonSizeY);
            Vector2 scrollBarButtonPosition = scrollBarPos.add(new Vector2(0, scrollBarButtonOffset));
            Vector2 scrollBarButtonSize = new Vector2(scrollBarWidth, scrollBarButtonSizeY);

            // Drawing the scroll bar
            g.setColor(BUTTON_DOWN_BG);
            GG.fillRect(scrollBarPos.sub(new Vector2(1, 1)), scrollBarSize.add(new Vector2(2, 2)));
            g.setColor(BUTTON_BG);
            GG.drawRect(scrollBarPos, scrollBarSize);
            g.setColor(BUTTON_BG);
            GG.fillRect(scrollBarButtonPosition, scrollBarButtonSize);
        } else {
            scrollPixels = 0.0;
        }
        
        // Save the scroll position
        Panel.scrollsTarget.put(this.currentListRandomName, scrollPixels);

        // Restore the previous position and size
        this.position = this.currentListPrevPosition;
        this.size = this.currentListPrevSize;

        // Restore clipping
        if (this.clipsStack.size() > 0) {
            g.setClip(this.clipsStack.remove(this.clipsStack.size() - 1));
        } else {
            g.setClip(null);
        }
    }

    // Returns the remaining vertical space available for layout
    private double remainingVerticalSpace() {
        return this.size.y - (this.position.y - this.windowPosition.y);
    }

    // Centers a label vertically within the given size and draws it at the specified position
    public Vector2 CenteredYLabel(String text, Vector2 position, Vector2 size) {
        position = position.add(this.position);
        g.setFont(TileMapEditor.ED_FONT);

        FontMetrics m = g.getFontMetrics();
        g.setColor(Color.WHITE);

        int baselineY = (int) (position.y + (size.y - (m.getAscent() + m.getDescent())) / 2 + m.getAscent());

        size.x = m.stringWidth(text);
        g.drawString(text, (int)(position.x), baselineY);
        
        return size;
    }

    // Calculates the dimensions of a button based on its text, position, and size
    public Rectangle CalculateButtonDims(String text, Vector2 position, Vector2 size) {
        if (!this.nextButtonAbsPos) {
            if (position.x <= 1.0) {
                position.x = position.x * this.size.x;
            }
            if (position.y <= 1.0) {
                position.y = position.y * this.remainingVerticalSpace();
            }

            position = position.add(this.position);
        }

        g.setFont(TileMapEditor.ED_FONT);

        FontMetrics m = g.getFontMetrics();
        int textWidth = m.stringWidth(text);
        
        double compSizeX = textWidth + 1.5 * PADDING, compSizeY = m.getHeight() + PADDING;
        if (size == null)
            size = new Vector2(compSizeX, compSizeY);

        if (size.x == 0.0)
            size.x = compSizeX;
        if (size.y == 0.0)
            size.y = compSizeY;
            
        if (size.x <= 1.0)
            size.x = this.size.x * size.x;
        if (size.y <= 1.0) {
            size.y = size.y * this.remainingVerticalSpace();
        }

        return new Rectangle((int)position.x, (int)position.y, (int)size.x, (int)size.y);
    }

    // Creates and displays a button, checking for interactions (hovering/click)
    public boolean Button(String text, Vector2 position, Vector2 size) {
        if (text == null) text = "N/A";

        Rectangle dims = CalculateButtonDims(text, position, size);

        position.x = dims.x;
        position.y = dims.y;
        size.x = dims.width;
        size.y = dims.height;

        g.setFont(TileMapEditor.ED_FONT);

        FontMetrics m = g.getFontMetrics();
        int textWidth = m.stringWidth(text);

        boolean inactive = this.nextButtonDisabled;
        boolean highlight = this.nextButtonHighlight;
        boolean hovering = !this.disabled && (!inactive && Vector2.AABBContainsPoint(position, size, Game.mousePos));
        boolean clicked = Game.IsMouseReleased(MouseEvent.BUTTON1);

        if (inactive) {
            g.setColor(BUTTON_BORDER);
        } else if (highlight) {
            g.setColor(BUTTON_HILI_BG);
        } else {
            g.setColor(hovering ? (clicked ? BUTTON_DOWN_BG : BUTTON_HOV_BG) : BUTTON_BG);
        }
        GG.fillRect(position, size);
        g.setColor(inactive ? BUTTON_DOWN_BG : BUTTON_BORDER);
        GG.drawRect(position, size);

        // Calculate the baseline Y-coordinate for vertically centered text
        int baselineY = (int) (position.y + (size.y - (m.getAscent() + m.getDescent())) / 2 + m.getAscent());

        g.setColor(inactive ? Color.GRAY : Color.WHITE);
        g.drawString(text, (int)(position.x + size.x / 2 - textWidth / 2), baselineY);
        
        this.lastButtonSize = size;
        this.nextButtonDisabled = false;
        this.nextButtonHighlight = false;
        this.nextButtonAbsPos = false;

        return hovering && clicked;
    }

    // Creates and displays a button from the top right position, checking for interactions (hovering/click)
    public boolean ButtonFromTopRight(String text, Vector2 position, Vector2 size) {
        g.setFont(TileMapEditor.ED_FONT);

        FontMetrics m = g.getFontMetrics();
        
        int textWidth = m.stringWidth(text);
        if (size == null)
            size = new Vector2(textWidth + 1.5 * PADDING, m.getHeight() + PADDING);
        if (size.x == 0.0)
            size.x = textWidth + 1.5 * PADDING;

        return this.Button(text, position.sub(new Vector2(size.x, 0)), size);
    }

}

