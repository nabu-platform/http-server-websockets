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

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import org.bouncycastle.util.Arrays;

import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.http.api.server.MessageDataProvider;
import be.nabu.libs.nio.api.MessageParser;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.PushbackContainer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.io.buffers.bytes.ByteBufferFactory;
import be.nabu.utils.mime.impl.MimeHeader;

/**
 * Example of the format as explained here: https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API/Writing_WebSocket_servers 
 

0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-------+-+-------------+-------------------------------+
|F|R|R|R| opcode|M| Payload len |    Extended payload length    |
|I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
|N|V|V|V|       |S|             |   (if payload len==126/127)   |
| |1|2|3|       |K|             |                               |
+-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
|     Extended payload length continued, if payload len == 127  |
+ - - - - - - - - - - - - - - - +-------------------------------+
|                               |Masking-key, if MASK set to 1  |
+-------------------------------+-------------------------------+
| Masking-key (continued)       |          Payload Data         |
+-------------------------------- - - - - - - - - - - - - - - - +
:                     Payload Data continued ...                :
+ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
|                     Payload Data continued ...                |
+---------------------------------------------------------------+

 * The header contains:
 * - 1 bit fin
 * - 3 bit RSV
 * - 4 bit opcode
 * - 1 bit mask flag
 * - 7 bit min length
 * - (optional) 16 bit if length == 126
 * - (optional) 64 bit if length == 127
 * - (optional) 32 bit mask (if flag set)
 * 
 * This amounts to max 112 bits or 14 bytes
 */
public class WebSocketRequestParser implements MessageParser<WebSocketRequest> {

	private String path;
	private double version;
	
	private OpCode opCode;
	private Boolean isFinal, isMasked;
	private byte [] mask;
	private Byte payloadLength;
	private Long extendedPayloadLength;
	
	private boolean headerParsed;
	private boolean closed, done;
	
	/**
	 * Check the above explanation for the max header size
	 */
	private byte [] headerBytes = new byte[14];
	private ByteBuffer buffer = IOUtils.wrap(headerBytes, false);
	private MessageDataProvider messageDataProvider;
	private Resource resource;
	private WritableContainer<ByteBuffer> writable;
	private long dataRead;
	private ByteBuffer copyBuffer = ByteBufferFactory.getInstance().newInstance(4096, true);
	private List<String> protocols;
	private Token token;
	private Device device;

	public WebSocketRequestParser(MessageDataProvider messageDataProvider, List<String> protocols, String path, double version, Token token, Device device) {
		this.messageDataProvider = messageDataProvider;
		this.protocols = protocols;
		this.path = path;
		this.version = version;
		this.token = token;
		this.device = device;
	}
	
	@Override
	public void close() throws IOException {
		if (writable != null) {
			writable.close();
		}
		if (resource != null) {
			ResourceUtils.close(resource);
		}
	}

	@Override
	public void push(PushbackContainer<ByteBuffer> content) throws ParseException, IOException {
		if (!done && !closed) {
			while(!parseHeader()) {
				if (buffer.remainingSpace() == 0) {
					throw new ParseException("Could not parse header within allotted space", 0);
				}
				long read = content.read(buffer);
				if (read < 0) {
					closed = true;
					break;
				}
				// not enough data available to finish parsing the header
				else if (read == 0) {
					break;
				}
			}
			if (headerParsed) {
				if (writable == null) {
					if (resource == null) {
						resource = messageDataProvider.newResource("WEBSOCKET", path, version, new MimeHeader("Content-Length", "" + (extendedPayloadLength != null ? extendedPayloadLength : payloadLength)));
					}
					writable = ((WritableResource) resource).getWritable();
				}
				if (buffer.remainingData() > 0) {
					// if is theoretically possible that we read a byte or two that was not for this message if the payload length is _very_ small
					if (buffer.remainingData() > payloadLength) {
						// write whatever we need
						if (payloadLength > 0) {
							writable.write(ByteBufferFactory.getInstance().limit(buffer, (long) payloadLength, null));
						}
						// push back the rest
						content.pushback(buffer);
						// mark as done
						done = true;
						// close the writable
						writable.close();
					}
					// otherwise, write out the rest of the buffer to the backend
					else {
						dataRead += buffer.remainingData();
						writable.write(buffer);
						if (buffer.remainingData() > 0) {
							throw new IOException("Could not empty buffer into backend");
						}
					}
				}
				// only read the data if we are not done (that rare usecase with tiny payload)
				if (!done) {
					long read = 0;
					long contentLength = extendedPayloadLength != null ? extendedPayloadLength : payloadLength;
					// copy data from the source
					// we never copy too much because we know exactly how big it is, no need to push back "remainder"
					while (!closed && !done && dataRead < contentLength && (copyBuffer.remainingData() > 0 || (read = content.read(ByteBufferFactory.getInstance().limit(copyBuffer, null, Math.min(copyBuffer.remainingSpace(), contentLength - dataRead)))) > 0)) {
						dataRead += copyBuffer.remainingData();
						writable.write(copyBuffer);
						if (copyBuffer.remainingData() > 0) {
							throw new IOException("Could not flush copy buffer to backend");
						}
					}
					if (dataRead == contentLength) {
						done = true;
						writable.close();
					}
					if (read == -1) {
						closed = true;
						writable.close();
					}
				}
			}
		}
	}

