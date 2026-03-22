package com.xfw.lockdown.mixin;

import java.util.List;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.xfw.lockdown.TemplateSeedResolver;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.RandomSequences;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 指定维度替换种子值
 * 支持两种场景：
 * 当通过getSeed()获取种子时（例如用于地形生成），返回配置的模板种子
 * 在ServerLevel构造期间，将WorldOptions中的种子替换为模板种子，以确保结构生成、随机序列等
 */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevel {

    /**
     * 拦截ServerLevel#getSeed()方法。
     * 如果当前维度被配置为使用模板种子，则返回该种子；否则返回原始种子
     *
     * @param cir 回调信息，用于设置返回值
     */
    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void lockdown$useTemplateSeedForSelectedDimensions(CallbackInfoReturnable<Long> cir) {
        ServerLevel serverLevel = (ServerLevel)(Object)this;
        long originalSeed = serverLevel.getServer().getWorldData().worldGenOptions().seed();
        TemplateSeedResolver.resolveSeedIfConfigured(serverLevel.getServer(), serverLevel.dimension(), originalSeed)
            .ifPresent(cir::setReturnValue);
    }

    /**
     * 修改ServerLevel构造方法中对WorldOptions.seed()调用的返回值。
     * 原版构造方法中会调用WorldOptions.seed()获取全局种子，用于结构生成器、随机序列等
     * 通过仅改写表达式结果，可以在构造期间替换掉种子，同时降低与其他同位置Mixin冲突的概率。
     *
     * @param originalSeed       原版WorldOptions.seed()返回的种子
     * @param server             服务器实例
     * @param dispatcher         执行器
     * @param levelStorageAccess 存储访问
     * @param serverLevelData    世界数据
     * @param dimension          当前维度
     * @param levelStem          维度配置
     * @param progressListener   区块加载监听器
     * @param isDebug            是否为调试模式
     * @param biomeZoomSeed      生物群系缩放种子
     * @param customSpawners     自定义生成器
     * @param tickTime           是否计时
     * @param randomSequences    随机序列
     * @return 应使用的种子值（如果当前维度配置了模板种子则返回模板种子，否则返回原始种子）
     */
    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldOptions;seed()J"))
    private long lockdown$useTemplateSeedForStructureChecks(
        long originalSeed,
        MinecraftServer server,
        java.util.concurrent.Executor dispatcher,
        LevelStorageSource.LevelStorageAccess levelStorageAccess,
        ServerLevelData serverLevelData,
        ResourceKey<Level> dimension,
        LevelStem levelStem,
        ChunkProgressListener progressListener,
        boolean isDebug,
        long biomeZoomSeed,
        List<CustomSpawner> customSpawners,
        boolean tickTime,
        RandomSequences randomSequences
    ) {
        return TemplateSeedResolver.resolveSeed(server, dimension, originalSeed);
    }
}




