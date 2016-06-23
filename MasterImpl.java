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
 *
 * @author thiago
 */
public class MasterImpl implements Master, MasterOverhead {

    private ConcurrentMap<Integer, Slave> slaves;
    private ConcurrentMap<Integer, String> slavesName;
    private ConcurrentMap<Integer, Long> checkpoint;
    private ConcurrentMap<Integer, Date> checkpointTime;
    private List<SlaveRunnable> runningSlaves;
    private List<Thread> threads;
    private List<Guess> guesses;
    private int dictionarySize;
    private ScheduledExecutorService checkpointTimerScheduler;
    private boolean isAttacking;

    public MasterImpl() {
        slaves = new ConcurrentHashMap<>();
        slavesName = new ConcurrentHashMap<>();
        checkpoint = new ConcurrentHashMap<>();
        checkpointTime = new ConcurrentHashMap<>();
        runningSlaves = new ArrayList<>();
        threads = new ArrayList<>();
        guesses = new ArrayList<>();
        checkpointTimerScheduler = Executors.newScheduledThreadPool(1);
    }

    @Override
    public int addSlave(Slave s, String slavename) throws RemoteException {
        int slaveId = -1;
        Map<Integer, String> slavesNameCopy;
        synchronized (slavesName) {
            slavesNameCopy = new HashMap<>(slavesName);
        }

        for (Map.Entry<Integer, String> entrySet : slavesNameCopy.entrySet()) {
            Integer key = entrySet.getKey();
            String value = entrySet.getValue();
            if (slavename.equals(value)) {
                slaveId = key;
                break;
            }
        }
        if (slaveId == -1) {
            slaveId++;
            System.out.println("Novo Escravo:");
            System.out.println("Nome: " + slavename);
            System.out.println("ID:" + slaveId + "\n\n");
        }

        slaves.put(slaveId, s);
        slavesName.put(slaveId, slavename);
        return slaveId;
    }

    @Override
    public void removeSlave(int slaveKey) throws RemoteException {
        slaves.remove(slaveKey);
        slavesName.remove(slaveKey);
        System.out.println("Escravo Removido:");
        System.out.println("ID: " + slaveKey + "\n");
    }

    @Override
    public synchronized void foundGuess(long currentindex, Guess currentguess) throws RemoteException {
        int slaveId = getRunningSlaveIdByIndex(currentindex);
        System.out.println("Nome do Escravo: " + slavesName.get(slaveId));
        System.out.println("Indice Atual: " + currentindex);
        System.out.println("\n");
        guesses.add(currentguess);
    }

    private int getRunningSlaveIdByIndex(long index) {
        int id = 0;
        for (SlaveRunnable runningSlave : runningSlaves) {
            if (runningSlave.initialwordindex <= index && index <= runningSlave.finalwordindex) {
                id = runningSlave.idSlave;
            }
        }
        return id;
    }

    @Override
    public void checkpoint(long currentindex) throws RemoteException {
        SlaveRunnable slaveCheckingIn = null;
        int slaveId = getRunningSlaveIdByIndex(currentindex);
        List<SlaveRunnable> runningSlavesCopy;
        synchronized (runningSlaves) {
            runningSlavesCopy = new ArrayList<>(runningSlaves);
        }

        for (SlaveRunnable runningSlave : runningSlavesCopy) {
            if (slaveId == runningSlave.idSlave) {
                slaveCheckingIn = runningSlave;
            }
        }

        checkpoint.put(slaveId, currentindex);
        checkpointTime.put(slaveId, new Date());
        System.out.println("\nCheckpoint Recebido:");
        System.out.println("Nome Escravo: " + slavesName.get(slaveId) + "");
        System.out.println("Indice: " + currentindex);
        if (slaveCheckingIn != null) {
            System.out.println("Tempo gasto desde o começo do ataque: " + (checkpointTime.get(slaveId).getTime() - slaveCheckingIn.dateTimeInit.getTime()) * 1.0 / 1000 + "\n");
        }
    }

