package com.dex.boxclans;

import com.dex.boxclans.api.ClanServiceImpl;
import com.dex.boxclans.chat.ChatPrompt;
import com.dex.boxclans.chat.ClanChat;
import com.dex.boxclans.chat.PlayerSettingsStore;
import com.dex.boxclans.clan.ClanIndex;
import com.dex.boxclans.clan.ClanManager;
import com.dex.boxclans.clan.InviteManager;
import com.dex.boxclans.clan.MemberLimit;
import com.dex.boxclans.clan.NoticeService;
import com.dex.boxclans.command.ClanActions;
import com.dex.boxclans.command.ClanAdminCommand;
import com.dex.boxclans.command.ClanChatCommand;
import com.dex.boxclans.command.ClanCommand;
import com.dex.boxclans.config.MenuConfig;
import com.dex.boxclans.config.Messages;
import com.dex.boxclans.config.Perks;
import com.dex.boxclans.config.RoleRegistry;
import com.dex.boxclans.config.Settings;
import com.dex.boxclans.config.Sounds;
import com.dex.boxclans.config.YamlFiles;
import com.dex.boxclans.gui.Guis;
import com.dex.boxclans.gui.Menu;
import com.dex.boxclans.gui.MenuAccess;
import com.dex.boxclans.storage.Database;
import com.dex.boxclans.text.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A fake Paper server for command tests: Mockito players that record their messages, an inventory that
 * records the open menu, an immediate scheduler, and every BoxClans component wired as in onEnable.
 */
public final class FakeServer implements AutoCloseable {

    static final Logger LOG = Logger.getLogger("fake");
    private static Server server;
    /** Online players of the running test (the Bukkit server is a JVM singleton). */
    static final Map<UUID, FakePlayer> ONLINE = new ConcurrentHashMap<>();
    static final Map<UUID, String> KNOWN = new ConcurrentHashMap<>();
    static final List<Event> EVENTS = new ArrayList<>();
    static final List<String> CONSOLE = new ArrayList<>();
    /** Console commands dispatched by the plugin (storage COMMAND items). */
    public static final List<String> COMMANDS = new ArrayList<>();
    /** Title of every inventory created, by holder. */
    public static final Map<InventoryHolder, String> TITLES = new ConcurrentHashMap<>();

    /** A recorded online player. */
    public static final class FakePlayer {
        public final Player player;
        public final UUID id;
        public final String name;
        public final Set<String> perms = ConcurrentHashMap.newKeySet();
        public final List<String> messages = new ArrayList<>();
        public final List<Component> components = new ArrayList<>();
        public Menu menu;
        /** 36 storage slots; {@code null} = empty. */
        public final ItemStack[] storage = new ItemStack[36];
        public ItemStack hand;
        public ItemStack cursor;

        FakePlayer(String name, Set<String> perms) {
            this.name = name;
            this.id = UUID.nameUUIDFromBytes(("fake:" + name).getBytes());
            this.perms.addAll(perms);
            Player p = mock(Player.class);
            when(p.getUniqueId()).thenReturn(id);
            when(p.getName()).thenReturn(name);
            when(p.isOnline()).thenReturn(true);
            when(p.hasPermission(anyString())).thenAnswer(inv -> this.perms.contains("*")
                    || this.perms.contains((String) inv.getArgument(0)));
            when(p.getLocation()).thenReturn(new Location(null, 0, 0, 0));
            when(p.canSee(any(Player.class))).thenReturn(true);
            org.bukkit.inventory.PlayerInventory bag = mock(org.bukkit.inventory.PlayerInventory.class);
            when(bag.getStorageContents()).thenAnswer(i -> storage.clone());
            when(bag.getItemInMainHand()).thenAnswer(i -> hand);
            when(bag.addItem(any(ItemStack[].class))).thenAnswer(i -> {
                for (Object o : i.getArguments()) {
                    for (int k = 0; k < storage.length; k++) {
                        if (storage[k] == null) {
                            storage[k] = (ItemStack) o;
                            break;
                        }
                    }
                }
                return new HashMap<Integer, ItemStack>();
            });
            when(p.getInventory()).thenReturn(bag);
            when(p.getItemOnCursor()).thenAnswer(i -> cursor);
            doAnswer(inv -> {
                Component c = inv.getArgument(0);
                components.add(c);
                messages.add(Text.plain(c));
                return null;
            }).when(p).sendMessage(any(Component.class));
            doAnswer(inv -> null).when(p).sendActionBar(any(Component.class));
            doAnswer(inv -> {
                Inventory i = inv.getArgument(0);
                menu = (Menu) i.getHolder(false);
                return null;
            }).when(p).openInventory(any(Inventory.class));
            doAnswer(inv -> {
                Menu m = menu;
                menu = null;
                if (m != null) MenuAccess.closed(m);
                return null;
            }).when(p).closeInventory();
            this.player = p;
        }

