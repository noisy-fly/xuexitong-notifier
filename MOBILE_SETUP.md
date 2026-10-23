# 手机端使用说明

Xuexitong Notifier 本体运行在服务器或电脑上，不是 Android APK。GitHub Release
中的 ZIP 可以用手机下载、备份或传到服务器部署；真正的手机系统弹窗由 ntfy
客户端接收。

## 接收手机弹窗

1. 在 Android 或 iOS 安装 ntfy 客户端。
2. 在服务端运行 `xuexitong-notifier init`，选择启用 ntfy，生成一个足够长的随机主题。
3. 在 ntfy 手机客户端订阅同一个主题。
4. 给 ntfy 开启系统通知、后台运行和电池优化白名单权限。
5. 运行 `xuexitong-notifier check --live`，然后启动后台服务。

ntfy 与 QQ 可以只启用一个，也可以同时启用。修改通道配置后，首次同步只会为新通道
建立静默基线，不会把历史消息集中推送到手机。

## 安全提示

- 随机主题等同于访问秘密，不要截图公开或提交到 Git。
- `.env`、`config.json` 和 `data/messages.db` 不包含在 Release 中，也不要手工上传。
- Release 不会修改已部署服务器的通道选择；当前生产环境仍可保持仅 QQ 模式。

