package be.nabu.libs.http.server.websockets.impl;

import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.nio.api.ExceptionFormatter;

public class WebSocketExceptionFormatter implements ExceptionFormatter<WebSocketRequest, WebSocketMessage> {

	@Override
	public WebSocketMessage format(WebSocketRequest request, Exception e) {
		// TODO: according to spec http://tools.ietf.org/html/rfc6455#section-5.5.1 you can send a 2-byte code followed by a UTF-8 encoded string explaining what went wrong
		return new WebSocketMessageImpl(OpCode.CLOSE, true, 0l, null);
	}

}
