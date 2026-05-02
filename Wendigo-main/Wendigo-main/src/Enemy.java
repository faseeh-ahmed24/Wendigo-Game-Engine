import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;

class EnemyManager {
    Tile crackingGFXTile; // Graphic tile used to show cracking animation on spawn points

    ArrayList<Tile> allSpawnTiles = new ArrayList<>(); // List of all potential spawn tiles on the map
    ArrayList<Spawn> openedSpawns = new ArrayList<>(); // List of active spawn points

    // Nested class representing an active spawn point
    class Spawn {
        public final double spawnSpawnTime = 5; // Time before spawn point finishes "charging"
        protected double openedTimestamp; // Timestamp when the spawn was activated
        protected Tile tile; // Tile associated with this spawn point

        public double nextMobSpawn = 0; // Timestamp for the next mob spawn

        Spawn(Tile t) {
            this.openedTimestamp = Game.now(); // Record the time the spawn was opened
            this.tile = t;
            this.nextMobSpawn = Game.now() + this.spawnSpawnTime + Math.random() * 10.0; // Randomize first spawn time
        }

        // Update the next spawn timestamp when a mob is spawned
        public void Spawned() {
            this.nextMobSpawn = Game.now() + 2.0 + Math.random() * 10.0;
        }
    }

    private double nextSpawn = 0; // Timestamp for when a new spawn point can be opened

    public EnemyManager() {
        crackingGFXTile = Game.gfxManager.GetGFX("gfx_crack"); // Load cracking animation graphic
        crackingGFXTile.animationControl = true; // Enable animation for the cracking graphic

        allSpawnTiles = Game.currentMap.GetMapTilesByTag("spawn", Game.currentMap.GetGroundLayer()); // Retrieve spawn tiles by tag

        nextSpawn = Game.now() + Math.random() * 5.0; // Randomize initial spawn opening time
    }
    
    // Update method for managing spawn activation and spawning enemies
    public void Update(double deltaTime) {
        if (Game.now() >= this.nextSpawn) { // Check if it's time to open a new spawn
            if (this.allSpawnTiles.size() > 0) {
                int randomIndex = (int)(Math.random() * this.allSpawnTiles.size()); // Select a random spawn tile
                this.openedSpawns.add(new Spawn(this.allSpawnTiles.remove(randomIndex))); // Move it to active spawns
            }
            nextSpawn = 30.0 + Game.now() + Math.random() * 25.0; // Randomize the next spawn activation time
        }
    }

    // Draw method to render cracking animation and handle mob spawning
    public void Draw(Graphics2D g) {
        for (Spawn s : this.openedSpawns) {
            Tile t = s.tile; // Get the tile for this spawn

            // Convert tile coordinates to world space for rendering
            Vector2 tilePosition = Game.currentMap.LocalToWorldVectorPositional(new Vector2(t.x, t.y));
            Vector2 tileSize = Game.currentMap.LocalToWorldVectorScalar(new Vector2(t.w, t.h)).sub(new Vector2(1));
            
            // Prepare cracking animation graphic
            GFX crack = new GFX(crackingGFXTile.Clone(), tilePosition, false);
            crack.position = tilePosition;
            crack.size = tileSize;

            // Calculate how far the cracking animation should progress
            double deltaSpawn = Game.now() - s.openedTimestamp;
            if (deltaSpawn <= s.spawnSpawnTime) {
                crack.tile.animCurrentFrame = (int)((deltaSpawn / s.spawnSpawnTime) * 3); // Update animation frame
            } else {
                crack.tile.animCurrentFrame = 3; // Animation fully played

                // Spawn mobs if it's time
                if (Game.now() > s.nextMobSpawn) {
                    Enemy enemyToSpawn;

                    // Randomly select which type of enemy to spawn
                    double random = Math.random() * 100;
                    if (random > 90) {
                        enemyToSpawn = new Rat();
                    } else if (random > 80) {
                        enemyToSpawn = new Enemy(HumanoidType.OGRE);
                    } else if (random > 70) {
                        enemyToSpawn = new Bomber();
                    } else {
                        enemyToSpawn = new HAR();
                    }

                    // Set enemy spawn position
                    enemyToSpawn.position = tilePosition.add(tileSize.scale(0.5)).sub(new Vector2(-20, -50));

                    Game.humanoids.add(enemyToSpawn); // Add enemy to the game's humanoid list

                    s.Spawned(); // Update spawn point for the next spawn
                }
            }
            
            Game.currentMap.RenderResponsibly(crack); // Render the cracking animation
        }
    }
}

