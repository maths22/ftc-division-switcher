// adapted from https://github.com/enmasseio/timesync/issues/25
declare module "timesync" {
    interface TimeSync<Type> {
        destroy();
        now(): number;
        on(event: "change", callback: (offset: number) => void);
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        on(event: "error", callback: (err: any) => void);
        on(event: "sync", callback: (value: "start" | "end") => void);
        off(event: "change" | "error" | "sync", callback?: () => void);
        sync();

        send(to: Type, data: object, timeout: number): Promise<void>;
        receive(from: Type | null, data: object);
    }

    interface TimeSyncCreateOptions<Type> {
        interval?: number;
        timeout?: number;
        delay?: number;
        repeat?: number;
        peers?: string | string[];
        server?: Type;
        now?: () => number;
    }

    function create<Type>(options: TimeSyncCreateOptions<Type>): TimeSync<Type>;
}
