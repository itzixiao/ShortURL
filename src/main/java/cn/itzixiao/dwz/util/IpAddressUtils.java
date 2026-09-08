package cn.itzixiao.dwz.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @Description: ip记录
 * @Author: Naccl
 * @Date: 2020-08-18
 */
@Slf4j
@Component
public class IpAddressUtils {
	/**
	 * 在Nginx等代理之后获取用户真实IP地址
	 *
	 * @param request
	 * @return
	 */
	public static String getIpAddress(HttpServletRequest request) {
		String remoteAddress = request.getRemoteAddr();
		// Only trust forwarded headers from a local reverse proxy. Direct clients
		// must not be able to spoof the address used by the rate limiter.
		boolean trustedProxy = "127.0.0.1".equals(remoteAddress)
				|| "::1".equals(remoteAddress) || "0:0:0:0:0:0:0:1".equals(remoteAddress);
		String ip = trustedProxy ? request.getHeader("X-Real-IP") : remoteAddress;
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = trustedProxy ? request.getHeader("x-forwarded-for") : remoteAddress;
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = trustedProxy ? request.getHeader("Proxy-Client-IP") : remoteAddress;
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = trustedProxy ? request.getHeader("WL-Proxy-Client-IP") : remoteAddress;
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = trustedProxy ? request.getHeader("HTTP_CLIENT_IP") : remoteAddress;
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = trustedProxy ? request.getHeader("HTTP_X_FORWARDED_FOR") : remoteAddress;
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = remoteAddress;
			if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
				//根据网卡取本机配置的IP
				InetAddress inet = null;
				try {
					inet = InetAddress.getLocalHost();
				} catch (UnknownHostException e) {
					log.error("getIpAddress exception:", e);
				}
				if (inet != null) {
					ip = inet.getHostAddress();
				}
			}
		}
		String clientIp = StringUtils.substringBefore(ip, ",").trim();
		return clientIp.length() > 64 ? clientIp.substring(0, 64) : clientIp;
	}
}
