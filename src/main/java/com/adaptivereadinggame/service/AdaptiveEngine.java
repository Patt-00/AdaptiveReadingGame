package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.ReadingMaterial;
import com.adaptivereadinggame.model.Student;

/** Selects material appropriate to a student's current performance. */
public interface AdaptiveEngine {
    Student currentStudent();
    ReadingMaterial nextMaterialFor(Student student);
}
