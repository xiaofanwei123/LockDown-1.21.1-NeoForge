package com.xfw.lockdown.mixin;

import java.util.List;
import com.xfw.lockdown.TemplateSeedResolver;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.RandomSequences;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel {
    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void lockdown$useTemplateSeedForSelectedDimensions(CallbackInfoReturnable<Long> cir) {
        ServerLevel serverLevel = (ServerLevel)(Object)this;
        if (!TemplateSeedResolver.shouldUseTemplateSeed(serverLevel.dimension())) {
            return;
        }

        long originalSeed = serverLevel.getServer().getWorldData().worldGenOptions().seed();
        cir.setReturnValue(TemplateSeedResolver.resolveSeed(serverLevel.getServer(), serverLevel.dimension(), originalSeed));
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldOptions;seed()J"))
    private long lockdown$useTemplateSeedForStructureChecks(
        WorldOptions worldOptions,
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
        long originalSeed = worldOptions.seed();
        return TemplateSeedResolver.resolveSeed(server, dimension, originalSeed);
    }
}




