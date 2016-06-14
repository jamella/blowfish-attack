import br.inf.ufes.pp2016_01.*;/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Subclasse que é executada por uma thread para efetuar o paralelismo
 * durante o ataque
 */
public class SlaveRunnable implements Runnable {

    public final int idSlave;
    public final Slave slave;
    public final long initialwordindex;
    public final long finalwordindex;
    public boolean success;
    public final byte[] ciphertext;
    public final byte[] knowntext;
    public final SlaveManager slaveManager;
    public Date dateTimeInit;

    public SlaveRunnable(int idSlave, Slave slave, long initialwordindex, long finalwordindex, byte[] ciphertext, byte[] knowntext, SlaveManager slaveManager) {
        this.idSlave = idSlave;
        this.slave = slave;
        this.initialwordindex = initialwordindex;
        this.finalwordindex = finalwordindex;
        this.ciphertext = ciphertext;
        this.knowntext = knowntext;
        this.slaveManager = slaveManager;
        this.success = false;
    }

    @Override
    public void run() {
        try {
            dateTimeInit = new Date();
            slave.startSubAttack(ciphertext, knowntext, initialwordindex, finalwordindex, slaveManager);
            success = true;
        } catch (Exception ex) {
            System.out.println("Exceção ao chamar escravo. Trabalho deverá ser redistribuido");
            ex.printStackTrace();
        }
    }
}
