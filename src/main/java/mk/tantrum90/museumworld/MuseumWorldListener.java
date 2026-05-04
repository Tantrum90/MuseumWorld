package mk.tantrum90.museumworld;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Golem;
import org.bukkit.entity.NPC;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

public final class MuseumWorldListener implements Listener {

    private final MuseumWorld plugin;

    public MuseumWorldListener(MuseumWorld plugin) {
        this.plugin = plugin;
    }

    private boolean isLocked(World world) {
        return plugin.isLocked(world);
    }

    private boolean isLocked(Player player) {
        return player != null && isLocked(player.getWorld());
    }

    private boolean isLocked(Entity entity) {
        return entity != null && isLocked(entity.getWorld());
    }

    private boolean canBypass(Player player) {
        if (player == null) {
            return false;
        }

        return player.hasPermission("museumworld.bypass")
                || player.hasPermission("museumworld.admin");
    }

    private MuseumWorld.PlayerDebugContext context(Player player) {
        if (player == null) {
            return null;
        }

        boolean locked = isLocked(player);
        boolean bypass = canBypass(player);

        return plugin.debugContext(
                player.getName(),
                player.getWorld().getName(),
                locked,
                bypass
        );
    }

    private MuseumWorld.PlayerDebugContext context(Entity entity, Player player) {
        if (player == null) {
            return null;
        }

        boolean locked = isLocked(entity);
        boolean bypass = canBypass(player);

        return plugin.debugContext(
                player.getName(),
                entity == null ? "unknown" : entity.getWorld().getName(),
                locked,
                bypass
        );
    }

    private void debugAllowed(String action, Player player, String reason) {
        plugin.debugDecision("ALLOWED", action, context(player), reason);
    }

    private void debugAllowed(String action, Entity entity, Player player, String reason) {
        plugin.debugDecision("ALLOWED", action, context(entity, player), reason);
    }

    private void debugDenied(String action, Player player, String reason) {
        plugin.debugDecision("DENIED", action, context(player), reason);
    }

    private void debugDenied(String action, Entity entity, Player player, String reason) {
        plugin.debugDecision("DENIED", action, context(entity, player), reason);
    }

    private void debugLeftClick(Player player) {
        if (!plugin.debugMode()) {
            return;
        }

        GameMode gameMode = player.getGameMode();

        String reason = switch (gameMode) {
            case ADVENTURE -> "left-click detected; Adventure mode prevents normal block breaking, so BlockBreakEvent may not fire";
            case SPECTATOR -> "left-click detected; Spectator mode prevents normal block breaking";
            case SURVIVAL, CREATIVE -> "left-click detected; BlockBreakEvent should fire if the player continues breaking and no other protection blocks it";
        };

        plugin.debugDecision("INFO", "left-click-block", context(player), "gamemode=" + gameMode.name() + " | " + reason);
    }

    private void notify(Player player, String key, String message) {
        if (player == null) {
            return;
        }

        if (!plugin.notifyPlayer()) {
            return;
        }

        if (plugin.shouldSend(player.getUniqueId(), key)) {
            player.sendMessage(message);
        }
    }

    private boolean isReadonlyEntity(Entity entity) {
        if (entity == null) {
            return false;
        }

        return plugin.readonlyEntities().contains(entity.getType());
    }

    private boolean isVehicleEntity(Entity entity) {
        if (entity == null) {
            return false;
        }

        String typeName = entity.getType().name();

        return typeName.endsWith("_BOAT")
                || typeName.endsWith("_CHEST_BOAT")
                || typeName.endsWith("_RAFT")
                || typeName.endsWith("_CHEST_RAFT")
                || typeName.equals("MINECART")
                || typeName.endsWith("_MINECART");
    }

    private boolean isFriendlyMob(Entity entity) {
        if (entity == null) {
            return false;
        }

        return entity instanceof Animals
                || entity instanceof Golem
                || entity instanceof WaterMob
                || entity instanceof NPC;
    }

    private boolean isReadonlyBlock(Material material) {
        if (material == null || material == Material.AIR) {
            return false;
        }

        return plugin.readonlyBlocks().contains(material);
    }

