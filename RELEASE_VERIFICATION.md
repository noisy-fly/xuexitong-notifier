# v0.2.0 发布验证

验证日期：2026-10-23

## Android

- 包名：`com.noisyfly.xuexitongnotifier`
- 版本：`2 / 0.2.0`
- 系统范围：Android 8.0（API 26）至 Android 15 目标版本（API 35）
- Release APK：zipalign 检查通过，APK Signature Scheme v2/v3 验证通过
- 签名证书 SHA-256：`7a5bf8f73f1331f2ee86ade517c36b55baa33118f80de641d59b29ca0d6aec80`
- 权限仅包含联网、通知、远程消息前台服务和开机恢复所需的 5 项权限
- Android 单元测试 13 项通过，Lint 无错误，Release 构建成功

## 服务端与产物

- Python 单元测试 25 项通过
- 服务器已启用 ntfy + QQ 双通道，systemd 服务 active，重启计数为 0
- ntfy 发布与 JSON 订阅接口均返回 HTTP 200，并匹配同一测试事件
- 源码包不含 `.env`、`config.json`、数据库或 Android 构建目录
- APK、源码 ZIP 和 wheel 的 SHA-256 校验均通过
- 已对解包产物扫描已知账号与密码，未发现匹配

## 待用户实机确认

构建电脑当前没有连接 ADB 设备。APK 的实体手机安装、通知授权、后台保活和真实系统
弹窗需要在下载后完成一次实机确认。

## GitHub Release

- `v0.2.0` 已作为正式版本发布：<https://github.com/noisy-fly/xuexitong-notifier/releases/tag/v0.2.0>
- 从 GitHub 重新下载了 APK、源码 ZIP、wheel 和校验文件
- 下载后的 3 个发布产物均通过 `SHA256SUMS.txt` 校验
- 下载后的 APK 再次通过 v2/v3 签名验证，签名证书与本地构建一致
