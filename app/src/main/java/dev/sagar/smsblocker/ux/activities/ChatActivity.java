package dev.sagar.smsblocker.ux.activities;

import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/*import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;*/

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import dev.sagar.smsblocker.Permission;
import dev.sagar.smsblocker.R;
import dev.sagar.smsblocker.tech.RequestCode;
import dev.sagar.smsblocker.tech.beans.Contact;
import dev.sagar.smsblocker.tech.beans.SIM;
import dev.sagar.smsblocker.tech.broadcastreceivers.LocalSMSDeliveredReceiver;
import dev.sagar.smsblocker.tech.broadcastreceivers.LocalSMSReceivedReceiver;
import dev.sagar.smsblocker.tech.broadcastreceivers.LocalSMSSentReceiver;
import dev.sagar.smsblocker.tech.datastructures.IndexedHashMap;
import dev.sagar.smsblocker.tech.exceptions.NoSuchContactException;
import dev.sagar.smsblocker.tech.exceptions.ReadContactPermissionException;
import dev.sagar.smsblocker.tech.handlers.httprequest.translate.TranslateService;
import dev.sagar.smsblocker.tech.utils.AnalyticsUtil;
import dev.sagar.smsblocker.tech.utils.ContactUtilSingleton;
import dev.sagar.smsblocker.tech.utils.LogUtil;
import dev.sagar.smsblocker.tech.utils.PhoneUtilsSingleton;
import dev.sagar.smsblocker.tech.utils.PictureUtilSingleton;
import dev.sagar.smsblocker.tech.utils.TelephonyUtilSingleton;
import dev.sagar.smsblocker.ux.adapters.RVChatAdapter;
import dev.sagar.smsblocker.tech.beans.SMS;
import dev.sagar.smsblocker.tech.utils.InboxUtil;
import dev.sagar.smsblocker.tech.utils.PermissionUtilSingleton;
import dev.sagar.smsblocker.tech.utils.SMSUtil;
import dev.sagar.smsblocker.ux.listeners.actionmodecallbacks.AMCallbackThread;

