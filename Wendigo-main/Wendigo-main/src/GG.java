/* GG.Java
* Author: Ibraheem Mustafa, Faseeh Ahmed
* Date Created: Dec 11th, 2024
* Date Last Edited: Dec 11th, 2024
* Description: GG (Good graphics), implement graphic methods but 
* using generic number types instead of ints.
*/

import java.awt.*;

/*
 * This class is pretty self explanatory.
 */

public class GG {
    public static Graphics2D g;
    public static Color COLOR_OPAQUE = new Color(0, 0, 0, 0);

    public static <N extends Number> void drawOval(N x, N y, N w, N h) {
        g.drawOval(x.intValue(), y.intValue(), w.intValue(), h.intValue());
    }

    public static <N extends Number> void fillOval(N x, N y, N w, N h) {
        g.fillOval(x.intValue(), y.intValue(), w.intValue(), h.intValue());
    }

    public static <N extends Number> void drawString(String text, N x, N y) {
        g.drawString(text, x.intValue(), y.intValue());
    }

    public static <N extends Number> void fillRect(N x, N y, N w, N h) {
        g.fillRect(x.intValue(), y.intValue(), w.intValue(), h.intValue());
    }

    public static <N extends Number> void drawRect(N x, N y, N w, N h) {
        g.drawRect(x.intValue(), y.intValue(), w.intValue(), h.intValue());
    }

    public static <N extends Number> void drawLine(N x1, N y1, N x2, N y2) {
        g.drawLine(x1.intValue(), y1.intValue(), x2.intValue(), y2.intValue());
    }

    public static <N extends Number> void fillRoundRect(N x1, N y1, N x2, N y2, N arcW, N arcH) {
        g.fillRoundRect(x1.intValue(), y1.intValue(), x2.intValue(), y2.intValue(), arcW.intValue(), arcH.intValue());
    }
    
    public static void drawLine(Vector2 a, Vector2 b) {
        GG.drawLine(a.x, a.y, b.x, b.y);
    }

    public static void fillRect(Rectangle rect) {
        GG.fillRect(rect.x, rect.y, rect.width, rect.height);
    }
    
    public static void drawRect(Rectangle rect) {
        GG.drawRect(rect.x, rect.y, rect.width, rect.height);
    }

    public static void fillRect(Vector2 position, Vector2 size) {
        GG.fillRect(position.x, position.y, size.x, size.y);
    }

    public static void drawRect(Vector2 position, Vector2 size) {
        GG.drawRect(position.x, position.y, size.x, size.y);
    }

    public static void drawOval(Vector2 position, Vector2 size) {
        GG.drawOval(position.x, position.y, size.x, size.y);
    }

    public static void fillOval(Vector2 position, Vector2 size) {
        GG.fillOval(position.x, position.y, size.x, size.y);
    }

    public static void fillRoundRect(Vector2 position, Vector2 size, Vector2 arcSize) {
        GG.fillRoundRect(position.x, position.y, size.x, size.y, arcSize.x, arcSize.y);
    }
    
    // TODO: Implement more functions
}
