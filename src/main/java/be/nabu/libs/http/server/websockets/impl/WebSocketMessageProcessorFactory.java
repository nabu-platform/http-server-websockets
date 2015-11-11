package be.nabu.libs.http.server.websockets.impl;

import java.util.ArrayList;
import java.util.List;

import be.nabu.libs.events.api.EventDispatcher;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.nio.api.MessageProcessor;
import be.nabu.libs.nio.api.MessageProcessorFactory;

public class WebSocketMessageProcessorFactory implements MessageProcessorFactory<WebSocketRequest, WebSocketMessage> {

	private EventDispatcher dispatcher;
	private List<WebSocketRequest> chain = new ArrayList<WebSocketRequest>();

	public WebSocketMessageProcessorFactory(EventDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}
	
	@Override
	public MessageProcessor<WebSocketRequest, WebSocketMessage> newProcessor(WebSocketRequest request) {
		return new WebSocketMessageProcessor(this, dispatcher);
	}

	List<WebSocketRequest> getChain() {
		return chain;
	}

}
