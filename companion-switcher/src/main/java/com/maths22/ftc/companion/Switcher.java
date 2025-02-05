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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.List;
import java.util.*;
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
    private final Map<Integer, Integer> currField = new HashMap<>();

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
                                }
                            }
                        }
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }
                }).get();
            } catch (InterruptedException | ExecutionException ex) {
                LOG.error("Failed to connect to switcher", ex);
            }
        });
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Switcher");
        frame.setContentPane(new Switcher().panel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
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
    }

    private void setCompanionVariable(String name, String value) {
        if(!companionUrl.getText().isEmpty()) {
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
