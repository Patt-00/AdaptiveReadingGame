package com.adaptivereadinggame.repository;

import com.adaptivereadinggame.model.Student;

import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for student records. */
public interface StudentRepository {
    Optional<Student> findById(UUID id);
    Student save(Student student);
}