    private boolean isViewOnlyContainer(Material material) {
        if (material == null || material == Material.AIR) {
            return false;
        }

        if (plugin.viewOnlyContainers().contains(material)) {
            return true;
        }

        String name = material.name();

        /*
         * If SHULKER_BOX is listed in config, treat all colored shulker boxes
         * as view-only too. This keeps the config easier to maintain.
         */
        return name.endsWith("_SHULKER_BOX")
                && plugin.viewOnlyContainers().contains(Material.SHULKER_BOX);
    }

    private boolean isUnprotectedTopInventory(Player player, Inventory topInventory) {
        if (topInventory == null) {
            return true;
        }

        if (!isLocked(player)) {
            return true;
        }

        if (canBypass(player)) {
            return true;
        }

        if (topInventory.getLocation() == null) {
            return !topInventory.getType().name().contains("ENDER_CHEST");
        }

        return !isLocked(topInventory.getLocation().getWorld());
    }

    private Material itemType(PlayerInteractEvent event) {
        if (event == null) {
            return Material.AIR;
        }

        ItemStack item = event.getItem();

        if (item == null) {
            return Material.AIR;
        }

        return item.getType();
    }

    private Material itemType(Player player, EquipmentSlot hand) {
        if (player == null || hand == null) {
            return Material.AIR;
        }

        ItemStack item = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();

        return item.getType();
    }

    private boolean isBucketItem(Material material) {
        if (material == null || material == Material.AIR) {
            return false;
        }

        String name = material.name();
        return name.equals("BUCKET") || name.endsWith("_BUCKET");
    }

    private boolean isFireStarter(Material material) {
        return material == Material.FLINT_AND_STEEL || material == Material.FIRE_CHARGE;
    }

    private boolean isVehicleItem(Material material) {
        if (material == null || material == Material.AIR) {
            return false;
        }

        String name = material.name();
        return name.endsWith("_BOAT")
                || name.endsWith("_CHEST_BOAT")
                || name.endsWith("_RAFT")
                || name.endsWith("_CHEST_RAFT")
                || name.equals("MINECART")
                || name.endsWith("_MINECART");
    }

    private boolean isBedBlock(Material material) {
        return material != null && material.name().endsWith("_BED");
    }

    private boolean isItemFrame(Entity entity) {
        if (entity == null) {
            return false;
        }

        EntityType type = entity.getType();
        return type == EntityType.ITEM_FRAME || type == EntityType.GLOW_ITEM_FRAME;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!isLocked(player)) {
            debugAllowed("block-break", player, "world is not locked");
            return;
        }

        if (canBypass(player)) {
            debugAllowed("block-break", player, "player has bypass/admin permission");
            return;
        }

        event.setCancelled(true);
        debugDenied("block-break", player, "locked world protection");
        notify(player, "block-break", plugin.msgBlocked());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (!isLocked(player)) {
            debugAllowed("block-place", player, "world is not locked");
            return;
        }

        if (canBypass(player)) {
            debugAllowed("block-place", player, "player has bypass/admin permission");
            return;
        }

