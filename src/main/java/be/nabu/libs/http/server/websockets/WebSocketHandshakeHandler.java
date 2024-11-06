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

package be.nabu.libs.http.server.websockets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.libs.http.HTTPCodes;
import be.nabu.libs.authentication.api.Authenticator;
import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.PermissionHandler;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.authentication.api.TokenValidator;
import be.nabu.libs.events.api.EventDispatcher;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.impl.WebSocketExceptionFormatter;
import be.nabu.libs.http.server.websockets.impl.WebSocketMessageFormatterFactory;
import be.nabu.libs.http.server.websockets.impl.WebSocketMessageProcessorFactory;
import be.nabu.libs.http.server.websockets.impl.WebSocketRequestParserFactory;
import be.nabu.libs.http.HTTPException;
import be.nabu.libs.nio.PipelineUtils;
import be.nabu.libs.nio.api.KeepAliveDecider;
import be.nabu.libs.nio.api.Pipeline;
import be.nabu.libs.nio.api.UpgradeableMessagePipeline;
import be.nabu.utils.codec.TranscoderUtils;
import be.nabu.utils.codec.impl.Base64Encoder;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.MimeUtils;
import be.nabu.utils.mime.impl.PlainMimeEmptyPart;
import be.nabu.utils.security.DigestAlgorithm;
import be.nabu.utils.security.SecurityUtils;
import be.nabu.libs.http.api.server.DeviceResolver;
import be.nabu.libs.http.api.server.MessageDataProvider;
import be.nabu.libs.http.api.server.TokenResolver;
import be.nabu.libs.http.core.DefaultHTTPResponse;

/**
 * Based among other resources on: https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API/Writing_WebSocket_servers
 * 
 * For example the client sends:
 * 
 * GET /chat HTTP/1.1
 * Host: example.com:8000
 * Upgrade: websocket
 * Connection: Upgrade
 * Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
 * Sec-WebSocket-Version: 13
 * 
 */
public class WebSocketHandshakeHandler implements EventHandler<HTTPRequest, HTTPResponse> {

	private MessageDataProvider dataProvider;
	private boolean shouldMaskResponses;
	private EventDispatcher dispatcher;
	
	private TokenResolver tokenResolver;
	private DeviceResolver deviceResolver;
	private TokenValidator tokenValidator;
	private PermissionHandler permissionHandler;
	
	private boolean requireUpgrade;

	public WebSocketHandshakeHandler(EventDispatcher dispatcher, MessageDataProvider dataProvider, boolean shouldMaskResponses) {
		this.dispatcher = dispatcher;
		this.dataProvider = dataProvider;
		this.shouldMaskResponses = shouldMaskResponses;
	}
	
