package com.goertek.transferlibrary;


import android.os.Handler;
import android.os.Message;

import com.goertek.transferlibrary.utils.LogUtils;

import java.lang.ref.WeakReference;

/**
 * Created by landon.xu on 2017/2/18.
 */

public class TransferManager {

    //传输类
    private ITransfer mTransfer;
    //tcp传输
    public static final int TCP_TRANSFER = 0;
    //udp传输
    public static final int UDP_TRANSFER = 1;

    private TransferHandler mTransferHandler = new TransferHandler(this);

    private ITransferListener mTransferListener;

    private Config mConfig = new Config();

    private BufferDataQueue mBufferDataQueue;

    private static final String DEFAULT_BROADCAST_IP = "224.1.1.1";

    private static final int DEFAULT_PORT = 10087;

    static class TransferHandler extends Handler {
        private static final String TAG = "TransferHandler";
        private WeakReference<TransferManager> mManagerWeakReference;

        TransferHandler(TransferManager manager) {
            mManagerWeakReference = new WeakReference<>(manager);
        }

        @Override
        public void handleMessage(Message msg) {
            TransferManager manager = mManagerWeakReference.get();
            if (manager != null && manager.mTransferListener != null) {
                switch (msg.what) {
                    case Event.BIND_ERROR:
                        manager.mTransferListener.onDisconnected("端口绑定失败，请尝试换端口");
                        //Toast.makeText(manager., "端口绑定失败，请尝试换端口", Toast.LENGTH_SHORT).show();
                        //activity.setSendingState(false);
                        break;
                    case Event.OTHER_ERROR:
                        //Toast.makeText(activity, (String) msg.obj, Toast.LENGTH_SHORT).show();
                        //activity.setSendingState(false);
                        manager.mTransferListener.onDisconnected((String) msg.obj);
                        break;
                    case Event.DATA_RECEIVE_EVENT:
                        LogUtils.d(TAG, "data receive");
                        //activity.mReceiveCount.setText(String.valueOf((long)msg.obj));
                        manager.mTransferListener.onReceive((byte[]) msg.obj);
                        break;
                    case Event.DATA_SEND_EVENT:
                        //activity.mSendCount.setText(String.valueOf((long)msg.obj));
                        manager.mTransferListener.onSend((long) msg.obj);
                        break;
                    case Event.CONNECT_EVENT:
                        manager.mTransferListener.onConnected();
                        break;
                    case Event.DISCONNECT_EVENT:
                        manager.mTransferListener.onDisconnected(null);
                        break;
                    default:
                        break;
                }
            }

        }
    }

    public TransferManager(BufferDataQueue bufferDataQueue) {
        this.mBufferDataQueue = bufferDataQueue;
    }


    /**
     * 初始化一对一传输
     *
     * @param transferType
     * @param ip
     * @param listener
     */
    public void startClient(int transferType, String ip, ITransferListener listener) {
        if (mBufferDataQueue == null) {
            return;
        }
        if (mTransfer != null) {
            mTransfer.destroy();
            mTransfer = null;
        }
        mTransferListener = listener;
        if (transferType == UDP_TRANSFER) {
            mTransfer = new UdpTransfer(ip, DEFAULT_PORT, false, mTransferHandler, mConfig, mBufferDataQueue);
        } else if (transferType == TCP_TRANSFER) {
            mTransfer = new TcpTransfer(ip, DEFAULT_PORT, false, mTransferHandler, mConfig, mBufferDataQueue);
        }
    }


    /**
     * 初始化一对一传输
     *
     * @param transferType
     * @param listener
     */
    public void startServer(int transferType, ITransferListener listener) {
        if (mBufferDataQueue == null) {
            return;
        }
        if (mTransfer != null) {
            mTransfer.destroy();
            mTransfer = null;
        }
        mTransferListener = listener;
        if (transferType == UDP_TRANSFER) {
            mTransfer = new UdpTransfer(null, DEFAULT_PORT, true, mTransferHandler, mConfig, mBufferDataQueue);
        } else if (transferType == TCP_TRANSFER) {
            mTransfer = new TcpTransfer(null, DEFAULT_PORT, true, mTransferHandler, mConfig, mBufferDataQueue);
        }
    }



    /**
     * 初始化多对多传输
     *
     * @param listener
     */
    public void startMultiTransfer(ITransferListener listener) {
        if (mTransfer != null) {
            mTransfer.destroy();
            mTransfer = null;
        }
        if (mBufferDataQueue == null) {
            return;
        }
        mTransferListener = listener;
        mTransfer = new MultiTransfer(DEFAULT_BROADCAST_IP, DEFAULT_PORT, mTransferHandler, false, mConfig, mBufferDataQueue);
    }

    /**
     * 初始化一对一传输
     *
     * @param transferType
     * @param ip
     * @param port
     * @param isServer
     * @param listener
     */
    public void startSingleTransfer(int transferType, String ip, int port, boolean isServer, ITransferListener listener) {
        if (mBufferDataQueue == null) {
            return;
        }
        if (mTransfer != null) {
            mTransfer.destroy();
            mTransfer = null;
        }
        mTransferListener = listener;
        if (transferType == UDP_TRANSFER) {
            mTransfer = new UdpTransfer(ip, port, isServer, mTransferHandler, mConfig, mBufferDataQueue);
        } else if (transferType == TCP_TRANSFER) {
            mTransfer = new TcpTransfer(ip, port, isServer, mTransferHandler, mConfig, mBufferDataQueue);
        }
    }

    /**
     * 初始化多对多传输
     *
     * @param ip
     * @param port
     * @param isBroadcast
     * @param listener
     */
    public void startMultiTransfer(String ip, int port, boolean isBroadcast, ITransferListener listener) {
        if (mTransfer != null) {
            mTransfer.destroy();
            mTransfer = null;
        }
        if (mBufferDataQueue == null) {
            return;
        }
        mTransferListener = listener;
        mTransfer = new MultiTransfer(ip, port, mTransferHandler, isBroadcast, mConfig, mBufferDataQueue);
    }




    public void destroy() {
        if (mTransfer != null) {
            mTransfer.destroy();
        }
        mTransfer = null;
    }


    public void setConfig(Config config) {
        if (config != null) {
            this.mConfig = config;
        }
    }

}
