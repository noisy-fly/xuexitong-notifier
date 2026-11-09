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

Android 8.0 及以上可以安装 GitHub Release 中的轻量原生 APK：

1. 在服务端配置并启用 ntfy 通道。
2. 安装 APK，填入相同的服务器地址、主题和可选访问令牌。
3. 允许通知权限，点击“保存并开始监听”。
4. 用“测试弹窗”检查系统通知；为持续接收，请允许应用后台运行。

默认使用 `https://ntfy.sh`。随机主题等同于访问秘密，不要公开；也可以把 `server_url` 改为自托管 ntfy，并在 `.env` 设置访问令牌。

也可以继续使用官方 ntfy Android/iOS 客户端。完整说明见
[MOBILE_SETUP.md](MOBILE_SETUP.md)。

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
# 如果 venv 报 ensurepip 不可用，先安装与 python3 --version 匹配的包。
# Ubuntu Python 3.14 示例：apt install python3.14-venv
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

Android 客户端使用 JDK 17 与 Android SDK 35：

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## 限制

- 学习通与环信接口并非公开稳定 API，平台改版后可能需要更新适配器。
- 当前 IM 采集覆盖账号已加入的群组漫游消息；环信未向该账号返回的会话无法采集。
- 学校统一认证、滑块验证码或二次验证出现时，需要人工处理，软件不会绕过风控。


## Android 作业提醒改版（开发中）

Android 客户端正在改为直接登录学习通、读取课程作业、考试和课程任务并提醒截止时间。登录后先选择需要提醒的课程，手动与自动同步只读取已选课程；未选课程不会安排提醒。账号和密码使用 Android Keystore 加密并仅存于应用私有空间，所有同步均为只读。列表会显示上次更新时间；作业、考试或课程任务读取失败时标记不完整，未知截止时间不会安排定时弹窗。已完成和待批阅作业仍会显示，并与截止状态分别标注；已完成项目不会安排截止提醒。

- 作业专区：在首页向左滑动，或点击“进入作业专区”；每门课程有独立主栏，显示明确未完成的项目数量，点击进入课程详情。绿色表示已完成，红色表示未完成，黄色表示状态未知；未知项目可点击手动标记完成，再次点击标记未完成，标记仅保存在本机并参与提醒判断。平台返回明确状态时，以平台状态为准。已知已截止或已结束的项目不显示，截止时间未知的项目仍会标注并显示。向右滑动或点击返回按钮可返回。
- 课程选择：点击“选择课程”，勾选后保存；更改选择会取消已移除课程的本地提醒，并立即尝试同步已选课程。取消全部选择将停止自动更新和作业提醒。
- 手动获取：点击“手动获取作业”，同步完成后自动进入课程列表；若部分来源失败会显示同步问题，遇到验证时请先完成验证。
- 定时更新：启用开关并设置间隔（至少 15 分钟），Android 可能因为省电策略推迟任务。
- 弹窗提醒：启用后设置截止前的分钟数；每次同步会更新本地提醒。请允许通知、精确提醒及后台运行，并按手机厂商设置自启动。
- 若学习通要求验证，停止本轮剩余请求并暂停自动同步 6 小时。若界面出现“打开学习通验证”，可在应用内由本人完成验证后点击“继续同步”，应用会在同一次会话中从中断的课程继续；如果仍无法通过，请稍后重试。
- 课程活动若只提供“进行中”而不提供个人完成状态，会标注“完成状态未知”；有有效截止时间时仍可能收到提醒。课程任务包中未提供截止时间的项目显示未知，不会安排定时弹窗。
- 列表提供的“剩余时间”用于估算截止时间并标记为“约”。详情页缺少年份时按当前日期最近的年份推断；详情无法识别时显示未知，请以学习通为准。

**开发版限制**：真机已验证所选课程同步、待互评和一条课程考试的截止时间，但部分课程考试页面格式仍无法识别，部分历史作业及课程任务包缺少可识别的截止时间；系统实际到时弹窗、其他考试入口、不同学校账号和学习通验证稳定性仍需继续验证，请以学习通为准。原 Python 消息服务和以下旧版文档仍保留，描述的是旧版功能。
