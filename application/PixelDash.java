package application; // Places the class in the same package as the database classes.

import java.awt.BasicStroke; // Draws outlines with custom thickness.
import java.awt.Color; // Provides colours for the game graphics.
import java.awt.Cursor; // Changes the mouse cursor over buttons.
import java.awt.Dimension; // Defines the game panel's dimensions.
import java.awt.Font; // Provides fonts for text.
import java.awt.FontMetrics; // Measures text for centring.
import java.awt.GradientPaint; // Draws smooth colour gradients.
import java.awt.Graphics; // Provides drawing operations.
import java.awt.Graphics2D; // Provides advanced drawing operations.
import java.awt.Point; // Stores the mouse position.
import java.awt.Polygon; // Allows triangular obstacles to be drawn.
import java.awt.Rectangle; // Defines clickable areas.
import java.awt.RenderingHints; // Enables smooth graphics.
import java.awt.Shape; // Stores clipping areas.
import java.awt.event.ActionEvent; // Represents Swing action events.
import java.awt.event.KeyEvent; // Provides keyboard key codes.
import java.awt.event.MouseAdapter; // Handles mouse clicks.
import java.awt.event.MouseEvent; // Represents mouse events.
import java.awt.event.MouseMotionAdapter; // Handles mouse movement.
import java.awt.geom.AffineTransform; // Scales and moves drawings.
import java.util.ArrayList; // Stores obstacles, coins and power-ups.
import java.util.Iterator; // Allows objects to be safely removed from lists.
import java.util.Random; // Generates random obstacle patterns.
import javax.swing.AbstractAction; // Connects keyboard shortcuts to actions.
import javax.swing.JPanel; // Provides the surface on which the game is drawn.
import javax.swing.KeyStroke; // Represents a keyboard shortcut.
import javax.swing.Timer; // Updates the game repeatedly.

/**
 * PixelDash: five worlds, a saved coin balance, an in-game character shop and a level select screen.
 * Everything is saved to SQLite through GameDatabase.
 * Menus work with the keyboard (arrows, ENTER, ESC) and the mouse.
 */
public class PixelDash extends JPanel { // Defines the main game panel.

    private static final long serialVersionUID = 1L; // Identifies this serializable Swing class.

    private static final int WIDTH = 1000; // Sets the game width in pixels.
    private static final int HEIGHT = 600; // Sets the game height in pixels.
    private static final int GROUND = 475; // Sets the vertical position of the ground.
    private static final int LEVEL_LENGTH = 2000; // Sets the distance needed to finish a level.

    private static final Font F_TITLE = new Font("Arial", Font.BOLD, 72); // Font for the game title.
    private static final Font F_HEAD = new Font("Arial", Font.BOLD, 42); // Font for screen headings.
    private static final Font F_BUTTON = new Font("Arial", Font.BOLD, 22); // Font for buttons.
    private static final Font F_SUB = new Font("Arial", Font.PLAIN, 18); // Font for subtitles.
    private static final Font F_BOLD = new Font("Arial", Font.BOLD, 16); // Font for card titles.
    private static final Font F_SMALL = new Font("Arial", Font.PLAIN, 13); // Font for small text.
    private static final Font F_BIG = new Font("Arial", Font.BOLD, 52); // Font for level numbers.
    private static final Font F_HUD = new Font("Arial", Font.BOLD, 18); // Font for the in-game display.

    private final GameDatabase database = new GameDatabase(); // Connects to the saved game data.
    private final Timer timer; // Updates gameplay approximately 60 times per second.
    private final Random random = new Random(); // Generates random game objects.
    private final CharacterDesign[] designs = CharacterDesign.values(); // Stores every character design.

    private enum State { MENU, LEVEL_SELECT, SHOP, HELP, PLAYING, PAUSED, LEVEL_COMPLETE, GAME_OVER, VICTORY } // Defines all game screens.

    private State state = State.MENU; // Starts on the main menu.
    private int level = 1; // Stores the current level number.

    private int selected = 0; // Stores the highlighted button in button menus.
    private int levelCursor = 0; // Stores the highlighted level card.
    private int shopCursor = 0; // Stores the highlighted shop card.
    private int pendingBuy = -1; // Stores the shop card waiting for purchase confirmation.
    private String message = ""; // Stores a temporary on-screen message.
    private int messageTimer = 0; // Counts down how long the message stays visible.
    private int frame = 0; // Counts frames for simple animations.
    private Point mouse = new Point(-1, -1); // Stores the latest mouse position.
    private boolean newBest = false; // Records whether the last finished run set a new best score.

    private int playerX = 130; // Stores the player's horizontal position.
    private int playerY = GROUND - 65; // Stores the player's vertical position.
    private final int playerWidth = 44; // Sets the player's width.
    private final int playerHeight = 65; // Sets the player's standing height.

    private double velocityY = 0; // Stores the player's vertical jumping speed.
    private boolean jumping = false; // Records whether the player is jumping.
    private boolean ducking = false; // Records whether the player is ducking.

    private int lives = 5; // Gives the player five lives per attempt.
    private int coins = 0; // Stores the saved total coin balance.
    private int runCoins = 0; // Stores the coins collected in the current attempt.
    private int distance = 0; // Tracks progress through the current level.

    private int shieldTimer = 0; // Counts down the shield's remaining duration.
    private int speedTimer = 0; // Counts down the speed boost's remaining duration.
    private int invulnerabilityTimer = 0; // Prevents repeated damage immediately after a hit.

    private int obstacleSpawnTimer = 0; // Controls when the next obstacle appears.
    private int coinSpawnTimer = 0; // Controls when the next coins appear.
    private int powerSpawnTimer = 0; // Controls when the next power-up appears.
    private int baseSpeed = 6; // Controls the basic scrolling speed.

    private final int[] best = new int[6]; // Caches the best score for levels 1 to 5.
    private final boolean[] done = new boolean[6]; // Caches which levels are completed.
    private final boolean[] ownedCache; // Caches which characters are owned.
    private CharacterDesign character; // Stores the equipped character design.

    private final ArrayList<Obstacle> obstacles = new ArrayList<>(); // Stores active obstacles.
    private final ArrayList<Coin> collectibleCoins = new ArrayList<>(); // Stores active coins.
    private final ArrayList<PowerUp> powerUps = new ArrayList<>(); // Stores active power-ups.

    public PixelDash() { // Creates and configures the game panel.

        setPreferredSize(new Dimension(WIDTH, HEIGHT)); // Sets the preferred window size.
        setFocusable(true); // Allows the panel to receive keyboard input.

        ownedCache = new boolean[designs.length]; // Prepares the ownership cache.
        character = CharacterDesign.fromId(database.getEquippedCharacter()); // Loads the saved outfit.
        refreshCache(); // Loads coins, scores and ownership.
        level = continueLevel(); // Selects the level the player should continue from.

        System.out.println("Database connected! Coins: " + database.getCoins() + " | Level: " + database.getCurrentLevel()); // Confirms the database connection.

        setupControls(); // Registers keyboard controls.
        setupMouse(); // Registers mouse controls.

        timer = new Timer(16, e -> updateGame()); // Schedules a game update every 16 milliseconds.
        timer.start(); // Starts the repeating game timer.

    } // Ends the constructor.

    // ============================================================
    // DATABASE CACHE
    // ============================================================

    private void refreshCache() { // Reloads saved data so menus do not query the database every frame.
        coins = database.getCoins(); // Loads the coin balance.
        for (int l = 1; l <= 5; l++) { // Visits each level.
            best[l] = database.getBestScore(l); // Loads the best score.
            done[l] = database.isLevelCompleted(l); // Loads the completion status.
        } // Ends the level loop.
        for (int i = 0; i < designs.length; i++) { // Visits each character.
            ownedCache[i] = database.ownsCharacter(designs[i].getId()); // Loads ownership.
        } // Ends the character loop.
    } // Ends refreshCache.

    private int continueLevel() { // Finds the next level the player should play.
        int highest = 0; // Tracks the highest completed level.
        for (int l = 1; l <= 5; l++) if (done[l]) highest = l; // Finds the highest completed level.
        return Math.min(5, highest + 1); // Returns the next level, capped at 5.
    } // Ends continueLevel.

    private boolean unlocked(int l) { // Checks whether a level is available.
        return l == 1 || done[l - 1]; // Level 1 is open; others need the previous level completed.
    } // Ends unlocked.

    // ============================================================
    // SCREEN NAVIGATION
    // ============================================================

    private void setState(State newState) { // Switches screens and resets the highlight.
        state = newState; // Changes the screen.
        selected = 0; // Highlights the first button.
        pendingBuy = -1; // Cancels any pending purchase.
        repaint(); // Redraws the screen.
    } // Ends setState.

    private void goMenu() { // Opens the main menu.
        refreshCache(); // Updates saved data.
        level = continueLevel(); // Updates the continue level.
        setState(State.MENU); // Shows the menu.
    } // Ends goMenu.

    private void goLevelSelect() { // Opens the level select screen.
        refreshCache(); // Updates saved data.
        levelCursor = continueLevel() - 1; // Highlights the continue level.
        setState(State.LEVEL_SELECT); // Shows the level select screen.
    } // Ends goLevelSelect.