public class Enemy extends Humanoid {
    private double lookAtVectorAssignedTime = 0; // Timestamp when lookAtVector was last set
    private double lookAtGoToTimeout = 6; // Timeout for how long to keep the current lookAtVector
    private double roamingStoodStillTill = 0; // Timestamp for when roaming stops standing still
    private Vector2 roamingStartPosition = null; // Starting position for roaming behavior
    private double eyeSight = 1920.0; // Distance the enemy can "see"
    public boolean canShoot = false; // Whether the enemy can shoot projectiles

    public Enemy(HumanoidType type) {
        super(type.name().toLowerCase(), 100, 100); // Call base class constructor with type name and default size
        
        this.type = type;
        this.collisionLayers.add("enemy"); // Add the enemy to collision layers
        this.state = State.ROAMING; // Default state is roaming
        this.roamingStartPosition = this.position.scale(1.0); // Save the initial position for roaming behavior

        /* Default attributes based on type */
        switch (type) {
            case OGRE: {
                this.SetMaxHealth(200);
                this.maxBulletsPerSecond = 0.3; // Shooting frequency
                this.movementSpeed = 30; // Movement speed
                this.meleeRange = 40; // Range for melee attacks
                this.canShoot = true; // Ogre can shoot
                this.eyeSight = 600; // Shorter vision range
                this.damageMultiplier = 0.8; // Damage scaling factor
            } break;
            case RAT: {
                this.SetMaxHealth(150);
                this.meleeRange = 50;
                this.movementSpeed = 40;
                this.eyeSight = 600;
                this.damageMultiplier = 0.8;
            } break;
            case HAR: {
                this.SetMaxHealth(80);
                this.meleeRange = 40;
                this.movementSpeed = 60;
                this.eyeSight = 900; // HAR has better vision
                this.damageMultiplier = 0.6;
            } break;
            case RAT_CHILD: {
                this.SetMaxHealth(50);
                this.meleeRange = 30;
                this.movementSpeed = 110; // RAT_CHILD moves the fastest
                this.eyeSight = 400; // Limited vision range
                this.damageMultiplier = 0.5;
            } break;
            default: {
            } break;
        }

        LoadAnimations(); // Load animations specific to this enemy
    }
    
    public void Update(double deltaTime) {          
        boolean inRange = Game.player.position.distance(this.position) < this.eyeSight; // Check if the player is within eyesight range
        /* Only perform raycast if the player is in range to save performance. */
        Physics.RaycastResult raycast = inRange ? Game.physics.RayCast(this.position, Game.player.position.sub(this.position), new String[] {"enemy"}) : null;
        boolean raycastObstructed = !(raycast == null || raycast.hit == Game.player); // Check if the player's line of sight is obstructed
    
        if (this.state == State.ROAMING) {
            // Switch to chasing state if the player is in range and visible
            if (inRange && !raycastObstructed) {
                this.state = State.CHASING;
            }
    
            // Generate a new roaming target if the current one is reached or timed out
            Vector2 newLookAtPoint = this.roamingStartPosition.add(new Vector2(Math.random() * 700, Math.random() * 700));
    
            if (this.position.distance(this.lookAtPoint) < 5 || (Game.now() - this.lookAtVectorAssignedTime) >= this.lookAtGoToTimeout) {
                this.lookAtPoint = newLookAtPoint;
                this.roamingStoodStillTill = Game.now() + Math.random() * 2.5; // Random wait time before moving again
                this.lookAtVectorAssignedTime = Game.now();
            }
    
            // Move towards the roaming target if the wait time has elapsed
            if (Game.now() > roamingStoodStillTill) {
                Vector2 movementVector = this.lookAtPoint.sub(this.position);
                this.velocity = this.velocity.add(movementVector.normalize().scale(this.movementSpeed * 0.7)); // Move at reduced speed
            }
        } else if (this.state == State.CHASING) {
            // Return to roaming if the player is out of range or obstructed
            if (!inRange || raycastObstructed) {
                this.roamingStartPosition = this.position.scale(1.0);
                this.lookAtVectorAssignedTime = Game.now() + 5.0; // Delay before resuming roaming
                this.state = State.ROAMING;
            }
    
            this.lookAtPoint = Game.player.position; // Set target to the player's position
    
            // Add noise to the movement for unpredictable behavior
            double noiseX = Game.ng.smoothNoise(Game.now(), this.randomSeed, 0.0);
            double noiseY = Game.ng.smoothNoise(Game.now(), this.randomSeed, 100.0);
            Vector2 noiseVector = new Vector2(noiseX, noiseY).scale(100);
            Vector2 lookAt = Game.player.position.add(noiseVector);
    
            // Move towards the noisy target
            Vector2 movementVector = lookAt.sub(this.position);
            this.velocity = this.velocity.add(movementVector.normalize().scale(this.movementSpeed));
    
            // Perform melee attack if within melee range
            if (lookAt.distance(this.position) < this.meleeRange) {
                this.MeleeAttack();
            }
    
            // Shoot bullets at the player if the enemy can shoot
            if (this.canShoot) {
                ShootBullet(lookAt.sub(this.position));
            }
        }
    
        HumanoidUpdate(deltaTime); // Call base class update logic
    }
    
