package be.nabu.libs.http.server.websockets.client;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.authentication.api.TokenValidator;
import be.nabu.libs.events.api.EventDispatcher;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.http.HTTPException;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.LinkableHTTPResponse;
import be.nabu.libs.http.api.server.AuthenticationHeader;
import be.nabu.libs.http.api.server.MessageDataProvider;
import be.nabu.libs.http.core.HTTPUtils;
import be.nabu.libs.http.core.ServerHeader;
import be.nabu.libs.http.server.websockets.WebSocketHandshakeHandler;
import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.http.server.websockets.impl.WebSocketExceptionFormatter;
import be.nabu.libs.http.server.websockets.impl.WebSocketMessageFormatterFactory;
import be.nabu.libs.http.server.websockets.impl.WebSocketMessageProcessorFactory;
import be.nabu.libs.http.server.websockets.impl.WebSocketRequestParserFactory;
import be.nabu.libs.nio.PipelineUtils;
import be.nabu.libs.nio.api.KeepAliveDecider;
import be.nabu.libs.nio.api.MessagePipeline;
import be.nabu.libs.nio.api.Pipeline;
import be.nabu.libs.nio.api.UpgradeableMessagePipeline;
import be.nabu.libs.resources.URIUtils;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.impl.MimeUtils;

public class ClientWebSocketUpgradeHandler implements EventHandler<HTTPResponse, HTTPRequest> {

	private MessageDataProvider dataProvider;
	private Logger logger = LoggerFactory.getLogger(getClass());
	private boolean shouldMaskResponses;
	private EventDispatcher dispatcher;
	
	public ClientWebSocketUpgradeHandler(MessageDataProvider dataProvider) {
		this(dataProvider, true, null);
	}
	public ClientWebSocketUpgradeHandler(MessageDataProvider dataProvider, boolean shouldMaskResponses) {
		this(dataProvider, shouldMaskResponses, null);
	}
	public ClientWebSocketUpgradeHandler(MessageDataProvider dataProvider, boolean shouldMaskResponses, EventDispatcher dispatcher) {
		this.dataProvider = dataProvider;
		this.shouldMaskResponses = shouldMaskResponses;
		this.dispatcher = dispatcher;
	}
	
	@Override
	public HTTPRequest handle(HTTPResponse response) {
		try {
			// we are only interested in 101 codes
			if (response.getCode() != 101) {
				return null;
			}
			
			// with the given header
			Header header = MimeUtils.getHeader("Upgrade", response.getContent().getHeaders());
			if (header == null || !header.getValue().equalsIgnoreCase("websocket")) {
				return null;
			}
			header = MimeUtils.getHeader("Sec-WebSocket-Accept", response.getContent().getHeaders());
			if (header == null) {
				return null;
			}
			
			double version = 13;
			TokenValidator tokenValidator = null;
			Device device = null;
			
			Pipeline pipeline = PipelineUtils.getPipeline();
			if (pipeline instanceof UpgradeableMessagePipeline) {
				HTTPRequest request = ((LinkableHTTPResponse) response).getRequest();
				
				Header key = MimeUtils.getHeader("Sec-WebSocket-Key", request.getContent().getHeaders());
				if (key == null) {
					throw new IllegalStateException("No key sent along in request");
				}
				String expected = WebSocketHandshakeHandler.calculateResponse(key.getValue());
				if (!expected.equals(header.getValue().trim())) {
					throw new IOException("The server did not respond with the expected value '" + expected + "', instead we received '" + header.getValue() + "'");
				}
				
				List<String> protocols = new ArrayList<String>();
				Header protocolHeader = MimeUtils.getHeader("Sec-WebSocket-Protocol", request.getContent().getHeaders());
				if (protocolHeader != null) {
					protocols.addAll(Arrays.asList(protocolHeader.getValue().split("[\\s]*,[\\s]*")));
				}
				
				AuthenticationHeader authenticationHeader = HTTPUtils.getAuthenticationHeader(request.getContent().getHeaders());
				Token token = authenticationHeader == null ? null : authenticationHeader.getToken();
				
				String requestPath = request.getTarget();
				
				// currently the only situation where we don't want to mask responses is in the reverse proxy which has to forward stuff
				// it is also in this context that we do proxy path rewriting, so we reuse the same boolean
				// if at some point we want multiple scenarios: add an additional boolean!
				if (!shouldMaskResponses) {
					// if it is an absolute path, we want to actual path (do we need query params etc as well?)
					// this may be more broadly applicable, but for now it is only breaking the reverse proxy so we will localize the fix
					if (requestPath.matches("^[\\w]+:/.*")) {
						URI uri = new URI(URIUtils.encodeURI(requestPath, false));
						requestPath = uri.getPath();
						if (uri.getQuery() != null) {
							requestPath += "?" + uri.getQuery();
						}
					}
					// the request that is linked to the response may already have been rewritten to strip the proxy path
					// if however, we leave the path like that, we will not be able to correctly subscribe to any messages because the paths don't match
					Header proxyPath = MimeUtils.getHeader(ServerHeader.PROXY_PATH.getName(), request.getContent().getHeaders());
					if (proxyPath != null && proxyPath.getValue() != null) {
						requestPath = proxyPath.getValue().replaceAll("[/]+$", "") + "/" + requestPath.replaceFirst("^[/]+", "");
					}
				}
				MessagePipeline<WebSocketRequest, WebSocketMessage> upgrade = ((UpgradeableMessagePipeline<?, ?>) pipeline).upgrade(
					new WebSocketRequestParserFactory(dataProvider, protocols, requestPath, version, token, device, tokenValidator), 
					new WebSocketMessageFormatterFactory(shouldMaskResponses), 
					new WebSocketMessageProcessorFactory(dispatcher == null ? pipeline.getServer().getDispatcher() : dispatcher), 
					new KeepAliveDecider<WebSocketMessage>() {
						@Override
						public boolean keepConnectionAlive(WebSocketMessage response) {
							return response == null || !OpCode.CLOSE.equals(response.getOpCode());
						}
					}, 
					new WebSocketExceptionFormatter()
				);
				// we want to inherit the context
				upgrade.getContext().putAll(pipeline.getContext());
			}
			else {
				throw new HTTPException(500, "Could not find pipeline to upgrade");
			}
		}
		catch (Exception e) {
			logger.error("Could not upgrade to websocket connection", e);
		}
		return null;
	}

}
