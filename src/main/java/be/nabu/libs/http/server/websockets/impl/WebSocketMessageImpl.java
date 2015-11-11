package be.nabu.libs.http.server.websockets.impl;

import java.io.IOException;
import java.io.InputStream;

import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.utils.io.IOUtils;

public class WebSocketMessageImpl implements WebSocketMessage {

	private OpCode opCode;
	private long size;
	private boolean isFinal;
	private ReadableResource data;

	public WebSocketMessageImpl(OpCode opCode, boolean isFinal, long size, ReadableResource data) {
		this.opCode = opCode;
		this.isFinal = isFinal;
		this.size = size;
		this.data = data;
	}
	
	@Override
	public OpCode getOpCode() {
		return opCode;
	}

	@Override
	public boolean isFinal() {
		return isFinal;
	}

	@Override
	public long getSize() {
		return size;
	}

	@Override
	public InputStream getData() {
		try {
			return data == null || size == 0 ? null : IOUtils.toInputStream(data.getReadable());
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
