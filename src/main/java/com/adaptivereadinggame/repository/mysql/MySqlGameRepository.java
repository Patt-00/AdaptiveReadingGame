package com.adaptivereadinggame.repository.mysql;

import com.adaptivereadinggame.model.*;
import com.adaptivereadinggame.repository.*;
import com.adaptivereadinggame.service.GameStateCodec;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** MySQL adapter for prepared content, assessments, first answers, and three save slots. */
public final class MySqlGameRepository implements ChapterRepository, AssessmentRepository,
        SaveGameRepository, SaveSnapshotRepository, StoryContentRepository, AssessmentDraftRepository {
    private record QuestionRow(UUID id, String prompt, int correctIndex, String explanation,
                               String checkpointId, Difficulty difficulty) {}
    private record DraftRow(UUID studentId, UUID chapterId, String storyNodeId, String checkpointId,
                            String routeId, String lastCompletedCheckpointId, Difficulty difficulty,
                            boolean completed, UUID sessionId) {}
    private record NodeRow(String id, String speaker, String dialogue, boolean thought,
                           String nextNodeId, boolean readingCheck, String speakerAssetKey,
                           String checkpointId, String sceneAssetKey, String cgAssetKey,
                           boolean storyComplete) {}

    private final ConnectionProvider connections;
    private final GameStateCodec codec;

    public MySqlGameRepository(ConnectionProvider connections) {
        this(connections, new GameStateCodec());
    }

    public MySqlGameRepository(ConnectionProvider connections, GameStateCodec codec) {
        this.connections = Objects.requireNonNull(connections);
        this.codec = Objects.requireNonNull(codec);
    }

    @Override public Optional<Chapter> findChapter(UUID id) {
        Objects.requireNonNull(id);
        return SqlSupport.read(connections, "chapter lookup", connection -> loadChapter(connection, id));
    }

    @Override public List<Chapter> findByDifficulty(Difficulty difficulty) {
        Objects.requireNonNull(difficulty);
        return SqlSupport.read(connections, "chapter list", connection -> {
            List<UUID> ids = new ArrayList<>();
            try (var statement = connection.prepareStatement(
                    "SELECT c.id FROM chapters c WHERE c.available_for_new_game = TRUE AND "
                            + "(c.difficulty = ? OR EXISTS ("
                            + "SELECT 1 FROM questions q WHERE q.chapter_id = c.id AND q.question_difficulty = ?)) "
                            + "ORDER BY c.title, c.id")) {
                statement.setString(1, difficulty.name());
                statement.setString(2, difficulty.name());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) ids.add(SqlSupport.uuid(rows.getString(1)));
                }
            }
            List<Chapter> chapters = new ArrayList<>(ids.size());
            for (UUID id : ids) loadChapter(connection, id).ifPresent(chapters::add);
            return List.copyOf(chapters);
        });
    }

    /** Published chapter IDs are immutable; edited content must receive a new stable ID. */
    @Override public Chapter saveChapter(Chapter chapter, boolean availableForNewGame) {
        Objects.requireNonNull(chapter);
        return SqlSupport.transaction(connections, "chapter write", connection -> {
            Optional<Chapter> existing = loadChapter(connection, chapter.id());
            if (existing.isPresent()) {
                if (!existing.get().equals(chapter))
                    throw new IllegalStateException("Prepared chapter content is immutable after it is stored.");
                try (var statement = connection.prepareStatement(
                        "UPDATE chapters SET available_for_new_game = ? WHERE id = ?")) {
                    statement.setBoolean(1, availableForNewGame);
                    statement.setString(2, SqlSupport.id(chapter.id()));
                    statement.executeUpdate();
                }
                return chapter;
            }
            try (var statement = connection.prepareStatement(
                    "INSERT INTO chapters(id, title, passage, difficulty, available_for_new_game) VALUES (?, ?, ?, ?, ?)")) {
                statement.setString(1, SqlSupport.id(chapter.id()));
                statement.setString(2, chapter.title());
                statement.setString(3, chapter.passage());
                statement.setString(4, chapter.difficulty().name());
                statement.setBoolean(5, availableForNewGame);
                statement.executeUpdate();
            }
            int order = 1;
            for (Question question : chapter.questions()) {
                insertQuestion(connection, chapter.id(), order++, question);
            }
            for (StoryCheckpoint checkpoint : chapter.checkpoints())
                insertStoryCheckpoint(connection, chapter.id(), checkpoint);
            return chapter;
        });
    }

    /**
     * Persists a complete prepared scene graph after its chapter/questions exist.
     * This is a content-loader operation, not a story-authoring feature. Content is
     * immutable once drafts, results, or saves refer to the chapter.
     */
    @Override public void saveStoryNodes(UUID chapterId, List<StoryNode> nodes, String firstNodeId) {
        saveStoryNodes(chapterId, nodes, firstNodeId, List.of());
    }

    @Override public void saveStoryNodes(UUID chapterId, List<StoryNode> nodes, String firstNodeId,
                                         List<StoryCheckpoint> checkpoints) {
        Objects.requireNonNull(chapterId);
        Objects.requireNonNull(nodes);
        checkpoints = List.copyOf(checkpoints);
        if (checkpoints.isEmpty()) StoryGraphValidator.validate(nodes, firstNodeId);
        else StoryGraphValidator.validateAdaptive(nodes, firstNodeId, checkpoints);
        final List<StoryCheckpoint> checkpointDefinitions = checkpoints;
        Map<String, StoryNode> byId = new LinkedHashMap<>();
        for (StoryNode node : nodes) {
            byId.put(node.storyNodeId(), node);
        }
        SqlSupport.transaction(connections, "prepared story write", connection -> {
            requireChapter(connection, chapterId);
            if (storyGraphMatches(connection, chapterId, nodes, firstNodeId, checkpointDefinitions)) return null;
            if (contentInUse(connection, chapterId))
                throw new IllegalStateException("Story content cannot change after a draft, result, or save exists.");
            try (var statement = connection.prepareStatement("DELETE FROM story_checkpoints WHERE chapter_id = ?")) {
                statement.setString(1, SqlSupport.id(chapterId)); statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("DELETE FROM chapter_start_nodes WHERE chapter_id = ?")) {
                statement.setString(1, SqlSupport.id(chapterId)); statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("DELETE FROM story_choices WHERE chapter_id = ?")) {
                statement.setString(1, SqlSupport.id(chapterId)); statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("DELETE FROM story_nodes WHERE chapter_id = ?")) {
                statement.setString(1, SqlSupport.id(chapterId)); statement.executeUpdate();
            }
            for (StoryNode node : nodes) insertStoryNode(connection, chapterId, node);
            for (StoryNode node : nodes) insertStoryChoices(connection, chapterId, node);
            for (StoryCheckpoint checkpoint : checkpointDefinitions) insertStoryCheckpoint(connection, chapterId, checkpoint);
            try (var statement = connection.prepareStatement(
                    "INSERT INTO chapter_start_nodes(chapter_id, node_id) VALUES (?, ?)")) {
                statement.setString(1, SqlSupport.id(chapterId)); statement.setString(2, firstNodeId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override public Optional<String> firstNodeId(UUID chapterId) {
        Objects.requireNonNull(chapterId);
        return SqlSupport.read(connections, "story start-node lookup", connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT node_id FROM chapter_start_nodes WHERE chapter_id = ?")) {
                statement.setString(1, SqlSupport.id(chapterId));
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(rows.getString(1)) : Optional.empty();
                }
            }
        });
    }

    @Override public Optional<StoryNode> findNode(UUID chapterId, String storyNodeId) {
        Objects.requireNonNull(chapterId);
        Objects.requireNonNull(storyNodeId);
        return SqlSupport.read(connections, "story node lookup", connection -> loadNode(connection, chapterId, storyNodeId));
    }

    private static Optional<Chapter> loadChapter(Connection connection, UUID id) throws SQLException {
        String title;
        String passage;
        Difficulty difficulty;
        try (var statement = connection.prepareStatement(
                "SELECT title, passage, difficulty FROM chapters WHERE id = ?")) {
            statement.setString(1, SqlSupport.id(id));
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                title = rows.getString("title");
                passage = rows.getString("passage");
                difficulty = Difficulty.valueOf(rows.getString("difficulty"));
            }
        }
        List<QuestionRow> questionRows = new ArrayList<>();
        try (var statement = connection.prepareStatement(
                "SELECT id, prompt, correct_choice_index, explanation, checkpoint_id, question_difficulty "
                        + "FROM questions WHERE chapter_id = ? ORDER BY question_order")) {
            statement.setString(1, SqlSupport.id(id));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) questionRows.add(new QuestionRow(SqlSupport.uuid(rows.getString("id")),
                        rows.getString("prompt"), rows.getInt("correct_choice_index"), rows.getString("explanation"),
                        rows.getString("checkpoint_id"), rows.getString("question_difficulty") == null ? null
                                : Difficulty.valueOf(rows.getString("question_difficulty"))));
            }
        }
        List<Question> questions = new ArrayList<>(questionRows.size());
        for (QuestionRow question : questionRows) {
            List<String> choices = new ArrayList<>();
            try (var statement = connection.prepareStatement(
                    "SELECT choice_text FROM question_choices WHERE question_id = ? ORDER BY choice_index")) {
                statement.setString(1, SqlSupport.id(question.id()));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) choices.add(rows.getString(1));
                }
            }
            try {
                questions.add(new Question(question.id(), question.prompt(), choices, question.correctIndex(),
                        question.explanation(), question.checkpointId(), question.difficulty()));
            } catch (IllegalArgumentException malformed) {
                throw new SQLException("Stored question choices do not match its answer key.", malformed);
            }
        }
        try {
            List<StoryCheckpoint> checkpoints = loadStoryCheckpoints(connection, id, questions);
            return Optional.of(new Chapter(id, title, passage, difficulty, questions, checkpoints));
        } catch (IllegalArgumentException malformed) {
            throw new SQLException("Stored chapter content is invalid.", malformed);
        }
    }

    private static List<StoryCheckpoint> loadStoryCheckpoints(Connection connection, UUID chapterId,
                                                               List<Question> questions) throws SQLException {
        List<StoryCheckpoint> checkpoints = new ArrayList<>();
        try (var statement = connection.prepareStatement(
                "SELECT checkpoint_id, node_id, passage, correct_next_node_id, incorrect_next_node_id "
                        + "FROM story_checkpoints WHERE chapter_id = ? ORDER BY checkpoint_id")) {
            statement.setString(1, SqlSupport.id(chapterId));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String checkpointId = rows.getString("checkpoint_id");
                    Map<Difficulty, List<Question>> variants = new EnumMap<>(Difficulty.class);
                    for (Difficulty level : Difficulty.values()) {
                        variants.put(level, questions.stream().filter(question -> checkpointId.equals(question.checkpointId())
                                && question.difficulty() == level).toList());
                    }
                    checkpoints.add(new StoryCheckpoint(checkpointId, rows.getString("node_id"),
                            rows.getString("passage"), variants,
                            rows.getString("correct_next_node_id"), rows.getString("incorrect_next_node_id")));
                }
            }
        } catch (IllegalArgumentException malformed) {
            throw new SQLException("Stored adaptive checkpoint content is invalid.", malformed);
        }
        return List.copyOf(checkpoints);
    }

    private static void insertQuestion(Connection connection, UUID chapterId, int order, Question question)
            throws SQLException {
        if (question.prompt() == null || question.prompt().isBlank())
            throw new IllegalArgumentException("A question prompt is required.");
        if (question.choices().size() > 65_535)
            throw new IllegalArgumentException("A question has too many answer choices.");
        try (var statement = connection.prepareStatement(
                "INSERT INTO questions(id, chapter_id, question_order, prompt, correct_choice_index, explanation, "
                        + "checkpoint_id, question_difficulty) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, SqlSupport.id(question.id()));
            statement.setString(2, SqlSupport.id(chapterId));
            statement.setInt(3, order);
            statement.setString(4, question.prompt());
            statement.setInt(5, question.correctChoiceIndex());
            statement.setString(6, question.explanation());
            statement.setString(7, question.checkpointId());
            if (question.difficulty() == null) statement.setNull(8, java.sql.Types.VARCHAR);
            else statement.setString(8, question.difficulty().name());
            statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement(
                "INSERT INTO question_choices(question_id, choice_index, choice_text) VALUES (?, ?, ?)")) {
            for (int index = 0; index < question.choices().size(); index++) {
                statement.setString(1, SqlSupport.id(question.id()));
                statement.setInt(2, index);
                statement.setString(3, question.choices().get(index));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Optional<StoryNode> loadNode(Connection connection, UUID chapterId, String storyNodeId)
            throws SQLException {
        NodeRow row;
        try (var statement = connection.prepareStatement(
                "SELECT node_id, speaker_name, dialogue_text, is_thought, next_node_id, reading_check, speaker_asset_key, "
                        + "checkpoint_id, scene_asset_key, cg_asset_key, story_complete "
                        + "FROM story_nodes WHERE chapter_id = ? AND node_id = ?")) {
            statement.setString(1, SqlSupport.id(chapterId)); statement.setString(2, storyNodeId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                row = new NodeRow(rows.getString("node_id"), rows.getString("speaker_name"),
                        rows.getString("dialogue_text"), rows.getBoolean("is_thought"),
                        rows.getString("next_node_id"), rows.getBoolean("reading_check"),
                        rows.getString("speaker_asset_key"), rows.getString("checkpoint_id"),
                        rows.getString("scene_asset_key"), rows.getString("cg_asset_key"),
                        rows.getBoolean("story_complete"));
            }
        }
        List<StoryChoice> choices = new ArrayList<>();
        try (var statement = connection.prepareStatement(
                "SELECT choice_id, choice_text, to_node_id, route_id FROM story_choices "
                        + "WHERE chapter_id = ? AND from_node_id = ? ORDER BY choice_order")) {
            statement.setString(1, SqlSupport.id(chapterId)); statement.setString(2, storyNodeId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) choices.add(new StoryChoice(rows.getString("choice_id"),
                        rows.getString("choice_text"), rows.getString("to_node_id"), rows.getString("route_id")));
            }
        }
        return Optional.of(new StoryNode(row.id(), row.speaker(), row.dialogue(), row.thought(), choices,
                row.nextNodeId() == null ? "" : row.nextNodeId(), row.readingCheck(), row.speakerAssetKey(),
                row.checkpointId(), row.sceneAssetKey(), row.cgAssetKey(), row.storyComplete()));
    }

    private static void insertStoryNode(Connection connection, UUID chapterId, StoryNode node) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO story_nodes(chapter_id, node_id, speaker_name, dialogue_text, is_thought, next_node_id, "
                        + "reading_check, speaker_asset_key, checkpoint_id, scene_asset_key, cg_asset_key, story_complete) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, SqlSupport.id(chapterId));
            statement.setString(2, node.storyNodeId());
            statement.setString(3, node.speakerName());
            statement.setString(4, node.dialogueText());
            statement.setBoolean(5, node.thought());
            if (node.nextNodeId().isEmpty()) statement.setNull(6, java.sql.Types.VARCHAR);
            else statement.setString(6, node.nextNodeId());
            statement.setBoolean(7, node.readingCheck());
            statement.setString(8, node.speakerAssetKey());
            statement.setString(9, node.checkpointId());
            statement.setString(10, node.sceneAssetKey());
            statement.setString(11, node.cgAssetKey());
            statement.setBoolean(12, node.storyComplete());
            statement.executeUpdate();
        }
    }

    private static boolean storyGraphMatches(Connection connection, UUID chapterId,
            List<StoryNode> expectedNodes, String expectedFirstNodeId,
            List<StoryCheckpoint> expectedCheckpoints) throws SQLException {
        String storedFirst;
        try (var statement = connection.prepareStatement(
                "SELECT node_id FROM chapter_start_nodes WHERE chapter_id = ?")) {
            statement.setString(1, SqlSupport.id(chapterId));
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return false;
                storedFirst = rows.getString(1);
            }
        }
        if (!Objects.equals(storedFirst, expectedFirstNodeId)) return false;
        try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM story_nodes WHERE chapter_id = ?")) {
            statement.setString(1, SqlSupport.id(chapterId));
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                if (rows.getInt(1) != expectedNodes.size()) return false;
            }
        }
        for (StoryNode expected : expectedNodes) {
            if (!loadNode(connection, chapterId, expected.storyNodeId()).filter(expected::equals).isPresent()) return false;
        }
        List<StoryCheckpoint> stored = loadChapter(connection, chapterId)
                .map(Chapter::checkpoints).orElse(List.of());
        if (stored.size() != expectedCheckpoints.size()) return false;
        Map<String, StoryCheckpoint> expectedById = new HashMap<>();
        expectedCheckpoints.forEach(checkpoint -> expectedById.put(checkpoint.id(), checkpoint));
        return stored.stream().allMatch(checkpoint -> checkpoint.equals(expectedById.get(checkpoint.id())));
    }

    private static void insertStoryCheckpoint(Connection connection, UUID chapterId,
                                              StoryCheckpoint checkpoint) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO story_checkpoints(chapter_id, checkpoint_id, node_id, passage, correct_next_node_id, incorrect_next_node_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, SqlSupport.id(chapterId));
            statement.setString(2, checkpoint.id());
            statement.setString(3, checkpoint.nodeId());
            statement.setString(4, checkpoint.passage());
            statement.setString(5, checkpoint.correctNextNodeId());
            statement.setString(6, checkpoint.incorrectNextNodeId());
            statement.executeUpdate();
        }
    }

    private static void insertStoryChoices(Connection connection, UUID chapterId, StoryNode node) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO story_choices(chapter_id, from_node_id, choice_id, choice_order, choice_text, to_node_id, route_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            int order = 1;
            for (StoryChoice choice : node.storyChoices()) {
                statement.setString(1, SqlSupport.id(chapterId));
                statement.setString(2, node.storyNodeId());
                statement.setString(3, choice.choiceId());
                statement.setInt(4, order++);
                statement.setString(5, choice.choiceText());
                statement.setString(6, choice.nextNodeId());
                statement.setString(7, choice.routeId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override public Optional<AssessmentResult> findCompleted(UUID studentId, UUID chapterId) {
        return findCompleted(studentId, chapterId, "");
    }

    @Override public Optional<AssessmentResult> findCompleted(UUID studentId, UUID chapterId, String checkpointId) {
        Objects.requireNonNull(studentId); Objects.requireNonNull(chapterId);
        return SqlSupport.read(connections, "assessment lookup", connection ->
                loadAssessment(connection, studentId, chapterId, Objects.requireNonNullElse(checkpointId, "")));
    }

    @Override public List<AssessmentResult> resultsForChapter(UUID studentId, UUID chapterId) {
        Objects.requireNonNull(studentId); Objects.requireNonNull(chapterId);
        return SqlSupport.read(connections, "chapter assessment results", connection -> {
            List<AssessmentResult> values = new ArrayList<>();
            try (var statement = connection.prepareStatement(
                    "SELECT id, student_id, chapter_id, checkpoint_id, correct_answers, total_questions, "
                            + "assessed_difficulty, next_difficulty, decision_source, completed_at "
                            + "FROM assessments WHERE student_id = ? AND chapter_id = ? ORDER BY completed_at, id")) {
                statement.setString(1, SqlSupport.id(studentId));
                statement.setString(2, SqlSupport.id(chapterId));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) values.add(readAssessment(rows));
                }
            }
            return List.copyOf(values);
        });
    }

    @Override public List<AssessmentResult> historyFor(UUID studentId) {
        Objects.requireNonNull(studentId);
        return SqlSupport.read(connections, "assessment history lookup", connection -> {
            List<AssessmentResult> history = new ArrayList<>();
            try (var statement = connection.prepareStatement(
                    "SELECT id, student_id, chapter_id, checkpoint_id, correct_answers, total_questions, "
                            + "assessed_difficulty, next_difficulty, decision_source, completed_at "
                            + "FROM assessments WHERE student_id = ? ORDER BY completed_at, id")) {
                statement.setString(1, SqlSupport.id(studentId));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) history.add(readAssessment(rows));
                }
            }
            return List.copyOf(history);
        });
    }

    @Override public AssessmentResult saveCompleted(AssessmentResult result) {
        Objects.requireNonNull(result);
        Attempt attempt = result.attempt();
        try {
            return SqlSupport.transaction(connections, "assessment completion", connection -> {
                Optional<AssessmentResult> existing = loadAssessment(connection, attempt.studentId(), attempt.materialId(), attempt.checkpointId());
                if (existing.isPresent()) return existing.get();
                DraftRow draft = lockDraft(connection, attempt.studentId(), attempt.materialId(), attempt.checkpointId());
                if (draft == null) throw new IllegalStateException("A stored first-answer draft is required to complete this chapter.");
                existing = loadAssessment(connection, attempt.studentId(), attempt.materialId(), attempt.checkpointId());
                if (existing.isPresent()) return existing.get();
                if (draft.completed()) throw new IllegalStateException("This assessment draft is already complete.");
                if (!draft.checkpointId().equals(attempt.checkpointId()))
                    throw new IllegalArgumentException("Assessment checkpoint does not match its stored draft.");

                Chapter chapter = loadChapter(connection, attempt.materialId())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown chapter."));
                Difficulty expectedDifficulty = draft.difficulty() == null ? chapter.difficulty() : draft.difficulty();
                if (result.assessedDifficulty() != expectedDifficulty)
                    throw new IllegalArgumentException("Assessment difficulty does not match the prepared chapter.");
                if (attempt.totalQuestions() != chapter.questions(draft.checkpointId(), expectedDifficulty).size())
                    throw new IllegalArgumentException("Assessment result does not contain every checkpoint question.");
                if (Math.abs(result.nextDifficulty().ordinal() - result.assessedDifficulty().ordinal()) > 1)
                    throw new IllegalArgumentException("The next difficulty may move only one level.");
                validateDraftScore(connection, draft.sessionId(), attempt.totalQuestions(), attempt.correctAnswers(),
                        draft.checkpointId(), draft.difficulty());

                try (var statement = connection.prepareStatement(
                        "INSERT INTO assessments(id, student_id, chapter_id, checkpoint_id, correct_answers, total_questions, "
                                + "assessed_difficulty, next_difficulty, decision_source, completed_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    statement.setString(1, SqlSupport.id(attempt.id()));
                    statement.setString(2, SqlSupport.id(attempt.studentId()));
                    statement.setString(3, SqlSupport.id(attempt.materialId()));
                    statement.setString(4, attempt.checkpointId());
                    statement.setInt(5, attempt.correctAnswers());
                    statement.setInt(6, attempt.totalQuestions());
                    statement.setString(7, result.assessedDifficulty().name());
                    statement.setString(8, result.nextDifficulty().name());
                    statement.setString(9, result.decisionSource().name());
                    statement.setObject(10, SqlSupport.utc(attempt.completedAt()));
                    statement.executeUpdate();
                } catch (SQLException duplicate) {
                    if (!SqlSupport.isConstraintViolation(duplicate)) throw duplicate;
                    Optional<AssessmentResult> raced = loadAssessment(connection, attempt.studentId(), attempt.materialId(), attempt.checkpointId());
                    if (raced.isPresent()) return raced.get();
                    throw duplicate;
                }
                try (var statement = connection.prepareStatement(
                        "UPDATE assessment_drafts SET completed_at = ? WHERE session_id = ? AND completed_at IS NULL")) {
                    statement.setObject(1, SqlSupport.utc(attempt.completedAt()));
                    statement.setString(2, SqlSupport.id(draft.sessionId()));
                    if (statement.executeUpdate() != 1)
                        throw new IllegalStateException("Assessment draft could not be marked complete.");
                }
                return loadAssessment(connection, attempt.studentId(), attempt.materialId(), attempt.checkpointId())
                        .orElseThrow(() -> new IllegalStateException("The saved assessment result could not be read back."));
            });
        } catch (RuntimeException failure) {
            if (SqlSupport.isConstraintViolation(failure.getCause() instanceof SQLException sql ? sql : null))
                throw new IllegalArgumentException("The assessment result violates a stored-data constraint.", failure);
            throw failure;
        }
    }

    private static DraftRow lockDraft(Connection connection, UUID studentId, UUID chapterId, String checkpointId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT session_id, student_id, chapter_id, story_node_id, checkpoint_id, route_id, "
                        + "last_completed_checkpoint_id, active_difficulty, completed_at "
                        + "FROM assessment_drafts WHERE student_id = ? AND chapter_id = ? AND checkpoint_id = ? FOR UPDATE")) {
            statement.setString(1, SqlSupport.id(studentId));
            statement.setString(2, SqlSupport.id(chapterId));
            statement.setString(3, checkpointId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                return new DraftRow(SqlSupport.uuid(rows.getString("student_id")), SqlSupport.uuid(rows.getString("chapter_id")),
                        rows.getString("story_node_id"), rows.getString("checkpoint_id"), rows.getString("route_id"),
                        rows.getString("last_completed_checkpoint_id"),
                        rows.getString("active_difficulty") == null ? null : Difficulty.valueOf(rows.getString("active_difficulty")),
                        rows.getTimestamp("completed_at") != null,
                        SqlSupport.uuid(rows.getString("session_id")));
            }
        }
    }

    private static void validateDraftScore(Connection connection, UUID sessionId, int total, int correct,
                                           String checkpointId, Difficulty difficulty)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) AS answer_count, COALESCE(SUM(CASE WHEN a.selected_choice_index = q.correct_choice_index "
                        + "THEN 1 ELSE 0 END), 0) AS correct_count "
                        + "FROM assessment_draft_answers a JOIN questions q "
                        + "ON q.id = a.question_id AND q.chapter_id = a.chapter_id WHERE a.session_id = ? "
                        + "AND q.checkpoint_id = ? AND (q.question_difficulty = ? OR (? = '' AND q.question_difficulty IS NULL))")) {
            statement.setString(1, SqlSupport.id(sessionId));
            statement.setString(2, checkpointId);
            statement.setString(3, difficulty == null ? "" : difficulty.name());
            statement.setString(4, checkpointId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                if (rows.getInt("answer_count") != total || rows.getInt("correct_count") != correct)
                    throw new IllegalArgumentException("The result does not match the stored first answers.");
            }
        }
    }

    private static Optional<AssessmentResult> loadAssessment(Connection connection, UUID studentId, UUID chapterId)
            throws SQLException {
        return loadAssessment(connection, studentId, chapterId, "");
    }

    private static Optional<AssessmentResult> loadAssessment(Connection connection, UUID studentId, UUID chapterId,
                                                              String checkpointId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT id, student_id, chapter_id, checkpoint_id, correct_answers, total_questions, assessed_difficulty, "
                        + "next_difficulty, decision_source, completed_at FROM assessments "
                        + "WHERE student_id = ? AND chapter_id = ? AND checkpoint_id = ?")) {
            statement.setString(1, SqlSupport.id(studentId));
            statement.setString(2, SqlSupport.id(chapterId));
            statement.setString(3, checkpointId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readAssessment(rows)) : Optional.empty();
            }
        }
    }

    private static AssessmentResult readAssessment(ResultSet rows) throws SQLException {
        Attempt attempt = new Attempt(SqlSupport.uuid(rows.getString("id")),
                SqlSupport.uuid(rows.getString("student_id")), SqlSupport.uuid(rows.getString("chapter_id")),
                rows.getString("checkpoint_id"),
                rows.getInt("correct_answers"), rows.getInt("total_questions"),
                SqlSupport.instant(rows.getObject("completed_at", java.time.LocalDateTime.class)));
        return new AssessmentResult(attempt, Difficulty.valueOf(rows.getString("assessed_difficulty")),
                Difficulty.valueOf(rows.getString("next_difficulty")),
                DifficultyDecisionSource.valueOf(rows.getString("decision_source")));
    }

    @Override public void createDraft(GameState gameState) {
        Objects.requireNonNull(gameState);
        if (gameState.assessmentSessionId() == null || gameState.questionIndex() != 0
                || !gameState.firstAnswers().isEmpty())
            throw new IllegalArgumentException("A new assessment draft must have a session ID and no answers.");
        SqlSupport.transaction(connections, "assessment draft creation", connection -> {
            requireStudent(connection, gameState.studentId());
            Chapter chapter = loadChapter(connection, gameState.chapterId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown chapter."));
            StoryNode node = loadNode(connection, gameState.chapterId(), gameState.storyNodeId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown story node."));
            if (!node.readingCheck()) throw new IllegalArgumentException("An assessment can start only at a reading-check node.");
            String checkpointId = gameState.activeCheckpointId();
            Difficulty activeDifficulty = gameState.activeDifficulty() == null
                    ? chapter.difficulty() : gameState.activeDifficulty();
            if (chapter.adaptive()) {
                var checkpoint = chapter.checkpointAtNode(node.storyNodeId())
                        .orElseThrow(() -> new IllegalArgumentException("The reading-check node has no checkpoint definition."));
                if (!checkpoint.id().equals(checkpointId))
                    throw new IllegalArgumentException("The draft checkpoint does not match its reading-check node.");
                if (gameState.activeDifficulty() == null)
                    throw new IllegalArgumentException("An adaptive checkpoint draft needs a selected difficulty.");
            } else if (!checkpointId.isEmpty()) {
                throw new IllegalArgumentException("A legacy chapter cannot start an adaptive checkpoint.");
            }
            if (loadAssessment(connection, gameState.studentId(), gameState.chapterId(), checkpointId).isPresent())
                throw new IllegalStateException("This reading checkpoint already has a completed assessment.");
            if (chapter.questions(checkpointId, activeDifficulty).isEmpty())
                throw new IllegalStateException("This reading checkpoint has no questions at the selected difficulty.");
            try (var statement = connection.prepareStatement(
                    "INSERT INTO assessment_drafts(session_id, student_id, chapter_id, story_node_id, checkpoint_id, route_id, "
                            + "last_completed_checkpoint_id, active_difficulty, created_at, completed_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)")) {
                statement.setString(1, SqlSupport.id(gameState.assessmentSessionId()));
                statement.setString(2, SqlSupport.id(gameState.studentId()));
                statement.setString(3, SqlSupport.id(gameState.chapterId()));
                statement.setString(4, gameState.storyNodeId());
                statement.setString(5, checkpointId);
                statement.setString(6, gameState.routeId());
                statement.setString(7, gameState.lastCompletedCheckpointId());
                if (gameState.activeDifficulty() == null) statement.setNull(8, java.sql.Types.VARCHAR);
                else statement.setString(8, gameState.activeDifficulty().name());
                statement.setObject(9, SqlSupport.utc(Instant.now()));
                statement.executeUpdate();
            } catch (SQLException duplicate) {
                if (SqlSupport.isConstraintViolation(duplicate))
                    throw new IllegalArgumentException("An assessment draft already exists for this learner or session.", duplicate);
                throw duplicate;
            }
            return null;
        });
    }

    @Override public int recordFirstAnswer(UUID sessionId, UUID studentId, UUID questionId, int selectedIndex) {
        Objects.requireNonNull(sessionId); Objects.requireNonNull(studentId); Objects.requireNonNull(questionId);
        if (selectedIndex < 0 || selectedIndex > 65_535) throw new IllegalArgumentException("Invalid choice index.");
        return SqlSupport.transaction(connections, "first-answer write", connection -> {
            DraftRow draft = lockDraftBySession(connection, sessionId);
            if (draft == null) throw new IllegalArgumentException("Unknown assessment session.");
            if (!draft.studentId().equals(studentId)) throw new SecurityException("Assessment belongs to another learner.");
            if (draft.completed()) throw new IllegalStateException("This assessment is already complete.");
            if (loadAssessment(connection, studentId, draft.chapterId(), draft.checkpointId()).isPresent())
                throw new IllegalStateException("A completed assessment cannot accept another answer.");
            if (!questionBelongsToCheckpoint(connection, questionId, draft.chapterId(),
                    draft.checkpointId(), draft.difficulty()))
                throw new IllegalArgumentException("Question does not belong to this assessment.");
            if (!choiceExists(connection, questionId, selectedIndex))
                throw new IllegalArgumentException("Selected answer is not available for this question.");

            Integer original = firstAnswer(connection, sessionId, questionId);
            if (original != null) return original;
            int answeredCount = countDraftAnswers(connection, sessionId);
            UUID expectedQuestion = questionAtOrder(connection, draft.chapterId(), draft.checkpointId(),
                    draft.difficulty(), answeredCount + 1);
            if (!questionId.equals(expectedQuestion))
                throw new IllegalStateException("Answer the current reading-check question first.");

            try (var statement = connection.prepareStatement(
                    "INSERT INTO assessment_draft_answers(session_id, student_id, chapter_id, question_id, "
                            + "selected_choice_index, answered_at) VALUES (?, ?, ?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE selected_choice_index = selected_choice_index")) {
                statement.setString(1, SqlSupport.id(sessionId));
                statement.setString(2, SqlSupport.id(studentId));
                statement.setString(3, SqlSupport.id(draft.chapterId()));
                statement.setString(4, SqlSupport.id(questionId));
                statement.setInt(5, selectedIndex);
                statement.setObject(6, SqlSupport.utc(Instant.now()));
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement(
                    "SELECT selected_choice_index FROM assessment_draft_answers WHERE session_id = ? AND question_id = ?")) {
                statement.setString(1, SqlSupport.id(sessionId)); statement.setString(2, SqlSupport.id(questionId));
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new IllegalStateException("First answer was not saved.");
                    return rows.getInt(1);
                }
            }
        });
    }

    private static Integer firstAnswer(Connection connection, UUID sessionId, UUID questionId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT selected_choice_index FROM assessment_draft_answers WHERE session_id = ? AND question_id = ?")) {
            statement.setString(1, SqlSupport.id(sessionId));
            statement.setString(2, SqlSupport.id(questionId));
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getInt(1) : null; }
        }
    }

    private static int countDraftAnswers(Connection connection, UUID sessionId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM assessment_draft_answers WHERE session_id = ?")) {
            statement.setString(1, SqlSupport.id(sessionId));
            try (ResultSet rows = statement.executeQuery()) { rows.next(); return rows.getInt(1); }
        }
    }

    private static UUID questionAtOrder(Connection connection, UUID chapterId, String checkpointId,
                                        Difficulty difficulty, int order) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT id FROM questions WHERE chapter_id = ? AND checkpoint_id = ? "
                        + "AND (question_difficulty = ? OR (? = '' AND question_difficulty IS NULL)) "
                        + "ORDER BY question_order LIMIT 1 OFFSET ?")) {
            statement.setString(1, SqlSupport.id(chapterId));
            statement.setString(2, checkpointId);
            statement.setString(3, difficulty == null ? "" : difficulty.name());
            statement.setString(4, checkpointId);
            statement.setInt(5, order - 1);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? SqlSupport.uuid(rows.getString(1)) : null;
            }
        }
    }

    @Override public Optional<GameState> findDraft(UUID sessionId) {
        Objects.requireNonNull(sessionId);
        return SqlSupport.read(connections, "assessment draft lookup", connection -> loadDraft(connection, sessionId));
    }

    @Override public Optional<SaveGame> findSlot(UUID studentId, int slot) {
        Objects.requireNonNull(studentId); requireSlot(slot);
        return SqlSupport.read(connections, "save-slot lookup", connection -> loadSave(connection, studentId, slot));
    }

    @Override public List<SaveGame> slotsFor(UUID studentId) {
        Objects.requireNonNull(studentId);
        return SqlSupport.read(connections, "save-slot list", connection -> {
            List<SaveGame> saves = new ArrayList<>();
            try (var statement = connection.prepareStatement(
                    "SELECT student_id, slot, chapter_id, story_node_id, difficulty, saved_at, route_id, checkpoint_id "
                            + "FROM save_slots WHERE student_id = ? ORDER BY slot")) {
                statement.setString(1, SqlSupport.id(studentId));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) saves.add(readSave(rows));
                }
            }
            return List.copyOf(saves);
        });
    }

    /** A legacy/basic checkpoint overwrite intentionally clears any prior full snapshot in that slot. */
    @Override public SaveGame saveSlot(SaveGame save) {
        Objects.requireNonNull(save); requireSlot(save.slot());
        return SqlSupport.transaction(connections, "basic save-slot write", connection -> {
            validateCheckpoint(connection, save);
            upsertSave(connection, save, null);
            return normalizeSave(save);
        });
    }

    @Override public SaveSnapshot saveSnapshot(SaveSnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        return SqlSupport.transaction(connections, "full save write", connection -> {
            validateCheckpoint(connection, snapshot.checkpoint());
            validateGameState(connection, snapshot.gameState(), true);
            String json = codec.encode(snapshot.gameState());
            upsertSave(connection, snapshot.checkpoint(), json);
            return loadSnapshot(connection, snapshot.checkpoint().studentId(), snapshot.checkpoint().slot())
                    .orElseThrow(() -> new IllegalStateException("Stored full save could not be read back."));
        });
    }

    @Override public Optional<SaveSnapshot> findSnapshot(UUID studentId, int slot) {
        Objects.requireNonNull(studentId); requireSlot(slot);
        return SqlSupport.read(connections, "full save lookup", connection -> loadSnapshot(connection, studentId, slot));
    }

    @Override public List<SaveSnapshot> snapshotsFor(UUID studentId) {
        Objects.requireNonNull(studentId);
        return SqlSupport.read(connections, "full save list", connection -> {
            List<SaveSnapshot> snapshots = new ArrayList<>();
            try (var statement = connection.prepareStatement(
                    "SELECT slot FROM save_slots WHERE student_id = ? AND game_state_json IS NOT NULL ORDER BY slot")) {
                statement.setString(1, SqlSupport.id(studentId));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) loadSnapshot(connection, studentId, rows.getInt("slot")).ifPresent(snapshots::add);
                }
            }
            return List.copyOf(snapshots);
        });
    }

    private static void upsertSave(Connection connection, SaveGame save, String gameStateJson) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO save_slots(student_id, slot, chapter_id, story_node_id, difficulty, saved_at, game_state_json, route_id, checkpoint_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                        + "chapter_id = VALUES(chapter_id), story_node_id = VALUES(story_node_id), "
                        + "difficulty = VALUES(difficulty), saved_at = VALUES(saved_at), game_state_json = VALUES(game_state_json), "
                        + "route_id = VALUES(route_id), checkpoint_id = VALUES(checkpoint_id)")) {
            statement.setString(1, SqlSupport.id(save.studentId()));
            statement.setInt(2, save.slot());
            statement.setString(3, SqlSupport.id(save.chapterId()));
            statement.setString(4, save.storyNodeId());
            statement.setString(5, save.difficulty().name());
            statement.setObject(6, SqlSupport.utc(save.savedAt()));
            if (gameStateJson == null) statement.setNull(7, java.sql.Types.VARCHAR);
            else statement.setString(7, gameStateJson);
            statement.setString(8, save.routeId());
            statement.setString(9, save.checkpointId());
            statement.executeUpdate();
        }
    }

    private static Optional<SaveGame> loadSave(Connection connection, UUID studentId, int slot) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT student_id, slot, chapter_id, story_node_id, difficulty, saved_at, route_id, checkpoint_id FROM save_slots "
                        + "WHERE student_id = ? AND slot = ?")) {
            statement.setString(1, SqlSupport.id(studentId)); statement.setInt(2, slot);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readSave(rows)) : Optional.empty();
            }
        }
    }

    private Optional<SaveSnapshot> loadSnapshot(Connection connection, UUID studentId, int slot)
            throws SQLException {
        SaveGame save;
        String json;
        try (var statement = connection.prepareStatement(
                "SELECT student_id, slot, chapter_id, story_node_id, difficulty, saved_at, game_state_json, route_id, checkpoint_id "
                        + "FROM save_slots WHERE student_id = ? AND slot = ? AND game_state_json IS NOT NULL")) {
            statement.setString(1, SqlSupport.id(studentId)); statement.setInt(2, slot);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                save = readSave(rows);
                json = rows.getString("game_state_json");
            }
        }
        try {
            GameState state = codec.decode(json);
            return Optional.of(new SaveSnapshot(save, state));
        } catch (IllegalArgumentException malformed) {
            throw new SQLException("Stored save data is invalid.", malformed);
        }
    }

    private static SaveGame readSave(ResultSet rows) throws SQLException {
        return new SaveGame(SqlSupport.uuid(rows.getString("student_id")), rows.getInt("slot"),
                SqlSupport.uuid(rows.getString("chapter_id")), rows.getString("story_node_id"),
                Difficulty.valueOf(rows.getString("difficulty")),
                SqlSupport.instant(rows.getObject("saved_at", java.time.LocalDateTime.class)),
                rows.getString("route_id"), rows.getString("checkpoint_id"));
    }

    private static SaveGame normalizeSave(SaveGame save) {
        return new SaveGame(save.studentId(), save.slot(), save.chapterId(), save.storyNodeId(), save.difficulty(),
                SqlSupport.instant(SqlSupport.utc(save.savedAt())), save.routeId(), save.checkpointId());
    }

    private static void validateCheckpoint(Connection connection, SaveGame save) throws SQLException {
        requireStudent(connection, save.studentId());
        Chapter chapter = loadChapter(connection, save.chapterId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown saved chapter."));
        if (!chapter.adaptive() && chapter.difficulty() != save.difficulty())
            throw new IllegalArgumentException("Saved difficulty does not match the prepared chapter.");
        StoryNode node = loadNode(connection, save.chapterId(), save.storyNodeId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown saved story node."));
        if (!save.checkpointId().isEmpty() && (!node.readingCheck()
                || chapter.checkpointAtNode(save.storyNodeId()).filter(cp -> cp.id().equals(save.checkpointId())).isEmpty()))
            throw new IllegalArgumentException("Saved checkpoint does not match the story node.");
    }

    private void validateGameState(Connection connection, GameState state, boolean matchStoredDraft)
            throws SQLException {
        requireStudent(connection, state.studentId());
        Chapter chapter = loadChapter(connection, state.chapterId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown saved chapter."));
        StoryNode node = loadNode(connection, state.chapterId(), state.storyNodeId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown saved story node."));
        if (!state.lastCompletedCheckpointId().isEmpty()
                && loadAssessment(connection, state.studentId(), state.chapterId(), state.lastCompletedCheckpointId()).isEmpty())
            throw new IllegalArgumentException("Saved state references a checkpoint result that is not stored.");
        if (state.assessmentSessionId() != null && !node.readingCheck())
            throw new IllegalArgumentException("An active reading check must be saved at a reading-check node.");
        List<Question> activeQuestions = chapter.questions();
        if (chapter.adaptive() && !state.activeCheckpointId().isEmpty()) {
            if (state.activeDifficulty() == null
                    || chapter.checkpointAtNode(state.storyNodeId())
                            .filter(checkpoint -> checkpoint.id().equals(state.activeCheckpointId())).isEmpty())
                throw new IllegalArgumentException("Saved checkpoint metadata does not match the story node.");
            activeQuestions = chapter.questions(state.activeCheckpointId(), state.activeDifficulty());
        } else if (chapter.adaptive() && state.assessmentSessionId() != null) {
            throw new IllegalArgumentException("An adaptive assessment save needs its checkpoint ID.");
        }
        if (state.questionIndex() > activeQuestions.size())
            throw new IllegalArgumentException("Saved question position is outside this chapter.");
        for (var answer : state.firstAnswers().entrySet()) {
            String checkpointId = state.activeCheckpointId();
            Difficulty activeDifficulty = state.activeDifficulty() == null ? chapter.difficulty() : state.activeDifficulty();
            if (!questionBelongsToCheckpoint(connection, answer.getKey(), state.chapterId(), checkpointId, activeDifficulty)
                    || !choiceExists(connection, answer.getKey(), answer.getValue()))
                throw new IllegalArgumentException("Saved answer does not belong to this chapter.");
        }
        if (state.assessmentSessionId() == null) return;
        DraftRow draft = lockDraftBySession(connection, state.assessmentSessionId());
        if (draft == null || !draft.studentId().equals(state.studentId())
                || !draft.chapterId().equals(state.chapterId()) || !draft.storyNodeId().equals(state.storyNodeId())
                || !draft.checkpointId().equals(state.activeCheckpointId()) || !draft.routeId().equals(state.routeId())
                || !draft.lastCompletedCheckpointId().equals(state.lastCompletedCheckpointId())
                || draft.difficulty() != state.activeDifficulty())
            throw new IllegalArgumentException("The saved reading-check session does not match its stored draft.");
        if (draft.completed()) throw new IllegalStateException("A completed reading check cannot be saved as active progress.");
        if (matchStoredDraft) {
            GameState stored = loadDraft(connection, state.assessmentSessionId())
                    .orElseThrow(() -> new IllegalStateException("The stored reading-check draft is unavailable."));
            if (stored.questionIndex() != state.questionIndex() || !stored.firstAnswers().equals(state.firstAnswers()))
                throw new IllegalArgumentException("Save the current stored first answers without changing them.");
        }
    }

    private static Optional<GameState> loadDraft(Connection connection, UUID sessionId) throws SQLException {
        UUID studentId;
        UUID chapterId;
        String nodeId;
        String checkpointId;
        String routeId;
        String stateLastCompletedCheckpointId;
        Difficulty difficulty;
        try (var statement = connection.prepareStatement(
                "SELECT student_id, chapter_id, story_node_id, checkpoint_id, route_id, "
                        + "last_completed_checkpoint_id, active_difficulty "
                        + "FROM assessment_drafts WHERE session_id = ?")) {
            statement.setString(1, SqlSupport.id(sessionId));
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                studentId = SqlSupport.uuid(rows.getString("student_id"));
                chapterId = SqlSupport.uuid(rows.getString("chapter_id"));
                nodeId = rows.getString("story_node_id");
                checkpointId = rows.getString("checkpoint_id");
                routeId = rows.getString("route_id");
                String lastCompletedCheckpointId = rows.getString("last_completed_checkpoint_id");
                String storedDifficulty = rows.getString("active_difficulty");
                difficulty = storedDifficulty == null ? null : Difficulty.valueOf(storedDifficulty);
                // Captured below so the progress value is read with the session metadata.
                stateLastCompletedCheckpointId = lastCompletedCheckpointId;
            }
        }
        Map<UUID, Integer> answers = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement(
                "SELECT question_id, selected_choice_index FROM assessment_draft_answers "
                        + "WHERE session_id = ? ORDER BY answered_at, question_id")) {
            statement.setString(1, SqlSupport.id(sessionId));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) answers.put(SqlSupport.uuid(rows.getString("question_id")),
                        rows.getInt("selected_choice_index"));
            }
        }
        return Optional.of(new GameState(studentId, chapterId, nodeId, sessionId, answers.size(), answers,
                routeId, checkpointId, difficulty, stateLastCompletedCheckpointId));
    }

    private static DraftRow lockDraftBySession(Connection connection, UUID sessionId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT student_id, chapter_id, story_node_id, checkpoint_id, route_id, last_completed_checkpoint_id, "
                        + "active_difficulty, completed_at FROM assessment_drafts "
                        + "WHERE session_id = ? FOR UPDATE")) {
            statement.setString(1, SqlSupport.id(sessionId));
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                return new DraftRow(SqlSupport.uuid(rows.getString("student_id")),
                        SqlSupport.uuid(rows.getString("chapter_id")), rows.getString("story_node_id"),
                        rows.getString("checkpoint_id"), rows.getString("route_id"),
                        rows.getString("last_completed_checkpoint_id"),
                        rows.getString("active_difficulty") == null ? null
                                : Difficulty.valueOf(rows.getString("active_difficulty")),
                        rows.getTimestamp("completed_at") != null, sessionId);
            }
        }
    }

    private static boolean questionBelongsToCheckpoint(Connection connection, UUID questionId, UUID chapterId,
            String checkpointId, Difficulty difficulty) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM questions WHERE id = ? AND chapter_id = ? AND checkpoint_id = ? "
                        + "AND (question_difficulty = ? OR (? = '' AND question_difficulty IS NULL))")) {
            statement.setString(1, SqlSupport.id(questionId));
            statement.setString(2, SqlSupport.id(chapterId));
            statement.setString(3, checkpointId);
            statement.setString(4, difficulty == null ? "" : difficulty.name());
            statement.setString(5, checkpointId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private static boolean choiceExists(Connection connection, UUID questionId, int choiceIndex) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM question_choices WHERE question_id = ? AND choice_index = ?")) {
            statement.setString(1, SqlSupport.id(questionId)); statement.setInt(2, choiceIndex);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private static void requireStudent(Connection connection, UUID studentId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT 1 FROM students WHERE id = ?")) {
            statement.setString(1, SqlSupport.id(studentId));
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("Unknown student.");
            }
        }
    }

    private static void requireChapter(Connection connection, UUID chapterId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT 1 FROM chapters WHERE id = ?")) {
            statement.setString(1, SqlSupport.id(chapterId));
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("Unknown chapter.");
            }
        }
    }

    private static boolean contentInUse(Connection connection, UUID chapterId) throws SQLException {
        String[] tables = {"assessment_drafts", "assessments", "save_slots"};
        for (String table : tables) {
            try (var statement = connection.prepareStatement(
                    "SELECT 1 FROM " + table + " WHERE chapter_id = ? LIMIT 1")) {
                statement.setString(1, SqlSupport.id(chapterId));
                try (ResultSet rows = statement.executeQuery()) { if (rows.next()) return true; }
            }
        }
        return false;
    }

    private static void requireSlot(int slot) {
        if (slot < 1 || slot > 3) throw new IllegalArgumentException("Save slot must be 1..3.");
    }
}
