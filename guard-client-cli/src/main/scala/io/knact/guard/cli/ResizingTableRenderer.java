package io.knact.guard.cli;

import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.gui2.table.DefaultTableRenderer;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.gui2.table.TableModel;

class ResizingTableRenderer<V> extends DefaultTableRenderer<V> {

	@Override
	public void drawComponent(TextGUIGraphics graphics, Table<V> table) {

		// FIXME IOBE when window resized within a tick
		try {
			super.drawComponent(graphics, table);
		} catch (Exception ignored) {  }

		int componentHeight = graphics.getSize().getRows();
		int columns = table.getSize().getColumns();
		// subtract one for header

		if (componentHeight >= 0) table.setVisibleRows(componentHeight - 1);
		TableModel<V> model = table.getTableModel();

		table.setVisibleColumns(model.getColumnCount());
		int sum = 0;
		for (int i = 0; i < model.getColumnCount(); i++) {
			if (sum >= columns) {
				table.setVisibleColumns(i + 1);
				break;
			}
			sum += table
					.getTableHeaderRenderer()
					.getPreferredSize(table, model.getColumnLabel(i), i)
					.getColumns();
		}

	}

}