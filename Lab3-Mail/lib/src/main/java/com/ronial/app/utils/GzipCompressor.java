package com.ronial.app.utils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class GzipCompressor {
    public static String compress(String data) {
        byte[] input = data.getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater();
        deflater.setInput(input);
        deflater.finish();

        byte[] buffer = new byte[1024];
        StringBuilder compressedData = new StringBuilder();

        while (!deflater.finished()) {
            int bytesCompressed = deflater.deflate(buffer);
            compressedData.append(Base64.getEncoder().encodeToString(Arrays.copyOfRange(buffer, 0, bytesCompressed)));
        }
        return compressedData.toString();
    }

    public static String decompress(String compressedData) throws Exception {
        byte[] input = Base64.getDecoder().decode(compressedData);
        Inflater inflater = new Inflater();
        inflater.setInput(input);

        byte[] buffer = new byte[1024];
        StringBuilder decompressedData = new StringBuilder();

        while (!inflater.finished()) {
            int bytesDecompressed = inflater.inflate(buffer);
            decompressedData.append(new String(buffer, 0, bytesDecompressed, StandardCharsets.UTF_8));
        }
        return decompressedData.toString();
    }
}