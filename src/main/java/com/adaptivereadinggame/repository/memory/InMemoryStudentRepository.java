package com.adaptivereadinggame.repository.memory;

import com.adaptivereadinggame.model.Student;
import com.adaptivereadinggame.repository.StudentRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Development adapter only; data disappears when the JVM exits. */
public final class InMemoryStudentRepository implements StudentRepository {
    private final ConcurrentHashMap<UUID, Student> students = new ConcurrentHashMap<>();
    @Override public Optional<Student> findById(UUID id) { return Optional.ofNullable(students.get(Objects.requireNonNull(id))); }
    @Override public Student save(Student student) {
        Objects.requireNonNull(student); students.put(Objects.requireNonNull(student.id()), student); return student;
    }
}
