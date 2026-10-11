
package application; // Places this class in the application package.

import java.awt.Color; // Imports colours for character artwork.

public enum CharacterDesign { // Lists all available character designs.

    CLASSIC("classic", "Classic Runner", 0, new Color(35, 95, 220), new Color(225, 40, 45)), // Free blue outfit.
    SHADOW("shadow", "Shadow Runner", 100, new Color(55, 55, 70), new Color(150, 90, 220)), // Dark outfit.
    FOREST("forest", "Forest Ranger", 150, new Color(35, 125, 65), new Color(230, 190, 65)), // Green outfit.
    ICE("ice", "Ice Explorer", 200, new Color(100, 205, 245), Color.WHITE), // Ice outfit.
    LAVA("lava", "Lava Champion", 300, new Color(210, 55, 30), new Color(255, 190, 35)), // Lava outfit.
    GOLD("gold", "Golden Hero", 500, new Color(245, 185, 25), new Color(255, 245, 130)); // Golden outfit.

    private final String id; // Stores the permanent database ID.
    private final String displayName; // Stores the name displayed to the player.
    private final int price; // Stores the price in coins.
    private final Color outfitColor; // Stores the main clothing colour.
    private final Color accentColor; // Stores the scarf and accessory colour.

    CharacterDesign(String id, String displayName, int price, Color outfitColor, Color accentColor) { // Initialises a design.
        this.id = id; // Saves the ID.
        this.displayName = displayName; // Saves the display name.
        this.price = price; // Saves the price.
        this.outfitColor = outfitColor; // Saves the main colour.
        this.accentColor = accentColor; // Saves the accessory colour.
    } // Ends the constructor.

    public String getId() { return id; } // Returns the database ID.
    public String getDisplayName() { return displayName; } // Returns the character name.
    public int getPrice() { return price; } // Returns the price.
    public Color getOutfitColor() { return outfitColor; } // Returns the outfit colour.
    public Color getAccentColor() { return accentColor; } // Returns the accent colour.

    public static CharacterDesign fromId(String id) { // Finds a design from a saved database ID.
        for (CharacterDesign design : values()) { // Examines each available design.
            if (design.id.equals(id)) return design; // Returns the matching design.
        } // Ends the search.
        return CLASSIC; // Uses the default design if the saved ID is unknown.
    } // Ends fromId.

} // Ends CharacterDesign.