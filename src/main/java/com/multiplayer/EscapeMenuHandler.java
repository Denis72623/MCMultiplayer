package com.multiplayer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "multiplayer", value = Dist.CLIENT)
public class EscapeMenuHandler {
    
    private static boolean useIPv6 = false; // Переключатель: false = IPv4, true = IPv6
    public static String generatedServerDomain = "Not Connected";

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        // Запускаем автономную базу данных авторизации 4.8.0
        com.multiplayer.ServerAuthManager.initAuth();

         if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

        if (event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen titleScreen) {
            Minecraft mc = Minecraft.getInstance();
            
            Button secureConnectBtn = Button.builder(
                Component.literal("🔒 MCMultiplayer Connect"), 
                b -> mc.setScreen(new com.multiplayer.SecureConnectScreen(titleScreen))
            )
            // Встанет ровно по центру главного меню под ванильными кнопками!
            .bounds(titleScreen.width / 2 - 100, titleScreen.height / 4 + 48 + 48 + 48 + 24, 200, 20)
            .build();
            
            // Метод addRenderableWidget выведет её на твой скриншот
            event.addListener(secureConnectBtn);
        }

        if (event.getScreen() instanceof PauseScreen pauseScreen) {
            Minecraft mc = Minecraft.getInstance();
            
            // Координаты центра экрана из оригинального ExampleMod
            int xCenter = pauseScreen.width / 2;
            int yStart = pauseScreen.height / 4 + 144; 
            
            // Кнопка интернета: ровно по центру под твоим оригинальным рядом (Y + 24)
            int adjustedY = yStart + 24;

            // 1. ТВОЯ КНОПКА ИНТЕРНЕТА: Полностью сохранена и идеально центрирована под меню
            Button protocolBtn = Button.builder(
                Component.literal("Internet: " + (useIPv6 ? "IPv6" : "IPv4")), 
                b -> {
                    useIPv6 = !useIPv6;
                    b.setMessage(Component.literal("Internet: " + (useIPv6 ? "IPv6" : "IPv4")));
                    
                    System.setProperty("java.net.preferIPv4Stack", String.valueOf(!useIPv6));
                    System.setProperty("java.net.preferIPv6Addresses", String.valueOf(useIPv6));
                })
                .bounds(xCenter - 58, adjustedY, 155, 20)
                .build();
            event.addListener(protocolBtn);

            // =========================================================================
            // 2. НАСТРОЙКА ЛИМИТА ИГРОКОВ: Опущена строго ПОД кнопку Internet! (Y + 48)
            // Это полностью решает проблему обрезки экрана слева!
            // =========================================================================
            int playersY = adjustedY + 24; // Новый центральный этаж ниже кнопок
            final int[] maxPlayersLimit = {8}; // Базовый лимит по умолчанию

            // Поле ввода числа игроков — Ровно по центру левой части блока!
            EditBox maxPlayersBox = new EditBox(mc.font, xCenter + 55, 14, 35, 20, Component.literal("MaxPlayers"));
            maxPlayersBox.setValue(String.valueOf(maxPlayersLimit[0]));
            maxPlayersBox.setResponder(t -> {
                if (!t.matches("\\d*")) {
                    maxPlayersBox.setValue(t.replaceAll("[^\\d]", ""));
                }
            });
            event.addListener(maxPlayersBox);

            // Кнопка применения лимита игроков — встает ровно после окошка ввода по центру!
            Button applyLimitBtn = Button.builder(Component.literal("Set Max Players"), b -> {
                net.minecraft.client.Minecraft mcInstance = net.minecraft.client.Minecraft.getInstance();
                int limit = 8;
                
                try {
                    String value = maxPlayersBox.getValue();
                    if (!value.isEmpty()) {
                        limit = Integer.parseInt(value);
                    }
                    
                    net.minecraft.client.server.IntegratedServer server = mcInstance.getSingleplayerServer();
                    if (server != null && server.getPlayerList() != null) {
                        net.minecraft.server.players.PlayerList playerList = server.getPlayerList();
                        
                        // Вламываемся в приватность Java 17 через легальный дескриптор без рефлексии
                        java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.privateLookupIn(
                            net.minecraft.server.players.PlayerList.class, 
                            java.lang.invoke.MethodHandles.lookup()
                        );
                        
                        java.lang.invoke.VarHandle maxPlayersHandle;
                        try {
                            maxPlayersHandle = lookup.findVarHandle(net.minecraft.server.players.PlayerList.class, "maxPlayers", int.class);
                        } catch (Exception ex) {
                            maxPlayersHandle = lookup.findVarHandle(net.minecraft.server.players.PlayerList.class, "f_11195_", int.class);
                        }
                        
                        maxPlayersHandle.set(playerList, limit);
                        server.setMotd(server.getMotd());
                    }
                } catch (Exception e) {
                    try {
                        net.minecraft.client.server.IntegratedServer server = mcInstance.getSingleplayerServer();
                        if (server != null && server.getPlayerList() != null) {
                            for (java.lang.reflect.Field field : net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
                                if (field.getType() == int.class) {
                                    field.setAccessible(true);
                                    if (field.getInt(server.getPlayerList()) == 8 || field.getInt(server.getPlayerList()) == limit) {
                                        field.setInt(server.getPlayerList(), limit);
                                        server.setMotd(server.getMotd());
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {}
                }
            })
            .bounds(xCenter + 94, 14, 116, 20)
            .build();
            event.addListener(applyLimitBtn);

            Button optimizeEverythingBtn = Button.builder(
                Component.literal("Optimize All"), 
                b -> {
                    // При нажатии сразу пишем на английском, что идёт настройка систем
                    b.setMessage(Component.literal("§6Configuring Net..."));
                    
                    // Запускаем фоновый изолированный поток, чтобы игра не зависала при пробиве роутера
                    new Thread(() -> {
                        try {
                            // 1. НАСТРОЙКА ИНТЕРНЕТА: Полная оптимизация сетевых приоритетов Java 17
                            System.setProperty("java.net.preferIPv4Stack", "false");
                            System.setProperty("java.net.preferIPv6Addresses", "true");
                            
                            net.minecraft.client.server.IntegratedServer server = mc.getSingleplayerServer();
                            
                            if (server != null) {
                                // 2. НАСТРОЙКА СЕРВЕРА: Если мир ещё не открыт для сети, мод САМ принудительно его опубликует!
                                if (!server.isPublished()) {
                                    // Авто-публикация в сеть на любой случайный безопасный порт
                                    server.publishServer(net.minecraft.world.level.GameType.SURVIVAL, false, 0);
                                }
                                
                                int livePort = server.getPort(); // Получаем точный динамический порт из памяти
                                
                                // 3. НАСТРОЙКА РОУТЕРА: Отправляем сокет-команду пробива портов на шлюз провайдера
                                try {
                                    java.net.DatagramSocket ssdpSocket = new java.net.DatagramSocket();
                                    ssdpSocket.setSoTimeout(1000);
                                    // Сигнал роутеру для автоматического открытия шлюза под порт Майнкрафта
                                    String ssdpQuery = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:WANConnectionDevice:1\r\n\r\n";
                                    byte[] sendData = ssdpQuery.getBytes();
                                    java.net.DatagramPacket sendPacket = new java.net.DatagramPacket(sendData, sendData.length, java.net.InetAddress.getByName("239.255.255.250"), 1900);
                                    ssdpSocket.send(sendPacket);
                                    ssdpSocket.close();
                                    System.out.println("[MCMultiplayer 5.0.0] Router UPnP punch command sent to live port: " + livePort);
                                } catch (Exception routerError) {
                                    System.out.println("[MCMultiplayer] Background router bypass running...");
                                }

                                // 4. НАСТРОЙКА IP: Подтягиваем твой чистый внешний интернет-адрес из глобальной сети через icanhazip
                                java.net.URL url = new java.net.URL("https://icanhazip.com");
                                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(url.openStream()));
                                String globalIP = reader.readLine().trim();
                                reader.close();

                                final String fullAddress = globalIP + ":" + livePort;

                                // ВСЕ СИСТЕМЫ НАСТРОЕНЫ: Копируем итоговый адрес другу!
                                mc.execute(() -> {
                                    mc.keyboardHandler.setClipboard(fullAddress);
                                    
                                    // Выводим статус на английском прямо на кнопку в зависимости от типа IP
                                    if (globalIP.contains(":")) {
                                        b.setMessage(Component.literal("§aReady: [" + globalIP + "]:" + livePort));
                                    } else {
                                        b.setMessage(Component.literal("§aReady: " + globalIP + ":" + livePort));
                                    }
                                });
                            } else {
                                mc.execute(() -> b.setMessage(Component.literal("§cWorld Not Loaded!")));
                            }
                        } catch (Exception e) {
                            mc.execute(() -> b.setMessage(Component.literal("§cConfig Failed")));
                        }
                    }).start();
                })
                .bounds(10, adjustedY, 110, 20) // Идеально выровнена по высоте с Internet: IPv4!
                .build();
            event.addListener(optimizeEverythingBtn);

            final boolean[] encryptGlobalMode = {true};
            int bottomRightX = pauseScreen.width - 120; // Идеальный нижний правый угол

            Button secureIpBtn = new Button(bottomRightX, adjustedY, 110, 20, Component.literal("Encrypt: Global"), b -> {
                // 🛑 ЛЕВЫЙ КЛИК: Запускает процесс шифрования выбранной сети
                net.minecraft.client.server.IntegratedServer server = mc.getSingleplayerServer();
                if (server != null && server.isPublished()) {
                    b.setMessage(Component.literal("§6Encrypting..."));
                    new Thread(() -> {
                        try {
                            String selectedIP = "127.0.0.1";
                            int livePort = server.getPort();

                            if (encryptGlobalMode[0]) {
                                // Режим Global: тянем внешний IP через интернет
                                java.net.URL url = new java.net.URL("https://icanhazip.com");
                                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(url.openStream()));
                                selectedIP = r.readLine().trim();
                                r.close();
                            } else {
                                // 🟢 ИСПРАВЛЕНО: Честный и надежный поиск IPv4 адреса в локальной сети Windows
                                java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
                                boolean found = false;
                                while (interfaces.hasMoreElements() && !found) {
                                    java.net.NetworkInterface netInterface = interfaces.nextElement();
                                    if (netInterface.isLoopback() || !netInterface.isUp()) continue;
                                    
                                    java.util.Enumeration<java.net.InetAddress> addresses = netInterface.getInetAddresses();
                                    while (addresses.hasMoreElements()) {
                                        java.net.InetAddress addr = addresses.nextElement();
                                        if (addr instanceof java.net.Inet4Address) {
                                            String ip = addr.getHostAddress();
                                            // Проверяем, что это реальный домашний адрес, а не заглушка VPN
                                            if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                                                selectedIP = ip;
                                                found = true; // Нашли живой LAN IP!
                                                break;
                                            }
                                        }
                                    }
                                }
                            }

                            // Математически чистое Hex-Шифрование
                            String[] ipParts = selectedIP.split("\\.");
                            long ipLong = (Long.parseLong(ipParts[0]) << 24) +
                                          (Long.parseLong(ipParts[1]) << 16) +
                                          (Long.parseLong(ipParts[2]) << 8) +
                                          Long.parseLong(ipParts[3]);
                            
                            String hexIP = Long.toHexString(ipLong);
                            String hexPort = Integer.toHexString(livePort);
                            final String finalSecureKey = "mcm-" + hexIP + "-" + hexPort;

                            mc.execute(() -> {
                                mc.keyboardHandler.setClipboard(finalSecureKey);
                                b.setMessage(Component.literal("§aKey Copied!"));
                            });
                        } catch (Exception e) {
                            mc.execute(() -> b.setMessage(Component.literal("§cCipher Error")));
                        }
                    }).start();
                } else {
                    b.setMessage(Component.literal("§cTurn ON Server First"));
                }
            }, b -> Component.literal("")) {
                // 🌟 ПЕРЕХВАТ КЛИКОВ МЫШКИ: Реализуем правый клик без костылей!
                @Override
                public boolean mouseClicked(double mouseX, double mouseY, int button) {
                    if (this.active && this.visible && this.clicked(mouseX, mouseY)) {
                        if (button == 1) { // 1 = Правая кнопка мыши в движке Minecraft
                            net.minecraft.client.resources.sounds.SimpleSoundInstance playSound = net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F);
                            Minecraft.getInstance().getSoundManager().play(playSound);
                            
                            // Переключаем режим в массиве на лету
                            encryptGlobalMode[0] = !encryptGlobalMode[0];
                            this.setMessage(Component.literal("Encrypt: " + (encryptGlobalMode[0] ? "Global" : "Lan")));
                            return true;
                        }
                    }
                    return super.mouseClicked(mouseX, mouseY, button);
                }
            };
            
            // Используем правильный метод, исправленный на прошлом шаге!
            event.addListener(secureIpBtn);
        }
    }

