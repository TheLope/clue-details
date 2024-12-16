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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Arrays;
import javax.inject.Inject;

import com.cluedetails.filters.ClueTier;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;

public class ClueDetailsWidgetOverlay extends OverlayPanel
{
	private final Client client;
	private final ClueDetailsConfig config;
	private final ConfigManager configManager;
	@Setter
	private ClueInventoryManager clueInventoryManager;

	private static final Color TITLED_CONTENT_COLOR = new Color(190, 190, 190);

	@Inject
	public ClueDetailsWidgetOverlay(Client client, ClueDetailsConfig config, ConfigManager configManager)
	{
		setPriority(PRIORITY_HIGHEST);
		setLayer(OverlayLayer.ABOVE_WIDGETS);

		this.client = client;
		this.config = config;
		this.configManager = configManager;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.showInventoryCluesOverlay())
		{
			createInventoryCluesOverlay();
		}

		return super.render(graphics);
	}

	private void createInventoryCluesOverlay()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory == null) return;

		for (Item item : inventory.getItems())
		{
			Clues clue = Clues.forItemId(item.getId());
			if (clue != null && !Arrays.asList(
				ClueTier.MEDIUM_CHALLENGE,
				ClueTier.HARD_CHALLENGE,
				ClueTier.ELITE_CHALLENGE).contains(clue.getClueTier()))
			{
				Color color = TITLED_CONTENT_COLOR;
				if (config.colorInventoryCluesOverlay())
				{
					color = clue.getDetailColor(configManager);
				}

				panelComponent.getChildren().add(LineComponent.builder()
					.left(clue.getDetail(configManager))
					.leftColor(color)
					.build());
			}

			ClueInstance clueInstance = clueInventoryManager.getTrackedClueByClueItemId(item.getId());
			if (clueInstance == null || clueInstance.getClueIds().isEmpty()) continue;

			for (Integer clueId : clueInstance.getClueIds())
			{
				Clues cluePart = Clues.forClueId(clueId);
				if (cluePart == null) continue;

				Color color = TITLED_CONTENT_COLOR;
				if (config.colorInventoryCluesOverlay())
				{
					color = cluePart.getDetailColor(configManager);
				}

				panelComponent.getChildren().add(LineComponent.builder()
					.left(cluePart.getDetail(configManager))
					.leftColor(color)
					.build());
			}
		}
	}
}
