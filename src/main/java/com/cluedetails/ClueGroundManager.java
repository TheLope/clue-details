/*
 * Copyright (c) 2024, Zoinkwiz <https://github.com/Zoinkwiz>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.cluedetails;

import java.util.stream.Collectors;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;

import java.util.*;
import net.runelite.client.config.ConfigManager;

public class ClueGroundManager
{
	private final Client client;

	private final ClueDetailsPlugin clueDetailsPlugin;
	@Getter
	private final ClueGroundSaveDataManager clueGroundSaveDataManager;
	@Getter
	private final Map<WorldPoint, List<ClueInstance>> groundClues = new HashMap<>();
	private final List<PendingGroundClue> pendingGroundClues = new ArrayList<>();
	private final Set<Tile> itemHasSpawnedOnTileThisTick = new HashSet<>();

	@Getter
	private final List<ClueInstance> despawnedClueQueueForInventoryCheck = new ArrayList<>();
	private final int MAX_DESPAWN_TIMER = 6100;
	private Zone lastZone;
	private Zone currentZone;

	public ClueGroundManager(Client client, ConfigManager configManager, ClueDetailsPlugin clueDetailsPlugin)
	{
		this.client = client;
		this.clueDetailsPlugin = clueDetailsPlugin;
		this.clueGroundSaveDataManager = new ClueGroundSaveDataManager(configManager, clueDetailsPlugin.gson);
		clueGroundSaveDataManager.loadStateFromConfig(client);
	}

	public void onItemSpawned(ItemSpawned event)
	{
		// On item spawned, check if is in known tile stack
		// If log in on tile with clues on it, spawned. Won't be dropped, but could be dropped?
		// Main issue is we don't want to create a new groundClue if it was dropped, as we will then also be doing another new one after.
		TileItem item = event.getItem();
		if (!Clues.isTrackedClueOrTornClue(item.getId(), clueDetailsPlugin.isDeveloperMode())) return;
		if (checkIfItemMatchesKnownItem(event.getTile(), item, event.getTile().getWorldLocation())) return;

		// New despawn timer, probably been dropped. Track to see what it was.
		if (item.getDespawnTime() - client.getTickCount() >= MAX_DESPAWN_TIMER || (
			clueDetailsPlugin.isDeveloperMode() && Clues.DEV_MODE_IDS.contains(item.getId()) && item.getDespawnTime() - client.getTickCount() >= 300))
		{
			pendingGroundClues.add(new PendingGroundClue(item, event.getTile().getWorldLocation(), client.getTickCount()));
		}
		else
		{
			// Handle items spawned on tile without aligned times and not dropped
			itemHasSpawnedOnTileThisTick.add(event.getTile());
		}
	}

	public void onItemDespawned(ItemDespawned event)
	{
		TileItem item = event.getItem();
		if (!Clues.isTrackedClueOrTornClue(item.getId(), clueDetailsPlugin.isDeveloperMode())) return;
		WorldPoint location = event.getTile().getWorldLocation();
		List<ClueInstance> cluesAtLocation = groundClues.get(location);

		// Catch despawn in vicinity
		if (cluesAtLocation == null) return;

		// If no logging out/reloading and such happens, despawnTime remains off by 1, so need to account for it
		if (item.getDespawnTime() - client.getTickCount() <= 1)
		{
			Optional<ClueInstance> optionalClue = cluesAtLocation.stream()
				.filter((clue) -> clue.getTileItem() == item)
				.findFirst();
			optionalClue.ifPresent(this::removeClue);
			return;
		}

		if (getTileAtWorldPoint(location) == null)
		{
			return;
		}

		// If despawned on a tile still in the scene, AND hasn't timed out, we might have:
		// 1. Picked up the clue
		// 2. Done nothing, clue is still there just with a new ID
		// We know it's 2 if we've gone from 5 zones distance to 4 zones distance
		Zone clueZone = new Zone(location);
		Zone currentZone = new Zone(client.getLocalPlayer().getWorldLocation());
		if (lastZone != null)
		{
			int distFromLastZone = clueZone.maxDistanceTo(lastZone);
			int distFromCurrentZone = clueZone.maxDistanceTo(currentZone);
			if (distFromLastZone == 4 && distFromCurrentZone == 3)
			{
				return;
			}
		}

		// Not gone over a zone to load, probably picked up
		Optional<ClueInstance> optionalClue = cluesAtLocation.stream()
			.filter((clue) -> clue.getTileItem() == item)
			.findFirst();
		optionalClue.ifPresent(despawnedClueQueueForInventoryCheck::add);

		// Remove the clue with matching tileItem
		cluesAtLocation.removeIf(clue -> clue.getTileItem() == item);

		// Clue despawned, don't know if it will spawn again as a new TileItem, or if it is gonezo
		// If it does respawn, we need it still in groundItems to check
		// If it doesn't respawn, we have nothing which is checking the tile

		if (cluesAtLocation.isEmpty())
		{
			groundClues.remove(location);
		}
	}

	private void addClue(ClueInstance clue)
	{
		groundClues.computeIfAbsent(clue.getLocation(), k -> new ArrayList<>()).add(clue);
	}

	private void removeClue(ClueInstance clue)
	{
		groundClues.get(clue.getLocation()).remove(clue);
	}

	public boolean checkIfItemMatchesKnownItem(Tile tile, TileItem tileItem, WorldPoint tileWp)
	{
		List<ClueInstance> knownItemsOnTile = groundClues.get(tileWp);
		if (knownItemsOnTile == null) return false;

		for (ClueInstance clueInstance : knownItemsOnTile)
		{
			int currentTick = client.getTickCount();
			if (getTrackedItemsAtTile(tile).contains(clueInstance.getTileItem())) continue;

			// For some reason this is always off by 1? IDK, but need to allow for it
			if (Math.abs(tileItem.getDespawnTime() - clueInstance.getDespawnTick(currentTick)) <= 1)
			{
				clueInstance.setTileItem(tileItem);
				return true;
			}
		}
		return false;
	}

	public void onGameTick()
	{
		currentZone = new Zone(client.getLocalPlayer().getWorldLocation());
		processPendingGroundCluesOnGameTick();
		processEmptyTiles();

		for (Tile tile : itemHasSpawnedOnTileThisTick)
		{
			checkClueThroughRelativeDespawnTimers(tile);
		}
		itemHasSpawnedOnTileThisTick.clear();
		removeDespawnedClues();

		lastZone = currentZone;
	}

	private void processEmptyTiles()
	{
		groundClues.entrySet().removeIf(entry -> {
			Tile tile = getTileAtWorldPoint(entry.getKey());
			if (tile == null) return false;

			Zone clueZone = new Zone(tile.getWorldLocation());
			// Item won't have potentially spawned if too far, so don't remove
			int zonesDistance = clueZone.maxDistanceTo(currentZone);
			if (zonesDistance >= 4) return false;
			return tile.getGroundItems() == null || tile.getGroundItems().isEmpty();
		});
	}

	public void processPendingGroundCluesFromInventoryChanged(ClueInstance removedClue)
	{
		Iterator<PendingGroundClue> groundClueIterator = pendingGroundClues.iterator();
		while (groundClueIterator.hasNext())
		{
			PendingGroundClue pendingGroundClue = groundClueIterator.next();
			// This should be enough, as a player shouldn't be able to drop two of the same item in the same tick
			// As you can only have one of each item on you at once
			if (removedClue.getItemId() == pendingGroundClue.getItem().getId())
			{
				// Found a match
				ClueInstance groundClueInstance = new ClueInstance(
					removedClue.getClueIds(),
					pendingGroundClue.getItem().getId(),
					pendingGroundClue.getLocation(),
					pendingGroundClue.getItem(),
					client.getTickCount()
				);

				addClue(groundClueInstance);

				// Remove matched clues from pending lists
				groundClueIterator.remove();
				break;
			}
		}
	}

	public void processPendingGroundCluesOnGameTick()
	{
		// Remove any with TileItem matching a properly tracked tile item
		pendingGroundClues.removeIf(pendingGroundClue ->
		{
			List<ClueInstance> groundCluesOnTile = groundClues.get(pendingGroundClue.getLocation());
			if (groundCluesOnTile == null) return false;
			return groundCluesOnTile
				.stream()
				.anyMatch(clueInstance -> clueInstance.getTileItem() == pendingGroundClue.getItem());
		});

		// If a tick has passed, and we've not associated the pending clue yet, mark it unknown
		pendingGroundClues.removeIf(pendingGroundClue ->
		{
			if (pendingGroundClue.getSpawnTick() < client.getTickCount())
			{
				ClueInstance groundClueInstance = new ClueInstance(
					List.of(),
					pendingGroundClue.getItem().getId(),
					pendingGroundClue.getLocation(),
					pendingGroundClue.getItem(),
					client.getTickCount()
				);

				addClue(groundClueInstance);
				return true;
			}
			return false;
		});
	}

	private void removeDespawnedClues()
	{
		groundClues.entrySet().removeIf(entry -> {
			List<ClueInstance> cluesList = entry.getValue();
			cluesList.removeIf(clueInstance -> clueInstance.getDespawnTick(client.getTickCount()) <= client.getTickCount());
			return cluesList.isEmpty();
		});
	}

	private void checkClueThroughRelativeDespawnTimers(Tile tile)
	{
		WorldPoint tileWp = tile.getWorldLocation();

		List<TileItem> itemsOnTile = getTrackedItemsAtTile(tile);
		if (itemsOnTile.isEmpty())
		{
			return;
		}

		if (!groundClues.containsKey(tileWp))
		{
			groundClues.put(tileWp, new ArrayList<>());
		}

		List<ClueInstance> storedClues = groundClues.get(tileWp);

		List<ClueInstance> updatedStoredClues = generateNewCluesOnTile(tileWp, storedClues, itemsOnTile);

		if (updatedStoredClues.isEmpty())
		{
			groundClues.remove(tileWp);
		}
		else
		{
			// If we didn't find an item for it on the tile, remove it
			updatedStoredClues.removeIf((clue) -> clue.getTileItem() == null);

			// Update the stored clues
			groundClues.put(tileWp, updatedStoredClues);
		}
	}

	private List<ClueInstance> generateNewCluesOnTile(WorldPoint tileWp, List<ClueInstance> storedClues, List<TileItem> cluesOnTile)
	{
		int currentTick = client.getTickCount();

		if (storedClues.size() == 1 && cluesOnTile.size() == 1)
		{
			// We assume it is the same clue. It is possible for it to be swapped with another clue though in
			// another client/mobile, and this will be wrong
			if (storedClues.get(0).getDespawnTick(currentTick) >= cluesOnTile.get(0).getDespawnTime())
			{
				storedClues.get(0).setTileItem(cluesOnTile.get(0));
				return storedClues;
			}
		}

		List<ClueInstance> sortedStoredClues = new ArrayList<>(storedClues);
		sortedStoredClues.sort(Comparator.comparingInt((clue) -> clue.getTicksToDespawnConsideringTileItem(currentTick)));

		// If only 1 of either but not both, less certainty as can't use diffs.
		// Could assume things like last clue expired, probs let's just assume nothing
		if (storedClues.size() <= 1 || cluesOnTile.size() == 1)
		{
			List<ClueInstance> actualCluesOnTile = new ArrayList<>();
			// Set tile's clues to just be unknown for all clues on tile
			for (TileItem groundClue : cluesOnTile)
			{
				ClueInstance clueInstance = new ClueInstance(List.of(),
					groundClue.getId(),
					tileWp,
					groundClue,
					client.getTickCount()
				);
				clueInstance.setTileItem(groundClue);
				actualCluesOnTile.add(clueInstance);
			}
			return actualCluesOnTile;
		}

		// Sort ground clues by despawn time ascending
		List<TileItem> sortedGroundClues = new ArrayList<>(cluesOnTile);
		sortedGroundClues.removeIf((tileItem -> tileItem.getDespawnTime() > sortedStoredClues.get(sortedStoredClues.size() - 1).getDespawnTick(currentTick)));
		sortedGroundClues.sort(Comparator.comparingInt(TileItem::getDespawnTime));

		Map<Integer, List<TileItem>> groundItemsByItemID = sortedGroundClues.stream()
			.collect(Collectors.groupingBy(TileItem::getId, LinkedHashMap::new, Collectors.toList()));
		Map<Integer, List<ClueInstance>> sortedItemsByItemID = sortedStoredClues.stream()
			.collect(Collectors.groupingBy(ClueInstance::getItemId, LinkedHashMap::new, Collectors.toList()));

		for (Integer itemID : groundItemsByItemID.keySet())
		{
			if (sortedItemsByItemID.get(itemID) == null) continue;
			findMatchingClues(sortedItemsByItemID.get(itemID), groundItemsByItemID.get(itemID));
		}
		
		List<ClueInstance> foundClues = new ArrayList<>();

		cluesOnTile.stream()
			.map(tileItem -> sortedStoredClues.stream()
				.filter(clue -> clue.getTileItem() == tileItem)
				.findFirst()
				.orElseGet(() -> {
					ClueInstance clueInstance = new ClueInstance(List.of(), tileItem.getId(), tileWp, tileItem, client.getTickCount());
					clueInstance.setTileItem(tileItem);
					return clueInstance;
				}))
			.forEach(foundClues::add);

		return foundClues;
	}

	private void findMatchingClues(List<ClueInstance> sortedStoredClues, List<TileItem> sortedGroundClues)
	{
		int currentTick = client.getTickCount();

		// Need to loop diffs, and see matches in each.
		// For items with the same ID, no matter what item you click in a stack, you will always pick up the first item dropped in the stack
		// This means we don't need to worry about considering gaps where a clue has been taken from the middle of a stack.
		int minGroundItemFound = 0;
		for (int i = 0; i < sortedStoredClues.size() - 1; i++)
		{
			ClueInstance clueInstance1 = sortedStoredClues.get(i);
			ClueInstance clueInstance2 = sortedStoredClues.get(i + 1);

			TileItem groundClue1 = sortedGroundClues.get(minGroundItemFound);
			TileItem groundClue2 = sortedGroundClues.get(minGroundItemFound + 1);

			int currentStoredClueDiff = clueInstance2.getTimeToDespawnFromDataInTicks() - clueInstance1.getTimeToDespawnFromDataInTicks();
			int currentGroundClueDiff = groundClue2.getDespawnTime() - groundClue1.getDespawnTime();

			// Same diff, probs same thing
			if (currentGroundClueDiff != currentStoredClueDiff) continue;
			// If item will despawn later than the stored clue, it can't be it.
			if (groundClue1.getDespawnTime() > clueInstance1.getDespawnTick(currentTick)) continue;
			if (groundClue2.getDespawnTime() > clueInstance2.getDespawnTick(currentTick)) continue;
			clueInstance1.setTileItem(groundClue1);
			clueInstance2.setTileItem(groundClue2);
			minGroundItemFound++;
		}
	}

	private Tile getTileAtWorldPoint(WorldPoint tileWp)
	{
		WorldView worldView = client.getTopLevelWorldView();
		LocalPoint tileLp = LocalPoint.fromWorld(worldView, tileWp);
		if (tileLp == null)
		{
			return null;
		}
		return worldView.getScene().getTiles()[tileWp.getPlane()][tileLp.getSceneX()][tileLp.getSceneY()];
	}

	private List<TileItem> getTrackedItemsAtTile(Tile tile)
	{
		List<TileItem> items = tile.getGroundItems();
		if (items == null)
		{
			return Collections.emptyList();
		}
		return items.stream()
			.filter(item -> Clues.isTrackedClueOrTornClue(item.getId(), clueDetailsPlugin.isDeveloperMode()))
			.collect(Collectors.toList());
	}

	public void saveStateToConfig()
	{
		clueGroundSaveDataManager.saveStateToConfig(client, groundClues);
	}

	public void loadStateFromConfig()
	{
		groundClues.clear();
		groundClues.putAll(clueGroundSaveDataManager.loadStateFromConfig(client));
	}
}
