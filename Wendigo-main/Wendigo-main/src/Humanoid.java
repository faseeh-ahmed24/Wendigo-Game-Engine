import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;

// Represents different animation states of a humanoid.
enum AnimationState {
    IDLE,           // Idle state
    WALK,           // Walking state
    BIRTHING,       // Birthing state
    EXPLODING,      // Exploding state
    DYING           // Dying state
};

// Represents different states a game object can be in.
enum State {
    PLAYER,         // Player state

    CHASING,        // Chasing state
    ROAMING,        // Roaming state
    
    RAT_BIRTHING,   // Rat birthing state

    BOMBER_EXPLODING, // Bomber exploding state

    HAR_RUNNING_AWAY, // Har running away state

    DYING,          // Dying state
    DEAD            // Dead state
}

// Represents the type of humanoid.
enum HumanoidType {
    HUMAN,          // Human type
    
    OGRE,           // Ogre type
    
    RAT,            // Rat type
    RAT_CHILD,      // Rat child type

    BOMBER,         // Bomber type

    HAR             // Har type
};

// Represents a bullet in the game.
class Bullet extends GameObject {
    public boolean destroyed = false; // Flag to check if the bullet is destroyed
    private Tile animatedSprite;      // Animated sprite for the bullet
    public double bulletSpeed = 400.0; // Speed of the bullet
    private Vector2 initialPosition;  // Initial position of the bullet
    private String shooter;           // The shooter of the bullet

    // Constructor for Bullet.
    public Bullet(Vector2 position, Vector2 velocity, String shooter) {
        Vector2 spriteSize = Game.currentMap.LocalToWorldVectorScalar(new Vector2(1,1));

        this.position = position.sub(spriteSize.scale(0.5));
        this.velocity = velocity;
        this.initialPosition = position.scale(1.0);
        this.shooter = shooter;

        this.animatedSprite = Game.currentMap.GetSheetTileByTag("bullet");

        Game.bm.bullets.add(this);
    }

    // Updates the bullet's position and checks for collisions.
    public void Update(double deltaTime) {
        Vector2 newPosition = this.position.add(this.velocity.normalize().scale(bulletSpeed).scale(deltaTime));
        Vector2 positionDifference = this.position.sub(newPosition);
        Vector2 direction = positionDifference.normalize().scale(positionDifference.magnitude() + 10.0);
        Vector2 bulletCenter = newPosition.add(this.size.scale(0.5));

        Physics.RaycastResult r = Game.physics.RayCast(newPosition, direction, new String[] {this.shooter});

        if (r != null) {
            Game.gfxManager.PlayGFXOnce("smoke_cloud", newPosition.sub(this.size.scale(0.5)));
            destroyed = true;

            for (Humanoid h : Game.humanoids) {
                if ((this.shooter == "player" && h.type != HumanoidType.HUMAN) || (this.shooter == "enemy" && h.type == HumanoidType.HUMAN)) {
                    Vector2 humanoidCenter = h.position.add(h.size.scale(0.5));
                    if (humanoidCenter.distance(bulletCenter) < 100) {
                        Vector2 humanDirection = humanoidCenter.sub(bulletCenter).normalize();

                        h.health -= 50;
                        h.velocity = h.velocity.add(humanDirection.scale(1500));
                    }
                }
            }
        }

        if (this.position.distance(this.initialPosition) > 2000) {
            destroyed = true;
        }

        this.position = newPosition;
    }

    // Draws the bullet on the screen.
    public void Draw(Graphics2D g) {
        Vector2 spriteSize = Game.currentMap.LocalToWorldVectorScalar(new Vector2(1,1));
        this.animatedSprite.Draw(g, this.position.x, this.position.y, spriteSize.x, spriteSize.y);
    }
}

// Manages the bullets in the game.
class BulletManager {
    ArrayList<Bullet> bullets = new ArrayList<>(); // List of active bullets
    
    // Updates all active bullets and removes destroyed ones.
    public void Update(double deltaTime) {
        ArrayList<Integer> deleteIndicies = new ArrayList<>();
        for (int i = 0; i < this.bullets.size(); i++) {
            Bullet b = this.bullets.get(i);

            b.Update(deltaTime);

            if (b.destroyed) {
                deleteIndicies.add(i); // Mark bullets for removal when destroyed
            }
        }

        // Remove destroyed bullets from the list
        for (int i : deleteIndicies) {
            this.bullets.remove(i);
        }
    }

    // Draws all active bullets to the screen.
    public void Draw(Graphics2D g) {
        for (Bullet b : this.bullets) {
            Game.currentMap.RenderResponsibly(b);
        }
    }
}
// Represents a humanoid character in the game, such as a player or enemy.
public class Humanoid extends GameObject {
    protected String name; // Name of the humanoid
    protected HumanoidType type; // Type of the humanoid (e.g., human, rat)
    
    public int health; // Current health of the humanoid
    protected int maxHealth; // Maximum health of the humanoid
    
