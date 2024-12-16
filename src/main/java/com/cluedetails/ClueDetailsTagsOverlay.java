/*
 * Copyright (c) 2024, TheLope <https://github.com/TheLope>
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

import java.util.List;
import javax.inject.Inject;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.components.TextComponent;

public class ClueDetailsTagsOverlay extends WidgetItemOverlay
{
	private final ClueDetailsPlugin clueDetailsPlugin;
	private final ClueDetailsConfig config;
	private final ConfigManager configManager;

	@Inject
	public ClueDetailsTagsOverlay(ClueDetailsPlugin clueDetailsPlugin, ClueDetailsConfig config, ConfigManager configManager)
	{
		this.clueDetailsPlugin = clueDetailsPlugin;
		this.config = config;
		this.configManager = configManager;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (config.showInventoryClueTags())
		{
			Clues clue = Clues.forItemId(itemId);
			String clueDetail = null;
			Color clueDetailColor = Color.WHITE;

			if (clue != null
				&& !(itemId >= InterfaceID.CLUE_BEGINNER_MAP_CHAMPIONS_GUILD
					&& itemId <= InterfaceID.CLUE_BEGINNER_MAP_WIZARDS_TOWER))
			{
				clueDetail = clue.getDetail(configManager);
				clueDetailColor = clue.getDetailColor(configManager);
			}
			// If clue can't be found by Clue ID, check if it can be found by Clue text
			else
			{
				if (Clues.isTrackedClue(itemId, clueDetailsPlugin.isDeveloperMode())
					&& clueDetailsPlugin.getClueInventoryManager().hasTrackedClues())
				{
					ClueInstance readClues = clueDetailsPlugin.getClueInventoryManager().getTrackedClueByClueItemId(itemId);
					if (readClues == null)
					{
						return;
					}
					List<Integer> ids = readClues.getClueIds();

					if (ids.isEmpty()) return;

					boolean isFirst = true;
					StringBuilder text = new StringBuilder();
					StringBuilder detail = new StringBuilder();
					for (Integer id : ids)
					{
						Clues clueDetails = Clues.forClueId(id);
						if (!isFirst)
						{
							text.append("<br>");
							detail.append("<br>");
						}
						text.append(clueDetails == null ? "error" : clueDetails.getClueText());
						detail.append(clueDetails == null ? "error" : clueDetails.getDetail(configManager));
						clueDetailColor = clueDetails.getDetailColor(configManager);
						isFirst = false;
					}

					// Handle three step cryptic clues
					final ThreeStepCrypticClue threeStepCrypticClue = ThreeStepCrypticClue.forText(text.toString());
					if (threeStepCrypticClue != null)
					{
						threeStepCrypticClue.update(clueDetailsPlugin.getClueInventoryManager().getTrackedCluesInInventory());
						clueDetail = threeStepCrypticClue.getDetail(configManager);
						clueDetailColor = Color.WHITE;
					}
					else
					{
						clueDetail = detail.toString();
					}
				}
			}
			renderText(graphics, widgetItem.getCanvasBounds(), clueDetail, clueDetailColor);
		}
	}

	public int textPosition(Graphics2D graphics, Rectangle bounds, int i, int detailCount)
	{
		// Middle of item
		if (detailCount == 3 && i == 1)
		{
			return (bounds.height + graphics.getFontMetrics().getHeight()) / 2;
		}

		// Bottom of item
		if ((config.clueTagLocation() == ClueDetailsConfig.ClueTagLocation.SPLIT && i == 1)
			|| (detailCount == 2 && i == 1)
			|| config.clueTagLocation() == ClueDetailsConfig.ClueTagLocation.BOTTOM && detailCount == 1
			|| i == 2)
		{
			return bounds.height;
		}

		// Top of item
		return graphics.getFontMetrics().getHeight();
	}

	// Render Clue Detail in the "Item Tag" style
	private void renderText(Graphics2D graphics, Rectangle bounds, String clueDetail, Color clueDetailColor)
	{
		if (clueDetail == null)
		{
			return;
		}

		graphics.setFont(FontManager.getRunescapeSmallFont());

		final TextComponent textComponent = new TextComponent();

		if (config.colorInventoryClueTags())
		{
			textComponent.setColor(clueDetailColor);
		}

		String[] clueDetails = new String [] {clueDetail};
		// Handle Three Step Cryptic Clues
		if (clueDetail.contains("<br>"))
		{
			clueDetails = clueDetail.split("<br>");
		}

		// Handle split
		if (config.clueTagLocation() == ClueDetailsConfig.ClueTagLocation.SPLIT
			&& !config.clueTagSplit().isEmpty()
			&& clueDetails.length == 1)
		{
			clueDetails = clueDetails[0].split(config.clueTagSplit(), 3);
		}

		int i = 0;
		int detailCount = clueDetails.length;
		for (String detail : clueDetails)
		{
			textComponent.setPosition(new Point(
				bounds.x - 1,
				bounds.y - 1 + textPosition(graphics, bounds, i, detailCount)
			));
			textComponent.setText(detail);
			textComponent.render(graphics);
			i++;
		}
	}
}
