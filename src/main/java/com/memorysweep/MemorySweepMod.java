package com.memorysweep;

import com.memorysweep.command.MemorySweepCommand;
import com.memorysweep.config.MemorySweepConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * MemorySweep —— 自动清理服务器内存的 NeoForge 模组。
 *
 * <ul>
 *   <li>提供 {@code /memorysweep} 指令用于手动清理内存。</li>
 *   <li>默认每 15 分钟自动清理一次(可在配置文件中调整)。</li>
 *   <li>同时根据堆内存使用率自动清理,但同一冷却周期(默认 2 分钟)内只执行一次。</li>
 * </ul>
 */
@Mod(MemorySweepMod.MOD_ID)
public final class MemorySweepMod {

    public static final String MOD_ID = "memorysweep";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final MemoryMonitor MEMORY_MONITOR = new MemoryMonitor(LOGGER);

    // FML 会识别构造函数里的 IEventBus / ModContainer 等参数类型并自动注入
    public MemorySweepMod(IEventBus modEventBus, ModContainer modContainer) {
        // 注册配置;NeoForge 会据此自动生成/加载 config/memorysweep-server.toml
        modContainer.registerConfig(ModConfig.Type.SERVER, MemorySweepConfig.SPEC);

        // 指令注册、服务器生命周期、tick 均属于游戏事件,注册到 NeoForge.EVENT_BUS(而不是 modEventBus)
        NeoForge.EVENT_BUS.addListener(MemorySweepCommand::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);

        LOGGER.info("[MemorySweep] 模组已加载。使用 /memorysweep 手动清理内存,或编辑配置文件调整自动清理行为。");
    }

    private void onServerStarted(ServerStartedEvent event) {
        MEMORY_MONITOR.onServerStarted(event.getServer());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        MEMORY_MONITOR.onServerTick(event.getServer());
    }

    public static MemoryMonitor getMemoryMonitor() {
        return MEMORY_MONITOR;
    }
}
