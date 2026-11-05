package cn.cardlab;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import java.nio.file.Files;
import java.io.File;

final class Vault {
    private final File file;
    Vault(Context c) { file=new File(c.getFilesDir(),"cards.enc"); }
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if(!ks.containsAlias("cardlab")) {
            KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder("cardlab",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); g.generateKey();
        }
        return (SecretKey)ks.getKey("cardlab",null);
    }
    JSONArray read() throws Exception {
        if(!file.exists()) return new JSONArray();
        byte[] data=Files.readAllBytes(file.toPath());
        if(data.length<28) throw new IllegalStateException("卡包文件损坏");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,data,0,12));
        return new JSONArray(Bytes.text(c.doFinal(data,12,data.length-12)));
    }
    void write(JSONArray cards) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key());
        byte[] body=c.doFinal(cards.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] all=new byte[12+body.length]; System.arraycopy(c.getIV(),0,all,0,12); System.arraycopy(body,0,all,12,body.length);
        File tmp=new File(file.getParentFile(),"cards.tmp"); Files.write(tmp.toPath(),all);
        Files.move(tmp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
