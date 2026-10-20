# Livingroom TV Clock · 客厅翻页时钟

为 Android 电视制作的原生翻页时钟与家庭信息屏。无需 WebView、Gradle 或第三方 Android UI 依赖。已在 Android 9 小米电视上运行。

## 功能

- 黑底翻页时钟：24 小时制、公历年份与星期、农历和闰月。
- 天气：尝试系统定位，12 秒无有效结果则回退上海宝山；Open-Meteo 天气、缓存、离线和过期状态。
- 画面：五档亮度、夜间调暗、定时轻微移动；打开侧栏后重新排版，保持日期和天气的文字大小。
- 右侧冰箱清单：占三分之一屏幕；横向 Tab、新增/重命名/删除；按冷藏/冷冻分组，显示数量、日期与到期提醒。
- 左侧备忘日程：实时倒计时、出发时间与开始时间、当天完成期限、新增/编辑/完成/删除。
- 电视本机日程提醒：Android AlarmManager + 通知；应用在前台时展开日程。
- 小米屏保广播接入，另提供标准 DreamService。
- 遥控器语音输入测试页：在设置中打开，已在真机验证搜狗输入法将语音识别文字回填至前台输入框；不包含独立录音或语音转日程功能。

公开版内置空白示例，不包含家庭库存、儿童姓名、接送安排、电视地址、签名文件或现场截图。

## 遥控器

| 场景 | 操作 |
| --- | --- |
| 时钟首页 | 左键打开日程，右键打开冰箱，菜单键设置 |
| 冰箱侧栏 | 顶部左右切换 Tab，上下浏览食材，「语音」更新库存，「管理」修改冰箱 |
| 日程侧栏 | 上下选择，确认键管理事项，＋选择语音输入或手动新增 |
| 侧栏 | 返回键收起 |
| 时钟首页 | 返回键退出屏保 |

## Windows 构建

安装 JDK 17、Android SDK，设置 `JAVA_HOME` 和 `ANDROID_HOME`。SDK 需要 Android platform 28 和 build-tools 35.0.0（可通过 Android Studio SDK Manager 安装）。

```powershell
sdkmanager 'platforms;android-28' 'build-tools;35.0.0' 'platform-tools'
./build.ps1
```

输出 `build/livingroom-flipclock.apk`。脚本也兼容相邻 `../.tools` 中的本地工具布局。第一次构建生成本机开发签名 `dev.keystore`；后续覆盖安装需要使用同一签名。签名文件不要提交。

GitHub Actions 使用 Windows runner 自动构建 APK。其他设备可以提交代码或手动运行 Actions，无需在设备上配置完整编译环境。Actions 构建使用临时开发签名，不适合直接覆盖其他签名安装的版本。

## 安装与试用

在电视上启用 ADB 调试并授权电脑，用实际地址替换示例：

```powershell
$tvAddress = 'YOUR_TV_IP:5555'
adb connect $tvAddress
adb -s $tvAddress install -r -g ./build/livingroom-flipclock.apk
adb -s $tvAddress shell am start -n cn.livingroom.flipclock/.ClockActivity
```

`-g` 授予清单声明的定位权限；可在系统设置撤销。不依赖 root，不自动修改全局屏幕亮度。

## 家庭数据

`assets/inventory.json` 和 `assets/schedule.json` 是公开默认数据。个人数据放在被 Git 忽略的 `local-assets/` 同名文件中，构建时会覆盖默认值。APK 会包含这些本地数据，请自行控制 APK 的分享范围。

```powershell
New-Item -ItemType Directory -Force local-assets
Copy-Item assets/*.json local-assets/
```

字段示例见 [数据格式](docs/DATA.md)。之后升级不会覆盖本机编辑的库存或日程。目前库存支持确认后语音新增和扣减，以及局域网网页编辑和双向同步；尚未提供文件导入。修改种子 JSON 不会自动更新已保存的设备数据。

## 语音添加日程

菜单 →「语音添加日程」，或左侧日程 → ＋ →「语音 / 自然语言输入」。在输入框内按遥控器语音键，说完按返回收起键盘，再选择「解析并核对」。核对页可编辑事项、日期、计划时间、出发时间及当天必须完成标记；只有选择「确认保存」才会写入电视本机并注册提醒。取消或返回修改不会保存。

