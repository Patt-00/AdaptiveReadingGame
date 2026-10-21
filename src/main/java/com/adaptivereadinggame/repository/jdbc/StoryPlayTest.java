package com.adaptivereadinggame.repository.jdbc;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Console test: plays the story from the database.
 * Menu: 1) Next / Choose   2) Save   3) Load   0) Quit      (save slots 1-5)
 * Uses the fake demo student and chapter 1 from the seed files.
 *
 * Run:  export DB_PASS='yourpassword'
 *       java -Dstdout.encoding=UTF-8 -cp "target/classes:$(cat /tmp/cp.txt)" \
 *            com.adaptivereadinggame.repository.jdbc.StoryPlayTest
 */
public class StoryPlayTest {

    private static final String STUDENT_ID = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String CHAPTER_ID = "cccccccc-0000-0000-0000-000000000001";
    private static final int MAX_SLOTS = 5;
    private static final int SCREEN_WIDTH = 70;

    // One row of story_choices: where a scene leads. text == null means automatic "Next".
    private record Link(String style, String text, String toNode) {}

    public static void main(String[] args) {
        String url  = env("DB_URL", "jdbc:mysql://localhost:3306/adaptive_reading");
        String user = env("DB_USER", "argdev");
        String pass = System.getenv("DB_PASS");
        if (pass == null) {
            System.out.println("Set DB_PASS first:  export DB_PASS='yourpassword'");
            return;
        }

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Scanner sc = new Scanner(System.in)) {

            String difficulty = queryString(conn,
                "SELECT difficulty FROM chapters WHERE id = ?", CHAPTER_ID);
            String currentNode = queryString(conn,
                "SELECT node_id FROM story_nodes WHERE chapter_id = ? AND is_start = 1 LIMIT 1",
                CHAPTER_ID);
            if (difficulty == null || currentNode == null) {
                System.out.println("No story found. Run:  bash database/setup.sh --reset");
                return;
            }

            boolean running = true;
            while (running) {
                List<Link> links = showScene(conn, currentNode);

                String firstOption = links.isEmpty() ? "Next (no next scene yet)"
                        : links.get(0).text() == null ? "Next" : "Choose";
                System.out.println("1) " + firstOption + "   2) Save   3) Load   0) Quit");
                System.out.print("> ");
                if (!sc.hasNextLine()) break;
                String input = sc.nextLine().trim();

                switch (input) {
                    case "1": {
                        if (links.isEmpty()) {
                            System.out.println("\n(No next scene yet. Add a link for this scene in story_choices.)");
                        } else if (links.get(0).text() == null) {
                            currentNode = links.get(0).toNode();          // automatic Next
                        } else {
                            String picked = chooseLink(sc, links);        // player picks a button
                            if (picked != null) currentNode = picked;
                        }
                        break;
                    }
                    case "2":
                        saveToSlot(conn, sc, currentNode, difficulty);
                        break;
                    case "3": {
                        String loaded = loadFromSlot(conn, sc);
                        if (loaded != null) currentNode = loaded;
                        break;
                    }
                    case "0":
                        running = false;
                        break;
                    default:
                        System.out.println("Please type 1, 2, 3, or 0.");
                }
            }
            System.out.println("Bye!");
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    // ---------- SHOW A SCENE ----------

    // Prints the scene header and all its lines. Returns the links that leave this scene.
    private static List<Link> showScene(Connection conn, String nodeId) throws SQLException {
        String nodeSql = "SELECT scene_name, location_label, background_image "
                       + "FROM story_nodes WHERE chapter_id = ? AND node_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(nodeSql)) {
            ps.setString(1, CHAPTER_ID);
            ps.setString(2, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("\n==============================================================");
                    System.out.println(nodeId + "  " + rs.getString("scene_name")
                            + "   (background: " + rs.getString("background_image") + ")");
                    String label = rs.getString("location_label");
                    if (label != null) System.out.println(label);
                    System.out.println("==============================================================");
                }
            }
        }

