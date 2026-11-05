package cn.cardlab;

import android.os.*;
import android.nfc.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

public class TagActionActivity extends AppCompatActivity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            if(!NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction()))throw new IllegalArgumentException("请通过 NFC 卡片触发任务");
            Parcelable[] messages=getIntent().getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if(messages==null||messages.length==0||!(messages[0] instanceof NdefMessage))throw new IllegalArgumentException("没有卡片任务");
            NdefRecord record=((NdefMessage)messages[0]).getRecords()[0];
            if(!TagTasks.MIME.equals(record.toMimeType())||record.getPayload().length>4096)throw new IllegalArgumentException("卡片任务格式不正确");
            JSONObject task=new JSONObject(new String(record.getPayload(),StandardCharsets.UTF_8));
            new MaterialAlertDialogBuilder(this).setTitle("执行 NFC 快捷任务？").setMessage(TagTasks.describe(task)).setNegativeButton("取消",(d,w)->finish()).setPositiveButton("打开",(d,w)->{
                try {TagTasks.launch(this,task);finish();}catch(Exception e){failure(e);}
            }).setOnCancelListener(d->finish()).show();
        }catch(Exception e){failure(e);}
    }
    private void failure(Exception e){new MaterialAlertDialogBuilder(this).setTitle("暂时无法打开").setMessage(e.getMessage()).setPositiveButton("关闭",(d,w)->finish()).setOnCancelListener(d->finish()).show();}
}
