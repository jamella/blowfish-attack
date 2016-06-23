/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import java.io.IOException;
import static java.lang.System.exit;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Random;
import java.io.FileWriter;
import br.inf.ufes.pp2016_01.*;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Client {

    private static final int SLAVES = 3;
    private static final int AVERAGE = 1;
    private static final int FINALSIZE = 2000;

    private final String host;
    private final String fileNameBytes;
    private final byte[] knownString;
    private int sizeOfBytesVector;
    private Master master;
    private byte[] cipherText;

    public Client(String host, String fileNameBytes, String knownString) {
        this.host = host;
        this.setMasterReference(this.host);
        this.fileNameBytes = fileNameBytes;
        this.setCipherBytesArray(this.fileNameBytes);
        this.knownString = knownString.getBytes();
    }

    public void setMasterReference(String host) {
        try {
            Registry registry = LocateRegistry.getRegistry(host.equals("null") ? null : host);
            master = (Master) registry.lookup("mestre");
        } catch (Exception ex) {
            System.out.println("Erro ao localizar o mestre");
            ex.printStackTrace();
        }
    }

    public void setCipherBytesArray(String fileNameBytes) {
        try {
            cipherText = Util.readFile(fileNameBytes);
            System.out.println(cipherText.length);
        } catch (IOException ex) {
            System.out.println("Wasn't able to open the file");
        }
    }

    public void attack(byte[] ciphertext, byte[] knowntext) {
        List<String> dictionary = Util.loadDictionary();
        for (int index = 0; index < ciphertext.length; index++) {
            try {
                String key = dictionary.get(index);
                SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "Blowfish");
                Cipher cipher = Cipher.getInstance("Blowfish");
                cipher.init(Cipher.DECRYPT_MODE, keySpec);
                byte[] decrypted = cipher.doFinal(ciphertext);

                if (Util.containsSubArray(decrypted, knowntext)) {
                    Guess guess = new Guess();
                    guess.setKey(key);
                    guess.setMessage(decrypted);
                }
            } catch (Exception ex) {
                //System.out.println("Error during decrypting");
            }
        }
    }

    public void attackAndSaveFile() {
        double[] serialTime = new double[50];
        byte[][] cipherTextAux = new byte[50][];
        try {
            FileWriter writerSerial = new FileWriter("testeSerialTamTempo.csv");
            //Iterates of the list of bytes, with a jump of 1000, for a serial test
            for(int i = 1000; i <= FINALSIZE; i = i+1000) {
                cipherTextAux[(i/1000) -1] = (byte[]) java.util.Arrays.copyOfRange(cipherText, 0, i);
                long startTimeLocal = System.nanoTime();
                attack(cipherTextAux[(i/1000) -1], knownString);
                long elapsedTimeLocal = System.nanoTime() - startTimeLocal;
                double seconds = (double) elapsedTimeLocal / 1000000000.0;
                serialTime[(i/1000) - 1] = seconds;
                writerSerial.append(i + "," + seconds + "\n");
            }
            writerSerial.close();
        } catch(IOException e) {
            e.printStackTrace();
        }

        try {
            //Iterates from 5 slaves till 2.
            for(int j = SLAVES; j > 1; j--) {
                //Create files to write the data.
    			FileWriter writerTime = new FileWriter("teste"+j+"TamTempo.csv");
    			FileWriter writerSpeedUp = new FileWriter("teste"+j+"TamSpeed.csv");
                FileWriter writerEficiency = new FileWriter("teste"+j+"TamEficiencia.csv");
    			FileWriter writerOverhead = new FileWriter("teste"+j+"TamOverhead.csv");

                //Iterates of the list of bytes, with a jump of 1000.
                for(int i = 1000; i <= FINALSIZE; i = i+1000) {
                    long startTime = 0;
    				long elapsedTime = 0;
    				int k = 1;

    				for(k = 0; k < AVERAGE; k++) {
    					startTime = System.nanoTime();
    					Guess[] guesses = this.master.attack(cipherTextAux[(i/1000) - 1], knownString);
    					elapsedTime += System.nanoTime() - startTime;
    				}

                    double seconds = (double) elapsedTime / 1000000000.0;
                    writerTime.append(i + "," + seconds/(k*1.0) + "\n");

                    double speedUp = (serialTime[(i/1000) - 1]/(elapsedTime/(k*1.0)));
    				writerSpeedUp.append(i + "," + speedUp + "\n");
                    writerEficiency.append(i + "," + speedUp/(j*1.0) + "\n");

                    elapsedTime = 0;
                    MasterOverhead m = (MasterOverhead) this.master;
                    for(k = 0; k < AVERAGE; k++) {
    					startTime = System.nanoTime();
    					Guess[] guesses = m.attackOverhead(cipherTextAux[(i/1000) - 1], knownString);
    					elapsedTime += System.nanoTime() - startTime;
    				}
    				seconds = (double) elapsedTime / 1000000000.0;
    				writerOverhead.append(i + "," + seconds/(k*1.0) + "\n");
                }
                this.master.removeSlave(j-1);
                writerTime.close();
                writerSpeedUp.close();
                writerOverhead.close();
                writerEficiency.close();
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Util.getRidOfPrint();
        if (args.length < 3) {
            System.out.println("Try:\njava Client MasterIP pathOfFile KnownText");
            exit(0);
        }

        Client client = new Client(args[0], args[1], args[2]);

        if (args.length == 4) {
            client.sizeOfBytesVector = Integer.parseInt(args[3]);
        }

        client.attackAndSaveFile();

    }

}
