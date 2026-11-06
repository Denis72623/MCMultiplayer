package com.multiplayer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModConfigScreen extends Screen {
    private final Screen lastScreen;

    public ModConfigScreen(Screen lastScreen) {
        super(Component.literal("MCMultiplayer Config"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        super.init();

        int xCenter = this.width / 2 - 100;
        int yCenter = this.height / 2;

        // 1. Включить/Выключить сам мод
        this.addRenderableWidget(CycleButton.onOffBuilder(ModConfig.ENABLE_MOD.get())
            .create(xCenter, yCenter - 75, 200, 20, 
                Component.literal("MCMultiplayer Mod"), 
                (button, value) -> {
                    ModConfig.ENABLE_MOD.set(value);
                    ModConfig.SPEC.save();
                }
            )
        );

        // 2. True/False для Online Mode
        this.addRenderableWidget(CycleButton.onOffBuilder(ModConfig.ONLINE_MODE.get())
            .create(xCenter, yCenter - 50, 200, 20, 
                Component.literal("Online Mode License Check"), 
                (button, value) -> {
                    ModConfig.ONLINE_MODE.set(value);
                    ModConfig.SPEC.save(); 
                }
            )
        );

        // 3. Переключатель защиты паролей
        this.addRenderableWidget(CycleButton.onOffBuilder(ModConfig.ENABLE_AUTH.get())
            .create(xCenter, yCenter - 25, 200, 20, 
                Component.literal("Password Security Account"), 
                (button, value) -> {
                    ModConfig.ENABLE_AUTH.set(value);
                    ModConfig.SPEC.save();
                }
            )
        );

        // 4. Кнопка "Done" — смещена идеально вниз без наложений!
        this.addRenderableWidget(Button.builder(Component.literal("Done"), (button) -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.lastScreen);
            }
        }).bounds(xCenter, yCenter + 85, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.lastScreen);
        }
    }
}