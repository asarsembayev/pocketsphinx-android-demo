/* ====================================================================
 * Copyright (c) 2014 Alpha Cephei Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY ALPHA CEPHEI INC. ``AS IS'' AND
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
 * NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 */

package edu.cmu.pocketsphinx.demo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

import static android.widget.Toast.makeText;

public class PocketSphinxActivity extends Activity implements
        RecognitionListener {

    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "wakeup";
    private static final String MENU_SEARCH = "menu";
    private static final String CONT_SPEECH = "speech";
    private static final String MINE_GRAM = "words";

    private Button correct_btn;
    private Button wrong_btn;
    private Button reset_btn;
    private Button export_btn;
    private int correct_answers = 0;
    private int wrong_answers = 0;

    /* Keyword we are looking for to activate menu */
    private static final String KEYPHRASE = "sphinx";

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private SpeechRecognizer recognizer;
    private HashMap<String, Integer> captions;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        // Prepare the data for UI
        captions = new HashMap<>();
        captions.put(KWS_SEARCH, R.string.kws_caption);
        captions.put(MENU_SEARCH, R.string.menu_caption);
        captions.put(MINE_GRAM, R.string.words_rec);
        captions.put(CONT_SPEECH, R.string.cont_speech_rec);
        setContentView(R.layout.main);
        ((TextView) findViewById(R.id.caption_text))
                .setText("Preparing the recognizer");

        // Check if user has given permission to record audio
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new SetupTask(this).execute();

        correct_btn = (Button) findViewById(R.id.correct);
        wrong_btn = (Button) findViewById(R.id.wrong);
        reset_btn = (Button) findViewById(R.id.reset);
        export_btn = (Button) findViewById(R.id.export_Stats);
    }

    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<PocketSphinxActivity> activityReference;
        SetupTask(PocketSphinxActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }
        @Override
        protected Exception doInBackground(Void... params) {
            try {
                Assets assets = new Assets(activityReference.get());
                File assetDir = assets.syncAssets();
                activityReference.get().setupRecognizer(assetDir);
            } catch (IOException e) {
                return e;
            }
            return null;
        }
        @Override
        protected void onPostExecute(Exception result) {
            if (result != null) {
                ((TextView) activityReference.get().findViewById(R.id.caption_text))
                        .setText("Failed to init recognizer " + result);
            } else {
                activityReference.get().switchSearch(KWS_SEARCH);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull  int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                new SetupTask(this).execute();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }

    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String text = hypothesis.getHypstr();
        if (text.equals(KEYPHRASE))
            switchSearch(MENU_SEARCH);
        else if (text.equals(MINE_GRAM))
            switchSearch(MINE_GRAM);
        else if (text.equals(CONT_SPEECH))
            switchSearch(CONT_SPEECH);
        else
            ((TextView) findViewById(R.id.result_text)).setText(text);
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {

        ((TextView) findViewById(R.id.result_text)).setText("");
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {
        ((TextView) findViewById(R.id.caption_text)).setText("Was the utterance recognized correct or wrong?");
        correct_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                correct_answers++;
                ((TextView) findViewById(R.id.caption_text)).setText("Correct, the # is " + correct_answers);
                if (((TextView) findViewById(R.id.correct_num)).getText() != null){
                    ((TextView) findViewById(R.id.correct_num)).setText(Integer.toString(correct_answers));
                }
            }
        });
        wrong_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                wrong_answers++;
                ((TextView) findViewById(R.id.caption_text)).setText("Wrong, the # is " + wrong_answers);
                if (((TextView) findViewById(R.id.wrong_num)).getText() != null){
                    ((TextView) findViewById(R.id.wrong_num)).setText(Integer.toString(wrong_answers));
                }
            }
        });
        reset_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                wrong_answers = 0;
                correct_answers = 0;
                ((TextView) findViewById(R.id.caption_text)).setText("Reset the answers counter to 0");
            }
        });

        export_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String currentDateandTime = sdf.format(new Date());
                String fileName = "_stats_" + currentDateandTime + "_.txt";
                String str = "The stats for: " + currentDateandTime + "\n\n" +
                        "Correct #: " + correct_answers + "\nWrong #: " + wrong_answers;
                String alt_path = "Android/data/edu.cmu.sphinx.pocketsphinx/files/stats/";

                Context context = getApplicationContext();
                File path = context.getFilesDir();
                File file = new File(path, fileName);
                System.out.println("the path: "+path + " file name " + file
                +"\n the STR " +str);

                FileOutputStream stream = null;
                try {
                    stream = new FileOutputStream(file);
                    stream.write(str.getBytes());
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        stream.close();
                        System.out.println("closedDDDDD");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                ((TextView) findViewById(R.id.caption_text)).setText("Export");
            }
        });

        if (!recognizer.getSearchName().equals(KWS_SEARCH))
            switchSearch(KWS_SEARCH);
        ((TextView) findViewById(R.id.caption_text)).setText("End of speech");

    }

    private void switchSearch(String searchName) {
        recognizer.stop();

        // If we are not spotting, start listening with timeout (10000 ms or 10 seconds).
        if (searchName.equals(KWS_SEARCH))
            recognizer.startListening(searchName);
        else
            recognizer.startListening(searchName, 10000);

        String caption = getResources().getString(captions.get(searchName));
        ((TextView) findViewById(R.id.caption_text)).setText(caption);
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))

                //.setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)

                .getRecognizer();
        recognizer.addListener(this);

        /* In your application you might not need to add all those searches.
          They are added here for demonstration. You can leave just one.
         */

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

        // Create grammar-based search for selection between demos
        File menuGrammar = new File(assetsDir, "menu.gram");
        recognizer.addGrammarSearch(MENU_SEARCH, menuGrammar);

        // Words recognition
        File mine_ngram = new File(assetsDir, "mine.gram");
        recognizer.addGrammarSearch(MINE_GRAM, mine_ngram);

        // Continuous speech recognition
        File continousSpeech = new File(assetsDir, "en-us.lm.bin");
        // Create keyword-activation search.
        recognizer.addNgramSearch(CONT_SPEECH, continousSpeech);
        // Start the search
        //recognizer.startListening(CONT_SPEECH);

    }

    @Override
    public void onError(Exception error) {
        ((TextView) findViewById(R.id.caption_text)).setText(error.getMessage());
    }

    @Override
    public void onTimeout() {
        switchSearch(KWS_SEARCH);
    }
}
