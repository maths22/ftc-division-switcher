package com.maths22.ftc;

import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import net.ser1.stomp.Client;
import net.ser1.stomp.Listener;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.List;
import java.util.Timer;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class DivisionSwitcher {
    private static final Logger LOG = LoggerFactory.getLogger(DivisionSwitcher.class);
    private static final Gson gson = new Gson();
    private final Set<Session> wsClients = new HashSet<>();
    private final SheetRetriever retriever;
    private String data;
    private JButton connectButton;
    private JTextField textField1;
    private JRadioButton division1RadioButton;
    private JRadioButton division2RadioButton;
    private JRadioButton rankingsRadioButton;
    private JPanel panel;
    private JButton d1ConnectButton;
    private JButton d2ConnectButton;
    private JTextField d0Address;
    private JTextField d1Address;
    private JTextField d2Address;
    private JTextArea ipTextArea;
    private JButton resetButton;
    private JButton d0ConnectButton;
    private JTextField sheetId;
    private JButton sheetIdLoadButton;
    private JLabel d1Status;
    private JLabel d0Status;
    private JLabel d2Status;
    private JLabel audienceStatus;
    private JComboBox<String> d0Event;
    private JComboBox<String> d1Event;
    private JComboBox<String> d2Event;
    private JCheckBox enabledCheckBox;
    private Client switcherClient;
    private FtcScoringClient d0Client = new FtcScoringClient(0, this::sendMatches);
    private FtcScoringClient d1Client = new FtcScoringClient(1, this::sendMatches);
    private FtcScoringClient d2Client = new FtcScoringClient(2, this::sendMatches);
    private String spreadsheetId;
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);

    public static void main(String[] args) {
        JFrame frame = new JFrame("DivisionSwitcher");
        frame.setContentPane(new DivisionSwitcher().panel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    public DivisionSwitcher() {
        division1RadioButton.addActionListener(e -> sendShowMessage("1"));
        division2RadioButton.addActionListener(e -> sendShowMessage("2"));
        rankingsRadioButton.addActionListener(e -> sendShowMessage("r"));
        connectButton.addActionListener((e) -> {
            if (switcherClient != null) {
                switcherClient.disconnect();
            }
            try {

                switcherClient = new Client(textField1.getText(), 43784, "user", "password");
                switcherClient.subscribe("/heartbeat", heartbeatListener(switcherClient, audienceStatus));


            } catch (IOException | LoginException e1) {
                e1.printStackTrace();
            }
        });
        d0Address.addActionListener((e) -> {
            d0Client.setBasePath(d0Address.getText());
            d0Event.removeAllItems();
            try {
                d0Client.getEvents().forEach((evt) -> d0Event.addItem(evt));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        d1Address.addActionListener((e) -> {
            d1Client.setBasePath(d1Address.getText());
            d1Event.removeAllItems();
            try {
                d1Client.getEvents().forEach((evt) -> d1Event.addItem(evt));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        d2Address.addActionListener((e) -> {
            d2Client.setBasePath(d2Address.getText());
            d2Event.removeAllItems();
            try {
                d2Client.getEvents().forEach((evt) -> d2Event.addItem(evt));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
       d0ConnectButton.addActionListener((ActionEvent e) -> {
            try {
                d0Client.setEvent((String) d1Event.getSelectedItem());
                if(d0Client.getEvent() == null) return;
//                d1Client.subscribe("/timer/status", (map, s) -> {
//                    sendTime("1", s);
//                });

                d0Client.loadMatches();
                d0Client.startSocketListener();

                ScoringLogin dialog = new ScoringLogin(d0Client);
                dialog.pack();
                dialog.setVisible(true);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        });
        d1ConnectButton.addActionListener((ActionEvent e) -> {
            try {
                d1Client.setEvent((String) d1Event.getSelectedItem());
                if(d1Client.getEvent() == null) return;
//                d1Client.subscribe("/timer/status", (map, s) -> {
//                    sendTime("1", s);
//                });

                d1Client.loadMatches();
                d1Client.startSocketListener();

                ScoringLogin dialog = new ScoringLogin(d1Client);
                dialog.pack();
                dialog.setVisible(true);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        });
        d2ConnectButton.addActionListener((ActionEvent e) -> {
            try {
                d2Client.setEvent((String) d1Event.getSelectedItem());
                if(d2Client.getEvent() == null) return;
//                d2Client.subscribe("/timer/status", (map, s) -> {
//                    sendTime("1", s);
//                });

                d2Client.loadMatches();
                d2Client.startSocketListener();

                ScoringLogin dialog = new ScoringLogin(d2Client);
                dialog.pack();
                dialog.setVisible(true);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        });

        Javalin app = Javalin.create((config) -> {
                    config.staticFiles.add(sfc -> {
                        sfc.directory = "/serve";
                        sfc.location = Location.CLASSPATH;
                    });
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

                    if(target.equals("0") || (target.equals("1") && match.isEmpty())) {
                        sendDisplayMessage(d0Client,display, match);
                    }
                    if(target.equals("1")) {
                        sendDisplayMessage(d1Client, display, match);
                    }
                    if(target.equals("2")) {
                        sendDisplayMessage(d2Client, display, match);
                    }

                    if(target.equals("1") || target.equals("2") || target.equals("r")) {
                        sendShowMessage(target);
                    } else {
                        ctx.status(HttpStatus.BAD_REQUEST);
                        return;
                    }

                    if(!match.equals("")) {
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
        } catch (IOException var5) {
            var5.printStackTrace();
        }
        resetButton.addActionListener(e -> {
            d0Client.stopSocketListener();
            d1Client.stopSocketListener();
            d2Client.stopSocketListener();
            d0Address.setText("");
            d1Address.setText("");
            d2Address.setText("");
            d0Event.removeAllItems();
            d1Event.removeAllItems();
            d2Event.removeAllItems();
            sendMatches();
        });
        sheetIdLoadButton.addActionListener(e -> {
            spreadsheetId = sheetId.getText();
            new Thread(DivisionSwitcher.this::sendAuxInfo).start();
        });
        enabledCheckBox.addActionListener(e -> new Thread(DivisionSwitcher.this::sendSingleStep).start());

        retriever = new SheetRetriever(new JavalinVerificationCodeReciever(app));
    }

    private Listener heartbeatListener(Client client, JLabel status) {
        status.setBackground(Color.GREEN);
        Timer timer = new Timer();
        final TimerTask[] task = {new TimerTask() {
            @Override
            public void run() {
                status.setBackground(Color.RED);
            }
        }};
        timer.schedule(task[0], 15000);

        return (a, b) -> {
            status.setBackground(Color.GREEN);
            task[0].cancel();
            task[0] = new TimerTask() {
                @Override
                public void run() {
                    status.setBackground(Color.RED);
                }
            };
            timer.schedule(task[0], 15000);
        };
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
            e.printStackTrace();
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
        List<Match> matches = new ArrayList<>();
        Iterator<Match> itA;
        Iterator<Match> itB;

        itA = d1Client.matches().stream().filter((m) -> m.id().matchType() == MatchType.PRACTICE).iterator();
        itB = d2Client.matches().stream().filter((m) -> m.id().matchType() == MatchType.PRACTICE).iterator();

        interleaveMatches(matches, itA, itB);

        itA = d1Client.matches().stream().filter((m) -> m.id().matchType() == MatchType.QUALS).iterator();
        itB = d2Client.matches().stream().filter((m) -> m.id().matchType() == MatchType.QUALS).iterator();

        interleaveMatches(matches, itA, itB);

        itA = d1Client.matches().stream().filter((m) -> m.id().matchType().isSemiFinal()).iterator();
        itB = d2Client.matches().stream().filter((m) -> m.id().matchType().isSemiFinal()).iterator();

        interleaveMatches(matches, itA, itB);

        itA = d1Client.matches().stream().filter((m) -> m.id().matchType() == MatchType.FINAL).iterator();
        itB = d2Client.matches().stream().filter((m) -> m.id().matchType() == MatchType.FINAL).iterator();

        interleaveMatches(matches, itA, itB);
        matches.addAll(d0Client.matches());

        return matches;
    }

    private void interleaveMatches(List<Match> matches, Iterator<Match> itA, Iterator<Match> itB) {
        while (itA.hasNext() || itB.hasNext()) {
            if (itA.hasNext()) matches.add(itA.next());
            if (itB.hasNext()) matches.add(itB.next());
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

    private void sendShowMessage(String s) {
        switch (s) {
            case "1" -> division1RadioButton.setSelected(true);
            case "2" -> division2RadioButton.setSelected(true);
            case "r" -> rankingsRadioButton.setSelected(true);
        }
        if(s.equals("r")) s = "rank";

        if(switcherClient != null) {
            switcherClient.send("/display/show", s);
        }
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
