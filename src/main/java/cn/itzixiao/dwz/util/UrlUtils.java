package cn.itzixiao.dwz.util;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @Description: URL校验
 * @Author: Naccl
 * @Date: 2021-03-24
 */
public class UrlUtils {
	public static boolean checkURL(String url) {
		if (url == null || url.length() == 0 || url.length() > 2000) {
			return false;
		}
		String candidate = url.trim();
		if (!candidate.matches("(?i)^https?://.+")) {
			return false;
		}
		try {
			URI uri = new URI(candidate);
			return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
					&& uri.getHost() != null && uri.getUserInfo() == null;
		} catch (URISyntaxException e) {
			return false;
		}
	}
}
