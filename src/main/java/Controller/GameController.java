package Controller;

import App.MainState;
import Model.*;
import Observers.BannerObserver;
import Observers.CardsObserver;
import Service.GameSetupService;
import View.DestinationPopUp;
import View.RoutePopUp;
import Observers.PlayerTurnObverser;
import com.google.cloud.firestore.ListenerRegistration;
import javafx.application.Platform;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import Observers.TurnTimerObserver;

import java.util.*;

public class GameController {
    private GameState gameState;
    private ListenerRegistration listenerRegistration;

    private PlayerTurnController playerTurnController = new PlayerTurnController();
    private CardsController cardsController = new CardsController();
    private MapController mapController = MapController.getInstance();
    private TurnTimerController turnTimerController = new TurnTimerController();
    private PlayerBannerController bannerController = new PlayerBannerController();

    private GameSetupService gameSetupService = new GameSetupService();

    private boolean firstTurn = true;

    private boolean lastRound = false;
    private boolean lastActionTaken = false;

    public GameController() {
        // Ugly
        mapController.setCardsController(cardsController);

        MainState.primaryStage.setOnCloseRequest(event -> {
            try {
                turnTimerController.stopTimer();
                MainState.firebaseService.removePlayer(MainState.roomCode, MainState.player_uuid);
                // If nobody is in the room, delete it.
                if (MainState.firebaseService.getPlayersFromLobby(MainState.roomCode).size() == 0) {
                    MainState.firebaseService.getLobbyReference(MainState.roomCode).delete();
                }
            } catch (Exception ignored) {}
        });
        initGame();
    }

    /**
     * AVOID AS MANY UPDATES AND TRY TO PUSH UPDATES ALL AT ONCE!
     * 0. ATTACH LISTENER FOR GAME INITIALIZATION FROM THE HOST
     * 1. If host Generate Decks
     * 2. If host Generate Player colors
     * 3. Give first turn
     * // LOOP
     * 4. Wait for event to check turn / Do action and give turn
     * 5. ................/ Get 1 instance of GameState en modify it
     * 6. Show new data / Update data
     * 7. Check end game.
     * // END LOOP
     */

    public void initGame() {
        gameState = MainState.firebaseService.getGameStateOfLobby(MainState.roomCode);
        attachListener();
        // Init for host
        if (gameState.getPlayer(MainState.player_uuid).getHost()) {
            generateDecks();
            initializePlayerColors();
            giveFirstTurn();
            gameState.setLoadedByHost(true);
            updateGameState();
            System.out.println("Initialized");
        }
    }

    // Step 0
    public void attachListener() {
        listenerRegistration = MainState.firebaseService.getLobbyReference(MainState.roomCode).addSnapshotListener(((documentSnapshot, e) -> {
            Platform.runLater(() -> {
                System.out.println("INCOMING UPDATE");
                GameState incomingGameState = documentSnapshot.toObject(GameState.class);
                if (incomingGameState.isLoadedByHost()) {
                    // A player has leaved
                    if (incomingGameState.getPlayers().size() < gameState.getPlayers().size()) {
                        removeLeftPlayers(incomingGameState);
                        bannerController.updatePlayersArray(gameState.getPlayers());
                    } else {
                        gameState = incomingGameState;
                        // Check trains for all players
                        checkTrains();
                        cardsController.notifyObservers(gameState.getOpenDeck());
                        bannerController.updatePlayersArray(gameState.getPlayers());
                        // End old timer and Make time init timer
                        turnTimerController.resetTimer(this);
                    }
                    try {
                        playerTurnController.checkMyTurn(gameState);
                        if (firstTurn && playerTurnController.getTurn()) {
                            firstTurn = false;
                            DestinationPopUp destinationPopUp = new DestinationPopUp();
                            destinationPopUp.showAtStartOfGame();
                            endTurn();
                        }
                        if (playerTurnController.getTurn()) {
                            checkEndGame();
                        }
                    } catch (Exception exception) {
                        exception.printStackTrace();
                        updateGameState();
                    }
                }
            });
        }));
    }

    // Step 1
    public void generateDecks() {
        ArrayList<TrainCard> closedCards = cardsController.generateClosedDeck();
        gameState.setOpenDeck(cardsController.generateOpenDeck(closedCards));
        gameState.setClosedDeck(closedCards);
        gameState.setDestinationDeck(gameSetupService.getDestinationTickets());
    }

