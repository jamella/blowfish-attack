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
import java.nio.file.Files;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    public static List<String> loadDictionary() {
        List<String> words = new ArrayList<>();

        Charset charset = Charset.forName("UTF-8");
        Path path = Paths.get("data/dicionario.txt");
        try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                words.add(line);
            }
        } catch (IOException x) {
            System.err.format("IOException: %s%n", x);
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

    public static void getRidOfPrint() {
        System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
            @Override public void write(int b) {}
        }) {
            @Override public void flush() {}
            @Override public void close() {}
            @Override public void write(int b) {}
            @Override public void write(byte[] b) {}
            @Override public void write(byte[] buf, int off, int len) {}
            @Override public void print(boolean b) {}
            @Override public void print(char c) {}
            @Override public void print(int i) {}
            @Override public void print(long l) {}
            @Override public void print(float f) {}
            @Override public void print(double d) {}
            @Override public void print(char[] s) {}
            @Override public void print(String s) {}
            @Override public void print(Object obj) {}
            @Override public void println() {}
            @Override public void println(boolean x) {}
            @Override public void println(char x) {}
            @Override public void println(int x) {}
            @Override public void println(long x) {}
            @Override public void println(float x) {}
            @Override public void println(double x) {}
            @Override public void println(char[] x) {}
            @Override public void println(String x) {}
            @Override public void println(Object x) {}
            @Override public java.io.PrintStream printf(String format, Object... args) { return this; }
            @Override public java.io.PrintStream printf(java.util.Locale l, String format, Object... args) { return this; }
            @Override public java.io.PrintStream format(String format, Object... args) { return this; }
            @Override public java.io.PrintStream format(java.util.Locale l, String format, Object... args) { return this; }
            @Override public java.io.PrintStream append(CharSequence csq) { return this; }
            @Override public java.io.PrintStream append(CharSequence csq, int start, int end) { return this; }
            @Override public java.io.PrintStream append(char c) { return this; }
        });
    }
}
