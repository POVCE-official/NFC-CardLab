# NFC CardLab

[English](#english) | [简体中文](#chinese)

<a id="english"></a>

## English

An Android NFC reader, writer, and card manager for Android 8.0 and later.

[Download APK](https://github.com/POVCE-official/NFC-CardLab/releases/latest) · [使用说明](使用说明.md)

### Features

- Quick actions: read a card, write text or a website URL, launch an app, open an app page, open system settings, and clear card content.
- Guided writing: enter or select content, review the size and overwrite confirmation, then tap one card to write.
- Website addresses automatically use `https://` when no scheme is provided. Apps can be selected from a searchable list of installed apps or specified by package name.
- App pages use URI deep links or App Links supported by the target app.
- System tasks open Wi-Fi, Bluetooth, sound, display, NFC, location, or airplane-mode settings. Switches are operated manually in the system panel.
- App and system tasks require CardLab on the phone reading the tag. Standard URL records work with other compatible phones. In the foreground, CardLab shows a button to try the scanned task.
- Options include success vibration, full Classic sector reading, and initialization of compatible blank tags. Quick scanning is the default.

### Card support

- **Identification:** technology types, UID, and available ATQA/SAK information.
- **NDEF:** text, URI, and raw record reading; capacity and read-only checks; read-back verification after writing existing NDEF tags. Unknown MIME records are displayed as data.
- **MIFARE Classic:** sector authentication using supplied keys, block reading with per-block status, individual data-block writing, and restoration of readable data blocks. Block 0 and sector trailers are protected from writing. This does not clone card identity or keys.
- **Ultralight / NTAG:** reads the first four pages and identifies the compatible family; full-capacity detection and exact model identification are not available.
- **ISO-DEP:** communication information and imported static APDU scripts. Vendor-specific file creation and dynamic authentication require the appropriate protocol.
- **Card wallet:** local snapshots encrypted with Android Keystore AES-GCM, with plain JSON import and export. Export needed data before uninstalling, which removes the local encryption key.
- **Known-key decryption:** AES, 3DES, and DES with CBC/PKCS5Padding, using a supplied key and IV.

Unknown-key recovery, universal CPU-card copying, phone HCE, and arbitrary `.apduscr` parsing are not implemented. Compatibility depends on the phone and card.

Clearing an NDEF tag replaces its content with an empty record; it is not a secure chip erase. Classic clearing writes zeroes to ordinary data blocks while retaining UID, keys, and access conditions. Initialization is limited to tags reported as `NdefFormatable` by the phone; tap again afterward to check the result.

### Usage

Choose **Read card** on the home screen and tap a card. To write text, URLs, or tasks, choose the corresponding shortcut first. For Classic sector contents, enter known keys in the advanced tools and enable full reading in settings. Snapshots can be saved, opened, exported, and restored through the card wallet.

Leaving the app cancels a pending operation. Removing the card during writing may leave a partially completed operation.

APDU scripts use UTF-8, one command per line, with `#` comments:

```text
# Example SELECT; replace the AID with one supported by your card
00 A4 04 00 05 F0 00 00 00 01 => 9000
```

The default expected status word is `9000`. A mismatch stops execution and shows the response. Commands are not retried, `61xx`/`6Cxx` responses are not followed automatically, and earlier operations are not rolled back. Imported scripts require confirmation before execution. This is not the complete Flipper `.apduscr` format.

---

<a id="chinese"></a>

## 简体中文

[English](#english) | [下载安装包](https://github.com/POVCE-official/NFC-CardLab/releases/latest)


Android NFC 读写与卡片管理工具，支持 Android 8.0 及以上版本。

### 功能与限制

#### 1.0 快捷首页

- 大按钮：读卡、写文字、写网址、打开 App、App 页面、系统功能、清空卡片内容。
- 简化流程：填写 / 选择内容 → 预览字节数并确认覆盖 → 独立贴卡页面 → 显示结果；单次准备仅处理一张卡。
- 网站自动补全 https://，App 从带搜索的已安装应用列表选择，也可手动填写包名。
- App 页面使用应用提供的 URI / App Link；目标应用必须支持该链接。
- 系统任务可选择 Wi-Fi、蓝牙、声音、显示亮度、NFC、定位、飞行模式设置。打开系统面板后手动操作，不提供通用静默开关。
- App 与系统任务使用 CardLab MIME 记录，碰卡执行的手机需安装 CardLab；应用在前台时读卡后点击“试用此任务”。普通网址使用标准 URI，其他兼容手机也可识别。
- 清空普通标签会覆盖为一条空 NDEF 记录，并非安全擦除整片芯片。高级 Classic 清空只将普通块写零，不改 UID、密钥或权限尾块。
- 设置：成功振动、完整 Classic 扇区读取、允许初始化兼容空白标签。默认快速扫描，不做耗时的 Classic 全卡认证。
- 初始化使用 Android NdefFormatable，仅适配手机报告支持的标签；初始化后需重新贴卡核对。已有 NDEF 标签写入后立即回读核对。

- NFC Reader Mode 自动识别技术类型、UID、ATQA/SAK。
- NDEF：读取文本、URI 和原始记录；文字覆盖写入，检查容量、只读状态并回读验证。未知 MIME 内容只作数据展示，不自动执行。
- Classic：使用用户输入的已知 6 字节密钥，逐扇区尝试 A/B 认证，逐块展示读取状态。支持单块写入与快照普通数据块恢复并回读验证，阻止块 0 与尾块写入。不能完整克隆卡片身份或密钥。
- Ultralight/NTAG：只读取前 4 页，系列识别；未实现全容量探测及型号确认。
- ISO-DEP：展示协议参数，通过手机执行用户导入的静态 APDU 脚本；不预装任意厂商 COS 的文件创建/动态认证协议。
- 卡包：快照在应用私有目录以 Android Keystore AES-GCM 加密保存；JSON 导入导出为明文。卸载后密钥丢失，需提前导出自己的数据。
- 已知密钥解密：AES、3DES、DES 的 CBC/PKCS5Padding；输入密钥和 IV。其他算法、填充和业务协议尚未适配。
- 未实现：未知密钥恢复、通用 CPU 卡复制、手机 HCE、任意 `.apduscr` 格式解析。不能保证所有手机和芯片兼容。

### 使用

首页点“读卡”后贴卡，无需参数。写文字、网址等先选择快捷入口。需要读取 Classic 内部块时，在高级页填写自己卡片的密钥（默认 FFFFFFFFFFFF），并在设置开启完整读取。在卡包保存快照，打开后可以查看、导出。高级页保留 Classic 块数据与 APDU 功能。离开 App 取消待执行任务；写入过程中移走卡片可能导致部分操作已生效。

APDU 文件格式（UTF-8，每行一条，`#` 开头为注释）：

```text
# SELECT 示例；请替换为目标卡实际支持的 AID
00 A4 04 00 05 F0 00 00 00 01 => 9000
```

默认期望状态字 9000。状态字不符立即停止，日志显示响应；不自动重试、不自动跟随 61xx/6Cxx、不回滚之前操作。脚本导入后需再次点执行并确认。此格式不是完整 Flipper `.apduscr`。

