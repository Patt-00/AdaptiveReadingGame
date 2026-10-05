package com.adaptivereadinggame.backend;

import com.adaptivereadinggame.model.*;
import java.util.List;
import java.util.UUID;

/** Console smoke test; no database, authentication, Python, or GUI is required. */
public final class BackendDemo {
    public static void main(String[] args) {
        var backend = BackendContext.inMemory();
        UUID studentId = UUID.randomUUID();
        backend.students().save(new Student(studentId, "Demo Reader", ReadingLevel.BEGINNER, 0, 0));
        var question = new Question(UUID.randomUUID(), "What did Mia plant?", List.of("A seed", "A book"), 0);
        var chapter = new Chapter(UUID.randomUUID(), "Mia's Garden", "Mia planted a seed and watered it.", Difficulty.MEDIUM, List.of(question));
        backend.chapters().saveChapter(chapter);
        UUID session = backend.assessmentService().start(studentId, chapter.id());
        backend.assessmentService().submit(session, question.id(), 0);
        var result = backend.assessmentService().complete(session);
        System.out.println("Score: " + result.attempt().correctAnswers() + "/" + result.attempt().totalQuestions());
        System.out.println("Next difficulty: " + result.nextDifficulty());
        System.out.println("Repeated completion returns same result: " + result.equals(backend.assessmentService().complete(session)));
        backend.progressService().save(studentId, 1, chapter.id(), "chapter-end");
        System.out.println("Saved slots: " + backend.progressService().slots(studentId).size());
        System.out.println("Completed history: " + backend.progressService().history(studentId).size());
        backend.assessmentService().clearSessionsFor(studentId);
        System.out.println("Demo uses volatile in-memory data. Login and database adapters are not implemented.");
    }
}
