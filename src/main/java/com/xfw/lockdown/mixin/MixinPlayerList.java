package com.xfw.lockdown.mixin;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public abstract class MixinPlayerList {
    @Shadow
    @Final
    private MinecraftServer server;

    @Unique
    private final Set<UUID> lockdown$newPlayers = new HashSet<>();

    @Inject(method = "load", at = @At("RETURN"))
    private void lockdown$trackNewPlayers(ServerPlayer player, CallbackInfoReturnable<Optional<CompoundTag>> cir) {
        if (cir.getReturnValue().isEmpty()) {
            this.lockdown$newPlayers.add(player.getUUID());
            return;
        }

        this.lockdown$newPlayers.remove(player.getUUID());
    }

    @ModifyVariable(method = "placeNewPlayer", at = @At(value = "STORE"), ordinal = 0)
    private ResourceKey<Level> lockdown$useConfiguredSpawnDimension(ResourceKey<Level> originalDimension, Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
        if (!this.lockdown$shouldUseConfiguredSpawn(player)) {
            return originalDimension;
        }

        ConfiguredSpawn configuredSpawn = ConfiguredSpawn.resolve(this.server);
        if (configuredSpawn == null) {
            return originalDimension;
        }

        return configuredSpawn.dimensionKey();
    }

    @Inject(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;getLoggableAddress(Z)Ljava/lang/String;", shift = At.Shift.BEFORE))
    private void lockdown$moveNewPlayersToConfiguredSpawn(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        if (!this.lockdown$shouldUseConfiguredSpawn(player)) {
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

        configuredSpawn.applyDefaultSpawn(targetLevel);
        player.moveTo(configuredSpawn.x(), configuredSpawn.y(), configuredSpawn.z(), configuredSpawn.yaw(), configuredSpawn.pitch());
    }

    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void lockdown$clearTrackedNewPlayer(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        this.lockdown$newPlayers.remove(player.getUUID());
    }

    @Unique
    private boolean lockdown$shouldUseConfiguredSpawn(ServerPlayer player) {
        return Config.loginSpawnTeleportEnabled.get() && this.lockdown$newPlayers.contains(player.getUUID());
    }
}


