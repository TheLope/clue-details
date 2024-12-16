/*
 * Copyright (c) 2024, Zoinkwiz <https://www.github.com/Zoinkwiz>
 * Copyright (c) 2017, Aria <aria@ar1as.space>
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public class ClueDetailsOverlay extends OverlayPanel
{
	private final Client client;
	private final ClueDetailsConfig config;
	private final TooltipManager tooltipManager;

	protected ModelOutlineRenderer modelOutlineRenderer;
	private final ConfigManager configManager;

	private final Notifier notifier;
	private ClueDetailsPlugin clueDetailsPlugin;
	private ClueGroundManager clueGroundManager;
	private ClueInventoryManager clueInventoryManager;

	protected Multimap<Tile, Integer> tileHighlights = ArrayListMultimap.create();

	protected static final int MAX_DISTANCE = 2350;
	protected static final int SCENE_TO_LOCAL = 128;

	@Inject
	public ClueDetailsOverlay(Client client, ClueDetailsConfig config, TooltipManager tooltipManager, ModelOutlineRenderer modelOutlineRenderer, ConfigManager configManager, Notifier notifier)
	{
		setPriority(PRIORITY_HIGHEST);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setDragTargetable(false);
		setPosition(OverlayPosition.TOOLTIP);

		this.client = client;
		this.config = config;
		this.tooltipManager = tooltipManager;
		this.modelOutlineRenderer = modelOutlineRenderer;
		this.configManager = configManager;
		this.notifier = notifier;

		tileHighlights.clear();
		if (client.getGameState() == GameState.LOGGING_IN)
		{
			refreshHighlights();
		}
	}

	public void startUp(ClueDetailsPlugin clueDetailsPlugin, ClueGroundManager clueGroundManager, ClueInventoryManager clueInventoryManager)
	{
		this.clueDetailsPlugin = clueDetailsPlugin;
		this.clueGroundManager = clueGroundManager;
		this.clueInventoryManager = clueInventoryManager;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (client.isMenuOpen())
		{
			showMenuItem();
		}
		else
		{
			if (config.showHoverText())
			{
				showHoveredItem();
			}
		}

		tileHighlights.keySet().forEach(tile -> checkAllTilesForHighlighting(tile, tileHighlights.get(tile)));

		return super.render(graphics);
	}

	private void showHoveredItem()
	{
		Menu menu = client.getMenu();
		MenuEntry[] menuEntries = menu.getMenuEntries();
		int last = menuEntries.length - 1;

		if (last < 0)
		{
			return;
		}

		MenuEntry menuEntry = menuEntries[last];
		MenuEntryAndPos menuEntryAndPos = new MenuEntryAndPos(menuEntry, last, 0);

		if (!isTakeClue(menuEntry) && !isReadClue(menuEntry))
		{
			return;
		}

		if (isReadClue(menuEntry) && (config.showInventoryClueTags() || (config.showInventoryCluesOverlay())))
		{
			return;
		}

		String clueText = getText(menuEntryAndPos, config.colorHoverText());

		if (clueText == null) return;
		// tooltip only supports </br> for multiline strings
		String tooltipClueText = clueText.replaceAll("<br>", "</br>");
		tooltipManager.add(new Tooltip(tooltipClueText));
	}

	private void showMenuItem()
	{
		Menu menu = client.getMenu();
		MenuEntry[] currentMenuEntries = menu.getMenuEntries();

		if (currentMenuEntries == null) return;

		//If on floor tile
		if (Arrays.stream(currentMenuEntries).noneMatch(this::isTakeClue))
		{
			return;
		}

		List<MenuEntryAndPos> entriesByTile = getEntriesByTile(currentMenuEntries);

		if (config.showHoverText() && !config.changeClueText())
		{
			addTooltip(entriesByTile);
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		MenuEntry[] entries = event.getMenuEntries();
		if (Arrays.stream(entries).noneMatch(this::isTakeClue))
		{
			return;
		}

		List<MenuEntryAndPos> entriesByTile = getEntriesByTile(entries);

		if (config.changeClueText())
		{
			changeFloorText(entriesByTile);
		}
	}

	private void changeFloorText(List<MenuEntryAndPos> entriesByTile)
	{
		// Change text of actual clue
		for (MenuEntryAndPos entryAndPos : entriesByTile)
		{
			MenuEntry menuEntry = entryAndPos.getMenuEntry();

			boolean showColor = config.colorChangeClueText();
			// Only change color if it is not the default
			if (showColor)
			{
				int scrollID = menuEntry.getIdentifier();
				if (isReadClue(menuEntry))
				{
					scrollID = menuEntry.getItemId();
				}
				Clues matchingClue = Clues.forItemId(scrollID);

				if (matchingClue != null)
				{
					if (matchingClue.getDetailColor(configManager) == Color.WHITE)
					{
						showColor = false;
					}
				}
			}

			String newText = getText(entryAndPos, showColor);
			if (newText == null || !isTakeOrMarkClue(menuEntry)) continue;
			String regex = "Clue scroll \\(.*?\\)";
			if (clueDetailsPlugin.isDeveloperMode())
			{
				regex = "(Daeyalt essence|Clue scroll \\(.*?\\))";
			}

			// Compile the pattern
			Pattern pattern = Pattern.compile(regex);
			Matcher matcher = pattern.matcher(menuEntry.getTarget());

			// Handle master three-step cryptic
			String[] newTexts = newText.split("<br>");

			// TODO: Text doesn't update after details changed
			// TODO: Doesn't update when torn parts obtained
			if (newTexts.length > 1)
			{
				Menu submenu = menuEntry.createSubMenu();

				for (String text : newTexts)
				{
					submenu.createMenuEntry(-1)
						.setOption(text)
						.setType(MenuAction.RUNELITE);
				}
				newText = "Three-step (master)";
			}
			// Replace the matched text with the new text
			String newTarget = matcher.replaceAll(newText);

			menuEntry.setTarget(newTarget);
		}
	}

	private void addTooltip(List<MenuEntryAndPos> relevantMenuEntries)
	{
		Point mousePosition = client.getMouseCanvasPosition();
		int menuX = client.getMenu().getMenuX();
		int menuY = client.getMenu().getMenuY();
		int menuWidth = client.getMenu().getMenuWidth();

		int menuEntryHeight = 15;
		int headerHeight = menuEntryHeight + 3;

		if (mousePosition.getX() < menuX || mousePosition.getX() > menuX + menuWidth) return;

		int posInMenuY = (Math.round(mousePosition.getY()) - menuY - headerHeight) / menuEntryHeight;
		int entryTopY = menuY + headerHeight + posInMenuY * menuEntryHeight;

		Optional<MenuEntryAndPos> entry = relevantMenuEntries.stream()
			.filter(menuEntryAndPos -> menuEntryAndPos.getPosInMenu() == posInMenuY)
			.findFirst();
		if (entry.isEmpty()) return;
		MenuEntryAndPos entryAndPos = entry.get();

		MenuEntry hoveredEntry = entryAndPos.getMenuEntry();

		if (!isTakeOrMarkClue(hoveredEntry)) return;

		String text = getText(entryAndPos, config.colorHoverText());

		panelComponent.getChildren().add(LineComponent.builder().left(text).build());
		double infoPanelWidth = panelComponent.getBounds().getWidth();
		int viewportWidth = client.getViewportWidth();
		if (menuX + menuWidth + infoPanelWidth > viewportWidth)
		{
			panelComponent.setPreferredLocation(new java.awt.Point(menuX - (int) infoPanelWidth, entryTopY));
		}
		else
		{
			panelComponent.setPreferredLocation(new java.awt.Point(menuX + menuWidth, entryTopY));
		}
	}

	private List<MenuEntryAndPos> getEntriesByTile(MenuEntry[] menuEntries)
	{
		// Order on floor is drop order from what I can tell.
		// Most recently dropped is in pos 0 of array, up to first item dropped
		List<MenuEntryAndPos> mappedEntries = new ArrayList<>();

		Map<WorldPoint, Integer> foundPosForWp = new HashMap<>();
		// We want to keep track from soonest to despawn to most recently dropped
		for (int i = menuEntries.length - 1; i >= 0; i--)
		{
			if (!isTakeOrMarkClue(menuEntries[i])) continue;
			int x = menuEntries[i].getParam0() * SCENE_TO_LOCAL;
			int y = menuEntries[i].getParam1() * SCENE_TO_LOCAL;
			int wv = menuEntries[i].getWorldViewId();
			LocalPoint itemLp = new LocalPoint(x, y, wv);
			WorldPoint itemWp = WorldPoint.fromLocal(client, itemLp);
			int currentPosForTile = foundPosForWp.getOrDefault(itemWp, 0);
			if (Clues.isTrackedClueOrTornClue(menuEntries[i].getIdentifier(), clueDetailsPlugin.isDeveloperMode()))
			{
				mappedEntries.add(new MenuEntryAndPos(menuEntries[i], menuEntries.length - i - 1, currentPosForTile));
				if (isTakeClue(menuEntries[i])) foundPosForWp.put(itemWp, currentPosForTile + 1);
			}
			else
			{
				mappedEntries.add(new MenuEntryAndPos(menuEntries[i], menuEntries.length - i - 1, -1));
			}
		}

		return mappedEntries;
	}

	public boolean isTakeClue(MenuEntry entry)
	{
		String option = entry.getOption();
		MenuAction type = entry.getType();
		int identifier = entry.getIdentifier();

		return Clues.isClue(identifier, clueDetailsPlugin.isDeveloperMode()) && type == MenuAction.GROUND_ITEM_THIRD_OPTION && option.equals("Take");
	}

	public boolean isTakeOrMarkClue(MenuEntry entry)
	{
		String option = entry.getOption();
		MenuAction type = entry.getType();
		int identifier = entry.getIdentifier();

		return Clues.isClue(identifier, clueDetailsPlugin.isDeveloperMode()) && (
			(type == MenuAction.GROUND_ITEM_THIRD_OPTION && option.equals("Take")) ||
				(type == MenuAction.RUNELITE && (option.equals("Unmark") || option.equals("Mark"))));
	}

	public boolean isReadClue(MenuEntry entry)
	{
		String option = entry.getOption();
		int itemId = entry.getItemId();
		return Clues.isClue(itemId, clueDetailsPlugin.isDeveloperMode()) && option.equals("Read");
	}

	private boolean shouldHighlight(int id)
	{
		if (id < 2677) return false; //TODO: Support fake beginner & master IDs
		String shouldHighlight = configManager.getConfiguration("clue-details-highlights", String.valueOf(id));
		return "true".equals(shouldHighlight);
	}

	private String getText(MenuEntryAndPos menuEntryAndPos, boolean showColor)
	{
		MenuEntry menuEntry = menuEntryAndPos.getMenuEntry();
		int scrollID = menuEntry.getIdentifier();
		if (isReadClue(menuEntry))
		{
			scrollID = menuEntry.getItemId();
		}
		Clues matchingClue = Clues.forItemId(scrollID);
		if (matchingClue != null)
		{
			String text = matchingClue.getDetail(configManager);
			String color = Integer.toHexString(matchingClue.getDetailColor(configManager).getRGB()).substring(2);
			if (showColor)
			{
				return "<col=" + color + ">" + text;
			}
			return text;
		}

		if (isReadClue(menuEntry))
		{
			ClueInstance clueInstance = clueInventoryManager.getTrackedClueByClueItemId(scrollID);
			if (clueInstance != null && !clueInstance.getClueIds().isEmpty())
			{
				return clueInstance.getCombinedClueText(configManager, showColor);
			}
		}

		MenuEntry[] currentMenuEntries = {menuEntry};
		List<MenuEntryAndPos> entriesByTile = new ArrayList<>();
		if (Arrays.stream(currentMenuEntries).anyMatch(this::isTakeClue))
		{
			entriesByTile = getEntriesByTile(currentMenuEntries);
		}

		if (!entriesByTile.isEmpty())
		{
			return getTextForTrackedClue(menuEntryAndPos, showColor);
		}

		return null;
	}

	private String getTextForTrackedClue(MenuEntryAndPos entry, boolean showColor)
	{
		MenuEntry menuEntry = entry.getMenuEntry();
		int sceneX = menuEntry.getParam0();
		int sceneY = menuEntry.getParam1();
		int wv = menuEntry.getWorldViewId();
		LocalPoint itemLp = new LocalPoint(sceneX * SCENE_TO_LOCAL, sceneY * SCENE_TO_LOCAL, wv);
		WorldPoint itemWp = WorldPoint.fromLocalInstance(client, itemLp);
		List<ClueInstance> trackedClues = clueGroundManager.getGroundClues().get(itemWp);
		if (trackedClues == null) return null;
		// TODO: Fix, when a clue is picked up, posOnTile doesn't work any more. Needs shifting
		ClueInstance clueInstance = trackedClues.get(entry.getPosOnTile());
		if (clueInstance == null) return null;

		return clueInstance.getCombinedClueText(configManager, showColor);
	}

	@Subscribe
	public void onGameStateChanged(final GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			tileHighlights.clear();
		}

		if (event.getGameState() == GameState.LOGGED_IN)
		{
			addItemTiles();
		}
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned itemSpawned)
	{
		TileItem item = itemSpawned.getItem();
		Tile tile = itemSpawned.getTile();
		if (shouldHighlight(item.getId()))
		{
			notifier.notify(config.markedClueDroppedNotification(), "A highlighted clue has dropped!");
			tileHighlights.get(tile).add(item.getId());
		}
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned itemDespawned)
	{
		Tile tile = itemDespawned.getTile();
		if (tileHighlights.containsKey(tile))
		{
			tileHighlights.get(tile).removeIf((i) -> i == itemDespawned.getItem().getId());
		}
	}

	protected void addItemTiles()
	{
		tileHighlights.clear();

		Tile[][] squareOfTiles = client.getTopLevelWorldView().getScene().getTiles()[client.getTopLevelWorldView().getPlane()];

		// Reduce the two-dimensional array into a single list for processing.
		List<Tile> tiles = Stream.of(squareOfTiles)
			.flatMap(Arrays::stream)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());

		for (Tile tile : tiles)
		{
			List<TileItem> items = tile.getGroundItems();
			if (items != null)
			{
				for (TileItem item : items)
				{
					if (item == null)
					{
						continue;
					}

					if (shouldHighlight(item.getId()))
					{
						tileHighlights.get(tile).add(item.getId());
						break;
					}
				}
			}
		}
	}

	public void refreshHighlights()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		addItemTiles();
	}

	private void checkAllTilesForHighlighting(Tile tile, Collection<Integer> ids)
	{
		if (!config.highlightMarkedClues())
		{
			return;
		}
		Player player = client.getLocalPlayer();

		if (player == null)
		{
			return;
		}

		LocalPoint playerLocation = player.getLocalLocation();
		if (!ids.isEmpty())
		{
			LocalPoint location = tile.getLocalLocation();

			if (location == null)
			{
				return;
			}

			if (location.distanceTo(playerLocation) > MAX_DISTANCE)
			{
				return;
			}

			Polygon poly = Perspective.getCanvasTilePoly(client, location);
			if (poly == null)
			{
				return;
			}

			// TODO: Currently outlines all items in the tile
			modelOutlineRenderer.drawOutline(
				tile.getItemLayer(),
				config.outlineWidth(),
				JagexColors.CHAT_PUBLIC_TEXT_OPAQUE_BACKGROUND,
				config.highlightFeather());
		}
	}
}
