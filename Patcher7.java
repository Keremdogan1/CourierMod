import java.nio.file.*;
import java.io.*;

public class Patcher7 {
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("src/main/java/com/rpsunucusu/courier/CourierMod.java");
        String content = new String(Files.readAllBytes(path), "UTF-8");

        // 1. Update Courier Delivery logic
        String kuryeTarget = "if (hasItem) {\n" +
                             "                                double totalDist = Math.sqrt(Math.pow(pm.dagitimLoc.x - pm.musteriLoc.x, 2) + Math.pow(pm.dagitimLoc.z - pm.musteriLoc.z, 2));\n" +
                             "                                double ucret = Math.floor(totalDist * data.kuryeCarpan);\n" +
                             "                                if (ucret < MIN_UCRET) ucret = MIN_UCRET;\n" +
                             "                                addPlayerPara(server, p, (int) ucret);";
        String kuryeReplace = "if (hasItem) {\n" +
                              "                                double totalDist = Math.sqrt(Math.pow(pm.dagitimLoc.x - pm.musteriLoc.x, 2) + Math.pow(pm.dagitimLoc.z - pm.musteriLoc.z, 2));\n" +
                              "                                double ucret = Math.floor(totalDist * data.kuryeCarpan);\n" +
                              "                                if (ucret < MIN_UCRET) ucret = MIN_UCRET;\n" +
                              "                                if (pm.isPlayerJob && pm.customerId != null) {\n" +
                              "                                    net.minecraft.server.network.ServerPlayerEntity customer = server.getPlayerManager().getPlayer(pm.customerId);\n" +
                              "                                    if (customer != null) {\n" +
                              "                                        p.getServer().getCommandManager().executeWithPrefix(p.getServer().getCommandSource(), \"eco take \" + customer.getName().getString() + \" \" + (int) ucret);\n" +
                              "                                        customer.sendMessage(net.minecraft.text.Text.literal(\"§cKurye ücreti olarak \" + (int) ucret + \" kesildi.\"));\n" +
                              "                                    }\n" +
                              "                                }\n" +
                              "                                addPlayerPara(server, p, (int) ucret);";
        if (content.contains(kuryeTarget)) {
            content = content.replace(kuryeTarget, kuryeReplace);
        }

        // 2. Update taksiBitir logic
        String taksiTarget = "if (distSq <= 100.0) {\n" +
                             "                    addXp(p, \"TAKSI\", 50.0);\n" +
                             "                    activeMissions.remove(p.getUuid());\n" +
                             "                    customer.sendMessage(Text.literal(\"§6[Taksi] §aHedefe ulaştınız. Yolculuk bitti.\"));\n" +
                             "                    p.sendMessage(Text.literal(\"§6[Taksi] §aMüşteriyi hedefine ulaştırdınız.\"));";
        String taksiReplace = "if (distSq <= 100.0) {\n" +
                              "                    double totalDist = Math.sqrt(Math.pow(pm.dagitimLoc.x - customer.getBlockPos().getX(), 2) + Math.pow(pm.dagitimLoc.z - customer.getBlockPos().getZ(), 2));\n" +
                              "                    double ucret = Math.floor(totalDist * data.taksiCarpan);\n" +
                              "                    if (ucret < MIN_UCRET) ucret = MIN_UCRET;\n" +
                              "                    p.getServer().getCommandManager().executeWithPrefix(p.getServer().getCommandSource(), \"eco take \" + customer.getName().getString() + \" \" + (int) ucret);\n" +
                              "                    customer.sendMessage(net.minecraft.text.Text.literal(\"§6[Taksi] §cTaksi ücreti olarak \" + (int) ucret + \" kesildi.\"));\n" +
                              "                    addPlayerPara(p.getServer(), p, (int) ucret);\n" +
                              "                    addXp(p, \"TAKSI\", 50.0);\n" +
                              "                    activeMissions.remove(p.getUuid());\n" +
                              "                    customer.sendMessage(Text.literal(\"§6[Taksi] §aHedefe ulaştınız. Yolculuk bitti.\"));\n" +
                              "                    p.sendMessage(Text.literal(\"§6[Taksi] §aMüşteriyi hedefine ulaştırdınız. Kazanılan: \" + (int) ucret + \"TL\"));";
        if (content.contains(taksiTarget)) {
            content = content.replace(taksiTarget, taksiReplace);
        }

        Files.write(path, content.getBytes("UTF-8"));
        System.out.println("Patcher7 applied.");
    }
}
