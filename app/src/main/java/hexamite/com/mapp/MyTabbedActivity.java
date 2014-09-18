package hexamite.com.mapp;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Locale;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v13.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

public class MyTabbedActivity extends Activity {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v13.app.FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;

    private Writer logWriter;
    private File logFile;
    Menu menu;

    private String prefHost = "10.10.100.254";
    private int prefPort = 8899;
    private String prefLogFile = "hx19.log";
    private int prefLogViewNumLines = 100;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_tabbed);

        // Create the adapter that will return a fragment for the only
        // primary sections of the activity.

        mSectionsPagerAdapter = new SectionsPagerAdapter(getFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        IntentFilter intentFilter = new IntentFilter(HxIntentService.ACTION_RECEIVE_MESSAGE);
        LocalBroadcastManager.getInstance(getBaseContext()).registerReceiver(new Hx19MessageReceiver(), intentFilter);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        OnSharedPreferenceChangeListener preferenceChangeListener = new OnSharedPreferenceChangeListener();
        preferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        preferenceChangeListener.onSharedPreferenceChanged(preferences, "pref_host");
        preferenceChangeListener.onSharedPreferenceChanged(preferences, "pref_port");
        preferenceChangeListener.onSharedPreferenceChanged(preferences, "pref_log_file");
        preferenceChangeListener.onSharedPreferenceChanged(preferences, "pref_logview_num_lines");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.my_tabbed, menu);
        this.menu = menu;
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        this.menu = menu;
        menu.findItem(R.id.action_log).setEnabled(isExternalStorageWritable());
        menu.findItem(R.id.action_connect).setChecked(HxIntentService.stateConnected);
        menu.findItem(R.id.action_sync_off).setEnabled(HxIntentService.stateConnected);
        menu.findItem(R.id.action_sync_on).setEnabled(HxIntentService.stateConnected);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, Settings.class));
            return true;
        } else if (id == R.id.action_sync_on) {
            sendMessage("M&$");
            return true;
        } else if (id == R.id.action_sync_off) {
            sendMessage("M&%");
            return true;
        } else if (id == R.id.action_log) {
            if(item.isChecked()) {
                stopLog();
            }  else {
                startLog();
            }
            item.setChecked(logWriter != null);
            return true;
        } else if (id == R.id.action_connect) {
            if(item.isChecked()) {
                Log.i("", "Disconnecting!");
                disconnect();
            } else {
                Log.i("", "Connecting!");
                connect();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startLog() {
        Log.i("", "startLog()");
        try {
            logFile = new File(getBaseContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), prefLogFile);
            // logFile = new File(getBaseContext().getFilesDir(), name);
            logWriter = new BufferedWriter(new FileWriter(logFile));
            Log.i("", "Log file: " + logFile.getAbsolutePath());
            Toast.makeText(getApplicationContext(), "Logging to " + logFile.getAbsolutePath() + ".", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "Failed to create external file.", Toast.LENGTH_LONG).show();
            Log.e("", e.toString());
        }
    }

    private void stopLog() {
        Log.i("", "stopLog()");
        if(logWriter != null) {
            try {
                logWriter.close();
            } catch (IOException e) {
                Log.e("", e.toString());
            }
            logWriter = null;
        }
        Toast.makeText(getApplicationContext(), "Stopped logging.", Toast.LENGTH_LONG).show();
    }

    public void sendMessage(View view) {
        EditText editText   = (EditText)findViewById(R.id.editText);
        String message = editText.getText().toString();
        sendMessage(message);
    }

    private void connect() {
        TextView textview = (TextView) findViewById(R.id.textView);
        textview.setMovementMethod(new ScrollingMovementMethod());

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String host = settings.getString("pref_host", "10.10.100.254");
        int port = Integer.parseInt(settings.getString("pref_port", "8899"));

        HxIntentService.connect(getApplicationContext(), host, port);
    }

    private void disconnect() {
        HxIntentService.commandDisconnect = true;
        HxIntentService.stopService(getBaseContext());
    }

    private void sendMessage(String message) {
        HxIntentService.addMessageOut(message);
    }

    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if(Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

     public class OnSharedPreferenceChangeListener implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.i("", "OnPreferenceChange: " + key);
            if("pref_host".equals(key)) {
                prefHost = sharedPreferences.getString(key, prefHost);
            } else if("pref_port".equals(key)) {
                prefPort = Integer.parseInt(sharedPreferences.getString(key, "" + prefPort));
            } else if("pref_log_file".equals(key)) {
                prefLogFile = sharedPreferences.getString(key, prefLogFile);
            } else if("pref_log_view_num_lines".equals(key)) {
                prefLogViewNumLines = Integer.parseInt(sharedPreferences.getString(key, "" + prefLogViewNumLines));
            }
        }
    }

    public class Hx19MessageReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            TextView textView = (TextView) findViewById(R.id.textView);
            if(textView != null) {
                String line = intent.getCharSequenceExtra("LINE") + "\n";
                textView.append(line);
                while (textView.getLineCount() > prefLogViewNumLines) {
                    int i = indexOfEOL(textView.getText());
                    if (i >= 0) {
                        textView.getEditableText().delete(0, i + 1);
                    }
                }
                if(logWriter != null) {
                    try {
                        logWriter.append(line);
                        logWriter.flush();
                    } catch (IOException e) {
                        Log.e("", e.toString());
                    }
                }
            }
        }

        private int indexOfEOL(CharSequence text) {
            for(int i = 0; i < text.length(); i++) {
                if(text.charAt(i) == '\n' || text.charAt(i) == '\r') {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return PlaceholderFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            // Show 1 total pages.
            return 1;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();
            switch (position) {
                case 0:
                    return getString(R.string.title_section1).toUpperCase(l);
            }
            return null;
        }

    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_my_tabbed, container, false);
            return rootView;
        }
    }

}
