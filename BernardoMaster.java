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




class BernardoMaster implements Master{
  private ConcurrentMap<Integer, SlaveRunnable> slaveMap = new ConcurrentHashMap<>();
  private ConcurrentMap<String, Integer> slaveNameMap = new ConcurrentHashMap<>();
  private int registry = 0;
  private List<Guess> guesses = new ArrayList<>();
  public byte[] cipherText;
  public byte[] knownText;
  private int dictionarySize;

  public BernardoMaster(int dictionarySize){
    this.dictionarySize = dictionarySize;
  }

  /**
	 * Registra escravo no mestre. Deve ser chamada a cada 30s
	 * por um escravo para se re-registrar.
	 * @param s referência para o escravo
	 * @param slaveName identificador para o escravo
	 * @return chave que identifica o escravo para posterior remoção
	 * @throws RemoteException
	 */
	public int addSlave(Slave s, String slaveName)throws RemoteException{
    if(slaveNameMap.containsKey(slaveName)){
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
	public void removeSlave(int slaveKey)throws RemoteException{
    SlaveRunnable slave = slaveMap.remove(slaveKey);
    if(slave == null){
      System.out.println("Slave "+ slaveKey + " not found!");
      throw new RemoteException();
    }
    slaveNameMap.remove(slave.getSlaveName());
    System.out.println("Slave "+ slave.getSlaveName()+" removed.");
  }

	/**
	 * Indica para o mestre que o escravo achou uma chave candidata.
	 * @param currentindex índice da chave candidata no dicionário
	 * @param currentguess chute que inclui chave candidata e
	 * mensagem decriptografada com a chave candidata
	 * @throws RemoteException
	 */
	public void foundGuess(long currentindex,Guess currentguess)throws RemoteException{
    int slave = findSlaveByIndex(currentindex);
    if(slave == -1){
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
  private int findSlaveByIndex(long index){
    for (ConcurrentMap.Entry<Integer, SlaveRunnable> entry : slaveMap.entrySet()){
      SlaveRunnable slave = entry.getValue();
      if(index <= slave.getFinalWordIndex() && index > slave.getInitialWordIndex()){
        return entry.getKey();
      }
    }
    return -1;
  }

	/**
	 * Chamado por cada escravo a cada 10s durante ataque para indicar
	 * progresso no ataque, e ao final do ataque.
	 * @param currentindex índice da chave já verificada
	 * @throws RemoteException
	 */
	public void checkpoint(long currentindex)throws RemoteException{
    SlaveRunnable slave = slaveMap.get(findSlaveByIndex(currentindex));
    if(slave != null){
      slave.setCurrentWordIndex(currentindex);
      slave.setLastCall(System.currentTimeMillis());
      System.out.println("Checkpoint received:");
      System.out.println("Slave name: " + slave.getSlaveName());
      System.out.println("Index: " + currentindex);
      return;
    }
    System.out.println("Checkpoint failed on index "+currentindex);
  }

  /**
	 * Operação oferecida pelo mestre para iniciar um ataque.
	 * @param ciphertext mensagem critografada
	 * @param knowntext trecho conhecido da mensagem decriptografada
	 * @return vetor de chutes: chaves candidatas e mensagem
	 * decritografada com chaves candidatas
	 */
	public Guess[] attack(byte[] ciphertext,byte[] knowntext)throws RemoteException{
    if (slaveMap.isEmpty()) {
        System.out.println("There are no slaves to execute the attack");
        return null;
    }
    this.cipherText = ciphertext;
    this.knownText = knowntext;

    ConcurrentMap<Integer,Thread> threads = spreadAttack(0,dictionarySize);

    Runnable watchDog = new Runnable() {
        @Override
        public void run() {
          try {
            watchAttack();
          }catch (RemoteException e) {System.out.println(e.getMessage());}
        }
    };
    try {
      for (ConcurrentMap.Entry<Integer, Thread> entry : threads.entrySet()){
        entry.getValue().join();
      }
    }catch (InterruptedException e) {
      System.out.println("Error while joining the Threads");
      System.out.println(e.getMessage());
    }

    return guesses.toArray(new Guess[guesses.size()]);
  }

  /**
   * Operação que trata de distribuir cada escravo a sua tarefa.
   * @param initialWordIndex o indice que começa a distribuição
   * @param finalWordIndex o indice que termina
   * @return um HashMap das threads já rodando.
   */
  protected ConcurrentMap<Integer,Thread> spreadAttack(long initialWordIndex,long finalWordIndex){
    int slaveMapSize = slaveMap.size();
    if(slaveMapSize == 0){
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
      if(extraPartition-- > 0){
        slaveMap.get(index).setSubAttack(aux,aux+slavePartition+1,this);
        aux += slavePartition + 1;
      }
      else{
        slaveMap.get(index).setSubAttack(aux,aux+slavePartition,this);
        aux += slavePartition;
      }
      Thread thread = new Thread(slaveMap.get(index));
      thread.start();
      threadMap.put(index,thread);
    }
    return threadMap;
  }

  /**
   * Função que verifica se todos os escravos realizaram checkpoint nos ultimos 20 segundos.
   * casp não, o escravo é removido.
   * @throws RemoteException
   */
  public void watchAttack() throws RemoteException{
    while (!allFinished()){
      for (Integer index : slaveMap.keySet()) {
        SlaveRunnable slave = slaveMap.get(index);
        if(slave.getLastCall() > System.currentTimeMillis() - 20000){
          removeSlave(slave.getKey());
          spreadAttack(slave.getCurrentWordIndex(),slave.getFinalWordIndex());
          System.out.println("Slave "+ slave.getSlaveName() + " late for the checkpoint.");
          System.out.println("Slave "+ slave.getSlaveName()+" discarted.");
        }
      }
      try {
        if (!allFinished()) {
          Thread.sleep(20000);
        }
      }catch (InterruptedException e) {
        System.out.println(e.getMessage());
      }
    }
  }


  /**
   * Retorna se todos os escravos já terminaram.
   */
  private boolean allFinished(){
    boolean aux = true;
    for(Integer index : slaveMap.keySet()){
      SlaveRunnable slave = slaveMap.get(index);
      if(slave.getCurrentWordIndex() < slave.getFinalWordIndex()){
        aux = false;
        break;
      }
    }
    return aux;
  }

  public static void main(String[] args) {
    try {
      Registry registry = LocateRegistry.getRegistry();
      List<String> dictionary = Util.loadDictionary();
	    Master master = (Master) UnicastRemoteObject.exportObject(new BernardoMaster(dictionary.size()),0);
	    // Bind the remote object in the registry
	    registry.rebind("mestre", master);
	    System.out.println("Server ready");
		} catch (Exception e) {
		    System.err.println("Server exception: " + e.toString());
        e.printStackTrace();
		}
	}
}
