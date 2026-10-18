# Shimeji-ee 代码库架构与深度知识库

> 对应源码目录：`Shimeji-Desktop-DeepTutor/src/main/java/com/group_finity/mascot/`  
> 目标：基于成熟的 Shimeji 桌面交互与物理引擎，接入 DeepTutor 伴学助手（藤原妹红），提供架构体系、生命周期、动作状态机与窗口检测机制的知识索引。

---

## 一、 整体设计与启动链路

Shimeji-ee 采用 **Java (JDK 25+) + Swing + JNA (Java Native Access)** 构建。通过 JNA 直接调用 Windows 原生 API（User32/Gdi32）获取活动窗口边界，并通过纯透明浮动窗口（无边框 `JWindow`）进行高帧率、高质量像素渲染。

```mermaid
sequenceDiagram
    participant Main as Main.java
    participant Manager as Manager.java
    participant Mascot as Mascot.java
    participant Behavior as Behavior / Action
    participant Win32 as Native Windows (JNA)
    participant Tray as TrayMenu.java

    Main->>Main: 初始化配置、加载语言包、扫描 img/ 角色目录
    Main->>Manager: 实例化 Mascot 管理器
    Main->>Tray: 创建任务栏系统托盘 (TrayMenu)
    Main->>Manager: 创建并初始化第一只吉祥物 (createMascot)
    Manager->>Mascot: 启动 Mascot 独立物理与动作定时器 (Tick 线程)
    loop 每帧物理与动作循环
        Mascot->>Win32: JNA 获取活动窗口边界 (GetForegroundWindow / GetWindowRect)
        Mascot->>Behavior: 计算下一个动作与物理位移 (爬墙/行走/坐下/下落)
        Mascot->>Mascot: 重绘透明 JWindow 帧图像
    end
```

---

## 二、 核心源码模块与职责划分

| 源码路径 | 核心类 | 关键职责说明 | 二次开发改动关注点 |
| :--- | :--- | :--- | :--- |
| `src/.../Main.java` | `Main` | 程序主入口、环境初始化、加载行为配置与配置窗口入口 | 启动时注入 `DeepTutorClient` 并在后台建立伴学长连接 |
| `src/.../Manager.java` | `Manager` | 协调和管理桌面所有活跃 Mascot 实例、托盘联动与销毁回收 | 避免多开错乱，支持向当前主宠广播消息和控制伴学行为 |
| `src/.../Mascot.java` | `Mascot` | **核心桌宠实体**：持有透明 `JWindow`，调度物理位置、重力判定、鼠标交互（抓取、投掷） | 聊天框与气泡（`chat/ChatBubble`）跟随它，响应妹红情绪状态 |
| `src/.../action/` | `Action`, `Move`, `Stay`, `Animate` | 具体原子动作执行器（下落、攀爬、走动、坐下、抱头） | 增加「打字」、「思考/把玩火符」、「说话/讲课」动作映射 |
| `src/.../behavior/` | `Behavior`, `UserBehavior` | 行为逻辑与转移状态机（由 XML 声明驱动） | 映射 DeepTutor 的 LLM 生成状态（如 Thinking、Streaming、Idle） |
| `src/.../environment/` | `Environment`, `WindowsEnvironment` | **JNA 窗口探测层**：检测窗口标题、工作区、任务栏坐标与 Z 轴碰撞 | 提取前台进程信息（如 `Code.exe`、`Unity.exe`）用于伴学上下文感知 |
| `src/.../image/` | `ImagePair`, `ImagePairs` | 管理左右朝向的帧图片缓冲与 DPI 自适应缩放 | 妹红动作切片（东方的妹红 Shimeji 资源包直接放置在 `img/Mokou`） |
| `src/.../TrayMenu.java` | `TrayMenu` | 任务栏托盘菜单、右键选项与全部清除逻辑 | 增加“呼叫妹红”、“伴学模式开关”、“配置 DeepTutor”菜单项 |

---

## 三、 关键底层技术与二次开发优势