	@Override
	public boolean isIdentified() {
		return headerParsed;
	}

	@Override
	public boolean isDone() {
		return done;
	}

	@Override
	public boolean isClosed() {
		return closed;
	}

	@Override
	public WebSocketRequest getMessage() {
		WebSocketRequest request = done 
			? new WebSocketRequestImpl(protocols, path, version, opCode, isMasked, mask, isFinal, extendedPayloadLength == null ? payloadLength : extendedPayloadLength, (ReadableResource) resource, token, device) 
			: null;
		return request;
	}
	
	private boolean parseHeader() throws ParseException, IOException {
		if (!headerParsed) {
			int headerSize = 2;
			if (isFinal == null) {
				isFinal = parseFinal();
			}
			if (isFinal != null && opCode == null) {
				opCode = parseOpCode();
			}
			if (opCode != null && isMasked == null) {
				isMasked = parseMasked();
			}
			if (isMasked != null && payloadLength == null) {
				payloadLength = parsePayloadLength();
			}
			if (payloadLength != null) {
				if ((payloadLength == 126 || payloadLength == 127) && extendedPayloadLength == null) {
					extendedPayloadLength = parseExtendedPayloadLength(payloadLength);
					headerSize += payloadLength == 126 ? 2 : 8;
				}
				if ((payloadLength != 127 && payloadLength != 126) || extendedPayloadLength != null) {
					if (isMasked && mask == null) {
						mask = parseMaskingKey();
						headerSize += 4;
					}
					if (!isMasked || mask != null) {
						// make sure whatever remains in the buffer is actual data
						buffer.skip(headerSize);
						headerParsed = true;
					}
				}
			}
		}
		return headerParsed;
	}

	private OpCode parseOpCode() throws ParseException {
		if (buffer.remainingData() >= 1) {
			OpCode opCode = OpCode.getOpCode((byte) (headerBytes[0] & 15));
			if (opCode == null) {
				throw new ParseException("Invalid opcode: " + (headerBytes[0] & 15), 0);
			}
			return opCode;
		}
		return null;
	}
	
	private Boolean parseMasked() {
		if (isMasked == null) {
			isMasked = buffer.remainingData() >= 2 ? getBit(headerBytes[1], 7) == 1 : null;
		}
		return isMasked;
	}
	
	private Boolean parseFinal() {
		return buffer.remainingData() >= 1 ? getBit(headerBytes[0], 7) == 1 : null;
	}

	private Byte parsePayloadLength() {
		return buffer.remainingData() >= 2 ? (byte) (headerBytes[1] & 127) : null;	
	}
	
	private Long parseExtendedPayloadLength(byte payloadLength) {
		// additional 2 bytes of length
		if ((payloadLength == 126 && this.buffer.remainingData() >= 4) || (payloadLength == 127 && this.buffer.remainingData() >= 10)) {
			java.nio.ByteBuffer buffer = null;
			buffer = java.nio.ByteBuffer.allocate(Long.BYTES);
			if (payloadLength == 126) {
				buffer.put(new byte[6]);
				buffer.put(Arrays.copyOfRange(headerBytes, 2, 4));
			}
			else {
				buffer.put(Arrays.copyOfRange(headerBytes, 2, 10));
			}
			buffer.flip();
			// make sure the leading bit is 0
			return buffer.getLong() & Long.MAX_VALUE;
		}
		return null;
	}
	
	private byte[] parseMaskingKey() {
		Byte payloadLength = parsePayloadLength();
		if (payloadLength != null) {
			int startPosition;
			if (payloadLength == 126) {
				startPosition = 4;
			}
			else if (payloadLength == 127) {
				startPosition = 10;
			}
			else {
				startPosition = 2;
			}
			if (buffer.remainingData() >= startPosition + 4) {
				return Arrays.copyOfRange(headerBytes, startPosition, startPosition + 4);
			}
		}
		return null;
	}
	
	public static byte getBit(byte value, int position) {
		return (byte) ((value >> position) & 1);
	}
	
}
