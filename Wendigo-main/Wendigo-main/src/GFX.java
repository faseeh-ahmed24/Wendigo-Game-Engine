import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

// Manages and handles the loading, drawing, and playback of graphical effects (GFX) in the game.
class GFXManager {
    protected HashMap<String, Tile> loadedGFXS = null;
    private ArrayList<GFX> activeGFXS = new ArrayList<>();

    public GFXManager() {
        String[] essentialGfxsNames = {"smoke_cloud", "gfx_slash", "gfx_eye_of_rah", "real_rah", "gfx_explode", "gfx_star_spin", "gfx_crack"};
        if (this.loadedGFXS == null) {
            this.loadedGFXS = new HashMap<>();
            for (String gfxName : essentialGfxsNames) {
                Tile gfx = Game.currentMap.GetSheetTileByTag(gfxName);
                if (gfx == null) {
                    new Message("Couldn't load GFX: " + gfxName);
                } else {
                    System.out.println("[LOG]: Loaded gfx: " + gfxName);
                    this.loadedGFXS.put(gfxName, gfx);
                }
            }
        }
    }

    // Draw all active GFX objects
    public void Draw(Graphics2D g) {
        Iterator<GFX> iterator = this.activeGFXS.iterator();
        while (iterator.hasNext()) {
            GFX gfx = iterator.next();
            if (gfx.tile.animPlayedCount >= 1) {
                iterator.remove();
                continue;
            }

            if (gfx.tile.tags.contains("real_rah")) {
                System.out.println("real rah!");
                gfx.Draw(g);
            } else {
                Game.currentMap.RenderResponsibly(gfx);
            }
        }
    }

    // Retrieve a specific GFX by name
    public Tile GetGFX(String gfx) {
        return this.loadedGFXS != null ? this.loadedGFXS.get(gfx) : null;
    }

    // Get the size of a specific GFX
    public Vector2 GetGFXSize(Tile gfxTile) {
        if (gfxTile == null || Game.currentMap == null) return new Vector2();
        return Game.currentMap.LocalToWorldVectorScalar(new Vector2(gfxTile.w, gfxTile.h));
    }

    public Vector2 GetGFXSize(String gfxName) {
        Tile gfx = GetGFX(gfxName);
        return GetGFXSize(gfx);
    }

    // Play a GFX once at a specified position with various parameters
    public void PlayGFXOnce(String gfxName, Vector2 position, double speed, boolean flip, GameObject attachTo) {
        Tile gfx = GetGFX(gfxName);
        Vector2 gfxSize = GetGFXSize(gfxName);

        if (gfx == null || gfxSize == null) {
            return;
        }

        gfx = gfx.Clone();
        gfx.animFPS *= speed;
        position = position.sub(gfxSize.scale(0.5));

        GFX fx = new GFX(gfx, position);
        fx.flipped = flip;
        fx.attachedTo = attachTo;

        activeGFXS.add(fx);
    }

    public void PlayGFXOnce(String gfxName, Vector2 position) {
        PlayGFXOnce(gfxName, position, 1.0, false, null);
    }

    public void PlayGFXOnce(String gfxName, Vector2 position, double speed) {
        PlayGFXOnce(gfxName, position, speed, false, null);
    }

    public void PlayGFXOnce(String gfxName, Vector2 position, double speed, boolean flipped) {
        PlayGFXOnce(gfxName, position, speed, flipped, null);
    }
}


// Represents a graphical effect (GFX) object that can be drawn in the game world.
public class GFX extends GameObject {
    protected Tile tile;
    public boolean flipped = false;
    public GameObject attachedTo = null;
    public Vector2 offsetPosition = new Vector2();

    public GFX(Tile gfxTile, Vector2 position, boolean reset) {
        super();
        this.tile = gfxTile;
        this.offsetPosition = position;
        this.size = Game.gfxManager.GetGFXSize(gfxTile);
        if (reset) {
            gfxTile.ResetAnimation();
        }
    }

    public GFX(Tile gfxTile, Vector2 position) {
        super();
        this.tile = gfxTile;
        this.offsetPosition = position;
        this.size = Game.gfxManager.GetGFXSize(gfxTile);
        gfxTile.ResetAnimation();
    }

    // Draw the GFX object, considering its position and any attachment.
    public void Draw(Graphics2D g) {
        if (Game.currentMap == null) return;

        Vector2 position = new Vector2(this.offsetPosition.x, this.offsetPosition.y);
        if (attachedTo != null) {
            position = position.add(attachedTo.position);
        }

        this.position = position;
        tile.Draw(g, position.x, position.y, this.size.x, this.size.y, flipped);
    }
}
