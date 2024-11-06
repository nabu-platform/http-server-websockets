/*
* Copyright (C) 2015 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.libs.http.server.websockets.impl;

import java.util.Map;

import be.nabu.libs.events.api.EventDispatcher;
import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.PongListener;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.nio.PipelineUtils;
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
			Map<String, Object> context = PipelineUtils.getPipeline().getContext();
			PongListener listener = (PongListener) context.remove(PongListener.KEY);
			if (listener != null) {
				listener.pongReceived(securityContext, sourceContext, request);
			}
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
