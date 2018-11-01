package com.bluetooth.unvarnishedtransmission;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class DeviceSearchActivity extends Activity {
	// Debug 
	private static final String TAG = "DeviceSearchActivity";
	private boolean D = true;
	
	// view
	private ListView mlvBTDevice;
	private Button mbtnReturn;
	private Button mbtnRefresh;
	private ProgressBar mprgBar;
	
	// adapter for le device list
	private LeDeviceListAdapter mLeDeviceListAdapter;
	
	// bluetooth control
    private BluetoothAdapter mBluetoothAdapter;
    
    // Intent request codes
 	private static final int REQUEST_ENABLE_BT = 1;
 	
 	// Stops scanning after 10 seconds
 	private static final long SCAN_PERIOD = 10000;
    
    // status of ble scan
    private boolean mScanning;
    
    private Handler mHandler;
    
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devicesearch);
        if(D) Log.d(TAG, "-------onCreate-------");
        
        mHandler = new Handler();

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
            DeviceSearchActivity.this.finish();
        }
        
        // ensure that Bluetooth exists
        if (!EnsureBleExist()) {
        	if(D) Log.e(TAG, "This device do not support BLE");
        	finish();
        }
        
        // device list initial
        mlvBTDevice = (ListView)findViewById(R.id.lvBTDevices);
        mlvBTDevice.setOnItemClickListener(new ItemClickEvent());
        
        // return button initial
        mbtnReturn = (Button)findViewById(R.id.back);
        mbtnReturn.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				if(D) Log.d(TAG, "return to the main activity");
				
				// stop the le scan when close the activity
		    	if(true == mScanning) {
		    		ScanLeDevice(false);
		    	}
		    	
				finish();
			}
		});
        
        // refresh button initial
        mbtnRefresh = (Button)findViewById(R.id.refresh);
        mbtnRefresh.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				if(D) Log.d(TAG, "refresh the data list");
				
				// clear the adapter
		    	mLeDeviceListAdapter.clear();
		    	
				// start the le scan
		    	if(false == mScanning) {
		    		ScanLeDevice(true);
		    	}
		    	
			}
		});
        mbtnRefresh.setVisibility(View.GONE);
        
        
        // process bar initial
        mprgBar = (ProgressBar)findViewById(R.id.progress_bar);
        mprgBar.setVisibility(View.GONE);
        
	}
	
	// judge the support of ble in android  
    private boolean EnsureBleExist() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "������֧��BLE", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
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
        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        // set the adapter
        mlvBTDevice.setAdapter(mLeDeviceListAdapter);
        // start le scan when activity is on
        ScanLeDevice(true);
    }
    
    
    private class ItemClickEvent implements AdapterView.OnItemClickListener {

		@Override
		public void onItemClick(AdapterView<?> l, View v, int position,
				long id) {
			// TODO Auto-generated method stub
			
			final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
			if(D) Log.i(TAG, "select a device, the name is " + device.getName() + ", addr is " + device.getAddress());
	        if (device == null) return;
	        
	        // store the information for result
	        Intent intent=new Intent();
	        //intent.putExtra(UnvarnishedTransmissionActivity.EXTRAS_DEVICE_NAME, device.getName());
	        //intent.putExtra(UnvarnishedTransmissionActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
	        intent.putExtra(UnvarnishedTransmissionActivity.EXTRAS_DEVICE, device);
	        setResult(RESULT_OK, intent);
	        
	        if(D) Log.d(TAG, "store information ok");
	        
	        // close the activity
	     	finish();
		}
    }
    
    @Override
    protected void onPause() {
    	if(D) Log.d(TAG, "-------onPause-------");
    	
    	// stop the le scan when close the activity
    	if(true == mScanning) {
    		ScanLeDevice(false);
    	}
    	
    	// clear the adapter
    	mLeDeviceListAdapter.clear();
    	super.onPause();
    }
    
    // Control le scan
    private void ScanLeDevice(boolean enable) { 	
    	//remove the stop le scan runnable
    	mHandler.removeCallbacks(mStopLeScan);
    	// control the process bar and le scan
    	if(true == enable) {
    		// avoid repetition operator
    		if(mScanning == enable) {
    			if(D) Log.e(TAG, "the le scan is already on");
    			return;
    		}
    		// Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(mStopLeScan, SCAN_PERIOD);
            if(D) Log.d(TAG, "start the le scan, on time is " + SCAN_PERIOD + "ms");
    		mBluetoothAdapter.startLeScan(mLeScanCallback);
    		
    		mprgBar.setVisibility(View.VISIBLE);
    		mbtnRefresh.setVisibility(View.GONE);
    	} else {
    		// avoid repetition operator
    		if(mScanning == enable) {
    			if(D) Log.e(TAG, "the le scan is already off");
    			return;
    		}
    		if(D) Log.d(TAG, "stop the le scan");
    		mBluetoothAdapter.stopLeScan(mLeScanCallback);
    		
    		mprgBar.setVisibility(View.GONE);
    		mbtnRefresh.setVisibility(View.VISIBLE);
    	}
    	// update le scan status
    	mScanning = enable;
    }

    // Stops scanning after a pre-defined scan period.
    Runnable mStopLeScan = new Runnable() {
        @Override
        public void run() {
        	if(D) Log.d(TAG, "le delay time reached");
        	// Stop le scan, delay SCAN_PERIOD ms
        	ScanLeDevice(false);
        }
    };
    
    private LeScanCallback mLeScanCallback = new LeScanCallback() {

		@Override
		public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
			// TODO Auto-generated method stub
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					// TODO Auto-generated method stub
					// add the device to the adaper
					if(D) Log.d(TAG, "find a ble device, name is " + device.getName()
										+ ", addr is " + device.getAddress() 
										+ "rssi is " + String.valueOf(rssi));
					mLeDeviceListAdapter.addDevice(rssi, device);
                    mLeDeviceListAdapter.notifyDataSetChanged();
				} 
			});
		}
    };
    
    public void onActivityResult(int requestCode, int resultCode, Intent data)  { 
    	if(D) Log.d(TAG, "-------onActivityResult-------");
    	if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        
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
        super.onActivityResult(requestCode, resultCode, data);
    }  
    
 // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private ArrayList<Integer> mRssi;
        //private HashMap<Integer, BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mRssi = new ArrayList<Integer>();
            mInflator = DeviceSearchActivity.this.getLayoutInflater();
        }

        public void addDevice(int rssi, BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
            	mLeDevices.add(device);
            	mRssi.add(rssi);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                viewHolder.deviceRssi = (TextView) view.findViewById(R.id.device_rssi);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }
            
            // get the device information
            BluetoothDevice device = mLeDevices.get(i);
            
            // set the list item show information
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText("unknown device");
            viewHolder.deviceAddress.setText(device.getAddress());
            viewHolder.deviceRssi.setText(String.valueOf(mRssi.get(i)));
            return view;
        }
    }
    
    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        TextView deviceRssi;
    }

}
