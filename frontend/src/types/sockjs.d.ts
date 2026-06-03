declare module 'sockjs-client/dist/sockjs' {
  import { EventEmitter } from 'events';

  interface SockJSOptions {
    server?: string;
    sessionId?: string | ((options: any) => string);
    transports?: string | string[];
    cookie_policy?: string;
    rtt?: number;
    ws_main?: any;
    info?: any;
  }

  class SockJS extends EventEmitter {
    constructor(url: string, protocols?: string | string[], options?: SockJSOptions);
    readonly protocol: string;
    readonly readyState: number;
    readonly bufferedAmount: number;
    readonly binaryType: string;
    close(code?: number, reason?: string): void;
    send(data: any): void;
    URL: string;
  }

  export default SockJS;
}