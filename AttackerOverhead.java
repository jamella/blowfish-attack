/**
 * Attacker.java
 */


import java.rmi.Remote;
import java.rmi.RemoteException;
import br.inf.ufes.pp2016_01.Guess;

public interface AttackerOverhead extends Remote {

	/**
	 * Operacao oferecida pelo mestre para iniciar um ataque.
	 * @param ciphertext mensagem critografada
	 * @param knowntext trecho conhecido da mensagem decriptografada
	 * @return vetor de chutes: chaves candidatas e mensagem
	 * decritografada com chaves candidatas
	 */
	public Guess[] attackOverhead(byte[] ciphertext,
			byte[] knowntext) throws RemoteException ;
}