    public void Draw(Graphics2D g) {
        HumanoidDraw(g); // Call base class draw logic
    }
}

class HAR extends Enemy {
    private double runAwayStartTime = 0; // Timestamp for when HAR starts running away

    public HAR() {
        super(HumanoidType.HAR); // Initialize HAR with the specific humanoid type
    }

    @Override
    public void Update(double deltaTime) {
        super.Update(deltaTime); // Call the base class update logic

        if (this.state == State.CHASING) {
            // Switch to running away if too close to the player or after a recent melee attack
            if (Game.player.position.distance(this.position) < 30 || (Game.now() - lastMelee) < 0.1) {
                this.lookAtPoint = this.position.add(new Vector2(Math.random() * 1000 - 500, Math.random() * 1000 - 500)); // Randomize escape direction
                this.state = State.HAR_RUNNING_AWAY; // Change state to running away
                this.MeleeAttack(); // Perform melee attack
                this.runAwayStartTime = Game.now(); // Record the time HAR starts running away
            }
        }

        if (this.state == State.HAR_RUNNING_AWAY) {
            // Add random noise to the movement for an unpredictable escape path
            double noiseX = Game.ng.smoothNoise(Game.now(), this.randomSeed, 0.0);
            double noiseY = Game.ng.smoothNoise(Game.now(), this.randomSeed, 100.0);
            Vector2 noiseVector = new Vector2(noiseX, noiseY).scale(100);
            Vector2 lookAt = this.lookAtPoint.add(noiseVector);

            // Move faster in the escape direction
            Vector2 movementVector = lookAt.sub(this.position);
            this.velocity = this.velocity.add(movementVector.normalize().scale(this.movementSpeed * 1.5));

            // Return to roaming state after running away for 5 seconds
            if ((Game.now() - runAwayStartTime) > 5) {
                this.state = State.ROAMING;
            }
        }
    }

    @Override
    public void Draw(Graphics2D g) {
        super.Draw(g); // Call the base class draw logic
    }
}
class Bomber extends Enemy {
    private double explodingStartTime = 0; // Timestamp for when the explosion sequence starts
    private final double explosionRange = 90; // Range within which the Bomber will start exploding
    private final double timeToExplode = 1.0; // Time delay before the Bomber explodes

    public Bomber() {
        super(HumanoidType.BOMBER); // Initialize the Bomber as a specific humanoid type
    }

