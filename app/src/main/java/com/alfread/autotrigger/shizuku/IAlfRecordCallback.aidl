package com.alfread.autotrigger.shizuku;

interface IAlfRecordCallback {
    void onLine(String line);
    void onStopped(String reason);
}
