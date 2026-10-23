# Xuexitong Notifier

一个只读的学习通消息监听服务。它定期读取学习通通知中心和消息（IM），将首次出现的消息保存到 SQLite，并按配置推送到手机通知弹窗和/或 QQ 好友。

## 设计原则

- 不修改学习通数据，不自动签到、不刷课。
- 不以“未读”作为“新消息”的判断依据；稳定消息 ID 一旦记录，之后不会重复通知。
- 第一次成功同步默认只建立基线，不发送历史消息。
- 学习通密码、推送令牌和 QQ 机器人令牌只从环境变量读取。
- 手机弹窗使用 ntfy 协议；QQ 使用 OneBot 11 HTTP API。

## 功能

- 学习通通知中心（收件箱）分页采集
- 学习通环信 IM 群组与漫游消息分页采集
- SQLite 持久去重，未读旧消息不会重复弹窗
- ntfy 手机系统通知和 OneBot QQ 私聊可单开或双开
- 首次同步默认静默建立基线
- 单次运行、长期后台运行、失败退避与自动重登

## 本地安装

需要 Python 3.11 或更高版本：

```bash
python -m venv .venv
# Linux/macOS
. .venv/bin/activate
# Windows PowerShell
# .venv\Scripts\Activate.ps1
python -m pip install -e .
xuexitong-notifier init
```

`init` 会询问学习通账号/密码和要启用的通道，生成：

- `config.json`：非敏感配置；
- `.env`：账号、密码和令牌，已被 Git 忽略；Linux 下权限设为 `600`；
- `data/messages.db`：首次运行后创建，保存去重和投递状态。

## 手机通知弹窗

1. 在 Android/iOS 安装 ntfy 客户端。
2. 运行 `xuexitong-notifier init`，启用 ntfy。
3. 在手机客户端订阅命令输出的随机主题。
4. 给 ntfy 客户端开启系统通知、后台运行和电池优化白名单权限。

默认使用 `https://ntfy.sh`。随机主题等同于访问秘密，不要公开；也可以把 `server_url` 改为自托管 ntfy，并在 `.env` 设置访问令牌。

## QQ 通知

QQ 通道要求 OneBot 11 HTTP API。默认调用 `http://127.0.0.1:4300`，先按备注或昵称“腾飞辉煌”唯一解析好友；在 `config.json` 填写 `target_user_id` 可直接固定 QQ 号。OneBot 应只绑定回环地址并设置访问令牌。

## 运行与检查

```bash
# 只检查配置、数据库和通道选择
xuexitong-notifier check

# 只读连接学习通和 OneBot，不发送通知
xuexitong-notifier check --live

# 单轮运行；首次会建立静默基线
xuexitong-notifier run --once

# 前台持续运行，Ctrl+C 停止
xuexitong-notifier run
```

如确实需要把首次抓到的历史消息也推送，将 `notify_existing` 改为 `true`。通常不建议这样做。

## Linux 后台运行

将仓库放在 `/opt/xuexitong-notifier`，创建虚拟环境并安装后：

```bash
cp deploy/xuexitong-notifier.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now xuexitong-notifier
systemctl status xuexitong-notifier
journalctl -u xuexitong-notifier -f
```

也可使用 Docker：

```bash
docker compose up -d --build
docker compose logs -f notifier
```

`network_mode: host` 用于访问服务器回环地址上的 OneBot；只用 ntfy 时可改为普通 bridge 网络。

## 测试

```bash
PYTHONPATH=src python -m unittest discover -v
```

测试使用本地夹具和假 HTTP 会话，不需要真实学习通账号，也不会发送 QQ 或手机通知。

## 限制

- 学习通与环信接口并非公开稳定 API，平台改版后可能需要更新适配器。
- 当前 IM 采集覆盖账号已加入的群组漫游消息；环信未向该账号返回的会话无法采集。
- 学校统一认证、滑块验证码或二次验证出现时，需要人工处理，软件不会绕过风控。

