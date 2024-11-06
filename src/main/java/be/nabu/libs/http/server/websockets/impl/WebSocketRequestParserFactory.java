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

import java.util.List;

import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.authentication.api.TokenValidator;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.nio.api.MessageParser;
import be.nabu.libs.nio.api.MessageParserFactory;
import be.nabu.libs.http.api.server.MessageDataProvider;

public class WebSocketRequestParserFactory implements MessageParserFactory<WebSocketRequest> {

	private MessageDataProvider dataProvider;
	private List<String> protocols;
	private String path;
	private double version;
	private Token token;
	private TokenValidator tokenValidator;
	private Device device;

	public WebSocketRequestParserFactory(MessageDataProvider dataProvider, List<String> protocols, String path, double version, Token token, Device device, TokenValidator tokenValidator) {
		this.protocols = protocols;
		this.path = path;
		this.version = version;
		this.dataProvider = dataProvider;
		this.token = token;
		this.device = device;
		this.tokenValidator = tokenValidator;
	}
	
	@Override
	public MessageParser<WebSocketRequest> newMessageParser() {
		if (token != null && tokenValidator != null && !tokenValidator.isValid(token)) {
			throw new RuntimeException("The token is no longer valid");
		}
		return new WebSocketRequestParser(dataProvider, protocols, path, version, token, device);
	}

	public MessageDataProvider getDataProvider() {
		return dataProvider;
	}

	public List<String> getProtocols() {
		return protocols;
	}

	public String getPath() {
		return path;
	}

	public double getVersion() {
		return version;
	}

	public Token getToken() {
		return token;
	}

	public TokenValidator getTokenValidator() {
		return tokenValidator;
	}

	public Device getDevice() {
		return device;
	}

	public void setToken(Token token) {
		this.token = token;
	}

	public void setDevice(Device device) {
		this.device = device;
	}
	
}
