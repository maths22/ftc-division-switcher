package com.maths22.ftc;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.util.Headers;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import net.ser1.stomp.Client;
import net.ser1.stomp.Listener;
import org.json.JSONArray;
import org.json.JSONObject;

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

public class DivisionSwitcher {
    private String data;
    private WebSocketChannel websocket;
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
    private Client switcherClient;
    private Client d0Client;
    private Client d1Client;
    private Client d2Client;
    private List<JSONObject> d0Matches = new ArrayList<>();
    private List<JSONObject> d1Matches = new ArrayList<>();
    private List<JSONObject> d2Matches = new ArrayList<>();
    private String spreadsheetId;
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
        d0ConnectButton.addActionListener((e) -> {
            if (d0Client != null) {
                d0Client.disconnect();
            }
            try {

                d0Client = new Client(d0Address.getText(), 43785, "user", "password");
                d0Client.subscribe("/timer/status", (map, s) -> {
                    sendTime("0", s);
                });
                d0Client.subscribe("/display/listmatches", (map, s) -> {
                    JSONArray matchArray = new JSONArray(s);
                    ArrayList<JSONObject> matches = new ArrayList<>();
                    for(int i = 0; i < matchArray.length(); i++) {
                        matches.add(matchArray.getJSONObject(i));
                    }
                    d0Matches = matches;
                    sendMatches();
                });
                d0Client.send("/display/getmatches","");
                d0Client.subscribe("/heartbeat", heartbeatListener(d0Client, d0Status));

            } catch (IOException | LoginException e1) {
                e1.printStackTrace();
            }
        });
        d1ConnectButton.addActionListener((ActionEvent e) -> {
            if (d1Client != null) {
                d1Client.disconnect();
            }
            try {

                d1Client = new Client(d1Address.getText(), 43786, "user", "password");
                d1Client.subscribe("/timer/status", (map, s) -> {
                    sendTime("1", s);
                });
                d1Client.subscribe("/display/listmatches", (map, s) -> {
                    JSONArray matchArray = new JSONArray(s);
                    ArrayList<JSONObject> matches = new ArrayList<>();
                    for(int i = 0; i < matchArray.length(); i++) {
                        matches.add(matchArray.getJSONObject(i));
                    }
                    d1Matches = matches;
                    sendMatches();
                });
                d1Client.send("/display/getmatches","");
                d1Client.subscribe("/heartbeat", heartbeatListener(d1Client, d1Status));

            } catch (IOException | LoginException e1) {
                e1.printStackTrace();
            }
        });
        d2ConnectButton.addActionListener((e) -> {
            if (d2Client != null) {
                d2Client.disconnect();
            }
            try {

                d2Client = new Client(d2Address.getText(), 43787, "user", "password");
                d2Client.subscribe("/timer/status", (map, s) -> {
                    sendTime("2", s);
                });
                d2Client.subscribe("/display/listmatches", (map, s) -> {
                    JSONArray matchArray = new JSONArray(s);
                    ArrayList<JSONObject> matches = new ArrayList<>();
                    for(int i = 0; i < matchArray.length(); i++) {
                        matches.add(matchArray.getJSONObject(i));
                    }
                    d2Matches = matches;
                    sendMatches();
                });
                d2Client.send("/display/getmatches","");
                d2Client.subscribe("/heartbeat", heartbeatListener(d2Client, d2Status));

            } catch (IOException | LoginException e1) {
                e1.printStackTrace();
            }
        });