public class ChatActivity extends AppCompatActivity implements
        RVChatAdapter.Callback,
        LocalSMSReceivedReceiver.Callback,
        LocalSMSSentReceiver.Callback,
        LocalSMSDeliveredReceiver.Callback,
        InboxUtil.Callback{

    //Log Initiate
    private LogUtil log = new LogUtil(this.getClass().getName());

    //Constants
    public static final String KEY_THREAD_ID = "THREAD_ID"; //This is required to read SMS
    public static final String KEY_ADDRESS = "ADDRESS_ID"; //This is required to show Contact related details
    public static final String KEY_SMS_ID = "SMS_ID";
    final String[] ALL_PERMISSIONS = Permission.ALL;
    final String READ_SMS = Permission.READ_SMS;
    final String RECEIVE_SMS = Permission.RECEIVE_SMS;
    final String SEND_SMS = Permission.SEND_SMS;
    final String READ_CONTACTS = Permission.READ_CONTACTS;

    //View
    private RecyclerView recyclerView;
    private ImageButton btnSend;
    private EditText etMsg;
    private TextView tvHeader;
    private View holderBodyET, holderLoader, holderMain;
    private View tvReplyNotSupported;
    private TextView tvSim;

    //Java Android
    private RVChatAdapter adapter;
    private AMCallbackThread amCallback;
    private Toolbar toolbar;
    //TODO Analytics test
    /*private GoogleAnalytics sAnalytics;
    private Tracker sTracker;*/


    //Java Core
    private IndexedHashMap<String, SMS> smses = new IndexedHashMap<>();
    private String threadId;
    private String address;
    private InboxUtil inboxUtil = null;
    private SMSUtil smsUtil;
    private final int REQUEST_CODE_ALL_PERMISSIONS = RequestCode.ALL_PERMISSIONS;
    private PermissionUtilSingleton permUtil = PermissionUtilSingleton.getInstance();
    private LocalSMSReceivedReceiver smsReceivedReceiver = null;
    private LocalSMSDeliveredReceiver smsDeliveredReceiver = null;
    private LocalSMSSentReceiver smsSentReceiver = null;
    private ContactUtilSingleton contactUtil = ContactUtilSingleton.getInstance();
    private TelephonyUtilSingleton telephonyUtils = TelephonyUtilSingleton.getInstance();

    //Flag
    private boolean alreadyHighlighted = false;

    private void showMsgs(){
        inboxUtil.getAllSMSFromTo(this.address);
        holderMain.setVisibility(View.GONE);
        holderLoader.setVisibility(View.VISIBLE);
    }

    private void hideMsgs(){

    }

    private void updateActionBar(){
        final String methodName =  "updateActionBar()";
        log.justEntered(methodName);

        String contact = null;
        Uri dpUri = null;
        try {
            contact = contactUtil.getContactName(this, this.address);
            dpUri = contactUtil.getPictureUri(this, this.address);
        } catch (ReadContactPermissionException e) {
            e.printStackTrace();
            contact = this.address;
        }

        log.info(methodName, "Setting contactName: "+contact);
        toolbar.setTitle(contact);
        if(contact!=null && !contact.equals(this.address)) {
            toolbar.setSubtitle(this.address);
        }
        else
            toolbar.setSubtitle(null);


        try {
            log.info(methodName, "Setting Contact picture in action bar");
            Drawable drawable = PictureUtilSingleton.getInstance().getPictureThumbDrawable(this, dpUri);
            log.info(methodName, "Received picture: "+drawable);
            getSupportActionBar().setLogo(drawable);

        } catch (NullPointerException e){
            log.info(methodName, "No picture for Contact..");
        }


        log.returning(methodName);
    }

    private SMS sendMsg(){
        final String methodName =  "sendMsg()";
        log.justEntered(methodName);

        String msg = etMsg.getText().toString().trim();
        String phoneNo = this.address;
        SMS newSMS = smsUtil.sendSMS(phoneNo, msg);

        log.returning(methodName);
        return newSMS;
    }

    public void registerReceivers(){
        final String methodName =  "registerReceivers()";
        log.justEntered(methodName);

        registerReceiver(smsReceivedReceiver, new IntentFilter(LocalSMSReceivedReceiver.EVENT_RECEIVED));
        smsReceivedReceiver.isRegistered = true;
        registerReceiver(smsSentReceiver, new IntentFilter(LocalSMSSentReceiver.EVENT_SENT));
        smsSentReceiver.isRegistered = true;
        registerReceiver(smsDeliveredReceiver, new IntentFilter(LocalSMSDeliveredReceiver.EVENT_DELIVERED));
        smsDeliveredReceiver.isRegistered = true;

        log.returning(methodName);
    }

    public void unregisterReceivers(){
        final String methodName =  "unregisterReceivers()";
        log.justEntered(methodName);

        log.info(methodName, "Unregistering Services... ");
        if(smsReceivedReceiver.isRegistered)
            unregisterReceiver(smsReceivedReceiver);
        smsReceivedReceiver.isRegistered = false;
        log.info(methodName, "Unregistered Received Receiver... ");

        if(smsSentReceiver.isRegistered)
            unregisterReceiver(smsSentReceiver);
        smsSentReceiver.isRegistered = false;
        log.info(methodName, "Unregistered Sent Receiver... ");

        if(smsDeliveredReceiver.isRegistered)
            unregisterReceiver(smsDeliveredReceiver);
        smsDeliveredReceiver.isRegistered = false;
        log.info(methodName, "Unregistered Delivery Receiver... ");

        log.returning(methodName);
    }

    public void smsSendUpdate(SMS newSMS) {
        final String methodName =  "smsSendUpdate()";
        log.justEntered(methodName);

        //add item in list
        String id = newSMS.getId();
        smses.put(id, newSMS, 0);

        //notify adapter that item is inserted
        adapter.notifyItemInserted(0);
        recyclerView.scrollToPosition(0); //Scroll to bottom

        log.returning(methodName);
    }

    public void addSMSinUI(SMS sms){
        final String methodName =  "addSMSinUI()";
        log.justEntered(methodName);

        String id = sms.getId();
        smses.put(id, sms, 0); //Adding Element to first in List
        adapter.notifyDataSetChanged();
        //recyclerView.scrollToPosition(smses.size()-1);

        log.returning(methodName);
    }

    public void showContact(){
        final String methodName =  "showDialer()";
        log.justEntered(methodName);

        String contactID = null;
        try {
            Contact contact = ContactUtilSingleton.getInstance().getContact(this, this.address);
            contactID = contact.getId();
        } catch (ReadContactPermissionException | NoSuchContactException e) {
            e.printStackTrace();
        }

        if(contactID!=null) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(contactID));
            intent.setData(uri);
            startActivity(intent);
        }

        log.returning(methodName);
    }

    public void showDialer(){
        final String methodName =  "showDialer()";
        log.justEntered(methodName);

        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:"+this.address));
        startActivity(intent);

        log.returning(methodName);
    }

    public void highlightSMS(){
        final String methodName =  "highlightSMS()";
        log.justEntered(methodName);

        Bundle basket = getIntent().getExtras();
        String smsId = basket.getString(KEY_SMS_ID);
        if(smsId!=null){
            //Find Position
            log.info(methodName, "Looking for position for SMS id: "+smsId);
            int pos = smses.indexOf(smsId);
            recyclerView.scrollToPosition(pos);
            adapter.highlightItem(pos);
        }
        else{
            log.info(methodName, "No special SMS to show");
        }

        log.returning(methodName);
    }

    private void init(){
        final String methodName =  "init()";
        log.justEntered(methodName);


        recyclerView = (RecyclerView) findViewById(R.id.lv_sms);
        btnSend = (ImageButton) findViewById(R.id.btn_send);
        etMsg = (EditText) findViewById(R.id.et_msg);
        holderBodyET = findViewById(R.id.holder_sms_et);
        tvReplyNotSupported = findViewById(R.id.tv_reply_not_supported);
        holderLoader = findViewById(R.id.holder_loader);
        holderMain = findViewById(R.id.holder_main);
        tvSim = (TextView) findViewById(R.id.tv_sim);

        if(inboxUtil == null) inboxUtil = new InboxUtil(this, this);
        smsUtil = new SMSUtil(this);
        smsReceivedReceiver = new LocalSMSReceivedReceiver(this);
        smsDeliveredReceiver = new LocalSMSDeliveredReceiver(this);
        smsSentReceiver = new LocalSMSSentReceiver(this);

        //From Previous Activity
        Bundle basket = getIntent().getExtras();
        this.threadId = basket.getString(KEY_THREAD_ID);
        this.address = basket.getString(KEY_ADDRESS);
        adapter = new RVChatAdapter(this, this, smses);
        amCallback = new AMCallbackThread(this, adapter);
        LinearLayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        mLayoutManager.setReverseLayout(true);
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setAdapter(adapter);

        log.returning(methodName);
    }

    private void addListeners(){
        final String methodName =  "addListeners()";
        log.debug(methodName, "Just Entered..");

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override

            public void onClick(View view) {
                String msg = etMsg.getText().toString().trim();
                if (TextUtils.isEmpty(msg)) {
                    Toast toast = Toast.makeText(getApplicationContext(), "Blank Message can not be send", Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                } else {
                    SMS newSMS = sendMsg();
                    smsSendUpdate(newSMS);
                }
                etMsg.setText("");
            }
        });

        log.debug(methodName, "Returning..");
    }


    //--- Activity Overriders Start ---
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String methodName =  "onCreate()";
        log.justEntered(methodName);
        AnalyticsUtil.start(this);

        setContentView(R.layout.activity_thread);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(""); //This is required otherwise title will not be populated
        setSupportActionBar(toolbar);

        //Set Action Bar Transparent
        Window window = this.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        init();
        updateActionBar();
        hideMsgs();
        addListeners();

        highlightSMS();

        log.returning(methodName);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        final String methodName =  "onRequestPermissionsResult()";
        log.justEntered(methodName);

        switch (requestCode) {
            case REQUEST_CODE_ALL_PERMISSIONS:
                boolean hasInboxPerm = permUtil.hasPermission(this, READ_CONTACTS);
                if(hasInboxPerm){
                    showMsgs();
                    updateActionBar();
                }
                else{
                    hideMsgs();
                    Toast.makeText(ChatActivity.this, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

        log.returning(methodName);
    }

    @Override
    protected void onStart() {
        final String methodName =  "onStart()";
        log.justEntered(methodName);

        boolean hasPermission = permUtil.hasPermission(this, READ_CONTACTS);
        if(hasPermission){
            showMsgs();
            registerReceivers();
        }
        else{
            permUtil.ask(this, ALL_PERMISSIONS, REQUEST_CODE_ALL_PERMISSIONS);
        }

        SIM currentSIM = telephonyUtils.getDefaultSmsSIM(this);
        int slot = currentSIM.getSlotNo()+1;  //As slot 0 indexed
        tvSim.setText(String.valueOf(slot));

        super.onStart();

        log.returning(methodName);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.transition_fade_in, R.anim.transition_fade_out);
    }

    @Override
    protected void onPause() {
        final String methodName =  "onPause()";
        log.justEntered(methodName);

        //markSMSRead
        inboxUtil.markSMSRead(this.address);

        super.onPause();

        log.returning(methodName);
    }

    @Override
    protected void onStop() {
        final String methodName =  "onStop()";
        log.justEntered(methodName);

        unregisterReceivers();
        adapter.unstarSelections();

        super.onStop();

        log.returning(methodName);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_chat, menu);


        //Hide Show contact option if contact not in contact
        Contact contact = contactUtil.getContactOrDefault(this, this.address);

        String id = contact.getId();

        if(id == null) {
            menu.findItem(R.id.show_contact).setVisible(false);
        }

        boolean isPersonalContact = PhoneUtilsSingleton.getInstance().isReplySupported(this.address);
        if(!isPersonalContact){
            menu.findItem(R.id.show_dialer).setVisible(false);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.show_contact: showContact(); break;
            case R.id.show_dialer: showDialer(); break;
        }

        return super.onOptionsItemSelected(item);
    }
    //--- Activity Overriders End ---


    //--- LocalSMSReceivedReceiver.Callback Overriders Start ---
    @Override
    public void onSMSReceived(SMS sms) {
        final String methodName = "onSMSReceived()";
        log.justEntered(methodName);

        String from = sms.getAddress();
        if(from.equals(this.address)) {
            addSMSinUI(sms);
        }

        log.returning(methodName);
    }
    //--- LocalSMSReceivedReceiver.Callback Overriders Ends ---


    //--- LocalSMSSentReceiver.Callback Overriders Start ---
    @Override
    public void onSMSSent(SMS sms) {
        final String methodName =  "onSMSSent(SMS)";
        log.justEntered(methodName);

        String id = sms.getId();
        SMS orgSMS = smses.get(id);
        orgSMS.setType(SMS.TYPE_SENT);
        int position = smses.indexOf(id);

        adapter.notifyItemChanged(position);
        /*smses.clear();
        smses.addAll(temp);
        adapter.notifyDataSetChanged();*/

        log.returning(methodName);
    }

    @Override
    public void onSMSSentFailure(SMS sms) {
        final String methodName =  "onSMSSentFailure(SMS)";
        log.justEntered(methodName);

        String id = sms.getId();
        SMS orgSMS = smses.get(id);
        orgSMS.setType(SMS.TYPE_FAILED);
        int position = smses.indexOf(id);

        adapter.notifyItemChanged(position);

        log.returning(methodName);
    }
    //--- LocalSMSSentReceiver.Callback Overriders Ends ---


    //--- LocalSMSDeliveredReceiver.Callback Overriders Start ---
    @Override
    public void onSMSDelivered(SMS sms) {
        String id = sms.getId();
        SMS orgSMS = smses.get(id);
        orgSMS.setSeen(true);
        int position = smses.indexOf(id);

        adapter.notifyItemChanged(position);
    }
    //--- LocalSMSDeliveredReceiver.Callback Overriders Ends ---


    //--- RVChatAdapter.Callback Starts ---
    @Override
    public void onItemLongClicked() {
        final String methodName =  "onItemLongClicked()";
        log.justEntered(methodName);

        startActionMode(amCallback);

        log.returning(methodName);
    }

    @Override
    public void singleSelectionMode() {
        final String methodName =  "singleSelectionMode()";
        log.justEntered(methodName);

        amCallback.enableCopy(false);

        log.returning(methodName);
    }

    @Override
    public void multiSelectionMode() {
        final String methodName =  "multiSelectionMode()";
        log.justEntered(methodName);

        amCallback.enableCopy(true);

        log.returning(methodName);
    }

    @Override
    public void allDeselected() {
        final String methodName =  "allDeselected()";
        log.justEntered(methodName);

        amCallback.finish();

        log.returning(methodName);
    }

    @Override
    public void onReplyNotSupported() {
        final String methodName =  "onReplyNotSupported()";
        log.justEntered(methodName);

        //Show Message
        tvReplyNotSupported.setVisibility(View.VISIBLE);

        //Attach RelativeLayout to message
        RelativeLayout.LayoutParams params= (RelativeLayout.LayoutParams) recyclerView.getLayoutParams();
        params.addRule(RelativeLayout.ABOVE, tvReplyNotSupported.getId());
        recyclerView.setLayoutParams(params);

        //Hide EditText
        ViewGroup.LayoutParams lp =  (ViewGroup.MarginLayoutParams) holderBodyET.getLayoutParams();
        lp.height = 1;
        holderBodyET.setLayoutParams(lp);
        holderBodyET.setVisibility(View.INVISIBLE);

        log.returning(methodName);
    }
    //--- RVChatAdapter.Callback Ends ---


    //TODO Remove Ananlytics
    /*synchronized public Tracker getDefaultTracker() {
        // To enable debug logging use: adb shell setprop log.tag.GAv4 DEBUG
        if (sTracker == null) {
            sTracker = sAnalytics.newTracker(R.xml.global_tracker);
        }

        return sTracker;
    }*/

    //---- InboxUtil.Callback Overrides Starts ----
    @Override
    public void onAllSMSFromToResult(IndexedHashMap<String, SMS> updateList) {
        final String methodName =  "onAllSMSFromToResult()";
        log.justEntered(methodName);

        holderLoader.setVisibility(View.GONE);
        holderMain.setVisibility(View.VISIBLE);

        smses.clear();

        smses.update(updateList);
        adapter.notifyDataSetChanged();

        if(!alreadyHighlighted) { //If this flag is not there then it will be highlighted in every refresh
            highlightSMS();
            alreadyHighlighted = true;
        }

        log.returning(methodName);
    }

    //---- InboxUtil.Callback Overrides Ends ----

}
