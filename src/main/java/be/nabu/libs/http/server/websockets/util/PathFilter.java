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

package be.nabu.libs.http.server.websockets.util;

import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;

public class PathFilter implements EventHandler<WebSocketRequest, Boolean> {

	private String path;
	private boolean isRegex, whitelist;

	public PathFilter(String path) {
		this(path, false, true);
	}
	
	public PathFilter(String path, boolean isRegex, boolean whitelist) {
		this.path = path;
		this.isRegex = isRegex;
		this.whitelist = whitelist;
		// make sure it is absolute
		if (!isRegex && !this.path.startsWith("/")) {
			this.path = "/" + this.path;
		}
	}
	
	@Override
	public Boolean handle(WebSocketRequest request) {
		if (whitelist) {
			return isRegex
				? !request.getPath().matches(path)
				: !request.getPath().startsWith(path);
		}
		else {
			return isRegex
				? request.getPath().matches(path)
				: request.getPath().startsWith(path);
		}
	}

}
