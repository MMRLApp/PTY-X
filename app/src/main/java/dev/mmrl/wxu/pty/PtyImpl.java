package dev.mmrl.wxu.pty;

class PtyImpl implements Pty {
    private long nativeHandle;
    private final EventListener listener;

    static {
        System.loadLibrary("pty");
    }

    public PtyImpl(EventListener listener) {
        this.listener = listener;
        nativeSetEventListener(listener);
    }

    @Override
    public void start(String shell, String[] args, String[] env, int cols, int rows) {
        nativeHandle = nativeStart(shell, args, env, cols, rows);
    }

    @Override
    public void resize(int cols, int rows) {
        nativeResize(nativeHandle, cols, rows);
    }

    @Override
    public void write(byte[] data) {
        nativeWrite(nativeHandle, data);
    }

    @Override
    public void kill() {
        nativeKill(nativeHandle);
    }

    private native long nativeStart(String shell, String[] args, String[] env, int cols, int rows);
    private native void nativeResize(long handle, int cols, int rows);
    private native void nativeWrite(long handle, byte[] data);
    private native void nativeKill(long handle);
    private native void nativeSetEventListener(EventListener listener);
}