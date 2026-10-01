package main.java;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** The dashboard screen. It displays values and provides input controls. */
public class DashboardView extends BorderPane {
    private final Label studentNameLabel = new Label();
    private final Label booksReadValue = new Label();
    private final Label minutesReadValue = new Label();
    private final Label sessionsValue = new Label();
    private final Label messageLabel = new Label("Try changing the name or logging a reading session.");

    private final TextField nameInput = new TextField();
    private final TextField minutesInput = new TextField();
    private final Button saveNameButton = new Button("Save name");
    private final Button logSessionButton = new Button("Log session");
    private final Button finishBookButton = new Button("Mark a book finished");

    public DashboardView() {
        setPadding(new Insets(24));
        setStyle("-fx-background-color: #f4f6fb;");

        setTop(createHeader());
        setCenter(createDashboardContent());
    }

    private VBox createHeader() {
        Label title = new Label("Reading Dashboard");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");
        studentNameLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #555555;");

        nameInput.setPromptText("Enter a student name");
        nameInput.setPrefColumnCount(18);
        HBox nameForm = new HBox(8, nameInput, saveNameButton);
        nameForm.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(8, title, studentNameLabel, nameForm);
        header.setPadding(new Insets(0, 0, 20, 0));
        return header;
    }

    private VBox createDashboardContent() {
        Label progressTitle = new Label("Your progress");
        progressTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        HBox statCards = new HBox(12,
                createStatCard("Books read", booksReadValue),
                createStatCard("Minutes read", minutesReadValue),
                createStatCard("Sessions", sessionsValue));

        Label actionsTitle = new Label("Update your progress");
        actionsTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label minutesHint = new Label("How many minutes did you read?");
        minutesInput.setPromptText("Minutes");
        minutesInput.setPrefColumnCount(8);
        HBox sessionForm = new HBox(8, minutesHint, minutesInput, logSessionButton);
        sessionForm.setAlignment(Pos.CENTER_LEFT);

        VBox actionsCard = new VBox(14, sessionForm, finishBookButton);
        actionsCard.setPadding(new Insets(18));
        actionsCard.setStyle("-fx-background-color: white; -fx-background-radius: 8;");

        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-text-fill: #3157a4;");

        VBox content = new VBox(12, progressTitle, statCards, actionsTitle, actionsCard, messageLabel);
        VBox.setVgrow(actionsCard, Priority.ALWAYS);
        content.setFillWidth(true);
        return content;
    }

    private VBox createStatCard(String title, Label value) {
        Label heading = new Label(title);
        heading.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        value.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #3157a4;");

        VBox card = new VBox(10, heading, value);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(18));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8;");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    // The controller calls these methods to show the model's current state.
    public void displayStudentName(String name) {
        studentNameLabel.setText("Hello, " + name + "!");
    }

    public void displayBooksRead(int booksRead) {
        booksReadValue.setText(String.valueOf(booksRead));
    }

    public void displayMinutesRead(int minutesRead) {
        minutesReadValue.setText(String.valueOf(minutesRead));
    }

    public void displaySessionsCompleted(int sessions) {
        sessionsValue.setText(String.valueOf(sessions));
    }

    public void displayMessage(String message, boolean isError) {
        messageLabel.setText(message);
        messageLabel.setStyle(isError
                ? "-fx-text-fill: #b3261e;"
                : "-fx-text-fill: #3157a4;");
    }

    public void clearMinutesInput() {
        minutesInput.clear();
    }

    // The controller reads these controls and decides what to do with the input.
    public TextField getNameInput() {
        return nameInput;
    }

    public TextField getMinutesInput() {
        return minutesInput;
    }

    public Button getSaveNameButton() {
        return saveNameButton;
    }

    public Button getLogSessionButton() {
        return logSessionButton;
    }

    public Button getFinishBookButton() {
        return finishBookButton;
    }
}
