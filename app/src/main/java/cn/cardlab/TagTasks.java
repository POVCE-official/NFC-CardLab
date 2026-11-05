package cn.cardlab;

import android.content.*;
import android.nfc.*;
import android.provider.Settings;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

final class TagTasks {
    static final String MIME="application/vnd.cn.cardlab.task";
    static final String[] SETTING_NAMES={"Wi-Fi 开关面板","蓝牙设置","声音与音量","显示与亮度","NFC 设置","定位开关设置","飞行模式设置"};
    static final String[] SETTING_IDS={"wifi","bluetooth","sound","display","nfc","location","airplane"};
    static NdefMessage message(String type,String value) throws Exception {
        switch(type) {
            case "text": if(value.trim().isEmpty())throw new IllegalArgumentException("请填写要写入的文字"); return new NdefMessage(NdefRecord.createTextRecord("zh",value));
            case "url": return new NdefMessage(NdefRecord.createUri(TaskRules.url(value,true)));
            case "deep": return new NdefMessage(NdefRecord.createUri(TaskRules.url(value,false)));
            case "app": value=TaskRules.packageName(value); break;
            case "settings": settingIntent(value); break;
            default: throw new IllegalArgumentException("未知快捷功能");
        }
        JSONObject task=new JSONObject().put("type",type).put("value",value);
        return new NdefMessage(new NdefRecord[]{NdefRecord.createMime(MIME,task.toString().getBytes(StandardCharsets.UTF_8)),NdefRecord.createApplicationRecord("cn.cardlab")});
    }
    static String describe(JSONObject task) throws Exception {
        String type=task.getString("type"),value=task.getString("value");
        if(type.equals("app"))return "打开 App："+TaskRules.packageName(value);
        if(type.equals("settings")){settingIntent(value);for(int i=0;i<SETTING_IDS.length;i++)if(SETTING_IDS[i].equals(value))return "打开"+SETTING_NAMES[i];}
        throw new IllegalArgumentException("无法识别的卡片任务");
    }
    static void launch(Context context,JSONObject task) throws Exception {
        describe(task);
        Intent intent;
        if(task.getString("type").equals("settings"))intent=settingIntent(task.getString("value"));
        else {intent=context.getPackageManager().getLaunchIntentForPackage(TaskRules.packageName(task.getString("value")));if(intent==null)throw new IllegalArgumentException("找不到该 App，请检查是否已安装");}
        context.startActivity(intent);
    }
    static Intent settingIntent(String id) {
        String action;
        switch(id){
            case "wifi": action=android.os.Build.VERSION.SDK_INT>=29?Settings.Panel.ACTION_WIFI:Settings.ACTION_WIFI_SETTINGS;break;
            case "bluetooth":action=Settings.ACTION_BLUETOOTH_SETTINGS;break;
            case "sound":action=Settings.ACTION_SOUND_SETTINGS;break;
            case "display":action=Settings.ACTION_DISPLAY_SETTINGS;break;
            case "nfc":action=Settings.ACTION_NFC_SETTINGS;break;
            case "location":action=Settings.ACTION_LOCATION_SOURCE_SETTINGS;break;
            case "airplane":action=Settings.ACTION_AIRPLANE_MODE_SETTINGS;break;
            default:throw new IllegalArgumentException("不支持此系统功能");
        }
        return new Intent(action);
    }
}
