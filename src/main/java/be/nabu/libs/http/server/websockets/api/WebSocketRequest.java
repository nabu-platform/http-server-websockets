package be.nabu.libs.http.server.websockets.api;

import java.io.InputStream;
import java.util.List;

import be.nabu.libs.authentication.api.Token;

public interface WebSocketRequest extends WebSocketMessage {
	public Token getToken();
	public String getPath();
	public double getVersion();
	public OpCode getOpCode();
	public boolean isFinal();
	public boolean isMasked();
	public long getSize();
	public InputStream getData();
	public List<String> getProtocols();
}
