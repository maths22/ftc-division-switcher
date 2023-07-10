package com.maths22.ftc;

import java.util.List;
import java.util.Map;

public abstract sealed class Message {
    public final MessageType messageType;

    protected Message(MessageType messageType) {
        this.messageType = messageType;
    }

    public enum MessageType {
        MATCH_DATA,
        AUX_INFO,
        SINGLE_STEP,
        STATE
    }

    @TypeScriptExport
    public static final class MatchData extends Message {
        public final List<Match> data;

        public MatchData(List<Match> data) {
            super(MessageType.MATCH_DATA);
            this.data = data;
        }
    }

    @TypeScriptExport
    public static final class AuxInfo extends Message {
        public final SheetRetriever.Result data;

        public AuxInfo(SheetRetriever.Result data) {
            super(MessageType.AUX_INFO);
            this.data = data;
        }
    }

    @TypeScriptExport
    public static final class SingleStep extends Message {
        public final boolean data;

        public SingleStep(boolean data) {
            super(MessageType.SINGLE_STEP);
            this.data = data;
        }
    }


    // todo use real types here pls thx
    @TypeScriptExport
    public static final class State extends Message {
        public final String data;

        public State(String data) {
            super(MessageType.STATE);
            this.data = data;
        }
    }
}
