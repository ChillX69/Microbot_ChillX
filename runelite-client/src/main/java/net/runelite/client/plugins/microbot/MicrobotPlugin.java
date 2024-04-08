package net.runelite.client.plugins.microbot;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ProfileManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.NPCManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.envisionplugins.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.cooking.CookingScript;
import net.runelite.client.plugins.microbot.mining.MiningScript;
import net.runelite.client.plugins.microbot.quest.QuestScript;
import net.runelite.client.plugins.microbot.staticwalker.StaticWalkerScript;
import net.runelite.client.plugins.microbot.staticwalker.pathfinder.WorldDataDownloader;
import net.runelite.client.plugins.microbot.thieving.ThievingScript;
import net.runelite.client.plugins.microbot.thieving.summergarden.SummerGardenConfig;
import net.runelite.client.plugins.microbot.thieving.summergarden.SummerGardenPlugin;
import net.runelite.client.plugins.microbot.thieving.summergarden.SummerGardenScript;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.event.EventSelector;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;
import net.runelite.client.plugins.microbot.util.walker.Walker;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Microbot",
        description = "Microbot",
        tags = {"main", "microbot", "parent"},
        alwaysOn = true,
        hidden = true
)
@Slf4j
public class MicrobotPlugin extends Plugin {
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ClientToolbar clientToolbar;
    @Inject
    Notifier notifier;
    @Inject
    WorldService worldService;
    @Inject
    ProfileManager profileManager;
    @Inject
    ItemManager itemManager;
    @Inject
    NPCManager npcManager;
    @Inject
    ConfigManager configManager;
    @Inject
    ChatMessageManager chatMessageManager;
    @Inject
    PluginManager pluginManager;

    @Inject
    private MicrobotOverlay microbotOverlay;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private SpriteManager spriteManager;
    @Inject
    private WorldMapOverlay worldMapOverlay;
    @Inject
    @Named("disableWalkerUpdate")
    private boolean disableWalkerUpdate;

    @Inject
    private Rs2NpcManager rs2NpcManager;

    private Plugin summerGardenPlugin = null;

    public ThievingScript thievingScript;
    public CookingScript cookingScript;
    public MiningScript miningScript;
    public SummerGardenScript summerGardenScript;
    public StaticWalkerScript staticWalkerScript;
    private Point lastMenuOpenedPoint;
    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts = false;
        Microbot.setClient(client);
        Microbot.setClientThread(clientThread);
        Microbot.setNotifier(notifier);
        Microbot.setWorldService(worldService);
        Microbot.setProfileManager(profileManager);
        Microbot.setItemManager(itemManager);
        Microbot.setNpcManager(npcManager);
        Microbot.setWalker(new Walker());
        Microbot.setMouse(new VirtualMouse());
        Microbot.setSpriteManager(spriteManager);
        Microbot.setDisableWalkerUpdate(disableWalkerUpdate);
        Microbot.setPluginManager(pluginManager);
        Microbot.setWorldMapOverlay(worldMapOverlay);
        if (overlayManager != null) {
            overlayManager.add(microbotOverlay);
        }

        new EventSelector(clientToolbar);

        WorldDataDownloader worldDataDownloader = new WorldDataDownloader();
        worldDataDownloader.run();

        BreakHandlerScript.initBreakHandler("Microbot", false);

        //TODO: Rs2NpcManager.loadJson();

