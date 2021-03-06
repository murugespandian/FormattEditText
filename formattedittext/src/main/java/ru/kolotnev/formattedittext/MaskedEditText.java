package ru.kolotnev.formattedittext;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatEditText;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.AttributeSet;

/**
 * Masked input field.
 * <p>
 * Based on sources from Reinaldo Arrosi
 * https://github.com/reinaldoarrosi/MaskedEditText
 */
@SuppressWarnings("unused")
public class MaskedEditText extends AppCompatEditText {
	private static final char NUMBER_MASK = '9';
	private static final char ALPHA_MASK = 'A';
	private static final char ALPHANUMERIC_MASK = '*';
	private static final char CHARACTER_MASK = '?';
	private static final char ESCAPE_CHAR = '\\';
	private static final char PLACEHOLDER = ' ';

	@NonNull
	private String mask;
	@NonNull
	private String placeholder;

	private final TextWatcher textWatcher = new TextWatcher() {
		private boolean updating = false;

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			/* do nothing */
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			/* do nothing */
		}

		@Override
		public void afterTextChanged(Editable s) {
			if (updating || mask.length() == 0)
				return;

			updating = true;

			stripMaskChars(s);
			formatMask(s);

			updating = false;
		}
	};

	public MaskedEditText(Context context) {
		this(context, "");
	}

	public MaskedEditText(Context context, String mask) {
		this(context, mask, PLACEHOLDER);
	}

	public MaskedEditText(Context context, String mask, char placeholder) {
		this(context, null, mask, placeholder);
	}

	public MaskedEditText(Context context, AttributeSet attr) {
		this(context, attr, "");
	}

	public MaskedEditText(Context context, AttributeSet attr, String mask) {
		this(context, attr, "", PLACEHOLDER);
	}

	public MaskedEditText(Context context, AttributeSet attr, @NonNull String mask, char placeholder) {
		super(context, attr);

		TypedArray a = context.obtainStyledAttributes(attr, R.styleable.MaskedEditText);
		final int n = a.getIndexCount();
		for (int i = 0; i < n; ++i) {
			int at = a.getIndex(i);
			if (at == R.styleable.MaskedEditText_mask) {
				String m = a.getString(at);
				if (mask.isEmpty() && m != null) {
					mask = m;
				}
			} else if (at == R.styleable.MaskedEditText_placeholder) {
				String pl = a.getString(at);
				if (pl != null && pl.length() > 0 && placeholder == PLACEHOLDER) {
					placeholder = pl.charAt(0);
				}
			}
		}

		a.recycle();

		this.mask = mask;
		this.placeholder = String.valueOf(placeholder);
		addTextChangedListener(textWatcher);

		if (mask.length() > 0)
			setText(getText()); // sets the text to create the mask
	}

	/**
	 * Returns the current mask.
	 *
	 * @return String used as mask for formatting text in input field.
	 */
	@NonNull
	public String getMask() {
		return mask;
	}

	/**
	 * Sets the new mask and updates the text in field.
	 *
	 * @param mask
	 * 		New mask.
	 */
	public void setMask(@NonNull String mask) {
		this.mask = mask;
		setText(getText());
	}

	/**
	 * Returns placeholder char.
	 *
	 * @return Char which currently used as placeholder.
	 */
	public char getPlaceholder() {
		return placeholder.charAt(0);
	}

	/**
	 * Sets the new placeholder and reformats current value.
	 *
	 * @param placeholder
	 * 		New placeholder char.
	 */
	public void setPlaceholder(char placeholder) {
		this.placeholder = String.valueOf(placeholder);
		setText(getText());
	}

	/**
	 * Returns current value in input field.
	 *
	 * @param removeMask
	 * 		Must be value returned without mask.
	 *
	 * @return Current value.
	 */
	@NonNull
	public Editable getText(boolean removeMask) {
		if (removeMask) {
			SpannableStringBuilder value = new SpannableStringBuilder(getText());
			stripMaskChars(value);
			return value;
		} else {
			return getText();
		}
	}

	private void formatMask(@NonNull Editable value) {
		InputFilter[] inputFilters = value.getFilters();
		value.setFilters(new InputFilter[0]);

		int i = 0;
		int j = 0;
		int maskLength = 0;
		boolean treatNextCharAsLiteral = false;

		Object selection = new Object();
		value.setSpan(selection, Selection.getSelectionStart(value), Selection.getSelectionEnd(value), Spanned.SPAN_MARK_MARK);

		while (i < mask.length()) {
			if (!treatNextCharAsLiteral && isMaskChar(mask.charAt(i))) {
				if (j >= value.length()) {
					value.insert(j, placeholder);
					value.setSpan(new PlaceholderSpan(), j, j + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					++j;
				} else if (!matchMask(mask.charAt(i), value.charAt(j))) {
					value.delete(j, j + 1);
					--i;
					--maskLength;
				} else {
					++j;
				}

				++maskLength;
			} else if (!treatNextCharAsLiteral && mask.charAt(i) == ESCAPE_CHAR) {
				treatNextCharAsLiteral = true;
			} else {
				value.insert(j, String.valueOf(mask.charAt(i)));
				value.setSpan(new LiteralSpan(), j, j + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
				treatNextCharAsLiteral = false;

				++j;
				++maskLength;
			}

			++i;
		}

		while (value.length() > maskLength) {
			int pos = value.length() - 1;
			value.delete(pos, pos + 1);
		}

		Selection.setSelection(value, value.getSpanStart(selection), value.getSpanEnd(selection));
		value.removeSpan(selection);

		value.setFilters(inputFilters);
	}

	private void stripMaskChars(@NonNull Editable value) {
		PlaceholderSpan[] pSpans = value.getSpans(0, value.length(), PlaceholderSpan.class);
		LiteralSpan[] literalSpans = value.getSpans(0, value.length(), LiteralSpan.class);

		for (PlaceholderSpan s : pSpans) {
			value.delete(value.getSpanStart(s), value.getSpanEnd(s));
		}

		for (LiteralSpan s : literalSpans) {
			value.delete(value.getSpanStart(s), value.getSpanEnd(s));
		}
	}

	private boolean matchMask(char mask, char value) {
		return mask == CHARACTER_MASK
				|| (mask == ALPHA_MASK && Character.isLetter(value))
				|| (mask == NUMBER_MASK && Character.isDigit(value))
				|| (mask == ALPHANUMERIC_MASK && (Character.isDigit(value) || Character.isLetter(value)));
	}

	private boolean isMaskChar(char mask) {
		switch (mask) {
			case NUMBER_MASK:
			case ALPHA_MASK:
			case ALPHANUMERIC_MASK:
			case CHARACTER_MASK:
				return true;
		}

		return false;
	}

	private class PlaceholderSpan {
		// this class is used just to keep track of placeholders in the text
	}

	private class LiteralSpan {
		// this class is used just to keep track of literal chars in the text
	}
}
