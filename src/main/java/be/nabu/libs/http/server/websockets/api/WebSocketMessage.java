package be.nabu.libs.http.server.websockets.api;

import java.io.InputStream;

public interface WebSocketMessage {
	public OpCode getOpCode();
	public boolean isFinal();
	public long getSize();
	public InputStream getData();
}
