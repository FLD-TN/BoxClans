package com.dex.boxclans.text;

import net.kyori.adventure.text.Component;

/**
 * The one name of a clan (§4) in every form it is shown. Built by {@link ClanNameFormatter}.
 *
 * @param raw       what the player typed ({@code &c&lRồng &6Đỏ}), stored in the database
 * @param key       command key ({@code RongDo})
 * @param plain     without colours ({@code Rồng Đỏ})
 * @param component coloured, for chat and GUIs
 * @param legacy    coloured, {@code §x} hex, for scoreboards / TAB / holograms
 * @param ampersand coloured, {@code &} codes and {@code &#RRGGBB}
 * @param mini      coloured, MiniMessage, to insert into message templates
 */
public record ClanName(String raw, String key, String plain, Component component, String legacy, String ampersand,
                       String mini) {

    /** Same name with another command key (a stored key wins over a regenerated one). */
    public ClanName withKey(String newKey) {
        return new ClanName(raw, newKey, plain, component, legacy, ampersand, mini);
    }

    /** For message placeholders: inserted as MiniMessage, keeps the colours. */
    public Text.Mini placeholder() {
        return new Text.Mini(mini);
    }
}
