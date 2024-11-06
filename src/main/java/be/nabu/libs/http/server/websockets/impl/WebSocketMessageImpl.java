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
