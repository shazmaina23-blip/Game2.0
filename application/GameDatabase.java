
package application; // Places this class in the application package.

import java.sql.Connection; // Represents a database connection.
import java.sql.DriverManager; // Opens database connections.
import java.sql.PreparedStatement; // Runs SQL with parameters.
import java.sql.ResultSet; // Reads query results.
import java.sql.SQLException; // Handles database errors.
import java.sql.Statement; // Executes SQL statements.

public class GameDatabase { // Manages all saved game data.

    private static final String URL = "jdbc:sqlite:pixeldash.db"; // Defines the local database file.

    public GameDatabase() { // Initialises the database.
        createTables(); // Creates tables if they do not exist.
        initialisePlayer(); // Creates the default player record.
    } // Ends the constructor.

    private Connection connect() throws SQLException { // Opens a database connection.
        return DriverManager.getConnection(URL); // Connects to SQLite.
    } // Ends connect.

    private void createTables() { // Creates all required database tables.

        String playerSql = "CREATE TABLE IF NOT EXISTS player (" // Begins the player table.
            + "id INTEGER PRIMARY KEY CHECK(id=1)," // Uses one local player profile.
            + "coins INTEGER NOT NULL DEFAULT 0," // Stores the coin balance.
            + "equipped_character TEXT NOT NULL DEFAULT 'classic')"; // Stores the equipped design.

        String scoresSql = "CREATE TABLE IF NOT EXISTS level_scores (" // Begins the scores table.
            + "level INTEGER PRIMARY KEY," // Stores the level number.
            + "best_score INTEGER NOT NULL DEFAULT 0," // Stores the best score.
            + "completed INTEGER NOT NULL DEFAULT 0)"; // Stores completion status.

        String charactersSql = "CREATE TABLE IF NOT EXISTS characters (" // Begins the character ownership table.
            + "character_id TEXT PRIMARY KEY," // Stores a unique character ID.
            + "owned INTEGER NOT NULL DEFAULT 0)"; // Stores whether the character is unlocked.

        try (Connection c = connect(); Statement s = c.createStatement()) { // Opens the connection and statement.
            s.execute(playerSql); // Creates the player table.
            s.execute(scoresSql); // Creates the scores table.
            s.execute(charactersSql); // Creates the characters table.
        } catch (SQLException e) { // Handles setup failures.
            throw new IllegalStateException("Could not create database tables.", e); // Reports the error.
        } // Ends error handling.

    } // Ends createTables.

    private void initialisePlayer() { // Creates initial records without resetting existing data.

        try (Connection c = connect(); Statement s = c.createStatement()) { // Opens the database.

            s.executeUpdate("INSERT OR IGNORE INTO player(id,coins,equipped_character) "
                + "VALUES(1,0,'classic')"); // Creates the player if missing.

            s.executeUpdate("INSERT OR IGNORE INTO characters(character_id,owned) "
                + "VALUES('classic',1)"); // Unlocks the free character.

            for (int level = 1; level <= 5; level++) { // Creates records for all five levels.
                try (PreparedStatement p = c.prepareStatement(
                    "INSERT OR IGNORE INTO level_scores(level,best_score,completed) "
                    + "VALUES(?,0,0)")) { // Prepares a level record.
                    p.setInt(1, level); // Sets the level number.
                    p.executeUpdate(); // Inserts it only if it does not exist.
                } // Closes the statement.
            } // Ends the loop.

        } catch (SQLException e) { // Handles initialisation errors.
            throw new IllegalStateException("Could not initialise saved game data.", e); // Reports the error.
        } // Ends error handling.

    } // Ends initialisePlayer.

