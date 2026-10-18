# 藤原妹红・Shimeji 桌宠集成任务清单 (TODO)

> 严格把控每个步骤独立可验证、手感优先、平稳落地。

---

## 阶段一：方案A环境搭建与本体编译启动（已完成 ✅）

- [x] **Task 1.1: 准备 JDK 25 与 Maven 构建环境**
  - 通过 `winget install Microsoft.OpenJDK.25` 全局安装 Microsoft OpenJDK **25.0.4.1**（安装至 `C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot`）。
  - 全局安装 Apache Maven **3.9.9**（`C:\Users\linmeng\AppData\Local\Programs\apache-maven-3.9.9`）。
  - 已写入用户级环境变量：`JAVA_HOME` 指向 JDK 25，`Path` 追加 `%JAVA_HOME%\bin` 与 Maven `bin`。
- [x] **Task 1.2: 配置构建与运行方式**
  - 新增 `scripts/build.ps1`（自动定位 JDK 25 + Maven，执行 `mvn package -DskipTests`，随后把发布 zip 解压到 `dist/`）。
  - `.vscode/tasks.json` 只保留 `build: shimeji`。
  - **构建产物**：`target/Shimeji-ee.jar`、`target/Shimeji-ee.exe`、`target/Shimeji-ee_1.0.22.zip`，以及可直接运行的 `dist/`。
  - **验证结果**：`mvn package` BUILD SUCCESS；`dist/Shimeji-ee.exe` 启动后 `javaw.exe` 稳定驻留，日志确认 mascot 创建、`Fall → WalkLeftAndSit` 行为链正常。

### ⚠️ 运行方式的关键坑（务必记住）

| 路径 | 能否直接跑 | 原因 |
| :--- | :--- | :--- |
| `dist\Shimeji-ee.exe` | ✅ **可以，双击即可** | `dist/` 里同时有 `conf/`、`img/`、`lib/`、`Shimeji-ee.jar` |
| `target\Shimeji-ee.exe` | ❌ **不行**（进程秒退、退出码 0、无任何提示） | launch4j 启动器把工作目录固定为 exe 所在目录，而 `target/` 里没有 `conf/` 与 `img/`；程序按相对路径读 `conf\actions.xml`、`img\Shimeji\` |
| `java -jar target\Shimeji-ee.jar`（在仓库根目录） | ✅ 可以 | 仓库根目录有 `conf/` 与 `img/`，且 jar 的 `Class-Path` 相对 `target/lib/` 能解析 |

因此：
- **日常使用**：双击 `dist\Shimeji-ee.exe`。
- **改完代码**：先跑 `build: shimeji`（会重新生成并解压 `dist/`），再双击 `dist\Shimeji-ee.exe`。
- 运行中的 `dist\` 被 exe 占用时构建会跳过解压并给出警告，需先退出桌宠。
- 环境要求：`JAVA_HOME` 必须指向 JDK 25（系统里还有旧 Java 8，launch4j 的 `minVersion` 是 25）。

---

## 阶段一·五：本体改造（已完成 ✅）

> 本阶段全部已在代码中落地并验证，下面的记录是**现状说明**，不是待办。

### Task 1.5.1 跳过桌宠选择器，默认加载第一个

- `Main.java`：`configurationLoadLoop()` 重写为 `loadConfigurations()`，不再弹 `ImageSetChooser`。
  - `activeImageSets` 为空时，用 `Files.list(IMAGE_DIRECTORY)` 取第一个非 `unused`、非 `.` 开头的目录。
  - `reloadAllImageSets()` / `setActiveImageSets()` 里的兜底也改成直接调用它。
- `Main.java` 里 `ImageSetChooser` 的 import 已删除；类本身保留，托盘菜单仍可用。

### Task 1.5.2 设置窗口新增 DeepTutor 页签

- `Settings.java` 新增字段与持久化键：

  | 字段 | settings.properties 键 | 默认值 |
  | :--- | :--- | :--- |
  | `deepTutorDirectory` | `DeepTutorDirectory` | `""` |
  | `deepTutorPartner` | `DeepTutorPartner` | `""` |
  | `chatWidth` | `ChatWidth` | `360` |
  | `chatFontSize` | `ChatFontSize` | `13` |
  | `chatLightMode` | `ChatLightMode` | `false` |

- `Settings.readBackendPort(Path)`：逐行读 `<目录>/data/user/settings/system.json`，正则取 `"backend_port": <n>`，读不到返回 `0`。**不要硬编码 8001**。
- `Settings.isDeepTutorConfigured()`：目录存在 + 已选伙伴 + 端口可读，三者同时满足才允许唤出聊天框。
- `SettingsWindow`：第 5 个页签，用 `BoxLayout(PAGE_AXIS)` + 末尾 `VerticalGlue` 贴顶排列（与「关于」页一致）。
  - DeepTutor 目录（文本框 + 浏览）、伙伴（下拉 + 刷新）、服务端口（只读标签）、聊天框宽度、聊天字号、浅色模式。
  - 伙伴扫描 `data/partners/partner-*/config.yaml`，只读顶层 `name:` 行做显示名，保存的是目录名（如 `partner-e67bc7f6`），跳过 `_runtime`。
  - 「浏览」走 `chat/FileDialogs.chooseDirectory(...)`，见 Task 1.5.3。
- 新文案全部走语言包：`DeepTutor`、`DeepTutorDirectory`、`Browse`、`Partner`、`Refresh`、`BackendPort`、`ChatWidth`、`ChatFontSize`、`ChatLightMode`、`AttachTooltip`、`SendTooltip`、`ShowChatInput`、`Close`、`PastedImage`、`ConfigureDeepTutorFirst`、`DeepTutorNotConnected`。

### Task 1.5.3 聊天框与气泡（同一套窗口，已实现）

> **输入框和输出气泡是同一个类、同一套皮肤、同一个窗口形状**：输入时是可编辑面板，回车后原地变成只读输出气泡。

资源与皮肤（按图像集各自一套）：

```
img/Shimeji/chat/          ← 不能放在 img/ 根目录（会被当成图像集）
    chat.xml
    input-attach.svg
    input-send.svg
    input-close.svg
