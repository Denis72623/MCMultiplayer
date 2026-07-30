package com.multiplayer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.server.IntegratedServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.server.players.IpBanList;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.blaze3d.platform.InputConstants;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Inet4Address;
import java.util.Enumeration;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.Date;
import java.nio.charset.StandardCharsets;

@Mod(MCMultiplayerMod.MODID)
public class MCMultiplayerMod {
    public static final String MODID = "multiplayer"; 
    private static String myLocalIP = "Loading...";
    private static String myExternalIP = "Loading...";
    private static boolean upnpConfigured = false;
    private static int lastPort = -1;
    private static long copiedLabelTimer = 0;
    private static String lastCopiedType = "";
    private static boolean isIpVisible = false;
    private static String upnpStatus = "Waiting for LAN open...";
    private static long lastUpnpRefreshTime = 0;
    private static boolean useTCPMode = true; // true = TCP, false = UDP
    private static String cachedBaseControl = "";
    private static String cachedTargetService = "";
    private static String cachedLocalIP = "";
    private static String SERVER_NAME = "My Server"; // Название по умолчанию
    private EditBox serverNameEditBox; // Текстовое поле ввода для экрана ESC

    private EditBox portEditBox;
    private Button createServerButton;

    public MCMultiplayerMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT, com.multiplayer.ModConfig.SPEC, "multiplayer-client.toml");

        context.registerExtensionPoint(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class, 
            () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                (mc, screen) -> new com.multiplayer.ModConfigScreen(screen)
            )
        );
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                new Thread(() -> {
                    try {
                        String detectedIP = "127.0.0.1";
                        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                        while (interfaces.hasMoreElements()) {
                            NetworkInterface iface = interfaces.nextElement();
                            if (iface.isLoopback() || !iface.isUp()) continue;
                            Enumeration<InetAddress> addresses = iface.getInetAddresses();
                            while (addresses.hasMoreElements()) {
                                InetAddress addr = addresses.nextElement();
                                if (addr instanceof Inet4Address && !addr.getHostAddress().startsWith("169.254")) {
                                    detectedIP = addr.getHostAddress();
                                }
                            }
                        }
                        myLocalIP = detectedIP;
                    } catch (Exception e) { myLocalIP = "127.0.0.1"; }

                    try {
                        String[] ipServices = { "https://ipify.org", "https://amazonaws.com", "https://icanhazip.com" };
                        String detectedExternalIP = null;
                        for (String service : ipServices) {
                            try {
                                URL url = new URL(service);
                                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                                conn.setConnectTimeout(4000);
                                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                                String line = reader.readLine();
                                reader.close();
                                if (line != null && !line.trim().isEmpty() && !line.contains("<")) {
                                    detectedExternalIP = line.trim();
                                    break;
                                }
                            } catch (Exception e) {}
                        }
                        myExternalIP = (detectedExternalIP != null) ? detectedExternalIP : "Check Connection";
                    } catch (Exception e) { myExternalIP = "Error"; }
                }).start();
            }
        });
    }

    @SuppressWarnings("unused")
    private String maskIP(String ip) {
        if (ip == null || ip.contains("Loading") || ip.contains("Error") || ip.contains("Check")) return ip;
        try {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) return parts[0] + "." + parts[1] + ".***.***";
        } catch (Exception e) {}
        return "********";
    }

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {

        com.multiplayer.NetworkProtectionHandler.initNetwork();

        if (event.getScreen() instanceof PauseScreen pauseScreen) {
            Minecraft mc = Minecraft.getInstance();
            IntegratedServer server = mc.getSingleplayerServer();

            if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

            int xCenter = pauseScreen.width / 2;
            int yStart = pauseScreen.height / 4 + 144; 

            // Блок 1: Если сервер еще НЕ запущен (Вводим имя, порт, протокол)
            if (server != null && !server.isPublished()) {
                this.serverNameEditBox = new net.minecraft.client.gui.components.EditBox(mc.font, xCenter - 102, yStart - 168, 204, 20, Component.literal("Server Name"));
                this.serverNameEditBox.setValue(SERVER_NAME);
                this.serverNameEditBox.setResponder(text -> {
                    if (!text.isEmpty()) {
                        SERVER_NAME = text;
                    }
                });
                event.addListener(this.serverNameEditBox);

                this.portEditBox = new EditBox(mc.font, xCenter - 102, yStart, 40, 20, Component.literal("Port"));
                this.portEditBox.setValue("25565");
                this.portEditBox.setResponder(text -> {
                    if (!text.matches("\\d*") || text.length() > 5) {
                        String cleaned = text.replaceAll("[^\\d]", "");
                        if (cleaned.length() > 5) cleaned = cleaned.substring(0, 5);
                        if (!text.equals(cleaned)) this.portEditBox.setValue(cleaned);
                    }
                });
                event.addListener(this.portEditBox);

                Component protoText = Component.literal(useTCPMode ? "Protocol: TCP" : "Protocol: UDP");
                Button toggleProtoButton = Button.builder(protoText, button -> {
                    useTCPMode = !useTCPMode;
                    button.setMessage(Component.literal(useTCPMode ? "Protocol: TCP" : "Protocol: UDP"));
                })
                .bounds(xCenter - 58, yStart, 90, 20)
                .build();
                event.addListener(toggleProtoButton);

                this.createServerButton = Button.builder(Component.literal("Open WAN"), button -> {
                    int port = 25565;
                    try { port = Integer.parseInt(this.portEditBox.getValue()); } catch (NumberFormatException e) { this.portEditBox.setValue("25565"); }
                    openServerWithUPnP(port);
                    mc.setScreen(null);
                })
                .bounds(xCenter + 36, yStart, 85, 20)
                .build();
                event.addListener(this.createServerButton);
            } 
            // Блок 2: Если сервер УЖЕ запущен (Всегда показываем красивую широкую кнопку Make Global IP Server!)
            else if (server != null && server.isPublished()) {
                Button makeGlobalIpButton = Button.builder(Component.literal("Make Global IP Server"), button -> {
                    int activePort = server.getPort();
                    upnpConfigured = true;
                    lastPort = activePort;
                    openServerWithUPnP(activePort);
                    mc.setScreen(null); 
                })
                .bounds(xCenter - 102, yStart, 204, 20) 
                .build();
                event.addListener(makeGlobalIpButton);
            }
        }
    }

    private void openServerWithUPnP(int port) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();

        if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

        if (server != null) {
            upnpConfigured = true;
            lastPort = port;
            upnpStatus = useTCPMode ? "Connecting to gateway via TCP..." : "Searching router via UDP...";

            final int finalPort = port;

            // =======================================================================
            // БЛОК ОПТИМИЗАЦИИ КЭША 4.2.0 (БЫСТРЫЙ ВЫХОД БЕЗ ПОВТОРНОГО ПОИСКА)
            // =======================================================================
            if (!cachedBaseControl.isEmpty() && !cachedTargetService.isEmpty()) {
                new Thread(() -> {
                    try {
                        String soapBody = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"h" + "ttp://schemas.xml" + "soap.org/soap/envelope/\"><s:Body><u:AddPortMapping xmlns:u=\"" + cachedTargetService + "\"><NewRemoteHost></NewRemoteHost><NewExternalPort>" + finalPort + "</NewExternalPort><NewProtocol>TCP</NewProtocol><NewInternalPort>" + finalPort + "</NewInternalPort><NewInternalClient>" + cachedLocalIP + "</NewInternalClient><NewEnabled>1</NewEnabled><NewPortMappingDescription>Minecraft WAN Server</NewPortMappingDescription><NewLeaseDuration>3600</NewLeaseDuration></u:AddPortMapping></s:Body></s:Envelope>";
                        HttpURLConnection conn = (HttpURLConnection) new URL(cachedBaseControl).openConnection();
                        conn.setRequestMethod("POST");
                        conn.setDoOutput(true);
                        conn.setConnectTimeout(5000); 
                        conn.setReadTimeout(5000);
                        conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
                        conn.setRequestProperty("SOAPACTION", "\"" + cachedTargetService + "#AddPortMapping\"");
                        
                        OutputStream os = conn.getOutputStream();
                        os.write(soapBody.getBytes(StandardCharsets.UTF_8));
                        os.flush(); os.close();
                        
                        int respCode = conn.getResponseCode();
                        conn.disconnect(); // ПОЧИНКА ЧУЖИХ СЕРВЕРОВ: Мгновенно освобождаем сокет кэша

                        mc.execute(() -> {
                            try { Thread.sleep(200); } catch (Exception e) {} 
                            if (server.isPublished() || server.publishServer(GameType.SURVIVAL, false, finalPort)) {
                                boolean isOnlineMode = com.multiplayer.ModConfig.ONLINE_MODE.get();
                                server.setUsesAuthentication(isOnlineMode);
                                if (respCode == 200) {
                                    upnpStatus = "UPnP Success! Port " + finalPort + " Open.";
                                } else {
                                    upnpStatus = "LAN opened. Code: " + respCode;
                                }
                                com.mojang.authlib.GameProfile hostProfile = mc.getUser().getGameProfile();
                                if (hostProfile != null && !server.getPlayerList().isOp(hostProfile)) {
                                    server.getPlayerList().op(hostProfile);
                                }
                            } else {
                                upnpStatus = "Failed to open local socket.";
                            }
                        });
                    } catch (Exception e) {}
                }).start();
                return; // Выходим из метода, глубокий нижний поток поиска роутера даже не создается!
            }

            // =======================================================================
            // ГЛУБОКИЙ ПОИСК И ПРОБРОС РОУТЕРА С НУЛЯ (КОГДА КЭШ ЕЩЕ ПУСТОЙ)
            // =======================================================================
            new Thread(() -> {
                try {
                    String currentLocalIP = "127.0.0.1";
                    try {
                        java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
                        while (interfaces.hasMoreElements()) {
                            java.net.NetworkInterface iface = interfaces.nextElement();
                            String name = iface.getDisplayName().toLowerCase();
                            
                            // Пропускаем петлю, выключенные карты и весь виртуальный софт
                            if (iface.isLoopback() || !iface.isUp() || name.contains("virtual") || name.contains("vmware") || name.contains("vbox") || name.contains("hamachi") || name.contains("radmin")) {
                                continue;
                            }
                            
                            java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                            while (addresses.hasMoreElements()) {
                                java.net.InetAddress addr = addresses.nextElement();
                                if (addr instanceof java.net.Inet4Address) {
                                    String ip = addr.getHostAddress();
                                    // Нам нужен адрес, который выдан именно домашним роутером (НЕ .1 на конце)
                                    if (!ip.endsWith(".1")) {
                                        currentLocalIP = ip;
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        try { currentLocalIP = java.net.InetAddress.getLocalHost().getHostAddress(); } catch (Exception ex) {}
                    }
                    String locationUrl = "";

                    if (useTCPMode) {
                        String gatewayIP = "192.168.1.1"; 
                        try {
                            Process process = Runtime.getRuntime().exec("cmd.exe /c route print 0.0.0.0");
                            BufferedReader routeReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                            String routeLine;
                            while ((routeLine = routeReader.readLine()) != null) {
                                routeLine = routeLine.trim();
                                if (routeLine.startsWith("0.0.0.0")) {
                                    String[] tokens = routeLine.split("\\s+");
                                    if (tokens.length >= 3) { gatewayIP = tokens[2]; break; } 
                                }
                            }
                            routeReader.close();
                        } catch (Exception e) { gatewayIP = "192.168.1.1"; }

                        int[] commonPorts = {1900, 2869, 5431, 80, 8080, 49152, 49153, 52869};
                        String[] upnpPaths = {
                            "/rootDesc.xml", "/gatedesc.xml", "/description.xml", 
                            "/upnp/IGD.xml", "/ipcDesc.xml", "/wanipconnection.xml", "/DeviceDescription.xml"
                        };

                        longSearch:
                        for (int p : commonPorts) {
                            try (java.net.Socket testSocket = new java.net.Socket()) {
                                testSocket.connect(new java.net.InetSocketAddress(gatewayIP, p), 100);
                            } catch (Exception e) {
                                continue; 
                            }

                            for (String path : upnpPaths) {
                                try {
                                    String testUrl = "http://" + gatewayIP + ":" + p + path;
                                    URL url = new URL(testUrl);
                                    HttpURLConnection testConn = (HttpURLConnection) url.openConnection();
                                    testConn.setRequestMethod("GET");
                                    
                                    // ИСПРАВЛЕНО ДЛЯ 4.4.0: Срезали вечную загрузку до 3 секунд
                                    testConn.setConnectTimeout(3000); 
                                    testConn.setReadTimeout(3000);
                                    
                                    if (testConn.getResponseCode() == 200) { 
                                        locationUrl = testUrl; 
                                        testConn.disconnect(); // ПОЧИНКА ЧУЖИХ СЕРВЕРОВ: Закрываем временный сокет опроса путей
                                        break longSearch; 
                                    }
                                    testConn.disconnect();
                                } catch (Exception e) {}
                            }
                        }
                    } 
                    else {
                        String discoverQuery = "M-SEARCH * HTTP/1.1\r\n" +
                                "HOST: 239.255.255.250:1900\r\n" +
                                "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n" +
                                "MAN: \"ssdp:discover\"\r\n" +
                                "MX: 2\r\n\r\n";
                        
                        DatagramSocket socket = new DatagramSocket();
                        socket.setSoTimeout(10000); 
                        byte[] sendData = discoverQuery.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("239.255.255.250"), 1900);
                        socket.send(sendPacket);

                        byte[] recData = new byte[1024];
                        DatagramPacket recPacket = new DatagramPacket(recData, recData.length);
                        socket.receive(recPacket);
                        String response = new String(recPacket.getData(), 0, recPacket.getLength());
                        socket.close(); // UDP сокет поиска роутера успешно закрыт

                        for (String line : response.split("\r\n")) {
                            if (line.toUpperCase().startsWith("LOCATION:")) { locationUrl = line.substring(9).trim(); break; }
                        }
                    }

                    if (!locationUrl.isEmpty()) {
                        URL urlXml = new URL(locationUrl);
                        HttpURLConnection xmlConn = (HttpURLConnection) urlXml.openConnection();
                        xmlConn.setConnectTimeout(3000);
                        xmlConn.setReadTimeout(3000);
                        
                        BufferedReader in = new BufferedReader(new InputStreamReader(xmlConn.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder xmlContent = new StringBuilder();
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) { xmlContent.append(inputLine); }
                        in.close();
                        xmlConn.disconnect(); // ПОЧИНКА ЧУЖИХ СЕРВЕРОВ: Мгновенно закрываем сокет после скачивания XML разметки роутера

                        String xml = xmlContent.toString();
                        String controlPath = "";
                        String targetService = "urn:schemas-upnp-org:service:WANIPConnection:1";
                        if (!xml.contains(targetService)) {
                            targetService = "urn:schemas-upnp-org:service:WANPPPConnection:1";
                        }
                        
                        if (xml.contains(targetService)) {
                            int serviceIdx = xml.indexOf(targetService);
                            int controlIdx = xml.indexOf("<controlURL>", serviceIdx);
                            if (controlIdx != -1) {
                                int closeIdx = xml.indexOf("</controlURL>", controlIdx);
                                controlPath = xml.substring(controlIdx + 12, closeIdx).trim();
                            }
                        }

                        if (controlPath.isEmpty()) {
                            controlPath = xml.contains("WANPPPConnection") ? "/ctl/PPPConn" : "/ctl/IPConn";
                        }

                        String baseControl = new URL(urlXml, controlPath).toString();

                        // СОХРАНЯЕМ ДАННЫЕ В КЭШ ДЛЯ БУДУЩИХ МГНОВЕННЫХ СТАРТОВ
                        cachedBaseControl = baseControl;
                        cachedTargetService = targetService;
                        cachedLocalIP = currentLocalIP;

                        // Блок удаления старого правила (SOAP Delete)
                        try {
                            String deleteBody = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"h" + "ttp://schemas.xml" + "soap.org/soap/envelope/\" s:encodingStyle=\"h" + "ttp://schemas.xml" + "soap.org/soap/encoding/\"><s:Body><u:DeletePortMapping xmlns:u=\"" + targetService + "\"><NewRemoteHost></NewRemoteHost><NewExternalPort>" + finalPort + "</NewExternalPort><NewProtocol>TCP</NewProtocol></u:DeletePortMapping></s:Body></s:Envelope>";
                            HttpURLConnection delConn = (HttpURLConnection) new URL(baseControl).openConnection();
                            delConn.setRequestMethod("POST");
                            delConn.setDoOutput(true);
                            delConn.setConnectTimeout(3000);
                            delConn.setReadTimeout(3000);
                            delConn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
                            delConn.setRequestProperty("SOAPACTION", "\"" + targetService + "#DeletePortMapping\"");
                            OutputStream delOs = delConn.getOutputStream();
                            delOs.write(deleteBody.getBytes(StandardCharsets.UTF_8));
                            delOs.flush(); delOs.close();
                            delConn.getResponseCode();
                            delConn.disconnect(); // ПОЧИНКА ЧУЖИХ СЕРВЕРОВ: Закрываем сокет удаления старого правила
                        } catch (Exception e) {}

                        // Формирование и отправка SOAP-пакета на добавление порта
                        String soapBody = "<?xml version=\"1.0\"?>" +
                                "<s:Envelope xmlns:s=\"h" + "ttp://schemas.xml" + "soap.org/soap/envelope/\">" +
                                "<s:Body>" +
                                "<u:AddPortMapping xmlns:u=\"" + targetService + "\">" +
                                "<NewRemoteHost></NewRemoteHost><NewExternalPort>" + finalPort + "</NewExternalPort><NewProtocol>TCP</NewProtocol><NewInternalPort>" + finalPort + "</NewInternalPort><NewInternalClient>" + currentLocalIP + "</NewInternalClient><NewEnabled>1</NewEnabled><NewPortMappingDescription>Minecraft WAN Server</NewPortMappingDescription><NewLeaseDuration>3600</NewLeaseDuration></u:AddPortMapping>" +
                                "</s:Body>" +
                                "</s:Envelope>";
                        
                        HttpURLConnection conn = (HttpURLConnection) new URL(baseControl).openConnection();
                        conn.setRequestMethod("POST");
                        conn.setDoOutput(true);
                        conn.setConnectTimeout(3000);
                        conn.setReadTimeout(3000);
                        conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
                        conn.setRequestProperty("SOAPACTION", "\"" + targetService + "#AddPortMapping\"");
                        
                        OutputStream os = conn.getOutputStream();
                        os.write(soapBody.getBytes(StandardCharsets.UTF_8));
                        os.flush(); os.close();

                        int respCode = conn.getResponseCode();
                        conn.disconnect(); // ПОЧИНКА ЧУЖИХ СЕРВЕРОВ: Полностью закрываем главный сокет! Сеть для игры свободна.
                        
                        mc.execute(() -> {
                            try {
                                Thread.sleep(200); 
                            } catch (Exception e) {}

                            boolean published = server.publishServer(GameType.SURVIVAL, false, finalPort);
                            if (published) {
                                boolean isOnlineMode = com.multiplayer.ModConfig.ONLINE_MODE.get();
                                server.setUsesAuthentication(isOnlineMode);

                                if (respCode == 200) {
                                upnpStatus = "UPnP Success! Port " + finalPort + " Open.";
                                } else if (respCode == 500) {
                                // Умное уведомление для версии 4.4.0
                                upnpStatus = "Port " + finalPort + " Blocked (Code 500). Change Port!";
                                } else {
                                upnpStatus = "LAN opened. Router response: " + respCode;
                            }
                                com.mojang.authlib.GameProfile hostProfile = mc.getUser().getGameProfile();
                                if (hostProfile != null && !server.getPlayerList().isOp(hostProfile)) {
                                    server.getPlayerList().op(hostProfile);
                                }
                            } else {
                                upnpStatus = "Failed to open local socket.";
                            }
                        });
                    } else {
                        mc.execute(() -> {
                            boolean isOnlineMode = com.multiplayer.ModConfig.ONLINE_MODE.get();
                            server.setUsesAuthentication(isOnlineMode);
                            server.publishServer(GameType.SURVIVAL, false, finalPort);
                            server.setUsesAuthentication(isOnlineMode);
                            upnpStatus = useTCPMode ? "TCP Error: Router description not found." : "UDP Error: Router did not respond.";
                        });
                    }
                } catch (Exception e) {
                    mc.execute(() -> {
                        boolean isOnlineMode = com.multiplayer.ModConfig.ONLINE_MODE.get();
                        server.setUsesAuthentication(isOnlineMode);
                        server.publishServer(GameType.SURVIVAL, false, finalPort);
                        server.setUsesAuthentication(isOnlineMode);
                        upnpStatus = "Network Error: " + e.getMessage();
                    });
                }
            }).start();
        }
    }

    @SubscribeEvent
    public void onScreenRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof PauseScreen) {
            Minecraft mc = Minecraft.getInstance();
            IntegratedServer server = mc.getSingleplayerServer();
            int currentPort = (server != null && server.isPublished()) ? server.getPort() : -1;

            if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;
            
            String localText = "Local IP (For those nearby): " + (isIpVisible ? myLocalIP : "§b[HIDDEN]") + (currentPort != -1 ? ":" + currentPort : "");
            String externalText = "Global IP (For other countries): " + (isIpVisible ? myExternalIP : "§b[HIDDEN]") + (currentPort != -1 ? ":" + currentPort : "");

            event.getGuiGraphics().drawString(mc.font, localText, 2, 10, 0xFFFFFF);
            event.getGuiGraphics().drawString(mc.font, externalText, 2, 24, 0xFFFFFF);

            // Сдвинуто на координату Y = 66, чтобы не накладываться на статус глобального сервера
            if (System.currentTimeMillis() - copiedLabelTimer < 3000) {
                event.getGuiGraphics().drawString(mc.font, lastCopiedType + " IP copied to clipboard!", 2, 66, 0xFFFF55);
            }

            if (server != null && server.isPublished()) {
                // Перезаписываем статус только если нет активного процесса UPnP (Поиск, Ошибка, Успех)
                if (!upnpConfigured || lastPort != currentPort) {
                    upnpConfigured = true;
                    lastPort = currentPort;
                    if (!upnpStatus.contains("Search") && !upnpStatus.contains("Success") && !upnpStatus.contains("Failed") && !upnpStatus.contains("Rejected") && !upnpStatus.contains("Error")) {
                        upnpStatus = "Server active on port: " + currentPort;
                    }
                }
                
                // Рисуем ванильный статус UPnP (Y = 38)
                int statusColor = (upnpStatus.contains("Failed") || upnpStatus.contains("Error") || upnpStatus.contains("Rejected") || upnpStatus.contains("Rejected") || upnpStatus.contains("Error")) ? 0xFF5555 : 0x55FF55;
                event.getGuiGraphics().drawString(mc.font, "UPnP Status: " + upnpStatus, 2, 38, statusColor);

                // ОБНОВЛЕНИЕ 4.0.0: Строка визуального статуса глобального сервера (Y = 52)
                String globalServerText = "Global Server: " + upnpStatus;

                // Делаем динамический цвет: если успех — зелёный, если ждёт — жёлтый, если ошибка — красный
                int globalColor = upnpStatus.contains("Success") ? 0x55FF55 : (upnpStatus.contains("Error") ? 0xFF5555 : 0xFFFF55);

                if (upnpStatus.contains("Success")) {
                    globalServerText = "Global Server: Available to the whole world!";
                    globalColor = 0x55FF55; // Зелёный
                } else if (upnpStatus.contains("Failed") || upnpStatus.contains("Error") || upnpStatus.contains("Rejected") || upnpStatus.contains("Rejected") || upnpStatus.contains("Error")) {
                    globalServerText = "Global Server: Closed (External access blocked)";
                    globalColor = 0xFF5555; // Красный
                }
                
                event.getGuiGraphics().drawString(mc.font, globalServerText, 2, 52, globalColor);

                String globalServerIpStatusText = "Global Server IP Status: " + (upnpStatus.contains("Success") ? "§aSUCCESS (Port Open)" : "§cFAILED (Blocked)");
                
                // Оставляем цвет синхронным со всей веткой
                int globalStatusIpColor = upnpStatus.contains("Success") ? 0x55FF55 : (upnpStatus.contains("Error") ? 0xFF5555 : 0xFFFF55);
                
                // Отрисовка на твоей координате Y = 66
                event.getGuiGraphics().drawString(mc.font, globalServerIpStatusText, 2, 80, globalStatusIpColor);


            } else {
                upnpConfigured = false;
                lastPort = -1;
            }

            // Твоя оригинальная логика бесконечного удержания WAN-порта (Keep-Alive)
            if (server != null && server.isPublished() && upnpStatus.contains("Success")) {
                long currentTime = System.currentTimeMillis();
                // 1800000 миллисекунд = 30 минут
                if (lastUpnpRefreshTime == 0) {
                    lastUpnpRefreshTime = currentTime;
                } else if (currentTime - lastUpnpRefreshTime > 1800000) {
                    lastUpnpRefreshTime = currentTime;
                    // Автоматически отправляем повторный запрос роутеру, чтобы сбросить его таймер закрытия
                    final int activePort = server.getPort();
                    new Thread(() -> {
                        try {
                            openServerWithUPnP(activePort);
                        } catch (Exception e) {}
                    }).start();
                }
            } else {
                lastUpnpRefreshTime = 0;
            }
        }
    }

    @SubscribeEvent
    public void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {

        if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

        if (event.getScreen() instanceof PauseScreen) {
            Minecraft mc = Minecraft.getInstance();
            IntegratedServer server = mc.getSingleplayerServer();
            if (event.getKeyCode() == InputConstants.KEY_H) isIpVisible = !isIpVisible;
            
            if (server != null && server.isPublished()) {
                String suffix = ":" + server.getPort();
                if (event.getKeyCode() == InputConstants.KEY_C) {
                    mc.keyboardHandler.setClipboard(myLocalIP + suffix);
                    lastCopiedType = "Local";
                    copiedLabelTimer = System.currentTimeMillis();
                }
                if (event.getKeyCode() == InputConstants.KEY_V && !myExternalIP.contains("Load") && !myExternalIP.contains("Err")) {
                    mc.keyboardHandler.setClipboard(myExternalIP + suffix);
                    lastCopiedType = "Global";
                    copiedLabelTimer = System.currentTimeMillis();
                }
            }
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
    
        if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("ban").requires(s -> s.hasPermission(4))
            .then(Commands.argument("target", StringArgumentType.string())
                .executes(c -> { executeBan(StringArgumentType.getString(c, "target"), "You have been banned from the server via MCMultiplayer"); return 1; })
                .then(Commands.argument("reason", StringArgumentType.greedyString())
                    .executes(c -> { executeBan(StringArgumentType.getString(c, "target"), StringArgumentType.getString(c, "reason")); return 1; }))));

        dispatcher.register(Commands.literal("ban-ip").requires(s -> s.hasPermission(4))
            .then(Commands.argument("target-ip", StringArgumentType.string())
                .executes(c -> {
                    String t = StringArgumentType.getString(c, "target-ip");
                    IntegratedServer srv = Minecraft.getInstance().getSingleplayerServer();
                    if (srv != null && srv.getPlayerList().getPlayerByName(t) != null) t = srv.getPlayerList().getPlayerByName(t).getIpAddress();
                    executeBanIP(cleanIPAddress(t), "IP Banned");
                    return 1;
                })));

        dispatcher.register(Commands.literal("pardon").requires(s -> s.hasPermission(4))
            .then(Commands.argument("target", StringArgumentType.string()).executes(c -> {
                String t = StringArgumentType.getString(c, "target");
                IntegratedServer srv = Minecraft.getInstance().getSingleplayerServer();
                if (srv != null) {
                    GameProfile p = new GameProfile(java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + t).getBytes(StandardCharsets.UTF_8)), t);
                    if (srv.getPlayerList().getBans().isBanned(p)) srv.getPlayerList().getBans().remove(p);
                }
                return 1;
            })));

        dispatcher.register(
            net.minecraft.commands.Commands.literal("wanrefresh")
                .requires(source -> source.hasPermission(4)) // Доступ строго хосту
                .executes(context -> {
                    // Вызываем метод сброса кэша и перезапуска UPnP
                    Wanrefresh(context.getSource(), context.getSource().getServer());
                    return 1;
                })
        );

        dispatcher.register(
            net.minecraft.commands.Commands.literal("wanstatus")
                .requires(source -> source.hasPermission(4)) // Доступ только для хоста (админа)
                .executes(context -> {
                    // ИСПРАВЛЕНИЕ: Вызываем метод и передаем ему источник команды
                    Wanstatus(context.getSource());
                    return 1;
                })
        );


        dispatcher.register(Commands.literal("pardon-ip").requires(s -> s.hasPermission(4))
            .then(Commands.argument("ip", StringArgumentType.string()).executes(c -> {
                String ip = cleanIPAddress(StringArgumentType.getString(c, "ip"));
                IntegratedServer srv = Minecraft.getInstance().getSingleplayerServer();
                if (srv != null && srv.getPlayerList().getIpBans().isBanned(ip)) srv.getPlayerList().getIpBans().remove(ip);
                return 1;
            })));
    }

    private static String cleanIPAddress(String ip) {

        String c = ip;
        if (c.contains("/")) c = c.substring(c.indexOf("/") + 1);
        if (c.contains(":")) {
            String[] splitParts = c.split(":");
            c = splitParts[0]; // ИСПРАВЛЕНИЕ: Берем строковый элемент массива
        }
        return c.trim();
    }

    private static void executeBan(String user, String r) {

        if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

        IntegratedServer srv = Minecraft.getInstance().getSingleplayerServer();
        if (srv != null) {
            GameProfile p = new GameProfile(java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + user).getBytes(StandardCharsets.UTF_8)), user);
            ServerPlayer pl = srv.getPlayerList().getPlayerByName(user);
            if (pl != null) p = pl.getGameProfile();
            // ИСПРАВЛЕНО: Убран лишний вызов .getServer()
            srv.getPlayerList().getBans().add(new UserBanListEntry(p, new Date(), "Host", null, r));
            if (pl != null) pl.connection.disconnect(Component.literal(r));
        }
    }

    private static void Wanstatus(net.minecraft.commands.CommandSourceStack source) {

        if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

        // Определяем режим, который сейчас выбран кнопкой на ESC
        String mode = useTCPMode ? "TCP (Manual Port Scan)" : "UDP (SSDP Discovery)";
        
        // Подбираем цвет для статуса роутера
        String statusColor = "§c"; // Красный при ошибке
        if (upnpStatus.contains("Success")) {
            statusColor = "§a"; // Зелёный при успехе
        } else if (upnpStatus.contains("Поиск") || upnpStatus.contains("Searching") || upnpStatus.contains("TCP...")) {
            statusColor = "§e"; // Жёлтый при ожидании
        }
        
        // Собираем текст статуса
        String text = "§b[WAN Status v4.2.0]\n" +
                "§7- Local IP (LAN): §e" + myLocalIP + ":" + lastPort + "\n" +
                "§7- Global IP (WAN): §e" + myExternalIP + ":" + lastPort + "\n" +
                "§7- Protocol Mode: §f" + mode + "\n" +
                "§7- UPnP Status: " + statusColor + upnpStatus + "\n" +
                "§7- Router Cache: §f" + (cachedBaseControl.isEmpty() ? "None (Slow Mode)" : "Saved (Instant Mode)");
                
        // ВЫВОДИМ ПРЯМО В ЧАТ: Используем стандартный метод отправки для 1.20.1 Forge
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(text), false);
    }

    private void Wanrefresh(net.minecraft.commands.CommandSourceStack source, net.minecraft.server.MinecraftServer server) {

        if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

        // Очищаем кэш версии 4.2.0 для принудительного глубокого пробития роутера
        cachedBaseControl = "";
        cachedTargetService = "";
        cachedLocalIP = "";
        
        // Отправляем цветное уведомление хосту в чат
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("§b[WAN Mod] §aRouter cache cleared! Re-mapping ports via network..."), false);
        
        // Автоматически перезапускаем UPnP на текущем активном порту игры
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            // openServerWithUPnP вызовется на том же порту, который использовался
            openServerWithUPnP(lastPort > 0 ? lastPort : 25565);
        }
    }

    private static void executeBanIP(String ip, String r) {

        if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

        IntegratedServer srv = Minecraft.getInstance().getSingleplayerServer();
        if (srv != null) {
            // ИСПРАВЛЕНО: Убран лишний вызов .getServer()
            srv.getPlayerList().getIpBans().add(new IpBanListEntry(ip, new Date(), "Host", null, r));
            for (ServerPlayer pl : srv.getPlayerList().getPlayers()) {
                if (cleanIPAddress(pl.getIpAddress()).equals(ip)) pl.connection.disconnect(Component.literal(r));
            }
        }
    }
}