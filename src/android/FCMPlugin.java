package com.gae.scaffolder.plugin;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.iid.FirebaseInstanceId;

import java.util.Map;
import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class FCMPlugin extends CordovaPlugin {
 
	private static final String TAG = "FCMPlugin";
	public static String nativeCallState = "";
	public String cenas = "";
	
	public static CordovaWebView gWebView;
	public static String notificationCallBack = "FCMPlugin.onNotificationReceived";
	public static String tokenRefreshCallBack = "FCMPlugin.onTokenRefreshReceived";
	public static Boolean notificationCallBackReady = false;
	
	public static Map<String, Object> lastPush = null;
	private static boolean isPaused = false, isResumed = false, isDestroyed = false;
	
	CallStateListener listener;

	public FCMPlugin() {}
	
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		gWebView = webView;
		Log.d(TAG, "==> FCMPlugin initialize");
		FirebaseMessaging.getInstance().subscribeToTopic("android");
		FirebaseMessaging.getInstance().subscribeToTopic("all");
	}
	 
	public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {

		Log.d(TAG,"==> FCMPlugin execute: "+ action);

		//listener for telephony
		prepareListener();

		try{
			// READY //
			if (action.equals("ready")) {
				//
				callbackContext.success();
			}
			// GET TOKEN //
			else if (action.equals("getToken")) {
				cordova.getActivity().runOnUiThread(new Runnable() {
					public void run() {
						try{
							String token = FirebaseInstanceId.getInstance().getToken();
							callbackContext.success( FirebaseInstanceId.getInstance().getToken() );
							Log.d(TAG,"\tToken: "+ token);
						}catch(Exception e){
							Log.d(TAG,"\tError retrieving token");
						}
					}
				});
			}
			// NOTIFICATION CALLBACK REGISTER //
			else if (action.equals("registerNotification")) {
				notificationCallBackReady = true;
				cordova.getActivity().runOnUiThread(new Runnable() {
					public void run() {
						if(lastPush != null) FCMPlugin.sendPushPayload( cordova.getActivity().getApplicationContext(), lastPush );
						lastPush = null;
						callbackContext.success();
					}
				});
			}
			// UN/SUBSCRIBE TOPICS //
			else if (action.equals("subscribeToTopic")) {
				cordova.getThreadPool().execute(new Runnable() {
					public void run() {
						try{
							FirebaseMessaging.getInstance().subscribeToTopic( args.getString(0) );
							callbackContext.success();
						}catch(Exception e){
							callbackContext.error(e.getMessage());
						}
					}
				});
			}
			else if (action.equals("unsubscribeFromTopic")) {
				cordova.getThreadPool().execute(new Runnable() {
					public void run() {
						try{
							FirebaseMessaging.getInstance().unsubscribeFromTopic( args.getString(0) );
							callbackContext.success();
						}catch(Exception e){
							callbackContext.error(e.getMessage());
						}
					}
				});
			}
			else{
				callbackContext.error("Method not found");
				return false;
			}
		}catch(Exception e){
			Log.d(TAG, "ERROR: onPluginAction: " + e.getMessage());
			callbackContext.error(e.getMessage());
			return false;
		}
		
		//cordova.getThreadPool().execute(new Runnable() {
		//	public void run() {
		//	  //
		//	}
		//});
		
		//cordova.getActivity().runOnUiThread(new Runnable() {
        //    public void run() {
        //      //
        //    }
        //});
		return true;
	}

	private void prepareListener() {
        if (listener == null) {
            listener = new CallStateListener();
            TelephonyManager TelephonyMgr = (TelephonyManager) cordova.getActivity().getSystemService(Context.TELEPHONY_SERVICE);
			TelephonyMgr.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }	
	
	public static void sendPushPayload(Context context, Map<String, Object> payload) {
		Log.d(TAG, "==> FCMPlugin sendPushPayload");
		Log.d(TAG, "\tnotificationCallBackReady: " + notificationCallBackReady);
		Log.d(TAG, "\tgWebView: " + gWebView);

	    try {
			JSONObject jo = new JSONObject();
			String callBack;
			for (String key: payload.keySet()) {
				if (payload.get(key).toString().equals("CALL") && isResumed != true) {
					if (isPaused == true || isDestroyed == true) {
						jo.put("isPaused", true);
					}
					Log.i(TAG, "PACKAGE NAME CONTEXT ---- " + context.getPackageName());
					if (isDestroyed == true) {
						Log.d(TAG, "STARTING ACTIVITY");
						Intent startIntent = new Intent(context, FCMPluginActivity.class);
						startIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
						context.startActivity(startIntent); 
					}
					else if (isPaused == true) {
						if (nativeCallState != "OFFHOOK") {
							Log.d(TAG, "RESUMING ACTIVITY");
							Intent startIntent = new Intent(context, FCMPluginActivity.class);
							startIntent.setAction(Intent.ACTION_MAIN);
							startIntent.addCategory(Intent.CATEGORY_LAUNCHER);
							context.startActivity(startIntent);
						}
					}					
				}
				jo.put(key, payload.get(key));
			}
			callBack = "javascript:" + notificationCallBack + "(" + jo.toString() + ")";
			if (notificationCallBackReady && gWebView != null) {
				Log.d(TAG, "\tSent PUSH to view: " + callBack);
				gWebView.sendJavascript(callBack);
			}else {
				Log.d(TAG, "\tView not ready. SAVED NOTIFICATION: " + callBack);
				lastPush = payload;
			}
		} catch (Exception e) {
			Log.d(TAG, "\tERROR sendPushToView. SAVED NOTIFICATION: " + e.getMessage());
			lastPush = payload;
		}
	}

	// public static void sendTokenRefresh(String token) {
	// 	Log.d(TAG, "==> FCMPlugin sendRefreshToken");
	//   try {
	// 		String callBack = "javascript:" + tokenRefreshCallBack + "('" + token + "')";
	// 		gWebView.sendJavascript(callBack);
	// 	} catch (Exception e) {
	// 		Log.d(TAG, "\tERROR sendRefreshToken: " + e.getMessage());
	// 	}
	// }
  
//   @Override
// 	public void onDestroy() {
// 		gWebView = null;
// 		notificationCallBackReady = false;
// 	}

	@Override
	public void onPause(boolean multitasking) {
		Log.i(TAG, "onPause");
		isPaused = true;
		isResumed = false;
		isDestroyed = false;
	}

	@Override
	public void onResume(boolean multitasking) {
		Log.i(TAG, "onResume");
		isResumed = true;
		isPaused = false;
		isDestroyed = false;
	}

	public void onDestroy() {
		Log.i(TAG, "onDestroy");
		super.onDestroy();
		isDestroyed = true;
		isPaused = false;
		isResumed = false;
		gWebView = null;
 		notificationCallBackReady = false;
	}
} 


class CallStateListener extends PhoneStateListener {

    public void onCallStateChanged(int state, String incomingNumber) {
		super.onCallStateChanged(state, incomingNumber);
		
        String msg = "";

        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
            msg = "IDLE";
            break;

            case TelephonyManager.CALL_STATE_OFFHOOK:
            msg = "OFFHOOK";
            break;

            case TelephonyManager.CALL_STATE_RINGING:
            msg = "RINGING";
            break;
        }
		FCMPlugin.nativeCallState = msg;

    }
}