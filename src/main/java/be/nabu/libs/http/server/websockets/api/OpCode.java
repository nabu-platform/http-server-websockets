package be.nabu.libs.http.server.websockets.api;

public enum OpCode {
	CONTINUATION((byte) 0x0),
	TEXT((byte) 0x1),		// always encoded in UTF-8
	BINARY((byte) 0x2),
	CLOSE((byte) 0x8),
	PING((byte) 0x9),
	PONG((byte) 0xA)
	;
	
	private byte code;

	private OpCode(byte code) {
		this.code = code;
	}

	public byte getCode() {
		return code;
	}
	
	public static OpCode getOpCode(byte value) {
		for (OpCode opCode : values()) {
			if (opCode.getCode() == value) {
				return opCode;
			}
		}
		return null;
	}
}
