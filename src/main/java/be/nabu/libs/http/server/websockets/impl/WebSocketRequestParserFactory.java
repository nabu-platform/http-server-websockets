package be.nabu.libs.http.server.websockets.impl;

import java.util.List;

import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.nio.api.MessageParser;
import be.nabu.libs.nio.api.MessageParserFactory;
import be.nabu.libs.http.api.server.MessageDataProvider;

public class WebSocketRequestParserFactory implements MessageParserFactory<WebSocketRequest> {

	private MessageDataProvider dataProvider;
	private List<String> protocols;
	private String path;
	private double version;

	public WebSocketRequestParserFactory(MessageDataProvider dataProvider, List<String> protocols, String path, double version) {
		this.protocols = protocols;
		this.path = path;
		this.version = version;
		this.dataProvider = dataProvider;
	}
	
	@Override
	public MessageParser<WebSocketRequest> newMessageParser() {
		return new WebSocketRequestParser(dataProvider, protocols, path, version);
	}

}
