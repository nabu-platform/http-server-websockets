package be.nabu.libs.http.server.websockets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.jws.WebResult;
import javax.net.ssl.SSLContext;

import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.RoleHandler;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.authentication.api.TokenValidator;
import be.nabu.libs.authentication.api.principals.BasicPrincipal;
import be.nabu.libs.events.api.EventDispatcher;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.events.api.EventSubscription;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.client.HTTPClient;
import be.nabu.libs.http.api.server.MessageDataProvider;
import be.nabu.libs.http.client.nio.NIOHTTPClientImpl;
import be.nabu.libs.http.core.DefaultHTTPRequest;
import be.nabu.libs.http.core.HTTPUtils;
import be.nabu.libs.http.server.SimpleAuthenticationHeader;
import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.http.server.websockets.client.ClientWebSocketUpgradeHandler;
import be.nabu.libs.http.server.websockets.impl.WebSocketRequestParserFactory;
import be.nabu.libs.http.server.websockets.util.PathFilter;
import be.nabu.libs.nio.PipelineUtils;
import be.nabu.libs.nio.api.MessageParserFactory;
import be.nabu.libs.nio.api.NIOServer;
import be.nabu.libs.nio.api.Pipeline;
import be.nabu.libs.nio.api.StandardizedMessagePipeline;
import be.nabu.utils.codec.TranscoderUtils;
import be.nabu.utils.codec.impl.Base64Encoder;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.impl.FormatException;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.PlainMimeEmptyPart;

public class WebSocketUtils {
	
	public static EventHandler<WebSocketRequest, Boolean> limitToPath(String path) {
		return new PathFilter(path, false, true);
	}
	
