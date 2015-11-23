package be.nabu.libs.http.server.websockets;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.http.server.websockets.impl.WebSocketRequestParserFactory;
import be.nabu.libs.http.server.websockets.util.PathFilter;
import be.nabu.libs.nio.PipelineUtils;
import be.nabu.libs.nio.api.MessageParserFactory;
import be.nabu.libs.nio.api.NIOServer;
import be.nabu.libs.nio.api.Pipeline;
import be.nabu.libs.nio.api.StandardizedMessagePipeline;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;

public class WebSocketUtils {
	
	public static EventHandler<WebSocketRequest, Boolean> limitToPath(String path) {
		return new PathFilter(path, false, true);
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
	
}
