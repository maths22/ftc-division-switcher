package com.maths22.ftc;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.maths22.ftc.scoring.client.EventPicker;
import com.maths22.ftc.scoring.client.FtcScoringClient;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinGson;
import io.javalin.websocket.WsContext;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Timer;
import java.util.*;

public class DivisionSwitcher {
    private static final Logger LOG = LoggerFactory.getLogger(DivisionSwitcher.class);
    private final Set<WsContext> matchWsClients = new HashSet<>();
    private final Set<WsContext> divisionWsClients = new HashSet<>();
    private final SheetRetriever retriever;
    private String data;
    private JPanel panel;
    private JTextArea ipTextArea;
    private JButton resetButton;
    private JTextField sheetId;
    private JButton sheetIdLoadButton;
    private JLabel audienceStatus;
    private JCheckBox enabledCheckBox;
    private JPanel nowShowingPicker;
    private List<MatchManager> clients = List.of();
    private Map<Integer, JRadioButton> divisionButtons = new HashMap<>();
    private int activeDivision = 0;
    private EventPicker eventSelector;
    private JTextArea loggerTextArea;
    private String spreadsheetId;
    private String worksheetName;
    private String keyColumn;
    private static final Gson timesyncGson = new Gson();

    public static void main(String[] args) {
        JFrame frame = new JFrame("DivisionSwitcher");
        DivisionSwitcher switcher = new DivisionSwitcher(frame);
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

    public DivisionSwitcher(JFrame frame) {
        eventSelector.setOnPick(((List<FtcScoringClient> scoringClients) -> {
            try {
                clients = scoringClients.stream().map(c -> new MatchManager(c, this::sendMatches)).toList();
                clients.forEach(MatchManager::loadMatches);
                clients.forEach(MatchManager::start);
                nowShowingPicker.removeAll();
                nowShowingPicker.setLayout(new GridLayoutManager(1, clients.size()));
                ButtonGroup newGroup = new ButtonGroup();
                divisionButtons.clear();
                for(int i = 0; i < clients.size(); i++) {
                    MatchManager c = clients.get(i);
                    JRadioButton newButton = new JRadioButton(c.getEvent().name(), i == 0);
                    newButton.addActionListener((evt) -> sendShowMessage(c.getEvent().division()));
                    GridConstraints constraints = new GridConstraints();
                    constraints.setColumn(i);
                    nowShowingPicker.add(newButton, constraints);
                    newGroup.add(newButton);
                    divisionButtons.put(c.getEvent().division(), newButton);
                }
                activeDivision = 0;
                frame.pack();
                sendEventInfo();

                ScoringLogin dialog = new ScoringLogin(clients.get(0));
                dialog.pack();
                dialog.setVisible(true);
            } catch (Exception ex) {
                LOG.error("Failed connecting to event", ex);
            }
        }));

        JavalinVerificationCodeReciever verificationCodeReciever = new JavalinVerificationCodeReciever();
        Javalin.create((config) -> {
                    config.staticFiles.add(sfc -> {
                        sfc.directory = "/serve";
                        sfc.location = Location.CLASSPATH;
                    });
                    config.registerPlugin(verificationCodeReciever);
                    config.jsonMapper(new JavalinGson());
                    config.jetty.modifyServer(server -> {
                        CustomRequestLog requestLog = new CustomRequestLog(new Slf4jRequestLogWriter(), "[%{server}a] %{client}a - %u %t \"%r\" %s %O \"%{Referer}i\" \"%{User-Agent}i\" %D");
                        server.setRequestLog(requestLog);
                    });
                })
                .get("/api/show", ctx -> {
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

                    sendDisplayMessage(clients.stream().filter(c -> c.getEvent().division() == Integer.parseInt(target)).findFirst().orElseThrow().getClient(), display, match);
                    sendShowMessage(Integer.parseInt(target));

                    if(!match.isEmpty() && !display.equals("announcement")) {
                        data = ctx.queryParam("data");
                        sendState();
                    }

                    ctx.json(Map.of("status", "ok"));
                })
                .ws("/api/matchstream", (cfg) -> {
                    cfg.onConnect((ctx) -> {
                        matchWsClients.add(ctx);

                        SheetRetriever.Result auxData = auxData();
                        if(auxData != null) {
                            ctx.send(new Message.AuxInfo(auxData));
                        }
                        ctx.send(new Message.MatchData(mergedMatches()));
                        if(data != null) {
                            ctx.send(new Message.State(data));
                        }
                        ctx.send(new Message.SingleStep(!enabledCheckBox.isSelected()));
                        ctx.send(new Message.EventInfo(clients.stream().map(MatchManager::getEvent).toList()));
                    });
                    cfg.onClose(matchWsClients::remove);
                    cfg.onMessage((ctx) -> {
                        String message = ctx.message();
                        if(message.startsWith("TIMESYNC:")) {
                            if(clients.isEmpty()) {
                                return;
                            }
                            Map<String, Object> req = timesyncGson.fromJson(
                                    message.substring("TIMESYNC:".length()),
                                    new TypeToken<Map<String, Object>>() {}.getType()
                            );
                            clients.get(0).getClient().timesync().thenAccept((v) -> {
                                Map<String, Object> ret = new HashMap<>();
                                ret.put("jsonrpc", "2.0");
                                ret.put("id", req.get("id"));
                                ret.put("result", v);
                                ctx.send("TIMESYNC:" + timesyncGson.toJson(ret));
                            });
                        }
                    });
                })
                .ws("/api/divisionstream", (cfg) -> {
                    cfg.onConnect((ctx) -> {
                        divisionWsClients.add(ctx);

                        ctx.send("division:" + activeDivision);
                    });
                    cfg.onClose(divisionWsClients::remove);
                })
                .start(Integer.parseInt(System.getProperty("serve.port", "8888")));
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
            eventSelector.reset();
            clients.forEach(MatchManager::stop);
            sendMatches();
        });
        sheetIdLoadButton.addActionListener(e -> {
            setSpreadsheetId(sheetId.getText());
        });
        enabledCheckBox.addActionListener(e -> new Thread(DivisionSwitcher.this::sendSingleStep).start());