        String lineSql = "SELECT line_type, speaker, expression, dialogue FROM story_lines "
                       + "WHERE chapter_id = ? AND node_id = ? ORDER BY line_no";
        try (PreparedStatement ps = conn.prepareStatement(lineSql)) {
            ps.setString(1, CHAPTER_ID);
            ps.setString(2, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(formatLine(rs.getString("line_type"),
                            rs.getString("speaker"), rs.getString("expression"),
                            rs.getString("dialogue")));
                }
            }
        }
        System.out.println();

        List<Link> links = new ArrayList<>();
        String linkSql = "SELECT choice_style, choice_text, to_node_id FROM story_choices "
                       + "WHERE chapter_id = ? AND from_node_id = ? ORDER BY choice_order";
        try (PreparedStatement ps = conn.prepareStatement(linkSql)) {
            ps.setString(1, CHAPTER_ID);
            ps.setString(2, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    links.add(new Link(rs.getString("choice_style"),
                            rs.getString("choice_text"), rs.getString("to_node_id")));
                }
            }
        }
        return links;
    }

    private static String formatLine(String type, String speaker, String expression, String text) {
        if ("SPEECH".equals(type)) {
            String who = speaker == null ? "" :
                    speaker + (expression == null ? "" : " [" + expression + "]") + ": ";
            return who + text;
        }
        if ("THOUGHT".equals(type)) {
            return "(" + (speaker == null ? "" : speaker + " thinks: ") + text + ")";
        }
        if ("CENTERED".equals(type)) {
            int pad = Math.max(0, (SCREEN_WIDTH - text.length()) / 2);
            return " ".repeat(pad) + text;
        }
        if ("DIRECTION".equals(type)) {
            return "[" + text + "]";
        }
        return text;
    }

    // ---------- CHOICE BUTTONS ----------

    // Shows the buttons and returns the chosen scene id, or null to stay on this scene.
    private static String chooseLink(Scanner sc, List<Link> links) {
        System.out.println("\nWhat do you do?");
        for (int i = 0; i < links.size(); i++) {
            System.out.println((i + 1) + ") " + formatChoice(links.get(i)));
        }
        System.out.println("0) Go back");
        System.out.print("> ");
        int pick = readNumber(sc);
        if (pick < 1 || pick > links.size()) return null;
        return links.get(pick - 1).toNode();
    }

    private static String formatChoice(Link link) {
        if ("SPEECH".equals(link.style()))  return "\u201c" + link.text() + "\u201d";
        if ("ACTION".equals(link.style()))  return "(" + link.text() + ")";
        if ("THOUGHT".equals(link.style())) return "(thinking) " + link.text();
        return link.text();
    }

    // ---------- SAVE ----------

    private static void saveToSlot(Connection conn, Scanner sc, String nodeId, String difficulty)
            throws SQLException {
        String[] labels = printSlots(conn);
        System.out.print("Save to which slot (1-" + MAX_SLOTS + ", 0 = cancel)? ");
        int slot = readNumber(sc);
        if (slot < 1 || slot > MAX_SLOTS) {
            System.out.println("Save cancelled.");
            return;
        }
        if (labels[slot] != null) {
            System.out.print("Slot " + slot + " already has a save. Overwrite? (y/n) ");
            if (!sc.hasNextLine() || !sc.nextLine().trim().equalsIgnoreCase("y")) {
                System.out.println("Save cancelled.");
                return;
            }
        }

        // Insert a new row, or overwrite this student's row for this slot.
        String sql = "INSERT INTO save_slots "
                   + "(student_id, slot, chapter_id, story_node_id, difficulty) "
                   + "VALUES (?, ?, ?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE chapter_id = VALUES(chapter_id), "
                   + "story_node_id = VALUES(story_node_id), "
                   + "difficulty = VALUES(difficulty), saved_at = CURRENT_TIMESTAMP(3)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, STUDENT_ID);
            ps.setInt(2, slot);
            ps.setString(3, CHAPTER_ID);
            ps.setString(4, nodeId);
            ps.setString(5, difficulty);
            ps.executeUpdate();
        }
        System.out.println("Saved to slot " + slot + ".");
    }

    // ---------- LOAD ----------

    // Returns the saved scene id, or null if the load was cancelled or the slot is empty.
    private static String loadFromSlot(Connection conn, Scanner sc) throws SQLException {
        String[] labels = printSlots(conn);
        System.out.print("Load which slot (1-" + MAX_SLOTS + ", 0 = cancel)? ");
        int slot = readNumber(sc);
        if (slot < 1 || slot > MAX_SLOTS) {
            System.out.println("Load cancelled.");
            return null;
        }
        if (labels[slot] == null) {
            System.out.println("Slot " + slot + " is empty.");
            return null;
        }
        String nodeId = queryString(conn,
            "SELECT story_node_id FROM save_slots WHERE student_id = ? AND slot = ?",
            STUDENT_ID, slot);
        System.out.println("Loaded slot " + slot + ".");
        return nodeId;
    }

    // ---------- SHOW SLOTS 1-5 ----------

    // Prints every slot and returns an array: labels[slot] is null when the slot is empty.
    private static String[] printSlots(Connection conn) throws SQLException {
        String[] labels = new String[MAX_SLOTS + 1];
        String sql = "SELECT sv.slot, n.scene_name, sv.saved_at "
                   + "FROM save_slots sv "
                   + "JOIN story_nodes n ON n.chapter_id = sv.chapter_id "
                   + "                  AND n.node_id = sv.story_node_id "
                   + "WHERE sv.student_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, STUDENT_ID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    if (slot >= 1 && slot <= MAX_SLOTS) {
                        labels[slot] = rs.getString("scene_name")
                                + "   (saved " + rs.getTimestamp("saved_at") + ")";
                    }
                }
            }
        }
        System.out.println("\n--- Save slots ---");
        for (int i = 1; i <= MAX_SLOTS; i++) {
            System.out.println("Slot " + i + ": " + (labels[i] == null ? "(empty)" : labels[i]));
        }
        return labels;
    }

    // ---------- SMALL HELPERS ----------

    private static int readNumber(Scanner sc) {
        if (!sc.hasNextLine()) return -1;
        try {
            return Integer.parseInt(sc.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // Runs a SELECT and returns the first column of the first row, or null.
    private static String queryString(Connection conn, String sql, Object... params)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
