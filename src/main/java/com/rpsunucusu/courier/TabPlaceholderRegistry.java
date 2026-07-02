package com.rpsunucusu.courier;

import me.neznamy.tab.api.TabAPI;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;

public class TabPlaceholderRegistry {
    public static void register(CourierMod mod, MinecraftServer[] serverRef, Map<UUID, CourierMod.PlayerMission> activeMissions, java.util.function.BiFunction<MinecraftServer, ServerPlayerEntity, Integer> getParaFunc) {
        try {
            // Register placeholder for baslik
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%gorev_baslik%", 500, tabPlayer -> {
                CourierMod.PlayerMission pm = activeMissions.get(tabPlayer.getUniqueId());
                if (pm == null) return "&7/kurye al &8- görev al";
                return pm.type.equals("TAKSI") ? "&e🚕 Taksi Görevi" : "&a📦 Kurye Görevi";
            });

            // Register placeholder for durum
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%gorev_durum%", 500, tabPlayer -> {
                CourierMod.PlayerMission pm = activeMissions.get(tabPlayer.getUniqueId());
                if (pm == null) return "&7/taksi al &8- görev al";
                if (pm.type.equals("TAKSI")) {
                    return pm.state.equals("TOPLAMA") ? "  &eYolcuyu Al" : "  &eHedefe Götür";
                } else {
                    return pm.state.equals("TOPLAMA") ? "  &ePaketi Al" : "  &eTeslim Et";
                }
            });

            // Register placeholder for hedef
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%gorev_hedef%", 500, tabPlayer -> {
                CourierMod.PlayerMission pm = activeMissions.get(tabPlayer.getUniqueId());
                if (pm == null) return "";
                String hedef = pm.state.equals("TOPLAMA") ? pm.dagitimLoc.name : pm.musteriLoc.name;
                return "  &b➤ &f" + hedef;
            });

            // Register player placeholders for para
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%kurye_para%", 500, tabPlayer -> {
                MinecraftServer server = serverRef[0];
                if (server == null) return "0";
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(tabPlayer.getUniqueId());
                if (p == null) return "0";
                return String.valueOf(getParaFunc.apply(server, p));
            });
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%objective:para%", 500, tabPlayer -> {
                MinecraftServer server = serverRef[0];
                if (server == null) return "0";
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(tabPlayer.getUniqueId());
                if (p == null) return "0";
                return String.valueOf(getParaFunc.apply(server, p));
            });
            
            // Server Placeholders for Holograms - Kurye
            for (int i = 1; i <= 10; i++) {
                final int rank = i;
                TabAPI.getInstance().getPlaceholderManager().registerServerPlaceholder("%courier_kurye_top_" + rank + "_isim%", 5000, () -> {
                    return getLeaderboardName(mod, serverRef, rank, true);
                });
                TabAPI.getInstance().getPlaceholderManager().registerServerPlaceholder("%courier_kurye_top_" + rank + "_seviye%", 5000, () -> {
                    return getLeaderboardLevel(mod, rank, true);
                });
            }

            // Server Placeholders for Holograms - Taksi
            for (int i = 1; i <= 10; i++) {
                final int rank = i;
                TabAPI.getInstance().getPlaceholderManager().registerServerPlaceholder("%courier_taksi_top_" + rank + "_isim%", 5000, () -> {
                    return getLeaderboardName(mod, serverRef, rank, false);
                });
                TabAPI.getInstance().getPlaceholderManager().registerServerPlaceholder("%courier_taksi_top_" + rank + "_seviye%", 5000, () -> {
                    return getLeaderboardLevel(mod, rank, false);
                });
            }

            System.out.println("[CourierMod] TAB Placeholders registered successfully!");
        } catch (Throwable t) {
            System.err.println("[CourierMod] Error registering TAB placeholders: " + t.getMessage());
            t.printStackTrace();
        }
    }

    private static String getLeaderboardName(CourierMod mod, MinecraftServer[] serverRef, int rank, boolean isKurye) {
        List<Map.Entry<String, CourierMod.PlayerStats>> sorted = getSortedStats(mod, isKurye);
        if (rank > sorted.size()) return "---";
        
        Map.Entry<String, CourierMod.PlayerStats> entry = sorted.get(rank - 1);
        String uuidStr = entry.getKey();
        
        MinecraftServer server = serverRef[0];
        if (server == null) return "Oyuncu";
        
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(UUID.fromString(uuidStr));
        if (target != null) {
            return target.getGameProfile().getName();
        } else {
            java.util.Optional<com.mojang.authlib.GameProfile> profile = server.getUserCache().getByUuid(UUID.fromString(uuidStr));
            if (profile.isPresent()) {
                return profile.get().getName();
            } else {
                return "Oyuncu (" + uuidStr.substring(0, 5) + ")";
            }
        }
    }

    private static String getLeaderboardLevel(CourierMod mod, int rank, boolean isKurye) {
        List<Map.Entry<String, CourierMod.PlayerStats>> sorted = getSortedStats(mod, isKurye);
        if (rank > sorted.size()) return "-";
        CourierMod.PlayerStats stats = sorted.get(rank - 1).getValue();
        return String.valueOf(isKurye ? stats.kuryeLevel : stats.taksiLevel);
    }

    private static List<Map.Entry<String, CourierMod.PlayerStats>> getSortedStats(CourierMod mod, boolean isKurye) {
        return mod.getData().playerStats.entrySet().stream()
            .filter(e -> {
                CourierMod.PlayerStats s = e.getValue();
                if (isKurye) return s.kuryeLevel > 1 || s.kuryeXp > 0;
                else return s.taksiLevel > 1 || s.taksiXp > 0;
            })
            .sorted(Map.Entry.<String, CourierMod.PlayerStats>comparingByValue(Comparator.comparingInt((CourierMod.PlayerStats s) -> isKurye ? s.kuryeLevel : s.taksiLevel).reversed()
            .thenComparingDouble(s -> isKurye ? -s.kuryeXp : -s.taksiXp)))
            .collect(Collectors.toList());
    }
}
