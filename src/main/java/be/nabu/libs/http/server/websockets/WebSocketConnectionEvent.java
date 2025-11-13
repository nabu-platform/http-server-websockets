package be.nabu.libs.http.server.websockets;

import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.nio.api.Pipeline;
// this does not seem to work, presumably we are still not getting the timing right
public interface WebSocketConnectionEvent {
	public enum ConnectionState {
		READY
	}
	public ConnectionState getState();
	public Pipeline getPipeline();
	public Token getToken();
	public HTTPRequest getOriginalHTTPRequest();
}
