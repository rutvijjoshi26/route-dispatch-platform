import { Client } from '@stomp/stompjs';

/** STOMP endpoint on the same origin as the page, so the dev proxy and nginx both route it. */
export function brokerUrl(location: Location = window.location): string {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${location.host}/ws`;
}

interface ConnectionCallbacks {
  onConnect: () => void;
  onDisconnect: () => void;
}

export function createStompClient(token: string, { onConnect, onDisconnect }: ConnectionCallbacks): Client {
  return new Client({
    brokerURL: brokerUrl(),
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect,
    onWebSocketClose: onDisconnect,
    onStompError: onDisconnect,
  });
}
