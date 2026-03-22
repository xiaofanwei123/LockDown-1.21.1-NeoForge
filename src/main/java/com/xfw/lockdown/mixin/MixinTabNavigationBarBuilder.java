package com.xfw.lockdown.mixin;

import com.xfw.lockdown.Config;
import java.util.Arrays;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * TabNavigationBar.Builder，用于在创建世界屏幕中隐藏指定的选项卡
 * 根据配置项enableWorldTab和enableMoreTab决定是否隐藏“世界”和“更多”
 */
@Mixin(TabNavigationBar.Builder.class)
public class MixinTabNavigationBarBuilder {
    @Unique
    private static final String LOCKDOWN$WORLD_TAB_TITLE_KEY = "createWorld.tab.world.title";
    @Unique
    private static final String LOCKDOWN$MORE_TAB_TITLE_KEY = "createWorld.tab.more.title";

    /**
     * 拦截TabNavigationBar.Builder#addTabs(Tab[]) 方法被调用时，修改传入的选项卡数组
     * 过滤掉需要隐藏的选项卡，如果过滤后数组为空则保留原数组以防止崩溃
     *
     * @param originalTabs 原始选项卡数组（原版传入）
     * @return 过滤后的选项卡数组，若过滤后为空则返回原数组
     */
    @ModifyVariable(method="addTabs", at=@At("HEAD"), argsOnly=true)
    private Tab[] filterTabs(Tab[] originalTabs) {
        Tab[] filteredTabs = Arrays.stream(originalTabs).filter(tab -> !this.lockdown$shouldHideTab(tab)).toArray(Tab[]::new);
        return filteredTabs.length == 0 ? originalTabs : filteredTabs;
    }

    @Unique
    private boolean lockdown$shouldHideTab(Tab tab) {
        return !Config.enableWorldTab.get() && this.lockdown$hasTabTitleKey(tab, LOCKDOWN$WORLD_TAB_TITLE_KEY)
            || !Config.enableMoreTab.get() && this.lockdown$hasTabTitleKey(tab, LOCKDOWN$MORE_TAB_TITLE_KEY);
    }

    @Unique
    private boolean lockdown$hasTabTitleKey(Tab tab, String key) {
        return tab.getTabTitle().getContents() instanceof TranslatableContents contents && key.equals(contents.getKey());
    }
}
