package com.goertek.transferlibrary;

import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.goertek.transferlibrary.utils.LogUtils;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

/**
 * Created by landon.xu on 2017/2/18.
 */

public class UdpTransfer implements ITransfer {

    private static final String TAG = "UdpTransfer";

    private volatile String mIP;

    private int mPort;

    private volatile boolean mReading = false;

    private volatile boolean mWriting = false;

    private BufferDataQueue mBufferDataQueue;

    private volatile DatagramSocket mSocket;

    private TransferManager.TransferHandler mEventHandler;

    private volatile long mReceiveCount;

    private volatile long mSendCount;

    private Config mConfig;

    private Thread mWriteThread;

    private Thread mReadThread;

    private volatile boolean mFirstConnected = false;


    public UdpTransfer(String ip, int port, boolean isServer, TransferManager.TransferHandler handler, Config config, BufferDataQueue queue) {
        LogUtils.d(TAG, "udp transfer");
        mConfig = config;
        mReading = false;
        mWriting = false;
        mFirstConnected = false;
        mIP = ip;
        mPort = port;
        mBufferDataQueue = queue;
        mEventHandler = handler;
        initSocket();
        mReceiveCount = 0;
        mSendCount = 0;
    }

    private void initSocket() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mSocket = new DatagramSocket(mPort);
                    mSocket.setReuseAddress(true);
                    startRead();
                    startWrite();
                } catch (BindException e) {
                    Log.d(TAG, "bindexception");
                    if (mEventHandler != null) {
                        Message msg = mEventHandler.obtainMessage(Event.BIND_ERROR);
                        msg.sendToTarget();
                    }
                    e.printStackTrace();
                } catch (SocketException e) {
                    e.printStackTrace();
                    if (mEventHandler != null) {
                        Message msg = mEventHandler.obtainMessage(Event.OTHER_ERROR);
                        msg.obj = e.getMessage();
                        msg.sendToTarget();
                    }
                } catch (IllegalArgumentException e) {
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
                    mFirstConnected = false;
                    while (mReading) {
                        if (mSocket != null) {
                            DatagramPacket packet = new DatagramPacket(new byte[mConfig.getPacketMaxLength()], mConfig.getPacketMaxLength());
                            mSocket.receive(packet);
                            //结束发送
                            if (TransferProtocol.parseDisconnectPack(packet.getData())) {
                                LogUtils.d(TAG, "结束发送");
                                mReading = false;
                                Message msg = mEventHandler.obtainMessage(Event.DISCONNECT_EVENT);
                                msg.sendToTarget();
                                destroy();
                                break;
                            }
                            //正常接收数据
                            else {
                                byte[] data = TransferProtocol.parseTransferPack(packet.getData());
                                if (data != null) {
                                    if (!mFirstConnected) {
                                        mFirstConnected = true;
                                        Message msg = mEventHandler.obtainMessage(Event.CONNECT_EVENT);
                                        msg.obj = packet.getAddress().getHostAddress();
                                        msg.sendToTarget();
                                    }
                                    if (TextUtils.isEmpty(mIP)) {
                                        mIP = packet.getAddress().getHostAddress();
                                    }
                                    mReceiveCount++;
                                    Message msg = mEventHandler.obtainMessage(Event.DATA_RECEIVE_EVENT);
                                    msg.obj = data;
                                    msg.sendToTarget();
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
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
                        if (mSocket != null && !TextUtils.isEmpty(mIP)) {
                            String msgString = mBufferDataQueue.get();
                            if (!TextUtils.isEmpty(msgString)) {
                                byte[] data = msgString.getBytes(Config.ENCODE_TYPE);
                                data = TransferProtocol.packTransferData(data);
                                DatagramPacket packet = new DatagramPacket(data, data.length, new InetSocketAddress(mIP, mPort));
                                mSocket.send(packet);
                                mSendCount++;
                                Message msg = mEventHandler.obtainMessage(Event.DATA_SEND_EVENT);
                                msg.obj = mSendCount;
                                msg.sendToTarget();
                                Thread.sleep(mConfig.getInterval());
                            }
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    private void startRead() {
        if (mSocket != null) {
            if (mReadThread == null) {
                mReadThread = getReadThread();
            }
            mReading = true;
            mReadThread.start();
        }
    }


    private void startWrite() {
        if (mSocket != null) {
            if (mWriteThread == null) {
                mWriteThread = getWriteThread();
            }
            mWriting = true;
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
        mFirstConnected = false;
        if (mSocket != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        LogUtils.d(TAG, "onStop run");
                        byte[] endData = TransferProtocol.packDisconnectPack();
                        if(mIP != null) {
                            DatagramPacket packet = new DatagramPacket(endData, endData.length, new InetSocketAddress(mIP, mPort));
                            if (mSocket != null) {
                                mSocket.send(packet);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (mSocket != null) {
                            mSocket.close();
                            mSocket = null;
                        }
                        if (mReadThread != null) {
                            mReadThread.interrupt();
                            mReadThread = null;
                        }
                        if (mWriteThread != null) {
                            mWriteThread.interrupt();
                            mWriteThread = null;
                        }
                    }
                }
            }).start();
        }
    }
}
