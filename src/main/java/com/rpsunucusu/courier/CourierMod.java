package com.rpsunucusu.courier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

    private static CourierMod instance;
    public static CourierMod getInstance() {
        return instance;
    }

    private static final String P = "\u00a76[Kurye] ";
    private static final String AP = "\u00a76[Kurye-Admin] ";
    private static final double UCRET_METRE_BASI = 0.1;
    private static final double MIN_UCRET = 5.0;

    private static final File DATA_FILE = new File("config/kurye_data.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DataModel data = new DataModel();
    private Map<UUID, PlayerMission> activeMissions = new HashMap<>();
    private Random random = new Random();

    private static final String PARA_OBJECTIVE = "para";
    private final MinecraftServer[] serverRef = new MinecraftServer[1];

    private final List<String> activityLog = new ArrayList<>();

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
    }

    public static class PlayerMission {
        public String type = "KURYE"; // "KURYE" veya "TAKSI"
        public String state;
        public LocationData dagitimLoc;
        public LocationData musteriLoc;
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

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
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
                    TabPlaceholderRegistry.register(serverRef, activeMissions, this::getPlayerParaPublic);
                    tabPlaceholdersRegistered = true;
                    System.out.println("[CourierMod] TAB Placeholders registered successfully!");
                } catch (Throwable t) {
                    System.err.println("[CourierMod] Failed to register TAB placeholders on SERVER_STARTED: " + t.getMessage());
                    t.printStackTrace();
                }
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        System.out.println("[CourierMod] Loaded successfully (1.20.1) - Scoreboard Edition!");
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
            )
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
            )
        );
    }

    private int helpCommand(CommandContext<ServerCommandSource> context) {
        ServerCommandSource src = context.getSource();
        src.sendMessage(Text.literal(P + "\u00a7e/kurye al \u00a77- Yeni bir teslimat g\u00f6revi al\u0131r."));
        src.sendMessage(Text.literal(P + "\u00a7e/kurye iptal \u00a77- Mevcut g\u00f6revi iptal eder."));
        src.sendMessage(Text.literal(P + "\u00a7e/kurye ipucu \u00a77- \u0130pu\u00e7lar\u0131n\u0131 a\u00e7ar/kapat\u0131r."));
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

    private int takeMission(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity p = context.getSource().getPlayer();
        if (p == null) return 0;

        if (activeMissions.containsKey(p.getUuid())) {
            p.sendMessage(Text.literal(P + "\u00a7cZaten aktif bir g\u00f6revin var!"));
            return 0;
        }
        if (data.dagitimNoktalari.isEmpty()) {
            p.sendMessage(Text.literal(P + "\u00a7cHen\u00fcz da\u011f\u0131t\u0131m noktas\u0131 eklenmemi\u015f!"));
            return 0;
        }
        if (data.musteriNoktalari.isEmpty()) {
            p.sendMessage(Text.literal(P + "\u00a7cHen\u00fcz m\u00fc\u015fteri noktas\u0131 eklenmemi\u015f!"));
            return 0;
        }

        // Distance filtering: Only find dagitim points within 500 meters of the player
        List<MissionPair> validPairs = new ArrayList<>();
        BlockPos pPos = p.getBlockPos();
        for (LocationData dLoc : data.dagitimNoktalari) {
            if (dLoc.world != null && dLoc.world.equalsIgnoreCase(p.getWorld().getRegistryKey().getValue().toString())) {
                double pDistSq = Math.pow(dLoc.x - pPos.getX(), 2) + Math.pow(dLoc.z - pPos.getZ(), 2);
                if (pDistSq <= 500.0 * 500.0) {
                    for (LocationData mLoc : data.musteriNoktalari) {
                        if (dLoc.world.equalsIgnoreCase(mLoc.world)) {
                            validPairs.add(new MissionPair(dLoc, mLoc));
                        }
                    }
                }
            }
        }

        if (validPairs.isEmpty()) {
            p.sendMessage(Text.literal(P + "\u00a7c500 metre mesafe dahilinde teslimat yapilabilecek uygun is bulunamadi!"));
            logActivity(p.getGameProfile().getName() + " gorev almaya calisti, ancak 500m dahilinde uygun is bulunamadi.");
            return 0;
        }

        MissionPair selected = validPairs.get(random.nextInt(validPairs.size()));
        LocationData dLoc = selected.dagitim;
        LocationData mLoc = selected.musteri;

        PlayerMission pm = new PlayerMission();
        pm.type = "KURYE";
        pm.state = "TOPLAMA";
        pm.dagitimLoc = dLoc;
        pm.musteriLoc = mLoc;
        activeMissions.put(p.getUuid(), pm);

        ensureParaObjective(context.getSource().getServer());

        p.sendMessage(Text.literal(P + "\u00a7aYeni g\u00f6rev al\u0131nd\u0131!"));
        
        MutableText message = Text.literal(P + "\u00a7e1. Durak: ");
        MutableText nameText = Text.literal("\u00a7b" + dLoc.name);
        nameText.setStyle(nameText.getStyle()
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kurye wp dagitim"))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("\u00a7aWaypoint olu\u015fturmak i\u00e7in t\u0131kla!"))));
        message.append(nameText);
        p.sendMessage(message);
        logActivity(p.getGameProfile().getName() + " yeni gorev aldi: " + dLoc.name + " -> " + mLoc.name);
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
            logActivity(p.getGameProfile().getName() + " aktif gorevini iptal etti.");
        } else {
            p.sendMessage(Text.literal(P + "\u00a7cAktif bir g\u00f6revin yok."));
        }
        return 1;
    }

    // === TAKSI COMMANDS ===
    private int taksiHelpCommand(CommandContext<ServerCommandSource> context) {
        ServerCommandSource src = context.getSource();
        src.sendMessage(Text.literal(P + "\u00a7e/taksi al \u00a77- Yeni bir taksi g\u00f6revi al\u0131r."));
        src.sendMessage(Text.literal(P + "\u00a7e/taksi iptal \u00a77- Mevcut g\u00f6revi iptal eder."));
        src.sendMessage(Text.literal(P + "\u00a7e/taksi ipucu \u00a77- \u0130pu\u00e7lar\u0131n\u0131 a\u00e7ar/kapat\u0131r."));
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
            p.sendMessage(Text.literal(P + "\u00a7cZaten aktif bir g\u00f6revin var (Kurye veya Taksi)!"));
            return 0;
        }
        if (data.taksiNoktalari.size() < 2) {
            p.sendMessage(Text.literal(P + "\u00a7cTaksi sistemi i\u00e7in en az 2 nokta eklenmi\u015f olmal\u0131d\u0131r!"));
            return 0;
        }
        
        List<MissionPair> validPairs = new ArrayList<>();
        BlockPos pPos = p.getBlockPos();
        for (LocationData dLoc : data.taksiNoktalari) {
            if (dLoc.world != null && dLoc.world.equalsIgnoreCase(p.getWorld().getRegistryKey().getValue().toString())) {
                double pDistSq = Math.pow(dLoc.x - pPos.getX(), 2) + Math.pow(dLoc.z - pPos.getZ(), 2);
                if (pDistSq <= 500.0 * 500.0) {
                    for (LocationData mLoc : data.taksiNoktalari) {
                        if (dLoc.world.equalsIgnoreCase(mLoc.world) && !dLoc.name.equals(mLoc.name)) {
                            validPairs.add(new MissionPair(dLoc, mLoc));
                        }
                    }
                }
            }
        }

        if (validPairs.isEmpty()) {
            p.sendMessage(Text.literal(P + "\u00a7c500 metre mesafe dahilinde uygun taksi ba\u015flang\u0131\u00e7 noktas\u0131 bulunamad\u0131!"));
            return 0;
        }

        MissionPair selected = validPairs.get(random.nextInt(validPairs.size()));
        PlayerMission pm = new PlayerMission();
        pm.type = "TAKSI";
        pm.state = "TOPLAMA";
        pm.dagitimLoc = selected.dagitim;
        pm.musteriLoc = selected.musteri;

        if (p.getWorld() instanceof ServerWorld) {
            ServerWorld world = (ServerWorld) p.getWorld();
            VillagerEntity villager = EntityType.VILLAGER.create(world);
            if (villager != null) {
                villager.refreshPositionAndAngles(pm.dagitimLoc.x + 0.5, pm.dagitimLoc.y, pm.dagitimLoc.z + 0.5, 0, 0);
                villager.setAiDisabled(true);
                villager.setInvulnerable(true);
                villager.setCustomName(Text.literal("\u00a7eTaksi M\u00fc\u015fterisi"));
                villager.setCustomNameVisible(true);
                world.spawnEntity(villager);
                pm.taxiVillagerId = villager.getUuid();
            }
        }

        activeMissions.put(p.getUuid(), pm);
        p.sendMessage(Text.literal(P + "\u00a7aTaksi g\u00f6revi al\u0131nd\u0131!"));
        
        MutableText message = Text.literal(P + "\u00a7eYolcu \u015fuarada bekliyor: ");
        MutableText nameText = Text.literal("\u00a7b" + pm.dagitimLoc.name);
        nameText.setStyle(nameText.getStyle()
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kurye wp dagitim"))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("\u00a7aWaypoint olu\u015fturmak i\u00e7in t\u0131kla!"))));
        message.append(nameText);
        p.sendMessage(message);
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
                    TabPlaceholderRegistry.register(serverRef, activeMissions, this::getPlayerParaPublic);
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
                                if (stack.isOf(Items.PAPER) && stack.hasCustomName() && stack.getName().getString().contains("M\u00fc\u015fteri Paketi")) {
                                    stack.decrement(1);
                                    hasItem = true;
                                    break;
                                }
                            }
                            if (hasItem) {
                                double totalDist = Math.sqrt(Math.pow(pm.dagitimLoc.x - pm.musteriLoc.x, 2) + Math.pow(pm.dagitimLoc.z - pm.musteriLoc.z, 2));
                                double ucret = Math.floor(totalDist * UCRET_METRE_BASI);
                                if (ucret < MIN_UCRET) ucret = MIN_UCRET;
                                addPlayerPara(server, p, (int) ucret);
                                p.sendMessage(Text.literal(P + "\u00a7bTeslimat ba\u015far\u0131l\u0131! \u00a7e" + (int) totalDist + " \u00a77metre yol yapt\u0131n."));
                                p.sendMessage(Text.literal("\u00a7aKazan\u00e7: \u00a7e" + (int) ucret + "\u20ba"));
                                logActivity(p.getGameProfile().getName() + " teslimati tamamladi! Yol: " + (int) totalDist + "m, Kazanc: " + (int) ucret + "\u20ba (" + pm.dagitimLoc.name + " -> " + pm.musteriLoc.name + ")");
                                
                                long completionElapsedMillis = System.currentTimeMillis() - pm.missionStartTime;
                                double completionElapsedSeconds = completionElapsedMillis / 1000.0;
                                if (completionElapsedSeconds < 1) completionElapsedSeconds = 1;
                                double speedFactor = (totalDist / completionElapsedSeconds) / 10.0;
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
                        double ucret = Math.floor(totalDist * UCRET_METRE_BASI);
                        if (ucret < MIN_UCRET) ucret = MIN_UCRET;
                        addPlayerPara(server, p, (int) ucret);
                        p.sendMessage(Text.literal(P + "\u00a7bTaksi g\u00f6revi ba\u015far\u0131l\u0131! \u00a7e" + (int) totalDist + " \u00a77metre yol yapt\u0131n."));
                        p.sendMessage(Text.literal("\u00a7aKazan\u00e7: \u00a7e" + (int) ucret + "\u20ba"));
                        
                        long completionElapsedMillis = System.currentTimeMillis() - pm.missionStartTime;
                        double completionElapsedSeconds = completionElapsedMillis / 1000.0;
                        if (completionElapsedSeconds < 1) completionElapsedSeconds = 1;
                        double speedFactor = (totalDist / completionElapsedSeconds) / 10.0;
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
