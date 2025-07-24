package io.github.fieldmesh;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SquadIndex {

    public static final List<String> SQUAD_NAMES = Collections.unmodifiableList(Arrays.asList(
            "Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot",
            "Golf", "Hotel", "India", "Juliett", "Kilo", "Lima", "Mike",
            "November", "Oscar", "Papa", "Quebec", "Romeo", "Sierra",
            "Tango", "Uniform", "Victor", "Whiskey", "X-ray", "Yankee", "Zulu", "Global"
    ));

    private static final List<Byte> SQUAD_IDS = Collections.unmodifiableList(Arrays.asList(
            (byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6,
            (byte) 7, (byte) 8, (byte) 9, (byte) 10, (byte) 11, (byte) 12, (byte) 13,
            (byte) 14, (byte) 15, (byte) 16, (byte) 17, (byte) 18, (byte) 19,
            (byte) 20, (byte) 21, (byte) 22, (byte) 23, (byte) 24, (byte) 25, (byte) 26, (byte) 0
    ));

    /**
     * Gets the byte ID for a given squad name. This search is case-insensitive.
     * @param squadName The name of the squad (e.g., "Alpha").
     * @return The corresponding byte ID, or 0 if the name is not found or is null.
     */

    /**
     * NEW METHOD: Returns a list of all defined squad IDs as strings.
     * @return A new list of squad ID strings.
     */
    public static List<String> getAllSquadNames() {
        return SQUAD_NAMES;
    }

    public static byte getIdByName(String squadName) {
        if (squadName == null) {
            return 0; // Default to Global
        }
        for (int i = 0; i < SQUAD_NAMES.size(); i++) {
            if (SQUAD_NAMES.get(i).equalsIgnoreCase(squadName)) {
                return SQUAD_IDS.get(i);
            }
        }
        return 0; // Default to Global if not found
    }

    /**
     * Gets the squad name for a given byte ID.
     * @param squadId The byte ID of the squad.
     * @return The corresponding squad name string, or "Global" if the ID is 0 or not found.
     */
    public static String getNameById(byte squadId) {
        if (squadId == 0) {
            return "Global";
        }
        int index = SQUAD_IDS.indexOf(squadId);
        if (index != -1) {
            return SQUAD_NAMES.get(index);
        }
        return "Global"; // Default to Global if not found
    }
}