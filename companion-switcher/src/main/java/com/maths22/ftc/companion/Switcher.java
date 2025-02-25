package com.maths22.ftc.companion;

import com.maths22.ftc.scoring.client.EventPicker;
import com.maths22.ftc.scoring.client.FtcScoringClient;
import com.maths22.ftc.scoring.client.models.Event;
import com.maths22.ftc.scoring.client.models.MatchUpdate;
import com.maths22.ftc.scoring.client.models.UpdateType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.List;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public class Switcher {
    private static final Logger LOG = LoggerFactory.getLogger(Switcher.class);
    private static final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private static final List<UpdateType> SUPPORTED_UPDATES = List.of(
            UpdateType.SHOW_MATCH,
            UpdateType.SHOW_PREVIEW,
            UpdateType.SHOW_RANDOM,
            UpdateType.MATCH_START,
            UpdateType.MATCH_ABORT
    );
    private JPanel panel;
    private JTextField companionUrl;
    private JTextField displaySwitcherHostField;
    private JButton displaySwitcherConnectButton;
    private WebSocket switcherSocket;
    private EventPicker scoringSelector;
    private JLabel divisionSwitcherStatus;
    private JTextArea loggerTextArea;
    private final Map<Integer, Integer> currField = new HashMap<>();
    private Timer timer = new Timer();
    private List<TimerTask> tasks = new ArrayList<>();

    public Switcher() {
        scoringSelector.setOnPick((clients) -> {
            clients.forEach(c -> c.setOnMatchUpdate(up -> this.process(c.getEvent(), up)));
            clients.forEach(FtcScoringClient::startSocketListener);
        });
        displaySwitcherConnectButton.addActionListener((e) -> {
            if(switcherSocket != null) {
                switcherSocket.abort();
                switcherSocket = null;
            }
            try {
                switcherSocket = client.newWebSocketBuilder().buildAsync(URI.create("ws://" + displaySwitcherHostField.getText() + "/divisionstream"), new WebSocket.Listener() {
                    private StringBuilder buffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        divisionSwitcherStatus.setText("Connected");
                        divisionSwitcherStatus.setBackground(new java.awt.Color(0,128,0));
                        WebSocket.Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buffer.append(data);
                        if(last) {
                            String message = buffer.toString();
                            buffer = new StringBuilder();

                            if(!message.equals("pong")) {
                                try {
                                    String[] parts = message.split(":");
                                    if(parts[0].equals("division")) {
                                        int division = Integer.parseInt(parts[1]);
                                        setCompanionVariable("ftc_division", String.valueOf(division));
                                    }
                                } catch (Exception ex) {
                                    LOG.error("Error processing stream update", ex);
                                    divisionSwitcherStatus.setText("Error");
                                    divisionSwitcherStatus.setBackground(new java.awt.Color(255, 0, 0));
                                }
                            }
                        }
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        WebSocket.Listener.super.onError(webSocket, error);
                        divisionSwitcherStatus.setText("Error");
                        divisionSwitcherStatus.setBackground(new java.awt.Color(255, 0, 0));
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        divisionSwitcherStatus.setText("Disconnected");
                        divisionSwitcherStatus.setBackground(new java.awt.Color(255, 255, 0));
                        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                    }
                }).get();
            } catch (InterruptedException | ExecutionException ex) {
                LOG.error("Failed to connect to switcher", ex);
                divisionSwitcherStatus.setText("Error");
                divisionSwitcherStatus.setBackground(new java.awt.Color(255, 0, 0));
            }
        });
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Switcher");
        Switcher switcher = new Switcher();
        frame.setContentPane(switcher.panel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

        System.setOut(new PrintStream(System.out) {
            public void println(String s) {
                switcher.logOut(s);
                super.println(s);
            }
        });

        System.setErr(new PrintStream(System.err) {
            public void println(String s) {
                switcher.logErr(s);
                super.println(s);
            }
        });
    }

    private void logOut(String s) {
        loggerTextArea.append(s + "\n");
    }

    private void logErr(String s) {
        loggerTextArea.append(s + "\n");
    }

    public void process(Event event, MatchUpdate update) {
        if(!SUPPORTED_UPDATES.contains(update.updateType())) {
            return;
        }
        int division = event.division();
        int field = update.payload().field();
        if(!currField.containsKey(division) || field != currField.get(division)) {
            currField.put(division, field);
            setCompanionVariable("ftc_d" + division + "_field", String.valueOf(field));
        }
        String stateKey = "ftc_d" + division + "_state";
        switch (update.updateType()) {
            case SHOW_PREVIEW -> setCompanionVariable(stateKey, "preview");
            case SHOW_MATCH -> setCompanionVariable(stateKey, "prematch");
            case MATCH_START -> {
                var transitionTrigger = new TimerTask() {
                    @Override
                    public void run() {
                        setCompanionVariable(stateKey, "transition");
                    }
                };
                var teleopTrigger = new TimerTask() {
                    @Override
                    public void run() {
                        setCompanionVariable(stateKey, "teleop");
                    }
                };
                var endgameTrigger = new TimerTask() {
                    @Override
                    public void run() {
                        setCompanionVariable(stateKey, "endgame");
                    }
                };
                var completedTrigger = new TimerTask() {
                    @Override
                    public void run() {
                        setCompanionVariable(stateKey, "completed");
                    }
                };
                tasks.add(transitionTrigger);
                tasks.add(teleopTrigger);
                tasks.add(endgameTrigger);
                tasks.add(completedTrigger);
                timer.schedule(transitionTrigger, 30 * 1000);
                timer.schedule(teleopTrigger, (30 + 8) * 1000);
                timer.schedule(endgameTrigger, (30 + 8 + (120-30)) * 1000);
                timer.schedule(completedTrigger, (30 + 8 + 120) * 1000);
                setCompanionVariable(stateKey, "auto");
            }
            case MATCH_ABORT -> {
                tasks.forEach(TimerTask::cancel);
                timer.purge();
                setCompanionVariable(stateKey, "aborted");
            }
        }

    }

    private void setCompanionVariable(String name, String value) {
        if(!companionUrl.getText().isEmpty()) {
            LOG.debug("Setting companion variable {} to {}", name, value);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + companionUrl.getText() + "/api/custom-variable/" + name + "/value"))
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString(value))
                    .build();
            try {
                client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                LOG.error("Failed to update companion", e);
            }
        }
    }

}
