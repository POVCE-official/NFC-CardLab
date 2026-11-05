package cn.cardlab;

import android.app.*;
import android.os.*;
import android.content.*;
import android.nfc.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

public class MainActivity extends AppCompatActivity {
    private LinearLayout body;
    private TextView status;
    private BottomNavigationView navigation;
    private SharedPreferences preferences;
    private String pendingTitle="";
    private int openDialogs;
    private NfcAdapter adapter;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean busy=new java.util.concurrent.atomic.AtomicBoolean();
    private JSONArray cards=new JSONArray();
    private JSONObject current;
    private Vault vault;
    private EditText keyInput, textInput, scriptInput;
    private String keys="FFFFFFFFFFFF", text="", script="";
    private String pending="read", payload="";
    private int blockNumber, expectedSize;
    private int page=1;
    private String log="等待操作。所有写入都需要先确认，再贴目标卡。";
    private boolean vaultReady=true;
    private static final int IMPORT=10, EXPORT=11, SCRIPT=12;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences=getSharedPreferences("options",MODE_PRIVATE);
        vault=new Vault(this);
        try { cards=vault.read(); } catch(Exception e) { vaultReady=false; log="卡包无法解锁："+e.getMessage()+"。请保留应用数据，避免覆盖。"; }
        adapter=NfcAdapter.getDefaultAdapter(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(0,dp(28),0,0);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        root.setBackgroundColor(0xFFF7F9F2);
        TextView title=label("卡研  /  CardLab",26); title.setPadding(dp(24),dp(16),dp(24),dp(4)); root.addView(title);
        status=label("",13); status.setPadding(dp(24),0,dp(24),dp(12)); root.addView(status);
        ScrollView scroll=new ScrollView(this); body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(24),dp(8),dp(24),dp(24)); scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        BottomNavigationView nav=new BottomNavigationView(this); navigation=nav;
        nav.getMenu().add(0,R.id.nav_home,0,"首页").setIcon(android.R.drawable.ic_menu_search);
        nav.getMenu().add(0,R.id.nav_wallet,1,"卡包").setIcon(android.R.drawable.ic_menu_agenda);
        nav.getMenu().add(0,R.id.nav_advanced,2,"高级").setIcon(android.R.drawable.ic_menu_manage);
        nav.getMenu().add(0,R.id.nav_settings,3,"设置").setIcon(android.R.drawable.ic_menu_preferences);
        nav.setOnItemSelectedListener(item->{ capture(); pending="read"; int id=item.getItemId();page=id==R.id.nav_home?1:id==R.id.nav_wallet?2:id==R.id.nav_advanced?3:4; render(); return true; }); root.addView(nav);
        setContentView(root); render();
    }
    @Override protected void onResume() {
        super.onResume();
        if(adapter!=null) adapter.enableReaderMode(this,this::onTag,NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_NFC_B|NfcAdapter.FLAG_READER_NFC_F|NfcAdapter.FLAG_READER_NFC_V,null);
        render();
    }
    @Override protected void onPause() {
        if(adapter!=null) adapter.disableReaderMode(this);
        pending="read"; super.onPause();
    }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
    private void capture() {
        if(keyInput!=null) keys=keyInput.getText().toString();
        if(textInput!=null) text=textInput.getText().toString();
        if(scriptInput!=null) script=scriptInput.getText().toString();
    }
    private void updateStatus() {
        if(status!=null) status.setText(adapter==null ? "此设备没有 NFC" : !adapter.isEnabled()?"NFC 未开启 · 请在系统设置打开":"NFC 已就绪 · "+(pending.equals("read")?"贴卡读取":"等待目标卡执行写入"));
    }
    private TextView label(String s,int size) { TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(0xFF243224); v.setPadding(0,dp(6),0,dp(6)); v.setTextIsSelectable(true); return v; }
    private void heading(String title,String sub) { body.addView(label(title,30)); body.addView(label(sub,14)); }
    private void info(String title,String content) {
        MaterialCardView c=new MaterialCardView(this); c.setRadius(dp(22)); c.setCardBackgroundColor(0xFFEAF0E4); c.setCardElevation(0);
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(18),dp(12),dp(18),dp(12)); l.addView(label(title,18)); l.addView(label(content,14)); c.addView(l);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(12),0,dp(8)); body.addView(c,p);
    }
    private void button(String title,Runnable action) { MaterialButton b=new MaterialButton(this); b.setText(title); b.setCornerRadius(dp(24)); b.setOnClickListener(v->action.run()); body.addView(b,new LinearLayout.LayoutParams(-1,-2)); }
    private EditText input(String hint,String value,boolean multiline) {
        EditText e=new EditText(this); e.setHint(hint); e.setText(value); e.setTextSize(14); e.setSingleLine(!multiline);
        e.setInputType(InputType.TYPE_CLASS_TEXT|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0)); body.addView(e,new LinearLayout.LayoutParams(-1,-2)); return e;
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private void render() {
        body.removeAllViews(); keyInput=null; textInput=null; scriptInput=null; updateStatus();
        if(busy.get()) {heading("正在处理卡片", "请保持卡片贴近手机，完成前不要移动。");body.addView(new ProgressBar(this));return;}
        if(!pending.equals("read")) {
            heading("现在，请贴卡",pendingTitle);
            info("等待卡片", "先移开卡片，再放到手机背面 NFC 感应区，保持不动。\n完成后会显示结果，同一次准备只执行一张卡。");
            button("取消，返回首页",()->{pending="read";render();});return;
        }
        if(page==1) {
            heading("NFC，轻松一点", "选好功能，再把卡片贴近手机。");
            shortcutRow("读卡","看看卡里有什么",this::readGuide,"写文字","保存文案或备注",()->writeWizard("text"));
            shortcutRow("写网址","碰一下打开网站",()->writeWizard("url"),"打开 App","选择手机里的应用",this::chooseApp);
            shortcutRow("App 页面","写入页面专属链接",()->writeWizard("deep"),"系统功能","Wi-Fi、蓝牙等设置",this::chooseSystem);
            button("清空卡片内容",this::clearDialog);
            if(current!=null) {
                info("最近读到的卡",current.optString("model")+"\nUID  "+current.optString("uid")+(current.has("capacity")?"\n可写容量："+current.optInt("capacity")+" 字节 · "+(current.optBoolean("writable")?"可写":"只读"):"")+"\n"+current.optString("note"));
                JSONArray r=current.optJSONArray("records");
                if(r!=null) for(int i=0;i<r.length();i++) {
                    JSONObject record=r.optJSONObject(i);if(record==null)continue;
                    if(record.optString("type").equals(Bytes.hex(TagTasks.MIME.getBytes(StandardCharsets.US_ASCII)))) {
                        try {JSONObject task=new JSONObject(Bytes.text(Bytes.hex(record.getString("payload"))));info("卡片快捷任务",TagTasks.describe(task));button("试用此任务",()->confirmTask(task));}catch(Exception e){info("卡片任务","格式无法识别");}
                    }else {info("卡内内容 "+(i+1),record.optString("content"));String content=record.optString("content");try{String link=TaskRules.url(content,false);button("打开此链接",()->confirm("打开卡片链接",link,()->openLink(link)));}catch(Exception ignored){}}
                }
                button("查看原始数据与读取状态",()->show("卡片数据",current.toString()));
                button("保存到加密卡包",this::saveCurrent);
                button("将此快照写入兼容白卡",()->arm("restore",current.toString(),"将覆盖目标卡数据。Classic 仅恢复已读取的普通数据块，跳过块 0 和所有尾块；NDEF 覆盖全部记录。CPU 文件需要使用适配的 APDU。\n源 UID："+current.optString("uid")));
                button("导出当前快照 JSON",()->document(EXPORT,"application/json","cardlab.json"));
            }
            info("操作状态",log);
        } else if(page==2) {
            heading("我的卡包",cards.length()+" 张本地快照 · Android Keystore 加密保存");
            button("导入 CardLab JSON 快照",()->document(IMPORT,"application/json",null));
            for(int i=0;i<cards.length();i++) {
                final int index=i; JSONObject c=cards.optJSONObject(i);
                info(c.optString("name","未命名卡片"),c.optString("model")+"\nUID  "+c.optString("uid"));
                button("打开 · "+c.optString("name","未命名"),()->{current=c; page=1; render();});
                button("删除此快照",()->confirm("删除快照", "仅删除本地保存的数据。",()->{try {JSONArray next=new JSONArray(); for(int j=0;j<cards.length();j++) if(j!=index) next.put(cards.get(j)); vault.write(next); cards=next; render();} catch(Exception e){error(e);} }));
            }
        } else if(page==3) {
            heading("读写工作台","写入前检查数据，确认后再贴目标卡。");
            keyInput=input("Classic 已知密钥，每行一个 6 字节 HEX",keys,true);
            textInput=input("写入 NDEF 的文字",text,true);
            button("准备写入 NDEF 文字",()->{capture(); if(text.isEmpty()){toast("请先填写文字");return;} arm("ndef",text,"将覆盖目标卡当前的全部 NDEF 记录。写后回读验证。");});
            button("写入一个 Classic 数据块",this::blockDialog);
            scriptInput=input("APDU，每行 HEX => 预期 SW（默认 9000）",script,true);
            button("导入 UTF-8 APDU 文本",()->document(SCRIPT,"text/*",null));
            button("执行 CPU 卡 APDU",()->{capture(); if(script.trim().isEmpty()){toast("请先填写指令");return;} arm("apdu",script,"将按顺序执行以下指令，可能修改卡片文件或密钥。遇到不匹配状态字即停止；不会回滚。\n\n"+script);});
            button("使用已知密钥解密数据",this::decryptDialog);
            button("查看执行日志",()->show("日志",log));
            info("兼容性", "Classic 依赖手机芯片支持；CPU 卡文件和认证需厂商协议。静态 APDU 支持导入执行，动态认证未通用适配。未知密钥恢复与手机模拟卡片尚未实现。");
        } else renderSettings();
    }
    private void onTag(Tag tag) {
        runOnUiThread(()->{
            if(openDialogs>0||!busy.compareAndSet(false,true)) return;
            capture(); String operation=pending, data=payload, keyText=keys; int block=blockNumber, size=expectedSize; pending="read"; render();
            worker.execute(()->{
                try {
                    List<byte[]> list=new ArrayList<>();
                    if(((operation.equals("read")||operation.equals("scan"))&&preferences.getBoolean("detailed",false))||operation.equals("block")||operation.equals("restore")||operation.equals("clearClassic")) for(String s:keyText.split("\\s+")) if(!s.isEmpty()) {byte[] k=Bytes.hex(s); if(k.length!=6) throw new IllegalArgumentException("Classic 密钥应为 6 字节"); list.add(k);}
                    JSONObject read=null; String result;
                    switch(operation) {
                        case "ndef": NfcEngine.writeText(tag,data); result="NDEF 写入并回读成功"; break;
                        case "block": NfcEngine.writeBlock(tag,block,Bytes.hex(data),list,size); result="块 "+block+" 写入并回读成功"; break;
                        case "apdu": result=NfcEngine.script(tag,data); break;
                        case "restore": result=NfcEngine.restore(tag,new JSONObject(data),list); break;
                        case "task": JSONObject task=new JSONObject(data); result=NfcEngine.writeTask(tag,TagTasks.message(task.getString("type"),task.getString("value")),preferences.getBoolean("format",false));break;
                        case "clear": result=NfcEngine.writeTask(tag,new NdefMessage(new NdefRecord(NdefRecord.TNF_EMPTY,new byte[0],new byte[0],new byte[0])),false);break;
                        case "clearClassic":result=NfcEngine.clearClassic(tag,list);break;
                        default: read=NfcEngine.read(tag,list,preferences.getBoolean("detailed",false)); result="读卡完成。下方可查看内容或保存到卡包。";
                    }
                    JSONObject snapshot=read; runOnUiThread(()->{if(isFinishing()||isDestroyed())return;if(snapshot!=null){current=snapshot;page=1;navigation.setSelectedItemId(R.id.nav_home);}else current=null; log=result; busy.set(false); render(); feedback(); if(!operation.equals("read")) show(operation.equals("clear")?"内容已清空":"操作完成",result);});
                } catch(Exception e) {runOnUiThread(()->{busy.set(false);if(isFinishing()||isDestroyed())return;log="操作未完成："+e.getMessage()+"\n如已执行部分写入，请重新读取确认。"; render(); show("操作结果",log);});}
            });
        });
    }
    private void arm(String operation,String data,String message) { if(adapter==null||!adapter.isEnabled()){toast("请先开启手机 NFC");return;} if(busy.get()){toast("请等待当前操作完成");return;} confirm("01 / 02  确认操作",message,()->{pending=operation; payload=data; pendingTitle=message; render();}); }
    private void track(androidx.appcompat.app.AlertDialog dialog){openDialogs++;dialog.setOnDismissListener(d->openDialogs=Math.max(0,openDialogs-1));}
    private void confirm(String t,String m,Runnable yes) {androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle(t).setMessage(m).setNegativeButton("取消",null).setPositiveButton("确认",(d,w)->yes.run()).create();track(dialog);dialog.show();}
    private void show(String t,String m) {TextView v=label(m,13); v.setPadding(dp(20),dp(12),dp(20),dp(12)); v.setTypeface(android.graphics.Typeface.MONOSPACE); ScrollView s=new ScrollView(this);s.addView(v);androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle(t).setView(s).setPositiveButton("关闭",null).create();track(dialog);dialog.show();}
    private void toast(String s) {Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void error(Exception e) {show("无法完成",String.valueOf(e.getMessage()));}
    private void saveCurrent() {
        if(!vaultReady){toast("卡包未解锁，禁止覆盖");return;}
        EditText name=new EditText(this); name.setHint("卡片名称");
        new MaterialAlertDialogBuilder(this).setTitle("保存快照").setView(name).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{
            try {JSONObject c=new JSONObject(current.toString());c.put("name",name.getText().toString().trim().isEmpty()?"未命名卡片":name.getText().toString());JSONArray next=new JSONArray(cards.toString());next.put(c);vault.write(next);cards=next;toast("已保存");}catch(Exception e){error(e);}
        }).show();
    }
    private void blockDialog() {
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(20),0,dp(20),0);
        EditText number=new EditText(this);number.setHint("目标块号（非 0、非扇区尾块）");number.setInputType(InputType.TYPE_CLASS_NUMBER);l.addView(number);
        EditText bytes=new EditText(this);bytes.setHint("16 字节 HEX，可从卡片原始视图复制");l.addView(bytes);
        new MaterialAlertDialogBuilder(this).setTitle("Classic 数据块写入").setView(l).setNegativeButton("取消",null).setPositiveButton("检查",(d,w)->{
            try {capture(); int b=Integer.parseInt(number.getText().toString());String h=bytes.getText().toString();if(Bytes.hex(h).length!=16||b<=0)throw new IllegalArgumentException("请输入正确块号与 16 字节数据");blockNumber=b;expectedSize=current==null?0:current.optInt("classicSize",0);arm("block",h,"覆盖块 "+b+"\n"+h+"\n目标卡使用扫描页填写的密钥认证。");}catch(Exception e){error(e);}
        }).show();
    }
    private void decryptDialog() {
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(20),0,dp(20),0);
        Spinner mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"AES/CBC/PKCS5Padding","DESede/CBC/PKCS5Padding","DES/CBC/PKCS5Padding"}));l.addView(mode);
        EditText k=new EditText(this);k.setHint("密钥 HEX（仅在当前内存使用）");k.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);l.addView(k);
        EditText iv=new EditText(this);iv.setHint("IV HEX");l.addView(iv);
        EditText data=new EditText(this);data.setHint("密文 HEX");l.addView(data);
        new MaterialAlertDialogBuilder(this).setTitle("已知密钥解密").setView(l).setNegativeButton("取消",null).setPositiveButton("解密",(d,w)->{
            byte[] key=null;
            try {String transformation=mode.getSelectedItem().toString();key=Bytes.hex(k.getText().toString());Cipher c=Cipher.getInstance(transformation);c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,transformation.split("/")[0]),new IvParameterSpec(Bytes.hex(iv.getText().toString())));byte[] plain=c.doFinal(Bytes.hex(data.getText().toString()));show("解密结果",Bytes.hex(plain)+"\n\n"+Bytes.text(plain));}catch(Exception e){error(e);}finally{if(key!=null)Arrays.fill(key,(byte)0);k.setText("");}
        }).show();
    }
    private void document(int code,String mime,String name) {
        if(code==EXPORT) {if(current==null)return;confirm("导出未加密快照","JSON 含卡片原始数据，请保存到可信位置。",()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,code);});}
        else startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE),code);
    }
    private void shortcutRow(String title,String subtitle,Runnable action,String title2,String subtitle2,Runnable action2) {
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        quickTile(row,title,subtitle,action);quickTile(row,title2,subtitle2,action2);body.addView(row);
    }
    private void quickTile(LinearLayout row,String title,String subtitle,Runnable action) {
        MaterialButton b=new MaterialButton(this);b.setText(getString(cn.cardlab.R.string.shortcut_label,title,subtitle));b.setTextSize(16);b.setAllCaps(false);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setCornerRadius(dp(24));b.setMinHeight(dp(108));b.setPadding(dp(16),dp(12),dp(8),dp(12));
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE3EBD9));b.setTextColor(0xFF243224);b.setContentDescription(title+"，"+subtitle);b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(b,p);
    }
    private void readGuide(){if(adapter==null||!adapter.isEnabled()){toast("请在系统设置开启 NFC");return;}pending="scan";pendingTitle="读卡：自动显示卡片类型、文字和网址。无需填写参数。";render();}
    private void writeWizard(String type) {
        String title,hint,help;
        switch(type){
            case "text":title="写文字";hint="在这里填写文案、备注或联系方式";help="文字直接保存在卡内，其他 NFC 读卡软件也可读取。";break;
            case "url":title="写网址";hint="例如 https://example.com";help="写入标准网址标签，碰卡后由手机打开网站。";break;
            case "deep":title="跳转 App 页面";hint="粘贴 App 提供的页面链接，如 myapp://page";help="需 App 支持该深层链接或 App Link；没有通用方法定位任意 App 页面。";break;
            default:title="指定 App 包名";hint="例如 com.example.app";help="也可从手机已安装应用中选择。读取此任务的手机需要安装 CardLab 和目标 App。";
        }
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(24),0,dp(24),0);form.addView(label(help,14));
        EditText value=new EditText(this);value.setHint(hint);value.setTextSize(16);value.setMinLines(type.equals("text")?3:1);value.setInputType(InputType.TYPE_CLASS_TEXT|(type.equals("text")?InputType.TYPE_TEXT_FLAG_MULTI_LINE:InputType.TYPE_TEXT_VARIATION_URI));form.addView(value);
        androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle(title).setView(form).setNegativeButton("取消",null).setPositiveButton("下一步",null).create();
        track(dialog);dialog.setOnShowListener(d->dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {String raw=value.getText().toString();TagTasks.message(type,raw);dialog.dismiss();prepareTask(type,raw,title);}catch(Exception e){value.setError(e.getMessage());}
        }));dialog.show();
    }
    private void prepareTask(String type,String value,String name) {
        try {
            NdefMessage msg=TagTasks.message(type,value);JSONObject task=new JSONObject().put("type",type).put("value",value);
            arm("task",task.toString(),name+"\n\n"+value+"\n\n将覆盖卡片原来的 NDEF 内容，需要 "+msg.toByteArray().length+" 字节。"+(preferences.getBoolean("format",false)?"\n已允许初始化兼容空白标签。":"")+(type.equals("app")||type.equals("settings")?"\n碰卡执行此任务的手机需安装 CardLab。":""));
        }catch(Exception e){error(e);}
    }
    private void chooseApp() {
        java.util.TreeMap<String,String> entries=new java.util.TreeMap<>();
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for(android.content.pm.ResolveInfo r:getPackageManager().queryIntentActivities(query,0)) entries.put(r.loadLabel(getPackageManager()).toString()+"\n"+r.activityInfo.packageName,r.activityInfo.packageName);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);
        EditText search=new EditText(this);search.setHint("搜索应用名或包名");box.addView(search);
        ListView list=new ListView(this);ArrayAdapter<String> options=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>(entries.keySet()));list.setAdapter(options);box.addView(list,new LinearLayout.LayoutParams(-1,dp(320)));
        androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle("选择要打开的 App").setView(box).setNegativeButton("取消",null).setNeutralButton("手动输入包名",(d,w)->writeWizard("app")).create();
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){options.getFilter().filter(s);}public void afterTextChanged(android.text.Editable e){}});
        list.setOnItemClickListener((p,v,pos,id)->{String name=options.getItem(pos);dialog.dismiss();prepareTask("app",entries.get(name),"打开 App："+name.split("\n")[0]);});track(dialog);dialog.show();
    }
    private void chooseSystem() {
        new MaterialAlertDialogBuilder(this).setTitle("选择系统功能").setItems(TagTasks.SETTING_NAMES,(d,index)->prepareTask("settings",TagTasks.SETTING_IDS[index],TagTasks.SETTING_NAMES[index]+"\n碰卡后打开系统设置面板，由你操作开关。")).setNegativeButton("取消",null).show();
    }
    private void clearDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("清空哪类内容？").setItems(new String[]{"普通 NFC 标签：清空文字、网址、快捷任务","Classic 卡：清空普通数据块（高级）"},(d,index)->{
            if(index==0)arm("clear","","清空这张 NFC 卡的文字、网址和快捷任务，保留可继续写入的空标签。\n这不是整片芯片的安全擦除，也不会解除卡片只读锁。建议先读卡保存备份。");
            else arm("clearClassic","","将使用高级页的已知密钥，把 Classic 卡的普通数据块写为 0。UID、密钥和权限尾块保留；失败时可能只清空一部分。请先备份。");
        }).setNegativeButton("取消",null).show();
    }
    private void renderSettings() {
        heading("按你的习惯使用","设置会自动保存在这台手机上。");
        setting("操作成功时振动","vibrate",true);
        setting("完整读取 Classic 扇区（更慢）","detailed",false);
        setting("允许初始化兼容空白 NFC 标签","format",false);
        info("初始化说明","仅对手机识别为 NdefFormatable 的标签生效，会改变标签内容；每次写入前仍需确认。已初始化标签会回读验证，首次初始化后需要重新贴卡检查。");
        button("打开手机 NFC 设置",()->{try{startActivity(new Intent(android.provider.Settings.ACTION_NFC_SETTINGS));}catch(Exception e){error(e);}});
        info("系统开关与跨手机使用","网址和文字使用标准 NDEF。App 与系统设置任务需要目标手机安装 CardLab。系统设置任务打开对应面板，不能保证静默切换开关。App 页面链接需目标应用支持。");
        info("版本","CardLab 1.0 · NFC 读写助手");
    }
    private void setting(String name,String key,boolean initial) {
        com.google.android.material.switchmaterial.SwitchMaterial s=new com.google.android.material.switchmaterial.SwitchMaterial(this);s.setText(name);s.setTextSize(16);s.setMinHeight(dp(64));s.setChecked(preferences.getBoolean(key,initial));s.setOnCheckedChangeListener((b,on)->preferences.edit().putBoolean(key,on).apply());body.addView(s);
    }
    private void feedback(){if(preferences.getBoolean("vibrate",true)){Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createOneShot(70,VibrationEffect.DEFAULT_AMPLITUDE));}}
    private void openLink(String link){try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(link)));}catch(Exception e){show("无法打开","手机没有可处理这个链接的 App。请检查链接或安装目标 App。");}}
    private void confirmTask(JSONObject task){try{confirm("执行卡片快捷任务",TagTasks.describe(task),()->{try{TagTasks.launch(this,task);}catch(Exception e){error(e);}});}catch(Exception e){error(e);}}
    @Override protected void onActivityResult(int req,int result,Intent intent) {
        super.onActivityResult(req,result,intent);if(result!=RESULT_OK||intent==null||intent.getData()==null)return;
        try {
            if(req==EXPORT) {try(java.io.OutputStream out=getContentResolver().openOutputStream(intent.getData())){out.write(current.toString(2).getBytes(StandardCharsets.UTF_8));}toast("已导出");return;}
            String content;
            try(java.io.InputStream in=getContentResolver().openInputStream(intent.getData());java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
                byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>2*1024*1024)throw new IllegalArgumentException("文件超过 2 MB");out.write(b,0,n);}content=out.toString("UTF-8");
            }
            if(req==SCRIPT){script=content;page=3;render();return;}
            if(!vaultReady)throw new IllegalStateException("卡包未解锁");
            JSONObject c=new JSONObject(content);if(!c.has("uid")||!c.has("technologies"))throw new IllegalArgumentException("不是 CardLab 快照");
            confirm("导入快照",c.optString("model")+"\nUID "+c.optString("uid"),()->{try{JSONArray next=new JSONArray(cards.toString());next.put(c);vault.write(next);cards=next;render();}catch(Exception e){error(e);}});
        }catch(Exception e){error(e);}
    }
}




