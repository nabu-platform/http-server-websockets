package be.nabu.libs.http.server.websockets.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.utils.codec.TranscoderUtils;
import be.nabu.utils.codec.impl.XORMaskTranscoder;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;

public class WebSocketRequestImpl implements WebSocketRequest {

	private OpCode opCode;
	private boolean isMasked;
	private boolean isFinal;
	private long size;
	private ReadableResource data;
	private String path;
	private double version;
	private List<String> protocols;
	private byte[] maskingKey;
	private Token token;
	private Device device;
	
	WebSocketRequestImpl(List<String> protocols, String path, double version, OpCode opCode, boolean isMasked, byte[] maskingKey, boolean isFinal, long size, ReadableResource data, Token token, Device device) {
		this.protocols = protocols;
		this.path = path;
		this.version = version;
		this.opCode = opCode;
		this.isMasked = isMasked;
		this.maskingKey = maskingKey;
		this.isFinal = isFinal;
		this.size = size;
		this.data = data;
		this.token = token;
		this.device = device;
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
			ReadableContainer<ByteBuffer> readable = data.getReadable();
			if (isMasked) {
				readable = TranscoderUtils.transcodeBytes(readable, new XORMaskTranscoder(maskingKey));
			}
			return IOUtils.toInputStream(readable, true);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getPath() {
		return path;
	}

	@Override
	public double getVersion() {
		return version;
	}

	@Override
	public List<String> getProtocols() {
		return protocols;
	}

	@Override
	public boolean isMasked() {
		return isMasked;
	}
	
	@Override
	public Token getToken() {
		return token;
	}

	@Override
	public Device getDevice() {
		return device;
	}
	
}
