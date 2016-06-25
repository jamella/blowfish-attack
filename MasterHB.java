import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import br.inf.ufes.pp2016_01.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import static java.util.concurrent.TimeUnit.SECONDS;




class MasterHB implements Master, MasterOverhead {
private ConcurrentMap<Integer, SlaveRunnable> slaveMap = new ConcurrentHashMap<>();
private ConcurrentMap<String, Integer> slaveNameMap = new ConcurrentHashMap<>();
private int registry = 0;
private List<Guess> guesses = new ArrayList<>();
public byte[] cipherText;
public byte[] knownText;
private int dictionarySize;

public MasterHB(int dictionarySize){
	this.dictionarySize = dictionarySize;
}

/**
 * Registra escravo no mestre. Deve ser chamada a cada 30s
 * por um escravo para se re-registrar.
 * @param s referencia para o escravo
 * @param slaveName identificador para o escravo
 * @return chave que identifica o escravo para posterior remocao
 * @throws RemoteException
 */
public int addSlave(Slave s, String slaveName) throws RemoteException {
	if(slaveNameMap.containsKey(slaveName)) {
		int key = slaveNameMap.get(slaveName);
		SlaveRunnable slave = new SlaveRunnable(s,slaveName,key);
		slaveMap.put(key, slave);
		return key;
	}
	int key = registry++;
	SlaveRunnable slave = new SlaveRunnable(s,slaveName,key);
	slaveNameMap.put(slaveName,key);
	slaveMap.put(key,slave);
	System.out.println("Slave "+ slaveName +" added.");
	return key;
}

/**
 * Desregistra escravo no mestre.
 * @param slaveKey chave que identifica o escravo
 * @throws RemoteException
 */
public void removeSlave(int slaveKey) throws RemoteException {
	SlaveRunnable slave = slaveMap.remove(slaveKey);
	if(slave == null) {
		System.out.println("Slave "+ slaveKey + " not found!");
		throw new RemoteException();
	}
	slaveNameMap.remove(slave.getSlaveName());
	System.out.println("Slave "+ slave.getSlaveName()+" removed.");
}

/**
 * Indica para o mestre que o escravo achou uma chave candidata.
 * @param currentindex indice da chave candidata no dicionario
 * @param currentguess chute que inclui chave candidata e
 * mensagem decriptografada com a chave candidata
 * @throws RemoteException
 */
public void foundGuess(long currentindex,Guess currentguess) throws RemoteException {
	SlaveRunnable slave = findSlaveByIndex(currentindex);
	if(slave == null) {
		System.out.println("Could not find slave responsible for the guess in the index "+ currentindex);
		System.out.println("Guess discarted");
	}else{
		System.out.println("Slave name: " + slaveMap.get(slave).getSlaveName());
		System.out.println("Current Index: " + currentindex);
		guesses.add(currentguess);
	}
}

/**
 * Encontra o escravo responsavel pelo processamento do dado indice
 * @param index.
 * @throws RemoteException
 */
private SlaveRunnable findSlaveByIndex(long index){
	for (ConcurrentMap.Entry<Integer, SlaveRunnable> entry : slaveMap.entrySet()) {
		SlaveRunnable slave = entry.getValue();
		System.out.println("Slave "+slave.getSlaveName() + "  initialwordindex: "+slave.getInitialWordIndex()+ "   finalwordindex: "+slave.getFinalWordIndex());
		System.out.println(index);
		if(index <= slave.getFinalWordIndex() -1 && index >= slave.getInitialWordIndex()) {
			return slave;
		}
	}
	return null;
}

/**
 * Chamado por cada escravo a cada 10s durante ataque para indicar
 * progresso no ataque, e ao final do ataque.
 * @param currentindex indice da chave ja verificada
 * @throws RemoteException
 */
public void checkpoint(long currentindex) throws RemoteException {
	SlaveRunnable slave = findSlaveByIndex(currentindex);
	if(slave != null) {
		slave.setCurrentWordIndex(currentindex);
		slave.setLastCall(System.currentTimeMillis());
		System.out.println("Checkpoint received:");
		System.out.println("Slave name: " + slave.getSlaveName());
		System.out.println("Index: " + currentindex);
		return;
	}
	System.out.println(findSlaveByIndex(currentindex));
	System.out.println("Checkpoint failed on index "+currentindex);
}

/**
 * Operacao oferecida pelo mestre para iniciar um ataque.
 * @param ciphertext mensagem critografada
 * @param knowntext trecho conhecido da mensagem decriptografada
 * @return vetor de chutes: chaves candidatas e mensagem
 * decritografada com chaves candidatas
 */
public Guess[] attack(byte[] ciphertext,byte[] knowntext) throws RemoteException {
	if (slaveMap.isEmpty()) {
		System.out.println("There are no slaves to execute the attack");
		return null;
	}
	this.cipherText = ciphertext;
	this.knownText = knowntext;

	ConcurrentMap<Integer,Thread> threads = spreadAttack(0,dictionarySize, false);

	Runnable watchDog = new Runnable() {
		@Override
		public void run() {
			try {
				watchAttack();
			}catch (RemoteException e) {System.out.println(e.getMessage()); }
		}
	};
	ScheduledExecutorService checkpointScheduler = Executors.newScheduledThreadPool(1);
	checkpointScheduler.scheduleAtFixedRate(watchDog, 21, 20, SECONDS);

	try {
		for (ConcurrentMap.Entry<Integer, Thread> entry : threads.entrySet()) {
			entry.getValue().join();
		}
	}catch (InterruptedException e) {
		System.out.println("Error while joining the Threads");
		System.out.println(e.getMessage());
	}

	return guesses.toArray(new Guess[guesses.size()]);
}

@Override
public Guess[] attackOverhead(byte[] ciphertext, byte[] knowntext) throws RemoteException {
	if (slaveMap.isEmpty()) {
		System.out.println("There are no slaves to execute the attack");
		return null;
	}
	this.cipherText = ciphertext;
	this.knownText = knowntext;

	ConcurrentMap<Integer,Thread> threads = spreadAttack(0,dictionarySize, true);

	try {
		for (ConcurrentMap.Entry<Integer, Thread> entry : threads.entrySet()) {
			entry.getValue().join();
		}
	}catch (InterruptedException e) {
		System.out.println("Error while joining the Threads");
		System.out.println(e.getMessage());
	}

	return null;
}

/**
 * Operacao que trata de distribuir cada escravo a sua tarefa.
 * @param initialWordIndex o indice que comeca a distribuicao
 * @param finalWordIndex o indice que termina
 * @return um HashMap das threads ja rodando.
 */
protected ConcurrentMap<Integer,Thread> spreadAttack(long initialWordIndex,long finalWordIndex, boolean overhead){
	int slaveMapSize = slaveMap.size();
	if(slaveMapSize == 0) {
		System.out.println("No Slaves");
		return null;
	}
	long dictionarySize = finalWordIndex - initialWordIndex;
	ConcurrentMap<Integer,Thread> threadMap = new ConcurrentHashMap<>();

	long slavePartition = dictionarySize/slaveMapSize;
	long extraPartition = dictionarySize%slaveMapSize;

	long aux = initialWordIndex;
	//Iterates over each slave activating a thread for each
	for (Integer index : slaveMap.keySet()) {
		if(extraPartition-- > 0) {
			slaveMap.get(index).setSubAttack(aux,aux+slavePartition+1,this, overhead);
			aux += slavePartition + 1;
		}
		else{
			slaveMap.get(index).setSubAttack(aux,aux+slavePartition,this, overhead);
			aux += slavePartition;
		}
		Thread thread = new Thread(slaveMap.get(index));
		thread.start();
		threadMap.put(index,thread);
	}
	return threadMap;
}

/**
 * Funcao que verifica se todos os escravos realizaram checkpoint nos ultimos 20 segundos.
 * casp nao, o escravo e removido.
 * @throws RemoteException
 */
public void watchAttack() throws RemoteException {
	for (Integer index : slaveMap.keySet()) {
		SlaveRunnable slave = slaveMap.get(index);
		if(slave.getLastCall() > System.currentTimeMillis() - 20000) {
			System.out.println("oi");
			if (!allFinished()) {
				//slave.getLastCall()
				//removeSlave(slave.getKey());
				//spreadAttack(slave.getCurrentWordIndex(),slave.getFinalWordIndex(), false);
				System.out.println("Slave "+ slave.getSlaveName() + " late for the checkpoint.");
			}
		}
	}
}


/**
 * Retorna se todos os escravos ja terminaram.
 */
private boolean allFinished(){
	boolean aux = true;
	for(Integer index : slaveMap.keySet()) {
		SlaveRunnable slave = slaveMap.get(index);
		if(slave.getCurrentWordIndex() < slave.getFinalWordIndex()) {
			aux = false;
			break;
		}
	}
	return aux;
}

public static void main(String[] args) {
	Util.getRidOfPrint();
	try {
		Registry registry = LocateRegistry.getRegistry();
		List<String> dictionary = Util.loadDictionary();
		Master master = (Master) UnicastRemoteObject.exportObject(new MasterHB(dictionary.size()),0);
		// Bind the remote object in the registry
		registry.rebind("mestre", master);
		System.out.println("Server ready");
	} catch (Exception e) {
		System.err.println("Server exception: " + e.toString());
		e.printStackTrace();
	}
}
}
