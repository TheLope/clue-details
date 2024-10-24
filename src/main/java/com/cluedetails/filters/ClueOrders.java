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
package com.cluedetails.filters;

import com.cluedetails.Clues;
import java.util.Comparator;
import java.util.List;

public class ClueOrders
{
	static List<ClueTier> tierOrder = List.of(
		ClueTier.BEGINNER,
		ClueTier.EASY,
		ClueTier.MEDIUM,
		ClueTier.MEDIUM_KEY,
		ClueTier.HARD,
		ClueTier.ELITE,
		ClueTier.MASTER
	);

	static List<ClueRegion> regionOrder = List.of(
		ClueRegion.MISTHALIN, ClueRegion.ASGARNIA, ClueRegion.KARAMJA, ClueRegion.KANDARIN, ClueRegion.FREMENNIK_PROVINCE, ClueRegion.KHARIDIAN_DESERT,
		ClueRegion.MORYTANIA, ClueRegion.TIRANNWN, ClueRegion.WILDERNESS, ClueRegion.KOUREND, ClueRegion.VARLAMORE
	);

	public static Comparator<Clues> sortByTier()
	{
		return Comparator.comparing(q -> tierOrder.indexOf(q));
	}

	public static Comparator<Clues> sortByRegion()
	{
		return Comparator.comparing(q -> regionOrder.indexOf(q));
	}
}
