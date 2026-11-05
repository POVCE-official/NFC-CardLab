package cn.cardlab;

import java.nio.charset.StandardCharsets;

public final class Bytes {
    public static byte[] hex(String input) {
        String s = input.replaceAll("\\s", "");
        if (s.length() % 2 != 0 || !s.matches("[0-9a-fA-F]*")) throw new IllegalArgumentException("请输入完整的十六进制字节");
        byte[] out = new byte[s.length()/2];
        for (int i=0;i<out.length;i++) out[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);
        return out;
    }
    public static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for(byte v:b) s.append(String.format("%02X",v & 255));
        return s.toString();
    }
    public static String text(byte[] b) { return new String(b, StandardCharsets.UTF_8); }
    public static String ndefText(byte[] p) {
        if(p.length==0) throw new IllegalArgumentException("空文本记录");
        int offset = 1+(p[0]&63);
        if(offset>p.length) throw new IllegalArgumentException("文本语言字段越界");
        return new String(p,offset,p.length-offset,(p[0]&128)==0 ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16);
    }
    public static byte[] apdu(String s) {
        byte[] b=hex(s);
        if(b.length<4 || b.length>65544) throw new IllegalArgumentException("APDU 长度不合法");
        return b;
    }
}
