package com.memorysweep;

import com.memorysweep.config.MemorySweepConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * 内存监控与清理的核心逻辑。
 *
 * <p>本类只会在服务器主线程(tick 线程)上被访问 —— {@link #onServerTick} 由
 * {@code ServerTickEvent.Post} 驱动,指令执行也运行在同一线程上,因此这里不需要
 * 任何额外的同步处理。</p>
 *
 * <p>支持三种触发清理的方式:</p>
 * <ul>
 *   <li>手动:玩家/控制台执行 {@code /memorysweep}</li>
 *   <li>定时:每隔 {@code intervalMinutes} 分钟(默认 15 分钟)清理一次</li>
 *   <li>使用率:堆内存占用达到 {@code memoryUsageThresholdPercent}(默认 80%)时清理,
 *       但距离上一次清理(无论是何种触发方式)不足 {@code usageCheckCooldownSeconds}
 *       (默认 120 秒,即 2 分钟)时不会重复触发。</li>
 * </ul>
 */
public final class MemoryMonitor {

    /** 清理的触发原因,用于日志/聊天提示文本。 */
    public enum CleanupReason {
        MANUAL("手动清理"),
        SCHEDULED("定时自动清理"),
        USAGE_TRIGGERED("内存使用率触发清理");

        private final String label;

        CleanupReason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** 一次清理执行前后的内存快照与结果,用于生成提示文本。 */
    public record CleanupResult(long beforeUsedBytes, long afterUsedBytes, long maxBytes, long durationMillis,
            CleanupReason reason) {

        public long freedBytes() {
            return Math.max(0L, beforeUsedBytes - afterUsedBytes);
        }

        public double beforePercent() {
            return maxBytes <= 0 ? 0.0 : (beforeUsedBytes * 100.0) / maxBytes;
        }

        public double afterPercent() {
            return maxBytes <= 0 ? 0.0 : (afterUsedBytes * 100.0) / maxBytes;
        }

        public String toLogText() {
            return String.format(Locale.ROOT,
                    "%s完成 | 清理前 %d MB (%.1f%%) -> 清理后 %d MB (%.1f%%) | 释放约 %d MB | 耗时 %d ms",
                    reason.label(), toMb(beforeUsedBytes), beforePercent(), toMb(afterUsedBytes), afterPercent(),
                    toMb(freedBytes()), durationMillis);
        }

        public String toChatText() {
            return String.format(Locale.ROOT, "[内存清理] %s | %d MB -> %d MB | 释放约 %d MB | 耗时 %d ms",
                    reason.label(), toMb(beforeUsedBytes), toMb(afterUsedBytes), toMb(freedBytes()), durationMillis);
        }

        private static long toMb(long bytes) {
            return bytes / (1024L * 1024L);
        }
    }

    private final Logger logger;

    private int tickCounter = 0;
    private long lastCleanupTimeMillis = 0L;
    private long lastScheduledCleanupTimeMillis = 0L;
    private long lastUsageCheckTimeMillis = 0L;

    public MemoryMonitor(Logger logger) {
        this.logger = logger;
    }

    /**
     * 服务器启动完成后调用一次,重置所有计时器,让定时清理从“服务器真正开始运行”那一刻起算。
     */
    public void onServerStarted(MinecraftServer server) {
        long now = System.currentTimeMillis();
        this.lastScheduledCleanupTimeMillis = now;
        this.lastCleanupTimeMillis = 0L; // 允许使用率触发的清理在启动后立刻可用,不受冷却限制
        this.lastUsageCheckTimeMillis = 0L;
        this.tickCounter = 0;

        if (MemorySweepConfig.LOG_TO_CONSOLE.get()) {
            logger.info(
                    "[MemorySweep] 内存监控已启动 | 定时清理: {} | 使用率触发清理: {}(阈值 {}%,冷却 {} 秒)",
                    MemorySweepConfig.AUTO_CLEANUP_ENABLED.get()
                            ? ("每 " + MemorySweepConfig.INTERVAL_MINUTES.get() + " 分钟一次")
                            : "已禁用",
                    MemorySweepConfig.USAGE_BASED_CLEANUP_ENABLED.get() ? "已启用" : "已禁用",
                    MemorySweepConfig.MEMORY_USAGE_THRESHOLD_PERCENT.get(),
                    MemorySweepConfig.USAGE_CHECK_COOLDOWN_SECONDS.get());
        }
    }

    /**
     * 每个服务器 tick 调用一次。内部按秒节流,避免每 tick 都做时间/内存运算。
     */
    public void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter < 20) { // 20 tick ≈ 1 秒(服务器满速运行时)
            return;
        }
        tickCounter = 0;

        long now = System.currentTimeMillis();

        if (MemorySweepConfig.AUTO_CLEANUP_ENABLED.get()) {
            long intervalMillis = MemorySweepConfig.INTERVAL_MINUTES.get() * 60_000L;
            if (now - lastScheduledCleanupTimeMillis >= intervalMillis) {
                lastScheduledCleanupTimeMillis = now;
                performCleanup(server, CleanupReason.SCHEDULED);
            }
        }

        if (MemorySweepConfig.USAGE_BASED_CLEANUP_ENABLED.get()) {
            long usageCheckIntervalMillis = MemorySweepConfig.USAGE_CHECK_INTERVAL_SECONDS.get() * 1000L;
            if (now - lastUsageCheckTimeMillis >= usageCheckIntervalMillis) {
                lastUsageCheckTimeMillis = now;
                maybeTriggerUsageCleanup(server, now);
            }
        }
    }

    private void maybeTriggerUsageCleanup(MinecraftServer server, long now) {
        if (currentUsagePercent() < MemorySweepConfig.MEMORY_USAGE_THRESHOLD_PERCENT.get()) {
            return;
        }

        long cooldownMillis = MemorySweepConfig.USAGE_CHECK_COOLDOWN_SECONDS.get() * 1000L;
        if (now - lastCleanupTimeMillis < cooldownMillis) {
            return; // 冷却中:同一冷却周期内(默认 2 分钟)只允许触发一次
        }

        performCleanup(server, CleanupReason.USAGE_TRIGGERED);
    }

    /** 当前堆内存使用率(0-100)。 */
    public double currentUsagePercent() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        if (max <= 0) {
            return 0.0;
        }
        long used = runtime.totalMemory() - runtime.freeMemory();
        return (used * 100.0) / max;
    }

    /**
     * 立即执行一次内存清理(调用 {@link System#gc()}),并根据配置输出日志/聊天播报。
     * 该方法本身不做冷却判断 —— 冷却只限制“使用率自动触发”,手动指令与定时清理调用此方法时始终会真正执行。
     */
    public CleanupResult performCleanup(MinecraftServer server, CleanupReason reason) {
        Runtime runtime = Runtime.getRuntime();
        long beforeUsed = runtime.totalMemory() - runtime.freeMemory();

        long startNanos = System.nanoTime();
        System.gc();
        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        long afterUsed = runtime.totalMemory() - runtime.freeMemory();

        lastCleanupTimeMillis = System.currentTimeMillis();

        CleanupResult result = new CleanupResult(beforeUsed, afterUsed, runtime.maxMemory(), durationMillis, reason);

        if (MemorySweepConfig.LOG_TO_CONSOLE.get()) {
            logger.info("[MemorySweep] {}", result.toLogText());
        }

        if (MemorySweepConfig.BROADCAST_TO_OPS.get() && server != null) {
            broadcastToOps(server, result);
        }

        return result;
    }

    private void broadcastToOps(MinecraftServer server, CleanupResult result) {
        Component message = Component.literal(result.toChatText()).withStyle(ChatFormatting.GRAY);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)) {
                player.sendSystemMessage(message);
            }
        }
    }

    /** 供 {@code /memorysweep status} 使用的一段人类可读状态文本。 */
    public String statusText() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        double percent = currentUsagePercent();

        long now = System.currentTimeMillis();
        long nextScheduledSeconds = -1;
        if (MemorySweepConfig.AUTO_CLEANUP_ENABLED.get()) {
            long intervalMillis = MemorySweepConfig.INTERVAL_MINUTES.get() * 60_000L;
            nextScheduledSeconds = Math.max(0L, (lastScheduledCleanupTimeMillis + intervalMillis - now) / 1000L);
        }

        String scheduledPart = MemorySweepConfig.AUTO_CLEANUP_ENABLED.get()
                ? String.format(Locale.ROOT, "每 %d 分钟一次(约 %d 秒后下一次)", MemorySweepConfig.INTERVAL_MINUTES.get(),
                        nextScheduledSeconds)
                : "已禁用";
        String usagePart = MemorySweepConfig.USAGE_BASED_CLEANUP_ENABLED.get()
                ? String.format(Locale.ROOT, "已启用(阈值 %d%%,冷却 %d 秒)",
                        MemorySweepConfig.MEMORY_USAGE_THRESHOLD_PERCENT.get(),
                        MemorySweepConfig.USAGE_CHECK_COOLDOWN_SECONDS.get())
                : "已禁用";

        return String.format(Locale.ROOT,
                "[内存清理状态] 当前使用 %d/%d MB (%.1f%%) | 定时清理: %s | 使用率触发: %s",
                used / (1024 * 1024), max / (1024 * 1024), percent, scheduledPart, usagePart);
    }
}
