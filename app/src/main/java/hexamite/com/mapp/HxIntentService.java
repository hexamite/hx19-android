package hexamite.com.mapp;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * helper methods.
 */
public class HxIntentService extends IntentService {

    public static final String ACTION_RECEIVE_MESSAGE = "hexamite.com.mapp.action.ACTION_RECEIVE_MESSAGE";

    public static final String ACTION_CONNECT = "hexamite.com.mapp.action.ACTION_CONNECT";

    private static final String HOST = "hexamite.com.mapp.extra.HOST";
    private static final String PORT = "hexamite.com.mapp.extra.PORT";

    private Socket socket;
    private InputStream input;
    private OutputStream output;

    private static Intent listenIntent;

    private static List<String> commands = Collections.synchronizedList(new LinkedList<String>());
    public static volatile boolean stateConnected;
    public static volatile boolean commandDisconnect;

    public static void addCommand(String command) {
        commands.add(command);
    }

    public HxIntentService() {
        super("HxIntentService");
    }

    @Override
    public void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    /**
     * Starts this service to perform connect with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * The listen service continues to listen until the connection breaks.
     * It sends whatever it receives via an intent which the app registers
     * a receiver for.
     *
     * @see IntentService
     */
    public static void connect(Context context, String host, int port) {
        Intent intent = new Intent(context, HxIntentService.class);
        intent.setAction(ACTION_CONNECT);
        intent.putExtra(HOST, host);
        intent.putExtra(PORT, port);
        context.startService(intent);
    }

    public static void stopService(Context context) {
        if(listenIntent != null) {
            context.stopService(listenIntent);
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_CONNECT.equals(action)) {
                listenIntent = intent;
                final String host = intent.getStringExtra(HOST);
                final int port = intent.getIntExtra(PORT, 8893);
                handleConnect(host, port);
            }
        }
    }

    /**
     * Handle action listening to socket in the provided background thread with the provided
     * parameters.
     */
    private void handleConnect(String host, int port) {
        try {

            socket = new Socket(host, port);
            socket.setSoTimeout(500);
            output = socket.getOutputStream();
            input = socket.getInputStream();
            stateConnected = true;
            commandDisconnect = false;
            Toast.makeText(getApplicationContext(), "Connected.", Toast.LENGTH_LONG).show();

            BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            String line = readOrTimeOut(reader);
            while (socket != null && !socket.isClosed() && !commandDisconnect) {
                if(line != null) {
                    Intent intent = new Intent(ACTION_RECEIVE_MESSAGE);
                    intent.putExtra("LINE", line);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                }
                writeCommands();
                line =  readOrTimeOut(reader);
            }

        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "No connection.", Toast.LENGTH_LONG).show();
            Log.i("", "Exception: " + e);
            e.printStackTrace();
        } finally {
            disconnect();
        }
    }

    private void writeCommands() throws IOException {
        while(!commands.isEmpty()) {
            String command = commands.remove(0);
            output.write(pack(command));
        }
        output.flush();
    }

    private String readOrTimeOut(BufferedReader reader) throws IOException {
        try {
            return reader.readLine();
        } catch(SocketTimeoutException e) {
            return null;
        }
    }

    private synchronized void disconnect() {
        commandDisconnect = false;
        stateConnected = false;
        if(socket != null) {
            try {
                socket.shutdownInput();
                socket.shutdownOutput();
                socket.close();
            } catch (IOException e) {
                Log.e("", "Error closing socket.");
            }
        }
        socket = null;
        input = null;
        output = null;
        Log.i("", "Connection closed.");
        // Toast.makeText(getApplicationContext(), "Connection closed.", Toast.LENGTH_LONG).show();
    }

    /**
     * Package `payload` into a valid HX19 package.
     * Appends a slash, a hexadecimal checksum and carriage return to `payload`.
     * */
    private byte[] pack(String payload) throws IOException {
        return pack(payload.getBytes());
    }

    /**
     * Package `payload` into a valid HX19 package.
     * Appends a slash, a hexadecimal checksum and carriage return to `payload`.
     * */
    private byte[] pack(byte[] payload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int sum = 0;
        for(byte b: payload) {
            sum += b;
        }
        out.write(payload);
        out.write('/');
        out.write(String.format("%X", sum).getBytes());
        out.write('\r');
        return out.toByteArray();
    }

}
