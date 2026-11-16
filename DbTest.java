

import java.sql.Connection; // Represents a database connection.
import java.sql.DriverManager; // Opens database connections.

import application.GameDatabase;

public class DbTest { // Tests the database alone.

    public static void main(String[] args) { // Program entry point.

        try { // Step 1: checks that the driver class loads.
            Class.forName("org.sqlite.JDBC"); // Loads the SQLite driver.
            System.out.println("STEP 1 OK: driver class found."); // Confirms the jar is on the classpath.
        } catch (Throwable t) { // Catches a missing driver.
            System.out.println("STEP 1 FAILED: " + t); // Prints the error.
            return; // Stops here.
        } // Ends step 1.

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:pixeldash.db")) { // Step 2: opens the database.
            System.out.println("STEP 2 OK: connected to " + c.getMetaData().getURL()); // Confirms the connection.
        } catch (Throwable t) { // Catches connection failures.
            Throwable root = t; // Starts from the top-level error.
            while (root.getCause() != null) root = root.getCause(); // Walks down to the original cause.
            System.out.println("STEP 2 FAILED: " + t); // Prints the top-level error.
            System.out.println("ROOT CAUSE: " + root); // Prints the original cause.
            return; // Stops here.
        } // Ends step 2.

        try { // Step 3: runs your real database class.
            GameDatabase db = new GameDatabase(); // Creates the tables.
            System.out.println("STEP 3 OK: Coins " + db.getCoins() + " | Level " + db.getCurrentLevel()); // Reads saved data.
        } catch (Throwable t) { // Catches setup failures.
            Throwable root = t; // Starts from the top-level error.
            while (root.getCause() != null) root = root.getCause(); // Walks down to the original cause.
            System.out.println("STEP 3 FAILED, ROOT CAUSE: " + root); // Prints the original cause.
        } // Ends step 3.

    } // Ends main.

} // Ends DbTest.