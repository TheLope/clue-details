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
package com.cluedetails.panels;

import com.cluedetails.CluePreferenceManager;
import com.cluedetails.Clues;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;

public class ClueTableCellRenderer extends JPanel implements TableCellRenderer
{
	// Constants for borders and colors
	private static final Border SELECTED_BORDER = new CompoundBorder(
		BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BRAND_ORANGE),
		BorderFactory.createEmptyBorder(5, 10, 4, 10));
	private static final Border UNSELECTED_BORDER = BorderFactory.createEmptyBorder(5, 10, 5, 10);

	private JTextArea textArea = JGenerator.makeJTextArea();
	private CluePreferenceManager cluePreferenceManager;
	private ConfigManager configManager;
	private ClueDetailsParentPanel clueDetailsParentPanel;

	public ClueTableCellRenderer(ClueDetailsParentPanel clueDetailsParentPanel, CluePreferenceManager cluePreferenceManager, ConfigManager configManager)
	{
		this.clueDetailsParentPanel = clueDetailsParentPanel;
		this.cluePreferenceManager = cluePreferenceManager;
		this.configManager = configManager;

		setLayout(new BorderLayout());
		setOpaque(true);
		setBorder(new EmptyBorder(5, 5, 0, 5));

		textArea.setMargin(new Insets(5, 5, 5, 0));
		add(textArea, BorderLayout.CENTER);
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
	{
		ListItem item = (ListItem) value;
		if (item.isHeader())
		{
			textArea.setText(item.getHeader());
			textArea.setForeground(Color.WHITE);
			textArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
			setBackground(ColorScheme.DARK_GRAY_COLOR);
			textArea.setBorder(UNSELECTED_BORDER);
		}
		else
		{
			Clues clue = item.getClue();
			textArea.setText(clue.getDetail(configManager));
			textArea.setBackground(clueDetailsParentPanel.getHoveredRow() == row ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			textArea.setForeground(clue.getDetailColor(configManager));
			textArea.setOpaque(true);

			boolean isActive = cluePreferenceManager.getPreference(clue.getClueID());
			textArea.setBorder(isActive ? SELECTED_BORDER : UNSELECTED_BORDER);
			setBackground(ColorScheme.DARK_GRAY_COLOR);
		}

		adjustRowHeight(table, row);

		return this;
	}

	private void adjustRowHeight(JTable table, int row)
	{
		int prefHeight = getPreferredHeight(textArea);

		if (table.getRowHeight(row) != prefHeight)
		{
			table.setRowHeight(row, prefHeight + 6);
		}
	}

	private int getPreferredHeight(JTextArea textArea)
	{
		int prefHeight = textArea.getPreferredSize().height + getInsets().top + getInsets().bottom;

		return prefHeight;
	}
}