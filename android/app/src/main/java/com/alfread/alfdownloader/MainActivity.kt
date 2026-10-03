package com.alfread.alfdownloader

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

const val BASE="http://127.0.0.1:8080"
const val TERMUX_PATH="/data/data/com.termux/files/home/Github/termux-server/start.sh"
val client=OkHttpClient.Builder().connectTimeout(3,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).build()

data class Job(val id:String,val status:String,val progress:Double,val message:String?,val error:String?)

class MainActivity:ComponentActivity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);setContent{Theme{App{startTermux()}}}}
 private fun startTermux(){
  try{
   val i=Intent("com.termux.RUN_COMMAND").setPackage("com.termux")
    .putExtra("com.termux.RUN_COMMAND_PATH",TERMUX_PATH)
    .putExtra("com.termux.RUN_COMMAND_WORKDIR","/data/data/com.termux/files/home/Github/termux-server")
    .putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
   startService(i)
   Toast.makeText(this,"Termux server diminta untuk start",Toast.LENGTH_SHORT).show()
  }catch(e:Exception){Toast.makeText(this,"Aktifkan allow-external-apps di Termux",Toast.LENGTH_LONG).show()}
 }
}

@Composable fun Theme(content:@Composable()->Unit){
 MaterialTheme(colorScheme=darkColorScheme(primary=Color.White,onPrimary=Color.Black,background=Color.Black,
  surface=Color(0xFF111111),surfaceVariant=Color(0xFF191919),onSurface=Color.White,onBackground=Color.White),content=content)
}
@Composable fun App(start:()->Unit){
 var tab by remember{mutableIntStateOf(0)}
 Scaffold(containerColor=Color.Black,bottomBar={
  NavigationBar(containerColor=Color(0xFF080808)){
   listOf(Icons.Default.Download to "Status",Icons.Default.History to "History",Icons.Default.Settings to "Settings").forEachIndexed{i,p->
    NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(p.first,null)},label={Text(p.second)})
   }
  }
 }){pad->when(tab){0->Home(Modifier.padding(pad),start);1->History(Modifier.padding(pad));2->Settings(Modifier.padding(pad))}}
}

@Composable fun Home(mod:Modifier,start:()->Unit){
 val scope=rememberCoroutineScope()
 var url by remember{mutableStateOf("")};var quality by remember{mutableStateOf("best")}
 var online by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)}
 var message by remember{mutableStateOf<String?>(null)};var job by remember{mutableStateOf<Job?>(null)}
 LaunchedEffect(Unit){while(true){online=health();delay(3000)}}
 LaunchedEffect(job?.id){val id=job?.id?:return@LaunchedEffect;while(true){apiJob(id)?.let{job=it};if(job?.status in listOf("completed","error","cancelled"))break;delay(1000)}}
 Column(mod.fillMaxSize().background(Color.Black).padding(24.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
   Column{Text("ALF Downloader",fontSize=32.sp,fontWeight=FontWeight.Black);Text("Monochrome media hub",color=Color.Gray)}
   Surface(shape=RoundedCornerShape(50),color=if(online)Color.White else Color(0xFF1B1B1B)){
    Text(if(online)"SERVER ONLINE" else "SERVER OFFLINE",color=if(online)Color.Black else Color.LightGray,fontSize=11.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(14.dp))
   }
  }
  Spacer(Modifier.height(24.dp))
  Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF111111)),shape=RoundedCornerShape(24.dp)){
   Column(Modifier.padding(20.dp)){
    Text("VIDEO URL",color=Color.Gray,fontWeight=FontWeight.Bold,fontSize=13.sp)
    OutlinedTextField(value=url,onValueChange={url=it},modifier=Modifier.fillMaxWidth().padding(top=8.dp),
     minLines=2,shape=RoundedCornerShape(14.dp),placeholder={Text("https://...")})
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth()){
     OutlinedButton(onClick={scope.launch{online=health()}},modifier=Modifier.weight(1f)){Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(5.dp));Text("CHECK")}
     Spacer(Modifier.width(8.dp))
     OutlinedButton(onClick={scope.launch{message=apiInfo(url)}},modifier=Modifier.weight(1f)){Icon(Icons.Default.Info,null);Spacer(Modifier.width(5.dp));Text("GET INFO")}
    }
   }
  }
  Spacer(Modifier.height(18.dp))
  Text("QUALITY",color=Color.Gray,fontWeight=FontWeight.Bold,fontSize=13.sp)
  Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
   listOf("best","720p","480p","audio").forEach{q->FilterChip(selected=quality==q,onClick={quality=q},label={Text(q.uppercase())})}
  }
  message?.let{Spacer(Modifier.height(12.dp));Text(it,fontSize=13.sp,color=Color.LightGray)}
  job?.let{j->
   Spacer(Modifier.height(14.dp));Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF111111)),shape=RoundedCornerShape(18.dp)){
    Column(Modifier.padding(15.dp)){Text("DOWNLOAD ${j.status.uppercase()}",fontWeight=FontWeight.Bold)
     Spacer(Modifier.height(8.dp));LinearProgressIndicator(progress={(j.progress/100).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
     Text("${j.progress.toInt()}%",color=Color.Gray)
     j.error?.let{Text(it,color=Color(0xFFFF8080),fontSize=12.sp)}
     if(j.status=="queued"||j.status=="downloading")TextButton(onClick={scope.launch{cancel(j.id)}}){Text("CANCEL")}
    }
   }
  }
  Spacer(Modifier.weight(1f))
  Button(onClick={
   if(!online){start();message="Menjalankan server Termux..."}
   else if(url.isBlank())message="Masukkan URL terlebih dahulu"
   else scope.launch{busy=true;job=download(url,quality);busy=false}
  },enabled=!busy,modifier=Modifier.fillMaxWidth().height(60.dp),shape=RoundedCornerShape(28.dp),
   colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Color.Black)){
   Icon(Icons.Default.Download,null);Spacer(Modifier.width(8.dp));Text(if(busy)"PROCESSING..." else "DOWNLOAD",fontWeight=FontWeight.Black)
  }
 }
}