        /** Items held in the storage slots. */
        public List<ItemStack> items() {
            List<ItemStack> out = new ArrayList<>();
            for (ItemStack i : storage) if (i != null) out.add(i);
            return out;
        }

        public boolean got(String fragment) {
            for (String m : messages) if (m.contains(fragment)) return true;
            return false;
        }

        public String last() {
            return messages.isEmpty() ? "" : messages.get(messages.size() - 1);
        }

        public void clear() {
            messages.clear();
            components.clear();
        }

        /** Clicks a layout character of the open menu. */
        public void click(char c) {
            click(c, ClickType.LEFT);
        }

        public void click(char c, ClickType type) {
            if (menu == null) throw new IllegalStateException(name + " has no open menu");
            MenuAccess.click(menu, c, type);
        }

        public void clickSlot(int slot, ClickType type) {
            if (menu == null) throw new IllegalStateException(name + " has no open menu");
            MenuAccess.click(menu, slot, type);
        }
    }

    public final BoxClansPlugin plugin;
    public final Database db;
    public final ClanIndex index = new ClanIndex();
    public final ClanManager manager;
    public final Settings settings;
    public final Messages messages;
    public final ChatPrompt prompts;
    public final com.dex.boxclans.points.PointsService points;
    public final com.dex.boxclans.currency.MinhHuyService minhHuy;
    public final com.dex.boxclans.shop.ShopService shop;
    public final com.dex.boxclans.vault.VaultService vault;
    public final FakeBoosts boosts;
    /** {@code false}: as if BoxCore 1.5.0 were missing. */
    public boolean boostsInstalled = true;
    /** The clock of the shop and the storage (weekly limits). */
    public volatile long now = System.currentTimeMillis();

