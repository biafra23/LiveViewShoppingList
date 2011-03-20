package org.openintents.extensions.liveviewshopping;

import org.openintents.shopping.Shopping;
import org.openintents.shopping.Shopping.Contains;
import org.openintents.shopping.Shopping.ContainsFull;
import org.openintents.shopping.provider.ShoppingUtils;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;

import com.sonyericsson.extras.liveware.plugins.AbstractPluginService;
import com.sonyericsson.extras.liveware.plugins.LiveViewAdapter;
import com.sonyericsson.extras.liveware.plugins.PluginConstants;
import com.sonyericsson.extras.liveware.plugins.PluginUtils;

public class LVShoppingService extends AbstractPluginService {

    private int mPos = 0;

    public class MyContentObserver extends ContentObserver {

        public MyContentObserver() {

            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            sendShoppingItem(mPos);
        }

    }

    private static final String LOG_TAG = "LVShoppingService";
    private static final String OPEN_IN_PHONE_ACTION_RETRY = "retry";
    private Handler mHandler;
    private boolean mAllowedToBind;
    private long mShoppingListId;

//    private Item mCurrentItem;

    private ContentObserver mContentObserver = new MyContentObserver();
    private Cursor mExistingItems;
    private String mSentItemId;

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        // ...
        // Do plugin specifics.
        // ...
        Log.i(PluginConstants.LOG_TAG, "Starting Service...");
        Log.i(PluginConstants.LOG_TAG, "Starting Service...");

        startWork();
        Log.i(LOG_TAG, "after startWork()");

        new Handler().postAtTime(new Runnable() {

            @Override
            public void run() {
                Log.i(LOG_TAG, "run() called");
                sendShoppingItem(mPos);

            }
        }, System.currentTimeMillis() + 1000 /** 60 * 2*/);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopWork();
    }

    protected void startWork() {
        // Check if plugin is enabled.
//        if (mSharedPreferences.getBoolean(PluginConstants.PREFERENCES_PLUGIN_ENABLED, false)) {

        Log.i(LOG_TAG, "Plugin enabled");

        // Do stuff.
        if (mHandler == null) {
            mHandler = new Handler();
        }

        // TODO use pick list and preferences
        mShoppingListId = ShoppingUtils.getFirstList(this);

        Log.d(LOG_TAG, "mShoppingListId: " + mShoppingListId);

        refreshCursor();
        // sendShoppingItem();

        getContentResolver().registerContentObserver(
                Shopping.Contains.CONTENT_URI, true, mContentObserver);
        getContentResolver().registerContentObserver(
                Shopping.ContainsFull.CONTENT_URI, true, mContentObserver);
        getContentResolver().registerContentObserver(
                Shopping.Items.CONTENT_URI, true, mContentObserver);
//        } else {
//            Log.e(LOG_TAG, "Plugin disabled");
//
//        }

        Log.d(LOG_TAG, "end of startWork() reached");
    }

