# 电视应用与遥控器入口实测

## 设备和 ADB

目标电视是 Android 9、32 位 ARM（armeabi-v7a），选 APK 前检查最低系统版本及架构。电视系统可见物理内存约 3GB，不能按64位设备选安装包。

切换有线和 Wi-Fi 可能改变 IP 或调试状态。先确认当前网络的 IP，再检查5555端口。连接超时意味着端口暂时不可达；connection refused 只说明连接被拒绝，不能直接认定授权失败。开发者模式开启不等于网络 ADB 正在监听，应关闭再开启 ADB／网络调试。此前允许过的电脑重新连接时可能不会再次弹授权。

## YouTube 电视版

实测安装 com.google.android.youtube.tv 7.28.300 的 ARMv7 拆分包，base 与架构 split 一并安装。安装前校验每个 APK 的签名、包名、架构和最低系统版本；不重签名或修改原 APK。不要把 YouTube 电视客户端与付费直播服务 YouTube TV 混淆。

小米桌面应用页曾出现“牌照方播控监管要求”提示；ADB 直接启动能进入 YouTube 欢迎页。随后时钟菜单中的标准 Android TV 启动 Intent 也能打开。没有捕获到该弹窗所属进程，因此不能据此断言具体拦截组件，或宣称系统限制已解除。未禁用或卸载任何系统组件。

“现在无法联网”和桌面入口提示是不同问题：应用能启动不代表已验证播放。代理和网络连接应独立检查。

## 时钟下方应用卡片

时钟模式按下键展开应用区，时钟适配到上方；上下左右选择，确认打开，返回或菜单键收起回到完整时钟。应用从 LEANBACK_LAUNCHER 与 LAUNCHER 查询，按包名去重，优先电视入口。网站快捷卡位于最前，YouTube 与 Clash 优先展示。列表可继续向下滚动。

## 电视浏览器与网站快捷入口

Chrome 没有专门适配电视遥控器的官方电视客户端。本次安装了 TV Bro 2.1.6 的官方 ARMv7 Gecko 包，以及 BrowseHere 官网提供的8.20.060电视版。浏览器兼容性应分别测试，不能仅凭可安装判断视频播放可用。

“一帆 TV”卡片使用 ACTION_VIEW 传入 https://www.yifan.tv/ ，不是把网站嵌入时钟。按用户最新选择，当前代码固定交给 TV Bro（com.phlox.tvwebbrowser），不再自动选择 BrowseHere。没有匹配入口时提示安装 TV Bro。

用户已确认网站可打开，当时 ADB 前台包核实为 TV Bro。BrowseHere 已安装，但尚未确认该网站在 BrowseHere 内的渲染及播放。不要把两者的验证结果混为一谈。

曾出现“系统未允许打开该应用”提示，最初实际是浏览器尚未安装触发 ActivityNotFoundException，并非已经确认系统拒绝；代码已对未找到浏览器提供单独提示。

自动审批曾拒绝自动打开网站，仅返回 blocked by policy、未给出详细原因；随后由用户用遥控器自行点击入口，并确认 TV Bro 可打开。

## Clash

Clash Verge 是桌面客户端。用户选择安装 Clash Meta for Android 2.11.35 ARMv7，包名 com.github.metacubex.clash.meta。订阅通过其 install-config 深链导入并选择；订阅 URL 不提交仓库。配置激活与 VPN 服务已启动是两个状态，需分别确认。不要凭时间先后就把 ADB 断连归因于代理。

## 本机视频模型目录

已确认的 MiniMax-H3 视频模型及配套组件共7个、约62.7GiB，原本就在 E 盘。现于 E:/AI-Models/Video 按 ComfyUI 模型类别集中提供，通过同盘 NTFS 硬链接兼容原路径，不另复制一份，不产生相应的 C 盘空间释放。所有已整理文件用文件身份一致性校验。

本机 ComfyUI extra_model_paths.yaml 已增加该外部目录，下次启动读取。没有停止视频生成服务。目录中的 MODEL-INVENTORY.json 是本机映射记录，不提交权重或私有绝对路径。此次只确认已识别的本机视频模型，不能据此宣称其他盘所有未知缓存都已扫描和迁移。
