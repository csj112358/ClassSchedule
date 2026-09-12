# 📚 课程表 Android App

一款采用苹果「液态玻璃」设计风格的课程表应用，支持从**教务系统课表 JSON** 一键导入课表，本地离线解析、免费无依赖。

> 核心工作流：把教务系统返回的课表 `response`（JSON）**粘贴**或**存成 `.txt` 导入**，应用本地解析成课程并写入课表，按周查看。

> ⚠️ **本项目不内置任何具体学校的课表格式。** 课表解析器 [SemesterJsonParser.kt](app/src/main/java/com/classschedule/data/parser/SemesterJsonParser.kt) 是一个**通用模板**，需要你按自己课表 JSON 的结构填好解析逻辑后才能用（见下方「课表格式适配说明」）；不想改代码也可用「AI 整理」。

## ✨ 功能特性

### 📥 课表导入（核心）
- **两种导入方式**：
  - **粘贴 JSON**：直接把教务课表 JSON 粘贴到文本框解析。
  - **从 txt 文件导入**：粘贴框放不下大文本时，把 JSON 存成 `.txt`，用右上角按钮读取（自动识别 UTF-8 / GBK 编码）。
- **两种导入模式**：
  - **第 N 周导入**（默认）：把解析结果折叠为指定周的「单周快照」，只覆盖该周，其它周不受影响。
  - **整学期导入**：按 JSON 自带的完整周次范围，清空整个学期并一次性写入。
- **批量按周导入**：把每周的 JSON 分别存成 `.txt`，**文件名改成周数**（如 `3.txt`、`第3周.txt`、`5周.txt`），可一次多选多个文件，批量导入多个周。
- **两种解析方式**：
  - **本地解析**（免费·离线·推荐）：在 `SemesterJsonParser` 里按你课表的格式实现解析逻辑，无需联网、无需任何密钥。
  - **AI 整理**（可选）：结构太绕、不想自己写解析时，可在设置里配置任意 OpenAI 兼容接口（内置 DeepSeek / Kimi 预设，也可手填地址与模型名），由模型把 JSON 整理成标准课次。**需要你自己填写 API Key，Key 仅保存在本机。**

### 🔔 上课提醒
- 上课前按提前量推送提醒，可自定义提前分钟数。
- 重启手机后自动恢复提醒（开机广播），提醒时间由 `ReminderScheduler` 统一调度。

### 📤 课时配置分享
- 把本机课时配置导出为文件分享给同学，也可从他人分享的文件导入。
- 通过 `FileProvider` 分享，无需存储权限。

### 📅 课程表管理
- 周视图展示，可左右翻周或下拉快速跳转到任意一周。
- 支持课程增删改查，10 种颜色自动标记不同课程。
- **短按课程卡片**弹出信息浮窗（教师 / 教室 / 节次 / 周次 / 备注）；**长按**直接进入编辑界面。

### 📆 学期管理
- 创建多个学期，设置起止日期，切换当前学期。
- 设置起止日期后**自动定位当前周**，并在课表星期行显示真实日期。

### ⏰ 课时配置
- 自定义每天节数与每节课上下课时间。
- 导入时可根据 JSON 里的节次时间补齐课时配置（含晚上节次）。

### 🎨 主题与小组件
- 6 种液态玻璃渐变主题：蓝紫、粉橙、青绿、暖阳、冰蓝、深紫。
- 桌面小组件（Glance）快速查看本周课程。

## 🏫 课表格式适配说明（本地解析必读）

课表解析器 [SemesterJsonParser.kt](app/src/main/java/com/classschedule/data/parser/SemesterJsonParser.kt) **是一个空白模板**，不内置任何学校的格式。用本地解析时，未适配会提示「无法识别为课表 JSON：请先在 SemesterJsonParser.parse() 里适配你课表的字段结构」。你需要按下面步骤，把它改成适配自己课表的解析器。

### 你需要做什么

