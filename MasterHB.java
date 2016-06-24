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
	private ConcurrentMap<Integer, Slave> slaveMap = new ConcurrentHashMap<>();
	private List<SlaveRunnable> slaveRunnableList = new ArrayList<>();
	private ConcurrentMap<Integer, String> slaveNameMap = new ConcurrentHashMap<>();
	protected ConcurrentMap<Integer, Long> currentWordIndexMap = new ConcurrentHashMap<>();
	private ConcurrentMap<Integer, Long> lastCallMap = new ConcurrentHashMap<>();
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
		if(slaveNameMap.containsValue(slaveName)) {
			for (ConcurrentMap.Entry<Integer, String> entry : slaveNameMap.entrySet()) {
				Integer key = entry.getKey();
				String value = entry.getValue();
				if (slaveName.equals(value)) {
					slaveMap.put(key, s);
					return key;
				}
			}
		}
		int key = registry++;
		slaveNameMap.put(key,slaveName);
		slaveMap.put(key,s);
		System.out.println("Slave "+ slaveName +" added.");
		return key;
	}

	/**
	* Desregistra escravo no mestre.
	* @param slaveKey chave que identifica o escravo
	* @throws RemoteException
	*/
	public void removeSlave(int slaveKey) throws RemoteException {
		Slave slave = slaveMap.remove(slaveKey);
		if(slave == null) {
			System.out.println("Slave "+ slaveKey + " not found!");
			throw new RemoteException();
		}
		System.out.println("Slave "+ slaveNameMap.remove(slaveKey)+" removed.");
	}

	/**
	* Indica para o mestre que o escravo achou uma chave candidata.
	* @param currentindex indice da chave candidata no dicionario
	* @param currentguess chute que inclui chave candidata e
	* mensagem decriptografada com a chave candidata
	* @throws RemoteException
	*/
	public void foundGuess(long currentindex,Guess currentguess) throws RemoteException {
		SlaveRunnable slaveRunnable = findSlaveRunnableByIndex(currentindex);
		if(slaveRunnable == null) {
			System.out.println("Could not find slave responsible for the guess in the index "+ currentindex);
			System.out.println("Guess discarted");
		}else{
			System.out.println("Slave name: " + slaveRunnable.getSlaveName()+" found guess");
			System.out.println("Current Index: " + currentindex);
			guesses.add(currentguess);
		}
	}

	/**
	* Encontra o escravo responsavel pelo processamento do dado indice
	* @param index.
	* @throws RemoteException
	*/
	private SlaveRunnable findSlaveRunnableByIndex(long index){
		for (SlaveRunnable slaveRunnable : slaveRunnableList) {
			if(index <= slaveRunnable.getFinalWordIndex() && index >= slaveRunnable.getInitialWordIndex()) {
				return slaveRunnable;
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
		SlaveRunnable slaveRunnable = findSlaveRunnableByIndex(currentindex);
		if(slaveRunnable != null) {
			currentWordIndexMap.put(slaveRunnable.getKey(),currentindex);
			lastCallMap.put(slaveRunnable.getKey(),System.currentTimeMillis());
			System.out.println("Checkpoint received:");
			System.out.println("Slave name: " + slaveRunnable.getSlaveName());
			System.out.println("Index: " + currentindex);
			return;
		}
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

		List<Thread> threads = spreadAttack(0,dictionarySize, false);

		Runnable watchDog = new Runnable() {
			@Override
			public void run() {
				try {
					watchAttack();
				}catch (RemoteException e) {System.out.println(e.getMessage()); }
			}
		};
		ScheduledExecutorService checkpointScheduler = Executors.newScheduledThreadPool(1);
		checkpointScheduler.scheduleAtFixedRate(watchDog, 6, 5, SECONDS);

		try {
			for (Thread thread : threads) {
				thread.join();
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

		List<Thread> threads = spreadAttack(0,dictionarySize, true);
		try {
			for (Thread thread : threads) {
				thread.join();
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
	protected List<Thread> spreadAttack(long initialWordIndex,long finalWordIndex, boolean overhead){
		int slaveMapSize = slaveMap.size();
		if(slaveMapSize == 0) {
			System.out.println("No Slaves");
			return null;
		}
		long searchSize = finalWordIndex - initialWordIndex;
		List<Thread> threadList = new ArrayList<>();

		long slavePartition = searchSize/slaveMapSize;
		long extraPartition = searchSize%slaveMapSize;

		long aux = initialWordIndex;
		//Iterates over each slave activating a thread for each
		for (ConcurrentMap.Entry<Integer, Slave> entry : slaveMap.entrySet()) {
			Slave slave = entry.getValue();
			String slaveName = slaveNameMap.get(entry.getKey());
			SlaveRunnable runnable;
			if(extraPartition-- > 0) {
				runnable = new SlaveRunnable(slave,slaveName,entry.getKey(), aux, aux+slavePartition,overhead,this);
				aux += slavePartition + 1;
			}
			else{
				runnable = new SlaveRunnable(slave,slaveName,entry.getKey(), aux, aux+slavePartition-1,overhead,this);
				aux += slavePartition;
			}
			Thread thread = new Thread(runnable);
			thread.start();
			threadList.add(thread);
			slaveRunnableList.add(runnable);
		}
		return threadList;
	}

	/**
	* Funcao que verifica se todos os escravos realizaram checkpoint nos ultimos 20 segundos.
	* casp nao, o escravo e removido.
	* @throws RemoteException
	*/
	public void watchAttack() throws RemoteException {
		synchronized(slaveMap){
			for (ConcurrentMap.Entry<Integer, Slave> entry : slaveMap.entrySet()) {
				long lastCall = lastCallMap.get(entry.getKey());
				System.out.println(lastCall - System.currentTimeMillis());
				if(lastCall > System.currentTimeMillis() - 5000) {
					System.out.println("oi");
					if (!allFinished()) {
						//removeSlave(runnable.getKey());
						//spreadAttack(runnable.getCurrentWordIndex(),runnable.getFinalWordIndex(), false);
						System.out.println("Slave "+ slaveNameMap.get(entry.getKey()) + " late for the checkpoint.");
					}
				}
			}
		}
	}


	/**
	* Retorna se todos os escravos ja terminaram.
	*/
	private boolean allFinished(){
		boolean aux = true;
		for(SlaveRunnable runnable : slaveRunnableList) {
			if(currentWordIndexMap.get(runnable.getKey()) < runnable.getFinalWordIndex()) {
				aux = false;
				break;
			}
		}
		return aux;
	}

	public static void main(String[] args) {
		//Util.getRidOfPrint();
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
