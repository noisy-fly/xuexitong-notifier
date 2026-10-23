# 手机端使用说明

Xuexitong Notifier 的采集器运行在服务器，GitHub Release 中的轻量 Android APK
负责订阅 ntfy，并把新消息显示为手机系统通知。APK 不会登录学习通，也不会保存
学习通账号、密码或 Cookie。

## 接收手机弹窗

1. 从私有 GitHub Release 下载并安装 `xuexitong-notifier-v0.2.0.apk`。
2. 在服务端运行 `xuexitong-notifier init`，启用 ntfy 并生成随机主题。
3. 在 APK 中填入服务端的 ntfy 地址、同一主题和可选访问令牌。
4. 允许通知权限，点击“保存并开始监听”。
5. 点击“测试弹窗”确认通知正常，并按手机系统提示允许后台运行。
6. 在服务端运行 `xuexitong-notifier check --live`，然后启动后台服务。

Android 8.0 及以上受支持。HTTPS 地址可直接使用；HTTP 仅允许本机、局域网 IP
或 `.local` 主机名。应用启用监听后会显示常驻低优先级通知，并在重启手机后恢复。
也可以使用官方 ntfy Android/iOS 客户端订阅同一主题。

ntfy 与 QQ 可以只启用一个，也可以同时启用。修改通道配置后，首次同步只会为新通道
建立静默基线，不会把历史消息集中推送到手机。

## 安全提示

- 随机主题等同于访问秘密，不要截图公开或提交到 Git。
- `.env`、`config.json` 和 `data/messages.db` 不包含在 Release 中，也不要手工上传。
- Release 不会修改已部署服务器的通道选择；当前生产环境仍可保持仅 QQ 模式。

