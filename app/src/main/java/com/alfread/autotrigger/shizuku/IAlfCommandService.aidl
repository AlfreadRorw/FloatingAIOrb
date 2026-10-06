package com.alfread.autotrigger.shizuku;

interface IAlfCommandService {
    String run(String command);
    void startRecording(String device, IAlfRecordCallback callback);
    void stopRecording();
}