```

`chat.xml` 格式：

```xml
<Chat>
    <Input Width="360" Height="60" CornerRadius="14" BorderWidth="2" Padding="8,10,8,10">
        <Button Name="Attach" Image="/chat/input-attach.svg" Size="18,18" Align="Left"/>
        <Button Name="Send"   Image="/chat/input-send.svg"   Size="18,18"/>
        <Button Name="Close"  Image="/chat/input-close.svg"  Size="16,16"/>
    </Input>
    <Bubble MaxWidth="340" CornerRadius="18" BorderWidth="2" Padding="10,16,10,16"
            TailWidth="24" TailHeight="12"/>
</Chat>
```

- **图标一律用 SVG**（也兼容 png）。`ChatIcons` 用 `jsvg` 按 `Size` 4× 超采样渲染再缩到目标尺寸，边缘不糊；SVG 单色即可，颜色由主题覆盖。
- 所有图标（Attach / Send / Close）统一 tint 成 `ChatTheme.icon()`（= 前景色），保证同一份 SVG 在深/浅两种模式下都清晰且颜色一致。
- `Close` 不是画出来的 `×`，而是和另外两个按钮一样来自皮肤；皮肤里缺 `Close` 时才回退成绘制字符。
- `<Bubble>` 可选，缺失时用 `ChatSkin.Bubble.DEFAULT`。皮肤文件缺失或损坏时用 `ChatSkin.fallback()`，**不抛异常**。

模块拆分（`com.group_finity.mascot.chat`）：

| 类 | 职责 |
| :--- | :--- |
| `ChatBubble` | 窗口本身：布局、输入、发送、回显、跟随定时器、按皮肤摆按钮 |
| `ChatSkin` | 解析 `chat.xml`，按钮列表、几何、`Bubble`，按图像集缓存（`forImageSet`） |
| `ChatTheme` | 深/浅两套色板；`ChatLightMode` 只做黑白互换 |
| `ChatIcons` | SVG / 位图加载、超采样、缩放、着色 |
| `AttachmentStrip` | 附件模型、chip 自绘、文件与剪贴板读取、缩略图、换行高度 |
| `BubbleStack` | 每个桌宠的气泡列表、最多 2 个的淘汰、上下堆叠与屏幕钳制 |
| `BubbleShape` | 圆角本体 + 小尖尖合并为单个 `Area`，绘制与 `setShape` 共用 |
| `FileDialogs` | 系统原生文件/目录对话框 |

交互约定：

| 操作 | 结果 |
| :--- | :--- |
| **Ctrl + 左键**单击桌宠 | 打开/关闭输入框（在 `mousePressed` 里处理，不进拖拽流程） |
| 输入框开启时**任意左键**单击桌宠 | 关闭输入框 |
| **Enter** | 发送：当前输入框**原地变成输出气泡**（输入区、按钮全部消失） |
| **Shift+Enter** | 换行，窗口无限增高 |
| 点 **Attach** | 系统文件对话框（多选），加入附件 |
| **Ctrl+V** | 剪贴板有文件/图片就加附件，否则按普通文本粘贴 |
| 气泡上的 **×** | 关闭该气泡（输入框关闭时会解冻桌宠） |

- 每个桌宠**最多同时存在 2 个气泡**（1 个输入 + 最多 1 个历史输出）。再唤出输入框时，最旧的先关掉。
- 发送后解除冻结：`setPaused(false)` → 把当初的 `releaseEvent` 交回原 `Behavior.mouseReleased()`，桌宠继续下落。
- 打开时：先 `setBehavior("StandForChat")` → `tick()` → `apply()` → 再 `setPaused(true)`，否则姿态不会刷新，且空中会因 `LostGroundException` 掉回 `Fall`。
- **未配置 DeepTutor 时 Ctrl+左键完全静默**（不切姿态、不冻结、不建窗）；右键菜单的「打开聊天框」置灰并带提示。`openChat()` 内部再查一次，防止从菜单绕过。
- 目前**未接网络**：`send()` 只写日志，并用输入内容自己回显成输出气泡，方便先验 UI。

附件（`AttachmentStrip`）：

- 位于**头部同一行**，与关闭按钮左右两端对齐，头部整体距上边框 `HEADER_TOP_INSET = 8`。
- 每个 chip 完全自绘：`文件名（超长省略…）` + `右槽` + `×`。右槽是**图片缩略图**（14px）或**大写扩展名**（`JAVA`、`PY`）。
- 每行至少容纳 **3 个** chip；超出换行，行高计入窗口高度。
- 粘贴的图片命名为「粘贴的图片 N」，显示缩略图，不显示扩展名。

### ⚠️ IME 花屏的三条硬约束（踩过坑，别回退）

中文输入法（微软拼音）在输入框里出现条纹/四块重影，根因与文本框透明度无关，而是**顶层窗口的 per-pixel alpha**。

1. **顶层窗口不能透明**：`setBackground(new Color(0,0,0,0))` 会让窗口变成 layered window，IME 的 composition string 由 TSF 直接画在 surface 上，DWM 合成时会重复采样。
   → 改用 `setBackground(ChatTheme.background())` + `setShape(BubbleShape.panel(...))` 做形状裁剪。
2. **`JTextArea` 必须不透明**，否则 IME 没有实心绘制面。
3. **不要用 `JScrollPane` 包输入框**：多一层透明 viewport，还会把 IME 预输入挤进额外绘制层。窗口靠 `resizeToFitText()` 自己长高。

其它注意事项：

- `JTextArea` 默认没有 `Shift+Enter` 绑定，必须显式绑 `DefaultEditorKit.insertBreakAction`。
- Tooltip 必须 `ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false)`，否则会被小窗口裁掉。
- 按钮放在文本框**下方**（`BorderLayout.SOUTH`），放两侧会随窗口长高而垂直居中。
- 附件 chip 不要用 `JLabel` + `JButton` 拼：`FlowLayout` 会把下边框裁掉、删除按钮会被挤没。整块自绘（`AttachmentStrip.ChipView`）。
- 气泡跟随用 `16ms` 定时器，并在位置/形状未变时跳过 `setLocation`、`doLayout`、`repaint`，否则会明显滞后且空转重绘。

---

## 阶段二：Java 通信层集成 (`DeepTutorClient`)

- [ ] **Task 2.1: 编写 Java 原生 DeepTutorClient**
  - 实现基于 `java.net.http.HttpClient` 的异步请求与 SSE 事件流解析。
  - 端口从 `Settings.readBackendPort(...)` 取，不要写死。
  - 编写并运行独立 main 测试，验证与藤原妹红的实时连通与流式吐字。
- [ ] **Task 2.2: 注入至 Shimeji 主控中枢**
  - 在 `Main.java` 或 `Manager.java` 中挂载通信单例。
  - 把 `ChatBubble.send()` 从「日志 + 原样回显」换成真实调用（现在搜索 `Sending message:` 一处就能找到）。
  - 注意：输出气泡目前是**不可聚焦**的只读窗口，流式吐字直接反复调用 `setMessage()` 即可。

---

## 阶段三：免焦点跟随气泡（已完成 ✅）

- [x] **Task 3.1: 气泡窗体**
  - 气泡与输入框是**同一个类** `ChatBubble`，只在构造时区分 `editing`；输出气泡调 `setFocusableWindowState(false)`，不抢键盘焦点。
  - 输入框回车后**原地变成输出气泡**，输入内容和按钮全部消失，不再新建窗口。
  - 每个桌宠最多 2 个气泡（`BubbleStack.MAX_BUBBLES`），再唤出输入框时淘汰最旧的。
  - 皮肤放在各图像集的 `chat/chat.xml`，`<Bubble>` 节点控制最大宽度、圆角、描边、内边距、小尖尖尺寸。
  - 小尖尖与本体合并成一个 `Area`（`BubbleShape`），描边完整、没有接缝，`setShape` 用同一路径裁剪窗口。
- [ ] **Task 3.2: 流式打字机动画**
  - 现在是一次性 `setMessage()`。接上 SSE 后改成逐段追加，并在每次追加后调用 `sizeToFitMessage()` + `BubbleStack.repositionAll()`。

---

## 阶段四：藤原妹红资源导入与动作机调优

- [ ] **Task 4.1: 导入藤原妹红 Shimeji 动作包**
  - 导入妹红高清切片与专属 `actions.xml` 到 `img/Mokou/`。
- [ ] **Task 4.2: 动作状态联动**
  - 思考中播放火符/托下巴动作；回复中播放比划交谈动作。

---

## 下一轮从这里开始

1. 在**新会话**里先读本节 + 上面「阶段一·五」现状，再动手。
2. 第一件事：`com.group_finity.mascot.deeptutor.DeepTutorClient`（健康探针 + SSE）。
3. 改完代码记得跑 `build: shimeji`；`dist/` 被占用时先关掉桌宠。

