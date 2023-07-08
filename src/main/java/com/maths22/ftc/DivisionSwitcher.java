package com.maths22.ftc;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import net.ser1.stomp.Client;
import net.ser1.stomp.Listener;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONArray;
import org.json.JSONObject;
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
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DivisionSwitcher {
    private static final Logger LOG = LoggerFactory.getLogger(DivisionSwitcher.class);
    private final Set<Session> wsClients = new HashSet<>();
    private ScheduledFuture<?> d0Loop = null;
    private ScheduledFuture<?> d1Loop = null;
    private ScheduledFuture<?> d2Loop = null;
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
    private FtcScoringClient d0Client = new FtcScoringClient(0);
    private FtcScoringClient d1Client = new FtcScoringClient(1);
    private FtcScoringClient d2Client = new FtcScoringClient(2);
    private List<JSONObject> d0Matches = new ArrayList<>();
    private List<JSONObject> d1Matches = new ArrayList<>();
    private List<JSONObject> d2Matches = new ArrayList<>();
    private String spreadsheetId;
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);

    private final SheetRetriever retriever = new SheetRetriever();

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
            if (d0Loop != null) {
                d0Loop.cancel(true);
            }
            try {
                d0Client.setEvent((String) d1Event.getSelectedItem());
                if(d0Client.getEvent() == null) return;
//                d1Client.subscribe("/timer/status", (map, s) -> {
//                    sendTime("1", s);
//                });

                d0Loop = executor.scheduleAtFixedRate(() -> {
                    try {
                        d0Matches = d0Client.getMatches().stream().map(Match::toJson).collect(Collectors.toList());
                        sendMatches();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }, 0, 5, TimeUnit.SECONDS);

                ScoringLogin dialog = new ScoringLogin(d0Client);
                dialog.pack();
                dialog.setVisible(true);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        });
        d1ConnectButton.addActionListener((ActionEvent e) -> {
            if (d1Loop != null) {
                d1Loop.cancel(true);
            }
            try {
                d1Client.setEvent((String) d1Event.getSelectedItem());
                if(d1Client.getEvent() == null) return;
//                d1Client.subscribe("/timer/status", (map, s) -> {
//                    sendTime("1", s);
//                });

                d1Loop = executor.scheduleAtFixedRate(() -> {
                    try {
                        d1Matches = d1Client.getMatches().stream().map(Match::toJson).collect(Collectors.toList());
                        sendMatches();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }, 0, 5, TimeUnit.SECONDS);

                ScoringLogin dialog = new ScoringLogin(d1Client);
                dialog.pack();
                dialog.setVisible(true);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        });
        d2ConnectButton.addActionListener((ActionEvent e) -> {
            if (d2Loop != null) {
                d2Loop.cancel(true);
            }
            try {
                d2Client.setEvent((String) d1Event.getSelectedItem());
                if(d2Client.getEvent() == null) return;
//                d2Client.subscribe("/timer/status", (map, s) -> {
//                    sendTime("1", s);
//                });

                d2Loop = executor.scheduleAtFixedRate(() -> {
                    try {
                        d2Matches = d2Client.getMatches().stream().map(Match::toJson).collect(Collectors.toList());
                        sendMatches();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }, 0, 5, TimeUnit.SECONDS);

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
                .get("/load", ctx -> {
                    new Thread(this::sendAuxInfo).start();
                    new Thread(this::sendMatches).start();
                    new Thread(this::sendSingleStep).start();
                })
                .get("/api", ctx -> {
                    if(ctx.queryParam("division") == null) {
                        ctx.status(HttpStatus.BAD_REQUEST);
                        return;
                    }
                    String target = ctx.queryParam("division");
                    String display = ctx.queryParam("display");
                    String match = "";
                    if(ctx.queryParamMap().containsKey("match")) {
                        match = ctx.queryParam("match");
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

                    data = ctx.queryParam("data");
                    sendState();

                    ctx.contentType("text/plain");
                    ctx.result("Updated");
                })
                .ws("/matchstream", (cfg) -> {
                    cfg.onConnect((ctx) -> {
                        wsClients.add(ctx.session);
                    });
                    cfg.onClose((ctx) -> {
                        wsClients.remove(ctx.session);
                    });
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
            if (ifs == null) {
                ipTextArea.setText(ipTextArea.getText() + "No Network Interfaces present\r\n");

                ipTextArea.setText(ipTextArea.getText() + "http://127.0.0.1:8080\r\n");
            } else {
                while(ifs.hasMoreElements()) {
                    NetworkInterface ni = ifs.nextElement();
                    Enumeration addrs = ni.getInetAddresses();

                    while(addrs.hasMoreElements()) {
                        InetAddress ia = (InetAddress)addrs.nextElement();
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
            }
            ipTextArea.setEditable(false);
        } catch (IOException var5) {
            var5.printStackTrace();
        }
        resetButton.addActionListener(e -> {
            if (d0Loop != null) {
                d0Loop.cancel(true);
            }
            if (d1Loop != null) {
                d1Loop.cancel(true);
            }
            if (d2Loop != null) {
                d2Loop.cancel(true);
            }
            d0Matches = new ArrayList<>();
            d1Matches = new ArrayList<>();
            d2Matches = new ArrayList<>();
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


        JSONObject data = new JSONObject();
        data.put("div", div);
        data.put("phase", toTitleCase(parts[2].replace('_', ' ')));
        data.put("min", Integer.parseInt(parts[3]) / 60);
        data.put("sec", Integer.parseInt(parts[3]) % 60);

        JSONObject send = new JSONObject();
        send.put("type", "time");
        send.put("data", data);
        for(Session socket : wsClients) {
            try {
                socket.getRemote().sendString(send.toString());
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
        JSONObject send = new JSONObject();
        send.put("data", data);
        send.put("type", "state");
        for(Session socket : wsClients) {
            try {
                socket.getRemote().sendString(send.toString());
            } catch (IOException e) {
                LOG.warn("Failed to send state", e);
            }
        }
    }

    private void sendSingleStep() {
        JSONObject send = new JSONObject();
        send.put("data", enabledCheckBox.isSelected() ? 0 : 1);
        send.put("type", "singleStep");
        for(Session socket : wsClients) {
            try {
                socket.getRemote().sendString(send.toString());
            } catch (IOException e) {
                LOG.warn("Failed to send step", e);
            }
        }
    }

    private void sendAuxInfo() {
        if(spreadsheetId == null || spreadsheetId.isEmpty()) return;
        try {
            JSONObject send = new JSONObject();
            SheetRetriever.Result res = retriever.getTeamInfo(spreadsheetId);
            JSONObject data = new JSONObject();
            data.put("titles", res.getTitles());
            data.put("entries", res.getEntries());
            send.put("data", data);
            send.put("type", "auxInfo");
            for(Session socket : wsClients) {
                socket.getRemote().sendString(send.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMatches() {
        List<JSONObject> matches = new ArrayList<>();
        Iterator<JSONObject> itA;
        Iterator<JSONObject> itB;

        itA = d1Matches.stream().filter((m) -> m.getString("id").contains(" P-")).iterator();
        itB = d2Matches.stream().filter((m) -> m.getString("id").contains(" P-")).iterator();

        interleaveMatches(matches, itA, itB);

        itA = d1Matches.stream().filter((m) -> m.getString("id").contains(" Q-")).iterator();
        itB = d2Matches.stream().filter((m) -> m.getString("id").contains(" Q-")).iterator();

        interleaveMatches(matches, itA, itB);

        itA = d1Matches.stream().filter((m) -> m.getString("id").contains(" R")).iterator();
        itB = d2Matches.stream().filter((m) -> m.getString("id").contains(" R")).iterator();

        interleaveMatches(matches, itA, itB);

        itA = d1Matches.stream().filter((m) -> m.getString("id").contains(" F-")).iterator();
        itB = d2Matches.stream().filter((m) -> m.getString("id").contains(" F-")).iterator();

        interleaveMatches(matches, itA, itB);
        matches.addAll(d0Matches);

        JSONArray array = new JSONArray();
        matches.forEach(array::put);
        JSONObject send = new JSONObject();
        send.put("data", array);
        send.put("type", "matchData");

        for(Session socket : wsClients) {
            try {
                socket.getRemote().sendString(send.toString());
            } catch (IOException e) {
                LOG.warn("Failed to send matches", e);
            }
        }
        sendState();
    }

    private void interleaveMatches(List<JSONObject> matches, Iterator<JSONObject> itA, Iterator<JSONObject> itB) {
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
        switch(s) {
            case "1":
                division1RadioButton.setSelected(true);
                break;
            case "2":
                division2RadioButton.setSelected(true);
                break;
            case "r":
                rankingsRadioButton.setSelected(true);
                break;
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