	@Override
	public HTTPResponse handle(HTTPRequest request) {
		// a websocket upgrade request _must_ use the GET method and _must_ be 1.1 or greater 
		if ("GET".equalsIgnoreCase(request.getMethod()) && request.getVersion() >= 1.1 && request.getContent() != null) {
			if (MimeUtils.contains("Connection", "Upgrade", request.getContent().getHeaders())) {
				Header upgradeHeader = MimeUtils.getHeader("Upgrade", request.getContent().getHeaders());
				// the upgrade is a websocket upgrade 
				if (upgradeHeader != null && "websocket".equalsIgnoreCase(upgradeHeader.getValue())) {
					// check if you are allowed
					Token token = tokenResolver == null ? null : tokenResolver.getToken(request.getContent().getHeaders());
					if (token != null && tokenValidator != null) {
						if (!tokenValidator.isValid(token)) {
							token = null;
						}
					}
					if (permissionHandler != null && !permissionHandler.hasPermission(token, request.getTarget(), "WEBSOCKET")) {
						throw new HTTPException(token == null ? 401 : 403, "User '" + (token == null ? Authenticator.ANONYMOUS : token.getName()) + "' does not have permission to upgrade to websockets on: " + request.getTarget());
					}
					
					Device device = deviceResolver == null ? null : deviceResolver.getDevice(request.getContent().getHeaders());
					
					Header versionHeader = MimeUtils.getHeader("Sec-WebSocket-Version", request.getContent().getHeaders());
					// currently we only support version 13 which is (at the time of writing) the latest and the only one that has any cross-browser support
					// any error in the handling must be met with a 400
					if (versionHeader == null || !"13".equals(versionHeader.getValue())) {
						throw new HTTPException(400, "Websocket upgrade request does not have the expected version: " + versionHeader.getValue());
					}
					Header keyHeader = MimeUtils.getHeader("Sec-WebSocket-Key", request.getContent().getHeaders());
					if (keyHeader == null) {
						throw new HTTPException(400, "Websocket upgrade request is missing a key");
					}
					Header protocolHeader = MimeUtils.getHeader("Sec-WebSocket-Protocol", request.getContent().getHeaders());
					List<String> protocols = new ArrayList<String>();
					if (protocolHeader != null) {
						protocols.addAll(Arrays.asList(protocolHeader.getValue().split("[\\s]*,[\\s]*")));
					}
					Pipeline pipeline = PipelineUtils.getPipeline();
					if (pipeline instanceof UpgradeableMessagePipeline) {
						double version = Double.parseDouble(versionHeader.getValue());
						((UpgradeableMessagePipeline<?, ?>) pipeline).upgrade(
							new WebSocketRequestParserFactory(dataProvider, protocols, request.getTarget(), version, token, device, tokenValidator), 
							new WebSocketMessageFormatterFactory(shouldMaskResponses), 
							new WebSocketMessageProcessorFactory(dispatcher), 
							new KeepAliveDecider<WebSocketMessage>() {
								@Override
								public boolean keepConnectionAlive(WebSocketMessage response) {
									return response == null || !OpCode.CLOSE.equals(response.getOpCode());
								}
							}, 
							new WebSocketExceptionFormatter()
						);
						String responseToken;
						try {
							responseToken = calculateResponse(keyHeader.getValue());
						}
						catch (Exception e) {
							throw new HTTPException(500, e);
						}
						PlainMimeEmptyPart content = new PlainMimeEmptyPart(null, 
							new MimeHeader("Upgrade", "websocket"),
							new MimeHeader("Connection", "Upgrade"),
							new MimeHeader("Sec-WebSocket-Accept", responseToken),
							new MimeHeader("Content-Length", "0")
						);
						// if you sent protocols, we must select one or it is considered invalid
						// in the future, we should make this an interface so you can actually choose the protocols you want
						// currently we just accept the first, this also requires support (in the future) in the reverse proxy
						if (!protocols.isEmpty()) {
							content.setHeader(new MimeHeader("Sec-WebSocket-Protocol", protocols.get(0)));
						}
						return new DefaultHTTPResponse(request, 101, HTTPCodes.getMessage(101), content);
					}
					else {
						throw new HTTPException(500, "Could not find pipeline to upgrade");
					}
				}
			}
			if (requireUpgrade) {
				return new DefaultHTTPResponse(request, 426, HTTPCodes.getMessage(426), new PlainMimeEmptyPart(null, 
					new MimeHeader("Content-Length", "0"),
					new MimeHeader("Upgrade", "websocket"),
					new MimeHeader("Connection", "Upgrade")
				));
			}
		}
		return null;
	}

	public static String calculateResponse(String value) throws IOException, NoSuchAlgorithmException {
		// the magic string is detailed in: http://tools.ietf.org/html/rfc6455#section-1.3
		String response = value + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
		byte[] digest = SecurityUtils.digest(new ByteArrayInputStream(response.getBytes("ASCII")), DigestAlgorithm.SHA1);
		byte[] bytes = IOUtils.toBytes(TranscoderUtils.transcodeBytes(IOUtils.wrap(digest, true), new Base64Encoder()));
		return new String(bytes, "ASCII");
	}

	public TokenResolver getTokenResolver() {
		return tokenResolver;
	}

	public void setTokenResolver(TokenResolver tokenResolver) {
		this.tokenResolver = tokenResolver;
	}

	public TokenValidator getTokenValidator() {
		return tokenValidator;
	}

	public void setTokenValidator(TokenValidator tokenValidator) {
		this.tokenValidator = tokenValidator;
	}

	public PermissionHandler getPermissionHandler() {
		return permissionHandler;
	}

	public void setPermissionHandler(PermissionHandler permissionHandler) {
		this.permissionHandler = permissionHandler;
	}

	public boolean isRequireUpgrade() {
		return requireUpgrade;
	}

	public void setRequireUpgrade(boolean requireUpgrade) {
		this.requireUpgrade = requireUpgrade;
	}

	public DeviceResolver getDeviceResolver() {
		return deviceResolver;
	}

	public void setDeviceResolver(DeviceResolver deviceResolver) {
		this.deviceResolver = deviceResolver;
	}
	
}