    private void goShop() { // Opens the character shop.
        refreshCache(); // Updates saved data.
        shopCursor = 0; // Defaults to the first card.
        for (int i = 0; i < designs.length; i++) if (designs[i] == character) shopCursor = i; // Highlights the equipped outfit.
        setState(State.SHOP); // Shows the shop.
    } // Ends goShop.

    private boolean isButtonMenu() { // Identifies screens that use a vertical list of buttons.
        return state == State.MENU || state == State.PAUSED || state == State.LEVEL_COMPLETE || state == State.GAME_OVER || state == State.VICTORY; // Returns true for button screens.
    } // Ends isButtonMenu.

    private String[] menuItems() { // Returns the button labels for the current screen.
        switch (state) { // Checks the current screen.
            case MENU: return new String[]{"CONTINUE  (LEVEL " + continueLevel() + ")", "LEVEL SELECT", "CHARACTER SHOP", "HOW TO PLAY"}; // Main menu buttons.
            case PAUSED: return new String[]{"RESUME", "RESTART LEVEL", "MAIN MENU"}; // Pause menu buttons.
            case LEVEL_COMPLETE: return new String[]{level < 5 ? "NEXT LEVEL" : "FINAL ENDING", "REPLAY LEVEL", "MAIN MENU"}; // Level complete buttons.
            case GAME_OVER: return new String[]{"RETRY LEVEL", "LEVEL SELECT", "MAIN MENU"}; // Game over buttons.
            case VICTORY: return new String[]{"PLAY AGAIN", "MAIN MENU"}; // Victory buttons.
            default: return new String[0]; // Other screens have no button list.
        } // Ends the switch.
    } // Ends menuItems.

    private int menuStartY() { // Returns the top of the button list for the current screen.
        switch (state) { // Checks the current screen.
            case MENU: return 215; // Main menu position.
            case PAUSED: return 262; // Pause menu position.
            case LEVEL_COMPLETE: return 345; // Level complete position.
            case GAME_OVER: return 300; // Game over position.
            case VICTORY: return 420; // Victory position.
            default: return 300; // Fallback position.
        } // Ends the switch.
    } // Ends menuStartY.

    private Rectangle buttonRect(int i) { // Calculates a button's clickable area.
        return new Rectangle((WIDTH - 360) / 2, menuStartY() + i * 66, 360, 52); // Stacks buttons vertically.
    } // Ends buttonRect.

    private Rectangle backRect() { // Calculates the BACK button's area.
        return new Rectangle((WIDTH - 200) / 2, 548, 200, 40); // Places it at the bottom centre.
    } // Ends backRect.

    private Rectangle levelCardRect(int i) { // Calculates a level card's area.
        return new Rectangle(40 + i * 187, 150, 172, 300); // Places five cards in a row.
    } // Ends levelCardRect.

    private int shopCols() { // Calculates how many shop cards fit in one row.
        return Math.max(1, Math.min(designs.length, 4)); // Uses up to four columns.
    } // Ends shopCols.

    private Rectangle shopCardRect(int i) { // Calculates a shop card's area.
        int cols = shopCols(); // Gets the column count.
        int rows = (designs.length + cols - 1) / cols; // Gets the row count.
        int h = Math.min(190, (390 - (rows - 1) * 14) / Math.max(1, rows)); // Shrinks cards if many designs exist.
        int w = 200; // Sets the card width.
        int totalW = cols * w + (cols - 1) * 16; // Calculates the width of a full row.
        int startX = (WIDTH - totalW) / 2; // Centres the row.
        int col = i % cols; // Gets the card's column.
        int row = i / cols; // Gets the card's row.
        int y = 118 + row * (h + 14) + (rows == 1 ? 40 : 0); // Calculates the vertical position.
        return new Rectangle(startX + col * (w + 16), y, w, h); // Returns the card area.
    } // Ends shopCardRect.

    // ============================================================
    // MENU ACTIONS
    // ============================================================

    private void activate(int i) { // Runs the action for the chosen button.
        switch (state) { // Checks the current screen.
            case MENU: // Main menu.
                if (i == 0) startLevel(continueLevel()); // Continues the adventure.
                else if (i == 1) goLevelSelect(); // Opens level select.
                else if (i == 2) goShop(); // Opens the shop.
                else setState(State.HELP); // Opens the help screen.
                break;
            case PAUSED: // Pause menu.
                if (i == 0) setState(State.PLAYING); // Resumes the game.
                else if (i == 1) startLevel(level); // Restarts the level.
                else goMenu(); // Returns to the menu.
                break;
            case LEVEL_COMPLETE: // Level complete menu.
                if (i == 0) { // First button.
                    if (level < 5) startLevel(level + 1); // Starts the next level.
                    else setState(State.VICTORY); // Shows the final ending.
                } else if (i == 1) startLevel(level); // Replays the level.
                else goMenu(); // Returns to the menu.
                break;
            case GAME_OVER: // Game over menu.
                if (i == 0) startLevel(level); // Retries the level.
                else if (i == 1) goLevelSelect(); // Opens level select.
                else goMenu(); // Returns to the menu.
                break;
            case VICTORY: // Victory menu.
                if (i == 0) startLevel(1); // Plays again from Level 1.
                else goMenu(); // Returns to the menu.
                break;
            default: break; // Other screens do nothing.
        } // Ends the switch.
    } // Ends activate.

    private void tryStartLevel(int l) { // Starts a level if it is unlocked.
        if (unlocked(l)) startLevel(l); // Starts the level.
        else showMessage("Level " + l + " is locked - complete Level " + (l - 1) + " first."); // Explains why it is locked.
    } // Ends tryStartLevel.

    private void showMessage(String text) { // Displays a temporary message.
        message = text; // Stores the text.
        messageTimer = 240; // Shows it for about four seconds.
    } // Ends showMessage.

    private void shopAction(int i) { // Buys or equips the highlighted character.

        if (i < 0 || i >= designs.length) return; // Ignores invalid cards.
        CharacterDesign d = designs[i]; // Gets the design.

        if (ownedCache[i]) { // Handles an owned design.
            database.equipCharacter(d.getId()); // Saves the equipped design.
            character = d; // Updates the player appearance.
            pendingBuy = -1; // Clears any pending purchase.
            showMessage(d.getDisplayName() + " equipped!"); // Confirms the change.

        } else if (coins < d.getPrice()) { // Handles an unaffordable design.
            pendingBuy = -1; // Clears any pending purchase.
            showMessage("Not enough coins - you need " + (d.getPrice() - coins) + " more."); // Explains the problem.

        } else if (pendingBuy != i) { // Handles the first press on an affordable design.
            pendingBuy = i; // Waits for confirmation.
            showMessage("Press ENTER or click again to buy " + d.getDisplayName() + " for " + d.getPrice() + " coins."); // Asks for confirmation.

        } else { // Handles the confirming press.
            if (database.buyCharacter(d.getId(), d.getPrice())) { // Attempts the purchase.
                database.equipCharacter(d.getId()); // Equips the new design.
                character = d; // Updates the player appearance.
                showMessage("Purchased and equipped " + d.getDisplayName() + "!"); // Confirms the purchase.
            } else { // Handles a failed purchase.
                showMessage("Purchase failed - not enough coins."); // Reports the failure.
            } // Ends the purchase result.
            pendingBuy = -1; // Clears the pending purchase.
        } // Ends the branches.

        refreshCache(); // Updates the coin balance and ownership.
        repaint(); // Redraws the shop.

    } // Ends shopAction.

    // ============================================================
    // KEYBOARD AND MOUSE CONTROLS
    // ============================================================