### 1. 为什么 Shimeji 比 eSheep 体验好得多？
* **平滑物理调度**：Shimeji 采用基于 DeltaTime 的动画帧调度，重力加速度和下落阻尼手感极佳，绝无卡顿或慢动作。
* **干净的进程与窗体生命周期**：所有 Mascot 由统一的 `Manager` 托管，退出或右键“关闭”时，统一触发 `dispose()` 和线程终止，绝不残留幽灵窗口。
* **庞大的二次元/东方素材生态**：Shimeji 拥有全球最成熟的桌宠动画标准（46张标准帧 + `actions.xml` / `behaviors.xml`）。藤原妹红作为东方知名角色，拥有大量高精度现成动作集。

---

## 四、 DeepTutor 伴学集成槽位

1. **服务单例通信层**：在 `com.group_finity.mascot.deeptutor.DeepTutorClient` 中使用现代 Java 11+ 内置的 `java.net.http.HttpClient`，连接 `http://127.0.0.1:<port>/api/partners/<partner-id>`。
   * `<port>` 由 `Settings.readBackendPort(...)` 从 `<DeepTutor目录>/data/user/settings/system.json` 的 `backend_port` 读取。
   * `<partner-id>` 由设置页的“伙伴”下拉给出（如 `partner-e67bc7f6`）。
2. **跟随气泡（已实现）**：气泡与输入框是**同一个类** `chat/ChatBubble`，构造时区分 `editing`。输出气泡会 `setFocusableWindowState(false)`，不抢键盘焦点，因此对着其他窗口打字不受影响。
   * 窗口仍是不透明表面，形状由 `BubbleShape.panel(...)` 生成后 `setShape()` 裁剪（含小尖尖）。
   * 位置更新用 `16ms` 定时器；位置与形状未变化时跳过 `setLocation` / `doLayout` / `repaint`。
3. **快捷唤出**：`Ctrl + 左键`单击妹红开/关输入框（在 `Mascot.mousePressed` 中处理，不进入拖拽状态机）；输入框开启时任意左键单击亦可关闭。
   * **未配置 DeepTutor 时完全静默**：不切姿态、不冻结、不建窗，右键菜单照常可用；菜单里的「打开聊天框」置灰。
4. **聊天框（已实现 `chat/ChatBubble`）**：
   * 皮肤放在各图像集自己的 `img/<图像集>/chat/` 下（`chat.xml` + 图标），换桌宠即换皮肤。
   * **必须是 `JDialog` 且窗口不透明**：透明顶层窗口会让中文 IME 的 composition string 重复采样而花屏；形状用 `setShape()` 裁剪。
   * 尺寸与字体由设置页控制（`ChatWidth` / `ChatFontSize`），高度随内容自动增长。
   * 输入与输出共用一套外观：回车后输入区与按钮消失、原地变成输出气泡；每个桌宠最多 2 个气泡（`BubbleStack`）。
   * 浅色模式由 `ChatLightMode` 控制，`ChatTheme` 只做黑白互换；所有图标（含关闭按钮）统一 tint 成 `ChatTheme.icon()`，所以同一批 SVG 能在两种模式下复用。
   * 附件（`AttachmentStrip`）是同一套窗口的一部分：头部一行内与关闭按钮左右对齐，图片显示缩略图、其它文件显示大写扩展名。

---

## 五、 资源目录约定

```
img/<图像集>/
    actions.xml / 动作切片...
    chat/
        chat.xml              ← 输入框与气泡共用的皮肤
        input-attach.svg      ← 18x18：添加附件
        input-send.svg        ← 18x18：发送
        input-close.svg       ← 16x16：关闭（输入框与气泡共用）
```

> `img/` 下的**每个一级子目录都会被当成一个图像集**。因此 `chat/` 必须放在图像集内部，不能放 `img/chat/`，否则启动时会多出一个名叫 chat 的桌宠。
>
> 图标优先用 SVG（用 `jsvg` 渲染，4× 超采样），png 亦可；SVG 单色即可，绘制时会被统一着色。

