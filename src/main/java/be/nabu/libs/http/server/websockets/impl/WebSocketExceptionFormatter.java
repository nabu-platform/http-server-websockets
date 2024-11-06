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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.nio.api.ExceptionFormatter;

public class WebSocketExceptionFormatter implements ExceptionFormatter<WebSocketRequest, WebSocketMessage> {

	private Logger logger = LoggerFactory.getLogger(getClass());
	
	@Override
	public WebSocketMessage format(WebSocketRequest request, Exception e) {
		logger.warn("Closing websocket connection due to exception", e);
		// TODO: according to spec http://tools.ietf.org/html/rfc6455#section-5.5.1 you can send a 2-byte code followed by a UTF-8 encoded string explaining what went wrong
		return new WebSocketMessageImpl(OpCode.CLOSE, true, 0l, null);
	}

}
