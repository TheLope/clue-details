package com.cluedetails.itemFilters.events;

import com.cluedetails.itemFilters.SearchFilter;
import com.cluedetails.itemFilters.model.FilterOption;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OtherFilterOptionActivated {
	private SearchFilter searchFilter;
	private FilterOption filterOption;

	public OtherFilterOptionActivated(SearchFilter filter, FilterOption option)
	{
		this.searchFilter = filter;
		this.filterOption = option;
	}
}