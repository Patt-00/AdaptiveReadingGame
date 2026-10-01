package main.java;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/** Stores reading progress and notifies listeners when its state changes. */
public class ReadingProgressModel {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);

    private String studentName = "Student";
    private int booksRead;
    private int minutesRead;
    private int sessionsCompleted;

    public String getStudentName() {
        return studentName;
    }

    public int getBooksRead() {
        return booksRead;
    }

    public int getMinutesRead() {
        return minutesRead;
    }

    public int getSessionsCompleted() {
        return sessionsCompleted;
    }

    public void setStudentName(String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a name before saving.");
        }

        String oldName = studentName;
        studentName = newName.trim();
        changes.firePropertyChange("studentName", oldName, studentName);
    }

    public void addReadingSession(int minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("Minutes must be greater than zero.");
        }

        int oldMinutes = minutesRead;
        minutesRead += minutes;
        changes.firePropertyChange("minutesRead", oldMinutes, minutesRead);

        int oldSessions = sessionsCompleted;
        sessionsCompleted++;
        changes.firePropertyChange("sessionsCompleted", oldSessions, sessionsCompleted);
    }

    public void addBookRead() {
        int oldBooks = booksRead;
        booksRead++;
        changes.firePropertyChange("booksRead", oldBooks, booksRead);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }
}