    public int getCoins() { // Loads the current coin balance.
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT coins FROM player WHERE id=1")) { // Reads the balance.
            return r.next() ? r.getInt("coins") : 0; // Returns the saved balance.
        } catch (SQLException e) { // Handles read errors.
            throw new IllegalStateException("Could not load coins.", e); // Reports the error.
        } // Ends error handling.
    } // Ends getCoins.

    public void addCoins(int amount) { // Saves newly earned coins.
        if (amount <= 0) return; // Rejects invalid rewards.
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement(
            "UPDATE player SET coins=coins+? WHERE id=1")) { // Prepares the balance update.
            p.setInt(1, amount); // Sets the reward amount.
            p.executeUpdate(); // Saves the updated balance.
        } catch (SQLException e) { // Handles write errors.
            throw new IllegalStateException("Could not save coins.", e); // Reports the error.
        } // Ends error handling.
    } // Ends addCoins.

    public boolean spendCoins(int amount) { // Deducts coins only when the balance is sufficient.
        if (amount < 0) return false; // Rejects negative prices.
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement(
            "UPDATE player SET coins=coins-? WHERE id=1 AND coins>=?")) { // Protects against overspending.
            p.setInt(1, amount); // Sets the deduction.
            p.setInt(2, amount); // Checks the available balance.
            return p.executeUpdate() == 1; // Returns whether payment succeeded.
        } catch (SQLException e) { // Handles write errors.
            throw new IllegalStateException("Could not spend coins.", e); // Reports the error.
        } // Ends error handling.
    } // Ends spendCoins.

    public void saveLevelScore(int level, int score, boolean completed) { // Saves a level's best result.
        if (level < 1 || level > 5 || score < 0) { // Checks the supplied values.
            throw new IllegalArgumentException("Invalid level or score."); // Rejects invalid input.
        } // Ends validation.

        String sql = "UPDATE level_scores SET best_score=MAX(best_score,?), "
            + "completed=MAX(completed,?) WHERE level=?"; // Preserves best scores and completed levels.

        try (Connection c = connect(); PreparedStatement p = c.prepareStatement(sql)) { // Prepares the update.
            p.setInt(1, score); // Sets the new score.
            p.setInt(2, completed ? 1 : 0); // Sets the completion flag.
            p.setInt(3, level); // Selects the level.
            p.executeUpdate(); // Saves the result.
        } catch (SQLException e) { // Handles write errors.
            throw new IllegalStateException("Could not save level score.", e); // Reports the error.
        } // Ends error handling.
    } // Ends saveLevelScore.

    public int getBestScore(int level) { // Loads a level's best score.
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement(
            "SELECT best_score FROM level_scores WHERE level=?")) { // Prepares the query.
            p.setInt(1, level); // Sets the level number.
            try (ResultSet r = p.executeQuery()) { // Executes the query.
                return r.next() ? r.getInt("best_score") : 0; // Returns the saved score.
            } // Closes the result set.
        } catch (SQLException e) { // Handles read errors.
            throw new IllegalStateException("Could not load score.", e); // Reports the error.
        } // Ends error handling.
    } // Ends getBestScore.

    public boolean isLevelCompleted(int level) { // Checks saved completion status.
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement(
            "SELECT completed FROM level_scores WHERE level=?")) { // Prepares the query.
            p.setInt(1, level); // Sets the level number.
            try (ResultSet r = p.executeQuery()) { // Executes the query.
                return r.next() && r.getInt("completed") == 1; // Returns the completion status.
            } // Closes the result set.
        } catch (SQLException e) { // Handles read errors.
            throw new IllegalStateException("Could not load completion status.", e); // Reports the error.
        } // Ends error handling.
    } // Ends isLevelCompleted.

    public boolean ownsCharacter(String id) { // Checks whether a design is unlocked.
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement(
            "SELECT owned FROM characters WHERE character_id=?")) { // Prepares the query.
            p.setString(1, id); // Sets the design ID.
            try (ResultSet r = p.executeQuery()) { // Executes the query.
                return r.next() && r.getInt("owned") == 1; // Returns ownership.
            } // Closes the result set.
        } catch (SQLException e) { // Handles read errors.
            throw new IllegalStateException("Could not check character ownership.", e); // Reports the error.
        } // Ends error handling.
    } // Ends ownsCharacter.

    public boolean buyCharacter(String id, int price) { // Buys a character safely.
        if (price < 0) return false; // Rejects invalid prices.

        try (Connection c = connect()) { // Opens a database connection.
            c.setAutoCommit(false); // Starts a transaction.

            try { // Performs the purchase as one transaction.

                try (PreparedStatement p = c.prepareStatement(
                    "INSERT OR IGNORE INTO characters(character_id,owned) VALUES(?,0)")) { // Creates the design record.
                    p.setString(1, id); // Sets the character ID.
                    p.executeUpdate(); // Ensures the record exists.
                } // Closes the statement.

                try (PreparedStatement p = c.prepareStatement(
                    "SELECT owned FROM characters WHERE character_id=?")) { // Checks ownership.
                    p.setString(1, id); // Selects the character.
                    try (ResultSet r = p.executeQuery()) { // Reads ownership.
                        if (r.next() && r.getInt("owned") == 1) { // Checks whether it is already unlocked.
                            c.rollback(); // Cancels the unnecessary transaction.
                            return true; // Reports that the character is already owned.
                        } // Ends the ownership check.
                    } // Closes the result set.
                } // Closes the statement.

                try (PreparedStatement p = c.prepareStatement(
                    "UPDATE player SET coins=coins-? WHERE id=1 AND coins>=?")) { // Deducts only affordable purchases.
                    p.setInt(1, price); // Sets the price.
                    p.setInt(2, price); // Checks the balance.
                    if (p.executeUpdate() != 1) { // Checks whether payment succeeded.
                        c.rollback(); // Cancels the purchase.
                        return false; // Reports insufficient coins.
                    } // Ends the payment check.
                } // Closes the statement.

                try (PreparedStatement p = c.prepareStatement(
                    "UPDATE characters SET owned=1 WHERE character_id=?")) { // Unlocks the design.
                    p.setString(1, id); // Selects the design.
                    p.executeUpdate(); // Saves ownership.
                } // Closes the statement.

                c.commit(); // Saves the deduction and unlock together.
                return true; // Reports purchase success.

            } catch (SQLException e) { // Handles transaction errors.
                c.rollback(); // Reverses incomplete purchase operations.
                throw e; // Passes the error to the outer handler.
            } finally { // Restores normal connection behaviour.
                c.setAutoCommit(true); // Ends transaction mode.
            } // Ends transaction cleanup.

        } catch (SQLException e) { // Handles connection errors.
            throw new IllegalStateException("Could not purchase character.", e); // Reports the error.
        } // Ends error handling.
    } // Ends buyCharacter.

    public String getEquippedCharacter() { // Loads the selected design.
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT equipped_character FROM player WHERE id=1")) { // Reads the design ID.
            return r.next() ? r.getString("equipped_character") : "classic"; // Returns the saved design.
        } catch (SQLException e) { // Handles read errors.
            throw new IllegalStateException("Could not load equipped character.", e); // Reports the error.
        } // Ends error handling.
    } // Ends getEquippedCharacter.

    public boolean equipCharacter(String id) { // Equips an owned design.
        if (!ownsCharacter(id)) return false; // Prevents using a locked design.
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement(
            "UPDATE player SET equipped_character=? WHERE id=1")) { // Prepares the update.
            p.setString(1, id); // Sets the chosen design.
            return p.executeUpdate() == 1; // Saves and confirms the selection.
        } catch (SQLException e) { // Handles write errors.
            throw new IllegalStateException("Could not equip character.", e); // Reports the error.
        } // Ends error handling.
    } // Ends equipCharacter.

} // Ends GameDatabase.