package com.maths22.ftc;

import com.google.gson.Gson;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Timer;
import java.util.*;

public class DivisionSwitcher {
    private static final Logger LOG = LoggerFactory.getLogger(DivisionSwitcher.class);
    private static final Gson gson = new Gson();
    private final Set<Session> wsClients = new HashSet<>();
    private final SheetRetriever retriever;
    private String data;
    private JPanel panel;
    private JTextField serverAddress;
    private JTextArea ipTextArea;
    private JButton resetButton;
    private JButton serverConnectButton;
    private JTextField sheetId;
    private JButton sheetIdLoadButton;
    private JLabel d0Status;
    private JLabel audienceStatus;
    private JComboBox<String> eventPicker;
    private JCheckBox enabledCheckBox;
    private JPanel nowShowingPicker;
    private List<FtcScoringClient> clients = List.of();
    private Map<Integer, JRadioButton> divisionButtons = new HashMap<>();
    private String spreadsheetId;

    public static void main(String[] args) {
        JFrame frame = new JFrame("DivisionSwitcher");
        new DivisionSwitcher(frame);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    public DivisionSwitcher(JFrame frame) {
        serverAddress.addActionListener((e) -> {
            eventPicker.removeAllItems();
            try {
                FtcScoringClient.getEvents(serverBaseUrl()).stream().sorted().forEach((evt) -> eventPicker.addItem(evt));
            } catch (Exception ex) {
                LOG.error("Failed loading event list", ex);
            }
            frame.pack();
        });

       serverConnectButton.addActionListener((ActionEvent e) -> {
            try {
                // TODO divisions
                String eventCode = (String) eventPicker.getSelectedItem();
                if(eventCode == null) {
                    LOG.debug("No event selected");
                    return;
                }
                FtcScoringClient baseClient = new FtcScoringClient(serverBaseUrl(), eventCode, this::sendMatches);
                if(!baseClient.getEvent().finals()) {
                    clients = List.of(baseClient);
                } else {
                    String baseCode = eventCode.substring(0, eventCode.length() - 1);
                    clients = new ArrayList<>();
                    clients.add(baseClient);
                    FtcScoringClient.getEvents(serverBaseUrl()).stream().filter(c -> !c.equals(eventCode) && c.startsWith(baseCode))
                            .map(c -> new FtcScoringClient(serverBaseUrl(), c, this::sendMatches))
                            .sorted(Comparator.comparingInt(c -> c.getEvent().division()))
                            .forEach(c -> clients.add(c));
                }

                clients.forEach(FtcScoringClient::loadMatches);
                clients.forEach(FtcScoringClient::startSocketListener);
                nowShowingPicker.removeAll();
                nowShowingPicker.setLayout(new GridLayoutManager(1, clients.size()));
                ButtonGroup newGroup = new ButtonGroup();
                divisionButtons.clear();
                for(int i = 0; i < clients.size(); i++) {
                    FtcScoringClient c = clients.get(i);
                    JRadioButton newButton = new JRadioButton(c.getEvent().name(), c.getEvent().eventCode().equals(eventCode));
                    newButton.addActionListener((evt) -> sendShowMessage(c.getEvent().division()));
                    GridConstraints constraints = new GridConstraints();
                    constraints.setColumn(i);
                    nowShowingPicker.add(newButton, constraints);
                    newGroup.add(newButton);
                    divisionButtons.put(c.getEvent().division(), newButton);
                }
                frame.pack();

                ScoringLogin dialog = new ScoringLogin(baseClient);
                dialog.pack();
                dialog.setVisible(true);
            } catch (Exception ex) {
                LOG.error("Failed connecting to event", ex);
            }
        });

        JavalinVerificationCodeReciever verificationCodeReciever = new JavalinVerificationCodeReciever();
        Javalin.create((config) -> {
                    config.staticFiles.add(sfc -> {
                        sfc.directory = "/serve";
                        sfc.location = Location.CLASSPATH;
                    });
                    config.staticFiles.add(sfc -> {
                        sfc.directory = "/public";
                        sfc.location = Location.CLASSPATH;
                    });
                    config.registerPlugin(verificationCodeReciever);
                })
                .get("/api", ctx -> {
                    if(ctx.queryParam("division") == null) {
                        ctx.status(HttpStatus.BAD_REQUEST);
                        return;
                    }
                    String target = ctx.queryParam("division");
                    String display = ctx.queryParam("display");
                    String match = ctx.queryParam("match");
                    if(match == null) {
                        match = "";
                    }
                    if(target == null) {
                        ctx.status(HttpStatus.BAD_REQUEST);
                        return;
                    }

                    sendDisplayMessage(clients.stream().filter(c -> c.getEvent().division() == Integer.parseInt(target)).findFirst().orElseThrow(), display, match);
                    sendShowMessage(Integer.parseInt(target));

                    if(!match.isEmpty()) {
                        data = ctx.queryParam("data");
                        sendState();
                    }

                    ctx.contentType("text/plain");
                    ctx.result("Updated");
                })
                .ws("/matchstream", (cfg) -> {
                    cfg.onConnect((ctx) -> {
                        wsClients.add(ctx.session);

                        SheetRetriever.Result auxData = auxData();
                        if(auxData != null) {
                            ctx.session.getRemote().sendString(gson.toJson(new Message.AuxInfo(auxData)));
                        }
                        ctx.session.getRemote().sendString(gson.toJson(new Message.MatchData(mergedMatches())));
                        if(data != null) {
                            ctx.session.getRemote().sendString(gson.toJson(new Message.State(data)));
                        }
                        ctx.session.getRemote().sendString(gson.toJson(new Message.SingleStep(!enabledCheckBox.isSelected())));
                    });
                    cfg.onClose((ctx) -> wsClients.remove(ctx.session));
                })
                .start(8888);
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                ping();
            }
        }, 0, 5000);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                new Thread(DivisionSwitcher.this::sendAuxInfo).start();
            }
        }, 0, 30000);

        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            ipTextArea.setBackground(null);
            ipTextArea.setBorder(null);
            ipTextArea.setEditable(true);
            while (ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                Enumeration<InetAddress> addrs = ni.getInetAddresses();

                while (addrs.hasMoreElements()) {
                    InetAddress ia = addrs.nextElement();
                    if (ia.getClass() == Inet4Address.class && !ia.isLoopbackAddress()) {
                        String addrStr = ia.getCanonicalHostName();
                        String ipStr = ia.getHostAddress();
                        if (addrStr.equals(ipStr)) {
                            addrStr = null;
                        }

                        if (addrStr != null) {
                            ipTextArea.setText(ipTextArea.getText() +
                                    "http://" + addrStr + ":8888\r\n");
                        }

                        ipTextArea.setText(ipTextArea.getText() +
                                "http://" + ipStr + ":8888\r\n");
                    }
                }
            }
            ipTextArea.setEditable(false);
        } catch (IOException ex) {
            LOG.error("Failed to enumerate network addresses");
        }
        resetButton.addActionListener(e -> {
            serverAddress.setText("");
            eventPicker.removeAllItems();
            clients.forEach(FtcScoringClient::stopSocketListener);
            sendMatches();
        });
        sheetIdLoadButton.addActionListener(e -> {
            spreadsheetId = sheetId.getText();
            new Thread(DivisionSwitcher.this::sendAuxInfo).start();
        });
        enabledCheckBox.addActionListener(e -> new Thread(DivisionSwitcher.this::sendSingleStep).start());

        retriever = new SheetRetriever(verificationCodeReciever);
        frame.setContentPane(panel);
    }

    private void sendTime(String div, String msg) {
        String[] parts = msg.split(" ");

        for(Session socket : wsClients) {
            try {
                socket.getRemote().sendString(gson.toJson(Map.of("type", "time", "data", Map.of(
                        "div", div,
                        "phase", toTitleCase(parts[2].replace('_', ' ')),
                        "min", Integer.parseInt(parts[3]) / 60,
                        "sec", Integer.parseInt(parts[3]) % 60
                ))));
            } catch (IOException e) {
                LOG.warn("Failed to send time", e);
            }
        }
    }

    private void ping() {
        for(Session socket : wsClients) {
            try {
                socket.getRemote().sendString("ping");
            } catch (IOException e) {
                LOG.warn("Failed to ping", e);
            }
        }
    }

    private void sendState() {
        if(data == null) return;

        for(Session socket : wsClients) {
            try {
                socket.getRemote().sendString(gson.toJson(new Message.State(data)));
            } catch (IOException e) {
                LOG.warn("Failed to send state", e);
            }
        }
    }

    private void sendSingleStep() {
        for(Session socket : wsClients) {
            try {
                socket.getRemote().sendString(gson.toJson(new Message.SingleStep(!enabledCheckBox.isSelected())));
            } catch (IOException e) {
                LOG.warn("Failed to send step", e);
            }
        }
    }

    private void sendAuxInfo() {
        SheetRetriever.Result data = auxData();
        if(data == null) return;
        try {
            for(Session socket : wsClients) {
                socket.getRemote().sendString(gson.toJson(new Message.AuxInfo(data)));
            }
        } catch (IOException e) {
            LOG.warn("Failed to send aux info", e);
        }
    }

    private SheetRetriever.Result auxData() {
        if(spreadsheetId == null || spreadsheetId.isEmpty()) return null;
        try {
            return retriever.getTeamInfo(spreadsheetId);
        } catch (IOException e) {
            LOG.error("Failed retrieving aux data", e);
        }
        return null;
    }

    private void sendMatches() {
        List<Match> data = mergedMatches();
        for(Session socket : wsClients) {
            try {
                socket.getRemote().sendString(gson.toJson(new Message.MatchData(data)));
            } catch (IOException e) {
                LOG.warn("Failed to send matches", e);
            }
        }
    }

    private List<Match> mergedMatches() {
        if(clients.isEmpty()) {
            return List.of();
        }
        List<Match> matches = new ArrayList<>();
        List<Iterator<Match>> iterators;

        iterators = clients.stream().skip(1).map((c) -> c.matches().stream().filter(m -> m.id().matchType() == MatchType.PRACTICE).iterator()).toList();
        interleaveMatches(matches, iterators);

        iterators = clients.stream().skip(1).map((c) -> c.matches().stream().filter(m -> m.id().matchType() == MatchType.QUALS).iterator()).toList();
        interleaveMatches(matches, iterators);

        iterators = clients.stream().skip(1).map((c) -> c.matches().stream().filter(m -> m.id().matchType().isSemiFinal()).iterator()).toList();
        interleaveMatches(matches, iterators);

        iterators = clients.stream().skip(1).map((c) -> c.matches().stream().filter(m -> m.id().matchType() == MatchType.FINAL).iterator()).toList();
        interleaveMatches(matches, iterators);

        matches.addAll(clients.get(0).matches());

        return matches;
    }

    private void interleaveMatches(List<Match> matches, List<Iterator<Match>> iterators) {
        while (iterators.stream().anyMatch(Iterator::hasNext)) {
            for (Iterator<Match> it : iterators) {
                if (it.hasNext()) matches.add(it.next());
            }
        }
    }

    private void sendDisplayMessage(FtcScoringClient client, String s, String m) {
        if(client != null) {
            switch (s) {
                case "prematch":
                    client.showMatch(m);
                    break;
                case "results":
                    client.showResults(m);
                    break;
                case "rankings":
                    client.showRanks();
                    break;
                case "announcement":
                    client.showMessage(m);
                    break;
                case "alliance":
                    client.showSelection();
                    break;
                case "sponsor":
                    client.showSponsors();
                    break;
                case "elimination":
                    client.showBracket();
                case "wifi":
                case "blank":
                case "video":
                case "key":
                case "online":
                    client.basicCommand(s);
                    break;
            }
        }
    }

    private void sendShowMessage(int i) {
        divisionButtons.get(i).setSelected(true);

        // TODO broadcast
    }

    private String serverBaseUrl() {
        return serverAddress.getText();
    }


    public static String toTitleCase(String input) {
        StringBuilder titleCase = new StringBuilder();
        boolean nextTitleCase = true;

        for (char c : input.toLowerCase().toCharArray()) {
            if (Character.isSpaceChar(c)) {
                nextTitleCase = true;
            } else if (nextTitleCase) {
                c = Character.toTitleCase(c);
                nextTitleCase = false;
            }

            titleCase.append(c);
        }

        return titleCase.toString();
    }
}
