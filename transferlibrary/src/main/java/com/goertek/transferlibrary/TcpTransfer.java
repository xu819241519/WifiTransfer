package com.goertek.transferlibrary;

import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.goertek.transferlibrary.utils.LogUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;


/**
 * Created by landon.xu on 2017/2/18.
 */

public class TcpTransfer implements ITransfer {

    private static final String TAG = "TcpTransfer";

    private String mIP;

    private int mPort;

    private ServerSocket mServerSocket;

    private volatile Socket mClientSocket;

    private volatile boolean mReading = false;

    private volatile boolean mWriting = false;

    private volatile boolean mAcceptClient = true;

    private static final int VERIFY_TIME_OUT = 1000;

    private BufferDataQueue mBufferDataQueue;

    private TransferManager.TransferHandler mEventHandler;

    private volatile long mReceiveCount;

    private volatile long mSendCount;

    private Config mConfig;

    private Thread mWriteThread;

    private Thread mReadThread;

    public TcpTransfer(String ip, int port, boolean isServer, TransferManager.TransferHandler handler, Config config, BufferDataQueue queue) {
        LogUtils.d(TAG, "tcp transfer");
        mConfig = config;
        mReading = false;
        mWriting = false;
        mIP = ip;
        mPort = port;
        mBufferDataQueue = queue;
        mEventHandler = handler;
        initSocket(isServer);
        mReceiveCount = 0;
        mSendCount = 0;
    }

