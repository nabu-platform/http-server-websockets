package be.nabu.libs.http.server.websockets.api;

import be.nabu.libs.nio.api.SecurityContext;
import be.nabu.libs.nio.api.SourceContext;

public interface PongListener {
	public static final String KEY = "pongListener";
	
	public void pongReceived(SecurityContext securityContext, SourceContext sourceContext, WebSocketRequest request);
}
