/*
 * Enjun
 *
 * @version     1.0 Beta 1
 * @author      Rocking Stars
 * @copyright   2018, Enjun
 *
 * Copyright 2018 RockingStars

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rockingstar.engine.lobby.controllers;

import com.rockingstar.engine.ServerConnection;
import com.rockingstar.engine.command.client.*;
import com.rockingstar.engine.game.*;
import com.rockingstar.engine.gui.controllers.AudioPlayer;
import com.rockingstar.engine.gui.controllers.GUIController;
import com.rockingstar.engine.io.models.Util;
import com.rockingstar.engine.lobby.models.LobbyModel;
import com.rockingstar.engine.lobby.views.LobbyView;
import com.rockingstar.engine.lobby.views.LoginView;
import com.rockingstar.modules.Reversi.controllers.ReversiController;
import com.rockingstar.modules.TicTacToe.controllers.TTTController;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Class that creates the login view and handles all the actions that can be performed in the login view.
 * @author Rockingstars
 * @since 1.0 Beta 1
 */
public class Launcher {

    private GUIController _guiController;

    private LobbyModel _model;
    private LobbyView _lobbyView;
    private ServerConnection _serverConnection;

    private LoginView _loginView;

    private AbstractGame _currentGame;

    private static Launcher _instance;

    private Player _localPlayer;

    public static final Object LOCK = new Object();

    private Thread _updatePlayerList;
    private AudioPlayer _backgroundMusic;

    private LinkedList<Player> _onlinePlayers;

    /**
     * Method to instantiate a launcher
     * @param guiController
     */
    private Launcher(GUIController guiController) {
        _guiController = guiController;

        _model = new LobbyModel();
        _loginView = new LoginView();

        addLoginActionHandlers(_loginView, this);
        _onlinePlayers = new LinkedList<>();

        setupOnlinePlayerList();
    }

    /**
     * Method that returns the instance
     * @return returns the instance
     */
    public static Launcher getInstance() {
        if (_instance == null)
            return null;

        return _instance;
    }

    /**
     *
     * @param guiController
     * @return
     */
    public static Launcher getInstance(GUIController guiController) {
        if (_instance == null)
            _instance = new Launcher(guiController);

        return _instance;
    }

    public void setCentralNode() {
        _guiController.setCenter(_loginView.getNode());
    }

    public void returnToLobby() {
        _guiController.setCenter(_lobbyView.getNode());
        _currentGame = null;

        setupOnlinePlayerList();
        _updatePlayerList.start();
        setupBackgroundMusic();
        _backgroundMusic.play();
    }

    private void loadModule(AbstractGame game) {
        _currentGame = game;
        Platform.runLater(() -> _guiController.setCenter(game.getView()));
        _backgroundMusic.end();
    }

    public void handleLogin(String username, String gameMode, boolean isAI, String difficulty) {
        // @todo Check for difficulty

        if (isAI){
            if (difficulty.equals("Easy")) {
                Util.displayStatus(difficulty + " Easy AI selected");
                _localPlayer = new EasyAI(username, new Color(0.5, 0.5, 0.5, 0));
            } else {
                Util.displayStatus(difficulty + " Hard AI selected");
                _localPlayer = new HardAI(username, new Color(0.5, 0.5, 0.5, 0));
            }

        } else {
              _localPlayer = new Player(username, new Color(0.5, 0.5, 0.5, 0));
        }

        if (_localPlayer.login()) {
            _lobbyView = new LobbyView(this);

            _lobbyView.setGameMode(gameMode);
            _lobbyView.setUsername(_localPlayer.getUsername());

            _lobbyView.setPlayerList(_onlinePlayers);

            _model.getPlayerList();
            _model.getGameList();

            _lobbyView.setup();

            _guiController.setCenter(_lobbyView.getNode());
            _guiController.addStylesheet("lobby");

            _updatePlayerList.start();
            setupBackgroundMusic();
            _backgroundMusic.start();
        }
    }

    public void challengeReceived(String response) {
        String[] parts = response.replaceAll("[^a-zA-Z0-9 \\-]","").split(" ");

        String challenger = parts[1];
        int challengeNumber;
        String gameType = parts[5];

        try {
            challengeNumber = Integer.parseInt(parts[3]);
        }
        catch (NumberFormatException e) {
            System.out.println("Invalid challenge");
            return;
        }

        Platform.runLater(() -> {
            Alert challengeInvitationAlert = new Alert(Alert.AlertType.CONFIRMATION);
            challengeInvitationAlert.setTitle("Challenge received");
            challengeInvitationAlert.setHeaderText(null);
            challengeInvitationAlert.setContentText("Player " + challenger + " has invited you to a game of " + gameType + ". Do you accept?");

            challengeInvitationAlert.showAndWait();
            if(challengeInvitationAlert.getResult() == ButtonType.CANCEL){
                return;
            } else if (challengeInvitationAlert.getResult() == ButtonType.OK) {
                CommandExecutor.execute(new AcceptChallengeCommand(_serverConnection, challengeNumber));
                Util.displayStatus("Accepting challenge from " + challenger);
            }
        });
    }

