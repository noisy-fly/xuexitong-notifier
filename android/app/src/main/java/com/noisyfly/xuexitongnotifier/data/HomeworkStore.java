package com.noisyfly.xuexitongnotifier.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

public final class HomeworkStore {
    private static final String ALIAS="homework_credentials_v1";
    private final SharedPreferences prefs;
    public HomeworkStore(Context c) { prefs=c.getApplicationContext().getSharedPreferences("homework",Context.MODE_PRIVATE); }
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        return (SecretKey)store.getKey(ALIAS,null);
    }
    public void saveCredentials(String username,String password) throws Exception {
        JSONObject object=new JSONObject(); object.put("user",username); object.put("password",password);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        String iv=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);
        String encrypted=Base64.encodeToString(cipher.doFinal(object.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if (!prefs.edit().putString("iv",iv).putString("secret",encrypted).commit()) throw new Exception("无法保存登录信息");
    }
    public JSONObject credentials() throws Exception {
        String iv=prefs.getString("iv",""); String secret=prefs.getString("secret","");
        if (iv.isEmpty() || secret.isEmpty()) return null;
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
        return new JSONObject(new String(cipher.doFinal(Base64.decode(secret,Base64.NO_WRAP)),StandardCharsets.UTF_8));
    }
    public boolean loggedIn() { return !prefs.getString("secret","").isEmpty(); }
    public java.util.Set<String> selectedCourses() { return new java.util.HashSet<>(prefs.getStringSet("selected_courses",java.util.Collections.emptySet())); }
    public void logout() { prefs.edit().clear().apply(); }
    public SharedPreferences prefs() { return prefs; }
}
