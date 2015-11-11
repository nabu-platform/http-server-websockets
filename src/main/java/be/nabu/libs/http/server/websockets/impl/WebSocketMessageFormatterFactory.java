package be.nabu.libs.http.server.websockets.impl;

import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.nio.api.MessageFormatter;
import be.nabu.libs.nio.api.MessageFormatterFactory;

public class WebSocketMessageFormatterFactory implements MessageFormatterFactory<WebSocketMessage> {

	private boolean shouldMask;

	public WebSocketMessageFormatterFactory(boolean shouldMask) {
		this.shouldMask = shouldMask;
	}
	
	@Override
	public MessageFormatter<WebSocketMessage> newMessageFormatter() {
		return new WebSocketMessageFormatter(shouldMask);
	}

}
