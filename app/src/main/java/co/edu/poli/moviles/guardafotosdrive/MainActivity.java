package co.edu.poli.moviles.guardafotosdrive;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.MetadataChangeSet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "MAIN_ACTIVITY";
    private static final int CODIGO_CAPTURA_IMAGEN = 1;
    private static final int CODIGO_IMAGEN_GUARDADA = 2;
    private static final int CODIGO_RESOLUCION = 3;

    private GoogleApiClient googleApiClient;
    private Bitmap imagen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (this.googleApiClient == null) {
            this.googleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        this.googleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (this.googleApiClient != null) {
            this.googleApiClient.disconnect();
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult resultado) {
        if (!resultado.hasResolution()) {
            GoogleApiAvailability.getInstance()
                    .getErrorDialog(this, resultado.getErrorCode(), 0)
                    .show();
            return;
        } else {
            try {
                resultado.startResolutionForResult(this, CODIGO_RESOLUCION);
            } catch (IntentSender.SendIntentException e) {
                Log.e(TAG, "error al arrancar la actividad de resolución");
            }
        }
    }

    public void capturarImagen(View view) {
        startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE),
                CODIGO_CAPTURA_IMAGEN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CODIGO_CAPTURA_IMAGEN:
                if (resultCode == Activity.RESULT_OK) {
                    this.imagen = (Bitmap) data.getExtras().get("data");
                    guardarArchivoEnGoogleDrive();
                }
                break;
            case CODIGO_IMAGEN_GUARDADA:
                if (resultCode == Activity.RESULT_OK) {
                    this.imagen = null;
                    Toast.makeText(this, "Se ha guardado la foto correctamente",
                            Toast.LENGTH_LONG).show();
                }
            default:
                break;
        }
    }

    private void guardarArchivoEnGoogleDrive() {
        Drive.DriveApi.newDriveContents(googleApiClient)
                .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
                    @Override
                    public void onResult(@NonNull DriveApi.DriveContentsResult resultado) {
                        if (!resultado.getStatus().isSuccess()) {
                            return;
                        }

                        ByteArrayOutputStream streamImagen = new ByteArrayOutputStream();
                        MainActivity.this.imagen.
                                compress(Bitmap.CompressFormat.PNG, 100, streamImagen);

                        try (OutputStream streamGoogleDrive = resultado
                                .getDriveContents().getOutputStream()) {
                            streamGoogleDrive.write(streamImagen.toByteArray());
                        } catch (IOException e) {
                            Log.e(TAG, "no se pudo escribir la imagen en Google Drive");
                        }

                        MetadataChangeSet metadataInicial = new MetadataChangeSet.Builder()
                                .setMimeType("image/jpeg")
                                .setTitle("NuevaFoto.png")
                                .build();

                        IntentSender intentSender = Drive.DriveApi
                                .newCreateFileActivityBuilder()
                                .setInitialMetadata(metadataInicial)
                                .setInitialDriveContents(resultado.getDriveContents())
                                .build(MainActivity.this.googleApiClient);

                        try {
                            startIntentSenderForResult(intentSender, CODIGO_IMAGEN_GUARDADA,
                                    null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            Log.e(TAG, "error abriendo el navegador de archivos");
                        }
                    }
                });
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "conectado a Google API");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "se ha suspendido la conexión a GoogleAPI");
    }
}
