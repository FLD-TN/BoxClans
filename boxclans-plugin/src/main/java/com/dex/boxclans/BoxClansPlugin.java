package com.dex.boxclans;

import com.dex.boxclans.api.ClanPoints;
import com.dex.boxclans.api.ClanCurrency;
import com.dex.boxclans.api.ClanCurrencyImpl;
import com.dex.boxclans.api.ClanPointsImpl;
import com.dex.boxclans.api.ClanService;
import com.dex.boxclans.chat.ChatListener;
import com.dex.boxclans.chat.ChatPrompt;
import com.dex.boxclans.chat.ClanChat;
import com.dex.boxclans.chat.PlayerSettingsStore;
import com.dex.boxclans.chat.PromptListener;
import com.dex.boxclans.command.ClanActions;
import com.dex.boxclans.command.ClanAdminCommand;
import com.dex.boxclans.command.ClanChatCommand;
import com.dex.boxclans.command.ClanCommand;
import com.dex.boxclans.command.IconCommand;
import com.dex.boxclans.command.KillEventCommand;
import com.dex.boxclans.command.PointsCommand;
import com.dex.boxclans.listener.CombatListener;
import com.dex.boxclans.listener.KillFeedback;
import com.dex.boxclans.command.MinhHuyCommand;
import com.dex.boxclans.currency.MinhHuyAnnouncer;
import com.dex.boxclans.currency.MinhHuyService;
import com.dex.boxclans.history.ClanHistory;
import com.dex.boxclans.points.KillEventManager;
import com.dex.boxclans.points.KillProcessor;
import com.dex.boxclans.points.KillRules;
import com.dex.boxclans.points.PointsAnnouncer;
import com.dex.boxclans.points.PointsConfig;
import com.dex.boxclans.points.PointsMigration;
import com.dex.boxclans.points.PointsService;
import com.dex.boxclans.gui.Guis;
import com.dex.boxclans.gui.Menu;
import com.dex.boxclans.gui.MenuListener;
import com.dex.boxclans.api.ClanServiceImpl;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.ClanIndex;
import com.dex.boxclans.clan.ClanManager;
import com.dex.boxclans.clan.InviteManager;
import com.dex.boxclans.clan.MemberLimit;
import com.dex.boxclans.clan.NoticeService;
import com.dex.boxclans.config.MenuConfig;
import com.dex.boxclans.config.Icons;
import com.dex.boxclans.config.Messages;
import com.dex.boxclans.config.Perks;
import com.dex.boxclans.config.RoleRegistry;
import com.dex.boxclans.config.Settings;
import com.dex.boxclans.config.Sounds;
import com.dex.boxclans.config.YamlFiles;
import com.dex.boxclans.hook.ItemsAdderHook;
import com.dex.boxclans.icon.IconGlyphs;
import com.dex.boxclans.listener.SessionListener;
import com.dex.boxclans.storage.Backups;
import com.dex.boxclans.storage.Database;
import com.dex.boxclans.text.ClanNameFormatter;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;

public final class BoxClansPlugin extends JavaPlugin {

    private volatile Settings settings;
    private volatile Messages messages;
    private volatile Sounds sounds;
    private volatile MenuConfig menus;
    private volatile RoleRegistry roles;
    private volatile Perks perks;
    private volatile PointsConfig pointsConfig;
    private volatile Icons icons;
    private volatile com.dex.boxclans.config.ShopConfig shopConfig;
    private com.dex.boxclans.shop.ShopService shop;
    private com.dex.boxclans.vault.VaultService vault;
    private com.dex.boxclans.shop.ShopActions shopActions;
    private com.dex.boxclans.shop.ShopEditor shopEditor;
    private com.dex.boxclans.hook.ClanBoosts clanBoosts;
    private final com.dex.boxclans.item.ItemCodec itemCodec = com.dex.boxclans.item.ItemCodec.PAPER;
    private final IconGlyphs iconGlyphs = new IconGlyphs(System::currentTimeMillis);
    private ItemsAdderHook itemsAdder;

