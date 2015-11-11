package be.nabu.libs.http.server.websockets.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;

public class WebSocketRequestChain implements WebSocketRequest {

	private WebSocketRequest[] requests;
	private String path;
	private Double version;
	private OpCode opCode;
	private boolean isMasked;
	private long totalSize;
	private List<String> protocols;

	public WebSocketRequestChain(WebSocketRequest...requests) {
		if (requests == null || requests.length == 0) {
			throw new IllegalArgumentException("No requests found");
		}
		// only unset if at least one message is not masked
		isMasked = true;
		for (int i = 0; i < requests.length; i++) {
			isMasked &= requests[i].isMasked();
			totalSize += requests[i].getSize();
			if (path == null) {
				path = requests[i].getPath();
			}
			else if (!path.equals(requests[i].getPath())) {
				throw new IllegalArgumentException("Can not chain together requests with different paths");
			}
			if (version == null) {
				version = requests[i].getVersion();
			}
			else if (!version.equals(requests[i].getVersion())) {
				throw new IllegalArgumentException("Can not chain together requests with different versions");
			}
			if (protocols == null) {
				protocols = requests[i].getProtocols();
			}
			else if (!protocols.equals(requests[i].getProtocols())) {
				throw new IllegalArgumentException("Can not chain together requests with different protocols");
			}
			if (i == 0) {
				opCode = requests[i].getOpCode();
			}
			else if (!OpCode.CONTINUATION.equals(requests[i].getOpCode())) {
				throw new IllegalArgumentException("Invalid opcodes in chain, expecting continue");
			}
			if (i == requests.length - 1) {
				if (!requests[i].isFinal()) {
					throw new IllegalArgumentException("The last message in the chain should be final");
				}
			}
			else if (requests[i].isFinal()) {
				throw new IllegalArgumentException("The messages in the chain (except the last) should not be final");
			}
		}
		this.requests = requests;
	}
	
	@Override
	public String getPath() {
		return requests[0].getPath();
	}

	@Override
	public double getVersion() {
		return version;
	}

	@Override
	public OpCode getOpCode() {
		return opCode;
	}

	@Override
	public boolean isFinal() {
		return true;
	}

	@Override
	public boolean isMasked() {
		return isMasked;
	}

	@Override
	public long getSize() {
		return totalSize;
	}

	@SuppressWarnings("unchecked")
	@Override
	public InputStream getData() {
		List<ReadableContainer<ByteBuffer>> datas = new ArrayList<ReadableContainer<ByteBuffer>>();
		for (WebSocketRequest request : requests) {
			datas.add(IOUtils.wrap(request.getData()));
		}
		return IOUtils.toInputStream(IOUtils.chain(true, datas.toArray(new ReadableContainer[0])));
	}

	@Override
	public List<String> getProtocols() {
		return protocols;
	}

}
