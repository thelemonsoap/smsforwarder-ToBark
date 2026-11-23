# 短信转发助手 Bark 增强版

基于 [SmsForwarder.cn / smsforwarder](https://github.com/Lucian-Wang/smsforwarder) 修改的非官方 Android 开源增强分支，修改基础为 v2.9.8，由 [thelemonsoap](https://github.com/thelemonsoap) 维护。原项目曾使用 `huahaotech/smsforwarder.cn` 地址，GitHub 当前 Fork 关联以上方上游仓库为准。

**当前版本：2.9.8-bark.26，内部版本号 versionCode：26。**

[下载 APK](https://github.com/thelemonsoap/smsforwarder-ToBark/releases) · [构建与发布说明](RELEASE.md) · [完整修改记录](BARK_README.txt) · [许可证](LICENSE.txt)

## 功能

- 支持 Bark、自定义 Webhook，以及原项目的企业微信、钉钉、飞书等转发渠道。
- 支持短信、来电、未接／拒接、号码补充、应用通知、电量及 LTE／5G 切换提醒。
- 13 类推送可分别设置模板；应用通知还支持按应用单独覆盖模板，自由安排应用名、标题和正文的位置。
- 模板变量菜单默认收起，展开后可查看该类模板支持的全部变量，并点击插入。
- 支持联系人姓名匹配、关键词和内容过滤、验证码提取、配置导入导出。
- 电量按 10% 阶段提醒，未充电状态显示为“使用”。移动信号使用 5 分钟波动过滤，通话期间及结束后 30 秒保持信号提醒静默，连接 Wi-Fi 时暂停 LTE／5G 检测。
- 纯黑界面、设置二级菜单、底部 dock 滑块、统一圆角；真实背景模糊限顶栏与 dock 内。
- 半透明黑色桌面小组件，按发送成功与服务状态事件更新，无定时刷新；磨砂采用静态纹理，不包含壁纸实时模糊。

## 使用

1. 从本仓库 Releases 下载 APK，按 Android 提示安装。
2. 在 App 中添加转发通道，填写自己的 Bark 地址或其他渠道参数并测试。
3. 按需要授予短信、电话、通讯录和通知访问权限，并配置后台运行设置。
4. 启用需要的提醒。正文模板在“设置 → 推送模板”中编辑。

本分支沿用原 Bark 永久签名和 `com.lanbing.smsforwarder.bark` 包名。后台运行、来电通知和桌面显示效果依手机系统而异。

## 构建与版本

使用 JDK 17、仓库自带的 Gradle Wrapper 8.4，以及 Android SDK 34。版本集中在根目录 `gradle.properties`：

```properties
barkVersionName=2.9.8
barkVersionCode=26
```

以上配置生成 2.9.8-bark.26。后续 bark.27 将编号改为 27，再同步修改版本说明；这个编号同时用于 Bark 后缀和内部版本号。

本机恢复自己保存的原签名备份后运行：

```bash
bash ./gradlew :app:testDebugUnitTest :app:assembleBark
```

正式 APK 位于 `app/build/outputs/apk/bark/`。GitHub Actions 通过仓库 Secrets 恢复同一把原签名，操作见 [RELEASE.md](RELEASE.md)。当前流程为手动运行正式构建，再手动发布 GitHub Release。

## 开源来源与许可证

本仓库是个人维护的增强分支，非上游官方发行版。保留原项目著作权、MIT 许可及第三方组件的许可说明，详见 [LICENSE.txt](LICENSE.txt) 和 `app/src/main/assets/` 中的许可文件。

签名私钥、签名密码、个人 Bark 设备 Key 和个人配置保存在本机或 GitHub Secrets 中。构建脚本中的证书 SHA-256 是公开指纹，用于核对覆盖升级所需的同一签名。

历史构建与验证记录见 [BARK_README.txt](BARK_README.txt)。当前构建和发布步骤以本页及 [RELEASE.md](RELEASE.md) 为准。

<details>
<summary>历史说明和上游使用文档（旧版本构建命令仅供追溯）</summary>

## Bark定制版 bark.14：Liquid Glass风格界面

Liquid Glass风格界面版（2.9.8-bark.14，versionCode 14）
沿用 bark.2 起永久签名，可直接覆盖 bark.13 及 bark.2～12，保留原有配置。
本版为安卓原生Compose中的Liquid Glass风格设计：顶部工具栏、底部悬浮胶囊导航使用半透明材质、柔和高光、细边缘和阴影；内容卡片采用稳定、易读的标准表面。首页、关键词、通道、设置、日志及电话/通讯录/信号/应用通知卡片采用统一配色、间距和圆角，弹窗、输入框及状态提示同时适配。
设置→外观与显示：跟随系统／浅色／深色，及“减少透明效果”。默认跟随系统；减少透明效果后使用实体背景，适合偏好更高可读性时开启。外观偏好仅保存在本机，不改变原业务配置导入导出，覆盖升级保留；原业务配置导出不包含本机外观偏好。
设计原则参考Apple官方Liquid Glass材料与采用指南，但本版运行在安卓，不调用苹果原生Glass框架；采用轻量渐变、透明度与高光，不模拟完整的实时背景折射、镜头光学或持续实时模糊。无常驻光效、传感器动画或后台渲染任务；导航颜色仅在选择或主题变化时短暂过渡，遵守Compose的系统动画时长设置。文本不会被模糊；深浅色标题有对比度检查。
保留短信/Bark、电话/未接/号码补充、通讯录姓名、应用通知、每10%连续电量、充电提醒、通话结束后30秒信号静默、5分钟波动过滤、Wi-Fi连接期间停止LTE／5G检测及此前省电优化。所有后台业务核心文件与 bark.13 保持逐字节一致；没有加入热点自动开关功能。权限清单不变。
验证：原194项回归测试及4项Android界面渲染/交互测试，覆盖五页浅深色导航、外观切换、原业务配置保留、1.5倍字体、减少透明效果与标题对比度。预览由Robolectric Native Graphics运行真实Compose代码生成，空配置示例状态，不是三星实机截图，也不是设计稿。生成的全部渲染图片附在validation/glass-previews。
重建：恢复独立签名备份后 ./gradlew :app:testDebugUnitTest :app:lintBark :app:assembleBark -PVERSION_NAME=2.9.8 -PVERSION_CODE=14。界面测试增加的Robolectric/Compose测试依赖仅用于测试，不进入Bark发布APK；测试需下载相应Android测试运行时并支持原生图形渲染。
附完整源码、bark.13→bark.14增量补丁、构建与验证记录；私钥和密码文件继续独立保管、不包含在源码包中。原项目著作权与MIT许可全文保留，关于页注明本次界面修改。未做真实三星手机触摸/折叠屏/续航或iPhone Bark送达实测。
参考：https://developer.apple.com/design/human-interface-guidelines/materials
参考：https://developer.apple.com/documentation/TechnologyOverviews/adopting-liquid-glass


## Bark 定制版 bark.13：Wi-Fi暂停移动信号检测

Wi-Fi暂停移动信号检测版（2.9.8-bark.13，versionCode 13）
沿用 bark.2 起永久签名，支持直接覆盖 bark.12 及 bark.2～11，保留配置。无需新增开关：连接Wi-Fi后自动暂停。
已连接任意可见Wi-Fi网络时，注销信号提醒模块的数据连接、网络显示和数据SIM变化监听，并取消未成熟的5分钟信号观察。暂停LTE／5G信号提醒及尚未开始发送的排队/重试消息；Wi-Fi即使没有互联网、待网页登录或处于VPN下仍按连接处理。仅打开Wi-Fi开关但未连接时不暂停。已开始HTTP请求无法撤回。
断开最后一个Wi-Fi后重新注册移动信号监听：丢弃Wi-Fi前的基准，默认静默观察5分钟确认当前LTE／5G，不补发Wi-Fi期间网络变化；之后新变化继续使用原固定5分钟窗口。若同时在通话静默期，则按原通话静默规则静默更新基准，不补发。首次仅蜂窝启动、未经历Wi-Fi时仍遵守原“开启时提醒当前网络”选项。
使用系统被动网络回调和一次启动快照，不扫描SSID、不新增轮询、不获取位置；VPN默认路由不影响按Wi-Fi传输判断，跟踪全部匹配网络，避免多Wi-Fi切换时提前恢复。每次连接/断开更新代次，短暂连上又断开的Wi-Fi同样使旧信号发送失效。发送前另行核查Wi-Fi状态。网络回调监听注册失败时保守暂停信号检测，设置页显示原因，可点击“重新检查信号”。
Wi-Fi暂停只影响信号提醒。短信、电话/未接/号码补充、通讯录姓名、应用通知、每10%连续电量及充电提醒保持原行为；电话状态监听继续用于电话提醒和通话结束后30秒的信号静默。保留 bark.11 省电和 bark.12 通讯录功能。
本机保存一个“Wi-Fi后需重建信号基准”运行标记，用于进程重启后继续避免补发；此标记不导出、不含SSID/号码/网络地址。设置页及关于页注明Wi-Fi自动暂停，原项目版权和MIT许可全文保留。权限清单不变。
重建：恢复独立签名备份后运行 ./gradlew :app:assembleBark -PVERSION_NAME=2.9.8 -PVERSION_CODE=13。
附完整源码、bark.12→bark.13补丁及验证记录；签名私钥继续独立保管、不在源码包中。未真实三星Wi-Fi/蜂窝/VPN/双卡切换和iPhone Bark实测；依赖系统交付网络状态，进程未运行时无法追溯期间连接变化。


## Bark 定制版 bark.12：通讯录姓名

通讯录姓名版（2.9.8-bark.12，versionCode 12）
沿用 bark.2 起永久签名，支持覆盖 bark.11 及 bark.2～10，保留配置。
使用：设置 → 通讯录姓名 → 开启“短信和电话优先显示联系人”并授权读取通讯录。新开关默认关闭，不自动请求权限。拒绝或撤销通讯录权限时短信和电话仍正常转发，使用号码或未知号码；权限再次允许后自动生效。导入配置包含此开关，但系统授权须在本机完成；旧配置缺失此项时关闭。
匹配成功只显示联系人姓名，不再附带对方号码；适用于短信、来电、未接/拒接及号码稍后补充提醒。本机SIM标识/本机号码仍按原设置处理。短信规则、发件人黑白名单、去重和失败队列仍使用原号码，不改成联系人姓名。
默认Bark短信正文现在遵守“显示发件人”的配置，显示“来自: 姓名/号码”，短信原文完整保留。通用Webhook的原content字段仍保留；sender字段显示姓名优先，Bark读取的body包含默认发件人信息。已有自定义模板由用户决定布局，使用{sender}显示姓名优先，无此占位符的模板不额外插入发件人。默认隐藏发件人时姓名和对方号码均隐藏；自定义模板显式{sender}仍遵守原模板语义。
号码仅做格式和明确国家码规范化：支持+86/0086与中国大陆手机号、+1与北美10位号码，不按任意后几位匹配。候选号码经核对再显示，多个不同姓名命中同一号码时回退号码；运营商提供姓名、网页黄页等网络查号不在本功能范围。受Android通讯录匹配规则与设备地区设置影响，个别号码可能仍无法匹配。
查询发生在后台发送线程，仅按当前号码向本机通讯录查询姓名，不扫描或上传整本通讯录，不建立姓名持久化缓存或轮询。查询配有取消超时，查询失败回退原号码，不影响转发。已完成发送的通知不会被事后改写；新短信/来电和短信失败重试会按当时通讯录重新匹配。对方隐藏号码时无法凭空识别姓名。
未改变5分钟固定信号波动过滤、通话结束后30秒信号静默、充放电每10%电量提醒、通知过滤去重及 bark.11 省电优化。关于页保留原开源项目、原著作权人和MIT许可全文，并补充本次改动。新增权限只有可选READ_CONTACTS。
重建：恢复独立签名备份后运行 ./gradlew :app:assembleBark -PVERSION_NAME=2.9.8 -PVERSION_CODE=12。
附完整源码、bark.11→bark.12补丁及验证记录。签名私钥继续独立保管、不在源码包中。未真实三星通讯录、权限授权/撤销、电话和iPhone Bark到达实测。


## Bark 定制版 bark.11：省电优化

省电优化版（2.9.8-bark.11，versionCode 11）
沿用 bark.2 起永久签名，支持覆盖 bark.10 及 bark.2～9，保留配置。
短信失败重试改为按最早到期消息安排一次任务；无失败队列时不再每10秒检查，无网络时等待网络恢复回调。保留原30/60/120/300秒重试节点（相对原消息时间），过期节点至少间隔10秒，网络恢复可立即重试。失败队列仅在进程首次使用时读盘；发送结果落盘前保留在途消息，避免并发网络回调重复启动同一批重试。崩溃发生在远端已接收但本机尚未保存结果之间时仍可能重发，不承诺严格只发送一次。
日志改为追加写入，累计超出200+50条才整理；界面始终最多显示最近200条并保持最新在前。旧版日志首次使用时自动迁移，格式标记与数据一起原子替换，不影响旧日志内容。
电量广播仅在需要提醒时获取SIM设备信息；连续电量状态未改变不重复写偏好设置。不相关应用通知被移除时不再保存未变化的去重快照。日志页后台/熄屏时暂停2秒自动刷新，回到前台恢复。
保留5分钟固定信号波动窗口、通话至结束后30秒信号静默、短信即时转发、电话号码补全、应用通知筛选去重、充放电每10%及原电量提醒模板。关于页增加本次修改说明，原项目MIT许可全文保留；权限不增加。
本版应用层优化适用于三星猎户座及其他Android芯片；不修改CPU调频、基带、内核，不承诺测得省电比例。为保持提醒及时，请保持通知/电话权限，不将本应用放入三星深度休眠名单。未做真实三星/Exynos耗电与后台送达实测。
重建：恢复独立签名备份后运行 ./gradlew :app:assembleBark -PVERSION_NAME=2.9.8 -PVERSION_CODE=11。
附完整源码、bark.10→bark.11增量补丁和本版构建/验证记录；签名私钥继续独立备份，不放入源码包。


# 短信转发助手 (SmsForwarder.cn)

> 本交付是 2.9.8-bark.3 用户定制版本。应用通知转发、电话提醒、升级迁移和固定签名构建说明见 [BARK_README.txt](BARK_README.txt)。

<div align="center">

![Version](https://img.shields.io/badge/version-2.9.6-blue.svg)
![Android](https://img.shields.io/badge/Android-5.0%2B-brightgreen.svg)
![License](https://img.shields.io/badge/license-MIT-orange.svg)
[![GitHub Stars](https://img.shields.io/github/stars/jinghong-me/smsforwarder.cn?style=social)](https://github.com/jinghong-me/smsforwarder.cn/stargazers)

轻量、稳定、开源的 Android 短信转发应用。支持企业微信、钉钉、飞书和自定义 Webhook 等多种转发渠道，支持关键词过滤、验证码提取、自定义消息模板等功能。

[快速开始](#快速开始) • [功能特性](#功能特性) • [下载安装](#下载安装) • [使用指南](#使用指南)

</div>

---

## ✨ 功能特性

### 🎯 核心功能
- **快速创建引导** - 三步式向导，30 秒完成配置（选通道→填 Webhook→设关键词），新手零门槛
- **多通道支持** - 企业微信、钉钉、飞书、通用 Webhook 四种转发通道
- **关键词匹配** - 支持包含、精确、正则表达式、排除四种匹配模式
- **多关键词组合** - AND/OR 逻辑组合，灵活控制匹配条件
- **发送者过滤** - 全局白名单/黑名单，支持通配符和正则匹配（自动兼容 +86 等国家代码前缀）
- **内容过滤** - 全局内容白名单/黑名单，支持通配符和正则匹配
- **验证码提取** - 自动识别并提取短信验证码
- **自定义消息模板** - 全局默认模板 + 每个通道独立模板，支持 7 种占位符
- **本机号码识别** - 双卡设备支持识别接收短信号码，转发时显示本机号码
- **自定义 SIM 号码** - 无法自动获取时支持手动输入本机号码
- **SIM 卡信息预览** - 前端显示 SIM 卡号码状态，提前预知能否获取
- **消息去重** - 5秒内相同内容自动去重，避免重复转发
- **智能重试** - 失败消息最多重试4次（30秒/1分钟/2分钟/5分钟）
- **持久化存储** - 失败消息保存本地，应用重启或网络恢复自动重试
- **电量提醒** - 低电量/高电量提醒，可独立配置开关和阈值
- **充电提醒** - 设备开始或结束充电时发送提醒，可选择发送到指定通道
- **配置导入导出** - 支持导出配置文件到系统下载目录、复制文本或直接分享
- **实时日志** - 转发日志自动刷新，实时查看转发状态

### 🛡️ 可靠性设计
- **前台服务常驻** - 提高在厂商定制 ROM（如 HyperOS）上的存活率
- **网络状态监听** - 网络恢复自动触发失败消息重试
- **运行日志记录** - 完整日志保存，方便排查问题
- **电池优化引导** - 引导用户加入白名单，减少系统杀死概率

### 🎨 用户体验
- **Material Design 3** - 现代化 UI 设计
- **深色模式** - 自动适配系统主题
- **通道测试** - 一键发送测试消息，验证通道配置
- **规则测试** - 实时预览匹配效果
- **开机自启** - 设备启动后自动运行
- **模板预览** - 编辑消息模板时实时预览效果

---

## 📥 下载安装

### 最新版本
| 版本 | 说明 | 下载链接 |
|------|------|----------|
| **v2.9.6** | 黑白名单快捷插入体验优化（发送者/内容分别预设正向/负向规则，白名单信任类、黑名单拦截类）；快速创建入口改为常驻显示，随时可快捷添加；快速创建通道名称自动去重；额外关键词列表改为一行一个完整显示；发送者号码自动归一化兼容 +86 前缀；关于页面简介更新。 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.9.5** | 新增快速创建引导（三步式向导），无通道时首页显示快速创建入口，3 步完成通道+关键词配置；修复配置导入丢失大量字段的严重问题；修复 ChannelLoader 和 MessageMatcher 的线程安全问题；ModernCard 增加 onClick 支持。 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.9.4** | 新增全局内容白名单/黑名单过滤；修复发送者黑白名单对带 +86 前缀号码匹配失效的问题（号码自动归一化）；修正发送者过滤 UI 描述示例；配置导入导出支持内容黑白名单。 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.9.3** | 全局消息模板改为手动保存（保存按钮 + 重置按钮）；占位符点击插入到光标位置而非末尾；日志页头部布局优化，增加实时刷新状态标签；修复多项 UI 交互问题。 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.9.2** | 新增正则表达式关键词匹配、发送者黑白名单过滤、多关键词 AND/OR 组合、自定义消息模板、通道测试按钮；全局消息模板编辑器替代原三开关；日志实时自动刷新；全面优化交互体验；多项性能和代码质量优化。 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.9.0** | 大幅精简APK体积（7MB→2MB+）；修复电量/充电提醒无法触发的问题；代码架构重构，抽取多个工具类提升可维护性。 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.8.2** | 增加充电提醒功能；增加防诈骗提示；重构配置导入导出功能（移除二维码方案，改用文件+文本方式）；优化电量提醒逻辑。 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.7.10** | 隐私合规整改：添加首次运行隐私政策弹窗（纯文本展示）；设置页面添加隐私政策查看入口和撤销同意功能；权限管理页面展示所有权限状态；优化隐私政策弹窗界面 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.7.9** | 隐私合规整改：优化权限申请时机，首次打开不再主动弹窗请求权限；用户点击开启服务时先显示权限说明对话框，引导用户到系统设置手动开启；移除直接系统权限请求弹窗 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.7.8** | 电量提醒消息添加SIM卡手机号标识，便于区分设备；修复飞书消息格式问题；低电量和高电量提醒可独立配置开关和阈值 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.7.7** | 修复SIM卡设置页面电话权限状态不实时更新问题；添加电话权限友好提示；添加开机自启动友好提示；更新隐私政策；新增电量提醒功能；优化APK大小；增强代码健壮性 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.7.6** | 修复首页权限状态实时更新问题；关于页面自动获取版本号；添加官方网址和备案号；删除版权信息中的版本号 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.7.5** | 更新签名证书，准备 APP 备案 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.7.2** | 优化更新对话框和关于对话框按钮样式，统一按钮大小和位置；修复双卡设备 SIM 卡识别 bug，SIM2 收到的短信现在会正确显示 SIM2 的本机号码；优化 subscriptionId 获取方式，提高兼容性 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.6.4** | 修复双卡设备 SIM 卡识别 bug，SIM2 收到的短信现在会正确显示 SIM2 的本机号码；优化 subscriptionId 获取方式，提高兼容性 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.6.1** | 更新关于对话框，添加版权信息和软件说明；调整菜单布局，避免功能重复 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.6.0** | 降低最低支持版本到 Android 5.0 (API 21)，支持更多设备 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.5.2** | 调整页面内容分配，首页包含服务开关和开机启动，设置页包含SIM卡和消息格式 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.5.1 | 修复自定义 SIM 号码显示逻辑问题，避免重复出现 SIM 卡 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.5.0 | 新增底部导航栏，将功能分为首页、关键词、通道、设置、日志5个标签页 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.4.1 | 优化自定义 SIM 号码功能，添加更清晰的使用提示 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.4.0 | 新增自定义 SIM 号码功能，无法自动获取时支持手动输入 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.3.0 | 新增消息格式配置选项、SIM 卡信息预览 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.2.0 | 新增本机号码识别，双卡设备支持显示接收短信号码 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.1.3 | 修复验证码识别错误，优化提取逻辑 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.1.2 | 调整验证码显示顺序，优化消息格式 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.1.0 | 验证码自动提取与突出显示 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.0.2 | 统一消息前缀格式 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.0.1 | 多关键词、多通道 | [v2.0.1](https://github.com/jinghong-me/smsforwarder.cn/releases/tag/v2.0.1) |
| v1.1 | 多关键词、单通道 | [v1.1](https://github.com/jinghong-me/smsforwarder.cn/releases/tag/1.1) |

---

## 🚀 快速开始

### 环境要求
- Android 5.0 (API 21) 及以上
- 需要短信接收权限、通知权限、网络权限

### 安装步骤
1. 下载 APK 并安装
2. 打开应用，阅读并同意隐私政策（首次运行显示）
3. 点击首页「快速创建」按钮，按引导 3 步完成配置
4. 开启转发服务（首次开启时会显示权限说明，引导到系统设置开启短信和通知权限）

> 💡 **新手推荐**：使用首页的「快速创建」引导，只需填写 Webhook 地址，3 步即可完成全部配置。

### 权限说明
| 权限 | 用途 | 是否必需 |
|------|------|----------|
| 接收短信 | 监听设备收到的短信，用于执行转发功能 | ✅ 必需 |
| 通知权限 | 显示前台服务通知，让您知道服务正在运行 | ✅ 必需 |
| 读取手机状态 | 识别双卡手机的 SIM 卡信息和获取本机号码 | ❌ 可选 |
| 网络权限 | 转发到您配置的 Webhook | ✅ 必需 |
| 开机自启 | 设备启动后自动启动转发服务 | ❌ 可选 |
| 忽略电池优化 | 防止系统杀死后台服务 | ❌ 可选 |

### 隐私政策
- 应用首次运行时会显示完整隐私政策弹窗（纯文本展示）
- 用户可随时在设置页面查看隐私政策或撤销同意
- 所有数据仅存储在本地，不上传服务器

---

## 📖 使用指南

### 1. 添加转发通道
进入「通道」Tab，点击右下角「+」按钮：
- **企业微信**：填写 Webhook 地址
- **钉钉**：填写 Webhook 地址
- **飞书**：填写 Webhook 地址
- **通用 Webhook**：填写 HTTP(S) 地址
- 点击「发送测试消息」可验证通道配置是否正确

### 2. 配置关键词规则
进入「关键词」Tab，点击右下角「+」按钮：
- **主关键词**：输入匹配关键词（留空表示转发全部）
- **匹配模式**：包含匹配 / 精确匹配 / 正则表达式 / 排除匹配
- **多关键词逻辑**：OR（任一命中）/ AND（全部命中）
- **额外关键词**：添加多个关键词进行组合匹配
- **发送者过滤**：按发送者号码过滤（支持通配符/正则）
- **转发通道**：选择目标转发通道
- **匹配测试**：输入测试短信实时预览匹配结果

### 3. 配置发送者过滤（可选）
进入「设置」→「发送者过滤」：
- **白名单**：只有匹配的发送者号码才会被转发（启用后其他全部拦截）
- **黑名单**：匹配的发送者号码直接拦截，不会转发
- 支持通配符（`*` 代表任意字符，`?` 代表单个字符）和正则表达式
- 自动兼容 `+86`、`0086` 等国家代码前缀，直接写 `139*`、`106*` 即可
- 白名单优先级高于黑名单

### 4. 配置内容过滤（可选）
进入「设置」→「内容过滤」：
- **白名单**：只有内容匹配的短信才会被转发（启用后其他全部拦截）
- **黑名单**：内容匹配的短信直接拦截，不会转发
- 支持通配符（`*` 代表任意字符，`?` 代表单个字符）和正则表达式
- 示例：`*推销*`、`*贷款*`、`*验证码*`、`*银行*`
- 白名单优先级高于黑名单

### 5. 配置消息模板
- **全局默认模板**：设置 → 消息模板，编辑全局默认转发格式
- **通道独立模板**：编辑通道时可自定义该通道的消息模板，留空则使用全局模板
- 支持占位符：`{sender}` `{content}` `{time}` `{sim}` `{code}` `{keyword}` `{channel}`
- 提供 6 种预置模板，编辑时实时预览效果

### 6. 开启服务
- 打开「首页」→ 开启「转发服务」开关
- （可选）开启「开机自启动」
- 建议在系统设置中允许自启动和后台保活

### 7. 查看日志
- 进入「日志」Tab 查看转发记录
- 默认开启实时刷新（每 2 秒自动更新）
- 可手动刷新或清除日志

### 8. 配置导入导出
- **导出配置**：在设置页面点击「导出配置」，可选三种方式：保存到系统下载目录、复制到剪贴板、或通过系统分享发送给其他设备
- **导入配置**：在设置页面点击「导入配置」，可选两种方式：从文件选择器选择配置文件，或粘贴之前复制的文本内容
- 配置包含：通道、关键词规则、消息模板、发送者黑白名单、内容黑白名单、电量提醒等全部设置

---

## 🛠️ 开发者指南

### 过滤优先级
短信转发的完整过滤链路（任一环节拦截即终止）：
```
发送者白名单 → 发送者黑名单 → 内容白名单 → 内容黑名单 → 关键词规则匹配
```
白名单优先级高于黑名单。启用白名单后，只有命中白名单的才会通过。

### 项目结构
```
app/src/main/java/com/lanbing/smsforwarder/
├── MainActivity.kt              # Compose UI、配置导入导出
├── SmsReceiver.kt               # 短信接收、转发、去重、重试
├── SmsForegroundService.kt      # 前台服务与通知
├── BootReceiver.kt              # 开机启动处理
├── LogStore.kt                  # 日志存储
├── models.kt                    # 数据模型
├── Constants.kt                 # 常量定义
├── ExportConfig.kt              # 导出配置数据类
├── ui/
│   ├── AppColors.kt             # 颜色常量
│   └── PrivacyPolicyContent.kt  # 隐私政策文案
└── utils/
    ├── ChannelLoader.kt         # 通道/配置加载（带缓存）
    ├── ConfigManager.kt         # 配置导入导出管理
    ├── HttpClient.kt            # OkHttp 客户端
    ├── WebhookSender.kt         # Webhook 消息构建与发送
    ├── MessageMatcher.kt        # 关键词、发送者、内容匹配引擎
    ├── MessageTemplateRenderer.kt  # 消息模板渲染器
    ├── VerificationCodeExtractor.kt # 验证码提取
    ├── BatteryMonitor.kt        # 电量监测
    ├── SimInfoUtils.kt          # SIM 卡信息工具
    ├── PermissionUtils.kt       # 权限跳转工具
    └── NetworkMonitor.kt        # 网络状态监测
```

### 本地构建
```bash
# 克隆仓库
git clone https://github.com/jinghong-me/smsforwarder.cn.git
cd smsforwarder.cn

# 构建 Debug APK
./gradlew clean :app:assembleDebug

# 构建 Release APK（需要签名配置）
./gradlew clean :app:assembleRelease
```

### 技术栈
- **语言**：Kotlin
- **UI**：Jetpack Compose + Material Design 3
- **网络**：OkHttp
- **最低支持**：Android 5.0 (API 21)
- **目标版本**：Android 14 (API 34)

---

## 🔧 常见问题

### Q: 服务经常被系统杀死怎么办？
A: 请在系统设置中：
1. 允许应用自启动
2. 关闭电池优化（设置页面有入口）
3. 锁定后台进程

### Q: 如何查看运行日志？
A: 在应用内「日志」标签页查看（默认实时刷新），或通过 adb 拉取：
```bash
adb shell run-as com.lanbing.smsforwarder cat files/sms_forwarder_logs.txt
```

### Q: 转发失败怎么办？
A: 检查：
1. Webhook 地址是否正确
2. 网络连接是否正常
3. 应用内日志查看详细错误

### Q: 如何测试通道配置是否正确？
A: 在通道编辑页面点击「发送测试消息」按钮，一键验证配置。

### Q: 如何自定义消息格式？
A: 有两种方式：
1. **全局模板**：设置 → 消息模板，编辑全局默认格式
2. **通道模板**：编辑具体通道时，在消息模板处自定义，留空则使用全局模板

### Q: 发送者黑名单/白名单怎么用？
A: 在「设置」→「发送者过滤」中配置。支持通配符匹配，比如：
- `106*` — 拦截所有 106 开头的号码
- `139*` — 拦截所有 139 开头的手机号
- `400*` — 拦截所有 400 开头的号码
- 系统会自动处理 `+86`、`0086` 等国家代码前缀，不需要手动加

### Q: 内容黑名单/白名单怎么用？
A: 在「设置」→「内容过滤」中配置。支持通配符匹配，比如：
- `*推销*` — 拦截所有包含"推销"的短信
- `*贷款*` — 拦截所有包含"贷款"的短信
- `*验证码*` — 白名单模式下只转发包含"验证码"的短信

### Q: 白名单和黑名单同时启用会怎样？
A: 白名单优先级更高。启用白名单后，只有命中白名单的才会通过；在通过白名单的基础上，再检查是否命中黑名单，命中则拦截。

### Q: 如何查看隐私政策？
A: 在应用内「设置」页面的「隐私设置」卡片中点击「隐私政策」按钮。

### Q: 如何撤销隐私政策同意？
A: 在应用内「设置」页面的「隐私设置」卡片中点击「撤销隐私政策同意」按钮，系统会清除同意状态并退出应用。

### Q: 应用会收集我的短信内容吗？
A: 不会。所有数据仅存储在您的手机本地，不会上传到任何服务器。短信内容仅在转发时临时处理，转发目标由您自己配置。

### Q: 双卡手机如何区分哪个号码收到的短信？
A: 应用会自动识别接收短信的 SIM 卡，并在转发消息中显示本机号码。如果无法自动获取，您可以在设置页面手动输入自定义 SIM 号码。

### Q: 如何测试关键词规则？
A: 在关键词编辑对话框的「匹配测试」区域，输入测试短信内容，实时显示匹配结果。

### Q: 如何在多个设备间同步配置？
A: 使用配置导入导出功能：在源设备点击「导出配置」保存到下载目录或复制文本，在目标设备点击「导入配置」选择文件或粘贴文本即可完成配置迁移。

### Q: 第一次使用怎么配置最快？
A: 打开应用后，在首页点击「快速创建」卡片，按三步向导操作：选择通道类型 → 粘贴 Webhook 地址 → 输入关键词（可选），点「完成」即可。整个过程只需 30 秒。

---

## 📄 许可证

本项目采用 [MIT 许可证](LICENSE.txt)。

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

提交 PR 时请说明：
- 变更目的与实现要点
- 隐私/合规影响（若处理敏感数据）
- 测试设备/Android 版本

---

<div align="center">

如果这个项目对你有帮助，欢迎给个 ⭐ Star！

</div>

</details>
