package com.novatron.novatronbiocore;

import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class MainActivity extends AppCompatActivity{

    @Override
    protected void onCreate(Bundle bundle){
        super.onCreate(bundle);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        //oyunu tam ekran yapmak için başlık çubuğnu ve bildirim panelini gizliyoruz
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        //oyun çöktüğünde hatayı dosyaya yaz ve oyunu kapat
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            saveCrashLog(throwable);
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        });

        try{

            GamePanel gp = new GamePanel(this);
            setContentView(gp);

            //önceki çalıştırmadan hata varsa göster
            checkCrashLog();

        }catch(Exception e){
            saveCrashLog(e);

            //oyun çöktüğü için ekranda siyah bir boşluk kalmasın diye geçici bir yazı yerleştir
            TextView errorView = new TextView(this);
            errorView.setText("The game could not be started!\nPlease forward the error report on the screen to the developer.");
            errorView.setGravity(Gravity.CENTER);
            errorView.setTextSize(18);
            setContentView(errorView);

            //kullanıcıya az önce fırlayan constructor hatasını diyalog olarak göster
            checkCrashLog();
        }
    }

    private void saveCrashLog(Throwable throwable){
        try{
            //Hata mesajını ve StackTrace'i (satır numaralarını) string'e çeviriyoruz
            String logPath = getApplicationContext().getCacheDir() + "/Game_Error";
            java.io.FileWriter fw = new java.io.FileWriter(logPath, false);
            java.io.PrintWriter pw = new java.io.PrintWriter(fw);

            throwable.printStackTrace(pw);
            pw.close();
            fw.close();
        }catch(Exception ignored){}
    }

    //File kayitDosyasi = new File(getContext().getFilesDir(), "oyun_kaydi.txt"); oyun kaydi için
    private void checkCrashLog(){
        File errorFile = new File(getCacheDir(), "Game_Error");

        if(errorFile.exists()){
            StringBuilder errorText = new StringBuilder();
            try{
                BufferedReader br = new BufferedReader(new FileReader(errorFile));
                String line;

                while((line = br.readLine()) != null)
                    errorText.append(line).append("\n");

                br.close();

            }catch(Exception ignored){}

            new AlertDialog.Builder(this)
                    .setTitle("The game crashed the last time!")
                    .setMessage(errorText.toString())
                    .setCancelable(false) //Kullanıcı ekrana rastgele basıp kapatamasın
                    .setPositiveButton("I copied the error code, delete it.",
                            (dialog, which) -> errorFile.delete())
                    .show();
        }
    }
}

//cd $env:LOCALAPPDATA\Android\Sdk\platform-tools
//./adb devices
//./adb tcpip 5555
//./adb connect 192.168.1.106: