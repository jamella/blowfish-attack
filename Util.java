/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.BufferedReader;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import java.util.ArrayList;
import br.inf.ufes.pp2016_01.*;
/**
 *
 * @author thiago
 */
public class Util {

    public static final int MIN_VALUE_TAM_BYTES = 1000;
    public static final int MAX_VALUE_TAM_BYTES = 100000;

    public static byte[] readFile(String filename) throws IOException {

        File file = new File(filename);
        InputStream is = new FileInputStream(file);
        long length = file.length();

        //creates array (assumes file length<Integer.MAX_VALUE)
        byte[] data = new byte[(int) length];

        int offset = 0;
        int count = 0;

        while ((offset < data.length) && (count = is.read(data, offset, data.length - offset)) >= 0) {
            offset += count;
        }
        is.close();
        return data;
    }

    public static void saveFile(String filename, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(filename);
        out.write(data);
        out.close();
    }

    //gera um vetor aleatoria de tamanho tamanho_vetor, ou tamanho aleatorio quando essa variavel for 0
    public static byte[] randonBytes(int tamanho_vetor) {

        SecureRandom r = new SecureRandom();
        int nElementos;

        if (tamanho_vetor == 0) {
            nElementos = r.nextInt(99000) + 1000;
        } else {
            System.out.println("tamanho do vetor: " + tamanho_vetor);
            nElementos = tamanho_vetor;
        }

        byte[] byte_array = new byte[nElementos];
        r.nextBytes(byte_array);  // randon byte array generated
        return byte_array;

    }

    public static List<byte[]> loadDictionaryToMemory() {
        List<byte[]> words = new ArrayList<>();
        InputStream ins = null; // raw byte-stream
        Reader r = null; // cooked reader
        BufferedReader br = null; // buffered for readLine()
        try {
            String s;
            ins = new FileInputStream("dados/dicionario.txt");
            r = new InputStreamReader(ins, "UTF-8"); // leave charset out for default
            br = new BufferedReader(r);
            while ((s = br.readLine()) != null) {
                words.add(s.getBytes());
            }
        } catch (Exception e) {
            System.err.println(e.getMessage()); // handle exception
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable t) { /* ensure close happens */ }
            }
            if (r != null) {
                try {
                    r.close();
                } catch (Throwable t) { /* ensure close happens */ }
            }
            if (ins != null) {
                try {
                    ins.close();
                } catch (Throwable t) { /* ensure close happens */ }
            }
        }

        return words;
    }

    public static boolean containsSubArray(byte[] source, byte[] match) {
        boolean found = false;
        if (source.length == 0 || match.length == 0 || source.length < match.length) {
            return found;
        }
        for (int i = 0; i < source.length; i++) {
            if (i + match.length > source.length) {
                return false;
            }
            for (int j = 0; j < match.length; j++) {
                if (source[i + j] != match[j]) {
                    break;
                }
                if (j == match.length - 1 && source[i + j] == match[j]) {
                    return true;
                }
            }
        }
        return found;
    }
}
