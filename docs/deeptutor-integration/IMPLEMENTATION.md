# 藤原妹红桌宠・Shimeji 架构实现与协议对接方案

> 目标：在 Java (JDK 25) + Swing/JNA 的 Shimeji 体系中，实现网络通信管道、无焦点跟随气泡与状态调度。

---

## 一、 系统架构设计

```mermaid
graph LR
    subgraph Shimeji Desktop Process [Java JVM 进程 (Shimeji-ee)]
        A[Mascot.java<br>透明 JWindow] --> B[物理引擎与动作机<br>Action / Behavior]
        A --> C[聊天框 / 气泡<br>ChatBubble.java]
        C --> CS[BubbleStack<br>最多 2 个，堆叠]
        C --> AT[AttachmentStrip<br>附件 chip]
        E[DeepTutorClient.java<br>java.net.http] --> C
        C --> E
    end

    subgraph DeepTutor Backend [本地服务 (Python FastAPI)]
        F[API 路由: /api/partners]<br>
        G[藤原妹红 Partner 实例: partner-e67bc7f6]
        H[SOUL & 记忆系统: L3 / 妹红人设]
        F --> G
        G --> H
    end

    E <==>|HTTP POST / SSE 流式事件| F
```

---

## 二、 核心通信实现设计 (`DeepTutorClient.java`)

在包 `com.group_finity.mascot.deeptutor` 下构建轻量客户端：

* **底层网络**：直接使用 Java 11+ 内置的 `java.net.http.HttpClient`，无需引入沉重的外部网络库。
* **地址来源**（不要写死）：
  * 端口：`Settings.readBackendPort(Path.of(settings.deepTutorDirectory))` 读 `<目录>/data/user/settings/system.json` 的 `backend_port`。
  * 伙伴 ID：`settings.deepTutorPartner`（设置页下拉选出的目录名，如 `partner-e67bc7f6`）。
* **协议与端点**（`http://127.0.0.1:<port>`）：
  * 健康探针：`GET /api/system/status`
  * 伙伴信息：`GET /api/partners/<partner-id>`
  * 流式对话：`POST /api/partners/<partner-id>/chat/execute-stream`
* **SSE 解析器**：
  * 读取 `HttpResponse<InputStream>` 流式行，按 `event:` 与 `data:` 进行正则/简单切分。
  * 提取 `content`（妹红流式输出内容）与 `thinking`（思考内容）。
  * 通过回调接口 `DeepTutorListener` 派发至 UI 线程 (`SwingUtilities.invokeLater`)。

---

## 三、 跟随气泡（已实现：`chat/ChatBubble.java`）

> 气泡与输入框是**同一个类**，构造时用 `editing` 区分；不要再去新建 `MascotBubble`。

1. **窗体属性**：
   * 继承 `JDialog`（无装饰）。输出气泡调 `setFocusableWindowState(false)`，所以只会显示、不会抢键盘焦点。
   * 窗口表面**保持不透明**，否则中文 IME 花屏（见第四节）；形状用 `setShape(BubbleShape.panel(...))` 裁剪。
2. **绘制**：
   * `BubbleShape` 把圆角本体与小尖尖合并成一个 `Area`，填充与描边用同一路径，描边四周完整、尖尖根部没有接缝。
   * 颜色来自 `ChatTheme`（深/浅两套），文字高度自动测算，宽度上限来自 `chat.xml` 的 `<Bubble MaxWidth>`。
3. **坐标锚定**：
   * `BubbleStack.repositionAll(mascot)` 统一堆叠：最新气泡最靠桌宠，空间不够时整体翻到脚下。
   * 跟随定时器 `16ms`；位置未变不调 `setLocation`，形状未变不重排不重绘。
   * 每个桌宠最多 `BubbleStack.MAX_BUBBLES = 2` 个气泡，新生时淘汰最旧的。

---

## 四、 聊天框（已实现：`chat/ChatBubble.java` + `chat/ChatSkin.java`）

* **触发**：`Ctrl + 左键`单击妹红开/关；输入框开启时任意左键单击也可关闭。在 `Mascot.mousePressed` 中处理，不进入拖拽状态机。
* **未配置 DeepTutor 时完全静默**：`Settings.isDeepTutorConfigured()` 不成立就直接 return，不切姿态、不冻结；右键菜单的「打开聊天框」置灰。
* **发送**：`Enter` 发送后，当前输入框**原地变成输出气泡**（输入区与按钮消失），不再新建窗口；`Shift+Enter` 换行。
* **切换姿态**：开窗前 `setBehavior("StandForChat")` → `tick()` → `apply()`，再 `setPaused(true)`；发送或关窗后把原 `releaseEvent` 交回原 `Behavior.mouseReleased()`。
* **按钮**：位于文本框下方，左右分组（`Align="Left"`）；图标来自皮肤，SVG 可用，统一 tint 成 `ChatTheme.icon()`。
* **浅色模式**：设置页 `ChatLightMode`，只做背景/前景互换。

### ⚠️ 顶层窗口必须是 `JDialog` 且不透明

| 错误做法 | 后果 |
| :--- | :--- |
| `JWindow` | Windows 不激活它，永远抢不到键盘焦点 |
| `setBackground(透明)` | layered window，中文 IME 预输入被重复采样 → 条纹/四块花屏 |
| `JScrollPane` 包输入框 | 多一层透明 viewport，同样导致 IME 渲染异常 |

正确做法：`JDialog(undecorated)` + `setBackground(ChatTheme.background())` + `setShape(BubbleShape.panel(...))` 做形状裁剪 + `JTextArea.setOpaque(true)`。

---

## 五、 附件与系统对话框

* **附件栏** `chat/AttachmentStrip.java`：位于头部同一行（与关闭按钮左右对齐），每个 chip 完全自绘 —— `文件名（超长省略…）` + `右槽` + `×`，右槽是图片缩略图或大写扩展名；每行至少容纳 3 个。
* **读取来源**：`Attach` 按钮 → `FileDialogs.chooseFiles(...)`；`Ctrl+V` → 先看剪贴板文件列表/图片，都没有才当普通文本粘贴。
* **系统原生对话框** `chat/FileDialogs.java`：Swing 自带的 `JFileChooser` 是老式界面，所以改为调用 PowerShell（`pwsh` → `powershell`）弹 WinForms 的 `OpenFileDialog` / `FolderBrowserDialog`；都不可用时才退回 `JFileChooser`，保证不会点不动。设置页的 DeepTutor 目录选择也走这里。

---

## 六、 新增依赖与构建

| 依赖 | 版本 | 用途 |
| :--- | :--- | :--- |
| `com.github.weisj:jsvg` | `1.6.0` | 渲染 `chat/` 下的 SVG 图标 |

- `pom.xml` 加依赖后，`module-info.java` 必须同步加 `requires com.github.weisj.jsvg;`，否则模块路径编译失败。
- `target/lib/` 会多出 `jsvg-1.6.0.jar`；`dist/` 由 `build: shimeji` 自动同步。
- 构建期间如果 `dist/` 被运行中的桌宠占用，脚本会跳过解压并给出警告，先退出桌宠再构建。

