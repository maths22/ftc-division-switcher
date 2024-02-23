package com.maths22.ftc.scoring.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class EventPicker {
    private static final Logger LOG = LoggerFactory.getLogger(EventPicker.class);
    private JPanel panel;
    private JTextField serverAddress;
    private JComboBox eventPicker;
    private JButton serverConnectButton;
    private JButton loadEventsBtn;
    private Consumer<List<FtcScoringClient>> onPick;
    private List<FtcScoringClient> clients;

    public EventPicker() {
        ActionListener loadEventsListener = (e) -> {
            eventPicker.removeAllItems();
            try {
                FtcScoringClient.getEvents(serverBaseUrl()).stream().sorted().forEach((evt) -> eventPicker.addItem(evt));
            } catch (Exception ex) {
                LOG.error("Failed loading event list", ex);
            }
        };
        serverAddress.addActionListener(loadEventsListener);
        loadEventsBtn.addActionListener(loadEventsListener);

        serverConnectButton.addActionListener((e) -> {
            String eventCode = (String) eventPicker.getSelectedItem();
            if(eventCode == null) {
                LOG.debug("No event selected");
                return;
            }
            disconnect();
            FtcScoringClient baseClient = new FtcScoringClient(serverBaseUrl(), eventCode);
            if(!baseClient.getEvent().finals()) {
                clients = List.of(baseClient);
            } else {
                String baseCode = eventCode.substring(0, eventCode.length() - 1);
                clients = new ArrayList<>();
                clients.add(baseClient);
                FtcScoringClient.getEvents(serverBaseUrl()).stream().filter(c -> !c.equals(eventCode) && c.startsWith(baseCode))
                        .map(c -> new FtcScoringClient(serverBaseUrl(), c))
                        .sorted(Comparator.comparingInt(c -> c.getEvent().division()))
                        .forEach(clients::add);
            }
            onPick.accept(clients);
        });
    }

    private String serverBaseUrl() {
        return serverAddress.getText();
    }

    public void setOnPick(Consumer<List<FtcScoringClient>> clientConsumer) {
        onPick = clientConsumer;
    }

    public void reset() {
        disconnect();
        serverAddress.setText("");
        eventPicker.removeAllItems();
    }

    private void disconnect() {
        if(clients != null) {
            clients.forEach(FtcScoringClient::stopSocketListener);
        }
    }
}