    @Override
    public Guess[] attack(byte[] ciphertext, byte[] knowntext) throws RemoteException {
        if (slaves.isEmpty()) {
            System.out.println("Não existe nenhum escravo registrado. Não é possível efetuar o ataque");
            return new Guess[0];
        }
        isAttacking = true;
        System.out.println("*****************************************\n");
        System.out.println("Iniciando Ataque!!!\n*****************************************\n\n");
        splitWorkBetweenSlaves(ciphertext, knowntext);

        boolean areThereFaultySlaves = true;
        while (areThereFaultySlaves) {
            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException ex) {
                    System.out.println("\nErro ao juntar as Threads\n");
                    ex.printStackTrace();
                }
            }
            threads = new ArrayList<>();
            List<SlaveRunnable> faultySlavesRunners = new ArrayList<>();
            for (SlaveRunnable runningSlave : runningSlaves) {
                if (!runningSlave.success) {
                    removeSlave(runningSlave.idSlave);
                    faultySlavesRunners.add(runningSlave);
                }
            }
            if (!faultySlavesRunners.isEmpty()) {
                System.out.println("Existem escravos que falharam. Redistribuindo trabalho entre os escravos restantes\n");
                if (slaves.isEmpty()) {
                    System.out.println("Não existe nenhum escravo registrado. Não é possível efetuar o ataque");
                    isAttacking = false;
                    return new Guess[0];
                }
                splitWorkFromFaultySlave(ciphertext, knowntext, faultySlavesRunners);
            } else {
                areThereFaultySlaves = false;
            }
        }
        isAttacking = false;
        checkpointTimerScheduler.shutdown();
        checkpointTimerScheduler = Executors.newScheduledThreadPool(1);
        checkpoint = new ConcurrentHashMap<>();
        checkpointTime = new ConcurrentHashMap<>();
        System.out.println("*****************************************\n");
        System.out.println("Ataque Finalizado\n*********s********************************\n\n");
        Guess[] guess = new Guess[guesses.size()];
        for (int i = 0; i < guesses.size(); i++) {
            guess[i] = guesses.get(i);
        }
        guesses = new ArrayList<>();
        return guess;
    }

    @Override
    public Guess[] attackOverhead(byte[] ciphertext, byte[] knowntext) throws RemoteException {
        return null;
    }

    public void splitWorkBetweenSlaves(byte[] ciphertext, byte[] knowntext) {
        Map<Integer, Slave> slavesCopy;
        synchronized (slaves) {
            slavesCopy = new HashMap<>(slaves);
        }
        int numberOfSlaves = slavesCopy.size();
        int qtd = this.dictionarySize / numberOfSlaves;
        int rst = this.dictionarySize % numberOfSlaves;

        int begin = 0;
        int end = qtd + rst;
        List<SlaveRunnable> runningSlavesSplitingWork = new ArrayList<>();

        for (Map.Entry<Integer, Slave> entrySet : slavesCopy.entrySet()) {
            Integer slaveId = entrySet.getKey();
            Slave slave = entrySet.getValue();
            SlaveRunnable sr = new SlaveRunnable(slaveId, slave, begin, end, ciphertext, knowntext, this);
            runningSlavesSplitingWork.add(sr);
            Thread thread = new Thread(sr, slaveId.toString());
            threads.add(thread);
            System.out.println("\nDistribuindo trabalho:");
            System.out.println("Nome do escravo: " + slavesName.get(slaveId));
            System.out.println("Indice de Inicio: " + begin);
            System.out.println("Indice de Fim: " + end + "\n");
            thread.start();
            begin = end;
            end += qtd;
        }
        runningSlaves = runningSlavesSplitingWork;
        initAutomaticRegistrationTimer();
    }

    public void splitWorkFromFaultySlave(byte[] ciphertext, byte[] knowntext, List<SlaveRunnable> faultySlavesRunners) {
        List<SlaveRunnable> newSlaveRunnables = new ArrayList<>();
        for (SlaveRunnable faultySlavesRunner : faultySlavesRunners) {
            Map<Integer, Slave> slavesCopy;
            synchronized (slaves) {
                slavesCopy = new HashMap<>(slaves);
            }
            int begin;
            Long checkpointSlaveRunnable = checkpoint.get(faultySlavesRunner.idSlave);
            if (checkpointSlaveRunnable != null) {
                begin = checkpointSlaveRunnable.intValue();
            } else {
                begin = (int) faultySlavesRunner.initialwordindex;
            }
            int numberOfSlaves = slavesCopy.size();
            int difference = (int) (faultySlavesRunner.finalwordindex - begin);
            int qtd = difference / numberOfSlaves;
            int rst = difference % numberOfSlaves;
            int end = begin + qtd + rst;

            for (Map.Entry<Integer, Slave> entrySet : slavesCopy.entrySet()) {
                Integer slaveId = entrySet.getKey();
                Slave slave = entrySet.getValue();
                SlaveRunnable sr = new SlaveRunnable(slaveId, slave, begin, end, ciphertext, knowntext, this);
                newSlaveRunnables.add(sr);
                Thread thread = new Thread(sr, slaveId.toString());
                threads.add(thread);
                System.out.println("\nReistribuindo trabalho:");
                System.out.println("Nome do escravo: " + slavesName.get(slaveId));
                System.out.println("Indice de Inicio: " + begin);
                System.out.println("Indice de Fim: " + end + "\n");
                thread.start();
                begin = end;
                end += qtd;
            }
        }
        runningSlaves = newSlaveRunnables;
    }

    public void checkIfThereAreZombies() {
        Map<Integer, Slave> slavesCopy;
        if (!isAttacking) {
            return;
        }
        synchronized (slaves) {
            slavesCopy = new HashMap<>(slaves);
        }
        for (Map.Entry<Integer, Slave> entrySet : slavesCopy.entrySet()) {
            Integer slaveId = entrySet.getKey();
            Date dateCheckpoint = checkpointTime.get(slaveId);
            Date now = new Date();
            long diff = now.getTime() - dateCheckpoint.getTime();
            if (diff > 2 * 1000) {
                System.out.println("O escravo " + slavesName.get(slaveId) + " não enviou checkpoint nenhum nos últimos 20 segunods. Seu trabalho foi interrompido\n");
                checkpointTime.remove(slaveId);
                try {
                    removeSlave(slaveId);
                } catch (RemoteException ex) {
                    System.out.println("\nErro ao remover um escravo que não enviou checkpoint nos últimos 20 segunods\n");
                }
                for (Thread thread : threads) {
                    if (thread.getName().equals(slaveId.toString())) {
                        thread.interrupt();
                    }
                }
            }
        }
    }

    public void initAutomaticRegistrationTimer() {
        Runnable checkIfRunningSlavesAreAlive = new Runnable() {
            @Override
            public void run() {
                checkIfThereAreZombies();
                checkpointTime = new ConcurrentHashMap<>();
            }
        };
        final ScheduledFuture<?> automaticRegistrationHandle = checkpointTimerScheduler.scheduleAtFixedRate(checkIfRunningSlavesAreAlive, 21, 20, SECONDS);
    }

    public void loadDictionary(String dictionaryName) {
        InputStream ins = null; // raw byte-stream
        Reader r = null; // cooked reader
        BufferedReader br = null; // buffered for readLine()
        int i = 0;
        try {
            String s;
            ins = new FileInputStream("dados/dicionario.txt");
            r = new InputStreamReader(ins, "UTF-8"); // leave charset out for default
            br = new BufferedReader(r);
            while ((s = br.readLine()) != null) {
                i++;
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
        this.dictionarySize = i;
        System.out.println("Tamanho do dicionário: " + i);
    }

    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry(); // opcional: host
            try {
                Master stub = (Master) registry.lookup("mestre");
                if (stub != null) {
                    registry.unbind("mestre");
                }
            } catch (RemoteException | NotBoundException ex) {
            }

            MasterImpl obj = new MasterImpl();

            List<String> dic = Util.loadDictionary();
            obj.dictionarySize = dic.size();

            Master objref = (Master) UnicastRemoteObject.exportObject(obj, 0);
            registry.rebind("mestre", objref);
            System.err.println("Server ready");
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
