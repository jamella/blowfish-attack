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
import java.io.File;
import java.util.Scanner;
import br.inf.ufes.pp2016_01.*;
import java.util.List;
import javax.crypto.Cipher;
import java.io.FileNotFoundException;
import javax.crypto.spec.SecretKeySpec;

public class Client {

private static final int SLAVES = 5;
private static final int AVERAGE = 1;
private static final int FINALSIZE = 100000;

private final String host;
private final String fileNameBytes;
private final byte[] knownString;
private Master master;
private byte[] cipherText;

public Client(String host, String fileNameBytes, String knownString, int size) {
	this.host = host;
	this.setMasterReference();
	this.fileNameBytes = fileNameBytes;
	this.setCipherBytesArray(this.fileNameBytes, size);
	this.knownString = knownString.getBytes();
}

public void setMasterReference() {
	try {
		Registry registry = LocateRegistry.getRegistry(host.equals("null") ? null : host);
		master = (Master) registry.lookup("mestre");
	} catch (Exception ex) {
		System.out.println("Erro ao localizar o mestre");
		ex.printStackTrace();
	}
}

public void setCipherBytesArray(String fileNameBytes, int size) {
	try {
		cipherText = Util.readFile(fileNameBytes);
		System.out.println(cipherText.length);
	} catch (IOException ex) {
		System.out.println("Wasn't able to open the file.\nGenerating a random byte array...");
		cipherText = Util.generateArrayOfRandonBytes(size);
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

public void normalAttack() {
	try {
		long startTime = System.nanoTime();
		Guess[] guesses = master.attack(cipherText, knownString);
		long elapsedTime = System.nanoTime() - startTime;
		double seconds = (double) elapsedTime / 1000000000.0;
		System.out.println("It was spent " + seconds + " seconds to find a answer.");

		if(guesses != null) {
			System.out.println("Possible Keys: " + guesses.length);
			for (Guess guess : guesses) {
				System.out.println(guess.getKey());
				Util.saveFile(guess.getKey() + ".msg", guess.getMessage());
			}
		}
	} catch (IOException ex) {
		ex.printStackTrace();
	}
}

public void automaticAttack() {
	double[] serialTime = new double[50];
	byte[][] cipherTextAux = new byte[50][];
	//try {
	//FileWriter writerSerial = new FileWriter("testeSerialTamTempo.csv");
	// Iterates of the list of bytes, with a jump of 1000, for a serial test
	int j = 0;
	//for(int i = 2000; i <= FINALSIZE; i = i+2000) {

	try {
		Scanner scan;
		File file = new File("testeSerialTamTempo");
		scan = new Scanner(file);

		while(scan.hasNextDouble())
		{
			serialTime[j] = scan.nextDouble();
			System.out.println(serialTime[j]);
			j++;

		}

	} catch (FileNotFoundException e1) {
		System.out.println("Nao abriu a porra do arquivo");
		e1.printStackTrace();
	}
	/*cipherTextAux[j] = (byte[]) java.util.Arrays.copyOfRange(cipherText, 0, i);
	   long startTimeLocal = System.nanoTime();
	   attack(cipherTextAux[j], knownString);
	   long elapsedTimeLocal = System.nanoTime() - startTimeLocal;
	   double seconds = (double) elapsedTimeLocal / 1000000000.0;
	   serialTime[j] = seconds;
	   writerSerial.append(i + "," + seconds + "\n");
	   j++;
	   }
	   writerSerial.close();
	   } catch(IOException e) {
	   e.printStackTrace();
	   }*/
	int m = 0;
	try {
		//Iterates from 5 slaves till 2.
		//for(j = SLAVES; j > ; j--) {
		//Create files to write the data.
		FileWriter writerTime = new FileWriter("teste"+SLAVES+"TamTempo.csv");
		FileWriter writerSpeedUp = new FileWriter("teste"+SLAVES+"TamSpeed.csv");
		FileWriter writerEficiency = new FileWriter("teste"+SLAVES+"TamEficiencia.csv");
		FileWriter writerOverhead = new FileWriter("teste"+SLAVES+"TamOverhead.csv");

		//Iterates of the list of bytes, with a jump of 1000.

		for(int i = 2000; i <= FINALSIZE; i = i+2000) {
			long startTime = 0;
			long elapsedTime = 0;
			int k = 1;
			cipherTextAux[m] = (byte[]) java.util.Arrays.copyOfRange(cipherText, 0, i);

			for(k = 0; k < AVERAGE; k++) {
				startTime = System.nanoTime();
				master.attack(cipherTextAux[m], knownString);
				elapsedTime += System.nanoTime() - startTime;
			}

			double seconds = (double) elapsedTime / 1000000000.0;
			writerTime.append(i + "," + seconds/(k*1.0) + "\n");
			System.out.println(m);
			System.out.println(serialTime[m]);

			float speedUp = (float)(serialTime[m]/(seconds/(k*1.0)));

			writerSpeedUp.append(i + "," + speedUp + "\n");
			writerEficiency.append(i + "," + speedUp/(SLAVES*1.0) + "\n");

			elapsedTime = 0;
			MasterOverhead ma = (MasterOverhead) master;
			for(k = 0; k < AVERAGE; k++) {
				startTime = System.nanoTime();
				ma.attackOverhead(cipherTextAux[m], knownString);
				elapsedTime += System.nanoTime() - startTime;
			}
			seconds = (double) elapsedTime / 1000000000.0;
			writerOverhead.append(i + "," + seconds/(k*1.0) + "\n");
			m++;
		}
		//this.master.removeSlave(j-1);
		writerTime.close();
		writerSpeedUp.close();
		writerOverhead.close();
		writerEficiency.close();
		//  }
	} catch(IOException e) {
		e.printStackTrace();
	}
}

public static void main(String[] args) {
	if (args.length < 3) {
		System.out.println("Try:\njava Client MasterIP pathOfFile KnownText");
		exit(0);
	}

	//Util.getRidOfPrint();

	Client client;
	if (args.length == 4) {
		int size = Integer.parseInt(args[3]);
		client = new Client(args[0], args[1], args[2], size);
	} else {
		client = new Client(args[0], args[1], args[2], 0);
	}

	client.automaticAttack();

}

}
