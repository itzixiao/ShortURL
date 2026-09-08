# 短链接服务

基于 Spring Boot、MySQL 和 Redis 的短链接生成与 302 重定向服务。提交 `http/https` 长链接后生成 Base62 短码，访问短码时跳转到原始地址并异步累加访问量。

## 功能与安全边界

- `POST /generate`：生成短链接，默认按 IP 每 10 秒最多 1 次。
- `GET /{shortCode}`：查询短码并返回 302；不存在时回到首页。
- 仅接受 `http` 和 `https`，长度不超过 2000 字符，不接受 URL 用户信息（如 `user:password@host`）。服务不会抓取目标地址，但短链接仍可能被用于钓鱼，生产环境应增加内容审核、域名黑名单和身份认证。
- Redis 用于热点缓存和原子限流，MySQL 用于持久化。短链缓存键格式为 `short:url:{短码}`，限流键前缀为 `short:rate-limit:`。服务默认监听所有网卡的 8060 端口；如不需要公网直连，请通过 `SERVER_ADDRESS=127.0.0.1` 覆盖并仅使用 Nginx 代理。

## 环境要求

- JDK 8+、Maven 3.6+、MySQL 5.7/8.0、Redis 5+

## 本地运行

```bash
mysql -uroot -p -e "CREATE DATABASE dwz CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -uroot -p dwz < short_url.sql
mvn clean package -DskipTests
java -jar target/dwz-0.0.1.jar --server.host=https://investreport.online/shortUrl/
```

连接配置通过环境变量提供，避免将凭据提交到仓库：

```bash
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/dwz?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8
SPRING_DATASOURCE_USERNAME=dwz
SPRING_DATASOURCE_PASSWORD=change-me
SPRING_REDIS_HOST=127.0.0.1
SPRING_REDIS_PASSWORD=change-me
```

默认监听 `0.0.0.0:8060`，因此所有可达 IP 均可访问；可通过 `SERVER_ADDRESS` 限制绑定地址。默认短链前缀为 `https://investreport.online/shortUrl/`，`server.host` 必须以 `/` 结尾，并设置为用户实际访问的 HTTPS 地址。

## 生产部署方案

建议使用专用 Linux 用户运行，MySQL/Redis 仅监听内网或本机。若需要公网直接访问短链，可在防火墙开放 8060；否则只开放 80/443。将 jar 放在 `/opt/dwz`，使用 systemd：

```ini
# /etc/systemd/system/dwz.service
[Unit]
Description=DWZ short URL service
After=network.target mysql.service redis.service
[Service]
User=dwz
WorkingDirectory=/opt/dwz
Environment="SPRING_PROFILES_ACTIVE=dev"
EnvironmentFile=/etc/dwz/dwz.env
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /opt/dwz/dwz-0.0.1.jar --server.host=https://investreport.online/shortUrl/
Restart=on-failure
NoNewPrivileges=true
PrivateTmp=true
[Install]
WantedBy=multi-user.target
```

`/etc/dwz/dwz.env` 保存 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`、`SPRING_REDIS_HOST`、`SPRING_REDIS_PASSWORD` 等变量，并限制为 root/dwz 可读。执行 `systemctl daemon-reload && systemctl enable --now dwz`，用 `journalctl -u dwz -f` 查看日志。

## Nginx HTTPS 反向代理

证书可使用 certbot 申请。配置将公网 443 转发到本机 8060，并传递真实客户端地址：

```nginx
server {
    listen 80;
    server_name investreport.online;
    return 301 https://$host$request_uri;
}
server {
    listen 443 ssl http2;
    server_name investreport.online;
    ssl_certificate /etc/letsencrypt/live/investreport.online/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/investreport.online/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 16k;
    location = /shortUrl {
        return 301 /shortUrl/;
    }
    location /shortUrl/ {
        proxy_pass http://127.0.0.1:8060/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 10s;
    }
}
```

替换域名和证书路径后执行 `nginx -t && systemctl reload nginx`。若仅通过 Nginx 提供服务，安全组/防火墙应禁止外部访问 TCP 8060；若需要所有 IP 直接访问，则开放该端口并确保应用自身已做好限流和监控。

## API 示例

```bash
curl -X POST https://investreport.online/shortUrl/generate -H 'Content-Type: application/json' -d '{"longURL":"https://www.example.com/path?a=1"}'
```

成功响应：`{"code":200,"msg":"请求成功","data":"https://investreport.online/shortUrl/abc123"}`。

## 运维建议

定期备份 MySQL `url_map` 表，监控 Redis/MySQL、应用 5xx 和 302 命中率；升级依赖时执行 `mvn test` 并检查安全公告。