        for (Plugin plugin : pluginManager.getPlugins()) {
            if (plugin.getClass() == SummerGardenPlugin.class) {
                summerGardenPlugin = plugin;
            }
        }
    }

    protected void shutDown() {
        BreakHandlerScript.disableParentPlugin();
        overlayManager.remove(microbotOverlay);
        Microbot.setWalker(null);
        if (cookingScript != null) {
            cookingScript.shutdown();
            cookingScript = null;
        }
        if (thievingScript != null) {
            thievingScript.shutdown();
            thievingScript = null;
        }
    }


    @Subscribe
    public void onStatChanged(StatChanged statChanged) {
        Microbot.setIsGainingExp(true);
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == InventoryID.BANK.getId()) {
            Rs2Bank.storeBankItemsInMemory(event);
        }
        if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
            Rs2Inventory.storeInventoryItemsInMemory(event);
        }
        if (event.getContainerId() == InventoryID.EQUIPMENT.getId()) {
            Rs2Equipment.storeEquipmentItemsInMemory(event);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.HOPPING || gameStateChanged.getGameState() == GameState.LOGIN_SCREEN || gameStateChanged.getGameState() == GameState.CONNECTION_LOST) {
            if (Rs2Bank.bankItems != null)
                Rs2Bank.bankItems.clear();
        }
    }

    private Consumer<MenuEntry> menuActionNpcConsumer(boolean shift, net.runelite.api.NPC npc) {
        return e ->
        {
            if (thievingScript == null) {
                thievingScript = new ThievingScript();
            } else {
                thievingScript.shutdown();
                thievingScript = null;
            }
        };
    }

    private Consumer<MenuEntry> menuActionCookingConsumer(int gameObjectId) {
        return e ->
        {
            if (cookingScript == null) {
                cookingScript = new CookingScript();
                cookingScript.run(gameObjectId);
            } else {
                cookingScript.shutdown();
                cookingScript = null;
            }
        };
    }

    private Consumer<MenuEntry> menuActionMinerConsumer(int gameObjectId) {
        return e ->
        {
            if (miningScript == null) {
                miningScript = new MiningScript();
                miningScript.run(gameObjectId);
            } else {
                miningScript.shutdown();
                miningScript = null;
            }
        };
    }

    private Consumer<MenuEntry> menuActionSummerGarden() {
        return e ->
        {
            if (summerGardenScript == null) {
                summerGardenScript = new SummerGardenScript();
                summerGardenScript.run(configManager.getConfig(SummerGardenConfig.class), chatMessageManager);
                startPlugin(summerGardenPlugin);

            } else {
                summerGardenScript.shutdown();
                summerGardenScript = null;
            }
        };
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event) {
        lastMenuOpenedPoint = client.getMouseCanvasPosition();
        MenuEntry[] entries = event.getMenuEntries();
        MenuEntry npcEntry = Arrays.stream(entries).filter(x -> x.getType() == MenuAction.EXAMINE_NPC).findFirst().orElse(null);
        MenuEntry objectEntry = Arrays.stream(entries).filter(x -> x.getType() == MenuAction.EXAMINE_OBJECT).findFirst().orElse(null);

        if (npcEntry != null) {
            net.runelite.api.NPC npc = Rs2Npc.getNpcByIndex(npcEntry.getIdentifier());

            List<MenuEntry> leftClickMenus = new ArrayList<>(entries.length + 2);

            if (Arrays.stream(event.getMenuEntries()).anyMatch(x -> x.getOption().equalsIgnoreCase("pickpocket"))) {
                leftClickMenus.add(Microbot.getClient().createMenuEntry(0)
                        .setOption(thievingScript == null ? "Start AutoThiever" : "Stop AutoThiever")
                        .setType(MenuAction.RUNELITE)
                        .onClick(menuActionNpcConsumer(false, npc)));
            }
        }
        if (objectEntry != null) {
            // Currently only supports alkharid furnace
            if (objectEntry.getIdentifier() == ObjectID.RANGE_26181) {

                List<MenuEntry> leftClickMenus = new ArrayList<>(entries.length + 2);

                leftClickMenus.add(Microbot.getClient().createMenuEntry(0)
                        .setOption(cookingScript == null ? "Start AutoCooker" : "Stop AutoCooker")
                        .setType(MenuAction.RUNELITE)
                        .onClick(menuActionCookingConsumer( objectEntry.getIdentifier())));
            } else if(objectEntry.getIdentifier() == ObjectID.SQIRK_TREE) {
                List<MenuEntry> leftClickMenus = new ArrayList<>(entries.length + 2);

                leftClickMenus.add(Microbot.getClient().createMenuEntry(0)
                        .setOption(summerGardenScript == null ? "Start SummerGarden" : "Stop SummerGarden")
                        .setType(MenuAction.RUNELITE)
                        .onClick(menuActionSummerGarden()));
            } else if (objectEntry.getTarget().toLowerCase().contains("rock")) {
                List<MenuEntry> leftClickMenus = new ArrayList<>(entries.length + 2);

                leftClickMenus.add(Microbot.getClient().createMenuEntry(0)
                        .setOption(miningScript == null ? "Start AutoMiner" : "Stop AutoMiner")
                        .setType(MenuAction.RUNELITE)
                        .onClick(menuActionMinerConsumer( objectEntry.getIdentifier())));
            }
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        Rs2Player.handlePotionTimers(event);
    }

    @Subscribe
    protected void onClientTick(ClientTick t) {
        if (!pluginManager.isActive(summerGardenPlugin) && summerGardenScript != null) {
            summerGardenScript.shutdown();
            summerGardenScript = null;
        }
        else if (pluginManager.isActive(summerGardenPlugin) && summerGardenScript == null) {
            summerGardenScript = new SummerGardenScript();
            summerGardenScript.run(configManager.getConfig(SummerGardenConfig.class), chatMessageManager);
            startPlugin(summerGardenPlugin);
        }
    }

    @Subscribe(priority = 999)
    private void onMenuEntryAdded(MenuEntryAdded event) {
        if (Microbot.targetMenu != null && event.getType() != Microbot.targetMenu.getType().getId()) {
            this.client.setMenuEntries(new MenuEntry[]{});
        }

        final Widget map = client.getWidget(WidgetInfo.WORLD_MAP_VIEW);

        if (map != null) {
            if (map.getBounds().contains(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY())) {
                addMapMenuEntries(event);
            }
        }

       // if (event.getType() != MenuAction.CC_OP.getId() || event.getActionParam1() != WORLD_SWITCHER_LIST.getId() && event.getActionParam1() != 11927560 && event.getActionParam1() != 4522007 && event.getActionParam1() != 24772686) {
            if (Microbot.targetMenu != null) {
                MenuEntry entry =
                        this.client.createMenuEntry(-1)
                                .setOption(Microbot.targetMenu.getOption())
                                .setTarget(Microbot.targetMenu.getTarget())
                                .setIdentifier(Microbot.targetMenu.getIdentifier())
                                .setType(Microbot.targetMenu.getType())
                                .setParam0(Microbot.targetMenu.getParam0())
                                .setParam1(Microbot.targetMenu.getParam1())
                                .setForceLeftClick(true);

                if (Microbot.targetMenu.getItemId() > 0) {
                    try {
                        Rs2Reflection.setItemId(entry, Microbot.targetMenu.getItemId());
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
                this.client.setMenuEntries(new MenuEntry[] {entry});
            }
        //}
    }

    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked event) {
        Microbot.targetMenu = null;
        System.out.println(event.getMenuEntry());
    }

    @SneakyThrows
    private void startPlugin(Plugin p) {
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                pluginManager.setPluginEnabled(p, true);
                pluginManager.startPlugin(p);
            }
            catch (PluginInstantiationException e)
            {
                System.out.printf("Failed to start plugin: %s%n", p.getName());
            }
        });
    }

    private WorldPoint getSelectedWorldPoint() {
        if (client.getWidget(ComponentID.WORLD_MAP_MAPVIEW) == null) {
            if (client.getSelectedSceneTile() != null) {
                return client.isInInstancedRegion() ?
                        WorldPoint.fromLocalInstance(client, client.getSelectedSceneTile().getLocalLocation()) :
                        client.getSelectedSceneTile().getWorldLocation();
            }
        } else {
            return Microbot.getWalker().calculateMapPoint(client.isMenuOpen() ? lastMenuOpenedPoint : client.getMouseCanvasPosition());
        }
        return null;
    }

    private Consumer<MenuEntry> worldMapConsumer() {
        return e ->
        {
            if (staticWalkerScript != null) {
                Microbot.getWalker().interruptStaticWalker();
                staticWalkerScript.shutdown();
                staticWalkerScript = null;
            } else {
                WorldPoint destination = getSelectedWorldPoint();
                Microbot.getWalker().hybridWalkTo(destination);
                staticWalkerScript = new StaticWalkerScript();
                staticWalkerScript.run(destination);
            }

        };
    }

    private void addMapMenuEntries(MenuEntryAdded event) {
        List<MenuEntry> entries = new LinkedList<>(Arrays.asList(client.getMenuEntries()));

        List<MenuEntry> leftClickMenus = new ArrayList<>(((int) entries.stream().count()) + 2);

        leftClickMenus.add(Microbot.getClient().createMenuEntry(0)
                .setOption(staticWalkerScript == null ? "Set AutoWalk Target" : "Clear AutoWalk target")
                .setTarget(event.getTarget())
                .setType(MenuAction.RUNELITE)
                .onClick(worldMapConsumer()));
    }
}
