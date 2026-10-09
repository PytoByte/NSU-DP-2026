package server;

import java.nio.channels.SelectionKey;

public final class KeyGeneratorResponseCallback {

    private final SelectionKey key;
    private final SelectorLoop selectorLoop;
    private final Runnable closeAction;

    public KeyGeneratorResponseCallback(SelectionKey key, SelectorLoop selectorLoop, Runnable closeAction) {
        this.key = key;
        this.selectorLoop = selectorLoop;
        this.closeAction = closeAction;
    }

    public void onSuccess(KeyMaterial km) {
        if (!key.isValid()) {
            closeAction.run();
            return;
        }
        try {
            key.attach(km.encode());
            key.interestOps(SelectionKey.OP_WRITE);
            selectorLoop.wakeup();
        } catch (Exception e) {
            closeAction.run();
        }
    }

    public void onFailure(Throwable error) {
        error.printStackTrace();
        closeAction.run();
    }
}