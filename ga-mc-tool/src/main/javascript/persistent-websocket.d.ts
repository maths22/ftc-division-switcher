declare module "persistent-websocket" {
    type PersistentWebsocketOptions = {
        debug: boolean,
        initialBackoffDelayMillis: number,
        maxBackoffDelayMillis: number,
        pingSendFunction: (socket: PersistentWebsocket) => void,
        pingIntervalSeconds: number,
        pingTimeoutMillis: number,
        connectTimeoutMillis: number,
        reachabilityTestUrl: string,
        reachabilityTestTimeoutMillis: number,
        reachabilityPollingIntervalMillis: number,
        xhrConstructor: new (...args: ConstructorParameters<XMLHttpRequest>) => InstanceType<XMLHttpRequest>,
        websocketConstructor: new (...args: ConstructorParameters<WebSocket>) => InstanceType<WebSocket>,
    };

    declare class PersistentWebsocket {
        constructor(url: string, options?: Partial<PersistentWebsocketOptions>);
        open(): void;
        close(code?: number, reason?: string): void;
        send(data: string | ArrayBufferLike | Blob | ArrayBufferView): void;

        onclose: ((this: PersistentWebsocket, ev: CloseEvent) => any) | null;
        onerror: ((this: PersistentWebsocket, ev: Event) => any) | null;
        onmessage: ((this: PersistentWebsocket, ev: MessageEvent) => any) | null;
        onopen: ((this: PersistentWebsocket, ev: Event) => any) | null;
    }

}