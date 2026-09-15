package com.metagz.supersuscheat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

public class SplashActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.splashLogo);
        TextView txt = findViewById(R.id.splashText);

        logo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_scale));
        txt.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in));

        new Handler().postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        }, 2200);
    }
}
