package com.memorysweep.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * MemorySweep 的配置定义,使用 NeoForge 内置的 {@link ModConfigSpec} 系统。
 * <p>
 * 注册为 {@code ModConfig.Type.SERVER} 后,NeoForge 会自动生成带注释的 TOML 配置文件
 * (专用服务器上位于 {@code config/memorysweep-server.toml};单人世界位于对应存档的
 * {@code serverconfig/memorysweep-server.toml}),并在配置被编辑、重新加载时自动更新
 * 下面这些字段的值 —— 因此本模组里任何地方读取配置,都应直接调用 {@code XXX.get()} /
 * {@code XXX.getAsBoolean()} / {@code XXX.getAsInt()},而不是把值缓存到普通变量里。
 */
public final class MemorySweepConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** 是否启用“定时自动清理”。 */
    public static final ModConfigSpec.BooleanValue AUTO_CLEANUP_ENABLED = BUILDER
            .comment("是否启用定时自动清理")
            .define("autoCleanupEnabled", true);

    /** 定时自动清理的间隔时间,单位:分钟。默认 15 分钟。 */
    public static final ModConfigSpec.IntValue INTERVAL_MINUTES = BUILDER
            .comment("定时自动清理的间隔时间(分钟),默认 15 分钟")
            .defineInRange("intervalMinutes", 15, 1, Integer.MAX_VALUE);

    /** 是否启用“根据内存使用率自动清理”。 */
    public static final ModConfigSpec.BooleanValue USAGE_BASED_CLEANUP_ENABLED = BUILDER
            .comment("是否启用根据内存使用率自动清理")
            .define("usageBasedCleanupEnabled", true);

    /** 触发使用率清理的堆内存占用阈值,单位:百分比(1-99)。默认 80。 */
    public static final ModConfigSpec.IntValue MEMORY_USAGE_THRESHOLD_PERCENT = BUILDER
            .comment("触发使用率清理的堆内存占用阈值(百分比,1-99),默认 80")
            .defineInRange("memoryUsageThresholdPercent", 80, 1, 99);

    /** 使用率触发的清理,两次执行之间的最短间隔,单位:秒。默认 120 秒(2 分钟)。 */
    public static final ModConfigSpec.IntValue USAGE_CHECK_COOLDOWN_SECONDS = BUILDER
            .comment("使用率触发的清理,两次执行之间的最短间隔(秒),默认 120 秒(2 分钟)")
            .defineInRange("usageCheckCooldownSeconds", 120, 1, Integer.MAX_VALUE);

    /** 后台检查内存使用率的频率,单位:秒。默认 5 秒检查一次。 */
    public static final ModConfigSpec.IntValue USAGE_CHECK_INTERVAL_SECONDS = BUILDER
            .comment("后台检查内存使用率的频率(秒),默认 5 秒")
            .defineInRange("usageCheckIntervalSeconds", 5, 1, Integer.MAX_VALUE);

    /** 每次清理后,是否在聊天栏向管理员(OP)播报清理结果。 */
    public static final ModConfigSpec.BooleanValue BROADCAST_TO_OPS = BUILDER
            .comment("每次清理后是否在聊天栏向管理员播报结果")
            .define("broadcastToOps", true);

    /** 每次清理后,是否在服务器控制台/日志中输出清理结果。 */
    public static final ModConfigSpec.BooleanValue LOG_TO_CONSOLE = BUILDER
            .comment("每次清理后是否在服务器控制台/日志中输出结果")
            .define("logToConsole", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private MemorySweepConfig() {
    }
}
