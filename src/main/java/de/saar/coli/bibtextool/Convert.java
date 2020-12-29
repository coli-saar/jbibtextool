package de.saar.coli.bibtextool;

import au.com.codeka.carrot.CarrotEngine;
import au.com.codeka.carrot.CarrotException;
import au.com.codeka.carrot.Configuration;
import au.com.codeka.carrot.bindings.MapBindings;
import au.com.codeka.carrot.resource.MemoryResourceLocator;
import au.com.codeka.carrot.resource.ResourceLocator;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.jbibtex.*;

import java.io.*;
import java.util.*;

public class Convert {
    private static BibTeXParser bibtexParser;
    private static LaTeXParser latexParser;
    private static CarrotEngine engine;

    public static void main(String[] args) throws IOException, ParseException, CarrotException {
        Args params = new Args();
        JCommander.newBuilder()
                .addObject(params)
                .build()
                .parse(args);


        bibtexParser = new BibTeXParser();
        latexParser = new LaTeXParser();

        engine = new CarrotEngine(new Configuration.Builder()
                .setResourceLocator(makeResourceLocator())
                .build()
        );



        // read bibtex
        BibTeXDatabase db = new BibTeXDatabase();
        List<Map<String,String>> entries = new ArrayList<>();

        for( String filename : params.filenames ) {
            collect(filename, db);
        }

        int numObjects = 0, numEntries = 0;
        for( BibTeXObject obj : db.getObjects() ) {
            numObjects++;
            Map<String,String> mapEntry = processBibtexObject(obj);
            if( mapEntry.get("TYPE") != null && mapEntry.get("author") != null && mapEntry.get("title") != null && mapEntry.get("year") != null ) {
                entries.add(mapEntry);
                numEntries++;
            } else {
                System.err.println("\ninvalid bibtex entry:");
                System.err.println(mapEntry);
            }
        }

        System.err.printf("Read %d bibtex entries (out of %d objects).\n", numEntries, numObjects);


        // sort database
        Collections.reverse(entries); // bottom entries in bibtex files should be shown last
        entries.sort(new BibtexEntryComparator()); // then sort descending by year

        // format entries
        List<ListEntry> formattedBibtex = new ArrayList<>();
        String previousYear = null;
        for( Map<String,String> entry : entries ) {
            if( previousYear == null || ! previousYear.equals(entry.get("year")) ) {
                formattedBibtex.add(new YearBeginsEntry(entry.get("year")));
                previousYear = entry.get("year");
            }

            formattedBibtex.add(formatBibtex(entry));
        }

        Map<String, Object> bindings = new TreeMap<>();
        bindings.put("entries", formattedBibtex);

        PrintWriter w = params.output != null ? new PrintWriter(new FileWriter(params.output)) : new PrintWriter(System.out);
        w.println(engine.process("publications.html", new MapBindings(bindings)));
    }

    public static String renderTemplate(String templateName, Map<String, Object> bindings) throws CarrotException {
        return engine.process(templateName, new MapBindings(bindings));
    }

    private static Map<String, String> processBibtexObject(BibTeXObject obj) throws ParseException {
        BibTeXEntry entry = (BibTeXEntry) obj;
        Map<String,String> ret = new HashMap<>();

        for( Map.Entry<Key,Value> kv : entry.getFields().entrySet() ) {
            String key = kv.getKey().toString().toLowerCase();
            String val = simplify(kv.getValue().toUserString());

            if( "author".equals(key.toLowerCase()) ) {
                val = formatAuthors(val);
            }

            ret.put(key, val);
        }

        ret.put("TYPE", entry.getType().toString());

        return ret;
    }

    private static String[] simplifySearchList = new String[] {
      "\\v{Z}",
        "\\'{c}",
      "{",
      "}",
            "\\\"a",
        "\\\"o",
            "\\\"u"
    };

    private static String[] simplifyReplaceList = new String[] {
        "Ž",
        "ć",
        "",
        "",
        "ä",
        "ö",
        "ü"
    };

    private static String simplify(String value) {
        return StringUtils.replaceEach(value, simplifySearchList, simplifyReplaceList);
    }

