package de.saar.coli.bibtextool;

import au.com.codeka.carrot.CarrotException;

import java.util.HashMap;
import java.util.Map;

public class YearBeginsEntry implements ListEntry {
    private String y;

    public YearBeginsEntry(String y) throws CarrotException {
        Map<String,Object> bindings = new HashMap<>();
        bindings.put("year", y);
        this.y = Convert.renderTemplate("year.html", bindings);
    }

    @Override
    public String getString() {
        return y;
    }
}