        retriever = new SheetRetriever(verificationCodeReciever);
        frame.setContentPane(panel);
    }

    private void setSpreadsheetId(String text) {
        spreadsheetId = text.strip();
        try {
            worksheetName = retriever.pickWorksheet(spreadsheetId);
            if(worksheetName == null) {
                return;
            }
            keyColumn = retriever.pickKeyColumn(spreadsheetId, worksheetName);
            if(keyColumn == null) {
                return;
            }
            new Thread(DivisionSwitcher.this::sendAuxInfo).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendTime(String div, String msg) {
        String[] parts = msg.split(" ");

//        for(Session socket : wsClients) {
//            try {
//                socket.getRemote().sendString(gson.toJson(Map.of("type", "time", "data", Map.of(
//                        "div", div,
//                        "phase", toTitleCase(parts[2].replace('_', ' ')),
//                        "min", Integer.parseInt(parts[3]) / 60,
//                        "sec", Integer.parseInt(parts[3]) % 60
//                ))));
//            } catch (IOException e) {
//                LOG.warn("Failed to send time", e);
//            }
//        }
    }

    private void ping() {
        matchWsClients.forEach(c -> c.send("ping"));
        divisionWsClients.forEach(c -> c.send("ping"));
    }

    private void sendState() {
        if(data == null) return;

        Message.State state = new Message.State(data);
        matchWsClients.forEach(c -> c.send(state));
    }

    private void sendSingleStep() {
        Message.SingleStep singleStep = new Message.SingleStep(!enabledCheckBox.isSelected());
        matchWsClients.forEach(c -> c.send(singleStep));
    }

    private void sendAuxInfo() {
        SheetRetriever.Result data = auxData();
        if(data == null) return;
        Message.AuxInfo auxInfo = new Message.AuxInfo(data);
        matchWsClients.forEach(c -> c.send(auxInfo));
    }

    private void sendEventInfo() {
        Message.EventInfo info = new Message.EventInfo(clients.stream().map(MatchManager::getEvent).toList());
        matchWsClients.forEach(c -> c.send(info));
    }

    private SheetRetriever.Result auxData() {
        if(spreadsheetId == null || spreadsheetId.isEmpty() || worksheetName == null || keyColumn == null) return null;
        try {
            return retriever.getTeamInfo(spreadsheetId, worksheetName, keyColumn);
        } catch (IOException e) {
            LOG.error("Failed retrieving aux data", e);
        }
        return null;
    }

    private void sendMatches() {
        Message.MatchData data = new Message.MatchData(mergedMatches());
        matchWsClients.forEach(c -> c.send(data));
    }

    private List<Match> mergedMatches() {
        if(clients.isEmpty()) {
            return List.of();
        }
        List<Match> matches = new ArrayList<>();
        List<Iterator<Match>> iterators;

        iterators = clients.stream().skip(1).map((c) -> c.getMatches().stream().filter(m -> m.id().matchType() == MatchType.PRACTICE).iterator()).toList();
        interleaveMatches(matches, iterators);

        iterators = clients.stream().skip(1).map((c) -> c.getMatches().stream().filter(m -> m.id().matchType() == MatchType.QUALS).iterator()).toList();
        interleaveMatches(matches, iterators);

        iterators = clients.stream().skip(1).map((c) -> c.getMatches().stream().filter(m -> m.id().matchType().isSemiFinal()).iterator()).toList();
        interleaveMatches(matches, iterators);

        iterators = clients.stream().skip(1).map((c) -> c.getMatches().stream().filter(m -> m.id().matchType() == MatchType.FINAL).iterator()).toList();
        interleaveMatches(matches, iterators);

        matches.addAll(clients.get(0).getMatches());

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
                case "prematch" -> client.showMatch(m);
                case "results" -> client.showResults(m);
                case "rankings" -> client.showRanks();
                case "announcement" -> client.showMessage(m);
                case "alliance" -> client.showSelection();
                case "sponsor" -> client.showSponsors();
                case "elimination" -> client.showBracket();
                case "status" -> client.showInspectionStatus();
                case "wifi", "blank", "video", "key", "online", "safety_security", "slideshow" ->
                        client.basicCommand(s);
            }
        }
    }

    private void sendShowMessage(int i) {
        divisionButtons.get(i).setSelected(true);
        activeDivision = i;

        divisionWsClients.forEach(c -> c.send("division:" + i));
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
