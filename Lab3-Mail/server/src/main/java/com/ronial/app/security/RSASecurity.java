package com.ronial.app.security;

import com.ronial.app.context.Context;
import com.ronial.app.context.ContextProvider;
import com.ronial.app.views.LogFrame;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RSASecurity implements Context {
    private static final String FOLDER_NAME = "keys";
    private static final String PATH_PUBLIC_KEY = "keys/ppk-u.key";
    private static final String PATH_PRIVATE_KEY = "keys/ppk-r.key";
    private KeyPair keyPair;
    public RSASecurity(){}
    public String encode(String value) throws NoSuchPaddingException, NoSuchAlgorithmException,
            IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        Cipher c = Cipher.getInstance("RSA");
        c.init(Cipher.ENCRYPT_MODE, getKeyPair().getPublic());
        byte encryptOut[] = c.doFinal(value.getBytes());
        return Base64.getEncoder().encodeToString(encryptOut);
    }

    public String decode(String hash) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException {
        Cipher c = Cipher.getInstance("RSA");
        c.init(Cipher.DECRYPT_MODE, getKeyPair().getPrivate());
        byte decryptOut[] = c.doFinal(Base64.getDecoder().decode(hash));
        return new String(decryptOut);
    }

    private KeyPair getKeyPair() throws NoSuchAlgorithmException {
        if (keyPair != null) return keyPair;
        File publicKeyFile = new File(PATH_PUBLIC_KEY);
        File privateKeyFile = new File(PATH_PRIVATE_KEY);
        if (publicKeyFile.exists() && privateKeyFile.exists()) {
            try {
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");

                byte[] publicKeyBytes = Files.readAllBytes(publicKeyFile.toPath());
                EncodedKeySpec publicEncodedKeySpec = new X509EncodedKeySpec(publicKeyBytes);
                PublicKey publicKey = keyFactory.generatePublic(publicEncodedKeySpec);

                byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());
                EncodedKeySpec privateEncodedKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
                PrivateKey privateKey = keyFactory.generatePrivate(privateEncodedKeySpec);
                keyPair = new KeyPair(publicKey, privateKey);
                return keyPair;
            } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException e) {
                ContextProvider.<LogFrame>get(LogFrame.class)
                        .addLog(RSASecurity.class, e);
                throw new RuntimeException(e);
            }
        } else {
           return generateKey();
        }
    }

    private KeyPair generateKey() throws NoSuchAlgorithmException {
        File directory = new File(FOLDER_NAME);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        keyPair = keyPairGenerator.generateKeyPair();

        try {
            FileOutputStream out = new FileOutputStream(PATH_PUBLIC_KEY);
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(keyPair.getPublic().getEncoded());
            out.write(publicKeySpec.getEncoded());
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            FileOutputStream out = new FileOutputStream(PATH_PRIVATE_KEY);
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(keyPair.getPrivate().getEncoded());
            out.write(privateKeySpec.getEncoded());
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return keyPair;
    }
}
