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

import com.cluedetails.panels.ClueDetailsParentPanel;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
		name = "Clue Details",
		description = "Provides details and highlighting for clues on the floor",
		tags = {"clue", "overlay"}
)
public class ClueDetailsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClueDetailsConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClueDetailsOverlay infoOverlay;

	@Inject
	private ClueDetailsTagsOverlay tagsOverlay;

	@Inject
	private ClueDetailsWidgetOverlay widgetOverlay;

	@Inject
	private EventBus eventBus;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	@Inject
	private ClueDetailsSharingManager clueDetailsSharingManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ItemManager itemManager;

	@Getter
	@Inject
	@Named("developerMode")
	private boolean developerMode;

	@Inject
	Gson gson;

	@Getter
	private ClueInventoryManager clueInventoryManager;

	@Getter
	private ClueGroundManager clueGroundManager;

	private ClueBankManager clueBankManager;

	private CluePreferenceManager cluePreferenceManager;

	@Getter
	private ClueDetailsParentPanel panel;

	private NavigationButton navButton;

	private boolean profileChanged;

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(infoOverlay);
		eventBus.register(infoOverlay);

		overlayManager.add(tagsOverlay);

		overlayManager.add(widgetOverlay);
		eventBus.register(widgetOverlay);

		cluePreferenceManager = new CluePreferenceManager(configManager);
		clueGroundManager = new ClueGroundManager(client, configManager, this);
		clueBankManager = new ClueBankManager(client, configManager, gson);
		clueInventoryManager = new ClueInventoryManager(client, configManager, this, clueGroundManager, clueBankManager, chatboxPanelManager);
		clueBankManager.startUp(clueInventoryManager);

		infoOverlay.startUp(this, clueGroundManager, clueInventoryManager);
		widgetOverlay.setClueInventoryManager(clueInventoryManager);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");

		panel = new ClueDetailsParentPanel(configManager, cluePreferenceManager, config, chatboxPanelManager, clueDetailsSharingManager);
		navButton = NavigationButton.builder()
				.tooltip("Clue Details")
				.icon(icon)
				.priority(7)
				.panel(panel)
				.build();

		if (config.showSidebar())
		{
			clientToolbar.addNavigation(navButton);
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(infoOverlay);
		eventBus.unregister(infoOverlay);

		overlayManager.remove(tagsOverlay);

		overlayManager.remove(widgetOverlay);
		eventBus.unregister(widgetOverlay);

		clientToolbar.removeNavigation(navButton);

		clueGroundManager.saveStateToConfig();
		clueBankManager.saveStateToConfig();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INVENTORY.getId())
		{
			clueInventoryManager.updateInventory(event.getItemContainer());
		}
		else if (event.getContainerId() == InventoryID.BANK.getId())
		{
			clueBankManager.handleBankChange(event.getItemContainer());
		}

	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() >= InterfaceID.CLUE_BEGINNER_MAP_CHAMPIONS_GUILD
			&& event.getGroupId() <= InterfaceID.CLUE_BEGINNER_MAP_WIZARDS_TOWER)
		{
			clueInventoryManager.updateClueText(event.getGroupId());
		}
		else if (event.getGroupId() == ComponentID.CLUESCROLL_TEXT >> 16)
		{
			clientThread.invokeLater(() ->
			{
				Widget clueScrollText = client.getWidget(ComponentID.CLUESCROLL_TEXT);
				if (clueScrollText != null)
				{
					String text = clueScrollText.getText();
					clueInventoryManager.updateClueText(text);
				}
			});
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			clueGroundManager.saveStateToConfig();
			clueBankManager.saveStateToConfig();
			profileChanged = true;
		}

		if (event.getGameState() == GameState.LOGGED_IN && profileChanged)
		{
			profileChanged = false;
			clueGroundManager.loadStateFromConfig();
			clueBankManager.loadStateFromConfig();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		profileChanged = true;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		clueGroundManager.onGameTick();
		clueInventoryManager.onGameTick();
	}

	/* This gets called when:
	   Player logs in
	   Player enters from outside 3 zones distance to 3 or closer (teleport in, run in)
	   Player turns on plugin (and seems onItemSpawned is called for all existing items in scene, including
	     ones outside the 3 zone limit which're rendered
	 */
	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		clueGroundManager.onItemSpawned(event);
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		clueGroundManager.onItemDespawned(event);
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		clueInventoryManager.onMenuEntryAdded(event, cluePreferenceManager, panel);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("clue-details-highlights"))
		{
			infoOverlay.refreshHighlights();
		}

		if (!event.getGroup().equals(ClueDetailsConfig.class.getAnnotation(ConfigGroup.class).value()))
		{
			return;
		}

		if ("showSidebar".equals(event.getKey()))
		{
			if ("true".equals(event.getNewValue()))
			{
				clientToolbar.addNavigation(navButton);
			}
			else
			{
				clientToolbar.removeNavigation(navButton);
			}
		}

		panel.refresh();
	}

	@Subscribe(priority = 100)
	private void onClientShutdown(ClientShutdown event)
	{
		clueGroundManager.saveStateToConfig();
		clueBankManager.saveStateToConfig();
	}

	@Provides
	ClueDetailsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClueDetailsConfig.class);
	}
}
