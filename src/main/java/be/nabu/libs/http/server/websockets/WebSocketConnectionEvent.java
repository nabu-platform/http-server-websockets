package be.nabu.libs.http.server.websockets;

import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.nio.api.Pipeline;

public interface WebSocketConnectionEvent {
	public enum ConnectionState {
		READY
	}
	public ConnectionState getState();
	public Pipeline getPipeline();
	public Token getToken();
}
