package com.example.vcvyc.myapplication;

import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.media.FaceDetector;
import android.media.Image;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

public class MainActivity extends AppCompatActivity {
    //模型地址、facenet类、要比较的两张图片
    public static String model_path="file:///android_asset/20180402-114759.pb";
    public Facenet facenet;
    public Bitmap bitmap1;
    public Bitmap bitmap2;
    public Bitmap bmpEnroll;
    public Bitmap bmpReco;
    //图片显示的空间
    public ImageView imageView1;
    public ImageView imageView2;
    //
    public MTCNN mtcnn;
    public Map<String, List<float[]>> embeddingsMap = new HashMap<>(); // dicc de embeddings
    public String inputText;
    public double RECOGNIZE_THRESHOLD = 0.9;

    //从assets中读取图片
    private  Bitmap readFromAssets(String filename){
        Bitmap bitmap;
        AssetManager asm=getAssets();
        try {
            InputStream is=asm.open(filename);
            bitmap= BitmapFactory.decodeStream(is);
            is.close();
        } catch (IOException e) {
            Log.e("MainActivity","[*]failed to open "+filename);
            e.printStackTrace();
            return null;
        }
        return Utils.copyBitmap(bitmap);
    }

    // funcion para cargar fotos de enroll en espacio de vectores

