package View;

import App.MainState;
import Controller.DestinationTicketController;
import Controller.GameController;
import Model.DestinationTicket;
import Model.GameState;
import Model.Player;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;

public class DestinationPopUp {
    private final DestinationTicketController destinationTicketController;
    private final double UNSELECTED_OPACITY = 0.6;
    private final double SELECTED_OPACITY = 1;

    private Stage stage;

    public DestinationPopUp(GameState gameState) {
        ArrayList<DestinationTicket> destinationTickets = gameState.getDestinationDeck();
        this.destinationTicketController = new DestinationTicketController(destinationTickets);
    }

    public void showAtStartOfGame(GameState gameState, GameController gameController) {
        this.showPopUp(this.destinationTicketController.drawTickets(true), gameState, gameController);
    }

    public void showDuringGame(GameState gameState, GameController gameController) {
        this.showPopUp(this.destinationTicketController.drawTickets(false), gameState, gameController);
    }

    private void showPopUp(ArrayList<DestinationTicket> destinationTickets, GameState gameState, GameController gameController) {
        stage = new Stage();
        stage.setTitle("Destination Tickets");
        stage.getIcons().add(new Image("images/traincards/traincard_back_small.png"));
        stage.setOnCloseRequest(Event::consume);

        ArrayList<DestinationTicket> selectedTickets = new ArrayList<>();
        int minimumTickets = destinationTickets.size() / 2;

        VBox vBox = new VBox();
        vBox.setAlignment(Pos.TOP_CENTER);
        vBox.setPadding(new Insets(20));
        vBox.setSpacing(20);

        Label label;

        if (destinationTickets.size() == 0) {
            label = new Label("No more cards available ");
            vBox.getChildren().add(label);
        } else {
            label = new Label("Select at least " + minimumTickets + " destination tickets");
            vBox.getChildren().add(label);
            createTicketGrid(vBox, destinationTickets, selectedTickets);
        }
        label.setId("selectTickets");

        Button closeButton = new Button("Confirm");
        Player player = gameController.getLocalPlayerFromGameState();
        closeButton.setOnAction(e -> {
            if (destinationTickets.size() == 0) {
                stage.close();
            }
            if (selectedTickets.size() >= minimumTickets) {
                for (DestinationTicket destinationTicket : selectedTickets) {
                    player.addDestinationTicket(destinationTicket);

                    destinationTickets.remove(destinationTicket);
                    gameState.getDestinationDeck().remove(destinationTicket);
                }
                for (DestinationTicket destinationTicket: destinationTickets){
                    destinationTicketController.returnCardToDeck(destinationTicket);
                }
                stage.close();
            }
        });

        vBox.getChildren().add(closeButton);
        Scene scene = new Scene(vBox);
        scene.getStylesheets().add(MainState.menuCSS);
        stage.setScene(scene);
        stage.initStyle(StageStyle.UTILITY);
        stage.setResizable(false);
        stage.setAlwaysOnTop(true);
        stage.showAndWait();
    }


    public void createTicketGrid(VBox vBox, ArrayList<DestinationTicket> destinationTickets, ArrayList<DestinationTicket> selectedTickets) {
        HBox hBoxTop = new HBox();
        HBox hBoxBottom = new HBox();

        int i = 0;
        for (DestinationTicket destinationTicket : destinationTickets) {
            String path = destinationTicket.fileName();
            ImageView ticketImageView = new ImageView(new Image(path));
            ticketImageView.setOpacity(UNSELECTED_OPACITY);
            ticketImageView.setOnMouseClicked(e -> {

                if (!selectedTickets.contains(destinationTicket)) {
                    selectedTickets.add(destinationTicket);
                    ticketImageView.setOpacity(SELECTED_OPACITY);
                } else {
                    selectedTickets.remove(destinationTicket);
                    ticketImageView.setOpacity(UNSELECTED_OPACITY);
                }
            });

            if (i < 2) {
                hBoxTop.getChildren().add(ticketImageView);
            } else {
                hBoxBottom.getChildren().add(ticketImageView);
            }
            i++;
        }
        hBoxTop.setAlignment(Pos.CENTER);
        hBoxTop.setSpacing(20);
        hBoxBottom.setAlignment(Pos.CENTER);
        hBoxBottom.setSpacing(20);
        vBox.getChildren().addAll(hBoxTop, hBoxBottom);
    }

    public Stage getStage() {
        return stage;

    }
}
