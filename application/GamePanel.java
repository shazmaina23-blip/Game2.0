
package application; // Places the class in the application package.

import java.awt.Color; // Provides colours for the game.
import java.awt.Dimension; // Defines the game window size.
import java.awt.Font; // Defines text styles.
import java.awt.Graphics; // Draws game graphics.
import java.awt.Graphics2D; // Provides advanced drawing.
import java.awt.Polygon; // Draws triangular spikes.
import java.awt.event.ActionEvent; // Represents keyboard actions.
import java.awt.event.KeyEvent; // Identifies keyboard keys.
import java.util.ArrayList; // Stores game objects.
import java.util.Iterator; // Removes objects safely during loops.
import java.util.Random; // Generates random obstacles.
import javax.swing.AbstractAction; // Connects keyboard actions.
import javax.swing.JComponent; // Provides keyboard focus constants.
import javax.swing.JOptionPane; // Displays shop and score dialogs.
import javax.swing.JPanel; // Provides the game drawing surface.
import javax.swing.KeyStroke; // Represents keyboard shortcuts.
import javax.swing.Timer; // Updates the game repeatedly.

public class GamePanel extends JPanel { // Main game panel.

    private static final long serialVersionUID = 1L; // Supports Java serialisation.

    private static final int WIDTH = 1000; // Sets the game width.
    private static final int HEIGHT = 600; // Sets the game height.
    private static final int GROUND = 485; // Sets the ground height.
    private static final int PLAYER_X = 130; // Keeps the player near the left side.
    private static final int PLAYER_W = 42; // Sets the player width.
    private static final int PLAYER_H = 68; // Sets the player height.
    private static final int LEVEL_LENGTH = 1500; // Sets the progress required to finish a level.

    private final GameDatabase database = new GameDatabase(); // Connects to saved game data.
    private final Random random = new Random(); // Generates random objects.
    private final Timer timer; // Updates the game about 60 times per second.

    private enum State { STORY, PLAYING, PAUSED, LEVEL_COMPLETE, GAME_OVER, VICTORY } // Defines the game screens.
    private State state = State.STORY; // Starts with the story screen.

    private int level = 1; // Starts at Level 1.
    private int lives = 5; // Gives the player five lives per attempt.
    private int coins; // Stores the current saved coin balance.
    private int distance; // Tracks level progress.
    private int levelScore; // Stores the score for the current run.
    private int speed = 6; // Sets the initial obstacle speed.
    private int spawnCounter; // Controls obstacle spawning.
    private int coinCounter; // Controls coin spawning.
    private int powerCounter; // Controls power-up spawning.
    private int animationFrame; // Animates moving scenery.
    private int invulnerability; // Briefly protects the player after being hit.
    private int shieldTimer; // Tracks remaining shield duration.
    private int speedTimer; // Tracks remaining speed boost duration.

    private int playerY = GROUND - PLAYER_H; // Places the player on the ground.
    private double verticalSpeed; // Stores vertical jumping velocity.
    private boolean jumping; // Tracks whether the player is jumping.
    private boolean ducking; // Tracks whether the player is ducking.
    private boolean shieldActive; // Tracks whether a shield is active.

    private CharacterDesign character; // Stores the equipped character design.

    private final ArrayList<Obstacle> obstacles = new ArrayList<>(); // Stores hazards.
    private final ArrayList<Coin> collectibles = new ArrayList<>(); // Stores coins.
    private final ArrayList<PowerUp> powerUps = new ArrayList<>(); // Stores power-ups.

    public GamePanel() { // Prepares the game panel.

        setPreferredSize(new Dimension(WIDTH, HEIGHT)); // Sets the window dimensions.
        setFocusable(true); // Allows keyboard input.
        setBackground(Color.BLACK); // Sets a fallback background colour.

        coins = database.getCoins(); // Loads saved coins.
        character = CharacterDesign.fromId(database.getEquippedCharacter()); // Loads the saved outfit.

        setupControls(); // Connects keyboard shortcuts.
        resetLevelObjects(); // Prepares the starting level.

        timer = new Timer(16, e -> updateGame()); // Creates the game loop.
        timer.start(); // Starts the timer.

    } // Ends the constructor.

