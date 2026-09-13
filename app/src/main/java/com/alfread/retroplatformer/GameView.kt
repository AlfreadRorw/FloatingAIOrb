package com.alfread.retroplatformer

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class GameView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var last = System.nanoTime()
    private var cam = 0f
    private var left = false
    private var right = false
    private var jump = false

    private var px = 260f
    private var py = 420f
    private var vx = 0f
    private var vy = 0f
    private var ground = false

    private var score = 0
    private var coinCount = 0
    private var lives = 3
    private var timer = 248f

    data class Platform(val x:Float,val y:Float,val w:Float,val h:Float)
    data class Coin(val x:Float,val y:Float,var taken:Boolean=false)
    data class Enemy(var x:Float,val y:Float,var d:Float=1f,var alive:Boolean=true)

    private val platforms = listOf(
        Platform(-100f,620f,900f,80f),
        Platform(1050f,650f,700f,80f),
        Platform(1950f,560f,650f,80f),
        Platform(2850f,650f,900f,80f),
        Platform(620f,380f,260f,45f),
        Platform(1350f,430f,300f,45f),
        Platform(2200f,330f,330f,45f)
    )
    private val coins = mutableListOf(
        Coin(700f,300f),Coin(770f,300f),Coin(840f,300f),
        Coin(960f,480f),Coin(1120f,510f),Coin(1460f,350f),
        Coin(1540f,350f),Coin(2280f,250f),Coin(2380f,250f),
        Coin(3100f,540f),Coin(3200f,540f)
    )
    private val enemies = mutableListOf(
        Enemy(420f,564f),Enemy(1200f,594f),Enemy(1500f,374f),
        Enemy(2100f,504f),Enemy(3050f,594f)
    )

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = min(.033f, (now-last)/1_000_000_000f)
        last = now
        update(dt)
        drawSky(c)
        c.save()
        c.translate(-cam,0f)
        platforms.forEach { drawPlatform(c,it) }
        drawPipe(c,260f,490f,150f,130f)
        drawPipe(c,1600f,510f,145f,140f)
        drawPipe(c,3350f,510f,145f,140f)
        drawBlock(c,790f,255f,true)
        drawBlock(c,860f,255f,false)
        drawBlock(c,930f,255f,true)
        coins.filter{!it.taken}.forEach { drawCoin(c,it.x,it.y) }
        enemies.filter{it.alive}.forEach { drawEnemy(c,it) }
        drawPlayer(c)
        c.restore()
        drawHud(c)
        drawControls(c)
        invalidate()
    }

    private fun update(dt:Float) {
        timer=max(0f,timer-dt)
        if(left){vx-=1800f*dt}
        if(right){vx+=1800f*dt}
        if(!left&&!right) vx*=.78f
        vx=vx.coerceIn(-350f,350f)
        if(jump&&ground){vy=-700f;ground=false}
        vy=min(1000f,vy+1750f*dt)

        px+=vx*dt
        py+=vy*dt
        ground=false

        platforms.forEach {
            if(hit(px,py,54f,68f,it.x,it.y,it.w,it.h) && vy>=0 && py+68f-vy*dt<=it.y+24f){
                py=it.y-68f;vy=0f;ground=true
            }
        }

        coins.forEach {
            if(!it.taken && hit(px,py,54f,68f,it.x-18,it.y-24,36f,48f)){
                it.taken=true;coinCount++;score+=100
            }
        }

        enemies.forEach {
            if(it.alive){
                it.x+=it.d*85f*dt
                if(it.x<0||it.x>3700)it.d*=-1f
                if(hit(px,py,54f,68f,it.x,it.y,58f,56f)){
                    if(vy>100f && py+60f<it.y+25f){
                        it.alive=false;vy=-420f;score+=200
                    } else {
                        lives--
                        px=260f;py=420f;vx=0f;vy=0f
                        if(lives<1){lives=3;score=0;coinCount=0;coins.forEach{x->x.taken=false};enemies.forEach{x->x.alive=true}}
                    }
                }
            }
        }

        if(py>height+150){lives--;px=260f;py=420f;vy=0f}
        cam=(px-width*.35f).coerceIn(0f,max(0f,3800f-width))
    }

    private fun drawSky(c:Canvas){
        c.drawColor(Color.rgb(35,135,225))
        p.color=Color.rgb(215,240,255)
        c.drawOval(80f,100f,430f,240f,p)
        c.drawOval(width*.55f,80f,width*.86f,190f,p)
        p.color=Color.rgb(110,190,105)
        for(i in -1..8)c.drawCircle(i*220f-(cam*.08f%220f),height*.72f,170f,p)
        p.color=Color.rgb(75,150,95)
        for(i in -1..10)c.drawCircle(i*170f-(cam*.14f%170f),height*.80f,110f,p)
        // original decorative castle silhouette
        p.color=Color.rgb(235,230,215)
        c.drawRect(width*.83f,height*.28f,width*.90f,height*.58f,p)
        c.drawRect(width*.79f,height*.36f,width*.83f,height*.58f,p)
        p.color=Color.rgb(205,75,110)
        triangle(c,width*.78f,height*.36f,width*.81f,height*.23f,width*.84f,height*.36f)
        triangle(c,width*.82f,height*.28f,width*.865f,height*.14f,width*.91f,height*.28f)
    }

    private fun drawPlatform(c:Canvas,a:Platform){
        p.color=Color.rgb(50,60,35);c.drawRect(a.x-4,a.y-4,a.x+a.w+4,a.y+a.h+4,p)
        p.color=Color.rgb(90,205,60);c.drawRect(a.x,a.y,a.x+a.w,a.y+24,p)
        p.color=Color.rgb(145,84,43);c.drawRect(a.x,a.y+24,a.x+a.w,a.y+a.h,p)
        p.color=Color.rgb(190,115,60)
        var x=a.x+18
        while(x<a.x+a.w){c.drawCircle(x,a.y+50,9f,p);x+=35}
    }

    private fun drawPipe(c:Canvas,x:Float,y:Float,w:Float,h:Float){
        p.color=Color.rgb(15,75,35);c.drawRect(x,y,x+w,y+h,p)
        p.color=Color.rgb(25,170,60);c.drawRect(x+8,y+5,x+w-8,y+h,p)
        p.color=Color.rgb(50,220,80);c.drawRect(x+22,y+5,x+42,y+h,p)
        p.color=Color.rgb(10,85,35);c.drawRect(x-10,y-20,x+w+10,y+20,p)
        p.color=Color.rgb(45,205,70);c.drawRect(x,y-13,x+w,y+16,p)
    }

    private fun drawBlock(c:Canvas,x:Float,y:Float,question:Boolean){
        p.color=Color.rgb(85,45,25);c.drawRect(x-3,y-3,x+69,y+69,p)
        p.color=if(question)Color.rgb(246,170,42) else Color.rgb(160,88,43)
        c.drawRect(x,y,x+66,y+66,p)
        if(question){
            p.color=Color.WHITE;p.textAlign=Paint.Align.CENTER;p.textSize=48f
            c.drawText("?",x+33,y+51,p)
        }
    }

    private fun drawCoin(c:Canvas,x:Float,y:Float){
        p.color=Color.rgb(90,55,10);c.drawOval(x-18,y-27,x+18,y+27,p)
        p.color=Color.rgb(255,195,25);c.drawOval(x-14,y-24,x+14,y+24,p)
        p.color=Color.rgb(255,240,90);c.drawRect(x-5,y-19,x+4,y+19,p)
    }

    private fun drawEnemy(c:Canvas,e:Enemy){
        p.color=Color.rgb(20,40,75);c.drawRoundRect(e.x,e.y,e.x+58,e.y+56,24f,24f,p)
        p.color=Color.rgb(60,135,225);c.drawRoundRect(e.x+4,e.y+4,e.x+54,e.y+52,20f,20f,p)
        p.color=Color.WHITE;c.drawCircle(e.x+20,e.y+22,5f,p);c.drawCircle(e.x+40,e.y+22,5f,p)
        p.color=Color.BLACK;c.drawCircle(e.x+21,e.y+22,2f,p);c.drawCircle(e.x+41,e.y+22,2f,p)
    }

    private fun drawPlayer(c:Canvas){
        // original helmeted hero, not Mario
        p.color=Color.rgb(20,30,55);c.drawRoundRect(px+5,py,px+50,py+43,18f,18f,p)
        p.color=Color.rgb(225,238,250);c.drawRoundRect(px+9,py+3,px+47,py+38,14f,14f,p)
        p.color=Color.rgb(245,190,150);c.drawRoundRect(px+18,py+16,px+39,py+35,5f,5f,p)
        p.color=Color.rgb(40,45,50);c.drawCircle(px+24,py+24,3f,p);c.drawCircle(px+34,py+24,3f,p)
        p.color=Color.rgb(65,125,210);c.drawRect(px+13,py+40,px+44,py+63,p)
        p.color=Color.rgb(220,70,55);c.drawRect(px-13,py+46,px+9,py+54,p)
        p.color=Color.rgb(45,70,120);c.drawRect(px+13,py+63,px+25,py+69,p);c.drawRect(px+34,py+63,px+46,py+69,p)
    }

    private fun drawHud(c:Canvas){
        p.color=Color.WHITE;p.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);p.textSize=32f;p.textAlign=Paint.Align.LEFT
        c.drawText("× $lives",35f,55f,p)
        c.drawText("SCORE",width*.32f,40f,p);c.drawText(score.toString().padStart(6,'0'),width*.32f,78f,p)
        c.drawText("◉ × $coinCount",width*.53f,58f,p)
        c.drawText("STAGE",width*.70f,40f,p);c.drawText("1-1",width*.72f,78f,p)
        c.drawText("TIME",width*.88f,40f,p);c.drawText(timer.toInt().toString(),width*.90f,78f,p)
    }

    private fun drawControls(c:Canvas){
        button(c,70f,height-100f,"◀",left)
        button(c,180f,height-100f,"▶",right)
        button(c,width-105f,height-100f,"JUMP",jump)
    }

    private fun button(c:Canvas,x:Float,y:Float,label:String,on:Boolean){
        p.color=if(on)Color.argb(210,240,240,255) else Color.argb(150,20,35,55)
        c.drawCircle(x,y,if(label=="JUMP")58f else 48f,p)
        p.color=Color.WHITE;p.textAlign=Paint.Align.CENTER;p.textSize=if(label=="JUMP")17f else 34f
        c.drawText(label,x,y+10,p)
    }

    override fun onTouchEvent(e:MotionEvent):Boolean{
        fun controls(){
            left=false;right=false;jump=false
            for(i in 0 until e.pointerCount){
                val x=e.getX(i);val y=e.getY(i)
                if(y>height-210){
                    if(x<125)left=true
                    else if(x<250)right=true
                    else if(x>width-230)jump=true
                }
            }
        }
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_MOVE->controls()
            MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP,MotionEvent.ACTION_CANCEL->{
                left=false;right=false;jump=false
            }
        }
        return true
    }

    private fun hit(ax:Float,ay:Float,aw:Float,ah:Float,bx:Float,by:Float,bw:Float,bh:Float)=
        ax<bx+bw && ax+aw>bx && ay<by+bh && ay+ah>by

    private fun triangle(c:Canvas,x1:Float,y1:Float,x2:Float,y2:Float,x3:Float,y3:Float){
        val path=Path();path.moveTo(x1,y1);path.lineTo(x2,y2);path.lineTo(x3,y3);path.close();c.drawPath(path,p)
    }
}
