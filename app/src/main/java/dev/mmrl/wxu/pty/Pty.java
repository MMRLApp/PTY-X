package dev.mmrl.wxu.pty;

public interface Pty {
    void start(String shell, String[] args, String[] env, int cols, int rows);
    void resize(int cols, int rows);
    void write(byte[] data);
    void kill();

    interface EventListener {
        void onData(byte[] data);
        void onExit(int exitCode);
    }

    static Pty create(EventListener listener) {
        return new PtyImpl(listener);
    }
}
