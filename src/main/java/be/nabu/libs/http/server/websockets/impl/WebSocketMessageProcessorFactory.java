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
