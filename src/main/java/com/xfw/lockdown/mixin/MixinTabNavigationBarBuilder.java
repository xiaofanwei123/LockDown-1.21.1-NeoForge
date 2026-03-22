package com.xfw.lockdown.mixin;

import com.xfw.lockdown.Config;
import java.util.Arrays;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value={TabNavigationBar.Builder.class})
public class MixinTabNavigationBarBuilder {
    @Unique
    private static final Component WORLD_TITLE = Component.translatable("createWorld.tab.world.title");

    @ModifyVariable(method={"addTabs"}, at=@At(value="HEAD"), argsOnly=true)
    private Tab[] filterTabs(Tab[] originalTabs) {
        return Arrays.stream(originalTabs).filter(tab -> !this.lockdown$shouldRemoveTab(tab)).toArray(Tab[]::new);
    }

    @Unique
    private boolean lockdown$shouldRemoveTab(Tab tab) {
        if (!Config.useTemplate.get()) {
            return false;
        }
        if (Config.disableWorldTab.get()) {
            return tab.getTabTitle().equals(WORLD_TITLE);
        }
        return false;
    }
}
