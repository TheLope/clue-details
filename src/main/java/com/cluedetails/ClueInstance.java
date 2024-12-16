/*
 * Copyright (c) 2024, Zoinkwiz <https://github.com/Zoinkwiz>
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

import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;

@Data
public class ClueInstance
{
	@Setter
	private List<Integer> clueIds; // Fake ID from ClueText
	private final int itemId; // Clue item ID
	private final WorldPoint location; // Null if in inventory

	@Getter
	private final Integer timeToDespawnFromDataInTicks;
	private TileItem tileItem;

	// Constructor for clues from config
	public ClueInstance(ClueInstanceData data)
	{
		this.clueIds = data.getClueIds();
		this.itemId = data.getItemId();
		this.location = data.getLocation();
		// if had on then turned off in same session, we don't know what happened in meantime.
		// Ticks go forward even when logged into other game modes. For simplicity we assume when
		// Loaded we just are starting from the exact same despawn time remaining.
		this.timeToDespawnFromDataInTicks = data.getDespawnTick();
	}

	// Constructor for inventory clues from inventory changed event
	public ClueInstance(List<Integer> clueIds, int itemId)
	{
		this.clueIds = clueIds;
		this.itemId = itemId;
		this.location = null;
		this.timeToDespawnFromDataInTicks = -1;
	}

	// Constructor for ground clues
	public ClueInstance(List<Integer> clueIds, int itemId, WorldPoint location, TileItem tileItem, int currentTick)
	{
		this.clueIds = clueIds;
		this.itemId = itemId;
		this.location = location;
		this.tileItem = tileItem;
		this.timeToDespawnFromDataInTicks = currentTick;
	}

	public int getDespawnTick(int currentTick)
	{
		if (tileItem != null)
		{
			return tileItem.getDespawnTime();
		}
		return currentTick + timeToDespawnFromDataInTicks;
	}

	// Theory: This should mean that tiles we've seen have TileItem, and the actual despawn is used for ALL items on that tile
	// For tiles we've not seen this session, all items on it should have no TileItem, and thus we'll keep the same consistent tick diff
	public int getTicksToDespawnConsideringTileItem(int currentTick)
	{
		if (tileItem != null)
		{
			return tileItem.getDespawnTime() - currentTick;
		}
		return timeToDespawnFromDataInTicks == null ? -1 : timeToDespawnFromDataInTicks;
	}

	public String getCombinedClueText(ConfigManager configManager, boolean showColor)
	{
		StringBuilder returnText = new StringBuilder();
		boolean isFirst = true;
		for (Integer clueId : getClueIds())
		{
			Clues cluePart = Clues.forClueId(clueId);
			if (cluePart == null) continue;
			if (isFirst)
			{
				isFirst = false;
			}
			else
			{
				returnText.append("<br>");
			}

			if (showColor)
			{
				String color = Integer.toHexString(cluePart.getDetailColor(configManager).getRGB()).substring(2);
				returnText.append("<col=").append(color).append(">");
			}

			returnText.append(cluePart.getDetail(configManager));
		}
		if (returnText.length() == 0) return "Unknown, read to track";
		return returnText.toString();
	}
}
