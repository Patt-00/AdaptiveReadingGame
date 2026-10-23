package com.adaptivereadinggame.repository.mysql;

import com.adaptivereadinggame.backend.BackendContext;
import com.adaptivereadinggame.content.StoryPack;
import com.adaptivereadinggame.content.StoryPackLoader;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Test-only entry point for checking saved assessment state across separate JVM processes. */
public final class MySqlProcessPersistenceProbe {
    private static final String PASSWORD = "StrongPass1!";

    private MySqlProcessPersistenceProbe() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1 || !List.of("seed", "resume").contains(arguments[0]))
            throw new IllegalArgumentException("Choose exactly one mode: seed or resume.");
        MySqlConfig config = MySqlConfig.fromEnvironment().orElseThrow();
        requireTestDatabase(config);
        String username = Objects.requireNonNull(System.getenv("ARG_PROCESS_SMOKE_USERNAME"),
                "Set ARG_PROCESS_SMOKE_USERNAME to a unique test username.");
        Path packPath = Path.of(Objects.requireNonNull(System.getenv("ARG_CONTENT_PACK"),
                "Set ARG_CONTENT_PACK to the test-only startup content pack."));
        StoryPack.ChapterPack chapter = new StoryPackLoader().read(packPath).chapters().getFirst();
        BackendContext backend = BackendContext.mysql(config);

        if (arguments[0].equals("seed")) seed(backend, chapter, packPath, username);
        else resume(backend, chapter, username);
    }

    private static void seed(BackendContext backend, StoryPack.ChapterPack chapter,
                             Path packPath, String username) throws Exception {
        int imported = backend.storyPackImporter().orElseThrow().importFile(packPath);
        if (imported != 1) throw new AssertionError("Expected one test chapter to import.");
        var player = backend.accountGameService();
        player.signup(username, "Process Smoke Reader", PASSWORD.toCharArray());
        player.login(username, PASSWORD.toCharArray());
        player.startChapter(chapter.chapter().id(), false);
        player.advanceStory();
        var session = player.startAssessment(chapter.chapter().id());
        var questions = player.questions(session);
        if (questions.size() != 2 || !player.submit(session, questions.getFirst().id(), 0))
            throw new AssertionError("The first process could not store the first answer.");
        player.save(1, false);
        player.logout(false);
        System.out.println("PROCESS_SEED_OK");
    }

    private static void resume(BackendContext backend, StoryPack.ChapterPack chapter, String username) {
        var player = backend.accountGameService();
        player.login(username, PASSWORD.toCharArray());
        var state = player.continueGame(false).orElseThrow(
                () -> new AssertionError("The second process could not find the saved chapter."));
        var firstQuestionId = chapter.chapter().questions().getFirst().id();
        if (!state.chapterId().equals(chapter.chapter().id()) || state.questionIndex() != 1
                || !Map.of(firstQuestionId, 0).equals(state.firstAnswers()))
            throw new AssertionError("The second process did not restore the authoritative first answer.");

        var session = Objects.requireNonNull(state.assessmentSessionId());
        var questions = player.questions(session);
        if (!questions.get(1).prompt().equals("Where does the reader continue after saving?"))
            throw new AssertionError("The second process resumed at the wrong question.");
        if (!player.submit(session, questions.get(1).id(), 0))
            throw new AssertionError("The second process could not store the next answer.");
        var result = player.complete(session);
        if (result.attempt().correctAnswers() != 2 || result.attempt().totalQuestions() != 2
                || player.history().size() != 1)
            throw new AssertionError("The second process did not persist the completed result.");
        player.logout(true);
        System.out.println("PROCESS_RESUME_OK");
    }

    private static void requireTestDatabase(MySqlConfig config) {
        URI address = URI.create("http" + config.url().substring("jdbc:mysql".length()));
        if (!List.of("localhost", "127.0.0.1", "::1").contains(address.getHost())
                || !"/adaptive_reading_test".equals(address.getPath()))
            throw new IllegalArgumentException("Process persistence probes may use only loopback adaptive_reading_test.");
    }
}
