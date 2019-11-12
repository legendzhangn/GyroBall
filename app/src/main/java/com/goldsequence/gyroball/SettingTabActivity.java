package com.goldsequence.gyroball;

import java.util.prefs.Preferences;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.widget.TabHost;

public class SettingTabActivity extends TabActivity {

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);

	    // Remove title bar
	   this.requestWindowFeature(Window.FEATURE_NO_TITLE);

	
	    setContentView(R.layout.tablayout);

        TabHost tabHost = getTabHost();  // The activity TabHost
        TabHost.TabSpec spec;  // Resusable TabSpec for each tab
        Intent intent;  // Reusable Intent for each tab

        // Create an Intent for the regular live wallpaper preferences activity
        intent = new Intent().setClass(this, SettingActivity.class);

        // Initialize a TabSpec and set the intent
        spec = tabHost.newTabSpec("TabTitle").setContent(intent);
        spec.setIndicator("TabTitle");

        tabHost.addTab(spec);

        tabHost.setCurrentTab(0);
	}
	
	@Override
   protected void onDestroy() 
   {
	   super.onDestroy();
   }

}
