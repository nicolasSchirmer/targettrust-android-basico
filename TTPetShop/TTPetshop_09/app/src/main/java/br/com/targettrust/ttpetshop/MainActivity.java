package br.com.targettrust.ttpetshop;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends ActionBarActivity {
    Toast t;
    ListView lv;
    ProgressDialog progressDialog;
    ImageView iv;
    private Handler mHandler;
    DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lv = (ListView) findViewById(R.id.listView);
        TextView status = (TextView)findViewById(R.id.status);
        iv = (ImageView) findViewById(R.id.logo);

        progressDialog = new ProgressDialog(this);

        if(isConnected()){
            status.setBackgroundColor(0xFF00CC00);
            status.setText("Conectado");
            new TrabalhoParalelo().execute("http://www.targettrust.com.br/img/header-logo_v2.png");

            SharedPreferences prefs = getPreferences(0);
            long lastUpdateTime =  prefs.getLong("lastUpdateTime", 0);

            if ((lastUpdateTime + (24 * 60 * 60 * 1000)) < System.currentTimeMillis()) {

                lastUpdateTime = System.currentTimeMillis();
                SharedPreferences.Editor editor = getPreferences(0).edit();
                editor.putLong("lastUpdateTime", lastUpdateTime);
                editor.commit();

                checkUpdate.start();

                new AsyncTaskParseJson().execute();
            }
        }
        else{
            status.setText("Sem Conexao");
            atualizaOff();
        }

        db = new DatabaseHelper(getApplicationContext());

        mHandler = new Handler();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_search:
                AlertDialog.Builder alert = new AlertDialog.Builder(this);

                alert.setTitle("Buscar");
                alert.setMessage("Pesquisar por animal");

                final EditText input = new EditText(this);
                alert.setView(input);

                alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String value = input.getText().toString();
                        Toast.makeText(getBaseContext(),"Você buscou por " +value ,Toast.LENGTH_LONG).show();
                    }
                });

                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled.
                    }
                });

                alert.show();
                return true;
            case R.id.action_settings:
                Toast.makeText(getBaseContext(),"Abrir Settings",Toast.LENGTH_LONG).show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    public void atualizaOff(){
        List<Pet> allPets = db.getAllPets();

        PetAdapter adapter = new PetAdapter(getBaseContext(), allPets);

        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View view, int position,long id) {
                Map<String, String> pet = (Map<String, String>) adapter.getItemAtPosition(position);
                t = Toast.makeText(getBaseContext(),"Selecionado: " + pet.get("Nome"),Toast.LENGTH_SHORT);
                t.show();
                Intent detalhe = new Intent(getBaseContext(),DetalheActivity.class);
                detalhe.putExtra("nome", pet.get("Nome") );
                detalhe.putExtra("arquivo", pet.get("arquivo") );
                startActivity(detalhe);
            }
        });

        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapter, View view, int position,long id) {
                Map<String, String> pet = (Map<String, String>) adapter.getItemAtPosition(position);
                if (t != null) {
                    t.cancel();
                }
                t = Toast.makeText(getBaseContext(),"Long Click: " + pet.get("Nome"),Toast.LENGTH_SHORT);
                t.show();
                return true;
            }
        });
    }
    public boolean isConnected(){
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
            return true;
        else
            return false;
    }
    static class ViewHolder {
        TextView nome;
        ImageView foto;
        int position;
    }
    private Runnable showUpdate = new Runnable(){
        public void run(){
            Vibrator v = (Vibrator) MainActivity.this.getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(500);

            new AlertDialog.Builder(MainActivity.this)
                    .setIcon(R.drawable.ic_launcher)
                    .setTitle("Atualização Disponivel")
                    .setMessage("Existe uma atualização disponivel, deseja verificar?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            Toast.makeText(MainActivity.this, "Atualizando sistema",Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            Toast.makeText(MainActivity.this, "Atualização cancelada",Toast.LENGTH_LONG).show();
                        }
                    })
                    .show();
        }
    };
    private Thread checkUpdate = new Thread() {
        public void run() {
            try {
                URL updateURL = new URL("https://gist.githubusercontent.com/anonymous/3906da947187879d882d/raw/d8263ee9860594d2806b0dfd1bfd17528b0ba2a4/update.txt");
                URLConnection conn = updateURL.openConnection();
                InputStream is = conn.getInputStream();
                BufferedInputStream bis = new BufferedInputStream(is);
                ByteArrayBuffer baf = new ByteArrayBuffer(50);

                int current = 0;
                while((current = bis.read()) != -1){
                    baf.append((byte)current);
                }
                final String s = new String(baf.toByteArray());

                int curVersion = getPackageManager().getPackageInfo("br.com.targettrust.ttpetshop", 0).versionCode;
                int newVersion = Integer.valueOf(s);

                if (newVersion > curVersion) {
                    mHandler.post(showUpdate);
                }
            } catch (Exception e) {
            }
        }
    };

    private static String convertInputStreamToString(InputStream inputStream) throws IOException{
        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while((line = bufferedReader.readLine()) != null)
            result += line;

        inputStream.close();
        return result;

    }
    public class AsyncTaskParseJson extends AsyncTask<Void, Void, String> {
        final String TAG = "AsyncTaskParseJson";
        InputStream inputStream = null;
        String result = "";

        String webservice_url = "https://gist.githubusercontent.com/jacksonfdam/6a3bbc8a104878a5c1b8/raw/d4305882ea989a940d279c69623f694c1ac59135/pets";

        JSONArray dataJsonArr = null;

        @Override
        protected void onPreExecute() {
            Log.v(TAG, webservice_url);
            progressDialog.setMessage("Downloading ...");
            progressDialog.show();
            progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface arg0) {
                    AsyncTaskParseJson.this.cancel(true);
                }
            });
        }

        @Override
        protected String doInBackground(Void... params) {
            InputStream inputStream = null;
            String result = "";
            try {
                HttpClient httpclient = new DefaultHttpClient();
                HttpResponse httpResponse = httpclient.execute(new HttpGet(webservice_url));
                inputStream = httpResponse.getEntity().getContent();
                if(inputStream != null)
                    result = convertInputStreamToString(inputStream);
                else
                    result = "Ocorreu um erro";
            } catch (Exception e) {
                AsyncTaskParseJson.this.cancel(true);
                if (progressDialog.isShowing())
                    progressDialog.dismiss();
                Log.d("InputStream", e.getLocalizedMessage());
            }
            return result;
        }


        @Override
        protected void onPostExecute(String strFromDoInBg) {
            Log.v(TAG, "strFromDoInBg : " + strFromDoInBg);
            JSONObject pet = null;
            try {
                pet = new JSONObject(strFromDoInBg);
                JSONArray jArray = pet.getJSONArray("pets");
                Log.v(TAG, "jArray : " + jArray.length());
                for(int i=0; i < jArray.length(); i++) {
                    JSONObject jObject = jArray.getJSONObject(i);
                    List<Pet> petsExistentes = db.getPetsByName(jObject.getString("nome"));
                    if(petsExistentes.size() == 0){
                        Pet petFromJson = new Pet(jObject.getString("nome"),jObject.getString("arquivo"));
                        long petFromJson_id = db.createPet(petFromJson);
                        Log.v(TAG, petFromJson_id + " - " + petFromJson.getNome() + " - " +petFromJson.getArquivo());
                    }
                }
                Log.v(TAG, "Pet Count: " + db.getAllPets().size());
                db.closeDB();

                PetJSONAdapter adapter = new PetJSONAdapter(getBaseContext(), jArray);

                lv.setAdapter(adapter);
                lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapter, View view, int position,long id) {
                       JSONObject pet = null;
                        pet = (JSONObject) adapter.getItemAtPosition(position);
                        try {
                            t = Toast.makeText(getBaseContext(),"Selecionado: " + pet.getString("nome"),Toast.LENGTH_SHORT);
                            t.show();
                            Intent detalhe = new Intent(getBaseContext(),DetalheActivity.class);
                            detalhe.putExtra("nome", pet.getString("nome") );
                            detalhe.putExtra("arquivo", pet.getString("arquivo") );
                            startActivity(detalhe);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });

                if (progressDialog.isShowing())
                    progressDialog.dismiss();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
    public class TrabalhoParalelo extends AsyncTask<String, Void, Bitmap> {

        String TAG = "TrabalhoParalelo";

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }
        @Override
        protected Bitmap doInBackground(String... params) {
            Bitmap img = null;
            try{

                URL url = new URL(params[0]);
                HttpURLConnection conexao = (HttpURLConnection) url.openConnection();
                InputStream input = conexao.getInputStream();
                img = BitmapFactory.decodeStream(input);

                String linha;
                String resultado = "";
                URL url1 = new URL("https://gist.githubusercontent.com/jacksonfdam/7e941a6f2330c6b207ee/raw/8bd854ce274e9a4a13c71400292c439828732592/mensagem.txt");
                BufferedReader in = new BufferedReader(new InputStreamReader(url1.openStream()));
                while ((linha = in.readLine()) != null) {
                    resultado += linha;
                }
                in.close();
                Log.v(TAG,resultado);

            }
            catch(IOException e){}

            return img;
        }
        @Override
        protected void onPostExecute(Bitmap result) {
            super.onPostExecute(result);
            if(result != null)
                iv.setImageBitmap(result);
        }

    }


    public class PetAdapter extends BaseAdapter {
        List<Pet> pets;
        Context ctx;
        AssetManager manager = getAssets();
        private String TAG = PetAdapter.class.getSimpleName();
        public PetAdapter(Context ctx, List<Pet> pets) {
            super();
            this.pets = pets;
            this.ctx = ctx;
            Log.v(TAG,"Construtor");
        }
        @Override
        public int getCount() {
            Log.v(TAG,"getCount : " +  pets.size() );
            return pets.size();
        }
        @Override
        public Pet getItem(int position) {
            Log.v(TAG,"getItem : " + position);
            return pets.get(position);
        }
        @Override
        public long getItemId(int position) {
            Log.v(TAG,"getItemId : " + position);
            return position;
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Log.v(TAG,"getView : " + position);
            View rowView = convertView;
            ViewHolder viewHolder;
            // reusa views
            if (rowView == null) {
                Log.v(TAG,"getView ViewHolder: " + position);
                LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = inflater.inflate(R.layout.linha_pet_list_item, null);

                // configura view holder
                viewHolder = new ViewHolder();
                viewHolder.nome = (TextView) rowView.findViewById(R.id.textView);
                viewHolder.foto = (ImageView) rowView.findViewById(R.id.imageView);
                rowView.setTag(viewHolder);
            }else{
                viewHolder = (ViewHolder) convertView.getTag();
            }
            // acessar o dado da posição
            Pet pet = getItem(position);

            // fill data
            viewHolder.nome.setText( pet.getNome() );

            try {
                InputStream ims = manager.open( pet.getFoto());
                Drawable d = Drawable.createFromStream(ims, null);
                viewHolder.foto.setImageDrawable(d);
            } catch(IOException ex) {
                Log.e("PetAdapter", ex.getLocalizedMessage());
            }

            // retorna o layout preenchido
            return rowView;
        }

    }
    public class PetJSONAdapter extends BaseAdapter {
        JSONArray pets;
        Context ctx;
        AssetManager manager = getAssets();
        private String TAG = PetAdapter.class.getSimpleName();
        public PetJSONAdapter(Context ctx, JSONArray pets) {
            super();
            this.pets = pets;
            this.ctx = ctx;
            Log.v(TAG,"Construtor");
        }
        @Override
        public int getCount() {
            Log.v(TAG,"getCount : " +  pets.length() );
            return pets.length();
        }
        @Override
        public JSONObject getItem(int position) {
            JSONObject jPet = null;
            Log.v(TAG,"getItem : " + position);

            try {
                jPet = pets.getJSONObject(position);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return jPet;
        }
        @Override
        public long getItemId(int position) {
            Log.v(TAG,"getItemId : " + position);
            return position;
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Log.v(TAG,"getView : " + position);
            View rowView = convertView;
            ViewHolder viewHolder;
            // reusa views
            if (rowView == null) {
                Log.v(TAG,"getView ViewHolder: " + position);
                LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = inflater.inflate(R.layout.linha_pet_list_item, null);

                // configura view holder
                viewHolder = new ViewHolder();
                viewHolder.nome = (TextView) rowView.findViewById(R.id.textView);
                viewHolder.foto = (ImageView) rowView.findViewById(R.id.imageView);
                rowView.setTag(viewHolder);
            }else{
                viewHolder = (ViewHolder) convertView.getTag();
            }
            // acessar o dado da posição
            JSONObject pet = getItem(position);

            // fill data
            try {
                viewHolder.nome.setText( pet.getString("nome") );

                try {
                    InputStream ims = manager.open( pet.getString("arquivo") + ".jpg");
                    Drawable d = Drawable.createFromStream(ims, null);
                    viewHolder.foto.setImageDrawable(d);
                } catch(IOException ex) {
                    Log.e("PetAdapter", ex.getLocalizedMessage());
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

            // retorna o layout preenchido
            return rowView;
        }

    }
}