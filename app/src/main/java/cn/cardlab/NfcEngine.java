package cn.cardlab;

import android.nfc.*;
import android.nfc.tech.*;
import org.json.*;
import java.util.*;

final class NfcEngine {
    static JSONObject read(Tag tag, List<byte[]> keys) throws Exception {
        return read(tag,keys,true);
    }
    static JSONObject read(Tag tag, List<byte[]> keys, boolean detailed) throws Exception {
        JSONObject o=new JSONObject();
        o.put("uid",Bytes.hex(tag.getId())); o.put("time",System.currentTimeMillis());
        o.put("technologies",new JSONArray(Arrays.asList(tag.getTechList())));
        o.put("model","未知型号（仅确认技术类型）");
        JSONArray records=new JSONArray(), blocks=new JSONArray(); o.put("records",records); o.put("blocks",blocks);
        NfcA a=NfcA.get(tag);
        if(a!=null) { o.put("atqa",Bytes.hex(a.getAtqa())); o.put("sak",a.getSak()); }
        Ndef n=Ndef.get(tag);
        if(n!=null) {
            n.connect();
            try {
                o.put("ndefType",n.getType()); o.put("capacity",n.getMaxSize()); o.put("writable",n.isWritable());
                NdefMessage m=n.getNdefMessage();
                if(m!=null) for(NdefRecord r:m.getRecords()) {
                    JSONObject rec=new JSONObject().put("tnf",r.getTnf()).put("type",Bytes.hex(r.getType())).put("id",Bytes.hex(r.getId())).put("payload",Bytes.hex(r.getPayload()));
                    try {
                        if(r.getTnf()==NdefRecord.TNF_WELL_KNOWN && Arrays.equals(r.getType(),NdefRecord.RTD_TEXT)) rec.put("content",Bytes.ndefText(r.getPayload()));
                        else if(r.toUri()!=null) rec.put("content",r.toUri().toString());
                        else rec.put("content",Bytes.text(r.getPayload()));
                    } catch(Exception e) { rec.put("content","无法解码："+e.getMessage()); }
                    records.put(rec);
                }
            } finally { n.close(); }
        }
        MifareClassic m=MifareClassic.get(tag);
        if(m!=null) {
            o.put("model","MIFARE Classic 兼容 / "+m.getSize()+" B（具体厂商未确认）");
            o.put("classicSize",m.getSize());
            if(!detailed){o.put("note","快速读取已完成；在设置开启完整扇区读取可查看更多数据");return o;}
            m.connect();
            try {
                for(int sector=0;sector<m.getSectorCount();sector++) {
                    boolean ok=authenticate(m,sector,keys);
                    int base=m.sectorToBlock(sector), count=m.getBlockCountInSector(sector);
                    for(int i=0;i<count;i++) {
                        JSONObject b=new JSONObject().put("sector",sector).put("block",base+i);
                        if(ok) try { b.put("hex",Bytes.hex(m.readBlock(base+i))).put("status","已读取"); }
                        catch(java.io.IOException e) { b.put("status","读取失败"); }
                        else b.put("status","需要认证");
                        if(i==count-1) b.put("note","扇区尾块：密钥字段可能被芯片隐藏，不代表真实密钥");
                        blocks.put(b);
                    }
                }
            } finally { m.close(); }
        } else if(MifareUltralight.get(tag)!=null) {
            o.put("model","Ultralight / NTAG 兼容系列（未确认具体芯片）");
            MifareUltralight u=MifareUltralight.get(tag); u.connect();
            try { blocks.put(new JSONObject().put("page",0).put("hex",Bytes.hex(u.readPages(0))).put("status","已读取前 4 页；其余容量未探测")); }
            finally { u.close(); }
        } else if(IsoDep.get(tag)!=null) {
            o.put("model","ISO-DEP CPU 卡（具体型号需协议适配）");
            IsoDep d=IsoDep.get(tag);
            if(d.getHistoricalBytes()!=null) o.put("historicalBytes",Bytes.hex(d.getHistoricalBytes()));
            if(d.getHiLayerResponse()!=null) o.put("hiLayerResponse",Bytes.hex(d.getHiLayerResponse()));
            o.put("note","文件内容需通过对应 COS / APDU 配置读取");
        }
        return o;
    }
    static boolean authenticate(MifareClassic m,int sector,List<byte[]> keys) throws Exception {
        for(byte[] k:keys) if(m.authenticateSectorWithKeyA(sector,k)||m.authenticateSectorWithKeyB(sector,k)) return true;
        return false;
    }
    static void writeText(Tag t,String text) throws Exception {
        writeNdef(t,new NdefMessage(NdefRecord.createTextRecord("zh",text)));
    }
    static String writeTask(Tag tag,NdefMessage message,boolean format) throws Exception {
        if(Ndef.get(tag)!=null){writeNdef(tag,message);return "写入成功，已回读核对。移开卡片后可重新贴卡查看。";}
        NdefFormatable f=NdefFormatable.get(tag);
        if(!format||f==null)throw new IllegalArgumentException("这张卡尚未支持 NDEF 写入。可在设置允许初始化兼容空白标签；CPU / 加密卡需专用协议。");
        f.connect();try{f.format(message);}finally{f.close();}
        return "标签初始化与写入指令已完成。请移开卡片后重新读取，确认内容。";
    }
    static String clearClassic(Tag tag,List<byte[]> keys) throws Exception {
        MifareClassic m=MifareClassic.get(tag);if(m==null)throw new IllegalArgumentException("目标不是手机支持的 Classic 卡");
        int count=0;
        for(int block=1;block<m.getBlockCount();block++){
            int sector=m.blockToSector(block);
            if(block==m.sectorToBlock(sector)+m.getBlockCountInSector(sector)-1)continue;
            try{writeBlock(tag,block,new byte[16],keys,m.getSize());count++;}
            catch(Exception e){throw new IllegalStateException("已清空 "+count+" 个数据块，停止于块 "+block+"："+e.getMessage());}
        }
        return "已清空并核对 "+count+" 个普通数据块。UID、密钥与权限尾块保留。";
    }
    static void writeNdef(Tag t,NdefMessage msg) throws Exception {
        Ndef n=Ndef.get(t); if(n==null) throw new IllegalArgumentException("目标不是已格式化 NDEF 标签");
        n.connect();
        try {
            if(!n.isWritable()) throw new IllegalArgumentException("标签只读");
            if(msg.toByteArray().length>n.getMaxSize()) throw new IllegalArgumentException("内容超过容量");
            n.writeNdefMessage(msg);
            if(!Arrays.equals(msg.toByteArray(),n.getNdefMessage().toByteArray())) throw new IllegalStateException("回读不一致");
        } finally { n.close(); }
    }
    static String restore(Tag t,JSONObject source,List<byte[]> keys) throws Exception {
        if(source.has("classicSize")) {
            JSONArray blocks=source.getJSONArray("blocks"); int written=0,skipped=0;
            MifareClassic target=MifareClassic.get(t);
            if(target==null||target.getSize()!=source.getInt("classicSize")) throw new IllegalArgumentException("目标卡型或容量不匹配");
            // Validate the entire imported snapshot before the first write.
            for(int i=0;i<blocks.length();i++) {
                JSONObject b=blocks.getJSONObject(i);
                if(!b.has("hex"))continue;
                int index=b.getInt("block");
                if(index<0||index>=target.getBlockCount()||Bytes.hex(b.getString("hex")).length!=16) throw new IllegalArgumentException("快照块数据无效");
            }
            for(int i=0;i<blocks.length();i++) {
                JSONObject b=blocks.getJSONObject(i);int index=b.optInt("block",-1);
                if(index<=0||!b.has("hex")){skipped++;continue;}
                int sector=target.blockToSector(index);
                if(index==target.sectorToBlock(sector)+target.getBlockCountInSector(sector)-1){skipped++;continue;}
                try { writeBlock(t,index,Bytes.hex(b.getString("hex")),keys,source.getInt("classicSize")); written++; }
                catch(Exception e){throw new IllegalStateException("已写入并校验 "+written+" 块，停止于块 "+index+"："+e.getMessage()+"。无自动回滚",e);}
            }
            return "数据恢复完成：写入并校验 "+written+" 块，跳过 "+skipped+" 块（含 UID、尾块与未读块）。不等同完整卡片复制。";
        }
        JSONArray records=source.optJSONArray("records");
        if(records==null||records.length()==0)throw new IllegalArgumentException("快照没有可恢复的 NDEF 或 Classic 数据");
        NdefRecord[] out=new NdefRecord[records.length()];
        for(int i=0;i<out.length;i++){JSONObject r=records.getJSONObject(i);out[i]=new NdefRecord((short)r.getInt("tnf"),Bytes.hex(r.getString("type")),Bytes.hex(r.optString("id")),Bytes.hex(r.getString("payload")));}
        writeNdef(t,new NdefMessage(out));
        return "NDEF 全部记录已恢复并回读验证";
    }
    static void writeBlock(Tag t,int block,byte[] data,List<byte[]> keys,int expectedSize) throws Exception {
        MifareClassic m=MifareClassic.get(t); if(m==null) throw new IllegalArgumentException("手机或目标不支持 Classic");
        if(expectedSize>0 && m.getSize()!=expectedSize) throw new IllegalArgumentException("源卡和目标容量不同");
        if(block<=0 || block>=m.getBlockCount() || data.length!=16) throw new IllegalArgumentException("块号或数据长度无效");
        int s=m.blockToSector(block);
        if(block==m.sectorToBlock(s)+m.getBlockCountInSector(s)-1) throw new IllegalArgumentException("此版本不写密钥与访问控制尾块");
        m.connect();
        try {
            if(!authenticate(m,s,keys)) throw new IllegalArgumentException("目标扇区认证失败");
            m.writeBlock(block,data);
            if(!Arrays.equals(data,m.readBlock(block))) throw new IllegalStateException("回读不一致");
        } finally { m.close(); }
    }
    static String script(Tag t,String script) throws Exception {
        List<byte[]> commands=new ArrayList<>(); List<String> expected=new ArrayList<>();
        for(String line:script.split("\\r?\\n")) {
            String s=line.trim(); if(s.isEmpty()||s.startsWith("#")) continue;
            String[] parts=s.split("=>",-1);
            if(parts.length>2) throw new IllegalArgumentException("格式：APDU => 预期状态字");
            commands.add(Bytes.apdu(parts[0]));
            String sw=parts.length==2 ? parts[1].trim().toUpperCase(Locale.ROOT):"9000";
            if(Bytes.hex(sw).length!=2) throw new IllegalArgumentException("状态字需要 2 字节");
            expected.add(sw);
        }
        if(commands.isEmpty()||commands.size()>256) throw new IllegalArgumentException("脚本需包含 1–256 条指令");
        IsoDep d=IsoDep.get(t); if(d==null) throw new IllegalArgumentException("目标不支持 ISO-DEP");
        StringBuilder log=new StringBuilder(); d.connect(); d.setTimeout(5000);
        try {
            for(int i=0;i<commands.size();i++) {
                byte[] c=commands.get(i);
                if(c.length>d.getMaxTransceiveLength()) throw new IllegalArgumentException("指令超过手机传输上限");
                String r=Bytes.hex(d.transceive(c));
                log.append(i+1).append("  → ").append(Bytes.hex(c)).append("\n← ").append(r).append("\n");
                if(r.length()<4||!r.endsWith(expected.get(i))) { log.append("停止：状态字不匹配；之前的指令可能已生效"); break; }
            }
        } finally { d.close(); }
        return log.toString();
    }
}
