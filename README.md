# NFC CardLab

English | [简体中文](README.zh-CN.md)

An Android NFC reader, writer, and card manager for Android 8.0 and later.

[Download APK](https://github.com/POVCE-official/NFC-CardLab/releases/latest) · [User guide (Chinese)](使用说明.md)

## Features

- Quick actions: read a card, write text or a website URL, launch an app, open an app page, open system settings, and clear card content.
- Guided writing: enter or select content, review the size and overwrite confirmation, then tap one card to write.
- Website addresses automatically use `https://` when no scheme is provided. Apps can be selected from a searchable list of installed apps or specified by package name.
- App pages use URI deep links or App Links supported by the target app.
- System tasks open Wi-Fi, Bluetooth, sound, display, NFC, location, or airplane-mode settings. Switches are operated manually in the system panel.
- App and system tasks require CardLab on the phone reading the tag. Standard URL records work with other compatible phones. In the foreground, CardLab shows a button to try the scanned task.
- Options include success vibration, full Classic sector reading, and initialization of compatible blank tags. Quick scanning is the default.

## Card support

- **Identification:** technology types, UID, and available ATQA/SAK information.
- **NDEF:** text, URI, and raw record reading; capacity and read-only checks; read-back verification after writing existing NDEF tags. Unknown MIME records are displayed as data.
- **MIFARE Classic:** sector authentication using supplied keys, block reading with per-block status, individual data-block writing, and restoration of readable data blocks. Block 0 and sector trailers are protected from writing. This does not clone card identity or keys.
- **Ultralight / NTAG:** reads the first four pages and identifies the compatible family; full-capacity detection and exact model identification are not available.
- **ISO-DEP:** communication information and imported static APDU scripts. Vendor-specific file creation and dynamic authentication require the appropriate protocol.
- **Card wallet:** local snapshots encrypted with Android Keystore AES-GCM, with plain JSON import and export. Export needed data before uninstalling, which removes the local encryption key.
- **Known-key decryption:** AES, 3DES, and DES with CBC/PKCS5Padding, using a supplied key and IV.

Unknown-key recovery, universal CPU-card copying, phone HCE, and arbitrary `.apduscr` parsing are not implemented. Compatibility depends on the phone and card.

Clearing an NDEF tag replaces its content with an empty record; it is not a secure chip erase. Classic clearing writes zeroes to ordinary data blocks while retaining UID, keys, and access conditions. Initialization is limited to tags reported as `NdefFormatable` by the phone; tap again afterward to check the result.

## Usage

Choose **Read card** on the home screen and tap a card. To write text, URLs, or tasks, choose the corresponding shortcut first. For Classic sector contents, enter known keys in the advanced tools and enable full reading in settings. Snapshots can be saved, opened, exported, and restored through the card wallet.

Leaving the app cancels a pending operation. Removing the card during writing may leave a partially completed operation.

APDU scripts use UTF-8, one command per line, with `#` comments:

```text
# Example SELECT; replace the AID with one supported by your card
00 A4 04 00 05 F0 00 00 00 01 => 9000
```

The default expected status word is `9000`. A mismatch stops execution and shows the response. Commands are not retried, `61xx`/`6Cxx` responses are not followed automatically, and earlier operations are not rolled back. Imported scripts require confirmation before execution. This is not the complete Flipper `.apduscr` format.
