# -*- coding: utf-8 -*-
with open('src/client/java/com/rpsunucusu/courier/client/CourierNavigationClient.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()
for i in range(len(lines)):
    if 'Liste boş veya null' in lines[i]:
        lines[i] = lines[i] + '''\t\t\t\t\t\t\tclient.execute(() -> {
\t\t\t\t\t\t\t\tif (client.player != null) {
\t\t\t\t\t\t\t\t\tclient.player.sendMessage(Text.literal("§c[Hata] Navigasyon rotası bulunamadı! Lütfen hedefe yaklaşın veya açık bir yol seçin."), false);
\t\t\t\t\t\t\t\t}
\t\t\t\t\t\t\t});\n'''
        break
with open('src/client/java/com/rpsunucusu/courier/client/CourierNavigationClient.java', 'w', encoding='utf-8') as f:
    f.writelines(lines)