    protected AnimationState animState = AnimationState.IDLE; // Current animation state
    protected HashMap<AnimationState, Tile> animations = new HashMap<>(); // Animation states mapped to tiles
    
    public Vector2 lookAtPoint = new Vector2(); // Point the humanoid is looking at

    public double movementSpeed = 100; // Movement speed of the humanoid
    public State state; // Current state (e.g., player, chasing)
    
    public double dashCooldown = 0.6; // Dash cooldown duration
    protected double lastTimeDashed = 0; // Time when the last dash occurred

    public double meleeRange = 70.0; // Melee attack range

    private double dyingAnimationCurrentScale = 1.0; // Current scale for dying animation
    
    private double lastBulletShot = 0; // Time of the last bullet shot
    public double maxBulletsPerSecond = 2; // Maximum bullets per second
    
    public double damageMultiplier = 1.0; // Multiplier for damage dealt by this humanoid

    protected double lastMelee = 0; // Time of the last melee attack
    public double maxMeleesPerSecond = 6; // Maximum melee attacks per second

    public int bulletFullMagazine = 5; // Full magazine capacity for bullets
    public int bulletMagazine = bulletFullMagazine; // Current bullet count in the magazine
    public Double reloadTill = null; // Time until the next reload is complete
    public double reloadTime = 3; // Time required to reload

    protected double spawnTime = 0; // Time when the humanoid spawned
    protected int randomSeed; // Random seed for unique generation

    private boolean sizeFix; // Whether size adjustment is required for this humanoid

    // Constructor for humanoid initialization.
    public Humanoid(String name, int health, int maxHealth) {
        this.health = health;
        this.name = name;
        this.sizeFix = this.name == "dino"; // Special case for "dino" humanoid
        this.maxHealth = maxHealth;
        this.collisionLayers.add("humanoid");
        this.type = HumanoidType.HUMAN;
        this.state = State.PLAYER;
        this.randomSeed = (int)(Math.random() * 1_000_000); // Random seed for uniqueness
        this.spawnTime = Game.now();
    }

    // Loads animations for the humanoid based on its name.
    public void LoadAnimations() {
        LoadAnimations(this.name);
    }

    // Loads animations for a given humanoid name prefix.
    public void LoadAnimations(String prefix) {
        if (Game.currentMap == null) return;

        // Load animations for each possible state
        for (AnimationState state : AnimationState.values()) {
            String stateString = state.name().toLowerCase();
            String tag = prefix.toLowerCase() + "_" + stateString;

            Tile t = Game.currentMap.GetSheetTileByTag(tag);
            if (t == null) {
                System.out.println("Missing animation: `" + tag + "` for: `" + this.name + "`");
            } else {
                System.out.println("[LOG] Loaded animation: " + tag);
                this.animations.put(state, t);
            }
        }
    }

    // Draws the humanoid with the appropriate animation based on its state.
    protected void HumanoidDraw(Graphics2D g) {
        Tile currentAnimatedTile;

        // Switch to select the appropriate animation state
        switch (this.animState) {
            case WALK: {
                currentAnimatedTile = this.animations.get(AnimationState.WALK);
            } break;
            case BIRTHING: {
                currentAnimatedTile = this.animations.get(AnimationState.BIRTHING);
            } break;
            case EXPLODING: {
                currentAnimatedTile = this.animations.get(AnimationState.EXPLODING);
            } break;
            case DYING: {
                if (dyingAnimationCurrentScale <= 0) {
                    this.state = State.DEAD;
                } else {
                    dyingAnimationCurrentScale -= 6.0 * Game.deltaTime; // Scale down the dying animation
                }
            }
            default: {
                currentAnimatedTile = this.animations.get(AnimationState.IDLE);
            } break;
        }

        Vector2 size = new Vector2(0, 0);

        Vector2 lookAtDelta = this.lookAtPoint.sub(this.position);

        if (currentAnimatedTile != null) {
            size = Game.currentMap.LocalToWorldVectorScalar(new Vector2(currentAnimatedTile.w, currentAnimatedTile.h)).scale(dyingAnimationCurrentScale);

            double transparency = 1.0;
            double deltaDash = Game.now() - this.lastTimeDashed;

            if (deltaDash < this.dashCooldown) {
                transparency = (Math.sin(Game.now()*24)/2.0 + 0.5)*0.3 + 0.5;
            }

            // Draw the current animation tile with possible flipping and transparency
            currentAnimatedTile.Draw(
                g,
                this.position.x,
                this.position.y - (sizeFix ? size.y * 0.25 : 0),
                size.x,
                size.y,
                lookAtDelta.x < 0 ? true : false,
                transparency
            );
        }

        this.size = size;

        if (sizeFix) {
            size = size.mult(new Vector2(1.0, 0.75)); // Apply size fix if necessary
        }
    }

    // Draws the humanoid.
    public void Draw(Graphics2D g) {
        HumanoidDraw(g);
    }

