package com.multiplayer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public class SecureConnectScreen extends Screen {
    private final Screen lastScreen;
    private EditBox ipInputBox;

    public SecureConnectScreen(Screen lastScreen) {
        super(Component.literal("MCMultiplayer Secure Connection"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        // Поле ввода секретного ключа по центру экрана
        this.ipInputBox = new EditBox(this.font, this.width / 2 - 150, this.height / 2 - 20, 300, 20, Component.literal("Enter Secret Key"));
        this.ipInputBox.setMaxLength(128);
        this.addWidget(this.ipInputBox);

        // КНОПКА ЗАЙТИ (JOIN): Универсально расшифровывает IPv4 и IPv6
        this.addRenderableWidget(Button.builder(Component.literal("Secure Join Server"), b -> {
            String secretKey = this.ipInputBox.getValue().trim().toLowerCase();
            
            if (secretKey.startsWith("mcm-")) {
                try {
                    String[] parts = secretKey.substring(4).split("-");
                    if (parts.length < 2) throw new Exception("Invalid key format");

                    String hexIP = parts[0];
                    String hexPort = parts[1];

                    // НАСТОЯЩЕЕ ДЕШИФРОВАНИЕ ДЛЯ IPv4 И IPv6 ЧЕРЕЗ МАССИВ БАЙТ
                    int len = hexIP.length();
                    // Длина HEX должна быть либо 8 символов (4 байта IPv4), либо 32 символа (16 байт IPv6)
                    if (len != 8 && len != 32) throw new Exception("Wrong IP HEX length");

                    byte[] ipBytes = new byte[len / 2];
                    for (int i = 0; i < len; i += 2) {
                        ipBytes[i / 2] = (byte) ((Character.digit(hexIP.charAt(i), 16) << 4)
                                             + Character.digit(hexIP.charAt(i+1), 16));
                    }

                    // Восстанавливаем объект адреса из байтов
                    java.net.InetAddress inetAddr = java.net.InetAddress.getByAddress(ipBytes);
                    String realIP = inetAddr.getHostAddress();

                    // Расшифровываем порт
                    int realPort = Integer.parseInt(hexPort, 16);
                    
                    // ВАЖНО ДЛЯ IPv6: Если адрес содержит двоеточия, его нужно обернуть в квадратные скобки [ ]
                    String finalRealAddress;
                    if (realIP.contains(":")) {
                        finalRealAddress = "[" + realIP + "]:" + realPort;
                    } else {
                        finalRealAddress = realIP + ":" + realPort;
                    }
                    
                    // НАСТОЯЩИЙ РАБОЧИЙ ВЫЗОВ ДЛЯ 1.20.1 FORGE
                    ServerData serverData = new ServerData("MCMultiplayer Server", finalRealAddress, false);
                    ServerAddress serverAddress = ServerAddress.parseString(finalRealAddress);
                    
                    // Запускаем подключение через официальный метод игры
                    ConnectScreen.startConnecting(this.lastScreen, this.minecraft, serverAddress, serverData, false);
                    
                    System.out.println("[MCMultiplayer] Key successfully decrypted! Connecting to: " + finalRealAddress);
                } catch (Exception e) {
                    b.setMessage(Component.literal("§cInvalid Secret Key!"));
                }
            } else {
                b.setMessage(Component.literal("§cNot a MCMultiplayer Key!"));
            }
        })
        .bounds(this.width / 2 - 150, this.height / 2 + 15, 145, 20)
        .build());

        // КНОПКА ОТМЕНЫ (Возврат в Главное меню)
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            this.minecraft.setScreen(this.lastScreen);
        })
        .bounds(this.width / 2 + 5, this.height / 2 + 15, 145, 20)
        .build());
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 50, 16777215);
        guiGraphics.drawString(this.font, "Paste your friend's secure server key below:", this.width / 2 - 150, this.height / 2 - 35, 10526880);
        this.ipInputBox.render(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {

        this.minecraft.setScreen(this.lastScreen);
    }
}