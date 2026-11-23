# Bark 版本构建与发布

当前版本为 **2.9.8-bark.26，versionCode 26**。本页与 Actions 中的 **Build signed Bark APK** 工作流对应。旧普通版发布入口已改为复用相同的 Bark 构建。

## 1. 统一版本信息

版本在根目录 gradle.properties 中维护：

```properties
barkVersionName=2.9.8
barkVersionCode=26
```

该配置生成 2.9.8-bark.26。下一个 bark.27 将编号改为 27；如果已经发布过更大的内部版本号，应使用比已发布版本更大的编号。同步更新 README 与版本说明，再 Commit、Push 到 main。

工作流从源码读取版本；不再把运行次数充当内部版本号，也不再固定为 bark.1。

## 2. 只发布已有的正式 APK

已经有对应源码版本的正式签名 APK 时，直接进行第 5 节，无需配置 Actions 签名 Secrets。

## 3. 首次配置 Actions 签名

仅在需要 GitHub 自动构建 APK 时配置。使用此前单独保存的 **原 Bark 签名备份**。

仓库中点击 **Settings → Secrets and variables → Actions → New repository secret**，添加：

| Secret 名称 | Value 内容 |
| --- | --- |
| KEYSTORE_BASE64 | 原 Bark .jks 文件的 Base64 文本 |
| KEYSTORE_PASSWORD | 原签名备份中的 storePassword |
| KEY_ALIAS | 原签名备份中的 keyAlias |
| KEY_PASSWORD | 原签名备份中的 keyPassword |

Mac 上可运行下方命令，将原 .jks 的 Base64 复制到剪贴板。先把示例路径替换为实际路径，再将剪贴板内容粘贴到 KEYSTORE_BASE64 的 Value：

```bash
base64 -i "/实际路径/signing/smsforwarder-bark.jks" | tr -d '\n' | pbcopy
```

Base64 是编码，不是加密；内容只粘贴到 Secret。签名备份、.jks、bark-signing.properties 和密码截图均不提交到代码仓库。

原 Bark 证书公开 SHA-256 指纹：

```text
e7c52e6d0ff2e04c8fe05c691a72f9548b934ee4c2b9f91a0f7b91d3a8d3f039
```

指纹来自现有 Bark APK 及 BARK_README.txt，与上游普通版 Lucian 证书不同。工作流在构建前和 APK 生成后核对签名；密钥不匹配时停止，不生成替代签名。

## 4. 运行正式 Bark 构建

1. Commit 并 Push 更新后的源码、版本配置和工作流。
2. 打开仓库 Actions。Fork 首次启用工作流时，如出现启用提示，启用自己仓库的工作流。
3. 选择 **Build signed Bark APK → Run workflow**，选中当前版本源码所在的 main 分支后运行。
4. 任务先运行发布校验脚本测试和 App 单元测试，再恢复临时原签名并执行 assembleBark。
5. 下载运行页面底部的构建产物 **smsforwarder-2.9.8-bark.26**。解压后包含：
   - smsforwarder-2.9.8-bark.26.apk
   - SHA256SUMS.txt
   - release-metadata.json

产物名称来自实际版本，后续版本无需手工修改工作流。任务核对包名 com.lanbing.smsforwarder.bark、版本号、非调试属性与原永久签名。

**Build signed Bark APK (legacy entry point)** 仅为旧入口的兼容转发。**Android Build** 是原有的调试构建，正式分发使用上面的 Bark 工作流产物。

Secrets 缺失、密钥不匹配或校验失败时，任务会失败。只有在 GitHub 实际运行成功后，才能确认线上构建通过。

## 5. 发布 GitHub Release

1. 打开仓库 **Releases → Draft a new release**；没有发布过时可能显示 **Create a new release**。
2. 选择或创建标签 **v2.9.8-bark.26**，Target 指向构建此 APK 的源码提交。使用历史构建产物时，标签应指向其实际构建的提交。
3. 标题填写 **短信转发助手 Bark 2.9.8-bark.26**，描述本版变化。
4. 上传解压后的 APK、SHA256SUMS.txt 和可选的 release-metadata.json。签名文件保留在本机或 Secrets 中。
5. 检查版本与附件对应后点击 **Publish release**。

当前工作流只构建和校验 APK；公开 Release 由你手动发布。后续版本创建新标签和新 Release，并保留旧版安装包及源码标签。

## 6. 本地构建

安装 JDK 17 和 Android SDK 34，在本机恢复原 bark-signing.properties 及 signing/smsforwarder-bark.jks，然后执行：

```bash
bash ./gradlew :app:testDebugUnitTest :app:assembleBark
```

也支持用 KEYSTORE_FILE、KEYSTORE_PASSWORD、KEY_ALIAS、KEY_PASSWORD 环境变量提供同一原签名。私钥和密码文件由 .gitignore 排除。

使用 Android SDK 校验包时，可执行：

```bash
python3 .github/scripts/bark_release.py verify-apk
```

脚本从 ANDROID_SDK_ROOT 或 ANDROID_HOME 查找 Build Tools。校验通过后，将正式 APK 和校验信息输出到 release-artifacts/，再把 APK 附加到 Release。
