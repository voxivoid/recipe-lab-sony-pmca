package com.voxivoid.recipelab;

/**
 * The name editor's state, without the camera: the text, the on-screen keyboard and where its highlight is. There is
 * no keyboard, touchscreen or text entry on these bodies, so a name is typed key by key from a grid drawn on screen
 * ({@link KeyboardView}), on keys every body has: the four-way moves the highlight, the wheel and the dial walk it
 * cell by cell, centre types the highlighted cell, trash deletes the last character, MENU cancels.
 *
 * A new recipe opens on a placeholder name (Untitled, Untitled 2 …) that the first character typed replaces, so nobody
 * has to delete it first; OK on the untouched placeholder keeps it. A rename opens on the real name, to be edited.
 *
 * No android.* import may appear here (tools/test.sh).
 */
final class NameEntry {
    /** the cells that are not a character: shift, delete, done */
    static final char SHIFT = '\u0001', BACK = '\b', OK = '\n';
    /** the keyboard, row by row; a cell is a character, a space, or one of the keys above */
    static final String[] GRID = { "ABCDEFGHIJKLM", "NOPQRSTUVWXYZ", "0123456789-.'", "\u0001()&+ \b\n" };
    /** what a press did */
    static final int NONE = 0, TYPED = 1, DONE = 2;

    private final StringBuilder text = new StringBuilder();
    private boolean placeholder, upper;
    private int row = 0, col = 0;
    private String error;

    private NameEntry(String start, boolean placeholder) {
        text.append(start);
        this.placeholder = placeholder;
        upper = placeholder || start.isEmpty();
    }

    /** a new recipe: the placeholder shows until the first character replaces it */
    static NameEntry blank(String placeholder) { return new NameEntry(placeholder, true); }

    /** a rename: the name as it is, to be edited */
    static NameEntry of(String name) { return new NameEntry(name, false); }

    /** what the field shows */
    String shown() { return text.toString(); }
    /** whether the field still shows the placeholder, which the view draws dimmed */
    boolean isPlaceholder() { return placeholder; }
    /** the name OK takes */
    String value() { return text.toString().trim(); }
    /** whether letters type in capitals: on for the first letter of a name, after that only when shifted */
    boolean upper() { return upper; }
    int row() { return row; }
    int col() { return col; }
    /** why the last OK was refused, shown under the field until the next key; null when nothing was */
    String error() { return error; }
    void setError(String e) { error = e; }

    static int rows() { return GRID.length; }
    static int cols(int r) { return GRID[r].length(); }
    static char cell(int r, int c) { return GRID[r].charAt(c); }

    /** the highlighted cell */
    char current() { return cell(row, col); }

    /**
     * The four-way: left / right move inside a row, up / down to the row above / below — onto the cell that sits over
     * the same part of the screen, as the rows have different numbers of cells. Everything wraps.
     */
    void move(int dRow, int dCol) {
        error = null;
        if (dCol != 0) col = (col + cols(row) + dCol) % cols(row);
        if (dRow != 0) {
            int to = (row + rows() + dRow) % rows();
            col = Math.min(cols(to) - 1, (int) ((col + 0.5f) * cols(to) / cols(row)));
            row = to;
        }
    }

    /** the wheel and the dial: the next / previous cell, reading order, wrapping from the last cell to the first */
    void step(int dir) {
        error = null;
        int n = 0, at = 0;
        for (int r = 0; r < rows(); r++) { if (r == row) at = n + col; n += cols(r); }
        at = (at + n + dir) % n;
        for (int r = 0; r < rows(); r++) { if (at < cols(r)) { row = r; col = at; return; } at -= cols(r); }
    }

    /** centre on the highlighted cell */
    int press() {
        error = null;
        char c = current();
        if (c == OK) return DONE;
        if (c == BACK) { backspace(); return TYPED; }
        if (c == SHIFT) { upper = !upper; return TYPED; }
        if (placeholder) { text.setLength(0); placeholder = false; }
        if (text.length() >= CustomRecipes.NAME_MAX) return NONE;
        boolean letter = Character.isLetter(c);
        text.append(letter && !upper ? Character.toLowerCase(c) : c);
        if (letter) upper = false;                                  // a capital for the first letter, then lower case
        return TYPED;
    }

    /** trash, or the delete cell: the last character goes; on the placeholder, all of it */
    void backspace() {
        error = null;
        if (placeholder) { text.setLength(0); placeholder = false; }
        else if (text.length() > 0) text.setLength(text.length() - 1);
        if (text.length() == 0) upper = true;
    }
}
