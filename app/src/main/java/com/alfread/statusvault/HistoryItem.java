package com.alfread.statusvault;

public class HistoryItem {
    public String name;
    public String destination;
    public long size;
    public long timestamp;
    public String mode;
    public String result;

    public HistoryItem(String name, String destination, long size, long timestamp, String mode, String result) {
        this.name = name;
        this.destination = destination;
        this.size = size;
        this.timestamp = timestamp;
        this.mode = mode;
        this.result = result;
    }
}