	public static WebSocketRequestParserFactory getParserFactory(Pipeline pipeline) {
		if (pipeline instanceof StandardizedMessagePipeline && ((StandardizedMessagePipeline<?, ?>) pipeline).getRequestParserFactory() instanceof WebSocketRequestParserFactory) {
			return (WebSocketRequestParserFactory) ((StandardizedMessagePipeline<?, ?>) pipeline).getRequestParserFactory();
		}
		else {
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	public static StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> getPipeline() {
		Pipeline pipeline = PipelineUtils.getPipeline();
		return pipeline == null || !(pipeline instanceof StandardizedMessagePipeline) || !(((StandardizedMessagePipeline<?, ?>) pipeline).getRequestParserFactory() instanceof WebSocketRequestParserFactory) 
			? null
			: (StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage>) pipeline;
	}
	
	public static List<StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage>> getWebsocketPipelines(String path) {
		Pipeline current = PipelineUtils.getPipeline();
		if (current != null) {
			return getWebsocketPipelines(current.getServer(), path);
		}
		else {
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	public static List<StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage>> getWebsocketPipelines(NIOServer server, String path) {
		List<StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage>> pipelines = new ArrayList<StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage>>();
		for (Pipeline pipeline : server.getPipelines()) {
			if (pipeline instanceof StandardizedMessagePipeline) {
				MessageParserFactory<?> requestParserFactory = ((StandardizedMessagePipeline<?, ?>) pipeline).getRequestParserFactory();
				if (requestParserFactory instanceof WebSocketRequestParserFactory) {
					if (path == null || path.equals(((WebSocketRequestParserFactory) requestParserFactory).getPath())) {
						pipelines.add((StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage>) pipeline);
					}
				}
			}
		}
		return pipelines;
	}
	
	public static Token getToken(StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline) {
		return ((WebSocketRequestParserFactory) pipeline.getRequestParserFactory()).getToken();
	}
	
	public static Device getDevice(StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline) {
		return ((WebSocketRequestParserFactory) pipeline.getRequestParserFactory()).getDevice();
	}
	
	public static WebSocketMessage newMessage(final byte [] bytes) {
		return new WebSocketMessage() {
			@Override
			public boolean isFinal() {
				return true;
			}
			@Override
			public long getSize() {
				return bytes.length;
			}
			@Override
			public OpCode getOpCode() {
				return OpCode.BINARY;
			}
			@Override
			public InputStream getData() {
				return new ByteArrayInputStream(bytes);
			}
		};
	}
	
	public static WebSocketMessage newMessage(final OpCode opCode, final boolean isFinal, final long size, final ReadableContainer<ByteBuffer> content) {
		return new WebSocketMessage() {
			@Override
			public boolean isFinal() {
				return isFinal;
			}
			@Override
			public long getSize() {
				return size;
			}
			@Override
			public OpCode getOpCode() {
				return opCode;
			}
			@Override
			public InputStream getData() {
				return IOUtils.toInputStream(content);
			}
		};
	}
	
	public static EventSubscription<HTTPResponse, HTTPRequest> allowWebsockets(NIOHTTPClientImpl client, MessageDataProvider dataProvider) {
		return client.getDispatcher().subscribe(HTTPResponse.class, new ClientWebSocketUpgradeHandler(dataProvider));
	}

	public static HTTPResponse upgrade(HTTPClient client, SSLContext context, String host, Integer port, String path, Token token, MessageDataProvider dataProvider, EventDispatcher dispatcher, List<String> protocols, WebAuthorizationType preemptiveAuthorization) throws UnsupportedEncodingException, IOException, FormatException, ParseException {
		if (port == null) {
			port = context == null ? 80 : 443;
		}
		// in all the examples i've seen they use 18 bytes, not sure if this is in the spec or not...
		byte [] bytes = new byte[18];
		new Random().nextBytes(bytes);
		// base64 encode them
		String value = new String(IOUtils.toBytes(TranscoderUtils.transcodeBytes(IOUtils.wrap(bytes, true), new Base64Encoder())), "ASCII");
		PlainMimeEmptyPart content = new PlainMimeEmptyPart(null, 
			new MimeHeader("Host", host + ":" + port),
			new MimeHeader("Upgrade", "websocket"),
			new MimeHeader("Connection", "Upgrade"),
			new MimeHeader("Sec-WebSocket-Key", value),
			new MimeHeader("Sec-WebSocket-Version", "13")
		);
		if (token != null) {
			content.setHeader(new SimpleAuthenticationHeader(token));
		}
		if (protocols != null && !protocols.isEmpty()) {
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < protocols.size(); i++) {
				if (i > 0) {
					builder.append(", ");
				}
				builder.append(protocols.get(i));
			}
			content.setHeader(new MimeHeader("Sec-WebSocket-Protocol", builder.toString()));
		}
		
		if (preemptiveAuthorization != null) {
			switch (preemptiveAuthorization) {
				case BASIC:
					String password = ((BasicPrincipal) token).getPassword();
					byte [] base64 = IOUtils.toBytes(TranscoderUtils.transcodeBytes(
						IOUtils.wrap((token.getName() + ":" + (password == null ? "" : password)).getBytes("UTF-8"), true), 
						new Base64Encoder())
					);
					content.setHeader(new MimeHeader(HTTPUtils.SERVER_AUTHENTICATE_RESPONSE, "Basic " + new String(base64, Charset.forName("ASCII"))));
				break;
				case BEARER:
					content.setHeader(new MimeHeader(HTTPUtils.SERVER_AUTHENTICATE_RESPONSE, "Bearer " + token.getName()));
				break;
			}
		}
		
		return client.execute(new DefaultHTTPRequest(
			"GET",
			path,
			content
		), token, context != null, true);
	}
	
	private boolean matches(TokenValidator tokenValidator, RoleHandler roleHandler, StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline, List<String> users, List<String> roles, List<String> devices, List<String> hosts, List<String> notUsers, List<String> notRoles, List<String> notDevices, List<String> notHosts) throws IOException {
		// we want to target users/roles
		if ((hosts != null && !hosts.isEmpty()) || (notHosts != null && !notHosts.isEmpty()) || (users != null && !users.isEmpty()) || (roles != null && !roles.isEmpty()) || (notUsers != null && !notUsers.isEmpty()) || (notRoles != null && !notRoles.isEmpty()) || (notDevices != null && !notDevices.isEmpty())) {
			Token token = WebSocketUtils.getToken(pipeline);
			if (tokenValidator != null && !tokenValidator.isValid(token)) {
				token = null;
			}
			if (roles != null && !roles.isEmpty()) {
				if (roleHandler == null) {
					throw new IllegalStateException("Role filtering requested but no role handler has been provided");
				}
				boolean hasRole = false;
				for (String role : roles) {
					hasRole = roleHandler.hasRole(token, role);
					if (hasRole) {
						break;
					}
				}
				if (!hasRole) {
					return false;
				}
			}
			if (notRoles != null && !notRoles.isEmpty()) {
				if (roleHandler == null) {
					throw new IllegalStateException("Role filtering requested but no role handler has been provided");
				}
				for (String role : notRoles) {
					if (roleHandler.hasRole(token, role)) {
						return false;
					}
				}
			}
			if (users != null && !users.isEmpty()) {
				if (token == null || !users.contains(token.getName())) {
					return false;
				}
			}
			if (notUsers != null && !notUsers.isEmpty()) {
				if (token != null && notUsers.contains(token.getName())) {
					return false;
				}
			}
			Device device = WebSocketUtils.getDevice(pipeline);
			if (devices != null && !devices.isEmpty()) {
				if (device == null || !devices.contains(device.getDeviceId())) {
					return false;
				}
			}
			if (notDevices != null && !notDevices.isEmpty()) {
				if (device != null && notDevices.contains(device.getDeviceId())) {
					return false;
				}
			}
			if ((hosts != null && !hosts.isEmpty()) || (notHosts != null && !notHosts.isEmpty())) {
				SocketAddress remoteSocketAddress = pipeline.getSourceContext().getSocketAddress();
				String host = remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getHostString() : null;
				int port = remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getPort() : 0;
				if (hosts != null && !hosts.isEmpty() && !hosts.contains(host)) {
					return false;
				}
				if (notHosts != null && !notHosts.isEmpty() && notHosts.contains(host)) {
					return false;
				}
				if (hosts != null && !hosts.isEmpty() && !hosts.contains(host + ":" + port)) {
					return false;
				}
				if (notHosts != null && !notHosts.isEmpty() && notHosts.contains(host + ":" + port)) {
					return false;
				}
			}
		}
		return true;
	}
	
	@WebResult(name = "clients")
	public List<Pipeline> broadcast(NIOServer server, String path, byte [] bytes, TokenValidator tokenValidator, RoleHandler roleHandler, List<String> users, List<String> roles, 
			List<String> devices, List<String> hosts, List<String> notUsers, List<String> notRoles, List<String> notDevices, List<String> notHosts) throws IOException {
		// we want to return a list of clients that we delivered the message to
		List<Pipeline> resultingPipelines = new ArrayList<Pipeline>();
		for (StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline : WebSocketUtils.getWebsocketPipelines((NIOServer) server, path)) {
			if (!matches(tokenValidator, roleHandler, pipeline, users, roles, devices, hosts, notUsers, notRoles, notDevices, notHosts)) {
				continue;
			}
			pipeline.getResponseQueue().add(
				WebSocketUtils.newMessage(OpCode.TEXT, true, bytes.length, IOUtils.wrap(bytes, true))	
			);
			resultingPipelines.add(pipeline);
		}
		return resultingPipelines;
	}
}