    private static void collect(String filename, BibTeXDatabase db) throws IOException, ParseException {
        if( filename.endsWith(".txt") ) {
            System.err.printf("Reading file list %s ...\n", filename);

            BufferedReader f = new BufferedReader(new FileReader(filename));
            for (String line = f.readLine(); line != null; line = f.readLine()) {
                line = line.trim();
                if (!"".equals(line) && !line.startsWith("#")) {
                    collect(line, db);
                }
            }
        } else if( filename.endsWith(".bib")) {
            System.err.printf(" - reading BibTeX file %s ...\n", filename);

            Reader r = new FileReader(filename);
            BibTeXDatabase local = bibtexParser.parse(r);
            for (BibTeXObject entry : local.getObjects()) {
                db.addObject(entry);
            }
        }
    }

    private static ListEntry formatBibtex(Map<String,String> entry) throws CarrotException {
        String type = entry.get("TYPE").toLowerCase();

        if( "article".equals(type) ) {
            entry.put("issueData", makeIssueData(entry));
        }

        try {
            return new FormattedBibtexEntry(engine.process(type + ".html", new MapBindings(Collections.unmodifiableMap(entry))));
        } catch(Exception e) {
            System.err.println("   ** unknown type: " + type);
            return new FormattedBibtexEntry("<p>(error)</p>");
        }
    }

    private static String makeIssueData(Map<String, String> entry) {
        boolean empty = true;
        String volume = entry.get("volume");
        String number = entry.get("number");
        String pages = entry.get("pages");
        String s1 = "", s2 = "";

        if( volume != null ) {
            empty = false;

            if( number != null ) {
                s1 = String.format("%s(%s)", volume, number);
            } else {
                s1 = volume;
            }
        }

        if( pages != null ) {
            if( empty ) {
                s2 = pages;
            } else {
                s2 = ":" + pages;
            }

            empty = false;
        }

        return (empty ? "" : ", ") + (s1 + s2).replaceAll("--", "-");
    }

    private static String formatOneAuthor(String author) {
        if( author.contains(",")) {
            String[] parts = author.split("\\s*,\\s*");
            return parts[1] + " " + parts[0];
        } else {
            return author;
        }
    }

    private static String formatAuthors(String authors) {
        String[] names = authors.split("\\s+and\\s+");

        for( int i = 0; i < names.length; i++ ) {
            names[i] = formatOneAuthor(names[i]);
        }

        switch(names.length) {
            case 1:
                return names[0];

            case 2:
                return String.format("%s and %s", names[0], names[1]);

            default:
                StringBuilder buf = new StringBuilder();
                for( int i = 0; i < names.length; i++ ) {
                    if( i == names.length - 1) {
                        buf.append(", and ");
                    } else if( i > 0 ) {
                        buf.append(", ");
                    }

                    buf.append(names[i]);
                }
                return buf.toString();
        }
    }




    private static ResourceLocator.Builder makeResourceLocator() {
        MemoryResourceLocator.Builder ret = new MemoryResourceLocator.Builder();
        ret.add("publications.html", slurp("/carrot/publications.html"));
        ret.add("inproceedings.html", slurp("/carrot/inproceedings.html"));
        ret.add("article.html", slurp("/carrot/article.html"));
        ret.add("incollection.html", slurp("/carrot/incollection.html"));
        ret.add("year.html", slurp("/carrot/year.html"));
        return ret;
    }

    /**
     * Reads the entire Reader into a string and returns it.
     *
     * @param reader
     * @return
     */
    private static String slurp(Reader reader) {
        try {
            char[] arr = new char[8 * 1024];
            StringBuilder buffer = new StringBuilder();
            int numCharsRead;
            while ((numCharsRead = reader.read(arr, 0, arr.length)) != -1) {
                buffer.append(arr, 0, numCharsRead);
            }
            reader.close();

            return buffer.toString();
        } catch (IOException e) {
            return null;
        }
    }

    private static String slurp(String resourceName) {
        Reader r = new InputStreamReader(Convert.class.getResourceAsStream(resourceName));
        return slurp(r);
    }


    public static class Args {
        @Parameter
        private List<String> filenames = new ArrayList<>();

        @Parameter(names = { "-o", "--output" }, description = "File to which the generated HTML should be written.")
        private String output = null;
    }
}
