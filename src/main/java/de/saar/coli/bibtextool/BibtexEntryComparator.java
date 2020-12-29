package de.saar.coli.bibtextool;

import java.util.Comparator;
import java.util.Map;

public class BibtexEntryComparator implements Comparator<Map<String, String>> {
    @Override
    public int compare(Map<String, String> o1, Map<String, String> o2) {
        int y = Integer.compare(year(o1), year(o2));
        return -y;

        /*
        if( y != 0 ) {
            return -y; // descending by year
        }

        return o1.get("author").compareTo(o2.get("author")); // then ascending by authors
        */
    }
    
    private int year(Map<String,String> entry) {
        String y = entry.get("year");

        if( y == null ) {
            return 10000;
        } else {
            return Integer.parseInt(y);
        }
    }
}
