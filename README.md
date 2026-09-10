# 📚 课程表 Android App

一款采用苹果「液态玻璃」设计风格的课程表应用，支持从**教务系统课表 JSON** 一键导入课表，本地离线解析、免费无依赖。

> 核心工作流：把教务系统返回的课表 `response`（JSON）**粘贴**或**存成 `.txt` 导入**，应用本地解析成课程并写入课表，按周查看。

> ⚠️ **本项目不内置任何具体学校的课表格式。** 课表解析器 [SemesterJsonParser.kt](app/src/main/java/com/classschedule/data/parser/SemesterJsonParser.kt) 是一个**空白模板**，需要你按自己学校教务系统的返回结构填好解析逻辑后才能用（见下方「学校适配说明」）。

## ✨ 功能特性

### 📥 课表导入（核心）
- **两种导入方式**：
  - **粘贴 JSON**：直接把教务课表 JSON 粘贴到文本框解析。
  - **从 txt 文件导入**：粘贴框放不下大文本时，把 response 存成 `.txt`，用右上角按钮读取（自动识别 UTF-8 / GBK 编码）。
- **两种导入模式**：
  - **第 N 周导入**（默认）：把解析结果折叠为指定周的「单周快照」，只覆盖该周，其它周不受影响。
  - **整学期导入**：按 JSON 自带的完整周次范围，清空整个学期并一次性写入。
- **批量按周导入**：把每周的 response 分别存成 `.txt`，**文件名改成周数**（如 `3.txt`、`第3周.txt`、`5周.txt`），可一次多选多个文件，批量导入多个周。
- **本地解析**（免费·离线）：在 `SemesterJsonParser` 里按你学校的格式实现解析逻辑，无需联网、无需任何密钥。

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

## 🏫 学校适配说明（必读）

课表解析器 [SemesterJsonParser.kt](app/src/main/java/com/classschedule/data/parser/SemesterJsonParser.kt) **默认是空的**，直接运行导入会在页面上提示「尚未适配你学校的课表格式」。你需要按下面步骤，把它改成适配自己学校的解析器。

### 你需要做什么

1. **拿到课表 JSON**：登录学校教务系统，打开课表页面，用浏览器 `F12 → Network` 找到返回课表数据的接口，复制其返回的 JSON（通常是 `{"data": ...}` 这样的结构）。
2. **理清结构**：梳理出「星期几 / 课程名 / 教师 / 教室 / 节次 / 周次 / 单双周」分别在 JSON 的哪个字段、以什么形式表示。
3. **改解析器**：打开 [SemesterJsonParser.kt](app/src/main/java/com/classschedule/data/parser/SemesterJsonParser.kt)，按文件里的 `TODO` 注释三步走：
   1. 在「一、原始 JSON 结构」处定义你学校的 JSON 数据类；
   2. 在「二、解析入口」的 `parse()` 里遍历并映射成课次（调用 `makeOccurrence(...)`）；
   3. 复用「三、通用工具」里的 `fromJson()` / `makeOccurrence()` / `parityText()`。
4. **编译运行**：在导入页粘贴 JSON，预览确认无误后即可导入。改好一次，其它同学也能直接用。

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
│   ├── db/
│   │   ├── AppDatabase.kt        # Room 数据库
│   │   └── Daos.kt               # DAO（含按周删除等 SQL）
│   ├── model/
│   │   └── Models.kt             # Course / Semester / PeriodConfig
│   ├── parser/
│   │   └── SemesterJsonParser.kt # 教务课表 JSON 解析器（学校格式适配模板）
│   └── repository/
│       └── Repositories.kt       # 数据仓库
├── di/
│   └── AppModule.kt              # Hilt 依赖注入
├── ui/
│   ├── components/
│   │   └── GlassComponents.kt    # 液态玻璃 UI 组件
│   ├── screens/
│   │   ├── ScheduleScreen.kt     # 课程表页面（短按信息 / 长按编辑）
│   │   ├── RecognitionScreen.kt  # 导入页面（粘贴 / 读文件 / 批量按周）
│   │   ├── SemesterScreen.kt     # 学期管理页面
│   │   ├── CourseEditScreen.kt   # 课程编辑页面
│   │   ├── PeriodConfigScreen.kt # 课时配置页面
│   │   └── SettingsScreen.kt     # 设置页面（学期/课时/主题）
│   ├── theme/
│   │   └── Theme.kt              # 主题配置
│   └── viewmodel/
│       └── ViewModels.kt         # MainViewModel（导入逻辑/周次计算等）
└── widget/
    ├── ScheduleWidget.kt         # 桌面小组件
    └── ScheduleWidgetReceiver.kt # 小组件接收器
```

## 🚀 使用说明

### 1. 创建学期（可选）
1. 进入「设置 → 学期管理」，点击「添加学期」。
2. 输入学期名称和起止日期，设为当前学期。
3. 未创建学期时，首次导入会自动创建「我的课表」。

> 设置起止日期后可自动定位当前周、在课表星期行显示真实日期；不设置也可手动翻周。

### 2. 适配学校格式（首次必做）
按上方「学校适配说明」，在 [SemesterJsonParser.kt](app/src/main/java/com/classschedule/data/parser/SemesterJsonParser.kt) 里填好你学校的课表解析逻辑并编译。

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

1. **本地解析免费离线**，无需联网、无需任何密钥。
2. **导入范围**：「第 N 周导入」与「批量按周导入」只覆盖指定周，不会动其它周；「整学期导入」会清空整个学期课表。
3. **txt 编码**：支持 UTF-8 与 GBK/GB18030（Windows 记事本保存的默认编码也能读）。
4. **数据本地存储**：应用数据保存在本地，卸载会丢失，请注意备份。

## 🔧 故障排除

### 问题 1: 导入时提示「尚未适配你学校的课表格式」
**原因**：项目不内置任何学校的课表格式，需要你先按自己学校教务返回结构填好解析器。
**解决**：参考上方「学校适配说明」，在 [SemesterJsonParser.kt](app/src/main/java/com/classschedule/data/parser/SemesterJsonParser.kt) 里实现 `parse()` 的解析逻辑。

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