    public boolean loadEnrollImgs(){
        try {
            String personList[] = getAssets().list("database/enroll");   // lista de personas
            int totalPers = personList.length;
            int cnt = 0;
            for (String person: personList){       // carpeta de personas
                String imgList[] = getAssets().list("database/enroll/"+person);  // aqui puede fallar /
                cnt++;
                String cntString = "Personas: "+cnt+" de "+totalPers;
                for (String img: imgList){
                    Log.d("MainActivity", cntString);
                    bmpEnroll = readFromAssets("database/enroll/"+person+"/"+img);
                    FaceFeature vector = getEmbedding(bmpEnroll);      // devuelve FaceFeature
                    float[] embedding = vector.getFeature();        // devuelve Embedding
                    Log.d("MainActivity", "Ingresando--- Persona: "+person+", Foto: "+img);
                    agregarEmbedding(person, embedding);         // agregar Embedding a espacio
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean loadRecoImgs(){
        try {
            String personList[] = getAssets().list("database/reco");   // lista de personas
            int totalPers = personList.length;
            int cnt = 0;
            for (String person: personList){       // carpeta de personas
                String imgList[] = getAssets().list("database/reco/"+person);  // aqui puede fallar /
                if (imgList.length < 8) continue;
                cnt++;
                String cntString = "Personas: "+cnt+" de "+totalPers;
                int cntCincoFotos = 0;
                for (String img: imgList){
                    cntCincoFotos++;
                    if (cntCincoFotos > 5) break;   // tomar solo primeras 5 fotos
                    Log.d("loadRecoImgs", cntString);
                    bmpEnroll = readFromAssets("database/reco/"+person+"/"+img);

                    try {
                        FaceFeature vector = getEmbedding(bmpEnroll);      // devuelve FaceFeature
                    float[] embedding = vector.getFeature();        // devuelve Embedding
                    Log.d("loadRecoImgs", "Ingresando--- Persona: "+person+", Foto: "+img);
                    agregarEmbedding(person, embedding);         // agregar Embedding a espacio
                    }
                    catch (ArrayIndexOutOfBoundsException e) {
                        cntCincoFotos--;
                        Log.d("loadRecoImgs", "SALTADA");
                        continue;
                    }
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public float getPrecision(){
        try {
            int totalFotos = 0;     // contador total fotos VALIDADAS
            int fotosCorrectas = 0; // contador fotos bien etiquetadas
            String personList[] = getAssets().list("database/reco");   // lista de personas
            int totalPers = personList.length;
            int cnt = 0;    // contador personas
            int cntSaltados = 0;    // contador saltados por MTCNN
            int cntConfundido = 0;  // contador pers confundidas por otra
            int cntNF = 0;          // contador personas sin cercanos
            for (String person: personList){       // carpeta de personas
                int cntNroFoto=0;       // contador para saltar primeras 5 fotos
                String imgList[] = getAssets().list("database/reco/"+person);
                cnt++;
                String cntString = "Personas: "+cnt+" de "+totalPers;
                for (String img: imgList){
                    if (imgList.length < 8) continue;
                    cntNroFoto++;
                    if (cntNroFoto <5) continue;  // saltarse primeras 5 fotos
                    if (cntNroFoto >10) break;        // terminar de pasar las fotos
                    Log.d("MainActivity", cntString);
                    bmpReco = readFromAssets("database/reco/"+person+"/"+img);
                    try {
                        FaceFeature vector = getEmbedding(bmpReco); // devuelve FaceFeature
                        float[] embedding = vector.getFeature();        // devuelve Embedding
                        String prediccion = buscarCercano(embedding);
                        totalFotos++;
                        if (prediccion.equals("No hay cercano")){
                            cntNF++;
                            Log.d("Prediccion", "ERROR(" + prediccion + ")--- Persona: " +
                                    person + ", Foto: " + img);
                        }
                        else if (person.equals(prediccion)) {
                            fotosCorrectas++;
                            Log.d("Prediccion", "OK--- Persona: " + person + ", Foto: " +
                                    img);
                        }
                        else {
                            cntConfundido++;
                            Log.d("Prediccion", "ERROR(" + prediccion + ")--- Persona: " +
                                    person + ", Foto: " + img);
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        Log.d("Prediccion", "SKIP--- Persona: "+
                                person + ", Foto: " + img);
                        cntSaltados++;
                        break;
                    }
                }
            }
            Log.d("Resumen", "TOT. FOTOS:"+totalFotos+" OK:"+fotosCorrectas +
                    " CONFUNDIDOS:"+cntConfundido+" NO ENCONTRADOS:"+ cntNF+" SKIP:"+
                    cntSaltados);
            return ((float) fotosCorrectas*100/totalFotos);
        } catch (IOException e) {
            return -1;
        }
    }


    public void textviewLog(String msg){
        TextView textView=(TextView)findViewById(R.id.textView);
        textView.append("\n"+msg);
    }
    public void showEmbedding(FaceFeature vector, long time){
        TextView textView=(TextView)findViewById(R.id.textView2);
        textView.setText("[*]Face detection + RBS running time:"+time+"\n");
        String v1Str = Arrays.toString(Arrays.copyOfRange(vector.getFeature(), 0, 4));

        // IMPLEMENTAR MANEJO DE ERRORES
        textView.append("[*]EMBEDDING: " + v1Str + "...\n");
    }

    public void showEmbeddingsMap(){
        TextView textView=(TextView)findViewById(R.id.textView2);
        for (String s : embeddingsMap.keySet()) {
            textView.append("[*]Nombre: " + s + " "); //Optional for better understanding
            int nroEmbeddings = 0;
            for (float[] r : embeddingsMap.get(s)) {
                nroEmbeddings++;

                textView.append(Arrays.toString(Arrays.copyOfRange(r, 0, 4)) + "...\n");
//                textView.append(Arrays.toString(r) + "...\n");
            }
//            textView.append(Integer.toString(nroEmbeddings));
        }
    }

    public void agregarEmbedding(String inputText, float[] embedding){
        if (embeddingsMap.containsKey(inputText)){
            embeddingsMap.get(inputText).add(embedding);
        }
        else{
            List<float[]> listaEmbeddings = new ArrayList<>();
            listaEmbeddings.add(embedding);
            embeddingsMap.put(inputText, listaEmbeddings);

        }
    }

    public static double getDistance(float[] array1, float[] array2){
        double Sum = 0.0;
        for (int i=0;i<array1.length;i++) {
            Sum = Sum+Math.pow((array1[i]-array2[i]),2.0);
        }
        return Math.sqrt(Sum);
    }

    public String buscarCercano(float[] embedding){
        Map<String, ArrayList<Double>> cercanos = new HashMap<>();
        // se hace la tabla con las distancias de cada individuo
        for (Map.Entry<String, List<float[]>> entry : embeddingsMap.entrySet()){ // recorrer keys
            for (int i=0; i<entry.getValue().size();i++){       // recorrer embeddings de c key
                double distance = getDistance(embedding, entry.getValue().get(i));
                if (distance < RECOGNIZE_THRESHOLD){        // si es menor que limite de dist
                    if (cercanos.containsKey(entry.getKey())){  // caso ya esta en cercanos
                        List<Double> listaCercanos = cercanos.get(entry.getKey());
                        listaCercanos.add(distance);
                    }
                    else{       // caso no esta en cercanos
                        ArrayList<Double> listaDistance = new ArrayList<Double>();
                        listaDistance.add(distance);
                        cercanos.put(entry.getKey(), listaDistance) ;  // agregar lista a dict
                    }
                }
            }
        }

        String resultado = "No Encontrado";
        if (cercanos.isEmpty()) return "No hay cercano";

        ArrayList<String> top3 = new ArrayList<>();
        // ordenar y obtener el mas cercano
        for (int i = 0; i<3; i++) {
            double minPrev = 10000;
            for (String s : cercanos.keySet()) {   // cada key
                for (Double r : cercanos.get(s)) {    // cada distancia
                    if (r < minPrev) {        // ver si es el menor
                        minPrev = r;         // actualizar
                        resultado = s;          // actualizar nombre del mas cerca
                    }
                }
            }
            // agregar a lista top5
            top3.add(resultado);
            // remover valor
            List<Double> listaCercanos = cercanos.get(resultado);
            listaCercanos.remove(minPrev);

        }

        int frec = 0;
        String masCercano = "No Encontrado";
        for (String name : top3){
            if (Collections.frequency(top3,name)>frec){
                masCercano = name;
            }
        }
        return masCercano;
    }

    //比较bitmap1和bitmap2(会先切割人脸在比较)
    public FaceFeature getEmbedding(Bitmap bmp){
        //(1)圈出人脸，人脸检测(可能会有多个人脸)
        /*安卓自带人脸检测实现
        Rect rect1 = FaceDetect.detectBiggestFace(bitmap1);
        if (rect1==null) return -1;
        Rect rect2 = FaceDetect.detectBiggestFace(bitmap2);
        if (rect2==null) return -2;*/
        Bitmap bm1=Utils.copyBitmap(bmp);
        //Bitmap bm2=Utils.copyBitmap(bitmap2);
        Vector<Box> boxes=mtcnn.detectFaces(bmp,40);
        //Vector<Box> boxes1=mtcnn.detectFaces(bitmap2,40);
        //if (boxes.size()==0) return -1;
        //if (boxes1.size()==0)return -2;
        for (int i=0;i<boxes.size();i++) Utils.drawBox(bmp,boxes.get(i),1+bmp.getWidth()/500 );
        //for (int i=0;i<boxes1.size();i++) Utils.drawBox(bitmap2,boxes1.get(i),1+bitmap2.getWidth()/500 );
        //Log.i("Main","[*]boxNum"+boxes1.size());
        Rect rect1=boxes.get(0).transform2Rect();
        //Rect rect2=boxes1.get(0).transform2Rect();
        //MTCNN检测到的人脸框，再上下左右扩展margin个像素点，再放入facenet中。
        int margin=0; //20这个值是facenet中设置的。自己应该可以调整。     CAMBIADO (20)
        Utils.rectExtend(bmp,rect1,margin);
        //Utils.rectExtend(bitmap2,rect2,margin);
        //要比较的两个人脸，加厚Rect
        Utils.drawRect(bmp,rect1,1+bmp.getWidth()/100 );
        //Utils.drawRect(bitmap2,rect2,1+bitmap2.getWidth()/100 );
        //(2)裁剪出人脸(只取第一张)
        Bitmap face1=Utils.crop(bmp,rect1);
        //Bitmap face2=Utils.crop(bitmap2,rect2);
        //(显示人脸)
        imageView1.setImageBitmap(bmp);
        //imageView2.setImageBitmap(bitmap2);
        //(3)特征提取
        FaceFeature ff1=facenet.recognizeImage(face1);
        //FaceFeature ff2=facenet.recognizeImage(face2);

        bmp=bm1;
        //bitmap2=bm2;
        //(4)比较
        //return ff1.compare(ff2);
        return ff1;
    }


        @Override
    protected void onCreate(Bundle savedInstanceState) {        // corre al inicializar
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mtcnn=new MTCNN(getAssets());
        imageView1=(ImageView)findViewById(R.id.imageView);
        //imageView2=(ImageView)findViewById(R.id.imageView2);
        final TextView textView4=(TextView)findViewById(R.id.textView4);
        //载入facenet
        long t_start=System.currentTimeMillis();
        facenet=new Facenet(getAssets());
        long t2=System.currentTimeMillis();
        textviewLog("[*]Tiempo de carga del modelo [ms]:"+(t2-t_start));
            //先从assets中读取图片
        bitmap1=readFromAssets("trump1.jpg");
        //bitmap2=readFromAssets("trump2.jpg");
        long t1=System.currentTimeMillis();
        FaceFeature vector=getEmbedding(bitmap1);
        showEmbedding(vector, System.currentTimeMillis()-t1);
        //Log.d("MainActivity","[*] end,score="+score);

        //以下是控件事件绑定之类；添加自己上传图片的功能

        imageView1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("MainActivity","[*]you click me ");
                Intent intent= new Intent(Intent.ACTION_PICK,null);
                intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,"image/*");
                startActivityForResult(intent, 0x1);
            }
        });

        Button btn=(Button)findViewById(R.id.button);   // BOTON CHICO ENTRENAMIENTO
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long t1=System.currentTimeMillis();
                FaceFeature vector=getEmbedding(bitmap1);
                float[] embedding = vector.getFeature();

                EditText mEdit = (EditText)findViewById(R.id.editText);
                inputText = mEdit.getText().toString();     // actualizar inputText con nombre
                agregarEmbedding(inputText, embedding);
                Toast.makeText(MainActivity.this, inputText +
                        " fue agregado al Database", Toast.LENGTH_SHORT).show();
                // cerrar teclado
                InputMethodManager inputManager = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                        InputMethodManager.HIDE_NOT_ALWAYS);
                mEdit.getText().clear();
                showEmbedding(getEmbedding(bitmap1), System.currentTimeMillis()-t1);
                showEmbeddingsMap();
            }
        });

        Button btn2=(Button)findViewById(R.id.button2); // BOTON GRANDE RECONOCER
        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long t1=System.currentTimeMillis();
                FaceFeature vector=getEmbedding(bitmap1);
                float[] embedding = vector.getFeature();
                String prediccion = buscarCercano(embedding);
                // PRINTEAR "PREDICCION RECONOCIDO"
                textView4.setText(prediccion);
                textView4.setVisibility(View.VISIBLE);
                Timer t = new Timer(false);
                t.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                textView4.setVisibility(View.INVISIBLE);
                            }
                        });
                    }
                }, 5000);
            }
        });

        // BOTON PARA CARGAR DATASET ENROLL
            Button btn3=(Button)findViewById(R.id.button3);
            btn3.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    textView4.setText("Cargando Dataset");