//    private void sendFirstShoppingItem() {
//        Log.d(LOG_TAG, "sendFirstShoppingItem()");
//        Log.d(LOG_TAG, "sendFirstShoppingItem()");
//        Log.d(LOG_TAG, "sendFirstShoppingItem()");
//
//        sendShoppingItem(0);
//    }


    private Item getItem(int pos) {

        Item result = null;
        if (mExistingItems != null) {
            mExistingItems.requery();
            mExistingItems.moveToPosition(pos);
            if (mExistingItems.getCount() > 0) {

                result = new Item(

                        mExistingItems.getString(0),
                        mExistingItems.getString(1),
                        mExistingItems.getString(2),
                        mExistingItems.getInt(3),
//                        mExistingItems.getInt(4),
                        mExistingItems.getInt(5)
                        
                );
            }
        }
        return result;
    }

    private void sendShoppingItem(int pos) {


        boolean sent = false;
        if (mExistingItems != null) {
            mExistingItems.requery();
            mExistingItems.moveToPosition(pos);

            if (mExistingItems.getCount() > 0) {

                mSentItemId = mExistingItems.getString(0);
                String item = mExistingItems.getString(1);
                String tags = mExistingItems.getString(2);

                String quantity = mExistingItems.getString(3);
                int status = mExistingItems.getInt(5);

                Uri boughtUri = Uri.withAppendedPath(Contains.CONTENT_URI, mExistingItems.getString(0));

                Log.v(LOG_TAG, "sending:" + item);
                Log.v(LOG_TAG, "sending uri:" + boughtUri.toString());


                if (!TextUtils.isEmpty(quantity)) {
                    item = quantity + " x " + item;
                }

                Log.d(LOG_TAG, "status: [" + status + "]");

                if (status == 1) {
                    item = "\u2610 " + item;
                } else {
//                    item = "\u2611 " + item;
                    item = "\u2713 " + item;
                }
                sendItemTextBitmap(mLiveViewAdapter, mPluginId, tags, item);
                sent = true;

                // mJerryService.sendAnnounce(mPluginId, mMenuIcon, quantity
                // + " " + tags, item, System.currentTimeMillis(), boughtUri
                // .toString());
                // mJerryService.sendImage(mPluginId, 1, 1, mMenuIcon);
            }
        }

        if (!sent) {
            sendTextBitmap(mLiveViewAdapter, mPluginId,
                    "no entries");
            mLiveViewAdapter.sendAnnounce(mPluginId, mMenuIcon,
                    getString(R.string.info), getString(R.string.no_item),
                    System.currentTimeMillis(), OPEN_IN_PHONE_ACTION_RETRY);
            mSentItemId = null;
        }
    }


    private void sendTextBitmap(LiveViewAdapter mLiveViewAdapter,
                                int mPluginId, String txt) {
        // Empty bitmap and link the canvas to it
        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.RGB_565);
        } catch (IllegalArgumentException e) {
            return;
        }

        Canvas canvas = new Canvas(bitmap);

        // Set the text properties in the canvas
        TextPaint textPaint = new TextPaint();
        textPaint.setTextSize(18);
        textPaint.setColor(Color.WHITE);

        // Create the text layout and draw it to the canvas
        Layout textLayout = new StaticLayout(txt, textPaint, 128,
                Layout.Alignment.ALIGN_CENTER, 1, 1, false);
        textLayout.draw(canvas);


        try {
            mLiveViewAdapter.sendImageAsBitmap(mPluginId,
                    PluginUtils.centerX(bitmap), PluginUtils.centerY(bitmap),
                    bitmap);
        } catch (Exception e) {
            Log.d(PluginConstants.LOG_TAG, "Failed to send bitmap", e);
        }

    }

    private void sendItemTextBitmap(LiveViewAdapter mLiveViewAdapter,
                                    int mPluginId, String tags, String item) {

        Log.d(LOG_TAG, "sendItemTextBitmap(): item: " + item);
        // Empty bitmap and link the canvas to it
        Bitmap bitmap = null;
        try {

            bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.RGB_565);

        } catch (IllegalArgumentException e) {
            return;
        }

        Canvas canvas = new Canvas(bitmap);

        // Set the text properties in the canvas
        TextPaint textPaint = new TextPaint();
        textPaint.setTextSize(14);
        textPaint.setAntiAlias(true);
        textPaint.setColor(Color.WHITE);

        // Create the text layout and draw it to the canvas
        Layout textLayout = new StaticLayout(item, textPaint, 128,
                Layout.Alignment.ALIGN_NORMAL, 1, 1, false);
        textLayout.draw(canvas);