    // Step 2
    public void initializePlayerColors(){
        final ArrayList<String> PLAYER_COLORS = new ArrayList<>(Arrays.asList("GREEN","BLUE","PURPLE","RED","YELLOW"));
        for (Player player : gameState.getPlayers()) {
            player.setPlayerColor(PLAYER_COLORS.remove(0));
        }
    }

    // Step 3
    public void giveFirstTurn() {
        playerTurnController.start(gameState);
    }

    // Game
    // Step 1: Set Actions
    // Step 2: if Actions = 2, End turn and set next player turn = true
    // Step 3: update gameState
    public void updateGameState() {
        System.out.println("updateGameState");
        MainState.firebaseService.updateGameStateOfLobby(MainState.roomCode, gameState);
    }


    // Actions
    // MAKE SURE ACTIONS ALWAYS HAVE incrementPlayerActionsTaken and checkNextTurn;
    public void pickClosedCard() {
        // Check if player turn
        System.out.println(playerTurnController.getTurn());
        if (playerTurnController.getTurn()) {
            TrainCard pickedClosedCard = cardsController.pickClosedCard(gameState);
            System.out.println("Picked Closed Card");
            addTrainCardToPlayerInventoryInGameState(pickedClosedCard);
            incrementPlayerActionsTaken();
            checkIfTurnIsOver();
        } else {
            System.out.println("IT'S NOT YOUR TURN");
        }
    }

