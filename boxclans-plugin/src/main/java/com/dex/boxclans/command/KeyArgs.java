package com.dex.boxclans.command;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.text.ClanNameFormatter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Command arguments: clan command keys (§4.3), role keys, tab filtering. */
final class KeyArgs {

    static final List<String> ROLE_KEYS = List.of(ClanRole.LEADER.key(), ClanRole.VICE.key(), ClanRole.MEMBER.key());

    private KeyArgs() {
    }

    /**
     * A clan from a command argument: its command key (case-insensitive), or its plain name typed in quotes
     * ({@code "Rồng Đỏ"}), which gives the same key.
     */
    static ClanSnapshot clan(BoxClansPlugin plugin, String arg) {
        if (arg == null || arg.isBlank()) return null;
        String a = arg.replace("\"", "").trim();
        ClanSnapshot c = plugin.index().byKey(a);
        return c != null ? c : plugin.index().byKey(ClanNameFormatter.key(a));
    }

    /** Every clan key, never a colour code (§4.3). */
    static List<String> keys(BoxClansPlugin plugin) {
        List<String> out = new ArrayList<>();
        for (ClanSnapshot c : plugin.index().all()) out.add(c.key());
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    static List<String> filter(Collection<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }
}
