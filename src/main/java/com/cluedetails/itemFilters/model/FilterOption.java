package com.cluedetails.itemFilters.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FilterOption
{
	private String title;
	private String searchValue;

	private Object data;

	public FilterOption(String title, String searchValue)
	{
		this.title = title;
		this.searchValue = searchValue;
	}
}