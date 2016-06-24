import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.ArrayList;
import java.util.List;
import java.rmi.RemoteException;
import br.inf.ufes.pp2016_01.*;

class SlaveRunnable implements Runnable {

  private final Slave slave;
  private final int key;
  private final String slaveName;
  private final long initialWordIndex;
  private final long finalWordIndex;
  private final boolean overhead;
  private final MasterHB callbackInterface;
  private final long timeInit;

  public SlaveRunnable(Slave slave, String slaveName,int key, long i, long f, boolean o, MasterHB m){
    this.slave = slave;
    this.slaveName = slaveName;
    this.key = key;
    this.initialWordIndex = i;
    this.finalWordIndex = f;
    this.overhead = o;
    this.callbackInterface = m;
    this.timeInit = System.currentTimeMillis();
  }


  @Override
  public void run(){
    try {
      byte[] cipherText = callbackInterface.cipherText;
      byte[] knownText = callbackInterface.knownText;
      if(this.overhead) {
        SlaveOverhead s = (SlaveOverhead) slave;
        s.startSubAttackOverhead(cipherText,knownText,initialWordIndex,finalWordIndex,(SlaveManager)callbackInterface);
      } else {
        slave.startSubAttack(cipherText,knownText,initialWordIndex,finalWordIndex,(SlaveManager)callbackInterface);
      }
    }
    catch (RemoteException e) {
      try {
        callbackInterface.removeSlave(key);
        callbackInterface.spreadAttack(callbackInterface.currentWordIndexMap.get(this.key),finalWordIndex, false);
      }catch (RemoteException ex) {}
    }
  }

  public synchronized long getInitialWordIndex(){
    return initialWordIndex;
  }

  public synchronized long getFinalWordIndex(){
    return finalWordIndex;
  }

  public synchronized String getSlaveName(){
    return slaveName;
  }

  public synchronized Slave getSlave(){
    return slave;
  }

  public synchronized int getKey(){
    return key;
  }

  public synchronized long getTimeInit(){
    return timeInit;
  }

}
