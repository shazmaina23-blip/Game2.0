
package application; // Places the class in the same package as PixelDash.

import java.awt.Color; // Provides colours for the game graphics.
import java.awt.Dimension; // Defines the game panel's dimensions.
import java.awt.Font; // Provides fonts for text.
import java.awt.FontMetrics; // Measures text for centring.
import java.awt.Graphics; // Provides drawing operations.
import java.awt.Graphics2D; // Provides advanced drawing operations.
import java.awt.Polygon; // Allows triangular obstacles to be drawn.
import java.awt.event.ActionEvent; // Represents Swing action events.
import java.awt.event.KeyEvent; // Provides keyboard key codes.
import java.awt.event.KeyAdapter; // Handles keyboard release events.
import java.util.ArrayList; // Stores obstacles, coins and power-ups.
import java.util.Iterator; // Allows objects to be safely removed from lists.
import java.util.Random; // Generates random obstacle patterns.
import javax.swing.AbstractAction; // Connects keyboard shortcuts to actions.
import javax.swing.JPanel; // Provides the surface on which the game is drawn.
import javax.swing.KeyStroke; // Represents a keyboard shortcut.
import javax.swing.Timer; // Updates the game repeatedly.

/**
 * Controls all gameplay, graphics, lives, levels and collisions.
 * Level 1: Green Hills.
 * Level 2: Rocky Canyon.
 * Level 3: Enchanted Forest.
 * Level 4: Frozen Peaks.
 * Level 5: Volcanic Fortress.
 */
public class PixelDash extends JPanel { // Defines the main game panel.

    private static final long serialVersionUID = 1L; // Identifies this serializable Swing class.

    private static final int WIDTH = 1000; // Sets the game width in pixels.
    private static final int HEIGHT = 600; // Sets the game height in pixels.
    private static final int GROUND = 475; // Sets the vertical position of the ground.
    private static final int LEVEL_LENGTH = 2000; // Sets the distance needed to finish a level.

    private final Timer timer; // Updates gameplay approximately 60 times per second.
    private final Random random = new Random(); // Generates random game objects.

    private enum State { STORY, PLAYING, PAUSED, LEVEL_COMPLETE, GAME_OVER, VICTORY } // Defines all game screens.

    private State state = State.STORY; // Starts the game on the story screen.
    private int level = 1; // Stores the current level number.

    private int playerX = 130; // Stores the player's horizontal position.
    private int playerY = GROUND - 65; // Stores the player's vertical position.
    private final int playerWidth = 44; // Sets the player's width.
    private final int playerHeight = 65; // Sets the player's standing height.

    private double velocityY = 0; // Stores the player's vertical jumping speed.
    private boolean jumping = false; // Records whether the player is jumping.
    private boolean ducking = false; // Records whether the player is ducking.

    private int lives = 5; // Gives the player five lives at the start.
    private int coins = 0; // Stores the total number of collected coins.
    private int distance = 0; // Tracks progress through the current level.

    private int shieldTimer = 0; // Counts down the shield's remaining duration.
    private int speedTimer = 0; // Counts down the speed boost's remaining duration.
    private int invulnerabilityTimer = 0; // Prevents repeated damage immediately after a hit.

    private int obstacleSpawnTimer = 0; // Controls when the next obstacle appears.
    private int coinSpawnTimer = 0; // Controls when the next coins appear.
    private int powerSpawnTimer = 0; // Controls when the next power-up appears.
    private int baseSpeed = 6; // Controls the basic scrolling speed.

    private final ArrayList<Obstacle> obstacles = new ArrayList<>(); // Stores active obstacles.
    private final ArrayList<Coin> collectibleCoins = new ArrayList<>(); // Stores active coins.
    private final ArrayList<PowerUp> powerUps = new ArrayList<>(); // Stores active power-ups.

    public PixelDash() { // Creates and configures the game panel.

        setPreferredSize(new Dimension(WIDTH, HEIGHT)); // Sets the preferred window size.
        setBackground(new Color(135, 206, 235)); // Sets the initial sky colour.
        setFocusable(true); // Allows the panel to receive keyboard input.
        setupControls(); // Registers all keyboard controls.

        timer = new Timer(16, e -> updateGame()); // Schedules a game update every 16 milliseconds.
        timer.start(); // Starts the repeating game timer.

    } // Ends the constructor.

    // ============================================================
    // KEYBOARD CONTROLS
    // ============================================================

