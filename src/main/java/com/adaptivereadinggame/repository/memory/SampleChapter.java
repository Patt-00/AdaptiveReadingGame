package com.adaptivereadinggame.repository.memory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.adaptivereadinggame.model.Chapter;
import com.adaptivereadinggame.model.Difficulty;
import com.adaptivereadinggame.model.Question;
import com.adaptivereadinggame.model.StoryChoice;
import com.adaptivereadinggame.model.StoryNode;
import com.adaptivereadinggame.repository.ChapterRepository;

/** Chapter 0 demo content. Nothing here loads itself; call load(...) on purpose. */
public class SampleChapter {
    // long string dialogue
    private static final String wakeUpSceneDialogue = "The person woke up from bed, ate breakfast, showered, dressed up, and went to school.";
    private static final String greetingFriend1Dialogue = "The person encountered his friend and he wants to give him a greeting.";
    private static final String lunchSceneDialogue = "After math class, they ate lunch. What do you eat?";

    // UUIDs are generated once per program run (static final = created when the class first loads).
    // Within one run they never change, but every restart creates new ones.
    public static final UUID chapterId = randomId();
    public static final UUID q1Id = randomId();
    public static final UUID q2Id = randomId();
    public static final UUID q3Id = randomId();
    public static final UUID q4Id = randomId();
    public static final UUID q5Id = randomId();

    public static final String FIRST_NODE_ID = "intro";

    // story nodes example: (id, speaker, text, thought, choices, nextNodeId, readingCheck)
    public static final StoryNode intro = new StoryNode("intro", "", "Person goes to class", false, List.of(), "wakeUpScene", false);
    public static final StoryNode wakeUpScene = new StoryNode("wakeUpScene", "", wakeUpSceneDialogue, false, List.of(), "greetingFriend1", false);
    // choice node example: the buttons replace the next node, so nextNodeId is ""
    public static final StoryNode greetingFriend1 = new StoryNode("greetingFriend1", "", greetingFriend1Dialogue, false, List.of(
            new StoryChoice("greet-hello", "Hello", "greetingFriend2"),
            new StoryChoice("greet-hi", "Hi", "greetingFriend2"),
            new StoryChoice("greet-whatsup", "What's up", "greetingFriend2")),
            "", false);
    public static final StoryNode greetingFriend2 = new StoryNode("greetingFriend2", "Friend", "Hello.", false, List.of(), "mathIntro", false);
    public static final StoryNode mathIntro = new StoryNode("mathIntro", "", "They went to their mathematics class.", false, List.of(), "mathTeacher", false);
    public static final StoryNode mathTeacher = new StoryNode("mathTeacher", "Teacher", "What is 1+1?", false, List.of(
            new StoryChoice("math-2", "2", "mathAnswer"),
            new StoryChoice("math-3", "3", "mathAnswer"),
            new StoryChoice("math-idk", "I don't know", "mathAnswer")),
            "", false);
    public static final StoryNode mathAnswer = new StoryNode("mathAnswer", "Teacher", "The answer is 2.", false, List.of(), "lunchScene", false);
    public static final StoryNode lunchScene = new StoryNode("lunchScene", "", lunchSceneDialogue, false, List.of(
            new StoryChoice("lunch-fish", "Fish", "lunchFish"),
            new StoryChoice("lunch-chicken", "Chicken", "lunchChicken"),
            new StoryChoice("lunch-sandwich", "Sandwich", "lunchSandwich")),
            "", false);
    // optional reading check nodes: they make each lunch choice lead somewhere different (useful for tests)
    public static final StoryNode lunchFish = new StoryNode("lunchFish", "", "They ate fish.", false,
            List.of(), "endingScene", false);
    public static final StoryNode lunchChicken = new StoryNode("lunchChicken", "", "They ate chicken.", false,
            List.of(), "endingScene", false);
    public static final StoryNode lunchSandwich = new StoryNode("lunchSandwich", "", "They ate a sandwich.",
            false, List.of(), "endingScene", false);
    // reading check: no choices, no next node, readingCheck = true (the questions start here)
    public static final StoryNode endingScene = new StoryNode("endingScene", "", "They went back to class, and then went home.", false, List.of(), "", true);

    // all nodes by ID, in story order, so a story repository can look them up
    private static final Map<String, StoryNode> NODES = buildNodes();

    private static Map<String, StoryNode> buildNodes() {
        Map<String, StoryNode> map = new LinkedHashMap<>();
        for (StoryNode node : List.of(intro, wakeUpScene, greetingFriend1, greetingFriend2, mathIntro,
                mathTeacher, mathAnswer, lunchScene, lunchFish, lunchChicken, lunchSandwich, endingScene)) {
            if (map.put(node.storyNodeId(), node) != null)
                throw new IllegalStateException("Duplicate story node ID: " + node.storyNodeId());
        }
        return Collections.unmodifiableMap(map);
    }

    /** Every story node of this chapter, keyed by node ID (read-only). */
    public static Map<String, StoryNode> nodes() {
        return NODES;
    }

    /** The prepared chapter: title, passage, EASY difficulty and the 5 reading-check questions. */
    public static Chapter chapter() {
        String passage = "The person woke up from bed, ate breakfast, showered, dressed up, and went to school. "
                + "He met his friend and greeted him. They went to their mathematics class first. "
                + "After math class, they ate lunch. Then they went back to class, and after that they went home.";
        return new Chapter(chapterId, "Chapter 0: Person Goes to Class", passage, Difficulty.EASY, List.of(
                new Question(q1Id, "Why did he wake up, eat breakfast, shower, and get dressed?",
                        List.of("To play with his friends", "To go to school", "To go to work"), 1),
                new Question(q2Id, "Who did he greet?",
                        List.of("Teacher", "Friend", "No one"), 1),
                new Question(q3Id, "Which class did they go to first?",
                        List.of("Science class", "English class", "Math class"), 2),
                new Question(q4Id, "What did they do after math class?",
                        List.of("Play", "Eat lunch", "Go to science class"), 1),
                new Question(q5Id, "What happened after lunch?",
                        List.of("They went back to class, then went home", "They went home right away",
                                "They played with friends, then went home"), 0)));
    }

    /** Saves the chapter into a chapter repository. Safe to call twice: the ID never changes. */
    public static void load(ChapterRepository chapters) {
        chapters.saveChapter(chapter());
    }

    /**
     * Makes a brand-new random UUID. Use it ONLY to create IDs for new content, then paste
     * the printed text into a constant. Never call it to fill the constants above.
     */
    public static UUID randomId() {
        return UUID.randomUUID();
    }
}