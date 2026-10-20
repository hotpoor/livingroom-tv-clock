# 换电脑接手

仓库：https://github.com/hotpoor/livingroom-tv-clock

先拉取 main，阅读 README.md、docs/VOICE.md、docs/DATA.md 和 docs/DEVELOPMENT.md。

## 当前部署

- 家庭局域网主机（.98）运行网页、SQLite 库存及模型服务；换电脑不会中断服务。
- 活动服务目录为主机上的 `~/AI-Models/qwen35-08b-schedule/schedule-service`。私有配置为该目录的 config.json，库存路径由 inventory_db 配置指定，默认 backend/state/inventory.sqlite3。更新服务前先确认实际路径并备份数据库，不用示例数据覆盖。
- 独立 Ollama 监听主机 localhost:11435，已有 qwen3.5:0.8b 和 qwen3.5:4b。不要修改原有 11434 服务。
- 网页「4B 解析试验」只返回草稿，不保存库存或日程。电视语音解析仍使用 0.8B。
- 网页/电视 WebSocket 只传更新通知，首次连接、重连或更新通知后使用 HTTP 拉取；版本冲突在网页处理。
- 电视本机已安装 0.9.1，使用本地 Fliqlo 字体，事件列表支持三个 Tab、提醒定位和紧凑三卡布局。

## 私有文件与构建

本仓库不包含家庭数据、设备凭证、网页口令、数据库、模型文件、Fliqlo 原字体或开发签名。继续使用已运行的网页不需要复制这些文件。

需要覆盖安装电视 APK 时，须从旧电脑安全转移 dev.keystore，否则新签名不能覆盖安装；不要通过卸载绕过，以免丢失本机日程。另需按需转移 local-assets/schedule-service.json 和 local-assets/fonts/Fliqlo.otf。它们均被 Git 忽略，不要上传。

公共构建回退到 Oswald 600。Windows 构建执行 ./verify.ps1；Python 检查执行 python -m unittest discover -s backend。ADB 连接与 SSH 登录在新电脑单独配置，不向公开仓库提交凭证。

## 最新修正

网页 4B 以原句为事实来源，库存只作参考；支持现有库存陈述、后置品牌、两位年份（20xx，提示核对）及“7个月过期”。数量和日历保质期由程序校验计算，结果仍需核对包装。

已用实际输入回放，正确识别肯德基番茄沙司、3包、放入、指定冰箱及上部冷藏、2026-06-13至2027-01-13；没有写库存。26项 Python 测试通过。
