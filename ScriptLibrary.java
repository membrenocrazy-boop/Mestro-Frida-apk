package com.mestro.ac;
import java.util.ArrayList;
import java.util.List;
public class ScriptLibrary {
    public static class Entry {
        public String name, path, command;
        public boolean isFolder;
        public Entry(String n, String p, String c, boolean f){name=n;path=p;command=c;isFolder=f;}
        public String toString(){return name + (isFolder?" [folder]":"");}
    }
    private static final List<Entry> list = new ArrayList<>();
    public static void add(Entry e){list.add(e);}
    public static List<Entry> getAll(){return list;}
}