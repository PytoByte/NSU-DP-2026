package org.example.list;

import org.example.common.SyncStringList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class SynchronizedArrayList implements SyncStringList {

    private final List<String> list = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void addFirst(String value) {
        list.add(0, value);
    }

    @Override
    public Iterator<String> iterator() {
        synchronized (list) {
            return new ArrayList<>(list).iterator();
        }
    }

    public int size() {
        return list.size();
    }

    public String get(int index) {
        return list.get(index);
    }

    public void swap(int index1, int index2) {
        Collections.swap(list, index1, index2);
    }
}
