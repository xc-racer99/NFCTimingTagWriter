package ca.orienteeringbc.nfctimingtagwriter;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.arch.persistence.room.Room;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.AsyncTask;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static android.nfc.NdefRecord.RTD_TEXT;
import static android.nfc.NdefRecord.TNF_WELL_KNOWN;

public class MainActivity extends AppCompatActivity {
    private static final String LAST_UPDATED_TAG = "WJR_LIST_LAST_UPDATED";
    private static final String DATABASE_NAME = "competitor_db";

    private Button selectNextCompetitor;
    private TextView lastUpdatedText;

    // List of all competitors
    private List<Competitor> competitorList;

    // Next competitor to write
    private Competitor nextCompetitor = null;

    // Database object
    private Database database;

    // NFC vars
    private NfcAdapter mNfcAdapter;
    private IntentFilter[] mWriteTagFilters;
    private PendingIntent mNfcPendingIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences preferences = getPreferences(MODE_PRIVATE);

        // Check about NFC
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null) {
            Toast.makeText(this, R.string.no_nfc, Toast.LENGTH_LONG).show();
            finish();
        }
        mNfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP), 0);
        mWriteTagFilters = new IntentFilter[] { new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED) };

        database = Room.databaseBuilder(getApplicationContext(), Database.class, DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .build();

        final EditText editText = findViewById(R.id.search_box);
        final TextView nextCompetitorText = findViewById(R.id.next_competitor_text);

        Button updateCompetitorList = findViewById(R.id.update_competitors_button);
        updateCompetitorList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new DownloadCompetitorsTask().execute();
            }
        });

        selectNextCompetitor = findViewById(R.id.select_next_competitor);
        selectNextCompetitor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick (View view) {
                LayoutInflater layoutInflaterAndroid = LayoutInflater.from(MainActivity.this);
                View mView = layoutInflaterAndroid.inflate(R.layout.alert_select_person, null);
                final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
                alertDialogBuilder.setView(mView);
                alertDialogBuilder.setNegativeButton(getText(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                });
                alertDialogBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        dialogInterface.cancel();
                    }
                });
                final AlertDialog alertDialog = alertDialogBuilder.create();

                final ArrayAdapter adapter = new ArrayAdapter<>(MainActivity.this,
                        android.R.layout.simple_list_item_1,
                        competitorList);
                final ListView listView = mView.findViewById(R.id.competitor_list);
                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        nextCompetitor = (Competitor) adapterView.getItemAtPosition(i);
                        nextCompetitorText.setText(getString(R.string.next_to_write, nextCompetitor.toString()));
                        alertDialog.dismiss();
                    }
                });
                listView.setAdapter(adapter);

                alertDialog.show();
            }
        });

        String lastUpdated = preferences.getString(LAST_UPDATED_TAG, null);
        lastUpdatedText = findViewById(R.id.last_updated_text);
        if (lastUpdated != null) {
            lastUpdatedText.setText(getString(R.string.last_updated, lastUpdated));
        }

        Button filterCompetitors = findViewById(R.id.filter_competitors);
        filterCompetitors.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new LoadCompetitorsTask().execute(editText.getText().toString());
            }
        });

        // Get the pre-existing list of competitors from the database
        new LoadCompetitorsTask().execute(editText.getText().toString());
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mNfcAdapter.isEnabled()){
            new AlertDialog.Builder(this).setTitle(R.string.enable_nfc)
                    .setMessage(R.string.enable_nfc_reason)
                    .setPositiveButton(R.string.go, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface arg0, int arg1) {
                            Intent setNfc = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                            startActivity(setNfc);
                            arg0.dismiss();
                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            finish(); // exit application if user cancels
                        }
                    }).create().show();
        }
        mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, mWriteTagFilters, null);
    }

    protected void onPause() {
        super.onPause();
        mNfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (nextCompetitor == null) {
            Toast.makeText(this, R.string.no_competitor_selected, Toast.LENGTH_SHORT).show();
            return;
        }

        if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if(supportedTechs(detectedTag.getTechList())) {
                // check if tag is writable (to the extent that we can
                if(writableTag(detectedTag)) {
                    //writeTag here
                    WriteResponse wr = writeTag(getMessage(), detectedTag);
                    String message = (wr.getStatus() == 1 ? "Success: " : "Failed: ") + wr.getMessage();
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

                    // Clear next person
                    if (wr.getStatus() == 1) {
                        nextCompetitor = null;
                        TextView textView = findViewById(R.id.next_competitor_text);
                        textView.setText(R.string.no_competitor_selected);
                    }
                } else {
                    Toast.makeText(this,"This tag is not writable", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this,"This tag type is not supported", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public WriteResponse writeTag(NdefMessage message, Tag tag) {
        if (message == null) {
            return new WriteResponse(0, "Unable to find correct character encoding");
        }

        int size = message.toByteArray().length;
        String mess;
        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();
                if (!ndef.isWritable()) {
                    return new WriteResponse(0,"Tag is read-only");
                }
                if (ndef.getMaxSize() < size) {
                    mess = "Tag capacity is " + ndef.getMaxSize() + " bytes, message is " + size
                            + " bytes.";
                    return new WriteResponse(0, mess);
                }
                ndef.writeNdefMessage(message);
                mess = "Wrote message to pre-formatted tag.";
                return new WriteResponse(1, mess);
            } else {
                NdefFormatable format = NdefFormatable.get(tag);
                if (format != null) {
                    try {
                        format.connect();
                        format.format(message);
                        mess = "Formatted tag and wrote message";
                        return new WriteResponse(1, mess);
                    } catch (IOException e) {
                        mess = "Failed to format tag.";
                        return new WriteResponse(0, mess);
                    }
                } else {
                    mess = "Tag doesn't support NDEF.";
                    return new WriteResponse(0, mess);
                }
            }
        } catch (Exception e) {
            mess = "Failed to write tag";
            return new WriteResponse(0, mess);
        }
    }
    private class WriteResponse {
        int status;
        String message;
        WriteResponse(int Status, String Message) {
            this.status = Status;
            this.message = Message;
        }
        int getStatus() {
            return status;
        }
        String getMessage() {
            return message;
        }
    }
    public static boolean supportedTechs(String[] techs) {
        boolean ultralight = false;
        boolean nfcA = false;
        boolean nDef = false;
        for(String tech : techs) {
            switch (tech) {
                case "android.nfc.tech.MifareUltralight":
                    ultralight = true;
                    break;
                case "android.nfc.tech.NfcA":
                    nfcA = true;
                    break;
                case "android.nfc.tech.Ndef":
                case "android.nfc.tech.NdefFormatable":
                    nDef = true;
                    break;
            }
        }
        return ultralight && nfcA && nDef;
    }
    private boolean writableTag(Tag tag) {
        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();
                if (!ndef.isWritable()) {
                    Toast.makeText(this,"Tag is read-only.",Toast.LENGTH_SHORT).show();
                    ndef.close();
                    return false;
                }
                ndef.close();
                return true;
            }
        } catch (Exception e) {
            Toast.makeText(this,"Failed to read tag",Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    private NdefMessage getMessage() {
        String text = "WjrId:" + nextCompetitor.getWjrId();

        byte[] textBytes;
        byte[] languageCodeBytes;
        try {
            textBytes = text.getBytes("UTF-8");
            languageCodeBytes = Locale.getDefault().getLanguage().
                    getBytes("US-ASCII");
        } catch (UnsupportedEncodingException e) {
            return null;
        }
        // We only have 6 bits to indicate ISO/IANA language code.
        if (languageCodeBytes.length >= 64) {
            throw new IllegalArgumentException("language code is too long, must be <64 bytes.");
        }
        ByteBuffer buffer = ByteBuffer.allocate(1 + languageCodeBytes.length + textBytes.length);

        byte status = (byte) (languageCodeBytes.length & 0xFF);
        buffer.put(status);
        buffer.put(languageCodeBytes);
        buffer.put(textBytes);

        return new NdefMessage(new NdefRecord(TNF_WELL_KNOWN, RTD_TEXT, null, buffer.array()));
    }

    private class LoadCompetitorsTask extends AsyncTask<String, Void, List<Competitor>> {

        @Override
        protected List<Competitor> doInBackground(String... search) {
            if (search[0].isEmpty())
                return database.daoAccess().getAllCompetitors();
            return database.daoAccess().getCompetitorsSearch("%" + search[0] + "%");
        }

        @Override
        protected void onPostExecute(List<Competitor> competitors) {
            if (competitors != null && competitors.size() > 0) {
                selectNextCompetitor.setEnabled(true);
                competitorList = competitors;
            }
        }
    }

    // Implementation of AsyncTask used to download XML from WJR for Clubs
    private class DownloadCompetitorsTask extends AsyncTask<Void, Void, List<Competitor>> {

        @Override
        protected List<Competitor> doInBackground(Void... voids) {
            try {
                List<Competitor> competitors = loadCompetitorListXml();

                if (competitors != null) {
                    database.daoAccess().deleteAllCompetitors();
                    database.daoAccess().insertCompetitorList(competitors);
                }

                return competitors;
            } catch (IOException e) {
                return null;
            } catch (XmlPullParserException e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<Competitor> competitors) {
            if (competitors != null && competitors.size() > 0) {
                competitorList = competitors;
                selectNextCompetitor.setEnabled(true);
                String lastUpdated = DateFormat.getDateInstance(DateFormat.LONG).format(new Date());
                lastUpdatedText.setText(getString(R.string.last_updated, lastUpdated));
                SharedPreferences preferences = getPreferences(MODE_PRIVATE);
                preferences.edit().putString(LAST_UPDATED_TAG, lastUpdated).apply();
            } else {
                Toast.makeText(MainActivity.this, R.string.update_competitor_list_fail, Toast.LENGTH_LONG).show();
            }
        }
    }

    private List<Competitor> loadCompetitorListXml() throws XmlPullParserException, IOException {
        InputStream stream = null;
        CompetitorXmlParser xmlParser = new CompetitorXmlParser();
        List<Competitor> competitors;

        try {
            Log.e("Tag", "Got to try");
            stream = downloadUrl();
            if (stream == null)
                Log.e("Stream", "Stream is null");
            competitors = xmlParser.parse(stream);

            // Makes sure that the streams are closed after the app is
            // finished using it.
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
        return competitors;
    }

    // Given a string representation of a URL, sets up a connection and gets
    // an input stream.
    private InputStream downloadUrl() {
        HttpURLConnection conn;
        try {
            URL url = new URL("https://whyjustrun.ca/iof/3.0/competitor_list.xml");
            conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(25000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            // Starts the query
            conn.connect();
            return conn.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
