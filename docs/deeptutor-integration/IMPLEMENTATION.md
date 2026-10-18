# 藤原妹红桌宠・Shimeji 架构实现与协议对接方案

> 目标：在 Java (JDK 25) + Swing/JNA 的 Shimeji 体系中，实现网络通信管道、无焦点跟随气泡与状态调度。

---

## 一、 系统架构设计

```mermaid
graph LR
    subgraph Shimeji Desktop Process [Java JVM 进程 (Shimeji-ee)]
        A[Mascot.java<br>透明 JWindow] --> B[物理引擎与动作机<br>Action / Behavior]
        A --> C[跟随气泡窗体<br>MascotBubble.java]
        A --> D[快捷输入栏<br>MascotInput.java]
        E[DeepTutorClient.java<br>java.net.http] --> A
        D --> E
        E --> C
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

## 三、 免抢焦点气泡组件 (`MascotBubble.java`)

1. **窗体继承与属性**：
   * 继承自 `javax.swing.JWindow`；
   * 重写 `public boolean getFocusableWindowState() { return false; }`，确保操作系统**永远不转移键盘焦点**；
   * 设置 `setBackground(new Color(0, 0, 0, 0))` 实现透明画布。
2. **绘制表现**：
   * 使用 `Graphics2D` 开启 `RenderingHints.KEY_ANTIALIASING`；
   * 绘制带尾巴的圆角深色透明气泡（对齐东方和风/妹红风格），自动测算字符串多行排版高度。
3. **坐标锚定**：
   * 在 `Mascot.tick()` 中读取妹红当前屏幕坐标 `(x, y)`；
   * 动态计算气泡位置：`bubble.setLocation(x - bubble.getWidth() / 2 + anchorOffset, y - bubble.getHeight() - 10)`；
   * 当妹红爬上屏幕顶端时，气泡自动下翻到脚下。

---

## 四、 极简输入框设计（已实现：`chat/InputWindow.java`）

> 实际类名是 `InputWindow`，位于 `com.group_finity.mascot.chat`；皮肤解析在 `ChatSkin`。

* **触发**：`Ctrl + 左键`单击妹红开/关；关闭时任意左键单击也可。在 `Mascot.mousePressed` 中处理，不进入拖拽状态机。
* **切换姿态**：开窗前 `setBehavior("StandForChat")` → `tick()` → `apply()`，再 `setPaused(true)`；关窗后把原 `releaseEvent` 交回原 `Behavior.mouseReleased()`。
* **输入**：`JTextArea`，`Enter` 发送（当前仅日志），`Shift+Enter` 换行，窗口随行数无限增高。
* **按钮**：位于文本框下方，左右分组（`Align="Left"`），tooltip 文案走语言包（`<Name>Tooltip`）。

### ⚠️ 顶层窗口必须是 `JDialog` 且不透明

| 错误做法 | 后果 |
| :--- | :--- |
| `JWindow` | Windows 不激活它，永远抢不到键盘焦点 |
| `setBackground(透明)` | layered window，中文 IME 预输入被重复采样 → 条纹/四块花屏 |
| `JScrollPane` 包输入框 | 多一层透明 viewport，同样导致 IME 渲染异常 |

正确做法：`JDialog(undecorated)` + `setBackground(Panel.background)` + `setShape(RoundRectangle2D)` 做圆角裁剪 + `JTextArea.setOpaque(true)`。

