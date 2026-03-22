package com.xfw.lockdown.mixin;

import java.util.Optional;
import com.llamalad7.mixinextras.sugar.Local;
import com.xfw.lockdown.Config;
import com.xfw.lockdown.ConfiguredSpawn;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 新玩家首次登录时的自定义出生点传送功能
 * 当配置loginSpawnTeleportEnabled为 true时，新玩家会被传送到配置的维度、坐标和朝向
 */
@Mixin(PlayerList.class)
public abstract class MixinPlayerList {
    @Shadow
    @Final
    private MinecraftServer server;


    /**
     * 拦截PlayerList#placeNewPlayer,修改玩家出生点维度.
     * Optional<CompoundTag> optional1 表示玩家是否有已有数据,通过 @ModifyVariable 在变量存储时拦截并替换维度键
     *
     * @param originalDimension 原版计算出的出生点维度
     * @param connection        网络连接
     * @param player            新加入的玩家
     * @param cookie            连接信息
     * @param optional1         玩家数据可选项
     * @return 实际应使用的维度键（若配置允许且玩家为新玩家则返回配置的维度，否则原维度）
     */
    @ModifyVariable(method = "placeNewPlayer", at = @At(value = "STORE"), ordinal = 0)
    private ResourceKey<Level> lockdown$useConfiguredSpawnDimension(ResourceKey<Level> originalDimension, Connection connection, ServerPlayer player, CommonListenerCookie cookie, @Local Optional<CompoundTag> optional1) {
        if (!this.lockdown$shouldApplyConfiguredSpawnToNewPlayer(optional1)) {
            return originalDimension;
        }

        ConfiguredSpawn configuredSpawn = ConfiguredSpawn.resolve(this.server);
        if (configuredSpawn == null) {
            return originalDimension;
        }

        //返回配置的维度键，用于后续创建玩家出生点
        return configuredSpawn.dimensionKey();
    }

    /**
     * 拦截PlayerList#placeNewPlayer，将新玩家传送到配置的自定义出生点。
     * 注入点在原版记录玩家IP地址之后（INVOKE 目标），此时玩家已加入世界，但尚未移动
     * Local获取之前已经提取的optional1变量。
     *
     * @param connection        网络连接
     * @param player            新玩家
     * @param cookie            连接信息
     * @param ci                回调信息
     * @param optional1         玩家数据可选项（@Local获取）
     */
    @Inject(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;getLoggableAddress(Z)Ljava/lang/String;"))
    private void lockdown$moveNewPlayersToConfiguredSpawn(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci, @Local Optional<CompoundTag> optional1) {
        if (!this.lockdown$shouldApplyConfiguredSpawnToNewPlayer(optional1)) {
            return;
        }

        ConfiguredSpawn configuredSpawn = ConfiguredSpawn.resolve(this.server);
        if (configuredSpawn == null) {
            return;
        }

        ServerLevel targetLevel = configuredSpawn.getLevel(this.server);
        if (targetLevel == null) {
            return;
        }
        //将玩家的默认出生点设置为配置值
        configuredSpawn.applyDefaultSpawn(targetLevel);
        player.moveTo(configuredSpawn.x(), configuredSpawn.y(), configuredSpawn.z(), configuredSpawn.yaw(), configuredSpawn.pitch());
    }

    /**
     * 判断是否应当将自定义出生点应用于当前新玩家。
     * @return true 表示应应用自定义出生点，false 表示继续使用原版逻辑
     */
    @Unique
    private boolean lockdown$shouldApplyConfiguredSpawnToNewPlayer(Optional<CompoundTag> optional1) {
        return Config.loginSpawnTeleportEnabled.get() && optional1.isEmpty();
    }
}


