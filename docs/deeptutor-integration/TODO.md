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

## 阶段二：Java 通信层集成 (`DeepTutorClient`)

- [ ] **Task 2.1: 编写 Java 原生 DeepTutorClient**
  - 实现基于 `java.net.http.HttpClient` 的异步请求与 SSE 事件流解析。
  - 编写并运行独立 main 测试，验证与藤原妹红的实时连通与流式吐字。
- [ ] **Task 2.2: 注入至 Shimeji 主控中枢**
  - 在 `Main.java` 或 `Manager.java` 中挂载通信单例。

---

## 阶段三：免焦点跟随气泡与交互输入

- [ ] **Task 3.1: 研发无焦点气泡窗体 `MascotBubble`**
  - 基于 `JWindow` + `getFocusableWindowState() == false` 保证打字光标不被打扰。
  - 实现流式打字机动画与自适应气泡尺寸。
- [ ] **Task 3.2: 研发快捷问答输入窗 `MascotInput`**
  - 双击或右键菜单触发输入，回车即发并收起。

---

## 阶段四：藤原妹红资源导入与动作机调优

- [ ] **Task 4.1: 导入藤原妹红 Shimeji 动作包**
  - 导入妹红高清切片与专属 `actions.xml` 到 `img/Mokou/`。
- [ ] **Task 4.2: 动作状态联动**
  - 思考中播放火符/托下巴动作；回复中播放比划交谈动作。
