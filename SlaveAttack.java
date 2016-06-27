import java.util.ArrayList;
import java.util.List;
import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import br.inf.ufes.pp2016_01.*;

class SlaveAttack{
  private Slave slave;
  private int key;

  private long initialWordIndex;
  private long finalWordIndex;
  private long currentWordIndex;
  private MasterBH master;

  private long lastCall;
  private long timeInit;

  private boolean overhead;


  public SlaveAttack(ConcurrentMap.Entry<Integer, Slave> entry,long i, long f, MasterBH m, boolean o){
    slave = entry.getValue();
    key = entry.getKey();
    initialWordIndex = i;
    finalWordIndex = f;
    currentWordIndex = i;
    master = m;
    overhead = o;
  }

  public void startSubAttack()throws RemoteException{
    timeInit = System.currentTimeMillis();
    lastCall = timeInit;
    try {
      if(overhead) {
        SlaveOverhead s = (SlaveOverhead) slave;
        s.startSubAttackOverhead(master.cipherText,master.knownText,initialWordIndex,finalWordIndex,(SlaveManager)master.callbackInterface);
      } else {
        slave.startSubAttack(master.cipherText,master.knownText,initialWordIndex,finalWordIndex,(SlaveManager)master.callbackInterface);
      }
    }
    catch (RemoteException e) {
      master.removeSlave(key);
      master.spreadAttack(currentWordIndex,finalWordIndex, false);
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

  public Slave getSlave(){
    return slave;
  }

  public int getKey(){
    return key;
  }

  public long getTimeInit(){
    return timeInit;
  }

  public String toString(){
    return key +"  " +initialWordIndex + "   " + currentWordIndex + "   " + finalWordIndex + "   "+ lastCall;
  }
}