//        textLayout.getWidth();
//        textLayout.getHeight();

        if (!TextUtils.isEmpty(tags)) {
            // now smaller text
            textPaint.setTextSize(10);
            canvas.translate(1, textLayout.getHeight());
            // Create the text layout and draw it to the canvas
            textLayout = new StaticLayout(tags, textPaint, 128,
                    Layout.Alignment.ALIGN_NORMAL, 1, 1, false);
            textLayout.draw(canvas);
        }

        try {
            mLiveViewAdapter.sendImageAsBitmap(mPluginId,
                    1, 1,
                    bitmap);
        } catch (Exception e) {
            Log.d(PluginConstants.LOG_TAG, "Failed to send bitmap", e);
        }

    }

    private void refreshCursor() {

        Log.d(LOG_TAG, "refreshCursor() called");
        try {
            if (mExistingItems != null) {
                mExistingItems.close();
            }
            String sortOrder = "contains.modified_date"; //

            mExistingItems = getContentResolver().query(

                    ContainsFull.CONTENT_URI,
                    new String[]{ContainsFull._ID, ContainsFull.ITEM_NAME,
                            ContainsFull.ITEM_TAGS, ContainsFull.QUANTITY,
                            ContainsFull.MODIFIED_DATE, ContainsFull.STATUS},
                    ContainsFull.LIST_ID + " = ? ",
//                            + " AND " + ContainsFull.STATUS
//                            + " = " + Status.WANT_TO_BUY,
                    new String[]{String.valueOf(mShoppingListId)}, null);


        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    protected void toggleBoughtItem() {
        if (mSentItemId != null) {

            Uri uri = Uri.withAppendedPath(Contains.CONTENT_URI, mSentItemId);
            toggleBoughtItem(uri.toString());
        }

    }

    protected void toggleBoughtItem2(String contentUri) {

        if (!OPEN_IN_PHONE_ACTION_RETRY.equals(contentUri)) {

            Log.d(LOG_TAG, "getItem(mPos).bought: " + getItem(mPos).bought);

            ContentValues values = new ContentValues();

            values.put(Shopping.Contains.STATUS, Shopping.Status.BOUGHT);
            int count = getContentResolver().update(
                    Uri.parse(contentUri), values, null, null);
            Log.v(LOG_TAG, count + " item(s) updated.");

        }

        sendShoppingItem(mPos);
    }

    protected void toggleBoughtItem(String contentUri) {

        if (!OPEN_IN_PHONE_ACTION_RETRY.equals(contentUri)) {

            ContentValues values = new ContentValues();


            if (getItem(mPos).bought) {

                Log.d(LOG_TAG, "getItem(mPos).bought");

                values.put(Shopping.Contains.STATUS, Shopping.Status.WANT_TO_BUY);

            } else {

                Log.d(LOG_TAG, "NOT getItem(mPos).bought");

                values.put(Shopping.Contains.STATUS, Shopping.Status.BOUGHT);

            }

            Log.d(LOG_TAG, "           values: " + values);
            Log.d(LOG_TAG, "contentUri: " + contentUri);

            int count = getContentResolver().update(Uri.parse(contentUri), values, null, null);
            Log.v(LOG_TAG, count + " item(s) updated.");

        }

        sendShoppingItem(mPos);
    }

    /**
     * Must be implemented. Stops plugin work, if any.
     */
    protected void stopWork() {
        mAllowedToBind = false;

    }

    /**
     * Must be implemented.
     * <p/>
     * PluginService has done connection and registering to the Jerry Service.
     * <p/>
     * If needed, do additional actions here, e.g. starting any worker that is
     * needed.
     */
    protected void onServiceConnectedExtended(ComponentName className,
                                              IBinder service) {
        mAllowedToBind = true;

    }

    /**
     * Must be implemented.
     * <p/>
     * PluginService has done disconnection from Jerry and service has been
     * stopped.
     * <p/>
     * Do any additional actions here.
     */
    protected void onServiceDisconnectedExtended(ComponentName className) {
        mAllowedToBind = false;

    }

    /**
     * Must be implemented.
     * <p/>
     * PluginService has checked if plugin has been enabled/disabled.
     * <p/>
     * The shared preferences has been changed. Take actions needed.
     */
    protected void onSharedPreferenceChangedExtended(SharedPreferences prefs,
                                                     String key) {
        // no additional preferences, maybe the shopping list (in the future)
    }

    @Override
    public IBinder onBind(Intent intent) {
        // we don't have an interface to bind to
        return null;
    }

    @Override
    protected boolean isSandboxPlugin() {
        return true;
    }

    @Override
    protected void startPlugin() {
        Log.d(LOG_TAG, "startPlugin");

        // Check if plugin is enabled.
        if (mSharedPreferences.getBoolean(PluginConstants.PREFERENCES_PLUGIN_ENABLED, false)) {
            sendShoppingItem(mPos);
        }

    }

    @Override
    protected void stopPlugin() {
        Log.d(PluginConstants.LOG_TAG, "stopPlugin");
        stopWork();

    }

    /**
     * @param buttonType  - up, left, down, right, select
     * @param doublepress
     * @param longpress
     */
    @Override
    protected void button(String buttonType, boolean doublepress,
                          boolean longpress) {

        Log.d(PluginConstants.LOG_TAG, "button - type " + buttonType
                + ", doublepress " + doublepress + ", longpress " + longpress);

        if ("select".equals(buttonType)) {
            toggleBoughtItem();
        }

        if (mExistingItems != null) {


            if ("up".equals(buttonType)) {

                mPos += mExistingItems.getCount() - 1;

            } else if ("down".equals(buttonType)) {

                mPos++;
            }
            mPos %= mExistingItems.getCount();

            sendShoppingItem(mPos);

        }
    }

    @Override
    protected void displayCaps(int displayWidthPx, int displayHeigthPx) {
        Log.d(PluginConstants.LOG_TAG, "displayCaps - width " + displayWidthPx
                + ", height " + displayHeigthPx);

    }

    @Override
    protected void onUnregistered() throws RemoteException {
        // TODO Auto-generated method stub

    }

    @Override
    protected void openInPhone(String openInPhoneAction) {
        Log.d(PluginConstants.LOG_TAG, "openInPhone: " + openInPhoneAction);
        toggleBoughtItem(openInPhoneAction);
        sendShoppingItem(mPos);

    }

    @Override
    protected void screenMode(int mode) {
        // TODO Auto-generated method stub

    }

    private class Item {

        private Item(String id, String item, String tags, int quantity, int bought) {
            this.item = item;
            this.tags = tags;
            this.quantity = quantity;
            if (bought == 1) {

                this.bought = false;
            } else {

                this.bought = true;

            }
        }

        public String id;
        public String item;
        public String tags;
        public int quantity;
        public boolean bought;
    }
}