1. **拿到课表 JSON**：登录学校教务系统，打开课表页面，用浏览器 `F12 → Network` 找到返回课表数据的接口，复制其返回的 JSON（通常是 `{"data": ...}` 这样的结构）。
2. **理清结构**：梳理出「星期几 / 课程名 / 教师 / 教室 / 节次 / 周次 / 单双周」分别在 JSON 的哪个字段、以什么形式表示。
3. **改解析器**：打开 [SemesterJsonParser.kt](app/src/main/java/com/classschedule/data/parser/SemesterJsonParser.kt)，按文件里标注的 `TODO(1/3)` ~ `TODO(3/3)` 三步走：
   1. 在「解析入口」的【原始 JSON 结构】处定义你课表的 JSON 数据类（文件里附了一个带注释的示例结构）；
   2. 改 `parse()`：遍历你的字段，取出星期几 / 课程名 / 教师 / 教室 / 节次 / 周次，调用 `toOccurrences(...)` 得到课次；
   3. 改 `extractSectionTimes()`：让它能遍历你的时间段字段（用于自动补齐课时配置；不改也能跑，只是不自动补时间）。
4. **编译运行**：在导入页粘贴 JSON，预览确认无误后即可导入。改好一次，其它同学也能直接用。

> 数据契约放在同目录的 [ParsedOccurrence.kt](app/src/main/java/com/classschedule/data/parser/ParsedOccurrence.kt)，通常不需要改动。

### 通用数据模型（无需改动）

解析器最终要把你的 JSON 映射成通用的 `ParsedOccurrence`，字段含义如下：

| 字段 | 含义 |
|------|------|
| `name` / `room` / `teacher` | 课程名 / 教室 / 教师 |
| `dayOfWeek` | 星期几（1=周一 … 7=周日） |
| `startPeriod` / `endPeriod` | 开始 / 结束节次 |
| `weekStart` / `weekEnd` | 开始 / 结束周 |
| `weekParity` | 0=每周、1=仅单周、2=仅双周 |

### 常见差异点

不同学校教务返回差异较大，主要留意：

- **顶层字段名**：可能是 `data`、`data.weekDays`、`data.courses`，或直接是一个数组；
- **星期几的表示**：可能是数字 `1~7`、也可能是「周一」中文，或靠数组顺序兜底；
- **节次**：可能是 `[3-4节]`、也可能是独立的 `startSection` / `endSection` 字段；
- **周次**：可能是 `1-16周`、`1-16周(单)`、`第1-16周`、或 `1-8,10-17周` 等多区间写法；
- **单双周**：可能是「单周/双周」「奇/偶」「1/2」等。

> 这些都用你自己的字段名 / 正则去匹配即可，模板里的注释会指出每个位置该填什么。

## 📋 环境要求

| 工具 | 版本 |
|------|------|
| Android Studio | Hedgehog (2023.1.1) 或更高 |
| JDK | 17 |
| Gradle | 8.2 |
| Android Gradle Plugin | 8.2.0 |
| Kotlin | 1.9.20 |
| KSP | 1.9.20-1.0.14 |
| compileSdk / targetSdk | 34 |
| minSdk | 26（Android 8.0） |

## 🇨🇳 国内镜像配置

项目已预配置国内镜像源（见 `settings.gradle.kts` 与 `gradle/wrapper/gradle-wrapper.properties`）：

| 配置项 | 镜像源 |
|-------|--------|
| Gradle 分发包 | 腾讯云镜像 |
| Maven 仓库 | 阿里云镜像（google / central / public / gradle-plugin）+ 腾讯云（备用） |

如需切换镜像，修改上述文件中的 URL 即可。常用镜像：阿里云 `https://maven.aliyun.com/repository/`、腾讯云 `https://mirrors.cloud.tencent.com/nexus/repository/maven-public/`、华为云 `https://repo.huaweicloud.com/repository/maven/`。

更多离线安装与代理配置见 [SETUP_GUIDE.md](SETUP_GUIDE.md)。

## 🛠 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3（液态玻璃渐变组件）
- **架构**: MVVM
- **数据库**: Room
- **依赖注入**: Hilt
- **序列化**: Gson
- **图片加载**: Coil
- **持久化**: DataStore
- **桌面小组件**: Glance