    private Database database;
    private Backups backups;
    private final ClanIndex index = new ClanIndex();
    private ClanManager manager;
    private MemberLimit limits;
    private InviteManager invites;
    private NoticeService notices;
    private ClanServiceImpl service;
    private ChatPrompt prompts;
    private PlayerSettingsStore playerSettings;
    private ClanChat clanChat;
    private ClanActions actions;
    private Guis guis;
    private ClanAdminCommand adminCommand;
    private PointsService points;
    private ClanPointsImpl pointsApi;
    private MinhHuyService minhHuy;
    private ClanCurrencyImpl currencyApi;
    private KillEventManager killEvents;
    private KillProcessor kills;
    private KillFeedback killFeedback;
    private Object placeholderHook;
    private Object luckPermsHook;

    @Override
    public void onEnable() {
        if (!loadConfigs()) {
            getLogger().severe("Could not load the configuration, BoxClans is disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        database = Database.open(new File(getDataFolder(), Database.FILE), getLogger());
        if (database == null) {
            getLogger().severe("The database is unavailable, BoxClans is disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        limits = new MemberLimit(player -> {
            Player p = Bukkit.getPlayer(player);
            if (p == null) return null;
            return new MemberLimit.Granted() {
                @Override
                public boolean test(String permission) {
                    return p.hasPermission(permission);
                }

                @Override
                public java.util.Collection<String> all() {
                    java.util.List<String> out = new java.util.ArrayList<>();
                    for (var info : p.getEffectivePermissions()) if (info.getValue()) out.add(info.getPermission());
                    return out;
                }
            };
        }, perks);
        invites = new InviteManager(System::currentTimeMillis, settings.inviteExpirySeconds(), settings.inviteMaxPending());
        notices = new NoticeService(this, database, this::messages);
        ClanManager.Events events = new ClanManager.Events() {
            @Override
            public <T extends Event> T call(T event) {
                Bukkit.getPluginManager().callEvent(event);
                return event;
            }
        };
        manager = new ClanManager(index, database, invites, limits, roles, settings.defaultFriendlyFire(),
                events, notices, System::currentTimeMillis, new SecureRandom());
        manager.defaultIcon(icons.defaultIcon());
        manager.history(new ClanHistory(database, System::currentTimeMillis, com.dex.boxclans.util.Names::of).recorder());
        try {
            List<ClanData> loaded = database.loadClans(ClanNameFormatter::render);
            manager.load(loaded);
            getLogger().info("Loaded " + loaded.size() + " clan(s)");
            // members saved before 'last seen' existed: read once from the server's player data
            int filled = manager.backfillLastSeen(id -> Bukkit.getOfflinePlayer(id).getLastSeen());
            if (filled > 0) getLogger().info("Filled in the last online time of " + filled + " member(s)");
        } catch (SQLException ex) {
            getLogger().log(Level.SEVERE, "Could not load clans, BoxClans is disabled", ex);
            database.close();
            database = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        points = new PointsService(manager, database, events, System::currentTimeMillis, pointsConfig,
                new PointsAnnouncer(this));
        try {
            points.daily().load();
        } catch (SQLException ex) {
            getLogger().log(Level.WARNING, "Could not read today's point counters, they start at 0", ex);
        }
        minhHuy = new MinhHuyService(manager, database, events, new MinhHuyAnnouncer(this));
        shop = new com.dex.boxclans.shop.ShopService(database, this::shopConfig, System::currentTimeMillis);
        vault = new com.dex.boxclans.vault.VaultService(database, System::currentTimeMillis);
        try {
            shop.load();
            vault.load();
        } catch (SQLException ex) {
            // never run with an empty view of the shop or the storages: a write would overwrite stored rows
            getLogger().log(Level.SEVERE, "Could not load the clan shop / storages, BoxClans is disabled", ex);
            database.close();
            database = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        pruneLog();
        if (Bukkit.getPluginManager().isPluginEnabled("BoxCore")) hookBoxCore();
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) hookLuckPerms();
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) hookItemsAdder();

        killEvents = new KillEventManager(this::pointsConfig, events, System::currentTimeMillis, (task, delay) -> {
            var handle = getServer().getScheduler().runTaskLater(this, task, Math.max(1, delay / 50));
            return handle::cancel;
        });
        killFeedback = new KillFeedback(this, new File(getDataFolder(), "antialt.log"));
        kills = new KillProcessor(manager, points, new KillRules(settings.antiFarm()), killEvents, killFeedback,
                System::currentTimeMillis);
        kills.staffWindow(Math.max(600, settings.antiFarm().networkWindow()));

        service = new ClanServiceImpl(index, this::roles);
        pointsApi = new ClanPointsImpl(() -> points, Bukkit::isPrimaryThread,
                task -> Bukkit.getScheduler().runTask(this, task), getLogger());
        getServer().getServicesManager().register(ClanService.class, service, this, ServicePriority.Normal);
        pointsApi.killEvents(killEvents);
        getServer().getServicesManager().register(ClanPoints.class, pointsApi, this, ServicePriority.Normal);
        currencyApi = new ClanCurrencyImpl(() -> minhHuy, index, Bukkit::isPrimaryThread,
                task -> Bukkit.getScheduler().runTask(this, task), getLogger());
        getServer().getServicesManager().register(ClanCurrency.class, currencyApi, this, ServicePriority.Normal);

        prompts = new ChatPrompt(System::currentTimeMillis, settings.promptTimeoutSeconds(), settings.cancelWords());
        playerSettings = new PlayerSettingsStore(database);
        clanChat = new ClanChat(this);
        actions = new ClanActions(this);
        shopActions = new com.dex.boxclans.shop.ShopActions(this);
        shopEditor = new com.dex.boxclans.shop.ShopEditor(this);
        guis = new Guis(this);
        adminCommand = new ClanAdminCommand(this);
        command("clan", new ClanCommand(this));
        command("cc", new ClanChatCommand(this));
        command("clanadmin", adminCommand);
        adminCommand.registerPoints(new PointsCommand(this));
        adminCommand.register("killevent", new KillEventCommand(this));
        adminCommand.register("icon", new IconCommand(this));
        adminCommand.register("minhhuy", new MinhHuyCommand(this));
        adminCommand.register("shop", new com.dex.boxclans.command.ShopCommand(this));
        adminCommand.register("kho", new com.dex.boxclans.command.VaultCommand(this));
        adminCommand.register("boost", new com.dex.boxclans.command.BoostCommand(this));
        adminCommand.register("history", new com.dex.boxclans.command.HistoryCommand(this));

        var pm = getServer().getPluginManager();
        pm.registerEvents(new SessionListener(manager, notices, points), this);
        pm.registerEvents(new PromptListener(this, prompts), this);
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new com.dex.boxclans.listener.ShopListener(shop, vault), this);
        if (Bukkit.getPluginManager().isPluginEnabled("BoxCore")) hookClanBoosts();
        getServer().getScheduler().runTaskTimer(this, prompts::expire, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, this::pruneLog, 72_000L, 72_000L);
        backups = new Backups(database, getDataFolder(), () -> settings.backup(), () -> pointsConfig.zone(),
                System::currentTimeMillis, getLogger());
        // first check one minute after start-up, then hourly: today's copy is made once, soon after 0h
        getServer().getScheduler().runTaskTimer(this, backups::runIfDue, 1_200L, 72_000L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            kills.cleanup();
            points.cleanup();
        }, 6_000L, 6_000L);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) registerPlaceholders();
        for (Player p : Bukkit.getOnlinePlayers()) {
            manager.onJoin(p.getUniqueId());
            playerSettings.load(p.getUniqueId());
        }
        getLogger().info("BoxClans " + getPluginMeta().getVersion() + " enabled");
    }

    /** Separate method: BoxCore classes are only loaded when BoxCore is installed (§10.4). */
    private void hookBoxCore() {
        try {
            com.dex.boxclans.hook.BoxCoreHook hook = com.dex.boxclans.hook.BoxCoreHook.create();
            if (hook == null) {
                getLogger().warning("BoxCore is enabled but provides no BoostService: clan points are not boosted");
                return;
            }
            points.booster(hook);
            String type = pointsConfig.boostType();
            if (!type.isEmpty() && !hook.isType(type)) {
                getLogger().warning("[points.yml] boxcore-boost-type '" + type + "' is not a BoxCore boost type: "
                        + "the multiplier stays 1");
            }
            getLogger().info("Hooked into BoxCore boosts");
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Could not hook into BoxCore, clan points are not boosted", t);
        }
    }

    /**
     * Separate method, like {@link #hookBoxCore}: clan boosts need BoxCore 1.5.0 (API 1.3.0). An older BoxCore leaves
     * clan bottles unusable in the storage and {@code /clanadmin boost} unavailable; nothing else changes.
     */
    private void hookClanBoosts() {
        try {
            com.dex.boxclans.hook.BoxCoreClansHook hook = com.dex.boxclans.hook.BoxCoreClansHook.create(this, index,
                    manager::history);
            if (hook == null) return;
            getServer().getPluginManager().registerEvents(hook, this);
            clanBoosts = hook;
            getLogger().info("Hooked into BoxCore clan boosts");
        } catch (Throwable t) {
            getLogger().warning("BoxCore has no clan boosts (needs BoxCore 1.5.0): clan bottles cannot be used from "
                    + "the storage (" + t + ")");
        }
    }

    /** Separate method: LuckPerms classes are only loaded when LuckPerms is installed (§6). */
    private void hookLuckPerms() {
        try {
            com.dex.boxclans.hook.LuckPermsHook hook = com.dex.boxclans.hook.LuckPermsHook.register(this, manager);
            if (hook == null) {
                getLogger().warning("LuckPerms is enabled but the API is unavailable: member limits won't auto-refresh");
                return;
            }
            luckPermsHook = hook;
            getLogger().info("Hooked into LuckPerms: member limits refresh on permission changes");
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Could not hook into LuckPerms, member limits won't auto-refresh", t);
        }
    }

    /** Clan shield icons come from ItemsAdder font images; refreshed whenever ItemsAdder (re)loads its data. */
    private void hookItemsAdder() {
        itemsAdder = ItemsAdderHook.create(this);
        if (itemsAdder == null) return;
        itemsAdder.onLoad(this::rebuildIcons);
        rebuildIcons();
        getLogger().info("Hooked into ItemsAdder: clan shield icons enabled");
    }

    /** Main thread: re-reads the icon characters (after an ItemsAdder load or a reload). */
    public void rebuildIcons() {
        iconGlyphs.rebuild(icons, itemsAdder);
    }

    /** Separate method: PlaceholderAPI classes are only loaded when the plugin is installed. */
    private void registerPlaceholders() {
        try {
            com.dex.boxclans.hook.PlaceholderHook hook = new com.dex.boxclans.hook.PlaceholderHook(
                    new com.dex.boxclans.hook.PlaceholderValues(this), getPluginMeta().getVersion());
            if (hook.register()) {
                placeholderHook = hook;
                getLogger().info("Registered PlaceholderAPI expansion 'boxclans'");
            } else {
                getLogger().warning("Could not register the PlaceholderAPI expansion 'boxclans'");
            }
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Could not register the PlaceholderAPI expansion", t);
        }
    }

    private void command(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command /" + name + " missing from plugin.yml");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) p.closeInventory();
        }
        if (prompts != null) prompts.clear();
        if (placeholderHook != null) {
            try {
                ((com.dex.boxclans.hook.PlaceholderHook) placeholderHook).unregister();
            } catch (Throwable ignored) {
                // PlaceholderAPI may already be disabled
            }
            placeholderHook = null;
        }
        if (luckPermsHook != null) {
            try {
                ((com.dex.boxclans.hook.LuckPermsHook) luckPermsHook).close();
            } catch (Throwable ignored) {
                // LuckPerms may already be disabled
            }
            luckPermsHook = null;
        }
        if (killEvents != null) killEvents.stop();
        if (killFeedback != null) killFeedback.close();
        getServer().getServicesManager().unregisterAll(this);
        if (manager != null && database != null) {
            for (Player p : Bukkit.getOnlinePlayers()) manager.seen(p.getUniqueId());
        }
        if (database != null) {
            database.close();
            database = null;
        }
    }

    /** Loads every YAML file; on failure the previous snapshots stay in use. */
    public boolean loadConfigs() {
        try {
            Settings newSettings = Settings.from(YamlFiles.load(this, Settings.FILE));
            Messages newMessages = Messages.from(YamlFiles.loadText(this, Messages.FILE));
            Sounds newSounds = Sounds.from(YamlFiles.load(this, Sounds.FILE), getLogger());
            RoleRegistry newRoles = RoleRegistry.from(YamlFiles.load(this, RoleRegistry.FILE), getLogger());
            Perks newPerks = Perks.from(YamlFiles.load(this, Perks.FILE));
            PointsMigration.upgrade(this);
            PointsConfig newPoints = PointsConfig.from(YamlFiles.load(this, PointsConfig.FILE), getLogger());
            Icons newIcons = Icons.from(YamlFiles.loadText(this, Icons.FILE), getLogger());
            com.dex.boxclans.config.ShopConfig newShop = com.dex.boxclans.config.ShopConfig.from(
                    YamlFiles.load(this, com.dex.boxclans.config.ShopConfig.FILE), getLogger());
            MenuConfig newMenus = MenuConfig.load(this);
            settings = newSettings;
            messages = newMessages;
            sounds = newSounds;
            roles = newRoles;
            perks = newPerks;
            pointsConfig = newPoints;
            icons = newIcons;
            shopConfig = newShop;
            if (itemsAdder != null) rebuildIcons();
            if (points != null) points.configure(newPoints);
            menus = newMenus;
            if (manager != null) {
                manager.reconfigure(newRoles, newSettings.defaultFriendlyFire());
                manager.defaultIcon(newIcons.defaultIcon());
                limits.perks(newPerks);
                invites.configure(newSettings.inviteExpirySeconds(), newSettings.inviteMaxPending());
            }
            if (prompts != null) prompts.configure(newSettings.promptTimeoutSeconds(), newSettings.cancelWords());
            if (kills != null) {
                kills.rules().configure(newSettings.antiFarm());
                kills.staffWindow(Math.max(600, newSettings.antiFarm().networkWindow()));
            }
            return true;
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "Configuration error", ex);
            return false;
        }
    }

    /** {@code /clanadmin reload}: every file; the old configuration stays when one fails. */
    public boolean reload() {
        return loadConfigs();
    }

    /** Notices (kicked, clan disbanded...) waiting this long for an offline player are dropped. */
    private static final int NOTICE_KEEP_DAYS = 30;

    /**
     * Deletes {@code points_log} rows older than {@code log-keep-days}, clan history older than
     * {@code history.keep-days} and notices older than 30 days (at start-up, then hourly).
     */
    private void pruneLog() {
        if (database == null) return;
        long now = System.currentTimeMillis();
        int days = pointsConfig.logKeepDays();
        if (days > 0) database.pruneLog(now - days * 86_400_000L);
        database.pruneHistory(now - settings.historyKeepDays() * 86_400_000L);
        database.pruneNotices(now - NOTICE_KEEP_DAYS * 86_400_000L);
    }

    public Icons icons() {
        return icons;
    }

    public IconGlyphs iconGlyphs() {
        return iconGlyphs;
    }

    /** {@code null} without ItemsAdder. */
    public ItemsAdderHook itemsAdder() {
        return itemsAdder;
    }

    public KillEventManager killEvents() {
        return killEvents;
    }

    public KillProcessor kills() {
        return kills;
    }

    public PointsService points() {
        return points;
    }

    public ClanPointsImpl pointsApi() {
        return pointsApi;
    }

    /** Minh Huy, the clan currency (BOXCLANS_SHOP_SPEC.md §2). */
    public com.dex.boxclans.config.ShopConfig shopConfig() {
        return shopConfig;
    }

    public com.dex.boxclans.shop.ShopService shop() {
        return shop;
    }

    public com.dex.boxclans.vault.VaultService vault() {
        return vault;
    }

    public com.dex.boxclans.shop.ShopActions shopActions() {
        return shopActions;
    }

    public com.dex.boxclans.shop.ShopEditor shopEditor() {
        return shopEditor;
    }

    /** Clan boosts of BoxCore 1.5.0, {@code null} without it. */
    public com.dex.boxclans.hook.ClanBoosts clanBoosts() {
        return clanBoosts;
    }

    public com.dex.boxclans.item.ItemCodec itemCodec() {
        return itemCodec;
    }

    public MinhHuyService minhHuy() {
        return minhHuy;
    }

    public ClanCurrencyImpl currencyApi() {
        return currencyApi;
    }

    public PointsConfig pointsConfig() {
        return pointsConfig;
    }

    public ClanServiceImpl service() {
        return service;
    }

    public ChatPrompt prompts() {
        return prompts;
    }

    public PlayerSettingsStore playerSettings() {
        return playerSettings;
    }

    public ClanChat clanChat() {
        return clanChat;
    }

    public ClanActions actions() {
        return actions;
    }

    public Guis guis() {
        return guis;
    }

    public ClanAdminCommand adminCommand() {
        return adminCommand;
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public Sounds sounds() {
        return sounds;
    }

    public MenuConfig menus() {
        return menus;
    }

    public RoleRegistry roles() {
        return roles;
    }

    public Perks perks() {
        return perks;
    }

    public ClanManager manager() {
        return manager;
    }

    public ClanIndex index() {
        return index;
    }

    public Database database() {
        return database;
    }
}