        event.setCancelled(true);
        debugDenied("block-place", player, "locked world protection");
        notify(player, "block-place", plugin.msgBlocked());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_BLOCK) {
            debugLeftClick(player);
            return;
        }

        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }

        if (!isLocked(player)) {
            debugAllowed("player-interact", player, "world is not locked");
            return;
        }

        if (canBypass(player)) {
            debugAllowed("player-interact", player, "player has bypass/admin permission");
            return;
        }

        Material usedItem = itemType(event);
        Block clickedBlock = event.getClickedBlock();

        if (plugin.blockBucketUse() && isBucketItem(usedItem)) {
            event.setCancelled(true);
            debugDenied("bucket-use", player, "bucket use blocked: " + usedItem.name());
            notify(player, "bucket-use", plugin.msgBlocked());
            return;
        }

        if (plugin.blockFireUse() && isFireStarter(usedItem)) {
            event.setCancelled(true);
            debugDenied("fire-use", player, "fire starter blocked: " + usedItem.name());
            notify(player, "fire-use", plugin.msgBlocked());
            return;
        }

        if (plugin.blockBoneMealUse() && usedItem == Material.BONE_MEAL) {
            event.setCancelled(true);
            debugDenied("bone-meal-use", player, "bone meal use blocked");
            notify(player, "bone-meal-use", plugin.msgBlocked());
            return;
        }

        if (plugin.blockVehiclePlaceBreak() && isVehicleItem(usedItem)) {
            event.setCancelled(true);
            debugDenied("vehicle-place", player, "vehicle placement/use blocked: " + usedItem.name());
            notify(player, "vehicle-place", plugin.msgBlocked());
            return;
        }

        if (plugin.blockTntIgnite()
                && clickedBlock != null
                && clickedBlock.getType() == Material.TNT
                && isFireStarter(usedItem)) {
            event.setCancelled(true);
            debugDenied("tnt-ignite", player, "TNT ignition blocked");
            notify(player, "tnt-ignite", plugin.msgBlocked());
            return;
        }

        if (plugin.blockPlayerBedUse()
                && clickedBlock != null
                && isBedBlock(clickedBlock.getType())) {
            event.setCancelled(true);
            debugDenied("bed-use", player, "bed use blocked");
            notify(player, "bed-use", plugin.msgBlocked());
            return;
        }

        if (action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!plugin.blockReadonlyInteractions()) {
            debugAllowed("block-interact", player, "read-only interactions disabled");
            return;
        }

        if (clickedBlock == null) {
            return;
        }

        Material material = clickedBlock.getType();

        if (!isReadonlyBlock(material)) {
            debugAllowed("block-interact", player, "block is not read-only: " + material.name());
            return;
        }

        if (isViewOnlyContainer(material)) {
            debugAllowed("block-interact", player, "view-only container allowed to open: " + material.name());
            return;
        }

        event.setCancelled(true);
        debugDenied("block-interact", player, "read-only block interaction blocked: " + material.name());
        notify(player, "readonly-block", plugin.msgBlocked());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (!plugin.blockItemDrop()) {
            return;
        }

        if (!isLocked(player)) {
            debugAllowed("item-drop", player, "world is not locked");
            return;
        }

        if (canBypass(player)) {
            debugAllowed("item-drop", player, "player has bypass/admin permission");
            return;
        }

        event.setCancelled(true);
        debugDenied("item-drop", player, "item dropping blocked");
        notify(player, "item-drop", plugin.msgBlocked());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!plugin.blockItemPickup()) {
            return;
        }

        if (!isLocked(player)) {
            debugAllowed("item-pickup", player, "world is not locked");
            return;
        }

        if (canBypass(player)) {
            debugAllowed("item-pickup", player, "player has bypass/admin permission");
            return;
        }

        event.setCancelled(true);
        debugDenied("item-pickup", player, "item pickup blocked");
        notify(player, "item-pickup", plugin.msgBlocked());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();

        if (!plugin.blockBucketUse()) {
            return;
        }

        if (!isLocked(player)) {
            debugAllowed("bucket-empty", player, "world is not locked");
            return;
        }

        if (canBypass(player)) {
            debugAllowed("bucket-empty", player, "player has bypass/admin permission");
            return;
        }

        event.setCancelled(true);
        debugDenied("bucket-empty", player, "bucket empty blocked");
        notify(player, "bucket-empty", plugin.msgBlocked());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();

        if (!plugin.blockBucketUse()) {
            return;
        }

        if (!isLocked(player)) {
            debugAllowed("bucket-fill", player, "world is not locked");
            return;
        }

        if (canBypass(player)) {
            debugAllowed("bucket-fill", player, "player has bypass/admin permission");
            return;
        }

        event.setCancelled(true);
        debugDenied("bucket-fill", player, "bucket fill blocked");
        notify(player, "bucket-fill", plugin.msgBlocked());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();

        if (isUnprotectedTopInventory(player, topInventory)) {
            debugAllowed("inventory-click", player, "top inventory is not protected");
            return;
        }

        int rawSlot = event.getRawSlot();
        int topSize = topInventory.getSize();

        if (rawSlot >= 0 && rawSlot < topSize) {
            event.setCancelled(true);
            debugDenied("inventory-click", player, "click inside protected top inventory");
            notify(player, "inventory-click", plugin.msgBlocked());
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
            debugDenied("inventory-shift-click", player, "shift-click involving protected inventory");
            notify(player, "inventory-shift-click", plugin.msgBlocked());
            return;
        }

        switch (event.getAction()) {
            case MOVE_TO_OTHER_INVENTORY,
                 HOTBAR_SWAP,
                 COLLECT_TO_CURSOR,
                 UNKNOWN -> {
                event.setCancelled(true);
                debugDenied("inventory-move", player, "inventory action can move items: " + event.getAction().name());
                notify(player, "inventory-move", plugin.msgBlocked());
            }
            default -> debugAllowed("inventory-click", player, "player inventory click allowed while container is open");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();

        if (isUnprotectedTopInventory(player, topInventory)) {
            debugAllowed("inventory-drag", player, "top inventory is not protected");
            return;
        }

        int topSize = topInventory.getSize();

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                event.setCancelled(true);
                debugDenied("inventory-drag", player, "drag into protected top inventory");
                notify(player, "inventory-drag", plugin.msgBlocked());
                return;
            }
        }

        debugAllowed("inventory-drag", player, "drag did not include protected top inventory");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        Inventory destination = event.getDestination();

        boolean sourceLocked = source.getLocation() != null && isLocked(source.getLocation().getWorld());
        boolean destinationLocked = destination.getLocation() != null && isLocked(destination.getLocation().getWorld());

        if (sourceLocked || destinationLocked) {
            event.setCancelled(true);

            if (plugin.debugMode()) {
                plugin.getLogger().info("[DEBUG] DENIED inventory-auto-move"
                        + " | sourceLocked=" + sourceLocked
                        + " | destinationLocked=" + destinationLocked
                        + " | reason=inventory move involving locked world");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player player = getResponsiblePlayer(event.getDamager());

        if (player == null) {
            return;
        }

        Entity damaged = event.getEntity();

        if (!isLocked(damaged)) {
            debugAllowed("entity-damage", damaged, player, "entity world is not locked");
            return;
        }

        if (canBypass(player)) {
            debugAllowed("entity-damage", damaged, player, "player has bypass/admin permission");
            return;
        }

        if (plugin.blockEntityDamage()) {
            event.setCancelled(true);
            debugDenied("entity-damage", damaged, player, "all entity damage is blocked in locked world");
            notify(player, "entity-damage", plugin.msgEntityDamage());
            return;
        }

        if (plugin.blockedEntityTypes().contains(damaged.getType())) {
            event.setCancelled(true);
            debugDenied("entity-damage", damaged, player, "entity type is blocked: " + damaged.getType().name());
            notify(player, "entity-damage", plugin.msgEntityDamage());
            return;
        }

        if (plugin.blockReadonlyInteractions() && isReadonlyEntity(damaged)) {
            event.setCancelled(true);
            debugDenied("readonly-entity-damage", damaged, player, "entity is read-only: " + damaged.getType().name());
            notify(player, "readonly-entity-damage", plugin.msgEntityDamage());
            return;
        }

        if (plugin.blockFriendlyDamage() && isFriendlyMob(damaged)) {
            event.setCancelled(true);
            debugDenied("friendly-damage", damaged, player, "friendly mob damage blocked: " + damaged.getType().name());
            notify(player, "friendly-damage", plugin.msgFriendlyDamage());
            return;
        }

        debugAllowed("entity-damage", damaged, player, "entity did not match blocked rules: " + damaged.getType().name());
    }

    private Player getResponsiblePlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }

        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();

        if (!isLocked(player)) {
            debugAllowed("entity-interact", player, "world is not locked");
            return;
        }

        if (canBypass(player)) {
            debugAllowed("entity-interact", player, "player has bypass/admin permission");
            return;
        }

        Entity clicked = event.getRightClicked();
        Material usedItem = itemType(player, event.getHand());

        if (plugin.blockItemFrameRotation() && isItemFrame(clicked)) {
            event.setCancelled(true);
            debugDenied("item-frame-rotation", clicked, player, "item frame interaction/rotation blocked");
            notify(player, "item-frame-rotation", plugin.msgBlocked());
            return;
        }

        if (plugin.blockLeadUse() && usedItem == Material.LEAD) {
            event.setCancelled(true);
            debugDenied("lead-use", clicked, player, "lead use blocked");
            notify(player, "lead-use", plugin.msgBlocked());
            return;
        }

        if (plugin.blockNameTagUse() && usedItem == Material.NAME_TAG) {
            event.setCancelled(true);
            debugDenied("name-tag-use", clicked, player, "name tag use blocked");
            notify(player, "name-tag-use", plugin.msgBlocked());
            return;
        }

        if (!plugin.blockReadonlyInteractions()) {
            debugAllowed("entity-interact", player, "read-only interactions disabled");
            return;
        }

        /*
         * Vehicles can also be listed under readonly-entities so they are protected
         * from damage/breaking/removal. Entering them is controlled separately by
         * block-vehicle-enter.
         *
         * If block-vehicle-enter is false, do not block the right-click that starts
         * vehicle entry.
         */
        if (isVehicleEntity(clicked) && !plugin.blockVehicleEnter()) {
            debugAllowed("vehicle-enter", clicked, player, "vehicle entering allowed by config");
            return;
        }

        if (isReadonlyEntity(clicked)) {
            event.setCancelled(true);
            debugDenied("entity-interact", clicked, player, "entity is read-only: " + clicked.getType().name());
            notify(player, "readonly-entity", plugin.msgBlocked());
            return;
        }

        debugAllowed("entity-interact", clicked, player, "entity is not read-only: " + clicked.getType().name());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();

        if (!plugin.blockArmorStandManipulation()) {
            return;
        }

        if (!isLocked(player)) {
            debugAllowed("armor-stand", player, "world is not locked");
            return;
        }

        if (canBypass(player)) {
            debugAllowed("armor-stand", player, "player has bypass/admin permission");
            return;
        }

        event.setCancelled(true);
        debugDenied("armor-stand", player, "armor stand manipulation blocked");
        notify(player, "armor-stand", plugin.msgBlocked());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player player)) {
            return;
        }

        if (!plugin.blockHangingBreak()) {
            return;
        }

        if (!isLocked(event.getEntity())) {
            debugAllowed("hanging-break", event.getEntity(), player, "entity world is not locked");
            return;
        }

        if (canBypass(player)) {
            debugAllowed("hanging-break", event.getEntity(), player, "player has bypass/admin permission");
            return;
        }

        event.setCancelled(true);
        debugDenied("hanging-break", event.getEntity(), player, "hanging entity removal blocked");
        notify(player, "hanging-break", plugin.msgBlocked());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();

        if (!isLocked(player)) {
            debugAllowed("hanging-place", player, "world is not locked");
            return;
        }

        if (canBypass(player)) {
            debugAllowed("hanging-place", player, "player has bypass/admin permission");
            return;
        }

        event.setCancelled(true);
        debugDenied("hanging-place", player, "hanging entity placement blocked");
        notify(player, "hanging-place", plugin.msgBlocked());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!plugin.blockFireUse() && !plugin.blockTntIgnite()) {
            return;
        }

        if (!isLocked(event.getBlock().getWorld())) {
            return;
        }

        event.setCancelled(true);

        if (plugin.debugMode()) {
            plugin.getLogger().info("[DEBUG] DENIED block-ignite"
                    + " | world=" + event.getBlock().getWorld().getName()
                    + " | cause=" + event.getCause().name()
                    + " | reason=fire/TNT ignition blocked in locked world");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTntPrime(TNTPrimeEvent event) {
        if (!plugin.blockTntIgnite()) {
            return;
        }

        if (!isLocked(event.getBlock().getWorld())) {
            return;
        }

        event.setCancelled(true);

        if (plugin.debugMode()) {
            plugin.getLogger().info("[DEBUG] DENIED tnt-prime"
                    + " | world=" + event.getBlock().getWorld().getName()
                    + " | reason=TNT priming blocked in locked world");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortalCreate(PortalCreateEvent event) {
        if (!plugin.blockPortalCreation()) {
            return;
        }

        if (!isLocked(event.getWorld())) {
            return;
        }

        event.setCancelled(true);

        if (plugin.debugMode()) {
            plugin.getLogger().info("[DEBUG] DENIED portal-create"
                    + " | world=" + event.getWorld().getName()
                    + " | reason=portal creation blocked in locked world");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockGrow(BlockGrowEvent event) {
        if (plugin.blockNaturalGrowth() && isLocked(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (plugin.blockNaturalGrowth() && isLocked(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockFade(BlockFadeEvent event) {
        if (plugin.blockNaturalGrowth() && isLocked(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBurn(BlockBurnEvent event) {
        if (plugin.blockNaturalGrowth() && isLocked(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (plugin.blockNaturalGrowth() && isLocked(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockFertilize(BlockFertilizeEvent event) {
        Player player = event.getPlayer();

        if (!plugin.blockBoneMealUse()) {
            return;
        }

        if (!isLocked(event.getBlock().getWorld())) {
            if (player != null) {
                debugAllowed("bone-meal-use", player, "world is not locked");
            }
            return;
        }

        if (canBypass(player)) {
            debugAllowed("bone-meal-use", player, "player has bypass/admin permission");
            return;
        }

        event.setCancelled(true);

        if (player != null) {
            debugDenied("bone-meal-use", player, "bone meal fertilization blocked");
            notify(player, "bone-meal-use", plugin.msgBlocked());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();

        if (!plugin.blockPlayerBedUse()) {
            return;
        }

        if (!isLocked(player)) {
            debugAllowed("bed-use", player, "world is not locked");
            return;
        }

        if (canBypass(player)) {
            debugAllowed("bed-use", player, "player has bypass/admin permission");
            return;
        }

        event.setCancelled(true);
        debugDenied("bed-use", player, "bed enter blocked");
        notify(player, "bed-use", plugin.msgBlocked());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!plugin.blockProjectileUse()) {
            return;
        }

        ProjectileSource shooter = event.getEntity().getShooter();

        if (!(shooter instanceof Player player)) {
            return;
        }

        if (!isLocked(player)) {
            debugAllowed("projectile-use", player, "world is not locked");
            return;
        }

        if (canBypass(player)) {
            debugAllowed("projectile-use", player, "player has bypass/admin permission");
            return;
        }

        if (isAllowedElytraFireworkBoost(player, event)) {
            debugAllowed("projectile-use", player, "Elytra firework boost allowed");
            return;
        }

        event.setCancelled(true);
        debugDenied("projectile-use", player, "projectile launch blocked: " + event.getEntityType().name());
        notify(player, "projectile-use", plugin.msgBlocked());
    }


    private boolean isAllowedElytraFireworkBoost(Player player, ProjectileLaunchEvent event) {
        if (!plugin.allowElytraFireworkBoost()) {
            return false;
        }

        if (event.getEntityType() != EntityType.FIREWORK_ROCKET) {
            return false;
        }

        ItemStack chestplate = player.getInventory().getChestplate();

        return player.isGliding()
                && chestplate.getType() == Material.ELYTRA;
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!plugin.blockVehicleEnter()) {
            return;
        }

        if (!(event.getEntered() instanceof Player player)) {
            return;
        }

        if (!isLocked(event.getVehicle())) {
            debugAllowed("vehicle-enter", event.getVehicle(), player, "vehicle world is not locked");
            return;
        }

        if (canBypass(player)) {
            debugAllowed("vehicle-enter", event.getVehicle(), player, "player has bypass/admin permission");
            return;
        }

        event.setCancelled(true);
        debugDenied("vehicle-enter", event.getVehicle(), player, "vehicle entering blocked");
        notify(player, "vehicle-enter", plugin.msgBlocked());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!plugin.blockVehiclePlaceBreak()) {
            return;
        }

        if (!(event.getAttacker() instanceof Player player)) {
            return;
        }

        if (!isLocked(event.getVehicle())) {
            debugAllowed("vehicle-break", event.getVehicle(), player, "vehicle world is not locked");
            return;
        }

        if (canBypass(player)) {
            debugAllowed("vehicle-break", event.getVehicle(), player, "player has bypass/admin permission");
            return;
        }

        event.setCancelled(true);
        debugDenied("vehicle-break", event.getVehicle(), player, "vehicle breaking blocked");
        notify(player, "vehicle-break", plugin.msgBlocked());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!isLocked(event.getLocation().getWorld())) {
            return;
        }

        event.blockList().clear();
        event.setCancelled(true);

        if (plugin.debugMode()) {
            plugin.getLogger().info("[DEBUG] DENIED entity-explode"
                    + " | world=" + event.getLocation().getWorld().getName()
                    + " | reason=explosion in locked world");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!isLocked(event.getBlock().getWorld())) {
            return;
        }

        event.blockList().clear();
        event.setCancelled(true);

        if (plugin.debugMode()) {
            plugin.getLogger().info("[DEBUG] DENIED block-explode"
                    + " | world=" + event.getBlock().getWorld().getName()
                    + " | reason=explosion in locked world");
        }
    }
}