## 📁 项目结构

```
app/src/main/java/com/classschedule/
├── ClassScheduleApp.kt           # Application（Hilt 入口）
├── MainActivity.kt               # 主 Activity 与导航
├── data/
│   ├── api/
│   │   └── KimiApiService.kt     # OpenAI 兼容接口客户端（AI 整理；Key 由用户自填）
│   ├── db/
│   │   ├── AppDatabase.kt        # Room 数据库
│   │   └── Daos.kt               # DAO（含按周删除等 SQL）
│   ├── model/
│   │   └── Models.kt             # Course / Semester / PeriodConfig / ApiConfig
│   ├── parser/
│   │   ├── SemesterJsonParser.kt # 课表 JSON 解析器（通用模板，格式自行适配）
│   │   └── ParsedOccurrence.kt   # 解析结果数据契约（通常无需改动）
│   ├── prefs/
│   │   ├── SettingsStore.kt      # DataStore 设置持久化
│   │   └── AppThemeColors.kt     # 主题配色
│   ├── share/
│   │   └── PeriodSharing.kt      # 课时配置的导出/导入格式
│   └── repository/
│       └── Repositories.kt       # 数据仓库
├── di/
│   └── AppModule.kt              # Hilt 依赖注入
├── reminder/
│   ├── ReminderScheduler.kt      # 上课提醒调度
│   ├── ClassReminderReceiver.kt  # 提醒触发接收器
│   ├── ClassReminderNotifier.kt  # 通知构建
│   ├── ReminderIntents.kt        # Intent 常量
│   └── BootCompletedReceiver.kt  # 开机后恢复提醒
├── ui/
│   ├── components/
│   │   └── GlassComponents.kt    # 液态玻璃 UI 组件
│   ├── glass/
│   │   ├── GlassTokens.kt        # 设计代币（圆角/弹簧/时长/光学）
│   │   ├── LiquidGlass.kt        # 液态玻璃核心绘制
│   │   ├── GlassMaterial.kt      # 玻璃材质
│   │   ├── GlassDialog.kt        # 玻璃弹窗
│   │   └── GlassDatePicker.kt    # 玻璃日期选择器
│   ├── screens/
│   │   ├── ScheduleScreen.kt     # 课程表页面（短按信息 / 长按编辑）
│   │   ├── RecognitionScreen.kt  # 导入页面（粘贴 / 读文件 / 批量按周）
│   │   ├── SemesterScreen.kt     # 学期管理页面
│   │   ├── CourseEditScreen.kt   # 课程编辑页面
│   │   ├── PeriodConfigScreen.kt # 课时配置页面
│   │   ├── PeriodSharingScreen.kt# 课时配置分享/导入页面
│   │   └── SettingsScreen.kt     # 设置页面（学期/课时/主题/API）
│   ├── theme/
│   │   └── Theme.kt              # 主题配置
│   └── viewmodel/
│       └── ViewModels.kt         # MainViewModel（导入逻辑/周次计算等）
├── util/
│   ├── FileTextReader.kt         # txt 读取（UTF-8 / GBK 自动识别）
│   ├── TextFileSharer.kt         # 文本文件分享（FileProvider）
│   └── ScheduleTime.kt           # 时间与节次换算
└── widget/
    ├── ScheduleWidgetBase.kt     # 小组件渲染基类
    ├── ScheduleWidget.kt         # 桌面小组件
    ├── ScheduleWidgetReceiver.kt # 小组件接收器
    └── WidgetUpdateWorker.kt     # 小组件定时刷新（WorkManager）
```

## 🚀 使用说明

### 1. 创建学期（可选）
1. 进入「设置 → 学期管理」，点击「添加学期」。
2. 输入学期名称和起止日期，设为当前学期。
3. 未创建学期时，首次导入会自动创建「我的课表」。

> 设置起止日期后可自动定位当前周、在课表星期行显示真实日期；不设置也可手动翻周。

