package com.goertek.transferlibrary;

import android.os.Message;
import android.text.TextUtils;

import com.goertek.transferlibrary.utils.LogUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

/**
 * Created by landon.xu on 2017/2/20.
 */

public class MultiTransfer implements ITransfer {

    private static final String TAG = "MultiTransfer";

    private String mIP;

    private int mPort;

    private volatile boolean mReading = false;

    private volatile boolean mWriting = false;

    private BufferDataQueue mBufferDataQueue;

    private TransferManager.TransferHandler mEventHandler;

    private volatile MulticastSocket mSocket;

    private volatile long mReceiveCount;

    private volatile long mSendCount;

    private boolean mIsBroadcast = false;

    private Config mConfig;

    private Thread mReadThread;

    private Thread mWriteThread;

    public MultiTransfer(String ip, int port, TransferManager.TransferHandler handler, boolean isBroadcast, Config config, BufferDataQueue queue) {
        LogUtils.d(TAG, "MultiTransfer");
        mConfig = config;
        mReading = false;
        mWriting = false;
        mBufferDataQueue = queue;
        mIP = ip;
        mPort = port;
        mEventHandler = handler;
        mReceiveCount = 0;
        mSendCount = 0;
        mIsBroadcast = isBroadcast;
        initSocket();
    }

    private void initSocket() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LogUtils.d(TAG, "initSocket");
                    mSocket = new MulticastSocket(mPort);
                    mSocket.setLoopbackMode(false);
                    InetAddress inetAddress = InetAddress.getByName(mIP);
                    if (!mIsBroadcast) {
                        mSocket.joinGroup(inetAddress);
                    }
                    startRead();
                    startWrite();
                } catch (IOException e) {
                    mSocket = null;
                    e.printStackTrace();
                    if (mEventHandler != null) {
                        Message msg = mEventHandler.obtainMessage(Event.OTHER_ERROR);
                        msg.obj = e.getMessage();
                        msg.sendToTarget();
                    }
                }
            }
        }).start();
    }

    private Thread getReadThread() {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LogUtils.d(TAG, "read thread");
                    while (mReading) {
                        if (mSocket != null) {
                            LogUtils.d(TAG, "read data");
                            final DatagramPacket packet = new DatagramPacket(new byte[mConfig.getPacketMaxLength()], mConfig.getPacketMaxLength());
                            mSocket.receive(packet);
                            byte[] data = TransferProtocol.parseTransferPack(packet.getData());
                            if (data != null) {
                                mReceiveCount++;
                                Message msg = mEventHandler.obtainMessage(Event.DATA_RECEIVE_EVENT);
                                msg.obj = data;
                                msg.sendToTarget();
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Message msg = mEventHandler.obtainMessage(Event.OTHER_ERROR);
                    msg.obj = e.getMessage();
                    msg.sendToTarget();
                }
            }
        });
    }


    private Thread getWriteThread() {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (mWriting) {
                        LogUtils.d(TAG, "write thread");
                        if (mSocket != null) {
                            String msgString = mBufferDataQueue.get();
                            if (!TextUtils.isEmpty(msgString)) {
                                byte[] data = msgString.getBytes(Config.ENCODE_TYPE);
                                data = TransferProtocol.packTransferData(data);
                                LogUtils.d(TAG, "write data");
                                DatagramPacket packet;
                                InetAddress address = InetAddress.getByName(mIP);
                                packet = new DatagramPacket(data, data.length, address, mPort);
                                mSocket.send(packet);
                                mSendCount++;
                                Message msg = mEventHandler.obtainMessage(Event.DATA_SEND_EVENT);
                                msg.obj = mSendCount;
                                msg.sendToTarget();
                                Thread.sleep(mConfig.getInterval());
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Message msg = mEventHandler.obtainMessage(Event.OTHER_ERROR);
                    msg.obj = e.getMessage();
                    msg.sendToTarget();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    private void startRead() {
        if (mSocket != null) {
            mReading = true;
            if (mReadThread == null) {
                mReadThread = getReadThread();
            }
            mReadThread.start();
        }
    }


    private void startWrite() {
        if (mSocket != null) {
            mWriting = true;
            if (mWriteThread == null) {
                mWriteThread = getWriteThread();
            }
            mWriteThread.start();
        }
    }


    private void stopRead() {
        mReading = false;
        if (mReadThread != null) {
            mReadThread.interrupt();
            mReadThread = null;
        }

    }


    private void stopWrite() {
        mWriting = false;
        if (mWriteThread != null) {
            mWriteThread.interrupt();
            mWriteThread = null;
        }
    }

    @Override
    public void destroy() {
        mWriting = false;
        mReading = false;
        new Thread(new Runnable() {
            @Override
            public void run() {

                if (mSocket != null) {
                    if (!mIsBroadcast) {
                        try {
                            InetAddress inetAddress = InetAddress.getByName(mIP);
                            mSocket.leaveGroup(inetAddress);
                            mSocket.close();
                        } catch (UnknownHostException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        mSocket.close();
                    }
                }
                if (mWriteThread != null) {
                    mWriteThread.interrupt();
                    mWriteThread = null;
                }
                if (mReadThread != null) {
                    mReadThread.interrupt();
                    mReadThread = null;
                }
            }
        }).start();
    }
}
