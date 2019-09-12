package io.edgebroker.flowdirector.docgen;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Generator {
    private static final String[] LINKREFHEADER = new String[]{"Direction", "Name", "Type", "Mandatory"};
    private static final String[] PROPHEADER = new String[]{"Name", "Description", "Mandatory", "Type", "Min", "Max", "Default", "Choices"};
    private static JsonObject load(File f) throws Exception {
        if (!f.exists())
            throw new Exception("File not found: " + f.getName());
        BufferedReader reader = new BufferedReader(new FileReader(f));
        char[] buffer = new char[(int) f.length()];
        reader.read(buffer);
        reader.close();
        return new JsonObject(new String(buffer));
    }

    private static File getDescriptor(File dir) {
        if (dir.isDirectory()) {
            File[] subs = dir.listFiles();
            if (subs != null) {
                for (File sub : subs) {
                    if (!sub.isDirectory() && sub.getName().equals("descriptor.json"))
                        return sub;
                }
            }
        }
        return null;
    }

    private static void createTableRows(List<String> elements, JsonArray input, JsonArray output) {
        if (input != null) {
            for (int i = 0; i < input.size(); i++) {
                JsonObject ele = input.getJsonObject(i);
                boolean mandatory = false;
                if (ele.getBoolean("mandatory") != null)
                    mandatory = ele.getBoolean("mandatory");
                tablerow(new String[]{"Input", ele.getString("name"), ele.getString("type"), mandatory ? "yes" : "no"}, i % 2 == 0, elements);
            }
        }
        if (output != null) {
            for (int i = 0; i < output.size(); i++) {
                JsonObject ele = output.getJsonObject(i);
                boolean mandatory = false;
                if (ele.getBoolean("mandatory") != null)
                    mandatory = ele.getBoolean("mandatory");
                tablerow(new String[]{"Output", ele.getString("name"), ele.getString("type"), mandatory ? "yes" : "no"}, i % 2 == 0, elements);
            }
        }
    }

    private static void tablestart(String[] header, List<String> elements) {
        elements.add("<table>");
        elements.add("<thead>");
        elements.add("<tr class=\"header\">");
        for (String h : header) {
            elements.add("<th>"+h+"</th>");
        }
        elements.add("</tr>");
        elements.add("</thead>");
        elements.add("<tbody>");
    }

    private static void tablerow(String[] values, boolean even, List<String> elements) {
        elements.add("<tr class=\""+(even?"even":"odd")+"\">");
        for (String v : values) {
            elements.add("<th>"+v+"</th>");
        }
        elements.add("</tr>");
    }

    private static void tableend(List<String> elements) {
        elements.add("</tbody>");
        elements.add("</table>\n");
    }

    private static void paragraph(String text, List<String> elements) {
        if (!text.trim().endsWith("."))
            text += ".";
        elements.add(text + "\n");
    }

    private static void headline(int level, String text, List<String> elements) {
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < level; i++)
            prefix.append("#");
        elements.add(prefix.toString() + " " + text + "\n");
    }

    private static void component(int level, String name, File descriptorFile, List<String> elements) throws Exception {
        headline(level + 1, name, elements);
        JsonObject descriptor = load(descriptorFile);
        paragraph(descriptor.getString("description"), elements);
        JsonObject links = descriptor.getJsonObject("links");
        if (links != null) {
            JsonArray input = links.getJsonArray("input");
            JsonArray output = links.getJsonArray("output");
            if (input != null || output != null) {
                headline(level + 2, "Links", elements);
                tablestart(LINKREFHEADER, elements);
                createTableRows(elements, input, output);
                tableend(elements);
            }
        }
        JsonObject refs = descriptor.getJsonObject("refs");
        if (refs != null) {
            JsonArray input = refs.getJsonArray("input");
            JsonArray output = refs.getJsonArray("output");
            if (input != null || output != null) {
                headline(level + 2, "References", elements);
                tablestart(LINKREFHEADER, elements);
                createTableRows(elements, input, output);
                tableend(elements);
            }
        }
        JsonArray props = descriptor.getJsonArray("properties");
        if (props != null && props.size() > 0){
            headline(level + 2, "Properties", elements);
            tablestart(PROPHEADER, elements);
            for (int i=0;i<props.size();i++){
                JsonObject ele = props.getJsonObject(i);
                String pname = ele.getString("label");
                String pdescription = ele.getString("description");
                String ptype = ele.getString("type");
                boolean mandatory = false;
                if (ele.getBoolean("mandatory") != null)
                    mandatory = ele.getBoolean("mandatory");
                String min = "";
                if (ele.getInteger("min") != null)
                    min = String.valueOf(ele.getInteger("min"));
                String max = "";
                if (ele.getInteger("max") != null)
                    max = String.valueOf(ele.getInteger("max"));
                String pdefault = "";
                if (ele.getValue("default") != null)
                    pdefault = ele.getValue("default").toString();
                String choice = "";
                if (ptype.equals("choice"))
                    choice = ele.getJsonArray("choice").encode()
                            .replaceAll(",", ", ")
                            .replaceAll("\"", "")
                            .replaceAll("\\[", "")
                            .replaceAll("\\]", "");
                tablerow(new String[]{pname, pdescription,mandatory?"yes":"no",ptype,min, max, pdefault, choice}, i%2==0, elements);
            }
            tableend(elements);
        }
    }

    private static void section(int level, File dir, List<String> elements) throws Exception {
        headline(level + 1, dir.getName(), elements);
        checkSubdirs(level, dir, elements);
    }

    private static void checkSubdirs(int level, File dir, List<String> elements) throws Exception {
        File[] subdirs = dir.listFiles();
        if (subdirs != null) {
            Arrays.sort(subdirs);
            for (File subdir : subdirs) {
                File descriptor = getDescriptor(subdir);
                if (descriptor != null)
                    component(level + 1, subdir.getName(), descriptor, elements);
                else if (subdir.isDirectory())
                    section(level + 1, subdir, elements);
            }
        }
    }

    private static void pageheader(String id, String title, List<String> elements) {
        elements.add("---");
        elements.add("id: " + id);
        elements.add("title: " + title);
        elements.add("---");
    }

    private static void page(File dir, String id, String title, List<String> elements) throws Exception {
        int level = 0;
        pageheader(id, title, elements);
        checkSubdirs(level, dir, elements);
    }

    private static void flush(File file, List<String> elements) throws Exception {
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        for (String element : elements) {
            writer.write(element);
            writer.newLine();
        }
        writer.flush();
        writer.close();
    }

    public static void main(String[] args) throws Exception {
        String inputDir = args[0];
        String outputDir = args[1];
        String siteBarsFile = args[2];
        File[] libs = new File(inputDir).listFiles();
        if (libs == null)
            throw new Exception("No libs found here: " + inputDir);
        Arrays.sort(libs);
        for (File lib : libs) {
            if (!lib.getName().equals("Subflows") && !lib.getName().startsWith(".DS_Store") && lib.isDirectory()) {
                List<String> elements = new ArrayList<>();
                String name = lib.getName().replaceAll(" ", "_") + "_comp";
                page(lib, name + ".md", lib.getName() + " Components", elements);
                flush(new File(outputDir + File.separatorChar + name + ".md"), elements);
            }
        }
    }
}