    public void startMatch(String response) {
        String[] parts = response.replaceAll("[^a-zA-Z0-9 \\-]","").split(" ");

        String startingPlayer = parts[1];
        String gameType = parts[3];
        String opponentName = parts[5];

        Player opponent = new Player(opponentName);

        AbstractGame gameModule;
        switch (gameType) {
            case "Tic-tac-toe":
            case "Tictactoe":
                if (_localPlayer instanceof AI) {
                    _localPlayer = new TTTAI(opponentName);
                    gameModule = new TTTController(_localPlayer, opponent);
                } else {
                    gameModule = new TTTController(_localPlayer, opponent);
                }
                break;
            case "Reversi":
                System.out.println(_localPlayer.getClass());
                gameModule = new ReversiController(_localPlayer, opponent);
                ((ReversiController) gameModule).setStartingPlayer(startingPlayer.equals(opponentName) ? opponent : _localPlayer);
                break;
            default:
                Util.displayStatus("Unsupported game module " + gameType);
                return;
        }

        Util.displayStatus("Loading game module " + gameType, true);

        loadModule(gameModule);
        gameModule.startGame();
    }


    public void updatePlayerList(String response) {
        HashMap<String, Player> playerNames = new HashMap<>();
        HashMap<String, Player> loggedInPlayers = new HashMap<>();

        for (Player player : _onlinePlayers)
            playerNames.put(player.getUsername(), player);

        for (String player : Util.parseFakeCollection(response)) {
            if (!playerNames.keySet().contains(player))
                loggedInPlayers.put(player, new Player(player));
            else
                loggedInPlayers.put(player, playerNames.get(player));
        }

        _onlinePlayers.clear();
        _onlinePlayers.addAll(loggedInPlayers.values());
        _lobbyView.setPlayerList(_onlinePlayers);
    }

    public void updateGameList(String response) {
        LinkedList<String> games = new LinkedList<>();
        games.addAll(Util.parseFakeCollection(response));
        _lobbyView.setGameList(games);
    }

    public AbstractGame getGame() {
        return _currentGame;
    }

    public void subscribeToGame(String game) {
        CommandExecutor.execute(new SubscribeCommand(ServerConnection.getInstance(), game));
    }

    private void setupBackgroundMusic() {
        _backgroundMusic = new AudioPlayer("cod2soundtrack.mp3", true);
    }

    private void setupOnlinePlayerList() {
        _updatePlayerList = new Thread(() -> {
            while (_currentGame == null) {
                _model.getPlayerList();

                try {
                    Thread.sleep(5000);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public boolean connectToServer(String hostname) {
        if (_serverConnection != null)
            return true;

        // Theres a better way to do this. But it's late and I'm not in the mood for writing a regex
        String[] parts = hostname.trim().split(":");

        if (parts.length != 2) {
            Alert uNameAlert = new Alert(Alert.AlertType.INFORMATION);
            uNameAlert.setTitle("ACHTUNG!");
            uNameAlert.setHeaderText("ACHTUNG!");
            uNameAlert.setContentText("Invalid host address. Required format: ip:port.");

            uNameAlert.showAndWait();
            return false;
        }

        try {
            int port = Integer.parseInt(parts[1]);

            _serverConnection = ServerConnection.getInstance(parts[0], port);
            _serverConnection.start();
        }
        catch (IOException | ArrayIndexOutOfBoundsException e) {
            Alert uNameAlert = new Alert(Alert.AlertType.INFORMATION);
            uNameAlert.setTitle("ACHTUNG!");
            uNameAlert.setHeaderText("ACHTUNG!");
            uNameAlert.setContentText("Der verdammte Server funktioniert nicht.");

            uNameAlert.showAndWait();
            return false;
        }

        return true;
    }

    public void addLoginActionHandlers(LoginView loginView ,Launcher launcher) {
        loginView.getContinueButton().setOnAction(e -> {
            boolean connected = launcher.connectToServer(loginView.getHostname());

            if (!connected)
                return;

            if (loginView.getGameMode().equals("Player")) {
                launcher.handleLogin(String.valueOf(loginView.getInsertedUsername()), loginView.getGameMode(), false, null);
            } else {
                launcher.handleLogin(String.valueOf(loginView.getInsertedUsername()), loginView.getGameMode(), true, loginView.getDifficulty());
            }
        });
    }


}