    public void pickOpenCard(int index) {
        try {
            if (playerTurnController.getTurn()) {
                TrainCard pickedOpenCard = cardsController.pickOpenCard(gameState, index);
                System.out.println("Picked Open Card");
                addTrainCardToPlayerInventoryInGameState(pickedOpenCard);

                if (pickedOpenCard.getColor().equals("LOCO")) {
                    getLocalPlayerFromGameState().setActionsTaken(2);
                } else {
                    incrementPlayerActionsTaken();
                }
                checkIfTurnIsOver();
            } else {
                System.out.println("IT'S NOT YOUR TURN");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public void buildRoute(Route route) {
        String selectedColor = null;
        boolean isBuilt = false;
        if (playerTurnController.getTurn() && getLocalPlayerFromGameState().getActionsTaken() == 0) {
            if (route.routeLength() <= getLocalPlayerFromGameState().getTrains()) {
                if (route.getColor().equals("GREY")) {
                    selectedColor = pickColorForGreyRoute(route);
                    isBuilt = mapController.claimRoute(route, selectedColor);
                } else {
                    isBuilt = mapController.claimRoute(route, route.getColor());
                }
                if (isBuilt) {
                    givePointForRouteSize(route.routeLength());
                    endTurn();
                } else {
                    if (selectedColor == null) {
                        System.out.println("Not enough cards of color " + route.getColor());
                    } else {
                        System.out.println("Not enough cards of color " + selectedColor);
                    }
                }
            } else {
                System.out.println("NOT ENOUGH TRAINS");
            }
        } else {
            System.out.println("IT'S NOT YOUR TURN");
        }
    }

    private String pickColorForGreyRoute(Route route) {
        ArrayList<String> possibleColors = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : getLocalPlayerFromGameState().trainCardsAsMap().entrySet()) {
            if (entry.getValue() >= route.routeLength()) {
                possibleColors.add(entry.getKey());
            }
        }
        if (possibleColors.size() == 0) {
            System.out.println("Not enough cards to build GREY route.");
            return null;
        }
        RoutePopUp routePopUp = new RoutePopUp(possibleColors);
        return routePopUp.showRoutePopUp();
    }

    private void givePointForRouteSize(int routeLength) {
        switch (routeLength) {
            case 1: getLocalPlayerFromGameState().incrementPoints(1); break;
            case 2: getLocalPlayerFromGameState().incrementPoints(2); break;
            case 3: getLocalPlayerFromGameState().incrementPoints(4); break;
            case 4: getLocalPlayerFromGameState().incrementPoints(7); break;
            case 6: getLocalPlayerFromGameState().incrementPoints(15); break;
            case 8: getLocalPlayerFromGameState().incrementPoints(21); break;
            default: getLocalPlayerFromGameState().incrementPoints(0); break;
        }
    }

    public void endTurn() {
        if (playerTurnController.getTurn()) {
            getLocalPlayerFromGameState().setActionsTaken(2);
            checkIfTurnIsOver();
        }
    }

    public void endGame() {
        System.out.println("GAME IS ENDED");
        listenerRegistration.remove();
    }

    public void checkEndGame() {
        if (lastRound && lastActionTaken) {
            endGame();
        }
    }

    // ===============================================================

    private void addTrainCardToPlayerInventoryInGameState(TrainCard trainCard) {
        getLocalPlayerFromGameState().addTrainCard(trainCard);
    }

    private Player getLocalPlayerFromGameState() {
        return gameState.getPlayer(MainState.player_uuid);
    }

    private Boolean isPlayerActionsTakenEquals2() {
        return (getLocalPlayerFromGameState().getActionsTaken() == 2);
    }

    private void incrementPlayerActionsTaken() {
        getLocalPlayerFromGameState().setActionsTaken(getLocalPlayerFromGameState().getActionsTaken() + 1);
    }

    public void registerCardsObserver(CardsObserver cardsObserver) {
        cardsController.registerObserver(cardsObserver);
    }

    public void registerTurnTimerObserver(TurnTimerObserver turnTimerObserver) {
        turnTimerController.registerObserver(turnTimerObserver);
    }

    public void registerPlayerTurnObserver(PlayerTurnObverser playerTurnObverser) {
        playerTurnController.registerObserver(playerTurnObverser);
    }

    public void registerBannerObserver(BannerObserver bannerObserver) {
        bannerController.registerObserver(bannerObserver);
    }

    private void checkIfTurnIsOver() {
        System.out.println("CHECK");
        if (isPlayerActionsTakenEquals2()) {
            // End turn
            System.out.println("NEXT TURN");

            if (lastRound) {
                lastActionTaken = true;
            }

            getLocalPlayerFromGameState().setActionsTaken(0);
            playerTurnController.nextTurn(gameState);
            updateGameState();
        }
    }

    private void removeLeftPlayers(GameState incomingGameState) {
        ArrayList<String> remainingPlayers = new ArrayList<>();
        incomingGameState.getPlayers().forEach((n) -> remainingPlayers.add(n.getUUID()));
        gameState.getPlayers().removeIf(player -> !remainingPlayers.contains(player.getUUID()));
    }

    // ===============================================================

    // Does anyone have 2 or less trains?
    public void checkTrains() {
        if (!lastRound) {
            for (Player player : gameState.getPlayers()) {
                if (player.getTrains() <= 2) {
                    lastRound = true;
                    break;
                }
            }
        }
    }

    public Player getCurrentPlayer() {
        return playerTurnController.getCurrent(gameState);
    }

    /**
     * This method checks if the Cities on the given DestinationTicket have been connected by the given Player
     * It calls singleStep(), which uses recursive backtracking to find the path
     */
    public boolean isConnected(DestinationTicket ticket, Player player) {
        return singleStep(ticket.getFirstCity(), ticket.getSecondCity(), player);
    }

    /**
     * This method runs a single step in the pathfinding algorithm using backtracking.
     * It checks all neighbors of the currentCity, and if the player has built a Route from
     * currentCity to the neighbor, this method calls itself again, but now with the
     * neighbor City as the new currentCity. This way all possibilities to connect any two given
     * Cities are tried
     * @param currentCity City that we are at to check for a connection to destinationCity
     * @param destinationCity City that we are looking for a connection to
     * @param player Player that we are checking for if they have a connection between the two Cities
     * @return true if there is a connection from the initial currentCity to the destinationCity, false otherwise
     */
    private boolean singleStep(City currentCity, City destinationCity, Player player) {
        // Accept case - we found the destination city
        if (currentCity.equals(destinationCity)) {
            return true;
        }
        // Reject case - we already visited this city
        if (currentCity.isVisited()) {
            return false;
        }
        // Backtracking step
        // Make a note that we visited this City, then try to go to each neighbor city
        currentCity.setVisited(true);
        for (City neighbor : currentCity.getNeighborCities()) {
            for (Route route : player.getClaimedRoutes()) {
                // If the player has built a route from currentCity to neighbor,
                // run the function again with neighbor as the new currentCity
                boolean connectedAToB = route.getFirstCity().equals(currentCity) && route.getSecondCity().equals(neighbor);
                boolean connectedBToA = route.getFirstCity().equals(neighbor) && route.getSecondCity().equals(currentCity);
                if (connectedAToB || connectedBToA) {
                    if (singleStep(neighbor, destinationCity, player)) {
                        return true;
                    }
                }
            }
        }
        // Dead end - this location can't be part of the solution
        // Unmark the location and go back to previous step
        currentCity.setVisited(false);
        return false;
    }
}
