package App;

import View.MainMenuView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.awt.*;

public class Main extends Application {

    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    private final int WINDOW_X_POSITION = screenSize.width / 2 - MainState.SCREEN_WIDTH / 2;
    private final int WINDOW_Y_POSITION = screenSize.height / 2 - MainState.SCREEN_HEIGHT / 2;

    // https://stackoverflow.com/a/52654791
    // "For Maven the solution is exactly the same: provide a new main class that doesn't extend from Application."
    //  public static void main(String[] args) {
    // HelloFX.main(args);
    //    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {

        MainState.primaryStage = primaryStage;
        MainMenuView mainMenuView = new MainMenuView();

        Scene scene = new Scene(mainMenuView, MainState.SCREEN_WIDTH, MainState.SCREEN_HEIGHT);
        scene.getStylesheets().add(MainState.MenuCSS);

        primaryStage.setScene(scene);
        primaryStage.setTitle("Ticket to Ride");
        primaryStage.getIcons().add(new Image("images/ttr_icon_main.png"));
        primaryStage.setResizable(false);
        primaryStage.setX(WINDOW_X_POSITION);
        primaryStage.setY(WINDOW_Y_POSITION);
        primaryStage.show();
    }
}
