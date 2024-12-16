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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import static net.runelite.api.ItemID.TORN_CLUE_SCROLL_PART_1;
import static net.runelite.api.ItemID.TORN_CLUE_SCROLL_PART_2;
import static net.runelite.api.ItemID.TORN_CLUE_SCROLL_PART_3;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

@Getter
@RequiredArgsConstructor
public class ThreeStepCrypticClue
{
	private final List<Map.Entry<Clues, Boolean>> clueSteps;
	private final String text;

	public static ThreeStepCrypticClue forText(String text)
	{
		final String[] split = text.split("<br>");
		final List<Map.Entry<Clues, Boolean>> steps = new ArrayList<>(split.length);

		for (String part : split)
		{
			boolean isDone = part.contains("<str>");
			final String rawText = Text.sanitizeMultilineText(part);

			for (Clues clue : Clues.CLUES)
			{
				if (!rawText.equalsIgnoreCase(clue.getClueText()))
				{
					continue;
				}

				steps.add(new AbstractMap.SimpleEntry<>(clue, isDone));
				break;
			}
		}

		if (steps.isEmpty() || steps.size() < 3)
		{
			return null;
		}

		return new ThreeStepCrypticClue(steps, text);
	}

	public void update(Set<Integer> trackedClues)
	{
		checkForPart(trackedClues, TORN_CLUE_SCROLL_PART_1, 0);
		checkForPart(trackedClues, TORN_CLUE_SCROLL_PART_2, 1);
		checkForPart(trackedClues, TORN_CLUE_SCROLL_PART_3, 2);
	}

	private void checkForPart(final Set<Integer> trackedClues, int clueScrollPart, int index)
	{
		// If we have the part then that step is done
		if (trackedClues.contains(clueScrollPart))
		{
			final Map.Entry<Clues, Boolean> entry = clueSteps.get(index);

			if (!entry.getValue())
			{
				entry.setValue(true);
			}
		}
	}

	public String getDetail(ConfigManager configManager)
	{
		StringBuilder text = new StringBuilder();

		for (final Map.Entry<Clues, Boolean> e : clueSteps)
		{
			if (!e.getValue())
			{
				Clues clue = e.getKey();
				String detail = clue.getDetail(configManager);
				String color = Integer.toHexString(clue.getDetailColor(configManager).getRGB()).substring(2);
				text.append("<col=").append(color).append(">").append(detail).append("<br>");
			}
		}
		return text.toString();
	}
}