        Undertow server = Undertow.builder()
                .addHttpListener(8887, "0.0.0.0")
                .setHandler(Handlers.path()
                        .addPrefixPath("/load", exchange -> {
                            if(d0Client != null) {
                                d0Client.send("/display/getmatches","");
                            }
                            if(d1Client != null) {
                                d1Client.send("/display/getmatches","");
                            }
                            if(d2Client != null) {
                                d2Client.send("/display/getmatches","");
                            }
                            new Thread(DivisionSwitcher.this::sendAuxInfo).start();
                        })
                        .addPrefixPath("/matchstream", Handlers.websocket((exchange, channel) -> {
                            websocket = channel;
                            channel.resumeReceives();
                        }))
                        .addPrefixPath("/api", exchange -> {
                            if(exchange.getQueryParameters().get("division") == null) {
                                exchange.setStatusCode(400);
                                return;
                            }
                            String target = exchange.getQueryParameters().get("division").peekFirst();
                            String display = exchange.getQueryParameters().get("display").peekFirst();
                            String match = exchange.getQueryParameters().get("match").peekFirst();
                            if(target == null) {
                                exchange.setStatusCode(400);
                                return;
                            }
                            if(target.equals("0") || (target.equals("1") && match.isEmpty())) {
                                sendD0DisplayMessage(display, match);
                            }
                            if(target.equals("1")) {
                                sendD1DisplayMessage(display, match);
                            }
                            if(target.equals("2")) {
                                sendD2DisplayMessage(display, match);
                            }

                            if(target.equals("1") || target.equals("2") || target.equals("r")) {
                                sendShowMessage(target);
                            } else {
                                exchange.setStatusCode(400);
                                return;
                            }

                            data = exchange.getQueryParameters().get("data").peekFirst();
                            sendState();

                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                            exchange.getResponseSender().send("Updated");

                        })
                        .addPrefixPath("/",
                                Handlers.resource(new ClassPathResourceManager(this.getClass().getClassLoader(), "serve")))
                )
                .build();
        server.start();
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
            if (d0Client != null) {
                d0Client.disconnect();
                d0Client = null;
            }
            if (d1Client != null) {
                d1Client.disconnect();
                d1Client = null;
            }
            if (d2Client != null) {
                d2Client.disconnect();
                d2Client = null;
            }
            d0Matches = new ArrayList<>();
            d1Matches = new ArrayList<>();
            d2Matches = new ArrayList<>();
            sendMatches();
        });
        sheetIdLoadButton.addActionListener(e -> {
            spreadsheetId = sheetId.getText();
            new Thread(DivisionSwitcher.this::sendAuxInfo).start();
        });
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
        if(websocket == null) return;
        for(WebSocketChannel socket : websocket.getPeerConnections()) {
            WebSockets.sendText(send.toString(), socket, null);
        }
    }

    private void ping() {
        if(websocket == null) return;
        for(WebSocketChannel socket : websocket.getPeerConnections()) {
            WebSockets.sendText("ping", socket, null);
        }
    }

    private void sendState() {
        if(websocket == null) return;
        if(data == null) return;
        JSONObject send = new JSONObject();
        send.put("data", data);
        send.put("type", "state");
        for(WebSocketChannel socket : websocket.getPeerConnections()) {
            WebSockets.sendText(send.toString(), socket, null);
        }
    }

    private void sendAuxInfo() {
        if(websocket == null) return;
        if(spreadsheetId == null || spreadsheetId.isEmpty()) return;
        try {
            JSONObject send = new JSONObject();
            SheetRetriever.Result res = retriever.getTeamInfo(spreadsheetId);
            JSONObject data = new JSONObject();
            data.put("titles", res.getTitles());
            data.put("entries", res.getEntries());
            send.put("data", data);
            send.put("type", "auxInfo");
            for(WebSocketChannel socket : websocket.getPeerConnections()) {
                WebSockets.sendText(send.toString(), socket, null);
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

        itA = d1Matches.stream().filter((m) -> m.getString("id").contains(" SF-")).iterator();
        itB = d2Matches.stream().filter((m) -> m.getString("id").contains(" SF-")).iterator();

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
        if(websocket != null) {
            for (WebSocketChannel socket : websocket.getPeerConnections()) {
                WebSockets.sendText(send.toString(), socket, null);
            }
            WebSockets.sendText(send.toString(), websocket, null);
        }
        sendState();
    }

    private void interleaveMatches(List<JSONObject> matches, Iterator<JSONObject> itA, Iterator<JSONObject> itB) {
        while (itA.hasNext() || itB.hasNext()) {
            if (itA.hasNext()) matches.add(itA.next());
            if (itB.hasNext()) matches.add(itB.next());
        }
    }

    private void sendD0DisplayMessage(String s, String m) {
        if(d0Client != null) {
            d0Client.send("/display/show", s + " " + m);
        }
    }

    private void sendD1DisplayMessage(String s, String m) {
        if(d1Client != null) {
            d1Client.send("/display/show", s + " " + m);
        }
    }

    private void sendD2DisplayMessage(String s, String m) {
        if(d2Client != null) {
            d2Client.send("/display/show", s + " " + m);
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