    private void bindKey(int keyCode, String name, Runnable action) { // Connects a key press to an action.

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyCode, 0), name); // Registers the shortcut.
        getActionMap().put(name, new AbstractAction() { // Creates the action associated with the shortcut.

            private static final long serialVersionUID = 1L; // Identifies this action class.

            @Override
            public void actionPerformed(ActionEvent e) { // Runs when the key is pressed.
                action.run(); // Performs the requested game action.
            } // Ends the action method.

        }); // Ends the keyboard action.

    } // Ends the bindKey method.

    private void bindRelease(int keyCode, String name, Runnable action) { // Connects a key release to an action.

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyCode, 0, true), name); // Registers the key release.
        getActionMap().put(name, new AbstractAction() { // Creates the release action.

            private static final long serialVersionUID = 1L; // Identifies this action class.

            @Override
            public void actionPerformed(ActionEvent e) { // Runs when the key is released.
                action.run(); // Performs the requested game action.
            } // Ends the action method.

        }); // Ends the release action.

    } // Ends the bindRelease method.

    private void setupControls() { // Registers every game control.

        bindKey(KeyEvent.VK_SPACE, "space", () -> { if (state == State.PLAYING) jump(); else confirm(); }); // SPACE jumps in-game and confirms in menus.
        bindKey(KeyEvent.VK_UP, "up", () -> { if (state == State.PLAYING) jump(); else navigate(0, -1); }); // UP jumps in-game and moves up in menus.
        bindKey(KeyEvent.VK_DOWN, "down", () -> { if (state == State.PLAYING) setDuck(true); else navigate(0, 1); }); // DOWN ducks in-game and moves down in menus.
        bindKey(KeyEvent.VK_S, "duckS", () -> { if (state == State.PLAYING) setDuck(true); else navigate(0, 1); }); // S behaves like DOWN.
        bindKey(KeyEvent.VK_LEFT, "left", () -> navigate(-1, 0)); // LEFT moves left in menus.
        bindKey(KeyEvent.VK_RIGHT, "right", () -> navigate(1, 0)); // RIGHT moves right in menus.
        bindKey(KeyEvent.VK_ENTER, "enter", this::confirm); // ENTER confirms the highlighted choice.
        bindKey(KeyEvent.VK_ESCAPE, "escape", this::back); // ESC goes back or pauses.
        bindKey(KeyEvent.VK_P, "pause", () -> { // P pauses and resumes.
            if (state == State.PLAYING) setState(State.PAUSED); // Pauses active gameplay.
            else if (state == State.PAUSED) setState(State.PLAYING); // Resumes paused gameplay.
        }); // Ends the pause action.
        bindKey(KeyEvent.VK_R, "retry", () -> { if (state == State.GAME_OVER) startLevel(level); }); // R retries after a game over.

        bindRelease(KeyEvent.VK_DOWN, "releaseDown", () -> ducking = false); // Stops ducking when DOWN is released.
        bindRelease(KeyEvent.VK_S, "releaseS", () -> ducking = false); // Stops ducking when S is released.

    } // Ends the control setup method.

    private void navigate(int dx, int dy) { // Moves the highlight in menus.

        if (isButtonMenu()) { // Handles button lists.
            int n = menuItems().length; // Counts the buttons.
            if (dy != 0 && n > 0) selected = (selected + dy + n) % n; // Moves up or down with wrap-around.

        } else if (state == State.LEVEL_SELECT) { // Handles the level cards.
            levelCursor = Math.max(0, Math.min(4, levelCursor + dx)); // Moves left or right.

        } else if (state == State.SHOP && designs.length > 0) { // Handles the shop cards.
            int target = shopCursor + dx + dy * shopCols(); // Calculates the new card.
            if (target >= 0 && target < designs.length) { // Checks the card exists.
                shopCursor = target; // Moves the highlight.
                pendingBuy = -1; // Cancels any pending purchase.
            } // Ends the existence check.
        } // Ends the screen checks.

        repaint(); // Redraws the screen.

    } // Ends navigate.

    private void confirm() { // Handles ENTER or SPACE in menus.
        if (isButtonMenu()) activate(selected); // Presses the highlighted button.
        else if (state == State.LEVEL_SELECT) tryStartLevel(levelCursor + 1); // Starts the highlighted level.
        else if (state == State.SHOP) shopAction(shopCursor); // Buys or equips the highlighted character.
        else if (state == State.HELP) goMenu(); // Leaves the help screen.
    } // Ends confirm.

    private void back() { // Handles ESC.
        switch (state) { // Checks the current screen.
            case PLAYING: setState(State.PAUSED); break; // Pauses the game.
            case PAUSED: setState(State.PLAYING); break; // Resumes the game.
            case LEVEL_SELECT: case SHOP: case HELP: goMenu(); break; // Returns to the menu.
            default: break; // Other screens ignore ESC.
        } // Ends the switch.
    } // Ends back.

    private void setupMouse() { // Registers mouse controls.

        addMouseMotionListener(new MouseMotionAdapter() { // Tracks the mouse position.
            @Override
            public void mouseMoved(MouseEvent e) { // Runs when the mouse moves.
                mouse = e.getPoint(); // Stores the position.
                handleHover(e.getPoint()); // Highlights whatever is under the mouse.
            } // Ends mouseMoved.
        }); // Ends the motion listener.

        addMouseListener(new MouseAdapter() { // Detects clicks.
            @Override
            public void mousePressed(MouseEvent e) { // Runs when a button is pressed.
                requestFocusInWindow(); // Makes sure the game has keyboard focus.
                handleClick(e.getPoint()); // Performs the clicked action.
            } // Ends mousePressed.
        }); // Ends the click listener.

    } // Ends setupMouse.

    private void handleHover(Point p) { // Highlights the item under the mouse.

        boolean over = false; // Tracks whether the mouse is over something clickable.

        if (isButtonMenu()) { // Handles button lists.
            for (int i = 0; i < menuItems().length; i++) { // Visits each button.
                if (buttonRect(i).contains(p)) { selected = i; over = true; } // Highlights the hovered button.
            } // Ends the loop.

        } else if (state == State.LEVEL_SELECT) { // Handles level cards.
            for (int i = 0; i < 5; i++) { // Visits each card.
                if (levelCardRect(i).contains(p)) { levelCursor = i; over = true; } // Highlights the hovered card.
            } // Ends the loop.
            if (backRect().contains(p)) over = true; // Makes BACK clickable.

        } else if (state == State.SHOP) { // Handles shop cards.
            for (int i = 0; i < designs.length; i++) { // Visits each card.
                if (shopCardRect(i).contains(p)) { // Checks the hovered card.
                    if (shopCursor != i) { shopCursor = i; pendingBuy = -1; } // Moves the highlight and cancels a pending purchase.
                    over = true; // Marks the card as clickable.
                } // Ends the hover check.
            } // Ends the loop.
            if (backRect().contains(p)) over = true; // Makes BACK clickable.

        } else if (state == State.HELP) { // Handles the help screen.
            if (backRect().contains(p)) over = true; // Makes BACK clickable.
        } // Ends the screen checks.

        setCursor(Cursor.getPredefinedCursor(over ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR)); // Shows a hand over clickable items.

    } // Ends handleHover.

    private void handleClick(Point p) { // Performs the action under the mouse.

        if (isButtonMenu()) { // Handles button lists.
            for (int i = 0; i < menuItems().length; i++) { // Visits each button.
                if (buttonRect(i).contains(p)) { activate(i); return; } // Presses the clicked button.
            } // Ends the loop.

        } else if (state == State.LEVEL_SELECT) { // Handles level cards.
            for (int i = 0; i < 5; i++) { // Visits each card.
                if (levelCardRect(i).contains(p)) { levelCursor = i; tryStartLevel(i + 1); return; } // Starts the clicked level.
            } // Ends the loop.
            if (backRect().contains(p)) goMenu(); // Returns to the menu.

        } else if (state == State.SHOP) { // Handles shop cards.
            for (int i = 0; i < designs.length; i++) { // Visits each card.
                if (shopCardRect(i).contains(p)) { // Checks the clicked card.
                    if (shopCursor != i) { shopCursor = i; pendingBuy = -1; } // Moves the highlight.
                    shopAction(i); // Buys or equips it.
                    return; // Stops checking.
                } // Ends the click check.
            } // Ends the loop.
            if (backRect().contains(p)) goMenu(); // Returns to the menu.

        } else if (state == State.HELP) { // Handles the help screen.
            if (backRect().contains(p)) goMenu(); // Returns to the menu.
        } // Ends the screen checks.

    } // Ends handleClick.

    private void jump() { // Starts a jump when allowed.

        if (state == State.PLAYING && !jumping && !ducking) { // Checks that jumping is allowed.
            jumping = true; // Marks the player as airborne.
            velocityY = -15; // Launches the player upwards.
        } // Ends the jump condition.

    } // Ends the jump method.

    private void setDuck(boolean value) { // Starts or stops ducking.
        if (state == State.PLAYING && !jumping) ducking = value; // Ducks only while on the ground.
    } // Ends the setDuck method.

    // ============================================================
    // LEVEL MANAGEMENT
    // ============================================================

    private void startLevel(int newLevel) { // Starts (or restarts) the specified level.

        level = Math.max(1, Math.min(5, newLevel)); // Keeps the level number between 1 and 5.
        lives = 5; // Refills all five lives at the beginning of every attempt.
        runCoins = 0; // Resets the coins collected in this attempt.
        distance = 0; // Resets progress for the level.
        newBest = false; // Clears the new best flag.
        baseSpeed = 5 + level; // Makes later levels faster.
        resetPlayerPosition(); // Places the player at the starting position.
        clearObjects(); // Removes obstacles and collectibles from the previous attempt.
        resetSpawnTimers(); // Resets obstacle, coin and power-up creation timers.
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)); // Restores the normal cursor.
        setState(State.PLAYING); // Starts active gameplay.

    } // Ends the startLevel method.

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

    private int currentScore() { // Calculates the score for the current attempt.
        return Math.max(0, distance + runCoins * 10 + lives * 100); // Combines distance, coins and lives.
    } // Ends the score method.

    private void saveScore(boolean completed) { // Saves the best score for the current level.
        int score = currentScore(); // Calculates the final score.
        newBest = score > best[level]; // Checks whether it beats the saved best.
        database.saveLevelScore(level, score, completed); // Writes the result to SQLite.
        refreshCache(); // Updates the cached scores and unlocks.
    } // Ends the saveScore method.

    // ============================================================
    // MAIN GAME LOOP
    // ============================================================

    private void updateGame() { // Updates movement, objects, collisions and progress.

        frame++; // Advances menu animations.
        if (messageTimer > 0) messageTimer--; // Counts down the on-screen message.

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
            saveScore(false); // Saves the best score without completing the level.
            setState(State.GAME_OVER); // Shows the retry screen.
            return; // Stops the update.
        } // Ends the game-over condition.

        if (distance >= LEVEL_LENGTH) { // Checks whether the finish has been reached.
            distance = LEVEL_LENGTH; // Caps progress at 100 percent.
            saveScore(true); // Saves the score and marks the level as completed.
            setState(State.LEVEL_COMPLETE); // Shows the level ending.
            return; // Stops the update.
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
    // MOVEMENT AND COLLISIONS
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
            boolean collected = overlaps(playerX, currentPlayerY(), playerWidth, currentPlayerHeight(), coin.x, coin.y, coin.size, coin.size); // Checks whether the player touches it.
            if (collected) { // Runs when a coin is collected.
                coins++; // Increases the displayed coin balance.
                runCoins++; // Increases the coins collected in this attempt.
                database.addCoins(1); // Saves the earned coin immediately.
                iterator.remove(); // Removes the collected coin.
            } // Ends the collection condition.
        } // Ends the coin collision loop.

    } // Ends the coin collision method.

    private void checkPowerUpCollisions() { // Detects collected power-ups.

        Iterator<PowerUp> iterator = powerUps.iterator(); // Creates a power-up iterator.
        while (iterator.hasNext()) { // Checks each power-up.
            PowerUp power = iterator.next(); // Gets the next power-up.
            boolean collected = overlaps(playerX, currentPlayerY(), playerWidth, currentPlayerHeight(), power.x, power.y, power.size, power.size); // Checks for contact.

            if (collected) { // Runs when the player collects a power-up.
                if (power.shield) shieldTimer = 300; // Activates the shield for about five seconds.
                else speedTimer = 300; // Activates the speed boost for about five seconds.
                iterator.remove(); // Removes the collected power-up.
            } // Ends the collection condition.
        } // Ends the power-up collision loop.

    } // Ends the power-up collision method.

    // ============================================================
    // GAMEPLAY BACKGROUNDS
    // ============================================================

    private Color skyColor(int l) { // Returns the sky colour of a level.
        switch (l) { // Checks the level.
            case 1: return new Color(135, 206, 235); // Blue sky for Green Hills.
            case 2: return new Color(220, 155, 105); // Dusty orange sky for Rocky Canyon.
            case 3: return new Color(65, 45, 100); // Purple sky for Enchanted Forest.
            case 4: return new Color(185, 225, 245); // Pale blue sky for Frozen Peaks.
            default: return new Color(95, 30, 35); // Dark red sky for Volcanic Fortress.
        } // Ends the switch.
    } // Ends skyColor.

    private Color groundColor(int l) { // Returns the ground colour of a level.
        switch (l) { // Checks the level.
            case 1: return new Color(70, 155, 65); // Green grass.
            case 2: return new Color(115, 75, 60); // Rocky ground.
            case 3: return new Color(40, 90, 60); // Dark forest ground.
            case 4: return new Color(215, 235, 245); // Snow and ice.
            default: return new Color(65, 40, 45); // Volcanic rock.
        } // Ends the switch.
    } // Ends groundColor.

    private String levelName(int l) { // Returns the name of a level.
        switch (l) { // Checks the level number.
            case 1: return "GREEN HILLS"; // Names Level 1.
            case 2: return "ROCKY CANYON"; // Names Level 2.
            case 3: return "ENCHANTED FOREST"; // Names Level 3.
            case 4: return "FROZEN PEAKS"; // Names Level 4.
            default: return "VOLCANIC FORTRESS"; // Names Level 5.
        } // Ends the level name selection.
    } // Ends levelName.

    private void drawBackground(Graphics2D g) { // Draws the sky and ground for the current level.

        g.setColor(skyColor(level)); // Selects the sky colour.
        g.fillRect(0, 0, WIDTH, HEIGHT); // Paints the sky across the entire window.

        switch (level) { // Selects the scenery for the current world.
            case 1: drawHills(g); break; // Draws green hills.
            case 2: drawCanyon(g); break; // Draws rocky cliffs.
            case 3: drawForest(g); break; // Draws the enchanted forest.
            case 4: drawMountains(g); break; // Draws snowy mountains.
            default: drawVolcano(g); break; // Draws the volcanic landscape.
        } // Ends the scenery selection.

        g.setColor(groundColor(level)); // Selects the ground colour.
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
    // CHARACTER AND OBJECT DRAWING
    // ============================================================

    private void drawPlayer(Graphics2D g) { // Draws the player during gameplay.

        if (invulnerabilityTimer > 0 && (invulnerabilityTimer / 5) % 2 == 0) return; // Makes the player flash briefly after a hit.

        int y = currentPlayerY(); // Gets the player's current vertical position.
        int height = currentPlayerHeight(); // Gets the player's current height.

        if (shieldTimer > 0) { // Checks whether a shield is active.
            g.setColor(Color.CYAN); // Selects cyan for the shield.
            g.drawOval(playerX - 10, y - 8, playerWidth + 20, height + 16); // Draws a protective ring.
        } // Ends the shield drawing condition.

        drawFigure(g, playerX, y, height, character.getOutfitColor(), character.getAccentColor()); // Draws the character with the equipped outfit.

    } // Ends the player drawing method.

    private void drawFigure(Graphics2D g, int px, int y, int height, Color outfit, Color accent) { // Draws the character at any position.

        g.setColor(new Color(65, 40, 25)); // Selects brown for the boots.
        g.fillRoundRect(px + 3, y + height - 9, 17, 12, 5, 5); // Draws the left boot.
        g.fillRoundRect(px + 25, y + height - 9, 17, 12, 5, 5); // Draws the right boot.

        g.setColor(new Color(35, 45, 105)); // Selects dark blue for the trousers.
        g.fillRect(px + 5, y + 38, 14, height - 43); // Draws the left trouser leg.
        g.fillRect(px + 25, y + 38, 14, height - 43); // Draws the right trouser leg.

        g.setColor(outfit); // Selects the outfit colour for the jacket.
        g.fillRoundRect(px + 4, y + 23, 36, 26, 7, 7); // Draws the jacket.
        g.setColor(Color.WHITE); // Selects white for the jacket zipper.
        g.drawLine(px + 22, y + 26, px + 22, y + 46); // Draws the zipper.

        g.setColor(outfit); // Selects the outfit colour for the sleeves.
        g.fillRoundRect(px - 2, y + 26, 10, 21, 5, 5); // Draws the left sleeve.
        g.fillRoundRect(px + 36, y + 26, 10, 21, 5, 5); // Draws the right sleeve.

        g.setColor(new Color(245, 190, 135)); // Selects skin colour for the hands.
        g.fillOval(px - 3, y + 41, 11, 10); // Draws the left hand.
        g.fillOval(px + 36, y + 41, 11, 10); // Draws the right hand.
        g.fillRect(px + 15, y + 19, 15, 10); // Draws the neck.
        g.fillOval(px + 7, y + 3, 32, 29); // Draws the face.

        g.setColor(new Color(75, 40, 20)); // Selects brown for the hair.
        g.fillOval(px + 6, y, 33, 15); // Draws the top of the hair.
        g.fillRect(px + 7, y + 5, 6, 12); // Draws the left side of the hair.
        g.fillRect(px + 32, y + 4, 7, 10); // Draws the right side of the hair.

        g.setColor(Color.BLACK); // Selects black for the eyes.
        g.fillOval(px + 15, y + 15, 4, 5); // Draws the left eye.
        g.fillOval(px + 29, y + 15, 4, 5); // Draws the right eye.

        g.setColor(new Color(210, 130, 100)); // Selects a skin shade for the nose.
        g.fillOval(px + 23, y + 19, 4, 4); // Draws the nose.

        g.setColor(accent); // Selects the accent colour for the scarf.
        g.fillRect(px + 12, y + 25, 23, 6); // Draws the scarf around the neck.
        g.fillPolygon(new Polygon(new int[]{px + 28, px + 37, px + 31}, new int[]{y + 28, y + 45, y + 31}, 3)); // Draws the trailing scarf end.

        g.setColor(Color.BLACK); // Selects black for the jacket outline.
        g.drawRoundRect(px + 4, y + 23, 36, 26, 7, 7); // Outlines the jacket.

    } // Ends the figure drawing method.

    private void drawFigureScaled(Graphics2D g, int cx, int top, double scale, Color outfit, Color accent) { // Draws the character scaled around a centre point.
        AffineTransform old = g.getTransform(); // Remembers the current transform.
        g.translate(cx, top); // Moves the origin to the figure's top centre.
        g.scale(scale, scale); // Scales the drawing.
        drawFigure(g, -22, 0, playerHeight, outfit, accent); // Draws the standing figure centred on the origin.
        g.setTransform(old); // Restores the original transform.
    } // Ends drawFigureScaled.

    private void drawObstacles(Graphics2D g) { // Draws every active obstacle.

        for (Obstacle o : obstacles) { // Visits each obstacle.

            if (o.spike) { // Draws triangular spikes.
                Color c = level == 4 ? new Color(130, 220, 255) : level == 5 ? new Color(255, 90, 25) : level == 3 ? new Color(90, 210, 100) : new Color(210, 35, 45); // Chooses a colour for the current world.
                g.setColor(c); // Applies the selected spike colour.
                g.fillPolygon(new Polygon(new int[]{o.x, o.x + o.width / 2, o.x + o.width}, new int[]{o.y + o.height, o.y, o.y + o.height}, 3)); // Draws the spike triangle.

            } else if (o.overhead) { // Draws branches or beams that must be ducked under.
                g.setColor(level == 3 ? new Color(65, 100, 45) : new Color(110, 100, 105)); // Chooses a branch or beam colour.
                g.fillRoundRect(o.x, o.y, o.width, o.height, 8, 8); // Draws the overhead obstacle.

            } else if (o.moving) { // Draws vertically moving hazards.
                g.setColor(level == 5 ? new Color(245, 90, 30) : level == 4 ? new Color(145, 225, 255) : level == 3 ? new Color(175, 90, 220) : new Color(125, 105, 95)); // Chooses the moving hazard colour.
                if (level == 5) g.fillOval(o.x, o.y, o.width, o.height); // Draws a round fireball in Level 5.
                else g.fillRoundRect(o.x, o.y, o.width, o.height, 10, 10); // Draws moving rocks or ice blocks.

            } else { // Draws stationary barriers.
                g.setColor(level == 1 ? new Color(125, 75, 35) : level == 2 ? new Color(100, 75, 65) : level == 3 ? new Color(75, 110, 50) : level == 4 ? new Color(130, 205, 235) : new Color(60, 45, 50)); // Chooses the barrier colour.
                g.fillRect(o.x, o.y, o.width, o.height); // Draws the barrier.
                g.setColor(Color.BLACK); // Selects black for the outline.
                g.drawRect(o.x, o.y, o.width, o.height); // Outlines the barrier.
            } // Ends the obstacle drawing selection.

        } // Ends the obstacle loop.

    } // Ends the obstacle drawing method.

    private void drawCoins(Graphics2D g) { // Draws all collectible coins.
        for (Coin c : collectibleCoins) drawCoinIcon(g, c.x, c.y, c.size); // Draws each coin.
    } // Ends the coin drawing method.

    private void drawCoinIcon(Graphics2D g, int x, int y, int size) { // Draws one gold coin.
        g.setColor(new Color(255, 215, 0)); // Selects gold for the coin.
        g.fillOval(x, y, size, size); // Draws the coin.
        g.setColor(new Color(210, 145, 0)); // Selects a darker gold outline.
        g.drawOval(x, y, size, size); // Outlines the coin.
        g.drawOval(x + size / 4, y + size / 4, size / 2, size / 2); // Draws the inner ring.
    } // Ends drawCoinIcon.

    private void drawPowerUps(Graphics2D g) { // Draws shields and speed boosts.
        for (PowerUp p : powerUps) { // Visits each power-up.
            g.setColor(p.shield ? Color.CYAN : Color.ORANGE); // Uses cyan for shields and orange for speed boosts.
            g.fillRoundRect(p.x, p.y, p.size, p.size, 8, 8); // Draws the power-up.
            g.setColor(Color.BLACK); // Selects black for the power-up label.
            g.setFont(F_HUD); // Sets the label font.
            g.drawString(p.shield ? "S" : "F", p.x + 9, p.y + 21); // Labels shields S and speed boosts F.
        } // Ends the power-up loop.
    } // Ends the power-up drawing method.

    private void drawHUD(Graphics2D g) { // Draws lives, coins and level progress.

        g.setColor(Color.BLACK); // Selects black for HUD text.
        g.setFont(F_HUD); // Sets the main HUD font.
        g.drawString("Level: " + level + " / 5", 20, 30); // Displays the current level.
        g.drawString("Lives: " + lives, 20, 55); // Displays remaining lives.
        g.drawString("Coins: " + coins, 20, 80); // Displays the saved coin balance.
        g.drawString("Score: " + currentScore(), 20, 105); // Displays the current score.
        g.drawString("Best: " + best[level], 20, 130); // Displays the saved best score.

        if (shieldTimer > 0) g.drawString("SHIELD ACTIVE", 20, 155); // Displays an active shield.
        if (speedTimer > 0) g.drawString("SPEED BOOST", 20, 180); // Displays an active speed boost.

        int progress = Math.min(100, distance * 100 / LEVEL_LENGTH); // Converts progress into a percentage.
        g.setColor(Color.WHITE); // Selects white for the progress bar background.
        g.fillRect(790, 40, 170, 14); // Draws the empty progress bar.
        g.setColor(new Color(35, 150, 60)); // Selects green for completed progress.
        g.fillRect(790, 40, progress * 170 / 100, 14); // Draws the filled portion of the bar.
        g.setColor(Color.BLACK); // Selects black for the bar outline.
        g.drawRect(790, 40, 170, 14); // Outlines the progress bar.

        g.setFont(F_SMALL); // Sets the controls font.
        g.drawString("SPACE / UP: Jump", 790, 75); // Displays the jump control.
        g.drawString("DOWN / S: Duck", 790, 94); // Displays the duck control.
        g.drawString("P / ESC: Pause", 790, 113); // Displays the pause control.

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
        g.setFont(F_SMALL); // Sets the label font.
        g.drawString("FINISH", x - 22, y - 10); // Labels the finish line.
    } // Ends the finish marker method.

    private void drawCat(Graphics2D g, int x, int y) { // Draws the rescued cat.
        g.setColor(new Color(245, 160, 70)); // Selects orange for the cat's fur.
        g.fillOval(x + 35, y + 35, 45, 15); // Draws the tail.
        g.fillOval(x + 5, y + 25, 65, 45); // Draws the body.
        g.fillOval(x + 5, y, 48, 43); // Draws the head.
        g.fillPolygon(new Polygon(new int[]{x + 8, x + 12, x + 24}, new int[]{y + 17, y - 4, y + 12}, 3)); // Draws the left ear.
        g.fillPolygon(new Polygon(new int[]{x + 34, x + 47, x + 47}, new int[]{y + 12, y - 4, y + 20}, 3)); // Draws the right ear.
        g.setColor(Color.BLACK); // Selects black for the eyes.
        g.fillOval(x + 17, y + 17, 5, 6); // Draws the left eye.
        g.fillOval(x + 36, y + 17, 5, 6); // Draws the right eye.
        g.setColor(Color.PINK); // Selects pink for the nose.
        g.fillOval(x + 27, y + 26, 7, 5); // Draws the nose.
    } // Ends the cat drawing method.

    // ============================================================
    // MENU DRAWING HELPERS
    // ============================================================

    private void drawCentered(Graphics2D g, String text, int y) { // Draws text centred horizontally.
        FontMetrics fm = g.getFontMetrics(); // Measures the text width.
        int x = (WIDTH - fm.stringWidth(text)) / 2; // Calculates the horizontal centre.
        g.drawString(text, x, y); // Draws the text at the calculated position.
    } // Ends the text centring method.

    private void drawCenteredIn(Graphics2D g, String text, Rectangle r) { // Draws text centred inside a rectangle.
        FontMetrics fm = g.getFontMetrics(); // Measures the text.
        int x = r.x + (r.width - fm.stringWidth(text)) / 2; // Calculates the horizontal position.
        int y = r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent(); // Calculates the vertical position.
        g.drawString(text, x, y); // Draws the text.
    } // Ends drawCenteredIn.

    private void drawPanel(Graphics2D g, int x, int y, int w, int h) { // Draws a dark rounded panel.
        g.setColor(new Color(10, 14, 35, 230)); // Selects a dark translucent colour.
        g.fillRoundRect(x, y, w, h, 24, 24); // Fills the panel.
        g.setColor(new Color(255, 255, 255, 70)); // Selects a soft border colour.
        g.setStroke(new BasicStroke(2f)); // Sets the border thickness.
        g.drawRoundRect(x, y, w, h, 24, 24); // Draws the border.
        g.setStroke(new BasicStroke(1f)); // Restores the default thickness.
    } // Ends drawPanel.

    private void drawButton(Graphics2D g, Rectangle r, String label, boolean sel) { // Draws one menu button.
        if (sel) g.setPaint(new GradientPaint(r.x, r.y, new Color(255, 225, 100), r.x, r.y + r.height, new Color(240, 160, 30))); // Uses a gold gradient when highlighted.
        else g.setColor(new Color(38, 48, 92)); // Uses a dark blue when not highlighted.
        g.fillRoundRect(r.x, r.y, r.width, r.height, 18, 18); // Fills the button.
        g.setColor(sel ? new Color(255, 250, 200) : new Color(120, 140, 210)); // Selects the border colour.
        g.setStroke(new BasicStroke(2f)); // Sets the border thickness.
        g.drawRoundRect(r.x, r.y, r.width, r.height, 18, 18); // Draws the border.
        g.setStroke(new BasicStroke(1f)); // Restores the default thickness.
        g.setFont(F_BUTTON); // Sets the button font.
        g.setColor(sel ? new Color(45, 25, 0) : Color.WHITE); // Selects the text colour.
        drawCenteredIn(g, label, r); // Draws the label.
    } // Ends drawButton.

    private void drawButtons(Graphics2D g) { // Draws the button list of the current screen.
        String[] items = menuItems(); // Gets the labels.
        for (int i = 0; i < items.length; i++) drawButton(g, buttonRect(i), items[i], i == selected); // Draws each button.
    } // Ends drawButtons.

    private void drawBackButton(Graphics2D g) { // Draws the BACK button.
        drawButton(g, backRect(), "BACK", backRect().contains(mouse)); // Highlights it when hovered.
    } // Ends drawBackButton.

    private void drawCoinBadge(Graphics2D g) { // Draws the coin balance in the top-right corner.
        g.setColor(new Color(10, 14, 35, 200)); // Selects a dark background.
        g.fillRoundRect(810, 18, 170, 40, 20, 20); // Draws the badge.
        drawCoinIcon(g, 824, 26, 24); // Draws a coin icon.
        g.setFont(F_BUTTON); // Sets the badge font.
        g.setColor(Color.WHITE); // Selects white text.
        g.drawString(String.valueOf(coins), 858, 47); // Draws the coin total.
    } // Ends drawCoinBadge.

    private void drawFooter(Graphics2D g, int y, String hint) { // Draws a hint or temporary message.
        g.setFont(F_BOLD); // Sets the footer font.
        if (messageTimer > 0) { g.setColor(new Color(255, 230, 120)); drawCentered(g, message, y); } // Shows the message in gold.
        else { g.setColor(new Color(170, 190, 240)); drawCentered(g, hint, y); } // Shows the hint in soft blue.
    } // Ends drawFooter.

    private void drawMenuBackground(Graphics2D g) { // Draws the night-sky backdrop for menu screens.
        g.setPaint(new GradientPaint(0, 0, new Color(18, 22, 60), 0, HEIGHT, new Color(90, 45, 120))); // Creates a purple gradient.
        g.fillRect(0, 0, WIDTH, HEIGHT); // Fills the screen.
        for (int i = 0; i < 60; i++) { // Draws twinkling stars.
            int sx = (i * 137 + 23) % WIDTH; // Calculates the star's x position.
            int sy = (i * 61 + 11) % 360; // Calculates the star's y position.
            int size = ((frame / 20 + i) % 4 == 0) ? 2 : 3; // Makes some stars twinkle.
            g.setColor(new Color(255, 255, 255, 120 + (i * 13) % 100)); // Selects a soft white.
            g.fillOval(sx, sy, size, size); // Draws the star.
        } // Ends the star loop.
        g.setColor(new Color(30, 25, 70)); // Selects a dark hill colour.
        g.fillOval(-120, 470, 620, 260); // Draws the left hill.
        g.fillOval(380, 490, 760, 260); // Draws the right hill.
        g.setColor(new Color(22, 18, 55)); // Selects a darker hill colour.
        g.fillOval(180, 520, 700, 200); // Draws the middle hill.
    } // Ends drawMenuBackground.

    // ============================================================
    // MENU SCREENS
    // ============================================================

    private void drawMainMenu(Graphics2D g) { // Draws the main menu.

        drawMenuBackground(g); // Draws the backdrop.
        drawCoinBadge(g); // Shows the coin balance.

        int bob = (int) (Math.sin(frame * 0.05) * 5); // Makes the title float gently.
        g.setFont(F_TITLE); // Sets the title font.
        g.setColor(new Color(0, 0, 0, 120)); // Selects a shadow colour.
        drawCentered(g, "PIXELDASH", 128 + bob + 4); // Draws the title shadow.
        g.setColor(Color.YELLOW); // Selects yellow for the title.
        drawCentered(g, "PIXELDASH", 124 + bob); // Draws the title.
        g.setFont(F_SUB); // Sets the subtitle font.
        g.setColor(new Color(190, 210, 255)); // Selects a soft blue.
        drawCentered(g, "A little cat is lost. Cross five worlds to find it.", 170); // Draws the subtitle.

        drawButtons(g); // Draws the menu buttons.

        drawFigureScaled(g, 160, 300, 2.2, character.getOutfitColor(), character.getAccentColor()); // Shows the equipped character.
        g.setFont(F_BOLD); // Sets the name font.
        g.setColor(Color.WHITE); // Selects white.
        FontMetrics fm = g.getFontMetrics(); // Measures the name.
        g.drawString(character.getDisplayName(), 160 - fm.stringWidth(character.getDisplayName()) / 2, 475); // Draws the outfit name.

        AffineTransform old = g.getTransform(); // Remembers the transform.
        g.translate(750, 330); // Positions the cat.
        g.scale(1.7, 1.7); // Enlarges the cat.
        drawCat(g, 0, 0); // Draws the cat waiting to be found.
        g.setTransform(old); // Restores the transform.

        drawFooter(g, 560, "UP / DOWN to choose, ENTER to select, or use the mouse"); // Shows the controls hint.

    } // Ends drawMainMenu.

    private void drawLevelSelect(Graphics2D g) { // Draws the level select screen.

        drawMenuBackground(g); // Draws the backdrop.
        drawCoinBadge(g); // Shows the coin balance.
        g.setFont(F_HEAD); // Sets the heading font.
        g.setColor(Color.YELLOW); // Selects yellow.
        drawCentered(g, "SELECT LEVEL", 80); // Draws the heading.
        g.setFont(F_SUB); // Sets the subtitle font.
        g.setColor(new Color(190, 210, 255)); // Selects soft blue.
        drawCentered(g, "Complete a world to unlock the next one.", 112); // Draws the subtitle.

        for (int i = 0; i < 5; i++) drawLevelCard(g, i); // Draws each level card.

        drawFooter(g, 492, "LEFT / RIGHT to choose, ENTER to play, ESC to go back"); // Shows the hint or message.
        drawBackButton(g); // Draws the BACK button.

    } // Ends drawLevelSelect.

    private void drawLevelCard(Graphics2D g, int i) { // Draws one level card.

        int l = i + 1; // Converts the index to a level number.
        boolean open = unlocked(l); // Checks whether the level is available.
        boolean sel = i == levelCursor; // Checks whether the card is highlighted.
        Rectangle r = levelCardRect(i); // Gets the card area.
        int x = r.x; // Stores the left edge.
        int y = r.y + (sel ? -8 : 0); // Lifts the highlighted card.
        int w = r.width; // Stores the width.
        int h = r.height; // Stores the height.

        g.setColor(new Color(28, 36, 78)); // Selects the card colour.
        g.fillRoundRect(x, y, w, h, 20, 20); // Draws the card.

        int sx = x + 8, sy = y + 8, sw = w - 16, sh = 120; // Defines the picture area.
        Shape oldClip = g.getClip(); // Remembers the clip area.
        g.clipRect(sx, sy, sw, sh); // Restricts drawing to the picture.
        g.setColor(skyColor(l)); // Selects the world's sky.
        g.fillRect(sx, sy, sw, sh); // Draws the sky.
        drawMiniScenery(g, l, sx, sy, sw, sh); // Draws a tiny version of the world.
        g.setClip(oldClip); // Restores the clip area.

        g.setFont(F_BIG); // Sets the number font.
        g.setColor(new Color(0, 0, 0, 90)); // Selects a shadow colour.
        FontMetrics fm = g.getFontMetrics(); // Measures the number.
        g.drawString(String.valueOf(l), x + (w - fm.stringWidth(String.valueOf(l))) / 2 + 2, sy + 78 + 2); // Draws the number shadow.
        g.setColor(Color.WHITE); // Selects white.
        g.drawString(String.valueOf(l), x + (w - fm.stringWidth(String.valueOf(l))) / 2, sy + 78); // Draws the number.

        g.setFont(F_BOLD); // Sets the name font.
        g.setColor(Color.WHITE); // Selects white.
        fm = g.getFontMetrics(); // Measures the name.
        String name = levelName(l); // Gets the world name.
        g.drawString(name, x + (w - fm.stringWidth(name)) / 2, y + 160); // Draws the name.

        g.setFont(F_SMALL); // Sets the score font.
        g.setColor(new Color(190, 210, 255)); // Selects soft blue.
        String bestText = "BEST: " + best[l]; // Builds the score text.
        fm = g.getFontMetrics(); // Measures the score text.
        g.drawString(bestText, x + (w - fm.stringWidth(bestText)) / 2, y + 190); // Draws the best score.

        String status = !open ? "LOCKED" : done[l] ? "COMPLETED" : "READY"; // Chooses the status.
        Color statusColor = !open ? new Color(150, 150, 165) : done[l] ? new Color(90, 220, 120) : new Color(255, 215, 80); // Chooses its colour.
        g.setFont(F_BOLD); // Sets the status font.
        fm = g.getFontMetrics(); // Measures the status.
        int pw = fm.stringWidth(status) + 30; // Calculates the pill width.
        int px = x + (w - pw) / 2; // Centres the pill.
        g.setColor(new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(), 50)); // Selects a faint pill colour.
        g.fillRoundRect(px, y + 210, pw, 28, 14, 14); // Draws the pill.
        g.setColor(statusColor); // Selects the status colour.
        g.drawString(status, px + 15, y + 230); // Draws the status.

        g.setFont(F_SMALL); // Sets the hint font.
        g.setColor(open ? new Color(255, 255, 255, 170) : new Color(255, 255, 255, 90)); // Selects the hint colour.
        String action = open ? "Click or ENTER to play" : "Finish Level " + (l - 1) + " first"; // Chooses the hint.
        fm = g.getFontMetrics(); // Measures the hint.
        g.drawString(action, x + (w - fm.stringWidth(action)) / 2, y + 273); // Draws the hint.

        if (done[l]) { // Adds a tick badge to completed levels.
            g.setColor(new Color(60, 190, 95)); // Selects green.
            g.fillOval(sx + sw - 28, sy + 6, 22, 22); // Draws the badge.
            g.setColor(Color.WHITE); // Selects white.
            g.setStroke(new BasicStroke(2.5f)); // Sets the tick thickness.
            g.drawLine(sx + sw - 22, sy + 17, sx + sw - 18, sy + 21); // Draws the first tick stroke.
            g.drawLine(sx + sw - 18, sy + 21, sx + sw - 11, sy + 12); // Draws the second tick stroke.
            g.setStroke(new BasicStroke(1f)); // Restores the default thickness.
        } // Ends the tick badge.

        if (!open) { // Dims locked cards and draws a padlock.
            g.setColor(new Color(10, 12, 30, 150)); // Selects a dark overlay.
            g.fillRoundRect(x, y, w, h, 20, 20); // Dims the card.
            int cx = x + w / 2, cy = sy + 62; // Calculates the padlock centre.
            g.setColor(new Color(210, 210, 225)); // Selects light grey.
            g.setStroke(new BasicStroke(4f)); // Sets the shackle thickness.
            g.drawArc(cx - 11, cy - 24, 22, 26, 0, 180); // Draws the padlock shackle.
            g.setStroke(new BasicStroke(1f)); // Restores the default thickness.
            g.fillRoundRect(cx - 17, cy - 6, 34, 26, 8, 8); // Draws the padlock body.
            g.setColor(new Color(60, 60, 80)); // Selects dark grey.
            g.fillOval(cx - 3, cy + 2, 6, 6); // Draws the keyhole.
        } // Ends the locked overlay.

        g.setStroke(new BasicStroke(sel ? 4f : 2f)); // Sets the border thickness.
        g.setColor(sel ? new Color(255, 215, 80) : new Color(255, 255, 255, 60)); // Selects the border colour.
        g.drawRoundRect(x, y, w, h, 20, 20); // Draws the card border.
        g.setStroke(new BasicStroke(1f)); // Restores the default thickness.

    } // Ends drawLevelCard.

    private void drawMiniScenery(Graphics2D g, int l, int sx, int sy, int sw, int sh) { // Draws a tiny landscape for a level card.

        int base = sy + sh - 28; // Calculates the top of the ground strip.
        g.setColor(groundColor(l)); // Selects the ground colour.
        g.fillRect(sx, base, sw, 28); // Draws the ground strip.

        switch (l) { // Chooses the scenery.
            case 1: // Green Hills.
                g.setColor(new Color(100, 180, 95)); // Selects hill green.
                g.fillOval(sx - 20, sy + sh - 70, 110, 90); // Draws the first hill.
                g.fillOval(sx + 60, sy + sh - 60, 130, 80); // Draws the second hill.
                break;
            case 2: // Rocky Canyon.
                g.setColor(new Color(155, 95, 65)); // Selects cliff brown.
                g.fillPolygon(new int[]{sx, sx + 40, sx + 80, sx + 120, sx + sw}, new int[]{base, sy + 30, base - 20, sy + 45, base}, 5); // Draws the cliffs.
                break;
            case 3: // Enchanted Forest.
                g.setColor(new Color(45, 35, 60)); // Selects trunk colour.
                g.fillRect(sx + 30, sy + 40, 12, base - sy - 40); // Draws the first trunk.
                g.fillRect(sx + 100, sy + 50, 12, base - sy - 50); // Draws the second trunk.
                g.setColor(new Color(65, 115, 90)); // Selects foliage green.
                g.fillOval(sx + 10, sy + 20, 52, 40); // Draws the first canopy.
                g.fillOval(sx + 80, sy + 30, 52, 40); // Draws the second canopy.
                break;
            case 4: // Frozen Peaks.
                g.setColor(new Color(110, 155, 185)); // Selects mountain blue.
                g.fillPolygon(new int[]{sx, sx + 45, sx + 90, sx + 125, sx + sw}, new int[]{base, sy + 25, base, sy + 45, base}, 5); // Draws the mountains.
                g.setColor(Color.WHITE); // Selects snow white.
                g.fillPolygon(new int[]{sx + 35, sx + 45, sx + 55}, new int[]{sy + 45, sy + 25, sy + 45}, 3); // Draws the snow cap.
                break;
            default: // Volcanic Fortress.
                g.setColor(new Color(45, 35, 40)); // Selects volcanic rock.
                g.fillPolygon(new int[]{sx, sx + 60, sx + 95, sx + sw}, new int[]{base, sy + 35, sy + 50, base}, 4); // Draws the volcano.
                g.setColor(new Color(225, 65, 30)); // Selects lava orange.
                g.fillOval(sx + 52, sy + 32, 28, 10); // Draws the crater glow.
                break;
        } // Ends the scenery switch.

    } // Ends drawMiniScenery.

    private void drawShop(Graphics2D g) { // Draws the character shop.

        drawMenuBackground(g); // Draws the backdrop.
        drawCoinBadge(g); // Shows the coin balance.
        g.setFont(F_HEAD); // Sets the heading font.
        g.setColor(Color.YELLOW); // Selects yellow.
        drawCentered(g, "CHARACTER SHOP", 72); // Draws the heading.
        g.setFont(F_SUB); // Sets the subtitle font.
        g.setColor(new Color(190, 210, 255)); // Selects soft blue.
        drawCentered(g, "Spend the coins you collect on new outfits. Your choice is saved.", 100); // Draws the subtitle.

        for (int i = 0; i < designs.length; i++) drawShopCard(g, i); // Draws each character card.

        drawFooter(g, 530, "Arrow keys to browse, ENTER to buy or equip, ESC to go back"); // Shows the hint or message.
        drawBackButton(g); // Draws the BACK button.

    } // Ends drawShop.

    private void drawShopCard(Graphics2D g, int i) { // Draws one character card.

        CharacterDesign d = designs[i]; // Gets the design.
        Rectangle r = shopCardRect(i); // Gets the card area.
        boolean sel = i == shopCursor; // Checks whether the card is highlighted.
        boolean own = ownedCache[i]; // Checks ownership.
        boolean eq = character == d; // Checks whether the design is equipped.
        boolean canBuy = coins >= d.getPrice(); // Checks whether it is affordable.
        int x = r.x, y = r.y + (sel ? -4 : 0), w = r.width, h = r.height; // Calculates the card position.
        double s = h / 190.0; // Calculates the scale for small cards.

        g.setColor(new Color(28, 36, 78)); // Selects the card colour.
        g.fillRoundRect(x, y, w, h, 20, 20); // Draws the card.

        Color acc = d.getAccentColor(); // Gets the accent colour.
        g.setColor(new Color(acc.getRed(), acc.getGreen(), acc.getBlue(), 70)); // Selects a soft glow.
        int glow = (int) (92 * s); // Calculates the glow size.
        g.fillOval(x + w / 2 - glow / 2, y + (int) (10 * s), glow, glow); // Draws the glow behind the figure.

        drawFigureScaled(g, x + w / 2, y + (int) (22 * s), 1.2 * s, d.getOutfitColor(), d.getAccentColor()); // Draws the character preview.

        g.setFont(F_BOLD); // Sets the name font.
        g.setColor(Color.WHITE); // Selects white.
        FontMetrics fm = g.getFontMetrics(); // Measures the name.
        g.drawString(d.getDisplayName(), x + (w - fm.stringWidth(d.getDisplayName())) / 2, y + (int) (132 * s)); // Draws the name.

        String status; // Stores the main status text.
        Color statusColor; // Stores its colour.
        String sub; // Stores the small hint.

        if (eq) { status = "EQUIPPED"; statusColor = new Color(90, 220, 120); sub = "Currently wearing"; } // Handles the equipped design.
        else if (own) { status = "OWNED"; statusColor = Color.WHITE; sub = "Click or ENTER to equip"; } // Handles an owned design.
        else { // Handles a locked design.
            status = d.getPrice() + " coins"; // Shows the price.
            statusColor = canBuy ? new Color(255, 215, 80) : new Color(240, 100, 100); // Gold if affordable, red if not.
            sub = pendingBuy == i ? "Press again to CONFIRM" : canBuy ? "Click or ENTER to buy" : "Need " + (d.getPrice() - coins) + " more coins"; // Chooses the hint.
        } // Ends the status selection.

        g.setFont(F_BOLD); // Sets the status font.
        fm = g.getFontMetrics(); // Measures the status.
        int sw = fm.stringWidth(status); // Gets the status width.
        int sx = x + (w - sw) / 2; // Centres the status.
        if (!own) { sx += 10; drawCoinIcon(g, sx - 22, y + (int) (154 * s) - 14, 16); } // Draws a coin icon beside prices.
        g.setColor(statusColor); // Selects the status colour.
        g.drawString(status, sx, y + (int) (154 * s)); // Draws the status.

        g.setFont(F_SMALL); // Sets the hint font.
        g.setColor(pendingBuy == i ? new Color(255, 230, 120) : new Color(170, 190, 240)); // Highlights a pending purchase.
        fm = g.getFontMetrics(); // Measures the hint.
        g.drawString(sub, x + (w - fm.stringWidth(sub)) / 2, y + (int) (174 * s)); // Draws the hint.

        g.setStroke(new BasicStroke(sel ? 4f : 2f)); // Sets the border thickness.
        g.setColor(sel ? new Color(255, 215, 80) : eq ? new Color(90, 220, 120) : new Color(255, 255, 255, 60)); // Selects the border colour.
        g.drawRoundRect(x, y, w, h, 20, 20); // Draws the border.
        g.setStroke(new BasicStroke(1f)); // Restores the default thickness.

    } // Ends drawShopCard.

    private void drawHelp(Graphics2D g) { // Draws the how-to-play screen.

        drawMenuBackground(g); // Draws the backdrop.
        drawPanel(g, 110, 40, 780, 490); // Draws the text panel.
        g.setFont(F_HEAD); // Sets the heading font.
        g.setColor(Color.YELLOW); // Selects yellow.
        drawCentered(g, "HOW TO PLAY", 100); // Draws the heading.

        g.setFont(F_SUB); // Sets the body font.
        g.setColor(Color.WHITE); // Selects white.
        drawCentered(g, "A little cat has gone missing beyond the hills.", 145); // Tells the story.
        drawCentered(g, "Travel through five dangerous worlds to find it.", 172); // Tells the story.

        g.setColor(Color.CYAN); // Selects cyan for the controls.
        drawCentered(g, "SPACE / UP  -  Jump", 225); // Explains jumping.
        drawCentered(g, "DOWN / S  -  Duck under beams and branches", 255); // Explains ducking.
        drawCentered(g, "P / ESC  -  Pause", 285); // Explains pausing.
        drawCentered(g, "Menus  -  Arrow keys + ENTER, or the mouse", 315); // Explains menu controls.

        g.setColor(Color.WHITE); // Selects white.
        drawCentered(g, "Cyan S = Shield (absorbs one hit)     Orange F = Speed boost", 365); // Explains power-ups.
        drawCentered(g, "Coins are saved - spend them in the Character Shop.", 395); // Explains coins.
        drawCentered(g, "Finish a world to unlock the next one. Best scores are saved.", 425); // Explains progression.

        drawBackButton(g); // Draws the BACK button.

    } // Ends drawHelp.

    // ============================================================
    // IN-GAME OVERLAYS
    // ============================================================

    private void drawPauseOverlay(Graphics2D g) { // Draws the pause menu.
        g.setColor(new Color(0, 0, 0, 170)); // Selects a dark overlay.
        g.fillRect(0, 0, WIDTH, HEIGHT); // Covers the gameplay screen.
        drawPanel(g, 290, 150, 420, 340); // Draws the menu panel.
        g.setFont(F_HEAD); // Sets the heading font.
        g.setColor(Color.WHITE); // Selects white.
        drawCentered(g, "PAUSED", 215); // Draws the heading.
        drawButtons(g); // Draws the pause buttons.
    } // Ends drawPauseOverlay.

    private void drawLevelCompleteOverlay(Graphics2D g) { // Draws the level complete screen.
        g.setColor(new Color(10, 15, 30, 225)); // Selects a dark overlay.
        g.fillRect(0, 0, WIDTH, HEIGHT); // Covers the gameplay screen.
        g.setFont(F_HEAD); // Sets the heading font.
        g.setColor(Color.YELLOW); // Selects yellow.
        drawCentered(g, "LEVEL " + level + " COMPLETE!", 90); // Confirms the level is finished.
        g.setFont(F_BUTTON); // Sets the level name font.
        g.setColor(Color.WHITE); // Selects white.
        drawCentered(g, levelName(level), 135); // Displays the completed world.
        g.setFont(F_SUB); // Sets the message font.
        drawCentered(g, "You reached the finish line!", 190); // Confirms the finish.
        drawCentered(g, "Score: " + currentScore() + "     Best: " + best[level], 225); // Shows the score and saved best.
        drawCentered(g, "Coins collected: " + runCoins + "     Total saved: " + coins, 255); // Shows the coin totals.
        if (newBest) { g.setColor(new Color(255, 215, 80)); g.setFont(F_BUTTON); drawCentered(g, "NEW BEST SCORE!", 300); } // Celebrates a new best.
        else if (level < 5) { g.setColor(new Color(90, 220, 120)); g.setFont(F_BOLD); drawCentered(g, "Level " + (level + 1) + " unlocked!", 300); } // Announces the unlock.
        drawButtons(g); // Draws the buttons.
    } // Ends drawLevelCompleteOverlay.

    private void drawGameOverOverlay(Graphics2D g) { // Draws the game over screen.
        g.setColor(new Color(0, 0, 0, 215)); // Selects a dark overlay.
        g.fillRect(0, 0, WIDTH, HEIGHT); // Covers the gameplay screen.
        g.setFont(F_TITLE); // Sets the heading font.
        g.setColor(Color.RED); // Selects red.
        drawCentered(g, "GAME OVER", 150); // Displays the game-over message.
        g.setFont(F_BUTTON); // Sets the level name font.
        g.setColor(Color.WHITE); // Selects white.
        drawCentered(g, "LEVEL " + level + " - " + levelName(level), 200); // Shows which level the player lost.
        g.setFont(F_SUB); // Sets the message font.
        drawCentered(g, "Your best score and coins have been saved.", 245); // Explains that progress is preserved.
        drawButtons(g); // Draws the buttons.
    } // Ends drawGameOverOverlay.

    private void drawVictoryScreen(Graphics2D g) { // Displays the final cat rescue.
        g.setColor(new Color(35, 15, 25)); // Selects a dark volcanic background.
        g.fillRect(0, 0, WIDTH, HEIGHT); // Paints the final screen.
        g.setColor(new Color(220, 80, 35)); // Selects orange for the rescue platform.
        g.fillRect(0, GROUND, WIDTH, HEIGHT - GROUND); // Draws the platform.
        g.setFont(F_HEAD); // Sets the heading font.
        g.setColor(Color.YELLOW); // Selects yellow.
        drawCentered(g, "YOU SAVED THE CAT!", 85); // Celebrates the rescue.
        drawCat(g, 465, 150); // Draws the rescued cat.
        g.setFont(F_SUB); // Sets the ending text font.
        g.setColor(Color.WHITE); // Selects white.
        drawCentered(g, "You crossed all five worlds.", 295); // Celebrates the completed adventure.
        drawCentered(g, "Your little friend is finally safe!", 330); // Confirms the cat is safe.
        drawCentered(g, "Total saved coins: " + coins, 365); // Displays the saved coin total.
        drawButtons(g); // Draws the buttons.
    } // Ends the victory screen method.

    // ============================================================
    // MAIN PAINT METHOD
    // ============================================================

    @Override
    protected void paintComponent(Graphics graphics) { // Redraws the screen whenever Swing requests it.

        super.paintComponent(graphics); // Clears the previous frame.
        Graphics2D g = (Graphics2D) graphics.create(); // Creates an independent graphics context.
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // Smooths shapes.
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON); // Smooths text.

        switch (state) { // Chooses what to draw.
            case MENU: drawMainMenu(g); break; // Draws the main menu.
            case LEVEL_SELECT: drawLevelSelect(g); break; // Draws level select.
            case SHOP: drawShop(g); break; // Draws the shop.
            case HELP: drawHelp(g); break; // Draws the help screen.
            case VICTORY: drawVictoryScreen(g); break; // Draws the final ending.
            default: // Draws gameplay and its overlays.
                drawBackground(g); // Draws the current world's scenery.
                drawObstacles(g); // Draws the hazards.
                drawCoins(g); // Draws coins.
                drawPowerUps(g); // Draws power-ups.
                drawPlayer(g); // Draws the player.
                drawHUD(g); // Draws lives, coins and progress.
                if (state == State.PAUSED) drawPauseOverlay(g); // Shows the pause menu.
                else if (state == State.LEVEL_COMPLETE) drawLevelCompleteOverlay(g); // Shows the level ending.
                else if (state == State.GAME_OVER) drawGameOverOverlay(g); // Shows the retry screen.
                break;
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

    // ============================================================
    // PROGRAM ENTRY POINT
    // ============================================================

    public static void main(String[] args) { // Starts the PixelDash game.

        javax.swing.SwingUtilities.invokeLater(() -> { // Runs on the Swing thread.

            javax.swing.JFrame frame = new javax.swing.JFrame("PixelDash"); // Creates the game window.
            PixelDash game = new PixelDash(); // Creates the game panel (prints the database message).

            frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE); // Stops the program when the window closes.
            frame.add(game); // Adds the game to the window.
            frame.pack(); // Sizes the window exactly to the game panel.
            frame.setResizable(false); // Prevents the window from being resized.
            frame.setLocationRelativeTo(null); // Centres the window.
            frame.setVisible(true); // Displays the window.
            game.requestFocusInWindow(); // Gives keyboard controls to the game.

        }); // Ends the Swing startup.

    } // Ends the main method.

} // Ends the PixelDash class.
