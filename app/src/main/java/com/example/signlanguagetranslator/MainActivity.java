package com.example.signlanguagetranslator;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.media.Image;
import android.os.Bundle;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.madhavanmalolan.ffmpegandroidlibrary.Controller;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.io.File;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import static android.content.ContentValues.TAG;
import static android.provider.Settings.NameValueTable.NAME;

import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// https://developer.android.com/guide/topics/connectivity/bluetooth

public class MainActivity extends AppCompatActivity {

    protected Interpreter tflite;
    private List<String> labels;
    private static int REQUEST_ENABLE_BT = 1;
    BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
    private Handler handler;
    private final int REQ_CODE_SPEECH_INPUT = 100;
    private Bitmap image = null;
    private ImageView imageView;
    private TensorImage inputImageBuffer;
    private TensorBuffer outputProbabilityBuffer;
    private TensorProcessor probabilityProcessor;
    private static final float PROBABILITY_MEAN = 0.0f;
    private static final float PROBABILITY_STD = 255.0f;
    File videoFile;
    FirebaseStorage storage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        OpenCVLoader.initDebug();
        try{
            tflite = new Interpreter(loadModelFile(MainActivity.this));
        } catch (IOException e){
            e.printStackTrace();
        }

        int imageTensorIndex = 0;
        int[] imageShape = tflite.getInputTensor(imageTensorIndex).shape(); // {1, height, width, 3}
        DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType();
        int probabilityTensorIndex = 0;
        int[] probabilityShape =
                tflite.getOutputTensor(probabilityTensorIndex).shape(); // {1, NUM_CLASSES}
        DataType probabilityDataType = tflite.getOutputTensor(probabilityTensorIndex).dataType();

        // Creates the input tensor.
        inputImageBuffer = new TensorImage(imageDataType);

        // Creates the output tensor and its processor.
        outputProbabilityBuffer = TensorBuffer.createFixedSize(probabilityShape, probabilityDataType);

