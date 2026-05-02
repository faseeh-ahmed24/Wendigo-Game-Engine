import java.awt.event.*;
import java.awt.*;

public class Wendigo {
    public static void main(String[] args) {
        GameFrame gf = new GameFrame();
        gf.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent ev) {
                Component c = (Component)ev.getSource();
                if (c.equals(gf)) {
                    Game.WINDOW_WIDTH = c.getWidth();
                    Game.WINDOW_HEIGHT = c.getHeight();
                }
            }
        });
    }
}
