package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.Outcome;
import com.dex.boxclans.clan.RolePermission;
import com.dex.boxclans.config.RoleRegistry;
import com.dex.boxclans.config.YamlFiles;
import com.dex.boxclans.storage.Database;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Durations;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** {@code menus/settings.yml}: clan settings (§8.4). */
public final class SettingsMenu extends LayoutMenu {

    public SettingsMenu(BoxClansPlugin plugin, Player viewer) {
        super(plugin, viewer, "settings");
    }

    private ClanData clan() {
        return plugin.manager().of(viewer.getUniqueId());
    }

    @Override
    public void open() {
        if (clan() == null) {
            plugin.guis().openList(viewer);
            return;
        }
        super.open();
    }

    /** {@code ✔ Bật} / {@code ✘ Tắt} lines, the current state highlighted. */
    private Object[] toggle(String prefix, String placeholder, boolean on) {
        return new Object[]{
                placeholder + "_enabled", mini(text(prefix + "-on-" + (on ? "enabled" : "disabled"))),
                placeholder + "_disabled", mini(text(prefix + "-off-" + (on ? "enabled" : "disabled")))};
    }

    private static Text.Mini mini(String legacy) {
        return new Text.Mini(Text.legacyToMini(legacy));
    }

    @Override
    protected void draw() {
        ClanData clan = clan();
        if (clan == null) return;
        Database.PlayerSettings ps = plugin.playerSettings().get(viewer.getUniqueId());
        boolean leader = clan.leader().equals(viewer.getUniqueId());
        fillEmpty();

        placeStatic('R', click -> {
            if (permitted(clan, RolePermission.RENAME_CLAN)) plugin.actions().startRename(viewer);
        }, "clan", clan.name().placeholder(), "key", clan.key());

        ItemStack flag = Flag.icon(clan.flag(), MenuItems.material(itemSection('F') == null ? null
                : itemSection('F').getString("material"), Material.ORANGE_BANNER));
        place('F', MenuItems.decorate(flag, itemSection('F'), null), click -> {
            if (permitted(clan, RolePermission.EDIT_FLAG)) plugin.guis().openFlag(viewer);
        });

        placeStatic('V', click -> {
            plugin.actions().toggleFriendlyFire(viewer);
            refresh();
        }, toggle("pvp", "pvp", clan.friendlyFire()));
        placeStatic('C', click -> {
            plugin.actions().toggleChatMode(viewer);
            refresh();
        }, toggle("chat", "chat", ps.chatMode()));
        place('S', glint('S', ps.chatSound(), toggle("sound", "chat_sound", ps.chatSound())), click -> {
            plugin.actions().toggleChatSound(viewer);
            refresh();
        });
        place('M', glint('M', ps.memberSound(), toggle("sound", "member_sound", ps.memberSound())), click -> {
            plugin.actions().toggleMemberSound(viewer);
            refresh();
        });

        placeStatic('I', click -> plugin.guis().openIcons(viewer));
        placeStatic('O', null, "roles", roleLines());
        ScoreLore.Lines score = ScoreLore.build(section("score") == null ? new org.bukkit.configuration.MemoryConfiguration()
                        : section("score"), plugin.pointsConfig(), plugin.killEvents() == null ? null : plugin.killEvents().current(),
                plugin.settings().antiFarm(), Durations.format(plugin.settings().antiFarm().pairWindow(), plugin.messages()),
                clan.uuid(), (c, source) -> plugin.points().clanToday(c, source));
        placeStatic('Z', null, "sources", score.sources(), "kill_event", score.killEvent(), "warnings", score.warnings());

        placeStatic('B', click -> plugin.guis().openMain(viewer));
        ItemStack leave = MenuItems.build(itemSection('L'));
        if (!leader) replaceLore(leave, itemSection('L').getStringList("lore-member"));
        place('L', leave, click -> plugin.actions().leave(viewer));
        ItemStack disband = MenuItems.build(itemSection('D'));
        if (!leader) replaceLore(disband, itemSection('D').getStringList("lore-member"));
        place('D', disband, click -> plugin.actions().disband(viewer));
    }

    private boolean permitted(ClanData clan, RolePermission permission) {
        if (plugin.manager().can(clan, viewer.getUniqueId(), permission)) return true;
        plugin.actions().fail(viewer, Outcome.NO_PERMISSION);
        return false;
    }

    private ItemStack glint(char c, boolean on, Object... ph) {
        ConfigurationSection s = itemSection(c);
        ItemStack item = MenuItems.build(s, ph);
        if (item != null && on && s != null && YamlFiles.bool(s, "enchantment-glint-when-enabled", false)) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setEnchantmentGlintOverride(true);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private static void replaceLore(ItemStack item, List<String> lore) {
        if (item == null || lore.isEmpty()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.lore(MenuItems.lore(lore));
        item.setItemMeta(meta);
    }

    /** Lore of {@code O}: every role of roles.yml with its colour and description. */
    private List<Text.Mini> roleLines() {
        List<Text.Mini> out = new ArrayList<>();
        List<String> head = cfg.getStringList("role-lines");
        for (ClanRole role : ClanRole.values()) {
            RoleRegistry.RoleInfo info = plugin.roles().info(role);
            for (String line : head) {
                out.add(new Text.Mini(Text.fill(Text.legacyToMini(line), "color", new Text.Mini(info.guiColorMini()),
                        "role", new Text.Mini(info.guiStyled()), "display", info.display(), "key", role.key())));
            }
            for (String d : info.description()) out.add(new Text.Mini(d));
        }
        return out;
    }
}
