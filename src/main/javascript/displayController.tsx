import 'bootstrap/dist/css/bootstrap.min.css';

import {createRoot} from 'react-dom/client';
import React, {useEffect, useState} from 'react';
import {
    AuxInfo,
    Match,
    MatchData,
    MatchId,
    MatchType,
    Message,
    Result,
    SingleStep,
    State as StateMessage
} from "./javaTypes";
import {PersistentWebsocket} from "persistent-websocket";
import {Button, Col, Form, Nav, Row, Tab, Table} from "react-bootstrap";
import {FullState, Matches, State} from "./types";
import {getNextState} from "./calculationHelpers";
import App from "./App";



const root = createRoot(document.body);
root.render(<App/>);