### 2. 适配课表格式（本地解析必做；用 AI 整理可跳过）
按上方「课表格式适配说明」，在 [SemesterJsonParser.kt](app/src/main/java/com/classschedule/data/parser/SemesterJsonParser.kt) 里填好你课表的解析逻辑并编译。

> 不想改代码：改用「AI 整理」，在「设置 → API 配置」里填一个 OpenAI 兼容接口的地址、模型名和 API Key 即可，无需适配解析器。

### 3. 导入课表

> ⚠️ **请确保您导入的课表数据为本人合法获取**（如本人登录教务系统导出的本人课表），请勿上传或分享他人数据。

**方式 A：粘贴 JSON**
1. 切换到「导入」页，默认「第 N 周导入」。
2. 把教务课表 response 粘贴到文本框。
3. 点击「开始解析」，预览无误后点击「导入到第 N 周」（仅覆盖该周）或切到「整学期导入」清空导入。

**方式 B：从 txt 文件导入**
1. 把教务 response 保存为 `.txt` 文件。
2. 在导入页点右上角「从 txt 文件导入」选择该文件，内容会自动填入文本框，后续同上。

**方式 C：批量按周导入（推荐整学期导入场景）**
1. 教务里每周各导出一份课表，分别存成 `.txt`，**文件名改成周数**（如 `1.txt` … `18.txt`）。
2. 导入页「第 N 周导入」模式下，点「选择多个 txt 文件」，长按勾选多个文件。
3. 确认弹窗会显示「将只覆盖第 1、2、… 周」，点「批量导入」即可。

### 4. 管理课程
- 课表页右下角 `+` 手动添加课程。
- **短按**课程卡片查看详情；**长按**进入编辑，可修改或删除。

## 🎨 主题预览

| 主题名称 | 说明 |
|---------|------|
| 蓝紫渐变 | 深邃星空蓝 + 梦幻紫 |
| 粉橙渐变 | 浪漫粉 + 活力橙 |
| 青绿渐变 | 清新青 + 自然绿 |
| 暖阳渐变 | 温暖黄 + 热情红 |
| 冰蓝渐变 | 冰川蓝 + 海洋蓝 |
| 深紫渐变 | 神秘紫 + 魔法粉 |

## 📝 注意事项

1. **本地解析免费离线**，无需联网、无需任何密钥；只有你主动选择「AI 整理」时才会联网，且使用你自己配置的 API Key。
2. **导入范围**：「第 N 周导入」与「批量按周导入」只覆盖指定周，不会动其它周；「整学期导入」会清空整个学期课表。
3. **txt 编码**：支持 UTF-8 与 GBK/GB18030（Windows 记事本保存的默认编码也能读）。
4. **数据本地存储**：应用数据保存在本地，卸载会丢失，请注意备份。API Key 同样仅存本机。

## 🔧 故障排除

### 问题 1: 导入时提示「无法识别为课表 JSON：请先在 SemesterJsonParser.parse() 里适配你课表的字段结构」
**原因**：项目不内置任何学校的课表格式，本地解析需要你先按自己课表 JSON 的结构填好解析器。
**解决**：参考上方「课表格式适配说明」，在 [SemesterJsonParser.kt](app/src/main/java/com/classschedule/data/parser/SemesterJsonParser.kt) 里实现 `parse()` 的解析逻辑；或改用「AI 整理」。

### 问题 2: 「第 N 周没有会上课的课程」
**原因**：解析出的课程周次不包含该周（例如课程只到第 8 周，却导入第 9 周）。请核对周次，或改用「整学期导入」。

### 问题 3: 多选文件界面无法勾选
**原因**：部分国产 ROM 的文件选择器多选入口不同。长按文件进入勾选模式，或进入后点右上角「多选」。

### 问题 4: 文件中文乱码
**原因**：文件保存成了特殊编码。应用已兼容 UTF-8 / GB18030；若仍乱码，请用记事本另存为 UTF-8。

### 问题 5: Gradle 同步失败 / 找不到 Build Tools
**解决方案**：参考 [SETUP_GUIDE.md](SETUP_GUIDE.md) 配置国内镜像与 SDK 组件。

## 📄 License

MIT License