    // Updates the humanoid's state based on current conditions.
    public void HumanoidUpdate(double deltaTime) {
        if (this.reloadTill != null && Game.now() > this.reloadTill) {
            this.bulletMagazine = bulletFullMagazine; // Reload bullets when the reload time has passed
            this.reloadTill = null;
        }

        if (this.health <= 0) {
            this.state = State.DYING;
            this.animState = AnimationState.DYING;
        } else {
            this.animState = this.velocity.magnitude() > 1 ? AnimationState.WALK : AnimationState.IDLE;
        }
    }

    // Shoots a bullet in a specified direction.
    public void ShootBullet(Vector2 direction) {
        if (bulletMagazine > 0) {
            if ((Game.now() - this.lastBulletShot) > 1.0/this.maxBulletsPerSecond) {
                this.bulletMagazine -= 1;
                Game.bm.bullets.add(new Bullet(this.position.add(this.size.scale(0.5)), direction, this.type == HumanoidType.HUMAN ? "player" : "enemy"));
                this.lastBulletShot = Game.now();
            }
        } else if (this.reloadTill == null) {
            this.reloadTill = Game.now() + this.reloadTime; // Start reloading if out of bullets
        }
    }

    // Performs a melee attack.
    public void MeleeAttack() {
        if (!((Game.now() - this.lastMelee) > 1.0/this.maxMeleesPerSecond)) {
            return;
        }
        this.lastMelee = Game.now();

        Vector2 lookAtDelta = this.lookAtPoint.sub(this.position);
        boolean flipped = lookAtDelta.x < 0 ? true : false;

        Game.gfxManager.PlayGFXOnce("gfx_slash", this.size.scale(0.5), 2.0, flipped, this);

        // Check for humanoids to hit with the melee attack
        for (Humanoid h : Game.humanoids) {
            if (h == this) continue;

            boolean checkDamage = false;

            if (this.type == HumanoidType.HUMAN && h.type != HumanoidType.HUMAN) {
                checkDamage = true;
            } 
            if (this.type != HumanoidType.HUMAN && h.type == HumanoidType.HUMAN) {
                checkDamage = true;
            }

            if (checkDamage) {
                Vector2 otherCentre = h.position.add(h.size.scale(0.5));

                Vector2 thisHitPos = this.position.add(new Vector2(0, this.size.y / 2.0));
                double size = this.size.x / 2.0;

                thisHitPos.x += flipped ? (-size) : (size);

                if (otherCentre.distance(thisHitPos) < this.meleeRange) {
                    Vector2 direction = h.position.sub(this.position).normalize();

                    // Apply knockback and damage
                    h.velocity = h.velocity.add(direction.scale(1000));
                    
                    h.health -= 40 * this.damageMultiplier;
                }
            }
        }
    }

    // Updates the humanoid's movement and abilities.
    public void Update(double deltaTime) {
        this.HumanoidUpdate(deltaTime);

        Vector2 movementVector = new Vector2();
        if (Game.IsKeyDown(KeyEvent.VK_W)) {
            movementVector.y = -1;
        } else if (Game.IsKeyDown(KeyEvent.VK_S)) {
            movementVector.y = 1;
        }
        
        if (Game.IsKeyDown(KeyEvent.VK_A)) {
            movementVector.x = -1;
        } else if (Game.IsKeyDown(KeyEvent.VK_D)) {
            movementVector.x = 1;
        }

        this.velocity = this.velocity.add(movementVector.scale(this.movementSpeed)); // Update velocity based on movement

        this.lookAtPoint = Game.worldMousePos;

        // Dash functionality
        if (Game.IsKeyPressed(KeyEvent.VK_SPACE)) {
            double deltaDashed = Game.now() - lastTimeDashed;

            if (deltaDashed > dashCooldown && this.velocity.magnitude() > 0) {
                Game.gfxManager.PlayGFXOnce("smoke_cloud", this.position.add(this.size.scale(0.5)), 1.0);

                this.velocity = this.velocity.add(this.velocity.normalize().scale(this.movementSpeed * 36.0)); // Dash speed boost
                this.lastTimeDashed = Game.now();
            }
        }

        if (Game.IsKeyPressed(KeyEvent.VK_Q)) {
            MeleeAttack();
        }

        if (Game.IsMouseDown(MouseEvent.BUTTON1)) {
            ShootBullet(Game.worldMousePos.sub(this.position)); // Shoot bullet towards mouse position
        }

        if (Game.IsKeyPressed(KeyEvent.VK_R)) {
            Game.gfxManager.PlayGFXOnce("real_rah", this.position.add(this.size.scale(0.5)).sub(new Vector2(0, this.size.y)), 1.5); // Special effect
        }
    }

    // Updates the maximum health of the humanoid.
    public void SetMaxHealth(int newMaxHealth) {
        double healthAsPercentage = this.health / (double)this.maxHealth;

        this.maxHealth = newMaxHealth;
        this.health = (int)(healthAsPercentage * maxHealth);
    }

    // Checks if the humanoid can dash based on cooldown.
    public boolean CanDash() {
        double deltaDashed = Game.now() - lastTimeDashed;
        if (deltaDashed > dashCooldown) {
            return true;
        }
        return false;
    }
}