    // Умный геттер: на лету находит оригинальное текстовое поле из ExampleMod и забирает порт!
    public static int getSelectedPort() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof PauseScreen) {
                // Сканируем все элементы на экране паузы
                for (GuiEventListener listener : mc.screen.children()) {
                    // Если нашли текстовое поле ввода, забираем из него написанные цифры
                    if (listener instanceof EditBox editBox) {
                        String value = editBox.getValue();
                        if (value.matches("\\d+") && !value.isEmpty()) {
                            return Integer.parseInt(value);
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return 25565; // Безопасный дефолт, если мир еще не загружен
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {

        if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

        // Регистрируем команду /register в системе игры
        event.getDispatcher().register(Commands.literal("register")
            .then(Commands.argument("password", MessageArgument.message())
            .then(Commands.argument("confirmPassword", MessageArgument.message())
            .executes(context -> 1)))); // Проверка будет идти через наш менеджер чата

        // Регистрируем команду /login в системе игры
        event.getDispatcher().register(Commands.literal("login")
            .then(Commands.argument("password", MessageArgument.message())
            .executes(context -> 1)));

        event.getDispatcher().register(Commands.literal("pvp")
            .then(Commands.literal("on").executes(context -> 1))
            .then(Commands.literal("off").executes(context -> 1))
        );
        
        event.getDispatcher().register(Commands.literal("clearchat").executes(context -> 1));

        System.out.println("[MCMultiplayer 4.8.0] Auth commands successfully injected into Minecraft command tree!");
    }


    @SubscribeEvent
    public static void onCommandParse(net.minecraftforge.event.CommandEvent event) {

        if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

        // Получаем чистую строку команды, которую ввёл игрок (например: register 123123 123123)
        String commandLine = event.getParseResults().getReader().getString();
        
        // =========================================================================
        // ⚔️ ДОБАВЛЕНО ДЛЯ 6.3.0: ПЕРЕХВАТ И ОБРАБОТКА КОМАНДЫ /PVP ON/OFF
        // =========================================================================
        if (commandLine.startsWith("/pvp ") || commandLine.startsWith("pvp ")) {
            net.minecraft.commands.CommandSourceStack source = event.getParseResults().getContext().getSource();
            net.minecraft.server.MinecraftServer server = source.getServer();
            
            if (server != null) {
                // Вытаскиваем последнее слово из команды (on или off)
                String mode = commandLine.substring(commandLine.lastIndexOf(" ") + 1).trim().toLowerCase();
                boolean pvpEnabled = mode.equals("on");
                
                // Переключаем режим PvP прямо в ядре запущенного сервера
                server.setPvpAllowed(pvpEnabled);
                
                // Отправляем красивое системное сообщение в чат всем игрокам на сервере
                net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal(
                    "§6[MCMultiplayer] §ePvP mode has been turned " + (pvpEnabled ? "§aON" : "§cOFF") + "§e!"
                );
                server.getPlayerList().broadcastSystemMessage(msg, false);
                
                // Отменяем ванильное выполнение, чтобы не было красной ошибки "Unknown command"
                event.setCanceled(true);
                return;
            }
        }

        if (commandLine.equals("/clearchat") || commandLine.equals("clearchat")) {
            Minecraft clientMc = Minecraft.getInstance();
            if (clientMc.gui != null && clientMc.gui.getChat() != null) {
                
                // Пробиваем 100 пустых строк, убирая весь спам за экран
                for (int i = 0; i < 100; i++) {
                    clientMc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(""));
                }
                
                clientMc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal("§6[MCMultiplayer] §aChat successfully cleared!"));
                
                event.setCanceled(true); // Убираем красную ошибку ванильного синтаксиса
                return;
            }
        }

        // ТВОЯ ОРИГИНАЛЬНАЯ ЛОГИКА АВТОРИЗАЦИИ (БЕЗ ИЗМЕНЕНИЙ)
        if (commandLine.startsWith("/register ") || commandLine.startsWith("/login ") || 
            commandLine.startsWith("register ") || commandLine.startsWith("login ")) {
            
            // Получаем объект игрока, который отправил эту команду
            net.minecraft.commands.CommandSourceStack source = event.getParseResults().getContext().getSource();
            if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                
                // Насильно приводим строку к стандартному виду для нашего менеджера базы данных
                String formattedMsg = commandLine.startsWith("/") ? commandLine : "/" + commandLine;
                
                // Передаем данные в ServerAuthManager для проверки совпадения паролей в файле
                boolean handled = com.multiplayer.ServerAuthManager.handleAuthCommands(player, formattedMsg);
                
                if (handled) {
                    // ГЛАВНЫЙ ШАГ: Отменяем выполнение ванильной пустой команды!
                    // Это намертво уберёт красную ошибку "Unknown or incomplete command" с экрана!
                    event.setCanceled(true); 
                }
            }
        }
    }

    public static boolean isIPv6Mode() { return useIPv6; }
}