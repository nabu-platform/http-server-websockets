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

import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.nio.api.MessageFormatter;
import be.nabu.libs.nio.api.MessageFormatterFactory;

public class WebSocketMessageFormatterFactory implements MessageFormatterFactory<WebSocketMessage> {

	private boolean shouldMask;

	public WebSocketMessageFormatterFactory(boolean shouldMask) {
		this.shouldMask = shouldMask;
	}
	
	@Override
	public MessageFormatter<WebSocketMessage> newMessageFormatter() {
		return new WebSocketMessageFormatter(shouldMask);
	}

}
