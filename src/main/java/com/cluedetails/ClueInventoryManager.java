/*
 * Copyright (c) 2024, Zoinkwiz <https://www.github.com/Zoinkwiz>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *	  list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *	  this list of conditions and the following disclaimer in the documentation
 *	  and/or other materials provided with the distribution.
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

import com.cluedetails.panels.ClueDetailsParentPanel;

import java.util.*;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NpcID;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.chatbox.ChatboxPanelManager;

@Singleton
public class ClueInventoryManager
{
	private final Client client;
	private final ConfigManager configManager;
	private final ClueDetailsPlugin clueDetailsPlugin;
	private final ClueGroundManager clueGroundManager;
	private final ClueBankManager clueBankManager;
	private final ChatboxPanelManager chatboxPanelManager;
	private final Map<Integer, ClueInstance> trackedCluesInInventory = new HashMap<>();
	private final Map<Integer, ClueInstance> previousTrackedCluesInInventory = new HashMap<>();
	private final EventBus eventBus;

	public ClueInventoryManager(Client client, ConfigManager configManager, ClueDetailsPlugin clueDetailsPlugin, ClueGroundManager clueGroundManager,
								ClueBankManager clueBankManager, ChatboxPanelManager chatboxPanelManager, EventBus eventBus)
	{
		this.client = client;
		this.configManager = configManager;
		this.clueDetailsPlugin = clueDetailsPlugin;
		this.clueGroundManager = clueGroundManager;
		this.clueBankManager = clueBankManager;
		this.chatboxPanelManager = chatboxPanelManager;
		this.eventBus = eventBus;
	}

	public void updateInventory(ItemContainer inventoryContainer)
	{
		// Copy current tracked clues to previous
		previousTrackedCluesInInventory.clear();
		previousTrackedCluesInInventory.putAll(trackedCluesInInventory);

		// Clear current tracked clues
		trackedCluesInInventory.clear();

		Item[] inventoryItems = inventoryContainer.getItems();

		for (Item item : inventoryItems)
		{
			if (item == null || !Clues.isTrackedClueOrTornClue(item.getId(), clueDetailsPlugin.isDeveloperMode())) continue;
			int itemId = item.getId();

			ClueInstance clueInstance = null;
			// If we have a clue we've picked up this tick, we've probably dropped and picked up a clue same tick
			for (ClueInstance clueFromFloor : clueGroundManager.getDespawnedClueQueueForInventoryCheck())
			{
				if (clueFromFloor.getItemId() == item.getId())
				{
					clueInstance = new ClueInstance(clueFromFloor.getClueIds(), itemId);
					break;
				}
			}
			if (clueInstance != null)
			{
				trackedCluesInInventory.put(itemId, clueInstance);
				continue;
			}

			// If clue is already in previous, keep the same ClueInstance
			clueInstance = previousTrackedCluesInInventory.get(itemId);
			if (clueInstance != null)
			{
				trackedCluesInInventory.put(itemId, clueInstance);
				continue;
			}

			clueInstance = new ClueInstance(new ArrayList<>(), itemId);
			trackedCluesInInventory.put(itemId, clueInstance);
		}

		clueGroundManager.getDespawnedClueQueueForInventoryCheck().clear();

		// Compare previous and current to find removed clues
		for (Integer itemId : previousTrackedCluesInInventory.keySet())
		{
			if (!trackedCluesInInventory.containsKey(itemId) || trackedCluesInInventory.get(itemId) != previousTrackedCluesInInventory.get(itemId))
			{
				// Clue was removed from inventory (possibly dropped)
				ClueInstance removedClue = previousTrackedCluesInInventory.get(itemId);
				if (removedClue != null)
				{
					clueGroundManager.processPendingGroundCluesFromInventoryChanged(removedClue);
					clueBankManager.addToRemovedClues(removedClue);
				}
			}
		}
	}

	public void updateClueText(String clueText)
	{
		List<Integer> clueIds = new ArrayList<>();

		// Allow for fake items to have info attached to them in dev mode
		if (clueDetailsPlugin.isDeveloperMode())
		{
			for (Integer devModeId : Clues.DEV_MODE_IDS)
			{
				int randomTestId = (int) (Math.random() * 20);
				trackedCluesInInventory.put(devModeId, new ClueInstance(List.of(randomTestId), devModeId));
			}
		}

		ThreeStepCrypticClue threeStepCrypticClue = ThreeStepCrypticClue.forText(clueText);
		if (threeStepCrypticClue != null)
		{
			for (Map.Entry<Clues, Boolean> clueStep : threeStepCrypticClue.getClueSteps())
			{
				clueIds.add(clueStep.getKey().getClueID());
			}
		}
		else
		{
			clueIds.add(Clues.forTextGetId(clueText));
		}

		if (clueIds.get(0) == null) return;

		Set<Integer> itemIDs = trackedCluesInInventory.keySet();
		for (Integer itemID : itemIDs)
		{
			ClueInstance clueInstance = trackedCluesInInventory.get(itemID);
			// Check that at least one part of the clue text matches the clue tier we're looking at
			if (clueInstance == null) continue;
			Clues clueInfo = Clues.forClueId(clueIds.get(0));
			if (clueInfo == null) continue;
			if (!Objects.equals(clueInfo.getItemID(), itemID)) continue;
			clueInstance.setClueIds(clueIds);
			break;
		}
	}

	// Only used for Beginner Map Clues
	public void updateClueText(Integer interfaceId)
	{
		List<Integer> clueIds = new ArrayList<>();

		// Beginner Map Clues all use the same ItemID, but the InterfaceID used to display them is unique
		clueIds.add(Clues.forInterfaceIdGetId(interfaceId));

		// Assume can only be beginner for now
		ClueInstance beginnerClueInInv = trackedCluesInInventory.get(ItemID.CLUE_SCROLL_BEGINNER);
		if (beginnerClueInInv == null) return;
		beginnerClueInInv.setClueIds(clueIds);
	}

	public Set<Integer> getTrackedCluesInInventory()
	{
		return trackedCluesInInventory.keySet();
	}

	public ClueInstance getTrackedClueByClueItemId(Integer clueItemID)
	{
		return trackedCluesInInventory.get(clueItemID);
	}

	public boolean hasTrackedClues()
	{
		return !trackedCluesInInventory.isEmpty();
	}

	public void onMenuEntryAdded(MenuEntryAdded event, CluePreferenceManager cluePreferenceManager, ClueDetailsParentPanel panel)
	{
		if (!client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}

		if (!event.getTarget().contains("Clue scroll")) return;
		if (!isExamineClue(event.getMenuEntry())) return;

		MenuEntry entry = event.getMenuEntry();
		final Widget w = entry.getWidget();
		boolean isInventoryMenu = w != null && WidgetUtil.componentToInterface(w.getId()) == InterfaceID.INVENTORY;
		int itemId = isInventoryMenu ? event.getItemId() : event.getIdentifier();
		boolean isMarked = cluePreferenceManager.getPreference(itemId);

		final int clueId;

		// Mark Option
		if (Clues.isTrackedClueOrTornClue(itemId, clueDetailsPlugin.isDeveloperMode()))
		{
			ClueInstance clueSelected = trackedCluesInInventory.get(itemId);
			if (clueSelected == null || clueSelected.getClueIds().isEmpty()) return;

			// If isn't a master three-step cryptic
			if (clueSelected.getClueIds().size() == 1)
			{
				clueId = clueSelected.getClueIds().get(0);
			}
			else
			{
				// Used in below TRACKED_CLUE_IDS check
				clueId = itemId;
			}
		}
		else
		{
			clueId = itemId;
			// We don't want to have marking on masters I think
			client.getMenu().createMenuEntry(-1)
				.setOption(isMarked ? "Unmark" : "Mark")
				.setTarget(event.getTarget())
				.setIdentifier(itemId)
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					boolean currentValue = cluePreferenceManager.getPreference(e.getIdentifier());
					cluePreferenceManager.savePreference(e.getIdentifier(), !currentValue);
					panel.refresh();
				});
		}
		if (!isInventoryMenu) return;

		// Clue Details Option
		if (Clues.isTrackedClueOrTornClue(clueId, clueDetailsPlugin.isDeveloperMode()))
		{
			ClueInstance clueSelected = trackedCluesInInventory.get(clueId);
			if (clueSelected == null || clueSelected.getClueIds().isEmpty()) return;

			MenuEntry parent = client.getMenu().createMenuEntry(-1)
				.setOption("Clue details")
				.setTarget(entry.getTarget())
				.setType(MenuAction.RUNELITE);

			Menu submenu = parent.createSubMenu();

			for (int id : clueSelected.getClueIds())
			{
				Clues clue = Clues.forClueId(id);
				if (clue == null)
				{
					System.out.println("Failed to find clue " + id);
					return;
				}

				submenu.createMenuEntry(-1)
					.setOption(clue.getDetail(configManager))
					.setType(MenuAction.RUNELITE)
					.onClick(e ->
						SwingUtilities.invokeLater(() ->
							chatboxPanelManager.openTextInput("Enter new clue detail:")
								.value(clue.getDetail(configManager))
								.onDone((newDetail) ->
								{
									configManager.setConfiguration("clue-details-text", String.valueOf(clue.getClueID()), newDetail);
									panel.refresh();
								})
								.build()));
			}
		}
		else
		{
			client.getMenu().createMenuEntry(-1)
				.setOption("Clue details")
				.setTarget(entry.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					Clues clue = Clues.forClueId(clueId);
					if (clue == null)
					{
						System.out.println("Failed to find clue " + clueId);
						return;
					}

					chatboxPanelManager.openTextInput("Enter new clue detail:")
						.value(clue.getDetail(configManager))
						.onDone((newDetail) ->
						{
							configManager.setConfiguration("clue-details-text", String.valueOf(clue.getClueID()), newDetail);
							panel.refresh();
						})
						.build();
				});
		}

		client.getMenu().createMenuEntry(-1)
			.setOption("Shortest path")
			.setTarget(entry.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(e ->
			{
				Clues clue = Clues.forClueId(clueId);
				if (clue == null)
				{
					System.out.println("Failed to find clue " + clueId);
					return;
				}

				Map<String, Object> data = new HashMap<>();
				data.put("target", clue.getLocation());
				eventBus.post(new PluginMessage("shortestpath", "path", data));
			});
	}

	public boolean isExamineClue(MenuEntry entry)
	{
		String[] textOptions = new String[] { "Clue scroll", "Challenge scroll", "Key (" };
		String target = entry.getTarget();
		String option = entry.getOption();
		return Arrays.stream(textOptions).anyMatch(target::contains) && option.equals("Examine");
	}

	public void onGameTick()
	{
		// Reset clue when receiving a new beginner or master clue
		// These clues use a single item ID, so we cannot detect step changes based on the item ID changing
		final Widget headModelWidget = client.getWidget(ComponentID.DIALOG_NPC_HEAD_MODEL);
		final Widget chatDialogClueItemWidget = client.getWidget(ComponentID.DIALOG_SPRITE_SPRITE);
		final Widget npcChatWidget = client.getWidget(ComponentID.DIALOG_NPC_TEXT);

		if (isNewBeginnerClue(chatDialogClueItemWidget)
			|| (isUriBeginnerClue(headModelWidget) && isUriStandardDialogue(npcChatWidget)))
		{
			ClueInstance clue = trackedCluesInInventory.get(ItemID.CLUE_SCROLL_BEGINNER);
			if (clue == null) return;
			clue.setClueIds(List.of());
		}
		else if (isNewMasterClue(chatDialogClueItemWidget)
			|| (isUriMasterClue(headModelWidget) && isUriStandardDialogue(npcChatWidget)))
		{
			ClueInstance clue =  trackedCluesInInventory.get(ItemID.CLUE_SCROLL_MASTER);
			if (clue == null) return;
			clue.setClueIds(List.of());
		}
	}

	private boolean isUriMasterClue(Widget headModel)
	{
		if (headModel == null) return false;
		return headModel.getModelId() == NpcID.URI_8638;
	}

	private boolean isUriBeginnerClue(Widget headModel)
	{
		if (headModel == null) return false;
		return headModel.getModelId() == NpcID.URI_7311;
	}

	private boolean isUriStandardDialogue(Widget npcChat)
	{
		if (npcChat == null) return false;
		// Check if speaking with another player's Uri or with incorrect attire
		return !npcChat.getText().contains("I do not believe we have any business, Comrade.");
	}

	private boolean isNewBeginnerClue(Widget chatDialogClueItem)
	{
		if (chatDialogClueItem == null) return false;
		return chatDialogClueItem.getItemId() == ItemID.CLUE_SCROLL_BEGINNER;
	}

	private boolean isNewMasterClue(Widget chatDialogClueItem)
	{
		if (chatDialogClueItem == null) return false;
		return chatDialogClueItem.getItemId() == ItemID.CLUE_SCROLL_MASTER;
	}
}
