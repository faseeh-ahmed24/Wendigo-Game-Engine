import java.awt.Graphics2D;

// Enum representing the possible states of the menu (Play or Quit).
enum MenuState {
    Play,
    Quit
}

// MainMenu class responsible for displaying the main menu, handling user input, and updating the menu's state.
public class MainMenu {

    MenuState state = null; // The current state of the menu (Play or Quit)

    // Updates the MainMenu state.
    public void Update(double deltaTime) {
        // Update the MainMenu
    }

    // Draws the MainMenu to the provided Graphics2D context.
    public void Draw(Graphics2D g) {
        double padding = 120; // Padding around the menu

        // Creates a new panel for the menu and calculates the size.
        Panel menuPanel = new Panel();
        Vector2 size = new Vector2(Game.WINDOW_WIDTH, Game.WINDOW_HEIGHT).sub(new Vector2(padding*2));
        
        // Sets the maximum and minimum size of the menu panel
        size.x = Math.min(size.x, 600);
        size.y = Math.max(size.y, 300);
        
        // Begins drawing the menu panel
        menuPanel.Begin(g, new Vector2(Game.WINDOW_WIDTH, Game.WINDOW_HEIGHT).scale(0.5).sub(size.scale(0.5)), size);

        // Set the menu title
        menuPanel.Name("Wendigo Main Menu");

        // Adds "Play Game" entry and handles user input
        menuPanel.EntryBegin("Play Game");
        if (menuPanel.EntryButton("Press to Play")) {
            this.state = MenuState.Play; // Sets the state to Play when pressed
        }
        menuPanel.EntryEnd();

        // Adds "Quit" entry and handles user input
        menuPanel.EntryBegin("Quit");
        if (menuPanel.EntryButton("Press to Quit")) {
            this.state = MenuState.Quit; // Sets the state to Quit when pressed
        }
        menuPanel.EntryEnd();

        // Displays the current high score
        menuPanel.EntryBegin("High Score: " + Game.currentMap.highScore);
        menuPanel.EntryEnd();

        // Displays "Controls" section
        menuPanel.EntryBegin("Controls");
        menuPanel.EntryEnd();

        // Lists the controls available in the game
        menuPanel.ListBegin("menuControls", new Vector2(), new Vector2(1.0, 1.0));
            menuPanel.Button("Movement: W A S D", new Vector2(), new Vector2(1.0, 0.0)); // Button for movement controls
            menuPanel.LayoutVertBAdded(0);

            menuPanel.Button("Melee: Q", new Vector2(), new Vector2(1.0, 0.0)); // Button for melee attack
            menuPanel.LayoutVertBAdded(0);

            menuPanel.Button("Shoot: RMB", new Vector2(), new Vector2(1.0, 0.0)); // Button for shoot action
            menuPanel.LayoutVertBAdded(0);

            menuPanel.Button("Dash: Space", new Vector2(), new Vector2(1.0, 0.0)); // Button for dash action
            menuPanel.LayoutVertBAdded(0);

            menuPanel.Button("Editor: E", new Vector2(), new Vector2(1.0, 0.0)); // Button for editor
            menuPanel.LayoutVertBAdded(0);
        menuPanel.ListEnd();

        // Ends the menu drawing
        menuPanel.End();
    }
}