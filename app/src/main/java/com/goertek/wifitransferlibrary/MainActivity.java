package com.goertek.wifitransferlibrary;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.goertek.transferlibrary.BufferDataQueue;
import com.goertek.transferlibrary.Config;
import com.goertek.transferlibrary.ITransferListener;
import com.goertek.transferlibrary.TransferManager;
import com.goertek.transferlibrary.utils.IPUtils;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements ITransferListener {

    private static final String TAG = "MainActivity";

    private TransferManager mTransferManager;

    private EditText mIPEditText;

    private EditText mPortEditText;

    private TextView mSendCount;

    private TextView mReceiveCount;

    private boolean mIsServer = true;

    private boolean mIsTcp = true;

    public static String mIPAddress;

    public static InetAddress mBroadcastAddress = null;

    private static final int WRITE_PERMISSION_CODE = 0;

    private boolean mWritePermissionGranted = false;

    private boolean mIsSending = false;

    private Button mSingleStartButton;

    private Button mSingleStopButton;

    private Button mMultiStartButton;

    private Button mMultiStopButton;

    private Config mConfig = new Config();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermission();
        initIP();
        initViews();
    }

    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_PERMISSION_CODE);
        } else {
            mWritePermissionGranted = true;
        }
    }

    private void initIP() {
        mIPAddress = IPUtils.getLocalIP(this);
    }

    private void initViews() {

        //一对一传输、多对多传输切换
        final LinearLayout llSingle = (LinearLayout) findViewById(R.id.ll_single);
        final LinearLayout llMulti = (LinearLayout) findViewById(R.id.ll_multi);
        RadioGroup rgTransferType = (RadioGroup) findViewById(R.id.rg_transfer_type);
        rgTransferType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (i == R.id.rb_multi) {
                    llSingle.setVisibility(View.GONE);
                    llMulti.setVisibility(View.VISIBLE);
                } else if (i == R.id.rb_single) {
                    llSingle.setVisibility(View.VISIBLE);
                    llMulti.setVisibility(View.GONE);
                }
            }
        });

        //间隔时间
        EditText etInterval = (EditText) findViewById(R.id.et_interval);
        etInterval.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (!TextUtils.isEmpty(editable.toString())) {
                    mConfig.setInterval(Integer.parseInt(editable.toString()));
                }
            }
        });

        //一对一传输
        final TextView tvIP = (TextView) findViewById(R.id.tv_ip);
        tvIP.setText("当前IP地址是：" + mIPAddress);
        mIPEditText = (EditText) findViewById(R.id.et_ip);
        mPortEditText = (EditText) findViewById(R.id.et_port);
        mSingleStartButton = (Button) findViewById(R.id.bt_start);
        mSingleStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mIsSending) {
                    mIPAddress = IPUtils.getLocalIP(MainActivity.this);
                    // LogUtils.d(TAG, "onClick");
                    String ip = mIPEditText.getText().toString();
                    String port = mPortEditText.getText().toString();
                    if ((!mIsServer && isValidIP(ip)) || mIsServer) {
                        if ((!TextUtils.isEmpty(ip) || mIsServer) && !TextUtils.isEmpty(port)) {
                            setSendingState(true);
                            BufferDataQueue queue = new BufferDataQueue();
                            queue.put("hello world");
                            mTransferManager = new TransferManager(queue);
                            mTransferManager.setConfig(mConfig);
                            if (mIsServer) {
                                mTransferManager.startServer(mIsTcp ? TransferManager.TCP_TRANSFER : TransferManager.UDP_TRANSFER, MainActivity.this);
                            } else {
                                mTransferManager.startClient(mIsTcp ? TransferManager.TCP_TRANSFER : TransferManager.UDP_TRANSFER, ip, MainActivity.this);
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "请填写完整信息", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "IP地址填写不合法", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        mSingleStopButton = (Button) findViewById(R.id.bt_stop);
        mSingleStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //LogUtils.d(TAG, "onStop");
                if (mTransferManager != null) {
                    mTransferManager.destroy();
                }
                setSendingState(false);
            }
        });
        RadioGroup rgNet = (RadioGroup) findViewById(R.id.rg_net_type);
        rgNet.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (i == R.id.rb_udp) {
                    mIsTcp = false;
                } else {
                    mIsTcp = true;
                }
            }
        });
        RadioGroup rgCS = (RadioGroup) findViewById(R.id.rg_cs_type);
        rgCS.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (i == R.id.rb_client) {
                    mIsServer = false;
                    mIPEditText.setVisibility(View.VISIBLE);
                    tvIP.setVisibility(View.GONE);
                } else {
                    mIsServer = true;
                    mIPEditText.setVisibility(View.GONE);
                    tvIP.setVisibility(View.VISIBLE);
                }
            }
        });

        //多对多传输
        final EditText etGroupIP = (EditText) findViewById(R.id.et_multi_ip);
        final EditText etGroupPort = (EditText) findViewById(R.id.et_multi_port);
        Button btJoinGroup = (Button) findViewById(R.id.bt_multi_join);
        mMultiStartButton = (Button) findViewById(R.id.bt_multi_start);
        mMultiStopButton = (Button) findViewById(R.id.bt_multi_stop);
        if (mBroadcastAddress != null) {
            btJoinGroup.setText("加入广播");
        } else {
            etGroupIP.setVisibility(View.VISIBLE);
        }

        mMultiStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mIsSending) {
                    mIPAddress = IPUtils.getLocalIP(MainActivity.this);
                    String ip = etGroupIP.getText().toString();
                    String port = etGroupPort.getText().toString();
                    if (mBroadcastAddress == null) {
                        if (!TextUtils.isEmpty(ip) && !TextUtils.isEmpty(port)) {
                            if (isValidIP(ip)) {
                                if (mTransferManager != null) {
                                    mTransferManager.destroy();
                                }
                                setSendingState(true);
                                final BufferDataQueue queue = new BufferDataQueue();
                                queue.put("hello world");
                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        for (int i = 0; i < 1000; ++i) {
                                            queue.put("hello world");
                                        }
                                    }
                                }, 3000);
                                mTransferManager = new TransferManager(queue);
                                mTransferManager.setConfig(mConfig);
                                mTransferManager.startMultiTransfer(MainActivity.this);
                            } else {
                                Toast.makeText(MainActivity.this, "组播地址不合法", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "请填写完整组播地址和端口号", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        if (!TextUtils.isEmpty(port)) {
                            if (mTransferManager != null) {
                                mTransferManager.destroy();
                            }
                            setSendingState(true);
                        } else {
                            Toast.makeText(MainActivity.this, "请填写完整组播地址和端口号", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        });
        mMultiStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTransferManager != null) {
                    mTransferManager.destroy();
                }
                setSendingState(false);
            }
        });

        mSendCount = (TextView) findViewById(R.id.tv_send);
        mReceiveCount = (TextView) findViewById(R.id.tv_receive);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mTransferManager != null) {
            mTransferManager.destroy();
        }
    }


    private void setSendingState(boolean started) {
        mIsSending = started;
        if (mIsSending) {
            mSingleStartButton.setEnabled(false);
            mMultiStartButton.setEnabled(false);
        } else {
            mSingleStartButton.setEnabled(true);
            mMultiStartButton.setEnabled(true);
        }
    }

    /**
     * 判断是否为合法IP
     *
     * @return the ip
     */
    public boolean isValidIP(String ipAddress) {
        String ip = "([1-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3}";
        Pattern pattern = Pattern.compile(ip);
        Matcher matcher = pattern.matcher(ipAddress);
        return matcher.matches();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WRITE_PERMISSION_CODE) {
            mWritePermissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
    }


    @Override
    public void onReceive(byte[] data) {
        try {
            mReceiveCount.setText(new String(data, 0, data.length, "UTF-8"));
            Log.d(TAG, "onReceive" + new String(data, 0, data.length, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            Log.d(TAG, "onReceive" + " " + e.getMessage());
        }
    }

    @Override
    public void onSend(long count) {
        mSendCount.setText(String.valueOf(count));
    }

    @Override
    public void onConnected() {
        Toast.makeText(MainActivity.this, "建立连接", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisconnected(String error) {
        Toast.makeText(MainActivity.this, "断开连接", Toast.LENGTH_SHORT).show();
    }

}
