package com.multiplayer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Enumeration;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = "multiplayer", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class IPV6 {

    public static KeyMapping toggleRenderKey; // Переключатель режимов отображения

    private static String cachedLocalIPv6 = "§eSearching...";
    private static String cachedGlobalIPv6 = "§eSearching...";
    
    private static long lastUpdateTime = 0;
    private static boolean isUpdating = false;

    // Режимы: 0 = Показать всё, 1 = Только LAN, 2 = Только Global, 3 = Скрыть всё
    private static int renderMode = 3; 

    // Флаги-триггеры для защиты от циклического спама при зажатии клавиш
    private static boolean toggleKeyWasPressed = false;

    @Mod.EventBusSubscriber(modid = "multiplayer", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class KeyRegisterHandler {
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            toggleRenderKey = new KeyMapping("key.multiplayer.toggle_ipv6_render", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.multiplayer");

            event.register(toggleRenderKey);
        }
    }

@SubscribeEvent
public static void onScreenRender(ScreenEvent.Render.Post event) {
    if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

    if (event.getScreen() instanceof PauseScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        long windowHandle = mc.getWindow().getWindow();

        // 1. АСИНХРОННЫЙ ОПРОС СЕТИ (Оставляем как было)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime > 10000 && !isUpdating) {
            lastUpdateTime = currentTime;
            isUpdating = true;
            updateAddressesAsync();
        }

        // 2. ПОЧИНЕННАЯ ОЧЕРЕДЬ КЛАВИШИ H (Работает во всех лаунчерах)
        if (toggleRenderKey != null) {
            // Получаем физический скан-код кнопки, привязанной к H в настройках
            int keyScanCode = toggleRenderKey.getKey().getValue();
            
            // Надежный опрос клавиатуры в обход блокировок GUI экрана паузы
            boolean toggleIsPressed = com.mojang.blaze3d.platform.InputConstants.isKeyDown(windowHandle, keyScanCode);
            
            if (toggleIsPressed && !toggleKeyWasPressed) {
                toggleKeyWasPressed = true;
                // Двигаем очередь по кругу через все 4 режима: 0 -> 1 -> 2 -> 3 -> 0
                renderMode = (renderMode + 1) % 4; 
            } else if (!toggleIsPressed) {
                toggleKeyWasPressed = false;
            }
        }

        // Логика видимости на основе очереди (0 = Показать всё, 1 = Только LAN, 2 = Только Global, 3 = Скрыть всё)
        boolean showLan = (renderMode == 0 || renderMode == 1);
        boolean showGlobal = (renderMode == 1 || renderMode == 2);

        // 4. ОТРИСОВКА В БЕЗОПАСНУЮ ЗОНУ
        Font font = mc.font;
        GuiGraphics graphics = event.getGuiGraphics();

        // Текст динамически скрывается или показывается в зависимости от текущего шага в очереди
        String localText = "Local IPv6: " + (showLan ? cachedLocalIPv6 : "§b[HIDDEN]");
        String externalText = "Global IPv6: " + (showGlobal ? cachedGlobalIPv6 : "§b[HIDDEN]");

        int startY = screen.height - 22; 

        graphics.drawString(font, localText, 5, startY, 0xFFFFFF, true);
        graphics.drawString(font, externalText, 5, startY + 10, 0xFFFFFF, true);
    }
}

@SubscribeEvent
public static void onScreenInit(net.minecraftforge.client.event.ScreenEvent.Init.Post event) {

        if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

    if (event.getScreen() instanceof net.minecraft.client.gui.screens.PauseScreen pauseScreen) {
        Minecraft mc = Minecraft.getInstance();
        
        int adjustedY = pauseScreen.height / 2 - 20; // Выравнивание по высоте (в районе кнопки Report Bugs)
        int bottomRightX = pauseScreen.width / 2 + 110; // Смещение вправо от центрального меню на 10 пикселей

        final boolean[] encryptGlobalMode = {true};

        // Создаем кнопку
        net.minecraft.client.gui.components.Button secureIpBtn = new net.minecraft.client.gui.components.Button(
            bottomRightX, adjustedY, 110, 20, 
            net.minecraft.network.chat.Component.literal("Encrypt IPv6: Global"), 
            b -> {
                // ЛЕВЫЙ КЛИК (Стандартное действие кнопки)
                net.minecraft.client.server.IntegratedServer server = mc.getSingleplayerServer();
                if (server != null && server.isPublished()) {
                    b.setMessage(net.minecraft.network.chat.Component.literal("§6Encrypting IPv6..."));
                    new Thread(() -> {
                        try {
                            String selectedIP = "127.0.0.1";
                            int livePort = server.getPort();

                            if (encryptGlobalMode[0]) { 
                                // ГЛОБАЛЬНЫЙ РЕЖИМ (Запрос внешнего IP)
                                try {
                                    // Сначала пробуем получить внешний IPv6
java.net.URL urlV6 = new java.net.URL("https://ident.me"); // Стучимся сразу на IPv6 поддомен
java.net.HttpURLConnection connV6 = (java.net.HttpURLConnection) urlV6.openConnection();
connV6.setConnectTimeout(3000);
connV6.setReadTimeout(3000);
connV6.setRequestProperty("Accept", "text/plain"); // Требуем чистый текст без HTML
connV6.setRequestProperty("User-Agent", "Mozilla/5.0");

try (java.io.BufferedReader rV6 = new java.io.BufferedReader(new java.io.InputStreamReader(connV6.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                                        String response = rV6.readLine();
                                        if (response != null && response.trim().contains(":")) {
                                            selectedIP = response.trim().toLowerCase();
                                        } else {
                                            throw new Exception("Not IPv6");
                                        }
                                    }
                                } catch (Exception eV6) {
                                    try {
                                        // ЗАЩИТА: Если глобальный IPv6 не найден, берем внешний IPv4
                                        java.net.URL urlV4 = new java.net.URL("https://ident.me");
                                        java.net.HttpURLConnection connV4 = (java.net.HttpURLConnection) urlV4.openConnection();
                                        connV4.setConnectTimeout(3000);
                                        connV4.setReadTimeout(3000);
                                        connV4.setRequestProperty("Accept", "text/plain"); // Чистый текст без HTML
                                        connV4.setRequestProperty("User-Agent", "Mozilla/5.0");
                                        
                                        try (java.io.BufferedReader rV4 = new java.io.BufferedReader(new java.io.InputStreamReader(connV4.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                                            String response = rV4.readLine();
                                            if (response != null && !response.contains("<")) {
                                                selectedIP = response.trim().toLowerCase();
                                            }
                                        }
                                    } catch (Exception eV4) {
                                        selectedIP = "127.0.0.1"; // Полный оффлайн-запас
                                    }
                                }
                            } else {
                                // ЛОКАЛЬНЫЙ РЕЖИМ (Поиск в локальной сети)
                                java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
                                boolean found = false;
                                String backupIpv4 = null;

                                while (interfaces.hasMoreElements() && !found) {
                                    java.net.NetworkInterface netInterface = interfaces.nextElement();
                                    if (netInterface.isLoopback() || !netInterface.isUp()) continue;
                                    
                                    java.util.Enumeration<java.net.InetAddress> addresses = netInterface.getInetAddresses();
                                    while (addresses.hasMoreElements()) {
                                        java.net.InetAddress addr = addresses.nextElement();
                                        
                                        if (addr instanceof java.net.Inet6Address) {
                                            String ip6 = addr.getHostAddress();
                                            String cleanIp6 = ip6.contains("%") ? ip6.split("%")[0] : ip6;
                                            cleanIp6 = cleanIp6.toLowerCase().trim();
                                            
                                            // Фильтруем локальные IPv6 адреса
                                            if (cleanIp6.startsWith("fe80") || cleanIp6.startsWith("fc00") || cleanIp6.startsWith("fd00")) {
                                                selectedIP = cleanIp6;
                                                found = true;
                                                break;
                                            }
                                        } else if (addr instanceof java.net.Inet4Address && backupIpv4 == null) {
                                            String ip4 = addr.getHostAddress();
                                            if (ip4.startsWith("192.168.") || ip4.startsWith("10.") || ip4.startsWith("172.")) {
                                                backupIpv4 = ip4;
                                            }
                                        }
                                    }
                                }
                                if (!found && backupIpv4 != null) {
                                    selectedIP = backupIpv4;
                                }
                            }

                            // НАСТОЯЩЕЕ HEX-ШИФРОВАНИЕ ДЛЯ IPv4 И IPv6
                            // Преобразуем строку IP в объект InetAddress, чтобы избежать багов с сокращениями IPv6 (::)
                            java.net.InetAddress inetAddr = java.net.InetAddress.getByName(selectedIP);
                            byte[] ipBytes = inetAddr.getAddress();
                            
                            StringBuilder hexIPBuilder = new StringBuilder();
                            for (byte bByte : ipBytes) {
                                // Каждый байт строго в 2 символа HEX
                                hexIPBuilder.append(String.format("%02x", bByte));
                            }
                            String hexIP = hexIPBuilder.toString();
                            
                            // Переводим порт в HEX
                            String hexPort = Integer.toHexString(livePort);
                            final String finalSecureKey = "mcm-" + hexIP + "-" + hexPort;

                            mc.execute(() -> {
                                mc.keyboardHandler.setClipboard(finalSecureKey);
                                b.setMessage(net.minecraft.network.chat.Component.literal("§aKey Copied!"));
                            });
                        } catch (Exception e) {
                            mc.execute(() -> b.setMessage(net.minecraft.network.chat.Component.literal("§cCipher Error")));
                        }
                    }).start();
                } else {
                    b.setMessage(net.minecraft.network.chat.Component.literal("§cTurn ON Server First"));
                }
            }, 
            b -> net.minecraft.network.chat.Component.empty()
        ) { 
            // ПЕРЕХВАТ ПРАВОГО КЛИКА МЫШИ
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (this.active && this.visible && this.clicked(mouseX, mouseY)) {
                    if (button == 1) { // 1 = Правая кнопка мыши в Майнкрафте
                        net.minecraft.client.resources.sounds.SimpleSoundInstance playSound = 
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F);
                        Minecraft.getInstance().getSoundManager().play(playSound);
                        
                        // ИСПРАВЛЕНИЕ: Добавлен индекс [0] для изменения значения в массиве
                        encryptGlobalMode[0] = !encryptGlobalMode[0];
                        this.setMessage(net.minecraft.network.chat.Component.literal("Encrypt IPv6: " + (encryptGlobalMode[0] ? "Global" : "Lan")));
                        return true; 
                    }
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }
        };

        // Добавляем созданную кнопку на экран паузы через листенер Forge
        event.addListener(secureIpBtn);
    }
}

@SubscribeEvent
public static void onScreenKeyPressed(net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre event) {
    if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

    // Срабатывает только когда открыт PauseScreen
    if (event.getScreen() instanceof PauseScreen) {
        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();

        // Проверяем клавишу H через ваш зарегистрированный KeyMapping
        if (toggleRenderKey != null && toggleRenderKey.matches(keyCode, scanCode)) {
            renderMode = (renderMode + 1) % 4; // Циклически переключаем режимы от 0 до 3
            event.setCanceled(true); // Сообщаем игре, что мы успешно обработали нажатие
        }
    }
}

private static void updateAddressesAsync() {
    if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

    CompletableFuture.runAsync(() -> {
        String foundLocal = "No IPv6 Lan";
        String foundGlobal = "No IPv6 Global";

        try {
            // 1. Поиск локального IPv6
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet6Address) {
                        String hostAddress = addr.getHostAddress();
                        String cleanIp = hostAddress.contains("%") ? hostAddress.split("%")[0] : hostAddress;
                        cleanIp = cleanIp.toLowerCase().trim();

                        if (cleanIp.startsWith("fe80") || cleanIp.startsWith("fc00") || cleanIp.startsWith("fd00")) {
                            foundLocal = cleanIp;
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. Безопасный запрос глобального IPv6
        try {
            foundGlobal = requestIpWithBackup("https://v6.ident.me", "https://icanhazip.com");
        } catch (Exception e) {
            // ЗАЩИТА: Если глобальный IPv6 не найден, запрашиваем внешний IPv4 для отрисовки на экране
            try {
                java.net.URL urlV4 = new java.net.URL("https://ident.me");
                java.net.HttpURLConnection connV4 = (java.net.HttpURLConnection) urlV4.openConnection();
                connV4.setConnectTimeout(3000);
                connV4.setReadTimeout(3000);
                connV4.setRequestProperty("Accept", "text/plain"); // Требуем чистый текст, игнорируя HTML
                connV4.setRequestProperty("User-Agent", "Mozilla/5.0");
                
                try (java.io.BufferedReader brV4 = new java.io.BufferedReader(new java.io.InputStreamReader(connV4.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String res4 = brV4.readLine();
                    if (res4 != null && !res4.contains("<")) {
                        foundGlobal = res4.trim().toLowerCase() + " (IPv4)"; // Заменяем ошибку на реальный IPv4!
                    }
                }
            } catch (Exception eV4) {
                foundGlobal = "No Internet";
            }
        } finally {
            cachedLocalIPv6 = foundLocal;
            cachedGlobalIPv6 = foundGlobal; // Теперь сюда прилетит чистый IP (или IPv4), который правильно отработает в [HIDDEN]
            isUpdating = false;
        }
    });
}

// Вспомогательный метод с поддержкой резервного сервиса и заголовков plain-text
private static String requestIpWithBackup(String primaryUrl, String backupUrl) {
    String result = requestSingleIp(primaryUrl);
    if (result == null) {
        result = requestSingleIp(backupUrl);
    }
    return result != null ? result : "No IPv6 Global";
}

private static String requestSingleIp(String urlStr) {
    try {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        
        // ВАЖНО: Заставляем сервер отдавать только чистый текст (plain text), а не HTML-страницу
        connection.setRequestProperty("Accept", "text/plain");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

        if (connection.getResponseCode() == 200) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String responseIp = br.readLine();
                if (responseIp != null) {
                    responseIp = responseIp.trim().toLowerCase();
                    // Валидация: настоящий IPv6 обязан содержать двоеточие и не должен содержать HTML тегов
                    if (responseIp.contains(":") && !responseIp.contains("<") && !responseIp.contains("html")) {
                        return responseIp;
                    }
                }
            }
        }
    } catch (Exception ignored) {}
    return null;
}
}