@Composable fun History(mod:Modifier){
 var jobs by remember{mutableStateOf(emptyList<Job>())}
 LaunchedEffect(Unit){while(true){jobs=jobs();delay(3000)}}
 Column(mod.fillMaxSize().background(Color.Black).padding(24.dp)){Text("History",fontSize=32.sp,fontWeight=FontWeight.Black);Text("Download activity",color=Color.Gray);Spacer(Modifier.height(18.dp))
  LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(jobs){j->Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF111111)),shape=RoundedCornerShape(16.dp)){
   Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(j.status=="completed")Icons.Default.CheckCircle else Icons.Default.Download,null);Spacer(Modifier.width(10.dp));Column{Text(j.id,fontWeight=FontWeight.Bold);Text("${j.status} • ${j.progress.toInt()}%",color=Color.Gray,fontSize=12.sp)}}}}}
  }
 }
}
@Composable fun Settings(mod:Modifier){
 Column(mod.fillMaxSize().background(Color.Black).padding(24.dp)){Text("Settings",fontSize=32.sp,fontWeight=FontWeight.Black);Text("ALF Downloader 1.1.0",color=Color.Gray);Spacer(Modifier.height(24.dp))
  Setting(Icons.Default.Terminal,"Termux Server","127.0.0.1:8080");Setting(Icons.Default.Download,"Folder","Download/ALF Downloader");Setting(Icons.Default.Info,"Backend","Flask + yt-dlp + FFmpeg")
 }
}
@Composable fun Setting(icon:ImageVector,title:String,value:String){Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null);Spacer(Modifier.width(14.dp));Column{Text(title,fontWeight=FontWeight.Bold);Text(value,color=Color.Gray,fontSize=13.sp)}}}

fun req(method:String,url:String,body:String?=null):String?=try{
 val b=Request.Builder().url(url);if(body!=null)b.method(method,body.toRequestBody("application/json".toMediaType()))else b.method(method,null)
 client.newCall(b.build()).execute().use{if(it.isSuccessful)it.body?.string()else null}
}catch(_:Exception){null}
fun health()=try{JSONObject(req("GET","$BASE/api/health")?:"{}").optBoolean("ok")}catch(_:Exception){false}
fun apiInfo(u:String):String{
 val raw=req("GET","$BASE/api/info?url="+URLEncoder.encode(u,"UTF-8"))?:return "Server tidak terhubung"
 return try{val o=JSONObject(raw);if(o.optBoolean("ok"))"${o.optString("title")}\n${o.optString("uploader")}" else o.optString("error")}catch(_:Exception){"Respons server tidak valid"}
}
fun parse(o:JSONObject)=Job(o.optString("id"),o.optString("status"),o.optDouble("progress"),o.optString("message").takeIf{it.isNotBlank()},o.optString("error").takeIf{it.isNotBlank()&&it!="null"})
fun download(u:String,q:String):Job?=try{val raw=req("POST","$BASE/api/download",JSONObject().put("url",u).put("quality",q).toString())?:return null;parse(JSONObject(raw).getJSONObject("job"))}catch(_:Exception){null}
fun apiJob(id:String):Job?=try{parse(JSONObject(req("GET","$BASE/api/jobs/$id")?:"{}").getJSONObject("job"))}catch(_:Exception){null}
fun cancel(id:String){req("POST","$BASE/api/jobs/$id/cancel")}
fun jobs():List<Job>=try{val a=JSONObject(req("GET","$BASE/api/jobs")?:"{}").getJSONArray("jobs");List(a.length()){parse(a.getJSONObject(it))}}catch(_:Exception){emptyList()}
