package com.auto.trigger;
interface IShellService {
    void destroy() = 16777114;
    int exec(String cmd) = 1;
    String info() = 2;
    boolean inj(int action, int x, int y) = 3;
    void startEvents() = 4;
    String drainEvents() = 5;
    void stopEvents() = 6;
    boolean injM(int slot, int action, int x, int y) = 8;
}
