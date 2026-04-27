package controle;

import algoritmo.Ambiente;
import gui.FramePrincipal;

public class ThreadSimulacao extends Thread {
    private static final int TEMPO_SIMULACAO_MS = 250;

    private final FramePrincipal framePrincipal;
    public boolean pleaseWait;
    public boolean allDone;
    private final int[][] matrizSimulacao;
    private final Ambiente ambiente;

    public ThreadSimulacao(Ambiente algoritmoLabirinto, FramePrincipal framePrincipal, int[][] matrizSimulacao) {
        this.pleaseWait = false;
        this.allDone = false;
        this.ambiente = algoritmoLabirinto;
        this.framePrincipal = framePrincipal;
        this.matrizSimulacao = matrizSimulacao;
    }

    @Override
    public void run() {
        do {
            try {
                Thread.sleep(TEMPO_SIMULACAO_MS);
                ambiente.executa();
                framePrincipal.atualizaGrid(ambiente.equipes);
                framePrincipal.atualizaAmbiente(matrizSimulacao);
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println(e.toString());
            }

            synchronized (this) {
                while (pleaseWait) {
                    try {
                        wait();
                    } catch (Exception ignored) {
                    }

                    if (allDone) {
                        break;
                    }
                }
            }
        } while (!allDone);

        System.out.println("Thread Simulacao Morreu");
    }
}
