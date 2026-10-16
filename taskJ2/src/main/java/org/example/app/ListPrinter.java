package org.example.app;

import org.example.common.SyncStringList;

public final class ListPrinter {

    public void print(SyncStringList list) {
        System.out.println("---");
        for (String value : list) {
            System.out.println(value);
        }
        System.out.println("---");
    }
}