    @Override
    public void Update(double deltaTime) {
        super.Update(deltaTime); // Call base class update logic

        if (this.state == State.CHASING) {
            // Switch to exploding state if the player is within explosion range
            if (this.position.distance(Game.player.position) <= explosionRange) {
                this.state = State.BOMBER_EXPLODING;
                this.explodingStartTime = Game.now(); // Record when the explosion sequence starts
            }
        }

        if (this.state == State.BOMBER_EXPLODING) {
            this.animState = AnimationState.EXPLODING; // Change animation to exploding state

            // Return to roaming state if the player moves far away from the explosion area
            if (this.position.distance(Game.player.position) > explosionRange * 3.0) {
                this.state = State.ROAMING;
            }

            // Check if it's time to explode
            if ((Game.now() - explodingStartTime) > this.timeToExplode) {
                // Play explosion graphics at Bomber's position
                Game.gfxManager.PlayGFXOnce("gfx_explode", this.position.add(this.size.scale(0.5)), 1.5);

                // Apply explosion effects to nearby humanoids
                for (Humanoid h : Game.humanoids) {
                    if (h.type == HumanoidType.HUMAN) { // Only affect humans
                        Vector2 direction = h.position.sub(this.position); // Direction from Bomber to humanoid
                        h.velocity = h.velocity.add(direction.normalize().scale(1600)); // Push the humanoid away
                        h.health -= 100; // Reduce health due to explosion
                    }
                }

                this.health = 0; // Kill the Bomber after exploding
            }
        }
    }
}

class Rat extends Enemy {
    private final double birthCooldown = 20; // Time interval between births
    private double lastBirthTime = 0; // Timestamp of the last birth
    protected ArrayList<Enemy> children = new ArrayList<>(); // List of Rat's spawned children
    private int childrenThisBirth = 0; // Number of children born in the current birthing session
    private final int maxChildren = 6; // Maximum children per birthing session

    public Rat() {
        super(HumanoidType.RAT); // Initialize Rat as a specific humanoid type
    }

    private void Birth() {
        this.lastBirthTime = Game.now(); // Record the birth start time
        this.state = State.RAT_BIRTHING; // Change state to birthing
        this.childrenThisBirth = 0; // Reset children count for the current birth
    }

    @Override
    public void Update(double deltaTime) {
        super.Update(deltaTime); // Call base class update logic

        // Iterate through children to update their behavior or remove them if necessary
        Iterator<Enemy> iterator = this.children.iterator();
        while (iterator.hasNext()) {
            Enemy child = iterator.next();
            
            // Remove child if dead
            if (child.state == State.DEAD) {
                iterator.remove();
            } 
            // Handle roaming children
            else if (child.state == State.ROAMING) {
                if (this.state == State.ROAMING) {
                    Vector2 center = this.position.add(this.size.scale(0.5)); // Center point for children

                    child.lookAtPoint = center; // Direct child to center

                    // Eliminate child if too close to the center
                    if (child.position.distance(center) < 40) {
                        child.health = Integer.MIN_VALUE;
                    }
                } else {
                    // Follow the parent's target
                    child.lookAtPoint = this.lookAtPoint;
                }
            }

            // Remove child if it is too far away or has lived too long
            if ((Game.now() - child.spawnTime > 15) || (child.position.distance(this.position) > 1920)) {
                child.health = Integer.MIN_VALUE;
            }
        }

        // Handle Rat behavior based on its state
        if (this.state == State.CHASING) {
            // Trigger birth if cooldown period has passed
            if (Game.now() - this.lastBirthTime >= this.birthCooldown) {
                Birth();
            }
        } else if (this.state == State.RAT_BIRTHING) {
            this.animState = AnimationState.BIRTHING; // Set birthing animation state

            // Return to chasing state after 3 seconds
            if ((Game.now() - this.lastBirthTime) > 3) {
                this.state = State.CHASING;
            }

            // Spawn children periodically during the birthing state
            if (Game.now() - this.lastBirthTime > 1.0 && Math.random() < 0.1 && childrenThisBirth < maxChildren) {
                this.childrenThisBirth++; // Increment children count

                Enemy child = new Enemy(HumanoidType.RAT_CHILD); // Create a new child
                child.position = this.position.add(new Vector2(Math.random() * this.size.x, Math.random() * this.size.y)); // Randomize child position

                this.children.add(child); // Add child to the list
                Game.humanoids.add(child); // Add child to the game
            }
        }
    }
}