    /** Stores an item by its material name: same material = same item (merging), decoded as a fresh stack. */
    public static final com.dex.boxclans.item.ItemCodec CODEC = new com.dex.boxclans.item.ItemCodec() {
        @Override
        public byte[] encode(ItemStack item) {
            return item.getType().name().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public ItemStack decode(byte[] data) {
            return com.dex.boxclans.gui.MenuItems.stack(
                    org.bukkit.Material.valueOf(new String(data, java.nio.charset.StandardCharsets.UTF_8)), 1);
        }
    };

    /**
     * BoxCore clan boosts for tests: a HONEY_BOTTLE is a clan bottle "mining x2 1h", a POTION a personal one. The
     * next results of {@link #start} can be queued; otherwise STARTED.
     */
    public static final class FakeBoosts implements com.dex.boxclans.hook.ClanBoosts {
        public final java.util.Deque<Status> next = new java.util.ArrayDeque<>();
        public final List<String> calls = new ArrayList<>();

        @Override
        public Bottle readBottle(ItemStack item) {
            if (item == null) return null;
            return switch (item.getType()) {
                case HONEY_BOTTLE -> new Bottle("mining", 20, 3600, true);
                case POTION -> new Bottle("quest", 15, 600, false);
                default -> null;
            };
        }

        @Override
        public Result start(UUID clan, String type, int tenths, long seconds, boolean confirmed, String actor) {
            calls.add("start " + type + " " + tenths + " " + seconds + " " + confirmed + " " + actor);
            Status s = next.isEmpty() ? Status.STARTED : next.poll();
            return new Result(s, s == Status.STARTED ? 0 : 20, System.currentTimeMillis() + 60_000, 300);
        }

        @Override
        public boolean stop(UUID clan, String type) {
            calls.add("stop " + type);
            return true;
        }

        @Override
        public boolean isType(String type) {
            return type.equals("mining") || type.equals("quest");
        }
    }
    public final com.dex.boxclans.points.KillEventManager killEvents;
    public final com.dex.boxclans.points.KillProcessor kills;
    public final RecordingFeedback feedback;
    /** Clock of the icon animation (ms); glyphs are {@code <font image id>}. */
    public final java.util.concurrent.atomic.AtomicLong iconClock = new java.util.concurrent.atomic.AtomicLong();
    public final com.dex.boxclans.icon.IconGlyphs iconGlyphs = new com.dex.boxclans.icon.IconGlyphs(iconClock::get);
    public final com.dex.boxclans.config.Icons icons;

    /** Records kill feedback (blocked kills, caps, staff alerts, log lines). */
    public static final class RecordingFeedback implements com.dex.boxclans.points.KillProcessor.Feedback {
        public final List<String> lines = new ArrayList<>();

        @Override
        public void blocked(UUID killer, UUID victim, com.dex.boxclans.points.KillRules.Verdict verdict) {
            lines.add("blocked " + verdict);
        }

        @Override
        public void clanCap(UUID killer, long cap) {
            lines.add("cap " + cap);
        }

        @Override
        public void altSuspect(UUID killer, String killerIp, UUID victim, String victimIp,
                               com.dex.boxclans.points.KillRules.Verdict verdict) {
            lines.add("staff " + verdict);
        }

        @Override
        public void log(UUID killer, String killerIp, UUID victim, String victimIp,
                        com.dex.boxclans.points.KillRules.Verdict verdict) {
            lines.add("log " + verdict);
        }

        public long count(String prefix) {
            return lines.stream().filter(l -> l.startsWith(prefix)).count();
        }
    }
    public final ClanCommand clan;
    public final ClanChatCommand cc;
    public final ClanAdminCommand admin;
    public final Command command = mock(Command.class);
    public final ConsoleCommandSender console;

    public FakeServer(File dir) {
        this(dir, null);
    }

    /** Loads a bundled menu with some keys changed, as if the owner edited the file and ran /clanadmin reload. */
    public MenuConfig menusWith(File dir, String menu, Map<String, Object> changes) {
        Map<String, YamlConfiguration> files = new HashMap<>();
        for (String name : MenuConfig.MENUS) {
            if (getClass().getClassLoader().getResource("menus/" + name + ".yml") == null) continue;
            YamlConfiguration y = bundled("menus/" + name + ".yml", dir);
            if (name.equals(menu)) changes.forEach(y::set);
            files.put(name, y);
        }
        return MenuConfig.of(files);
    }

    public FakeServer(File dir, YamlConfiguration configOverride) {
        installServer();
        ONLINE.clear();
        KNOWN.clear();
        EVENTS.clear();
        CONSOLE.clear();
        console = server.getConsoleSender();

        YamlConfiguration cfg = bundled("config.yml", dir);
        if (configOverride != null) for (String k : configOverride.getKeys(true)) {
            if (!configOverride.isConfigurationSection(k)) cfg.set(k, configOverride.get(k));
        }
        settings = Settings.from(cfg);
        messages = Messages.from(bundled("messages.yml", dir));
        Sounds sounds = Sounds.from(bundled("sounds.yml", dir), LOG);
        RoleRegistry roles = RoleRegistry.from(bundled("roles.yml", dir), LOG);
        Perks perks = Perks.from(bundled("perks.yml", dir));
        Map<String, YamlConfiguration> menuFiles = new HashMap<>();
        for (String name : MenuConfig.MENUS) {
            if (getClass().getClassLoader().getResource("menus/" + name + ".yml") != null) {
                menuFiles.put(name, bundled("menus/" + name + ".yml", dir));
            }
        }
        MenuConfig menus = MenuConfig.of(menuFiles);

        db = Database.open(new File(dir, "clans.db"), LOG);
        plugin = mock(BoxClansPlugin.class);
        MemberLimit limits = new MemberLimit(id -> {
            FakePlayer p = ONLINE.get(id);
            return p == null ? null : p.player::hasPermission;
        }, perks);
        InviteManager invites = new InviteManager(System::currentTimeMillis, settings.inviteExpirySeconds(),
                settings.inviteMaxPending());
        NoticeService notices = new NoticeService(plugin, db, () -> messages);
        manager = new ClanManager(index, db, invites, limits, roles, settings.defaultFriendlyFire(),
                new ClanManager.Events() {
                    @Override
                    public <T extends Event> T call(T event) {
                        EVENTS.add(event);
                        return event;
                    }
                }, notices, System::currentTimeMillis, new Random(7));
        // 1.2.0: the clan history, names as the plugin resolves them
        manager.history(new com.dex.boxclans.history.ClanHistory(db, System::currentTimeMillis,
                com.dex.boxclans.util.Names::of).recorder());
        prompts = new ChatPrompt(System::currentTimeMillis, settings.promptTimeoutSeconds(), settings.cancelWords());
        PlayerSettingsStore playerSettings = new PlayerSettingsStore(db);

        when(plugin.getLogger()).thenReturn(LOG);
        when(plugin.settings()).thenReturn(settings);
        when(plugin.messages()).thenReturn(messages);
        when(plugin.sounds()).thenReturn(sounds);
        when(plugin.roles()).thenReturn(roles);
        when(plugin.perks()).thenReturn(perks);
        when(plugin.menus()).thenReturn(menus);
        when(plugin.index()).thenReturn(index);
        when(plugin.manager()).thenReturn(manager);
        when(plugin.database()).thenReturn(db);
        when(plugin.prompts()).thenReturn(prompts);
        when(plugin.playerSettings()).thenReturn(playerSettings);
        ClanChat chat = new ClanChat(plugin);
        when(plugin.clanChat()).thenReturn(chat);
        ClanActions actions = new ClanActions(plugin);
        when(plugin.actions()).thenReturn(actions);
        Guis guis = new Guis(plugin);
        when(plugin.guis()).thenReturn(guis);
        when(plugin.service()).thenReturn(new ClanServiceImpl(index, () -> roles));
        com.dex.boxclans.points.PointsConfig pointsConfig = com.dex.boxclans.points.PointsConfig.from(
                bundled("points.yml", dir), LOG);
        points = new com.dex.boxclans.points.PointsService(manager, db, new ClanManager.Events() {
            @Override
            public <T extends Event> T call(T event) {
                EVENTS.add(event);
                return event;
            }
        }, System::currentTimeMillis, pointsConfig, new com.dex.boxclans.points.PointsAnnouncer(plugin));
        when(plugin.points()).thenReturn(points);
        killEvents = new com.dex.boxclans.points.KillEventManager(() -> pointsConfig, new ClanManager.Events() {
            @Override
            public <T extends Event> T call(T event) {
                EVENTS.add(event);
                return event;
            }
        }, System::currentTimeMillis, (task, delay) -> () -> { });
        feedback = new RecordingFeedback();
        kills = new com.dex.boxclans.points.KillProcessor(manager, points,
                new com.dex.boxclans.points.KillRules(settings.antiFarm()), killEvents, feedback, System::currentTimeMillis);
        when(plugin.killEvents()).thenReturn(killEvents);
        when(plugin.kills()).thenReturn(kills);
        when(plugin.pointsConfig()).thenReturn(pointsConfig);
        // 1.3.0: shop, storage, a fake item codec (material names) and fake BoxCore clan boosts
        com.dex.boxclans.config.ShopConfig shopConfig = com.dex.boxclans.config.ShopConfig.from(
                bundled("shop.yml", dir), LOG);
        when(plugin.shopConfig()).thenReturn(shopConfig);
        when(plugin.itemCodec()).thenReturn(CODEC);
        shop = new com.dex.boxclans.shop.ShopService(db, () -> shopConfig, () -> now);
        vault = new com.dex.boxclans.vault.VaultService(db, () -> now);
        try {
            shop.load();
            vault.load();
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException(ex);
        }
        when(plugin.shop()).thenReturn(shop);
        when(plugin.vault()).thenReturn(vault);
        boosts = new FakeBoosts();
        when(plugin.clanBoosts()).thenAnswer(i -> boostsInstalled ? boosts : null);
        minhHuy = new com.dex.boxclans.currency.MinhHuyService(manager, db, new ClanManager.Events() {
            @Override
            public <T extends Event> T call(T event) {
                EVENTS.add(event);
                return event;
            }
        }, new com.dex.boxclans.currency.MinhHuyAnnouncer(plugin));
        when(plugin.minhHuy()).thenReturn(minhHuy);
        admin = new ClanAdminCommand(plugin);
        admin.registerPoints(new com.dex.boxclans.command.PointsCommand(plugin));
        admin.register("killevent", new com.dex.boxclans.command.KillEventCommand(plugin));
        admin.register("icon", new com.dex.boxclans.command.IconCommand(plugin));
        admin.register("minhhuy", new com.dex.boxclans.command.MinhHuyCommand(plugin));
        admin.register("shop", new com.dex.boxclans.command.ShopCommand(plugin));
        admin.register("kho", new com.dex.boxclans.command.VaultCommand(plugin));
        admin.register("boost", new com.dex.boxclans.command.BoostCommand(plugin));
        admin.register("history", new com.dex.boxclans.command.HistoryCommand(plugin));
        com.dex.boxclans.shop.ShopActions shopActions = new com.dex.boxclans.shop.ShopActions(plugin);
        when(plugin.shopActions()).thenReturn(shopActions);
        com.dex.boxclans.shop.ShopEditor shopEditor = new com.dex.boxclans.shop.ShopEditor(plugin);
        when(plugin.shopEditor()).thenReturn(shopEditor);
        icons = com.dex.boxclans.config.Icons.from(bundled("icons.yml", dir), LOG);
        iconGlyphs.rebuild(icons, id -> "<" + id + ">");
        when(plugin.icons()).thenReturn(icons);
        when(plugin.iconGlyphs()).thenReturn(iconGlyphs);
        when(plugin.adminCommand()).thenReturn(admin);
        clan = new ClanCommand(plugin);
        cc = new ClanChatCommand(plugin);
    }

    private YamlConfiguration bundled(String name, File dir) {
        return YamlFiles.load(new File(dir, "absent-" + name.replace('/', '-')),
                getClass().getClassLoader().getResourceAsStream(name), LOG);
    }

    public FakePlayer join(String name, String... perms) {
        Set<String> set = new java.util.HashSet<>(List.of(perms));
        set.add("boxclans.use");
        set.add("boxclans.create");
        set.add("boxclans.chat");
        FakePlayer p = new FakePlayer(name, set);
        ONLINE.put(p.id, p);
        KNOWN.put(p.id, name);
        return p;
    }

    public void quit(FakePlayer p) {
        ONLINE.remove(p.id);
    }

    public void clan(FakePlayer p, String... args) {
        clan.onCommand(p.player, command, "clan", args);
    }

    public void cc(FakePlayer p, String... args) {
        cc.onCommand(p.player, command, "cc", args);
    }

    public void admin(org.bukkit.command.CommandSender sender, String... args) {
        admin.onCommand(sender, command, "clanadmin", args);
    }

    /** Types a line in chat while a prompt is open. */
    public ChatPrompt.Result type(FakePlayer p, String text) {
        return prompts.input(p.id, text);
    }

    @Override
    public void close() {
        db.close();
        ONLINE.clear();
    }

    // ------------------------------------------------------------------ the singleton server

    private static synchronized void installServer() {
        if (server != null) return;
        Server s = mock(Server.class);
        when(s.getLogger()).thenReturn(LOG);
        when(s.getOnlinePlayers()).thenAnswer(inv -> {
            List<Player> out = new ArrayList<>();
            for (FakePlayer p : ONLINE.values()) out.add(p.player);
            return out;
        });
        when(s.getPlayer(any(UUID.class))).thenAnswer(inv -> {
            FakePlayer p = ONLINE.get((UUID) inv.getArgument(0));
            return p == null ? null : p.player;
        });
        when(s.getPlayerExact(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            for (FakePlayer p : ONLINE.values()) if (p.name.equalsIgnoreCase(name)) return p.player;
            return null;
        });
        when(s.getOfflinePlayer(any(UUID.class))).thenAnswer(inv -> offline(inv.getArgument(0)));
        when(s.getOfflinePlayerIfCached(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            for (Map.Entry<UUID, String> e : KNOWN.entrySet()) {
                if (e.getValue().equalsIgnoreCase(name)) return offline(e.getKey());
            }
            return null;
        });
        when(s.getItemFactory()).thenReturn(mock(org.bukkit.inventory.ItemFactory.class));
        com.dex.boxclans.gui.MenuItems.factory((material, amount) -> {
            ItemStack item = mock(ItemStack.class);
            when(item.getType()).thenReturn(material);
            when(item.getAmount()).thenReturn(amount);
            when(item.clone()).thenReturn(item);
            return item;
        });
        PluginManager pm = mock(PluginManager.class);
        when(s.getPluginManager()).thenReturn(pm);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        }).when(scheduler).runTask(any(Plugin.class), any(Runnable.class));
        when(s.getScheduler()).thenReturn(scheduler);
        when(s.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                .thenAnswer(inv -> {
                    TITLES.put(inv.getArgument(0), Text.plain(inv.getArgument(2)));
                    return inventory(inv.getArgument(0), inv.getArgument(1));
                });
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(console.getName()).thenReturn("CONSOLE");
        when(console.hasPermission(anyString())).thenReturn(true);
        doAnswer(inv -> {
            CONSOLE.add(Text.plain(inv.getArgument(0)));
            return null;
        }).when(console).sendMessage(any(Component.class));
        when(s.getConsoleSender()).thenReturn(console);
        when(s.dispatchCommand(any(org.bukkit.command.CommandSender.class), anyString())).thenAnswer(inv -> {
            COMMANDS.add(inv.getArgument(1));
            return true;
        });
        try {
            // Bukkit.setServer prints the build info, which a test classpath does not have
            java.lang.reflect.Field f = Bukkit.class.getDeclaredField("server");
            f.setAccessible(true);
            if (f.get(null) == null) f.set(null, s);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        server = s;
    }

    private static OfflinePlayer offline(UUID id) {
        OfflinePlayer p = mock(OfflinePlayer.class);
        when(p.getUniqueId()).thenReturn(id);
        when(p.getName()).thenReturn(KNOWN.get(id));
        return p;
    }

    private static Inventory inventory(InventoryHolder holder, int size) {
        Inventory inv = mock(Inventory.class);
        Map<Integer, ItemStack> items = new LinkedHashMap<>();
        when(inv.getHolder()).thenReturn(holder);
        when(inv.getHolder(false)).thenReturn(holder);
        when(inv.getSize()).thenReturn(size);
        doAnswer(i -> {
            items.put(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(inv).setItem(anyInt(), any());
        when(inv.getItem(anyInt())).thenAnswer(i -> items.get((Integer) i.getArgument(0)));
        doAnswer(i -> {
            items.clear();
            return null;
        }).when(inv).clear();
        return inv;
    }
}
