/*
 * Copyright (c) 2021, Adam <Adam@sigterm.info>
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

import com.google.common.base.Strings;
import com.google.common.util.concurrent.Runnables;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.grounditems.GroundItemsConfig;
import net.runelite.client.plugins.inventorytags.InventoryTagsConfig;

@Slf4j
public class ClueDetailsSharingManager
{
	private final ClueDetailsPlugin plugin;
	private final ClueDetailsConfig config;
	private final ChatMessageManager chatMessageManager;
	private final ChatboxPanelManager chatboxPanelManager;
	private final Gson gson;

	private final ConfigManager configManager;

	@Inject
	private ClueDetailsSharingManager(ClueDetailsPlugin plugin, ClueDetailsConfig config, ChatMessageManager chatMessageManager, ChatboxPanelManager chatboxPanelManager,
										Gson gson, ConfigManager configManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.chatMessageManager = chatMessageManager;
		this.chatboxPanelManager = chatboxPanelManager;
		this.gson = gson;
		this.configManager = configManager;
	}

	public void resetClueDetails()
	{
		List<Clues> filteredClues = Clues.CLUES.stream()
			.filter(config.filterListByTier())
			.filter(config.filterListByRegion())
			.collect(Collectors.toList());

		for (Clues clue : filteredClues)
		{
			int id = clue.getClueID();
			configManager.unsetConfiguration("clue-details-text", String.valueOf(id));
		}
	}

	public void exportClueDetails()
	{
		List<ClueIdToDetails> clueIdToDetailsList = new ArrayList<>();

		List<Clues> filteredClues = Clues.CLUES.stream()
			.filter(config.filterListByTier())
			.filter(config.filterListByRegion())
			.collect(Collectors.toList());

		for (Clues clue : filteredClues)
		{
			int id = clue.getClueID();
			String clueText = configManager.getConfiguration("clue-details-text", String.valueOf(id));
			String clueColor = configManager.getConfiguration("clue-details-color", String.valueOf(id));

			// Support importing text, or color, or both
			if (clueColor != null)
			{
				if (clueText != null)
				{
					clueIdToDetailsList.add(new ClueIdToDetails(id, clueText, Color.decode(clueColor)));
				}
				else
				{
					clueIdToDetailsList.add(new ClueIdToDetails(id, Color.decode(clueColor)));
				}
			}
			else
			{
				if (clueText != null)
				{
					clueIdToDetailsList.add(new ClueIdToDetails(id, clueText));
				}
			}
		}

		if (clueIdToDetailsList.isEmpty())
		{
			sendChatMessage("You have no updated clue details to export.");
			return;
		}

		final String exportDump = gson.toJson(clueIdToDetailsList);

		log.debug("Exported clue details: {}", exportDump);

		Toolkit.getDefaultToolkit()
			.getSystemClipboard()
			.setContents(new StringSelection(exportDump), null);
		sendChatMessage(clueIdToDetailsList.size() + " clue details were copied to your clipboard.");
	}

	public void promptForImport()
	{
		final String clipboardText;
		try
		{
			clipboardText = Toolkit.getDefaultToolkit()
				.getSystemClipboard()
				.getData(DataFlavor.stringFlavor)
				.toString();
		}
		catch (IOException | UnsupportedFlavorException ex)
		{
			sendChatMessage("Unable to read system clipboard.");
			log.warn("error reading clipboard", ex);
			return;
		}

		log.debug("Clipboard contents: {}", clipboardText);
		if (Strings.isNullOrEmpty(clipboardText))
		{
			sendChatMessage("You do not have any clue details copied in your clipboard.");
			return;
		}

		List<ClueIdToDetails> importClueDetails;
		try
		{
			// CHECKSTYLE:OFF
			importClueDetails = gson.fromJson(clipboardText, new TypeToken<List<ClueIdToDetails>>(){}.getType());
			// CHECKSTYLE:ON
		}
		catch (JsonSyntaxException e)
		{
			log.debug("Malformed JSON for clipboard import", e);
			sendChatMessage("You do not have any clue details copied in your clipboard.");
			return;
		}
		catch (NumberFormatException e)
		{
			log.debug("Malformed JSON for clipboard import", e);
			sendChatMessage("Your clue details color is not properly formatted.");
			return;
		}

		if (importClueDetails.isEmpty())
		{
			sendChatMessage("You do not have any clue details copied in your clipboard.");
			return;
		}

		chatboxPanelManager.openTextMenuInput("Are you sure you want to import " + importClueDetails.size() + " clue details?")
			.option("Yes", () -> importClueDetails(importClueDetails))
			.option("No", Runnables.doNothing())
			.build();
	}

	private void importClueDetails(Collection<ClueIdToDetails> importPoints)
	{
		for (ClueIdToDetails importPoint : importPoints)
		{
			if (importPoint.text != null)
			{
				configManager.setConfiguration("clue-details-text", String.valueOf(importPoint.id), importPoint.text);
			}
			if (importPoint.color != null)
			{
				// Default color is white, so we don't need to store if user selects white
				if (Objects.equals(importPoint.color, Color.decode("#FFFFFF")))
				{
					configManager.unsetConfiguration("clue-details-color", String.valueOf(importPoint.id));
				}
				else
				{
					configManager.setConfiguration("clue-details-color", String.valueOf(importPoint.id), importPoint.color);
				}

				// Ground Items and Inventory Tags cannot support unique colors for beginner & master clues
				if (importPoint.id >= 2677 && (config.colorGroundItems() || config.colorInventoryTags()))
				{
					// Ensure ARGB format
					Color color = Color.decode(configManager.getConfiguration("clue-details-color", String.valueOf(importPoint.id)));

					if (config.colorGroundItems())
					{
						configManager.setConfiguration(GroundItemsConfig.GROUP, "highlight_" + importPoint.id, color);
					}
					if (config.colorInventoryTags())
					{
						configManager.setConfiguration(InventoryTagsConfig.GROUP, "tag_" + importPoint.id,
							gson.toJson(Map.of("color", color)));
					}
				}
			}
		}

		sendChatMessage(importPoints.size() + " clue details were imported from the clipboard.");
		plugin.getPanel().refresh();
	}

	private void sendChatMessage(final String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}
}
