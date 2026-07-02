package com.rpsunucusu.courier;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class FabricPlaceholderRegistry {

    public static void register(CourierMod mod, MinecraftServer[] serverRef) {
        
        // Kurye Top 1-10 Isim
        for (int i = 1; i <= 10; i++) {
            final int rank = i;
            Placeholders.register(new Identifier("courier", "kurye_top_" + rank + "_isim"), (ctx, arg) -> {
                if (serverRef[0] == null) return PlaceholderResult.value("Bilinmiyor");
                return PlaceholderResult.value(getKuryeTopIsim(mod, serverRef[0], rank));
            });
            Placeholders.register(new Identifier("courier", "kurye_top_" + rank + "_seviye"), (ctx, arg) -> {
                if (serverRef[0] == null) return PlaceholderResult.value("0");
                return PlaceholderResult.value(getKuryeTopSeviye(mod, rank));
            });
        }

        // Taksi Top 1-10
        for (int i = 1; i <= 10; i++) {
            final int rank = i;
            Placeholders.register(new Identifier("courier", "taksi_top_" + rank + "_isim"), (ctx, arg) -> {
                if (serverRef[0] == null) return PlaceholderResult.value("Bilinmiyor");
                return PlaceholderResult.value(getTaksiTopIsim(mod, serverRef[0], rank));
            });
            Placeholders.register(new Identifier("courier", "taksi_top_" + rank + "_seviye"), (ctx, arg) -> {
                if (serverRef[0] == null) return PlaceholderResult.value("0");
                return PlaceholderResult.value(getTaksiTopSeviye(mod, rank));
            });
        }
        
    }

    private static String getKuryeTopIsim(CourierMod mod, MinecraftServer server, int rank) {
        List<Map.Entry<String, CourierMod.PlayerStats>> list = new ArrayList<>(mod.getData().playerStats.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue().kuryeLevel, a.getValue().kuryeLevel));
        if (list.size() >= rank) {
            Map.Entry<String, CourierMod.PlayerStats> entry = list.get(rank - 1);
            java.util.Optional<com.mojang.authlib.GameProfile> profile = server.getUserCache().getByUuid(UUID.fromString(entry.getKey()));
            if (profile.isPresent()) {
                return profile.get().getName();
            }
            return "Oyuncu (" + entry.getKey().substring(0, 5) + ")";
        }
        return "-";
    }

    private static String getKuryeTopSeviye(CourierMod mod, int rank) {
        List<Map.Entry<String, CourierMod.PlayerStats>> list = new ArrayList<>(mod.getData().playerStats.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue().kuryeLevel, a.getValue().kuryeLevel));
        if (list.size() >= rank) {
            return String.valueOf(list.get(rank - 1).getValue().kuryeLevel);
        }
        return "0";
    }

    private static String getTaksiTopIsim(CourierMod mod, MinecraftServer server, int rank) {
        List<Map.Entry<String, CourierMod.PlayerStats>> list = new ArrayList<>(mod.getData().playerStats.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue().taksiLevel, a.getValue().taksiLevel));
        if (list.size() >= rank) {
            Map.Entry<String, CourierMod.PlayerStats> entry = list.get(rank - 1);
            java.util.Optional<com.mojang.authlib.GameProfile> profile = server.getUserCache().getByUuid(UUID.fromString(entry.getKey()));
            if (profile.isPresent()) {
                return profile.get().getName();
            }
            return "Oyuncu (" + entry.getKey().substring(0, 5) + ")";
        }
        return "-";
    }

    private static String getTaksiTopSeviye(CourierMod mod, int rank) {
        List<Map.Entry<String, CourierMod.PlayerStats>> list = new ArrayList<>(mod.getData().playerStats.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue().taksiLevel, a.getValue().taksiLevel));
        if (list.size() >= rank) {
            return String.valueOf(list.get(rank - 1).getValue().taksiLevel);
        }
        return "0";
    }
}
