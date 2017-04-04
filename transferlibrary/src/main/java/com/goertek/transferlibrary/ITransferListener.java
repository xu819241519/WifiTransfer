package com.goertek.transferlibrary;

/**
 * Created by landon.xu on 2017/3/10.
 */

public interface ITransferListener {

    void onReceive(byte[] data);

    void onSend(long count);

    void onConnected();

    void onDisconnected(String error);
}
