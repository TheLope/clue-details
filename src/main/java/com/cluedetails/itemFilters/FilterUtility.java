package com.cluedetails.itemFilters;

import java.util.List;

public class FilterUtility
{
	public static short[] getPrimitiveShortArray(List<Short> shorts)
	{
		short[] recentItems = new short[shorts.size()];
		for (int i = 0; i < shorts.size(); i++)
			recentItems[i] = shorts.get(i);
		return recentItems;
	}
}