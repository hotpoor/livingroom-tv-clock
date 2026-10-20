# 语音日程与局域网草稿服务

电视输入法负责语音转文字。应用把文字发给家庭局域网服务：程序计算日期时间，小模型提取事项，电视显示可编辑草稿。只有用户确认才调用本机 ScheduleStore 保存、注册开始及出发提醒。服务没有保存日程的接口，也不保存输入文字或访问日志。

## 服务运行

Python 3.10+，仅标准库。先通过 Ollama 安装 `qwen3.5:0.8b`。将 `backend/config.example.json` 复制为不提交的 `backend/config.json`，用 `python -c "import secrets; print(secrets.token_urlsafe(32))"` 生成随机口令，填写本机的局域网地址、端口及 Ollama 地址，然后在 backend 目录运行：

```sh
python -m unittest -v
python server.py
```

`bind` 默认只监听回环地址。供电视访问时改为该机器的局域网 IP；不要通过路由器端口转发暴露服务。`GET /health` 返回健康状态；`POST /parse` 需要 `Authorization: Bearer <token>`，请求 `{"text":"明天下午四点十分接孩子，下午四点出发"}`。响应含 `title/date/time/departureTime/mustFinishToday/issues/referenceTime/modelUsed/requiresConfirmation`。日期时间始终来自规则，模型不能修改。

Qwen 使用两组事项提取示例和 JSON Schema。模型超时、改写原文或删掉关键信息时回退到原文中去掉日期时间的事项文本；只允许可选地去掉开头的“去／要”。响应 `modelUsed` 表示是否采用模型标题。纯备忘、时间缺失或日期含糊时会返回空字段与提示，电视要求补齐日期时间后才能设置提醒。目前不支持无时间备忘或重复提醒。

## 电视配置

在被 Git 忽略的 `local-assets/schedule-service.json` 中填写：

```json
{"url":"http://YOUR_LAN_HOST:18765","token":"与服务一致的随机口令"}
```

构建时覆盖公开的空配置。应用允许局域网 HTTP，口令随私有 APK 打包，因此不要公开分享该 APK。公开源码、CI APK 不包含家庭服务地址或口令。不要把配置、服务日志、模型文件或家庭数据提交到仓库。网络不可用时可以重试或手动填写，未确认的草稿不会保存。

电视确认页显示模型事项、原始输入、规则日期时间和缺失提示。时间已经过去、日期无效、出发晚于计划、字段未填时不保存。返回修改、取消以及请求期间退出均不会创建提醒。当前日程存储使用上海时区，计算基准来自服务上的当前上海时间，务必保持服务主机与电视时钟准确。

## 支持与边界

- 今天、明天、后天、大后天；本周、下周、下下周；星期、礼拜；明确年月日和 ISO 日期。
- 上午／下午／晚上、24 小时、整点、半点、一刻／三刻，以及若干分钟／小时后。
- 「16点10分接孩子，16:00出发」与「出发时间16:00」。未说日期而说了具体时间，默认今天并在确认页显示；不会自动顺延到明天。
- 信息不足或检测到多个不同日期／时间时提示核对。下个月、周末、每天等不支持的规则不会被推测成具体日期。
- 只处理单条日程。规则不是通用自然语言理解器，任何解析结果都需要在电视核对；不拦截屏保首页的小爱语音。

## 验证

```powershell
python -m unittest discover -s backend -v
./verify.ps1
# 已安装同一开发签名的主 APK，且电视配置的服务可用时：
./checks/run-device-checks.ps1 -TvAddress YOUR_TV_IP:5555
```

设备检查临时安装同签名 instrumentation APK，测试草稿解析、明确保存、取消与缺失时间校验，结束后删除它创建的测试日程及测试 APK。会暂时占用电视前台界面，请在空闲时运行。它验证文字输入后的流程；真实麦克风录入仍需按遥控器语音键实测。
