package com.alfread.statusvault;

import java.io.File;

public class StatusItem {
    public final File file;
    public boolean selected;

    public StatusItem(File file) {
        this.file = file;
    }
}
