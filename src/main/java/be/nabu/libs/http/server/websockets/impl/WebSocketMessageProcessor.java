package be.nabu.libs.http.server.websockets.impl;

import be.nabu.libs.events.api.EventDispatcher;
import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.nio.api.SecurityContext;
import be.nabu.libs.nio.api.SourceContext;
import be.nabu.libs.nio.impl.EventDrivenMessageProcessor;

public class WebSocketMessageProcessor extends EventDrivenMessageProcessor<WebSocketRequest, WebSocketMessage> {

	private WebSocketMessageProcessorFactory factory;

	public WebSocketMessageProcessor(WebSocketMessageProcessorFactory webSocketMessageProcessorFactory, EventDispatcher dispatcher) {
		super(WebSocketRequest.class, WebSocketMessage.class, dispatcher, new WebSocketExceptionFormatter(), false);
		this.factory = webSocketMessageProcessorFactory;
	}

	// TODO: set max limits on chained data size or # of messages
	@Override
	public WebSocketMessage process(SecurityContext securityContext, SourceContext sourceContext, WebSocketRequest request) {
		if (OpCode.CLOSE.equals(request.getOpCode())) {
			return new WebSocketMessageImpl(OpCode.CLOSE, true, 0, null);
		}
		// send back a PONG
		else if (OpCode.PING.equals(request.getOpCode())) {
			return new WebSocketMessageImpl(OpCode.PONG, true, 0, null);
		}
		// pongs can be ignored
		else if (OpCode.PONG.equals(request.getOpCode())) {
			return null;
		}
		if (request.isFinal()) {
			// Use the factory (one instance per pipeline) to buffer incoming requests for chaining
			if (!factory.getChain().isEmpty()) {
				factory.getChain().add(request);
				request = new WebSocketRequestChain(factory.getChain().toArray(new WebSocketRequest[0]));
				factory.getChain().clear();
			}
			return super.process(securityContext, sourceContext, request);
		}
		else {
			factory.getChain().add(request);
		}
		return null;
	}

	
}