日期时间由 Python 规则计算，Qwen3.5-0.8B 仅提取事项。模型超时、不可用或返回非原文内容时保留规则提取的事项，仍需人工核对。接口只返回草稿，不保存日程。支持的中文时间、服务部署和私有连接配置见 [语音服务](docs/VOICE.md)。公开 APK 默认未配置服务，可使用手动填写。

## 语音更新冰箱

右侧冰箱顶部「语音」，或菜单 →「语音更新冰箱」。支持「放进了两盒酸奶」「吃掉三个牛肉烧卖」：先核对冰箱、食材批次、数量和分区，再预览库存变化，点击「确认更新」才保存。结构化库存摘要帮助识别简称和分区，数量变化由程序校验。库存不足、批次不明或核对版本已变化时会阻止更新；详见 [冰箱语音与批次](docs/FRIDGE-VOICE.md)。

## 结构

```text
src/cn/livingroom/flipclock/
  ClockActivity.java     入口、遥控器与设置
  ClockView.java         原生 Canvas 时钟与响应式排版
  Weather.java           定位、天气与缓存
  FridgePanel.java       冰箱 Tab、分区卡片与本机数据
  SchedulePanel.java     日程 UI、编辑与倒计时
  ScheduleStore.java     持久化、时区与定时注册
  ScheduleReceiver.java  本机通知与前台提醒
  VoiceScheduleActivity.java  语音输入、草稿核对与确认保存
  VoiceFridgeActivity.java    库存语音、冰箱/批次选择、变更确认
  InventoryStore.java        库存读取、稳定批次 ID、版本校验与增减
  SaverReceiver.java    小米屏保广播
  ClockDream.java       标准 Android 屏保入口
```

## 验证与贡献

运行 `./verify.ps1` 构建并验证时间转换、非法日期、倒计时边界和种子数据。修改 UI 后建议在电视上检查首页、左右侧栏、遥控器焦点、返回行为及大字号布局。详见 [贡献说明](CONTRIBUTING.md) 和 [开发记录](docs/DEVELOPMENT.md)。

## 当前限制

- Android 7.0+；真机验证环境为 Android 9，小米不同固件的自动屏保入口仍需测试。
- 小米固件关闭标准 Dream 支持时使用自有广播，不保证其他品牌自动触发。
- 后台通知呈现、休眠与关机提醒取决于电视系统；当前没有手机推送或云端提醒。
- 小爱语音唤起尚未实现；没有自动读取冰箱内物品的能力。
- 天气坐标发送给 Open-Meteo，反向地理编码使用系统 Geocoder；默认天气地点是上海宝山。

视觉参考翻页钟这一通用形式，未包含 Fliqlo 的代码、图片或品牌素材。天气数据来自 [Open-Meteo](https://open-meteo.com/)。


### 局域网库存编辑与同步

同一服务提供网页编辑冰箱、食材批次、数量、区域和保质期，使用独立网页口令登录。网页口令哈希配置在私有 `config.json` 的 `web_password_hash` 中，库存保存在服务端 SQLite，数据库与私有配置不提交仓库。日程暂不在网页编辑范围内。

网页和电视通过已认证的 `/api/events` WebSocket 接收库存更新通知；通知不携带库存。每次连接成功（包括重连）或收到更新通知，客户端通过 HTTP 请求拉取最新版本。网页使用会话 Cookie，电视使用设备凭证。电视本地确认修改仍通过 HTTP 提交，版本冲突会保留两边内容，等待网页核对，不直接覆盖。

WebSocket 心跳每 10 秒发送，连接断开后约 3 秒重试。电视进程运行时另有每 5 分钟 HTTP 补同步，系统后台任务按 Android 调度执行；电视关机或进程停止时不会保持连接，重新打开应用后补拉。网页不再每 5 秒轮询库存。


### 时钟字体

默认公开构建内置 Oswald 600，并附带 SIL OFL 授权。可将自行取得且有权使用的字体放在被 Git 忽略的 `local-assets/fonts/Fliqlo.otf`，本地构建会优先加载该字体。Fliqlo 原字体及包含它的本地 APK 不在本仓库分发。数字按实际字形边界居中，并为最宽数字组合保留空间。

网页部署还需配置 `web_password_hash`：在 backend 目录执行 `python -c "from inventory_db import password_hash; import getpass; print(password_hash(getpass.getpass()))"`，将输出写入私有配置。设备 `token` 与网页口令用途不同，不应互相替代。首次连接由电视上传已有库存初始化服务端。
