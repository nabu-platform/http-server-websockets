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