    /**
     * 初始化socket
     *
     * @param isServer 是否作为server端启动
     */
    private void initSocket(boolean isServer) {
        if (isServer) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        mServerSocket = new ServerSocket(mPort);
                        //验证身份
                        byte[] verifyData = new byte[TransferProtocol.packConnectionData().length];
                        while (mAcceptClient) {
                            try {
                                LogUtils.d(TAG, "start server");
                                mServerSocket.setReuseAddress(true);
                                Socket tempSocket = mServerSocket.accept();
                                if (!mReading) {
                                    mClientSocket = tempSocket;
                                    mClientSocket.setSoTimeout(VERIFY_TIME_OUT);
                                    try {
                                        verifyData[0] = 0;
                                        mClientSocket.getInputStream().read(verifyData);
                                        mClientSocket.setSoTimeout(0);
                                        if (TransferProtocol.parseConnectionPack(verifyData)) {
                                            //发送验证成功消息
                                            mClientSocket.getOutputStream().write(TransferProtocol.packConnectionData());
                                            mClientSocket.getOutputStream().flush();
                                            Message msg = mEventHandler.obtainMessage(Event.CONNECT_EVENT);
                                            msg.sendToTarget();
                                            startRead();
                                            startWrite();
                                        }
                                    } catch (SocketTimeoutException e) {
                                        LogUtils.d(TAG, "verify time out");
                                    }
                                }


                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (BindException e) {
                        Log.d(TAG, "bindexception");
                        if (mEventHandler != null) {
                            Message msg = mEventHandler.obtainMessage(Event.BIND_ERROR);
                            msg.sendToTarget();
                        }
                        e.printStackTrace();
                    } catch (IOException e) {
                        Log.d(TAG, "ioexception");
                        if (mEventHandler != null) {
                            Message msg = mEventHandler.obtainMessage(Event.OTHER_ERROR);
                            msg.obj = e.getMessage();
                            msg.sendToTarget();
                        }
                        e.printStackTrace();
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        if (mEventHandler != null) {
                            Message msg = mEventHandler.obtainMessage(Event.OTHER_ERROR);
                            msg.obj = "端口号超出范围";
                            msg.sendToTarget();
                        }
                    }
                }
            }).start();
        } else {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    LogUtils.d(TAG, "tcp client");
                    try {
                        mClientSocket = new Socket(mIP, mPort);
                        //发送验证消息
                        mClientSocket.getOutputStream().write(TransferProtocol.packConnectionData());
                        mClientSocket.getOutputStream().flush();
                        //验证身份
                        byte[] verifyData = new byte[TransferProtocol.packConnectionData().length];
                        mClientSocket.setSoTimeout(VERIFY_TIME_OUT);
                        try {
                            mClientSocket.getInputStream().read(verifyData);
                            mClientSocket.setSoTimeout(0);
                            if (TransferProtocol.parseConnectionPack(verifyData)) {
                                Message msg = mEventHandler.obtainMessage(Event.CONNECT_EVENT);
                                msg.sendToTarget();
                                startRead();
                                startWrite();
                            } else {
                                Message msg = mEventHandler.obtainMessage(Event.OTHER_ERROR);
                                msg.obj = "验证身份失败";
                                msg.sendToTarget();
                            }
                        } catch (SocketTimeoutException e) {
                            LogUtils.d(TAG, "verify time out");
                            Message msg = mEventHandler.obtainMessage(Event.OTHER_ERROR);
                            msg.obj = "验证身份超时";
                            msg.sendToTarget();
                        }
                    } catch (IOException e) {
                        if (mEventHandler != null) {
                            Message msg = mEventHandler.obtainMessage(Event.OTHER_ERROR);
                            msg.obj = e.getMessage();
                            msg.sendToTarget();
                        }
                        e.printStackTrace();
                    } catch (IllegalArgumentException e) {
                        if (mEventHandler != null) {
                            Message msg = mEventHandler.obtainMessage(Event.OTHER_ERROR);
                            msg.obj = e.getMessage();
                            msg.sendToTarget();
                        }
                        e.printStackTrace();
                    }
                }
            }).start();

        }

    }

    /**
     * 读取进程
     */
    private Thread getReadThread() {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                LogUtils.d(TAG, "onReadThread run");
                try {
                    byte[] bufferData = new byte[mConfig.getPacketMaxLength()];
                    while (mReading) {
                        if (mClientSocket != null) {
                            LogUtils.d(TAG, "onReadThread run mClientSocket != null");
                            mClientSocket.getInputStream().read(bufferData);
                            if (TransferProtocol.parseDisconnectPack(bufferData)) {
                                LogUtils.d(TAG, "end message");
                                mReading = false;
                                Message msg = mEventHandler.obtainMessage(Event.DISCONNECT_EVENT);
                                msg.sendToTarget();
                                break;
                            }
                            byte[] data = TransferProtocol.parseTransferPack(bufferData);
                            if (data != null) {
                                mReceiveCount++;
                                LogUtils.d(TAG, "data send handler");
                                Message message = mEventHandler.obtainMessage(Event.DATA_RECEIVE_EVENT);
                                message.obj = data;
                                message.sendToTarget();
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 写进程
     */
    private Thread getWriteThread() {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                LogUtils.d(TAG, "onWriteThread run");
                try {
                    while (mWriting) {
                        if (mClientSocket != null) {
                            LogUtils.d(TAG, "mClientSocket != null");
                            OutputStream outputStream = mClientSocket.getOutputStream();
                            String msgString = mBufferDataQueue.get();
                            if (!TextUtils.isEmpty(msgString)) {
                                byte[] data = msgString.getBytes(Config.ENCODE_TYPE);

                                LogUtils.d(TAG, "send data");
                                outputStream.write(TransferProtocol.packTransferData(data));
                                outputStream.flush();
                                mSendCount++;
                                Message message = mEventHandler.obtainMessage(Event.DATA_SEND_EVENT);
                                message.obj = mSendCount;
                                message.sendToTarget();
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

        mReading = true;
        if (mReadThread == null) {
            mReadThread = getReadThread();
        }
        mReadThread.start();


    }


    private void startWrite() {
        mWriting = true;
        if (mClientSocket != null) {
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
        mReading = false;
        mWriting = false;
        mAcceptClient = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mClientSocket != null) {
                    try {
                        OutputStream outputStream = mClientSocket.getOutputStream();
                        outputStream.write(TransferProtocol.packDisconnectPack());
                        outputStream.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            mClientSocket.getOutputStream().close();
                            mClientSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            mClientSocket = null;
                        }
                    }
                }
                if (mServerSocket != null) {
                    try {
                        mServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        mServerSocket = null;
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
