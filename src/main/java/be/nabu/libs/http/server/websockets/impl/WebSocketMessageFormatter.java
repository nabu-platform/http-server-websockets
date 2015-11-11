package be.nabu.libs.http.server.websockets.impl;

import java.io.IOException;
import java.util.Random;

import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.nio.api.MessageFormatter;
import be.nabu.utils.codec.TranscoderUtils;
import be.nabu.utils.codec.impl.XORMaskTranscoder;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.buffers.bytes.ByteBufferFactory;

public class WebSocketMessageFormatter implements MessageFormatter<WebSocketMessage> {

	private ByteBuffer header;
	private byte[] maskingKey;
	private boolean mask;
	
	public WebSocketMessageFormatter(boolean mask) {
		this.mask = mask;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public ReadableContainer<ByteBuffer> format(WebSocketMessage message) {
		try {
			header = formatHeader(message);
			header.close();
			if (message.getSize() == 0) {
				return header;
			}
			else {
				ReadableContainer<ByteBuffer> data = mask ? TranscoderUtils.transcodeBytes(IOUtils.wrap(message.getData()), new XORMaskTranscoder(maskingKey)) : IOUtils.wrap(message.getData());
				return IOUtils.chain(true, header, data);
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private ByteBuffer formatHeader(WebSocketMessage message) throws IOException {
		ByteBuffer buffer = ByteBufferFactory.getInstance().newInstance(14, false);
		byte [] single = new byte[1];
		
		// first byte: final & rsv & opcode
		byte firstByte = 0;
		if (message.isFinal()) {
			firstByte = (byte) (firstByte | 128);
		}
		firstByte |= message.getOpCode().getCode();
		single[0] = firstByte;
		buffer.write(single);
		
		// second byte: mask & length
		byte secondByte = 0;
		if (mask) {
			secondByte = (byte) (secondByte | 128);
		}
		byte [] extendedLength = null;
		java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.allocate(Long.BYTES);
		byteBuffer.putLong(message.getSize());
		byteBuffer.flip();
		// this sort of ignores the fact that the short is signed, but all it does (or should do) is use the 64 bit field for a value that might've also fit in the 16 bit one, nothing in the spec prevents that
		if (message.getSize() > Short.MAX_VALUE) {
			// we go for 64 bits
			secondByte |= 127;
			extendedLength = new byte[8];
			byteBuffer.get(extendedLength);
		}
		else if (message.getSize() > 125) {
			// we go for 16 bit
			secondByte |= 126;
			extendedLength = new byte[2];
			byteBuffer.position(6);
			byteBuffer.get(extendedLength);
		}
		else {
			secondByte += message.getSize();
		}
		single[0] = secondByte;
		buffer.write(single);
		
		// write any extended length header parts if required
		if (extendedLength != null) {
			buffer.write(extendedLength);
		}
		
		// write the masking key if required
		if (mask) {
			maskingKey = new byte[4];
			new Random().nextBytes(maskingKey);
			buffer.write(maskingKey);
		}
		return buffer;
	}

}
