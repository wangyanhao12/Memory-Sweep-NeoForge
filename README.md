# MemorySweep（NeoForge 版）

一个适用于 **Minecraft Java 版 26.1.2（NeoForge）** 的自动内存清理模组。

> 这是 NeoForge 版本。Fabric 版本是独立的另一份工程,两者核心逻辑(定时清理、使用率触发清理、冷却机制)完全一致,只是接入各自平台事件系统与配置系统的“胶水代码”不同。

## 功能

- **`/memorysweep`** —— 立即手动执行一次内存清理。
- **`/memorysweep status`**(附加功能)—— 查看当前堆内存使用率、下一次定时清理的倒计时等状态信息。
- **定时自动清理** —— 默认每 **15 分钟**清理一次,可在配置文件中调整。
- **使用率自动清理** —— 当堆内存使用率达到设定阈值(默认 **80%**)时自动清理,但同一冷却周期内(默认 **2 分钟**)只会执行一次,避免因内存长期处于高位而反复触发。

## ⚠️ 关于“清理内存”的实际效果,请务必了解

这个模组的清理动作,本质上是调用 Java 的 `System.gc()`,也就是向 JVM **建议**它执行一次垃圾回收(GC)。几点必须说明清楚:

1. **`System.gc()` 只是建议,不是强制命令。** 现代 JVM(尤其是默认的 G1 收集器)通常会响应并执行一次 Full GC,但如果服务器启动参数里加了 `-XX:+DisableExplicitGC`,这个调用会被直接忽略。
2. **“已用内存变少”不等于“性能变好”。** 现代垃圾回收器本身已经会在合适的时机自动回收内存;强制触发一次 Full GC 有时反而会造成短暂的卡顿(STW 停顿)。
3. 如果服务器出现持续的内存压力甚至 OOM,根本解决办法是调整 `-Xmx`、优化模组/数据包、减少加载的区块和实体数量,而不是依赖频繁强制 GC。
4. 默认定时间隔 15 分钟、使用率触发冷却 2 分钟,就是为了避免过于频繁地强制 GC 造成卡顿。

## 环境要求

| 项目 | 版本 |
|---|---|
| Minecraft | Java 版 26.1.2 |
| NeoForge | 26.1.2.87 或更新的 26.1.2.x 版本 |
| Java(运行环境) | 25 或更高 |
| Gradle(仅开发环境需要) | 9.2.1(项目自带 Wrapper,无需手动安装) |

> Minecraft 26.1 起移除了代码混淆,NeoForge 也随之简化了映射工具链,直接使用 Mojang 官方命名,并要求 Java 25。本项目基于 NeoForge 官方 `NeoForgeMDKs/MDK-26.1.2-ModDevGradle` 模板(使用 ModDevGradle 构建插件)搭建,事件注册(`RegisterCommandsEvent`、`ServerTickEvent.Post`、`ServerStartedEvent`)与配置系统(`ModConfigSpec`)均对照 NeoForge 官方源码在 `26.1.2-stable` 标签下的真实代码确认过。

## 构建方法

本项目已经包含 Gradle Wrapper,不需要本机预装 Gradle,但需要 **JDK 25**。

```bash
# Linux / macOS
./gradlew build

# Windows
gradlew.bat build
```

构建完成后,产物在 `build/libs/memorysweep-1.0.0.jar`。

> 由于本项目开发环境的网络限制,没有条件在联网的真实 Minecraft/NeoForge 环境中实际编译运行一遍。建议你在本地执行一次 `./gradlew build` 作为最终确认;如果报错,把报错信息发给我,我可以帮你快速定位修正。

### 不想在本地装 JDK 25?用 GitHub Actions 云端构建

项目里已经带了 `.github/workflows/build.yml`。新建一个 GitHub 仓库,把项目推上去,GitHub 会自动在云端完成真正的编译:

```bash
cd memorysweep-neoforge
git init
git add .
git commit -m "init"
git branch -M main
git remote add origin https://github.com/你的用户名/你的仓库名.git
git push -u origin main
```

推送后打开仓库的 **Actions** 标签页,几分钟后在对应运行记录的 **Artifacts** 里下载 `memorysweep-neoforge-jar`。

⚠️ 如果用网页拖拽上传代码而不是 `git push`,注意 `.github` 这个文件夹名带点,容易被系统文件管理器当作隐藏文件夹漏传,导致 Actions 检测不到工作流。稳妥起见,可以单独用网页的 "Add file → Create new file" 创建 `.github/workflows/build.yml` 并粘贴内容。

## 安装方法

1. 安装对应版本的 [NeoForge](https://neoforged.net/) (26.1.2 分支,版本号 26.1.2.87 或更新)。
2. 把构建出来的 `memorysweep-1.0.0.jar` 放进服务器(或客户端)的 `mods` 文件夹。
3. 启动服务器。

## 指令说明

| 指令 | 说明 | 权限要求 |
|---|---|---|
| `/memorysweep` | 立即执行一次内存清理 | 相当于原版管理员(OP)权限等级 2 及以上;命令方块同样可以执行 |
| `/memorysweep status` | 查看当前内存使用率与自动清理状态 | 同上 |

## 配置文件说明

配置使用 NeoForge 内置的 `ModConfigSpec` 系统,注册为 `ModConfig.Type.SERVER`,会自动生成**带注释**的 TOML 文件:

- 专用服务器:`config/memorysweep-server.toml`
- 单人世界:对应存档目录下的 `serverconfig/memorysweep-server.toml`

| 字段 | 默认值 | 说明 |
|---|---|---|
| `autoCleanupEnabled` | `true` | 是否启用“定时自动清理” |
| `intervalMinutes` | `15` | 定时自动清理的间隔(分钟) |
| `usageBasedCleanupEnabled` | `true` | 是否启用“根据内存使用率自动清理” |
| `memoryUsageThresholdPercent` | `80` | 触发使用率清理的堆内存占用阈值(百分比,1-99) |
| `usageCheckCooldownSeconds` | `120` | 使用率触发的清理,两次执行之间的最短间隔(秒);默认 2 分钟 |
| `usageCheckIntervalSeconds` | `5` | 后台检查内存使用率的频率(秒) |
| `broadcastToOps` | `true` | 清理后是否在聊天栏向管理员播报结果 |
| `logToConsole` | `true` | 清理后是否在服务器控制台/日志中输出结果 |

与 Fabric 版不同,这份配置文件由游戏自动管理:游戏内修改后可以直接重新加载(NeoForge 的 `SERVER` 类型配置支持热重载),不一定需要重启服务器。文件里每一项也都带有中文注释,直接打开编辑即可。

## 项目结构

```
memorysweep-neoforge/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat / gradle/wrapper/...
├── .github/workflows/build.yml
├── LICENSE
├── README.md
└── src/main/
    ├── java/com/memorysweep/
    │   ├── MemorySweepMod.java        # 模组入口(@Mod),注册事件与配置
    │   ├── MemoryMonitor.java         # 核心逻辑:定时清理 + 使用率触发清理(含冷却)
    │   ├── command/MemorySweepCommand.java  # /memorysweep 指令(RegisterCommandsEvent)
    │   └── config/MemorySweepConfig.java    # ModConfigSpec 配置定义
    └── templates/META-INF/
        └── neoforge.mods.toml          # 模组元数据模板
```

## 个性化

发布前建议编辑 `src/main/templates/META-INF/neoforge.mods.toml` 里的 `authors` 字段,填上你自己的名字。
