# 藤原妹红・DeepTutor 伴学桌宠需求规格说明书 (PRD)

> 版本：v2.0.0 (基于 Shimeji-Desktop 架构)
> 目标伙伴：藤原妹红 (`partner-e67bc7f6`)，对齐 `DeepTutor/data/partners/partner-e67bc7f6/workspace/user/workspace/SOUL.md` 与人设记忆

---

## 一、 背景与产品定位

* **痛点**：DeepTutor 浏览器交互模式无法做到同屏沉浸式陪伴，在写 Shader 或调试图形学代码时频繁切屏降低效率。
* **定位**：打造一个**原生流畅、动作丰富、具备真实桌面物理（爬墙/窗口吸附/摇腿/投掷）、打通 DeepTutor 伴学问答**的藤原妹红桌宠。
* **人设**：活了千三百年的不死者。说话不摆架子、豪爽护短、奔着解决眼前问题讲手艺。

---

## 二、 核心功能需求

### 1. 桌面交互与随身伴学 (Chat & Companion)

* **FR-1.1 免抢焦点跟随气泡 (Companion Floating Speech Bubble)**：
  * 妹红说话时，头顶弹出精致圆角对话气泡。
  * 必须使用 `getFocusableWindowState() == false`，**打死不抢占用户在 VS Code 敲代码的输入光标**。
  * 支持打字机效果流式输出（对接 DeepTutor SSE `content` 事件）。
  * 气泡带倒计时自动隐退，点击可锁定或复制回答。
* **FR-1.2 极简唤出问答输入框 (Quick Launcher)**：
  * 双击桌宠本体，或按下全局热键（如 `Alt + Space`），就地弹出轻量单行输入栏。
  * 按 `Enter` 即刻发送并弹出气泡打字，按 `Esc` 迅速收起。
* **FR-1.3 情绪与动作联动 (Action & Mood Mapping)**：
  * **思考中 (Thinking)**：妹红进入“思考/手中捏着火符玩”动作。
  * **回复中 (Speaking)**：头顶气泡冒出，播放“交谈比划”动作。
  * **闲置与陪伴 (Idle)**：在活跃窗口边缘走动、坐着晃腿、爬墙。
  * **主动关怀 (Care)**：检测到用户长时间停留在 GLSL/Unity 窗口或深夜时，主动弹气泡问候。

### 2. DeepTutor 深度对接能力 (Engine Integration)

* **FR-2.1 服务发现与健康检查**：
  * 启动时连接 `http://127.0.0.1:8001/api/system/status`，自动检查服务在线与伙伴配置。

---

## 三、 非功能性需求 (NFR)

1. **帧率与流畅度**：物理和渲染维持 30~60 FPS 平滑插值，彻底摒弃旧版卡顿体验。
2. **退出完整性**：右键托盘“退出”或“散开全部”时，所有窗口和后台守护线程必须立即被干净释放，无残留进程。
3. **东方资源无缝兼容**：保持对现有标准 Shimeji 东方妹红动作包的 100% 格式兼容（直接放进 `img/Mokou` 即可加载）。
