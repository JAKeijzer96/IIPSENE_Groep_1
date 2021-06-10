package Controller;

import App.MainState;
import Model.*;
import Observers.MapObserver;
import Service.GameSetupService;
import Service.OverlayEventHandler;
import javafx.event.Event;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MapController {
    private final MapModel mapModel;
    private final GameSetupService gameSetupService;
    private GameController gameController;
    private CardsController cardsController;
    private HashMap<RouteCell, Rectangle> cellRectangleMap;
    static MapController mapController;

    public MapController() {
        this.gameSetupService = new GameSetupService();
        this.cellRectangleMap = new HashMap<>();
        this.mapModel = new MapModel(gameSetupService.getRoutes(), gameSetupService.getCities());
        this.mapModel.setCityOverlays(createCityOverlays());
        this.mapModel.setRouteCellOverlays(createRouteCellOverlays());
    }

    public void setCardsController(CardsController cardsController) {
        this.cardsController = cardsController;
    }

    public static MapController getInstance() {
        if (mapController == null) {
            mapController = new MapController();
        }
        return mapController;
    }


    /**
     * Create the overlays for the cities on the map.
     * Sets the correct location, size and adds event handler
     * @return A list of Circle objects which represent the overlays
     */
    private ArrayList<Circle> createCityOverlays() {
        ArrayList<Circle> circleList = new ArrayList<>();
        for (City city : this.mapModel.getCities()) {
            Circle circle = new Circle();
            circle.setTranslateX(city.getOffsetX() / 2);
            circle.setTranslateY(city.getOffsetY() / 2);
            circle.setRadius(this.mapModel.getRadius());
            circle.setFill(Color.TRANSPARENT);
            circle.addEventHandler(MouseEvent.ANY, new OverlayEventHandler(
                    e -> {
                        if (circle.getFill().equals(Color.TRANSPARENT)) {
                            circle.setFill( Color.BLACK);
                        } else {
                            circle.setFill(Color.TRANSPARENT);
                        }
                    },
                    Event::consume)
            );
            circleList.add(circle);
        }
        return circleList;
    }

    /**
     * Creates the overlays for the individual routecells on the map.
     * Sets the correct location, size, rotation and adds event handler
     * @return A list of RouteCell object which represent the overlays
     */
    private ArrayList<Rectangle> createRouteCellOverlays() {
        ArrayList<Rectangle> overlays = new ArrayList<>();
        ArrayList<Route> routes = this.mapModel.getRoutes();
        for (Route route : routes) {
            for (RouteCell routeCell : route.getRouteCells()) {
                Rectangle rectangle = new Rectangle();
                rectangle.setTranslateX(routeCell.getTranslateX() / 2);
                rectangle.setTranslateY(routeCell.getTranslateY() / 2);
                rectangle.setRotate(routeCell.getRotation());
                rectangle.setWidth(this.mapModel.getCellWidth());
                rectangle.setHeight(this.mapModel.getCellHeight());
                rectangle.addEventHandler(MouseEvent.ANY, new OverlayEventHandler(
                        e -> {
                            for (Route route1 : routes) {
                                if (route1.getRouteCells().contains(routeCell)) {
                                    gameController.buildRoute(route);
                                }
                            }
                        },
                        Event::consume
                ));
                rectangle.setFill(Color.TRANSPARENT);
                cellRectangleMap.put(routeCell, rectangle);
                overlays.add(rectangle);
            }
        }
        return overlays;
    }

    // When this method is called, we assume that the player has already selected the color
    // with which they want to build the route, in case it is grey.
    public boolean claimRoute(Route route, String color) {
        if (cellRectangleMap.get(route.getRouteCells().get(0)).getFill() != Color.TRANSPARENT ) {
            return false;
        }

        String routeColor = route.getColor();
        // If the Route is grey, check for cards of the given color
        if (routeColor.equals("GREY")) {
            routeColor = color;
        }
        String type = route.getType();
        int requiredLocos = route.getRequiredLocomotives();
        int routeLength = route.routeLength();
        Player currentPlayer = this.gameController.getCurrentPlayer();
        ArrayList<TrainCard> playerHand = currentPlayer.getTrainCards();
        ArrayList<TrainCard> correctColorCards = new ArrayList<>();
        ArrayList<TrainCard> locosInHand = new ArrayList<>();
        for (TrainCard trainCard : playerHand) {
            if (trainCard.getColor().equals(routeColor)) {
                correctColorCards.add(trainCard);
            } else if (trainCard.getColor().equals("LOCO")) {
                locosInHand.add(trainCard);
            }
            if (correctColorCards.size() >= routeLength && locosInHand.size() >= requiredLocos) {
                break;
            }
        }
        // Not enough cards of the right color
        if (correctColorCards.size() + locosInHand.size() < routeLength) {
            return false;
        }
        int cardsToRemove = routeLength - requiredLocos;

        if (type.equals("TUNNEL")) {
            int tunnels = generateTunnels(routeColor);
            cardsToRemove = cardsToRemove + tunnels;
        }
        if (type.equals("FERRY") && locosInHand.size() < requiredLocos) {
            return false;
        }

        // Remove cards from hand
        // TODO: probably in handController?

        int locosToRemove = requiredLocos;
        for (TrainCard trainCard : correctColorCards) {
            if (cardsToRemove > 0) {
                playerHand.remove(trainCard);
                cardsToRemove--;
            }
        }
        // Remove locos for ferries, or as extra for standard routes
        for (TrainCard trainCard : locosInHand) {
            if (cardsToRemove > 0 || locosToRemove > 0) {
                playerHand.remove(trainCard);
                cardsToRemove--;
                locosToRemove--;
            }
        }

        currentPlayer.addClaimedRoute(route);
        currentPlayer.givePointForRouteSize(routeLength);
        for (RouteCell routeCell : route.getRouteCells()) {
            cellRectangleMap.get(routeCell).setFill(this.mapModel.getImagePattern(gameController.getCurrentPlayer().getPlayerColor()));
        }

        gameController.checkTrains();
        return true;
    }

    /**
     * Zooms in on the mapModel and updates the mapView
     *
     */
    public void zoomIn() {
        if (this.mapModel.isZoomedIn()) {
            return;
        }
        this.mapModel.setZoomedIn(true);

        for (Rectangle rectangle : this.mapModel.getRouteCellOverlays()) {
            RouteCell routeCell = getKeyByValue(cellRectangleMap, rectangle);
            rectangle.setWidth(this.mapModel.getCellWidth());
            rectangle.setHeight(this.mapModel.getCellHeight());
            rectangle.setTranslateX(routeCell.getTranslateX());
            rectangle.setTranslateY(routeCell.getTranslateY());
        }

        for (Circle circle : this.mapModel.getCityOverlays()) {
            circle.setRadius(this.mapModel.getRadius());
            circle.setTranslateX(circle.getTranslateX() * 2);
            circle.setTranslateY(circle.getTranslateY() * 2);
        }
        this.mapModel.notifyObservers();
    }

    public static <T, E> T getKeyByValue(Map<T, E> map, E value) {
        for (Map.Entry<T, E> entry : map.entrySet()) {
            if (Objects.equals(value, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Zooms out on the mapModel and updates the mapView
     */
    public void zoomOut() {
        if (! this.mapModel.isZoomedIn()) {
            return;
        }
        this.mapModel.setZoomedIn(false);

        for (Rectangle rectangle : this.mapModel.getRouteCellOverlays()) {
            RouteCell routeCell = getKeyByValue(cellRectangleMap, rectangle);
            rectangle.setWidth(this.mapModel.getCellWidth());
            rectangle.setHeight(this.mapModel.getCellHeight());
            rectangle.setTranslateX(routeCell.getTranslateX() / 2);
            rectangle.setTranslateY(routeCell.getTranslateY() / 2);
        }

        for (Circle circle : this.mapModel.getCityOverlays()) {
            circle.setRadius(this.mapModel.getRadius());
            circle.setTranslateX(circle.getTranslateX() / 2);
            circle.setTranslateY(circle.getTranslateY() / 2);
        }
        this.mapModel.notifyObservers();
    }

    public int generateTunnels(String color){
        int tunnels = 0;
        for(int i =0;i<3;i++){
            TrainCard randomCard = cardsController.pickClosedCard(MainState.firebaseService.getGameStateOfLobby(MainState.roomCode));
            if(randomCard.getColor().equals(color)){
                tunnels++;
            }
        }
        System.out.println("you have to build " + tunnels + "tunnels!");
        return tunnels;
    }

    public MapModel getMapModel() {
        return mapModel;
    }

    public void setGameController(GameController gameController) {
        this.gameController = gameController;
    }

    public GameSetupService getGameSetupService() {
        return gameSetupService;
    }

    public void registerObserver(MapObserver observer) {
        this.mapModel.registerObserver(observer);
    }

    public void unregisterObserver(MapObserver observer) {
        this.mapModel.unregisterObserver(observer);
    }
}
