package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.ReadingLevel;
import com.adaptivereadinggame.model.ReadingMaterial;
import com.adaptivereadinggame.model.Student;

import java.util.List;
import java.util.UUID;

/** Temporary development implementation; replace with persisted data later. */
public class InMemoryAdaptiveEngine implements AdaptiveEngine {
    private final Student demoStudent = new Student(UUID.randomUUID(), "Reader",
            ReadingLevel.BEGINNER, 0, 0.0);

    @Override
    public Student currentStudent() {
        return demoStudent;
    }

    @Override
    public ReadingMaterial nextMaterialFor(Student student) {
        return new ReadingMaterial(UUID.randomUUID(), "Coming soon", "",
                student.readingLevel(), List.of());
    }
}
