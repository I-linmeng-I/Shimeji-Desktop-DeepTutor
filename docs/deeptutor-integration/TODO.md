# 藤原妹红・Shimeji 桌宠集成任务清单 (TODO)

> 严格把控每个步骤独立可验证、手感优先、平稳落地。

---

## 阶段一：方案A环境搭建与本体编译启动（当前阶段 🎯）

- [ ] **Task 1.1: 准备 JDK 25 与 Maven 构建环境**
  - 为本地提供/下载免安装版 OpenJDK 25 与 Maven，或在 VS Code 任务中配置对应环境变量。
- [ ] **Task 1.2: 配置 VS Code 编译与启动 Task**
  - 在 `.vscode/tasks.json` 中配置 `build: shimeji` 与 `run: shimeji`。
  - 编译打包 `mascot-1.0.22.jar`，启动并验证原生窗口物理攀爬与退出清理。

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