    private void setupControls() { // Connects keyboard shortcuts.

        bind(KeyEvent.VK_SPACE, "jump", () -> jump()); // Space jumps.
        bind(KeyEvent.VK_UP, "jumpUp", () -> jump()); // Up arrow jumps.
        bind(KeyEvent.VK_DOWN, "duck", () -> setDuck(true)); // Down arrow ducks.
        bind(KeyEvent.VK_S, "duckS", () -> setDuck(true)); // S also ducks.
        bindRelease(KeyEvent.VK_DOWN, "releaseDuck", () -> setDuck(false)); // Releases ducking.
        bindRelease(KeyEvent.VK_S, "releaseDuckS", () -> setDuck(false)); // Releases ducking.
        bind(KeyEvent.VK_P, "pause", () -> togglePause()); // P pauses or resumes.
        bind(KeyEvent.VK_ENTER, "enter", () -> enterAction()); // Enter continues the story or level.
        bind(KeyEvent.VK_R, "restart", () -> restartCurrentLevel()); // R restarts the current level after death.
        bind(KeyEvent.VK_C, "shop", () -> openCharacterShop()); // C opens the character shop.
        bind(KeyEvent.VK_V, "scores", () -> showLevelScores()); // V shows saved level scores.
        bind(KeyEvent.VK_ESCAPE, "escape", () -> escapeAction()); // Escape pauses or returns to the story.

    } // Ends setupControls.

