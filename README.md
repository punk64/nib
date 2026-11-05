# 落笔 Nib

> 安卓端随手记录工具。工作 / 个人 / 家庭三线并行，单机优先，预留多端同步。
>
> An Android note-and-task app for quick capture. Work / personal / family side by side, local-first, sync-ready.

**[中文](#中文) · [English](#english)**

---

## 中文

一句话记下，解析器替你拆好日期、时间、标签和优先级。

### 特性

- **速记解析**：`明天10点 交周报 #工作 !!` 自动识别日期、时间、标签、优先级与重复周期。支持 今天 / 明天 / 后天 / 大后天 / 昨天 / 前天 / 大前天 / N天后 / 周几 / 下周几 / 绝对日期，以及 凌晨 / 早上 / 上午 / 中午 / 下午 / 傍晚 / 晚上 等时段词。
- **四个视图**：今日（逾期 / 今天 / 未排期 / 已完成）、全部（筛选与搜索）、日历（本地记录 + 系统日程双点标记）、我的。
- **提醒双通道**：应用内精确闹钟 + 通知，或写入系统日历，二选一。
- **组织能力**：清单单选、标签多选、子项、置顶、拖拽排序、批量操作、导入分享文本。
- **五套主题**：极光玻璃 / 暗房胶片 / 纸感编辑 / 终端 HUD / 简约，24 色强调色，圆角、颗粒、密度可调。
- **桌面入口**：速记浮层、三个 Glance 组件、长按快捷方式、快捷设置磁贴。
- **本地优先**：Room 单机存储；ULID 主键、软删除、HLC 时间戳、fractional index、字段级 op_log 已为同步预留。
- **导出**：Markdown / ICS / 整库 JSON，支持备份导入。

### 技术栈

| 项 | 版本 |
|---|---|
| 语言 | Kotlin 2.4 |
| UI | Jetpack Compose（Material 3） |
| 数据 | Room 2.8（6 表 + 4 次迁移） |
| 桌面组件 | Glance 1.2 |
| 构建 | Gradle 9.3 / AGP 9.1 |
| SDK | compileSdk 37 / targetSdk 36 / minSdk 24 |

### 构建

```bash
cd nib_native
./gradlew :app:assembleDebug
```

产物位于 `nib_native/app/build/outputs/apk/debug/app-debug.apk`。需要 JDK 17 与本地 Android SDK（`nib_native/local.properties` 中的 `sdk.dir`）。

运行单元测试：

```bash
./gradlew :app:testDebugUnitTest
```

### 目录结构

```
.
├── nib_native/                 纯原生实现（Kotlin + Compose + Room）
│   └── app/src/main/java/com/yourname/nib/
│       ├── core/               数据、解析、提醒、日历、同步、导出、主题
│       ├── features/           今日 / 全部 / 日历 / 编辑 / 我的
│       ├── widgets/            通用组件与桌面入口组件
│       └── MainScreen.kt
├── nib/                        Flutter 早期实现（留档，不参与构建）
├── 落笔-预览-*.html             设计预览（视觉与交互基准）
├── 落笔-设计方案.md             设计方案
└── 产物/                       历史构建产物
```

### 已知边界

- 单机运行，服务端同步尚未接入（`SyncEngine` 与传输层已就绪，缺服务端）。
- 仅构建 debug 包，未做 release 签名。

---

## English

Write it down in one line — the parser splits out date, time, tags and priority for you.

### Features

- **Quick capture parsing**: `明天10点 交周报 #工作 !!` recognizes date, time, tags, priority and recurrence. Supports 今天 / 明天 / 后天 / 大后天 / 昨天 / 前天 / 大前天 / N天后 / weekday / next weekday / absolute dates, plus periods such as 凌晨 / 早上 / 上午 / 中午 / 下午 / 傍晚 / 晚上.
- **Four views**: Today (overdue / today / unscheduled / done), All (filters and search), Calendar (local entries and system events with two dot styles), Me.
- **Dual reminder channels**: exact in-app alarm with notification, or write into the system calendar — pick one.
- **Organization**: single list per entry, multiple tags, sub-items, pinning, drag sorting, batch actions, share-text import.
- **Five themes**: aurora glass / darkroom film / paper edit / terminal HUD / minimal, 24 accent colors, tunable radius, grain and density.
- **Home screen entries**: quick capture overlay, three Glance widgets, long-press shortcuts, quick settings tile.
- **Local-first**: Room storage; ULID keys, soft delete, HLC timestamps, fractional indexes and a field-level op_log are already in place for sync.
- **Export**: Markdown / ICS / full JSON backup with import.

### Stack

| Item | Version |
|---|---|
| Language | Kotlin 2.4 |
| UI | Jetpack Compose (Material 3) |
| Data | Room 2.8 (6 tables, 4 migrations) |
| Widgets | Glance 1.2 |
| Build | Gradle 9.3 / AGP 9.1 |
| SDK | compileSdk 37 / targetSdk 36 / minSdk 24 |

### Build

```bash
cd nib_native
./gradlew :app:assembleDebug
```

The APK lands in `nib_native/app/build/outputs/apk/debug/app-debug.apk`. Requires JDK 17 and a local Android SDK (`sdk.dir` in `nib_native/local.properties`).

Run unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

### Layout

```
.
├── nib_native/                 native implementation (Kotlin + Compose + Room)
│   └── app/src/main/java/com/yourname/nib/
│       ├── core/               data, parser, reminders, calendar, sync, export, theme
│       ├── features/           today / all / calendar / editor / me
│       ├── widgets/            shared widgets and home screen components
│       └── MainScreen.kt
├── nib/                        early Flutter implementation (archived, not built)
├── 落笔-预览-*.html             design previews (visual and interaction baseline)
├── 落笔-设计方案.md             design document
└── 产物/                       historical builds
```

### Known limits

- Local-only; the server side of sync is not wired up yet (`SyncEngine` and the transport layer are ready, the backend is not).
- Only debug builds are produced; release signing is not configured.

---

## 项目地址 / Links

- 仓库 / Repository: https://github.com/punk64/nib
- 问题反馈 / Issues: https://github.com/punk64/nib/issues

## 许可 / License

未指定许可证，保留所有权利。如需复用请先联系作者。
No license specified, all rights reserved. Contact the author before reuse.
