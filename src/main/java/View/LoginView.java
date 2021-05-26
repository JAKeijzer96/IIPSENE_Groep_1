package View;

import Controller.LoginController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class LoginView extends StackPane {
    private Stage primaryStage;
    private LoginController controller = new LoginController();

    public LoginView(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // Logo
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(40));
        ImageView title = new ImageView("images/main_menu_logo.png");
        title.setFitWidth(title.getImage().getWidth() * 0.9);
        title.setFitHeight(title.getImage().getHeight() * 0.9);
        grid.add(title, 0,0,2,1);

        // Background Effect
        ColorAdjust colorAdjust = new ColorAdjust();
        colorAdjust.setBrightness(-0.5);

        // Background for textFields and return to menu button
        ImageView background = new ImageView("images/main_menu_background.jpg");
        background.setFitWidth(1280);
        background.setFitHeight(720);
        background.setEffect(colorAdjust);

        // Layout
        VBox app = new VBox(10);
        app.setAlignment(Pos.BOTTOM_LEFT);
        app.setPadding(new Insets(40));
        Button returnToMenu = new Button("Return to menu");

        // Box with the explanation, input username, input roomcode and join button
        VBox textFields = new VBox(20);
        textFields.setId("textFields");
        Text explaination = new Text("Enter username and room code to join a game with your friends");
        explaination.setId("text");

        TextField inputUsername = new TextField();
        inputUsername.setPromptText("Username...");
        inputUsername.setFocusTraversable(false);

        // input and join button next to each other
        HBox inputWithJoin = new HBox();
        Button join = new Button("Join");
        TextField inputCode = new TextField();
        inputCode.setPromptText("Room code...");
        inputCode.setFocusTraversable(false);
        HBox.setHgrow(inputCode, Priority.ALWAYS);
        HBox.setHgrow(join, Priority.ALWAYS);

        // Add margins
        VBox.setMargin(explaination, new Insets(20, 10, 20, 10));
        VBox.setMargin(inputWithJoin, new Insets(0, 0, 20, 0));
        VBox.setMargin(textFields, new Insets(30, 0, 30, 0));

        // Add all
        inputWithJoin.getChildren().add(inputCode);
        inputWithJoin.getChildren().add(join);

        textFields.getChildren().add(explaination);
        textFields.getChildren().add(inputUsername);
        textFields.getChildren().add(inputWithJoin);

        app.getChildren().add(textFields);
        app.getChildren().add(returnToMenu);

        getChildren().add(background);
        getChildren().add(grid);
        getChildren().add(app);

        // Event Handlers

        // Unfocus textfield when clicked outside the textfield
        setOnMouseClicked(e -> {
            requestFocus();
        });

        //join
        join.setOnMouseClicked(e -> controller.join(inputUsername, inputCode));

        //returnToMenu
        returnToMenu.setOnMouseClicked(e -> controller.returnToMenu(primaryStage));
    }
}