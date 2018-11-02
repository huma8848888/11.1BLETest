package com.bluetooth.unvarnishedtransmission;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class UnvarnishedTransmissionActivity extends Activity {
	// Debug 
	private static final String TAG = "firstActivity";
	private boolean D = true;
	
	// Intent request codes
	private static final int REQUEST_SEARCH_BLE_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;
	
	// msg for ui update
	private static final int MSG_EDIT_RX_STRING_UPDATE = 1;
	private static final int MSG_EDIT_TX_STRING_UPDATE = 2;
	private static final int MSG_EDIT_TX_STRING_ASCII_OR_HEX = 3;
	private static final int MSG_CONNECTION_STATE_UPDATE = 4;
	private static final int MSG_SEND_MESSAGE_ERROR_UI_UPDATE = 5;
	private static final int MSG_SPEED_STRING_UPDATE = 6;
	
	// MTU size 
	private static int MTU_SIZE_EXPECT = 300;
	private static int MTU_PAYLOAD_SIZE_LIMIT = 20;
	
	// Return flag
	public static final String EXTRAS_DEVICE = "DEVICE";
	public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    
    // flag for ascii or hex, true is ASCII, false is hex
    private boolean isTxAsciiHex = true;
    private boolean isRxAsciiHex = true;
    
    // flag for rx on/off
    private boolean isRxOn = false;
    
    // flag for auto tx 
    private boolean isAutoTx = false;
    
    // flag for connect state
    private boolean isConnectDevice = false;
    
    // RX counter and TX counter
    private volatile long mRxCounter;
    private volatile long mTxCounter;
    
    // Tx interval
    private int mTxInterval;
    private int MIN_TX_INTERVAL = 20;
    
    // ͬ���ź����������첽������
    // should add volatile
 	private volatile boolean isWriteCharacteristicOk = true;
 	private volatile boolean writeCharacteristicError = false;
    
 	// Thread
    private ThreadAutoTx mThreadAutoTx;
    private ThreadRx mThreadRx;
    private ThreadUnpackSend mUnpackThread;
    
    // Speed UI Update
    private ThreadSpeed mThreadSpeed;
    private int SPEED_STRING_UPDATE_TIME = 1000;
    private long lastTxCounter = 0;
    private long lastRxCounter = 0;
    private long lastSpeedUpdateTime;
    private boolean isSpeedUpdateOn = false;
    private DecimalFormat speedFormate = new DecimalFormat("###,###,###.##");
    
    
    // RX string builder, store the rx data
    // max rx speed is MAX_RX_BUFFER/RX_STRING_UPDATE_TIME = 40KByte/s
    private StringBuilder mRxStringBuildler;
    private int MAX_RX_BUFFER = 2000; 
    private int RX_STRING_UPDATE_TIME = 50;
    private int MAX_RX_SHOW_BUFFER = 5000; 
    
    // Unpack sending flag
    private boolean isUnpackSending = false;
    
    // Selected Device information
    private String mDeviceName;
    private String mDeviceAddress; 
    private BluetoothDevice mDevice;
    
    // bluetooth control
    private BluetoothAdapter mBluetoothAdapter;
    
    
    // Bluetooth GATT
    private BluetoothGatt mBluetoothGatt;
    
    // Bluetooth GATT Service
    private BluetoothGattService mBluetoothGattService; 
    
    // Test Characteristic
    private BluetoothGattCharacteristic mTestCharacter;
    
    // bluetooth Manager
    private BluetoothManager mBluetoothManager;
    // Button
    private Button mbtnTx;
    private Button mbtnClear;
        
    // EditText
    private EditText medtRxString;
    private EditText medtTxString;
    private EditText medtTxInterval;
    
    // Switch
    private Switch mswTxAsciiHex;
    
    // TextView 
    private TextView mtvRxCount;
    private TextView mtvRxSpeed;
    private TextView mtvTxCount;
    private TextView mtvTxSpeed;
    private TextView mtvGattStatus;
    
    // ToggleButton
    private ToggleButton mtbAutoTx;
    private ToggleButton mtbRxControl;
    
    // State control
    private GattConnectState mConnectionState;
    private enum GattConnectState {
    	STATE_INITIAL,
    	STATE_DISCONNECTED,
    	STATE_CONNECTING,
    	STATE_CONNECTED,
    	STATE_CHARACTERISTIC_CONNECTED,
    	STATE_CHARACTERISTIC_NOT_FOUND;
    }
    
    // UUID, modify for new spec
    //private final static UUID TEST_SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");      //ffe0
    private final static UUID TEST_SERVICE_UUID = UUID.fromString("0000e0ff-3c17-d293-8e48-14fe2e4da212");      //123bit
    private final static UUID TEST_CHARACTERISTIC_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    
    /** Client configuration descriptor that will allow us to enable notifications and indications */
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");   //geyun:just need support ccc
    
    // InputMethodManager
    InputMethodManager mInputMethodManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unvarnished_transmission);
        if(D) Log.d(TAG, "-------onCreate-------");
        // Set Title
        try {
        	// ActionBar initial
            getActionBar().setTitle("����˫��͸��" + this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName);
            //getActionBar().setDisplayHomeAsUpEnabled(true);
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        // get the bluetooth adapter
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        
        // judge whether android have bluetooth
        if(null == mBluetoothAdapter) {
        	if(D) Log.e(TAG, "This device do not support Bluetooth");
            Dialog alertDialog = new AlertDialog.Builder(this).
                    setMessage("This device do not support Bluetooth").
                    create();
            alertDialog.show();
        }
        
        // ensure that Bluetooth exists
        if (!EnsureBleExist()) {
        	if(D) Log.e(TAG, "This device do not support BLE");
        	finish();
        }
        
        
        // Button
        mbtnTx = (Button) findViewById(R.id.btnTx);
        mbtnClear = (Button) findViewById(R.id.btnClear);
     
        // Button listener 
        mbtnTx.setOnClickListener(new ButtonClick());
        mbtnClear.setOnClickListener(new ButtonClick());
     	
        // EditText initial
        medtRxString = (EditText) findViewById(R.id.edtRxString);
        medtTxString = (EditText) findViewById(R.id.edtTxString);
        medtTxInterval = (EditText) findViewById(R.id.edtTxInterval);
                
        // edit text watcher
        medtTxInterval.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView arg0, int arg1, KeyEvent arg2) {
				if (arg1 == EditorInfo.IME_ACTION_DONE) {
					// judge the tx interval
					TxIntervalJudge();
				}
				return false;
			}
        });
        
        // Switch initial
        mswTxAsciiHex = (Switch)findViewById(R.id.swTxAsciiHex);
        mswTxAsciiHex.setOnCheckedChangeListener(new OnCheckedChangeListener() {
        	@Override  
            public void onCheckedChanged(CompoundButton buttonView,  
                    boolean isChecked) {  
                // TODO Auto-generated method stub  
                if (isChecked) {  
                    isTxAsciiHex = false;
                } else {  
                	isTxAsciiHex = true;
                }
                // send the msg
		    	Message message = new Message();
		        message.what = MSG_EDIT_TX_STRING_ASCII_OR_HEX;
		        handler.sendMessage(message);
            }  
        });
        
        
        // EditText initial
        mtbAutoTx = (ToggleButton) findViewById(R.id.tbAutoTx);
        mtbAutoTx.setOnCheckedChangeListener(new OnCheckedChangeListener() {
        	@Override  
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // TODO Auto-generated method stub  
        		// judge the check status
                if (isChecked) {  
                	// judge the tx interval is null
//                	if(medtTxInterval.getText().toString() == null || medtTxInterval.getText().toString().length() == 0) {
//                		if(D) Log.e(TAG, "the tx interval shoud not be empty");
//                		Toast.makeText(UnvarnishedTransmissionActivity.this, "the tx interval shoud not be empty!", Toast.LENGTH_SHORT).show();
//                		mtbAutoTx.setChecked(false);
//                		return;
//                	}
                	// judge the Tx interval value
                	mTxInterval = Integer.parseInt(medtTxInterval.getText().toString().replace(" ", ""));
                	
                	// limit the tx interval, here can do more thing, now we just consider the ble interval
                	if(mTxInterval < MIN_TX_INTERVAL) {
                		if(D) Log.e(TAG, "the tx interval is less than the limit ble interval");
                		Toast.makeText(UnvarnishedTransmissionActivity.this, "the tx interval is too small! the limit time is: " 
                						+ String.valueOf(MIN_TX_INTERVAL) + "ms", Toast.LENGTH_SHORT).show();
                		medtTxInterval.setText(String.valueOf(MIN_TX_INTERVAL));
                		mtbAutoTx.setChecked(false);
                		return;
                	}
                    isAutoTx = true;
                    mbtnTx.setEnabled(false);
                    medtTxString.setEnabled(false);
                    medtTxInterval.setEnabled(false);
                    
                    // run the auto tx thread
                    mThreadAutoTx = new ThreadAutoTx();
                    mThreadAutoTx.start();
                    
                } else {  
                	isAutoTx = false;
                	mbtnTx.setEnabled(true);
                	medtTxString.setEnabled(true);
                	medtTxInterval.setEnabled(true);
                }
            }  
        });
        
        mtbRxControl = (ToggleButton) findViewById(R.id.tbRxControl);
        mtbRxControl.setOnCheckedChangeListener(new OnCheckedChangeListener() {
        	@Override  
            public void onCheckedChanged(CompoundButton buttonView,  
                    boolean isChecked) {  
                // TODO Auto-generated method stub  
        		// judge the check status
                if (isChecked) {  
                	if(null != mTestCharacter) {
                		enableNotification(mBluetoothGatt, mTestCharacter);
                	}
                	isRxOn = true;
                	
                	// Create a thread to receice data and update ui
                	mThreadRx = new ThreadRx();
                	mThreadRx.start();
                    
                } else {  
                	isRxOn = false;
                }
            }  
        });
        
        
        // TextView initial 
        mtvRxCount = (TextView)findViewById(R.id.tvRxCount);
        mtvRxSpeed = (TextView)findViewById(R.id.tvRxSpeed);
        mtvTxCount = (TextView)findViewById(R.id.tvTxCount);
        mtvTxSpeed = (TextView)findViewById(R.id.tvTxSpeed);
        mtvGattStatus = (TextView)findViewById(R.id.tvGattStatus);
                
        // RX string builder initial capacity with MAX_RX_BUFFER
        mRxStringBuildler = new StringBuilder(MAX_RX_BUFFER);
            	
    	// Get the bluetooth manager
    	mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    	
    	// Update the connection state
    	SetConnectionState(GattConnectState.STATE_INITIAL);
    	
    	//InputMethodManager
    	mInputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    }
    
    @Override
    protected void onResume() {
    	if(D) Log.d(TAG, "-------onResume-------");
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
            	if(D) Log.d(TAG, "start bluetooth");
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
    	menu.clear();
    	// if not find the special characteristic
        if(GattConnectState.STATE_CHARACTERISTIC_CONNECTED != mConnectionState) {
        	menu.add("ѡ���豸").setIcon(R.drawable.select).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        } else {
        	menu.add("�Ͽ�����").setIcon(R.drawable.disconnect).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
    	return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	super.onOptionsItemSelected(item);
        if(D) Log.d(TAG, "-------onOptionsItemSelected-------");
        switch(item.getItemId()) {//�õ��������item��itemId
	        case 0: //��Ӧ��ID������add���������趨��Id
	        	// if have a gatt connection, disconnect and unregister it
	            if(GattConnectState.STATE_CHARACTERISTIC_CONNECTED == mConnectionState 
	            	|| GattConnectState.STATE_CONNECTED == mConnectionState 
	            	|| GattConnectState.STATE_CHARACTERISTIC_NOT_FOUND == mConnectionState ) {
	            	
	            	// only find the characteristic, then return, disconnect will very soon change the state, so do like this
	            	if(GattConnectState.STATE_CHARACTERISTIC_CONNECTED == mConnectionState) {
	            		// Try to disconnect the gatt server, ensure unregister the callback (in the callback register it)
	            		if(BluetoothProfile.STATE_CONNECTED == mBluetoothManager.getConnectionState(mDevice, BluetoothProfile.GATT)) {
	            			mBluetoothGatt.disconnect();
		            	}
	            		return true;
	            	}
	            	
	            	// Try to disconnect the gatt server, ensure unregister the callback (in the callback register it)
	            	// here din't return
	            	if(BluetoothProfile.STATE_CONNECTED == mBluetoothManager.getConnectionState(mDevice, BluetoothProfile.GATT)) {
            			mBluetoothGatt.disconnect();
	            	}
	            }
	        	
	            // if didn't hava a gatt connect, find a device and create it
	        	if(D) Log.i(TAG, "start select ble device");
	        		        	
	        	// start the device search activity for select device
	        	Intent intent = new Intent(this, DeviceSearchActivity.class);
	            startActivityForResult(intent, REQUEST_SEARCH_BLE_DEVICE);
	            
	            break;
	            
	        default:
	        	if(D) Log.e(TAG, "something may error");
	            break;
	    }
        return true;
    }
    
    @Override
    public void onStop( ) {
    	if(D) Log.d(TAG, "-------onStop-------");
    	// Do something when activity on stop
    	// kill the auto unpack thread
    	if(null != mUnpackThread) {
    		mUnpackThread.interrupt();
    	}
    	
    	// kill the auto tx thread
    	if(null != mThreadAutoTx) {
    		mThreadAutoTx.interrupt();
    	}
    	
    	// kill the rx thread
    	if(null != mThreadRx) {
    		mThreadRx.interrupt();
    	}
    	
    	// kill the speed ui update thread
    	if(null != mThreadSpeed) {
    		mThreadSpeed.interrupt();
    	}
    	super.onStop();
    }
    
    @Override
    public void onDestroy( ) {
    	if(D) Log.d(TAG, "-------onDestroy-------");
    	
    	// Try to disconnect the gatt server
    	if(BluetoothProfile.STATE_CONNECTED == mBluetoothManager.getConnectionState(mDevice, BluetoothProfile.GATT)) {
			mBluetoothGatt.disconnect();
    	}
    	
    	mTestCharacter = null;
    	
    	super.onDestroy();
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent intent)  { 
    	if(D) Log.d(TAG, "-------onActivityResult-------");
    	if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_SEARCH_BLE_DEVICE:
	    	// When the request to enable Bluetooth returns 
	    	if (resultCode == Activity.RESULT_OK) {
	    		//Stroe the information
	    		//mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
	            //mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
	    		mDevice = intent.getParcelableExtra(EXTRAS_DEVICE);
	    		if(D) Log.e(TAG, "select a device, the name is " + mDevice.getName() + ", addr is " + mDevice.getAddress());

	        	// Update the connection state
	        	SetConnectionState(GattConnectState.STATE_CONNECTING);
	        	
	    		ConnectDevice(mDevice);
	        } else {
	        	// do nothing
	        	if(D) Log.e(TAG, "the result code is error!");
	        }
	    	break;
	          
        case REQUEST_ENABLE_BT:
	    	// When the request to enable Bluetooth returns 
	    	if (resultCode == Activity.RESULT_OK) {
	    		//do nothing
	            Toast.makeText(this, "Bt is enabled!", Toast.LENGTH_SHORT).show();            
	        } else {
	        	// User did not enable Bluetooth or an error occured
	        	Toast.makeText(this, "Bt is not enabled!", Toast.LENGTH_SHORT).show();
	        	finish();
	        }
	    	break;
	    	
        default:
        	break;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }  
    
    // judge the support of ble in android  
    private boolean EnsureBleExist() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "������֧��BLE", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }	
    
    // judge tx interval
    private void TxIntervalJudge() {
    	//if (key == EditorInfo.IME_ACTION_DONE || keyevent.getAction() == KeyEvent.KEYCODE_BACK) {
			// judge the tx interval is null
        	if(medtTxInterval.getText().toString() == null || medtTxInterval.getText().toString().length() == 0) {
        		if(D) Log.e(TAG, "the tx interval shoud not be empty");
        		Toast.makeText(UnvarnishedTransmissionActivity.this, "the tx interval shoud not be empty!", Toast.LENGTH_SHORT).show();
        		// update the value
        		medtTxInterval.setText(String.valueOf(MIN_TX_INTERVAL));
        	}
        	// judge the Tx interval value
        	int txInterval = Integer.parseInt(medtTxInterval.getText().toString().replace(" ", ""));
        	
        	// limit the tx interval, here can do more thing, now we just consider the ble interval
        	if(txInterval < MIN_TX_INTERVAL) {
        		if(D) Log.e(TAG, "the tx interval is less than the limit ble interval");
        		Toast.makeText(UnvarnishedTransmissionActivity.this, "the tx interval is too small! the limit time is: " 
        						+ String.valueOf(MIN_TX_INTERVAL) + "ms", Toast.LENGTH_SHORT).show();
        		// update the value
        		medtTxInterval.setText(String.valueOf(MIN_TX_INTERVAL));
        		
        	}
        	// remove the focus
        	medtTxInterval.clearFocus();
        	closeInputMethod();
		//}
    }
    // cloae the input method
    private void closeInputMethod() {
        boolean isOpen = mInputMethodManager.isActive();
        if (isOpen) {
            // imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS);//û����ʾ����ʾ
        	mInputMethodManager.hideSoftInputFromWindow(UnvarnishedTransmissionActivity.this.getCurrentFocus().getWindowToken(),
            		InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }
    class ButtonClick implements OnClickListener
	{
		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			switch(v.getId()) {
			//If the Tx button click
			case R.id.btnTx:
				// send message to remote
				SendMessageToRemote();

				break;
			//If the Tx button click
			case R.id.btnClear:
				//Clear the rx buffer
				medtRxString.setText("");
				//Clear the rx counter
				mRxCounter = 0;
				lastRxCounter = 0;
				mtvRxCount.setText("Rx Count: 0");
				mtvRxSpeed.setText("Rx Speed: 0 Byte/s");
				
				//Clear the tx buffer
				//medtTxString.setText("");
				//Clear the tx counter
				mTxCounter = 0;
				lastTxCounter = 0;
				mtvTxCount.setText("Tx Count: 0");
				mtvTxSpeed.setText("Tx Speed: 0 Byte/s");
				
				lastSpeedUpdateTime = 0;
				break;
			}
		}
	}
    
    // send data to the remote device and update ui
	public void SendMessageToRemote( ) {
		byte[] sendData;
		if(0 == medtTxString.length()) {
			if(D) Log.w(TAG, "the tx string is empty!");
			// send the msg to update the ui
			Message message = new Message();
			message.what = MSG_SEND_MESSAGE_ERROR_UI_UPDATE;
			message.arg1 = 0;
			handler.sendMessage(message);
			return;
		}

		// judge the type of edit Tx String buffer, and change to byte[]
		if(false) {//Ascii
			sendData = StringByteTrans.Str2Bytes(medtTxString.getText().toString());
			//sendData = DigitalTrans.AsciiString2Byte(medtTxString.getText().toString());
		} else {//hex
			// remove the space and the "0x", and change to byte[]
			sendData = StringByteTrans.hexStr2Bytes(medtTxString.getText().toString().replace(" ", "").replace("0x", ""));
			//sendData = DigitalTrans.hexStringToByte(medtTxString.getText().toString().replace(" ", "").replace("0x", ""));
		}
		if(sendData == null) {
			if(D) Log.e(TAG, "the tx string have some error!");

			// stop the auto tx
			isAutoTx = false;

			// send the msg to update the ui
			Message message = new Message();
			message.what = MSG_SEND_MESSAGE_ERROR_UI_UPDATE;
			message.arg1 = 1;
			handler.sendMessage(message);
			return;
		}


		// ensure last unpack tx is OK
		if(true == isUnpackSending) {
			if(D) Log.e(TAG, "the last tx string didn't all send!");

			// stop the auto tx
			isAutoTx = false;

			// send the msg to update the ui
			Message message = new Message();
			message.what = MSG_SEND_MESSAGE_ERROR_UI_UPDATE;
			message.arg1 = 2;
			handler.sendMessage(message);
			return;
		}

		// send data to the remote device
		mUnpackThread = new ThreadUnpackSend(sendData);
		mUnpackThread.start();

		if(D) Log.d(TAG, "send data is: " + Arrays.toString(sendData));


	}


	// send data to the remote device and update ui
	public void SendMessageToRemote(String data) {
		byte[] sendData;
//		if(0 == medtTxString.length()) {
//			if(D) Log.w(TAG, "the tx string is empty!");
//			// send the msg to update the ui
//			Message message = new Message();
//			message.what = MSG_SEND_MESSAGE_ERROR_UI_UPDATE;
//			message.arg1 = 0;
//			handler.sendMessage(message);
//			return;
//		}

		// judge the type of edit Tx String buffer, and change to byte[]
		if(false) {//Ascii
			sendData = StringByteTrans.Str2Bytes(medtTxString.getText().toString());
			//sendData = DigitalTrans.AsciiString2Byte(medtTxString.getText().toString());
		} else {//hex
			// remove the space and the "0x", and change to byte[]
			sendData = StringByteTrans.hexStr2Bytes(data.replace(" ", "").replace("0x", ""));
			//sendData = DigitalTrans.hexStringToByte(medtTxString.getText().toString().replace(" ", "").replace("0x", ""));
		}
		if(sendData == null) {
			if(D) Log.e(TAG, "the tx string have some error!");

			// stop the auto tx
			isAutoTx = false;

			// send the msg to update the ui
			Message message = new Message();
			message.what = MSG_SEND_MESSAGE_ERROR_UI_UPDATE;
			message.arg1 = 1;
			handler.sendMessage(message);
			return;
		}


		// ensure last unpack tx is OK
		if(true == isUnpackSending) {
			if(D) Log.e(TAG, "the last tx string didn't all send!");

			// stop the auto tx
			isAutoTx = false;

			// send the msg to update the ui
			Message message = new Message();
			message.what = MSG_SEND_MESSAGE_ERROR_UI_UPDATE;
			message.arg1 = 2;
			handler.sendMessage(message);
			return;
		}

		// send data to the remote device
		mUnpackThread = new ThreadUnpackSend(sendData);
		mUnpackThread.start();

		if(D) Log.d(TAG, "send data is: " + Arrays.toString(sendData));

	}
    
    
    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param The destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean ConnectDevice(BluetoothDevice device) {
    	if (device == null) {
    		if(D) Log.e(TAG, "Device not found.  Unable to connect.");
            return false;
        }
    	
    	// Try to connect the gatt server
    	mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
    	if(D) Log.d(TAG, "Trying to create a new connection.");
		return D;
    	
    }
    
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
    	@Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if(D) Log.e(TAG, "onMtuChanged new mtu is " + mtu);
            if(D) Log.e(TAG, "onMtuChanged new status is " + String.valueOf(status));
            // change the mtu real payloaf size
            MTU_PAYLOAD_SIZE_LIMIT = mtu - 3;

            // Attempts to discover services after successful connection.
            if(D) Log.i(TAG, "Attempting to start service discovery:" +
                    mBluetoothGatt.discoverServices());
        }



        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
    		if(D) Log.d(TAG, "the new staus is " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
            	// Update the connection state
            	SetConnectionState(GattConnectState.STATE_CONNECTED);

                if(D) Log.i(TAG, "Connected to GATT server.");
                
                // only android 5.0 add the requestMTU feature
                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
	                // Attempts to discover services after successful connection.
	                if(D) Log.i(TAG, "Attempting to start service discovery:" +
	                        mBluetoothGatt.discoverServices());
                } else {
                	if (D) Log.i(TAG, "Attempting to request mtu size, expect mtu size is: " + String.valueOf(MTU_SIZE_EXPECT));
                    mBluetoothGatt.requestMtu(MTU_SIZE_EXPECT);
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            	// Update the connection state
            	SetConnectionState(GattConnectState.STATE_DISCONNECTED);
            	
            	// Try to close the gatt server, ensure unregister the callback
            	if(null != mBluetoothGatt) {
            		mBluetoothGatt.close();
            	}
            	
                if(D) Log.i(TAG, "Disconnected from GATT server.");
            }
        }
    	
    	@Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
            	// get all service on the remote device
            	final List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service : services) {
                	if(D) Log.d(TAG, "the GATT server uuid is " + service.getUuid());
                	// get all Characteristics in the service, then we can use the UUID or the attribute handle to get the Characteristics's value
                	List<BluetoothGattCharacteristic> characters = service.getCharacteristics();
                	for(BluetoothGattCharacteristic character : characters) {
                		if(D) Log.d(TAG, "the GATT server include Characteristics uuid is " + character.getUuid());
                		if(D) Log.d(TAG, "the Characteristics's permissoion is " + character.getPermissions());
                		if(D) Log.d(TAG, "the Characteristics's properties is 0x" + Integer.toHexString(character.getProperties()));
                		// get all descriptors in the characteristic 
                		List<BluetoothGattDescriptor> descriptors = character.getDescriptors();
                		for(BluetoothGattDescriptor descriptor : descriptors) {
                    		if(D) Log.d(TAG, "the Characteristics include descriptor uuid is " + descriptor.getUuid());
                    	}
                		
                		if(character.getUuid().equals(TEST_CHARACTERISTIC_UUID)) {
                			// find the test character, then we can use it to set value or get value.
                			mTestCharacter = character;
                			
                			// Update the connection state
                        	SetConnectionState(GattConnectState.STATE_CHARACTERISTIC_CONNECTED);
                		}
                	}
                }
                
                // if not find the special characteristic
                if(GattConnectState.STATE_CHARACTERISTIC_CONNECTED != mConnectionState) {
                	// Update the connection state
                	SetConnectionState(GattConnectState.STATE_CHARACTERISTIC_NOT_FOUND);
                }
            }
        }
    	
    	@Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    		byte[] data;
    		if(TEST_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
    			data = characteristic.getValue();
    			// call function to deal the data
    			onDataReceive(data);
    		} else {
    			if(D) Log.w(TAG, "receive other notification");
    			return;
    		}
        }
    	
    	@Override
        public void onDescriptorWrite(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID.equals(descriptor.getUuid())) {
                    if(D) Log.d(TAG, "CCC:  ok,try to write test----> ");
                }
            } else {
            	if(D) Log.e(TAG, "Descriptor write error: " + status);
            }
        };
        
    	@Override
    	public void onCharacteristicWrite (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
    		// Here can do something to verify the write
    		
            if (TEST_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
            	if(D) Log.d(TAG, "onCharacteristicWrite UUID is: " + characteristic.getUuid());
            	if(D) Log.d(TAG, "onCharacteristicWrite data value:"+Arrays.toString(characteristic.getValue()));
            	
        		if (status == BluetoothGatt.GATT_SUCCESS) {
        			writeCharacteristicError = false;
        		} else {
        			writeCharacteristicError = true;
                	if(D) Log.e(TAG, "Characteristic write error: " + status + "try again.");
                }
        		
        		isWriteCharacteristicOk = true;
            }
	        
    	}
    };

    public volatile String hexReceived = null;

	public static float getFloat(byte[] a) {
		// 4 bytes
		byte[] byteValToConvert = new byte[4];
		byte temp;
		for (int i = 0 ; i < 4 ; i++ ){
			temp = a[i + 5];
			byteValToConvert[i]  = temp;
		}

		int accum = 0;
		for ( int shiftBy = 0; shiftBy < 4; shiftBy++ ) {
			accum |= (byteValToConvert[shiftBy] & 0xff) << shiftBy * 8;
		}
		return Float.intBitsToFloat(accum);
	}

	// data receive
    public void onDataReceive(byte[] data) {
    	if(true == isRxOn) {

    		hexReceived = StringByteTrans.byte2HexStr(data);
	    	if(D) Log.d(TAG, "receive data is: " + hexReceived);
	    	if (data.length > 2){
	    		Log.d("floatVal","float value is :"+getFloat(data));
			}
	    	mRxCounter = mRxCounter + data.length;
	    	// Store the receive data, store with ASCII, should store in a StringBuilder first, 
	    	// because the receive speed will be very fast, near 10ms a packet
			mRxStringBuildler.append(StringByteTrans.byte2HexStr(data));
//	    	mRxStringBuildler.append(StringByteTrans.Byte2String(data));
	    	//mRxStringBuildler.append(DigitalTrans.bytetoString(data));
	
	    	// send the msg, here may send less times MSG, so we use the StringBuildler
	    	//Message message = new Message();
	        //message.what = MSG_EDIT_RX_STRING_UPDATE;
	        //handler.sendMessage(message);
    	}
    }

    
    
    // for ui update
    final Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            super.handleMessage(msg);
            if(msg.what == MSG_EDIT_RX_STRING_UPDATE){
            	if(0 == mRxStringBuildler.length()) {
            		//if(D) Log.w(TAG, "no rx data, don't update");
            		return;
            	}
            	
            	// store the buffer
            	String s = mRxStringBuildler.toString();
            	// Clear the buffer
            	mRxStringBuildler.delete(0, mRxStringBuildler.length());
            	
            	// update the rx edit text ui
            	medtRxString.append(s);
            	
            	// if rx edit text is too full
            	if(MAX_RX_SHOW_BUFFER < medtRxString.length()) {
            		medtRxString.setText("");
            	}
            	
            	// update the rx counter ui
            	mtvRxCount.setText("Rx Count: " + String.valueOf(mRxCounter));
            	
            	
            } else if(msg.what == MSG_EDIT_TX_STRING_UPDATE) {
            	// do something
            	
            	// update the tx counter ui
            	mtvTxCount.setText("Tx Count: " + String.valueOf(mTxCounter));
            	
            	
            } else if(msg.what == MSG_SPEED_STRING_UPDATE) {
            	long curTime = System.currentTimeMillis();
            	
            	if(0 == lastSpeedUpdateTime) {
            		if(D) Log.d(TAG, "first time to update speed ui");
            		lastSpeedUpdateTime = System.currentTimeMillis();
            		return;
            	}
                
                if(curTime == lastSpeedUpdateTime) {
                	if(D) Log.w(TAG, "speed calculate with time equal");
                	return;
                }
                
                if(mRxCounter < lastRxCounter) {
                	//if(D) Log.w(TAG, "rx counter didn't change");
                } else {
	                // update the rx speed ui
	                float rxSpeed = ((float)(mRxCounter - lastRxCounter))/((float)(curTime - lastSpeedUpdateTime))*1000;
	            	mtvRxSpeed.setText("Rx Speed: " 
	            				+ speedFormate.format(rxSpeed)
	            				+ " Byte/s");
                }
                if(mTxCounter < lastTxCounter) {
                	//if(D) Log.w(TAG, "tx counter didn't change");
                } else {
	                // update the tx speed ui
	            	float txSpeed = ((float)(mTxCounter - lastTxCounter))/((float)(curTime - lastSpeedUpdateTime))*1000;
	            	mtvTxSpeed.setText("Tx Speed: " 
	        					+ speedFormate.format(txSpeed)
	        					+ " Byte/s");
                }
            	// record the last counter
            	lastTxCounter = mTxCounter;
                lastRxCounter = mRxCounter;
                
                // record the last update time
                lastSpeedUpdateTime = System.currentTimeMillis();
                
            } else if(msg.what == MSG_EDIT_TX_STRING_ASCII_OR_HEX) {
            	String oldStr;
            	String newStr;
            	if(true == isTxAsciiHex) {// hex to ascii
            		// remove the space and the "0x"
            		oldStr = medtTxString.getText().toString().replace(" ", "").replace("0x", "");// hex string
            		if(oldStr.equals("")) {
            			if(D) Log.w(TAG, "change to ASCII failed, the edit text have no string");
            			return;
            		}
            		// Change to ascii str
            		newStr = StringByteTrans.hexStr2Str(oldStr);
            		//newStr = DigitalTrans.HexString2AsciiString(oldStr);
            	} else {// ascii to hex
            		oldStr = medtTxString.getText().toString();// ascii string
            		if(oldStr.equals("")) {
            			if(D) Log.w(TAG, "change to HEX failed, the edit text have no string");
            			return;
            		}
            		newStr = StringByteTrans.str2HexStr(oldStr);
            		//newStr = DigitalTrans.AsciiString2HexString(oldStr);
            	}
            	if(newStr.equals("")) {
        			if(D) Log.w(TAG, "change to ASCII/HEX failed, the edit text format is error");
        			// Clear the edit text
        			medtTxString.setText("");
        			return;
        		}
            	medtTxString.setText(newStr);
            } else if(msg.what == MSG_CONNECTION_STATE_UPDATE) {
            	if(GattConnectState.STATE_INITIAL == mConnectionState) {
            		
            		if(D) Log.i(TAG, "The connection state now is STATE_INITIAL");
            		// Initial state
            		medtTxInterval.setText("500");// initial tx interval
            		mswTxAsciiHex.setChecked(false);
            		mtbAutoTx.setChecked(false);
            		
            		mtbAutoTx.setEnabled(false);
            		mbtnTx.setEnabled(false);
            		mtbRxControl.setEnabled(false);
            		
            		// Change the connect state text
            		mtvGattStatus.setText("Must find a device to connect");
            		mtvGattStatus.setTextColor(android.graphics.Color.RED);
            	} else if(GattConnectState.STATE_CONNECTING == mConnectionState) {
            		if(D) Log.i(TAG, "The connection state now is STATE_CONNECTING");
            		mtbRxControl.setChecked(false);
            		
            		mtbAutoTx.setEnabled(false);
            		mbtnTx.setEnabled(false);
            		mtbRxControl.setEnabled(false);
            		
            		// Change the connect state text
            		mtvGattStatus.setText("Waiting for connecting");
            		mtvGattStatus.setTextColor(android.graphics.Color.RED);
            	} else if(GattConnectState.STATE_DISCONNECTED == mConnectionState) {
            		if(D) Log.i(TAG, "The connection state now is STATE_DISCONNECTED");
            		// disable rx
            		mtbRxControl.setChecked(false);
            		
            		// Close Rx and Tx thread
            		mtbAutoTx.setChecked(false);
            		mtbRxControl.setChecked(false);
            		
            		mtbAutoTx.setEnabled(false);
            		mbtnTx.setEnabled(false);
            		mtbRxControl.setEnabled(false);
            		
            		// change the connect state
            		isConnectDevice = false;
            		
            		// Change the connect state text
            		mtvGattStatus.setText("No connected device");
            		mtvGattStatus.setTextColor(android.graphics.Color.RED);
            		            		
            		// Update Menu
            		invalidateOptionsMenu();
            		
            		// turn off the update speed ui thread
            		isSpeedUpdateOn = false;
            	} else if(GattConnectState.STATE_CONNECTED == mConnectionState) {
            		if(D) Log.i(TAG, "The connection state now is STATE_CONNECTED");
            		
            		mtbAutoTx.setEnabled(false);
            		mbtnTx.setEnabled(false);
            		mtbRxControl.setEnabled(false);
            		// Change the connect state text
            		mtvGattStatus.setText("Waiting for find the characteristic");
            		mtvGattStatus.setTextColor(android.graphics.Color.RED);
            	} else if(GattConnectState.STATE_CHARACTERISTIC_CONNECTED == mConnectionState) {
            		if(D) Log.i(TAG, "The connection state now is STATE_CHARACTERISTIC_CONNECTED");
            		
            		mtbAutoTx.setEnabled(true);
            		mbtnTx.setEnabled(true);
            		mtbRxControl.setEnabled(true);
            		
            		// change the connect state
            		isConnectDevice = true;
            		
            		// enable rx
            		mtbRxControl.setChecked(true);
            		
            		// Change the connect state text
            		mtvGattStatus.setText("Connected with ( " + mDevice.getName() + " )");
            		mtvGattStatus.setTextColor(android.graphics.Color.BLUE);
            		
            		// Update Menu
            		invalidateOptionsMenu();
            		
            		// turn on the update speed ui thread
            		isSpeedUpdateOn = true;
            		// Create a thread to update speed ui
                	mThreadSpeed = new ThreadSpeed();
                	mThreadSpeed.start();
            	} else if(GattConnectState.STATE_CHARACTERISTIC_NOT_FOUND == mConnectionState) {
            		if(D) Log.i(TAG, "The connection state now is STATE_CHARACTERISTIC_NOT_FOUND");
            		
            		mtbAutoTx.setEnabled(false);
            		mbtnTx.setEnabled(false);
            		mtbRxControl.setEnabled(false);
            		
            		// Change the connect state text
            		mtvGattStatus.setText(mDevice.getName() + "is not the right device");
            		mtvGattStatus.setTextColor(android.graphics.Color.RED);
            	}
            } else if(msg.what == MSG_SEND_MESSAGE_ERROR_UI_UPDATE) {
            	if(msg.arg1 == 0) {
            		Toast.makeText(UnvarnishedTransmissionActivity.this, "the tx string is empty!", Toast.LENGTH_SHORT).show();
            	} else if (msg.arg1 == 1) {
            		Toast.makeText(UnvarnishedTransmissionActivity.this, "the tx string have some error!", Toast.LENGTH_SHORT).show();
        			
            	} else if(msg.arg1 == 2) {
            		Toast.makeText(UnvarnishedTransmissionActivity.this, "the last tx string didn't all send!", Toast.LENGTH_SHORT).show();
            	}
            	// close the auto tx
    			if(mtbAutoTx.isChecked()) {
    				mtbAutoTx.setChecked(false);
    			}
            	
            }
            
            
        }
    };
    
    // Auto Tx Thread
	//在此处使用自动发送开关来进行采集
    public class ThreadAutoTx extends Thread {
    	public void run() {
    		if(D) Log.i(TAG, "auto tx thread is run");
    		while(isAutoTx) {
    			// send message to remote
				SendMessageToRemote("AA");//发送AA

				while(hexReceived == "55"){//等待55,这里为什么不是在不相等的时候等待？好奇怪
					try {
						if(D) Log.i(TAG, "auto run waiting 55" + " hexReceived is " + hexReceived);
						Thread.sleep(1);
					}catch (InterruptedException e){
						e.printStackTrace();
					}
				}

				SendMessageToRemote("53");
    			try {
					if(D) Log.i(TAG, "after send 53 wait a moment");
					Thread.sleep(mTxInterval);
    			} catch (InterruptedException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}

				SendMessageToRemote("41");//发送01Bit3
				try {
					if(D) Log.i(TAG, "after send 41 Bit3 wait a moment");
					Thread.sleep(mTxInterval);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				SendMessageToRemote("00");//发送00 Bit2
				try {
					if(D) Log.i(TAG, "after send Bit2 00 wait a moment");
					Thread.sleep(mTxInterval);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				SendMessageToRemote("00");//发送00 Bit1
				try {
					if(D) Log.i(TAG, "after send Bit1 00 wait a moment");
					Thread.sleep(mTxInterval);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				SendMessageToRemote("01");//发送01 Bit0
				if(D) Log.i(TAG, "send 01");


				while(hexReceived =="AD"){//等待AD到来
					try {
						if (D) Log.d(TAG,"waiting for AD" + "hexReceived is " + hexReceived);
						Thread.sleep(1);
					}catch (InterruptedException e){
						e.printStackTrace();
					}
				}

//				SendMessageToRemote("52");//发送52
//				try {
//					if(D) Log.i(TAG, "after send  52 wait a moment");
//					Thread.sleep(mTxInterval);
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}

				while (isAutoTx){
					SendMessageToRemote("52");//发送52
					while(hexReceived =="AD"){//等待AD到来
						try {
							if (D) Log.d(TAG,"waiting for AD" + "hexReceived is " + hexReceived);
							Thread.sleep(1);
						}catch (InterruptedException e){
							e.printStackTrace();
						}
					}
					try {
						if(D) Log.i(TAG, "after send  52 wait a moment");
						Thread.sleep(mTxInterval);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
			}
    		if(D) Log.i(TAG, "auto tx thread is stop");
    	}
    }
    
    
    // Rx Thread
    public class ThreadRx extends Thread {
    	public void run() {
    		if(D) Log.i(TAG, "rx thread is run");
    		while(isRxOn) {
    			// every 100ms send a update message, about 20/10*100=200Byte
    			try {
    				Thread.sleep(RX_STRING_UPDATE_TIME);
    			} catch (InterruptedException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
    			// send the msg, here may send less times MSG, so we use the StringBuildler
    	    	Message message = new Message();
    	        message.what = MSG_EDIT_RX_STRING_UPDATE;
    	        handler.sendMessage(message);
    		}
    		if(D) Log.i(TAG, "rx thread is stop");
    	}
    }
    
    // Speed Thread
    public class ThreadSpeed extends Thread {
    	public void run() {
    		if(D) Log.i(TAG, "speed string UI update thread is run");
    		while(isSpeedUpdateOn) {
    			// every 100ms send a update message, about 20/10*100=200Byte
    			try {
    				Thread.sleep(SPEED_STRING_UPDATE_TIME);
    			} catch (InterruptedException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
    			// send the msg, here may send less times MSG, so we use the StringBuildler
    	    	Message message = new Message();
    	        message.what = MSG_SPEED_STRING_UPDATE;
    	        handler.sendMessage(message);
    		}
    		if(D) Log.i(TAG, "speed string UI update thread is stop");
    	}
    }
    
    // unpack and send thread
    public class ThreadUnpackSend extends Thread {
    	byte[] sendData;
    	ThreadUnpackSend(byte[] data) {
    		sendData = data;
    	}
    	public void run() {
    		if(D) Log.d(TAG, "ThreadUnpackSend is run");
    		// time test
    		if(D) Log.e("TIME_thread run", String.valueOf(System.currentTimeMillis()));
    		// set the unpack sending flag 
    		isUnpackSending = true;
    		// send data to the remote device
    		if(null != mTestCharacter) {
    			// unpack the send data, because of the MTU size is limit
    			int length = sendData.length;
    			int unpackCount = 0;
    			byte[] realSendData;
    			do {
    				
    				if(length <= MTU_PAYLOAD_SIZE_LIMIT) {
    					realSendData = new byte[length];
    					System.arraycopy(sendData, unpackCount * MTU_PAYLOAD_SIZE_LIMIT, realSendData, 0, length);
    					
    					// update length value
    		            length = 0;
    				} else {
    					realSendData = new byte[MTU_PAYLOAD_SIZE_LIMIT];
    					System.arraycopy(sendData, unpackCount * MTU_PAYLOAD_SIZE_LIMIT, realSendData, 0, MTU_PAYLOAD_SIZE_LIMIT);
    					
    					// update length value
    		            length = length - MTU_PAYLOAD_SIZE_LIMIT;
    				}
    				
    				SendData(realSendData);
    				
    	            // unpack counter increase
    	            unpackCount++;
    	            
    	            
    			} while(length != 0);
    			
    			// set the unpack sending flag 
        		isUnpackSending = false;
        		
    			if(D) Log.d(TAG, "ThreadUnpackSend stop");
    		}//if(null != mTestCharacter)
    	}//run
    }
    
    private void SendData(byte[] realData) {
    	// for GKI get buffer error exit
		long timeCost = 0;
    	
        
		// initial the status
		writeCharacteristicError = true;
        
		while(true == writeCharacteristicError) {
			// mBluetoothGatt.getConnectionState(mDevice) can not use in thread, so we use a flag to
			// break the circulate when disconnect the connect
			if(true == isConnectDevice) {
				// for GKI get buffer error exit
				timeCost = System.currentTimeMillis();
				
				if(D) Log.e(TAG, "writeCharacteristicError start Status:" + writeCharacteristicError);
				
                // initial the status
                isWriteCharacteristicOk = false;
                
				// Set the send type(command)
				mTestCharacter.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
				// include the send data
				mTestCharacter.setValue(realData);
				// send the data
	            mBluetoothGatt.writeCharacteristic(mTestCharacter);
	            
	            // wait for characteristic write ok
                while(isWriteCharacteristicOk != true) {
                	if(false == isConnectDevice) {
                		if(D) Log.e(TAG, "break the circulate when disconnect the connect, no callback");
                		break;
                	}
                	
                	// for GKI get buffer error exit
                	// if 10 seconds no callback we think GKI get buffer error
                	if((System.currentTimeMillis() - timeCost)/1000 > 10) {
                		if(D) Log.e(TAG, "GKI get buffer error close the BT and exit");
                		// becouse GKI error, so we should close the bt
                        if (mBluetoothAdapter.isEnabled()) {
                            if(D) Log.d(TAG, "close bluetooth");
                            mBluetoothAdapter.disable();
                        }
                        
                        // close the activity
                        finish();
                	}
                };
                
                if(D) Log.e(TAG, "writeCharacteristicError stop Status:" + writeCharacteristicError);
			} else {
				// break the circulate when disconnect the connect
				if(D) Log.e(TAG, "break the circulate when disconnect the connect, write error");
				break;
			}
		}
		
		if(D) Log.d(TAG, "send data is: " + Arrays.toString(realData));
		
		// update tx counter, only write ok then update the counter
		if(false == writeCharacteristicError) {
			mTxCounter = mTxCounter + realData.length;
		}
		
		// send the msg, here may send less times MSG, so we use the StringBuildler
    	Message message = new Message();
        message.what = MSG_EDIT_TX_STRING_UPDATE;
        handler.sendMessage(message);
        
    }
    
    // set the connect state
    private void SetConnectionState(GattConnectState state) {
    	// Update the connect state
    	mConnectionState = state;
    	
    	// send the msg
    	Message message = new Message();
        message.what = MSG_CONNECTION_STATE_UPDATE;
        handler.sendMessage(message);
    }
    
    /*geyun send ccc---------notify characteristic*/
    private void enableNotification(final BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
    {
        if(D) Log.i(TAG, "geyun write CCC    +");
        gatt.setCharacteristicNotification(characteristic, true);
        // enable remote notification
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
        if(D) Log.i(TAG, "geyun write CCC   -");
    }
}