    private void bindKey(int keyCode, String name, Runnable action) { // Connects a key to an action.

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyCode, 0), name); // Registers the shortcut.
        getActionMap().put(name, new AbstractAction() { // Creates the action associated with the shortcut.

            private static final long serialVersionUID = 1L; // Identifies this action class.

            @Override
            public void actionPerformed(ActionEvent e) { // Runs when the key is pressed.
                action.run(); // Performs the requested game action.
            } // Ends the action method.

        }); // Ends the keyboard action.

    } // Ends the bindKey method.

    private void setupControls() { // Registers every game control.

        bindKey(KeyEvent.VK_SPACE, "jumpSpace", this::jump); // Makes SPACE jump.
        bindKey(KeyEvent.VK_UP, "jumpUp", this::jump); // Makes UP ARROW jump.

        bindKey(KeyEvent.VK_DOWN, "duckDown", () -> { // Makes DOWN ARROW duck.
            if (state == State.PLAYING && !jumping) ducking = true; // Ducks only during active ground movement.
        }); // Ends the DOWN ARROW action.

        bindKey(KeyEvent.VK_S, "duckS", () -> { // Makes S an alternative duck key.
            if (state == State.PLAYING && !jumping) ducking = true; // Ducks only while the player is on the ground.
        }); // Ends the S action.

        bindKey(KeyEvent.VK_P, "pause", () -> { // Registers the pause shortcut.
            if (state == State.PLAYING) state = State.PAUSED; // Pauses active gameplay.
            else if (state == State.PAUSED) state = State.PLAYING; // Resumes paused gameplay.
            repaint(); // Redraws the screen after changing the state.
        }); // Ends the pause action.

        bindKey(KeyEvent.VK_ENTER, "enter", () -> { // Registers the ENTER key.
            if (state == State.STORY) startLevel(1); // Starts the first level from the story screen.
            else if (state == State.LEVEL_COMPLETE && level < 5) startLevel(level + 1); // Starts the next level.
            else if (state == State.LEVEL_COMPLETE && level == 5) state = State.VICTORY; // Shows the final ending.
            repaint(); // Redraws the updated screen.
        }); // Ends the ENTER action.

        bindKey(KeyEvent.VK_R, "restart", () -> { // Registers the restart key.
            if (state == State.GAME_OVER) restartCurrentLevel(); // Retries the current level after a game over.
            else if (state == State.VICTORY) { // Handles restarting after the final victory.
                coins = 0; // Resets the coins for a new adventure.
                lives = 5; // Restores all five lives.
                startLevel(1); // Begins a new adventure at Level 1.
            } // Ends the victory condition.
            repaint(); // Redraws the screen after restarting.
        }); // Ends the restart action.

        addKeyListener(new KeyAdapter() { // Detects when a key is released.

            @Override
            public void keyReleased(KeyEvent e) { // Runs when the player releases a key.
                if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_S) ducking = false; // Stops ducking.
            } // Ends the key release method.

        }); // Ends the keyboard listener.

    } // Ends the control setup method.

    private void jump() { // Starts a jump when allowed.

        if (state == State.PLAYING && !jumping && !ducking) { // Checks that jumping is allowed.
            jumping = true; // Marks the player as airborne.
            velocityY = -15; // Launches the player upwards.
        } // Ends the jump condition.

    } // Ends the jump method.

    // ============================================================
    // LEVEL MANAGEMENT AND LIFE REFILLS
    // ============================================================

    private void startLevel(int newLevel) { // Starts the specified level.

        level = Math.max(1, Math.min(5, newLevel)); // Keeps the level number between 1 and 5.
        lives = 5; // REFILLS ALL FIVE LIVES AT THE BEGINNING OF EVERY LEVEL.
        distance = 0; // Resets progress for the new level.
        baseSpeed = 5 + level; // Makes later levels faster.
        resetPlayerPosition(); // Places the player at the starting position.
        clearObjects(); // Removes obstacles and collectibles from the previous level.
        resetSpawnTimers(); // Resets obstacle, coin and power-up creation timers.
        state = State.PLAYING; // Starts active gameplay.

    } // Ends the startLevel method.

    private void restartCurrentLevel() { // Restarts the level where the player died.

        lives = 5; // REFILLS ALL FIVE LIVES WHEN RETRYING THE CURRENT LEVEL.
        distance = 0; // Resets progress only for this level.
        baseSpeed = 5 + level; // Restores the correct speed for this level.
        resetPlayerPosition(); // Resets the player's movement and position.
        clearObjects(); // Removes the old obstacles and collectibles.
        resetSpawnTimers(); // Restarts the object creation timers.
        state = State.PLAYING; // Returns to gameplay without changing the level number.

    } // Ends the current-level restart method.

    private void resetPlayerPosition() { // Resets player movement and temporary effects.

        playerX = 130; // Returns the player to the starting horizontal position.
        playerY = GROUND - playerHeight; // Places the player on the ground.
        velocityY = 0; // Stops any previous vertical movement.
        jumping = false; // Clears the jumping state.
        ducking = false; // Clears the ducking state.
        shieldTimer = 0; // Removes any previous shield.
        speedTimer = 0; // Removes any previous speed boost.
        invulnerabilityTimer = 0; // Clears the previous damage protection.

    } // Ends the player reset method.

    private void clearObjects() { // Removes all objects from the previous attempt.

        obstacles.clear(); // Removes every obstacle.
        collectibleCoins.clear(); // Removes every coin currently on screen.
        powerUps.clear(); // Removes every power-up currently on screen.

    } // Ends the object clearing method.

    private void resetSpawnTimers() { // Resets the object generation counters.

        obstacleSpawnTimer = 0; // Resets the obstacle timer.
        coinSpawnTimer = 0; // Resets the coin timer.
        powerSpawnTimer = 0; // Resets the power-up timer.

    } // Ends the timer reset method.

    // ============================================================
    // MAIN GAME LOOP
    // ============================================================

    private void updateGame() { // Updates movement, objects, collisions and progress.

        if (state != State.PLAYING) { // Checks whether the game is paused or displaying a menu.
            repaint(); // Keeps the current screen visible.
            return; // Skips gameplay updates when the game is not active.
        } // Ends the state check.

        if (jumping) { // Updates the player's jump.
            playerY += (int) velocityY; // Moves the player vertically.
            velocityY += 0.7; // Applies gravity.
            if (playerY >= GROUND - playerHeight) { // Checks whether the player has landed.
                playerY = GROUND - playerHeight; // Places the player exactly on the ground.
                velocityY = 0; // Stops vertical movement.
                jumping = false; // Ends the jump.
            } // Ends the landing condition.
        } // Ends the jumping update.

        if (shieldTimer > 0) shieldTimer--; // Counts down the shield duration.
        if (speedTimer > 0) speedTimer--; // Counts down the speed boost duration.
        if (invulnerabilityTimer > 0) invulnerabilityTimer--; // Counts down post-hit protection.

        distance++; // Advances the current level's progress.

        obstacleSpawnTimer++; // Advances the obstacle creation timer.
        int spawnDelay = Math.max(38, 95 - level * 9); // Shortens the delay in harder levels.
        if (obstacleSpawnTimer >= spawnDelay) { // Checks whether it is time for an obstacle.
            spawnObstacle(); // Creates a level-specific obstacle.
            obstacleSpawnTimer = 0; // Restarts the obstacle timer.
        } // Ends the obstacle spawning condition.

        coinSpawnTimer++; // Advances the coin creation timer.
        if (coinSpawnTimer >= 105) { // Checks whether it is time for coins.
            spawnCoins(); // Creates a row of coins.
            coinSpawnTimer = 0; // Restarts the coin timer.
        } // Ends the coin spawning condition.

        powerSpawnTimer++; // Advances the power-up creation timer.
        if (powerSpawnTimer >= 300) { // Checks whether it is time for a power-up.
            spawnPowerUp(); // Creates a shield or speed boost.
            powerSpawnTimer = 0; // Restarts the power-up timer.
        } // Ends the power-up spawning condition.

        moveObjects(); // Moves obstacles, coins and power-ups.
        checkObstacleCollisions(); // Checks whether the player hits an obstacle.
        checkCoinCollisions(); // Checks whether the player collects coins.
        checkPowerUpCollisions(); // Checks whether the player collects power-ups.

        if (lives <= 0) { // Checks whether the player has lost all lives.
            lives = 0; // Prevents the displayed life count from becoming negative.
            state = State.GAME_OVER; // Shows the current-level retry screen.
        } // Ends the game-over condition.

        if (distance >= LEVEL_LENGTH && state == State.PLAYING) { // Checks whether the finish has been reached.
            distance = LEVEL_LENGTH; // Caps progress at 100 percent.
            state = State.LEVEL_COMPLETE; // Shows the level's definite ending.
        } // Ends the level completion condition.

        repaint(); // Requests a new frame to display the changes.

    } // Ends the main game loop.

    // ============================================================
    // OBSTACLE CREATION
    // ============================================================

    private void spawnObstacle() { // Creates obstacles according to the current world.

        if (level == 1) { // Level 1 contains simple barriers.
            boolean tall = random.nextBoolean(); // Randomly selects a short or tall barrier.
            int height = tall ? 90 : 40; // Sets the barrier height.
            int width = tall ? 55 : 45; // Sets the barrier width.
            addObstacle(WIDTH, GROUND - height, width, height, tall, false, false, false, false); // Adds the barrier.

        } else if (level == 2) { // Level 2 contains rocky canyon hazards.
            int type = random.nextInt(3); // Randomly selects a canyon obstacle.
            if (type == 0) addObstacle(WIDTH, GROUND - 34, 58, 34, false, true, false, false, false); // Adds spikes.
            else if (type == 1) addObstacle(WIDTH, GROUND - 120, 48, 60, false, false, true, false, false); // Adds a moving rock.
            else addObstacle(WIDTH, GROUND - 65, 55, 65, true, false, false, false, false); // Adds a stationary rock.

        } else if (level == 3) { // Level 3 contains enchanted forest hazards.
            int type = random.nextInt(3); // Selects a random forest obstacle.
            if (type == 0) addObstacle(WIDTH, GROUND - 48, 50, 48, false, true, false, false, false); // Adds thorn spikes.
            else if (type == 1) addObstacle(WIDTH, GROUND - 105, 100, 28, false, false, false, true, false); // Adds a branch to duck under.
            else addObstacle(WIDTH, GROUND - 145, 42, 45, false, false, true, false, false); // Adds a floating hazard.

        } else if (level == 4) { // Level 4 contains icy mountain hazards.
            int type = random.nextInt(3); // Selects a random ice obstacle.
            if (type == 0) addObstacle(WIDTH, GROUND - 45, 65, 45, false, true, false, false, false); // Adds ice spikes.
            else if (type == 1) addObstacle(WIDTH, GROUND - 155, 55, 65, false, false, true, false, true); // Adds a moving ice block.
            else addObstacle(WIDTH, GROUND - 100, 110, 28, false, false, false, true, false); // Adds a low ice beam.

        } else { // Level 5 contains the hardest volcanic hazards.
            int type = random.nextInt(4); // Selects a random volcanic obstacle.
            if (type == 0) addObstacle(WIDTH, GROUND - 32, 85, 32, false, true, false, false, false); // Adds a lava hazard.
            else if (type == 1) addObstacle(WIDTH, GROUND - 150, 45, 45, false, false, true, false, false); // Adds a moving fireball.
            else if (type == 2) addObstacle(WIDTH, GROUND - 115, 95, 28, false, false, false, true, false); // Adds a low fire beam.
            else addObstacle(WIDTH, GROUND - 100, 60, 100, true, false, false, false, false); // Adds a tall volcanic rock.
        } // Ends the level-specific obstacle selection.

    } // Ends the obstacle spawning method.

    private void addObstacle(int x, int y, int width, int height, boolean tall, boolean spike, boolean moving, boolean overhead, boolean fastVertical) { // Creates an obstacle.
        obstacles.add(new Obstacle(x, y, width, height, tall, spike, moving, overhead, fastVertical)); // Stores the new obstacle.
    } // Ends the obstacle creation helper.

    private void spawnCoins() { // Creates three coins in a row.
        int coinY = GROUND - 65 - random.nextInt(90); // Chooses a collectible height.
        for (int i = 0; i < 3; i++) collectibleCoins.add(new Coin(WIDTH + i * 38, coinY, 20)); // Adds three spaced coins.
    } // Ends the coin spawning method.

    private void spawnPowerUp() { // Creates a temporary power-up.
        boolean shield = random.nextBoolean(); // Randomly selects a shield or speed boost.
        powerUps.add(new PowerUp(WIDTH, GROUND - 120, 30, shield)); // Adds the power-up to the world.
    } // Ends the power-up spawning method.

    // ============================================================
    // MOVEMENT
    // ============================================================

    private void moveObjects() { // Scrolls every object towards the player.

        int speed = baseSpeed + (speedTimer > 0 ? 4 : 0); // Adds extra speed while a boost is active.

        Iterator<Obstacle> obstacleIterator = obstacles.iterator(); // Creates a safe obstacle iterator.
        while (obstacleIterator.hasNext()) { // Visits each obstacle.
            Obstacle obstacle = obstacleIterator.next(); // Gets the next obstacle.
            obstacle.x -= speed; // Moves the obstacle left.

            if (obstacle.moving) { // Checks whether the obstacle moves vertically.
                double verticalRate = obstacle.fastVertical ? 0.14 : 0.09; // Selects its vertical speed.
                obstacle.phase += verticalRate; // Advances the movement animation.
                obstacle.y = obstacle.baseY + (int) (Math.sin(obstacle.phase) * 60); // Moves it up and down.
            } // Ends vertical movement.

            if (obstacle.x + obstacle.width < 0) obstacleIterator.remove(); // Removes obstacles that leave the screen.
        } // Ends the obstacle loop.

        Iterator<Coin> coinIterator = collectibleCoins.iterator(); // Creates a safe coin iterator.
        while (coinIterator.hasNext()) { // Visits each coin.
            Coin coin = coinIterator.next(); // Gets the next coin.
            coin.x -= speed; // Moves the coin left.
            if (coin.x + coin.size < 0) coinIterator.remove(); // Removes coins outside the screen.
        } // Ends the coin loop.

        Iterator<PowerUp> powerIterator = powerUps.iterator(); // Creates a safe power-up iterator.
        while (powerIterator.hasNext()) { // Visits each power-up.
            PowerUp power = powerIterator.next(); // Gets the next power-up.
            power.x -= speed; // Moves the power-up left.
            if (power.x + power.size < 0) powerIterator.remove(); // Removes power-ups outside the screen.
        } // Ends the power-up loop.

    } // Ends the object movement method.

    // ============================================================
    // COLLISION DETECTION
    // ============================================================

    private boolean overlaps(int x1, int y1, int w1, int h1, int x2, int y2, int w2, int h2) { // Tests two rectangles.
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2; // Returns true if they overlap.
    } // Ends the collision helper.

    private int currentPlayerY() { // Calculates the player's current vertical position.
        return ducking ? GROUND - 35 : playerY; // Lowers the collision box when ducking.
    } // Ends the vertical position helper.

    private int currentPlayerHeight() { // Calculates the player's current height.
        return ducking ? 35 : playerHeight; // Makes the collision box shorter while ducking.
    } // Ends the player height helper.

    private void checkObstacleCollisions() { // Detects collisions with hazards.

        Iterator<Obstacle> iterator = obstacles.iterator(); // Creates an obstacle iterator.
        while (iterator.hasNext()) { // Checks each obstacle.
            Obstacle obstacle = iterator.next(); // Gets the next obstacle.
            boolean hit = overlaps(playerX, currentPlayerY(), playerWidth, currentPlayerHeight(), obstacle.x, obstacle.y, obstacle.width, obstacle.height); // Tests for a collision.

            if (!hit) continue; // Skips obstacles that do not touch the player.

            if (shieldTimer > 0) { // Checks whether a shield is active.
                shieldTimer = 0; // Uses up the shield.
                iterator.remove(); // Removes the obstacle that hit the shield.
                continue; // Continues checking the remaining obstacles.
            } // Ends the shield condition.

            if (invulnerabilityTimer == 0) { // Prevents multiple lives being lost immediately.
                lives--; // Removes one life after a hit.
                invulnerabilityTimer = 70; // Starts a short recovery period.
            } // Ends the damage condition.

            iterator.remove(); // Removes the obstacle after the collision.
        } // Ends the obstacle collision loop.

    } // Ends the obstacle collision method.

    private void checkCoinCollisions() { // Detects collected coins.

        Iterator<Coin> iterator = collectibleCoins.iterator(); // Creates a coin iterator.
        while (iterator.hasNext()) { // Checks each coin.
            Coin coin = iterator.next(); // Gets the next coin.
            boolean collected = overlaps(playerX, playerY, playerWidth, playerHeight, coin.x, coin.y, coin.size, coin.size); // Checks whether the player touches it.
            if (collected) { // Runs when a coin is collected.
                coins++; // Increases the total coin count.
                iterator.remove(); // Removes the collected coin.
            } // Ends the collection condition.
        } // Ends the coin collision loop.

    } // Ends the coin collision method.

    private void checkPowerUpCollisions() { // Detects collected power-ups.

        Iterator<PowerUp> iterator = powerUps.iterator(); // Creates a power-up iterator.
        while (iterator.hasNext()) { // Checks each power-up.
            PowerUp power = iterator.next(); // Gets the next power-up.
            boolean collected = overlaps(playerX, playerY, playerWidth, playerHeight, power.x, power.y, power.size, power.size); // Checks for contact.

            if (collected) { // Runs when the player collects a power-up.
                if (power.shield) shieldTimer = 300; // Activates the shield for about five seconds.
                else speedTimer = 300; // Activates the speed boost for about five seconds.
                iterator.remove(); // Removes the collected power-up.
            } // Ends the collection condition.
        } // Ends the power-up collision loop.

    } // Ends the power-up collision method.

    // ============================================================
    // BACKGROUNDS AND SCENERY
    // ============================================================

    private void drawBackground(Graphics2D g) { // Draws the sky and ground for the current level.

        switch (level) { // Selects the sky colour.
            case 1: g.setColor(new Color(135, 206, 235)); break; // Uses a blue sky for Green Hills.
            case 2: g.setColor(new Color(220, 155, 105)); break; // Uses a dusty orange sky for Rocky Canyon.
            case 3: g.setColor(new Color(65, 45, 100)); break; // Uses a purple sky for Enchanted Forest.
            case 4: g.setColor(new Color(185, 225, 245)); break; // Uses a pale blue sky for Frozen Peaks.
            default: g.setColor(new Color(95, 30, 35)); break; // Uses a dark red sky for Volcanic Fortress.
        } // Ends the sky colour selection.

        g.fillRect(0, 0, WIDTH, HEIGHT); // Paints the sky across the entire window.

        switch (level) { // Selects the scenery for the current world.
            case 1: drawHills(g); break; // Draws green hills.
            case 2: drawCanyon(g); break; // Draws rocky cliffs.
            case 3: drawForest(g); break; // Draws the enchanted forest.
            case 4: drawMountains(g); break; // Draws snowy mountains.
            default: drawVolcano(g); break; // Draws the volcanic landscape.
        } // Ends the scenery selection.

        switch (level) { // Selects the ground colour.
            case 1: g.setColor(new Color(70, 155, 65)); break; // Green grass.
            case 2: g.setColor(new Color(115, 75, 60)); break; // Rocky ground.
            case 3: g.setColor(new Color(40, 90, 60)); break; // Dark forest ground.
            case 4: g.setColor(new Color(215, 235, 245)); break; // Snow and ice.
            default: g.setColor(new Color(65, 40, 45)); break; // Volcanic rock.
        } // Ends the ground colour selection.

        g.fillRect(0, GROUND, WIDTH, HEIGHT - GROUND); // Draws the ground.
        g.setColor(Color.DARK_GRAY); // Selects the colour for the ground border.
        g.fillRect(0, GROUND, WIDTH, 5); // Draws the boundary above the ground.

        if (distance >= LEVEL_LENGTH - 260) drawFinishMarker(g); // Shows the finish marker near the end.

    } // Ends the background drawing method.

    private void drawHills(Graphics2D g) { // Draws the Level 1 landscape.
        g.setColor(new Color(100, 180, 95)); // Selects a hill colour.
        g.fillOval(30, 320, 390, 190); // Draws the first hill.
        g.fillOval(330, 340, 430, 180); // Draws the second hill.
        g.fillOval(690, 315, 420, 200); // Draws the third hill.
        for (int x = 70; x < WIDTH; x += 230) { // Places trees at regular intervals.
            g.setColor(new Color(105, 65, 35)); // Selects brown for tree trunks.
            g.fillRect(x, 350, 22, 95); // Draws a tree trunk.
            g.setColor(new Color(35, 125, 55)); // Selects green for the leaves.
            g.fillOval(x - 28, 315, 80, 75); // Draws the tree canopy.
        } // Ends the tree drawing loop.
    } // Ends the hills method.

    private void drawCanyon(Graphics2D g) { // Draws the Level 2 rocky canyon.
        g.setColor(new Color(155, 95, 65)); // Selects a brown colour for distant cliffs.
        g.fillPolygon(new Polygon(new int[]{0,100,190,280,390,490,600,720,850,1000,1000,0}, new int[]{370,240,320,205,330,225,345,210,315,230,475,475}, 12)); // Draws the distant cliffs.
        g.setColor(new Color(100, 65, 55)); // Selects a darker colour for foreground rocks.
        g.fillPolygon(new Polygon(new int[]{0,140,235,330,455,570,700,840,1000,1000,0}, new int[]{415,330,405,315,410,325,415,340,395,475,475}, 11)); // Draws the nearer cliffs.
    } // Ends the canyon method.

    private void drawForest(Graphics2D g) { // Draws the Level 3 enchanted forest.
        for (int x = 40; x < WIDTH; x += 180) { // Places trees throughout the forest.
            g.setColor(new Color(45, 35, 60)); // Selects a dark colour for tree trunks.
            g.fillRect(x, 220, 35, 260); // Draws the trunk.
            g.setColor(new Color(65, 115, 90)); // Selects green for the foliage.
            g.fillOval(x - 40, 185, 115, 90); // Draws the leafy canopy.
        } // Ends the tree loop.
        for (int x = 100; x < WIDTH; x += 220) { // Places glowing mushrooms.
            g.setColor(new Color(80, 240, 210)); // Selects cyan for mushroom stems.
            g.fillRect(x + 10, 425, 9, 30); // Draws a mushroom stem.
            g.setColor(new Color(190, 70, 245)); // Selects purple for the mushroom cap.
            g.fillOval(x, 410, 30, 22); // Draws the mushroom cap.
        } // Ends the mushroom loop.
    } // Ends the forest method.

    private void drawMountains(Graphics2D g) { // Draws the Level 4 frozen mountains.
        g.setColor(new Color(110, 155, 185)); // Selects blue-grey for the mountains.
        g.fillPolygon(new Polygon(new int[]{0,130,250,390,540,700,850,1000,1000,0}, new int[]{380,170,360,120,350,145,360,180,475,475}, 10)); // Draws the mountain range.
        g.setColor(Color.WHITE); // Selects white for snowy peaks.
        g.fillPolygon(new Polygon(new int[]{85,130,180}, new int[]{245,170,245}, 3)); // Draws the first snow cap.
        g.fillPolygon(new Polygon(new int[]{345,390,435}, new int[]{195,120,195}, 3)); // Draws the second snow cap.
        g.fillPolygon(new Polygon(new int[]{805,850,895}, new int[]{245,180,245}, 3)); // Draws the third snow cap.
        for (int x = 80; x < WIDTH; x += 220) { // Places pine trees in the snow.
            g.setColor(new Color(30, 85, 105)); // Selects a dark icy colour for the trees.
            g.fillPolygon(new Polygon(new int[]{x,x+24,x+48}, new int[]{390,305,390}, 3)); // Draws the upper branches.
            g.fillPolygon(new Polygon(new int[]{x+5,x+24,x+43}, new int[]{355,280,355}, 3)); // Draws the lower branches.
        } // Ends the pine tree loop.
    } // Ends the mountain method.

    private void drawVolcano(Graphics2D g) { // Draws the Level 5 volcanic landscape.
        g.setColor(new Color(45, 35, 40)); // Selects dark grey for volcanic rock.
        g.fillPolygon(new Polygon(new int[]{0,150,300,430,540,680,820,1000,1000,0}, new int[]{380,330,210,300,155,305,220,350,475,475}, 10)); // Draws the volcano.
        g.setColor(new Color(225, 65, 30)); // Selects orange-red for glowing lava.
        g.fillPolygon(new Polygon(new int[]{390,430,415,450,440,470,420}, new int[]{285,315,350,385,415,455,455}, 7)); // Draws one lava channel.
        g.fillPolygon(new Polygon(new int[]{655,680,665,700,690,720,675}, new int[]{300,330,365,390,420,455,455}, 7)); // Draws another lava channel.
        g.setColor(Color.YELLOW); // Selects yellow for the crater glow.
        g.fillOval(500, 151, 55, 16); // Draws the glowing centre of the crater.
    } // Ends the volcano method.

    // ============================================================
    // PLAYER DRAWING
    // ============================================================

    private void drawPlayer(Graphics2D g) { // Draws the player's detailed character.

        if (invulnerabilityTimer > 0 && (invulnerabilityTimer / 5) % 2 == 0) return; // Makes the player flash briefly after a hit.

        int y = currentPlayerY(); // Gets the player's current vertical position.
        int height = currentPlayerHeight(); // Gets the player's current height.

        if (shieldTimer > 0) { // Checks whether a shield is active.
            g.setColor(Color.CYAN); // Selects cyan for the shield.
            g.drawOval(playerX - 10, y - 8, playerWidth + 20, height + 16); // Draws a protective ring.
        } // Ends the shield drawing condition.

        g.setColor(new Color(65, 40, 25)); // Selects brown for the boots.
        g.fillRoundRect(playerX + 3, y + height - 9, 17, 12, 5, 5); // Draws the left boot.
        g.fillRoundRect(playerX + 25, y + height - 9, 17, 12, 5, 5); // Draws the right boot.

        g.setColor(new Color(35, 45, 105)); // Selects dark blue for the trousers.
        g.fillRect(playerX + 5, y + 38, 14, height - 43); // Draws the left trouser leg.
        g.fillRect(playerX + 25, y + 38, 14, height - 43); // Draws the right trouser leg.

        g.setColor(new Color(35, 95, 220)); // Selects bright blue for the jacket.
        g.fillRoundRect(playerX + 4, y + 23, 36, 26, 7, 7); // Draws the jacket.
        g.setColor(Color.WHITE); // Selects white for the jacket zipper.
        g.drawLine(playerX + 22, y + 26, playerX + 22, y + 46); // Draws the zipper.

        g.setColor(new Color(35, 95, 220)); // Selects blue for the sleeves.
        g.fillRoundRect(playerX - 2, y + 26, 10, 21, 5, 5); // Draws the left sleeve.
        g.fillRoundRect(playerX + 36, y + 26, 10, 21, 5, 5); // Draws the right sleeve.

        g.setColor(new Color(245, 190, 135)); // Selects skin colour for the hands.
        g.fillOval(playerX - 3, y + 41, 11, 10); // Draws the left hand.
        g.fillOval(playerX + 36, y + 41, 11, 10); // Draws the right hand.
        g.fillRect(playerX + 15, y + 19, 15, 10); // Draws the neck.
        g.fillOval(playerX + 7, y + 3, 32, 29); // Draws the face.

        g.setColor(new Color(75, 40, 20)); // Selects brown for the hair.
        g.fillOval(playerX + 6, y, 33, 15); // Draws the top of the hair.
        g.fillRect(playerX + 7, y + 5, 6, 12); // Draws the left side of the hair.
        g.fillRect(playerX + 32, y + 4, 7, 10); // Draws the right side of the hair.

        g.setColor(Color.BLACK); // Selects black for the eyes.
        g.fillOval(playerX + 15, y + 15, 4, 5); // Draws the left eye.
        g.fillOval(playerX + 29, y + 15, 4, 5); // Draws the right eye.

        g.setColor(new Color(210, 130, 100)); // Selects a skin shade for the nose.
        g.fillOval(playerX + 23, y + 19, 4, 4); // Draws the nose.

        g.setColor(new Color(225, 40, 45)); // Selects red for the scarf.
        g.fillRect(playerX + 12, y + 25, 23, 6); // Draws the scarf around the neck.
        g.fillPolygon(new Polygon(new int[]{playerX+28,playerX+37,playerX+31}, new int[]{y+28,y+45,y+31}, 3)); // Draws the trailing scarf end.

        g.setColor(Color.BLACK); // Selects black for the jacket outline.
        g.drawRoundRect(playerX + 4, y + 23, 36, 26, 7, 7); // Outlines the jacket.

    } // Ends the player drawing method.

    // ============================================================
    // OBSTACLE, COIN AND POWER-UP DRAWING
    // ============================================================

    private void drawObstacles(Graphics2D g) { // Draws every active obstacle.

        for (Obstacle o : obstacles) { // Visits each obstacle.

            if (o.spike) { // Draws triangular spikes.
                Color c = level == 4 ? new Color(130, 220, 255) : level == 5 ? new Color(255, 90, 25) : level == 3 ? new Color(90, 210, 100) : new Color(210, 35, 45); // Chooses a colour for the current world.
                g.setColor(c); // Applies the selected spike colour.
                g.fillPolygon(new Polygon(new int[]{o.x,o.x+o.width/2,o.x+o.width}, new int[]{o.y+o.height,o.y,o.y+o.height}, 3)); // Draws the spike triangle.

            } else if (o.overhead) { // Draws branches or beams that must be ducked under.
                g.setColor(level == 3 ? new Color(65, 100, 45) : new Color(110, 100, 105)); // Chooses a branch or beam colour.
                g.fillRoundRect(o.x, o.y, o.width, o.height, 8, 8); // Draws the overhead obstacle.

            } else if (o.moving) { // Draws vertically moving hazards.
                g.setColor(level == 5 ? new Color(245, 90, 30) : level == 4 ? new Color(145, 225, 255) : level == 3 ? new Color(175, 90, 220) : new Color(125, 105, 95)); // Chooses the moving hazard colour.
                if (level == 5) g.fillOval(o.x, o.y, o.width, o.height); // Draws a round fireball in Level 5.
                else g.fillRoundRect(o.x, o.y, o.width, o.height, 10, 10); // Draws moving rocks or ice blocks.

            } else { // Draws stationary barriers.
                g.setColor(level == 1 ? new Color(125,75,35) : level == 2 ? new Color(100,75,65) : level == 3 ? new Color(75,110,50) : level == 4 ? new Color(130,205,235) : new Color(60,45,50)); // Chooses the barrier colour.
                g.fillRect(o.x, o.y, o.width, o.height); // Draws the barrier.
                g.setColor(Color.BLACK); // Selects black for the outline.
                g.drawRect(o.x, o.y, o.width, o.height); // Outlines the barrier.
            } // Ends the obstacle drawing selection.

        } // Ends the obstacle loop.

    } // Ends the obstacle drawing method.

    private void drawCoins(Graphics2D g) { // Draws all collectible coins.
        for (Coin c : collectibleCoins) { // Visits each coin.
            g.setColor(new Color(255, 215, 0)); // Selects gold for the coin.
            g.fillOval(c.x, c.y, c.size, c.size); // Draws the coin.
            g.setColor(new Color(210, 145, 0)); // Selects a darker gold outline.
            g.drawOval(c.x, c.y, c.size, c.size); // Outlines the coin.
        } // Ends the coin drawing loop.
    } // Ends the coin drawing method.

    private void drawPowerUps(Graphics2D g) { // Draws shields and speed boosts.
        for (PowerUp p : powerUps) { // Visits each power-up.
            g.setColor(p.shield ? Color.CYAN : Color.ORANGE); // Uses cyan for shields and orange for speed boosts.
            g.fillRoundRect(p.x, p.y, p.size, p.size, 8, 8); // Draws the power-up.
            g.setColor(Color.BLACK); // Selects black for the power-up label.
            g.setFont(new Font("Arial", Font.BOLD, 18)); // Sets the label font.
            g.drawString(p.shield ? "S" : "F", p.x + 9, p.y + 21); // Labels shields S and speed boosts F.
        } // Ends the power-up loop.
    } // Ends the power-up drawing method.

    // ============================================================
    // HUD AND FINISH LINE
    // ============================================================

    private void drawHUD(Graphics2D g) { // Draws lives, coins and level progress.

        g.setColor(Color.BLACK); // Selects black for HUD text.
        g.setFont(new Font("Arial", Font.BOLD, 18)); // Sets the main HUD font.
        g.drawString("Level: " + level + " / 5", 20, 30); // Displays the current level.
        g.drawString("Lives: " + lives, 20, 55); // Displays remaining lives.
        g.drawString("Coins: " + coins, 20, 80); // Displays collected coins.

        if (shieldTimer > 0) g.drawString("SHIELD ACTIVE", 20, 105); // Displays an active shield.
        if (speedTimer > 0) g.drawString("SPEED BOOST", 20, 130); // Displays an active speed boost.

        int progress = Math.min(100, distance * 100 / LEVEL_LENGTH); // Converts progress into a percentage.
        g.setColor(Color.WHITE); // Selects white for the progress bar background.
        g.fillRect(790, 40, 170, 14); // Draws the empty progress bar.
        g.setColor(new Color(35, 150, 60)); // Selects green for completed progress.
        g.fillRect(790, 40, progress * 170 / 100, 14); // Draws the filled portion of the bar.
        g.setColor(Color.BLACK); // Selects black for the bar outline.
        g.drawRect(790, 40, 170, 14); // Outlines the progress bar.

        g.setFont(new Font("Arial", Font.PLAIN, 13)); // Sets the controls font.
        g.drawString("SPACE / UP: Jump", 790, 75); // Displays the jump control.
        g.drawString("DOWN / S: Duck", 790, 94); // Displays the duck control.
        g.drawString("P: Pause", 790, 113); // Displays the pause control.

    } // Ends the HUD method.

    private void drawFinishMarker(Graphics2D g) { // Draws the finish flag near the end of a level.
        int x = 925; // Sets the finish post's horizontal position.
        int y = GROUND - 125; // Sets the finish flag's vertical position.
        g.setColor(Color.WHITE); // Selects white for the post.
        g.fillRect(x, y, 8, 125); // Draws the finish post.
        int cell = 14; // Sets the size of each checkered square.
        for (int row = 0; row < 4; row++) { // Visits each row of flag squares.
            for (int col = 0; col < 4; col++) { // Visits each column of flag squares.
                g.setColor((row + col) % 2 == 0 ? Color.BLACK : Color.WHITE); // Alternates black and white squares.
                g.fillRect(x + 8 + col * cell, y + row * cell, cell, cell); // Draws one flag square.
            } // Ends the column loop.
        } // Ends the row loop.
        g.setColor(Color.BLACK); // Selects black for the flag label.
        g.drawString("FINISH", x - 22, y - 10); // Labels the finish line.
    } // Ends the finish marker method.

    // ============================================================
    // STORY AND ENDING SCREENS
    // ============================================================

    private void drawCentered(Graphics2D g, String text, int y) { // Draws text centred horizontally.
        FontMetrics fm = g.getFontMetrics(); // Measures the text width.
        int x = (WIDTH - fm.stringWidth(text)) / 2; // Calculates the horizontal centre.
        g.drawString(text, x, y); // Draws the text at the calculated position.
    } // Ends the text centring method.

    private void drawStoryScreen(Graphics2D g) { // Displays the opening backstory.
        g.setColor(new Color(20, 30, 55, 235)); // Selects a dark background colour.
        g.fillRoundRect(90, 75, 820, 420, 24, 24); // Draws the story panel.
        g.setColor(Color.YELLOW); // Selects yellow for the title.
        g.setFont(new Font("Arial", Font.BOLD, 42)); // Sets the title font.
        drawCentered(g, "PIXELDASH", 145); // Displays the game title.
        g.setColor(Color.WHITE); // Selects white for the story.
        g.setFont(new Font("Arial", Font.PLAIN, 19)); // Sets the story font.
        drawCentered(g, "A little cat has gone missing beyond the hills.", 215); // Introduces the missing cat.
        drawCentered(g, "Travel through five dangerous worlds to find it.", 252); // Explains the adventure.
        drawCentered(g, "Jump over spikes and dodge moving hazards.", 289); // Introduces the obstacles.
        drawCentered(g, "Collect coins and find helpful power-ups.", 326); // Explains collectibles.
        g.setColor(Color.CYAN); // Selects cyan for the key story line.
        drawCentered(g, "Five levels. Five worlds. One rescue.", 375); // Summarises the adventure.
        g.setColor(Color.YELLOW); // Selects yellow for the start instruction.
        g.setFont(new Font("Arial", Font.BOLD, 22)); // Sets the start instruction font.
        drawCentered(g, "Press ENTER to begin", 435); // Tells the player how to start.
    } // Ends the story screen method.

    private void drawLevelCompleteScreen(Graphics2D g) { // Displays the current level's ending.
        g.setColor(new Color(10, 15, 30, 220)); // Selects a dark overlay.
        g.fillRect(0, 0, WIDTH, HEIGHT); // Covers the gameplay screen.
        g.setColor(Color.YELLOW); // Selects yellow for the ending title.
        g.setFont(new Font("Arial", Font.BOLD, 38)); // Sets the ending title font.
        drawCentered(g, "LEVEL " + level + " COMPLETE!", 165); // Confirms the level is finished.
        g.setColor(Color.WHITE); // Selects white for the level name.
        g.setFont(new Font("Arial", Font.BOLD, 27)); // Sets the level name font.
        drawCentered(g, getLevelName(), 215); // Displays the completed world.
        g.setFont(new Font("Arial", Font.PLAIN, 20)); // Sets the message font.
        drawCentered(g, "You reached the finish line!", 275); // Confirms the finish.
        drawCentered(g, "Coins collected: " + coins, 315); // Shows the coin total.
        g.setColor(Color.CYAN); // Selects cyan for the next action.
        g.setFont(new Font("Arial", Font.BOLD, 22)); // Sets the instruction font.
        if (level < 5) drawCentered(g, "Press ENTER for Level " + (level + 1), 385); // Offers the next level.
        else drawCentered(g, "Press ENTER for the final ending", 385); // Offers the final ending after Level 5.
    } // Ends the level ending method.

    private String getLevelName() { // Returns the name of the current level.
        switch (level) { // Checks the level number.
            case 1: return "GREEN HILLS"; // Names Level 1.
            case 2: return "ROCKY CANYON"; // Names Level 2.
            case 3: return "ENCHANTED FOREST"; // Names Level 3.
            case 4: return "FROZEN PEAKS"; // Names Level 4.
            default: return "VOLCANIC FORTRESS"; // Names Level 5.
        } // Ends the level name selection.
    } // Ends the level name method.

    private void drawGameOverScreen(Graphics2D g) { // Displays the current-level game-over screen.
        g.setColor(new Color(0, 0, 0, 210)); // Selects a dark overlay.
        g.fillRect(0, 0, WIDTH, HEIGHT); // Covers the gameplay screen.
        g.setColor(Color.RED); // Selects red for the game-over heading.
        g.setFont(new Font("Arial", Font.BOLD, 44)); // Sets the heading font.
        drawCentered(g, "GAME OVER", 210); // Displays the game-over message.
        g.setColor(Color.WHITE); // Selects white for the level name.
        g.setFont(new Font("Arial", Font.BOLD, 23)); // Sets the level name font.
        drawCentered(g, getLevelName(), 260); // Shows which level the player lost.
        g.setFont(new Font("Arial", Font.PLAIN, 20)); // Sets the instruction font.
        drawCentered(g, "Your completed levels stay completed.", 320); // Explains that previous progress is preserved.
        g.setColor(Color.YELLOW); // Selects yellow for the retry instruction.
        g.setFont(new Font("Arial", Font.BOLD, 22)); // Sets the retry instruction font.
        drawCentered(g, "Press R to retry Level " + level, 400); // Restarts the same level.
    } // Ends the game-over method.

    private void drawPauseScreen(Graphics2D g) { // Displays the pause overlay.
        g.setColor(new Color(0, 0, 0, 190)); // Selects a dark transparent overlay.
        g.fillRect(0, 0, WIDTH, HEIGHT); // Covers the gameplay screen.
        g.setColor(Color.WHITE); // Selects white for pause text.
        g.setFont(new Font("Arial", Font.BOLD, 42)); // Sets the heading font.
        drawCentered(g, "PAUSED", 260); // Displays the pause heading.
        g.setFont(new Font("Arial", Font.PLAIN, 22)); // Sets the instruction font.
        drawCentered(g, "Press P to resume", 310); // Explains how to resume.
    } // Ends the pause screen method.

    private void drawCat(Graphics2D g, int x, int y) { // Draws the rescued cat.
        g.setColor(new Color(245, 160, 70)); // Selects orange for the cat's fur.
        g.fillOval(x + 35, y + 35, 45, 15); // Draws the tail.
        g.fillOval(x + 5, y + 25, 65, 45); // Draws the body.
        g.fillOval(x + 5, y, 48, 43); // Draws the head.
        g.fillPolygon(new Polygon(new int[]{x+8,x+12,x+24}, new int[]{y+17,y-4,y+12}, 3)); // Draws the left ear.
        g.fillPolygon(new Polygon(new int[]{x+34,x+47,x+47}, new int[]{y+12,y-4,y+20}, 3)); // Draws the right ear.
        g.setColor(Color.BLACK); // Selects black for the eyes.
        g.fillOval(x + 17, y + 17, 5, 6); // Draws the left eye.
        g.fillOval(x + 36, y + 17, 5, 6); // Draws the right eye.
        g.setColor(Color.PINK); // Selects pink for the nose.
        g.fillOval(x + 27, y + 26, 7, 5); // Draws the nose.
    } // Ends the cat drawing method.

    private void drawVictoryScreen(Graphics2D g) { // Displays the final cat rescue.
        g.setColor(new Color(35, 15, 25)); // Selects a dark volcanic background.
        g.fillRect(0, 0, WIDTH, HEIGHT); // Paints the final screen.
        g.setColor(new Color(220, 80, 35)); // Selects orange for the rescue platform.
        g.fillRect(0, GROUND, WIDTH, HEIGHT - GROUND); // Draws the platform.
        g.setColor(Color.YELLOW); // Selects yellow for the victory heading.
        g.setFont(new Font("Arial", Font.BOLD, 38)); // Sets the heading font.
        drawCentered(g, "YOU SAVED THE CAT!", 115); // Celebrates the rescue.
        drawCat(g, 465, 220); // Draws the rescued cat.
        g.setColor(Color.WHITE); // Selects white for the ending story.
        g.setFont(new Font("Arial", Font.PLAIN, 21)); // Sets the ending text font.
        drawCentered(g, "You crossed all five worlds.", 345); // Celebrates the completed adventure.
        drawCentered(g, "Your little friend is finally safe!", 382); // Confirms the cat is safe.
        drawCentered(g, "Total coins: " + coins, 417); // Displays the final coin total.
        g.setColor(Color.YELLOW); // Selects yellow for the replay instruction.
        g.setFont(new Font("Arial", Font.BOLD, 22)); // Sets the replay instruction font.
        drawCentered(g, "Press R to begin a new adventure", 470); // Offers a new playthrough.
    } // Ends the victory screen method.

    // ============================================================
    // MAIN PAINT METHOD
    // ============================================================

    @Override
    protected void paintComponent(Graphics graphics) { // Redraws the screen whenever Swing requests it.

        super.paintComponent(graphics); // Clears the previous frame.
        Graphics2D g = (Graphics2D) graphics.create(); // Creates an independent graphics context.

        drawBackground(g); // Draws the current world's scenery.

        if (state != State.STORY) { // Draws gameplay objects after the story screen.
            drawObstacles(g); // Draws the hazards.
            drawCoins(g); // Draws coins.
            drawPowerUps(g); // Draws power-ups.
            drawPlayer(g); // Draws the player.
            drawHUD(g); // Draws lives, coins and progress.
        } // Ends the gameplay drawing condition.

        switch (state) { // Draws the appropriate screen overlay.
            case STORY: drawStoryScreen(g); break; // Shows the backstory.
            case PAUSED: drawPauseScreen(g); break; // Shows the pause screen.
            case LEVEL_COMPLETE: drawLevelCompleteScreen(g); break; // Shows the level ending.
            case GAME_OVER: drawGameOverScreen(g); break; // Shows the current-level retry screen.
            case VICTORY: drawVictoryScreen(g); break; // Shows the final cat rescue.
            default: break; // Leaves the gameplay screen unobstructed.
        } // Ends the screen selection.

        g.dispose(); // Releases the graphics resources.

    } // Ends the paint method.

    // ============================================================
    // GAME OBJECT CLASSES
    // ============================================================

    private static class Obstacle { // Stores the properties of one obstacle.

        int x; // Stores the horizontal position.
        int y; // Stores the vertical position.
        int width; // Stores the obstacle width.
        int height; // Stores the obstacle height.

        boolean tall; // Identifies a tall barrier.
        boolean spike; // Identifies a spike obstacle.
        boolean moving; // Identifies a vertically moving obstacle.
        boolean overhead; // Identifies a beam or branch to duck under.
        boolean fastVertical; // Identifies a faster vertical movement pattern.

        int baseY; // Stores the original vertical position.
        double phase = 0; // Stores the animation phase.

        Obstacle(int x, int y, int width, int height, boolean tall, boolean spike, boolean moving, boolean overhead, boolean fastVertical) { // Creates an obstacle.
            this.x = x; // Saves the horizontal position.
            this.y = y; // Saves the vertical position.
            this.width = width; // Saves the width.
            this.height = height; // Saves the height.
            this.tall = tall; // Saves the tall barrier flag.
            this.spike = spike; // Saves the spike flag.
            this.moving = moving; // Saves the moving obstacle flag.
            this.overhead = overhead; // Saves the overhead obstacle flag.
            this.fastVertical = fastVertical; // Saves the faster movement flag.
            this.baseY = y; // Records the starting height for vertical animation.
        } // Ends the obstacle constructor.

    } // Ends the Obstacle class.

    private static class Coin { // Stores one collectible coin.

        int x; // Stores the coin's horizontal position.
        int y; // Stores the coin's vertical position.
        int size; // Stores the coin's diameter.

        Coin(int x, int y, int size) { // Creates a coin.
            this.x = x; // Saves the horizontal position.
            this.y = y; // Saves the vertical position.
            this.size = size; // Saves the coin size.
        } // Ends the coin constructor.

    } // Ends the Coin class.

    private static class PowerUp { // Stores one shield or speed power-up.

        int x; // Stores the power-up's horizontal position.
        int y; // Stores the power-up's vertical position.
        int size; // Stores the power-up dimensions.
        boolean shield; // True means shield; false means speed boost.

        PowerUp(int x, int y, int size, boolean shield) { // Creates a power-up.
            this.x = x; // Saves the horizontal position.
            this.y = y; // Saves the vertical position.
            this.size = size; // Saves the size.
            this.shield = shield; // Saves the power-up type.
        } // Ends the power-up constructor.

    } // Ends the PowerUp class.

/**
 * Main method: starts the PixelDash game.
 */
public static void main(String[] args) {

    // Creates the game window.
    javax.swing.JFrame frame =
            new javax.swing.JFrame("PixelDash");

    // Creates your existing game panel.
    PixelDash game = new PixelDash();

    // Sets the window size.
    frame.setSize(WIDTH, HEIGHT);

    // Stops the program when the window is closed.
    frame.setDefaultCloseOperation(
            javax.swing.JFrame.EXIT_ON_CLOSE);

    // Adds your game to the window.
    frame.add(game);

    // Prevents the window from being resized.
    frame.setResizable(false);

    // Centres the window.
    frame.setLocationRelativeTo(null);

    // Displays the window.
    frame.setVisible(true);

    // Gives keyboard controls to the game.
    game.requestFocusInWindow();
}

} // Ends the GamePanel class.