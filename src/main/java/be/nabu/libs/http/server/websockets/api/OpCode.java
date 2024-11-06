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
