# 如月相随

## 后端外部配置

仓库中的 `voice-backend/src/main/resources/application.yml` 只是配置模板。生产服务器使用与 JAR 同目录的外部 `application.yml`，不要把服务器密钥写回仓库。

服务器目录建议如下：

```text
/www/wwwroot/voice-backend/
├── voice-backend-1.0.0.jar
├── application.yml
└── start.sh
```

首次部署时，将仓库模板复制到服务器 JAR 目录并填写真实配置：

```bash
cp voice-backend/src/main/resources/application.yml /www/wwwroot/voice-backend/application.yml
```

至少修改以下配置：

```yaml
spring:
	datasource:
		url: jdbc:mysql://localhost:3306/voice_assistant?useSSL=false&serverTimezone=Asia/Shanghai
		username: 服务器数据库用户名
		password: 服务器数据库密码

api:
	baidu:
		app-id: 百度应用ID
		api-key: 百度API Key
		secret-key: 百度Secret Key
	deepseek:
		api-key: DeepSeek API Key
		       model: deepseek-v4-pro

jwt:
	secret: 一段足够长的随机字符串

ai:
       memory:
	       max-characters: 1600
	history:
		enabled: true
	       max-messages: 20
```

将 `voice-backend/start.sh` 一并上传到 JAR 目录，然后启动：

```bash
cd /www/wwwroot/voice-backend
chmod +x start.sh
./start.sh
```

`start.sh` 会根据脚本自身位置查找 JAR 和 `application.yml`，因此即使从其他工作目录调用，也会读取 JAR 同目录的配置。外部配置通过 `spring.config.additional-location` 加载，优先级高于 JAR 内的模板。

修改服务器配置后重启后端才能生效：

```bash
cd /www/wwwroot/voice-backend
./start.sh
```

若由 systemd 管理，`ExecStart` 应指向该脚本：

```ini
WorkingDirectory=/www/wwwroot/voice-backend
ExecStart=/www/wwwroot/voice-backend/start.sh
```