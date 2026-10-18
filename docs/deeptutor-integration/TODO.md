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

- `Settings.readBackendPort(Path)`：逐行读 `<目录>/data/user/settings/system.json`，正则取 `"backend_port": <n>`，读不到返回 `0`。**不要硬编码 8001**。
- `SettingsWindow`：第 5 个页签，用 `BoxLayout(PAGE_AXIS)` + 末尾 `VerticalGlue` 贴顶排列（与「关于」页一致）。
  - DeepTutor 目录（文本框 + 浏览）、伙伴（下拉 + 刷新）、服务端口（只读标签）、聊天框宽度、聊天字号。
  - 伙伴扫描 `data/partners/partner-*/config.yaml`，只读顶层 `name:` 行做显示名，保存的是目录名（如 `partner-e67bc7f6`），跳过 `_runtime`。
- 新文案全部走语言包：`DeepTutor`、`DeepTutorDirectory`、`Browse`、`Partner`、`Refresh`、`BackendPort`、`ChatWidth`、`ChatFontSize`、`AttachTooltip`、`SendTooltip`、`ShowChatInput`。

### Task 1.5.3 聊天输入框（仅绘制与交互，未接网络）

资源与皮肤：

```
img/Shimeji/chat/          ← 每个图像集各自一套，不能放在 img/ 根目录（会被当成图像集）
    chat.xml
    input-attach.png
    input-send.png
```

`chat.xml` 格式：

```xml
<Chat>
    <Input Width="360" Height="60" CornerRadius="14" BorderWidth="1" Padding="8,10,8,10">
        <Button Name="Attach" Image="/chat/input-attach.png" Size="18,18" Align="Left"/>
        <Button Name="Send"   Image="/chat/input-send.png"   Size="18,18"/>
    </Input>
</Chat>
```

- `chat/ChatSkin.java`：解析该 xml，持有宽高/圆角/边框/内边距/按钮（含 `leftAligned`）。按图像集分别缓存。
- `chat/InputWindow.java`：`JDialog`（**不能用 `JWindow`**，它抢不到焦点）。

交互约定：

| 操作 | 结果 |
| :--- | :--- |
| **Ctrl + 左键**单击桌宠 | 打开/关闭聊天框（在 `mousePressed` 里处理，不进拖拽流程） |
| 关闭聊天框时的**任意左键**单击桌宠 | 关闭聊天框（在 `mousePressed` 的 `else` 分支里，判 `BUTTON1` 后 `return`） |
| Enter | 发送（目前只 `log.info`） |
| Shift+Enter | 换行，窗口无限增高 |

- 打开时：先 `setBehavior("StandForChat")` → `tick()` → `apply()` → 再 `setPaused(true)`，否则姿态不会刷新，且空中会因 `LostGroundException` 掉回 `Fall`。
- 关闭时：`setPaused(false)` → 把当初的 `releaseEvent` 交回原 `Behavior.mouseReleased()`，桌宠从冻结位置继续下落。
- `conf/actions.xml` 新增 `StandForChat`（`Type="Stay"`，**不带 `BorderType`**，任何位置都能保持），`conf/behaviors.xml` 新增同名 `Behavior`（`Frequency="0" Hidden="true"`）。

### ⚠️ IME 花屏的三条硬约束（踩过坑，别回退）

中文输入法（微软拼音）在输入框里出现条纹/四块重影，根因与文本框透明度无关，而是**顶层窗口的 per-pixel alpha**。

1. **顶层窗口不能透明**：`setBackground(new Color(0,0,0,0))` 会让窗口变成 layered window，IME 的 composition string 由 TSF 直接画在 surface 上，DWM 合成时会重复采样。
   → 改用 `setBackground(UIManager.getColor("Panel.background"))` + `setShape(new RoundRectangle2D...)` 做圆角裁剪。
2. **`JTextArea` 必须不透明**，否则 IME 没有实心绘制面。
3. **不要用 `JScrollPane` 包输入框**：多一层透明 viewport，还会把 IME 预输入挤进额外绘制层。窗口靠 `resizeToFitText()` 自己长高。

其它注意事项：

- `JTextArea` 默认没有 `Shift+Enter` 绑定，必须显式绑 `DefaultEditorKit.insertBreakAction`。
- Tooltip 必须 `ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false)`，否则会被小窗口裁掉。
- 按钮放在文本框**下方**（`BorderLayout.SOUTH`），放两侧会随窗口长高而垂直居中。

---

## 阶段二：Java 通信层集成 (`DeepTutorClient`)

- [ ] **Task 2.1: 编写 Java 原生 DeepTutorClient**
  - 实现基于 `java.net.http.HttpClient` 的异步请求与 SSE 事件流解析。
  - 端口从 `Settings.readBackendPort(...)` 取，不要写死。
  - 编写并运行独立 main 测试，验证与藤原妹红的实时连通与流式吐字。
- [ ] **Task 2.2: 注入至 Shimeji 主控中枢**
  - 在 `Main.java` 或 `Manager.java` 中挂载通信单例。
  - 把 `InputWindow.send()` 从 `log.info` 换成真实调用。

---

## 阶段三：免焦点跟随气泡

- [ ] **Task 3.1: 研发无焦点气泡窗体 `MascotBubble`**
  - 基于 `JWindow` + `getFocusableWindowState() == false` 保证打字光标不被打扰（气泡只读，不需要 IME）。
  - 实现流式打字机动画与自适应气泡尺寸。
  - 皮肤同样放在各图像集的 `chat/` 目录下（`chat.xml` 增加 `<Bubble>` 节点）。

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

