package com.salvagingafktimer;

import com.google.inject.Provides;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.DynamicObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WorldEntityDespawned;
import net.runelite.api.events.WorldEntitySpawned;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
        name = "Salvaging AFK Timer",
        description = "AFK timers and status tracking for Sailing shipwreck salvaging",
        tags = {"sailing", "salvaging", "shipwreck", "cargo", "afk", "timer"}
)
public class SalvagingAfkTimerPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(SalvagingAfkTimerPlugin.class);

    private static final int CRYSTAL_READY_ANIMATION_ID = 13177;
    private static final long CRYSTAL_COOLDOWN_MILLIS = 60_000L;
    private static final long GAME_TICK_MILLIS = 600L;
    private static final int SORTING_TICKS_PER_SALVAGE = 3;
    private static final int INVENTORY_SIZE = 28;
    private static final int SCENE_RESCAN_TICKS = 5;
    private static final int CARGO_ACTION_MAX_DELAY_TICKS = 4;
    private static final int WRECK_FORGET_AFTER_TICKS = SCENE_RESCAN_TICKS * 2;
    private static final int MAX_RESPAWN_SAMPLES = 20;
    private static final int MIN_RATE_TICKS = 15;
    private static final int REQUIRED_SALVAGE_CALIBRATION_COUNT = 8;
    private static final long WRECK_SOONISH_MILLIS = 10_000L;
    private static final long WRECK_WARNING_MAX_MILLIS = 30_000L;
    private static final long RESPAWN_ZERO_GRACE_MILLIS = 10_000L;
    private static final String UNKNOWN_RESPAWN_ETA_TEXT =
            "unlucky spawns, unknown ETA";
    private static final int SALVAGE_RATE_WINDOW_SECONDS = 600;
    private static final String STARTUP_MESSAGE =
            "Salvaging AFK Timer active - please use a designated salvaging world " +
                    "for remotely accurate shipwreck timers!";
    private static final String STARTUP_MESSAGE_COLOR = "1e5aa8";
    private static final Set<Integer> BOAT_WORLD_ENTITY_TYPES = Set.of(1, 2, 3);

    private static final Set<Integer> SAILING_SALVAGING_ANIMATION_IDS = Set.of(
            AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_1X3_DROP01,
            AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_2X5_DROP01,
            AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_3X8_DROP01,
            AnimationID.HUMAN_SAILING_SALVAGE01_LARGE01_DROP01,
            AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_1X3_IDLE01,
            AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_2X5_IDLE01,
            AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_3X8_IDLE01,
            AnimationID.HUMAN_SAILING_SALVAGE01_LARGE01_IDLE01
    );

    private static final Set<Integer> SAILING_SORTING_ANIMATION_IDS = Set.of(
            AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_1X3_INTERACT01,
            AnimationID.HUMAN_SAILING_SALVAGE01_LARGE01_INTERACT01
    );

    private static final Set<Integer> CARGO_INVENTORY_IDS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    InventoryID.SAILING_BOAT_1_CARGOHOLD,
                    InventoryID.SAILING_BOAT_2_CARGOHOLD,
                    InventoryID.SAILING_BOAT_3_CARGOHOLD,
                    InventoryID.SAILING_BOAT_4_CARGOHOLD,
                    InventoryID.SAILING_BOAT_5_CARGOHOLD
            ))
    );

    private static final String CREWMATE_SALVAGE_MESSAGE =
            "Managed to hook some salvage! I'll put it in the cargo hold.";
    private static final String CREWMATE_CARGO_FULL_MESSAGE =
            "The cargo hold is full. I can't salvage anything.";
    private static final Set<String> JENKINS_SALVAGE_MESSAGES = Set.of(
            "Wooo.",
            "Woooo wooo.",
            "Woooo wooo wooo.",
            "Woooo wooooo woooo.",
            "Woooo wooo wooooo woooo."
    );
    private static final String EMPTY_CARGO_WIDGET_TEXT =
            "This cargo hold has no items to show here.";

    @Inject
    private Client client;

    @Inject
    private SalvagingAfkTimerConfig config;

    @Inject
    private KeyManager keyManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private SalvagingStatusOverlay statusOverlay;

    @Inject
    private SalvagingMinimalistOverlay minimalistOverlay;

    @Inject
    private SalvagingDimmerOverlay dimmerOverlay;

    private final Map<WreckKey, WreckObservation> wrecks = new HashMap<>();
    private final Map<ShipwreckType, Deque<Long>> respawnSamplesByType = new HashMap<>();
    private final Map<Integer, CargoState> cargoStates = new HashMap<>();
    private final Map<Integer, Boolean> salvageItemCache = new HashMap<>();
    private final Map<Integer, Boolean> stackableItemCache = new HashMap<>();
    private final Map<Integer, String> itemNameCache = new HashMap<>();
    private final Deque<SalvageRateSample> salvageRateSamples = new ArrayDeque<>();

    private BoatSnapshot boatSnapshot = new BoatSnapshot();
    private InventorySnapshot inventorySnapshot = InventorySnapshot.empty();
    private PlayerActivity playerActivity = PlayerActivity.IDLE;
    private PendingCargoAction pendingCargoAction;

    private long tickCounter;
    private long activeSalvagingTicks;
    private boolean salvagingSessionActive;

    private boolean sortingSessionActive;
    private long sortingFinishAtMillis;

    private WreckKey trackedSalvagingWreckKey;
    private static final long FACILITY_UNDIM_MILLIS = 3_000L;
    // Crewmates report salvage at least once a minute while working a hook.
    private static final long CREW_SALVAGING_WINDOW_TICKS = 100L;

    private long salvagingDimmerSuppressedUntilMillis;
    private boolean dimmerSortingArmed = true;
    private volatile boolean dimmerEnabled = true;

    private final HotkeyListener toggleHotkeyListener = new HotkeyListener(() -> config.toggleHotkey())
    {
        @Override
        public void hotkeyPressed()
        {
            dimmerEnabled = !dimmerEnabled;

            String affectedOverlays = config.toggleOnlyHidesDimmer()
                    ? "Distraction dimmer"
                    : "Distraction dimmer and text overlays";
            recordSignal(affectedOverlays + " " +
                    (dimmerEnabled ? "enabled" : "disabled") + " by hotkey");
        }
    };

    private int lastSailingXp = -1;
    private int lastSailingXpDelta;
    private long lastConfirmedSalvagingXpTick = Long.MIN_VALUE / 4;
    private long ignoreSalvageInventoryGainsUntilTick = Long.MIN_VALUE / 4;
    private long pendingJenkinsSignalUntilTick = Long.MIN_VALUE / 4;
    private long lastCrewSalvageTick = Long.MIN_VALUE / 4;

    private long extractorReadyAtMillis;
    private boolean extractorStateInitialized;
    private int previousExtractorObjectId = -1;
    private int previousExtractorAnimationId = -1;

    private boolean movedThisTick;
    private WorldPoint previousPlayerLocation;
    private int currentAnimation = -1;
    private int lastContainerId = -1;
    private int lastContainerSize = -1;
    private String lastMenuOption = "None";
    private String lastMenuTarget = "";
    private int lastMenuIdentifier = -1;
    private String lastSignal = "Waiting for data";
    private long lastSignalAtMillis;
    private int lastObservedWorld = -1;
    private boolean startupMessageShown;

    @Provides
    SalvagingAfkTimerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(SalvagingAfkTimerConfig.class);
    }

    @Override
    protected void startUp()
    {
        dimmerEnabled = true;
        startupMessageShown = false;
        resetTransientState();

        overlayManager.add(dimmerOverlay);
        overlayManager.add(statusOverlay);
        overlayManager.add(minimalistOverlay);
        keyManager.registerKeyListener(toggleHotkeyListener);

        log.info("Salvaging AFK Timer started");
    }

    @Override
    protected void shutDown()
    {
        keyManager.unregisterKeyListener(toggleHotkeyListener);
        overlayManager.remove(minimalistOverlay);
        overlayManager.remove(statusOverlay);
        overlayManager.remove(dimmerOverlay);

        resetTransientState();
        dimmerEnabled = true;
        log.info("Salvaging AFK Timer stopped");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();

        if (state == GameState.LOGGED_IN)
        {
            resetWreckCalibrationIfWorldChanged();
            showStartupMessageIfReady();
            return;
        }

        if (state == GameState.LOGIN_SCREEN ||
                state == GameState.HOPPING ||
                state == GameState.CONNECTION_LOST)
        {
            resetTransientState();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        tickCounter++;
        resetWreckCalibrationIfWorldChanged();
        showStartupMessageIfReady();

        updatePlayerMovementAndAnimation();
        initializeInventoryIfNeeded();
        initializeSailingXpIfNeeded();
        refreshBoatIdentity();
        resolvePendingCargoAction();

        if (tickCounter % SCENE_RESCAN_TICKS == 0 || boatSnapshot.worldViewId == -1)
        {
            rescanRelevantScenes();
        }

        updateExtractorClock();
        removeStaleWrecks();
        inferPlayerActivity();
        updateTrackedSalvagingWreck();
        updateSalvagingSessionClock();
        updateSortingSessionClock();
    }

    @Subscribe
    public void onWorldEntitySpawned(WorldEntitySpawned event)
    {
        WorldEntity entity = event.getWorldEntity();
        int entityType = entity.getConfig().getId();
        if (!BOAT_WORLD_ENTITY_TYPES.contains(entityType))
        {
            return;
        }

        Player player = client.getLocalPlayer();
        if (player != null && player.getWorldView().getId() == entity.getWorldView().getId())
        {
            adoptBoatIdentity(entity);
        }

        debug("Boat world entity spawned: type=" + entityType +
                ", worldView=" + entity.getWorldView().getId());
    }

    @Subscribe
    public void onWorldEntityDespawned(WorldEntityDespawned event)
    {
        WorldEntity entity = event.getWorldEntity();
        if (entity.getWorldView().getId() == boatSnapshot.worldViewId)
        {
            boatSnapshot = new BoatSnapshot();
        }

        debug("World entity despawned: type=" + entity.getConfig().getId() +
                ", worldView=" + entity.getWorldView().getId());
    }

    @Subscribe
    public void onWorldViewUnloaded(WorldViewUnloaded event)
    {
        int worldViewId = event.getWorldView().getId();
        if (worldViewId == boatSnapshot.worldViewId)
        {
            boatSnapshot = new BoatSnapshot();
        }

        removeWrecksByWorldView(worldViewId);
        debug("World view unloaded: " + worldViewId);
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        GameObject object = event.getGameObject();
        classifyBoatObject(object, boatSnapshot);
        observeWreck(object, "spawn");
        logObjectEvent("spawned", object);
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        GameObject object = event.getGameObject();
        clearBoatObjectReference(object);

        ShipwreckType wreckType = ShipwreckType.fromAnyObjectId(object.getId());
        if (wreckType != null)
        {
            WreckKey key = new WreckKey(wreckType, object.getWorldLocation());
            WreckObservation observation = wrecks.get(key);
            if (observation != null && observation.objectId == object.getId())
            {
                observation.present = false;
                observation.lastSeenTick = tickCounter;
            }
        }

        logObjectEvent("despawned", object);
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        if (event.getActor() != client.getLocalPlayer())
        {
            return;
        }

        Player player = client.getLocalPlayer();
        currentAnimation = player == null ? -1 : player.getAnimation();
        debug("Local player animation changed to " + currentAnimation);
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        if (event.getSkill() != Skill.SAILING)
        {
            return;
        }

        int newXp = event.getXp();
        if (lastSailingXp < 0)
        {
            lastSailingXp = newXp;
            return;
        }

        int xpDelta = newXp - lastSailingXp;
        lastSailingXp = newXp;
        if (xpDelta <= 0)
        {
            return;
        }

        lastSailingXpDelta = xpDelta;

        if (tickCounter <= pendingJenkinsSignalUntilTick)
        {
            applyCrewSalvageToCargo("Jenkins overhead plus Sailing XP");
            pendingJenkinsSignalUntilTick = Long.MIN_VALUE / 4;
        }

        if (isActuallySalvaging())
        {
            lastConfirmedSalvagingXpTick = tickCounter;
            recordSignal("Salvaging XP +" + xpDelta + " during animation " + currentAnimation);
        }
        else if (isActuallySortingSalvage())
        {
            recordSignal("Sorting XP +" + xpDelta + " during animation " + currentAnimation);
        }
        else
        {
            debug("Sailing XP +" + xpDelta + " outside recognised salvage animations; animation=" +
                    currentAnimation);
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        String option = clean(event.getMenuOption());
        String target = clean(event.getMenuTarget());
        String optionLower = option.toLowerCase(Locale.ROOT);
        String targetLower = target.toLowerCase(Locale.ROOT);

        lastMenuOption = option;
        lastMenuTarget = target;
        lastMenuIdentifier = event.getId();
        recordSignal("Menu: " + option + " -> " + target + " [id=" + event.getId() + "]");
        updateDimmerArming(optionLower, targetLower, event.getId());

        if (!optionLower.contains("withdraw") && !optionLower.contains("deposit"))
        {
            return;
        }

        if (!isCargoInteraction(targetLower, event.getId()))
        {
            return;
        }

        CargoActionType actionType = optionLower.contains("withdraw")
                ? CargoActionType.WITHDRAW
                : CargoActionType.DEPOSIT;

        CargoState cargoBeforeAction = currentCargoState();
        pendingCargoAction = new PendingCargoAction(
                actionType,
                inventorySnapshot.copy(),
                cargoBeforeAction.getEstimatedUsed(),
                cargoBeforeAction.getConfirmedUsed(),
                cargoBeforeAction.confirmedKnown,
                CARGO_ACTION_MAX_DELAY_TICKS,
                option + " -> " + target
        );

        if (actionType == CargoActionType.WITHDRAW)
        {
            ignoreSalvageInventoryGainsUntilTick = tickCounter + CARGO_ACTION_MAX_DELAY_TICKS + 1L;
        }

        debug("Queued cargo " + actionType + " action with inventory " +
                inventorySnapshot.itemQuantities);
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        int rawContainerId = event.getContainerId();
        int normalizedContainerId = rawContainerId & 0x4FFF;
        lastContainerId = rawContainerId;
        lastContainerSize = event.getItemContainer() == null
                ? -1
                : event.getItemContainer().getItems().length;

        if (rawContainerId == InventoryID.INV)
        {
            InventorySnapshot oldSnapshot = inventorySnapshot;
            InventorySnapshot newSnapshot = createInventorySnapshot(event.getItemContainer());
            inventorySnapshot = newSnapshot;
            handleInventoryDifference(oldSnapshot, newSnapshot);

            if (pendingCargoAction != null)
            {
                pendingCargoAction.inventoryUpdated = true;
                // Wait until the next GameTick before applying the inventory fallback.
                // This gives the authoritative cargo container event priority when both
                // updates are delivered for the same action.
            }
            return;
        }

        if (!CARGO_INVENTORY_IDS.contains(normalizedContainerId))
        {
            return;
        }

        CargoState cargo = currentCargoState();
        cargo.replaceFromAuthoritativeContainer(event.getItemContainer());
        cargo.lastConfirmedAtMillis = System.currentTimeMillis();

        if (pendingCargoAction != null)
        {
            pendingCargoAction.cargoContainerUpdated = true;
        }

        recordSignal("Cargo confirmed at " + cargo.getConfirmedUsed() +
                " slots from container " + rawContainerId);
    }

    @Subscribe
    public void onOverheadTextChanged(OverheadTextChanged event)
    {
        Actor actor = event.getActor();
        Player localPlayer = client.getLocalPlayer();
        if (!(actor instanceof NPC) || localPlayer == null ||
                actor.getWorldView() != localPlayer.getWorldView())
        {
            return;
        }

        String rawText = event.getOverheadText();
        String text = clean(rawText);

        if (CREWMATE_SALVAGE_MESSAGE.equals(rawText) || CREWMATE_SALVAGE_MESSAGE.equals(text))
        {
            applyCrewSalvageToCargo("crewmate salvage overhead");
            return;
        }

        if (CREWMATE_CARGO_FULL_MESSAGE.equals(rawText) || CREWMATE_CARGO_FULL_MESSAGE.equals(text))
        {
            CargoState cargo = currentCargoState();
            int capacity = getCargoCapacity();
            if (capacity >= 0)
            {
                int missing = Math.max(0, capacity - cargo.getEstimatedUsed());
                cargo.addCrewUnknownSlots(missing);
                cargo.crewAddedSlots += missing;
            }
            recordSignal("Crew reported cargo full");
            return;
        }

        if (JENKINS_SALVAGE_MESSAGES.contains(rawText) || JENKINS_SALVAGE_MESSAGES.contains(text))
        {
            // The Sailing plugin treats Jenkins' varying 'Wooo' messages as a pending
            // salvage action and confirms it when Sailing XP changes. Keep a two-tick
            // window so event ordering near a tick boundary cannot lose the deposit.
            pendingJenkinsSignalUntilTick = tickCounter + 2L;
            recordSignal("Jenkins salvage signal detected; awaiting Sailing XP");
        }
    }

    private void applyCrewSalvageToCargo(String source)
    {
        CargoState cargo = currentCargoState();
        int usedBefore = cargo.getEstimatedUsed();
        cargo.addCrewItem(ItemID.SAILING_SMALL_SHIPWRECK_SALVAGE, 1);
        int slotDelta = Math.max(0, cargo.getEstimatedUsed() - usedBefore);
        cargo.crewAddedSlots += slotDelta;
        lastCrewSalvageTick = tickCounter;
        recordSignal("Crew added " + slotDelta + " salvage slot via " + source);
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() != InterfaceID.SAILING_BOAT_CARGOHOLD)
        {
            return;
        }

        Widget itemsParent = client.getWidget(InterfaceID.SailingBoatCargohold.ITEMS);
        Widget firstChild = itemsParent == null ? null : itemsParent.getChild(0);
        if (firstChild != null && Objects.equals(firstChild.getText(), EMPTY_CARGO_WIDGET_TEXT))
        {
            CargoState cargo = currentCargoState();
            cargo.clearAsConfirmedEmpty();
            cargo.lastConfirmedAtMillis = System.currentTimeMillis();
            recordSignal("Cargo widget confirmed an empty hold");
        }
    }

    private void initializeInventoryIfNeeded()
    {
        if (inventorySnapshot.initialized)
        {
            return;
        }

        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory != null)
        {
            inventorySnapshot = createInventorySnapshot(inventory);
            debug("Initial inventory snapshot loaded: salvage=" + inventorySnapshot.salvageCount +
                    ", emptySlots=" + inventorySnapshot.emptySlots);
        }
    }

    private void initializeSailingXpIfNeeded()
    {
        if (lastSailingXp >= 0 || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        lastSailingXp = client.getSkillExperience(Skill.SAILING);
        debug("Initialized Sailing XP baseline at " + lastSailingXp);
    }

    private void updatePlayerMovementAndAnimation()
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            currentAnimation = -1;
            movedThisTick = false;
            previousPlayerLocation = null;
            return;
        }

        currentAnimation = player.getAnimation();
        WorldPoint currentLocation = player.getWorldLocation();
        movedThisTick = previousPlayerLocation != null &&
                !previousPlayerLocation.equals(currentLocation);
        previousPlayerLocation = currentLocation;
    }

    private void inferPlayerActivity()
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            playerActivity = PlayerActivity.IDLE;
            return;
        }

        if (isActuallySortingSalvage())
        {
            playerActivity = PlayerActivity.SORTING_SALVAGE;
            return;
        }

        if (isActuallySalvaging())
        {
            playerActivity = PlayerActivity.SALVAGING;
            return;
        }

        if (currentAnimation == -1 && !movedThisTick && player.getInteracting() == null)
        {
            playerActivity = PlayerActivity.IDLE;
            return;
        }

        playerActivity = PlayerActivity.OTHER;
    }

    private boolean isActuallySalvaging()
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return false;
        }

        return SAILING_SALVAGING_ANIMATION_IDS.contains(player.getAnimation());
    }

    private boolean isActuallySortingSalvage()
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return false;
        }

        return SAILING_SORTING_ANIMATION_IDS.contains(player.getAnimation());
    }

    private void updateTrackedSalvagingWreck()
    {
        if (playerActivity != PlayerActivity.SALVAGING)
        {
            setTrackedSalvagingWreck(null);
            return;
        }

        int sailingLevel = client.getBoostedSkillLevel(Skill.SAILING);
        List<WreckObservation> activeWrecks = new ArrayList<>();
        for (WreckObservation observation : getNearestWreckObservations(2))
        {
            if (observation.active
                    && sailingLevel >= observation.type.getRequiredSailingLevel())
            {
                activeWrecks.add(observation);
            }
        }

        /*
         * With two active wrecks beside the boat, RuneLite does not expose
         * which wreck the hook is actually using. Track a specific wreck only
         * when exactly one usable local wreck is active. With two active, the
         * ETA falls back to the time until neither local wreck is available.
         */
        WreckObservation tracked = activeWrecks.size() == 1
                ? activeWrecks.get(0)
                : null;

        WreckKey nextKey = tracked == null
                ? null
                : new WreckKey(tracked.type, tracked.location);
        setTrackedSalvagingWreck(nextKey);
    }

    private void setTrackedSalvagingWreck(WreckKey key)
    {
        if (Objects.equals(trackedSalvagingWreckKey, key))
        {
            return;
        }

        trackedSalvagingWreckKey = key;
    }

    private void updateSalvagingSessionClock()
    {
        boolean activelySalvaging = playerActivity == PlayerActivity.SALVAGING;

        if (activelySalvaging)
        {
            if (!salvagingSessionActive)
            {
                recordSignal("Resumed 10-minute active salvaging average");
            }

            salvagingSessionActive = true;
            activeSalvagingTicks++;
            pruneSalvageRateSamples();
            return;
        }

        if (salvagingSessionActive)
        {
            salvagingSessionActive = false;
            recordSignal("Paused 10-minute active salvaging average");
        }
    }


    private void updateSortingSessionClock()
    {
        boolean activelySorting = playerActivity == PlayerActivity.SORTING_SALVAGE;

        if (activelySorting)
        {
            if (!sortingSessionActive)
            {
                sortingSessionActive = true;
                sortingFinishAtMillis = System.currentTimeMillis()
                        + (long) inventorySnapshot.salvageCount
                        * SORTING_TICKS_PER_SALVAGE
                        * GAME_TICK_MILLIS;

                recordSignal("Started smooth sorting countdown for "
                        + inventorySnapshot.salvageCount + " salvage");
            }
            return;
        }

        if (sortingSessionActive)
        {
            sortingSessionActive = false;
            sortingFinishAtMillis = 0L;
            recordSignal("Stopped smooth sorting countdown");
        }
    }

    private void handleInventoryDifference(
            InventorySnapshot oldSnapshot,
            InventorySnapshot newSnapshot)
    {
        if (!oldSnapshot.initialized)
        {
            return;
        }

        int salvageDelta = newSnapshot.salvageCount - oldSnapshot.salvageCount;
        if (salvageDelta > 0)
        {
            boolean pendingWithdrawal = pendingCargoAction != null &&
                    pendingCargoAction.actionType == CargoActionType.WITHDRAW;
            boolean withdrawalWindow = tickCounter <= ignoreSalvageInventoryGainsUntilTick;
            boolean confirmedHarvest = isActuallySalvaging() ||
                    tickCounter - lastConfirmedSalvagingXpTick <= 1;

            if (pendingWithdrawal || withdrawalWindow)
            {
                recordSignal("Ignored +" + salvageDelta + " salvage from cargo withdrawal");
            }
            else if (confirmedHarvest)
            {
                recordSalvageAcquisitions(salvageDelta);
                recordSignal("Harvested " + salvageDelta + " salvage");
            }
            else
            {
                recordSignal("Ignored +" + salvageDelta +
                        " salvage because player was not actively salvaging");
            }
        }

        if (salvageDelta != 0)
        {
            debug("Salvage inventory delta=" + salvageDelta +
                    ", old=" + oldSnapshot.salvageCount +
                    ", new=" + newSnapshot.salvageCount +
                    ", animation=" + currentAnimation);
        }
    }

    private void recordSalvageAcquisitions(int amount)
    {
        if (amount <= 0)
        {
            return;
        }

        salvageRateSamples.addLast(new SalvageRateSample(activeSalvagingTicks, amount));
        pruneSalvageRateSamples();
    }

    private void pruneSalvageRateSamples()
    {
        long earliestTick = Math.max(0, activeSalvagingTicks - getRateWindowTicks());
        while (!salvageRateSamples.isEmpty() &&
                salvageRateSamples.peekFirst().activeTick < earliestTick)
        {
            salvageRateSamples.removeFirst();
        }
    }

    private long getRateWindowTicks()
    {
        int windowSeconds = Math.max(15,
                Math.min(SALVAGE_RATE_WINDOW_SECONDS, config.rateWindowSeconds()));
        return Math.max(1L,
                (windowSeconds * 1000L + GAME_TICK_MILLIS - 1L) / GAME_TICK_MILLIS);
    }

    private int getSalvageRateSampleCount()
    {
        long earliestTick = Math.max(0L, activeSalvagingTicks - getRateWindowTicks());
        int amount = 0;
        for (SalvageRateSample sample : salvageRateSamples)
        {
            if (sample.activeTick >= earliestTick)
            {
                amount += sample.amount;
            }
        }
        return amount;
    }

    private int getSalvageCalibrationRemaining()
    {
        return Math.max(
                0,
                REQUIRED_SALVAGE_CALIBRATION_COUNT - getSalvageRateSampleCount()
        );
    }

    private double calculateSalvagePerMinute()
    {
        if (playerActivity != PlayerActivity.SALVAGING || salvageRateSamples.isEmpty())
        {
            return Double.NaN;
        }

        long measuredTicks = Math.min(activeSalvagingTicks, getRateWindowTicks());
        if (measuredTicks < MIN_RATE_TICKS)
        {
            return Double.NaN;
        }

        int amount = 0;
        long earliestTick = Math.max(0, activeSalvagingTicks - measuredTicks);
        for (SalvageRateSample sample : salvageRateSamples)
        {
            if (sample.activeTick >= earliestTick)
            {
                amount += sample.amount;
            }
        }

        return amount * 60_000.0 / (measuredTicks * GAME_TICK_MILLIS);
    }

    private void resolvePendingCargoAction()
    {
        if (pendingCargoAction == null)
        {
            return;
        }

        /*
         * Inventory movement is required before either fallback can run. Cargo
         * container updates are compared with the pre-click confirmed value so an
         * authoritative withdrawal and the inventory fallback cannot both subtract
         * the same items.
         */
        if (pendingCargoAction.inventoryUpdated)
        {
            if (pendingCargoAction.actionType == CargoActionType.DEPOSIT)
            {
                if (pendingCargoAction.cargoContainerUpdated)
                {
                    reconcileDepositAfterContainerUpdate(pendingCargoAction);
                }
                else
                {
                    applyCargoInventoryDelta(
                            pendingCargoAction.before,
                            inventorySnapshot,
                            pendingCargoAction.actionType
                    );
                }
            }
            else if (pendingCargoAction.cargoContainerUpdated)
            {
                reconcileWithdrawalAfterContainerUpdate(pendingCargoAction);
            }
            else
            {
                applyCargoInventoryDelta(
                        pendingCargoAction.before,
                        inventorySnapshot,
                        pendingCargoAction.actionType
                );
            }

            pendingCargoAction = null;
            return;
        }

        /*
         * Do not immediately clear a withdrawal merely because the cargo container
         * updated. Keep the action alive briefly so the inventory gain can arrive
         * and the two signals can be reconciled.
         */
        pendingCargoAction.ticksRemaining--;
        if (pendingCargoAction.ticksRemaining < 0)
        {
            if (pendingCargoAction.cargoContainerUpdated)
            {
                debug("Cargo action resolved by container update; no inventory change was observed");
            }
            else
            {
                debug("Cargo action expired without matching inventory or cargo update");
            }
            pendingCargoAction = null;
        }
    }

    private void reconcileDepositAfterContainerUpdate(PendingCargoAction action)
    {
        int removedSalvage = Math.max(
                0,
                action.before.salvageCount - inventorySnapshot.salvageCount
        );

        if (removedSalvage <= 0)
        {
            debug("Deposit reconciliation found no salvage removed from inventory");
            return;
        }

        CargoState cargo = currentCargoState();
        int targetEstimate = action.estimatedUsedBefore + removedSalvage;
        int currentEstimate = cargo.getEstimatedUsed();
        int missingSlots = Math.max(0, targetEstimate - currentEstimate);

        if (missingSlots > 0)
        {
            cargo.addPersonalUnknownSlots(missingSlots);
            cargo.personalAddedSlots += missingSlots;
        }

        recordSignal(
                "Deposit-all reconciled: inventory -" + removedSalvage +
                        ", estimate +" + missingSlots
        );
        debug(
                "Deposit reconciliation: beforeEstimate=" + action.estimatedUsedBefore +
                        ", salvageRemoved=" + removedSalvage +
                        ", containerEstimate=" + currentEstimate +
                        ", fallbackAdded=" + missingSlots +
                        ", finalEstimate=" + cargo.getEstimatedUsed()
        );
    }

    private void reconcileWithdrawalAfterContainerUpdate(PendingCargoAction action)
    {
        int addedSalvage = Math.max(
                0,
                inventorySnapshot.salvageCount - action.before.salvageCount
        );

        if (addedSalvage <= 0)
        {
            debug("Withdrawal reconciliation found no salvage added to inventory");
            return;
        }

        CargoState cargo = currentCargoState();

        /*
         * Only use the inventory fallback when the confirmed cargo figure was
         * known before the click and remained exactly unchanged after the cargo
         * container event. Any authoritative change means the game has already
         * accounted for the withdrawal.
         */
        if (!action.confirmedKnownBefore || !cargo.confirmedKnown)
        {
            recordSignal("Withdrawal used authoritative cargo update; fallback skipped");
            return;
        }

        int confirmedAfter = cargo.getConfirmedUsed();
        if (confirmedAfter != action.confirmedUsedBefore)
        {
            recordSignal(
                    "Withdrawal confirmed by cargo change " +
                            action.confirmedUsedBefore + " -> " + confirmedAfter
            );
            return;
        }

        applyCargoInventoryDelta(
                action.before,
                inventorySnapshot,
                action.actionType
        );
        debug(
                "Withdrawal fallback applied because confirmed cargo stayed at " +
                        confirmedAfter + "; inventory salvage +" + addedSalvage
        );
    }

    private void applyCargoInventoryDelta(
            InventorySnapshot before,
            InventorySnapshot after,
            CargoActionType actionType)
    {
        CargoState cargo = currentCargoState();
        int usedBefore = cargo.getEstimatedUsed();

        Set<Integer> itemIds = new HashSet<>();
        itemIds.addAll(before.itemQuantities.keySet());
        itemIds.addAll(after.itemQuantities.keySet());

        for (Integer itemId : itemIds)
        {
            int oldQuantity = before.itemQuantities.getOrDefault(itemId, 0);
            int newQuantity = after.itemQuantities.getOrDefault(itemId, 0);

            if (actionType == CargoActionType.DEPOSIT && oldQuantity > newQuantity)
            {
                cargo.addPersonalItem(itemId, oldQuantity - newQuantity);
            }
            else if (actionType == CargoActionType.WITHDRAW && newQuantity > oldQuantity)
            {
                cargo.removeTrackedItem(itemId, newQuantity - oldQuantity);
            }
        }

        int usedAfter = cargo.getEstimatedUsed();
        int slotDelta = usedAfter - usedBefore;
        if (slotDelta > 0)
        {
            cargo.personalAddedSlots += slotDelta;
        }
        else if (slotDelta < 0)
        {
            cargo.withdrawnSlots += -slotDelta;
        }

        cargo.inferredSinceConfirmation = true;
        cargo.lastTrackedChangeAtMillis = System.currentTimeMillis();

        recordSignal("Cargo tracking " + (slotDelta >= 0 ? "+" : "") + slotDelta +
                " slots from " + actionType.name().toLowerCase(Locale.ROOT) +
                " inventory fallback");
    }

    private void refreshBoatIdentity()
    {
        Player player = client.getLocalPlayer();
        if (player == null || player.getWorldView().isTopLevel())
        {
            return;
        }

        WorldEntity boatEntity = getCurrentBoatWorldEntity();
        if (boatEntity != null)
        {
            adoptBoatIdentity(boatEntity);
        }
        else if (boatSnapshot.worldViewId != player.getWorldView().getId())
        {
            BoatSnapshot replacement = new BoatSnapshot();
            replacement.worldViewId = player.getWorldView().getId();
            boatSnapshot = replacement;
        }
    }

    private WorldEntity getCurrentBoatWorldEntity()
    {
        Player player = client.getLocalPlayer();
        WorldView topLevel = client.getTopLevelWorldView();
        if (player == null || topLevel == null || player.getWorldView().isTopLevel())
        {
            return null;
        }

        return topLevel.worldEntities().byIndex(player.getWorldView().getId());
    }

    private void adoptBoatIdentity(WorldEntity entity)
    {
        int worldViewId = entity.getWorldView().getId();
        int entityType = entity.getConfig().getId();
        if (!BOAT_WORLD_ENTITY_TYPES.contains(entityType))
        {
            return;
        }

        if (boatSnapshot.worldViewId != worldViewId)
        {
            boatSnapshot = new BoatSnapshot();
            boatSnapshot.worldViewId = worldViewId;
        }

        boatSnapshot.worldEntityType = entityType;
        ShipSize fromEntity = ShipSize.fromWorldEntityType(entityType);
        if (fromEntity != null)
        {
            boatSnapshot.shipSize = fromEntity;
        }
    }

    private void rescanRelevantScenes()
    {
        Player player = client.getLocalPlayer();
        if (client.getGameState() != GameState.LOGGED_IN || player == null)
        {
            return;
        }

        refreshBoatIdentity();

        for (WreckObservation observation : wrecks.values())
        {
            observation.seenInLastScan = false;
        }

        if (!player.getWorldView().isTopLevel())
        {
            scanBoatWorldView(player.getWorldView());
        }

        WorldView topLevel = client.getTopLevelWorldView();
        if (topLevel != null)
        {
            scanWreckWorldView(topLevel);
        }

        for (WreckObservation observation : wrecks.values())
        {
            if (!observation.seenInLastScan && observation.worldViewId ==
                    (topLevel == null ? Integer.MIN_VALUE : topLevel.getId()))
            {
                observation.present = false;
            }
        }
    }

    private void scanBoatWorldView(WorldView worldView)
    {
        forEachGameObject(worldView, object -> classifyBoatObject(object, boatSnapshot));
    }

    private void scanWreckWorldView(WorldView worldView)
    {
        forEachGameObject(worldView, object -> observeWreck(object, "scan"));
    }

    private void forEachGameObject(WorldView worldView, GameObjectConsumer consumer)
    {
        if (worldView == null)
        {
            return;
        }

        Scene scene = worldView.getScene();
        if (scene == null || scene.getTiles() == null)
        {
            return;
        }

        for (Tile[][] plane : scene.getTiles())
        {
            if (plane == null)
            {
                continue;
            }
            for (Tile[] column : plane)
            {
                if (column == null)
                {
                    continue;
                }
                for (Tile tile : column)
                {
                    if (tile == null)
                    {
                        continue;
                    }
                    for (GameObject object : tile.getGameObjects())
                    {
                        if (object != null)
                        {
                            consumer.accept(object);
                        }
                    }
                }
            }
        }
    }

    private void classifyBoatObject(GameObject object, BoatSnapshot snapshot)
    {
        if (object == null || snapshot.worldViewId == -1 ||
                object.getWorldView().getId() != snapshot.worldViewId)
        {
            return;
        }

        ShipSize size = ShipSize.fromGameObjectId(object.getId());
        if (size != null)
        {
            snapshot.shipSize = size;
            snapshot.hullObject = object;
        }

        CargoHoldTier cargoTier = CargoHoldTier.fromGameObjectId(object.getId());
        if (cargoTier != null)
        {
            snapshot.cargoHoldTier = cargoTier;
            snapshot.cargoHoldObject = object;
        }

        if (object.getId() == ObjectID.SAILING_CRYSTAL_EXTRACTOR_ACTIVATED ||
                object.getId() == ObjectID.SAILING_CRYSTAL_EXTRACTOR_DEACTIVATED)
        {
            snapshot.crystalExtractor = object;
        }

        String name = getObjectName(object.getId()).toLowerCase(Locale.ROOT);
        if (name.contains("salvaging hook"))
        {
            snapshot.salvagingHookObjectIds.add(object.getId());
        }
        if (name.contains("salvaging station"))
        {
            snapshot.salvagingStationObjectId = object.getId();
        }
    }

    private void clearBoatObjectReference(GameObject object)
    {
        if (object == null || object.getWorldView().getId() != boatSnapshot.worldViewId)
        {
            return;
        }

        if (boatSnapshot.hullObject == object)
        {
            boatSnapshot.hullObject = null;
        }
        if (boatSnapshot.cargoHoldObject == object)
        {
            boatSnapshot.cargoHoldObject = null;
        }
        if (boatSnapshot.crystalExtractor == object)
        {
            boatSnapshot.crystalExtractor = null;
        }
        boatSnapshot.salvagingHookObjectIds.remove(object.getId());
    }

    private void observeWreck(GameObject object, String source)
    {
        if (object == null)
        {
            return;
        }

        ShipwreckType type = ShipwreckType.fromAnyObjectId(object.getId());
        if (type == null)
        {
            return;
        }

        boolean active = ShipwreckType.isActiveObjectId(object.getId());
        WorldPoint location = object.getWorldLocation();
        WreckKey key = new WreckKey(type, location);
        WreckObservation observation = wrecks.get(key);
        long now = System.currentTimeMillis();

        if (observation == null)
        {
            observation = new WreckObservation(type, location, object.getWorldView().getId());
            observation.active = active;
            observation.objectId = object.getId();
            observation.present = true;
            observation.seenInLastScan = true;
            observation.lastSeenTick = tickCounter;

            if (active)
            {
                /*
                 * Finding an already-active wreck does not reveal when its
                 * lifespan began. It remains uncalibrated until this exact
                 * wreck is observed inactive and then becomes active.
                 */
                observation.timerStartedAtMillis = 0L;
                observation.observedSpawnThisSession = false;
                observation.timerStartReason = "awaiting observed spawn";
            }
            else
            {
                /*
                 * Discovering an existing stump does not tell us when that site
                 * sank. Only an observed active-to-inactive transition can start
                 * a pool-cycle estimate.
                 */
                observation.inactiveSinceMillis = 0L;
                observation.timerStartReason = "inactive, sink time unknown";
            }

            addWreckObservation(key, observation);
            return;
        }

        if (active && !observation.active)
        {
            if (observation.inactiveSinceMillis > 0)
            {
                recordRespawnSample(type, now - observation.inactiveSinceMillis);
            }
            observation.timerStartedAtMillis = now;
            observation.observedSpawnThisSession = true;
            observation.timerStartReason = "observed spawn";
            observation.inactiveSinceMillis = 0L;
            recordSignal(type.getDisplayName() + " wreck became active at " + formatPoint(location));
        }
        else if (!active && observation.active)
        {
            observation.timerStartedAtMillis = 0L;
            observation.inactiveSinceMillis = now;
            observation.timerStartReason = "inactive";
            recordSignal(type.getDisplayName() + " wreck became inactive at " + formatPoint(location));
        }

        observation.active = active;
        observation.objectId = object.getId();
        observation.present = true;
        observation.seenInLastScan = true;
        observation.lastSeenTick = tickCounter;
    }

    private void recordRespawnSample(ShipwreckType type, long durationMillis)
    {
        if (durationMillis < 1_000L)
        {
            return;
        }

        Deque<Long> samples = respawnSamplesByType.computeIfAbsent(type, ignored -> new ArrayDeque<>());
        samples.addLast(durationMillis);
        while (samples.size() > MAX_RESPAWN_SAMPLES)
        {
            samples.removeFirst();
        }
        debug("Recorded " + type.getDisplayName() + " inactive-to-active sample: " +
                formatDurationMillis(durationMillis));
    }

    private long getLearnedRespawnMillis(ShipwreckType type)
    {
        Deque<Long> samples = respawnSamplesByType.get(type);
        if (samples == null || samples.isEmpty())
        {
            return -1L;
        }

        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    private void removeStaleWrecks()
    {
        removeStaleWreckObservations();
    }

    private void updateExtractorClock()
    {
        GameObject extractor = boatSnapshot.crystalExtractor;
        int objectId = extractor == null ? -1 : extractor.getId();
        int animationId = getObjectAnimationId(extractor);
        boolean inactive = objectId == ObjectID.SAILING_CRYSTAL_EXTRACTOR_DEACTIVATED;
        boolean activated = objectId == ObjectID.SAILING_CRYSTAL_EXTRACTOR_ACTIVATED;
        boolean ready = activated && animationId == CRYSTAL_READY_ANIMATION_ID;

        if (!extractorStateInitialized)
        {
            extractorStateInitialized = true;
            previousExtractorObjectId = objectId;
            previousExtractorAnimationId = animationId;
            if (inactive || ready)
            {
                extractorReadyAtMillis = 0L;
            }
            return;
        }

        boolean activatedTransition =
                previousExtractorObjectId == ObjectID.SAILING_CRYSTAL_EXTRACTOR_DEACTIVATED &&
                        objectId == ObjectID.SAILING_CRYSTAL_EXTRACTOR_ACTIVATED;
        boolean harvestedTransition =
                previousExtractorObjectId == ObjectID.SAILING_CRYSTAL_EXTRACTOR_ACTIVATED &&
                        previousExtractorAnimationId == CRYSTAL_READY_ANIMATION_ID &&
                        activated && animationId != CRYSTAL_READY_ANIMATION_ID;

        if (inactive || objectId == -1)
        {
            extractorReadyAtMillis = 0L;
        }
        else if (ready)
        {
            extractorReadyAtMillis = 0L;
        }
        else if (activatedTransition || harvestedTransition)
        {
            extractorReadyAtMillis = System.currentTimeMillis() + CRYSTAL_COOLDOWN_MILLIS;
            recordSignal("Crystal extractor cooldown started from actual state transition");
        }

        previousExtractorObjectId = objectId;
        previousExtractorAnimationId = animationId;
    }

    private int getObjectAnimationId(GameObject object)
    {
        if (object == null)
        {
            return -1;
        }

        Renderable renderable = object.getRenderable();
        if (!(renderable instanceof DynamicObject))
        {
            return -1;
        }

        DynamicObject dynamicObject = (DynamicObject) renderable;
        return dynamicObject.getAnimation() == null ? -1 : dynamicObject.getAnimation().getId();
    }

    private InventorySnapshot createInventorySnapshot(ItemContainer container)
    {
        if (container == null)
        {
            return InventorySnapshot.empty();
        }

        Map<Integer, Integer> quantities = new HashMap<>();
        int salvageCount = 0;
        int occupiedSlots = 0;

        for (Item item : container.getItems())
        {
            if (item == null || item.getId() <= 0)
            {
                continue;
            }
            occupiedSlots++;
            quantities.merge(item.getId(), item.getQuantity(), Integer::sum);
            if (isSalvageItem(item.getId()))
            {
                salvageCount += item.getQuantity();
            }
        }

        return new InventorySnapshot(
                quantities,
                salvageCount,
                Math.max(0, INVENTORY_SIZE - occupiedSlots),
                true
        );
    }

    private boolean isSalvageItem(int itemId)
    {
        return salvageItemCache.computeIfAbsent(itemId, id ->
                getItemName(id).toLowerCase(Locale.ROOT).endsWith(" salvage"));
    }

    private boolean isStackable(int itemId)
    {
        return stackableItemCache.computeIfAbsent(itemId, id ->
        {
            ItemComposition definition = client.getItemDefinition(id);
            return definition != null && definition.isStackable();
        });
    }

    private String getItemName(int itemId)
    {
        return itemNameCache.computeIfAbsent(itemId, id ->
        {
            ItemComposition definition = client.getItemDefinition(id);
            return definition == null || definition.getName() == null
                    ? ""
                    : clean(definition.getName());
        });
    }

    private String getObjectName(int objectId)
    {
        ObjectComposition definition = client.getObjectDefinition(objectId);
        return definition == null || definition.getName() == null
                ? ""
                : clean(definition.getName());
    }

    private CargoState currentCargoState()
    {
        return cargoStates.computeIfAbsent(getCurrentBoatSlot(), ignored -> new CargoState());
    }

    private int getCurrentBoatSlot()
    {
        try
        {
            return client.getVarbitValue(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED) - 1;
        }
        catch (RuntimeException exception)
        {
            return -1;
        }
    }

    private void updateDimmerArming(String optionLower, String targetLower, int identifier)
    {
        if (optionLower.contains("examine"))
        {
            return;
        }

        // Clicking the salvaging hook immediately restores the salvaging dimmer,
        // even when the three-second facility suppression is still running.
        if (isSalvagingHookInteraction(optionLower, targetLower, identifier))
        {
            salvagingDimmerSuppressedUntilMillis = 0L;
            return;
        }

        if (isSortingStationInteraction(optionLower, targetLower, identifier))
        {
            dimmerSortingArmed = true;
            return;
        }

        // Inventory, interface, NPC, minimap and unrelated object clicks do not
        // affect the dimmer. Only the player's cargo hold or Crystal Extractor
        // briefly suppresses the salvaging dimmer.
        if (playerActivity == PlayerActivity.SALVAGING
                && (isDirectCargoHoldInteraction(targetLower, identifier)
                || isCrystalExtractorInteraction(targetLower, identifier)))
        {
            salvagingDimmerSuppressedUntilMillis =
                    System.currentTimeMillis() + FACILITY_UNDIM_MILLIS;
        }
    }

    private boolean isDirectCargoHoldInteraction(String targetLower, int identifier)
    {
        if (targetLower != null && targetLower.contains("cargo hold"))
        {
            return true;
        }

        GameObject cargoHold = boatSnapshot.cargoHoldObject;
        return cargoHold != null && identifier == cargoHold.getId();
    }

    private boolean isCrystalExtractorInteraction(String targetLower, int identifier)
    {
        if (targetLower != null && targetLower.contains("crystal extractor"))
        {
            return true;
        }

        GameObject extractor = boatSnapshot.crystalExtractor;
        return extractor != null && identifier == extractor.getId();
    }

    private boolean isSalvagingHookInteraction(
            String optionLower,
            String targetLower,
            int identifier)
    {
        return targetLower.contains("salvaging hook")
                || boatSnapshot.salvagingHookObjectIds.contains(identifier)
                || (optionLower.contains("operate") && targetLower.contains("hook"))
                || optionLower.equals("salvage");
    }

    private boolean isSortingStationInteraction(
            String optionLower,
            String targetLower,
            int identifier)
    {
        return targetLower.contains("salvaging station")
                || identifier == boatSnapshot.salvagingStationObjectId
                || (optionLower.contains("sort") && targetLower.contains("salvage"));
    }

    private boolean isCargoInteraction(String targetLower, int identifier)
    {
        if (targetLower != null && targetLower.contains("cargo hold"))
        {
            return true;
        }

        GameObject cargoHold = boatSnapshot.cargoHoldObject;
        if (cargoHold != null && identifier == cargoHold.getId())
        {
            return true;
        }

        return isCargoWidgetVisible();
    }

    private boolean isCargoWidgetVisible()
    {
        Widget widget = client.getWidget(InterfaceID.SailingBoatCargohold.UNIVERSE);
        return widget != null && !widget.isHidden();
    }

    private WorldPoint getTopLevelReferencePoint()
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return null;
        }

        WorldView playerWorldView = player.getWorldView();
        if (playerWorldView.isTopLevel())
        {
            return player.getWorldLocation();
        }

        WorldEntity boat = getCurrentBoatWorldEntity();
        if (boat == null)
        {
            return null;
        }

        // The player location is local to the nested boat world view. Convert it
        // into the top-level world before comparing it with shipwreck coordinates.
        // This is the same coordinate conversion used by the Sailing plugin.
        LocalPoint topLevelLocalPoint = boat.transformToMainWorld(player.getLocalLocation());
        return topLevelLocalPoint == null
                ? null
                : WorldPoint.fromLocal(client, topLevelLocalPoint);
    }

    private List<WreckObservation> getNearestWreckObservations(int limit)
    {
        WorldPoint referencePoint = getTopLevelReferencePoint();
        if (referencePoint == null)
        {
            return Collections.emptyList();
        }

        List<WreckObservation> observations = new ArrayList<>();
        for (WreckObservation observation : wrecks.values())
        {
            if (observation.present && observation.location.getPlane() == referencePoint.getPlane())
            {
                observations.add(observation);
            }
        }

        observations.sort(
                Comparator.comparingInt((WreckObservation observation) ->
                                observation.location.distanceTo(referencePoint))
                        .thenComparingInt(observation -> observation.location.getX())
                        .thenComparingInt(observation -> observation.location.getY())
                        .thenComparingInt(observation -> observation.type.ordinal())
        );

        if (observations.size() <= limit)
        {
            return observations;
        }
        return new ArrayList<>(observations.subList(0, limit));
    }

    private long getWreckRemainingMillis(WreckObservation observation)
    {
        if (!observation.active || observation.timerStartedAtMillis <= 0L)
        {
            return -1L;
        }

        long end = observation.timerStartedAtMillis +
                observation.type.getAverageDurationSeconds() * 1000L;
        return Math.max(0L, end - System.currentTimeMillis());
    }

    private void addWreckObservation(WreckKey key, WreckObservation observation)
    {
        wrecks.put(key, observation);
        recordSignal("Detected " + observation.type.getDisplayName() + " wreck at " +
                formatPoint(observation.location) + " as " +
                (observation.active ? "active" : "inactive"));
    }

    private void removeWrecksByWorldView(int worldViewId)
    {
        wrecks.entrySet().removeIf(entry -> entry.getValue().worldViewId == worldViewId);
    }

    private void removeStaleWreckObservations()
    {
        wrecks.entrySet().removeIf(entry ->
                !entry.getValue().present &&
                        tickCounter - entry.getValue().lastSeenTick > WRECK_FORGET_AFTER_TICKS);
    }

    private void logObjectEvent(String verb, GameObject object)
    {
        if (object == null)
        {
            return;
        }

        boolean relevant = ShipwreckType.fromAnyObjectId(object.getId()) != null ||
                ShipSize.fromGameObjectId(object.getId()) != null ||
                CargoHoldTier.fromGameObjectId(object.getId()) != null ||
                object.getId() == ObjectID.SAILING_CRYSTAL_EXTRACTOR_ACTIVATED ||
                object.getId() == ObjectID.SAILING_CRYSTAL_EXTRACTOR_DEACTIVATED;
        if (!relevant)
        {
            return;
        }

        log.debug("[SalvagingAfkTimer] Object {}: id={}, name='{}', point={}, worldView={}",
                verb,
                object.getId(),
                getObjectName(object.getId()),
                formatPoint(object.getWorldLocation()),
                object.getWorldView().getId());
    }

    private void resetTransientState()
    {
        clearWreckObservations();
        respawnSamplesByType.clear();
        cargoStates.clear();
        salvageRateSamples.clear();
        boatSnapshot = new BoatSnapshot();
        inventorySnapshot = InventorySnapshot.empty();
        playerActivity = PlayerActivity.IDLE;
        pendingCargoAction = null;
        tickCounter = 0L;
        activeSalvagingTicks = 0L;
        salvagingSessionActive = false;
        sortingSessionActive = false;
        sortingFinishAtMillis = 0L;
        trackedSalvagingWreckKey = null;
        salvagingDimmerSuppressedUntilMillis = 0L;
        dimmerSortingArmed = true;
        lastSailingXp = -1;
        lastSailingXpDelta = 0;
        lastConfirmedSalvagingXpTick = Long.MIN_VALUE / 4;
        ignoreSalvageInventoryGainsUntilTick = Long.MIN_VALUE / 4;
        lastCrewSalvageTick = Long.MIN_VALUE / 4;
        pendingJenkinsSignalUntilTick = Long.MIN_VALUE / 4;
        extractorReadyAtMillis = 0L;
        extractorStateInitialized = false;
        previousExtractorObjectId = -1;
        previousExtractorAnimationId = -1;
        previousPlayerLocation = null;
        movedThisTick = false;
        currentAnimation = -1;
        lastContainerId = -1;
        lastContainerSize = -1;
        lastMenuOption = "None";
        lastMenuTarget = "";
        lastMenuIdentifier = -1;
        lastSignal = "Waiting for data";
        lastSignalAtMillis = 0L;
        lastObservedWorld = -1;
    }

    private void showStartupMessageIfReady()
    {
        if (startupMessageShown ||
                client.getGameState() != GameState.LOGGED_IN ||
                client.getLocalPlayer() == null)
        {
            return;
        }

        Widget welcomeScreen = client.getWidget(InterfaceID.WelcomeScreen.MOTW);
        if (welcomeScreen != null && !welcomeScreen.isHidden())
        {
            return;
        }

        client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                "<col=" + STARTUP_MESSAGE_COLOR + ">" + STARTUP_MESSAGE + "</col>",
                null
        );
        startupMessageShown = true;
    }

    private void resetWreckCalibrationIfWorldChanged()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        int currentWorld = client.getWorld();
        if (currentWorld <= 0)
        {
            return;
        }

        if (lastObservedWorld != -1 && currentWorld != lastObservedWorld)
        {
            resetWreckCalibrationState();
            recordSignal("World changed; shipwreck timers returned to calibrating");
        }

        lastObservedWorld = currentWorld;
    }

    private void resetWreckCalibrationState()
    {
        clearWreckObservations();
        respawnSamplesByType.clear();
        trackedSalvagingWreckKey = null;
    }

    private void clearWreckObservations()
    {
        wrecks.clear();
    }

    private void recordSignal(String signal)
    {
        lastSignal = signal;
        lastSignalAtMillis = System.currentTimeMillis();
        debug(signal);
    }

    private void debug(String message)
    {
        log.debug("[SalvagingAfkTimer] {}", message);
    }

    private static String clean(String text)
    {
        return text == null ? "" : Text.removeTags(text).trim();
    }

    private static String formatPoint(WorldPoint point)
    {
        return point == null
                ? "?, ?, ?"
                : point.getX() + ", " + point.getY() + ", " + point.getPlane();
    }

    private static String formatDuration(long totalSeconds)
    {
        long safe = Math.max(0L, totalSeconds);
        long hours = safe / 3600L;
        long minutes = (safe % 3600L) / 60L;
        long seconds = safe % 60L;
        if (hours > 0)
        {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    private static String formatDurationMillis(long totalMillis)
    {
        return formatDuration((Math.max(0L, totalMillis) + 999L) / 1000L);
    }

    private static String getExtractorLoadingDots()
    {
        int dotCount = (int) ((System.currentTimeMillis() / 500L) % 3L) + 1;
        return ".".repeat(dotCount);
    }

    private ShipSize getEffectiveShipSize()
    {
        return config.shipSizeOverride() == ShipSize.AUTO
                ? boatSnapshot.shipSize
                : config.shipSizeOverride();
    }

    private CargoHoldTier getEffectiveCargoHoldTier()
    {
        return config.cargoHoldTierOverride() == CargoHoldTier.AUTO
                ? boatSnapshot.cargoHoldTier
                : config.cargoHoldTierOverride();
    }

    public PlayerActivity getPlayerActivity()
    {
        return playerActivity;
    }

    public int getCurrentAnimation()
    {
        return currentAnimation;
    }

    public boolean isCrystalExtractorDetectedForOverlay()
    {
        return boatSnapshot.crystalExtractor != null;
    }

    public String getCrystalExtractorStatus()
    {
        GameObject extractor = boatSnapshot.crystalExtractor;
        if (extractor == null)
        {
            return "Not detected";
        }

        if (extractor.getId() == ObjectID.SAILING_CRYSTAL_EXTRACTOR_DEACTIVATED)
        {
            return "Inactive";
        }

        if (getObjectAnimationId(extractor) == CRYSTAL_READY_ANIMATION_ID)
        {
            return "Ready";
        }

        if (extractorReadyAtMillis > 0L)
        {
            long remaining = extractorReadyAtMillis - System.currentTimeMillis();
            return remaining > 0L ? formatDurationMillis(remaining) : getExtractorLoadingDots();
        }

        return "Active, timer start unknown";
    }

    public String getShipDescription()
    {
        ShipSize size = getEffectiveShipSize();
        CargoHoldTier tier = getEffectiveCargoHoldTier();
        return (size == null ? "Unknown size" : size.getDisplayName()) + ", " +
                (tier == null ? "Unknown hold" : tier.getDisplayName() + " hold");
    }

    public int getCargoCapacity()
    {
        CargoHoldTier tier = getEffectiveCargoHoldTier();
        return tier == null ? -1 : tier.getCapacity(getEffectiveShipSize());
    }

    public String getCargoCapacityText()
    {
        int capacity = getCargoCapacity();
        return capacity < 0 ? "Unknown" : String.valueOf(capacity);
    }

    public String getConfirmedCargoText()
    {
        CargoState cargo = currentCargoState();
        if (!cargo.confirmedKnown)
        {
            return "Unknown, open cargo hold";
        }

        String source = cargo.inferredSinceConfirmation ? "tracked" : "container";
        String age = cargo.lastConfirmedAtMillis <= 0L
                ? ""
                : ", base " + formatDuration((System.currentTimeMillis() - cargo.lastConfirmedAtMillis) / 1000L) + " old";
        return cargo.getConfirmedUsed() + capacitySuffix() + " (" + source + age + ")";
    }

    public String getEstimatedCargoText()
    {
        CargoState cargo = currentCargoState();
        return cargo.getEstimatedUsed() + capacitySuffix() +
                " [P+" + cargo.personalAddedSlots +
                " C+" + cargo.crewAddedSlots +
                " W-" + cargo.withdrawnSlots + "]";
    }

    public String getEstimatedCargoOverlayText()
    {
        CargoState cargo = currentCargoState();
        if (!cargo.confirmedKnown)
        {
            return "Please open cargo to sync";
        }

        return cargo.getEstimatedUsed() + capacitySuffix();
    }

    public int getNearbyAvailableWreckCount()
    {
        int sailingLevel = client.getBoostedSkillLevel(Skill.SAILING);
        int available = 0;

        for (WreckObservation observation : getNearestWreckObservations(2))
        {
            if (observation.active &&
                    sailingLevel >= observation.type.getRequiredSailingLevel())
            {
                available++;
            }
        }

        return available;
    }

    private WreckObservation getTrackedSalvagingWreck()
    {
        if (trackedSalvagingWreckKey == null)
        {
            return null;
        }

        WreckObservation observation = wrecks.get(trackedSalvagingWreckKey);
        return observation != null && observation.present ? observation : null;
    }

    private long getTrackedSalvagingWreckRemainingMillis()
    {
        WreckObservation observation = getTrackedSalvagingWreck();
        if (observation == null)
        {
            return -1L;
        }

        if (!observation.active)
        {
            return 0L;
        }
        if (!observation.observedSpawnThisSession)
        {
            return -1L;
        }

        return getWreckRemainingMillis(observation);
    }

    private String getZeroWreckRespawnText()
    {
        List<WreckObservation> workingPair = getNearestWreckObservations(2);
        if (workingPair.isEmpty())
        {
            return "Respawn calibrating";
        }

        int sailingLevel = client.getBoostedSkillLevel(Skill.SAILING);
        Set<ShipwreckType> workingPoolTypes = new HashSet<>();
        Set<Integer> workingPlanes = new HashSet<>();
        Set<Integer> workingWorldViewIds = new HashSet<>();
        for (WreckObservation observation : workingPair)
        {
            if (sailingLevel >= observation.type.getRequiredSailingLevel())
            {
                workingPoolTypes.add(observation.type);
                workingPlanes.add(observation.location.getPlane());
                workingWorldViewIds.add(observation.worldViewId);
            }
        }

        if (workingPoolTypes.isEmpty())
        {
            return "Respawn calibrating";
        }

        long now = System.currentTimeMillis();
        long earliestFutureDeadline = Long.MAX_VALUE;
        long latestExpiredDeadline = Long.MIN_VALUE;
        boolean hasKnownPoolCycle = false;

        /*
         * The two nearest sites are the local working pair, but every observed
         * site of the same tier, plane and world view contributes pool-cycle
         * evidence. Recently missing observations remain usable until the stale
         * cleanup window expires, protecting against a missed object event.
         * In a mass world, each witnessed sink or spawn is treated as the
         * beginning of another full-duration pool cycle.
         */
        for (WreckObservation observation : wrecks.values())
        {
            if (!workingPoolTypes.contains(observation.type) ||
                    !workingPlanes.contains(observation.location.getPlane()) ||
                    !workingWorldViewIds.contains(observation.worldViewId) ||
                    sailingLevel < observation.type.getRequiredSailingLevel())
            {
                continue;
            }

            long cycleStartedAtMillis = -1L;
            if (observation.active &&
                    observation.observedSpawnThisSession &&
                    observation.timerStartedAtMillis > 0L)
            {
                cycleStartedAtMillis = observation.timerStartedAtMillis;
            }
            else if (!observation.active && observation.inactiveSinceMillis > 0L)
            {
                /*
                 * A witnessed sink causes another pool site to rise immediately.
                 * Even when that replacement site is outside the visible scene,
                 * the sink time gives us one estimated future turnover.
                 */
                cycleStartedAtMillis = observation.inactiveSinceMillis;
            }

            if (cycleStartedAtMillis <= 0L)
            {
                continue;
            }

            hasKnownPoolCycle = true;
            long deadline = cycleStartedAtMillis +
                    observation.type.getAverageDurationSeconds() * 1000L;

            if (deadline > now)
            {
                earliestFutureDeadline = Math.min(
                        earliestFutureDeadline,
                        deadline
                );
            }
            else
            {
                latestExpiredDeadline = Math.max(
                        latestExpiredDeadline,
                        deadline
                );
            }
        }

        if (earliestFutureDeadline != Long.MAX_VALUE)
        {
            return "Respawn in " +
                    formatDurationMillis(earliestFutureDeadline - now) +
                    " max";
        }

        if (latestExpiredDeadline != Long.MIN_VALUE &&
                now - latestExpiredDeadline < RESPAWN_ZERO_GRACE_MILLIS)
        {
            return "Respawn in 0:00 max";
        }

        return hasKnownPoolCycle
                ? UNKNOWN_RESPAWN_ETA_TEXT
                : "Respawn calibrating";
    }

    public boolean isWreckTimerCalibrated()
    {
        List<WreckObservation> nearest = getNearestWreckObservations(2);
        if (nearest.size() < 2)
        {
            return false;
        }

        for (WreckObservation observation : nearest)
        {
            if (!observation.observedSpawnThisSession)
            {
                return false;
            }
        }

        return true;
    }

    private long getPotentiallyNoWreckRemainingMillis()
    {
        if (!isWreckTimerCalibrated())
        {
            return -1L;
        }

        int sailingLevel = client.getBoostedSkillLevel(Skill.SAILING);
        long longestRemaining = -1L;
        for (WreckObservation observation : getNearestWreckObservations(2))
        {
            if (observation.active &&
                    sailingLevel >= observation.type.getRequiredSailingLevel())
            {
                longestRemaining = Math.max(
                        longestRemaining,
                        getWreckRemainingMillis(observation)
                );
            }
        }

        return longestRemaining < 0L ? 0L : longestRemaining;
    }

    public String getPotentiallyNoWreckCompactText()
    {
        if (getNearbyAvailableWreckCount() == 0)
        {
            return getZeroWreckRespawnText();
        }

        if (!isWreckTimerCalibrated())
        {
            return "calibrating";
        }

        long remaining = getPotentiallyNoWreckRemainingMillis();
        if (remaining <= 0L)
        {
            return getWreckExpiryText(getPotentiallyNoWreckOverdueMillis());
        }

        return formatDurationMillis(remaining);
    }

    private String getWreckExpiryText(long overdueMillis)
    {
        long elapsed = Math.max(0L, overdueMillis);
        if (elapsed < WRECK_SOONISH_MILLIS)
        {
            return "Wreck soonish";
        }
        if (elapsed < WRECK_WARNING_MAX_MILLIS)
        {
            return "Wreck soon";
        }

        return "Wreck timing stale";
    }

    private boolean isWreckExpiryWarningStale(long overdueMillis)
    {
        return overdueMillis >= WRECK_WARNING_MAX_MILLIS;
    }

    private long getTrackedSalvagingWreckOverdueMillis()
    {
        WreckObservation observation = getTrackedSalvagingWreck();
        if (observation == null)
        {
            return 0L;
        }

        long now = System.currentTimeMillis();
        if (!observation.active && observation.inactiveSinceMillis > 0L)
        {
            return Math.max(0L, now - observation.inactiveSinceMillis);
        }

        if (observation.timerStartedAtMillis <= 0L)
        {
            return 0L;
        }

        long expectedEnd = observation.timerStartedAtMillis +
                observation.type.getAverageDurationSeconds() * 1000L;
        return Math.max(0L, now - expectedEnd);
    }

    private long getPotentiallyNoWreckOverdueMillis()
    {
        int sailingLevel = client.getBoostedSkillLevel(Skill.SAILING);
        long latestExpectedEnd = Long.MIN_VALUE;
        for (WreckObservation observation : getNearestWreckObservations(2))
        {
            if (!observation.active ||
                    sailingLevel < observation.type.getRequiredSailingLevel() ||
                    observation.timerStartedAtMillis <= 0L)
            {
                continue;
            }

            long expectedEnd = observation.timerStartedAtMillis +
                    observation.type.getAverageDurationSeconds() * 1000L;
            latestExpectedEnd = Math.max(latestExpectedEnd, expectedEnd);
        }

        if (latestExpectedEnd == Long.MIN_VALUE)
        {
            return 0L;
        }

        return Math.max(0L, System.currentTimeMillis() - latestExpectedEnd);
    }

    private String capacitySuffix()
    {
        int capacity = getCargoCapacity();
        return capacity < 0 ? "" : "/" + capacity;
    }

    public String getInventoryText()
    {
        return inventorySnapshot.salvageCount + " salvage, " +
                inventorySnapshot.emptySlots + " empty";
    }

    public String getSalvageRateText()
    {
        if (playerActivity != PlayerActivity.SALVAGING)
        {
            return "Only while salvaging";
        }

        double rate = calculateSalvagePerMinute();
        return Double.isNaN(rate)
                ? "Learning 10m active average"
                : String.format(Locale.ROOT, "%.1f/min", rate);
    }

    private long getInventoryFullEtaMillis()
    {
        if (playerActivity != PlayerActivity.SALVAGING)
        {
            return -1L;
        }
        if (inventorySnapshot.emptySlots <= 0)
        {
            return 0L;
        }

        double rate = calculateSalvagePerMinute();
        if (Double.isNaN(rate) || rate <= 0.0)
        {
            return -1L;
        }

        return Math.max(
                0L,
                Math.round(inventorySnapshot.emptySlots / rate * 60_000.0)
        );
    }

    public String getSalvagingOverlayEtaText()
    {
        if (playerActivity != PlayerActivity.SALVAGING)
        {
            return "IDLE";
        }

        int calibrationRemaining = getSalvageCalibrationRemaining();
        if (calibrationRemaining > 0)
        {
            return "Calibrating (" + calibrationRemaining + ")";
        }

        long inventoryRemaining = getInventoryFullEtaMillis();
        if (inventoryRemaining < 0L)
        {
            return "Calibrating";
        }

        long selected = inventoryRemaining;
        long trackedWreckRemaining = getTrackedSalvagingWreckRemainingMillis();
        if (trackedWreckRemaining == 0L)
        {
            long overdueMillis = getTrackedSalvagingWreckOverdueMillis();
            String expiryText = getWreckExpiryText(overdueMillis);
            return isWreckExpiryWarningStale(overdueMillis)
                    ? formatDurationMillis(inventoryRemaining)
                    : expiryText;
        }
        else if (trackedWreckRemaining > 0L)
        {
            selected = Math.min(selected, trackedWreckRemaining);
        }
        else if (isWreckTimerCalibrated())
        {
            long wreckRemaining = getPotentiallyNoWreckRemainingMillis();
            if (wreckRemaining == 0L)
            {
                long overdueMillis = getPotentiallyNoWreckOverdueMillis();
                String expiryText = getWreckExpiryText(overdueMillis);
                return isWreckExpiryWarningStale(overdueMillis)
                        ? formatDurationMillis(inventoryRemaining)
                        : expiryText;
            }
            else if (wreckRemaining > 0L)
            {
                selected = Math.min(selected, wreckRemaining);
            }
        }

        return formatDurationMillis(selected);
    }

    public String getInventoryFullEtaText()
    {
        if (playerActivity != PlayerActivity.SALVAGING)
        {
            return "Only while salvaging";
        }
        if (inventorySnapshot.emptySlots <= 0)
        {
            return "Full";
        }

        double rate = calculateSalvagePerMinute();
        if (Double.isNaN(rate))
        {
            return "Learning 10m active rate";
        }
        if (rate <= 0.0)
        {
            return "No gains yet";
        }

        return formatDuration(Math.round(inventorySnapshot.emptySlots / rate * 60.0));
    }

    public String getSortingEtaText()
    {
        if (playerActivity != PlayerActivity.SORTING_SALVAGE)
        {
            return "Only while sorting";
        }

        if (!sortingSessionActive || sortingFinishAtMillis <= 0L)
        {
            return "Starting";
        }

        return formatDurationMillis(
                Math.max(0L, sortingFinishAtMillis - System.currentTimeMillis())
        );
    }

    public boolean isPlayerOnBoat()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return false;
        }

        Player player = client.getLocalPlayer();
        if (player == null || player.getWorldView() == null || player.getWorldView().isTopLevel())
        {
            return false;
        }

        WorldEntity boat = getCurrentBoatWorldEntity();
        return boat != null
                && BOAT_WORLD_ENTITY_TYPES.contains(boat.getConfig().getId())
                && boat.getWorldView().getId() == player.getWorldView().getId();
    }

    public boolean shouldShowTimerOverlay()
    {
        return config.showMovableTextOverlay() && shouldShowTextOverlays();
    }

    public boolean shouldShowMinimalistTimerOverlay()
    {
        return config.showMinimalistTextBox() && shouldShowTextOverlays();
    }

    private boolean shouldShowTextOverlays()
    {
        if (!isPlayerOnBoat())
        {
            return false;
        }

        if (!config.toggleOnlyHidesDimmer() && !dimmerEnabled)
        {
            return false;
        }

        return !config.onlyShowTimerOverlayWhenDimmerActive() || shouldShowDimmer();
    }

    public boolean shouldShowDimmer()
    {
        if (!isPlayerOnBoat())
        {
            return false;
        }

        if (!dimmerEnabled)
        {
            return false;
        }

        if (config.undimWhenExtractorReady() && isCrystalExtractorReadyForOverlay())
        {
            return false;
        }

        if (playerActivity == PlayerActivity.SALVAGING)
        {
            return config.dimWhenSalvaging()
                    && System.currentTimeMillis() >= salvagingDimmerSuppressedUntilMillis;
        }

        if (playerActivity == PlayerActivity.SORTING_SALVAGE)
        {
            if (!config.dimWhenSorting() || !dimmerSortingArmed)
            {
                return false;
            }

            if (config.undimSoonerWhenSorting())
            {
                long remaining = getSortingEtaRemainingMillis();
                long threshold = Math.max(0, config.sortingUndimSeconds()) * 1000L;
                if (remaining >= 0L && remaining <= threshold)
                {
                    return false;
                }
            }

            return true;
        }

        if (playerActivity == PlayerActivity.IDLE)
        {
            if (config.dimWhenCrewSalvaging() && isCrewSalvaging() && !isCargoHoldAtUndimThreshold())
            {
                return true;
            }

            return config.dimWhenNoWrecks() && isWaitingForWreckRespawn();
        }

        return false;
    }

    private boolean isCrewSalvaging()
    {
        return tickCounter - lastCrewSalvageTick <= CREW_SALVAGING_WINDOW_TICKS;
    }

    private boolean isWaitingForWreckRespawn()
    {
        // Only dim once nearby wrecks have been seen, so open water never dims.
        return !getNearestWreckObservations(2).isEmpty() && getNearbyAvailableWreckCount() == 0;
    }

    private boolean isCargoHoldAtUndimThreshold()
    {
        int capacity = getCargoCapacity();
        if (capacity <= 0)
        {
            return false;
        }

        int used = currentCargoState().getEstimatedUsed();
        return used * 100 >= capacity * config.cargoHoldUndimPercent();
    }

    public long getSortingEtaRemainingMillis()
    {
        if (playerActivity != PlayerActivity.SORTING_SALVAGE
                || !sortingSessionActive
                || sortingFinishAtMillis <= 0L)
        {
            return -1L;
        }

        return Math.max(0L, sortingFinishAtMillis - System.currentTimeMillis());
    }

    public boolean isCrystalExtractorReadyForOverlay()
    {
        GameObject extractor = boatSnapshot.crystalExtractor;

        return extractor != null
                && extractor.getId() != ObjectID.SAILING_CRYSTAL_EXTRACTOR_DEACTIVATED
                && getObjectAnimationId(extractor) == CRYSTAL_READY_ANIMATION_ID;
    }

    public String getTimeUntilPotentiallyNoWreckText()
    {
        List<WreckObservation> nearest = getNearestWreckObservations(2);
        if (nearest.isEmpty())
        {
            return "No nearby wrecks detected";
        }

        if (!isWreckTimerCalibrated())
        {
            return "Calibrating";
        }

        int sailingLevel = client.getBoostedSkillLevel(Skill.SAILING);
        long longestRemaining = -1L;
        for (WreckObservation observation : nearest)
        {
            if (observation.active && sailingLevel >= observation.type.getRequiredSailingLevel())
            {
                long remaining = getWreckRemainingMillis(observation);
                longestRemaining = Math.max(longestRemaining, remaining);
            }
        }

        return longestRemaining < 0L ? "Now" : formatDurationMillis(longestRemaining) + " est.";
    }

    public String getTimeUntilWreckAvailableText()
    {
        List<WreckObservation> nearest = getNearestWreckObservations(2);
        if (nearest.isEmpty())
        {
            return "No nearby wrecks detected";
        }

        int sailingLevel = client.getBoostedSkillLevel(Skill.SAILING);
        for (WreckObservation observation : nearest)
        {
            if (observation.active &&
                    sailingLevel >= observation.type.getRequiredSailingLevel())
            {
                return "Now";
            }
        }

        return getZeroWreckRespawnText();
    }

    public List<WreckDisplay> getNearestWrecks(int limit)
    {
        WorldPoint referencePoint = getTopLevelReferencePoint();
        if (referencePoint == null)
        {
            return Collections.emptyList();
        }

        int sailingLevel = client.getBoostedSkillLevel(Skill.SAILING);
        List<WreckDisplay> result = new ArrayList<>();
        for (WreckObservation observation : getNearestWreckObservations(limit))
        {
            int distance = observation.location.distanceTo(referencePoint);
            String title = observation.type.getDisplayName() + " @ " +
                    formatPoint(observation.location) + " [d=" + distance + "]";
            String status;

            if (!observation.active)
            {
                status = "Inactive/stump [id=" + observation.objectId + "]";
            }
            else if (sailingLevel < observation.type.getRequiredSailingLevel())
            {
                status = "Active, requires level " + observation.type.getRequiredSailingLevel() +
                        " [id=" + observation.objectId + "]";
            }
            else if (!observation.observedSpawnThisSession)
            {
                status = "Active, timer calibrating [id=" + observation.objectId + "]";
            }
            else
            {
                long remaining = getWreckRemainingMillis(observation);
                status = formatDurationMillis(Math.max(0L, remaining)) + ", " +
                        observation.timerStartReason + " [id=" + observation.objectId + "]";
            }

            result.add(new WreckDisplay(title, status));
        }
        return result;
    }

    public String getLastMenuDebug()
    {
        return lastMenuOption + (lastMenuTarget.isEmpty() ? "" : " -> " + lastMenuTarget) +
                " [" + lastMenuIdentifier + "]";
    }

    public String getLastContainerDebug()
    {
        return lastContainerId < 0
                ? "None"
                : lastContainerId + " (normalized " + (lastContainerId & 0x4FFF) +
                  ", size " + lastContainerSize + ")";
    }

    public String getLearnedSalvageAnimationsText()
    {
        return joinInts(SAILING_SALVAGING_ANIMATION_IDS);
    }

    public String getLearnedSortingAnimationsText()
    {
        return joinInts(SAILING_SORTING_ANIMATION_IDS);
    }

    public String getBoatObjectIdsDebug()
    {
        return "slot=" + getCurrentBoatSlot() +
                ", entityType=" + boatSnapshot.worldEntityType +
                ", hull=" + (boatSnapshot.hullObject == null ? -1 : boatSnapshot.hullObject.getId()) +
                ", hold=" + (boatSnapshot.cargoHoldObject == null ? -1 : boatSnapshot.cargoHoldObject.getId()) +
                ", station=" + boatSnapshot.salvagingStationObjectId +
                ", hooks=" + joinInts(boatSnapshot.salvagingHookObjectIds) +
                ", extractor=" + (boatSnapshot.crystalExtractor == null ? -1 : boatSnapshot.crystalExtractor.getId());
    }

    public String getWorldViewDebug()
    {
        Player player = client.getLocalPlayer();
        WorldView top = client.getTopLevelWorldView();
        return "player=" + (player == null ? "?" : player.getWorldView().getId()) +
                ", boat=" + boatSnapshot.worldViewId +
                ", top=" + (top == null ? "?" : top.getId()) +
                ", ref=" + formatPoint(getTopLevelReferencePoint());
    }

    public String getLastSignalDebug()
    {
        if (lastSignalAtMillis <= 0L)
        {
            return lastSignal;
        }
        long age = Math.max(0L, (System.currentTimeMillis() - lastSignalAtMillis) / 1000L);
        return lastSignal + " (" + age + "s)";
    }

    private static String joinInts(Set<Integer> values)
    {
        if (values.isEmpty())
        {
            return "None";
        }
        StringJoiner joiner = new StringJoiner(",");
        for (Integer value : values)
        {
            joiner.add(String.valueOf(value));
        }
        return joiner.toString();
    }

    public static final class WreckDisplay
    {
        private final String title;
        private final String status;

        private WreckDisplay(String title, String status)
        {
            this.title = title;
            this.status = status;
        }

        public String getTitle()
        {
            return title;
        }

        public String getStatus()
        {
            return status;
        }
    }

    private final class CargoState
    {
        private final Map<Integer, Integer> confirmedItems = new HashMap<>();
        private final Map<Integer, Integer> personalUnconfirmedItems = new HashMap<>();
        private final Map<Integer, Integer> crewTrackedItems = new HashMap<>();
        private int unknownConfirmedSlots;
        private int unknownPersonalSlots;
        private int unknownCrewSlots;
        private boolean confirmedKnown;
        private boolean inferredSinceConfirmation;
        private int personalAddedSlots;
        private int crewAddedSlots;
        private int withdrawnSlots;
        private long lastConfirmedAtMillis;
        private long lastTrackedChangeAtMillis;

        private void replaceFromAuthoritativeContainer(ItemContainer container)
        {
            confirmedItems.clear();
            personalUnconfirmedItems.clear();
            crewTrackedItems.clear();
            unknownConfirmedSlots = 0;
            unknownPersonalSlots = 0;
            unknownCrewSlots = 0;
            personalAddedSlots = 0;
            crewAddedSlots = 0;
            withdrawnSlots = 0;
            confirmedKnown = true;
            inferredSinceConfirmation = false;
            lastTrackedChangeAtMillis = System.currentTimeMillis();

            if (container == null)
            {
                return;
            }

            for (Item item : container.getItems())
            {
                if (item == null || item.getId() <= 0)
                {
                    continue;
                }
                confirmedItems.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }

        private void clearAsConfirmedEmpty()
        {
            confirmedItems.clear();
            personalUnconfirmedItems.clear();
            crewTrackedItems.clear();
            unknownConfirmedSlots = 0;
            unknownPersonalSlots = 0;
            unknownCrewSlots = 0;
            confirmedKnown = true;
            inferredSinceConfirmation = false;
            personalAddedSlots = 0;
            crewAddedSlots = 0;
            withdrawnSlots = 0;
            lastTrackedChangeAtMillis = System.currentTimeMillis();
        }

        private void addPersonalItem(int itemId, int quantity)
        {
            if (quantity <= 0)
            {
                return;
            }

            personalUnconfirmedItems.merge(itemId, quantity, Integer::sum);
            inferredSinceConfirmation = true;
            lastTrackedChangeAtMillis = System.currentTimeMillis();
        }

        private void addCrewItem(int itemId, int quantity)
        {
            if (quantity <= 0)
            {
                return;
            }

            crewTrackedItems.merge(itemId, quantity, Integer::sum);
            inferredSinceConfirmation = true;
            lastTrackedChangeAtMillis = System.currentTimeMillis();
        }

        private void removeTrackedItem(int itemId, int quantity)
        {
            if (quantity <= 0)
            {
                return;
            }

            int remainder = removeFromMap(personalUnconfirmedItems, itemId, quantity);
            remainder = removeFromMap(crewTrackedItems, itemId, remainder);
            remainder = removeFromMap(confirmedItems, itemId, remainder);

            if (!isStackable(itemId) && remainder > 0)
            {
                int removed = Math.min(unknownPersonalSlots, remainder);
                unknownPersonalSlots -= removed;
                remainder -= removed;

                removed = Math.min(unknownCrewSlots, remainder);
                unknownCrewSlots -= removed;
                remainder -= removed;

                removed = Math.min(unknownConfirmedSlots, remainder);
                unknownConfirmedSlots -= removed;
            }

            inferredSinceConfirmation = true;
            lastTrackedChangeAtMillis = System.currentTimeMillis();
        }

        private int removeFromMap(Map<Integer, Integer> items, int itemId, int quantity)
        {
            if (quantity <= 0)
            {
                return 0;
            }

            int oldQuantity = items.getOrDefault(itemId, 0);
            int removed = Math.min(oldQuantity, quantity);
            int newQuantity = oldQuantity - removed;
            if (newQuantity <= 0)
            {
                items.remove(itemId);
            }
            else
            {
                items.put(itemId, newQuantity);
            }
            return quantity - removed;
        }

        private void addPersonalUnknownSlots(int slots)
        {
            if (slots <= 0)
            {
                return;
            }

            unknownPersonalSlots += slots;
            inferredSinceConfirmation = true;
            lastTrackedChangeAtMillis = System.currentTimeMillis();
        }

        private void addCrewUnknownSlots(int slots)
        {
            if (slots <= 0)
            {
                return;
            }

            unknownCrewSlots += slots;
            inferredSinceConfirmation = true;
            lastTrackedChangeAtMillis = System.currentTimeMillis();
        }

        private int getConfirmedUsed()
        {
            int used = calculateCombinedUsed(
                    unknownConfirmedSlots + unknownCrewSlots,
                    confirmedItems,
                    crewTrackedItems
            );
            return capUsed(used);
        }

        private int getEstimatedUsed()
        {
            int used = calculateCombinedUsed(
                    unknownConfirmedSlots + unknownCrewSlots + unknownPersonalSlots,
                    confirmedItems,
                    crewTrackedItems,
                    personalUnconfirmedItems
            );
            return capUsed(used);
        }

        @SafeVarargs
        private final int calculateCombinedUsed(int unknownSlots, Map<Integer, Integer>... sources)
        {
            Map<Integer, Integer> combined = new HashMap<>();
            for (Map<Integer, Integer> source : sources)
            {
                for (Map.Entry<Integer, Integer> entry : source.entrySet())
                {
                    combined.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            }

            int used = Math.max(0, unknownSlots);
            for (Map.Entry<Integer, Integer> entry : combined.entrySet())
            {
                used += isStackable(entry.getKey()) ? 1 : Math.max(0, entry.getValue());
            }
            return used;
        }

        private int capUsed(int used)
        {
            int capacity = getCargoCapacity();
            return capacity < 0 ? Math.max(0, used) : Math.min(capacity, Math.max(0, used));
        }
    }

    private static final class BoatSnapshot
    {
        private int worldViewId = -1;
        private int worldEntityType = -1;
        private ShipSize shipSize;
        private CargoHoldTier cargoHoldTier;
        private GameObject hullObject;
        private GameObject cargoHoldObject;
        private GameObject crystalExtractor;
        private int salvagingStationObjectId = -1;
        private final Set<Integer> salvagingHookObjectIds = new HashSet<>();
    }

    private static final class InventorySnapshot
    {
        private final Map<Integer, Integer> itemQuantities;
        private final int salvageCount;
        private final int emptySlots;
        private final boolean initialized;

        private InventorySnapshot(
                Map<Integer, Integer> itemQuantities,
                int salvageCount,
                int emptySlots,
                boolean initialized)
        {
            this.itemQuantities = itemQuantities;
            this.salvageCount = salvageCount;
            this.emptySlots = emptySlots;
            this.initialized = initialized;
        }

        private static InventorySnapshot empty()
        {
            return new InventorySnapshot(new HashMap<>(), 0, INVENTORY_SIZE, false);
        }

        private InventorySnapshot copy()
        {
            return new InventorySnapshot(
                    new HashMap<>(itemQuantities), salvageCount, emptySlots, initialized);
        }
    }

    private static final class PendingCargoAction
    {
        private final CargoActionType actionType;
        private final InventorySnapshot before;
        private final int estimatedUsedBefore;
        private final int confirmedUsedBefore;
        private final boolean confirmedKnownBefore;
        private int ticksRemaining;
        private final String description;
        private boolean inventoryUpdated;
        private boolean cargoContainerUpdated;

        private PendingCargoAction(
                CargoActionType actionType,
                InventorySnapshot before,
                int estimatedUsedBefore,
                int confirmedUsedBefore,
                boolean confirmedKnownBefore,
                int ticksRemaining,
                String description)
        {
            this.actionType = actionType;
            this.before = before;
            this.estimatedUsedBefore = estimatedUsedBefore;
            this.confirmedUsedBefore = confirmedUsedBefore;
            this.confirmedKnownBefore = confirmedKnownBefore;
            this.ticksRemaining = ticksRemaining;
            this.description = description;
        }
    }

    private enum CargoActionType
    {
        DEPOSIT,
        WITHDRAW
    }

    private static final class SalvageRateSample
    {
        private final long activeTick;
        private final int amount;

        private SalvageRateSample(long activeTick, int amount)
        {
            this.activeTick = activeTick;
            this.amount = amount;
        }
    }

    private static final class WreckKey
    {
        private final ShipwreckType type;
        private final WorldPoint location;

        private WreckKey(ShipwreckType type, WorldPoint location)
        {
            this.type = type;
            this.location = location;
        }

        @Override
        public boolean equals(Object object)
        {
            if (this == object)
            {
                return true;
            }
            if (!(object instanceof WreckKey))
            {
                return false;
            }
            WreckKey other = (WreckKey) object;
            return type == other.type && Objects.equals(location, other.location);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(type, location);
        }
    }

    private static final class WreckObservation
    {
        private final ShipwreckType type;
        private final WorldPoint location;
        private final int worldViewId;
        private boolean active;
        private boolean present;
        private boolean seenInLastScan;
        private int objectId;
        private long timerStartedAtMillis;
        private boolean observedSpawnThisSession;
        private long inactiveSinceMillis;
        private long lastSeenTick;
        private String timerStartReason = "not started";

        private WreckObservation(ShipwreckType type, WorldPoint location, int worldViewId)
        {
            this.type = type;
            this.location = location;
            this.worldViewId = worldViewId;
        }
    }

    @FunctionalInterface
    private interface GameObjectConsumer
    {
        void accept(GameObject object);
    }
}