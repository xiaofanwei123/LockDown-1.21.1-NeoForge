package com.xfw.lockdown.mixin;

import com.xfw.lockdown.Config;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={TitleScreen.class})
public abstract class MixinTitleScreen
        extends Screen {
    protected MixinTitleScreen(Component component) {
        super(component);
    }

    @Inject(method={"init"}, at={@At(value="RETURN")})
    private void onInit(CallbackInfo ci) {
        Button singleplayerButton = this.lockdown$findButton(Component.translatable("menu.singleplayer"));
        Button multiplayerButton = this.lockdown$findButton(Component.translatable("menu.multiplayer"));

        if (!Config.disableSingleplayer.get() && singleplayerButton != null) {
            this.lockdown$hideButton(singleplayerButton);
            this.lockdown$moveWidgetsBelow(singleplayerButton.getY(), -24);
        }

        if (!Config.disableMultiplayer.get() && multiplayerButton != null) {
            this.lockdown$hideButton(multiplayerButton);
            this.lockdown$moveWidgetsBelow(multiplayerButton.getY(), -24);
        }
    }

    @Unique
    private Button lockdown$findButton(Component message) {
        for (GuiEventListener child : this.children()) {
            if (child instanceof Button button && button.getMessage().equals(message)) {
                return button;
            }
        }

        return null;
    }

    @Unique
    private void lockdown$hideButton(Button button) {
        button.active = false;
        button.visible = false;
    }

    @Unique
    private void lockdown$moveWidgetsBelow(int startY, int deltaY) {
        int maxMenuY = this.height / 4 + 160;
        List<? extends GuiEventListener> children = this.children();

        for (GuiEventListener child : children) {
            if (child instanceof AbstractWidget widget && widget.getY() > startY && widget.getY() < maxMenuY) {
                widget.setY(widget.getY() + deltaY);
            }
        }
    }
}
