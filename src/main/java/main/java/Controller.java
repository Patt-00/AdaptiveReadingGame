package main.java;

import java.beans.PropertyChangeEvent;

/** Connects dashboard input, model operations, and displayed model state. */
public class Controller {
    private final ReadingProgressModel model;
    private final DashboardView view;

    public Controller(ReadingProgressModel model, DashboardView view) {
        this.model = model;
        this.view = view;

        // The model notifies this controller after changing its data.
        model.addPropertyChangeListener(this::updateView);

        // The controller listens for user actions from the view.
        view.getSaveNameButton().setOnAction(event -> saveStudentName());
        view.getLogSessionButton().setOnAction(event -> logReadingSession());
        view.getFinishBookButton().setOnAction(event -> finishBook());

        showCurrentModelState();
    }

    public DashboardView getView() {
        return view;
    }

    private void saveStudentName() {
        try {
            model.setStudentName(view.getNameInput().getText());
            view.getNameInput().clear();
            view.displayMessage("Name saved.", false);
        } catch (IllegalArgumentException error) {
            view.displayMessage(error.getMessage(), true);
        }
    }

    private void logReadingSession() {
        int minutes;
        try {
            minutes = Integer.parseInt(view.getMinutesInput().getText().trim());
        } catch (NumberFormatException error) {
            view.displayMessage("Enter the reading time as a whole number of minutes.", true);
            return;
        }

        try {
            model.addReadingSession(minutes);
            view.clearMinutesInput();
            view.displayMessage("Reading session added.", false);
        } catch (IllegalArgumentException error) {
            view.displayMessage(error.getMessage(), true);
        }
    }

    private void finishBook() {
        model.addBookRead();
        view.displayMessage("Book added to your progress.", false);
    }

    /** Called when the model notifies its listeners about a changed property. */
    private void updateView(PropertyChangeEvent event) {
        switch (event.getPropertyName()) {
            case "studentName" -> view.displayStudentName(model.getStudentName());
            case "booksRead" -> view.displayBooksRead(model.getBooksRead());
            case "minutesRead" -> view.displayMinutesRead(model.getMinutesRead());
            case "sessionsCompleted" -> view.displaySessionsCompleted(model.getSessionsCompleted());
            default -> { }
        }
    }

    private void showCurrentModelState() {
        view.displayStudentName(model.getStudentName());
        view.displayBooksRead(model.getBooksRead());
        view.displayMinutesRead(model.getMinutesRead());
        view.displaySessionsCompleted(model.getSessionsCompleted());
    }
}
