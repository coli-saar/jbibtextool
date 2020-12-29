package de.saar.coli.bibtextool;

public class FormattedBibtexEntry implements ListEntry {
    private String s;

    public FormattedBibtexEntry(String s) {
        this.s = s;
    }

    @Override
    public String getString() {
        return s;
    }
}