        probabilityProcessor = new TensorProcessor.Builder().build();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        storage = FirebaseStorage.getInstance();
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        mAuth.signInAnonymously();
    }

    void connectServer(View v) {
        // Add view components
        //EditText ipv4AddressView = findViewById(R.id.IPAddress);
        String ipv4Address = "199.98.27.181"; //ipv4AddressView.getText().toString();

        //EditText portNumberView = findViewById(R.id.portNumber);
        String portNumber = "5000"; //portNumberView.getText().toString();

        String postUrl = "http://" + ipv4Address + ":" + portNumber + "/";

        String postBodyText = "Bahhhhh";
        MediaType mediaType = MediaType.parse("text/plain; charset=utf-8");
        RequestBody postBody = RequestBody.create(mediaType, postBodyText);

        postRequest(postUrl, postBody);

    }

    void postRequest(String postUrl, RequestBody postBody) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(postUrl)
                .post(postBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                call.cancel();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //TextView responseText = findViewById(R.id.responseText);
                        //responseText.setText("Failed to Connect to Server");
                        System.out.println("Failed to Connect to Server");
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        /*
                        TextView responseText = findViewById(R.id.responseText);
                        try {
                            responseText.setText(response.body().string());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                         */
                        //System.out.println(response.body().string());
                    }
                });
            }
        });
    }

    public void testGCS(View view) throws IOException {
        // Create a storage reference from our app
        StorageReference storageRef = storage.getReference();

        // Create a reference with an initial file path and name
        //StorageReference pathReference = storageRef.child("images/stars.jpg");

        // Create a reference to a file from a Google Cloud Storage URI
        //StorageReference gsReference = storage.getReferenceFromURI("gs://precise-airship-267920/videos/test_blob");
        //StorageReference gsReference = storage.getReferenceFromUrl("https://storage.cloud.google.com/precise-airship-267920.appspot.com/videos/test_blob.h264");
        System.out.println("Started Download");
        //File testFile = File.createTempFile("blob", ".h264", getCacheDir());
        File testFile = new File(getCacheDir() + "/blob.h264");
        StorageReference testRef = storageRef.child("videos/test_blob.h264");
        //File video = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "video.h264");
        /*
        File localFile = new File(getFilesDir()+ "/Download/" ,"video.h264"); //File.createTempFile("video", "h264", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        localFile.mkdirs();
        downloadFile(
                "https://storage.cloud.google.com/precise-airship-267920/videos/test_blob.h264", localFile);
        System.out.println(localFile.getAbsoluteFile());
        long temp = localFile.length();
        System.out.println(temp);
        */
        //localFile.mkdirs();
        testRef.getFile(testFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                // Local temp file has been created
                System.out.println("Downloaded");
                System.out.println(getCacheDir());
                System.out.println("Copied");
                System.out.println(testFile.length());
                videoFile = testFile;
                videoToImages(videoFile);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                // Handle any errors
                System.out.println("Failed Download");
            }
        });
    }

    public void videoToImages(File video){
        String outputPath = getCacheDir().getAbsolutePath() + "/SLAB/$filename%03d.bmp";
        System.out.println(outputPath);
        Controller.getInstance().run(new String[]{
                "-i",
                video.getAbsolutePath(),
                "-r",
                "1/1",
                outputPath

        });
        String dir = getCacheDir().toString() + "/SLAB";
        File directory = new File(dir);
        File[] files = directory.listFiles();
        for(int i = 0; i < files.length; i++){
            System.out.println(files[i].getName());
        }

        /*int frameNum = 0;
        VideoCapture cap = new VideoCapture();
        String inputFile = video.getAbsolutePath();
        String outputFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/testIm";
        System.out.println(inputFile);
        cap.open(inputFile);
        int video_length = (int) cap.get(Videoio.CAP_PROP_FRAME_COUNT);
        int frames_per_second = (int) cap.get(Videoio.CAP_PROP_FPS);
        int frame_number = (int) cap.get(Videoio.CAP_PROP_POS_FRAMES);

        Mat frame = new Mat();

        if (cap.isOpened())
        {
            System.err.println("Video is opened");
            System.err.println("Number of Frames: " + video_length);
            System.err.println(frames_per_second + " Frames per Second");
            System.err.println("Converting Video...");

            while(cap.read(frame)) //the last frame of the movie will be invalid. check for it !
            {
                Imgcodecs.imwrite(outputFile + "/" + frame_number +".jpg", frame);
                frame_number++;
            }
            cap.release();
        }*/

    }

    private static void downloadFile(String url, File outputFile) {
        try {
            URL u = new URL(url);
            URLConnection conn = u.openConnection();
            int contentLength = conn.getContentLength();
            System.out.println("download" + contentLength);
            DataInputStream stream = new DataInputStream(u.openStream());

            byte[] buffer = new byte[contentLength];
            stream.readFully(buffer);
            stream.close();

            DataOutputStream fos = new DataOutputStream(new FileOutputStream(outputFile));
            fos.write(buffer);
            fos.flush();
            fos.close();

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("we dumb");

        }
    }



    public static class Recognition {
        /**
         * A unique identifier for what has been recognized. Specific to the class, not the instance of
         * the object.
         */
        private final String id;

        /** Display name for the recognition. */
        private final String title;

        /**
         * A sortable score for how good the recognition is relative to others. Higher should be better.
         */
        private final Float confidence;

        /** Optional location within the source image for the location of the recognized object. */
        private RectF location;

        public Recognition(
                final String id, final String title, final Float confidence, final RectF location) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return new RectF(location);
        }

        public void setLocation(RectF location) {
            this.location = location;
        }

        @Override
        public String toString() {
            String resultString = "";
            if (id != null) {
                resultString += "[" + id + "] ";
            }

            if (title != null) {
                resultString += title + " ";
            }

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f);
            }

            if (location != null) {
                resultString += location + " ";
            }

            return resultString.trim();
        }
    }


    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
        InputStream istr;
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd("letter_net.tflite");
        labels = FileUtil.loadLabels(activity, "labels.txt");
        try{
            istr = activity.getAssets().open("L.jpg");
            image = BitmapFactory.decodeStream(istr);
        } catch (IOException e){
            e.printStackTrace();
        }
        imageView = findViewById(R.id.imageView);
        imageView.setImageBitmap(image);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private TensorImage loadImage(final Bitmap bitmap, int sensorOrientation) {
        // Loads bitmap into a TensorImage.
        inputImageBuffer.load(bitmap);
        // Creates processor for the TensorImage.
        int cropSize = Math.min(bitmap.getWidth(), bitmap.getHeight());
        int numRoration = sensorOrientation / 90;
        // TODO(b/143564309): Fuse ops inside ImageProcessor.
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeOp(200, 200, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                        .build();
        return imageProcessor.process(inputImageBuffer);
    }



    public void Classify(View view){
        //Image im =
        inputImageBuffer = loadImage(image, 0);
        tflite.run(inputImageBuffer.getBuffer(), outputProbabilityBuffer.getBuffer().rewind());


        Map<String, Float> labeledProbability =
                new TensorLabel(labels, probabilityProcessor.process(outputProbabilityBuffer))
                        .getMapWithFloatValue();

        List<Recognition> recog = getLetter(labeledProbability);
        System.out.println("Classified");
    }

    private static List<Recognition> getLetter(Map<String, Float> labelProb){


        PriorityQueue<Recognition> pq =
                new PriorityQueue<>(
                        26,
                        new Comparator<Recognition>() {
                            @Override
                            public int compare(Recognition lhs, Recognition rhs) {
                                // Intentionally reversed to put high confidence at the head of the queue.
                                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                            }
                        });

        for (Map.Entry<String, Float> entry : labelProb.entrySet()) {
            pq.add(new Recognition("" + entry.getKey(), entry.getKey(), entry.getValue(), null));
        }

        final ArrayList<Recognition> recognitions = new ArrayList<>();
        int recognitionsSize = Math.min(pq.size(), 26);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(pq.poll());
        }
        return recognitions;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void discoverDevices(View view){
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        Spinner spinner = findViewById(R.id.device_dropdown);
        ArrayList<String> arrayList  = new ArrayList<>();
        for (BluetoothDevice device : pairedDevices) {
            String deviceName = device.getName();
            String deviceHardwareAddress = device.getAddress(); // MAC address
            System.out.println(deviceName + deviceHardwareAddress);
            arrayList.add(deviceName);
        }
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(this,                         android.R.layout.simple_spinner_item, arrayList);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(arrayAdapter);
    }

    final UUID MY_UUID = UUID.fromString("8d0aa681-5e25-44b4-a5d3-d9d907d81c9d");

    private interface MessageConstants {
        public static final int MESSAGE_READ = 0;
        public static final int MESSAGE_WRITE = 1;
        public static final int MESSAGE_TOAST = 2;

        // ... (Add other message types here as needed.)
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer = null;

        public ConnectThread(BluetoothDevice device){
            BluetoothSocket tmp = null;
            mmDevice = device;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try{
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch(IOException e) {
                // Do something useful
            }
            mmSocket = tmp;
            try{
                tmpIn = mmSocket.getInputStream();
            } catch (IOException e){

            }
            try{
                tmpOut = mmSocket.getOutputStream();
            } catch (IOException e){

            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            mmBuffer = new byte[1024];
            int numBytes;
            btAdapter.cancelDiscovery();
            System.out.println("RUNNING");
            try{
                mmSocket.connect();
            } catch (IOException e){
                e.printStackTrace();
            }
            while(true){
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmBuffer);
                    // Send the obtained bytes to the UI activity.
                    String s = new String(mmBuffer, StandardCharsets.UTF_8);
                    System.out.println(s);
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;
                }

            }
            /*try{
                mmSocket.connect();
                mmBuffer = new byte[1024];
                while(true){
                    numBytes = mmInStream.read(mmBuffer);
                    if(mmBuffer != null){
                        File imgFile = new File(Environment.DIRECTORY_DOWNLOADS);
                        System.out.println("MADE FILE");
                        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(imgFile));
                        bos.write(mmBuffer);
                        bos.flush();
                        bos.close();
                        break;
                    }
                }

            } catch (IOException e){
                /*
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;

            }*/
        }

        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);

                // Share the sent message with the UI activity.
                /*Message writtenMsg = handler.obtainMessage(
                        MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
                writtenMsg.sendToTarget();&*/
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);

                // Send a failure message back to the activity.
                Message writeErrorMsg =
                        handler.obtainMessage(MessageConstants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast",
                        "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                handler.sendMessage(writeErrorMsg);
            }
        }

        public void cancel() {
            try{
                mmSocket.close();
            } catch (IOException e){

            }
        }

    }

    ConnectThread bt;
    public void connectToDevice(View view){

        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        Spinner spinner = findViewById(R.id.device_dropdown);
        String macAddress = "";
        BluetoothDevice selDevice = null;
        for (BluetoothDevice device : pairedDevices) {
            String deviceName = device.getName();
            String deviceHardwareAddress = device.getAddress(); // MAC address
            selDevice = device;
            if(spinner.getSelectedItem().toString().equals(deviceName)){
                macAddress = deviceHardwareAddress;
                break;
            }
        }
        // Connect to MAC Address
        // Use Pre-Defined UUID
        System.out.println(macAddress);
        bt = new ConnectThread(selDevice);
        bt.run();
    }

    public void sendData(View view){
        EditText str_data = findViewById(R.id.data);
        String str = str_data.getText().toString();
        byte[] data = str.getBytes();
        bt.write(data);
    }

    private void promptSpeechInput(){
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_prompt));

        try{
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException e){
            Toast.makeText(getApplicationContext(), getString(R.string.speech_not_supported), Toast.LENGTH_SHORT).show();

        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode){
            case REQ_CODE_SPEECH_INPUT:
        }
    }
}