    private void bind(int key, String name, Runnable action) { // Connects a key press to an action.
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name); // Registers the key.
        getActionMap().put(name, new AbstractAction() { // Creates the keyboard action.
            private static final long serialVersionUID = 1L; // Identifies the action class.
            @Override public void actionPerformed(ActionEvent e) { action.run(); } // Runs the action.
        }); // Ends the action definition.
    } // Ends bind.

    private void bindRelease(int key, String name, Runnable action) { // Connects a key release to an action.
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0, true), name); // Registers key release.
        getActionMap().put(name, new AbstractAction() { // Creates the release action.
            private static final long serialVersionUID = 1L; // Identifies the action class.
            @Override public void actionPerformed(ActionEvent e) { action.run(); } // Runs the action.
        }); // Ends the action definition.
    } // Ends bindRelease.

    private void jump() { // Makes the player jump.
        if (state == State.PLAYING && !jumping && !ducking) { // Allows jumping only during gameplay.
            jumping = true; // Marks the player as airborne.
            verticalSpeed = -15; // Launches the player upwards.
        } // Ends the condition.
    } // Ends jump.

    private void setDuck(boolean value) { // Starts or stops ducking.
        if (state == State.PLAYING && !jumping) ducking = value; // Ducks only while on the ground.
    } // Ends setDuck.

    private void togglePause() { // Pauses or resumes gameplay.
        if (state == State.PLAYING) state = State.PAUSED; // Pauses an active game.
        else if (state == State.PAUSED) state = State.PLAYING; // Resumes a paused game.
        repaint(); // Redraws the screen.
    } // Ends togglePause.

    private void enterAction() { // Handles Enter depending on the current screen.
        if (state == State.STORY) state = State.PLAYING; // Starts the adventure.
        else if (state == State.PAUSED) state = State.PLAYING; // Resumes the game.
        else if (state == State.LEVEL_COMPLETE && level < 5) { // Continues to the next level.
            level++; // Advances to the next level.
            startLevel(); // Resets the level.
        } else if (state == State.LEVEL_COMPLETE && level == 5) { // Ends the final level.
            state = State.VICTORY; // Shows the final victory screen.
        } else if (state == State.VICTORY) { // Allows a new adventure after winning.
            level = 1; // Returns to Level 1.
            lives = 5; // Restores five lives.
            startLevel(); // Starts the first level.
        } // Ends the state checks.
        repaint(); // Redraws the screen.
    } // Ends enterAction.

    private void escapeAction() { // Handles Escape.
        if (state == State.PLAYING) state = State.PAUSED; // Pauses active gameplay.
        else if (state == State.PAUSED) state = State.PLAYING; // Resumes gameplay.
        else if (state == State.STORY) state = State.PLAYING; // Allows skipping the story.
        repaint(); // Redraws the screen.
    } // Ends escapeAction.

    private void startLevel() { // Begins a fresh attempt at the selected level.
        distance = 0; // Resets level progress.
        levelScore = 0; // Resets the run score.
        lives = 5; // Refills all five lives at the beginning of each level.
        speed = 5 + level; // Increases the starting speed each level.
        invulnerability = 0; // Clears temporary damage protection.
        shieldTimer = 0; // Clears the shield timer.
        speedTimer = 0; // Clears the speed boost.
        shieldActive = false; // Deactivates the shield.
        jumping = false; // Resets jumping.
        ducking = false; // Resets ducking.
        playerY = GROUND - PLAYER_H; // Places the player on the ground.
        verticalSpeed = 0; // Resets jump velocity.
        spawnCounter = 0; // Resets obstacle spawning.
        coinCounter = 0; // Resets coin spawning.
        powerCounter = 0; // Resets power-up spawning.
        resetLevelObjects(); // Clears old hazards and collectibles.
        state = State.PLAYING; // Starts the new level.
        repaint(); // Draws the new level.
    } // Ends startLevel.

    private void restartCurrentLevel() { // Restarts the current level after a death.
        if (state == State.GAME_OVER) startLevel(); // Does not reset the player's level number.
        repaint(); // Updates the screen.
    } // Ends restartCurrentLevel.

    private void resetLevelObjects() { // Clears objects from the previous attempt.
        obstacles.clear(); // Removes old obstacles.
        collectibles.clear(); // Removes old coins.
        powerUps.clear(); // Removes old power-ups.
    } // Ends resetLevelObjects.

    private void updateGame() { // Updates game physics and object positions.

        animationFrame++; // Advances scenery animation.

        if (state != State.PLAYING) { // Stops game physics on menus and ending screens.
            repaint(); // Keeps the screen responsive.
            return; // Skips gameplay updates.
        } // Ends the state check.

        distance++; // Advances level progress.
        levelScore = distance + coins * 10 + lives * 100; // Calculates the run score.

        if (jumping) { // Updates the jump.
            playerY += (int) verticalSpeed; // Moves the player vertically.
            verticalSpeed += 0.72; // Applies gravity.
            if (playerY >= GROUND - PLAYER_H) { // Checks whether the player landed.
                playerY = GROUND - PLAYER_H; // Places the player on the ground.
                jumping = false; // Ends the jump.
                verticalSpeed = 0; // Resets jump velocity.
            } // Ends landing check.
        } // Ends jump update.

        if (invulnerability > 0) invulnerability--; // Counts down temporary damage protection.
        if (shieldTimer > 0) shieldTimer--; // Counts down shield duration.
        if (speedTimer > 0) speedTimer--; // Counts down speed boost duration.
        shieldActive = shieldTimer > 0; // Updates the shield state.

        spawnCounter++; // Advances the obstacle timer.
        if (spawnCounter >= Math.max(34, 82 - level * 8)) { // Spawns more frequently in later levels.
            spawnObstacle(); // Creates an obstacle.
            spawnCounter = 0; // Resets the obstacle timer.
        } // Ends obstacle spawning.

        coinCounter++; // Advances the coin timer.
        if (coinCounter >= 55) { // Creates coins periodically.
            collectibles.add(new Coin(WIDTH, GROUND - 90 - random.nextInt(90))); // Creates a coin at a random height.
            coinCounter = 0; // Resets the coin timer.
        } // Ends coin spawning.

        powerCounter++; // Advances the power-up timer.
        if (powerCounter >= 240) { // Creates power-ups less frequently.
            powerUps.add(new PowerUp(WIDTH, GROUND - 115, random.nextBoolean())); // Creates a shield or speed boost.
            powerCounter = 0; // Resets the power-up timer.
        } // Ends power-up spawning.

        int movement = speed + (speedTimer > 0 ? 3 : 0); // Calculates the obstacle movement speed.

        Iterator<Obstacle> obstacleIterator = obstacles.iterator(); // Creates a safe obstacle iterator.
        while (obstacleIterator.hasNext()) { // Visits every obstacle.
            Obstacle o = obstacleIterator.next(); // Gets the next obstacle.
            o.x -= movement; // Moves the obstacle towards the player.
            if (o.moving) o.y += o.direction * 2; // Moves moving obstacles vertically.
            if (o.moving && (o.y < GROUND - o.height - 115 || o.y > GROUND - o.height)) o.direction *= -1; // Reverses movement at the limits.
            if (o.x + o.width < 0) obstacleIterator.remove(); // Removes obstacles that leave the screen.
        } // Ends obstacle movement.

        Iterator<Coin> coinIterator = collectibles.iterator(); // Creates a safe coin iterator.
        while (coinIterator.hasNext()) { // Visits each coin.
            Coin c = coinIterator.next(); // Gets the next coin.
            c.x -= movement; // Moves the coin towards the player.
            if (c.x + c.size < 0) coinIterator.remove(); // Removes coins that leave the screen.
        } // Ends coin movement.

        Iterator<PowerUp> powerIterator = powerUps.iterator(); // Creates a safe power-up iterator.
        while (powerIterator.hasNext()) { // Visits each power-up.
            PowerUp p = powerIterator.next(); // Gets the next power-up.
            p.x -= movement; // Moves the power-up towards the player.
            if (p.x + p.size < 0) powerIterator.remove(); // Removes power-ups that leave the screen.
        } // Ends power-up movement.

        checkCollisions(); // Checks for collisions and rewards.

        if (lives <= 0 && state == State.PLAYING) { // Checks whether all lives were lost.
            saveCurrentScore(false); // Saves the best score without completing the level.
            state = State.GAME_OVER; // Displays the retry screen.
        } // Ends game-over check.

        if (distance >= LEVEL_LENGTH && state == State.PLAYING) { // Checks whether the finish line was reached.
            saveCurrentScore(true); // Saves the score and completion status.
            state = State.LEVEL_COMPLETE; // Displays the level ending.
        } // Ends finish-line check.

        repaint(); // Redraws the updated game.

    } // Ends updateGame.

    private void spawnObstacle() { // Creates obstacles appropriate to the current level.

        int type = random.nextInt(Math.min(level + 1, 5)); // Unlocks more obstacle types as levels increase.

        if (type == 0) { // Creates a standard obstacle.
            obstacles.add(new Obstacle(WIDTH, GROUND - 43, 43, 43, 0, false, false)); // Adds a block to jump over.
        } else if (type == 1) { // Creates spikes.
            obstacles.add(new Obstacle(WIDTH, GROUND - 28, 55, 28, 1, false, false)); // Adds triangular spikes.
        } else if (type == 2) { // Creates a moving obstacle.
            obstacles.add(new Obstacle(WIDTH, GROUND - 95, 48, 40, 2, true, false)); // Adds a moving hazard.
        } else if (type == 3) { // Creates a tall obstacle.
            obstacles.add(new Obstacle(WIDTH, GROUND - 110, 45, 110, 3, false, false)); // Adds a tall block.
        } else { // Creates a low overhead hazard.
            obstacles.add(new Obstacle(WIDTH, GROUND - 105, 70, 30, 4, false, true)); // Adds a hazard the player must duck under.
        } // Ends obstacle selection.

    } // Ends spawnObstacle.

    private void checkCollisions() { // Checks collisions between the player and game objects.

        int px = PLAYER_X; // Defines the player collision box's horizontal position.
        int py = ducking ? GROUND - 35 : playerY; // Shrinks the player box when ducking.
        int ph = ducking ? 35 : PLAYER_H; // Sets the collision box's height.
        int pw = PLAYER_W; // Sets the collision box's width.

        for (Iterator<Obstacle> it = obstacles.iterator(); it.hasNext();) { // Visits every obstacle.
            Obstacle o = it.next(); // Gets the obstacle.
            int oy = o.overhead ? o.y : o.y; // Uses the obstacle's defined vertical position.
            boolean hit = px + pw > o.x + 4 && px < o.x + o.width - 4 && py + ph > oy + 4 && py < oy + o.height; // Tests overlap.

            if (o.type == 1 && jumping && playerY + PLAYER_H < GROUND - 4) hit = false; // Lets airborne players pass over spikes.
            if (o.overhead && ducking) hit = false; // Lets ducking players pass under overhead hazards.

            if (hit && invulnerability == 0) { // Applies damage only when protection has expired.
                if (shieldActive) { // Checks for an active shield.
                    shieldTimer = 0; // Uses up the shield.
                    shieldActive = false; // Disables the shield.
                } else { // Handles an unshielded collision.
                    lives--; // Removes one life.
                    invulnerability = 65; // Grants a short grace period.
                } // Ends shield handling.
                it.remove(); // Removes the obstacle after impact.
            } // Ends collision response.
        } // Ends obstacle collisions.

        for (Iterator<Coin> it = collectibles.iterator(); it.hasNext();) { // Visits each coin.
            Coin c = it.next(); // Gets the coin.
            if (px < c.x + c.size && px + pw > c.x && py < c.y + c.size && py + ph > c.y) { // Checks overlap.
                coins++; // Increases the in-game balance.
                database.addCoins(1); // Saves the earned coin immediately.
                it.remove(); // Removes the collected coin.
            } // Ends coin collection.
        } // Ends coin collisions.

        for (Iterator<PowerUp> it = powerUps.iterator(); it.hasNext();) { // Visits each power-up.
            PowerUp p = it.next(); // Gets the power-up.
            if (px < p.x + p.size && px + pw > p.x && py < p.y + p.size && py + ph > p.y) { // Checks overlap.
                if (p.shield) shieldTimer = 300; // Activates the shield for about five seconds.
                else speedTimer = 300; // Activates the speed boost for about five seconds.
                it.remove(); // Removes the collected power-up.
            } // Ends power-up collection.
        } // Ends power-up collisions.

    } // Ends checkCollisions.

    private void saveCurrentScore(boolean completed) { // Saves the best score for the current level.
        int finalScore = Math.max(0, distance + coins * 10 + lives * 100); // Calculates a non-negative score.
        database.saveLevelScore(level, finalScore, completed); // Saves the result to SQLite.
    } // Ends saveCurrentScore.

    @Override
    protected void paintComponent(Graphics graphics) { // Draws a new frame.
        super.paintComponent(graphics); // Clears the old frame.
        Graphics2D g = (Graphics2D) graphics.create(); // Creates a drawing context.

        drawBackground(g); // Draws the scenery.
        drawGround(g); // Draws the ground.
        drawObstacles(g); // Draws obstacles.
        drawCoins(g); // Draws coins.
        drawPowerUps(g); // Draws power-ups.
        drawPlayer(g); // Draws the character.
        drawHUD(g); // Draws score, lives, and controls.

        if (state == State.STORY) drawOverlay(g, "PIXELDASH", "A cat is lost! Find it through five dangerous worlds.", "Press ENTER to begin"); // Draws the opening story.
        if (state == State.PAUSED) drawOverlay(g, "PAUSED", "Take a breath before continuing.", "Press P or ENTER to resume"); // Draws pause screen.
        if (state == State.GAME_OVER) drawOverlay(g, "LEVEL " + level + " FAILED", "Your best score has been saved.", "Press R to retry this level"); // Draws retry screen.
        if (state == State.LEVEL_COMPLETE) drawLevelEnding(g); // Draws the current level ending.
        if (state == State.VICTORY) drawOverlay(g, "ADVENTURE COMPLETE!", "You rescued the cat and finished all five worlds!", "Press ENTER to play again"); // Draws final victory.

        g.dispose(); // Releases the drawing context.
    } // Ends paintComponent.

    private void drawBackground(Graphics2D g) { // Draws a different sky for every level.

        Color sky; // Stores the sky colour.
        if (level == 1) sky = new Color(130, 205, 245); // Level 1: bright meadow.
        else if (level == 2) sky = new Color(35, 40, 85); // Level 2: dark night.
        else if (level == 3) sky = new Color(245, 155, 90); // Level 3: desert.
        else if (level == 4) sky = new Color(185, 225, 245); // Level 4: icy mountains.
        else sky = new Color(85, 40, 100); // Level 5: volcanic twilight.

        g.setColor(sky); // Selects the sky colour.
        g.fillRect(0, 0, WIDTH, HEIGHT); // Fills the sky.

        if (level == 1) { // Draws the meadow scenery.
            g.setColor(new Color(85, 180, 90)); // Selects green hills.
            g.fillOval(60, 320, 400, 180); // Draws the first hill.
            g.fillOval(450, 300, 500, 200); // Draws the second hill.
            g.setColor(Color.YELLOW); // Selects sunlight.
            g.fillOval(800, 55, 70, 70); // Draws the sun.
        } else if (level == 2) { // Draws the night scenery.
            g.setColor(new Color(245, 245, 200)); // Selects moon colour.
            g.fillOval(790, 55, 65, 65); // Draws the moon.
            g.setColor(Color.WHITE); // Selects star colour.
            for (int i = 0; i < 24; i++) g.fillOval((i * 83 + 20) % WIDTH, 35 + (i * 31) % 210, 3, 3); // Draws stars.
            g.setColor(new Color(55, 65, 90)); // Selects distant hill colour.
            g.fillOval(100, 330, 450, 160); // Draws night hills.
            g.fillOval(500, 300, 500, 190); // Draws another hill.
        } else if (level == 3) { // Draws desert scenery.
            g.setColor(new Color(220, 125, 60)); // Selects desert dune colour.
            g.fillOval(30, 330, 480, 180); // Draws the first dune.
            g.fillOval(470, 300, 600, 210); // Draws the second dune.
            g.setColor(new Color(110, 75, 50)); // Selects cactus colour.
            g.fillRect(760, 325, 18, 130); // Draws a cactus trunk.
            g.fillRect(735, 360, 25, 12); // Draws a cactus arm.
            g.fillRect(735, 350, 12, 35); // Draws the cactus arm vertically.
        } else if (level == 4) { // Draws icy mountains.
            g.setColor(new Color(115, 165, 195)); // Selects mountain colour.
            int[] x1 = {80, 250, 420}; // Defines the mountain's horizontal points.
            int[] y1 = {400, 110, 400}; // Defines the mountain's vertical points.
            g.fillPolygon(new Polygon(x1, y1, 3)); // Draws the first mountain.
            int[] x2 = {370, 620, 850}; // Defines another mountain.
            int[] y2 = {400, 80, 400}; // Defines its height.
            g.fillPolygon(new Polygon(x2, y2, 3)); // Draws the second mountain.
            g.setColor(Color.WHITE); // Selects snow colour.
            g.fillOval(120, 360, 150, 30); // Adds snow on the distant landscape.
        } else { // Draws volcanic scenery.
            g.setColor(new Color(105, 45, 65)); // Selects dark volcanic mountains.
            int[] x = {0, 180, 350, 550, 760, 1000}; // Defines mountain points.
            int[] y = {390, 210, 390, 160, 390, 230}; // Defines mountain heights.
            g.fillPolygon(new Polygon(x, y, x.length)); // Draws the mountain range.
            g.setColor(new Color(255, 100, 30)); // Selects lava colour.
            g.fillOval(500, 330, 180, 100); // Draws a distant lava glow.
        } // Ends scenery selection.

    } // Ends drawBackground.

    private void drawGround(Graphics2D g) { // Draws the floor for the current world.
        if (level == 1) g.setColor(new Color(65, 155, 65)); // Meadow grass.
        else if (level == 2) g.setColor(new Color(55, 60, 80)); // Night path.
        else if (level == 3) g.setColor(new Color(200, 145, 65)); // Desert sand.
        else if (level == 4) g.setColor(new Color(210, 240, 250)); // Icy floor.
        else g.setColor(new Color(85, 45, 50)); // Volcanic ground.
        g.fillRect(0, GROUND, WIDTH, HEIGHT - GROUND); // Fills the floor.
        g.setColor(Color.BLACK); // Selects the boundary colour.
        g.drawLine(0, GROUND, WIDTH, GROUND); // Marks the top of the ground.
    } // Ends drawGround.

    private void drawPlayer(Graphics2D g) { // Draws the character with distinct body parts.

        if (invulnerability > 0 && animationFrame % 8 < 4) return; // Flashes after taking damage.

        int h = ducking ? 35 : PLAYER_H; // Shortens the body while ducking.
        int y = ducking ? GROUND - h : playerY; // Adjusts the body position.
        int head = 24; // Defines the head size.

        if (shieldActive) { // Draws a protective ring.
            g.setColor(new Color(70, 225, 255)); // Selects the shield colour.
            g.drawOval(PLAYER_X - 10, y - 10, PLAYER_W + 20, h + 20); // Draws the shield.
        } // Ends shield drawing.

        g.setColor(character.getOutfitColor()); // Selects the purchased outfit colour.
        g.fillRoundRect(PLAYER_X + 6, y + head - 2, 30, h - head + 2, 8, 8); // Draws the torso.

        g.setColor(new Color(235, 185, 145)); // Selects skin colour.
        g.fillOval(PLAYER_X + 8, y, head, head); // Draws the head.

        g.setColor(character.getOutfitColor()); // Selects the matching leg colour.
        if (!ducking) { // Draws separate legs when standing.
            g.fillRect(PLAYER_X + 7, y + 51, 11, 15); // Draws the left leg.
            g.fillRect(PLAYER_X + 24, y + 51, 11, 15); // Draws the right leg.
        } // Ends leg drawing.

        g.setColor(new Color(45, 35, 30)); // Selects hair colour.
        g.fillArc(PLAYER_X + 7, y - 2, 23, 15, 0, 180); // Draws hair.
        g.setColor(Color.WHITE); // Selects eye colour.
        g.fillOval(PLAYER_X + 23, y + 8, 6, 6); // Draws the eye.
        g.setColor(Color.BLACK); // Selects pupil colour.
        g.fillOval(PLAYER_X + 26, y + 10, 3, 3); // Draws the pupil.

        g.setColor(character.getAccentColor()); // Selects the scarf colour.
        g.fillRect(PLAYER_X + 9, y + 24, 26, 6); // Draws the scarf.
        g.fillRect(PLAYER_X + 27, y + 27, 6, 12); // Draws the hanging scarf.

        g.setColor(Color.BLACK); // Selects boot colour.
        if (!ducking) { // Draws boots while standing.
            g.fillRect(PLAYER_X + 4, y + 63, 16, 5); // Draws the left boot.
            g.fillRect(PLAYER_X + 22, y + 63, 16, 5); // Draws the right boot.
        } // Ends boot drawing.

    } // Ends drawPlayer.

    private void drawObstacles(Graphics2D g) { // Draws all hazards.

        for (Obstacle o : obstacles) { // Visits every obstacle.

            if (o.type == 1) { // Draws spikes as triangles.
                g.setColor(new Color(220, 40, 40)); // Selects spike colour.
                int[] x = {o.x, o.x + o.width / 2, o.x + o.width}; // Defines triangle corners.
                int[] y = {GROUND, GROUND - o.height, GROUND}; // Defines triangle heights.
                g.fillPolygon(new Polygon(x, y, 3)); // Draws the spikes.
            } else if (o.type == 2) { // Draws moving hazards.
                g.setColor(new Color(180, 55, 200)); // Selects moving hazard colour.
                g.fillOval(o.x, o.y, o.width, o.height); // Draws the moving orb.
            } else if (o.overhead) { // Draws hazards that require ducking.
                g.setColor(new Color(90, 65, 50)); // Selects the overhead hazard colour.
                g.fillRect(o.x, o.y, o.width, o.height); // Draws the overhead bar.
            } else { // Draws regular blocks and tall obstacles.
                g.setColor(level == 4 ? new Color(110, 190, 225) : new Color(130, 65, 35)); // Selects an appropriate colour.
                g.fillRect(o.x, o.y, o.width, o.height); // Draws the block.
                g.setColor(Color.BLACK); // Selects outline colour.
                g.drawRect(o.x, o.y, o.width, o.height); // Draws the outline.
            } // Ends obstacle drawing.

        } // Ends obstacle loop.

    } // Ends drawObstacles.

    private void drawCoins(Graphics2D g) { // Draws collectible coins.
        for (Coin c : collectibles) { // Visits every coin.
            g.setColor(new Color(255, 205, 25)); // Selects gold colour.
            g.fillOval(c.x, c.y, c.size, c.size); // Draws the coin.
            g.setColor(new Color(210, 140, 0)); // Selects the outline colour.
            g.drawOval(c.x, c.y, c.size, c.size); // Draws the coin outline.
        } // Ends the coin loop.
    } // Ends drawCoins.

    private void drawPowerUps(Graphics2D g) { // Draws collectible power-ups.
        for (PowerUp p : powerUps) { // Visits every power-up.
            g.setColor(p.shield ? Color.CYAN : Color.ORANGE); // Selects shield or speed colour.
            g.fillRoundRect(p.x, p.y, p.size, p.size, 8, 8); // Draws the power-up.
            g.setColor(Color.BLACK); // Selects text colour.
            g.drawString(p.shield ? "S" : "F", p.x + 9, p.y + 21); // Labels the power-up.
        } // Ends the power-up loop.
    } // Ends drawPowerUps.

    private void drawHUD(Graphics2D g) { // Draws the player information.

        g.setColor(Color.BLACK); // Selects HUD text colour.
        g.setFont(new Font("Arial", Font.BOLD, 17)); // Sets HUD font.
        g.drawString("LEVEL " + level + " / 5", 18, 27); // Shows the level.
        g.drawString("LIVES: " + lives, 18, 51); // Shows remaining lives.
        g.drawString("COINS: " + coins, 18, 75); // Shows the saved balance.
        g.drawString("SCORE: " + levelScore, 18, 99); // Shows the current score.
        g.drawString("BEST: " + database.getBestScore(level), 18, 123); // Shows the saved best score.
        g.drawString("PROGRESS: " + Math.min(100, distance * 100 / LEVEL_LENGTH) + "%", 780, 27); // Shows level progress.

        g.setFont(new Font("Arial", Font.PLAIN, 13)); // Sets the controls font.
        g.drawString("SPACE/UP: Jump", 780, 50); // Displays jump controls.
        g.drawString("DOWN/S: Duck", 780, 70); // Displays duck controls.
        g.drawString("P: Pause  C: Shop  V: Scores", 710, 90); // Displays menu controls.

    } // Ends drawHUD.

    private void drawOverlay(Graphics2D g, String title, String message, String instruction) { // Draws a menu overlay.

        g.setColor(new Color(0, 0, 0, 205)); // Selects translucent black.
        g.fillRoundRect(175, 185, 650, 230, 22, 22); // Draws the overlay panel.
        g.setColor(Color.WHITE); // Selects white text.
        g.setFont(new Font("Arial", Font.BOLD, 32)); // Sets the heading font.
        centreText(g, title, 250); // Draws the heading.
        g.setFont(new Font("Arial", Font.PLAIN, 18)); // Sets the message font.
        centreText(g, message, 305); // Draws the message.
        g.setFont(new Font("Arial", Font.BOLD, 17)); // Sets the instruction font.
        centreText(g, instruction, 360); // Draws the instruction.

    } // Ends drawOverlay.

    private void drawLevelEnding(Graphics2D g) { // Draws a definite ending for each level.

        String[] themes = {"Meadow", "Moonlit Ruins", "Desert", "Ice Mountains", "Volcanic World"}; // Names each level theme.
        String theme = themes[level - 1]; // Gets the current theme name.
        drawOverlay(g, "LEVEL " + level + " COMPLETE!", "You conquered " + theme + "!", level < 5 ? "Press ENTER for the next world" : "Press ENTER for the final rescue"); // Displays the ending.

        if (level == 5) { // Draws the rescued cat at the final ending.
            g.setColor(new Color(210, 160, 100)); // Selects cat fur colour.
            g.fillOval(455, 415, 38, 30); // Draws the cat's body.
            g.fillOval(478, 405, 27, 27); // Draws the cat's head.
            g.fillPolygon(new Polygon(new int[]{480, 482, 490}, new int[]{410, 395, 410}, 3)); // Draws one ear.
            g.fillPolygon(new Polygon(new int[]{495, 501, 505}, new int[]{410, 395, 413}, 3)); // Draws the other ear.
            g.setColor(Color.BLACK); // Selects the eye colour.
            g.fillOval(490, 414, 3, 3); // Draws a cat eye.
            g.fillOval(500, 414, 3, 3); // Draws the other cat eye.
            g.setColor(Color.WHITE); // Selects the text colour.
            g.drawString("You rescued the cat!", 425, 465); // Announces the rescue.
        } // Ends the cat rescue scene.

    } // Ends drawLevelEnding.

    private void centreText(Graphics2D g, String text, int y) { // Centres text horizontally.
        int x = (WIDTH - g.getFontMetrics().stringWidth(text)) / 2; // Calculates the left position.
        g.drawString(text, x, y); // Draws the centred text.
    } // Ends centreText.

    private void openCharacterShop() { // Opens the character shop.

        CharacterDesign[] designs = CharacterDesign.values(); // Loads all character designs.
        String[] options = new String[designs.length + 1]; // Creates a menu option for each design.

        for (int i = 0; i < designs.length; i++) { // Visits each character.
            CharacterDesign d = designs[i]; // Gets the current design.
            boolean owned = database.ownsCharacter(d.getId()); // Checks ownership.
            boolean equipped = character == d; // Checks whether it is equipped.
            String status = equipped ? "EQUIPPED" : owned ? "OWNED - equip" : d.getPrice() + " coins - buy"; // Creates status text.
            options[i] = d.getDisplayName() + " | " + status; // Creates the menu label.
        } // Ends the menu-building loop.

        options[designs.length] = "Close"; // Adds the close option.

        while (true) { // Allows multiple purchases or outfit changes.
            int choice = JOptionPane.showOptionDialog(this, "Saved coins: " + database.getCoins(), "PixelDash Character Shop", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]); // Shows the shop.
            if (choice < 0 || choice == designs.length) break; // Closes the shop when requested.

            CharacterDesign selected = designs[choice]; // Gets the selected design.

            if (database.ownsCharacter(selected.getId())) { // Handles an already-owned design.
                database.equipCharacter(selected.getId()); // Saves the equipped design.
                character = selected; // Updates the player appearance.
            } else { // Handles a locked design.
                int answer = JOptionPane.showConfirmDialog(this, "Buy " + selected.getDisplayName() + " for " + selected.getPrice() + " coins?", "Confirm Purchase", JOptionPane.YES_NO_OPTION); // Asks for confirmation.
                if (answer == JOptionPane.YES_OPTION && database.buyCharacter(selected.getId(), selected.getPrice())) { // Purchases the design.
                    database.equipCharacter(selected.getId()); // Equips the new design.
                    character = selected; // Updates the player appearance.
                    JOptionPane.showMessageDialog(this, "Character purchased and equipped!"); // Confirms the purchase.
                } else if (answer == JOptionPane.YES_OPTION) { // Handles insufficient coins.
                    JOptionPane.showMessageDialog(this, "Not enough coins to buy this character."); // Displays the warning.
                } // Ends the purchase result.
            } // Ends ownership handling.

            coins = database.getCoins(); // Refreshes the displayed balance.
            repaint(); // Updates the player drawing.
        } // Ends the shop loop.

    } // Ends openCharacterShop.

    private void showLevelScores() { // Displays saved best scores.
        StringBuilder text = new StringBuilder("PIXELDASH BEST SCORES\n\n"); // Creates the report heading.
        for (int i = 1; i <= 5; i++) { // Visits all five levels.
            text.append("Level ").append(i).append(": ").append(database.getBestScore(i)); // Adds the best score.
            text.append(database.isLevelCompleted(i) ? " points - Completed\n" : " points - Not completed\n"); // Adds completion status.
        } // Ends the score loop.
        JOptionPane.showMessageDialog(this, text.toString(), "Level Scores", JOptionPane.INFORMATION_MESSAGE); // Displays the report.
    } // Ends showLevelScores.

    private static class Obstacle { // Represents a hazard.
        int x, y, width, height, type, direction; // Stores position, dimensions, type, and movement direction.
        boolean moving, overhead; // Identifies moving and overhead hazards.
        Obstacle(int x, int y, int width, int height, int type, boolean moving, boolean overhead) { // Creates a hazard.
            this.x = x; this.y = y; this.width = width; this.height = height; this.type = type; this.moving = moving; this.overhead = overhead; // Saves its properties.
            this.direction = 1; // Starts moving downwards.
        } // Ends the constructor.
    } // Ends Obstacle.

    private static class Coin { // Represents a collectible coin.
        int x, y; // Stores the coin position.
        final int size = 22; // Defines the coin diameter.
        Coin(int x, int y) { this.x = x; this.y = y; } // Creates the coin.
    } // Ends Coin.

    private static class PowerUp { // Represents a shield or speed boost.
        int x, y; // Stores the power-up position.
        final int size = 30; // Defines the power-up size.
        boolean shield; // True means shield; false means speed boost.
        PowerUp(int x, int y, boolean shield) { this.x = x; this.y = y; this.shield = shield; } // Creates the power-up.
    } // Ends PowerUp.

} // Ends GamePanel.