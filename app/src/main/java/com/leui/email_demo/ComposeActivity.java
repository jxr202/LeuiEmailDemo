package com.leui.email_demo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.text.util.Rfc822Tokenizer;
import android.util.Log;

import com.letv.commons.chip.RecipientEditTextView;

/**
 * Created by Jxr33 on 2017-03-15
 */
public class ComposeActivity extends Activity {

    private static final String TAG = "ComposeActivity";

    private RecipientEditTextView mTo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.le_compose);
        initView();
        checkPermission();
    }

    private void checkPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "checkPermission.. has no permission");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, 100);
        }
        else {
            Log.i(TAG, "checkPermission.. already has permission");
            setupRecipients();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 100: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setupRecipients();
                } else {
                    finish();
                }
            }
        }
    }

    private void initView() {
        mTo = (RecipientEditTextView) findViewById(R.id.to);
    }

    private void setupRecipients() {
        mTo.setAdapter(new BaseRecipientAdapter(this));
        mTo.setTokenizer(new Rfc822Tokenizer());
        mTo.setThreshold(1);    //每输入一个字符就进行匹配
    }


}