//                    loadEnrollImgs();
                    loadRecoImgs();
                    textView4.setText(" Enroll Dataset Cargado");
                    Log.d("MainActivity", "ENROLL DATASET IMPORTADO CORRECTAMENTE");

                }
            });

            // BOTON PARA OBTENER PRECISION
            Button btn4=(Button)findViewById(R.id.button4);
            btn4.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    textView4.setText("Calculando Precision");
                    float precision = getPrecision();
                    textView4.setText("Precision = "+precision);
                    Log.d("MainActivity", "PRECISION OBTENIDA =" +precision);

                }
            });
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        if(data==null)return;
        try {
            Bitmap bm = MediaStore.Images.Media.getBitmap(this.getContentResolver(), data.getData());
            if (bm.getWidth()>1000)  bm=Utils.resize(bm,1000);
            if (requestCode == 0x1 && resultCode == RESULT_OK) {
                //imageView1.setImageURI(data.getData());
                bitmap1=Utils.copyBitmap(bm);
                imageView1.setImageBitmap(bitmap1);
            }else {
                //imageView2.setImageURI(data.getData());
                bitmap2=Utils.copyBitmap(bm);
                imageView2.setImageBitmap(bitmap2);
            }
        }catch (Exception e){
            Log.d("MainActivity","[*]"+e);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
