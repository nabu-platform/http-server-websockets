package be.nabu.libs.http.server.websockets.api;

import java.util.List;

import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.Token;

public interface WebSocketRequest extends WebSocketMessage {
	public Token getToken();
	public Device getDevice();
	public String getPath();
	public double getVersion();
	public boolean isMasked();
	public List<String> getProtocols();
}
