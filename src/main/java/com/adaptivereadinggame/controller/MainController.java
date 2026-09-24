package com.adaptivereadinggame.controller;

import com.adaptivereadinggame.model.Student;
import com.adaptivereadinggame.service.AdaptiveEngine;
import com.adaptivereadinggame.service.InMemoryAdaptiveEngine;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/** Coordinates dashboard events and presents data supplied by the model layer. */
public class MainController {
    private final AdaptiveEngine adaptiveEngine = new InMemoryAdaptiveEngine();

    @FXML private Label welcomeLabel;
    @FXML private Label levelLabel;
    @FXML private Label progressLabel;
    @FXML private Label statusLabel;

    @FXML
    private void initialize() {
        showStudent(adaptiveEngine.currentStudent());
    }

    @FXML
    private void startReading() {
        statusLabel.setText("A reading session will appear here.");
    }

    @FXML
    private void viewProgress() {
        Student student = adaptiveEngine.currentStudent();
        statusLabel.setText("Completed attempts: " + student.completedAttempts());
    }

    private void showStudent(Student student) {
        welcomeLabel.setText("Welcome, " + student.displayName() + "!");
        levelLabel.setText("Current level: " + student.readingLevel());
        progressLabel.setText("Accuracy: " + Math.round(student.accuracy() * 100) + "%");
        statusLabel.setText("Choose an activity to begin.");
    }
}
