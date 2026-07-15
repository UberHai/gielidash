package com.gielidash.ui;

import javax.swing.JTextField;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/** Restricts a text field to whole numbers only (user-decided for all fee inputs). */
final class NumericField
{
	private NumericField()
	{
	}

	static void apply(JTextField field)
	{
		((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter()
		{
			@Override
			public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
				throws BadLocationException
			{
				super.insertString(fb, offset, string.replaceAll("[^0-9]", ""), attr);
			}

			@Override
			public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
				throws BadLocationException
			{
				super.replace(fb, offset, length, text == null ? null : text.replaceAll("[^0-9]", ""), attrs);
			}
		});
	}
}
