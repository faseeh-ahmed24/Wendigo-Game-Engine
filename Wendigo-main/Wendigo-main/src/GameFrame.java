/* Game.Java
* Author: Ibraheem Mustafa, Faseeh Ahmed
* Date Created: Dec 11th, 2024
* Date Last Edited: Dec 11th, 2024
* Description: Essentially the class that handles the window 
*/

import javax.swing.*;
import java.awt.*;

public class GameFrame extends JFrame {
    public static Game _Game;
    public GameFrame() {
        // Configuration
        final int init_width = 1600;
        final int init_height = 900;

        final int min_width = init_width / 2;
        final int min_height = init_height / 2;

        final boolean resizable = true;

        System.out.println("[LOG]: Creating window. Initial size: " + init_width + "x" + init_height
                            + ", minimum size: " + min_width + "x" + min_height + ", resizeable = " + resizable);
        
        _Game = new Game(this);

        this.add(_Game);
        this.setTitle("Wendigo");

        // Set properties
        this.setResizable(resizable);
        this.setUndecorated(false);
        this.setMinimumSize(new Dimension(min_width, min_height));
        this.setBackground(Color.BLACK);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // X button will stop program execution
        this.setSize(init_width, init_height);
        this.pack(); // makes components fit in window - don't need to set JFrame size, as it will adjust accordingly
        this.setVisible(true); // makes window visible to user
        this.setLocationRelativeTo(null); // set window in middle of screen
    }
}
