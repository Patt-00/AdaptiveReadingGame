package com.adaptivereadinggame.content;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the playable draft against the supplied source, including dialogue-choice text. */
class SuppliedStoryboardTest {
    @Test void keepsTheAvailableNarrativeChoicesAndQuestionsThroughBothInvestigations() throws Exception {
        var pack = new StoryPackLoader().read(Path.of("assets/content/hailey-investigation-draft.json"));
        var item = pack.chapters().getFirst();
        List<String> content = new ArrayList<>();
        var characters = new CharacterAssetResolver();
        var images = new StoryAssetResolver();
        Path assets = Path.of("assets");
        item.nodes().forEach(node -> {
            content.add(node.dialogueText());
            node.storyChoices().forEach(choice -> content.add(choice.choiceText()));
            assertFalse(node.cgAssetKey().equals("cg-killer-caught"));
            if (!node.speakerAssetKey().isEmpty()) assertTrue(characters.findCharacterImage(node.speakerAssetKey(), assets).isPresent(), node.speakerAssetKey());
            if (!node.sceneAssetKey().isEmpty()) assertTrue(images.findSceneImage(node.sceneAssetKey(), assets).isPresent(), node.sceneAssetKey());
            if (!node.cgAssetKey().isEmpty()) assertTrue(images.findCgImage(node.cgAssetKey(), assets).isPresent(), node.cgAssetKey());
        });
        item.chapter().checkpoints().forEach(checkpoint -> content.add(checkpoint.passage()));
        item.chapter().questions().forEach(question -> {
            content.add(question.prompt());
            content.addAll(question.choices());
            assertTrue(question.explanation().startsWith("Provisional answer key:"));
        });
        String corpus = String.join("\n", content);
        Pattern speaker = Pattern.compile("^(?:(?:Lady|Lord|Sir|Prince)\\s+)?(Amelia|Eris|Davor|Owen|Ellis|Robert|Emrys|Hailey|Herald|MC|\\?\\?\\?)(?:\\s*\\([^)]*\\))?\\s*$");
        Pattern branch = Pattern.compile("IF\\s*\\((.*?)\\)");
        List<String> missing = new ArrayList<>();
        boolean script = false;
        for (String source : Files.readAllLines(Path.of("assets/content/source/storyboard-draft.txt"))) {
            if (source.contains("Script(intro)")) { script = true; continue; }
            if (source.contains("💀 Conclusion")) break;
            if (!script) continue;
            String line = source.replaceAll("\\[[a-z]{1,2}\\]", "").trim().replaceAll("[ \\t]+", " ")
                    .replace("Ellis", "Owen").replace("girl/boy", "servant")
                    .replace("MC, Was it?", "Servant, was it?")
                    .replace("Perhaps she’s overworked", "Perhaps they’re overworked")
                    .replace("opened with a loud creek", "opened with a loud creak");
            if (line.isBlank() || speaker.matcher(line).matches() || line.startsWith("***")
                    || line.startsWith("[") || line.matches("^[🫓🗣😵🔎📚☀].*")
                    || line.startsWith("MINIGAME") || line.startsWith("SELECT YOUR") || line.contains("DIFFICULTY")) continue;
            var choice = branch.matcher(line);
            if (choice.find()) {
                if (line.contains("Answer correctly") || line.contains("Answer incorrectly")) continue;
                line = choice.group(1).replaceAll("^[“”\"]+|[“”\"]+$", "");
            }
            line = line.replaceAll("^\\*?\\s*Option [A-D]:\\s*", "").replaceFirst("^Question: ", "");
            // The source prompt names an absent clue; the draft uses the described evidence instead.
            if (line.contains("Revoked Knighthood Decree")) continue;
            if (!corpus.contains(line)) missing.add(line);
        }
        assertTrue(missing.isEmpty(), () -> "Missing supplied story text: " + missing);
        assertTrue(item.nodes().stream().anyMatch(node -> node.speakerName().equals("You")
                && !node.thought() && node.dialogueText().contains("Her life is worth more than your political documents")));
        assertTrue(item.nodes().stream().anyMatch(node -> node.speakerName().equals("Eris")
                && node.dialogueText().contains("W-What are you doing with my handkerchief")));
        assertTrue(item.nodes().stream().anyMatch(node -> node.speakerName().equals("Owen")
                && node.dialogueText().contains("Robert... is that your administrative signet")));
        assertTrue(item.nodes().stream().anyMatch(node -> node.speakerName().equals("Robert")
                && node.dialogueText().contains("My signet is right here")));
        assertTrue(item.nodes().stream().anyMatch(node -> node.dialogueText().contains("CURRENT STORY DRAFT ENDS HERE")));
        assertTrue(item.nodes().stream().anyMatch(node -> node.dialogueText().contains("answer keys are provisional")));
    }
}
