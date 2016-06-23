import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.ArrayList;
import java.util.List;
import java.rmi.RemoteException;
import br.inf.ufes.pp2016_01.*;

class SlaveRunnable implements Runnable {

  private Slave slave;
  private int key;
  private String slaveName;
  private byte[] cipherText;
  private byte[] knownText;
  private long initialWordIndex;
  private long finalWordIndex;
  private long currentWordIndex;
  private boolean overhead;
  private MasterHB callbackInterface;

  private long lastCall;
  private long timeInit;

  public void setSubAttack(
		long initialWordIndex,
		long finalWordIndex,
		MasterHB callbackInterface,
    boolean overhead){
        this.initialWordIndex = initialWordIndex;
        this.finalWordIndex = finalWordIndex;
        this.callbackInterface = callbackInterface;
        this.currentWordIndex = initialWordIndex;
        this.overhead = overhead;
  }

  public SlaveRunnable(Slave slave, String slaveName,int key){
    this.slave = slave;
    this.slaveName = slaveName;
    this.key = key;
  }

  @Override
  public void run(){
    timeInit = System.currentTimeMillis();
    lastCall = timeInit;
    try {
      cipherText = callbackInterface.cipherText;
      knownText = callbackInterface.knownText;
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
        callbackInterface.spreadAttack(currentWordIndex,finalWordIndex, false);
      }catch (RemoteException ex) {}
    }
  }

  public void setCurrentWordIndex(long newIndex){
    currentWordIndex = newIndex;
  }

  public void setLastCall(long newTime){
    lastCall = newTime;
  }

  public long getLastCall(){
    return lastCall;
  }

  public long getCurrentWordIndex(){
    return currentWordIndex;
  }

  public long getInitialWordIndex(){
    return currentWordIndex;
  }

  public long getFinalWordIndex(){
    return finalWordIndex;
  }

  public String getSlaveName(){
    return slaveName;
  }

  public Slave getSlave(){
    return slave;
  }

  public int getKey(){
    return key;
  }

  public long getTimeInit(){
    return timeInit;
  }
}
