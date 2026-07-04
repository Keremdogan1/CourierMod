package com.rpsunucusu.courier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import java.util.Comparator;
import java.util.stream.Collectors;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.Box;

import java.lang.reflect.Method;
import java.util.Iterator;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CourierMod implements ModInitializer {
    public static final Identifier SYNC_LOCATIONS = new Identifier("couriermod", "sync_locations");
    public static net.minecraft.server.MinecraftServer serverInstance;

    private static CourierMod instance;
    public static CourierMod getInstance() {
        return instance;
    }

    private static final String P = "\u00a76[Kurye] ";
    private static final String AP = "\u00a76[Kurye-Admin] ";
    private static final double MIN_UCRET = 5.0;

    private static final File DATA_FILE = new File("config/kurye_data.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DataModel data = new DataModel();
    private Map<UUID, PlayerMission> activeMissions = new HashMap<>();
    private Random random = new Random();

    private static final String PARA_OBJECTIVE = "para";
    private final MinecraftServer[] serverRef = new MinecraftServer[1];

    private final List<String> activityLog = new ArrayList<>();

    public static class PlayerCallRequest {
        public java.util.UUID playerId;
        public String playerName;
        public String type; // "KURYE" or "TAKSI"
        public long timestamp;
        public LocationData location;
        public LocationData targetLocation;
        public PlayerCallRequest(java.util.UUID id, String name, String type, long time, LocationData loc, LocationData targetLoc) {
            this.playerId = id; this.playerName = name; this.type = type; this.timestamp = time; this.location = loc; this.targetLocation = targetLoc;
        }
    }

    public static class JobOffer {
        public String jobId;
        public String type;
        public boolean isPlayerRequest;
        public PlayerCallRequest playerRequest;
        public MissionPair npcMissionPair;
        public LocationData npcTaksiPickup;
        public LocationData npcTaksiDropoff;
        public long expireTime;
    }

    private java.util.List<PlayerCallRequest> callRequests = new java.util.ArrayList<>();
    private java.util.Map<String, JobOffer> availableOffers = new java.util.HashMap<>();


    public static class LocationData {
        public String name;
        public int x, y, z;
        public String world;
        public LocationData(String name, int x, int y, int z, String world) {
            this.name = name; this.x = x; this.y = y; this.z = z; this.world = world;
        }
    }

    public static class PlayerStats {
        public int kuryeLevel = 1;
        public double kuryeXp = 0;
        public int taksiLevel = 1;
        public double taksiXp = 0;
    }

    public static class DataModel {
        public List<LocationData> dagitimNoktalari = new ArrayList<>();
        public List<LocationData> musteriNoktalari = new ArrayList<>();
        public List<LocationData> taksiNoktalari = new ArrayList<>();
        public List<String> ipucuKapatanlar = new ArrayList<>();
        public Map<String, PlayerStats> playerStats = new HashMap<>();
        public double kuryeCarpan = 0.1;
        public double taksiCarpan = 0.1;
    }

    public DataModel getData() {
        return data;
    }

    public static class PlayerMission {
        public String type = "KURYE"; // "KURYE" veya "TAKSI"
        public String state;
        public LocationData dagitimLoc;
        public LocationData musteriLoc;
    public boolean isPlayerJob;
    public UUID customerId;
        public UUID taxiVillagerId = null;
        public int ticksAtTarget = 0;
        public long missionStartTime = System.currentTimeMillis();
    }

    private static final String[] TIPS = {
        "\u00a76\u00a7l\u0130pucu: \u00a7eYolunu bulam\u0131yor musun? Sohbetteki \u00a7bKonum \u00a7eyaz\u0131s\u0131na t\u0131klayarak \u00a7bWaypoint \u00a7eolu\u015fturabilirsin!",
        "\u00a76\u00a7l\u0130pucu: \u00a7eMesafeye g\u00f6re kazan\u00e7 sa\u011flars\u0131n. Her \u00a7b10 metrede \u00a7e1\u20ba \u00a7ekazan\u0131rs\u0131n!",
        "\u00a76\u00a7l\u0130pucu: \u00a7eBu ipu\u00e7lar\u0131n\u0131 kapatmak i\u00e7in \u00a7b/kurye ipucu \u00a7eyazabilirsin."
    };

    public static class MissionPair {
        public LocationData dagitim;
        public LocationData musteri;
        public MissionPair(LocationData dagitim, LocationData musteri) {
            this.dagitim = dagitim;
            this.musteri = musteri;
        }
    }

    public void logActivity(String message) {
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String logEntry = "[" + timestamp + "] " + message;
        synchronized (activityLog) {
            activityLog.add(logEntry);
        }
        System.out.println("[CourierMod-Log] " + logEntry);
    }

    @Override
    public void onInitialize() {
        instance = this;
        loadData();
        logActivity("Kurye Modu yuklendi. Sunucu baslatiliyor...");
        CommandRegistrationCallback.EVENT.register(this::registerCommands);
        FabricPlaceholderRegistry.register(this, serverRef);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverInstance = server;
            serverRef[0] = server;
            
            // Clean up old sidebar objective if it exists to avoid conflicts
            try {
                Scoreboard sb = server.getScoreboard();
                ScoreboardObjective oldSidebar = sb.getNullableObjective("courier_sidebar");
                if (oldSidebar != null) {
                    sb.removeObjective(oldSidebar);
                    System.out.println("[CourierMod] Cleaned up legacy vanilla sidebar objective.");
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }

            boolean isTabLoaded = FabricLoader.getInstance().isModLoaded("tab");
            if (isTabLoaded && !tabPlaceholdersRegistered) {
                try {
                    System.out.println("[CourierMod] Server started. Registering TAB placeholders...");
                    TabPlaceholderRegistry.register(this, serverRef, activeMissions, this::getPlayerParaPublic);
                    tabPlaceholdersRegistered = true;
                    System.out.println("[CourierMod] TAB Placeholders registered successfully!");
                } catch (Throwable t) {
                    System.err.println("[CourierMod] Failed to register TAB placeholders on SERVER_STARTED: " + t.getMessage());
                    t.printStackTrace();
                }
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof net.minecraft.server.network.ServerPlayerEntity p) {
                // If the player is a taxi driver in an automobility car
                if (p.hasVehicle() && net.minecraft.entity.EntityType.getId(p.getVehicle().getType()).toString().contains("automobility")) {
                    if (activeMissions.containsKey(p.getUuid())) {
                        PlayerMission pm = activeMissions.get(p.getUuid());
                        if (pm.type.equals("TAKSI")) return false;
                    }
                    for (PlayerMission pm : activeMissions.values()) {
                        if (pm.type.equals("TAKSI") && pm.isPlayerJob && p.getUuid().equals(pm.customerId)) return false;
                    }
                }
                // If the player is the customer riding the taxi driver
                if (p.hasVehicle() && p.getVehicle() instanceof net.minecraft.server.network.ServerPlayerEntity driver) {
                    if (activeMissions.containsKey(driver.getUuid())) {
                        PlayerMission pm = activeMissions.get(driver.getUuid());
                        if (pm.type.equals("TAKSI") && p.getUuid().equals(pm.customerId)) return false;
                    }
                }
            }
            return true;
        });
        
        
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            syncLocationsToPlayer(handler.player);
        });

        System.out.println("[CourierMod] Loaded successfully (1.5.0) - JourneyMap Client Edition!");
    }

    private void loadData() {
        if (DATA_FILE.exists()) {
            try (FileReader reader = new FileReader(DATA_FILE)) {
                Type type = new TypeToken<DataModel>(){}.getType();
                data = GSON.fromJson(reader, type);
                if (data == null) data = new DataModel();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void syncLocationsToAll() {
        if (serverInstance == null) return;
        String json = new Gson().toJson(data);
        for (ServerPlayerEntity p : serverInstance.getPlayerManager().getPlayerList()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(json, 327670);
            ServerPlayNetworking.send(p, SYNC_LOCATIONS, buf);
        }
        syncTaksiNoktalariToAll();
    }

    public void syncLocationsToPlayer(ServerPlayerEntity p) {
        String json = new Gson().toJson(data);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(json, 327670);
        ServerPlayNetworking.send(p, SYNC_LOCATIONS, buf);
        syncTaksiNoktalariToPlayer(p);
    }

    public void syncTaksiNoktalariToAll() {
        if (serverInstance == null) return;
        String json = new Gson().toJson(data.taksiNoktalari);
        for (ServerPlayerEntity p : serverInstance.getPlayerManager().getPlayerList()) {
            syncTaksiNoktalariToPlayer(p);
        }
    }

    public void syncTaksiNoktalariToPlayer(ServerPlayerEntity p) {
        String json = new Gson().toJson(data.taksiNoktalari);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(json, 327670);
        ServerPlayNetworking.send(p, new Identifier("couriermod", "taksi_sync"), buf);
    }

    private void saveData() {
        try {
            DATA_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(DATA_FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // === Scoreboard Helpers ===

    private ScoreboardObjective getOrCreateObjective(Scoreboard scoreboard, String name, String displayName) {
        ScoreboardObjective obj = scoreboard.getNullableObjective(name);
        if (obj == null) {
            obj = scoreboard.addObjective(name, ScoreboardCriterion.DUMMY,
                Text.literal(displayName), ScoreboardCriterion.RenderType.INTEGER);
        }
        return obj;
    }

    private void ensureParaObjective(MinecraftServer server) {
        getOrCreateObjective(server.getScoreboard(), PARA_OBJECTIVE, "\u00a7e\ud83d\udcb0 Para");
    }

    private int getPlayerPara(MinecraftServer server, ServerPlayerEntity p) {
        try {
            Class<?> playerDataStateClass = Class.forName("com.example.secretid.PlayerDataState");
            Method getServerStateMethod = playerDataStateClass.getMethod("getServerState", MinecraftServer.class);
            Object stateInstance = getServerStateMethod.invoke(null, server);
            Method getBalanceMethod = playerDataStateClass.getMethod("getBalance", java.util.UUID.class);
            return ((Double) getBalanceMethod.invoke(stateInstance, p.getUuid())).intValue();
        } catch (Exception e) {
            Scoreboard sb = server.getScoreboard();
            ScoreboardObjective obj = sb.getNullableObjective(PARA_OBJECTIVE);
            if (obj == null) return 0;
            String name = p.getGameProfile().getName();
            if (!sb.playerHasObjective(name, obj)) return 0;
            return sb.getPlayerScore(name, obj).getScore();
        }
    }

    public int getPlayerParaPublic(MinecraftServer server, ServerPlayerEntity p) {
        return getPlayerPara(server, p);
    }

    private void addPlayerPara(MinecraftServer server, ServerPlayerEntity p, int amount) {
        try {
            Class<?> playerDataStateClass = Class.forName("com.example.secretid.PlayerDataState");
            Method getServerStateMethod = playerDataStateClass.getMethod("getServerState", MinecraftServer.class);
            Object stateInstance = getServerStateMethod.invoke(null, server);
            Method addBalanceMethod = playerDataStateClass.getMethod("addBalance", java.util.UUID.class, double.class);
            addBalanceMethod.invoke(stateInstance, p.getUuid(), (double) amount);
        } catch (Exception e) {
            Scoreboard sb = server.getScoreboard();
            ensureParaObjective(server);
            ScoreboardObjective obj = sb.getNullableObjective(PARA_OBJECTIVE);
            if (obj == null) return;
            ScoreboardPlayerScore score = sb.getPlayerScore(p.getGameProfile().getName(), obj);
            score.setScore(score.getScore() + amount);
        }
    }

    private void updateSidebar(MinecraftServer server, ServerPlayerEntity p) {
        Scoreboard sb = server.getScoreboard();
        String sidebarName = "courier_sidebar";
        ScoreboardObjective sidebar = sb.getNullableObjective(sidebarName);

        if (sidebar != null) {
            sb.removeObjective(sidebar);
        }

        sidebar = sb.addObjective(sidebarName, ScoreboardCriterion.DUMMY,
            Text.literal("\u00a76\u00a7l\u2b50 RP Sunucusu"), ScoreboardCriterion.RenderType.INTEGER);
        sb.setObjectiveSlot(1, sidebar);

        int para = getPlayerPara(server, p);
        PlayerMission pm = activeMissions.get(p.getUuid());

        if (pm != null) {
            sb.getPlayerScore("\u00a78\u00a7m              ", sidebar).setScore(7);
            sb.getPlayerScore("\u00a7e\ud83d\udcb0 " + para + "\u20ba", sidebar).setScore(6);
            sb.getPlayerScore("\u00a7r", sidebar).setScore(5);
            sb.getPlayerScore("\u00a7a\ud83d\udce6 Kurye G\u00f6revi", sidebar).setScore(4);
            String stateText = pm.state.equals("TOPLAMA") ? "\u00a7ePaketi Al" : "\u00a7eTeslim Et";
            sb.getPlayerScore("  \u00a77" + stateText, sidebar).setScore(3);
            String hedef = pm.state.equals("TOPLAMA") ? pm.dagitimLoc.name : pm.musteriLoc.name;
            sb.getPlayerScore("  \u00a7b\u27a4 " + hedef, sidebar).setScore(2);
            sb.getPlayerScore("\u00a7r\u00a7r", sidebar).setScore(1);
            sb.getPlayerScore("\u00a78\u00a7m               ", sidebar).setScore(0);
        } else {
            sb.getPlayerScore("\u00a78\u00a7m              ", sidebar).setScore(4);
            sb.getPlayerScore("\u00a7e\ud83d\udcb0 " + para + "\u20ba", sidebar).setScore(3);
            sb.getPlayerScore("\u00a7r", sidebar).setScore(2);
            sb.getPlayerScore("\u00a77/kurye al \u00a78- g\u00f6rev al", sidebar).setScore(1);
            sb.getPlayerScore("\u00a78\u00a7m               ", sidebar).setScore(0);
        }
    }

    // === Commands ===

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("kurye")
            .executes(this::helpCommand)
            .then(CommandManager.literal("al").executes(this::takeMission))
            .then(CommandManager.literal("iptal").executes(this::cancelMission))
            .then(CommandManager.literal("ipucu").executes(this::toggleIpucu))
            .then(CommandManager.literal("log")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(this::showLog))
            .then(CommandManager.literal("wp")
                .then(CommandManager.literal("dagitim").executes(this::getDagitimWp))
                .then(CommandManager.literal("musteri").executes(this::getMusteriWp)))
            .then(CommandManager.literal("admin")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(this::adminHelpCommand)
                .then(CommandManager.literal("dagitim-ekle").then(CommandManager.argument("isim", StringArgumentType.string()).executes(this::addDagitim)))
                .then(CommandManager.literal("musteri-ekle").then(CommandManager.argument("isim", StringArgumentType.string()).executes(this::addMusteri)))
                .then(CommandManager.literal("dagitim-sil").then(CommandManager.argument("isim", StringArgumentType.string()).executes(this::deleteDagitim)))
                .then(CommandManager.literal("musteri-sil").then(CommandManager.argument("isim", StringArgumentType.string()).executes(this::deleteMusteri)))
                .then(CommandManager.literal("listele").executes(this::listPoints))
                .then(CommandManager.literal("carpan")
                    .executes(this::showKuryeCarpan)
                    .then(CommandManager.argument("deger", DoubleArgumentType.doubleArg(0.01, 100.0)).executes(this::setKuryeCarpan))
                )
                .then(CommandManager.literal("carpan-sifirla").executes(this::resetKuryeCarpan))
                .then(CommandManager.literal("siralama-sifirla").executes(this::resetKuryeSiralama))
            )
            .then(CommandManager.literal("siralama").executes(this::showKuryeSiralama))
            .then(CommandManager.literal("cagir").executes(this::playerKuryeCagir))
            .then(CommandManager.literal("iptal-cagri").executes(this::cancelCallKurye))
        );

        dispatcher.register(CommandManager.literal("taksi")
            .executes(this::taksiHelpCommand)
            .then(CommandManager.literal("al").executes(this::takeTaksiMission))
            .then(CommandManager.literal("iptal").executes(this::cancelMission))
            .then(CommandManager.literal("ipucu").executes(this::toggleIpucu))
            .then(CommandManager.literal("admin")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(this::taksiAdminHelpCommand)
                .then(CommandManager.literal("nokta-ekle").then(CommandManager.argument("isim", StringArgumentType.string()).executes(this::addTaksiNokta)))
                .then(CommandManager.literal("nokta-sil").then(CommandManager.argument("isim", StringArgumentType.string()).executes(this::deleteTaksiNokta)))
                .then(CommandManager.literal("listele").executes(this::listTaksiPoints))
                .then(CommandManager.literal("carpan")
                    .executes(this::showTaksiCarpan)
                    .then(CommandManager.argument("deger", DoubleArgumentType.doubleArg(0.01, 100.0)).executes(this::setTaksiCarpan))
                )
                .then(CommandManager.literal("carpan-sifirla").executes(this::resetTaksiCarpan))
                .then(CommandManager.literal("siralama-sifirla").executes(this::resetTaksiSiralama))
            )
            .then(CommandManager.literal("siralama").executes(this::showTaksiSiralama))
            .then(CommandManager.literal("cagir")
                .executes(this::listTaksiHedefleri)
                .then(CommandManager.argument("hedef_adi", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(this::playerTaksiCagir)))
            .then(CommandManager.literal("iptal-cagri").executes(this::cancelCallTaksi))
        );
    }

    private int helpCommand(CommandContext<ServerCommandSource> context) {
        ServerCommandSource src = context.getSource();
        src.sendMessage(Text.literal(P + "\u00a7e/kurye al \u00a77- Yeni bir teslimat g\u00f6revi al\u0131r."));
        src.sendMessage(Text.literal(P + "\u00a7e/kurye iptal \u00a77- Mevcut g\u00f6revi iptal eder."));
        src.sendMessage(Text.literal(P + "\u00a7e/kurye ipucu \u00a77- \u0130pu\u00e7lar\u0131n\u0131 a\u00e7ar/kapat\u0131r."));
        src.sendMessage(Text.literal(P + "\u00a7e/kurye siralama \u00a77- En iyi kuryeleri listeler."));
        if (src.hasPermissionLevel(2)) {
            src.sendMessage(Text.literal(P + "\u00a7c/kurye log \u00a77- Aktivite loglarini listeler ve kaydeder."));
            src.sendMessage(Text.literal(P + "\u00a7c/kurye admin \u00a77- Admin yardim menusu."));
        }
        return 1;
    }

    private int showLog(CommandContext<ServerCommandSource> context) {
        ServerCommandSource src = context.getSource();
        File logFile = new File("config/kurye_activity.log");
        try {
            logFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(logFile)) {
                synchronized (activityLog) {
                    for (String log : activityLog) {
                        writer.write(log + "\n");
                    }
                }
            }
            src.sendMessage(Text.literal(P + "\u00a7aTum loglar \u00a7econfig/kurye_activity.log \u00a7adosyasina kaydedildi."));
            logActivity((src.getPlayer() != null ? src.getPlayer().getGameProfile().getName() : "Console") + " kurye loglarini kaydetti.");
        } catch (IOException e) {
            src.sendMessage(Text.literal(P + "\u00a7cLog dosyasi olusturulamadi: " + e.getMessage()));
            e.printStackTrace();
        }

        src.sendMessage(Text.literal(P + "\u00a7e--- Son 15 Aktivite Logu ---"));
        synchronized (activityLog) {
            int size = activityLog.size();
            int start = Math.max(0, size - 15);
            for (int i = start; i < size; i++) {
                src.sendMessage(Text.literal("\u00a77" + activityLog.get(i)));
            }
        }
        return 1;
    }

    private int adminHelpCommand(CommandContext<ServerCommandSource> context) {
        ServerCommandSource src = context.getSource();
        src.sendMessage(Text.literal("\u00a7m---------\u00a7r " + AP + " \u00a7m---------"));
        src.sendMessage(Text.literal("\u00a7e/kurye admin dagitim-ekle <isim> \u00a77- Da\u011f\u0131t\u0131m noktas\u0131 ekler."));
        src.sendMessage(Text.literal("\u00a7e/kurye admin musteri-ekle <isim> \u00a77- M\u00fc\u015fteri noktas\u0131 ekler."));
        src.sendMessage(Text.literal("\u00a7e/kurye admin dagitim-sil <isim> \u00a77- Da\u011f\u0131t\u0131m noktas\u0131 siler."));
        src.sendMessage(Text.literal("\u00a7e/kurye admin musteri-sil <isim> \u00a77- M\u00fc\u015fteri noktas\u0131 siler."));
        src.sendMessage(Text.literal("\u00a7e/kurye admin listele \u00a77- T\u00fcm noktalar\u0131 listeler."));
        src.sendMessage(Text.literal("\u00a7e/kurye admin carpan [<deger>] \u00a77- Ekonomi \u00e7arpan\u0131n\u0131 g\u00f6sterir/ayarlar."));
        src.sendMessage(Text.literal("\u00a7e/kurye admin carpan-sifirla \u00a77- Ekonomi \u00e7arpan\u0131n\u0131 varsay\u0131lana (0.1) d\u00f6nd\u00fcr\u00fcr."));
        src.sendMessage(Text.literal("\u00a7e/kurye admin siralama-sifirla \u00a77- T\u00fcm kuryelerin seviye ve xp verilerini s\u0131f\u0131rlar."));
        return 1;
    }

    private int showKuryeCarpan(CommandContext<ServerCommandSource> context) {
        context.getSource().sendMessage(Text.literal(AP + "\u00a7eKurye \u00e7arpan\u0131 \u015fu an: \u00a7a" + data.kuryeCarpan + " \u00a77(Metre ba\u015f\u0131 kazan\u00e7)"));
        return 1;
    }
    private int setKuryeCarpan(CommandContext<ServerCommandSource> context) {
        double d = DoubleArgumentType.getDouble(context, "deger");
        data.kuryeCarpan = d;
        saveData();
        context.getSource().sendMessage(Text.literal(AP + "\u00a7aKurye \u00e7arpan\u0131 ba\u015far\u0131yla g\u00fcncellendi: \u00a7e" + d));
        logActivity(context.getSource().getName() + " kurye carpanini " + d + " yapti.");
        return 1;
    }
    private int resetKuryeCarpan(CommandContext<ServerCommandSource> context) {
        data.kuryeCarpan = 0.1;
        saveData();
        context.getSource().sendMessage(Text.literal(AP + "\u00a7aKurye \u00e7arpan\u0131 s\u0131f\u0131rland\u0131 (\u00a7e0.1\u00a7a)."));
        return 1;
    }
    private int showKuryeSiralama(CommandContext<ServerCommandSource> context) {
        ServerCommandSource src = context.getSource();
        src.sendMessage(Text.literal("\u00a76\u00a7m--------\u00a7r \u00a7e\u2b50 Kurye S\u0131ralamas\u0131 \u2b50 \u00a76\u00a7m--------"));
        List<Map.Entry<String, PlayerStats>> sorted = data.playerStats.entrySet().stream()
            .sorted(Map.Entry.<String, PlayerStats>comparingByValue(Comparator.comparingInt((PlayerStats s) -> s.kuryeLevel).reversed()
            .thenComparingDouble(s -> -s.kuryeXp)))
            .collect(Collectors.toList());
        int rank = 1;
        String myUuid = src.getPlayer() != null ? src.getPlayer().getUuidAsString() : "";
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            Map.Entry<String, PlayerStats> entry = sorted.get(i);
            PlayerStats stats = entry.getValue();
            if (stats.kuryeLevel <= 1 && stats.kuryeXp == 0) continue;
            
            String playerName = "Bilinmiyor";
            ServerPlayerEntity target = src.getServer().getPlayerManager().getPlayer(UUID.fromString(entry.getKey()));
            if (target != null) {
                playerName = target.getGameProfile().getName();
            } else {
                java.util.Optional<com.mojang.authlib.GameProfile> profile = src.getServer().getUserCache().getByUuid(UUID.fromString(entry.getKey()));
                if (profile.isPresent()) {
                    playerName = profile.get().getName();
                } else {
                    playerName = "Oyuncu (" + entry.getKey().substring(0, 5) + ")";
                }
            }
            String prefix = (entry.getKey().equals(myUuid)) ? "\u00a7a" : "\u00a7e";
            src.sendMessage(Text.literal("\u00a76" + rank + ". " + prefix + playerName + " \u00a77- Seviye: \u00a7b" + stats.kuryeLevel + " \u00a77(XP: " + (int)stats.kuryeXp + ")"));
            rank++;
        }
        return 1;
    }
    private int resetKuryeSiralama(CommandContext<ServerCommandSource> context) {
        for (PlayerStats stats : data.playerStats.values()) {
            stats.kuryeLevel = 1;
            stats.kuryeXp = 0;
        }
        saveData();
        context.getSource().sendMessage(Text.literal(AP + "\u00a7cT\u00fcm kurye s\u0131ralamas\u0131 s\u0131f\u0131rland\u0131!"));
        logActivity(context.getSource().getName() + " kurye siralamasini sifirladi.");
        return 1;
    }

    private int addDagitim(CommandContext<ServerCommandSource> context) {
        String isim = StringArgumentType.getString(context, "isim");
        if (context.getSource().getPlayer() == null) return 0;
        ServerPlayerEntity p = context.getSource().getPlayer();
        BlockPos pos = p.getBlockPos();
        data.dagitimNoktalari.add(new LocationData(isim, pos.getX(), pos.getY(), pos.getZ(), p.getWorld().getRegistryKey().getValue().toString()));
        saveData();
        p.sendMessage(Text.literal(AP + "\u00a7aDa\u011f\u0131t\u0131m noktas\u0131 eklendi: \u00a7e" + isim));
        logActivity(p.getGameProfile().getName() + " dagitim noktasi ekledi: " + isim + " (" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")");
        return 1;
    }

    private int addMusteri(CommandContext<ServerCommandSource> context) {
        String isim = StringArgumentType.getString(context, "isim");
        if (context.getSource().getPlayer() == null) return 0;
        ServerPlayerEntity p = context.getSource().getPlayer();
        BlockPos pos = p.getBlockPos();
        data.musteriNoktalari.add(new LocationData(isim, pos.getX(), pos.getY(), pos.getZ(), p.getWorld().getRegistryKey().getValue().toString()));
        saveData();
        p.sendMessage(Text.literal(AP + "\u00a7aM\u00fc\u015fteri noktas\u0131 eklendi: \u00a7e" + isim));
        logActivity(p.getGameProfile().getName() + " musteri noktasi ekledi: " + isim + " (" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")");
        return 1;
    }

    private int listPoints(CommandContext<ServerCommandSource> context) {
        ServerCommandSource src = context.getSource();
        src.sendMessage(Text.literal(AP + "\u00a7e--- Da\u011f\u0131t\u0131m Noktalar\u0131 ---"));
        for (int i = 0; i < data.dagitimNoktalari.size(); i++) {
            LocationData l = data.dagitimNoktalari.get(i);
            src.sendMessage(Text.literal("\u00a77" + (i + 1) + ": \u00a7f" + l.name + " \u00a78(" + l.x + "," + l.y + "," + l.z + ")"));
        }
        src.sendMessage(Text.literal(AP + "\u00a7e--- M\u00fc\u015fteri Noktalar\u0131 ---"));
        for (int i = 0; i < data.musteriNoktalari.size(); i++) {
            LocationData l = data.musteriNoktalari.get(i);
            src.sendMessage(Text.literal("\u00a77" + (i + 1) + ": \u00a7f" + l.name + " \u00a78(" + l.x + "," + l.y + "," + l.z + ")"));
        }
        return 1;
    }

    private int deleteDagitim(CommandContext<ServerCommandSource> context) {
        String isim = StringArgumentType.getString(context, "isim");
        ServerPlayerEntity p = context.getSource().getPlayer();
        
        LocationData found = null;
        for (LocationData loc : data.dagitimNoktalari) {
            if (loc.name.equalsIgnoreCase(isim)) {
                found = loc;
                break;
            }
        }
        
        if (found != null) {
            data.dagitimNoktalari.remove(found);
            saveData();
            if (p != null) {
                p.sendMessage(Text.literal(AP + "\u00a7aDa\u011f\u0131t\u0131m noktas\u0131 silindi: \u00a7e" + isim));
            }
            logActivity((p != null ? p.getGameProfile().getName() : "Console") + " dagitim noktasi sildi: " + isim);
            return 1;
        } else {
            if (p != null) {
                p.sendMessage(Text.literal(AP + "\u00a7cDa\u011f\u0131t\u0131m noktas\u0131 bulunamad\u0131: \u00a7e" + isim));
            }
            return 0;
        }
    }

    private int deleteMusteri(CommandContext<ServerCommandSource> context) {
        String isim = StringArgumentType.getString(context, "isim");
        ServerPlayerEntity p = context.getSource().getPlayer();
        
        LocationData found = null;
        for (LocationData loc : data.musteriNoktalari) {
            if (loc.name.equalsIgnoreCase(isim)) {
                found = loc;
                break;
            }
        }
        
        if (found != null) {
            data.musteriNoktalari.remove(found);
            saveData();
            if (p != null) {
                p.sendMessage(Text.literal(AP + "\u00a7aM\u00fc\u015fteri noktas\u0131 silindi: \u00a7e" + isim));
            }
            logActivity((p != null ? p.getGameProfile().getName() : "Console") + " musteri noktasi sildi: " + isim);
            return 1;
        } else {
            if (p != null) {
                p.sendMessage(Text.literal(AP + "\u00a7cM\u00fc\u015fteri noktas\u0131 bulunamad\u0131: \u00a7e" + isim));
            }
            return 0;
        }
    }

    private int getDagitimWp(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity p = context.getSource().getPlayer();
        if (p == null) return 0;
        PlayerMission pm = activeMissions.get(p.getUuid());
        if (pm == null) {
            p.sendMessage(Text.literal(P + "\u00a7cAktif bir g\u00f6revin yok."));
            return 0;
        }
        LocationData dLoc = pm.dagitimLoc;
        String jmLink = String.format("[name:\"%s\", x:%d, y:%d, z:%d]", dLoc.name, dLoc.x, dLoc.y, dLoc.z);
        p.sendMessage(Text.literal(P + "\u00a7eDa\u011f\u0131t\u0131m noktas\u0131 waypoint'i olu\u015fturmak i\u00e7in t\u0131klay\u0131n: \u00a7b" + jmLink));
        return 1;
    }

    private int getMusteriWp(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity p = context.getSource().getPlayer();
        if (p == null) return 0;
        PlayerMission pm = activeMissions.get(p.getUuid());
        if (pm == null) {
            p.sendMessage(Text.literal(P + "\u00a7cAktif bir g\u00f6revin yok."));
            return 0;
        }
        LocationData mLoc = pm.musteriLoc;
        String jmLink = String.format("[name:\"%s\", x:%d, y:%d, z:%d]", mLoc.name, mLoc.x, mLoc.y, mLoc.z);
        p.sendMessage(Text.literal(P + "\u00a7eM\u00fc\u015fteri noktas\u0131 waypoint'i olu\u015fturmak i\u00e7in t\u0131klay\u0131n: \u00a7b" + jmLink));
        return 1;
    }

    // === Mission Logic ===

    private int taksiBitir(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity p = context.getSource().getPlayer();
        if (p == null) return 0;
        PlayerMission pm = activeMissions.get(p.getUuid());
        if (pm == null || !pm.type.equals("TAKSI")) { p.sendMessage(Text.literal("§6[Taksi] §cAktif bir taksi göreviniz yok!")); return 0; }
        if (pm.isPlayerJob && pm.customerId != null) {
            ServerPlayerEntity customer = p.getServer().getPlayerManager().getPlayer(pm.customerId);
            if (customer != null) {
                double distSq = p.getBlockPos().getSquaredDistance(customer.getBlockPos());
                if (distSq <= 100.0) {
                    double totalDist = Math.sqrt(Math.pow(pm.dagitimLoc.x - customer.getBlockPos().getX(), 2) + Math.pow(pm.dagitimLoc.z - customer.getBlockPos().getZ(), 2));
                    double ucret = Math.floor(totalDist * data.taksiCarpan);
                    if (ucret < MIN_UCRET) ucret = MIN_UCRET;
                    p.getServer().getCommandManager().executeWithPrefix(p.getServer().getCommandSource(), "eco take " + customer.getName().getString() + " " + (int) ucret);
                    customer.sendMessage(net.minecraft.text.Text.literal("§6[Taksi] §cTaksi ücreti olarak " + (int) ucret + " kesildi."));
                    addPlayerPara(p.getServer(), p, (int) ucret);
                    addXp(p, "TAKSI", 50.0);
                    activeMissions.remove(p.getUuid());
                    customer.stopRiding();
                            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket(p));
                    customer.sendMessage(Text.literal("§6[Taksi] §aHedefe ulaştınız. Yolculuk bitti."));
                    p.sendMessage(Text.literal("§6[Taksi] §aMüşteriyi hedefine ulaştırdınız. Kazanılan: " + (int) ucret + "TL"));
                } else {
                    p.sendMessage(Text.literal("§6[Taksi] §cMüşteriye yeterince yakın değilsiniz!"));
                }
            } else {
                p.sendMessage(Text.literal("§6[Taksi] §cMüşteri çevrimiçi değil, görev iptal edildi."));
                activeMissions.remove(p.getUuid());
            }
        } else {
            p.sendMessage(Text.literal("§6[Taksi] §cBu görev bir NPC görevi, hedef noktaya giderek otomatik bitirebilirsiniz."));
        }
        return 1;
    }


    private int takeMission(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity p = context.getSource().getPlayer();
        if (p == null) return 0;
        if (activeMissions.containsKey(p.getUuid())) {
            p.sendMessage(Text.literal(P + "§cZaten aktif bir görevin var (Kurye veya Taksi)!"));
            return 0;
        }
        if (data.dagitimNoktalari.isEmpty() || data.musteriNoktalari.isEmpty()) {
            p.sendMessage(Text.literal(P + "§cKurye sistemi için en az 1 dağıtım ve 1 müşteri noktası gereklidir!"));
            return 0;
        }
        new JobMenuGui(p, "KURYE", data, activeMissions, callRequests).open();
        return 1;
    }

    private int playerKuryeCagir(CommandContext<net.minecraft.server.command.ServerCommandSource> context) {
        try {
            net.minecraft.server.network.ServerPlayerEntity p = context.getSource().getPlayer();
            if (p == null) return 0;
            for (PlayerCallRequest req : callRequests) {
                if (req.playerId.equals(p.getUuid()) && req.type.equals("KURYE")) {
                    p.sendMessage(net.minecraft.text.Text.literal(P + "§cZaten aktif bir kurye isteğiniz var!"));
                    return 0;
                }
            }
            int para = getPlayerPara(context.getSource().getServer(), p);
            if (para < MIN_UCRET) {
                p.sendMessage(net.minecraft.text.Text.literal(P + "§cYetersiz bakiye! Kurye çağırmak için en az " + (int)MIN_UCRET + " TL gerekiyor."));
                return 0;
            }
            LocationData loc = new LocationData(p.getGameProfile().getName() + " Konumu", p.getBlockPos().getX(), p.getBlockPos().getY(), p.getBlockPos().getZ(), p.getWorld().getRegistryKey().getValue().toString());
            callRequests.add(new PlayerCallRequest(p.getUuid(), p.getGameProfile().getName(), "KURYE", System.currentTimeMillis(), loc, null));
            p.sendMessage(net.minecraft.text.Text.literal(P + "§aİsteğiniz kuryelere iletildi, en kısa sürede biri kabul edecektir."));
            return 1;
        } catch (Exception e) {
            if (context.getSource().getPlayer() != null) {
                context.getSource().getPlayer().sendMessage(net.minecraft.text.Text.literal("§cHata (Kurye Çağır): " + e.toString()));
            }
            e.printStackTrace();
            return 0;
        }
    }

    private int listTaksiHedefleri(CommandContext<net.minecraft.server.command.ServerCommandSource> context) {
        net.minecraft.server.network.ServerPlayerEntity p = context.getSource().getPlayer();
        if (p == null) return 0;
        if (data.taksiNoktalari.isEmpty()) {
            p.sendMessage(net.minecraft.text.Text.literal("§6[Taksi] §cHeniç taksi noktası yok."));
            return 0;
        }
        
        syncTaksiNoktalariToPlayer(p);
        
        PacketByteBuf buf = PacketByteBufs.create();
        ServerPlayNetworking.send(p, new Identifier("couriermod", "open_taksi_map"), buf);
        return 1;
    }

    private int playerTaksiCagir(CommandContext<net.minecraft.server.command.ServerCommandSource> context) {
        try {
            net.minecraft.server.network.ServerPlayerEntity p = context.getSource().getPlayer();
            if (p == null) return 0;
            String hedefAdi = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "hedef_adi");
            LocationData hedef = null;
            for (LocationData loc : data.taksiNoktalari) {
                if (loc.name.equalsIgnoreCase(hedefAdi)) { hedef = loc; break; }
            }
            if (hedef == null) {
                p.sendMessage(net.minecraft.text.Text.literal("§6[Taksi] §cBelirtilen hedef bulunamadı!"));
                return 0;
            }
            for (PlayerCallRequest req : callRequests) {
                if (req.playerId.equals(p.getUuid()) && req.type.equals("TAKSI")) {
                    p.sendMessage(net.minecraft.text.Text.literal("§6[Taksi] §cZaten aktif bir taksi isteğiniz var!"));
                    return 0;
                }
            }
            double dist = Math.sqrt(Math.pow(p.getBlockPos().getX() - hedef.x, 2) + Math.pow(p.getBlockPos().getZ() - hedef.z, 2));
            double ucret = Math.max(MIN_UCRET, Math.floor(dist * data.taksiCarpan));
            int para = getPlayerPara(context.getSource().getServer(), p);
            if (para < ucret) {
                p.sendMessage(net.minecraft.text.Text.literal("§6[Taksi] §cYetersiz bakiye! Bu yolculuk " + (int)ucret + " TL tutuyor, sende " + para + " TL var."));
                return 0;
            }
            addPlayerPara(context.getSource().getServer(), p, -(int)ucret);
            p.sendMessage(net.minecraft.text.Text.literal("§6[Taksi] §aEmanet olarak " + (int)ucret + " TL hesabınızdan kesildi."));
            LocationData loc = new LocationData(p.getGameProfile().getName() + " Konumu", p.getBlockPos().getX(), p.getBlockPos().getY(), p.getBlockPos().getZ(), p.getWorld().getRegistryKey().getValue().toString());
            callRequests.add(new PlayerCallRequest(p.getUuid(), p.getGameProfile().getName(), "TAKSI", System.currentTimeMillis(), loc, hedef));
            
            String msgText = "§6[Taksi] §aİsteğiniz taksicilere iletildi (Tahmini Ücret: " + (int)ucret + " TL).";
            PacketByteBuf bufSuccess = PacketByteBufs.create();
            bufSuccess.writeString(msgText);
            ServerPlayNetworking.send(p, new net.minecraft.util.Identifier("couriermod", "taksi_request_success"), bufSuccess);
        
            return 1;
        } catch (Exception e) {
            if (context.getSource().getPlayer() != null) {
                context.getSource().getPlayer().sendMessage(net.minecraft.text.Text.literal("§cHata (Taksi Çağır): " + e.toString()));
            }
            e.printStackTrace();
            return 0;
        }
    }

    private int acceptKurye(CommandContext<net.minecraft.server.command.ServerCommandSource> context) {
        net.minecraft.server.network.ServerPlayerEntity p = context.getSource().getPlayer();
        if (p == null) return 0;
        String jobId = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "jobId");
        JobOffer offer = availableOffers.get(jobId);
        if (offer == null || !offer.type.equals("KURYE")) { p.sendMessage(net.minecraft.text.Text.literal(P + "§cBu iş artık mevcut değil.")); return 0; }
        if (activeMissions.containsKey(p.getUuid())) { p.sendMessage(net.minecraft.text.Text.literal(P + "§cZaten aktif bir göreviniz var.")); return 0; }
        PlayerMission pm = new PlayerMission();
        pm.type = "KURYE";
        pm.missionStartTime = System.currentTimeMillis();
        if (offer.isPlayerRequest) {
            pm.isPlayerJob = true;
            pm.customerId = offer.playerRequest.playerId;
            pm.musteriLoc = offer.playerRequest.location;
            LocationData closestHub = null;
            double minD = Double.MAX_VALUE;
            for(LocationData hub : data.dagitimNoktalari) {
                double d = p.getBlockPos().getSquaredDistance(hub.x, p.getBlockPos().getY(), hub.z);
                if (d < minD) { minD = d; closestHub = hub; }
            }
            pm.dagitimLoc = closestHub != null ? closestHub : data.dagitimNoktalari.get(0);
            p.sendMessage(net.minecraft.text.Text.literal(P + "§aBir oyuncunun kurye isteğini kabul ettiniz. Önce paketi almak için Dağıtım Merkezine gidin."));
            net.minecraft.server.network.ServerPlayerEntity customer = p.getServer().getPlayerManager().getPlayer(pm.customerId);
            if (customer != null) customer.sendMessage(net.minecraft.text.Text.literal(P + "§aKuryeniz yola çıktı!"));
            callRequests.remove(offer.playerRequest);
        } else {
            pm.isPlayerJob = false;
            pm.dagitimLoc = offer.npcMissionPair.dagitim;
            pm.musteriLoc = offer.npcMissionPair.musteri;
            p.sendMessage(net.minecraft.text.Text.literal(P + "§aNPC görevini kabul ettiniz. Dağıtım Merkezine gidin."));
        }
        activeMissions.put(p.getUuid(), pm);
        availableOffers.remove(jobId);
        return 1;
    }

    private int acceptTaksi(CommandContext<net.minecraft.server.command.ServerCommandSource> context) {
        net.minecraft.server.network.ServerPlayerEntity p = context.getSource().getPlayer();
        if (p == null) return 0;
        String jobId = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "jobId");
        JobOffer offer = availableOffers.get(jobId);
        if (offer == null || !offer.type.equals("TAKSI")) { p.sendMessage(net.minecraft.text.Text.literal("§6[Taksi] §cBu iş artık mevcut değil.")); return 0; }
        if (activeMissions.containsKey(p.getUuid())) { p.sendMessage(net.minecraft.text.Text.literal("§6[Taksi] §cZaten aktif bir göreviniz var.")); return 0; }
        PlayerMission pm = new PlayerMission();
        pm.type = "TAKSI";
        pm.missionStartTime = System.currentTimeMillis();
        if (offer.isPlayerRequest) {
            pm.isPlayerJob = true;
            pm.customerId = offer.playerRequest.playerId;
            pm.dagitimLoc = offer.playerRequest.location;
            pm.musteriLoc = offer.playerRequest.targetLocation;
            p.sendMessage(net.minecraft.text.Text.literal("§6[Taksi] §aBir oyuncunun taksi isteğini kabul ettiniz. Müşteriyi almaya gidin."));
            net.minecraft.server.network.ServerPlayerEntity customer = p.getServer().getPlayerManager().getPlayer(pm.customerId);
            if (customer != null) customer.sendMessage(net.minecraft.text.Text.literal("§6[Taksi] §aTaksiniz yola çıktı!"));
            callRequests.remove(offer.playerRequest);
        } else {
            pm.isPlayerJob = false;
            pm.dagitimLoc = offer.npcTaksiPickup;
            pm.musteriLoc = offer.npcTaksiDropoff;
            p.sendMessage(net.minecraft.text.Text.literal("§6[Taksi] §aNPC taksi görevini kabul ettiniz. Durağa gidin."));
        }
        activeMissions.put(p.getUuid(), pm);
        availableOffers.remove(jobId);
        return 1;
    }

    private int cancelMission(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity p = context.getSource().getPlayer();
        if (p == null) return 0;

        if (activeMissions.containsKey(p.getUuid())) {
            PlayerMission pm = activeMissions.get(p.getUuid());
            if (pm.type.equals("TAKSI") && pm.taxiVillagerId != null) {
                if (p.getWorld() instanceof ServerWorld) {
                    Entity v = ((ServerWorld)p.getWorld()).getEntity(pm.taxiVillagerId);
                    if (v != null) v.discard();
                }
            }
            activeMissions.remove(p.getUuid());
            p.getInventory().remove(s -> s.isOf(Items.PAPER) && s.hasCustomName() && s.getName().getString().contains("M\u00fc\u015fteri Paketi"), 1, p.getInventory());
            p.sendMessage(Text.literal(P + "\u00a7cG\u00f6rev iptal edildi."));
            
            if (pm.isPlayerJob && pm.customerId != null) {
                ServerPlayerEntity customer = context.getSource().getServer().getPlayerManager().getPlayer(pm.customerId);
                if (customer != null) {
                    if (pm.type.equals("TAKSI")) {
                        customer.stopRiding();
                            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket(p));
                    }
                    customer.sendMessage(Text.literal(pm.type.equals("TAKSI") ? "§6[Taksi] §cTaksiciniz görevi iptal etti! Lütfen tekrar çağırın." : P + "§cKuryeniz görevi iptal etti! Lütfen tekrar çağırın."));
                }
            }
            
            logActivity(p.getGameProfile().getName() + " aktif gorevini iptal etti.");
        } else {
            p.sendMessage(Text.literal(P + "\u00a7cAktif bir g\u00f6revin yok."));
        }
        return 1;
    }

    private int cancelCall(CommandContext<ServerCommandSource> context, String type) {
        ServerPlayerEntity p = context.getSource().getPlayer();
        if (p == null) return 0;
        
        // 1. Check if unaccepted call
        PlayerCallRequest targetReq = null;
        for (PlayerCallRequest req : callRequests) {
            if (req.playerId.equals(p.getUuid()) && req.type.equals(type)) {
                targetReq = req;
                break;
            }
        }
        
        long currentTime = System.currentTimeMillis();
        
        if (targetReq != null) {
            long elapsed = currentTime - targetReq.timestamp;
            if (elapsed < 60000) {
                p.sendMessage(Text.literal((type.equals("TAKSI") ? "§6[Taksi] §c" : P + "§c") + "Siparişinizin üzerinden henüz 1 dakika geçmediği için iptal edemezsiniz!"));
                return 0;
            }
            callRequests.remove(targetReq);
            p.sendMessage(Text.literal((type.equals("TAKSI") ? "§6[Taksi] §a" : P + "§a") + "Bekleyen çağrınız başarıyla iptal edildi."));
            return 1;
        }
        
        // 2. Check if accepted mission
        PlayerMission targetMission = null;
        java.util.UUID courierId = null;
        for (java.util.Map.Entry<java.util.UUID, PlayerMission> entry : activeMissions.entrySet()) {
            PlayerMission pm = entry.getValue();
            if (pm.isPlayerJob && pm.type.equals(type) && p.getUuid().equals(pm.customerId)) {
                targetMission = pm;
                courierId = entry.getKey();
                break;
            }
        }
        
        if (targetMission != null) {
            long elapsed = currentTime - targetMission.missionStartTime;
            if (elapsed < 60000) {
                p.sendMessage(Text.literal((type.equals("TAKSI") ? "§6[Taksi] §c" : P + "§c") + "Kuryeniz/Taksiciniz görevi kabul edeli henüz 1 dakika bile olmadı! Biraz daha beklemelisiniz."));
                return 0;
            }
            
            if (courierId != null) {
                activeMissions.remove(courierId);
                ServerPlayerEntity courier = context.getSource().getServer().getPlayerManager().getPlayer(courierId);
                if (courier != null) {
                    courier.sendMessage(Text.literal((type.equals("TAKSI") ? "§6[Taksi] §c" : P + "§c") + "Müşteri görevi iptal etti!"));
                    courier.getInventory().remove(s -> s.isOf(Items.PAPER) && s.hasCustomName() && s.getName().getString().contains("M\u00fc\u015fteri Paketi"), 1, courier.getInventory());
                    if (targetMission.type.equals("TAKSI") && targetMission.taxiVillagerId != null) {
                        if (courier.getWorld() instanceof net.minecraft.server.world.ServerWorld) {
                            net.minecraft.entity.Entity v = ((net.minecraft.server.world.ServerWorld)courier.getWorld()).getEntity(targetMission.taxiVillagerId);
                            if (v != null) v.discard();
                        }
                    }
                }
            }
            p.sendMessage(Text.literal((type.equals("TAKSI") ? "§6[Taksi] §a" : P + "§a") + "Görevi başarıyla iptal ettiniz."));
            return 1;
        }
        
        p.sendMessage(Text.literal((type.equals("TAKSI") ? "§6[Taksi] §c" : P + "§c") + "İptal edilecek bir çağrınız veya aktif görevde olan kuryeniz yok."));
        return 0;
    }

    private int cancelCallKurye(CommandContext<ServerCommandSource> context) { return cancelCall(context, "KURYE"); }
    private int cancelCallTaksi(CommandContext<ServerCommandSource> context) { return cancelCall(context, "TAKSI"); }

    // === TAKSI COMMANDS ===
    private int taksiHelpCommand(CommandContext<ServerCommandSource> context) {
        ServerCommandSource src = context.getSource();
        src.sendMessage(Text.literal(P + "\u00a7e/taksi al \u00a77- Yeni bir taksi g\u00f6revi al\u0131r."));
        src.sendMessage(Text.literal(P + "\u00a7e/taksi iptal \u00a77- Mevcut g\u00f6revi iptal eder."));
        src.sendMessage(Text.literal(P + "\u00a7e/taksi ipucu \u00a77- \u0130pu\u00e7lar\u0131n\u0131 a\u00e7ar/kapat\u0131r."));
        src.sendMessage(Text.literal(P + "\u00a7e/taksi siralama \u00a77- En iyi taksicileri listeler."));
        return 1;
    }

    private int toggleIpucu(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity p = context.getSource().getPlayer();
        if (p == null) return 0;
        String uuid = p.getUuidAsString();
        if (data.ipucuKapatanlar.contains(uuid)) {
            data.ipucuKapatanlar.remove(uuid);
            p.sendMessage(Text.literal(P + "\u00a7a\u0130pu\u00e7lar\u0131 a\u00e7\u0131ld\u0131."));
        } else {
            data.ipucuKapatanlar.add(uuid);
            p.sendMessage(Text.literal(P + "\u00a7c\u0130pu\u00e7lar\u0131 kapat\u0131ld\u0131."));
        }
        saveData();
        return 1;
    }

    private int taksiAdminHelpCommand(CommandContext<ServerCommandSource> context) {
        ServerCommandSource src = context.getSource();
        src.sendMessage(Text.literal("\u00a7m---------\u00a7r " + AP + " \u00a7m---------"));
        src.sendMessage(Text.literal("\u00a7e/taksi admin nokta-ekle <isim> \u00a77- Taksi noktas\u0131 ekler."));
        src.sendMessage(Text.literal("\u00a7e/taksi admin nokta-sil <isim> \u00a77- Taksi noktas\u0131 siler."));
        src.sendMessage(Text.literal("\u00a7e/taksi admin listele \u00a77- Taksi noktalar\u0131n\u0131 listeler."));
        src.sendMessage(Text.literal("\u00a7e/taksi admin carpan [<deger>] \u00a77- Ekonomi \u00e7arpan\u0131n\u0131 g\u00f6sterir/ayarlar."));
        src.sendMessage(Text.literal("\u00a7e/taksi admin carpan-sifirla \u00a77- Ekonomi \u00e7arpan\u0131n\u0131 varsay\u0131lana (0.1) d\u00f6nd\u00fcr\u00fcr."));
        src.sendMessage(Text.literal("\u00a7e/taksi admin siralama-sifirla \u00a77- T\u00fcm taksicilerin seviye ve xp verilerini s\u0131f\u0131rlar."));
        return 1;
    }

    private int showTaksiCarpan(CommandContext<ServerCommandSource> context) {
        context.getSource().sendMessage(Text.literal(AP + "\u00a7eTaksi \u00e7arpan\u0131 \u015fu an: \u00a7a" + data.taksiCarpan + " \u00a77(Metre ba\u015f\u0131 kazan\u00e7)"));
        return 1;
    }
    private int setTaksiCarpan(CommandContext<ServerCommandSource> context) {
        double d = DoubleArgumentType.getDouble(context, "deger");
        data.taksiCarpan = d;
        saveData();
        context.getSource().sendMessage(Text.literal(AP + "\u00a7aTaksi \u00e7arpan\u0131 ba\u015far\u0131yla g\u00fcncellendi: \u00a7e" + d));
        logActivity(context.getSource().getName() + " taksi carpanini " + d + " yapti.");
        return 1;
    }
    private int resetTaksiCarpan(CommandContext<ServerCommandSource> context) {
        data.taksiCarpan = 0.1;
        saveData();
        context.getSource().sendMessage(Text.literal(AP + "\u00a7aTaksi \u00e7arpan\u0131 s\u0131f\u0131rland\u0131 (\u00a7e0.1\u00a7a)."));
        return 1;
    }
    private int showTaksiSiralama(CommandContext<ServerCommandSource> context) {
        ServerCommandSource src = context.getSource();
        src.sendMessage(Text.literal("\u00a76\u00a7m--------\u00a7r \u00a7e\u2b50 Taksi S\u0131ralamas\u0131 \u2b50 \u00a76\u00a7m--------"));
        List<Map.Entry<String, PlayerStats>> sorted = data.playerStats.entrySet().stream()
            .sorted(Map.Entry.<String, PlayerStats>comparingByValue(Comparator.comparingInt((PlayerStats s) -> s.taksiLevel).reversed()
            .thenComparingDouble(s -> -s.taksiXp)))
            .collect(Collectors.toList());
        int rank = 1;
        String myUuid = src.getPlayer() != null ? src.getPlayer().getUuidAsString() : "";
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            Map.Entry<String, PlayerStats> entry = sorted.get(i);
            PlayerStats stats = entry.getValue();
            if (stats.taksiLevel <= 1 && stats.taksiXp == 0) continue;
            
            String playerName = "Bilinmiyor";
            ServerPlayerEntity target = src.getServer().getPlayerManager().getPlayer(UUID.fromString(entry.getKey()));
            if (target != null) {
                playerName = target.getGameProfile().getName();
            } else {
                java.util.Optional<com.mojang.authlib.GameProfile> profile = src.getServer().getUserCache().getByUuid(UUID.fromString(entry.getKey()));
                if (profile.isPresent()) {
                    playerName = profile.get().getName();
                } else {
                    playerName = "Oyuncu (" + entry.getKey().substring(0, 5) + ")";
                }
            }
            String prefix = (entry.getKey().equals(myUuid)) ? "\u00a7a" : "\u00a7e";
            src.sendMessage(Text.literal("\u00a76" + rank + ". " + prefix + playerName + " \u00a77- Seviye: \u00a7b" + stats.taksiLevel + " \u00a77(XP: " + (int)stats.taksiXp + ")"));
            rank++;
        }
        return 1;
    }
    private int resetTaksiSiralama(CommandContext<ServerCommandSource> context) {
        for (PlayerStats stats : data.playerStats.values()) {
            stats.taksiLevel = 1;
            stats.taksiXp = 0;
        }
        saveData();
        context.getSource().sendMessage(Text.literal(AP + "\u00a7cT\u00fcm taksi s\u0131ralamas\u0131 s\u0131f\u0131rland\u0131!"));
        logActivity(context.getSource().getName() + " taksi siralamasini sifirladi.");
        return 1;
    }

    private int addTaksiNokta(CommandContext<ServerCommandSource> context) {
        String isim = StringArgumentType.getString(context, "isim");
        if (context.getSource().getPlayer() == null) return 0;
        ServerPlayerEntity p = context.getSource().getPlayer();
        BlockPos pos = p.getBlockPos();
        data.taksiNoktalari.add(new LocationData(isim, pos.getX(), pos.getY(), pos.getZ(), p.getWorld().getRegistryKey().getValue().toString()));
        saveData();
        p.sendMessage(Text.literal(AP + "\u00a7aTaksi noktas\u0131 eklendi: \u00a7e" + isim));
        return 1;
    }

    private int deleteTaksiNokta(CommandContext<ServerCommandSource> context) {
        String isim = StringArgumentType.getString(context, "isim");
        LocationData found = null;
        for (LocationData loc : data.taksiNoktalari) { if (loc.name.equalsIgnoreCase(isim)) found = loc; }
        if (found != null) {
            data.taksiNoktalari.remove(found); saveData();
            context.getSource().sendMessage(Text.literal(AP + "\u00a7aTaksi noktas\u0131 silindi."));
            return 1;
        }
        return 0;
    }

    private int listTaksiPoints(CommandContext<ServerCommandSource> context) {
        ServerCommandSource src = context.getSource();
        src.sendMessage(Text.literal(AP + "\u00a7e--- Taksi Noktalar\u0131 ---"));
        for (LocationData l : data.taksiNoktalari) src.sendMessage(Text.literal("\u00a77- \u00a7f" + l.name));
        return 1;
    }

    private int takeTaksiMission(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity p = context.getSource().getPlayer();
        if (p == null) return 0;
        if (activeMissions.containsKey(p.getUuid())) {
            p.sendMessage(Text.literal(P + "§cZaten aktif bir görevin var (Kurye veya Taksi)!"));
            return 0;
        }
        if (data.taksiNoktalari.size() < 2) {
            p.sendMessage(Text.literal(P + "§cTaksi sistemi için en az 2 nokta eklenmiş olmalıdır!"));
            return 0;
        }
        new JobMenuGui(p, "TAKSI", data, activeMissions, callRequests).open();
        return 1;
    }

    // === Tick Handler ===

    private int tickCounter = 0;
    private int particleTickCounter = 0;
    private int tipCounter = 0;
    private boolean tabPlaceholdersRegistered = false;

    public PlayerStats getStats(ServerPlayerEntity p) {
        return data.playerStats.computeIfAbsent(p.getUuidAsString(), k -> new PlayerStats());
    }

    public void addXp(ServerPlayerEntity p, String jobType, double xpGained) {
        PlayerStats stats = getStats(p);
        boolean leveledUp = false;
        int newLevel;
        if (jobType.equals("KURYE")) {
            stats.kuryeXp += xpGained;
            while (stats.kuryeXp >= stats.kuryeLevel * 100) {
                stats.kuryeXp -= stats.kuryeLevel * 100;
                stats.kuryeLevel++;
                leveledUp = true;
            }
            newLevel = stats.kuryeLevel;
        } else {
            stats.taksiXp += xpGained;
            while (stats.taksiXp >= stats.taksiLevel * 100) {
                stats.taksiXp -= stats.taksiLevel * 100;
                stats.taksiLevel++;
                leveledUp = true;
            }
            newLevel = stats.taksiLevel;
        }
        
        saveData();
        p.sendMessage(Text.literal(P + "\u00a7a+" + (int)xpGained + " XP Kazan\u0131ld\u0131!"));
        
        if (leveledUp) {
            String title = "\u00a7a\u00a7lSEV\u0130YE ATLANDI!";
            String subtitle = "\u00a7e" + jobType + " Seviyesi: \u00a7b" + newLevel;
            p.getServer().getCommandManager().executeWithPrefix(p.getServer().getCommandSource(), "title " + p.getName().getString() + " title {\"text\":\"" + title + "\"}");
            p.getServer().getCommandManager().executeWithPrefix(p.getServer().getCommandSource(), "title " + p.getName().getString() + " subtitle {\"text\":\"" + subtitle + "\"}");
            p.getServer().getCommandManager().executeWithPrefix(p.getServer().getCommandSource(), "playsound entity.player.levelup player " + p.getName().getString() + " ~ ~ ~ 1.0 1.0");
        }
    }

    private boolean hasEmptyHotbarSlot(ServerPlayerEntity p) {
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isEmpty()) return true;
        }
        return false;
    }

    private void spawnBeaconParticles(ServerPlayerEntity p, LocationData loc) {
        if (!p.getWorld().getRegistryKey().getValue().toString().equals(loc.world)) return;
        double dist = Math.sqrt(p.getBlockPos().getSquaredDistance(loc.x, loc.y, loc.z));
        if (dist > 100) return; // Only show within 100 blocks
        ServerWorld world = (ServerWorld) p.getWorld();
        // Spawn a column of particles at the location (beacon-like)
        for (int dy = 0; dy < 20; dy++) {
            world.spawnParticles(p, ParticleTypes.END_ROD, true,
                loc.x + 0.5, loc.y + 1.0 + dy, loc.z + 0.5,
                2, 0.15, 0.1, 0.15, 0.01);
        }
        // Ground ring effect
        for (int angle = 0; angle < 360; angle += 30) {
            double rad = Math.toRadians(angle);
            double px = loc.x + 0.5 + Math.cos(rad) * 1.5;
            double pz = loc.z + 0.5 + Math.sin(rad) * 1.5;
            world.spawnParticles(p, ParticleTypes.HAPPY_VILLAGER, true,
                px, loc.y + 1.0, pz,
                1, 0, 0, 0, 0);
        }
    }

    private void onServerTick(MinecraftServer server) {
        serverRef[0] = server;

        boolean isTabLoaded = FabricLoader.getInstance().isModLoaded("tab");
        if (isTabLoaded && !tabPlaceholdersRegistered) {
            try {
                if (me.neznamy.tab.api.TabAPI.getInstance() != null) {
                    System.out.println("[CourierMod] TAB API is ready (via tick). Registering placeholders...");
                    TabPlaceholderRegistry.register(this, serverRef, activeMissions, this::getPlayerParaPublic);
                    tabPlaceholdersRegistered = true;
                    System.out.println("[CourierMod] TAB Placeholders registered successfully!");
                }
            } catch (Throwable t) {
                System.err.println("[CourierMod] TAB placeholder registration failed (tick): " + t.getMessage());
            }
        }

        // Particle effects every 10 ticks (0.5 second)
        particleTickCounter++;
        if (particleTickCounter >= 10) {
            particleTickCounter = 0;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                PlayerMission pm = activeMissions.get(p.getUuid());
                if (pm == null) continue;
                if (pm.state.equals("TOPLAMA")) {
                    spawnBeaconParticles(p, pm.dagitimLoc);
                } else if (pm.state.equals("TESLIMAT")) {
                    spawnBeaconParticles(p, pm.musteriLoc);
                }
            }
        }

        tickCounter++;
        if (tickCounter < 20) return;
        tickCounter = 0;
        
        long currentTime = System.currentTimeMillis();
        java.util.List<java.util.UUID> toCancel = new java.util.ArrayList<>();
        for (java.util.Map.Entry<java.util.UUID, PlayerMission> entry : activeMissions.entrySet()) {
            PlayerMission pm = entry.getValue();
            if (pm.isPlayerJob && pm.customerId != null) {
                long elapsed = currentTime - pm.missionStartTime;
                if (elapsed > 5 * 60 * 1000) {
                    toCancel.add(entry.getKey());
                } else if (elapsed > 2 * 60 * 1000) {
                    long elapsedSeconds = elapsed / 1000;
                    if (elapsedSeconds % 30 == 0) {
                        ServerPlayerEntity courier = server.getPlayerManager().getPlayer(entry.getKey());
                        if (courier != null) {
                            long remaining = (5 * 60 * 1000 - elapsed) / 1000;
                            courier.sendMessage(Text.literal(pm.type.equals("TAKSI") ? "§6[Taksi] §cMüşteri sizi bekliyor! İptal olmasına " + remaining + " saniye kaldı!" : P + "§cMüşteri sizi bekliyor! İptal olmasına " + remaining + " saniye kaldı!"));
                        }
                    }
                    if (elapsedSeconds % 30 == 0) {
                        ServerPlayerEntity customer = server.getPlayerManager().getPlayer(pm.customerId);
                        if (customer != null) {
                            customer.sendMessage(Text.literal(pm.type.equals("TAKSI") ? "§6[Taksi] §cTaksiciniz henüz ulaşmadı. İsterseniz /taksi iptal-cagri ile iptal edebilirsiniz." : P + "§cKuryeniz henüz ulaşmadı. İsterseniz /kurye iptal-cagri ile iptal edebilirsiniz."));
                        }
                    }
                }
            }
        }
        for (java.util.UUID id : toCancel) {
            PlayerMission pm = activeMissions.get(id);
            activeMissions.remove(id);
            ServerPlayerEntity courier = server.getPlayerManager().getPlayer(id);
            if (courier != null) {
                courier.sendMessage(Text.literal(pm.type.equals("TAKSI") ? "§6[Taksi] §cGörev zaman aşımına uğradı." : P + "§cGörev zaman aşımına uğradı."));
                courier.getInventory().remove(s -> s.isOf(Items.PAPER) && s.hasCustomName() && s.getName().getString().contains("M\u00fc\u015fteri Paketi"), 1, courier.getInventory());
                if (pm.type.equals("TAKSI") && pm.taxiVillagerId != null) {
                    if (courier.getWorld() instanceof net.minecraft.server.world.ServerWorld) {
                        net.minecraft.entity.Entity v = ((net.minecraft.server.world.ServerWorld)courier.getWorld()).getEntity(pm.taxiVillagerId);
                        if (v != null) v.discard();
                    }
                }
            }
            if (pm.customerId != null) {
                ServerPlayerEntity customer = server.getPlayerManager().getPlayer(pm.customerId);
                if (customer != null) {
                    customer.sendMessage(Text.literal(pm.type.equals("TAKSI") ? "§6[Taksi] §cTaksiciniz uzun süre gelmediği için görev iptal edildi." : P + "§cKuryeniz uzun süre gelmediği için görev iptal edildi."));
                }
            }
        }
        
        java.util.List<PlayerCallRequest> expiredRequests = new java.util.ArrayList<>();
        for (PlayerCallRequest req : callRequests) {
            long elapsed = currentTime - req.timestamp;
            if (elapsed > 5 * 60 * 1000) {
                expiredRequests.add(req);
            } else if (elapsed > 2 * 60 * 1000) {
                long elapsedSeconds = elapsed / 1000;
                if (elapsedSeconds % 30 == 0) {
                    ServerPlayerEntity customer = server.getPlayerManager().getPlayer(req.playerId);
                    if (customer != null) {
                        customer.sendMessage(Text.literal(req.type.equals("TAKSI") ? "§6[Taksi] §cTaksiniz henüz bulunamadı... Beklemeye devam edebilir veya /taksi iptal-cagri ile iptal edebilirsiniz." : P + "§cKurye henüz bulunamadı... Beklemeye devam edebilir veya /kurye iptal-cagri ile iptal edebilirsiniz."));
                    }
                }
            }
            
        for (PlayerCallRequest expReq : expiredRequests) {
            callRequests.remove(expReq);
            if (expReq.type.equals("TAKSI")) {
                double dist = Math.sqrt(Math.pow(expReq.location.x - expReq.targetLocation.x, 2) + Math.pow(expReq.location.z - expReq.targetLocation.z, 2));
                double ucret = Math.max(MIN_UCRET, Math.floor(dist * data.taksiCarpan));
                ServerPlayerEntity customer = server.getPlayerManager().getPlayer(expReq.playerId);
                if (customer != null) {
                    addPlayerPara(server, customer, (int)ucret);
                    customer.sendMessage(Text.literal("§6[Taksi] §cÇağrınız zaman aşımına uğradığı için " + (int)ucret + " TL iade edildi."));
                }
            }
        }
        }
        
        for (PlayerCallRequest req : expiredRequests) {
            callRequests.remove(req);
            ServerPlayerEntity customer = server.getPlayerManager().getPlayer(req.playerId);
            if (customer != null) {
                customer.sendMessage(Text.literal(req.type.equals("TAKSI") ? "§6[Taksi] §c5 dakika boyunca hiçbir taksi çağrınızı kabul etmediği için isteğiniz otomatik olarak iptal edildi." : P + "§c5 dakika boyunca hiçbir kurye çağrınızı kabul etmediği için isteğiniz otomatik olarak iptal edildi."));
            }
        }
        
        tipCounter++;

        ensureParaObjective(server);

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            // updateSidebar(server, p); // Disabled to prevent conflicts with TAB scoreboard
            
            PlayerMission pm = activeMissions.get(p.getUuid());
            if (pm == null) continue;
            
            PlayerStats stats = getStats(p);
            int currentLevel = pm.type.equals("KURYE") ? stats.kuryeLevel : stats.taksiLevel;
            double currentXp = pm.type.equals("KURYE") ? stats.kuryeXp : stats.taksiXp;
            int reqXp = currentLevel * 100;
            
            long elapsedMillis = System.currentTimeMillis() - pm.missionStartTime;
            long elapsedSeconds = elapsedMillis / 1000;
            String timeStr = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60);
            
            String actionBarText = "\u00a7f[\u00a7e\u23f1 " + timeStr + "\u00a7f] \u00a7b" + pm.type + " Lvl " + currentLevel + " \u00a77(XP: " + (int)currentXp + "/" + reqXp + ")";
            
            if (tipCounter % 15 >= 0 && tipCounter % 15 <= 2 && !data.ipucuKapatanlar.contains(p.getUuidAsString())) {
                String tip = TIPS[(tipCounter / 15) % TIPS.length];
                actionBarText = "\u00a7f[\u00a7e\u23f1 " + timeStr + "\u00a7f] " + tip;
            }
            
            p.sendMessage(Text.literal(actionBarText), true);

            BlockPos pPos = p.getBlockPos();
            if (pm.type.equals("KURYE")) {
                if (pm.state.equals("TOPLAMA")) {
                    if (p.getWorld().getRegistryKey().getValue().toString().equals(pm.dagitimLoc.world)) {
                        double dist = Math.sqrt(pPos.getSquaredDistance(pm.dagitimLoc.x, pm.dagitimLoc.y, pm.dagitimLoc.z));
                        if (dist < 5.0) {
                            if (!hasEmptyHotbarSlot(p)) {
                                p.sendMessage(Text.literal(P + "\u00a7c\u00d6nce elinin bo\u015f oldu\u011fundan emin ol! Hotbar\u0131nda yer yok."));
                                continue;
                            }
                            pm.state = "TESLIMAT";
                            ItemStack item = new ItemStack(Items.PAPER);
                            item.setCustomName(Text.literal("\u00a7dM\u00fc\u015fteri Paketi"));
                            p.getInventory().insertStack(item);
                            MutableText message = Text.literal(P + "\u00a7aPaketi ald\u0131n! \u015eimdi ");
                            MutableText nameText = Text.literal("\u00a7b" + pm.musteriLoc.name);
                            nameText.setStyle(nameText.getStyle()
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kurye wp musteri"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("\u00a7aWaypoint olu\u015fturmak i\u00e7in t\u0131kla!"))));
                            message.append(nameText).append(Text.literal(" \u00a7akonumuna g\u00f6t\u00fcr."));
                            p.sendMessage(message);
                            logActivity(p.getGameProfile().getName() + " paketi teslim aldi, musteriye goturuyor: " + pm.musteriLoc.name);
                        }
                    }
                } else if (pm.state.equals("TESLIMAT")) {
                    if (p.getWorld().getRegistryKey().getValue().toString().equals(pm.musteriLoc.world)) {
                        double dist = Math.sqrt(pPos.getSquaredDistance(pm.musteriLoc.x, pm.musteriLoc.y, pm.musteriLoc.z));
                        if (dist < 5.0) {
                            boolean hasItem = false;
                            for (int i = 0; i < p.getInventory().size(); i++) {
                                ItemStack stack = p.getInventory().getStack(i);
                                if (stack.isOf(Items.BUNDLE) && stack.hasCustomName() && stack.getName().getString().contains("M\u00fch\u00fcrl\u00fc Kurye Boh\u00e7as\u0131")) {
                                    stack.decrement(1);
                                    hasItem = true;
                                    break;
                                }
                            }
                            if (hasItem) {
                                double totalDist = Math.sqrt(Math.pow(pm.dagitimLoc.x - pm.musteriLoc.x, 2) + Math.pow(pm.dagitimLoc.z - pm.musteriLoc.z, 2));
                                double ucret = Math.floor(totalDist * data.kuryeCarpan);
                                if (ucret < MIN_UCRET) ucret = MIN_UCRET;
                                addPlayerPara(server, p, (int) ucret);
                                p.sendMessage(Text.literal(P + "\u00a7bTeslimat ba\u015far\u0131l\u0131! \u00a7e" + (int) totalDist + " \u00a77metre yol yapt\u0131n."));
                                  if (pm.isPlayerJob) {
                                      ServerPlayerEntity customer = server.getPlayerManager().getPlayer(pm.customerId);
                                      if (customer != null) {
                                          ItemStack beef = new ItemStack(Items.COOKED_BEEF, 6);
                                          customer.getInventory().insertStack(beef);
                                          customer.sendMessage(Text.literal("\u00a7a[Kurye] \u00a7aKurye boh\u00e7as\u0131 teslim edildi ve i\u00e7inden 6 adet pi\u015fmi\u015f biftek \u00e7\u0131kt\u0131!"));
                                      }
                                  }
                                p.sendMessage(Text.literal("\u00a7aKazan\u00e7: \u00a7e" + (int) ucret + "\u20ba"));
                                logActivity(p.getGameProfile().getName() + " teslimati tamamladi! Yol: " + (int) totalDist + "m, Kazanc: " + (int) ucret + "\u20ba (" + pm.dagitimLoc.name + " -> " + pm.musteriLoc.name + ")");
                                
                                long completionElapsedMillis = System.currentTimeMillis() - pm.missionStartTime;
                                double completionElapsedSeconds = completionElapsedMillis / 1000.0;
                                if (completionElapsedSeconds < 1) completionElapsedSeconds = 1;
                                double speedFactor = (totalDist / completionElapsedSeconds) / 4.0;
                                if (speedFactor < 0.5) speedFactor = 0.5;
                                if (speedFactor > 2.0) speedFactor = 2.0;
                                double gainedXp = (totalDist * 0.5) * speedFactor;
                                addXp(p, "KURYE", gainedXp);
                                
                                activeMissions.remove(p.getUuid());
                            } else {
                                p.sendMessage(Text.literal(P + "\u00a7cPaket elinde de\u011fil! G\u00f6rev ba\u015far\u0131s\u0131z."));
                                logActivity(p.getGameProfile().getName() + " teslimat yapmaya calisti fakat elinde paket yok! Gorev basarisiz.");
                                activeMissions.remove(p.getUuid());
                            }
                        }
                    }
                }
            } else if (pm.type.equals("TAKSI")) {
                boolean inAutomobilityCar = false;
                if (p.hasVehicle()) {
                    String vehicleId = EntityType.getId(p.getVehicle().getType()).toString();
                    if (vehicleId.contains("automobility")) {
                        inAutomobilityCar = true;
                    }
                }

                if (pm.state.equals("TOPLAMA")) {
                    if (pm.isPlayerJob && pm.customerId != null) {
                        ServerPlayerEntity customer = server.getPlayerManager().getPlayer(pm.customerId);
                        if (customer != null) {
                            pm.dagitimLoc.x = customer.getBlockPos().getX();
                            pm.dagitimLoc.y = customer.getBlockPos().getY();
                            pm.dagitimLoc.z = customer.getBlockPos().getZ();
                            pm.dagitimLoc.world = customer.getWorld().getRegistryKey().getValue().toString();
                        }
                    }

                    if (p.getWorld().getRegistryKey().getValue().toString().equals(pm.dagitimLoc.world)) {
                        double dist = Math.sqrt(pPos.getSquaredDistance(pm.dagitimLoc.x, pm.dagitimLoc.y, pm.dagitimLoc.z));
                        if (dist < 5.0) {
                            if (!inAutomobilityCar) {
                                if (tickCounter % 2 == 0) p.sendMessage(Text.literal(P + "\u00a7cM\u00fc\u015fteriyi almak i\u00e7in Automobility arac\u0131nda olmal\u0131s\u0131n!"), true);
                                continue;
                            }
                            pm.state = "TESLIMAT";
                            if (p.getWorld() instanceof ServerWorld && pm.taxiVillagerId != null) {
                                Entity v = ((ServerWorld)p.getWorld()).getEntity(pm.taxiVillagerId);
                                if (v != null) v.discard();
                            }
                            
                            if (pm.isPlayerJob && pm.customerId != null) {
                                ServerPlayerEntity customer = server.getPlayerManager().getPlayer(pm.customerId);
                                if (customer != null) {
                                    customer.startRiding(p, true);
                                    p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket(p));
                                    customer.sendMessage(Text.literal("§6[Taksi] §aTaksiye bindiniz!"));
                                }
                            }
                            
                            MutableText message = Text.literal(P + "\u00a7aM\u00fc\u015fteri araca bindi! \u00a7eHedef: ");
                            MutableText nameText = Text.literal("\u00a7b" + pm.musteriLoc.name);
                            nameText.setStyle(nameText.getStyle()
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kurye wp musteri"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("\u00a7aWaypoint olu\u015fturmak i\u00e7in t\u0131kla!"))));
                            message.append(nameText);
                            p.sendMessage(message);
                        }
                    }
                } else if (pm.state.equals("TESLIMAT")) {
                    if (p.getWorld().getRegistryKey().getValue().toString().equals(pm.musteriLoc.world)) {
                        double dist = Math.sqrt(pPos.getSquaredDistance(pm.musteriLoc.x, pm.musteriLoc.y, pm.musteriLoc.z));
                        if (dist < 5.0) {
                            if (!inAutomobilityCar) {
                                if (tickCounter % 2 == 0) p.sendMessage(Text.literal(P + "\u00a7cM\u00fc\u015fteriyi hedefe Automobility arac\u0131yla g\u00f6t\u00fcrmelisin!"), true);
                                continue;
                            }
                            if (!pm.isPlayerJob) {
                                pm.state = "TAMAMLANIYOR";
                                pm.ticksAtTarget = 0;
                                if (p.getWorld() instanceof ServerWorld) {
                                    ServerWorld world = (ServerWorld) p.getWorld();
                                    VillagerEntity villager = EntityType.VILLAGER.create(world);
                                    if (villager != null) {
                                        villager.refreshPositionAndAngles(pm.musteriLoc.x + 0.5, pm.musteriLoc.y, pm.musteriLoc.z + 0.5, 0, 0);
                                        villager.setAiDisabled(true);
                                        villager.setInvulnerable(true);
                                        villager.setCustomName(Text.literal("\u00a7eTaksi M\u00fc\u015fterisi"));
                                        villager.setCustomNameVisible(true);
                                        world.spawnEntity(villager);
                                        pm.taxiVillagerId = villager.getUuid();
                                    }
                                }
                                p.sendMessage(Text.literal(P + "\u00a7eM\u00fc\u015fteri iniyor, l\u00fctfen bekle..."));
                            } else {
                                double totalDist = Math.sqrt(Math.pow(pm.dagitimLoc.x - pm.musteriLoc.x, 2) + Math.pow(pm.dagitimLoc.z - pm.musteriLoc.z, 2));
                                double ucret = Math.floor(totalDist * data.taksiCarpan);
                                if (ucret < MIN_UCRET) ucret = MIN_UCRET;
                                
                                ServerPlayerEntity customer = server.getPlayerManager().getPlayer(pm.customerId);
                                if (customer != null) {
                                    server.getCommandManager().executeWithPrefix(server.getCommandSource(), "eco take " + customer.getName().getString() + " " + (int) ucret);
                                    customer.sendMessage(net.minecraft.text.Text.literal("§6[Taksi] §cTaksi ücreti olarak " + (int) ucret + " kesildi."));
                                    customer.stopRiding();
                            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket(p));
                                    customer.sendMessage(Text.literal("§6[Taksi] §aHedefe ulaştınız. Yolculuk bitti."));
                                    if (pm.musteriLoc != null) {
                                        customer.teleport(customer.getServerWorld(), pm.musteriLoc.x, pm.musteriLoc.y + 1, pm.musteriLoc.z, customer.getYaw(), customer.getPitch());
                                    }
                                }
                                
                                addPlayerPara(server, p, (int) ucret);
                                addXp(p, "TAKSI", 50.0);
                                activeMissions.remove(p.getUuid());
                                p.sendMessage(Text.literal("§6[Taksi] §aMüşteriyi hedefine ulaştırdınız. Kazanılan: " + (int) ucret + "TL"));
                            }
                        }
                    }
                } else if (pm.state.equals("TAMAMLANIYOR")) {
                    pm.ticksAtTarget++;
                    if (pm.ticksAtTarget >= 2) { 
                        if (p.getWorld() instanceof ServerWorld && pm.taxiVillagerId != null) {
                            Entity v = ((ServerWorld)p.getWorld()).getEntity(pm.taxiVillagerId);
                            if (v != null) v.discard();
                        }
                        double totalDist = Math.sqrt(Math.pow(pm.dagitimLoc.x - pm.musteriLoc.x, 2) + Math.pow(pm.dagitimLoc.z - pm.musteriLoc.z, 2));
                        double ucret = Math.floor(totalDist * data.taksiCarpan);
                        if (ucret < MIN_UCRET) ucret = MIN_UCRET;
                        addPlayerPara(server, p, (int) ucret);
                        p.sendMessage(Text.literal(P + "\u00a7bTaksi g\u00f6revi ba\u015far\u0131l\u0131! \u00a7e" + (int) totalDist + " \u00a77metre yol yapt\u0131n."));
                        p.sendMessage(Text.literal("\u00a7aKazan\u00e7: \u00a7e" + (int) ucret + "\u20ba"));
                        
                        long completionElapsedMillis = System.currentTimeMillis() - pm.missionStartTime;
                        double completionElapsedSeconds = completionElapsedMillis / 1000.0;
                        if (completionElapsedSeconds < 1) completionElapsedSeconds = 1;
                        double speedFactor = (totalDist / completionElapsedSeconds) / 4.0;
                        if (speedFactor < 0.5) speedFactor = 0.5;
                        if (speedFactor > 2.0) speedFactor = 2.0;
                        double gainedXp = (totalDist * 0.5) * speedFactor;
                        addXp(p, "TAKSI", gainedXp);
                        
                        activeMissions.remove(p.getUuid());
                    }
                }
            }
        }
    }
}
